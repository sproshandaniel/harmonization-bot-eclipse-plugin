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

public class CodeExplanationDialog extends Dialog {

    private final String objectName;
    private final String explanation;

    public CodeExplanationDialog(Shell parentShell, String objectName, String explanation) {
        super(parentShell);
        this.objectName = (objectName == null || objectName.isBlank()) ? "ADT_OBJECT" : objectName;
        this.explanation = explanation == null ? "" : explanation;
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        Composite container = new Composite(area, SWT.NONE);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        container.setLayout(new GridLayout(1, false));

        Label label = new Label(container, SWT.NONE);
        label.setText("ABAP explanation for " + objectName + ":");

        Text content = new Text(container, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.H_SCROLL);
        content.setEditable(false);
        content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        content.setText(explanation);

        GridData data = (GridData) content.getLayoutData();
        data.widthHint = 900;
        data.heightHint = 520;
        return area;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.CLOSE_ID, IDialogConstants.CLOSE_LABEL, true);
    }

    @Override
    protected void buttonPressed(int buttonId) {
        if (buttonId == IDialogConstants.CLOSE_ID) {
            close();
            return;
        }
        super.buttonPressed(buttonId);
    }
}
