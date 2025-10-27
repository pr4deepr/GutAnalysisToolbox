package UI.util;

import ij.IJ;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Locale;

/**
 * Centralized validation helpers for user-supplied file paths and numeric-ish
 * parameters, with friendly Swing error dialogs.
 *
 * <p>
 * Used by multiple panes before running analysis to ensure we have:
 * </p>
 * <ul>
 *   <li>A valid image file with a supported extension.</li>
 *   <li>A valid model ZIP (.zip) when required.</li>
 *   <li>A writable output directory (or ability to create it).</li>
 *   <li>An existing ganglia model folder under {@code &lt;Fiji&gt;/models}.</li>
 * </ul>
 *
 * <p>
 * All methods are static; class is not instantiable.
 * </p>
 */
public final class InputValidation {

    private InputValidation() {}

    // Allowed image types (edit here once for all panes)
    private static final String[] IMAGE_EXTS = {
            "tif","tiff","ome.tif","czi","lif","nd2","lsm"
    };

    /**
     * True if the given string is "placeholder-ish" instead of a real path.
     *
     * <p>
     * We treat null, blank, or strings containing "path/to" as placeholders.
     * This lets us detect when a text field is still showing example text like
     * {@code /path/to/image.tif} rather than an actual chosen file.
     * </p>
     *
     * @param s
     *        Candidate file path string from the UI.
     *
     * @return {@code true} if it looks like a placeholder (null/blank/"path/to"),
     *         otherwise {@code false}.
     */
    public static boolean isPlaceholderPath(String s) {
        if (s == null) return true;
        String t = s.trim();
        if (t.isEmpty()) return true;
        // catches “/path/to/image”, “…/path/to/…”, etc.
        return t.contains("path/to");
    }

    /**
     * Check whether a file has a recognized microscopy image extension.
     *
     * <p>
     * Allowed types are defined once in {@link #IMAGE_EXTS}. We also do a
     * fallback check for ".tif" / ".tiff" specifically, to catch cases
     * like "something.ome.tif".
     * </p>
     *
     * @param f
     *        File to test.
     *
     * @return {@code true} if {@code f}'s name ends with a supported extension
     *         (case-insensitive), otherwise {@code false}.
     */
    public static boolean hasImageExtension(File f) {
        String name = f.getName().toLowerCase(Locale.ROOT);
        for (String ext : IMAGE_EXTS) {
            if (name.endsWith("." + ext) || name.endsWith(ext)) return true;
        }
        // allow plain ".tif" check to also hit when ext list contains "ome.tif"
        return name.endsWith(".tif") || name.endsWith(".tiff");
    }

