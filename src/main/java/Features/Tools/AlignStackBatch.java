package Features.Tools;

import Features.Core.Params;
import ij.IJ;
import ij.ImagePlus;

import java.io.File;

/**
 * AlignStackBatch plugin
 * Performs batch alignment on multiple image stacks (.tif, .lif) using:
 * - Linear Stack Alignment with SIFT
 * - Template Matching
 * - StackReg (currently not implemented in batch mode)
 */
public class AlignStackBatch {

    /**
     * Run batch alignment on all image stacks in the input directory
     * @param p Params object with batch settings
     * @throws Exception if input validation fails
     */
    public static void runBatch(Params p) throws Exception {
        // Validate input directory
        if (p.inputDir == null || p.inputDir.trim().isEmpty())
            throw new IllegalArgumentException("Input directory not specified");

        File folder = new File(p.inputDir.trim());
        if (!folder.exists() || !folder.isDirectory())
            throw new IllegalArgumentException("Invalid input directory: " + p.inputDir);

        // Determine file extension to process
        String ext = (p.fileExt == null || p.fileExt.trim().isEmpty()) ? ".tif" : p.fileExt.trim().toLowerCase();
        String[] files = folder.list((dir, name) -> name.toLowerCase().endsWith(ext));
        if (files == null || files.length == 0) {
            IJ.log("No files found with extension " + ext + " in " + folder.getAbsolutePath());
            return;
        }

        IJ.log("Files found: " + files.length);

        // Process each file
        for (String fileName : files) {
            File file = new File(folder, fileName);
            IJ.log("Processing file: " + file.getName());

            ImagePlus imp = IJ.openImage(file.getAbsolutePath());
            if (imp != null) processFile(imp, p);
            else IJ.log("Failed to open file: " + file.getName());
        }

        IJ.log("Batch alignment finished.");
    }

    /**
     * Open Bio-Formats image for multi-series files (.lif, etc.)
     * @param file File to open
     * @param series Series number to open (0 for default)
     * @return ImagePlus object or null if failed
     */
    private static ImagePlus openBioFormatsImage(File file, int series) {
        try {
            String cmd = (series > 0)
                    ? "open=[" + file.getAbsolutePath() + "] color_mode=Default rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT series_" + series
                    : "open=[" + file.getAbsolutePath() + "] autoscale color_mode=Default rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT";

            IJ.run("Bio-Formats Importer", cmd);
            ImagePlus imp = IJ.getImage();
            if (imp == null) IJ.log("Failed to open " + file.getName());
            return imp;

        } catch (Exception ex) {
            IJ.log("Error opening " + file.getName() + ": " + ex.getMessage());
            return null;
        }
    }

    /**
     * Process a single ImagePlus stack: channel selection, alignment, saving
     * @param imp ImagePlus to process
     * @param p Params object with alignment settings
     */
    private static void processFile(ImagePlus imp, Params p) {
        if (imp == null) return;

        // Skip stacks with too few frames
        int sizeT = imp.getNFrames();
        if (sizeT <= 10) {
            IJ.log("Skipping " + imp.getTitle() + " (<=10 frames)");
            imp.close();
            return;
        }

        // Keep only first channel if multi-channel
        int sizeC = imp.getNChannels();
        if (sizeC > 1) {
            IJ.run(imp, "Split Channels", "");
            ImagePlus firstChannel = IJ.getImage();
            IJ.selectWindow("C1-" + imp.getTitle());
            for (int c = 2; c <= sizeC; c++) IJ.run("Close");
            imp = firstChannel;
        }

        IJ.log("Running alignment on " + imp.getTitle());

        // Alignment steps
        if (p.useSIFT) AlignStack.alignSIFT(imp, true);
        if (p.useTemplateMatching) AlignStack.alignTemplateMatching(imp, p.referenceFrame);
        if (p.useStackReg) {
            throw new UnsupportedOperationException(
                "StackReg alignment is not implemented in batch mode at this time"
            );
        }

        // Determine output path
        String outputDir = (p.outputDir != null && !p.outputDir.trim().isEmpty())
                ? p.outputDir.trim()
                : new File(".").getAbsolutePath();

        String outFile = outputDir + File.separator + imp.getTitle() + "_aligned.tif";
        IJ.saveAsTiff(imp, outFile);
        IJ.log("Saved aligned stack: " + outFile);

        // Save alignment CSV (placeholders if no ResultsTable)
        AlignStack.saveAlignmentResultsCSV(imp, outputDir);

        // Clean up
        imp.close();
        System.gc();
    }

}
