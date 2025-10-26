// Features/AnalyseWorkflows/NeuronsMultiNoHuPipeline.java
package Features.AnalyseWorkflows;

import Features.Core.Params;
import Features.Core.PluginCalls;
import Features.Tools.ImageOps;
import Features.Tools.OutputIO;
import Features.Tools.ProgressUI;
import UI.panes.Tools.ReviewUI;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.macro.Interpreter;
import ij.plugin.frame.RoiManager;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static Features.Tools.RoiManagerHelper.*;

// Class: NeuronsMultiNoHuPipeline
/**
 * Multi-marker pipeline for images WITHOUT a Hu channel.
 *
 * Key ideas:
 *  - Works across multiple user-defined markers/channels.
 *  - Segments each marker (StarDist or custom ROI zip), lets the user review/edit,
 *    and exports per-marker ROIs, overlays, and counts.
 *  - Optionally segments ganglia (without relying on Hu) and reports per-ganglion
 *    cell counts and areas.
 *  - Computes simple AND-combination markers (markerA+markerB).
 *  - Can optionally run spatial analysis after export.
 *
 * Unlike the Hu pipeline, this does not gate markers by Hu overlap, because Hu does
 * not exist here. Each marker is segmented independently.
 */

public class NeuronsMultiNoHuPipeline {

    // Inner class: MarkerSpec
    /**
     * Describes one biological marker/channel to analyze.
     *
     * Fields:
     *  - {@code name}: human-readable marker name (used in output file names).
     *  - {@code channel}: 1-based channel index in the MAX projection.
     *  - {@code prob}/{@code nms}: optional overrides for StarDist thresholds for this marker.
     *  - {@code customRoisZip}: optional pre-existing ROI .zip to import instead of running StarDist.
     *
     * Fluent helpers:
     *  - {@link #withThresh(Double, Double)} to override prob/NMS.
     *  - {@link #withCustomRois(File)} to inject an ROI zip instead of segmenting.
     */

    public static final class MarkerSpec {
        public final String name;
        public final int channel;     // 1-based in MAX composite
        public Double prob;           // optional StarDist override
        public Double nms;            // optional StarDist override
        public File customRoisZip;


        public MarkerSpec(String name, int channel) { this.name = name; this.channel = channel; }
        public MarkerSpec withThresh(Double prob, Double nms) { this.prob = prob; this.nms = nms; return this; }
        public MarkerSpec withCustomRois(File zip) { this.customRoisZip = zip; return this; }
    }



    // Inner class: MultiParams
    /**
     * Parameters for the multi-marker no-Hu pipeline.
     *
     * Fields:
     *  - {@code base}: shared {@link Params} controlling projection, rescaling, ganglia, etc.
     *  - {@code subtypeModelZip}: StarDist model ZIP to use for each marker channel.
     *  - {@code multiProb}/{@code multiNms}: default StarDist thresholds if a marker doesn't
     *    provide its own.
     *  - {@code overlapFrac}: (kept for parity with other pipelines; combos use AND, not fractions).
     *  - {@code markers}: list of {@link MarkerSpec} to process.
     */

    public static final class MultiParams {
        public Params base;               // projection / rescale / ganglia options reused
        public String subtypeModelZip;    // StarDist model (ZIP) for subtype channels
        public double multiProb = 0.50;
        public double multiNms  = 0.30;
        public double overlapFrac = 0.40; // kept for parity; combos are hard AND here
        public final List<MarkerSpec> markers = new ArrayList<>();
    }


    // Method: estimateSteps(MultiParams mp)
    /**
     * Estimates how many progress-bar steps will be needed for a given
     * {@link MultiParams} run. Used to initialize the determinate ProgressUI.
     *
     * Roughly accounts for:
     *  - Base prep and (optional) ganglia analysis.
     *  - Per-marker segmentation + review.
     *  - Pairwise AND combos.
     *  - Final CSV/export.
     *
     * @param mp  The run configuration.
     * @return    Approximate number of UI progress steps.
     */

    public static int estimateSteps(MultiParams mp){

        int n = mp != null ? mp.markers.size() : 0;
        int nCombos = (n * (n - 1)) / 2;

        // Base: open + projection + rescale math
        int base = 3;

        // Ganglia (No-Hu path): run model, label/export ROIs, compute areas, cleanup
        if (mp != null && mp.base != null && mp.base.cellCountsPerGanglia) {
            base += 4;
        }

        // Per-marker: prep, segment, post/resize, review, save  (5 each)
        int perMarker = 5 * n;

        // Combos: build AND + save (2 each)
        int combos = 2 * nCombos;

        // Finalize: write CSV + save MAX/cleanup
        int tail = 2;

        return base + perMarker + combos + tail;
    }

