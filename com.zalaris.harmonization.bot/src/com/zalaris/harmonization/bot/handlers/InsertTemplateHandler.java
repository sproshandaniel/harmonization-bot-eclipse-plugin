package com.zalaris.harmonization.bot.handlers;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

import com.zalaris.harmonization.bot.views.chatView;

public class InsertTemplateHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {

        IEditorPart editor = HandlerUtil.getActiveEditor(event);
        if (!(editor instanceof ITextEditor)) {
            chatView.postFromHandler("Active editor is not text-based – cannot insert template.");
            return null;
        }

        ITextEditor textEditor = (ITextEditor) editor;
        IDocument doc = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());

        // 1) Build the template list (for now: hard-coded demo)
        Map<String, String> templates = buildDemoTemplates();

        // 2) Show selection dialog
        Shell shell = HandlerUtil.getActiveShell(event);
        ElementListSelectionDialog dialog =
                new ElementListSelectionDialog(shell, new LabelProvider());
        dialog.setTitle("Insert Harmonized Template");
        dialog.setMessage("Select a template to insert at the cursor:");
        dialog.setElements(templates.keySet().toArray()); // keys as labels
        dialog.setMultipleSelection(false);

        if (dialog.open() != ElementListSelectionDialog.OK) {
            return null; // user cancelled
        }

        Object result = dialog.getFirstResult();
        if (!(result instanceof String)) {
            return null;
        }

        String selectedLabel = (String) result;
        String templateBody = templates.get(selectedLabel);
        if (templateBody == null) {
            chatView.postFromHandler("No template body found for selection.");
            return null;
        }

        // 3) Insert at caret position
        ITextSelection selection =
                (ITextSelection) textEditor.getSelectionProvider().getSelection();
        int offset = selection.getOffset();

        try {
            doc.replace(offset, 0, templateBody + System.lineSeparator());
            chatView.postFromHandler("Inserted template: " + selectedLabel);
        } catch (BadLocationException e) {
            throw new ExecutionException("Failed to insert template", e);
        }

        return null;
    }

    private Map<String, String> buildDemoTemplates() {
        Map<String, String> map = new LinkedHashMap<>();

        map.put("Class – Standard public class (ZCL_*, Z_* method)",
                getClassTemplate("ZCL_MY_CLASS"));
        map.put("Method – Z_ method skeleton",
                getMethodTemplate("Z_DO_SOMETHING"));
        map.put("SELECT – Safe SELECT without SELECT *",
                getSelectTemplate());

        // Later, replace this with templates loaded from backend:
        // GET /api/projects/{id}/templates -> map.put(label, body)

        return map;
    }

    private String getClassTemplate(String className) {
        return ""
            + "CLASS " + className + " DEFINITION\n"
            + "  PUBLIC\n"
            + "  CREATE PUBLIC.\n"
            + "  PUBLIC SECTION.\n"
            + "    METHODS Z_DO_SOMETHING.\n"
            + "  PROTECTED SECTION.\n"
            + "  PRIVATE SECTION.\n"
            + "ENDCLASS.\n"
            + "\n"
            + "CLASS " + className + " IMPLEMENTATION.\n"
            + "  METHOD Z_DO_SOMETHING.\n"
            + "    \" TODO: implement\n"
            + "  ENDMETHOD.\n"
            + "ENDCLASS.\n";
    }

    private String getSelectTemplate() {
        return ""
            + "SELECT field1 field2\n"
            + "  FROM ztable\n"
            + "  INTO TABLE @DATA(lt_result)\n"
            + "  WHERE key_field = @lv_key.\n";
    }

    private String getMethodTemplate(String methodName) {
        return ""
            + "METHOD " + methodName + ".\n"
            + "  \" TODO: implement logic\n"
            + "ENDMETHOD.\n";
    }
}
