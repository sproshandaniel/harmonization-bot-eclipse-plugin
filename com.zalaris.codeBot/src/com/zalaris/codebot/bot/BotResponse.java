package com.zalaris.codebot.bot;

import java.util.Collections;
import java.util.List;

public class BotResponse {

    public enum Kind {
        INFO,
        VALIDATION_RESULT,
        TEMPLATE_SUGGESTION
    }

    private final Kind kind;
    private final String message;
    private final String templateCode;
    private final List<RuleViolation> violations;

    public BotResponse(Kind kind, String message) {
        this(kind, message, null, Collections.emptyList());
    }

    public BotResponse(Kind kind, String message, String templateCode) {
        this(kind, message, templateCode, Collections.emptyList());
    }

    public BotResponse(Kind kind,
                       String message,
                       String templateCode,
                       List<RuleViolation> violations) {
        this.kind = kind;
        this.message = message;
        this.templateCode = templateCode;
        this.violations = (violations != null) ? violations : Collections.emptyList();
    }

    public Kind getKind() {
        return kind;
    }

    public String getMessage() {
        return message;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public boolean hasTemplate() {
        return templateCode != null && !templateCode.isEmpty();
    }

    public List<RuleViolation> getViolations() {
        return violations;
    }

    public boolean hasViolations() {
        return !violations.isEmpty();
    }

    /**
     * Violated rule from the dashboard.
     */
    public static class RuleViolation {
        private final String projectName;
        private final String rulePackName;
        private final String ruleId;
        private final String title;
        private final String description;
        private final int line;
        private final String correctCode;

        public RuleViolation(String projectName,
                             String rulePackName,
                             String ruleId,
                             String title,
                             String description,
                             int line,
                             String correctCode) {
            this.projectName = projectName;
            this.rulePackName = rulePackName;
            this.ruleId = ruleId;
            this.title = title;
            this.description = description;
            this.line = line;
            this.correctCode = correctCode;
        }

        public String getProjectName() { return projectName; }
        public String getRulePackName() { return rulePackName; }
        public String getRuleId() { return ruleId; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public int getLine() { return line; }
        public String getCorrectCode() { return correctCode; }
    }
}