    // Inner class: NoHuResult
    /**
     * Output bundle summarizing a completed no-Hu multi-marker run.
     *
     * Contains:
     *  - {@code outDir}: where results were written.
     *  - {@code baseName}: basename of the input image.
     *  - {@code max}: the max-projection used for overlays / preview.
     *  - {@code totals}: map of marker/combination name → total number of detected cells.
     *  - {@code perGanglia}: map of marker/combination name → array[ganglionId] of cell counts,
     *                        if ganglia analysis was run.
     *  - {@code nGanglia}: number of ganglia detected (nullable if ganglia disabled).
     *  - {@code gangliaAreaUm2}: per-ganglion area in µm² (nullable if ganglia disabled).
     *  - {@code doSpatialAnalysis}: whether spatial analysis was requested.
     *
     * Used by downstream UI panels and spatial analysis runners.
     */

    public static final class NoHuResult {
        public final File outDir;
        public final String baseName;
        public final ImagePlus max;                 // for thumbnails
        public final LinkedHashMap<String,Integer> totals;       // marker or combo -> total cells
        public final LinkedHashMap<String,int[]>   perGanglia;   // marker or combo -> counts per ganglion (1..G)
        public final Integer nGanglia;                              // null if ganglia not run
        public final double[] gangliaAreaUm2;                       // null if ganglia not run
        public final Boolean doSpatialAnalysis;

        public NoHuResult(File outDir, String baseName, ImagePlus max,
                          LinkedHashMap<String,Integer> totals,
                          LinkedHashMap<String,int[]> perGanglia,
                          Integer nGanglia, double[] gangliaAreaUm2, Boolean doSpatialAnalysis) {
            this.outDir = outDir;
            this.baseName = baseName;
            this.max = max;
            this.totals = totals;
            this.perGanglia = perGanglia;
            this.nGanglia = nGanglia;
            this.gangliaAreaUm2 = gangliaAreaUm2;
            this.doSpatialAnalysis = doSpatialAnalysis;
        }
    }



