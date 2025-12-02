package services.merge;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Streams many delimited text files (typically CSV) into one or more merged outputs.
 *
 * Features
 * - Adds an "Experiment" column as the first column; its value is derived by a LabelStrategy.
 * - Writes the header only once per output, taken from the first matching file.
 * - Single-pattern mode: match files whose names contain a substring (case-insensitive).
 * - Multi-merge mode: discover base names (e.g., "results") in the first subfolder and
 *   merge each base across the entire tree as <base>.<ext>.
 * - Extension-aware APIs using FileExtension (default CSV).
 *
 * Notes
 * - Lines are copied verbatim; the merger does not parse/modify column delimiters.
 * - Empty lines are skipped.
 */
public class CsvMerger {

    private static final char BOM = '\uFEFF';

    private final LabelStrategy labelStrategy;
    private final Charset charset;
    private final boolean quoteLabelIfComma;
    private final boolean verifyHeaderMatch;

    /**
     * Construct with defaults:
     * charset = UTF-8, quoteLabelIfComma = true, verifyHeaderMatch = false
     */
    public CsvMerger(LabelStrategy labelStrategy) {
        this(labelStrategy, StandardCharsets.UTF_8, true, false);
    }

    public CsvMerger(LabelStrategy labelStrategy,
                     Charset charset,
                     boolean quoteLabelIfComma,
                     boolean verifyHeaderMatch) {
        this.labelStrategy = Objects.requireNonNull(labelStrategy, "labelStrategy");
        this.charset = Objects.requireNonNull(charset, "charset");
        this.quoteLabelIfComma = quoteLabelIfComma;
        this.verifyHeaderMatch = verifyHeaderMatch;
    }

    /* -------------------------------------------------------------
     * Public API — Single-pattern
     * ------------------------------------------------------------- */

    /** Back-compat: defaults to CSV extension. */
    public Path mergeSinglePattern(Path root, String namePattern) throws IOException, MergeException {
        return mergeSinglePattern(root, namePattern, FileExtension.CSV);
    }

    /**
     * Merge all files under {@code root} whose filename (case-insensitive)
     * contains {@code namePattern} and ends with {@code ext}.
     *
     * Output path: root/Merged_<experiment>_<sanitizedPattern>.<ext>
     */
    public Path mergeSinglePattern(Path root, String namePattern, FileExtension ext)
            throws IOException, MergeException {

        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(namePattern, "namePattern");
        Objects.requireNonNull(ext, "ext");

        if (!Files.isDirectory(root)) {
            throw new MergeException("Root is not a directory: " + root);
        }

        final String experiment = root.getFileName().toString();
        final String sanitized = namePattern.replaceAll("\\W+", "_");
        final Path out = root.resolve("Merged_" + experiment + "_" + sanitized + "." + ext.ext());

        if (Files.exists(out)) {
            throw new MergeException("Output already exists: " + out);
        }

        final String needle = namePattern.toLowerCase(Locale.ROOT);

        Predicate<Path> match = p -> Files.isRegularFile(p)
                && ext.matches(p)
                && p.getFileName().toString().toLowerCase(Locale.ROOT).contains(needle);

        return writeMerged(root, out, match);
    }

    /* -------------------------------------------------------------
     * Public API — Multi from first subfolder (discover bases)
     * ------------------------------------------------------------- */

    /** Back-compat: defaults to CSV extension. */
    public List<Path> mergeFromFirstSubfolderPatterns(Path root) throws IOException, MergeException {
        return mergeFromFirstSubfolderPatterns(root, FileExtension.CSV);
    }

    /**
     * Discover unique base names for files with {@code ext} inside the first subdirectory of {@code root},
     * then for each base name B merge every matching {@code B.ext} across the entire tree under {@code root}.
     *
     * Output paths: root/Merged_<experiment>_<base>.<ext> for each discovered base.
     */
    public List<Path> mergeFromFirstSubfolderPatterns(Path root, FileExtension ext)
            throws IOException, MergeException {

        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(ext, "ext");

        if (!Files.isDirectory(root)) {
            throw new MergeException("Root is not a directory: " + root);
        }

        Path first = DirectoryUtils.firstSubdirectory(root).get();
        if (first == null) {
            throw new MergeException("No subdirectories under: " + root);
        }

        Set<String> bases = DirectoryUtils.collectBaseNames(first, ext);
        if (bases.isEmpty()) {
            throw new MergeException("No *." + ext.ext() + " files found under: " + first);
        }

        final String experiment = root.getFileName().toString();
        final List<Path> outs = new ArrayList<>(bases.size());

        // Preflight: ensure we won't overwrite any output
        for (String base : bases) {
            Path out = root.resolve("Merged_" + experiment + "_" + base + "." + ext.ext());
            if (Files.exists(out)) {
                throw new MergeException("Output already exists: " + out);
            }
        }

        // Write each merged output
        for (String base : bases) {
            final String exactFilename = ext.withExt(base);
            final Path out = root.resolve("Merged_" + experiment + "_" + base + "." + ext.ext());

            Predicate<Path> match = p -> Files.isRegularFile(p)
                    && ext.matches(p)
                    && p.getFileName().toString().equalsIgnoreCase(exactFilename);

            writeMerged(root, out, match);
            outs.add(out);
        }

        return outs;
    }

