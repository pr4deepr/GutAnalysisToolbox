package Features.AnalyseWorkflows;

import Features.Core.Params;
import Features.Tools.*;
import UI.panes.Tools.ReviewUI;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.macro.Interpreter;
import ij.plugin.frame.RoiManager;

import static Features.Tools.RoiManagerHelper.*;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
// Class: NeuronsMultiPipeline
/**
 * Full multiplex workflow for Hu + subtype markers.
 *
 * This pipeline:
 *  - Runs the Hu pipeline once to get Hu neuron labels and (optionally) ganglia.
 *  - For each additional marker channel:
 *      * Segments that channel (or imports ROIs),
 *      * Gates detections to Hu by overlap fraction,
 *      * Lets the user review the gated ROIs,
 *      * Saves ROIs/overlays/counts.
 *  - Builds pairwise combinations (markerA+markerB) by ANDing Hu-gated masks.
 *  - Exports a summary CSV (per marker / combo, per ganglion if available).
 *  - Optionally runs spatial analysis (pairwise and per-marker).
 *
 * This is the "Hu-gated multiplex" analysis: every subtype marker is interpreted
 * in terms of which Hu-positive cells express it.
 */

public class NeuronsMultiPipeline {

    // Inner class: MarkerSpec
    /**
     * Describes one subtype marker/channel in the multiplex workflow.
     *
     * Fields:
     *  - {@code name}: marker name (used in output file names and UI labels).
     *  - {@code channel}: 1-based channel index in the max projection.
     *  - {@code prob}/{@code nms}: optional StarDist thresholds overriding {@code mp.multiProb/multiNms}.
     *  - {@code customRoisZip}: optional existing ROI zip; if provided, segmentation is skipped
     *    and those ROIs are rasterized instead.
     *
     * Utility:
     *  - {@link #copy()} produces a deep copy suitable for cloning {@link MultiParams}.
     */

    public static final class MarkerSpec {
        public final String name;
        public final int channel;      // 1-based
        public Double prob;            // optional
        public Double nms;             // optional


        public File customRoisZip;     // optional: user-supplied ROI zip

        public MarkerSpec(String name, int channel) {
            this.name = name;
            this.channel = channel;
        }
        public MarkerSpec withThresh(Double prob, Double nms) {
            this.prob = prob; this.nms = nms; return this;
        }
        public MarkerSpec withCustomRois(File zip) {
            this.customRoisZip = zip;
            return this;
        }

        public MarkerSpec copy() {
            MarkerSpec m = new MarkerSpec(this.name, this.channel);
            m.prob = this.prob;
            m.nms  = this.nms;
            m.customRoisZip = this.customRoisZip;
            return m;
        }
    }

    // Inner class: MultiParams
    /**
     * Configuration for a multiplex (Hu + subtype) run.
     *
     * Fields:
     *  - {@code base}: a {@link Params} describing Hu channel, ganglia mode, scaling, etc.
     *  - {@code subtypeModelZip}: StarDist model ZIP for subtypes.
     *  - {@code multiProb}/{@code multiNms}: default StarDist thresholds for subtype segmentation.
     *  - {@code overlapFrac}: minimum fractional overlap required for a Hu neuron to count
     *                         as positive for this marker.
     *  - {@code markers}: list of subtype {@link MarkerSpec}s to evaluate against Hu.
     *
     * Includes {@link #copy()} which deep-copies everything (including {@code base} and each marker).
     */

    public static final class MultiParams {
        public Params base;                          // your existing Params (Hu + ganglia config)
        public String subtypeModelZip;               // StarDist model zip for subtype channels
        public double multiProb   = 0.50;            // default StarDist thresholds for subtype
        public double multiNms    = 0.30;
        public double overlapFrac = 0.40;            // Hu label must be >= this fraction covered by marker

        public final List<MarkerSpec> markers = new ArrayList<>();

        public MultiParams copy() {
            MultiParams c = new MultiParams();
            c.base           = (this.base != null) ? this.base.copy() : null; // needs Params.copy()
            c.subtypeModelZip = this.subtypeModelZip;
            c.multiProb       = this.multiProb;
            c.multiNms        = this.multiNms;
            c.overlapFrac     = this.overlapFrac;

            // deep-copy markers
            for (MarkerSpec m : this.markers) {
                if (m != null) c.markers.add(m.copy());
            }
            return c;
        }


    }

