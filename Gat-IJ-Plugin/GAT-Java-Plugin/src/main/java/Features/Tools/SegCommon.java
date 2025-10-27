package Features.Tools;

import Features.Core.Params;
import Features.AnalyseWorkflows.NeuronsMultiPipeline;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

import static Features.Tools.RoiManagerHelper.*;

/**
 * Shared segmentation helpers used by the "Tuning" / preview tools and by
 * interactive parameter sweeps.
 *
 * Responsibilities:
 *  - Run Hu segmentation once with custom overrides (segmentHuOne)
 *  - Run subtype/marker segmentation once (segmentSubtypeOne)
 *  - Build quick-and-dirty ganglia previews from Hu ROIs
 *  - Save flattened overlay PNGs for UI previews
 *
 * Everything here is meant to be "single-shot" (one channel, one setting)
 * rather than running the whole pipeline.
 */
public final class SegCommon {


    /**
     * Result of a quick segmentation pass.
     *
     * rm:
     *   RoiManager containing one ROI per detected object.
     *
     * labels16:
     *   16-bit label map aligned to the MAX image. May be null for certain subtype runs.
     *
     * count:
     *   number of detected objects (basically max label ID).
     *
     * Call dispose() after you're done to hide/close temporary images and clear the RM.
     */
    public static final class SegResult {
        public final RoiManager rm;
        public final ImagePlus labels16;   // label map aligned to MAX (may be null for subtype)
        public final int count;

        public SegResult(RoiManager rm, ImagePlus labels16, int count) {
            this.rm = rm;
            this.labels16 = labels16;
            this.count = count;
        }

        /**
         * Dispose/cleanup helper:
         *  - Closes label map (without prompting to save).
         *  - Resets and hides the RoiManager.
         */
        public void dispose() {
            try {
                if (labels16 != null) { labels16.changes = false; labels16.close(); }
                if (rm != null) { rm.reset(); rm.setVisible(false); }
            } catch (Throwable ignore) {}
        }
    }

    // ----------------------- HU (single run) ----------------------

