package com.zalaris.codebot.views;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

public class TechnicalDocumentDialog extends Dialog {

    private static final int SAVE_LOCAL_BUTTON_ID = IDialogConstants.CLIENT_ID + 1;
    private static final int PASTE_GENERATED_BUTTON_ID = IDialogConstants.CLIENT_ID + 2;
    private static final int CLOSE_BUTTON_ID = IDialogConstants.CLOSE_ID;
    private final String objectName;
    private final String initialDocument;
    private final String generatedContent;
    private Text documentText;
    private boolean saved;

    public TechnicalDocumentDialog(
            Shell parentShell,
            String objectName,
            String initialDocument,
            String generatedContent) {
        super(parentShell);
        this.objectName = objectName == null ? "ADT_OBJECT" : objectName;
        this.initialDocument = initialDocument == null ? "" : initialDocument;
        this.generatedContent = generatedContent == null ? "" : generatedContent;
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
        if (!generatedContent.isBlank()) {
            createButton(parent, PASTE_GENERATED_BUTTON_ID, "Paste Generated Content", false);
        }
        createButton(parent, SAVE_LOCAL_BUTTON_ID, "Save Locally", false);
        createButton(parent, CLOSE_BUTTON_ID, IDialogConstants.CLOSE_LABEL, true);
        updateCloseButtonState();
    }

    @Override
    protected void buttonPressed(int buttonId) {
        if (buttonId == PASTE_GENERATED_BUTTON_ID) {
            pasteGeneratedAtCursor();
            return;
        }
        if (buttonId == SAVE_LOCAL_BUTTON_ID) {
            saveLocally();
            return;
        }
        if (buttonId == CLOSE_BUTTON_ID && !saved) {
            return;
        }
        super.buttonPressed(buttonId);
    }

    private void saveLocally() {
        FileDialog dialog = new FileDialog(getShell(), SWT.SAVE);
        dialog.setText("Save Technical Document");
        dialog.setFilterExtensions(new String[] { "*.docx", "*.doc", "*.md", "*.txt", "*.*" });
        dialog.setFileName(safeFileName(this.objectName + "-technical-design.docx"));
        String selected = dialog.open();
        if (selected == null || selected.isBlank()) {
            return;
        }
        try {
            Path target = Path.of(selected);
            String lower = selected.toLowerCase();
            if (lower.endsWith(".docx")) {
                writeSimpleDocx(target, currentDocument());
            } else {
                // .doc, .md, .txt and fallback are saved as UTF-8 text.
                Files.writeString(target, currentDocument(), StandardCharsets.UTF_8);
            }
            saved = true;
            updateCloseButtonState();
            MessageDialog.openInformation(getShell(), "Technical Document", "Document saved to:\n" + selected);
        } catch (Exception ex) {
            MessageDialog.openError(getShell(), "Save Failed", ex.getMessage());
        }
    }

    private void updateCloseButtonState() {
        if (getButton(CLOSE_BUTTON_ID) != null && !getButton(CLOSE_BUTTON_ID).isDisposed()) {
            getButton(CLOSE_BUTTON_ID).setEnabled(saved);
        }
    }

    private void writeSimpleDocx(Path target, String content) throws Exception {
        String text = content == null ? "" : content;
        String documentXml = buildDocumentXml(text);

        try (OutputStream out = Files.newOutputStream(target);
                ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            putZipEntry(zip, "[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                            + "<Override PartName=\"/word/document.xml\" "
                            + "ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                            + "</Types>");

            putZipEntry(zip, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                            + "<Relationship Id=\"rId1\" "
                            + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" "
                            + "Target=\"word/document.xml\"/>"
                            + "</Relationships>");

            putZipEntry(zip, "word/document.xml", documentXml);
        }
    }

    private void putZipEntry(ZipOutputStream zip, String name, String xml) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write((xml == null ? "" : xml).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String buildDocumentXml(String content) {
        String[] lines = (content == null ? "" : content).split("\\R", -1);
        StringBuilder body = new StringBuilder();
        for (String line : lines) {
            body.append("<w:p><w:r><w:t xml:space=\"preserve\">")
                    .append(escapeXml(line))
                    .append("</w:t></w:r></w:p>");
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + "<w:body>" + body + "<w:sectPr/></w:body></w:document>";
    }

    private String escapeXml(String value) {
        String text = value == null ? "" : value;
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private void pasteGeneratedAtCursor() {
        if (documentText == null || documentText.isDisposed() || generatedContent.isBlank()) {
            return;
        }
        String insertText = generatedContent.trim();
        int offset = documentText.getCaretPosition();
        String current = documentText.getText();
        boolean addLeadingBreak = offset > 0 && !current.substring(0, offset).endsWith("\n");
        boolean addTrailingBreak = offset < current.length() && !current.substring(offset).startsWith("\n");
        StringBuilder payload = new StringBuilder();
        if (addLeadingBreak) {
            payload.append(System.lineSeparator());
        }
        payload.append(insertText);
        if (addTrailingBreak) {
            payload.append(System.lineSeparator());
        }
        documentText.insert(payload.toString());
        documentText.setFocus();
    }

    private String currentDocument() {
        return documentText == null ? "" : documentText.getText();
    }

    private String safeFileName(String source) {
        String clean = source == null ? "" : source.replaceAll("[^A-Za-z0-9._-]+", "-");
        clean = clean.replaceAll("^-+", "").replaceAll("-+$", "");
        return clean.isBlank() ? "technical-design.md" : clean;
    }

    public boolean wasSaved() {
        return saved;
    }
}
