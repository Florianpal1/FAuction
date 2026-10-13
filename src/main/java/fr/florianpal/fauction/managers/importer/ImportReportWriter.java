package fr.florianpal.fauction.managers.importer;

import fr.florianpal.fauction.api.importer.ImportDataType;
import fr.florianpal.fauction.api.importer.ImportReport;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Writes the detailed report of an import, {@code plugins/FAuction/imports/<id>-<date>.log} : the
 * counters, then every row refused, skipped or failed with its reason.
 */
public final class ImportReportWriter {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private ImportReportWriter() {
    }

    /**
     * @return the file written.
     */
    public static File write(File folder, ImportReport report, String progressLine, ZoneId zone) throws IOException {
        Files.createDirectories(folder.toPath());

        String base = report.importerId() + "-" + FILE_DATE.format(report.startedAt().atZone(zone));
        File file = new File(folder, base + ".log");
        for (int i = 2; file.exists(); i++) {
            file = new File(folder, base + "-" + i + ".log");
        }

        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            writer.write("FAuction import report");
            writer.newLine();
            writer.write("Importer : " + report.importerId());
            writer.newLine();
            writer.write("Mode : " + (report.dryRun() ? "dry run, nothing written" : "import"));
            writer.newLine();
            writer.write("Status : " + report.status() + (report.failure() != null ? " - " + report.failure() : ""));
            writer.newLine();
            writer.write("Started : " + report.startedAt().atZone(zone) + ", finished : " + report.finishedAt().atZone(zone));
            writer.newLine();
            writer.newLine();

            for (Map.Entry<ImportDataType, ImportReport.TypeCounts> entry : report.counts().entrySet()) {
                ImportReport.TypeCounts counts = entry.getValue();
                writer.write(String.format("%-17s read %d, imported %d (of which given back to the seller %d), already imported %d, rejected %d, failed %d",
                        entry.getKey().id(), counts.read(), counts.imported(), counts.movedToExpires(),
                        counts.alreadyImported(), counts.rejected(), counts.failed()));
                writer.newLine();
            }
            writer.write("Skipped by the module : " + report.skipped());
            writer.newLine();
            writer.write("Last progress : " + progressLine);
            writer.newLine();
            writer.newLine();

            writer.write("Rows refused, skipped or failed (" + report.issueCount() + ") :");
            writer.newLine();
            for (ImportReport.Issue issue : report.issues()) {
                writer.write(issue.kind() + "\t" + (issue.type() == null ? "-" : issue.type().id()) + "\t" + issue.sourceId() + "\t" + issue.reason());
                writer.newLine();
            }
            long notListed = report.issueCount() - report.issues().size();
            if (notListed > 0) {
                writer.write("... and " + notListed + " more, not kept in memory");
                writer.newLine();
            }
        }
        return file;
    }
}
