package com.zalaris.codebot.views;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.part.ViewPart;

import com.zalaris.codebot.adt.AbapEditorUtil;
import com.zalaris.codebot.bot.BotResponse;
import com.zalaris.codebot.bot.BotResponse.RuleViolation;
import com.zalaris.codebot.bot.SimpleRuleBot;
import com.zalaris.codebot.governance.ViolationGovernanceService;

import jakarta.inject.Inject;

/**
 * CodeBot view: single-window chat and response experience.
 */
public class BotView extends ViewPart {

    public static final String ID = "com.zalaris.codebot.views.BotView";

    @Inject
    IWorkbench workbench;

    private Text questionText;
    private Text conversationText;
    private Text violationDetailText;
    private org.eclipse.swt.widgets.List violationsList;
    private Label statusLabel;
    private Button chatButton;
    private Button validateButton;
    private Button clearButton;
    private Button pasteButton;

    private final SimpleRuleBot bot = new SimpleRuleBot();
    private BotResponse lastResponse;
    private List<RuleViolation> currentViolations = java.util.Collections.emptyList();

    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout(1, false));

        Label title = new Label(parent, SWT.NONE);
        title.setText("CodeBot");

        Label promptLabel = new Label(parent, SWT.NONE);
        promptLabel.setText("Request");

        questionText = new Text(parent, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        GridData qData = new GridData(SWT.FILL, SWT.TOP, true, false);
        qData.heightHint = 72;
        questionText.setLayoutData(qData);

        Composite actions = new Composite(parent, SWT.NONE);
        actions.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        GridLayout actionLayout = new GridLayout(5, false);
        actionLayout.marginWidth = 0;
        actionLayout.marginHeight = 0;
        actions.setLayout(actionLayout);

        chatButton = new Button(actions, SWT.PUSH);
        chatButton.setText("Chat");

        validateButton = new Button(actions, SWT.PUSH);
        validateButton.setText("Validate");

        clearButton = new Button(actions, SWT.PUSH);
        clearButton.setText("Clear");

        pasteButton = new Button(actions, SWT.PUSH);
        pasteButton.setText("Paste Suggestion");
        pasteButton.setEnabled(false);

        statusLabel = new Label(actions, SWT.NONE);
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        statusLabel.setText("Ready");

        Label responseLabel = new Label(parent, SWT.NONE);
        responseLabel.setText("Conversation");

        conversationText = new Text(
                parent,
                SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.READ_ONLY);
        conversationText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        Label violationsLabel = new Label(parent, SWT.NONE);
        violationsLabel.setText("Violations (click to jump to line)");

        violationsList = new org.eclipse.swt.widgets.List(parent, SWT.BORDER | SWT.V_SCROLL);
        GridData vData = new GridData(SWT.FILL, SWT.TOP, true, false);
        vData.heightHint = 100;
        violationsList.setLayoutData(vData);

        Label violationDetailLabel = new Label(parent, SWT.NONE);
        violationDetailLabel.setText("Selected Violation Detail");

        violationDetailText = new Text(
                parent,
                SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.READ_ONLY);
        GridData vdData = new GridData(SWT.FILL, SWT.TOP, true, false);
        vdData.heightHint = 90;
        violationDetailText.setLayoutData(vdData);

        hookListeners();
    }

    private void hookListeners() {
        chatButton.addListener(SWT.Selection, e -> handleChat(false));
        validateButton.addListener(SWT.Selection, e -> handleChat(true));
        clearButton.addListener(SWT.Selection, e -> clearConversation());
        pasteButton.addListener(SWT.Selection, e -> handlePaste());
        violationsList.addListener(SWT.Selection, e -> handleViolationClick());
        violationsList.addListener(SWT.DefaultSelection, e -> handleViolationClick());
    }

    private void handleChat(boolean forceValidate) {
        String question = questionText.getText().trim();
        if (forceValidate) {
            question = "validate current object";
            questionText.setText(question);
        }
        if (question.isEmpty()) {
            MessageDialog.openInformation(getSite().getShell(), "CodeBot", "Enter a prompt first.");
            return;
        }

        appendConversation("You", question);
        statusLabel.setText("Processing...");
        lastResponse = bot.reply(question);
        pasteButton.setEnabled(lastResponse != null && hasPasteableSuggestion(lastResponse));
        currentViolations = java.util.Collections.emptyList();
        violationsList.removeAll();
        violationDetailText.setText("");

        if (lastResponse == null) {
            appendConversation("CodeBot", "No response from bot.");
            statusLabel.setText("No response");
            return;
        }

        if (lastResponse.getKind() == BotResponse.Kind.TEMPLATE_SUGGESTION && lastResponse.hasTemplate()) {
            String msg = lastResponse.getMessage()
                    + "\n\n--- Suggested Template ---\n"
                    + lastResponse.getTemplateCode();
            appendConversation("CodeBot", msg);
            statusLabel.setText("Template suggestion ready");
            return;
        }

        if (lastResponse.getKind() == BotResponse.Kind.VALIDATION_RESULT) {
            if (lastResponse.hasViolations()) {
                List<RuleViolation> violations = sortViolationsBySeverity(lastResponse.getViolations());
                currentViolations = violations;
                StringBuilder sb = new StringBuilder();
                sb.append(lastResponse.getMessage())
                        .append("\n\nTotal violations: ").append(violations.size())
                        .append("\n")
                        .append("Use the violations panel to review and jump to each line.\n");

                int previewCount = Math.min(3, violations.size());
                sb.append("\nTop findings:\n");
                for (int i = 0; i < previewCount; i++) {
                    RuleViolation v = violations.get(i);
                    String sev = normalizeSeverity(v.getSeverity());
                    sb.append("- ").append(sev).append(" | Line ").append(v.getLine())
                            .append(" [").append(v.getRuleId()).append("] ")
                            .append(v.getTitle()).append("\n");
                    violationsList.add(formatViolationListEntry(v));
                }
                for (int i = previewCount; i < violations.size(); i++) {
                    RuleViolation v = violations.get(i);
                    violationsList.add(formatViolationListEntry(v));
                }
                if (violations.size() > previewCount) {
                    sb.append("...and ").append(violations.size() - previewCount).append(" more.");
                }
                appendConversation("CodeBot", sb.toString());
                statusLabel.setText("Validation completed with violations");
                ViolationGovernanceService.updateFromValidation(
                        AbapEditorUtil.getActiveEditorNameOrDefault(),
                        violations);
                showViolationDetails(violations.get(0));
                violationsList.select(0);
            } else {
                appendConversation("CodeBot", lastResponse.getMessage() + "\n\nNo violations detected.");
                statusLabel.setText("Validation passed");
                violationDetailText.setText("");
                ViolationGovernanceService.clear();
            }
            return;
        }

        appendConversation("CodeBot", lastResponse.getMessage());
        statusLabel.setText("Response ready");
    }

    private void appendConversation(String role, String content) {
        String current = conversationText.getText();
        String prefix = current.isEmpty() ? "" : "\n\n";
        conversationText.setText(current + prefix + role + ":\n" + content);
    }

    private void clearConversation() {
        questionText.setText("");
        conversationText.setText("");
        violationsList.removeAll();
        violationDetailText.setText("");
        currentViolations = java.util.Collections.emptyList();
        statusLabel.setText("Cleared");
        pasteButton.setEnabled(false);
        lastResponse = null;
    }

    private void handleViolationClick() {
        int index = violationsList.getSelectionIndex();
        if (index < 0 || index >= currentViolations.size()) {
            return;
        }
        RuleViolation violation = currentViolations.get(index);
        boolean ok = AbapEditorUtil.goToLine(violation.getLine());
        showViolationPopup(violation);
        if (!ok) {
            MessageDialog.openError(
                    getSite().getShell(),
                    "CodeBot",
                    "Could not navigate to line " + violation.getLine()
                            + ". Ensure an ABAP editor is active.");
            return;
        }
        statusLabel.setText("Jumped to line " + violation.getLine());
    }

    private String formatViolationListEntry(RuleViolation violation) {
        String sev = normalizeSeverity(violation.getSeverity());
        return "[" + sev + "] Line " + violation.getLine() + " [" + violation.getRuleId() + "] " + violation.getTitle();
    }

    private String normalizeSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            return "MAJOR";
        }
        return severity.trim().toUpperCase(Locale.ROOT);
    }

    private int severityRank(RuleViolation violation) {
        String sev = normalizeSeverity(violation.getSeverity());
        switch (sev) {
            case "CRITICAL":
                return 0;
            case "MAJOR":
                return 1;
            case "MINOR":
                return 2;
            case "INFO":
                return 3;
            default:
                return 4;
        }
    }

    private List<RuleViolation> sortViolationsBySeverity(List<RuleViolation> violations) {
        List<RuleViolation> sorted = new ArrayList<>(violations);
        sorted.sort(
                Comparator.comparingInt(this::severityRank)
                        .thenComparingInt(v -> Math.max(1, v.getLine())));
        return sorted;
    }

    private void showViolationPopup(RuleViolation violation) {
        StringBuilder sb = new StringBuilder();
        sb.append("Rule: ").append(violation.getTitle())
                .append("\n")
                .append("Severity: ").append(violation.getSeverity())
                .append("\n")
                .append("Line: ").append(violation.getLine())
                .append("\n")
                .append("Rule ID: ").append(violation.getRuleId())
                .append("\n")
                .append("Pack: ").append(violation.getRulePackName())
                .append("\n\n")
                .append("Description:\n")
                .append(violation.getDescription());
        if (violation.getCorrectCode() != null && !violation.getCorrectCode().trim().isEmpty()) {
            sb.append("\n\nSuggested correction:\n").append(violation.getCorrectCode());
        }
        MessageDialog.openInformation(getSite().getShell(), "Violation Detail", sb.toString());
    }

    private void showViolationDetails(RuleViolation violation) {
        StringBuilder sb = new StringBuilder();
        sb.append("Rule: ").append(violation.getTitle())
                .append("\n")
                .append("Severity: ").append(violation.getSeverity())
                .append("\n")
                .append("Line: ").append(violation.getLine())
                .append("\n")
                .append("Rule ID: ").append(violation.getRuleId())
                .append("\n")
                .append("Pack: ").append(violation.getRulePackName())
                .append("\n\n")
                .append("Description:\n")
                .append(violation.getDescription());
        if (violation.getCorrectCode() != null && !violation.getCorrectCode().trim().isEmpty()) {
            sb.append("\n\nSuggested correction:\n").append(violation.getCorrectCode());
        }
        violationDetailText.setText(sb.toString());
    }

    private void handlePaste() {
        String pasteContent = getPasteableSuggestion(lastResponse);
        if (pasteContent.isEmpty()) {
            MessageDialog.openInformation(
                    getSite().getShell(),
                    "CodeBot",
                    "There is no suggestion to paste for this response.");
            return;
        }

        boolean ok = AbapEditorUtil.insertTextAtCursor(pasteContent);
        if (!ok) {
            MessageDialog.openError(
                    getSite().getShell(),
                    "CodeBot",
                    "Could not insert template. Ensure an ABAP editor is active.");
            return;
        }

        statusLabel.setText("Suggestion pasted into editor");
    }

    private boolean hasPasteableSuggestion(BotResponse response) {
        return !getPasteableSuggestion(response).isEmpty();
    }

    private String getPasteableSuggestion(BotResponse response) {
        if (response == null) {
            return "";
        }
        if (response.hasViolations()) {
            for (RuleViolation violation : response.getViolations()) {
                String correction = violation.getCorrectCode();
                if (correction != null && !correction.trim().isEmpty()) {
                    return correction;
                }
            }
        }
        if (response.hasTemplate()) {
            String code = response.getTemplateCode();
            if (code != null && !code.trim().isEmpty()) {
                return code;
            }
        }
        return "";
    }

    @Override
    public void setFocus() {
        if (questionText != null && !questionText.isDisposed()) {
            questionText.setFocus();
        }
    }
}
