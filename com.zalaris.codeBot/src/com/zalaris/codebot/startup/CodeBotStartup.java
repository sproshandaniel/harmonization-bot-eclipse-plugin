package com.zalaris.codebot.startup;

import java.util.Collections;
import java.util.Locale;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IExecutionListener;
import org.eclipse.core.commands.NotHandledException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;

import com.zalaris.codebot.adt.AbapEditorUtil;
import com.zalaris.codebot.api.BackendApiClient;
import com.zalaris.codebot.governance.ViolationGovernanceService;
import com.zalaris.codebot.handlers.ActivateWithValidationHandler;
import com.zalaris.codebot.util.UserRoleUtil;

public class CodeBotStartup implements IStartup {
    private static volatile long lastTransportDocPromptAtMillis = 0L;

    @Override
    public void earlyStartup() {
        PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
            ICommandService commandService = PlatformUI.getWorkbench().getService(ICommandService.class);
            if (commandService == null) {
                return;
            }
            System.out.println("[CodeBot] Startup listener initialized");
            ViolationGovernanceService.clearAllCodeBotMarkersInWorkspace();

            commandService.addExecutionListener(new IExecutionListener() {
                @Override
                public void preExecute(String commandId, ExecutionEvent event) {
                    if (commandId != null && commandId.toLowerCase().contains("activ")) {
                        System.out.println("[CodeBot] Command observed: " + commandId);
                    }
                    boolean directMatch = isLikelyTransportReleaseCommand(commandId);
                    boolean organizerFallback = isTransportOrganizerActive() && isLikelyTransportOrganizerAction(commandId);
                    if (directMatch || organizerFallback) {
                        AbapEditorUtil.captureActiveEditorSnapshot("preExecute transport command: " + commandId);
                    }
                    ViolationGovernanceService.onLikelyActivationCommand(commandId);
                }

                @Override
                public void postExecuteSuccess(String commandId, Object returnValue) {
                    if (commandId != null && commandId.toLowerCase().contains("activ")) {
                        System.out.println("[CodeBot] Command observed: " + commandId);
                    }
                    ViolationGovernanceService.onLikelyActivationCommand(commandId);
                    triggerTechnicalDocOnTransportRelease(commandId);
                }

                @Override
                public void postExecuteFailure(String commandId, ExecutionException exception) {
                    // no-op
                }

                @Override
                public void notHandled(String commandId, NotHandledException exception) {
                    // no-op
                }
            });
        });
    }

    private static void triggerTechnicalDocOnTransportRelease(String commandId) {
        logTransportLikeCommand(commandId);
        boolean directMatch = isLikelyTransportReleaseCommand(commandId);
        boolean organizerFallback = isTransportOrganizerActive() && isLikelyTransportOrganizerAction(commandId);
        if (!directMatch && !organizerFallback) {
            return;
        }
        if (organizerFallback && !directMatch) {
            System.out.println("[CodeBot] Transport organizer fallback trigger for command: " + commandId);
        } else {
            System.out.println("[CodeBot] Transport release-like command observed: " + commandId);
        }
        long now = System.currentTimeMillis();
        if (now - lastTransportDocPromptAtMillis < 2000) {
            return;
        }
        lastTransportDocPromptAtMillis = now;

        PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
            if (PlatformUI.getWorkbench().getActiveWorkbenchWindow() == null) {
                return;
            }
            Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
            boolean mandatory = UserRoleUtil.isMandatoryDocumentationRole();
            String objectName = AbapEditorUtil.getActiveEditorNameOrDefault();
            if (mandatory && isMandatoryDocumentationAlreadyHandled(objectName)) {
                System.out.println("[CodeBot] Mandatory documentation already handled for object: " + objectName);
                return;
            }
            boolean completed = new ActivateWithValidationHandler()
                    .promptTechnicalDocumentFlow(shell, Collections.emptyList(), mandatory);
            if (mandatory && !completed) {
                handleMandatoryDocumentationViolation(shell);
            }
        });
    }

    private static boolean isMandatoryDocumentationAlreadyHandled(String objectName) {
        try {
            BackendApiClient api = new BackendApiClient();
            if (api.hasReleasedWithoutDocumentationViolation(objectName)) {
                return true;
            }
            return api.hasAnyTechnicalDocumentForObject(objectName);
        } catch (Exception ex) {
            System.out.println("[CodeBot] Could not determine mandatory-documentation state: " + ex.getMessage());
            return false;
        }
    }

    private static void handleMandatoryDocumentationViolation(Shell shell) {
        try {
            BackendApiClient api = new BackendApiClient();
            String objectName = AbapEditorUtil.getActiveEditorNameOrDefault();
            String transport = AbapEditorUtil.getActiveTransportOrDefault();
            if (api.hasReleasedWithoutDocumentationViolation(objectName)) {
                System.out.println("[CodeBot] Skipping duplicate documentation violation for object: " + objectName);
                return;
            }
            String role = UserRoleUtil.resolveRole();
            MessageDialog.openError(
                    shell,
                    "Mandatory Documentation Violation",
                    "Transport released without completing required technical documentation for role '" + role + "'.");
            api.logViolation(
                    "technical-documentation-mandatory",
                    objectName,
                    transport,
                    "MAJOR",
                    "Released without documentation");
        } catch (Exception ex) {
            System.out.println("[CodeBot] Failed to log mandatory documentation violation: " + ex.getMessage());
        }
    }

    private static boolean isLikelyTransportReleaseCommand(String commandId) {
        if (commandId == null || commandId.isBlank()) {
            return false;
        }
        String id = commandId.toLowerCase(Locale.ROOT);
        boolean transportContext = id.contains("transport")
                || id.contains("cts")
                || id.contains("request")
                || id.contains("organizer")
                || id.contains("trkorr")
                || id.contains("se09")
                || id.contains("se10");
        boolean releaseAction = id.contains("release")
                || id.contains("move")
                || id.contains("import")
                || id.contains("export")
                || id.contains("deploy")
                || id.contains("deployment")
                || id.contains(".rel")
                || id.contains("rel.");
        return transportContext && releaseAction;
    }

    private static boolean isLikelySapOrAdtCommand(String commandId) {
        if (commandId == null || commandId.isBlank()) {
            return false;
        }
        String id = commandId.toLowerCase(Locale.ROOT);
        return id.contains("com.sap") || id.contains("adt") || id.contains("abap");
    }

    private static boolean isLikelyTransportOrganizerAction(String commandId) {
        if (!isLikelySapOrAdtCommand(commandId)) {
            return false;
        }
        if (commandId == null || commandId.isBlank()) {
            return false;
        }
        String id = commandId.toLowerCase(Locale.ROOT);
        boolean transportContext = id.contains("transport")
                || id.contains("cts")
                || id.contains("request")
                || id.contains("organizer")
                || id.contains("trkorr")
                || id.contains("se09")
                || id.contains("se10");
        boolean releaseLike = id.contains("release")
                || id.contains("move")
                || id.contains("import")
                || id.contains("export")
                || id.contains("deploy")
                || id.contains("deployment")
                || id.contains(".rel")
                || id.contains("rel.");
        return transportContext || releaseLike;
    }

    private static boolean isTransportOrganizerActive() {
        try {
            if (PlatformUI.getWorkbench().getActiveWorkbenchWindow() == null) {
                return false;
            }
            IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
            if (page == null) {
                return false;
            }
            IWorkbenchPart part = page.getActivePart();
            if (part == null) {
                return false;
            }
            String title = part.getTitle() == null ? "" : part.getTitle().toLowerCase(Locale.ROOT);
            String siteId = (part.getSite() == null || part.getSite().getId() == null)
                    ? ""
                    : part.getSite().getId().toLowerCase(Locale.ROOT);
            return title.contains("transport organizer")
                    || title.contains("transport")
                    || siteId.contains("transport")
                    || siteId.contains("organizer")
                    || siteId.contains("se09")
                    || siteId.contains("se10");
        } catch (Exception ex) {
            System.out.println("[CodeBot] Failed to inspect active part for transport organizer: " + ex.getMessage());
            return false;
        }
    }

    private static void logTransportLikeCommand(String commandId) {
        if (commandId == null || commandId.isBlank()) {
            return;
        }
        String id = commandId.toLowerCase(Locale.ROOT);
        if (id.contains("transport") || id.contains("request") || id.contains("organizer") || id.contains("release")) {
            System.out.println("[CodeBot] Transport command candidate: " + commandId);
        }
    }
}
