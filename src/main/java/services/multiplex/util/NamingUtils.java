package services.multiplex.util;

import java.io.File;
import java.util.Locale;

/**
 * Utility functions for consistent filename handling in multiplex workflows.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Derive base names without file extensions.</li>
 *   <li>Normalize identifiers (lowercase, trimmed, no spaces) for
 *       case-insensitive matching across rounds and markers.</li>
 *   <li>Check whether a file has a .tif/.tiff extension.</li>
 * </ul>
 *
 * <p><b>Design notes</b></p>
 * <ul>
 *   <li>Normalization is aggressive: converts to lowercase and strips
 *       spaces, so that filenames like {@code "Layer 1_Hu.tif"} and
 *       {@code "layer1_hu.TIF"} normalize to the same token.</li>
 *   <li>Methods do not check for {@code null} files; callers must supply
 *       valid {@link File} references.</li>
 * </ul>
 */
public final class NamingUtils {

    /** Utility class; not instantiable. */
    private NamingUtils() {}

    /**
     * Get the filename without its extension.
     *
     * @param f file whose basename to extract; must not be {@code null}.
     * @return basename string with extension removed (if present).
     *         Example: {@code "Image1.tif"} → {@code "Image1"}.
     */
    public static String baseNameNoExt(File f) {
        String name = f.getName();
        int idx = name.lastIndexOf('.');
        return (idx >= 0) ? name.substring(0, idx) : name;
    }

    /**
     * Normalize a string for case-insensitive matching.
     * <p>
     * Rules:
     * <ul>
     *   <li>Convert to lowercase (root locale);</li>
     *   <li>Trim leading and trailing whitespace;</li>
     *   <li>Remove all spaces.</li>
     * </ul>
     *
     * @param s input string (may be null).
     * @return normalized string, or {@code ""} if input is null.
     *         Example: {@code " Layer 1 "} → {@code "layer1"}.
     */
    public static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).trim().replace(" ", "");
    }

    /**
     * Determine whether a file is a TIFF image by extension.
     * <p>
     * Accepted suffixes: {@code .tif}, {@code .tiff} (case-insensitive).
     *
     * @param f file to check.
     * @return true if the filename ends with a TIFF extension.
     */
    public static boolean hasTifExt(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        return n.endsWith(".tif") || n.endsWith(".tiff");
    }
}
