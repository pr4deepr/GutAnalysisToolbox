package Analysis;

import ij.*;
import ij.process.ImageProcessor;
import ij.measure.ResultsTable;
import net.haesleinhuepf.clij2.CLIJ2;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;

import java.io.File;

/**
 * Performs GPU-accelerated spatial analysis on labeled cell images to compute
 * neighbor counts for each cell using label dilation and touching neighbor detection.
 */
public class SpatialSingleCellType {

    private static final String FILE_SEPARATOR = File.separator;

    /**
     * Executes spatial neighbor analysis on labeled cells, optionally incorporating
     * ganglia boundaries. Dilates cell labels, computes touching neighbors, and saves
     * results as CSV. Optionally saves the labeled cell image.
     *
     * @param cellType name of the cell type being analyzed
     * @param cellImage title of the labeled cell image window in ImageJ
     * @param gangliaBinary title of ganglia binary mask window, or "NA" if not used
     * @param savePath root directory for saving outputs
     * @param labelDilation dilation distance in physical units (e.g., microns)
     * @param saveParametricImage whether to save the labeled cell image as TIFF
     * @param pixelWidth pixel size in physical units for converting dilation distance
     * @param roiPath original ROI file path (currently unused, kept for interface compatibility)
     * @throws Exception if image retrieval or GPU processing fails
     */
    public static void execute(String cellType, String cellImage, String gangliaBinary,
                               String savePath, double labelDilation, boolean saveParametricImage,
                               double pixelWidth, String roiPath) throws Exception {

        CLIJ2 clij2 = CLIJ2.getInstance();
        int labelDilationPixels = (int) Math.round(labelDilation / pixelWidth);

        String spatialSavePath = savePath + FILE_SEPARATOR + "spatial_analysis" + FILE_SEPARATOR;
        new File(spatialSavePath).mkdirs();

        ImagePlus cellImg = WindowManager.getImage(cellImage);
        if (cellImg == null) {
            IJ.error("Cell image not found: " + cellImage);
            return;
        }

        int width = cellImg.getWidth();
        int height = cellImg.getHeight();
        ImageProcessor labelIp = cellImg.getProcessor();
        int maxLabel = (int) labelIp.getStatistics().max;

        // Push label image to GPU
        ClearCLBuffer cellBuffer = clij2.push(cellImg);

        // Dilate labels
        ClearCLBuffer dilated = clij2.create(cellBuffer);
        clij2.dilateLabels(cellBuffer, dilated, labelDilationPixels);

        // Compute touching neighbor map
        ClearCLBuffer neighborMap = clij2.create(cellBuffer);
        clij2.touchingNeighborCountMap(dilated, neighborMap);

        // Pull neighbor map back to ImageJ (hidden, no window created)
        ImagePlus neighborImg = clij2.pull(neighborMap);
        ImageProcessor neighborIp = neighborImg.getProcessor();

        // Build results table with neighbor counts per label
        ResultsTable outTable = new ResultsTable();
        for (int label = 1; label <= maxLabel; label++) {
            int neighborCount = 0;
            outer:
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if ((int) labelIp.getPixel(x, y) == label) {
                        neighborCount = (int) neighborIp.getPixel(x, y);
                        break outer;
                    }
                }
            }
            outTable.incrementCounter();
            outTable.addLabel(String.valueOf(label));
            outTable.addValue("No of cells around " + cellType, neighborCount);
        }

        // Save CSV
        String csvPath = spatialSavePath + "Neighbour_count_" + cellType + ".csv";
        outTable.save(csvPath);

        // Save labeled cell image if requested
        if (saveParametricImage) {
            IJ.saveAs(cellImg, "Tiff", spatialSavePath + "cell_labels.tif");
        }

        // Cleanup GPU buffers
        cellBuffer.close();
        dilated.close();
        neighborMap.close();
        neighborImg.close();
    }
}