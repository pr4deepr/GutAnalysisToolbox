// Features/Tools/SegOne.java
package Features.Tools;

import Features.Core.Params;
import Features.AnalyseWorkflows.NeuronsMultiPipeline;
import UI.panes.Tools.TuningTools;
import ij.ImagePlus;

import java.io.File;
import java.util.Locale;
/**
 * Tiny "one-shot" runners used by the TuningTools UI.
 *
 * Each method:
 *   - runs a segmentation with a tweaked parameter (e.g. different rescale or prob),
 *   - counts objects,
 *   - writes a preview PNG overlay,
 *   - returns a TuningTools.Row { paramValue, count, previewFile }.
 *
 * The tuning UI can then show a table / gallery of results side-by-side so
 * the user can decide which parameter looks best
 *  */
public final class SegOne {

    /**
     * Sweep the RESCALE FACTOR for Hu segmentation.
     *
     * We:
     *   1. Copy base Params.
     *   2. Force rescaleToTrainingPx = true and trainingRescaleFactor = trainingRescale.
     *   3. Extract the Hu channel.
     *   4. Run segmentHuOne() with those params.
     *   5. Save an overlay PNG to outDir named "tune_rescale_<factor>.png".
     *   6. Return a row: (factor, objectCount, pngFile).
     *
     * @param max               MAX/EDF projection.
     * @param huCh              1-based Hu channel index.
     * @param base              baseline Params to copy/modify.
     * @param trainingRescale   candidate rescale factor to test.
     * @param outDir            output directory for preview PNG.
     * @return                  TuningTools.Row for UI tables.
     */
    public static TuningTools.Row runHuAtScale(ImagePlus max, int huCh, Params base, double trainingRescale, File outDir){
        Params p = (base != null) ? base.copy() : new Params();
        p.rescaleToTrainingPx   = true;
        p.trainingRescaleFactor = trainingRescale;

        ImagePlus ch = ImageOps.extractChannel(max, huCh);
        SegCommon.SegResult r  = SegCommon.segmentHuOne(ch, max, p, /*probOverride*/ null);
        File png = SegCommon.saveOverlay(max, r.rm, outDir, "tune_rescale_"+fmt(trainingRescale)+".png");
        cleanup(ch, r);
        return new TuningTools.Row(trainingRescale, r.count, png);
    }

    /**
     * Sweep the PROBABILITY THRESHOLD for Hu segmentation.
     *
     * Very similar to runHuAtScale, but here we override StarDist's probThresh
     * (via the probOverride argument in segmentHuOne) and keep the rescaleFactor fixed.
     *
     * @param max             MAX/EDF projection.
     * @param huCh            1-based Hu channel index.
     * @param base            baseline Params to copy/modify.
     * @param trainingRescale rescale factor to apply (kept constant during this sweep).
     * @param prob            StarDist probability threshold to test.
     * @param outDir          output directory for preview PNG.
     * @return                TuningTools.Row with (prob, count, previewPNG).
     */
    public static TuningTools.Row runHuAtProb(ImagePlus max, int huCh, Params base, double trainingRescale, double prob, File outDir){
        Params p = (base != null) ? base.copy() : new Params();
        p.rescaleToTrainingPx   = true;
        p.trainingRescaleFactor = trainingRescale;

        ImagePlus ch = ImageOps.extractChannel(max, huCh);
        SegCommon.SegResult r  = SegCommon.segmentHuOne(ch, max, p, /*probOverride*/ prob);
        File png = SegCommon.saveOverlay(max, r.rm, outDir, "tune_hu_prob_"+fmt(prob)+".png");
        cleanup(ch, r);
        return new TuningTools.Row(prob, r.count, png);
    }

