package com.zalaris.codebot.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.zalaris.codebot.util.JsonUtil;

public class BackendApiClient {
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration DOC_REQUEST_TIMEOUT = Duration.ofSeconds(90);

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

    public Map<String, Object> generateTechnicalDoc(
            String code,
            String objectName,
            String changeSummary,
            String validationSummary)
            throws IOException, InterruptedException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code == null ? "" : code);
        payload.put("object_name", objectName == null ? "ADT_OBJECT" : objectName);
        payload.put("project_id", projectId.isEmpty() ? null : projectId);
        payload.put("developer", user);
        payload.put("change_summary", changeSummary == null ? "" : changeSummary);
        payload.put("validation_summary", validationSummary == null ? "" : validationSummary);
        return postJson("/api/docs/generate", payload, DOC_REQUEST_TIMEOUT);
    }

    public Map<String, Object> enrichTechnicalDoc(
            String existingDocument,
            String code,
            String objectName,
            String changeSummary,
            String validationSummary)
            throws IOException, InterruptedException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("existing_document", existingDocument == null ? "" : existingDocument);
        payload.put("code", code == null ? "" : code);
        payload.put("object_name", objectName == null ? "ADT_OBJECT" : objectName);
        payload.put("project_id", projectId.isEmpty() ? null : projectId);
        payload.put("developer", user);
        payload.put("change_summary", changeSummary == null ? "" : changeSummary);
        payload.put("validation_summary", validationSummary == null ? "" : validationSummary);
        return postJson("/api/docs/enrich", payload, DOC_REQUEST_TIMEOUT);
    }


    public Map<String, Object> getLatestTechnicalDoc(String objectName)
            throws IOException, InterruptedException {
        StringBuilder path = new StringBuilder("/api/docs/latest");
        List<String> queryParts = new ArrayList<>();
        String normalizedObject = (objectName == null || objectName.isBlank()) ? "ADT_OBJECT" : objectName;
        queryParts.add("object_name=" + urlEncode(normalizedObject));
        if (!projectId.isBlank()) {
            queryParts.add("project_id=" + urlEncode(projectId));
        }
        if (!user.isBlank()) {
            queryParts.add("developer=" + urlEncode(user));
        }
        if (!queryParts.isEmpty()) {
            path.append("?").append(String.join("&", queryParts));
        }
        return getJsonObject(path.toString());
    }

    public List<String> getViolationEnforcedRoles() throws IOException, InterruptedException {
        return getRoleListSetting("violation_enforced_roles", List.of("developer", "senior_developer"));
    }

    public List<String> getMandatoryDocumentationRoles() throws IOException, InterruptedException {
        return getRoleListSetting("mandatory_documentation_roles", List.of());
    }

    public boolean hasReleasedWithoutDocumentationViolation(String objectName)
            throws IOException, InterruptedException {
        String normalizedObject = (objectName == null || objectName.isBlank()) ? "ADT_OBJECT" : objectName;
        String path = "/api/dashboard/violations/exists?object_name="
                + urlEncode(normalizedObject)
                + "&statuses="
                + urlEncode("Released without documentation");
        Map<String, Object> response = getJsonObject(path);
        Object raw = response.get("exists");
        if (raw instanceof Boolean) {
            return ((Boolean) raw).booleanValue();
        }
        if (raw instanceof Number) {
            return ((Number) raw).intValue() != 0;
        }
        if (raw != null) {
            String text = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
            return text.equals("true") || text.equals("1") || text.equals("yes");
        }
        return false;
    }

    public boolean hasAnyTechnicalDocumentForObject(String objectName)
            throws IOException, InterruptedException {
        try {
            Map<String, Object> latest = getLatestTechnicalDoc(objectName);
            Object rawDoc = latest.get("document");
            String doc = rawDoc == null ? "" : String.valueOf(rawDoc).trim();
            return !doc.isEmpty();
        } catch (IOException ex) {
            String msg = ex.getMessage() == null ? "" : ex.getMessage();
            if (msg.contains("404")) {
                return false;
            }
            throw ex;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> getRoleListSetting(String key, List<String> defaults) throws IOException, InterruptedException {
        Object parsed = getJsonAny("/api/settings");
        if (!(parsed instanceof Map<?, ?> root)) {
            return new ArrayList<>(defaults);
        }
        Object governanceObj = ((Map<String, Object>) root).get("governance_controls");
        if (!(governanceObj instanceof Map<?, ?> governance)) {
            return new ArrayList<>(defaults);
        }
        Object rawRoles = governance.get(key);
        if (!(rawRoles instanceof List<?> rawList)) {
            return new ArrayList<>(defaults);
        }
        List<String> out = new ArrayList<>();
        for (Object item : rawList) {
            if (item == null) {
                continue;
            }
            String role = String.valueOf(item).trim().toLowerCase(Locale.ROOT);
            if (!role.isEmpty()) {
                out.add(role);
            }
        }
        if (out.isEmpty()) {
            return new ArrayList<>(defaults);
        }
        return out;
    }

    public Map<String, Object> saveTechnicalDoc(
            String title,
            String document,
            String objectName)
            throws IOException, InterruptedException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title == null ? "Technical Design" : title);
        payload.put("document", document == null ? "" : document);
        payload.put("object_name", objectName == null ? "ADT_OBJECT" : objectName);
        payload.put("project_id", projectId.isEmpty() ? null : projectId);
        payload.put("developer", user);
        return postJson("/api/docs/save", payload);
    }

    public String resolveRoleFromProjectMembership() throws IOException, InterruptedException {
        Object parsed = getJsonAny("/api/projects");
        List<Object> projects = asList(parsed);
        if (projects.isEmpty() && parsed instanceof Map<?, ?>) {
            Object nested = ((Map<?, ?>) parsed).get("projects");
            projects = asList(nested);
        }
        if (projects.isEmpty()) {
            return "";
        }

        String bestRole = "";
        String userLower = user == null ? "" : user.trim().toLowerCase(Locale.ROOT);
        for (Object projectObj : projects) {
            if (!(projectObj instanceof Map<?, ?>)) {
                continue;
            }
            Map<?, ?> project = (Map<?, ?>) projectObj;
            String pid = asString(project, "id");
            if (!projectId.isBlank() && !projectId.equals(pid)) {
                continue;
            }
            List<Object> members = asList(project.get("members"));
            for (Object memberObj : members) {
                if (!(memberObj instanceof Map<?, ?>)) {
                    continue;
                }
                Map<?, ?> member = (Map<?, ?>) memberObj;
                String email = asString(member, "email").toLowerCase(Locale.ROOT);
                if (email.isEmpty() || !isSameIdentity(userLower, email)) {
                    continue;
                }
                String role = normalizeRole(asString(member, "role"));
                if (priority(role) > priority(bestRole)) {
                    bestRole = role;
                }
            }
        }
        return bestRole;
    }

    private String asString(Map<?, ?> map, String key) {
        if (map == null) {
            return "";
        }
        Object value = map.get(key);
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    private boolean isSameIdentity(String runtimeUser, String memberEmail) {
        String u = runtimeUser == null ? "" : runtimeUser.trim().toLowerCase(Locale.ROOT);
        String m = memberEmail == null ? "" : memberEmail.trim().toLowerCase(Locale.ROOT);
        if (u.isEmpty() || m.isEmpty()) {
            return false;
        }
        if (u.equals(m)) {
            return true;
        }
        String mLocal = m.contains("@") ? m.substring(0, m.indexOf('@')) : m;
        if (u.equals(mLocal)) {
            return true;
        }
        String uLocal = u.contains("@") ? u.substring(0, u.indexOf('@')) : u;
        return uLocal.equals(mLocal);
    }

    private int priority(String role) {
        if ("architect".equals(role)) {
            return 3;
        }
        if ("senior_developer".equals(role)) {
            return 2;
        }
        if ("developer".equals(role)) {
            return 1;
        }
        return 0;
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return "";
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("senior developer")) {
            return "senior_developer";
        }
        return normalized;
    }

    private List<Object> asList(Object value) {
        if (value instanceof List<?>) {
            return new ArrayList<>((List<?>) value);
        }
        return java.util.Collections.emptyList();
    }

    private Map<String, Object> postJson(String path, Map<String, Object> payload)
            throws IOException, InterruptedException {
        return postJson(path, payload, DEFAULT_REQUEST_TIMEOUT);
    }

    private Map<String, Object> postJson(String path, Map<String, Object> payload, Duration timeout)
            throws IOException, InterruptedException {
        String body = JsonUtil.stringify(payload);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(timeout == null ? DEFAULT_REQUEST_TIMEOUT : timeout)
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


    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private Map<String, Object> getJsonObject(String path) throws IOException, InterruptedException {
        Object parsed = getJsonAny(path);
        if (parsed instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    out.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return out;
        }
        throw new IOException("Backend returned non-object response from " + baseUrl + path);
    }
    private Object getJsonAny(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("x-hb-user", user)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(
                    "Backend API error " + response.statusCode() + " from " + baseUrl + path + ": " + response.body());
        }
        return JsonUtil.parse(response.body());
    }
}
