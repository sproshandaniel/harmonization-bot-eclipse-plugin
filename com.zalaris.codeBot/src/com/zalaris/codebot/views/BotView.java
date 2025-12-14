package com.zalaris.codebot.views;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

import jakarta.inject.Inject;
import org.eclipse.ui.IWorkbench;
import org.eclipse.jface.dialogs.MessageDialog;

import com.zalaris.codebot.bot.BotResponse;
import com.zalaris.codebot.bot.SimpleRuleBot;
import com.zalaris.codebot.bot.BotResponse.RuleViolation;
import com.zalaris.codebot.adt.AbapEditorUtil;

/**
 * CodeBot view – validate ABAP code and provide templates.
 */
public class BotView extends ViewPart {

    public static final String ID = "com.zalaris.codebot.views.BotView";

    @Inject
    IWorkbench workbench;

    private Text questionText;
    private Text answerText;
    private Button askButton;
    private Button pasteButton;

    // SWT violations list – fully qualified to avoid collision with java.util.List
    private org.eclipse.swt.widgets.List violationsList;

    private final SimpleRuleBot bot = new SimpleRuleBot();
    private BotResponse lastResponse; // remember last response, including violations or template

    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout(1, false));

        Label title = new Label(parent, SWT.NONE);
        title.setText("CodeBot – ABAP Assistant");

        Label qLabel = new Label(parent, SWT.NONE);
        qLabel.setText("Ask ZalBot (e.g., 'validate current object', 'template for select single'):");

        questionText = new Text(parent, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        GridData qData = new GridData(SWT.FILL, SWT.FILL, true, false);
        qData.heightHint = 60;
        questionText.setLayoutData(qData);

        askButton = new Button(parent, SWT.PUSH);
        askButton.setText("Ask");
        askButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

        // Violations list label
        Label vLabel = new Label(parent, SWT.NONE);
        vLabel.setText("Rule violations (if any):");

        // List of violations
        violationsList =
            new org.eclipse.swt.widgets.List(parent, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);
        GridData vData = new GridData(SWT.FILL, SWT.FILL, true, false);
        vData.heightHint = 100;
        violationsList.setLayoutData(vData);

        Label aLabel = new Label(parent, SWT.NONE);
        aLabel.setText("Details / Answer:");

        answerText = new Text(parent,
                SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.READ_ONLY);
        GridData aData = new GridData(SWT.FILL, SWT.FILL, true, true);
        aData.heightHint = 200;
        answerText.setLayoutData(aData);

        pasteButton = new Button(parent, SWT.PUSH);
        pasteButton.setText("Paste template into editor");
        pasteButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        pasteButton.setEnabled(false); // only enabled when a template is available

        hookListeners();
    }

    private void hookListeners() {
        askButton.addListener(SWT.Selection, e -> handleAsk());
        pasteButton.addListener(SWT.Selection, e -> handlePaste());

        // When a violation is selected, show its details in the answer area
        violationsList.addListener(SWT.Selection, e -> handleViolationSelection());
    }

    private void handleAsk() {
        String question = questionText.getText();
        lastResponse = bot.reply(question);

        // Clear old violations on each request
        violationsList.removeAll();

        if (lastResponse == null) {
            answerText.setText("No response from bot.");
            pasteButton.setEnabled(false);
            return;
        }

        // Branch based on response kind
        if (lastResponse.getKind() == BotResponse.Kind.TEMPLATE_SUGGESTION
                && lastResponse.hasTemplate()) {

            // Template response: show message + code, enable paste
            String msg = lastResponse.getMessage()
                       + "\n\n--- Suggested Template ---\n"
                       + lastResponse.getTemplateCode();
            answerText.setText(msg);
            pasteButton.setEnabled(true);

        } else if (lastResponse.getKind() == BotResponse.Kind.VALIDATION_RESULT) {
            // Validation result: show summary and violations
            StringBuilder sb = new StringBuilder();

            if (lastResponse.hasViolations()) {
                List<RuleViolation> vs = lastResponse.getViolations();
                sb.append(lastResponse.getMessage())
                  .append("\n\n")
                  .append("Total violations: ").append(vs.size())
                  .append("\n")
                  .append("Select a violation in the list above to see details.\n");

                // Populate SWT list
                for (RuleViolation v : vs) {
                    String line = String.format(
                        "Line %d: [%s] %s",
                        v.getLine(),
                        v.getRuleId(),
                        v.getTitle()
                    );
                    violationsList.add(line);
                }

                // Auto-select first violation and show its details
                if (!vs.isEmpty()) {
                    violationsList.select(0);
                    showViolationDetails(vs.get(0));
                }

            } else {
                sb.append(lastResponse.getMessage())
                  .append("\n\nNo harmonization rule violations detected.");
            }

            // If we didn't already show details (e.g. no violations), show summary
            if (!lastResponse.hasViolations()) {
                answerText.setText(sb.toString());
            }

            // For validation responses we do not paste templates
            pasteButton.setEnabled(false);

        } else {
            // Generic info response
            answerText.setText(lastResponse.getMessage());
            pasteButton.setEnabled(false);
        }
    }

    private void handleViolationSelection() {
        if (lastResponse == null || !lastResponse.hasViolations()) {
            return;
        }
        int index = violationsList.getSelectionIndex();
        if (index < 0) {
            return;
        }

        List<RuleViolation> vs = lastResponse.getViolations();
        if (index >= vs.size()) {
            return;
        }

        RuleViolation v = vs.get(index);
        showViolationDetails(v);
    }

    private void showViolationDetails(RuleViolation v) {
        StringBuilder sb = new StringBuilder();

        sb.append("Rule ID : ").append(v.getRuleId()).append("\n")
          .append("Title   : ").append(v.getTitle()).append("\n")
          .append("Pack    : ").append(v.getRulePackName()).append("\n")
          .append("Project : ").append(v.getProjectName()).append("\n")
          .append("Line    : ").append(v.getLine()).append("\n\n")
          .append("Description:\n")
          .append(v.getDescription()).append("\n\n");

        if (v.getCorrectCode() != null && !v.getCorrectCode().isEmpty()) {
            sb.append("--- Suggested correction ---\n")
              .append(v.getCorrectCode()).append("\n");
        }

        answerText.setText(sb.toString());
    }

    private void handlePaste() {
        if (lastResponse == null || !lastResponse.hasTemplate()) {
            MessageDialog.openInformation(getSite().getShell(),
                    "CodeBot",
                    "There is no template to paste. Ask for a template first.");
            return;
        }

        boolean ok = AbapEditorUtil.insertTextAtCursor(lastResponse.getTemplateCode());
        if (!ok) {
            MessageDialog.openError(getSite().getShell(),
                    "CodeBot",
                    "Could not insert template. Please ensure an ABAP editor is active.");
        } else {
            MessageDialog.openInformation(getSite().getShell(),
                    "CodeBot",
                    "Template inserted into the active editor.");
        }
    }

    @Override
    public void setFocus() {
        if (questionText != null && !questionText.isDisposed()) {
            questionText.setFocus();
        }
    }
}