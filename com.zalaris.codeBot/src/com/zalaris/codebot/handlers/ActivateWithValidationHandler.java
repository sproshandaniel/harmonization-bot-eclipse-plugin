package com.zalaris.codebot.handlers;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

import com.zalaris.codebot.adt.AbapEditorUtil;
import com.zalaris.codebot.api.BackendApiClient;
import com.zalaris.codebot.bot.BotResponse;
import com.zalaris.codebot.bot.BotResponse.RuleViolation;
import com.zalaris.codebot.bot.SimpleRuleBot;
import com.zalaris.codebot.governance.ViolationGovernanceService;
import com.zalaris.codebot.util.UserRoleUtil;
import com.zalaris.codebot.views.GeneratedContentDialog;
import com.zalaris.codebot.views.TechnicalDocumentDialog;

/**
 * Command handler that performs validation before allowing activation.
 * If MAJOR validation fails, activation is blocked.
 */
public class ActivateWithValidationHandler extends AbstractHandler {

    private final SimpleRuleBot bot = new SimpleRuleBot();
    private final BackendApiClient apiClient = new BackendApiClient();

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        Shell shell = HandlerUtil.getActiveShell(event);
        if (UserRoleUtil.isValidationExemptRole()) {
            performRealActivation();
            MessageDialog.openInformation(shell, "Activation", "Activation completed successfully.");
            return null;
        }

        BotResponse response = bot.validateCurrentEditor();
        List<RuleViolation> violations = response.getViolations();
        ViolationGovernanceService.updateFromValidation(
                AbapEditorUtil.getActiveEditorNameOrDefault(),
                violations);

        if (hasMajor(violations)) {
            MessageDialog.openError(shell, "Activation blocked", response.getMessage());
            return null;
        }

        performRealActivation();