    /**
     * Segment Hu-positive neurons from a single channel and return ROIs + count.
     *
     * Steps (mirrors main Hu pipeline but simplified):
     *   1. Compute rescale factor so pixel size matches StarDist training size.
     *   2. Resize the channel if needed.
     *   3. Run StarDist to get a label map.
     *   4. Remove border-touching labels.
     *   5. Size-filter based on neuronSegLowerLimitUm (in microns → px).
     *   6. Resize labels back up to MAX size if we downscaled.
     *   7. Dump labels to a RoiManager.
     *
     * @param ch            single-channel image (Hu channel extracted from MAX).
     * @param max           the full MAX/EDF projection image (used for calibration and alignment).
     * @param p             Params containing StarDist model path and size thresholds.
     * @param probOverride  if non-null, overrides p.probThresh for just this run (used by tuning sliders).
     *
     * @return SegResult with ROIs, final relabeled mask, and object count.
     *
     * @throws IllegalArgumentException if inputs are missing or invalid.
     */
    public static SegResult segmentHuOne(ImagePlus ch, ImagePlus max, Params p, Double probOverride) {
        if (ch == null || ch.getProcessor() == null) throw new IllegalArgumentException("channel image is null");
        if (max == null) throw new IllegalArgumentException("max image is null");
        if (p == null)  throw new IllegalArgumentException("Params cannot be null");
        if (p.stardistModelZip == null) throw new IllegalArgumentException("Params.stardistModelZip is null");

        // --- compute scale factor like your pipelines ---
        double pxUm = max.getCalibration() != null ? max.getCalibration().pixelWidth : 0.0;
        double scale = (p.trainingRescaleFactor > 0) ? p.trainingRescaleFactor : 1.0;
        double targetPxUm = (p.trainingPixelSizeUm > 0) ? (p.trainingPixelSizeUm / scale) : 0.0;
        double scaleFactor = (p.rescaleToTrainingPx && pxUm > 0 && targetPxUm > 0) ? (pxUm / targetPxUm) : 1.0;
        if (Math.abs(scaleFactor - 1.0) < 1e-3) scaleFactor = 1.0;

        ImagePlus segInput = (scaleFactor == 1.0)
                ? ch.duplicate()
                : ImageOps.resizeToIntensity(ch, (int)Math.round(ch.getWidth()*scaleFactor), (int)Math.round(ch.getHeight()*scaleFactor));

        try {
            double prob = (probOverride != null) ? probOverride
                    : (p.probThresh > 0 ? p.probThresh : 0.50);
            double nms  = (p.nmsThresh  > 0 ? p.nmsThresh  : 0.30);

            // --- StarDist label map on segInput scale ---
            ImagePlus labels = Features.Core.PluginCalls.runStarDist2DLabel(segInput, p.stardistModelZip, prob, nms);
            labels = Features.Core.PluginCalls.removeBorderLabels(labels);

            // --- size filter in pixels, using effective pixel size at segInput scale ---
            Double minMicron = (p.neuronSegLowerLimitUm != null) ? p.neuronSegLowerLimitUm : p.neuronSegMinMicron;
            int minPx = 0;
            if (minMicron != null && pxUm > 0) {
                double effUm = (scaleFactor == 1.0 || targetPxUm == 0.0) ? pxUm : targetPxUm;
                minPx = (int)Math.max(1, Math.round(minMicron / effUm));
            }
            if (minPx > 0) {
                labels = Features.Core.PluginCalls.labelMinSizeFilterPx(labels, minPx);
            }

            // --- back to MAX size if needed ---
            if (labels.getWidth() != max.getWidth() || labels.getHeight() != max.getHeight()) {
                labels = ImageOps.resizeTo(labels, max.getWidth(), max.getHeight());
                labels.setCalibration(max.getCalibration());
            }

            // --- labels → ROI Manager ---
            RmHandle rmh = ensureGlobalRM();
            RoiManager rm = rmh.rm;
            rm.reset(); rm.setVisible(false);
            Features.Core.PluginCalls.labelsToRois(labels);
            syncToSingleton(new RoiManager[]{rm});

            int count = countLabels(labels);
            return new SegResult(rm, labels, count);

        } finally {
            segInput.changes = false;
            segInput.close();
        }
    }

    // -------------------- Subtype (single run) --------------------

