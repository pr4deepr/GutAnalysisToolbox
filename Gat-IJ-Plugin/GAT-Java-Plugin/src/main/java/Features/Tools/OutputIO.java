package Features.Tools;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.io.FileSaver;
import ij.plugin.frame.RoiManager;
import ij.io.FileInfo;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * File/IO helpers for saving analysis results:
 *  - creating analysis output directories,
 *  - saving ROIs, TIFFs, flattened overlays,
 *  - writing CSV summaries for neuron counts, ganglia stats, multi-marker stats.
 *
 * All the pipelines call into this to keep naming and directory layout consistent.
 */
public final class OutputIO {
    private OutputIO(){}

    /**
     * Choose (or create) an output directory for a given run.
     *
     * Rules:
     *   1. Pick a parent directory:
     *      - if 'explicitParent' is provided, use that,
     *      - else use the directory of the original image file if available,
     *      - else fall back to IJ.getDirectory("image") or user.home.
     *   2. Inside that parent, create/ensure "Analysis".
     *   3. Inside Analysis/, create a folder named after baseName.
     *      - If that folder already exists, append _1, _2, ... to avoid overwrite.
     *
     * @param explicitParent user-selected parent folder (nullable).
     * @param imp            source image (to infer calibration/path).
     * @param baseName       base file name (no extension).
     * @return               A unique directory under Analysis/.
     * @throws IllegalStateException if we fail to create the directory.
     */
    public static File prepareOutputDir(String explicitParent, ImagePlus imp, String baseName) {
        // Resolve parent dir
        File parent;
        if (explicitParent != null && !explicitParent.trim().isEmpty()) {
            parent = new File(explicitParent);
        } else {
            FileInfo fi = imp.getOriginalFileInfo();
            if (fi != null && fi.directory != null && !fi.directory.trim().isEmpty()) {
                parent = new File(fi.directory);
            } else {
                String fallback = IJ.getDirectory("image");
                parent = new File(fallback != null ? fallback : System.getProperty("user.home"));
            }
        }

        // Analysis/<baseName> with mkdirs() checks
        File analysis = new File(parent, "Analysis");
        if (!analysis.exists() && !analysis.mkdirs()) {
            throw new IllegalStateException("Failed to create dir: " + analysis.getAbsolutePath());
        }

        File out = uniqueDir(new File(analysis, baseName));
        if (!out.exists() && !out.mkdirs()) {
            throw new IllegalStateException("Failed to create dir: " + out.getAbsolutePath());
        }


        return out;
    }

    /**
     * INTERNAL HELPER (private): uniqueDir(File target)
     *
     * If target already exists, append _1, _2, ... until we find a free name.
     * Used by prepareOutputDir() so we never overwrite previous runs.
     *
     * @param target desired directory path.
     * @return       unique, non-existing directory path.
     */
    private static File uniqueDir(File target) {
        File parent = target.getParentFile();
        String name = target.getName();
        String base = name;
        String ext  = "";
        int dot = name.lastIndexOf('.');
        // treat ".bashrc" as no-extension (dot must not be the first char)
        if (dot > 0 && dot < name.length() - 1) {
            base = name.substring(0, dot);
            ext  = name.substring(dot); // includes the dot
        }
        if (!target.exists()) return target;
        int k = 1;
        while (true) {
            File cand = (parent == null)
                    ? new File(base + "_" + k + ext)
                    : new File(parent, base + "_" + k + ext);
            if (!cand.exists()) return cand;
            k++;
        }
    }

    /**
     * Save all ROIs currently in a RoiManager to a .zip file.
     *
     * The RoiManager handles the actual writing.
     *
     * @param rm  RoiManager containing ROIs to save.
     * @param zip Destination .zip file.
     */
    public static void saveRois(RoiManager rm, File zip) {
        rm.runCommand("Save", zip.getAbsolutePath());
    }

    /**
     * Save an ImagePlus as TIFF at the given path.
     *
     * @param imp Image to save.
     * @param out Destination .tif file.
     */
    public static void saveTiff(ImagePlus imp, File out) {
        new FileSaver(imp).saveAsTiff(out.getAbsolutePath());
    }

