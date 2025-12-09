package com.zalaris.harmonization.bot.handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

import com.zalaris.harmonization.bot.views.chatView;

public class ValidateHandler extends AbstractHandler {

    public static final String MARKER_TYPE = "com.zalaris.harmonization.bot.marker";

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {

        IEditorPart editor = HandlerUtil.getActiveEditor(event);
        if (!(editor instanceof ITextEditor)) {
            chatView.postFromHandler("Active editor is not text-based – cannot validate.");
            return null;
        }

        ITextEditor textEditor = (ITextEditor) editor;
        IDocument doc = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
        String content = doc.get();

        // Resolve the underlying IFile (works for ABAP projects that are linked to workspace resources)
        IFile file = textEditor.getEditorInput().getAdapter(IFile.class);

        // Clear old markers
        if (file != null) {
            try {
            	file.deleteMarkers(MARKER_TYPE, true, org.eclipse.core.resources.IResource.DEPTH_ZERO);
            } catch (CoreException e) {
                // ignore for demo
            }
        }

        // Run the same demo rules as in chatView
        List<String> issues = new ArrayList<>();
        List<Violation> violations = validateContent(content, issues);

        // Add markers in the file
        if (file != null) {
            for (Violation v : violations) {
                try {
                    IMarker marker = file.createMarker(MARKER_TYPE);
                    marker.setAttribute(IMarker.MESSAGE, v.message);
                    marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
                    marker.setAttribute(IMarker.LINE_NUMBER, v.line);
                } catch (CoreException e) {
                    e.printStackTrace();
                }
            }
        }

        // Report into bot view
        chatView.postValidationSummary(issues);

        // Demo “block activation”
        if (!issues.isEmpty()) {
            chatView.postFromHandler(
                "Activation blocked: harmonization rules failed (" + issues.size() + " issue(s))."
            );
            org.eclipse.jface.dialogs.MessageDialog.openError(
                    HandlerUtil.getActiveShell(event),
                    "Harmonization Check",
                    "Activation blocked due to harmonization rule violations.\n" +
                    "Check Problems view and Harmonization Bot for details.");
        } else {
            chatView.postFromHandler("Activation permitted: no harmonization issues.");
        }

        return null;
    }

    // Same rules as in chatView demo: ZCL_, no SELECT *, Z_ methods
    private List<Violation> validateContent(String content, List<String> issuesOut) {
        List<Violation> violations = new ArrayList<>();

        String[] lines = content.split("\\r?\\n");
        String upper = content.toUpperCase(Locale.ROOT);

        // Rule 1: Class name must start with ZCL_
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim().toUpperCase(Locale.ROOT);
            if (line.startsWith("CLASS ") && line.contains(" DEFINITION")) {
                String afterClass = line.substring("CLASS ".length()).trim();
                String className = afterClass.split("\\s+")[0];
                if (!className.startsWith("ZCL_")) {
                    String msg = "ABAP.NAMING.CLASS: Class name should start with ZCL_. Found '" + className + "'.";
                    issuesOut.add(msg);
                    violations.add(new Violation(i + 1, msg));
                }
            }
        }

        // Rule 2: Avoid SELECT *
        if (upper.contains("SELECT *")) {
            String msg = "ABAP.PERF.SELECT_STAR: Avoid 'SELECT *'. Select only required fields.";
            issuesOut.add(msg);
            // We don't know exact line easily, so use 1 for demo
            violations.add(new Violation(1, msg));
        }

        // Rule 3: Methods should start with Z_
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim().toUpperCase(Locale.ROOT);
            if (line.startsWith("METHOD ")) {
                String afterMethod = line.substring("METHOD ".length()).trim();
                String methodName = afterMethod.split("\\s+")[0];
                if (!methodName.startsWith("Z_")) {
                    String msg = "ABAP.NAMING.METHOD: Method name should start with Z_. Found '" + methodName + "'.";
                    issuesOut.add(msg);
                    violations.add(new Violation(i + 1, msg));
                }
            }
        }

        return violations;
    }

    private static class Violation {
        final int line;
        final String message;

        Violation(int line, String message) {
            this.line = line;
            this.message = message;
        }
    }
}

