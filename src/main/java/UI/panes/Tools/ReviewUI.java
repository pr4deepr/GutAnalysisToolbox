
        package UI.panes.Tools;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.measure.Calibration;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

/**
 * Utility class for interactive ROI review and label map rebuilding.
 * <p>
 * This class wraps a common review flow used in neuron/ganglia validation:
 * </p>
 * <ol>
 *   <li>Show an image (typically a max projection or segmentation backdrop).</li>
 *   <li>Show all current ROIs from a {@link ij.plugin.frame.RoiManager} as an overlay.</li>
 *   <li>Let the user edit ROIs (add, delete, tweak vertex positions) using standard ImageJ tools.</li>
 *   <li>After the user confirms, rasterize the final ROIs into a label image
 *       where each ROI is assigned a unique integer ID.</li>
 *   <li>If the user removed everything, optionally fall back to a provided
 *       default label map.</li>
 * </ol>
 *
 * <p>
 * The goal is to give users a human-in-the-loop correction step before
 * finalizing segmentation masks. The resulting mask can then be passed
 * downstream for counting, morphology, ganglia extraction, etc.
 * </p>
 *
 * <p>
 * {@code ReviewUI} is a static helper and is not meant to be instantiated.
 * </p>
 */
public final class ReviewUI {
    /**
     * Private constructor to prevent instantiation.
     * <p>
     * {@code ReviewUI} is a static utility class; all functionality is provided
     * through static helper methods and it is not meant to be created.
     * </p>
     */
    private ReviewUI(){}

    /**
     * Opens an interactive review step for segmentation ROIs, then rebuilds
     * a labeled mask from the (possibly edited) ROIs.
     * <p>
     * The method:
     * </p>
     * <ol>
     *   <li>Duplicates and shows {@code backdrop} in its own window.</li>
     *   <li>Shows all ROIs from {@code rm} as an overlay with labels.</li>
     *   <li>Prompts the user to adjust ROIs:
     *       <ul>
     *         <li>Draw new ROI → press 'T' to add</li>
     *         <li>Select ROI → Delete to remove</li>
     *         <li>Drag vertices to edit shape</li>
     *       </ul>
     *   </li>
     *   <li>Waits until the user clicks OK in the dialog.</li>
     *   <li>Converts the final ROIs into a 16-bit label image where each ROI
     *       is filled with a unique integer ID.</li>
     *   <li>If no ROIs remain (so the rebuilt mask is empty), falls back to
     *       {@code fallbackLabels} instead.</li>
     * </ol>
     *
     * <p>The review window is then closed and the final label map is returned.</p>
     *
     * @param backdrop        image to display behind the ROIs during review
     * @param rm              ROI Manager containing editable ROIs
     * @param title           title to apply to the temporary review window
     * @param cal             calibration to attach to the output label map
     * @param fallbackLabels  backup label map to use if the user ends up with
     *                        zero ROIs; may be {@code null}
     * @return a new {@link ImagePlus} where each ROI is rasterized as a unique
     *         label ID (&gt;0). If no ROIs remain and {@code fallbackLabels}
     *         is non-null, a duplicate of {@code fallbackLabels} is returned.
     */
    public static ImagePlus reviewAndRebuildLabels(ImagePlus backdrop,
                                                   RoiManager rm,
                                                   String title,
                                                   Calibration cal,
                                                   ImagePlus fallbackLabels) {
        // display
        ImagePlus show = backdrop.duplicate();
        show.setTitle(title);
        show.show();

        rm.setVisible(true);
        // make sure overlay is bound to THIS window and labels are shown
        rm.runCommand(show, "Show All with labels");

        IJ.setTool("polygon");
        new WaitForUserDialog(
                "Review: " + title,
                "• Draw a new ROI and press 'T' to add\n" +
                        "• Select a ROI and press Delete to remove\n" +
                        "• Drag vertices to tweak shapes\n" +
                        "Click OK when done."
        ).show();

        rm.runCommand(show, "Show All without labels");

        // rebuild labels from ROIs
        ImagePlus labels = labelsFromRois(show.getWidth(), show.getHeight(), cal, rm);

        // fallback if user ended up with 0 ROIs / 0 labels
        if (countLabels(labels) == 0 && fallbackLabels != null) {
            labels.close();
            labels = fallbackLabels.duplicate(); // preserve original result
            labels.setTitle("labels_from_review_fallback");
        }

        show.changes = false; show.close();
        return labels;
    }

    /**
     * Converts all area ROIs in the given {@link RoiManager} into a 16-bit
     * label mask.
     * <p>
     * Each ROI is filled (not outlined) into a {@link ShortProcessor} and
     * assigned a monotonically increasing integer ID starting at 1.
     * Non-area ROIs (e.g. point or line ROIs) are skipped.
     * </p>
     *
     * @param w   image width in pixels for the output label map
     * @param h   image height in pixels for the output label map
     * @param cal calibration to apply to the returned {@link ImagePlus}
     * @param rm  ROI Manager providing the ROIs to rasterize
     * @return a new 16-bit {@link ImagePlus} named {@code "labels_from_review"}
     *         whose pixel values are per-ROI labels (0 = background)
     */
    private static ImagePlus labelsFromRois(int w, int h, Calibration cal, RoiManager rm) {
        ShortProcessor sp = new ShortProcessor(w, h);
        ImageProcessor ip = sp;
        Roi[] rois = rm.getRoisAsArray();
        int id = 1;
        for (Roi r : rois) {
            if (r == null || !r.isArea()) continue; // skip points/lines
            ip.setRoi(r);
            ip.setValue(id & 0xFFFF);
            ip.fill();
            id++;
        }
        ImagePlus out = new ImagePlus("labels_from_review", sp);
        out.setCalibration(cal);
        return out;
    }

    /**
     * Counts how many unique, non-zero labels exist in a 16-bit label map.
     * <p>
     * This is done by finding the maximum pixel value in the mask, assuming
     * labels were assigned as 1..N with 0 as background.
     * </p>
     *
     * @param labels16 16-bit label image whose pixels are ROI IDs
     * @return the highest non-zero label index found, or 0 if the mask
     *         contains only background
     */
    private static int countLabels(ImagePlus labels16) {
        short[] px = (short[]) labels16.getProcessor().getPixels();
        int max = 0;
        for (short v : px) {
            int u = v & 0xFFFF;
            if (u > max) max = u;
        }
        return max;
    }
}
