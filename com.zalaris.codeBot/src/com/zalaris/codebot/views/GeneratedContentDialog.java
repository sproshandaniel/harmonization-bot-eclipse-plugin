package com.zalaris.codebot.views;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class GeneratedContentDialog extends Dialog {

    public static final int ACTION_UPLOAD = IDialogConstants.CLIENT_ID + 1;
    public static final int ACTION_SAVE_NEW = IDialogConstants.CLIENT_ID + 2;
    public static final int ACTION_CLOSE = IDialogConstants.CLOSE_ID;

    private final String generatedContent;

    public GeneratedContentDialog(Shell parentShell, String generatedContent) {
        super(parentShell);
        this.generatedContent = generatedContent == null ? "" : generatedContent;
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        Composite container = new Composite(area, SWT.NONE);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        container.setLayout(new GridLayout(1, false));

        Label label = new Label(container, SWT.NONE);
        label.setText("Generated change summary and pseudocode:");

        Text preview = new Text(container, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.H_SCROLL);
        preview.setEditable(false);
        preview.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        preview.setText(generatedContent);

        GridData data = (GridData) preview.getLayoutData();
        data.widthHint = 820;
        data.heightHint = 420;

        return area;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, ACTION_UPLOAD, "Upload Document", false);
        createButton(parent, ACTION_SAVE_NEW, "Save in New Document", false);
        createButton(parent, ACTION_CLOSE, IDialogConstants.CLOSE_LABEL, true);
    }

    @Override
    protected void buttonPressed(int buttonId) {
        if (buttonId == ACTION_UPLOAD || buttonId == ACTION_SAVE_NEW || buttonId == ACTION_CLOSE) {
            setReturnCode(buttonId);
            close();
            return;
        }
        super.buttonPressed(buttonId);
    }
}
