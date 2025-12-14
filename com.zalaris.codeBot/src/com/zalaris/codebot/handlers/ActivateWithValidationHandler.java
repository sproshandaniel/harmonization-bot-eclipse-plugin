package com.zalaris.codebot.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

import com.zalaris.codebot.validation.ActivationValidator;
import com.zalaris.codebot.validation.ActivationValidator.ValidationResult;

/**
 * Command handler that performs validation before allowing activation.
 * If validation fails, activation is blocked.
 */
public class ActivateWithValidationHandler extends AbstractHandler {

    private final ActivationValidator validator = new ActivationValidator();

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        Shell shell = HandlerUtil.getActiveShell(event);

        // TODO: replace these dummy values with real ABAP object metadata from ADT/editor
        String objectName = "Z_DEMO_OBJECT";
        String objectType = "CLASS"; // e.g., CLASS, PROGRAM, etc.
        String sourceCode = getCurrentSourceCode(); // to be wired later

        ValidationResult result = validator.validate(objectName, objectType, sourceCode);

        if (!result.valid()) {
            // 💥 This is where activation is stopped
            MessageDialog.openError(shell, "Activation blocked", result.message());
            return null; // DO NOT call real activation
        }

        // If validation passes, trigger real activation
        performRealActivation(objectName, objectType, sourceCode);

        MessageDialog.openInformation(shell, "Activation", "Activation completed successfully.");
        return null;
    }

    /**
     * TODO: Integrate with ABAP Development Tools (ADT) APIs to get source from the active editor.
     */
    private String getCurrentSourceCode() {
        // For now, just a placeholder; later we'll read actual editor content.
        return "";
    }

    /**
     * TODO: Integrate with ADT / backend call that performs the activation.
     */
    private void performRealActivation(String objectName, String objectType, String sourceCode) {
        // Placeholder where you'll call the ABAP activation service.
    }
}