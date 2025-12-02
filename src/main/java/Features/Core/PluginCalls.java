package Features.Core;

import Features.Tools.ProgressUI;
import Features.Tools.SilentRun;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.frame.RoiManager;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.text.DecimalFormat;


/**
 * Centralized wrapper for "run this ImageJ / Fiji thing" calls.
 *
 * Goals:
 *  - Hide ImageJ macro-style IJ.run(...) boilerplate behind named helpers.
 *  - Ensure we consistently propagate calibration, hide temp windows,
 *    and clean up after plugin calls.
 *  - Provide deterministic "new image" capture (findNewImageSince).
 *
 * Pipelines call these helpers instead of sprinkling IJ.run() everywhere.
 */
public final class PluginCalls {
    private PluginCalls(){}


    /** Formatter used for building macro command strings with predictable decimal separators
     *  (US locale, no commas). Used when passing thresholds to StarDist, etc.
     */
    private static final DecimalFormat DF = new DecimalFormat("0.######",
            java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US));

    /**
     * Open an image using Bio-Formats with consistent options (composite, etc.),
     * then hide the resulting window.
     *
     * Behavior:
     *   - Runs "Bio-Formats" importer via IJ.run
     *   - Returns the ImagePlus that Bio-Formats created
     *   - Keeps it open but not visible (so future calls can still access it)
     *
     * @param path absolute path to the image file (LIF, CZI, etc.).
     * @return     hidden ImagePlus in memory, or null if Bio-Formats failed.
     */
    public static ImagePlus openWithBioFormats(String path) {
        IJ.run("Bio-Formats", "open=[" + path + "] color_mode=Composite rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT");
        ImagePlus imp = IJ.getImage();
        if (imp != null) imp.hide();     // <-- keep it open/active, but not visible
        return imp;
    }

    /**
     * Build an RGB composite image for visualizing ganglia vs Hu neurons.
     *
     * R = Hu channel, G = ganglia marker channel, B = Hu again
     * → Hu shows as magenta, ganglia marker as green.
     *
     * Used mainly for overlays in review/export UIs.
     *
     * @param maxProj       MAX or EDF projection (multi-channel).
     * @param gangliaCh1    1-based index of ganglia/neurite channel.
     * @param huCh1         1-based index of Hu/neuron channel.
     * @return              Hidden RGB ImagePlus with calibration copied.
     */
    public static ImagePlus buildGangliaRgbForOverlay(ImagePlus maxProj, int gangliaCh1, int huCh1) {
        ImagePlus g  = Features.Tools.ImageOps.extractChannel(maxProj, gangliaCh1);
        ImagePlus hu = Features.Tools.ImageOps.extractChannel(maxProj, huCh1);
        IJ.resetMinAndMax(g);  IJ.resetMinAndMax(hu);

        ij.process.ByteProcessor r8 = (ij.process.ByteProcessor) hu.getProcessor().convertToByte(true);
        ij.process.ByteProcessor g8 = (ij.process.ByteProcessor) g .getProcessor().convertToByte(true);
        ij.process.ByteProcessor b8 = (ij.process.ByteProcessor) hu.getProcessor().convertToByte(true);

        ij.process.ColorProcessor cp = new ij.process.ColorProcessor(maxProj.getWidth(), maxProj.getHeight());
        cp.setRGB((byte[]) r8.getPixels(), (byte[]) g8.getPixels(), (byte[]) b8.getPixels());

        ImagePlus rgb = new ImagePlus("ganglia_rgb_base", cp);
        rgb.setCalibration(maxProj.getCalibration());
        rgb.hide();

        g.changes=false; g.close();
        hu.changes=false; hu.close();
        return rgb;
    }

    /**
     * Create an Extended Depth of Focus projection (variance fusion) using CLIJ2.
     * Falls back to IJ.run() integration points for CLIJ2.
     *
     * Steps:
     *   - Push current Z stack to GPU
     *   - Run "Extended Depth Of Focus (variance)"
     *   - Pull single-plane result
     *   - Copy calibration and hide it
     *
     * @param src  Original Z-stack ImagePlus
     * @return     A new 2D ImagePlus called "EDF_<srcTitle>", hidden.
     */
    public static ImagePlus clij2EdfVariance(ImagePlus src) {
        src.show();
        IJ.run("CLIJ2 Macro Extensions", "cl_device=");
        IJ.run("CLIJ2 Push Current Z Stack", "");
        IJ.run("CLIJ2 Extended Depth Of Focus (variance)", "radius_x=2 radius_y=2 sigma=10");
        String outTitle = "EDF_" + src.getTitle();
        IJ.run("CLIJ2 Pull", "destination=" + outTitle);
        IJ.run("CLIJ2 Clear", "");
        ImagePlus out = IJ.getImage();
        out.setTitle(outTitle);
        out.setCalibration(src.getCalibration());
        out.hide();
        return out;
    }

    /**
     * Quick unit check for microns.
     *
     * @param unit Calibration unit string from ImagePlus.getCalibration().getUnit()
     * @return     true if it's one of "µm", "um", "micron", "microns" (case-insensitive).
     */
    public static boolean isMicronUnit(String unit) {
        if (unit == null) return false;
        String u = unit.trim().toLowerCase(java.util.Locale.ROOT);
        return u.equals("µm") || u.equals("um") || u.equals("micron") || u.equals("microns");
    }

    /**
     * Convert a 16-bit label image to ROIs via MorphoLibJ's
     * "Label Map to ROIs".
     *
     * Side effect:
     *   - Populates the global RoiManager with one ROI per label.
     *
     * @param labels 16-bit connected-component label map.
     */
    public static void labelsToRois(ImagePlus labels) {
        // MorphoLibJ "Label Map to ROIs"
        String opts = "Connectivity=C8 Vertex Location=Corners Name Pattern=r%03d";
        SilentRun.on(labels, "Label Map to ROIs", opts);
    }


    /**
     * Remove labels that touch the border of the image using MorphoLibJ's
     * "Remove Border Labels".
     *
     * We:
     *   - Ensure the input is treated as a single-slice 16-bit label map.
     *   - Remember which images existed before running the command.
     *   - Run the command on the label map.
     *   - Grab the newly created output from ImageJ.
     *   - Copy calibration, hide it, and close temps.
     *
     * @param labels 16-bit label map (possibly multi-slice).
     * @return       New 16-bit label map with border-touching objects removed.
     */
    public static ImagePlus removeBorderLabels(ImagePlus labels) {
        ImagePlus lab2d = ensure2DLabel(labels);
        int[] before = ij.WindowManager.getIDList();
        SilentRun.on(lab2d, "Remove Border Labels", "left right top bottom");
        ImagePlus out = findNewImageSince(before);
        if (out == null) out = IJ.getImage();
        if (out == null) throw new IllegalStateException("Remove Border Labels produced no output.");

        out.setCalibration(lab2d.getCalibration());
        if (out != lab2d) lab2d.close();
        if (out != labels) labels.close();
        out.hide();
        return out;
    }

    /**
     * Apply "Label Size Filtering" (MorphoLibJ) to remove objects smaller than a
     * pixel-area threshold.
     *
     * Used to drop tiny debris after StarDist or DIJ.
     *
     * @param labels 16-bit label map.
     * @param minPx  Minimum allowed size in pixels.
     * @return       New 16-bit label map, cleaned and hidden.
     * @throws IllegalStateException if MorpholibJ didn't return output.
     */
    public static ImagePlus labelMinSizeFilterPx(ImagePlus labels, int minPx) {
        int[] before = ij.WindowManager.getIDList();
        SilentRun.on(labels, "Label Size Filtering",
                "operation=Greater_Than_Or_Equal size=" + Math.max(1, minPx));
        ImagePlus out = findNewImageSince(before);
        if (out == null) out = IJ.getImage();
        if (out == null) throw new IllegalStateException("Label Size Filtering produced no output.");
        out.hide();
        out.setCalibration(labels.getCalibration());
        if (out != labels) labels.close();
        return out;
    }

    /**
     * Heuristic tile-count for StarDist based on image size.
     * Larger images get more tiles to avoid memory issues during inference.
     *
     * @param w image width in pixels
     * @param h image height in pixels
     * @return  suggested nTiles parameter for StarDist.
     */
    public static int suggestTiles(int w, int h) {
        int n = 4;
        if (w > 2000 || h > 2000) n = 5;
        if (w > 4500 || h > 4500) n = 8;
        if (w > 9000 || h > 9000) n = 16;
        if (w > 15000 || h > 15000) n = 24;
        return n;
    }


    /**
     * INTERNAL HELPER (private): showHidden(ImagePlus imp)
     *
     * Ensures an ImagePlus is registered with ImageJ/WindowManager (so plugins
     * can find it by title), but keeps the actual window invisible to the user.
     *
     * StarDist wants to look up an 'input' image by name, so we do this:
     *   - give our input image a safe title,
     *   - show it, then immediately hide its frame.
     */
    private static void showHidden(ImagePlus imp) {
        if (imp == null) return;
        // Ensure the image is registered for lookup by title,
        // but don't let the window be visible.
        if (imp.getWindow() == null) imp.show();
        if (imp.getWindow() != null) imp.getWindow().setVisible(false);
    }

    /**
     * Run StarDist 2D on a single-channel image and get back a label image.
     *
     * What this does:
     *   1. Make sure the input image has a stable, legal title (StarDist finds it by name).
     *   2. Snapshot currently-open images.
     *   3. Build and run the StarDist command via IJ.run("Command From Macro", ...).
     *   4. Capture the new label image that StarDist created.
     *   5. Copy calibration, hide the result, return it.
     *
     * @param input    Single-channel image to segment (already pre-scaled and contrast-normalized by caller).
     * @param modelZip Path to the StarDist .zip model.
     * @param prob     Probability threshold.
     * @param nms      NMS threshold.
     * @return         16-bit label map ImagePlus (hidden). Each object has a unique label ID (1..N).
     *
     * @throws IllegalArgumentException if the modelZip doesn't exist.
     * @throws IllegalStateException    if StarDist didn't create a label image.
     */
    public static ImagePlus runStarDist2DLabel(ImagePlus input, String modelZip, double prob, double nms) {
        if (modelZip == null || !new File(modelZip).isFile())
            throw new IllegalArgumentException("StarDist ZIP not found: " + modelZip);

        // Stable, safe title for binding
        String uniq = input.getTitle();
        if (uniq == null || uniq.isEmpty() || uniq.contains(".")) {
            uniq = "SDIN_" + System.nanoTime();
            input.setTitle(uniq);
        }
        uniq = uniq.replace("'", "");

        // Register, but keep the window invisible
        showHidden(input);

        // Snapshot open images so we can detect StarDist's output deterministically
        int[] before = ij.WindowManager.getIDList();

        int nTiles = suggestTiles(input.getWidth(), input.getHeight());
        String modelEsc = modelZip.replace("\\", "\\\\");
        String args =
                "command=[de.csbdresden.stardist.StarDist2D],"
                        + "args=['input':'" + uniq + "',"
                        + " 'modelChoice':'Model (.zip) from File',"
                        + " 'normalizeInput':'true','percentileBottom':'1.0','percentileTop':'99.8',"
                        + " 'probThresh':'" + DF.format(prob) + "','nmsThresh':'" + DF.format(nms) + "',"
                        + " 'outputType':'Label Image',"
                        + " 'modelFile':'" + modelEsc + "', 'nTiles':'" + nTiles + "',"
                        + " 'excludeBoundary':'2','roiPosition':'Automatic',"
                        + " 'verbose':'false','showCsbdeepProgress':'false','showProbAndDist':'false'],"
                        + " process=[false]";

        // Run the command; StarDist finds the input by its title
        IJ.run("Command From Macro", args);

        // Locate the label image that StarDist created
        ImagePlus label = findNewImageSince(before);
        if (label == null) {
            // Fallback for some builds that name it literally "Label Image" or "(V)"
            ImagePlus byName = ij.WindowManager.getImage("Label Image");
            if (byName == null) byName = ij.WindowManager.getImage("Label Image (V)");
            label = (byName != null) ? byName : IJ.getImage();
        }
        if (label == null) throw new IllegalStateException("StarDist did not return a label image.");

        // Keep silent and propagate calibration
        label.setCalibration(input.getCalibration());
        label.hide(); // window remains registered but not visible
        return label;
    }


    /**
     * Returns the first ImagePlus that appeared after a snapshot of window IDs.
     *
     * Typical pattern:
     *   int[] before = WindowManager.getIDList();
     *   IJ.run("Some Command", ...);
     *   ImagePlus out = findNewImageSince(before);
     *
     * @param beforeIds Snapshot of open image IDs before running some plugin.
     * @return          The new ImagePlus created, or null if nothing new opened.
     */
    public static ImagePlus findNewImageSince(int[] beforeIds) {
        java.util.HashSet<Integer> prev = new java.util.HashSet<>();
        if (beforeIds != null) for (int id : beforeIds) prev.add(id);

        int[] after = ij.WindowManager.getIDList();
        if (after == null) return null;

        for (int id : after) {
            if (!prev.contains(id)) {
                return ij.WindowManager.getImage(id);
            }
        }
        return null;
    }

    /**
     * INTERNAL HELPER (private): ensure2DLabel(ImagePlus src)
     *
     * Guarantees we have a single-slice, 16-bit label map for MorphoLibJ ops
     * that don't like hyperstacks. If the input has multiple slices, we take
     * slice 1. We then force 16-bit just in case.
     *
     * @param src Input label map, possibly multi-slice or not 16-bit.
     * @return    A duplicate single-slice 16-bit label map with same calibration.
     */
    private static ImagePlus ensure2DLabel(ImagePlus src) {
        ImagePlus lab2d;
        if (src.getStackSize() > 1) {
            lab2d = new ImagePlus("labels2d", src.getStack().getProcessor(1).duplicate());
        } else {
            lab2d = src.duplicate();
        }
        lab2d.setCalibration(src.getCalibration());
        if (lab2d.getType() != ImagePlus.GRAY16) {
            IJ.run(lab2d, "16-bit", ""); // MorphoLibJ label ops are happiest with 16-bit labels
        }
        return lab2d;
    }


    /**
     * Container object for ganglia segmentation prep.
     *
     * dijInput3C: a hidden 3-channel float hyperstack (C=3,Z=1,T=1) normalized to [0..1]
     *             ready to feed DeepImageJ.
     * rgbForOverlay: an RGB visualization where Hu is magenta and ganglia channel is green,
     *                used for overlay review / saving pretty figures.
     */
    public static final class GangliaPrep {
        public final ImagePlus dijInput3C;   // 3-channel, 32-bit hyperstack (C=3,Z=1,T=1), 0..1
        public final ImagePlus rgbForOverlay; // RGB Color image for painting overlay
        GangliaPrep(ImagePlus d, ImagePlus r) { dijInput3C=d; rgbForOverlay=r; }
    }

    /**
     * Prepare inputs for ganglia segmentation.
     *
     * Produces:
     *   - a 3-channel float hyperstack (R=Hu, G=Ganglia, B=Hu) normalized to [0..1] for DeepImageJ,
     *   - an RGB preview image for overlay/QA, with calibration copied.
     *
     * This mirrors the macro's "build RGB preview and DIJ input" steps,
     * but does it in code without popping ImageJ dialogs.
     *
     * @param maxProj    The MAX/EDF projection with all channels.
     * @param gangliaCh1 1-based channel index for ganglia/neurite signal.
     * @param huCh1      1-based channel index for Hu/neuron signal.
     * @return           GangliaPrep bundle (both images hidden in memory).
     */
    public static GangliaPrep prepareGangliaInputs(ImagePlus maxProj, int gangliaCh1, int huCh1) {
        // Extract the two source channels (grayscale, no UI)
        ImagePlus g  = Features.Tools.ImageOps.extractChannel(maxProj, gangliaCh1); // ganglia marker
        ImagePlus hu = Features.Tools.ImageOps.extractChannel(maxProj, huCh1);      // Hu (cells)
        IJ.resetMinAndMax(g);  IJ.resetMinAndMax(hu);  // define display ranges

        final int w = maxProj.getWidth(), h = maxProj.getHeight();

        // Build the review RGB image: R=Hu, G=Ganglia, B=Hu  → Hu appears magenta, ganglia green
        //    Use convertToByte(true) so display range is respected.
        ij.process.ByteProcessor r8 = (ij.process.ByteProcessor) hu.getProcessor().convertToByte(true);
        ij.process.ByteProcessor g8 = (ij.process.ByteProcessor) g .getProcessor().convertToByte(true);
        ij.process.ByteProcessor b8 = (ij.process.ByteProcessor) hu.getProcessor().convertToByte(true);

        ij.process.ColorProcessor cp = new ij.process.ColorProcessor(w, h);
        cp.setRGB((byte[]) r8.getPixels(), (byte[]) g8.getPixels(), (byte[]) b8.getPixels());

        ImagePlus rgb = new ImagePlus("ganglia_rgb", cp);
        rgb.setCalibration(maxProj.getCalibration());
        rgb.hide();

        // keep a hidden copy with the old helper name if any code still expects it.
        ImagePlus rgb2 = new ImagePlus("ganglia_rgb_2", (ij.process.ColorProcessor) cp.duplicate());
        rgb2.setCalibration(maxProj.getCalibration());
        rgb2.hide();

        // Build DeepImageJ input: 3 slices of float 0..1, exposed as C=3 hyperstack
        ij.process.FloatProcessor rf = r8.convertToFloatProcessor(); rf.multiply(1.0/255.0);
        ij.process.FloatProcessor gf = g8.convertToFloatProcessor(); gf.multiply(1.0/255.0);
        ij.process.FloatProcessor bf = b8.convertToFloatProcessor(); bf.multiply(1.0/255.0);

        ImageStack st = new ImageStack(w, h);
        st.addSlice("R", rf); st.addSlice("G", gf); st.addSlice("B", bf);

        ImagePlus dij = new ImagePlus("ganglia_rgb", st);   // title kept stable for DIJ
        dij.setDimensions(3, 1, 1);                         // C=3
        dij.setOpenAsHyperStack(true);
        dij.setCalibration(maxProj.getCalibration());
        dij.hide();

        // Tidy temps
        g.changes = false;  g.close();
        hu.changes = false; hu.close();

        return new GangliaPrep(dij, rgb);
    }






    /**
     * Full ganglia segmentation pipeline using DeepImageJ, including cleanup and optional manual review.
     *
     * Steps:
     *   1. Build DeepImageJ input (3-channel float stack) and an RGB overlay preview (prepareGangliaInputs).
     *   2. Run "DeepImageJ Run" on the model in {@code modelFolderName} (under Fiji/models).
     *   3. Threshold probability map to binary (gangliaProbThresh01).
     *   4. Morphological open / size filtering in µm² (converted to px using calibration).
     *   5. (Optional) Interactive paint step where user can fix the ganglia mask
     *      using a white/black brush palette (showPaintPalette).
     *   6. Additional size opening passes to clean up.
     *
     * Returns:
     *   - A final 8-bit binary mask of ganglia (foreground=255).
     *     The caller can convert that to labels if needed.
     *
     * @param maxProj            MAX/EDF projection.
     * @param gangliaCh1         1-based ganglia marker channel index.
     * @param huCh1              1-based Hu channel index.
     * @param modelFolderName    Name of the DeepImageJ model folder under Fiji/models.
     * @param minAreaUm2         Minimum ganglion size cutoff in µm² (fallback to params if 0).
     * @param p                  Params (for thresholds, interactive review toggle, etc.).
     * @param progress           Progress UI; we temporarily hide it during manual review.
     * @return                   8-bit binary mask image ("ganglia_mask"-like), hidden.
     *
     * @throws IllegalArgumentException if model folder isn't found.
     * @throws IllegalStateException    if DeepImageJ doesn't return an output.
     */
    public static ImagePlus runDeepImageJForGanglia(
            ImagePlus maxProj, int gangliaCh1, int huCh1,
            String modelFolderName, double minAreaUm2, Params p, ProgressUI progress) {

        GangliaPrep prep = prepareGangliaInputs(maxProj, gangliaCh1, huCh1);
        ImagePlus in3C = prep.dijInput3C;        // feed DIJ
        ImagePlus rgbColor = prep.rgbForOverlay;

        // DIJ model folder
        File fiji = new File(IJ.getDirectory("imagej"));
        File modelDir = new File(new File(fiji, "models"), modelFolderName);
        if (!modelDir.isDirectory())
            throw new IllegalArgumentException("DeepImageJ model folder not found: " + modelDir);

        int[] before = ij.WindowManager.getIDList();
        IJ.run(in3C, "DeepImageJ Run", "model_path=[" + modelDir.getAbsolutePath() + "] input_path=null output_folder=null display_output=all");
        ImagePlus out = Features.Core.PluginCalls.findNewImageSince(before);
        if (out == null) out = IJ.getImage();
        if (out == null) throw new IllegalStateException("DeepImageJ produced no output.");
        out.setCalibration(maxProj.getCalibration());


        if (p != null && p.gangliaProbThresh01 != null) {
            out = probToBinary(out, p.gangliaProbThresh01);
        }

        // Binary Open
        int it = (p != null ? Math.max(0, p.gangliaOpenIterations) : 3);
        IJ.run(out, "Options...", "iterations=" + it + " count=2 black do=Open");

        // Size Opening in µm² to px using MAX calibration
        double px = (maxProj.getCalibration() != null && maxProj.getCalibration().pixelWidth > 0)
                ? maxProj.getCalibration().pixelWidth : 1.0;
        double areaUm2 = (minAreaUm2 > 0 ? minAreaUm2
                : (p != null && p.gangliaMinAreaUm2 != null ? p.gangliaMinAreaUm2 : 200.0));
        int minAreaPx = (int)Math.ceil(areaUm2 / (px * px));
        SilentRun.runAndGrab(out, "Size Opening 2D/3D", "min=" + Math.max(1, minAreaPx));


        // Interactive review
        if (p != null && p.gangliaInteractiveReview) {
            ij.macro.Interpreter.batchMode = false;
            progress.setVisible(false);

            out.setTitle("ganglia_mask");

            // colored overlay
            ij.gui.ImageRoi ir = new ij.gui.ImageRoi(0, 0, rgbColor.getProcessor().duplicate());
            ir.setOpacity(0.60);
            out.setOverlay(new ij.gui.Overlay(ir));

            // make sure *this* window has focus
            out.show();
            IJ.selectWindow(out.getID());
            if (out.getWindow() != null) {
                out.getWindow().toFront();
                if (out.getCanvas() != null) out.getCanvas().requestFocusInWindow();
            }

            // set brush tool robustly (Toolbar API + string fallback)

            IJ.setTool("Paintbrush Tool");

            // ensure FG/BG are correct for painting; X will toggle them
            IJ.setForegroundColor(255, 255, 255);   // WHITE = add
            IJ.setBackgroundColor(0, 0, 0);         // BLACK = remove

            showPaintPalette(
                    out.getWindow(),
                    "Ganglia overlay",
                    "Paint on 'ganglia_mask'. WHITE adds, BLACK removes."
            );

            // clean up + hide the review window so it doesn't reappear later
            IJ.run(out, "Select None", "");
            out.setOverlay(null);
            if (out.getWindow() != null) out.hide();

            ij.macro.Interpreter.batchMode = true;
            progress.setVisible(true);

            // optional tidy
            rgbColor.changes = false; rgbColor.close();
        }



        // Second Size Opening pass
        out = SilentRun.runAndGrab(out, "Size Opening 2D/3D", "min=" + Math.max(1, minAreaPx));
        out = SilentRun.runAndGrab(out, "Size Opening 2D/3D", "min=" + Math.max(1, minAreaPx));
        // Cleanup temps
        if (rgbColor != in3C) { rgbColor.changes = false; rgbColor.close(); }
        if (in3C != out)       { in3C.changes = false; in3C.close(); }
        ImagePlus rgb2 = ij.WindowManager.getImage("ganglia_rgb_2");
        if (rgb2 != null) { rgb2.changes = false; rgb2.close(); }

        // Return final binary (macro keeps binary here)
        if (out.getWindow() != null) out.hide();
        return out;
    }

    /**
     * Clear any active threshold overlay (red LUT) on an ImagePlus.
     * Safe to call on null.
     *
     * @param imp image whose threshold should be reset.
     */
    public static void clearThreshold(ImagePlus imp) {
        if (imp != null && imp.getProcessor() != null) {
            imp.getProcessor().resetThreshold();  // clears the red overlay
            imp.updateAndDraw();
        }
    }


    /**
     * Convert a probability/probability-like map (0..1 scaled or grayscale)
     * into a binary (0/255) mask using a given threshold in [0..1].
     *
     * Steps:
     *   - Ensure 8-bit.
     *   - Threshold: t = round(thresh01 * 255).
     *   - Convert to Mask (IJ.run).
     *   - Clear the visual threshold overlay.
     *
     * @param prob       Input probability map.
     * @param thresh01   Threshold in [0..1] where pixels >= thresh become 255.
     * @return           The same ImagePlus, now an 8-bit binary mask.
     */
    public static ImagePlus probToBinary(ImagePlus prob, double thresh01) {
        if (prob.getBitDepth() != 8) IJ.run(prob, "8-bit", ""); // scales 0..255
        int t = (int)Math.round(Math.max(0, Math.min(255, thresh01 * 255.0)));
        IJ.setThreshold(prob, t, 255);
        SilentRun.on(prob, "Convert to Mask", "");
        clearThreshold(prob);
        prob.hide();
        return prob; // now an 8-bit mask
    }



    /**
     * Convert an 8-bit binary mask into a 16-bit label map via "Connected Components Labeling",
     * and hide the result.
     *
     * Assumes:
     *   - Foreground is 255, background is 0.
     *
     * @param binary 8-bit mask.
     * @return       16-bit label map (1..N), calibration copied.
     * @throws IllegalStateException if labeling didn't produce an output.
     */
    public static ImagePlus binaryToLabels(ImagePlus binary) {
        SilentRun.on(binary, "Convert to Mask", "");
        int[] before = ij.WindowManager.getIDList();
        SilentRun.on(binary, "Connected Components Labeling", "connectivity=8");
        ImagePlus labels = findNewImageSince(before);
        if (labels == null) labels = IJ.getImage();
        if (labels == null) throw new IllegalStateException("Connected Components produced no output.");

        labels.setCalibration(binary.getCalibration());
        clearThreshold(labels);
        labels.hide();
        return labels;
    }

    /**
     * Paint the current contents of a RoiManager into a binary mask with the same
     * spatial size and calibration as {@code ref}.
     *
     * Implementation:
     *   - Create a blank 8-bit image.
     *   - Associate RoiManager with it.
     *   - "Show All without labels" + "Fill" to burn ROIs in.
     *
     * @param ref Reference image for width/height/calibration.
     * @param rm  RoiManager containing ROIs to rasterize.
     * @return    8-bit binary mask ImagePlus (foreground=255 where ROIs were).
     */
    public static ImagePlus roisToBinary(ImagePlus ref, RoiManager rm) {
        ImagePlus mask = IJ.createImage("ganglia_binary", "8-bit black", ref.getWidth(), ref.getHeight(), 1);
        mask.setCalibration(ref.getCalibration());

        // rm bindings can target an ImagePlus without showing:
        rm.runCommand("Associate", "true");
        rm.runCommand(mask, "Show All without labels");
        rm.runCommand(mask, "Deselect");
        rm.runCommand(mask, "Fill");
        return mask;
    }

    /**
     * INTERNAL HELPER (private): setAddMode()
     *
     * Helper for showPaintPalette(): sets ImageJ's paintbrush FG color to white
     * (add foreground) and BG to black.
     */
    private static void setAddMode() { ij.IJ.setForegroundColor(255,255,255); ij.IJ.setBackgroundColor(0,0,0); }
    /**
     * INTERNAL HELPER (private): setEraseMode()
     *
     * Helper for showPaintPalette(): sets ImageJ's paintbrush FG color to black
     * (erase) and BG to white.
     */
    private static void setEraseMode(){ ij.IJ.setForegroundColor(0,0,0);     ij.IJ.setBackgroundColor(255,255,255); }

    /**
     * Pops up a tiny always-on-top palette with:
     *   - a toggle button ("Add (white)" / "Erase (black)") that flips the
     *     brush's foreground/background colors,
     *   - a "Done" button to finish editing.
     *
     * Meanwhile, the user can directly paint onto the currently open ganglia mask
     * image using ImageJ's paintbrush tool (we set/tool it before opening the dialog).
     *
     * This method:
     *   - Brings the image window to the front so brush input goes there.
     *   - Blocks the calling (pipeline) thread until "Done" is clicked,
     *     but does NOT block the Swing EDT (palette is MODELESS).
     *
     * @param owner    Parent window to center the palette near (can be null).
     * @param title    Title for the palette dialog.
     * @param helpLine Small HTML-friendly help text to show in the palette.
     */
    public static void showPaintPalette(java.awt.Window owner, String title, String helpLine) {
        // start in ADD mode by default
        setAddMode();

        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

        final JDialog dlg = new JDialog(owner, title, Dialog.ModalityType.MODELESS);
        dlg.setAlwaysOnTop(true);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(8,8));
        root.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        if (helpLine != null && !helpLine.isEmpty()) {
            JLabel tip = new JLabel("<html>" + helpLine + "<br/>Tip: toggle paint on/off with button.</html>");
            root.add(tip, BorderLayout.NORTH);
        }

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JToggleButton mode = new JToggleButton("Add (white)");
        JButton done = new JButton("Done");

        mode.addActionListener(e -> {
            if (mode.isSelected()) { setEraseMode(); mode.setText("Erase (black)"); }
            else                   { setAddMode();   mode.setText("Add (white)");   }
        });
        done.addActionListener(e -> { dlg.dispose(); latch.countDown(); });

        row.add(mode); row.add(done);
        root.add(row, BorderLayout.CENTER);

        dlg.setContentPane(root);
        dlg.pack();
        dlg.setResizable(false);
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);

        // Give focus back to the image window/canvas right after showing the palette
        if (owner != null) {
            SwingUtilities.invokeLater(() -> {
                owner.toFront();
                owner.requestFocus();
            });
        }

        // Block ONLY the calling thread (not the EDT).
        if (!SwingUtilities.isEventDispatchThread()) {
            try { latch.await(); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        }
    }



}