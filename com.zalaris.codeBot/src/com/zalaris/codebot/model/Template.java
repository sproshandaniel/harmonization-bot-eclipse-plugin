package com.zalaris.codebot.model;

import java.util.List;

public class Template {

    private final String id;
    private final String title;
    private final String description;
    private final List<String> triggers;   // phrases that activate this template
    private final String snippet;          // ABAP code to insert

    public Template(String id,
                    String title,
                    String description,
                    List<String> triggers,
                    String snippet) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.triggers = triggers;
        this.snippet = snippet;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public List<String> getTriggers() { return triggers; }
    public String getSnippet() { return snippet; }
}
