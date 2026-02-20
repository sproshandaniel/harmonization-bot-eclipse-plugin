package com.zalaris.codebot.governance;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

import com.zalaris.codebot.adt.AbapEditorUtil;
import com.zalaris.codebot.api.BackendApiClient;
import com.zalaris.codebot.bot.BotResponse.RuleViolation;

public final class ViolationGovernanceService {

    private static final String MARKER_TYPE = IMarker.PROBLEM;
    private static final String SOURCE = "CodeBot";

    private static volatile String pendingObjectName = "";
    private static volatile List<RuleViolation> pendingMajorViolations = Collections.emptyList();
    private static volatile long lastLoggedAtMillis = 0L;
    private static volatile long lastValidationRunMillis = 0L;

    private ViolationGovernanceService() {
    }

    public static void updateFromValidation(String objectName, List<RuleViolation> violations) {
        List<RuleViolation> majors = filterMajorOnly(violations);
        pendingObjectName = objectName == null ? "ADT_OBJECT" : objectName;
        pendingMajorViolations = majors;

        if (majors.isEmpty()) {
            clearAllCodeBotMarkersInWorkspace();
            return;
        }
        publishMarkers(majors);
    }

    public static void clear() {
        pendingMajorViolations = Collections.emptyList();
        clearAllCodeBotMarkersInWorkspace();
    }

    public static boolean hasBlockingViolations() {
        return !pendingMajorViolations.isEmpty();
    }

    public static void onLikelyActivationCommand(String commandId) {
        if (!isLikelyActivationCommand(commandId)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastValidationRunMillis < 1200) {
            return;
        }
        lastValidationRunMillis = now;

        System.out.println("[CodeBot] Triggering activation-attempt validation for command: " + commandId);
        new Thread(ViolationGovernanceService::validateOnActivationAttempt, "codebot-activation-validate").start();
    }

