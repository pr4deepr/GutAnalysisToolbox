package Analysis;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.frame.RoiManager;
import java.io.File;
import java.awt.GridLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import Features.Core.Params;

/**
 * This CalciumAnalysis class in provides methods for loading, processing, and analysing calcium
 * imaging data including image manipulation, ROI management, and measurement extraction.
 */
public class CalciumAnalysis {

    private final Params p;
    private ImagePlus rawStack;     // Original image stack
    public ImagePlus maxProj;       // Max intensity projection
    public ImagePlus normStack;     // Normalized F/F0 stack
    private RoiManager rm;          // ROI Manager for handling regions

    /**
     * Constructs a new {@code CalciumAnalysis} instance using the specified parameters.
     *
     * @param params the analysis parameters containing file paths and processing options
     */

    public CalciumAnalysis(Params params) {
        this.p = params;
    }

    /** Step 1: Load the image from file path */
    public void openImage() {
        File imgFile = new File(p.imagePath);
        if (!imgFile.exists()) {
            IJ.error("File not found: " + p.imagePath);
            return;
        }
        rawStack = IJ.openImage(p.imagePath);
        this.maxProj = rawStack;
        rawStack.show();
        IJ.selectWindow(rawStack.getTitle());
        IJ.log("Step 1: Image loaded successfully.");
    }

    /** Step 2: Generate max intensity projection for user-specified frame range */
    public void createMaxProjection() {
        if (maxProj.getStackSize() <= 1) {
            IJ.showMessage("Error", "Image must have multiple slices for Max Projection.");
            return;
        }
        int[] frames = promptForFrames(maxProj.getStackSize());
        if (frames == null) return;

        int start = frames[0];
        int end = frames[1];
        IJ.run(rawStack, "Z Project...", "start=" + start + " stop=" + end + " projection=[Max Intensity]");
        maxProj = IJ.getImage();
        maxProj.setTitle("MAX_" + new File(p.imagePath).getName());
        maxProj.show();
        IJ.log("Step 2: Max intensity projection created.");
    }

    /** Step 3: Perform F/F0 normalization */
    public void normalizeStack() {
        if (!p.useFF0) {
            normStack = rawStack;
            return;
        }

        int[] frames = promptForBaseline(rawStack.getStackSize());
        if (frames == null) return;

        int start = frames[0];
        int end = frames[1];
        IJ.run(rawStack, "Z Project...", "start=" + start + " stop=" + end + " projection=[Average Intensity]");
        ImagePlus f0 = IJ.getImage();

        IJ.run("Image Calculator...", "image1=[" + rawStack.getTitle() + "] operation=Divide image2=[" + f0.getTitle() + "] create 32-bit stack");
        normStack = IJ.getImage();
        normStack.setTitle("F_F0_" + new File(p.imagePath).getName());
        normStack.show();
        IJ.log("Step 3: F/F0 normalization completed.");
    }

    /** Step 4: Initialize ROI Manager */
    public void setupROIManager() {
        rm = RoiManager.getInstance();
        if (rm == null) rm = new RoiManager();
        rm.reset();
        IJ.log("Step 4: ROI Manager initialized and cleared.");
    }

    /** Step 5: Import or generate ROIs for the specified cell type */
    public void handleCellType(int i) {
        String cellName = (p.cellNames != null && p.cellNames.size() > i)
                ? p.cellNames.get(i)
                : "CellType" + (i + 1);

        boolean imported = false;

        // Try importing ROI file if path is provided
        if (p.roiPath != null && !p.roiPath.isEmpty()) {
            File roiFile = new File(p.roiPath);
            if (roiFile.exists()) {
                try {
                    rm.runCommand("Open", roiFile.getAbsolutePath());
                    IJ.showMessage("ROIs Imported",
                            "Successfully imported ROIs from:\n" + roiFile.getName());
                    imported = true;
                } catch (Exception ex) {
                    IJ.showMessage("Error", "Failed to import ROIs:\n" + ex.getMessage());
                }
            } else {
                IJ.showMessage("ROI File Missing",
                        "The specified ROI file does not exist:\n" + roiFile.getAbsolutePath());
            }
        }

        // If no ROI imported, attempt automated segmentation with StarDist
        if (!imported && p.useStarDist) {
            try {
                runStarDist(maxProj, new File(p.imagePath).getParentFile());
                IJ.showMessage("StarDist Segmentation Completed",
                        "ROIs generated automatically for " + cellName + ".");
                imported = true;
            } catch (Exception ex) {
                IJ.showMessage("StarDist Error",
                        "StarDist segmentation failed: " + ex.getMessage());
            }
        }

        // If still no ROIs, prompt user to manually draw
        if (!imported) {
            IJ.selectWindow(maxProj.getTitle());
            IJ.setTool("oval");
            IJ.showMessage("Draw ROIs",
                    "No ROI file provided.\nPlease manually draw ROIs for " + cellName +
                    " using Oval or Freehand tools.");
        }
    }

