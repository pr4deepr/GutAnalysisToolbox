package Analysis;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.frame.RoiManager;

import Features.Core.PluginCalls;

/**
 * Converts ROIs from ROI Manager into a 16-bit label image.
 * Creates a window named "label_mapss" where each ROI becomes a unique labeled region.
 */
public class ConvertROIToLabels {

    /**
     * Converts all ROIs in ROI Manager to a label image using the active image as canvas.
     * Replaces any existing "label_mapss" window. If no ROIs exist, creates a blank label image.
     * ROIs are first converted to binary, then to sequential integer labels (1, 2, 3, ...).
     * Calibration is preserved from the source image.
     */
    public static void execute() {
        ImagePlus canvas = IJ.getImage();
        if (canvas == null) {
            IJ.error("No image open");
            return;
        }

        RoiManager rm = RoiManager.getInstance();
        if (rm == null) rm = new RoiManager();
        if (rm.getCount() == 0) {
            IJ.log("No ROIs in ROI Manager; creating blank label image.");
            ImagePlus blank = IJ.createImage("label_mapss", "16-bit black",
                    canvas.getWidth(), canvas.getHeight(), 1);
            blank.setCalibration(canvas.getCalibration());
            blank.show();
            return;
        }

        // Remove previous output
        ImagePlus old = WindowManager.getImage("label_mapss");
        if (old != null) { old.changes = false; old.close(); }

        // Convert: ROIs -> binary -> labels
        ImagePlus bin = PluginCalls.roisToBinary(canvas, rm);
        ImagePlus lab = PluginCalls.binaryToLabels(bin);

        lab.setCalibration(canvas.getCalibration());
        lab.setTitle("label_mapss");
        lab.show();

        bin.changes = false;
        bin.close();
        rm.reset();
    }
}