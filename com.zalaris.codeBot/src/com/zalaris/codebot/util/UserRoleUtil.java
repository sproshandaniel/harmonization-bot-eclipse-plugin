package com.zalaris.codebot.util;

import com.zalaris.codebot.api.BackendApiClient;

public final class UserRoleUtil {

    private static final long CACHE_TTL_MS = 60_000L;
    private static volatile long cachedAtMs = 0L;
    private static volatile String cachedRole = "developer";

    private UserRoleUtil() {
    }

    public static String resolveRole() {
        String explicit = readSetting("codebot.user.role", "");
        if (!explicit.isBlank()) {
            return normalizeRole(explicit);
        }

        long now = System.currentTimeMillis();
        if (now - cachedAtMs <= CACHE_TTL_MS && cachedRole != null && !cachedRole.isBlank()) {
            return cachedRole;
        }

        String resolved = "developer";
        try {
            BackendApiClient api = new BackendApiClient();
            String fromProject = api.resolveRoleFromProjectMembership();
            if (fromProject != null && !fromProject.isBlank()) {
                resolved = normalizeRole(fromProject);
            }
        } catch (Exception ignored) {
            // fallback to default role when backend role resolution is unavailable
        }
        cachedRole = resolved;
        cachedAtMs = now;
        return resolved;
    }

    public static boolean isValidationExemptRole() {
        String role = resolveRole();
        return "architect".equals(role) || "senior_developer".equals(role);
    }

    private static String readSetting(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            value = System.getenv(key.toUpperCase().replace('.', '_'));
        }
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    private static String normalizeRole(String role) {
        if (role == null) {
            return "developer";
        }
        String normalized = role.trim().toLowerCase();
        if (normalized.equals("senior developer")) {
            return "senior_developer";
        }
        if (normalized.isEmpty()) {
            return "developer";
        }
        return normalized;
    }
}