    // Method: run(MultiParams mp)
    /**
     * Executes the multi-marker, no-Hu workflow.
     *
     * High-level steps:
     *   1. Open image, build MAX/EDF projection, compute scaling factors.
     *   2. (Optional) Run ganglia detection once for the whole image (DeepImageJ / import / manual).
     *      Export ganglia ROIs and overlays, measure ganglia area.
     *   3. For each marker in {@code mp.markers}:
     *        - Extract that channel from MAX, rescale if needed.
     *        - Either import given ROI zip or run StarDist with the subtype model.
     *        - Filter/resize labels, push ROIs to ROI Manager.
     *        - Pop up an interactive review to let the user edit detections.
     *        - Save final ROIs, count cells, generate overlays.
     *        - (If ganglia are available) count cells per ganglion.
     *   4. For every pair of markers, compute an AND-combination mask, count cells,
     *      export ROIs/overlays, and (optionally) per-ganglion stats.
     *   5. Write a summary CSV of per-marker and per-combo counts, plus ganglia stats.
     *   6. Save the MAX projection.
     *   7. Optionally run spatial analysis for each marker.
     *   8. Show a summary UI (ResultsMultiNoHuUI) on the EDT.
     *
     * Side effects:
     *   - Writes TIFFs, ROI zips, overlays, and CSVs to {@code mp.base.outputDir}
     *     (or a default Analysis folder).
     *   - Pops up interactive review windows for each marker.
     *
     * @param mp  The complete analysis configuration, including shared {@link Params},
     *            model paths, thresholds, and marker list.
     * @throws IllegalArgumentException if required model files are missing, or no markers provided.
     * @throws IllegalStateException    if no image is available to analyze.
     */
    public void run(MultiParams mp) {

        try {
            if (mp == null || mp.base == null) throw new IllegalArgumentException("MultiParams/base cannot be null.");
            if (mp.subtypeModelZip == null || !new File(mp.subtypeModelZip).isFile())
                throw new IllegalArgumentException("Subtype StarDist model not found: " + mp.subtypeModelZip);
            if (mp.markers.isEmpty()) throw new IllegalArgumentException("Add at least one marker.");

            ij.macro.Interpreter.batchMode = true;
            ProgressUI progress = new ProgressUI("No-Hu multi-channel");
            progress.start(estimateSteps(mp));

            // 1) Open image & make MAX
            progress.step("Open image");
            ImagePlus imp = (mp.base.imagePath == null || mp.base.imagePath.isEmpty())
                    ? IJ.getImage()
                    : PluginCalls.openWithBioFormats(mp.base.imagePath);
            if (imp == null) throw new IllegalStateException("No image available to analyze.");

            final String baseName = stripExt(imp.getTitle());
            final File outDir = OutputIO.prepareOutputDir(mp.base.outputDir, imp, baseName);

            progress.step("Create projection");
            ImagePlus max = (imp.getNSlices() > 1)
                    ? (mp.base.useClij2EDF ? PluginCalls.clij2EdfVariance(imp) : ImageOps.mip(imp))
                    : imp.duplicate();
            max.setTitle("MAX_" + baseName);

            //create our global roi manager
            RmHandle rmh = ensureGlobalRM();

            // 2) Rescale math
            progress.step("Rescale math");
            final double pxUm = (max.getCalibration() != null && max.getCalibration().pixelWidth > 0)
                    ? max.getCalibration().pixelWidth : 1.0;
            final double scale = (mp.base.trainingRescaleFactor > 0) ? mp.base.trainingRescaleFactor : 1.0;
            final double targetPxUm = mp.base.trainingPixelSizeUm / scale;
            double scaleFactor = (mp.base.rescaleToTrainingPx ? (pxUm / targetPxUm) : 1.0);
            if (Math.abs(scaleFactor - 1.0) < 1e-3) scaleFactor = 1.0;

            int minPx = 0; // min size in pixels at segmentation scale
            if (mp.base.neuronSegMinMicron != null && pxUm > 0) {
                double eff = (scaleFactor == 1.0) ? pxUm : targetPxUm;
                minPx = (int) Math.max(1, Math.round(mp.base.neuronSegMinMicron / eff));
            }


            // 2.5) Ganglia (once)
            ImagePlus gangliaLabels = null;
            double[] gangliaAreaUm2 = null;
            int nGanglia = 0;

            if (mp.base.cellCountsPerGanglia) {
                progress.pulse("Ganglia: segment (" + mp.base.gangliaMode + ")");
                // No Hu labels in this pipeline → pass null for neuronLabels
                ImagePlus gangliaOut = GangliaOps.segment(mp.base, max, /*neuronLabels=*/null, progress);
                progress.stopPulse("Ganglia: segmentation done");

                progress.step("Ganglia: label/export/areas");
                // If segment() returned binary, convert; if it returned labels, this is quick no-op
                // Ensure we end with a label map either way
                ImagePlus glabels = (gangliaOut.getBitDepth() == 8)
                        ? PluginCalls.binaryToLabels(gangliaOut)
                        : gangliaOut;
                glabels.setCalibration(max.getCalibration());
                gangliaLabels = glabels;

                RoiManager rmG = rmh.rm;
                rmG.reset();
                rmG.setVisible(false);
                PluginCalls.labelsToRois(gangliaLabels);
                syncToSingleton(new RoiManager[]{rmG});
                nGanglia = rmG.getCount();

                if (nGanglia > 0) {
                    OutputIO.saveRois(rmG, new File(outDir, "Ganglia_ROIs_" + baseName + ".zip"));
                    if (mp.base.saveFlattenedOverlay)
                        OutputIO.saveFlattenedOverlay(max, rmG,
                                new File(outDir, "MAX_" + baseName + "_ganglia_overlay.tif"));
                }
                rmG.reset();
                rmG.setVisible(false);

                gangliaAreaUm2 = GangliaOps.areaPerGanglionUm2(gangliaLabels);

                // tidy original
                if (gangliaOut != gangliaLabels) {
                    gangliaOut.changes = false;
                    gangliaOut.close();
                }
            }


            // 3) Results stores
            LinkedHashMap<String, Integer> totals = new LinkedHashMap<>();
            LinkedHashMap<String, int[]> perGanglia = new LinkedHashMap<>();
            Map<String, ImagePlus> labelsByMarker = new LinkedHashMap<>();

            // 4) Per-marker: segment → review → save
            for (MarkerSpec m : mp.markers) {
                progress.step("Prep: " + m.name);


                ImagePlus ch = ImageOps.extractChannel(max, m.channel);
                ImagePlus segInput = (scaleFactor == 1.0)
                        ? ch
                        : ImageOps.resizeToIntensity(ch,
                        (int) Math.round(ch.getWidth() * scaleFactor),
                        (int) Math.round(ch.getHeight() * scaleFactor));

                progress.pulse("Segment: " + m.name);
                ImagePlus markerLabels;
                if (m.customRoisZip != null && m.customRoisZip.isFile()) {

                    RmHandle rmh2 = ensureGlobalRM();
                    RoiManager tmp = rmh2.rm;
                    tmp.reset();
                    tmp.setVisible(false);
                    tmp.runCommand("Open", m.customRoisZip.getAbsolutePath());
                    if (tmp.getCount() == 0) {
                        throw new IllegalArgumentException("ROI zip '" + m.customRoisZip.getName() + "' contains no ROIs.");
                    }

                    // 2) ROI Manager macro commands need batch mode OFF so the mask has a canvas
                    boolean prevBatch = ij.macro.Interpreter.batchMode;
                    ij.macro.Interpreter.batchMode = false;
                    try {
                        // Paint ROIs -> binary -> labels (your original helpers)
                        ImagePlus bin = Features.Core.PluginCalls.roisToBinary(max, tmp);
                        ImagePlus lab = Features.Core.PluginCalls.binaryToLabels(bin);
                        lab.setCalibration(max.getCalibration());

                        // tidy
                        bin.changes = false;
                        bin.close();
                        markerLabels = lab;
                    } finally {
                        ij.macro.Interpreter.batchMode = prevBatch;
                        tmp.reset();
                        tmp.setVisible(false);
                    }
                } else {
                    double prob = (m.prob != null) ? m.prob : mp.multiProb;
                    double nms = (m.nms != null) ? m.nms : mp.multiNms;
                    markerLabels = PluginCalls.runStarDist2DLabel(segInput, mp.subtypeModelZip, prob, nms);
                    markerLabels = PluginCalls.removeBorderLabels(markerLabels);
                    if (minPx > 0) markerLabels = PluginCalls.labelMinSizeFilterPx(markerLabels, minPx);
                    if (markerLabels.getWidth() != max.getWidth() || markerLabels.getHeight() != max.getHeight()) {
                        markerLabels = ImageOps.resizeTo(markerLabels, max.getWidth(), max.getHeight());
                    }
                }
                progress.stopPulse("Segment done: " + m.name);

                progress.step("Review: " + m.name);
                // ---- Review (seed RM, pass fallback) ----
                RoiManager rmRev = rmh.rm;
                rmRev.reset();
                PluginCalls.labelsToRois(markerLabels);        // seed with current call
                syncToSingleton(new RoiManager[]{rmRev});
                ImagePlus fallback = markerLabels.duplicate();
                ij.macro.Interpreter.batchMode = false;
                progress.setVisible(false);
                ImagePlus reviewed = ReviewUI.reviewAndRebuildLabels(
                        ch, rmRev, m.name + " (review)", max.getCalibration(), fallback);
                progress.setVisible(true);
                Roi[] edited = rmRev.getRoisAsArray();
                ij.macro.Interpreter.batchMode = true;
                rmRev.reset();
                rmRev.setVisible(false);
                fallback.close();

                if (gangliaLabels != null) {
                    GangliaOps.Result r = GangliaOps.countPerGanglion(reviewed, gangliaLabels);
                    perGanglia.put(m.name, r.countsPerGanglion);

                    // area is the same for all markers; keep it once
                    if (gangliaAreaUm2 == null) gangliaAreaUm2 = r.areaUm2;
                }

                progress.step("Save: " + m.name);
                // Count & save
                int n = countLabels(reviewed);
                totals.put(m.name, n);


                RoiManager rmSave = rmh.rm;
                rmSave.reset();
                for (ij.gui.Roi r : edited) if (r != null) rmSave.addRoi((ij.gui.Roi) r.clone());

                if (rmSave.getCount() > 0) {
                    OutputIO.saveRois(rmSave, new File(outDir, m.name + "_ROIs_" + baseName + ".zip"));
                    if (mp.base.saveFlattenedOverlay)
                        OutputIO.saveFlattenedOverlay(max, rmSave, new File(outDir, "MAX_" + baseName + "_" + m.name + "_overlay.tif"));
                }
                rmSave.reset();
                rmSave.setVisible(false);

                labelsByMarker.put(m.name, reviewed); // keep for combos
                ch.close();
                if (segInput != ch) segInput.close();
                markerLabels.close();
            }

            // 5) Pairwise combos (AND)
            List<String> names = new ArrayList<>(labelsByMarker.keySet());
            for (int i = 0; i < names.size(); i++) {
                for (int j = i + 1; j < names.size(); j++) {
                    String aName = names.get(i), bName = names.get(j);
                    String combo = aName + "+" + bName;

                    progress.step("Combo: " + combo);
                    ImagePlus a = labelsByMarker.get(aName);
                    ImagePlus b = labelsByMarker.get(bName);
                    ImagePlus c = andLabels(a, b);                 // pixelwise AND -> relabel

                    int n = countLabels(c);
                    totals.put(combo, n);

                    if (gangliaLabels != null) {
                        GangliaOps.Result rc = GangliaOps.countPerGanglion(c, gangliaLabels);
                        perGanglia.put(combo, rc.countsPerGanglion);
                    }

                    progress.step("Save combo: " + combo);

                    RoiManager rm = rmh.rm;
                    rm.reset();
                    PluginCalls.labelsToRois(c);
                    syncToSingleton(new RoiManager[]{rm});
                    if (rm.getCount() > 0) {
                        OutputIO.saveRois(rm, new File(outDir, combo + "_ROIs_" + baseName + ".zip"));
                        if (mp.base.saveFlattenedOverlay)
                            OutputIO.saveFlattenedOverlay(max, rm, new File(outDir, "MAX_" + baseName + "_" + combo + "_overlay.tif"));
                    }
                    rm.reset();
                    rm.setVisible(false);
                    c.close();
                }
            }


            progress.step("Write CSV");
            // 6) CSV
            OutputIO.writeMultiCsvNoHu(
                    new File(outDir, "Analysis_NoHu_" + baseName + "_cell_counts_multi.csv"),
                    baseName,
                    totals,
                    perGanglia,
                    gangliaAreaUm2
            );

            progress.step("Save MAX & cleanup");
            // 7) Save MAX and clean up
            OutputIO.saveTiff(max, new File(outDir, "MAX_" + baseName + ".tif"));
            for (ImagePlus keep : labelsByMarker.values()) keep.close();
            if (gangliaLabels != null) {
                gangliaLabels.changes = false;
                gangliaLabels.close();
            }

            //close the progress bar
            progress.close();

            NoHuResult result = new NoHuResult(
                    outDir,
                    baseName,
                    max,
                    totals,
                    perGanglia,
                    (gangliaLabels != null ? Integer.valueOf(nGanglia) : null),
                    gangliaAreaUm2,
                    mp.base.doSpatialAnalysis
            );
            if (mp.base.doSpatialAnalysis) {
                runSingleSpatialPerMarker(result, mp);
            }
            maybeCloseRM(rmh);
            SwingUtilities.invokeLater(() ->
                    UI.panes.Results.ResultsMultiNoHuUI.promptAndMaybeShow(result)
            );
        }finally {
            Interpreter.batchMode = false;
        }

    }

