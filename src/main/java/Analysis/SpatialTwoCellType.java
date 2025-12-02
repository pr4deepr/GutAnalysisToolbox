package Analysis;

import ij.*;
import ij.process.ImageProcessor;
import ij.measure.ResultsTable;
import net.haesleinhuepf.clij2.CLIJ2;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import ij.plugin.frame.RoiManager;

import java.io.File;

/**
 * Performs GPU-accelerated spatial analysis on two cell types to compute neighbor counts
 * between them. Generates CSV results and optional parametric images showing neighbor distributions.
 */
public class SpatialTwoCellType {

    private static final String FILE_SEPARATOR = File.separator;

    /**
     * Executes bidirectional neighbor analysis between two cell types. Counts how many cells
     * of each type are within dilated regions of the other, optionally restricted by ganglia boundaries.
     *
     * @param cellType1 name of first cell type
     * @param cellImage1 title of first cell's labeled image window
     * @param cellType2 name of second cell type
     * @param cellImage2 title of second cell's labeled image window
     * @param gangliaBinary title of ganglia binary mask window, or "NA" if not used
     * @param savePath root directory for saving outputs
     * @param labelDilation dilation distance in physical units (e.g., microns)
     * @param saveParametricImage whether to save parametric images showing neighbor counts
     * @param pixelWidth pixel size in physical units for converting dilation distance
     * @param roi1Path ROI file path for first cell type
     * @param roi2Path ROI file path for second cell type
     * @throws Exception if image retrieval or GPU processing fails
     */
    public static void execute(String cellType1, String cellImage1, String cellType2, String cellImage2,
                               String gangliaBinary, String savePath, double labelDilation,
                               boolean saveParametricImage, double pixelWidth, String roi1Path, String roi2Path) throws Exception {

        CLIJ2 clij2 = CLIJ2.getInstance();
        int labelDilationPixels = (int) Math.round(labelDilation / pixelWidth);

        String spatialSavePath = savePath + FILE_SEPARATOR + "spatial_analysis" + FILE_SEPARATOR;
        new File(spatialSavePath).mkdirs();

        // Get cell images
        ImagePlus cellImg1 = WindowManager.getImage(cellImage1);
        ImagePlus cellImg2 = WindowManager.getImage(cellImage2);

        if (cellImg1 == null) {
            IJ.error("Cell image 1 not found: " + cellImage1);
            return;
        }
        if (cellImg2 == null) {
            IJ.error("Cell image 2 not found: " + cellImage2);
            return;
        }

        int width = cellImg1.getWidth();
        int height = cellImg1.getHeight();

        // Get max labels for each cell type
        ImageProcessor labelIp1 = cellImg1.getProcessor();
        ImageProcessor labelIp2 = cellImg2.getProcessor();
        int maxLabel1 = (int) labelIp1.getStatistics().max;
        int maxLabel2 = (int) labelIp2.getStatistics().max;

        // Count cell2 neighbors around cell1
        int[] countsCell2AroundCell1 = countNeighboursAroundRef(clij2, cellImg1, cellImg2, labelDilationPixels, gangliaBinary, width, height);

        // Count cell1 neighbors around cell2
        int[] countsCell1AroundCell2 = countNeighboursAroundRef(clij2, cellImg2, cellImg1, labelDilationPixels, gangliaBinary, width, height);

        // Get ROI labels
        String[] cell1Names = getRoiLabels(roi1Path, cellImage1);
        String[] cell2Names = getRoiLabels(roi2Path, cellImage2);

        // Create results table
        ResultsTable outTable = new ResultsTable();

        // Add data ensuring arrays match expected lengths
        int maxRows = Math.max(Math.max(cell1Names.length, cell2Names.length),
                Math.max(countsCell2AroundCell1.length - 1, countsCell1AroundCell2.length - 1));

        for (int i = 0; i < maxRows; i++) {
            outTable.incrementCounter();

            // Cell 1 data
            if (i < cell1Names.length) {
                outTable.addValue(cellType1 + "_id", cell1Names[i]);
            } else {
                outTable.addValue(cellType1 + "_id", "");
            }

            if (i + 1 < countsCell2AroundCell1.length) {
                outTable.addValue("No of " + cellType2 + " around " + cellType1, countsCell2AroundCell1[i + 1]);
            } else {
                outTable.addValue("No of " + cellType2 + " around " + cellType1, 0);
            }

            // Cell 2 data
            if (i < cell2Names.length) {
                outTable.addValue(cellType2 + "_id", cell2Names[i]);
            } else {
                outTable.addValue(cellType2 + "_id", "");
            }

            if (i + 1 < countsCell1AroundCell2.length) {
                outTable.addValue("No of " + cellType1 + " around " + cellType2, countsCell1AroundCell2[i + 1]);
            } else {
                outTable.addValue("No of " + cellType1 + " around " + cellType2, 0);
            }
        }

        // Save CSV
        String csvPath = spatialSavePath + "Neighbour_count_" + cellType1 + "_" + cellType2 + ".csv";
        outTable.save(csvPath);

        // Save parametric images if requested
        if (saveParametricImage) {
            createParametricImage(clij2, cellImg1, countsCell2AroundCell1, cellType2 + "_around_" + cellType1, spatialSavePath);
            createParametricImage(clij2, cellImg2, countsCell1AroundCell2, cellType1 + "_around_" + cellType2, spatialSavePath);
        }
    }