    /**
     * Sweep the PROBABILITY THRESHOLD for a subtype/marker channel.
     *
     * This is used to tune mp.multiProb for subtypes.
     *
     * Steps:
     *   1. Copy the MultiParams (mp.copy()).
     *   2. Set multiProb = prob.
     *   3. Extract the subtype channel from max.
     *   4. Run segmentSubtypeOne() once.
     *   5. Save overlay PNG "tune_subtype_prob_<prob>.png".
     *   6. Return (prob, count, previewPNG).
     *
     * Note: mpc.base must not be null, because segmentSubtypeOne uses mpc.base
     *       for rescaling logic.
     *
     * @param max       MAX/EDF projection.
     * @param subtypeCh 1-based channel index for this subtype.
     * @param mp        original MultiParams (will be copied).
     * @param prob      candidate multiProb to test.
     * @param outDir    output directory for preview PNG.
     * @return          TuningTools.Row for the UI.
     */
    public static TuningTools.Row runSubtypeAtProb(ImagePlus max, int subtypeCh,
                                                   NeuronsMultiPipeline.MultiParams mp, double prob, File outDir){
        NeuronsMultiPipeline.MultiParams mpc = (mp != null) ? mp.copy() : new NeuronsMultiPipeline.MultiParams();
        mpc.multiProb = prob;

        // guard: segmentSubtypeOne requires base != null
        if (mpc.base == null)
            throw new IllegalArgumentException("MultiParams.base cannot be null for subtype sweep.");

        ImagePlus ch = ImageOps.extractChannel(max, subtypeCh);
        SegCommon.SegResult r  = SegCommon.segmentSubtypeOne(ch, max, mpc);
        File png = SegCommon.saveOverlay(max, r.rm, outDir, "tune_subtype_prob_"+fmt(prob)+".png");
        cleanup(ch, r);
        return new TuningTools.Row(prob, r.count, png);
    }

    /**
     * Sweep the ganglia "expansion radius" (in microns) derived from Hu neurons.
     *
     * This is a preview of DEFINE_FROM_HU style ganglia:
     *   - Segment Hu once.
     *   - Take the Hu ROIs and dilate them expansionUm microns.
     *   - Connected-components label that dilated mask.
     *   - Count how many blobs result.
     *   - Save a PNG overlay "tune_ganglia_<X>um.png".
     *
     * This helps the user guess how far Hu needs to be dilated to merge into a ganglion.
     *
     * @param max     MAX/EDF projection image.
     * @param huCh    1-based Hu channel.
     * @param base    baseline Params for Hu segmentation (copied internally).
     * @param um      dilation distance in microns to test.
     * @param outDir  output dir for the preview PNG.
     * @return        TuningTools.Row with (um, numGanglia, previewPNG).
     */
    public static TuningTools.Row runGangliaFromHuExpansion(ImagePlus max, int huCh, Params base, double um, File outDir){
        Params p = (base != null) ? base.copy() : new Params();

        //  segment Hu once using base
        ImagePlus ch = ImageOps.extractChannel(max, huCh);
        SegCommon.SegResult r  = SegCommon.segmentHuOne(ch, max, p, null);

        //  Preview labels by expansion (µm) → CC
        ImagePlus ganglia = SegCommon.gangliaByExpansionPreview(max, r.rm, um);

        //  Save preview; count CC
        int cc = SegCommon.countLabels(ganglia);
        File png = SegCommon.saveMaskOverlay(max, ganglia, outDir, "tune_ganglia_"+fmt(um)+"um.png");

        cleanup(ch, r);
        ganglia.changes=false; ganglia.close();
        return new TuningTools.Row(um, cc, png);
    }

    /**
     * INTERNAL: close temp channel image and SegResult.
     *
     * Used by the tuning code to ensure we don't leave extra windows or ROIs.
     *
     * @param ch channel ImagePlus we created.
     * @param r  SegResult from a segmentation run.
     */
    private static void cleanup(ImagePlus ch, SegCommon.SegResult r){ ch.close(); r.dispose(); }
    /**
     * INTERNAL: format a double for filenames and UI rows using US locale,
     * e.g. "0.500".
     *
     * @param d number to format.
     * @return  string like "0.500".
     */
    private static String fmt(double d){ return String.format(Locale.US,"%.3f", d); }

    private SegOne() {}
}
