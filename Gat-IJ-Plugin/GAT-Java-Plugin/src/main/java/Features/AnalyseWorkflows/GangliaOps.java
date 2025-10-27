package Features.AnalyseWorkflows;

import Features.Core.Params;
import Features.Core.PluginCalls;
import Features.Tools.ProgressUI;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.WaitForUserDialog;
import ij.plugin.frame.RoiManager;

import static Features.Core.PluginCalls.clearThreshold;
import static Features.Tools.RoiManagerHelper.*;


// Class: GangliaOps
/**
 * Static utilities for detecting ganglia regions and computing per-ganglion statistics.
 * Supports multiple ganglia segmentation modes (DeepImageJ model, Hu-derived dilation,
 * imported ROIs, manual drawing), and downstream quantification such as neuron counts
 * per ganglion and ganglion area in µm².
 *
 * This class is stateless and not instantiable.
 */
public final class GangliaOps {
    private GangliaOps(){}

    // Method: segment(Params p, ImagePlus maxProjection, ImagePlus neuronLabels, ProgressUI progress)
    /**
     * Segments ganglia using the strategy specified in {@code p.gangliaMode}, and returns
     * a labeled 16-bit image where each ganglion is assigned a unique positive integer ID.
     *
     * Supported modes:
     * - DEEPIMAGEJ: run a trained model to infer ganglia ROIs.
     * - DEFINE_FROM_HU: dilate Hu-positive neuron labels and cluster them into regions.
     * - IMPORT_ROI: load user-supplied ROIs from a .zip and rasterize them.
     * - MANUAL: prompt the user to draw ganglia ROIs interactively.
     *
     * @param p                Pipeline parameters (contains segmentation mode, channels, etc.).
     * @param maxProjection    Flattened / projected image used as spatial reference.
     * @param neuronLabels     (Optional) neuron label map. Required for DEFINE_FROM_HU mode; ignored in others.
     * @param progress         Optional progress UI to pulse/step; may be null.
     * @return                 A 16-bit label map image in MAX projection space. Each ganglion ID ≥ 1.
     */
    public static ImagePlus segment(Params p, ImagePlus maxProjection, ImagePlus neuronLabels, ProgressUI progress) {
        switch (p.gangliaMode) {
            case DEFINE_FROM_HU:
                return defineFromHu(p, neuronLabels, maxProjection);
            case IMPORT_ROI:
                return importRoiToLabels(p, maxProjection);
            case MANUAL:
                return manualDrawToLabels(p,maxProjection);
            case DEEPIMAGEJ:
            default:
                return deepImageJ(p, maxProjection, progress);
        }
    }

    // Method: countPerGanglion(ImagePlus neuronLabels, ImagePlus gangliaLabels)
    /**
     * For each neuron label, computes its centroid and determines which ganglion
     * that centroid falls inside. Returns counts of neurons per ganglion, as well
     * as ganglion areas in µm².
     *
     * The method assumes:
     * - {@code neuronLabels} is a 16-bit label map where each neuron has a unique ID.
     * - {@code gangliaLabels} is a 16-bit label map where each ganglion has a unique ID.
     * - Both images are the same XY size.
     *
     * @param neuronLabels   Labeled neuron segmentation (16-bit). Each cell ID ≥ 1.
     * @param gangliaLabels  Labeled ganglia segmentation (16-bit). Each ganglion ID ≥ 1.
     * @return               A {@link Result} object containing:
     *                       - countsPerGanglion[id] = #neurons in ganglion 'id'
     *                       - areaUm2[id] = ganglion area in µm²
     *                       - maxGanglionId = highest ganglion ID seen
     */
    public static Result countPerGanglion(ImagePlus neuronLabels, ImagePlus gangliaLabels) {
        final int w = neuronLabels.getWidth(), h = neuronLabels.getHeight();
        final short[] nl = (short[]) neuronLabels.getProcessor().convertToShort(false).getPixels();
        final short[] gl = (short[]) gangliaLabels.getProcessor().convertToShort(false).getPixels();

        int maxN = 0, maxG = 0;
        for (short v : nl) if ((v & 0xffff) > maxN) maxN = (v & 0xffff);
        for (short v : gl) if ((v & 0xffff) > maxG) maxG = (v & 0xffff);

        if (maxN == 0 || maxG == 0) return new Result(new int[0], new double[0], 0);

        double[] sx = new double[maxN + 1], sy = new double[maxN + 1];
        int[] cnt = new int[maxN + 1];

        int idx = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++, idx++) {
                int id = nl[idx] & 0xffff;
                if (id > 0) { sx[id] += x; sy[id] += y; cnt[id]++; }
            }

