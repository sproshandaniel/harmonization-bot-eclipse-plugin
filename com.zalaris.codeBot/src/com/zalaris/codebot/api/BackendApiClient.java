package com.zalaris.codebot.api;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.zalaris.codebot.util.JsonUtil;

public class BackendApiClient {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .version(HttpClient.Version.HTTP_1_1)
            .proxy(new ProxySelector() {
                @Override
                public List<Proxy> select(URI uri) {
                    return List.of(Proxy.NO_PROXY);
                }

                @Override
                public void connectFailed(URI uri, java.net.SocketAddress sa, IOException ioe) {
                    // no-op
                }
            })
            .build();

    private final String baseUrl;
    private final String user;
    private final String projectId;
    private final String packName;

    public BackendApiClient() {
        this.baseUrl = normalizeBaseUrl(readSetting("codebot.backend.url", "http://127.0.0.1:8000"));
        this.user = resolveDeveloperIdentity();
        this.projectId = readSetting("codebot.project.id", "");
        this.packName = readSetting("codebot.pack.name", "");
    }

    private static String readSetting(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            value = System.getenv(key.toUpperCase().replace('.', '_'));
        }
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    private static String resolveDeveloperIdentity() {
        // Explicit plugin setting has highest priority.
        String configured = readSetting("codebot.user", "");
        if (!configured.isBlank()) {
            return configured;
        }

        // Try common local identities in Eclipse runtime/OS.
        String[] candidates = new String[] {
                System.getenv("HB_USER_EMAIL"),
                System.getenv("USEREMAIL"),
                System.getenv("EMAIL"),
                System.getProperty("user.name"),
                System.getenv("USERNAME"),
                System.getenv("USER"),
        };
        for (String candidate : candidates) {
            if (candidate != null) {
                String clean = candidate.trim();
                if (!clean.isEmpty()) {
                    return clean;
                }
            }
        }
        return "unknown-developer";
    }

    private static String normalizeBaseUrl(String configured) {
        String raw = (configured == null) ? "" : configured.trim();
        if (raw.isEmpty()) {
            return "http://127.0.0.1:8000";
        }
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) {
            raw = "http://" + raw;
        }
        while (raw.endsWith("/")) {
            raw = raw.substring(0, raw.length() - 1);
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.endsWith("/api")) {
            raw = raw.substring(0, raw.length() - 4);
            lower = raw.toLowerCase(Locale.ROOT);
        }
        // Local uvicorn dev backend is HTTP only.
        if ((lower.startsWith("https://127.0.0.1") || lower.startsWith("https://localhost"))) {
            raw = "http://" + raw.substring("https://".length());
        }
        return raw;
    }

    public Map<String, Object> assist(
            String query,
            String code,
            String objectName,
            String transport,
            boolean logViolations)
            throws IOException, InterruptedException {
        return assist(query, code, objectName, transport, logViolations, false);
    }

    public Map<String, Object> assist(
            String query,
            String code,
            String objectName,
            String transport,
            boolean logViolations,
            boolean llmFallbackConfirmed)
            throws IOException, InterruptedException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", query == null ? "" : query);
        payload.put("code", code == null ? "" : code);
        payload.put("object_name", objectName == null ? "ADT_OBJECT" : objectName);
        payload.put("project_id", projectId.isEmpty() ? null : projectId);
        payload.put("pack_name", packName.isEmpty() ? null : packName);
        payload.put("developer", user);
        payload.put("transport", transport == null ? "ADT" : transport);
        payload.put("top_k", 6);
        payload.put("log_violations", logViolations);
        payload.put("llm_fallback_confirmed", llmFallbackConfirmed);

        return postJson("/api/bot/assist", payload);
    }

    public Map<String, Object> validate(String code, String objectName, String transport)
            throws IOException, InterruptedException {
        return validate(code, objectName, transport, true);
    }

    public Map<String, Object> validate(String code, String objectName, String transport, boolean logViolations)
            throws IOException, InterruptedException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code == null ? "" : code);
        payload.put("object_name", objectName == null ? "ADT_OBJECT" : objectName);
        payload.put("project_id", projectId.isEmpty() ? null : projectId);
        payload.put("pack_name", packName.isEmpty() ? null : packName);
        payload.put("developer", user);
        payload.put("transport", transport == null ? "ADT" : transport);
        payload.put("top_k", 30);
        payload.put("log_violations", logViolations);

        return postJson("/api/bot/validate", payload);
    }

    public void logViolation(String rulePack, String objectName, String transport, String severity)
            throws IOException, InterruptedException {
        logViolation(rulePack, objectName, transport, severity, "not fixed");
    }

    public void logViolation(String rulePack, String objectName, String transport, String severity, String status)
            throws IOException, InterruptedException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rule_pack", (rulePack == null || rulePack.isBlank()) ? "generic" : rulePack);
        payload.put("object_name", objectName == null ? "ADT_OBJECT" : objectName);
        payload.put("transport", transport == null ? "ADT" : transport);
        payload.put("developer", user);
        payload.put("severity", (severity == null || severity.isBlank()) ? "MAJOR" : severity);
        payload.put("status", (status == null || status.isBlank()) ? "not fixed" : status);
        postJson("/api/dashboard/violations", payload);
    }

    public void markViolationFixed(String objectName, String transport)
            throws IOException, InterruptedException {
        logViolation("generic", objectName, transport, "MAJOR", "fixed");
    }

    private Map<String, Object> postJson(String path, Map<String, Object> payload)
            throws IOException, InterruptedException {
        String body = JsonUtil.stringify(payload);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("x-hb-user", user)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(
                    "Backend API error " + response.statusCode() + " from " + baseUrl + path + ": " + response.body());
        }
        return JsonUtil.parseObject(response.body());
    }
}