    /* -------------------------------------------------------------
     * Core writer
     * ------------------------------------------------------------- */

    /**
     * Walks {@code root}, filters with {@code fileMatch}, and writes a single merged output to {@code out}.
     * Adds the "Experiment" header and per-row label via {@link LabelStrategy}.
     *
     * IMPORTANT: iterates the stream with an iterator so checked MergeException can propagate.
     */
    private Path writeMerged(Path root, Path out, Predicate<Path> fileMatch)
            throws IOException, MergeException {

        final String[] headerHolder = { null };
        boolean wroteAnyRow = false;

        try (BufferedWriter writer = Files.newBufferedWriter(out, charset)) {
            try (Stream<Path> s = Files.walk(root)) {
                Iterator<Path> it = s.filter(fileMatch).iterator();
                while (it.hasNext()) {
                    Path p = it.next();
                    appendCsvFile(root, p, headerHolder, writer); // may throw IOException or MergeException
                    wroteAnyRow = true;
                }
            } catch (UncheckedIOException uioe) {
                // Best-effort cleanup of partial file
                try { Files.deleteIfExists(out); } catch (IOException ignore) {}
                Throwable cause = uioe.getCause();
                if (cause instanceof IOException) throw (IOException) cause;
                throw uioe;
            } catch (RuntimeException re) {
                // In case something else unexpected bubbles up
                try { Files.deleteIfExists(out); } catch (IOException ignore) {}
                throw re;
            }
        }

        if (!wroteAnyRow) {
            // No matches: remove the empty file and report
            Files.deleteIfExists(out);
            throw new MergeException("No matching files found under: " + root);
        }

        return out;
    }

    /**
     * Appends one file into the merged writer.
     * - First non-empty line is treated as the header.
     * - Writes "Experiment,<header>" once (on the first file).
     * - Writes "<label>,<row>" for each subsequent line.
     */
    protected void appendCsvFile(Path root, Path csvFile, String[] headerHolder, BufferedWriter writer)
            throws IOException, MergeException {

        final String labelRaw = labelStrategy.labelFor(root, csvFile);
        final String label = formatLabel(labelRaw);

        try (BufferedReader br = Files.newBufferedReader(csvFile, charset)) {
            String line;
            boolean firstLine = true;

            while ((line = br.readLine()) != null) {
                if (line.isEmpty()) continue;

                // Strip UTF-8 BOM if it's at the start of the line
                if (firstLine && !line.isEmpty() && line.charAt(0) == BOM) {
                    line = line.substring(1);
                }

                if (firstLine) {
                    firstLine = false;

                    // Header handling
                    if (headerHolder[0] == null) {
                        headerHolder[0] = line;
                        writer.write("Experiment," + line);
                        writer.newLine();
                    } else if (verifyHeaderMatch && !line.equals(headerHolder[0])) {
                        throw new MergeException("Header mismatch in file: " + csvFile
                                + "\nExpected: " + headerHolder[0]
                                + "\nFound:    " + line);
                    }

                    continue; // do not treat header as data
                }

                // Data row
                writer.write(label);
                writer.write(',');
                writer.write(line);
                writer.newLine();
            }
        }
    }

    /* -------------------------------------------------------------
     * Helpers
     * ------------------------------------------------------------- */

    private String formatLabel(String raw) {
        if (raw == null) return "";
        String s = raw;
        if (quoteLabelIfComma && (s.indexOf(',') >= 0 || s.indexOf('"') >= 0)) {
            // CSV quoting: wrap in quotes and escape internal quotes by doubling them
            s = s.replace("\"", "\"\"");
            s = "\"" + s + "\"";
        }
        return s;
    }
}
