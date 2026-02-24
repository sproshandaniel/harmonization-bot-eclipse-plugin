package com.zalaris.codebot.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.zalaris.codebot.api.BackendApiClient;

public final class UserRoleUtil {

    private static final long CACHE_TTL_MS = 60_000L;
    private static final List<String> DEFAULT_VIOLATION_ENFORCED_ROLES = List.of("developer", "senior_developer");
    private static final List<String> DEFAULT_MANDATORY_DOCUMENTATION_ROLES = List.of();
    private static volatile long cachedRoleAtMs = 0L;
    private static volatile long cachedGovernanceAtMs = 0L;
    private static volatile String cachedRole = "developer";
    private static volatile List<String> cachedViolationEnforcedRoles = new ArrayList<>(DEFAULT_VIOLATION_ENFORCED_ROLES);
    private static volatile List<String> cachedMandatoryDocumentationRoles = new ArrayList<>(DEFAULT_MANDATORY_DOCUMENTATION_ROLES);

    private UserRoleUtil() {
    }

    public static String resolveRole() {
        String explicit = readSetting("codebot.user.role", "");
        if (!explicit.isBlank()) {
            return normalizeRole(explicit);
        }

        long now = System.currentTimeMillis();
        if (now - cachedRoleAtMs <= CACHE_TTL_MS && cachedRole != null && !cachedRole.isBlank()) {
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
        cachedRoleAtMs = now;
        return resolved;
    }

    public static boolean isValidationExemptRole() {
        return !isViolationEnforcedRole();
    }

    public static boolean isViolationEnforcedRole() {
        String role = normalizeRole(resolveRole());
        Set<String> allowed = new HashSet<>(getViolationEnforcedRoles());
        return allowed.contains(role);
    }

    public static boolean isMandatoryDocumentationRole() {
        String role = normalizeRole(resolveRole());
        Set<String> mandatory = new HashSet<>(getMandatoryDocumentationRoles());
        return mandatory.contains(role);
    }

    public static List<String> getViolationEnforcedRoles() {
        refreshGovernanceRolesCacheIfNeeded();
        return new ArrayList<>(cachedViolationEnforcedRoles);
    }

    public static List<String> getMandatoryDocumentationRoles() {
        refreshGovernanceRolesCacheIfNeeded();
        return new ArrayList<>(cachedMandatoryDocumentationRoles);
    }

    private static void refreshGovernanceRolesCacheIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - cachedGovernanceAtMs <= CACHE_TTL_MS) {
            return;
        }
        try {
            BackendApiClient api = new BackendApiClient();
            cachedViolationEnforcedRoles = normalizeRoleList(api.getViolationEnforcedRoles(), DEFAULT_VIOLATION_ENFORCED_ROLES);
            cachedMandatoryDocumentationRoles = normalizeRoleList(api.getMandatoryDocumentationRoles(), DEFAULT_MANDATORY_DOCUMENTATION_ROLES);
        } catch (Exception ignored) {
            cachedViolationEnforcedRoles = new ArrayList<>(DEFAULT_VIOLATION_ENFORCED_ROLES);
            cachedMandatoryDocumentationRoles = new ArrayList<>(DEFAULT_MANDATORY_DOCUMENTATION_ROLES);
        }
        cachedGovernanceAtMs = now;
    }

    private static List<String> normalizeRoleList(List<String> raw, List<String> defaults) {
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>(defaults);
        }
        List<String> normalized = new ArrayList<>();
        for (String item : raw) {
            String role = normalizeRole(item);
            if (!role.isEmpty() && !normalized.contains(role)) {
                normalized.add(role);
            }
        }
        if (normalized.isEmpty()) {
            return new ArrayList<>(defaults);
        }
        return normalized;
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
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("senior developer")) {
            return "senior_developer";
        }
        if (normalized.isEmpty()) {
            return "developer";
        }
        return normalized;
    }
}