    private static void validateOnActivationAttempt() {
        String objectName = captureActiveObjectName();
        String code = captureActiveCode();

        if (code == null || code.isBlank()) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException ignored) {
            }
            code = captureActiveCode();
        }

        if (code == null || code.isBlank()) {
            System.out.println("[CodeBot] Skipping validation: active editor content unavailable.");
            return;
        }

        BackendApiClient api = new BackendApiClient();
        try {
            Map<String, Object> response = api.validate(code, objectName, "ADT", false);
            List<RuleViolation> majors = filterMajorOnly(parseViolations(response.get("violations")));

            pendingObjectName = objectName;
            pendingMajorViolations = majors;

            if (majors.isEmpty()) {
                clearAllCodeBotMarkersInWorkspace();
                try {
                    api.markViolationFixed(objectName, "ADT");
                } catch (IOException | InterruptedException ex) {
                    System.out.println("[CodeBot] Failed to mark violation fixed: " + ex.getMessage());
                }
                return;
            }

            publishMarkers(majors);
            showForgotValidationWarning(majors.size());
            logMajorsToBackend(api, majors, objectName);
        } catch (Exception ex) {
            System.out.println("[CodeBot] Activation-attempt validation failed: " + ex.getMessage());
        }
    }

    private static void logMajorsToBackend(BackendApiClient api, List<RuleViolation> majors, String objectName) {
        long now = System.currentTimeMillis();
        if (now - lastLoggedAtMillis < 3000) {
            return;
        }
        lastLoggedAtMillis = now;

        RuleViolation top = majors.get(0);
        try {
            api.logViolation(top.getRulePackName(), objectName, "ADT", "MAJOR", "not fixed");
        } catch (IOException | InterruptedException ex) {
            System.out.println("[CodeBot] Failed to log violation on activation attempt: " + ex.getMessage());
        }
    }

    private static String captureActiveCode() {
        final String[] value = new String[] { "" };
        Display display = Display.getDefault();
        if (display == null || display.isDisposed()) {
            return "";
        }
        display.syncExec(() -> value[0] = AbapEditorUtil.getActiveEditorContentOrEmpty());
        return value[0];
    }

    private static String captureActiveObjectName() {
        final String[] value = new String[] { "ADT_OBJECT" };
        Display display = Display.getDefault();
        if (display == null || display.isDisposed()) {
            return "ADT_OBJECT";
        }
        display.syncExec(() -> value[0] = AbapEditorUtil.getActiveEditorNameOrDefault());
        return value[0];
    }

    private static List<RuleViolation> parseViolations(Object raw) {
        if (!(raw instanceof List<?>)) {
            return Collections.emptyList();
        }
        List<?> items = (List<?>) raw;
        List<RuleViolation> result = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?>)) {
                continue;
            }
            Map<?, ?> v = (Map<?, ?>) item;
            result.add(new RuleViolation(
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

    private static String asString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        return text.isEmpty() ? fallback : text;
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static List<RuleViolation> filterMajorOnly(List<RuleViolation> violations) {
        if (violations == null || violations.isEmpty()) {
            return Collections.emptyList();
        }
        List<RuleViolation> result = new ArrayList<>();
        for (RuleViolation v : violations) {
            if ("MAJOR".equals(normalizeSeverity(v.getSeverity()))) {
                result.add(v);
            }
        }
        return result;
    }

    private static String normalizeSeverity(String severity) {
        if (severity == null) {
            return "MAJOR";
        }
        return severity.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isLikelyActivationCommand(String commandId) {
        if (commandId == null) {
            return false;
        }
        String c = commandId.toLowerCase(Locale.ROOT);
        if (c.contains("activ")) {
            return true;
        }
        if (c.startsWith("com.sap.adt")) {
            return c.contains("abap") || c.contains("source") || c.contains("object") || c.contains("workbench");
        }
        return c.contains("save") && (c.contains("adt") || c.contains("abap"));
    }

    private static void showForgotValidationWarning(int count) {
        Display display = Display.getDefault();
        if (display == null || display.isDisposed()) {
            return;
        }
        display.asyncExec(() -> {
            if (PlatformUI.getWorkbench().getActiveWorkbenchWindow() == null) {
                return;
            }
            MessageDialog.openError(
                    PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                    "CodeBot Governance",
                    "You have activated the code without performing Zalaris Code governance validations and your code has Major Violations. Open  Zalcode  and fix the Violations.");
        });
    }

    private static void publishMarkers(List<RuleViolation> violations) {
        IResource resource = AbapEditorUtil.getActiveEditorResource();
        if (resource == null) {
            return;
        }
        clearMarkers(resource);
        for (RuleViolation v : violations) {
            try {
                IMarker marker = resource.createMarker(MARKER_TYPE);
                marker.setAttribute(IMarker.SOURCE_ID, SOURCE);
                marker.setAttribute(IMarker.MESSAGE,
                        "You have activated the code without performing Zalaris Code governance validations and your code has Major Violations. Open  Zalcode  and fix the Violations.");
                marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
                marker.setAttribute(IMarker.LINE_NUMBER, Math.max(1, v.getLine()));
            } catch (Exception ex) {
                System.out.println("[CodeBot] Failed to create marker: " + ex.getMessage());
            }
        }
    }

    public static void clearAllCodeBotMarkersInWorkspace() {
        try {
            IResource root = org.eclipse.core.resources.ResourcesPlugin.getWorkspace().getRoot();
            IMarker[] markers = root.findMarkers(MARKER_TYPE, true, IResource.DEPTH_INFINITE);
            for (IMarker marker : markers) {
                String source = marker.getAttribute(IMarker.SOURCE_ID, "");
                if (SOURCE.equals(source)) {
                    marker.delete();
                }
            }
        } catch (Exception ex) {
            System.out.println("[CodeBot] Failed to clear workspace markers: " + ex.getMessage());
        }
    }

    private static void clearMarkers() {
        IResource resource = AbapEditorUtil.getActiveEditorResource();
        if (resource == null) {
            return;
        }
        clearMarkers(resource);
    }

    private static void clearMarkers(IResource resource) {
        try {
            IMarker[] markers = resource.findMarkers(MARKER_TYPE, true, IResource.DEPTH_ZERO);
            for (IMarker marker : markers) {
                String source = marker.getAttribute(IMarker.SOURCE_ID, "");
                if (SOURCE.equals(source)) {
                    marker.delete();
                }
            }
        } catch (Exception ex) {
            System.out.println("[CodeBot] Failed to clear markers: " + ex.getMessage());
        }
    }
}