    // Inner class: MultiResult
    /**
     * Output bundle summarizing a Hu + multiplex run.
     *
     * Contains:
     *  - {@code outDir}: destination directory for all saved outputs.
     *  - {@code baseName}: basename of the source image.
     *  - {@code max}: the final max-projection ImagePlus (for thumbnails and overlays).
     *  - {@code totalHu}: total Hu-positive neuron count after review.
     *  - {@code nGanglia}: number of ganglia detected (nullable if ganglia disabled).
     *  - {@code gangliaAreaUm2}: area per ganglion in µm² (nullable).
     *  - {@code gangliaLabels}: ganglia label map (nullable).
     *  - {@code totals}: map markerOrCombo → Hu-gated neuron count.
     *  - {@code perGanglia}: map markerOrCombo → array[ganglionId] of Hu-gated neuron counts.
     *  - {@code doSpatialAnalysis}: whether spatial analysis should be executed downstream.
     *  - {@code neuronsPerGanglion}: Hu-only neuron counts per ganglion (index 1..G).
     *
     * This bundle is passed to downstream UI panels and spatial-analysis runners.
     */

    public static final class MultiResult {
        public final File outDir;
        public final String baseName;
        public final ImagePlus max;                 // preview / thumbnails
        public final int totalHu;                   // total Hu neurons
        public final Integer nGanglia;              // may be null
        public final double[] gangliaAreaUm2;       // may be null
        public final ImagePlus gangliaLabels;       // may be null

        // marker or combo name -> total Hu-gated neuron count
        public final LinkedHashMap<String,Integer> totals;

        // marker or combo name -> neurons-per-ganglion array (1..G)
        public final LinkedHashMap<String,int[]> perGanglia;
        public final Boolean doSpatialAnalysis;
        public final int[]   neuronsPerGanglion; // Hu-only, index 1..n (0 unused)


        public MultiResult(File outDir,
                           String baseName,
                           ImagePlus max,
                           int totalHu,
                           Integer nGanglia,
                           double[] gangliaAreaUm2,
                           ImagePlus gangliaLabels,
                           LinkedHashMap<String,Integer> totals,
                           LinkedHashMap<String,int[]> perGanglia, Boolean doSpatialAnalysis, int[] neuronsPerGanglion) {
            this.outDir = outDir;
            this.baseName = baseName;
            this.max = max;
            this.totalHu = totalHu;
            this.nGanglia = nGanglia;
            this.gangliaAreaUm2 = gangliaAreaUm2;
            this.gangliaLabels = gangliaLabels;
            this.totals = totals;
            this.perGanglia = perGanglia;
            this.doSpatialAnalysis = doSpatialAnalysis;

            this.neuronsPerGanglion = neuronsPerGanglion;
        }
    }


    // Method: run(MultiParams mp)
    /**
     * Executes the full Hu-gated multiplex workflow.
     *
     * Steps:
     *   1. Run the Hu pipeline in "return mode" to get:
     *        - Hu neuron label map (after user review),
     *        - ganglia labels / counts (if enabled),
     *        - MAX projection and output directory.
     *
     *   2. For each marker in {@code mp.markers}:
     *        a. Extract that marker's channel from MAX and rescale if needed.
     *        b. Segment it with StarDist (or import ROIs from a zip).
     *        c. Filter/resize labels and remove tiny/border objects.
     *        d. Gate those detections against the Hu label map:
     *             - A Hu neuron is considered "positive" if
     *               the overlap with this marker ≥ {@code mp.overlapFrac}.
     *        e. Seed the ROI Manager with the gated Hu neurons, pop up an interactive
     *           review dialog where the user can edit them, then rebuild a clean
     *           label map from the edits.
     *        f. Save final ROIs, save overlay images, and record per-marker counts.
     *        g. If ganglia are available, count per-ganglion marker expression.
     *
     *   3. Build pairwise AND combinations across markers:
     *        - Intersect ("AND") the Hu-gated masks from two markers.
     *        - Save ROIs/overlays and include them in per-ganglion stats.
     *
     *   4. Write a multiplex CSV summarizing Hu totals, per-marker totals,
     *      pairwise combo totals, per-ganglion breakdowns, and ganglia areas.
     *
     *   5. Optionally run spatial analysis:
     *        - Pairwise two-marker spatial analysis.
     *        - Per-marker single-cell-type spatial analysis.
     *
     *   6. Show a summary UI (ResultsMultiUI) on the EDT.
     *
     * Side effects:
     *   - Creates/overwrites files in the output directory for ROIs, overlays, TIFFs, and CSVs.
     *   - Shows interactive review dialogs during execution.
     *
     * @param mp  Fully-specified multiplex configuration.
     * @throws IllegalArgumentException if required StarDist model(s) or markers are missing.
     */

