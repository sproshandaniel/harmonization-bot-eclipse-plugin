package com.zalaris.codebot.startup;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IExecutionListener;
import org.eclipse.core.commands.NotHandledException;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;

import com.zalaris.codebot.governance.ViolationGovernanceService;

public class CodeBotStartup implements IStartup {

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
                    ViolationGovernanceService.onLikelyActivationCommand(commandId);
                }

                @Override
                public void postExecuteSuccess(String commandId, Object returnValue) {
                    if (commandId != null && commandId.toLowerCase().contains("activ")) {
                        System.out.println("[CodeBot] Command observed: " + commandId);
                    }
                    ViolationGovernanceService.onLikelyActivationCommand(commandId);
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
}