        MessageDialog.openInformation(shell, "Activation", "Activation completed successfully.");
        return null;
    }

    private boolean hasMajor(List<RuleViolation> violations) {
        if (violations == null || violations.isEmpty()) {
            return false;
        }
        for (RuleViolation v : violations) {
            String severity = (v.getSeverity() == null ? "" : v.getSeverity()).toUpperCase(Locale.ROOT);
            if ("MAJOR".equals(severity)) {
                return true;
            }
        }
        return false;
    }

    private void performRealActivation() {
        // Placeholder where you'll call the ABAP activation service.
    }

    public boolean promptTechnicalDocumentFlow(Shell shell, List<RuleViolation> violations) {
        return promptTechnicalDocumentFlow(shell, violations, false);
    }

    public boolean promptTechnicalDocumentFlow(Shell shell, List<RuleViolation> violations, boolean mandatory) {
        MessageDialog proceedDialog = new MessageDialog(
                shell,
                "Technical Documentation",
                null,
                "Click proceed to complete technical documentation.",
                MessageDialog.INFORMATION,
                new String[] { "Proceed", "Cancel" },
                0);
        boolean generate = proceedDialog.open() == 0;
        if (!generate) {
            return false;
        }

        String objectName = AbapEditorUtil.getActiveEditorNameOrDefault();
        String code = AbapEditorUtil.getActiveEditorContentOrEmpty();
        if (code == null || code.isBlank()) {
            code = AbapEditorUtil.getLastSnapshotCodeOrEmpty();
            String snapObject = AbapEditorUtil.getLastSnapshotObjectNameOrDefault();
            if ((objectName == null || objectName.isBlank() || "ADT_OBJECT".equalsIgnoreCase(objectName))
                    && snapObject != null
                    && !snapObject.isBlank()) {
                objectName = snapObject;
            }
        }
        if (code == null || code.isBlank()) {
            MessageDialog.openError(
                    shell,
                    "Technical Documentation",
                    "No active ADT editor code was found. Open the ABAP object in ADT editor and try again.");
            return false;
        }
        String validationSummary = buildValidationSummary(violations);
        String changeSummary = "Auto-generated from current source changes and validation context.";

        try {
            Map<String, Object> response =
                    generateTechnicalDocWithProgress(shell, code, objectName, changeSummary, validationSummary);
            String generatedDocument = asString(response.get("document"), "");
            if (generatedDocument.isBlank()) {
                MessageDialog.openError(shell, "Technical Documentation", "Generated document was empty.");
                return false;
            }
            String generatedSummaryAndPseudocode = extractSummaryAndPseudocode(generatedDocument, code);

            Shell parent = shell != null ? shell : PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
            GeneratedContentDialog contentDialog = new GeneratedContentDialog(parent, generatedSummaryAndPseudocode);
            int action = contentDialog.open();
            if (action == GeneratedContentDialog.ACTION_CLOSE || action == Window.CANCEL) {
                return false;
            }

            String documentToEdit;
            String generatedForPaste = "";
            if (action == GeneratedContentDialog.ACTION_SAVE_NEW) {
                documentToEdit = generatedSummaryAndPseudocode;
            } else if (action == GeneratedContentDialog.ACTION_UPLOAD) {
                String existingDoc = loadExistingDocument(shell);
                if (existingDoc == null) {
                    return false;
                }
                documentToEdit = existingDoc;
                generatedForPaste = generatedSummaryAndPseudocode;
            } else {
                return false;
            }

            TechnicalDocumentDialog dialog = new TechnicalDocumentDialog(
                    parent,
                    objectName,
                    documentToEdit,
                    generatedForPaste);
            dialog.open();
            if (mandatory && !dialog.wasSaved()) {
                MessageDialog.openError(
                        shell,
                        "Technical Documentation Required",
                        "Documentation is mandatory for your role. Save the document before closing.");
                return false;
            }
            return dialog.wasSaved();
        } catch (Exception ex) {
            MessageDialog.openError(shell, "Technical Documentation", "Failed to generate/update document:\n" + ex.getMessage());
            return false;
        }
    }

    private Map<String, Object> generateTechnicalDocWithProgress(
            Shell shell,
            String code,
            String objectName,
            String changeSummary,
            String validationSummary) throws Exception {
        final Map<String, Object>[] holder = new Map[1];
        final Exception[] error = new Exception[1];

        ProgressMonitorDialog progress = new ProgressMonitorDialog(shell);
        IRunnableWithProgress task = (IProgressMonitor monitor) -> {
            monitor.beginTask("Generating technical documentation...", IProgressMonitor.UNKNOWN);
            try {
                holder[0] = apiClient.generateTechnicalDoc(code, objectName, changeSummary, validationSummary);
            } catch (Exception ex) {
                error[0] = ex;
            } finally {
                monitor.done();
            }
        };

        try {
            progress.run(true, false, task);
        } catch (InvocationTargetException | InterruptedException ex) {
            throw new Exception("Document generation was interrupted.", ex);
        }
        if (error[0] != null) {
            throw error[0];
        }
        return holder[0];
    }

    private String loadExistingDocument(Shell shell) throws Exception {
        FileDialog dialog = new FileDialog(shell, SWT.OPEN);
        dialog.setText("Upload Existing Technical Document");
        dialog.setFilterExtensions(new String[] { "*.docx", "*.md", "*.txt", "*.*" });
        String selected = dialog.open();
        if (selected == null || selected.isBlank()) {
            return null;
        }
        Path selectedPath = Path.of(selected);
        String lower = selected.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".docx")) {
            try {
                return extractTextFromDocx(selectedPath);
            } catch (Exception ex) {
                MessageDialog.openInformation(
                        shell,
                        "Technical Documentation",
                        "Uploaded Word document has no readable text. Continuing with an empty base document.");
                return "";
            }
        }
        return Files.readString(selectedPath, StandardCharsets.UTF_8);
    }

    private String extractTextFromDocx(Path path) throws Exception {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            ZipEntry documentXml = zip.getEntry("word/document.xml");
            if (documentXml == null) {
                throw new IllegalArgumentException("Invalid .docx file: missing word/document.xml");
            }
            String xml = readZipEntry(zip, documentXml);
            String text = xmlToPlainText(xml);
            if (text.isBlank()) {
                throw new IllegalArgumentException("Could not extract readable text from the selected .docx file.");
            }
            return text;
        }
    }

    private String readZipEntry(ZipFile zip, ZipEntry entry) throws Exception {
        StringBuilder out = new StringBuilder();
        try (InputStream input = zip.getInputStream(entry);
                InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
                BufferedReader buffered = new BufferedReader(reader)) {
            String line;
            while ((line = buffered.readLine()) != null) {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    private String xmlToPlainText(String xml) {
        if (xml == null || xml.isBlank()) {
            return "";
        }
        String text = xml;
        text = text.replaceAll("(?i)</w:p>", "\n");
        text = text.replaceAll("(?i)<w:tab[^>]*/>", "\t");
        text = text.replaceAll("(?i)<w:br[^>]*/>", "\n");
        text = text.replaceAll("<[^>]+>", " ");
        text = decodeXmlEntities(text);
        text = text.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
        text = text.replaceAll("\\n[ ]+", "\n");
        text = text.replaceAll("\\n{3,}", "\n\n");
        return text.trim();
    }

    private String decodeXmlEntities(String text) {
        return text.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'");
    }

    private String extractSummaryAndPseudocode(String generatedDocument, String code) {
        String purpose = extractSection(generatedDocument, "Purpose of Change");
        if (purpose.isBlank()) {
            purpose = extractSection(generatedDocument, "Purpose");
        }
        if (purpose.isBlank()) {
            purpose = extractSection(generatedDocument, "Short Change Summary");
        }
        if (purpose.isBlank()) {
            purpose = extractSection(generatedDocument, "Change Summary");
        }

        String flowchart = extractSection(generatedDocument, "Detailed Text Flowchart (Step-by-Step Execution Logic)");
        if (flowchart.isBlank()) {
            flowchart = extractSection(generatedDocument, "Step-by-Step Flowchart (Text)");
        }
        if (flowchart.isBlank()) {
            flowchart = extractSection(generatedDocument, "Pseudocode of Changes");
        }
        if (flowchart.isBlank()) {
            flowchart = extractSection(generatedDocument, "Pseudocode");
        }

        if (purpose.isBlank()) {
            purpose = derivePurposeFromCode(code);
        }
        if (flowchart.isBlank()) {
            flowchart = deriveFlowchartTextFromCode(code);
        }
        String mermaid = extractSection(generatedDocument, "Graphical Flowchart (Mermaid Diagram)");
        if (mermaid.isBlank()) {
            mermaid = extractSection(generatedDocument, "Graphical Flowchart");
        }
        if (mermaid.isBlank()) {
            mermaid = extractFirstMermaidBlock(generatedDocument);
        }
        if (mermaid.isBlank()) {
            mermaid = deriveMermaidFromCode(code);
        }

        StringBuilder out = new StringBuilder();
        out.append("## Purpose of Code").append(System.lineSeparator());
        out.append(purpose.isBlank()
                ? "Process ABAP business logic in the active editor object."
                : purpose.trim());
        out.append(System.lineSeparator()).append(System.lineSeparator());
        out.append("## Step-by-Step Flowchart (Text)").append(System.lineSeparator());
        out.append(flowchart.isBlank()
                ? "1. Read inputs\n2. Execute main processing\n3. Persist/return results"
                : flowchart.trim());
        out.append(System.lineSeparator()).append(System.lineSeparator());
        out.append("## Graphical Flowchart (Mermaid Diagram)").append(System.lineSeparator());
        out.append(mermaid.isBlank()
                ? "```mermaid\nflowchart TD\n    A[Start] --> B[Process]\n    B --> C[End]\n```"
                : mermaid.trim());
        return out.toString().trim();
    }

    private String derivePurposeFromCode(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        String[] lines = code.split("\\R");
        boolean hasSelect = false;
        boolean hasDelete = false;
        boolean hasAlv = false;
        boolean hasTryCatch = false;
        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            String upper = line.toUpperCase(Locale.ROOT);
            if (upper.startsWith("SELECT ")) {
                hasSelect = true;
            } else if (upper.contains("DELETE FROM ")) {
                hasDelete = true;
            } else if (upper.contains("CL_SALV_TABLE=>FACTORY") || upper.contains("LO_ALV->DISPLAY")) {
                hasAlv = true;
            } else if (upper.startsWith("TRY") || upper.startsWith("CATCH")) {
                hasTryCatch = true;
            }
        }
        StringBuilder out = new StringBuilder();
        if (hasSelect) {
            out.append("Reads work-schedule and employee-related records for validation. ");
        }
        if (hasDelete) {
            out.append("Removes eligible time-management entries based on holiday and rule checks. ");
        }
        if (hasAlv) {
            out.append("Builds ALV output to report processed schedules and deletions. ");
        }
        if (hasTryCatch) {
            out.append("Includes protected arithmetic handling to avoid overflow dumps.");
        }
        String result = out.toString().trim();
        return result.isEmpty() ? "Processes ABAP business logic for schedule-driven updates and reporting." : result;
    }

    private String deriveFlowchartTextFromCode(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        String[] lines = code.split("\\R");
        boolean hasSelect = false;
        boolean hasGroupBy = false;
        boolean hasLoop = false;
        boolean hasFunctionCall = false;
        boolean hasDelete = false;
        boolean hasAlv = false;
        boolean hasTryCatch = false;

        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            String upper = line.toUpperCase(Locale.ROOT);
            if (upper.startsWith("SELECT ")) {
                hasSelect = true;
            }
            if (upper.contains("GROUP BY")) {
                hasGroupBy = true;
            }
            if (upper.startsWith("LOOP AT ")) {
                hasLoop = true;
            }
            if (upper.startsWith("CALL FUNCTION")) {
                hasFunctionCall = true;
            }
            if (upper.contains("DELETE FROM ")) {
                hasDelete = true;
            }
            if (upper.contains("CL_SALV_TABLE=>FACTORY") || upper.contains("LO_ALV->DISPLAY")) {
                hasAlv = true;
            }
            if (upper.startsWith("TRY") || upper.startsWith("CATCH")) {
                hasTryCatch = true;
            }
        }

        List<String> steps = new java.util.ArrayList<>();
        int stepNo = 1;
        steps.add(stepNo++ + ". Start and initialize counters/output structures.");
        if (hasSelect) {
            steps.add(stepNo++ + ". Read schedule, holiday, and employee master data from tables.");
        }
        if (hasGroupBy) {
            steps.add(stepNo++ + ". Group schedules by keys and process each schedule group.");
        }
        if (hasLoop) {
            steps.add(stepNo++ + ". Iterate holidays and employees, derive valid holiday dates, and apply checks.");
        }
        if (hasFunctionCall) {
            steps.add(stepNo++ + ". Call HR time-data function to evaluate availability for the date.");
        }
        if (hasDelete) {
            steps.add(stepNo++ + ". Delete matching records when business conditions are satisfied and update counters.");
        }
        if (hasTryCatch) {
            steps.add(stepNo++ + ". Protect arithmetic operations with TRY/CATCH handling.");
        }
        if (hasAlv) {
            steps.add(stepNo++ + ". Build and display ALV report with summary and sorted results.");
        }
        steps.add(stepNo + ". End processing.");
        return String.join(System.lineSeparator(), steps);
    }

    private String extractFirstMermaidBlock(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        String lower = markdown.toLowerCase(Locale.ROOT);
        int start = lower.indexOf("```mermaid");
        if (start < 0) {
            return "";
        }
        int end = lower.indexOf("```", start + "```mermaid".length());
        if (end < 0) {
            return markdown.substring(start).trim();
        }
        return markdown.substring(start, end + 3).trim();
    }

    private String deriveMermaidFromCode(String code) {
        boolean hasSelect = false;
        boolean hasLoop = false;
        boolean hasDelete = false;
        boolean hasTryCatch = false;
        if (code != null && !code.isBlank()) {
            for (String raw : code.split("\\R")) {
                String upper = (raw == null ? "" : raw.trim()).toUpperCase(Locale.ROOT);
                if (upper.startsWith("SELECT ")) {
                    hasSelect = true;
                }
                if (upper.startsWith("LOOP AT ")) {
                    hasLoop = true;
                }
                if (upper.contains("DELETE FROM ")) {
                    hasDelete = true;
                }
                if (upper.startsWith("TRY") || upper.startsWith("CATCH")) {
                    hasTryCatch = true;
                }
            }
        }

        StringBuilder m = new StringBuilder();
        m.append("```mermaid").append(System.lineSeparator());
        m.append("flowchart TD").append(System.lineSeparator());
        m.append("    A[Start] --> B[Initialize counters and output]").append(System.lineSeparator());
        if (hasSelect) {
            m.append("    B --> C[Read schedule/holiday/employee data]").append(System.lineSeparator());
        } else {
            m.append("    B --> C[Read input/context data]").append(System.lineSeparator());
        }
        if (hasLoop) {
            m.append("    C --> D{More schedule/employee items?}").append(System.lineSeparator());
            m.append("    D -- Yes --> E[Process current item]").append(System.lineSeparator());
            if (hasDelete) {
                m.append("    E --> F{Delete condition met?}").append(System.lineSeparator());
                m.append("    F -- Yes --> G[Delete DB record and update counters]").append(System.lineSeparator());
                m.append("    F -- No --> H[Skip delete]").append(System.lineSeparator());
                m.append("    G --> D").append(System.lineSeparator());
                m.append("    H --> D").append(System.lineSeparator());
            } else {
                m.append("    E --> D").append(System.lineSeparator());
            }
            m.append("    D -- No --> I[Prepare output]").append(System.lineSeparator());
        } else {
            m.append("    C --> I[Prepare output]").append(System.lineSeparator());
        }
        if (hasTryCatch) {
            m.append("    I --> J[Apply TRY/CATCH protected operations]").append(System.lineSeparator());
            m.append("    J --> K[End]").append(System.lineSeparator());
        } else {
            m.append("    I --> K[End]").append(System.lineSeparator());
        }
        m.append("```");
        return m.toString();
    }

    private String extractSection(String markdown, String sectionTitle) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        String[] lines = markdown.split("\\R");
        StringBuilder section = new StringBuilder();
        boolean inSection = false;
        String target = canonicalHeading(sectionTitle);
        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine;
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                String normalized = canonicalHeading(trimmed.replaceFirst("^#+\\s*", "").trim());
                boolean sameSection = headingMatches(normalized, target);
                if (inSection && !sameSection) {
                    break;
                }
                if (sameSection) {
                    inSection = true;
                    continue;
                }
            }
            if (inSection) {
                section.append(line).append(System.lineSeparator());
            }
        }
        return section.toString().trim();
    }

    private String canonicalHeading(String heading) {
        String text = heading == null ? "" : heading.trim().toLowerCase(Locale.ROOT);
        text = text.replaceAll("^#+\\s*", "");
        text = text.replaceAll("^[0-9]+[.)\\-:\\s]*", "");
        text = text.replaceAll("[^a-z0-9]+", " ").trim();
        return text;
    }

    private boolean headingMatches(String heading, String target) {
        if (heading == null || target == null || heading.isBlank() || target.isBlank()) {
            return false;
        }
        if (heading.equals(target)) {
            return true;
        }
        if (heading.contains(target) || target.contains(heading)) {
            return true;
        }
        if (target.contains("purpose of change") && heading.contains("purpose")) {
            return true;
        }
        if (target.contains("detailed text flowchart")
                && (heading.contains("text flowchart") || heading.contains("execution logic"))) {
            return true;
        }
        if (target.contains("graphical flowchart")
                && (heading.contains("graphical flowchart") || heading.contains("mermaid"))) {
            return true;
        }
        return false;
    }

    private String buildValidationSummary(List<RuleViolation> violations) {
        if (violations == null || violations.isEmpty()) {
            return "Validation passed with no rule violations.";
        }
        long major = violations.stream()
                .filter(v -> "MAJOR".equalsIgnoreCase(v.getSeverity()))
                .count();
        long minor = violations.stream()
                .filter(v -> "MINOR".equalsIgnoreCase(v.getSeverity()))
                .count();
        long info = violations.stream()
                .filter(v -> "INFO".equalsIgnoreCase(v.getSeverity()))
                .count();
        return "Validation results: major=" + major + ", minor=" + minor + ", info=" + info + ".";
    }

    private String asString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }
}
