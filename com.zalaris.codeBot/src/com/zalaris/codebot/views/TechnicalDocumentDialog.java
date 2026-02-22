package com.zalaris.codebot.views;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.zalaris.codebot.api.BackendApiClient;

public class TechnicalDocumentDialog extends Dialog {

    private static final int SAVE_LOCAL_BUTTON_ID = IDialogConstants.CLIENT_ID + 1;
    private static final int SAVE_BACKEND_BUTTON_ID = IDialogConstants.CLIENT_ID + 2;

    private final BackendApiClient apiClient;
    private final String objectName;
    private final String title;
    private final String initialDocument;
    private Text documentText;

    public TechnicalDocumentDialog(
            Shell parentShell,
            BackendApiClient apiClient,
            String objectName,
            String title,
            String initialDocument) {
        super(parentShell);
        this.apiClient = apiClient;
        this.objectName = objectName == null ? "ADT_OBJECT" : objectName;
        this.title = (title == null || title.isBlank()) ? "Technical Design" : title;
        this.initialDocument = initialDocument == null ? "" : initialDocument;
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        Composite container = new Composite(area, SWT.NONE);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        container.setLayout(new GridLayout(1, false));

        Label titleLabel = new Label(container, SWT.NONE);
        titleLabel.setText("Review and edit the generated technical document before saving.");

        documentText = new Text(container, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.H_SCROLL);
        GridData docData = new GridData(SWT.FILL, SWT.FILL, true, true);
        docData.widthHint = 820;
        docData.heightHint = 520;
        documentText.setLayoutData(docData);
        documentText.setText(initialDocument);

        return area;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, SAVE_LOCAL_BUTTON_ID, "Save Locally", false);
        createButton(parent, SAVE_BACKEND_BUTTON_ID, "Save To Backend", false);
        createButton(parent, IDialogConstants.CLOSE_ID, IDialogConstants.CLOSE_LABEL, true);
    }

    @Override
    protected void buttonPressed(int buttonId) {
        if (buttonId == SAVE_LOCAL_BUTTON_ID) {
            saveLocally();
            return;
        }
        if (buttonId == SAVE_BACKEND_BUTTON_ID) {
            saveToBackend();
            return;
        }
        super.buttonPressed(buttonId);
    }

    private void saveLocally() {
        FileDialog dialog = new FileDialog(getShell(), SWT.SAVE);
        dialog.setText("Save Technical Document");
        dialog.setFilterExtensions(new String[] { "*.md", "*.txt", "*.*" });
        dialog.setFileName(safeFileName(this.objectName + "-technical-design.md"));
        String selected = dialog.open();
        if (selected == null || selected.isBlank()) {
            return;
        }
        try {
            Files.writeString(Path.of(selected), currentDocument(), StandardCharsets.UTF_8);
            MessageDialog.openInformation(getShell(), "Technical Document", "Document saved to:\n" + selected);
        } catch (Exception ex) {
            MessageDialog.openError(getShell(), "Save Failed", ex.getMessage());
        }
    }

    private void saveToBackend() {
        try {
            Map<String, Object> response = apiClient.saveTechnicalDoc(title, currentDocument(), objectName);
            Object rawPath = response.get("saved_path");
            String savedPath = rawPath == null ? "" : String.valueOf(rawPath);
            if (savedPath.isBlank()) {
                MessageDialog.openInformation(getShell(), "Technical Document", "Document saved via backend.");
                return;
            }
            MessageDialog.openInformation(getShell(), "Technical Document", "Document saved via backend:\n" + savedPath);
        } catch (Exception ex) {
            MessageDialog.openError(getShell(), "Backend Save Failed", ex.getMessage());
        }
    }

    private String currentDocument() {
        return documentText == null ? "" : documentText.getText();
    }

    private String safeFileName(String source) {
        String clean = source == null ? "" : source.replaceAll("[^A-Za-z0-9._-]+", "-");
        clean = clean.replaceAll("^-+", "").replaceAll("-+$", "");
        return clean.isBlank() ? "technical-design.md" : clean;
    }
}
