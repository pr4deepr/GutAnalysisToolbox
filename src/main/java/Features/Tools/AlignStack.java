package Features.Tools;

import Features.Core.Params;
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.measure.ResultsTable;
import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * AlignStack plugin
 * Aligns a multi-frame TIFF stack using Linear Stack Alignment with SIFT and/or Template Matching.
 * Converts macro logic to Java, using parameters from the Params object.
 */
public class AlignStack implements PlugIn {

    /** Container for alignment output: aligned stack and CSV file */
    public static class AlignResult {
        public final ImagePlus alignedStack;
        public final File resultCSV;

        public AlignResult(ImagePlus alignedStack, File resultCSV) {
            this.alignedStack = alignedStack;
            this.resultCSV = resultCSV;
        }
    }

    // URL for plugin installation guidance
    private static final String PLUGIN_INSTALLATION_URL =
            "https://sites.imagej.net/Template_Matching/";

    @Override
    public void run(String arg) {
        // Notify user about required plugins
        IJ.log("Please install template plugin if needed: " + PLUGIN_INSTALLATION_URL);
        IJ.showMessage("Info", "Please Ensure Linear Stack Alignment:Template Matching plugin is installed.\n" +
                "URL: " + PLUGIN_INSTALLATION_URL);
    }

    /**
     * Align the stack using parameters from the user interface (Params object)
     * @param p Params object containing alignment settings and paths
     * @return AlignResult containing the aligned ImagePlus and CSV of motion vectors
     * @throws Exception if input is invalid or stack is too short
     */
    public AlignResult run(Params p) throws Exception {
        if (p.imagePath == null || p.imagePath.isEmpty()) {
            throw new IllegalArgumentException("No input image specified");
        }

        File file = new File(p.imagePath);
        if (!file.exists()) throw new IllegalArgumentException("File not found: " + p.imagePath);

        ImagePlus imp = IJ.openImage(p.imagePath);
        if (imp == null) throw new RuntimeException("Failed to open image: " + p.imagePath);

        int sizeC = imp.getNChannels();
        int sizeT = imp.getNFrames();

        // Require at least 10 frames for meaningful alignment
        if (sizeT < 10) {
            IJ.log("Stack has fewer than 10 frames. Skipping alignment: " + file.getName());
            throw new Exception("Stack has fewer than 10 frames. Skipping alignment.");
        }

        // Determine reference frame
        int refFrame = Math.min(Math.max(1, p.referenceFrame), sizeT);
        imp.setSlice(refFrame);
        IJ.showStatus("Starting alignment: " + file.getName());

        // Handle multi-channel stacks by splitting channels
        if (sizeC > 1) {
            IJ.run(imp, "Split Channels", "");
            ImagePlus firstChannel = IJ.getImage();
            firstChannel.setTitle(file.getName());
            IJ.selectWindow(file.getName());

            // Close all other channels
            for (int c = 2; c <= sizeC; c++) {
                IJ.selectWindow("C" + c + "-" + file.getName());
                IJ.run("Close");
            }
            imp = firstChannel;
        }

        // Perform SIFT alignment if requested
        if (p.useSIFT) {
            alignSIFT(imp, false);
        }

        // Perform Template Matching alignment if requested
        if (p.useTemplateMatching) {
            alignTemplateMatching(imp, refFrame);
        }

        // Save aligned stack if requested
        if (p.saveAlignedStack) {
            String outputPath = p.outputDir;
            if (!outputPath.endsWith(File.separator)) outputPath += File.separator;
            String outFile = outputPath + file.getName().replace(".tif", "_aligned.tif");
            IJ.saveAsTiff(imp, outFile);
            IJ.log("Saved aligned stack to: " + outFile);
        }

        // Export CSV with X/Y shifts per slice if available
        File resultCSV = new File(p.outputDir, imp.getTitle() + "_alignment.csv");
        ResultsTable rt = ResultsTable.getResultsTable();
        if (rt != null && rt.getCounter() > 0) {
            int lastCol = rt.getLastColumn();
            int secondLastCol = lastCol - 1;

            try (PrintWriter pw = new PrintWriter(new FileWriter(resultCSV))) {
                pw.println("Slice,Dx,Dy");
                for (int i = 0; i < rt.getCounter(); i++) {
                    double valDx = rt.getValueAsDouble(secondLastCol, i);
                    double valDy = rt.getValueAsDouble(lastCol, i);
                    pw.printf("%d,%.3f,%.3f%n", i + 1, valDx, valDy);
                }
            } catch (IOException ex) {
                IJ.log("Failed to save alignment CSV: " + ex.getMessage());
            }
        } else {
            IJ.log("Warning: ResultsTable is empty. No motion data found.");
        }

        // Clean memory
        IJ.run("Collect Garbage");
        IJ.showStatus("Alignment done: " + file.getName());

        return new AlignResult(imp, resultCSV);
    }