    /**
     * Counts marker cell labels within dilated reference cell regions, optionally restricted
     * by ganglia boundaries. Uses GPU-accelerated label dilation and overlap counting.
     *
     * @param clij2 CLIJ2 instance for GPU operations
     * @param refImg reference cell labeled image
     * @param markerImg marker cell labeled image to count
     * @param dilationPixels dilation radius in pixels
     * @param gangliaBinary title of ganglia binary mask, or "NA" if not used
     * @param width image width
     * @param height image height
     * @return array where index i contains neighbor count for label i (index 0 is background)
     */
    private static int[] countNeighboursAroundRef(CLIJ2 clij2, ImagePlus refImg, ImagePlus markerImg,
                                                  int dilationPixels, String gangliaBinary, int width, int height) {

        // Push images to GPU
        ClearCLBuffer refBuffer = clij2.push(refImg);
        ClearCLBuffer markerBuffer = clij2.push(markerImg);

        // Dilate reference cells
        ClearCLBuffer refDilated = clij2.create(refBuffer);
        clij2.dilateLabels(refBuffer, refDilated, dilationPixels);

        ClearCLBuffer refDilatedFinal = refDilated;

        // Apply ganglia restriction if available
        if (!gangliaBinary.equals("NA") && WindowManager.getImage(gangliaBinary) != null) {
            ImagePlus gangliaImg = WindowManager.getImage(gangliaBinary);
            ClearCLBuffer gangliaBuffer = clij2.push(gangliaImg);
            ClearCLBuffer refDilatedRestricted = clij2.create(refBuffer);
            clij2.multiplyImages(refDilated, gangliaBuffer, refDilatedRestricted);
            refDilatedFinal = refDilatedRestricted;
            gangliaBuffer.close();
        }

        // Count label overlaps (number of marker pixels per reference label)
        ClearCLBuffer labelOverlapCount = clij2.create(refBuffer);
        clij2.labelOverlapCountMap(refDilatedFinal, markerBuffer, labelOverlapCount);

        // Pull overlap map back to CPU
        ImagePlus overlapImg = clij2.pull(labelOverlapCount);
        ImageProcessor overlapIp = overlapImg.getProcessor();

        // Get max label in reference image
        ImageProcessor refIp = refImg.getProcessor();
        int maxLabel = (int) refIp.getStatistics().max;

        int[] counts = new int[maxLabel + 1]; // counts[0] is background
        for (int label = 1; label <= maxLabel; label++) {
            // Find first pixel of this label and read count from overlap map
            outer:
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if ((int) refIp.getPixel(x, y) == label) {
                        counts[label] = (int) overlapIp.getPixel(x, y);
                        break outer;
                    }
                }
            }
        }

        // Cleanup GPU buffers
        refBuffer.close();
        markerBuffer.close();
        refDilated.close();
        if (refDilatedFinal != refDilated) refDilatedFinal.close();
        labelOverlapCount.close();
        overlapImg.close();

        return counts;
    }

    /**
     * Creates a parametric image where each cell label is colored by its neighbor count.
     * Applies Fire LUT and saves as TIFF without displaying.
     *
     * @param clij2 CLIJ2 instance for GPU operations
     * @param cellImg labeled cell image
     * @param counts neighbor counts per label (array index corresponds to label value)
     * @param imageName output filename (without extension)
     * @param savePath directory to save TIFF file
     */
    private static void createParametricImage(CLIJ2 clij2, ImagePlus cellImg, int[] counts,
                                              String imageName, String savePath) {

        // Convert int array to float array
        float[] floatCounts = new float[counts.length];
        for (int i = 0; i < counts.length; i++) {
            floatCounts[i] = (float) counts[i];
        }

        ClearCLBuffer cellBuffer = clij2.push(cellImg);
        ClearCLBuffer vectorNeighbours = clij2.pushArray(floatCounts, floatCounts.length, 1, 1);
        ClearCLBuffer paramImg = clij2.create(cellBuffer);

        clij2.replaceIntensities(cellBuffer, vectorNeighbours, paramImg);

        ImagePlus paramResult = clij2.pull(paramImg);
        paramResult.setTitle(imageName);
        IJ.run(paramResult, "Fire", "");

        // Save parametric image
        IJ.saveAs(paramResult, "Tiff", savePath + imageName + ".tif");

        // Close without showing
        paramResult.close();

        // Cleanup
        cellBuffer.close();
        vectorNeighbours.close();
        paramImg.close();
    }

    /**
     * Extracts ROI labels from a ROI zip file. Parses ROI names to extract numeric IDs,
     * falling back to index-based numbering if names are missing.
     *
     * @param roiZipPath path to ROI zip file
     * @param cellImage cell image title (used for context, currently unused)
     * @return array of label strings corresponding to each ROI
     */
    private static String[] getRoiLabels(String roiZipPath, String cellImage) {
        RoiManager rm = RoiManager.getInstance();
        if (rm == null) rm = new RoiManager();
        rm.reset();
        rm.runCommand("Open", roiZipPath);

        ij.gui.Roi[] rois = rm.getRoisAsArray();
        String[] labels = new String[rois.length];

        for (int i = 0; i < rois.length; i++) {
            String name = (rois[i] != null) ? rois[i].getName() : null;
            if (name == null || name.isEmpty()) {
                labels[i] = String.valueOf(i + 1);
            } else {
                int colon = name.indexOf(':'); // strip "Label: 17" → "17"
                labels[i] = (colon >= 0 && colon < name.length() - 1)
                        ? name.substring(colon + 1)
                        : name;
            }
        }

        rm.reset();
        return labels;
    }
}