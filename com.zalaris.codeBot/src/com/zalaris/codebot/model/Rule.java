package com.zalaris.codebot.model;

public class Rule {

    private final String id;
    private final String title;
    private final String description;
    private final RuleType type;
    private final Severity severity;
    private final String pattern;       // simple regex / substring for demo
    private final String badExample;
    private final String goodExample;

    public Rule(String id,
                String title,
                String description,
                RuleType type,
                Severity severity,
                String pattern,
                String badExample,
                String goodExample) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.type = type;
        this.severity = severity;
        this.pattern = pattern;
        this.badExample = badExample;
        this.goodExample = goodExample;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public RuleType getType() { return type; }
    public Severity getSeverity() { return severity; }
    public String getPattern() { return pattern; }
    public String getBadExample() { return badExample; }
    public String getGoodExample() { return goodExample; }
}