    /**
     * Render a flattened overlay (labels drawn on top of the base image)
     * and save it as a TIFF. This gives you a publication-style "cells outlined"
     * QC snapshot.
     *
     * Steps:
     *   - Duplicate the base image (hidden).
     *   - Ask RoiManager to "Show All with labels" on the duplicate, so it paints overlays.
     *   - Call ImagePlus.flatten() to bake overlay into RGB pixels.
     *   - Save the flattened RGB.
     *   - Clean up temp images.
     *
     * @param base Base image (usually MAX projection).
     * @param rm   RoiManager with the ROIs to overlay.
     * @param out  Destination TIFF file.
     */
    public static void saveFlattenedOverlay(ImagePlus base, RoiManager rm, File out) {
        // Work on a hidden duplicate
        ImagePlus dup = base.duplicate();
        dup.hide();

        // Ask RM to draw its overlay onto this dup
        rm.runCommand(dup, "Show All with labels");

        // Pure-API flatten
        ImagePlus flat = dup.flatten();        // returns an RGB image with overlay baked in

        new ij.io.FileSaver(flat).saveAsTiff(out.getAbsolutePath());

        // tidy
        dup.changes = false;  dup.close();
        flat.changes = false; flat.close();
    }

    /**
     * Write a simple 2-column CSV:
     *   File name,Total <cellType>
     *   basename, count
     *
     * Used for Hu-only runs / neuron counts.
     *
     * @param csv       Destination CSV file.
     * @param baseName  Base image name.
     * @param cellType  Friendly cell type label ("Hu", "Neuron", etc.).
     * @param count     Total cell count.
     */
    public static void writeCountsCsv(File csv, String baseName, String cellType, int count) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(csv))) {
            pw.println("File name,Total " + cellType);
            pw.println(baseName + "," + count);
        } catch (IOException e) {
            IJ.log("Failed writing CSV: " + e.getMessage());
        }
    }

    /**
     * Write ganglion stats CSV with columns:
     *   ganglion_id, neuron_count, area_um2
     *
     * Each row is one ganglion label ID.
     *
     * @param out     Destination CSV file.
     * @param counts  counts[gid] = neuron count for ganglion gid.
     * @param areaUm2 areaUm2[gid] = area in square microns for ganglion gid.
     */
    public static void writeGangliaCsv(File out, int[] counts, double[] areaUm2) {
        try (java.io.PrintWriter pw = new java.io.PrintWriter(out)) {
            pw.println("ganglion_id,neuron_count,area_um2");
            int n = Math.max(counts.length, areaUm2.length);
            for (int gid = 1; gid < n; gid++) {
                int c = (gid < counts.length) ? counts[gid] : 0;
                double a = (gid < areaUm2.length) ? areaUm2[gid] : 0.0;
                // skip empty ganglia
                if (c == 0 && a == 0) continue;
                pw.printf(java.util.Locale.US, "%d,%d,%.6f%n", gid, c, a);
            }
        } catch (Exception e) {
            ij.IJ.handleException(e);
        }
    }


    /**
     * Write multi-marker CSV for the Hu (gated) pipeline.
     *
     * Structure:
     *   Header row:
     *     File name, Total Hu, [No of ganglia], <all marker/combo totals...>,
     *     [<marker per-ganglion cols...>], [Area_per_ganglia_um2]
     *
     *   Then one or more data rows.
     *   If ganglia data exists, we include per-ganglion distributions for each marker/combo
     *   (countsPerGanglion arrays), and an area column.
     *
     * @param csv              Destination CSV.
     * @param baseName         Base image name.
     * @param totalHu          Total Hu neuron count.
     * @param nGangliaOrNull   Number of ganglia (nullable if ganglia wasn't run).
     * @param totals           Map markerName->totalCount (includes combos like "A+B").
     * @param perGanglia       Map markerName->int[gid] neuron counts per ganglion.
     * @param gangliaAreaUm2   Per-ganglion areas in µm².
     */
    public static void writeMultiCsv(
            File csv,
            String baseName,
            int totalHu,
            Integer nGangliaOrNull,                       // may be null
            LinkedHashMap<String,Integer> totals,         // marker & combo totals
            LinkedHashMap<String,int[]> perGanglia,       // marker & combo per ganglion counts (optional)
            double[] gangliaAreaUm2                       // optional (length = #ganglia)
    ) {
        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(csv))) {
            // Header
            java.util.List<String> headers = new java.util.ArrayList<>();
            headers.add("File name");
            headers.add("Total Hu");
            if (nGangliaOrNull != null) headers.add("No of ganglia");

            // totals per marker/combo
            headers.addAll(totals.keySet());

            // per-ganglia blocks
            if (nGangliaOrNull != null) {
                for (String name : perGanglia.keySet()) {
                    headers.add(name + " counts per ganglia");
                }
                headers.add("Area_per_ganglia_um2");
            }
            pw.println(String.join(",", headers));

            //  Rows
            int nRows = (nGangliaOrNull == null) ? 1 : Math.max(1, nGangliaOrNull);

            for (int r = 0; r < nRows; r++) {
                java.util.List<String> cells = new java.util.ArrayList<>();
                if (r == 0) {
                    cells.add(baseName);
                    cells.add(Integer.toString(totalHu));
                    if (nGangliaOrNull != null) cells.add(Integer.toString(nGangliaOrNull));
                    for (String name : totals.keySet()) cells.add(Integer.toString(totals.get(name)));
                } else {
                    // blank for totals on subsequent lines
                    cells.add(""); // File name
                    cells.add(""); // Total Hu
                    if (nGangliaOrNull != null) cells.add("");
                    for (int i = 0; i < totals.size(); i++) cells.add("");
                }

                if (nGangliaOrNull != null) {
                    // per-ganglia columns
                    for (String name : perGanglia.keySet()) {
                        int[] vec = perGanglia.get(name);
                        int v = (vec != null && r < vec.length) ? vec[r] : 0;
                        cells.add(Integer.toString(v));
                    }
                    // area column
                    double a = (gangliaAreaUm2 != null && r < gangliaAreaUm2.length) ? gangliaAreaUm2[r] : 0.0;
                    cells.add(String.format(java.util.Locale.US, "%.6f", a));
                }

                pw.println(String.join(",", cells));
            }
        } catch (Exception e) {
            ij.IJ.handleException(e);
        }
    }

    /**
     * Write multi-marker CSV for the No-Hu pipeline.
     *
     * Similar idea to writeMultiCsv(), but:
     *   - There is no global "Total Hu".
     *   - Still supports per-ganglion breakdown if ganglia were computed separately.
     *
     * Header includes:
     *   File name, [No of ganglia], <marker/combo totals...>,
     *   [<marker counts per ganglia...>], Area_per_ganglia_um2
     *
     * Rows:
     *   - First row: totals and ganglia distributions.
     *   - Additional rows (if ganglia exists) list per-ganglion counts/area.
     *
     * @param csv                     Destination CSV.
     * @param baseName                Base image name.
     * @param totals                  Map markerName->totalCount for each marker and combo.
     * @param perGangliaOrNull        Optional map markerName->per-ganglion counts.
     * @param gangliaAreaUm2OrNull    Optional per-ganglion area array.
     */
    public static void writeMultiCsvNoHu(
            File csv,
            String baseName,
            LinkedHashMap<String,Integer> totals,
            LinkedHashMap<String,int[]> perGangliaOrNull,
            double[] gangliaAreaUm2OrNull
    ) {
        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(csv))) {

            // determine #ganglia from inputs (if any)
            int nGanglia = 0;
            if (perGangliaOrNull != null) {
                for (Map.Entry<String,int[]> e : perGangliaOrNull.entrySet()) {
                    int[] v = e.getValue();
                    if (v != null) nGanglia = Math.max(nGanglia, v.length);
                }
            }
            if (gangliaAreaUm2OrNull != null) {
                nGanglia = Math.max(nGanglia, gangliaAreaUm2OrNull.length);
            }
            boolean hasGanglia = nGanglia > 0;

            // header
            java.util.List<String> headers = new java.util.ArrayList<>();
            headers.add("File name");
            if (hasGanglia) headers.add("No of ganglia");

            // totals per marker/combo (in insertion order from LinkedHashMap)
            headers.addAll(totals.keySet());

            // per-ganglia blocks
            if (hasGanglia && perGangliaOrNull != null && !perGangliaOrNull.isEmpty()) {
                for (String name : perGangliaOrNull.keySet()) {
                    headers.add(name + " counts per ganglia");
                }
                headers.add("Area_per_ganglia_um2");
            }
            pw.println(String.join(",", headers));

            // rows
            int nRows = hasGanglia ? nGanglia : 1;

            for (int r = 0; r < nRows; r++) {
                java.util.List<String> cells = new java.util.ArrayList<>();

                if (r == 0) {
                    cells.add(baseName);
                    if (hasGanglia) cells.add(Integer.toString(nGanglia));
                    for (String name : totals.keySet()) {
                        cells.add(Integer.toString(totals.getOrDefault(name, 0)));
                    }
                } else {
                    // blanks under the totals on subsequent lines
                    cells.add(""); // File name
                    if (hasGanglia) cells.add(""); // No. of ganglia
                    for (int i = 0; i < totals.size(); i++) cells.add("");
                }

                if (hasGanglia && perGangliaOrNull != null && !perGangliaOrNull.isEmpty()) {
                    for (String name : perGangliaOrNull.keySet()) {
                        int[] vec = perGangliaOrNull.get(name);
                        int v = (vec != null && r < vec.length) ? vec[r] : 0;
                        cells.add(Integer.toString(v));
                    }
                    double a = (gangliaAreaUm2OrNull != null && r < gangliaAreaUm2OrNull.length)
                            ? gangliaAreaUm2OrNull[r] : 0.0;
                    cells.add(String.format(java.util.Locale.US, "%.6f", a));
                }

                pw.println(String.join(",", cells));
            }
        } catch (Exception e) {
            ij.IJ.handleException(e);
        }
    }

}
