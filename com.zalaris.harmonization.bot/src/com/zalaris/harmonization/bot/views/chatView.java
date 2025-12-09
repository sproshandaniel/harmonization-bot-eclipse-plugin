package com.zalaris.harmonization.bot.views;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

public class chatView extends ViewPart {

    public static final String ID = "com.company.harmonizationbot.views.ChatView";

    private static chatView instance;
    
    public chatView() {
        instance = this;              // <─ remember latest instance
    }
    
    private Text chatArea;
    private Text inputField;
    private Button sendButton;

    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout(1, false));

        chatArea = new Text(parent,
                SWT.MULTI | SWT.BORDER | SWT.WRAP | SWT.V_SCROLL | SWT.READ_ONLY);
        GridData chatData = new GridData(SWT.FILL, SWT.FILL, true, true);
        chatData.heightHint = 200;
        chatArea.setLayoutData(chatData);

        Composite inputBar = new Composite(parent, SWT.NONE);
        inputBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        inputBar.setLayout(new GridLayout(2, false));

        inputField = new Text(inputBar, SWT.SINGLE | SWT.BORDER);
        inputField.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        sendButton = new Button(inputBar, SWT.PUSH);
        sendButton.setText("Send");

        hookListeners();
        showWelcomeMessage();
    }

    private void hookListeners() {
        sendButton.addListener(SWT.Selection, e -> handleUserInput());
        inputField.addListener(SWT.DefaultSelection, e -> handleUserInput());
    }

    private void handleUserInput() {
        String userText = inputField.getText().trim();
        if (userText.isEmpty()) {
            return;
        }

        appendToChat("You: " + userText);
        inputField.setText("");

        // demo response
        appendToChat("Bot: (demo) I received: " + userText);
    }

    private void showWelcomeMessage() {
        appendToChat("Bot: Welcome to the Harmonization Bot (demo view).");
        appendToChat("Bot: Type something and press Enter or click Send.");
    }

    private void appendToChat(String text) {
        chatArea.append(text + System.lineSeparator());
    }


    // static helper for other classes
    public static void postFromHandler(String text) {
        if (instance != null) {
            instance.appendToChat("Bot: " + text);
        }
    }

    public static void postValidationSummary(java.util.List<String> issues) {
        if (instance == null) return;
        if (issues.isEmpty()) {
            instance.appendToChat("Bot: ✅ No harmonization issues in current object.");
        } else {
            instance.appendToChat("Bot: ❌ Harmonization issues:");
            for (String issue : issues) {
                instance.appendToChat("  - " + issue);
            }
        }
    }
    
    @Override
    public void setFocus() {
        inputField.setFocus();
    }
}