    /**
     * Segment one subtype/marker channel (e.g. ChAT+, VIP+, etc.) using MultiParams.
     *
     * Similar to segmentHuOne() except:
     *   - Uses mp.subtypeModelZip plus mp.multiProb/mp.multiNms thresholds.
     *   - Uses mp.base (a Params) for rescale rules.
     *   - Returns labels resized to MAX dimensions and pushed into a fresh RoiManager.
     *
     * This does NOT apply Hu gating. It's just "cells in this marker channel".
     * Hu gating happens in the full multi pipeline.
     *
     * @param ch    extracted single channel for the marker.
     * @param max   MAX/EDF projection for alignment/calibration.
     * @param mp    MultiParams from NeuronsMultiPipeline (must include mp.base).
     *
     * @return SegResult with ROIs, label map, and object count.
     */
    public static SegResult segmentSubtypeOne(ImagePlus ch, ImagePlus max, NeuronsMultiPipeline.MultiParams mp) {
        if (ch == null || ch.getProcessor() == null) throw new IllegalArgumentException("channel image is null");
        if (max == null) throw new IllegalArgumentException("max image is null");
        if (mp == null || mp.base == null) throw new IllegalArgumentException("MultiParams/base cannot be null");
        if (mp.subtypeModelZip == null) throw new IllegalArgumentException("MultiParams.subtypeModelZip is null");

        // scale like in your multi pipeline
        double pxUm = max.getCalibration() != null ? max.getCalibration().pixelWidth : 0.0;
        double scale = (mp.base.trainingRescaleFactor > 0) ? mp.base.trainingRescaleFactor : 1.0;
        double targetPxUm = (mp.base.trainingPixelSizeUm > 0) ? (mp.base.trainingPixelSizeUm / scale) : 0.0;
        double scaleFactor = (mp.base.rescaleToTrainingPx && pxUm > 0 && targetPxUm > 0) ? (pxUm / targetPxUm) : 1.0;
        if (Math.abs(scaleFactor - 1.0) < 1e-3) scaleFactor = 1.0;

        ImagePlus segInput = (scaleFactor == 1.0)
                ? ch.duplicate()
                : ImageOps.resizeToIntensity(ch, (int)Math.round(ch.getWidth()*scaleFactor), (int)Math.round(ch.getHeight()*scaleFactor));

        try {
            double prob = (mp.multiProb > 0) ? mp.multiProb : 0.50;
            double nms  = (mp.multiNms  > 0) ? mp.multiNms  : 0.30;

            ImagePlus labels = Features.Core.PluginCalls.runStarDist2DLabel(segInput, mp.subtypeModelZip, prob, nms);
            labels = Features.Core.PluginCalls.removeBorderLabels(labels);

            // optional min-size using same logic as in your multi pipeline
            Double minMicron = (mp.base.neuronSegLowerLimitUm != null)
                    ? mp.base.neuronSegLowerLimitUm
                    : mp.base.neuronSegMinMicron;
            int subtypeMinPx = 0;
            if (minMicron != null && pxUm > 0) {
                double effUm = (scaleFactor == 1.0 || targetPxUm == 0.0) ? pxUm : targetPxUm;
                subtypeMinPx = (int)Math.max(1, Math.round(minMicron / effUm));
            }
            if (subtypeMinPx > 0) {
                labels = Features.Core.PluginCalls.labelMinSizeFilterPx(labels, subtypeMinPx);
            }

            if (labels.getWidth() != max.getWidth() || labels.getHeight() != max.getHeight()) {
                labels = ImageOps.resizeTo(labels, max.getWidth(), max.getHeight());
                labels.setCalibration(max.getCalibration());
            }

            RmHandle rmh = ensureGlobalRM();
            RoiManager rm = rmh.rm;
            rm.reset(); rm.setVisible(false);
            Features.Core.PluginCalls.labelsToRois(labels);
            syncToSingleton(new RoiManager[]{rm});

            int count = countLabels(labels);
            return new SegResult(rm, labels, count);

        } finally {
            segInput.changes = false;
            segInput.close();
        }
    }

    // ------------------- Ganglia (Hu expansion) -------------------

    /**
     * Quick ganglia preview based on Hu neurons only.
     *
     * Used in tuning: "What does my ganglia look like if I just dilate Hu neurons by X µm?"
     *
     * Steps:
     *   1. Take all Hu ROIs, rasterize them to a binary mask.
     *   2. Morphologically dilate the mask N times, where N ≈ expansionUm / pixelSize.
     *   3. Connected components to get a ganglia label map.
     *
     * This is intentionally lightweight and approximate. The real pipeline might
     * call DeepImageJ etc.
     *
     * @param max         MAX projection (for size + calibration).
     * @param huRm        RoiManager containing Hu neurons.
     * @param expansionUm how far (µm) to dilate.
     * @return            16-bit label map of "ganglia-like blobs".
     */
    public static ImagePlus gangliaByExpansionPreview(ImagePlus max, RoiManager huRm, double expansionUm) {
        if (max == null) throw new IllegalArgumentException("max is null");
        if (huRm == null || huRm.getCount() == 0) throw new IllegalArgumentException("Hu ROI manager is empty");

        double pxUm = max.getCalibration() != null ? max.getCalibration().pixelWidth : 0.0;
        int iters = (pxUm > 0) ? Math.max(1, (int)Math.round(expansionUm / pxUm)) : 6;

        // ROIs → binary mask (8-bit) same size as MAX
        ImagePlus bin = Features.Core.PluginCalls.roisToBinary(max, huRm);

        // simple pixel dilation for 'iters' steps
        for (int k = 0; k < iters; k++) {
            IJ.run(bin, "Dilate", "");
        }

        // mask → labels
        ImagePlus labels = Features.Core.PluginCalls.binaryToLabels(bin);
        labels.setCalibration(max.getCalibration());

        // tidy
        bin.changes = false; bin.close();
        return labels;
    }


