package com.zalaris.codebot.handlers;

import java.util.List;
import java.util.Locale;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

import com.zalaris.codebot.adt.AbapEditorUtil;
import com.zalaris.codebot.bot.BotResponse;
import com.zalaris.codebot.bot.BotResponse.RuleViolation;
import com.zalaris.codebot.bot.SimpleRuleBot;
import com.zalaris.codebot.governance.ViolationGovernanceService;

/**
 * Command handler that performs validation before allowing activation.
 * If MAJOR validation fails, activation is blocked.
 */
public class ActivateWithValidationHandler extends AbstractHandler {

    private final SimpleRuleBot bot = new SimpleRuleBot();

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        Shell shell = HandlerUtil.getActiveShell(event);

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
}