    /**
     * Validate that a path points to an existing, supported image file.
     *
     * <p>
     * Rules:
     * </p>
     * <ul>
     *   <li>Rejects placeholder paths (see {@link #isPlaceholderPath(String)}).</li>
     *   <li>Requires an actual on-disk file.</li>
     *   <li>Requires an allowed extension (see {@link #hasImageExtension(File)}).</li>
     * </ul>
     *
     * <p>
     * On failure we show a modal {@link JOptionPane#showMessageDialog}
     * explaining what went wrong, and return {@code false}. On success we
     * return {@code true} without showing anything.
     * </p>
     *
     * @param parent
     *        Parent component for error dialogs (may be {@code null}).
     *
     * @param path
     *        The user-entered path string.
     *
     * @return {@code true} if valid and OK to proceed; {@code false} otherwise.
     */
    public static boolean validateImageOrShow(Component parent, String path) {
        if (isPlaceholderPath(path)) {
            JOptionPane.showMessageDialog(parent, "Please select an input image file.",
                    "Missing image", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        File f = new File(path);
        if (!f.isFile()) {
            JOptionPane.showMessageDialog(parent, "Input image does not exist:\n" + f.getAbsolutePath(),
                    "Invalid image", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        if (!hasImageExtension(f)) {
            JOptionPane.showMessageDialog(parent,
                    "Unsupported image type. Allowed: .tif/.tiff/.ome.tif, .czi, .lif, .nd2, .lsm",
                    "Invalid image type", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    /**
     * Validate that {@code path} refers to a model ZIP on disk.
     *
     * <p>
     * Used for StarDist / subtype models which are shipped as .zip files.
     * </p>
     *
     * <p>
     * Fails if:
     * </p>
     * <ul>
     *   <li>{@code path} is null/blank,</li>
     *   <li>the file does not exist,</li>
     *   <li>or the filename does not end with ".zip".</li>
     * </ul>
     *
     * <p>
     * On failure, shows an error dialog including {@code label}
     * (e.g. "StarDist model") for clarity, and returns {@code false}.
     * </p>
     *
     * @param parent
     *        Parent component for the error dialog (may be {@code null}).
     *
     * @param path
     *        Candidate file path to the model .zip.
     *
     * @param label
     *        Human-readable label for the thing being validated
     *        (e.g. "the StarDist model").
     *
     * @return {@code true} if the ZIP exists and ends in ".zip", else {@code false}.
     */
    public static boolean validateZipOrShow(Component parent, String path, String label) {
        if (path == null || path.trim().isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Please choose " + label + " (.zip).",
                    "Missing file", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        File f = new File(path);
        if (!f.isFile() || !f.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            JOptionPane.showMessageDialog(parent, label + " must be a .zip:\n" + f.getAbsolutePath(),
                    "Invalid file", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    /**
     * Validate (or create) an output directory path.
     *
     * <p>
     * Behavior:
     * </p>
     * <ul>
     *   <li>If {@code path} is null/blank, we treat that as "optional" and allow it (return true).</li>
     *   <li>If the directory exists:
     *       <ul>
     *         <li>It must actually be a directory, and</li>
     *         <li>It must be writable.</li>
     *       </ul>
     *   </li>
     *   <li>If it does not exist, we attempt {@code mkdirs()}. If that fails, we show an error.</li>
     * </ul>
     *
     * @param parent
     *        Parent component for error dialogs (may be {@code null}).
     *
     * @param path
     *        User-entered output directory path (may be blank / null to skip).
     *
     * @return {@code true} if acceptable / created / writable; {@code false} otherwise.
     */
    public static boolean validateOutputDirOrShow(Component parent, String path) {
        if (path == null || path.trim().isEmpty()) return true; // optional
        File dir = new File(path);
        if (dir.exists()) {
            if (!dir.isDirectory()) {
                JOptionPane.showMessageDialog(parent, "Output path exists but is not a directory:\n" + dir.getAbsolutePath(),
                        "Invalid output", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            if (!dir.canWrite()) {
                JOptionPane.showMessageDialog(parent, "Output directory is not writable:\n" + dir.getAbsolutePath(),
                        "Invalid output", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            return true;
        }
        if (!dir.mkdirs()) {
            JOptionPane.showMessageDialog(parent, "Could not create output directory:\n" + dir.getAbsolutePath(),
                    "Invalid output", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    /**
     * Validate that a named subfolder under {@code Fiji/models} exists.
     *
     * <p>
     * This is used for the Ganglia / DeepImageJ model, which is usually provided
     * as a folder (not just a single .zip) and must live under
     * the ImageJ/Fiji "models" directory.
     * </p>
     *
     * <p>
     * If {@code folderName} is blank/missing or if the resolved folder doesn't
     * exist, we show an error dialog and return {@code false}.
     * </p>
     *
     * @param parent
     *        Parent component for displaying modal errors (may be {@code null}).
     *
     * @param folderName
     *        The expected folder name (not a full path) under {@code &lt;Fiji&gt;/models}.
     *
     * @return {@code true} if the folder exists, {@code false} (with dialog) otherwise.
     */
    public static boolean validateModelsFolderOrShow(Component parent, String folderName) {
        if (folderName == null || folderName.trim().isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                    "Please choose a Ganglia model folder (under <Fiji>/models).",
                    "Missing ganglia model", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        File models = new File(IJ.getDirectory("imagej"), "models");
        File target = new File(models, folderName);
        if (!target.isDirectory()) {
            JOptionPane.showMessageDialog(parent,
                    "Ganglia model folder not found under <Fiji>/models:\n" + target.getAbsolutePath(),
                    "Invalid ganglia model folder", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }
}