        int[] perGanglion = new int[maxG + 1];
        for (int id = 1; id <= maxN; id++) {
            if (cnt[id] == 0) continue;
            int cx = (int)Math.round(sx[id] / cnt[id]);
            int cy = (int)Math.round(sy[id] / cnt[id]);
            if (cx < 0) cx = 0; if (cy < 0) cy = 0;
            if (cx >= w) cx = w - 1; if (cy >= h) cy = h - 1;
            int gid = gl[cy * w + cx] & 0xffff;
            if (gid > 0) perGanglion[gid]++;
        }

        // area in µm²
        int[] areaPx = new int[maxG + 1];
        for (short v : gl) {
            int gid = v & 0xffff;
            if (gid > 0) areaPx[gid]++;
        }
        double pxUm = gangliaLabels.getCalibration().pixelWidth > 0 ? gangliaLabels.getCalibration().pixelWidth : 1.0;
        double s = pxUm * pxUm;
        double[] areaUm2 = new double[maxG + 1];
        for (int g = 1; g <= maxG; g++) areaUm2[g] = areaPx[g] * s;

        return new Result(perGanglion, areaUm2, maxG);
    }



    // Method (private): deepImageJ(Params p, ImagePlus maxProjection, ImagePlus neuronLabels, ProgressUI progress)
    /**
     * Segments ganglia regions using a DeepImageJ model and converts the result
     * into a labeled (16-bit) ganglia map.
     *
     * Steps:
     *   1. Calls {@code PluginCalls.runDeepImageJForGanglia(...)} on the specified
     *      "fibres / neurites" and "cell body" channels.
     *   2. Converts the returned binary mask into a connected-components label image.
     *   3. Propagates calibration from {@code maxProjection}.
     *   4. Cleans up temporary binary images.
     *
     * Notes:
     *   - Uses {@code p.gangliaModelFolder} to locate the DeepImageJ model.
     *   - Uses {@code p.gangliaChannel} (fibres/neurites channel) and
     *     {@code p.gangliaCellChannel} (cell-body channel). If {@code gangliaCellChannel}
     *     isn't provided, falls back to the Hu channel.
     *
     * @param p               Full parameter object containing ganglia model path and channels.
     * @param maxProjection   The reference MAX/EDF projection for geometry and calibration.
     * @param progress        Progress UI callback for showing status/pulsing.
     * @return                A 16-bit label map where each ganglion has a unique ID ≥ 1.
     */
    private static ImagePlus deepImageJ(Params p, ImagePlus maxProjection, ProgressUI progress) {
        int fibresCh = (p.gangliaChannel > 0) ? p.gangliaChannel : 1;
        int cellCh   = (p.gangliaCellChannel != null && p.gangliaCellChannel > 0) ? p.gangliaCellChannel : p.huChannel;
        double minArea = (p.gangliaMinAreaUm2 != null) ? p.gangliaMinAreaUm2 : 200.0;
        ImagePlus bin = PluginCalls.runDeepImageJForGanglia(
                maxProjection, fibresCh, cellCh, p.gangliaModelFolder, minArea, p, progress);


        ImagePlus labels = PluginCalls.binaryToLabels(bin);
        labels.setCalibration(maxProjection.getCalibration());
        if (labels != bin){
            bin.changes = false;
            bin.close();
        }
        return labels;
    }

    // Method (private): defineFromHu(Params p, ImagePlus neuronLabels, ImagePlus ref)
    /**
     * Approximates ganglia by dilating Hu-positive neuron labels and grouping them.
     *
     * Steps (roughly parallels macro "DEFINE_FROM_HU"):
     *   1. Duplicates {@code neuronLabels}, thresholds it to build a binary mask of neurons.
     *   2. Computes how many iterations of binary dilation correspond to
     *      {@code p.huDilationMicron} microns in real units, based on
     *      {@code ref.getCalibration().pixelWidth}.
     *   3. Applies that many dilations to grow clusters.
     *   4. Converts the grown binary mask into a labeled ganglia image.
     *   5. Copies spatial calibration from {@code ref}.
     *
     * This is used when the user wants to infer “ganglia” by spatial proximity
     * of Hu+ neurons, rather than using a trained model.
     *
     * @param p             Params holding {@code huDilationMicron}.
     * @param neuronLabels  16-bit Hu-neuron label map. Used as seeds.
     * @param ref           Reference image (e.g. MAX projection) for calibration.
     * @return              16-bit ganglia label map, where each contiguous
     *                      dilated region becomes its own ganglion ID.
     * @throws IllegalStateException if the image is not calibrated in microns and
     *                               {@code p.huDilationMicron} can't be converted.
     */

    private static ImagePlus defineFromHu(Params p, ImagePlus neuronLabels, ImagePlus ref) {
        // labels -> binary (inline)
        ImagePlus bin = neuronLabels.duplicate();
        bin.show();
        IJ.run(bin, "Select None", "");
        IJ.setThreshold(bin, 1, 65535);
        IJ.run(bin, "Convert to Mask", "");
        clearThreshold(bin);

        double pxUm = (ref.getCalibration() != null) ? ref.getCalibration().pixelWidth : 0.0;
        if (pxUm <= 0) throw new IllegalStateException("Image must be calibrated in microns.");
        int iters = Math.max(0, (int) Math.round(p.huDilationMicron / pxUm));  // allow 0

        for (int i = 0; i < iters; i++) IJ.run(bin, "Dilate", "");

        ImagePlus labels = PluginCalls.binaryToLabels(bin);
        labels.setCalibration(ref.getCalibration());
        if (labels != bin) { bin.changes = false; bin.close(); }
        return labels;
    }

    // Method (private): importRoiToLabels(Params p, ImagePlus ref)
    /**
     * Builds a ganglia label map from a user-supplied ROI .zip file.
     *
     * Steps:
     *   1. Opens (loads) an ROI set from {@code p.customGangliaRoiZip} into a shared
     *      global {@link RoiManager}.
     *   2. Rasterize those ROIs onto a blank image with the same size as {@code ref}.
     *   3. Converts that rasterized binary mask to a 16-bit connected-component
     *      label image.
     *   4. Applies the calibration and returns the label map.
     *
     * This is used when {@code p.gangliaMode == IMPORT_ROI}. No model inference
     * or Hu-based dilation is performed: we trust the provided ROIs.
     *
     * @param p    Params whose {@code customGangliaRoiZip} points to a .zip of ROIs.
     * @param ref  Reference image for canvas size and calibration.
     * @return     16-bit ganglia label map.
     * @throws IllegalArgumentException if {@code p.customGangliaRoiZip} is missing or empty.
     */

    private static ImagePlus importRoiToLabels(Params p, ImagePlus ref) {
        if (p.customGangliaRoiZip == null || p.customGangliaRoiZip.isEmpty())
            throw new IllegalArgumentException("Custom ROI zip path is empty.");
        RmHandle rmh = ensureGlobalRM();
        RoiManager rm = rmh.rm;
        rm.reset();
        rm.setVisible(false);
        rm.runCommand("Open", p.customGangliaRoiZip);
        ImagePlus bin = PluginCalls.roisToBinary(ref, rm);
        rm.reset();
        ImagePlus lab = PluginCalls.binaryToLabels(bin);
        lab.setCalibration(ref.getCalibration());
        if (lab != bin) bin.close();
        return lab;
    }

    // Method: areaPerGanglionUm2(ImagePlus gangliaLabels)
    /**
     * Computes the planar area (in µm²) of each ganglion label in a ganglia label map.
     * Index {@code g} in the returned array corresponds to ganglion ID {@code g}
     * in the label map.
     *
     * @param gangliaLabels 16-bit label map of ganglia regions. Must have valid spatial calibration.
     * @return              Array where index g is the area of ganglion g in µm².
     *                      Index 0 is unused.
     */
    public static double[] areaPerGanglionUm2(ImagePlus gangliaLabels) {
        short[] gl = (short[]) gangliaLabels.getProcessor().convertToShort(false).getPixels();
        int maxG = 0;
        for (short v : gl) { int g = v & 0xffff; if (g > maxG) maxG = g; }
        int[] areaPx = new int[maxG + 1];
        for (short v : gl) { int g = v & 0xffff; if (g > 0) areaPx[g]++; }

        double pxUm = gangliaLabels.getCalibration().pixelWidth > 0
                ? gangliaLabels.getCalibration().pixelWidth : 1.0;
        double s = pxUm * pxUm;

        double[] areaUm2 = new double[maxG + 1];
        for (int g = 1; g <= maxG; g++) areaUm2[g] = areaPx[g] * s;
        return areaUm2;
    }


    // Method (private): manualDrawToLabels(Params p, ImagePlus ref)
    /**
     * Interactive/manual ganglia definition.
     *
     * Workflow:
     *   1. Builds an RGB "review" image that helps visualize fibres + Hu channels
     *      (falls back to {@code ref} if RGB fusion fails).
     *   2. Shows the image and a visible {@link RoiManager}. The user is instructed
     *      to draw each ganglion outline (freehand/polygon) and press 'T' to add
     *      each region to the ROI Manager.
     *   3. Blocks on a {@link WaitForUserDialog} until the user clicks OK.
     *   4. Converts the final ROI set to a binary mask and then to a 16-bit
     *      label map.
     *   5. Applies calibration, hides the ROI Manager again, and disposes of UI images.
     *
     * Used when {@code p.gangliaMode == MANUAL}.
     *
     * @param p    Params with ganglia channel info for RGB overlay building.
     * @param ref  Reference MAX projection (used for canvas size and calibration).
     * @return     16-bit ganglia label map, one ID per user-drawn ROI.
     */

    private static ImagePlus manualDrawToLabels(Params p, ImagePlus ref) {
        // Use shared RM; caller/pipeline will close via maybeCloseRM(...)
        RmHandle rmh = ensureGlobalRM();
        RoiManager rm = rmh.rm;
        rm.reset();
        rm.setVisible(true);

        ImagePlus review;
        try {
            review = PluginCalls.buildGangliaRgbForOverlay(ref, p.gangliaChannel, p.huChannel);
        } catch (Throwable t) {
            review = ref.duplicate();
        }
        ij.macro.Interpreter.batchMode = false;
        review.setTitle("Draw ganglia ROIs (press T to add)");
        IJ.resetMinAndMax(review);
        review.show();
        rm.runCommand("Show All with labels");


        IJ.setTool("freehand");
        new WaitForUserDialog(
                "Ganglia outline",
                "Draw each ganglion (Freehand/Polygon) and press 'T' to add to ROI Manager.\n" +
                        "Delete to remove.\n" +
                        "Click OK when done."
        ).show();

        ij.macro.Interpreter.batchMode = true;

        ImagePlus bin = PluginCalls.roisToBinary(review, rm);
        ImagePlus lab = PluginCalls.binaryToLabels(bin);
        lab.setCalibration(ref.getCalibration());


        rm.reset();
        rm.setVisible(false);
        bin.changes = false; bin.close();
        review.changes = false; review.close();

        return lab;
    }


    // Inner class: GangliaOps.Result
    /**
     * Container for ganglion quantification results.
     *
     * countsPerGanglion[g] = number of neurons assigned to ganglion ID g
     * areaUm2[g]           = area of ganglion ID g in square microns
     * maxGanglionId        = highest ganglion ID present
     */
    public static final class Result {
        public final int[] countsPerGanglion;  // index = ganglion id
        public final double[] areaUm2;         // index = ganglion id
        public final int maxGanglionId;
        public Result(int[] c, double[] a, int maxId) {
            this.countsPerGanglion = c; this.areaUm2 = a; this.maxGanglionId = maxId;
        }
    }


    // Method: keepGangliaWithAtLeast(ImagePlus gangliaLabels, int[] countsPerGanglion, int minCount)
    /**
     * Produces a binary mask (8-bit) containing only those ganglia that have at least
     * {@code minCount} neurons assigned to them.
     *
     * Pixels belonging to qualifying ganglia are set to 255, others to 0.
     * The returned image is titled exactly "ganglia_binary" to match downstream macros.
     *
     * @param gangliaLabels        16-bit label map of ganglia (each ganglion has an ID ≥ 1).
     * @param countsPerGanglion    Array where index g is the number of neurons in ganglion g.
     * @param minCount             Minimum neuron count required for a ganglion to be kept.
     * @return                     8-bit binary mask image ("ganglia_binary") in the same XY size,
     *                             with inherited calibration.
     */

    public static ImagePlus keepGangliaWithAtLeast(ImagePlus gangliaLabels, int[] countsPerGanglion, int minCount) {
        final int w = gangliaLabels.getWidth(), h = gangliaLabels.getHeight();
        final short[] gl = (short[]) gangliaLabels.getProcessor().convertToShort(false).getPixels();

        ImagePlus bin = ij.IJ.createImage("ganglia_binary", "8-bit black", w, h, 1);
        byte[] bp = (byte[]) bin.getProcessor().getPixels();

        int idx = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++, idx++) {
                int gid = gl[idx] & 0xffff;
                if (gid > 0 && gid < countsPerGanglion.length && countsPerGanglion[gid] >= minCount) {
                    bp[idx] = (byte) 255;
                }
            }
        }
        bin.setCalibration(gangliaLabels.getCalibration());
        return bin;
    }

}