    public void run(MultiParams mp) {


        try {
            if (mp == null || mp.base == null) throw new IllegalArgumentException("MultiParams/base cannot be null");
            if (mp.subtypeModelZip == null || !new File(mp.subtypeModelZip).isFile())
                throw new IllegalArgumentException("Subtype StarDist model not found: " + mp.subtypeModelZip);
            if (mp.markers.isEmpty()) throw new IllegalArgumentException("No markers provided.");


            final int perMarkerSteps = 4;
            final int comboSteps = 1;
            final int nm = mp.markers.size();
            final int nCombos = (nm * (nm - 1)) / 2;

            // total = Hu + per-marker + combos + 1(final CSV)
            int totalSteps = NeuronsHuPipeline.estimateSteps(mp.base)
                    + (perMarkerSteps * nm)
                    + (comboSteps * nCombos)
                    + 1;

            ProgressUI progress = new ProgressUI("Hu + Multi-channel");
            progress.start(totalSteps);

            // 1) Run Hu once (returns MAX, Hu labels, ganglia info)
            NeuronsHuPipeline.HuResult hu = new NeuronsHuPipeline().run(mp.base, /*huReturn=*/true, progress);
            Interpreter.batchMode = true;
            ImagePlus max = hu.max;
            ImagePlus huLab = hu.neuronLabels;
            int totalHu = hu.totalNeuronCount;


            // 2) Common bits for rescale math
            double pxUm = max.getCalibration().pixelWidth;
            double scale = (mp.base.trainingRescaleFactor > 0) ? mp.base.trainingRescaleFactor : 1.0;
            double targetPxUm = mp.base.trainingPixelSizeUm / scale;
            double scaleFactor = (mp.base.rescaleToTrainingPx && pxUm > 0) ? (pxUm / targetPxUm) : 1.0;
            if (Math.abs(scaleFactor - 1.0) < 1e-3) scaleFactor = 1.0;

            // min size for subtype sanity (macro neuron_lower_limit in microns → pixels in segInput scale)
            int subtypeMinPx = 0;
            if (mp.base.neuronSegMinMicron != null && pxUm > 0) {
                double eff = (scaleFactor == 1.0) ? pxUm : (targetPxUm); // segInput calibration after rescale
                subtypeMinPx = (int) Math.max(1, Math.round(mp.base.neuronSegMinMicron / eff));
            }

            // 3) Collect results
            File outDir = hu.outDir;
            String baseName = hu.baseName;
            LinkedHashMap<String, Integer> totals = new LinkedHashMap<>();
            LinkedHashMap<String, int[]> perGanglia = new LinkedHashMap<>();

            double[] gangliaArea = hu.gangliaAreaUm2;       // may be null
            Integer nGanglia = hu.nGanglia;             // may be null

            // For combos later
            Map<String, boolean[]> keepMaskByMarker = new LinkedHashMap<>();

            RmHandle rmh = ensureGlobalRM();
            RoiManager rm = rmh.rm;
            rm.setVisible(false);

            // 4) Loop each marker

            for (MarkerSpec m : mp.markers) {
                ij.macro.Interpreter.batchMode = true;
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

                    markerLabels = Features.Core.PluginCalls.runStarDist2DLabel(segInput, mp.subtypeModelZip, prob, nms);
                    markerLabels = Features.Core.PluginCalls.removeBorderLabels(markerLabels);
                }


                if (subtypeMinPx > 0)
                    markerLabels = Features.Core.PluginCalls.labelMinSizeFilterPx(markerLabels, subtypeMinPx);
                progress.stopPulse("Segment done: " + m.name);
                progress.step("Postprocess/resize: " + m.name);
                if (markerLabels.getWidth() != max.getWidth() || markerLabels.getHeight() != max.getHeight()) {
                    markerLabels = ImageOps.resizeTo(markerLabels, max.getWidth(), max.getHeight());
                }

                // Determine which Hu labels are positive for this marker (fractional overlap >= overlapFrac)
                boolean[] keep = Features.Tools.LabelOps.neuronsPositiveByOverlap(huLab, markerLabels, mp.overlapFrac);
                keepMaskByMarker.put(m.name, keep);

                // Build filtered Hu label map for this marker (for ROI export / ganglia counts)
                ImagePlus filteredLabels = Features.Tools.LabelOps.keepHuLabels(huLab, keep);


                // Seed RM with current Hu-gated labels
                rm.reset();
                Features.Core.PluginCalls.labelsToRois(filteredLabels);
                syncToSingleton(new RoiManager[]{rm});

                //Build our backdrop
                ImagePlus backdrop = ch.duplicate();
                IJ.run(backdrop, "Red", "");
                IJ.resetMinAndMax(backdrop);

                progress.step("Review: " + m.name);
                // Launch review and rebuild labels from edited ROIs
                ij.macro.Interpreter.batchMode = false;
                progress.setVisible(false);
                ImagePlus reviewed = ReviewUI.reviewAndRebuildLabels(
                        backdrop,
                        rm,
                        m.name + " (Hu-gated)",
                        max.getCalibration(),
                        filteredLabels
                );
                progress.setVisible(true);
                ij.macro.Interpreter.batchMode = true;
                Roi[] edited = rm.getRoisAsArray();

                progress.step("Save: " + m.name);
                // Count + save ROIs
                int markerTotal = countLabels(reviewed);
                totals.put(m.name, markerTotal);


                rm.reset();
                for (ij.gui.Roi r : edited) if (r != null) rm.addRoi((ij.gui.Roi) r.clone());
                if (rm.getCount() > 0) {
                    OutputIO.saveRois(rm, new File(outDir, m.name + "_ROIs_" + baseName + ".zip"));
                    if (mp.base.saveFlattenedOverlay)
                        OutputIO.saveFlattenedOverlay(max, rm, new File(outDir, "MAX_" + baseName + "_" + m.name + "_overlay.tif"));
                }
                rm.reset();
                rm.setVisible(false);

                // Per-ganglion counts if available
                if (hu.gangliaLabels != null) {
                    GangliaOps.Result rM = GangliaOps.countPerGanglion(reviewed, hu.gangliaLabels);
                    perGanglia.put(m.name, rM.countsPerGanglion);
                }

                // cleanup
                ch.close();
                segInput.close();
                markerLabels.close();
                reviewed.close();
                filteredLabels.close();
            }

            // 5) Build combos (AND of keep arrays)
            List<String> names = new ArrayList<>(keepMaskByMarker.keySet());
            for (int i = 0; i < names.size(); i++) {
                for (int j = i + 1; j < names.size(); j++) {
                    String comboName = names.get(i) + "+" + names.get(j);
                    boolean[] a = keepMaskByMarker.get(names.get(i));
                    boolean[] b = keepMaskByMarker.get(names.get(j));
                    boolean[] and = andMasks(a, b);
                    ImagePlus lab = LabelOps.keepHuLabels(huLab, and);
                    int n = countLabels(lab);
                    totals.put(comboName, n);

                    if (hu.gangliaLabels != null) {
                        GangliaOps.Result rc = GangliaOps.countPerGanglion(lab, hu.gangliaLabels);
                        perGanglia.put(comboName, rc.countsPerGanglion);
                    }
                    progress.step("Save combo: " + comboName);
                    rm.reset();
                    Features.Core.PluginCalls.labelsToRois(lab);
                    syncToSingleton(new RoiManager[]{rm});
                    if (rm.getCount() > 0) {
                        OutputIO.saveRois(rm, new File(outDir, comboName + "_ROIs_" + baseName + ".zip"));
                        if (mp.base.saveFlattenedOverlay)
                            OutputIO.saveFlattenedOverlay(max, rm, new File(outDir, "MAX_" + baseName + "_" + comboName + "_overlay.tif"));
                    }
                    rm.reset();
                    rm.setVisible(false);
                    lab.close();
                }
            }


            // 6) Write the macro-style multi CSV
            OutputIO.writeMultiCsv(
                    new File(outDir, "Analysis_Hu_" + baseName + "_cell_counts_multi.csv"),
                    baseName,
                    totalHu,
                    nGanglia,
                    totals,
                    perGanglia,
                    gangliaArea
            );

            progress.close();

            MultiResult mr = new MultiResult(
                    outDir, baseName, max, totalHu,
                    nGanglia, gangliaArea, hu.gangliaLabels,
                    totals, perGanglia, mp.base.doSpatialAnalysis, hu.neuronsPerGanglion
            );

            RoiManager rmRev = rmh.rm;
            rmRev.setVisible(false);
            rmRev.reset();
            maybeCloseRM(rmh);

            if (mp.base.doSpatialAnalysis) {
                runSpatialFromHu(mr, mp);
                runSingleSpatialPerMarker(mr, mp);
            }

            SwingUtilities.invokeLater(() -> UI.panes.Results.ResultsMultiUI.promptAndMaybeShow(mr));
        }finally {
            Interpreter.batchMode = false;
        }

    }

    // Method (private): andMasks(boolean[] a, boolean[] b)
    /**
     * Element-wise AND of two boolean arrays of possibly different lengths.
     *
     * This is used to build combo markers (e.g. "Marker1+Marker2") in the Hu-gated pipeline:
     *   - We track, for each Hu neuron ID, whether it was "kept" (positive) for Marker1
     *     and separately for Marker2.
     *   - The AND array marks Hu neurons that were positive for BOTH markers.
     *
     * Implementation detail:
     *   - The result length is max(a.length, b.length).
     *   - Missing indices in the shorter array are treated as false.
     *
     * @param a  Inclusion mask for marker A, indexed by Hu neuron ID.
     * @param b  Inclusion mask for marker B, indexed by Hu neuron ID.
     * @return   Array where out[i] = a[i] && b[i].
     */
    private static boolean[] andMasks(boolean[] a, boolean[] b) {
        int n = Math.max(a.length, b.length);
        boolean[] out = new boolean[n];
        for (int i = 0; i < n; i++) {
            boolean ai = (i < a.length) && a[i];
            boolean bi = (i < b.length) && b[i];
            out[i] = ai && bi;
        }
        return out;
    }


    // Method (private): runSpatialFromHu(MultiResult mr, MultiParams p)
    /**
     * Runs pairwise spatial analysis between every distinct pair of markers from
     * a Hu-gated multiplex run.
     *
     * For each pair (A,B):
     *   - Looks up their exported ROI zips:
     *       A_ROIs_<baseName>.zip
     *       B_ROIs_<baseName>.zip
     *   - Calls {@code Analysis.TwoCellTypeAnalysis(...)} to compute spatial co-localization,
     *     proximity, etc., writing the outputs into {@code mr.outDir}.
     *
     * Includes:
     *   - expansion radius (microns) from {@code p.base.spatialExpansionUm}
     *   - flag for saving per-object parametric output from {@code p.base.spatialSaveParametric}
     *
     * If either ROI zip is missing, we log a note and skip that pair.
     *
     * Why "Hu-gated":
     *   - Marker ROIs are already Hu-gated when saved (only Hu neurons that pass a given marker's
     *     overlapFrac are exported). So this spatial analysis is effectively "spatial relationships
     *     between Hu+MarkerA cells and Hu+MarkerB cells".
     *
     * @param mr  The {@link MultiResult} object from the multiplex run (contains outDir/baseName).
     * @param p   The same {@link MultiParams} used to run the pipeline, for spatial settings.
     *
     * Side effects:
     *   - Writes spatial analysis CSVs/tables/plots to {@code mr.outDir}.
     *   - Logs errors but does not throw.
     */
    private void runSpatialFromHu(MultiResult mr, MultiParams p) {
        if (mr == null || p == null) return;

        // if you gate this with a checkbox:
        // if (!Boolean.TRUE.equals(p.base.doSpatialAnalysis)) return;

        if (p.markers.size() < 2) {
            IJ.log("Spatial analysis skipped (need ≥ 2 markers).");
            return;
        }

        // Same inputs the Spatial pane expects
        String maxPath = new File(mr.outDir, "MAX_" + mr.baseName + ".tif").getAbsolutePath();
        String gangliaZip = (mr.nGanglia != null && mr.nGanglia > 0)
                ? new File(mr.outDir, "Ganglia_ROIs_" + mr.baseName + ".zip").getAbsolutePath()
                : "NA";
        String outDir = mr.outDir.getAbsolutePath();

        double expansionUm = (p.base.spatialExpansionUm != null) ? p.base.spatialExpansionUm : 6.5;
        boolean saveParametric = (p.base.spatialSaveParametric != null) && p.base.spatialSaveParametric;

        for (int i = 0; i < p.markers.size(); i++) {
            for (int j = i + 1; j < p.markers.size(); j++) {
                MarkerSpec a = p.markers.get(i);
                MarkerSpec b = p.markers.get(j);

                String roiA = new File(mr.outDir, a.name + "_ROIs_" + mr.baseName + ".zip").getAbsolutePath();
                String roiB = new File(mr.outDir, b.name + "_ROIs_" + mr.baseName + ".zip").getAbsolutePath();

                if (!new File(roiA).isFile() || !new File(roiB).isFile()) {
                    IJ.log("Spatial: missing ROI zips for " + a.name + " or " + b.name);
                    continue;
                }

                try {
                    new Analysis.TwoCellTypeAnalysis(
                            maxPath,
                            a.name, roiA,
                            b.name, roiB,
                            null,
                            outDir,
                            expansionUm,
                            saveParametric
                    ).execute();
                } catch (Exception ex) {
                    IJ.log("Spatial (" + a.name + " vs " + b.name + ") failed: " + ex.getMessage());
                }
            }
        }
    }


    // Method: runSingleSpatialPerMarker(MultiResult mr, MultiParams p)
    /**
     * Runs per-marker spatial analysis treating each marker independently
     * (plus Hu). This is similar to {@link #runSpatialFromHu} but for single
     * cell types instead of pairs.
     *
     * For each marker (and for Hu):
     *   - Locates the corresponding ROI zip saved during the run.
     *   - Calls the single-cell-type spatial analysis code.
     *   - Logs and skips if the ROI zip is missing.
     *
     * @param mr  The {@link MultiResult} bundle from this run.
     * @param p   The {@link MultiParams} specifying spatial settings such as
     *            expansion radius and whether to save parametric output.
     */

    private void runSingleSpatialPerMarker(MultiResult mr, MultiParams p) {
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
        names.add("Hu");
        for (MarkerSpec m : p.markers) names.add(m.name);

        for (String name : names) {
            // Hu uses the pre-existing Neuron_ROIs_<baseName>.zip
            File roiZipFile = name.equals("Hu")
                    ? new File(mr.outDir, "Neuron_ROIs_" + mr.baseName + ".zip")
                    : new File(mr.outDir, name + "_ROIs_" + mr.baseName + ".zip");

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


    // Method (private): countLabels(ImagePlus labels16)
    /**
     * Counts labeled objects in a Hu- or combo-derived label map.
     *
     * We assume the label map is a relabeled connected-component image
     * where each object ID is an integer in [1..K]. We just scan for
     * the largest ID and interpret that as "number of objects".
     *
     * Used after rebuilding ROIs into a label map post-review.
     *
     * @param labels16 16-bit label map image.
     * @return         K, i.e. how many labeled objects are present.
     */
    private static int countLabels(ImagePlus labels16) {
        // Label map with values 0..K; just find max label ID (they’re contiguous after binary re-label)
        short[] px = (short[]) labels16.getProcessor().getPixels();
        int max = 0;
        for (short v : px) {
            int u = v & 0xFFFF;
            if (u > max) max = u;
        }
        return max;
    }
}
