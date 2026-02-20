package com.zalaris.codebot.bot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.zalaris.codebot.adt.AbapEditorUtil;
import com.zalaris.codebot.api.BackendApiClient;
import com.zalaris.codebot.bot.BotResponse.Kind;
import com.zalaris.codebot.bot.BotResponse.RuleViolation;

public class SimpleRuleBot {

    private final BackendApiClient apiClient = new BackendApiClient();

    public BotResponse reply(String question) {
        String query = (question == null) ? "" : question.trim();
        if (query.isEmpty()) {
            return new BotResponse(
                    Kind.INFO,
                    "Ask for validation, template, or wizard guidance. Example: 'validate current object' or 'template for singleton class'.");
        }

        String activeCode = AbapEditorUtil.getActiveEditorContentOrEmpty();
        String objectName = AbapEditorUtil.getActiveEditorNameOrDefault();
        boolean shouldLogViolations = isValidationQuery(query);

        try {
            Map<String, Object> response = apiClient.assist(
                    query,
                    activeCode,
                    objectName,
                    "ADT",
                    shouldLogViolations);
            return toBotResponse(response);
        } catch (Exception ex) {
            return new BotResponse(
                    Kind.INFO,
                    "Backend connection failed. Ensure API is running and reachable at codebot.backend.url.\nDetails: "
                            + ex.getMessage());
        }
    }

    private boolean isValidationQuery(String query) {
        String q = query.toLowerCase();
        return q.contains("validate") || q.contains("violation") || q.contains("check code");
    }

    public BotResponse validateCurrentEditor() {
        String activeCode = AbapEditorUtil.getActiveEditorContentOrEmpty();
        String objectName = AbapEditorUtil.getActiveEditorNameOrDefault();
        try {
            Map<String, Object> response = apiClient.validate(activeCode, objectName, "ADT");
            return toBotResponse(response);
        } catch (Exception ex) {
            return new BotResponse(
                    Kind.INFO,
                    "Backend validation failed. " + ex.getMessage());
        }
    }

    private BotResponse toBotResponse(Map<String, Object> response) {
        String message = asString(response.get("message"), "No response from backend.");
        List<RuleViolation> violations = parseViolations(response.get("violations"));
        Map<String, Object> suggestions = asMap(response.get("suggestions"));

        String templateCode = firstSnippet(suggestions, "templates");
        if (templateCode == null || templateCode.isEmpty()) {
            templateCode = firstSnippet(suggestions, "wizards");
        }
        String violationFixCode = firstViolationFix(violations);
        String pasteCandidate = (violationFixCode != null && !violationFixCode.isEmpty())
                ? violationFixCode
                : templateCode;

        if (!violations.isEmpty()) {
            return new BotResponse(Kind.VALIDATION_RESULT, message, pasteCandidate, violations);
        }
        if (templateCode != null && !templateCode.isEmpty()) {
            return new BotResponse(Kind.TEMPLATE_SUGGESTION, message, templateCode, Collections.emptyList());
        }
        return new BotResponse(Kind.INFO, message, null, Collections.emptyList());
    }

    private String firstSnippet(Map<String, Object> suggestions, String key) {
        List<Object> entries = asList(suggestions.get(key));
        if (entries.isEmpty()) {
            return "";
        }
        Map<String, Object> first = asMap(entries.get(0));
        return asString(first.get("snippet"), "");
    }

    private List<RuleViolation> parseViolations(Object raw) {
        List<Object> items = asList(raw);
        List<RuleViolation> result = new ArrayList<>();
        for (Object item : items) {
            Map<String, Object> v = asMap(item);
            result.add(
                    new RuleViolation(
                            asString(v.get("project"), "ADT"),
                            asString(v.get("rule_pack"), "generic"),
                            asString(v.get("rule_id"), "unknown.rule"),
                            asString(v.get("title"), "Rule violation"),
                            asString(v.get("message"), asString(v.get("description"), "Violation detected.")),
                            asString(v.get("severity"), "MAJOR"),
                            asInt(v.get("line"), 1),
                            asString(v.get("suggested_code"), asString(v.get("fix"), ""))));
        }
        return result;
    }

    private String firstViolationFix(List<RuleViolation> violations) {
        for (RuleViolation v : violations) {
            String fix = v.getCorrectCode();
            if (fix != null && !fix.trim().isEmpty()) {
                return fix;
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private List<Object> asList(Object value) {
        if (value instanceof List) {
            return (List<Object>) value;
        }
        return Collections.emptyList();
    }

    private String asString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        return text.isEmpty() ? fallback : text;
    }

    private int asInt(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return fallback;
        }
    }
}
