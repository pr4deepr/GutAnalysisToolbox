package services.multiplex.util;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.frame.RoiManager;

/**
 * Utility methods to simplify common ImageJ operations used in
 * the multiplex registration workflow.
 * <p>
 * These are thin wrappers around {@link IJ} and {@link WindowManager}
 * that encapsulate repetitive macro-like commands (copy/paste,
 * select window, reset ROI manager, etc.).
 * </p>
 *
 * <h3>Design notes</h3>
 * <ul>
 *   <li>All methods act on the global ImageJ state (open windows,
 *       current active image, ROI Manager singleton).</li>
 *   <li>None of the methods are thread-safe; call from the same
 *       thread that owns the ImageJ UI (typically the EDT or a
 *       controlled worker thread that knows ImageJ is running).</li>
 *   <li>Exceptions from IJ macro commands (e.g., no active window)
 *       will propagate as {@link RuntimeException}s.</li>
 * </ul>
 */
public final class IJUtils {

    /** Utility class; not instantiable. */
    private IJUtils() {}

    /**
     * Ensure the global {@link RoiManager} exists and reset its contents.
     * <p>
     * If an instance already exists, it is cleared.
     * If not, a new instance is created and immediately reset.
     * </p>
     */
    public static void ensureRoiManagerReset() {
        RoiManager rm = RoiManager.getInstance2();
        if (rm != null) {
            rm.reset();
        } else {
            new RoiManager().reset();
        }
    }

    /**
     * Close all currently open windows (images and non-image frames).
     * <p>
     * Equivalent to the ImageJ macro command:
     * <pre>{@code
     * IJ.run("Close All");
     * }</pre>
     *
     * <b>Note:</b> this is global and will close user images as well,
     * not just those opened by this plugin.
     */
    public static void closeAllNonImageWindows() {
        IJ.run("Close All");
    }

    /**
     * Make the given window the active/current window by title.
     *
     * @param title title of the image window to select.
     *              If null or no window matches, nothing happens.
     */
    public static void selectWindow(String title) {
        ImagePlus imp = WindowManager.getImage(title);
        if (imp != null) {
            WindowManager.setCurrentWindow(imp.getWindow());
        }
    }

    /**
     * Copy the active image (or ROI selection) to the clipboard
     * using standard macro commands.
     * <p>
     * Equivalent macro sequence:
     * <pre>{@code
     * run("Select All");
     * run("Copy");
     * run("Select None");
     * }</pre>
     * <b>Note:</b> This assumes an active image window is available.
     */
    public static void copyActiveToClipboard() {
        IJ.run("Select All");
        IJ.run("Copy");
        IJ.run("Select None");
    }
}
