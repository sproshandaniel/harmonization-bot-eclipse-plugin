package com.zalaris.harmonization.bot.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

import com.zalaris.harmonization.bot.views.chatView;

public class InsertClassTemplateHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {

        IEditorPart editor = HandlerUtil.getActiveEditor(event);
        if (!(editor instanceof ITextEditor)) {
            chatView.postFromHandler("Active editor is not text-based – cannot insert template.");
            return null;
        }

        ITextEditor textEditor = (ITextEditor) editor;
        IDocument doc = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());

        ISelection selection = textEditor.getSelectionProvider().getSelection();
        int offset = 0;
        if (selection instanceof ITextSelection) {
            offset = ((ITextSelection) selection).getOffset();
        }

        String template = getClassTemplate("ZCL_MY_CLASS");

        try {
            doc.replace(offset, 0, template + System.lineSeparator());
            chatView.postFromHandler("Inserted harmonized class template (ZCL_*, Z_* method).");
        } catch (BadLocationException e) {
            throw new ExecutionException("Failed to insert template", e);
        }

        return null;
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
}