    /** Step 6: Rename ROIs according to cell names */
    public void renameROIs() {
        int roiCount = rm.getCount();
        for (int r = 0; r < roiCount; r++) {
            String name = (p.cellNames != null && !p.cellNames.isEmpty())
                    ? p.cellNames.get(r % p.cellNames.size()) + "_" + (r + 1)
                    : "Cell_" + (r + 1);
            IJ.runMacro("roiManager(\"Select\", " + r + ");");
            IJ.runMacro("roiManager(\"Rename\", \"" + name + "\");");
        }
    }

    /** Step 7: Measure intensity for all ROIs in the normalized stack */
    public void measureROIs() {
        IJ.selectWindow(normStack.getTitle());
        IJ.run("Set Measurements...", "mean redirect=None decimal=2");
        rm.runCommand("Multi Measure");
        IJ.wait(50); // small delay to ensure measurements are recorded
    }

    /** Step 8: Save measurement results and ROIs to RESULTS folder */
    public File saveResults() {
        File imgFile = new File(p.imagePath);
        File resultsDir = new File(imgFile.getParentFile(),
                "RESULTS" + File.separator + imgFile.getName().replace(".tif", ""));
        if (!resultsDir.exists()) resultsDir.mkdirs();

        // Save measurements CSV
        File csvFile = new File(resultsDir, "RESULTS_" + imgFile.getName() + ".csv");
        IJ.saveAs("Results", csvFile.getAbsolutePath());

        // Save normalized stack if applicable
        if (p.useFF0) {
            IJ.selectWindow(normStack.getTitle());
            IJ.run("Select None");
            rm.deselect();
            IJ.saveAs("Tiff", new File(resultsDir, normStack.getTitle() + ".tif").getAbsolutePath());
            IJ.run("Close");
        }

        // Save all ROIs
        rm.deselect();
        rm.runCommand("Save", new File(resultsDir, "ROIS_" + imgFile.getName() + "_CELLS.zip").getAbsolutePath());
        IJ.log("Step 8: Results and ROIs saved at " + resultsDir.getAbsolutePath());

        return csvFile;
    }

    /** Run StarDist segmentation (currently disabled) */
    private void runStarDist(ImagePlus img, File resultsDir) {
        if (img == null) {
            IJ.showMessage("Error", "No image available for StarDist segmentation.");
            return;
        }

        IJ.showMessage("Error", "StarDist segmentation is currently disabled.");
        return;
    }

    /** Prompt user to select start and end frames for max projection */
    private int[] promptForFrames(int stackSize) {
        JPanel panel = new JPanel(new GridLayout(2, 2, 4, 4));

        SpinnerNumberModel startModel = new SpinnerNumberModel(1, 1, stackSize, 1);
        SpinnerNumberModel endModel = new SpinnerNumberModel(stackSize, 1, stackSize, 1);

        JSpinner startSpinner = new JSpinner(startModel);
        JSpinner endSpinner = new JSpinner(endModel);

        panel.add(new JLabel("Start frame:"));
        panel.add(startSpinner);
        panel.add(new JLabel("End frame:"));
        panel.add(endSpinner);

        int option = JOptionPane.showConfirmDialog(
                null, panel, "Select Max Projection Frames", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (option == JOptionPane.OK_OPTION) {
            int start = (Integer) startSpinner.getValue();
            int end = (Integer) endSpinner.getValue();
            if (start > end) {
                IJ.showMessage("Error", "Start frame must be ≤ end frame.");
                return null;
            }
            return new int[]{start, end};
        }
        return null;
    }

    /** Prompt user to select start and end frames for baseline calculation */
    private int[] promptForBaseline(int stackSize) {
        JPanel panel = new JPanel(new GridLayout(2, 2, 4, 4));

        SpinnerNumberModel startModel = new SpinnerNumberModel(1, 1, stackSize, 1);
        SpinnerNumberModel endModel = new SpinnerNumberModel(stackSize, 1, stackSize, 1);

        JSpinner startSpinner = new JSpinner(startModel);
        JSpinner endSpinner = new JSpinner(endModel);

        panel.add(new JLabel("Start frame:"));
        panel.add(startSpinner);
        panel.add(new JLabel("End frame:"));
        panel.add(endSpinner);

        int option = JOptionPane.showConfirmDialog(
                null, panel, "Select Baseline Frames", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (option == JOptionPane.OK_OPTION) {
            int start = (Integer) startSpinner.getValue();
            int end = (Integer) endSpinner.getValue();
            if (start > end) {
                IJ.showMessage("Error", "Start frame must be ≤ end frame.");
                return null;
            }
            return new int[]{start, end};
        }
        return null;
    }
}