    // Method (private): countLabels(ImagePlus labels16)
    /**
     * Counts connected components in a 16-bit label map by scanning for the
     * maximum pixel value.
     *
     * Assumptions:
     *   - The label map is a typical connected-components result:
     *     background = 0, objects have IDs 1..K.
     *   - IDs are contiguous up to K.
     *
     * @param labels16 16-bit label map image.
     * @return         Number of labeled objects (i.e. max label ID).
     */

    private static int countLabels(ImagePlus labels16) {
        short[] px = (short[]) labels16.getProcessor().getPixels();
        int max = 0;
        for (short v : px) { int u = v & 0xFFFF; if (u > max) max = u; }
        return max;
    }

    // Method (private): stripExt(String name)
    /**
     * Removes the last file extension from a string.
     *
     * Example:
     *   "foo/bar/image.lif" -> "image"
     *
     * Used to compute {@code baseName} for output file naming.
     *
     * @param name  The original title or filename.
     * @return      The same string without the final ".ext" portion.
     */

    private static String stripExt(String name) {
        int dot = (name != null) ? name.lastIndexOf('.') : -1;
        return (dot > 0) ? name.substring(0, dot) : name;
    }

    // Method (private): andLabels(ImagePlus a, ImagePlus b)
    /**
     * Computes the logical AND of two label maps and returns a newly relabeled map.
     *
     * Details:
     *   1. For each pixel, if both {@code a} and {@code b} are non-zero at that location,
     *      mark that pixel as foreground in a temporary binary image.
     *   2. Convert that binary image to a fresh 16-bit connected-components label map.
     *   3. Copy calibration from {@code a}.
     *
     * This is how we generate "combo markers" like "MarkerA+MarkerB" in the no-Hu pipeline.
     * Cells that are positive in both markers become a new set of objects in the combo.
     *
     * @param a  First 16-bit label map.
     * @param b  Second 16-bit label map.
     * @return   New 16-bit label map for the intersection, with contiguous IDs.
     */

