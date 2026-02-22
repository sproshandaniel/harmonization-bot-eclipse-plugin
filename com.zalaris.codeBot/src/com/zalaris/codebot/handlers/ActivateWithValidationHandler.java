package com.zalaris.codebot.handlers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

import com.zalaris.codebot.adt.AbapEditorUtil;
import com.zalaris.codebot.api.BackendApiClient;
import com.zalaris.codebot.bot.BotResponse;
import com.zalaris.codebot.bot.BotResponse.RuleViolation;
import com.zalaris.codebot.bot.SimpleRuleBot;
import com.zalaris.codebot.governance.ViolationGovernanceService;
import com.zalaris.codebot.util.UserRoleUtil;
import com.zalaris.codebot.views.TechnicalDocumentDialog;

/**
 * Command handler that performs validation before allowing activation.
 * If MAJOR validation fails, activation is blocked.
 */
public class ActivateWithValidationHandler extends AbstractHandler {

    private final SimpleRuleBot bot = new SimpleRuleBot();
    private final BackendApiClient apiClient = new BackendApiClient();

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        Shell shell = HandlerUtil.getActiveShell(event);
        if (UserRoleUtil.isValidationExemptRole()) {
            performRealActivation();
            MessageDialog.openInformation(shell, "Activation", "Activation completed successfully.");
            return null;
        }

        BotResponse response = bot.validateCurrentEditor();
        List<RuleViolation> violations = response.getViolations();
        ViolationGovernanceService.updateFromValidation(
                AbapEditorUtil.getActiveEditorNameOrDefault(),
                violations);

        if (hasMajor(violations)) {
            MessageDialog.openError(shell, "Activation blocked", response.getMessage());
            return null;
        }

        performRealActivation();

        MessageDialog.openInformation(shell, "Activation", "Activation completed successfully.");
        promptTechnicalDocumentFlow(shell, violations);
        return null;
    }

    private boolean hasMajor(List<RuleViolation> violations) {
        if (violations == null || violations.isEmpty()) {
            return false;
        }
        for (RuleViolation v : violations) {
            String severity = (v.getSeverity() == null ? "" : v.getSeverity()).toUpperCase(Locale.ROOT);
            if ("MAJOR".equals(severity)) {
                return true;
            }
        }
        return false;
    }

    private void performRealActivation() {
        // Placeholder where you'll call the ABAP activation service.
    }

    private void promptTechnicalDocumentFlow(Shell shell, List<RuleViolation> violations) {
        boolean generate = MessageDialog.openQuestion(
                shell,
                "Technical Documentation",
                "Activation completed.\n\nDo you want to generate/update technical documentation now?");
        if (!generate) {
            return;
        }

        int mode = chooseMode(shell);
        if (mode < 0) {
            return;
        }

        String objectName = AbapEditorUtil.getActiveEditorNameOrDefault();
        String code = AbapEditorUtil.getActiveEditorContentOrEmpty();
        String validationSummary = buildValidationSummary(violations);
        String changeSummary = promptChangeSummary(shell);

        try {
            Map<String, Object> response;
            if (mode == 0) {
                response = apiClient.generateTechnicalDoc(code, objectName, changeSummary, validationSummary);
            } else {
                String existingDoc = loadExistingDocument(shell);
                if (existingDoc == null) {
                    return;
                }
                response = apiClient.enrichTechnicalDoc(existingDoc, code, objectName, changeSummary, validationSummary);
            }

            String title = asString(response.get("title"), "Technical Design - " + objectName);
            String document = asString(response.get("document"), "");
            if (document.isBlank()) {
                MessageDialog.openError(shell, "Technical Documentation", "Generated document was empty.");
                return;
            }

            Shell parent = shell != null ? shell : PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
            TechnicalDocumentDialog dialog = new TechnicalDocumentDialog(parent, apiClient, objectName, title, document);
            dialog.open();
        } catch (Exception ex) {
            MessageDialog.openError(shell, "Technical Documentation", "Failed to generate/update document:\n" + ex.getMessage());
        }
    }

    private int chooseMode(Shell shell) {
        MessageDialog modeDialog = new MessageDialog(
                shell,
                "Technical Documentation",
                null,
                "Choose how to prepare documentation for the current activation:",
                MessageDialog.QUESTION,
                new String[] { "Generate New", "Enrich Existing", "Cancel" },
                0);
        int selected = modeDialog.open();
        if (selected == 0) {
            return 0;
        }
        if (selected == 1) {
            return 1;
        }
        return -1;
    }

    private String loadExistingDocument(Shell shell) throws Exception {
        FileDialog dialog = new FileDialog(shell, SWT.OPEN);
        dialog.setText("Select Existing Technical Document");
        dialog.setFilterExtensions(new String[] { "*.md", "*.txt", "*.*" });
        String selected = dialog.open();
        if (selected == null || selected.isBlank()) {
            return null;
        }
        return Files.readString(Path.of(selected), StandardCharsets.UTF_8);
    }

    private String buildValidationSummary(List<RuleViolation> violations) {
        if (violations == null || violations.isEmpty()) {
            return "Validation passed with no rule violations.";
        }
        long major = violations.stream()
                .filter(v -> "MAJOR".equalsIgnoreCase(v.getSeverity()))
                .count();
        long minor = violations.stream()
                .filter(v -> "MINOR".equalsIgnoreCase(v.getSeverity()))
                .count();
        long info = violations.stream()
                .filter(v -> "INFO".equalsIgnoreCase(v.getSeverity()))
                .count();
        return "Validation results: major=" + major + ", minor=" + minor + ", info=" + info + ".";
    }

    private String promptChangeSummary(Shell shell) {
        InputDialog dialog = new InputDialog(
                shell,
                "Change Summary",
                "Optional: describe current change intent (used to enrich technical documentation).",
                "",
                null);
        int result = dialog.open();
        if (result != Window.OK) {
            return "";
        }
        return dialog.getValue() == null ? "" : dialog.getValue().trim();
    }

    private String asString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }
}