    /**
     * Save an RGB PNG preview of MAX with the given ROIs outlined in red.
     *
     * Steps:
     *   - Duplicate MAX
     *   - Build an Overlay with each ROI stroked red, width 1.5px
     *   - dup.flatten() → RGB
     *   - Write to disk as .png
     *
     * @param max      base image for context.
     * @param rm       RoiManager with ROIs to draw.
     * @param outDir   directory to save into (will mkdirs()).
     * @param fileName desired filename (".png" appended if missing).
     * @return         File pointing to the written PNG, or null on failure.
     */
    public static File saveOverlay(ImagePlus max, RoiManager rm, File outDir, String fileName) {
        try {
            if (!outDir.exists()) outDir.mkdirs();
            File out = new File(outDir, fileName.endsWith(".png") ? fileName : (fileName + ".png"));

            ImagePlus dup = max.duplicate();
            Overlay ov = new Overlay();
            for (Roi r : rm.getRoisAsArray()) {
                if (r == null) continue;
                Roi c = (Roi) r.clone();
                c.setStrokeColor(new Color(241, 7, 7));
                c.setStrokeWidth(1.5);
                c.setFillColor(null);
                ov.add(c);
            }
            dup.setOverlay(ov);
            ImagePlus flat = dup.flatten();
            BufferedImage bi = flat.getBufferedImage();
            ImageIO.write(bi, "PNG", out);

            dup.changes = false; dup.close();
            flat.changes = false; flat.close();
            return out;
        } catch (Exception ex) {
            IJ.handleException(ex);
            return null;
        }
    }

    /**
     * Save an RGB PNG where we first convert a label map or binary mask to ROIs,
     * then draw those ROIs on top of MAX.
     *
     * Convenience wrapper around saveOverlay() that takes a label/binary image
     * instead of an RoiManager.
     *
     * @param max           base image.
     * @param labelsOrMask  16-bit label map or 8-bit mask.
     * @param outDir        destination folder.
     * @param fileName      suggested filename (.png appended if missing).
     * @return              File pointing to saved PNG, or null on failure.
     */
    public static File saveMaskOverlay(ImagePlus max, ImagePlus labelsOrMask, File outDir, String fileName) {
        try {
            RmHandle rmh = ensureGlobalRM();
            RoiManager rm = rmh.rm;
            rm.reset(); rm.setVisible(false);

            Features.Core.PluginCalls.labelsToRois(labelsOrMask);
            syncToSingleton(new RoiManager[]{rm});

            File f = saveOverlay(max, rm, outDir, fileName);

            rm.reset(); rm.setVisible(false);
            return f;
        } catch (Exception ex) {
            IJ.handleException(ex);
            return null;
        }
    }

    /**
     * Count how many distinct objects are in a 16-bit label map.
     * We assume labels are contiguous integers 1..N.
     *
     * Implementation detail: just find the max pixel value.
     *
     * @param labels16 16-bit label map.
     * @return         number of labeled objects (max label ID).
     */
    public static int countLabels(ImagePlus labels16) {
        if (labels16 == null || labels16.getProcessor() == null) return 0;
        ImageProcessor ip = labels16.getProcessor();
        int w = ip.getWidth(), h = ip.getHeight();
        int max = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = ip.get(x, y) & 0xFFFF;
                if (v > max) max = v;
            }
        }
        return max;
    }

    private SegCommon() {}
}
