package Features.Tools;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;

/**
 * Utility to run IJ.run(...) commands "silently" against a specific ImagePlus,
 * without stealing focus or popping new windows unnecessarily.
 *
 * Why this exists:
 *  - Some ImageJ commands act on the "current image".
 *  - We want to run them on a given ImagePlus programmatically,
 *    even if that image isn't frontmost / visible.
 *
 * We temporarily set that ImagePlus as the "temp current image" in WindowManager,
 * run the command, then restore.
 *
 * There's also runAndGrab() to capture any output ImagePlus the command produces.
 */
public final class SilentRun {
    private SilentRun() {}

    /**
     * Run an ImageJ command with options string, binding execution to `imp`.
     *
     * Example:
     *   SilentRun.on(myImage, "Convert to Mask", "");
     *
     * Implementation details:
     *   - WindowManager.setTempCurrentImage(imp) makes ImageJ think `imp` is active.
     *   - IJ.run(...) executes the command/plug-in.
     *   - We reset the temp current image in finally{}.
     *
     * @param imp      target ImagePlus to act on.
     * @param command  IJ.run command string (e.g. "Convert to Mask").
     * @param options  IJ.run options string.
     */
    public static void on(ImagePlus imp, String command, String options) {
        WindowManager.setTempCurrentImage(imp);
        try {
            IJ.run(imp, command, options);  // bound execution
        } finally {
            WindowManager.setTempCurrentImage(null);
        }
    }

    /**
     * Same idea as on(...), but returns any *new* image produced by that command.
     *
     * Steps:
     *   1. Record all open image IDs.
     *   2. Call on(bound, command, options).
     *   3. Ask PluginCalls.findNewImageSince(...) for the newly created ImagePlus.
     *   4. Hide that new ImagePlus before returning (so we don't spam windows).
     *
     * This is used for commands like "Size Opening 2D/3D" or "Connected Components Labeling"
     * which generate a brand new output image.
     *
     * @param bound    target ImagePlus to act on.
     * @param command  IJ.run command name.
     * @param options  IJ.run options.
     * @return         newly created ImagePlus from that command (hidden), or null if nothing new appeared.
     */
    public static ImagePlus runAndGrab(ImagePlus bound, String command, String options) {
        int[] before = ij.WindowManager.getIDList();
        on(bound, command, options);
        ImagePlus out = Features.Core.PluginCalls.findNewImageSince(before);
        if (out != null) out.hide();
        return out;
    }
}