    private static ImagePlus andLabels(ImagePlus a, ImagePlus b) {
        int w = a.getWidth(), h = a.getHeight();
        short[] pa = (short[]) a.getProcessor().getPixels();
        short[] pb = (short[]) b.getProcessor().getPixels();
        byte[] bin = new byte[w * h];

        for (int i = 0, n = bin.length; i < n; i++) {
            int va = pa[i] & 0xFFFF;
            int vb = pb[i] & 0xFFFF;
            bin[i] = (byte) ((va > 0 && vb > 0) ? 255 : 0);
        }
        ImagePlus binary = new ImagePlus("and_bin", new ij.process.ByteProcessor(w, h, bin, null));
        binary.setCalibration(a.getCalibration());
        ImagePlus relabeled = PluginCalls.binaryToLabels(binary);
        binary.close();
        relabeled.setCalibration(a.getCalibration());
        return relabeled;
    }

    // Method (private): runSingleSpatialPerMarker(NoHuResult mr, MultiParams p)
    /**
     * Runs per-marker spatial analysis (single cell type) for every marker in a no-Hu run.
     *
     * For each marker name:
     *   - Builds the expected ROI zip path: {@code <marker>_ROIs_<baseName>.zip}.
     *   - Calls {@code Analysis.SingleCellTypeAnalysis(...)} to compute spatial metrics
     *     (e.g. nearest-neighbor distances, density maps, etc.).
     *   - Passes expansion radius and "save parametric" flags from {@code p.base}.
     *
     * A note about "Hu":
     *   - This pipeline is "no-Hu", but spatial analysis code assumes you can pass any
     *     marker name + ROI zip. We include whatever markers were processed. You already
     *     populate that list from {@code mp.markers}.
     *
     * If an expected ROI zip isn't found, we log and skip that marker.
     *
     * @param mr  The {@link NoHuResult} produced by {@link #run(MultiParams)}.
     * @param p   The same {@link MultiParams} (for spatial settings like expansion radius).
     *
     * Side effects:
     *   - Writes spatial CSVs/etc. to {@code mr.outDir}.
     *   - Logs failures but does not throw.
     */
    private void runSingleSpatialPerMarker(NoHuResult mr, MultiParams p) {
        if (mr == null || p == null) return;

        String maxPath = new File(mr.outDir, "MAX_" + mr.baseName + ".tif").getAbsolutePath();
        String gangliaZip = (mr.nGanglia != null && mr.nGanglia > 0)
                ? new File(mr.outDir, "Ganglia_ROIs_" + mr.baseName + ".zip").getAbsolutePath()
                : "NA";
        String outDir = mr.outDir.getAbsolutePath();

        double expansionUm = (p.base.spatialExpansionUm != null) ? p.base.spatialExpansionUm : 6.5;
        boolean saveParametric = (p.base.spatialSaveParametric != null) && p.base.spatialSaveParametric;

        // include Hu + all subtype markers
        java.util.List<String> names = new java.util.ArrayList<>();
        for (MarkerSpec m : p.markers) names.add(m.name);

        for (String name : names) {
            // Hu uses the pre-existing Neuron_ROIs_<baseName>.zip
            File roiZipFile = new File(mr.outDir, name + "_ROIs_" + mr.baseName + ".zip");

            // fallback if someone saved Hu under a different scheme
            if (!roiZipFile.isFile() && name.equals("Hu")) {
                File alt = new File(mr.outDir, "Hu_ROIs_" + mr.baseName + ".zip");
                if (alt.isFile()) roiZipFile = alt;
            }

            if (!roiZipFile.isFile()) {
                IJ.log("Spatial single: missing ROI zip for " + name + " (" + roiZipFile.getName() + ")");
                continue;
            }

            try {
                new Analysis.SingleCellTypeAnalysis(
                        maxPath,
                        roiZipFile.getAbsolutePath(),
                        null,
                        outDir,
                        name,
                        expansionUm,
                        saveParametric
                ).execute();
            } catch (Exception ex) {
                IJ.log("Spatial single (" + name + ") failed: " + ex.getMessage());
            }
        }


    }
}
