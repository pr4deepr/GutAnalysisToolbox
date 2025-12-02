package Analysis;

import ij.*;
import ij.io.Opener;
import ij.plugin.frame.RoiManager;

import java.io.File;

/**
 * Performs spatial analysis on two cell types using maximum projection images,
 * ROIs for each cell type, and optional ganglia ROIs. Generates labeled images
 * and executes bidirectional neighbor analysis between the two cell populations.
 */
public class TwoCellTypeAnalysis {

    private String maxProjPath;
    private String cellType1;
    private String roi1Path;
    private String cellType2;
    private String roi2Path;
    private String roiGangliaPath;
    private String savePath;
    private double labelDilation;
    private boolean saveParametricImage;

    /**
     * Constructs a two cell type analysis pipeline.
     *
     * @param maxProjPath maximum projection image file path
     * @param cellType1 name of first cell type
     * @param roi1Path ROI file path for first cell type (.zip or .roi)
     * @param cellType2 name of second cell type
     * @param roi2Path ROI file path for second cell type (.zip or .roi)
     * @param roiGangliaPath ganglia ROI file path, or "NA" if not applicable
     * @param savePath directory path for saving results
     * @param labelDilation dilation distance for label expansion (in pixels)
     * @param saveParametricImage whether to save parametric images showing neighbor distributions
     */
    public TwoCellTypeAnalysis(String maxProjPath, String cellType1, String roi1Path,
                               String cellType2, String roi2Path, String roiGangliaPath,
                               String savePath, double labelDilation, boolean saveParametricImage) {
        this.maxProjPath = maxProjPath;
        this.cellType1 = cellType1;
        this.roi1Path = roi1Path;
        this.cellType2 = cellType2;
        this.roi2Path = roi2Path;
        this.roiGangliaPath = roiGangliaPath;
        this.savePath = savePath;
        this.labelDilation = labelDilation;
        this.saveParametricImage = saveParametricImage;
    }

    /**
     * Executes the complete analysis pipeline: opens images, converts ROIs to labels for
     * both cell types, creates ganglia masks if applicable, and runs bidirectional spatial
     * neighbor analysis between the two cell populations.
     *
     * @throws Exception if image loading fails, cell types have identical names, or processing encounters errors
     */
    public void execute() throws Exception {
        // Clear previous results
        IJ.run("Clear Results");
        IJ.run("Close All");

        // Validate cell type names are different
        if (cellType1.equals(cellType2)) {
            throw new Exception("Cell names are the same for both celltypes");
        }

        // Open the maximum projection image
        Opener opener = new Opener();
        ImagePlus maxProjImage = opener.openImage(maxProjPath);
        if (maxProjImage == null) {
            throw new Exception("Could not open maximum projection image: " + maxProjPath);
        }
        maxProjImage.show();

        // Get pixel size
        double pixelWidth = maxProjImage.getCalibration().pixelWidth;
        String unit = maxProjImage.getCalibration().getUnit();

        // Reset ROI Manager
        RoiManager roiManager = RoiManager.getInstance();
        if (roiManager == null) {
            roiManager = new RoiManager();
        }
        roiManager.reset();

        // Process ganglia ROI if provided
        String gangliaBinary = "";
        IJ.run("Options...", "iterations=1 count=1 black");

        if (roiGangliaPath != null && !roiGangliaPath.equals("NA") && new File(roiGangliaPath).exists()) {
            roiManager.runCommand("Open", roiGangliaPath);

            // Convert ganglia ROIs to label map
            ConvertROIToLabels.execute();

            Thread.sleep(10);
            IJ.run("Select None");
            IJ.run("Remove Overlay");

            // Create binary mask for ganglia
            IJ.setThreshold(0.5, 65535);
            IJ.run("Convert to Mask");
            IJ.getImage().setTitle("Ganglia_outline");
            gangliaBinary = IJ.getImage().getTitle();
            IJ.run("Divide...", "value=255");
            IJ.setMinAndMax(0, 1);

            roiManager.reset();
        } else {
            gangliaBinary = "NA";
        }

        // Process cell 1 ROIs
        roiManager.runCommand("Open", roi1Path);
        ConvertROIToLabels.execute();
        IJ.getImage().setTitle(cellType1 + "_labels");
        Thread.sleep(10);
        IJ.run("Select None");
        String labelCell1Img = IJ.getImage().getTitle();

        roiManager.reset();

        // Process cell 2 ROIs
        roiManager.runCommand("Open", roi2Path);
        ConvertROIToLabels.execute();
        IJ.getImage().setTitle(cellType2 + "_labels");
        Thread.sleep(10);
        IJ.run("Select None");
        String labelCell2Img = IJ.getImage().getTitle();

        // Run bidirectional spatial analysis
        SpatialTwoCellType.execute(cellType1, labelCell1Img, cellType2, labelCell2Img,
                gangliaBinary, savePath, labelDilation, saveParametricImage,
                pixelWidth, roi1Path, roi2Path);

        Thread.sleep(5);

        // Close all images
        IJ.run("Close All");
    }
}