package com.zalaris.codebot.validation;

/**
 * Central place for activation rules.
 * For now it's hardcoded; later we can plug in real logic
 * (naming conventions, forbidden statements, etc.).
 */
public class ActivationValidator {

    public record ValidationResult(boolean valid, String message) {
        public static ValidationResult ok() {
            return new ValidationResult(true, "OK");
        }

        public static ValidationResult error(String msg) {
            return new ValidationResult(false, msg);
        }
    }

    /**
     * Skeleton for validation.
     * Later we can inspect ABAP object name, type and source code.
     */
    public ValidationResult validate(String objectName, String objectType, String sourceCode) {
        // Example dummy rule: forbid some keyword
        if (sourceCode != null && sourceCode.contains("FORBIDDEN_KEYWORD")) {
            return ValidationResult.error(
                "Activation blocked: usage of FORBIDDEN_KEYWORD is not allowed in " + objectName + "."
            );
        }

        // Add more rules here...

        return ValidationResult.ok();
    }
}