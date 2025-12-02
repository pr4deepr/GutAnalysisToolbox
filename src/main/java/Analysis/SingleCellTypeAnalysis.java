package Analysis;

import ij.*;
import ij.io.Opener;
import ij.plugin.frame.RoiManager;

import java.io.File;

/**
 * Performs spatial analysis on a single cell type using maximum projection images,
 * cell ROIs, and optional ganglia ROIs. Generates labeled images and executes
 * downstream spatial analysis.
 */
public class SingleCellTypeAnalysis {

    private String maxProjPath;
    private String roiPath;
    private String roiGangliaPath;
    private String savePath;
    private String cellType;
    private double labelDilation;
    private boolean saveParametricImage;

    /**
     * Constructs a single cell type analysis pipeline.
     *
     * @param maxProjPath maximum projection image file path
     * @param roiPath cell ROI file path (.zip or .roi)
     * @param roiGangliaPath ganglia ROI file path, or "NA" if not applicable
     * @param savePath directory path for saving results
     * @param cellType name of the cell type being analyzed
     * @param labelDilation dilation distance for label expansion (in pixels)
     * @param saveParametricImage whether to save intermediate parametric images
     */
    public SingleCellTypeAnalysis(String maxProjPath, String roiPath, String roiGangliaPath,
                                  String savePath, String cellType, double labelDilation,
                                  boolean saveParametricImage) {
        this.maxProjPath = maxProjPath;
        this.roiPath = roiPath;
        this.roiGangliaPath = roiGangliaPath;
        this.savePath = savePath;
        this.cellType = cellType;
        this.labelDilation = labelDilation;
        this.saveParametricImage = saveParametricImage;
    }

    /**
     * Executes the complete analysis pipeline: opens images, converts ROIs to labels,
     * creates ganglia masks if applicable, and runs spatial analysis.
     *
     * @throws Exception if image loading fails or processing encounters errors
     */
    public void execute() throws Exception {
        // Clear previous results
        IJ.run("Clear Results");
        IJ.run("Close All");

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

        // Process cell ROIs
        roiManager.runCommand("Open", roiPath);
        ConvertROIToLabels.execute();
        IJ.getImage().setTitle("Cell_labels");
        Thread.sleep(10);
        IJ.run("Select None");
        String labelCellImg = IJ.getImage().getTitle();

        // Run spatial analysis
        SpatialSingleCellType.execute(cellType, labelCellImg, gangliaBinary, savePath,
                labelDilation, saveParametricImage, pixelWidth, roiPath);

        Thread.sleep(5);

        // Close all images
        IJ.run("Close All");
    }
}