    /**
     * Perform SIFT-based alignment using the Linear Stack Alignment plugin
     * @param imp ImagePlus stack to align
     * @param defaultSettings Whether to use default parameters
     */
    public static void alignSIFT(ImagePlus imp, boolean defaultSettings) {
        int size = Math.min(imp.getWidth(), imp.getHeight());
        int maximalAlignmentError = (int) Math.ceil(0.1 * size);
        double inlierRatio = defaultSettings ? 0.05 : 0.7;
        int featureDescSize = defaultSettings ? 4 : (size < 500 ? 8 : 4);

        String args = String.format(
                "initial_gaussian_blur=1.60 steps_per_scale_octave=%d minimum_image_size=64 " +
                "maximum_image_size=%d feature_descriptor_size=%d feature_descriptor_orientation_bins=8 " +
                "closest/next_closest_ratio=0.92 maximal_alignment_error=%d inlier_ratio=%f expected_transformation=Affine",
                defaultSettings ? 3 : 4, size, featureDescSize, maximalAlignmentError, inlierRatio
        );

        IJ.run(imp, "Linear Stack Alignment with SIFT", args);
        IJ.wait(100);
    }

    /**
     * Perform Template Matching-based alignment
     * @param imp ImagePlus stack to align
     * @param refFrame Reference frame number
     */
    public static void alignTemplateMatching(ImagePlus imp, int refFrame) {
        int xSize = (int) Math.floor(imp.getWidth() * 0.7);
        int ySize = (int) Math.floor(imp.getHeight() * 0.7);
        int x0 = (int) Math.floor(imp.getWidth() / 6.0);
        int y0 = (int) Math.floor(imp.getHeight() / 6.0);

        String args = String.format(
                "method=5 windowsizex=%d windowsizey=%d x0=%d y0=%d swindow=0 subpixel=false itpmethod=0 ref.slice=%d show=true",
                xSize, ySize, x0, y0, refFrame
        );

        IJ.run(imp, "Align slices in stack...", args);
        IJ.wait(10);
    }

    /**
     * Optional batch registration using StackReg plugin
     */
    public static void alignStackReg(ImagePlus imp, int referenceFrame) {
        if (imp == null) return;
        imp.setT(referenceFrame);
        IJ.run(imp, "StackReg", "transformation=[Rigid Body]");
    }

    /**
     * Save simple alignment shifts as CSV (placeholder if ResultsTable is empty)
     */
    public static void saveAlignmentResultsCSV(ImagePlus imp, String outputDir) {
        if (imp == null || outputDir == null) return;

        File csvFile = new File(outputDir, imp.getTitle() + "_alignment.csv");
        try (PrintWriter pw = new PrintWriter(csvFile)) {
            pw.println("Frame,X_shift,Y_shift");
            int nFrames = imp.getNFrames();
            for (int t = 1; t <= nFrames; t++) {
                double xShift = 0; // Placeholder
                double yShift = 0; // Placeholder
                pw.printf("%d,%.2f,%.2f%n", t, xShift, yShift);
            }
        } catch (Exception e) {
            IJ.log("Failed to save CSV: " + e.getMessage());
        }
    }
}
