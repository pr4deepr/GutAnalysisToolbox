package Features.Tools;

import Features.Core.PluginCalls;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;


/**
 * Utilities for reasoning about label maps (16-bit integer segmentation maps).
 * Mostly focused on "Hu-gated subtypes":
 *  - find which Hu neurons are positive for a marker,
 *  - keep only those Hu neurons,
 *  - rebuild contiguous labels from masks.
 */
public final class LabelOps {
    private LabelOps(){}

    /**
     * For each Hu neuron ID in a Hu label map, compute how much of that neuron's
     * area overlaps a marker label map. If the overlap fraction >= fracThresh,
     * mark that neuron as "positive".
     *
     * Returns a boolean[] keep[] where keep[id] is true if neuron 'id' is marker-positive.
     *
     * Assumptions:
     *   - huLabels is a 16-bit label map where each neuron has its own ID (1..N).
     *   - markerLabels is a 16-bit label map from StarDist/segmentation of
     *     some marker channel (nonzero means positive).
     *
     * @param huLabels      Hu neuron label map.
     * @param markerLabels  Marker-specific label map.
     * @param fracThresh    Minimum fractional overlap (0..1) required to call that neuron positive.
     * @return              boolean array of length maxHuId+1, where index==Hu label ID.
     */
    public static boolean[] neuronsPositiveByOverlap(ImagePlus huLabels, ImagePlus markerLabels, double fracThresh) {
        ImageProcessor hu = huLabels.getProcessor();
        ImageProcessor mk = markerLabels.getProcessor();
        int w = hu.getWidth(), h = hu.getHeight();

        // find max Hu ID
        int maxId = 0;
        for (int y=0; y<h; y++) {
            for (int x=0; x<w; x++) {
                int id = hu.get(x,y) & 0xFFFF;
                if (id > maxId) maxId = id;
            }
        }
        long[] total = new long[maxId + 1];
        long[] hits  = new long[maxId + 1];

        for (int y=0; y<h; y++) {
            for (int x=0; x<w; x++) {
                int id = hu.get(x,y) & 0xFFFF;
                if (id == 0) continue;
                total[id]++;
                if ((mk.get(x,y) & 0xFFFF) > 0) hits[id]++;
            }
        }

        boolean[] keep = new boolean[maxId + 1];
        for (int id=1; id<=maxId; id++) {
            if (total[id] == 0) { keep[id] = false; continue; }
            double frac = (double)hits[id] / (double)total[id];
            keep[id] = frac >= fracThresh;
        }
        return keep;
    }



    /**
     * Build a new label map that only keeps Hu neuron IDs marked as true in 'keep'.
     *
     * Steps:
     *   - For each pixel: if its Hu neuron ID is in-bounds and keep[id] is true, mark 255 in a binary mask.
     *   - Convert that binary mask to a fresh, contiguous 16-bit label image
     *     (connected components labeling).
     *   - Copy calibration from the input.
     *
     * Use case:
     *   After deciding which Hu neurons express MarkerX, this returns a clean,
     *   relabeled ImagePlus of only those MarkerX+ Hu neurons.
     *
     * @param huLabels Hu neuron label map (16-bit).
     * @param keep     boolean mask from neuronsPositiveByOverlap(...).
     * @return         New 16-bit label map ImagePlus of kept neurons only.
     */
    public static ImagePlus keepHuLabels(ImagePlus huLabels, boolean[] keep) {
        int w = huLabels.getWidth(), h = huLabels.getHeight();
        byte[] bin = new byte[w*h];

        int i=0;
        for (int y=0; y<h; y++) {
            for (int x=0; x<w; x++, i++) {
                int id = huLabels.getProcessor().get(x,y) & 0xFFFF;
                bin[i] = (byte)((id>0 && id < keep.length && keep[id]) ? 255 : 0);
            }
        }
        ImagePlus binary = new ImagePlus("keep_bin", new ij.process.ByteProcessor(w,h,bin,null));
        ImagePlus relabeled = PluginCalls.binaryToLabels(binary);
        // adopt calibration
        relabeled.setCalibration(huLabels.getCalibration());
        binary.close();
        return relabeled;
    }




}
