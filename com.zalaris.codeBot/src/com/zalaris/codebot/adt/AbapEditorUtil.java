package com.zalaris.codebot.adt;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

public class AbapEditorUtil {

    /**
     * Try to adapt the active editor to an ITextEditor (works for ADT editors like ProgramEditor).
     */
    private static ITextEditor getActiveTextEditor() {
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window == null) {
            System.out.println("[CodeBot Debug] getActiveTextEditor: no active workbench window.");
            return null;
        }

        IWorkbenchPage page = window.getActivePage();
        if (page == null) {
            System.out.println("[CodeBot Debug] getActiveTextEditor: no active page.");
            return null;
        }

        IEditorPart editorPart = page.getActiveEditor();
        if (editorPart == null) {
            System.out.println("[CodeBot Debug] getActiveTextEditor: no active editor.");
            return null;
        }

        System.out.println("[CodeBot Debug] Active editor class: " + editorPart.getClass().getName());

        if (editorPart instanceof ITextEditor) {
            return (ITextEditor) editorPart;
        }

        if (editorPart instanceof IAdaptable) {
            ITextEditor adapted = ((IAdaptable) editorPart).getAdapter(ITextEditor.class);
            if (adapted != null) {
                System.out.println("[CodeBot Debug] getActiveTextEditor: obtained ITextEditor via adapter.");
                return adapted;
            }
        }

        System.out.println("[CodeBot Debug] getActiveTextEditor: could not adapt to ITextEditor.");
        return null;
    }

    public static String getActiveEditorNameOrDefault() {
        try {
            IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (window == null) {
                return "ADT_OBJECT";
            }
            IWorkbenchPage page = window.getActivePage();
            if (page == null) {
                return "ADT_OBJECT";
            }
            IEditorPart editorPart = page.getActiveEditor();
            if (editorPart == null) {
                return "ADT_OBJECT";
            }
            String title = editorPart.getTitle();
            if (title == null || title.trim().isEmpty()) {
                return "ADT_OBJECT";
            }
            return title.trim();
        } catch (Exception ex) {
            return "ADT_OBJECT";
        }
    }

    /**
     * Read full content of the active ABAP/text editor.
     */
    public static String getActiveEditorContentOrEmpty() {
        try {
            ITextEditor textEditor = getActiveTextEditor();
            if (textEditor == null) {
                return "";
            }

            IDocument document =
                    textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());

            if (document == null) {
                System.out.println("[CodeBot Debug] getActiveEditorContentOrEmpty: document is null.");
                return "";
            }

            String content = document.get();
            if (content == null) {
                System.out.println("[CodeBot Debug] getActiveEditorContentOrEmpty: content is null.");
                return "";
            }

            System.out.println("[CodeBot Debug] Successfully read editor content, length=" + content.length());
            return content;

        } catch (Exception e) {
            System.out.println("[CodeBot Debug] Exception while reading editor content: " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }

    /**
     * Insert the given text at the current cursor position (or replace the selection)
     * in the active ABAP/text editor. Returns true if insertion succeeded.
     */
    public static boolean insertTextAtCursor(String text) {
        try {
            ITextEditor textEditor = getActiveTextEditor();
            if (textEditor == null) {
                System.out.println("[CodeBot Debug] insertTextAtCursor: no ITextEditor available.");
                return false;
            }

            IDocument document =
                    textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
            if (document == null) {
                System.out.println("[CodeBot Debug] insertTextAtCursor: document is null.");
                return false;
            }

            ISelectionProvider selectionProvider = textEditor.getSelectionProvider();
            ISelection selection =
                    (selectionProvider != null) ? selectionProvider.getSelection() : null;

            int offset = document.getLength();
            int length = 0;

            if (selection instanceof ITextSelection) {
                ITextSelection textSel = (ITextSelection) selection;
                offset = textSel.getOffset();
                length = textSel.getLength();
            }

            document.replace(offset, length, text);
            return true;

        } catch (Exception e) {
            System.out.println("[CodeBot Debug] insertTextAtCursor: exception " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Navigate the active ABAP/text editor to a 1-based line number.
     */
    public static boolean goToLine(int lineNumber) {
        if (lineNumber < 1) {
            return false;
        }
        try {
            ITextEditor textEditor = getActiveTextEditor();
            if (textEditor == null) {
                System.out.println("[CodeBot Debug] goToLine: no ITextEditor available.");
                return false;
            }

            IDocument document =
                    textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
            if (document == null) {
                System.out.println("[CodeBot Debug] goToLine: document is null.");
                return false;
            }

            int targetLineIndex = lineNumber - 1;
            if (targetLineIndex >= document.getNumberOfLines()) {
                targetLineIndex = Math.max(0, document.getNumberOfLines() - 1);
            }

            int offset = document.getLineOffset(targetLineIndex);
            int length = document.getLineLength(targetLineIndex);
            textEditor.selectAndReveal(offset, Math.max(0, length));
            return true;
        } catch (BadLocationException e) {
            System.out.println("[CodeBot Debug] goToLine: invalid line " + lineNumber + " - " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.out.println("[CodeBot Debug] goToLine: exception " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static IResource getActiveEditorResource() {
        try {
            IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (window == null) {
                return null;
            }
            IWorkbenchPage page = window.getActivePage();
            if (page == null) {
                return null;
            }
            IEditorPart editorPart = page.getActiveEditor();
            if (editorPart == null) {
                return null;
            }
            IEditorInput input = editorPart.getEditorInput();
            if (input instanceof IAdaptable) {
                Object adapted = ((IAdaptable) input).getAdapter(IResource.class);
                if (adapted instanceof IResource) {
                    return (IResource) adapted;
                }
            }
            if (editorPart instanceof IAdaptable) {
                Object adapted = ((IAdaptable) editorPart).getAdapter(IResource.class);
                if (adapted instanceof IResource) {
                    return (IResource) adapted;
                }
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }
}
