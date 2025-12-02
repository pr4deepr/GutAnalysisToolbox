package services.multiplex.core;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.frame.RoiManager;

import static services.multiplex.util.IJUtils.selectWindow;

/**
 * Provides feature-matching utilities used to compute landmark correspondences
 * between a reference (template) image and a target image in multiplex registration.
 *
 * <h3>Overview</h3>
 * This helper encapsulates the macro-level strategy:
 * <ol>
 *   <li>Try <b>SIFT</b> (mpicbg/Descriptor-based registration) with increasing
 *       {@code steps_per_scale_octave};</li>
 *   <li>Fallback to <b>MOPS</b> if SIFT yields no matches;</li>
 *   <li>Fallback to <b>Block Matching</b> if both SIFT and MOPS fail.</li>
 * </ol>
 *
 * On success, the method stores the landmark correspondences (as selections/ROIs)
 * for both images in the global {@link RoiManager}, using a consistent naming scheme:
 * <pre>
 *   &lt;common&gt;_&lt;k&gt;_ref    // landmarks on the reference image
 *   &lt;common&gt;_&lt;k&gt;_target // landmarks on the target image
 * </pre>
 * where {@code <common>} is the normalized name of the common marker (e.g., "hu")
 * and {@code k} is a 1-based pair index provided by the caller.
 *
 * <h3>Dependencies</h3>
 * Requires the Fiji plugins that register these commands:
 * <ul>
 *   <li><i>Extract SIFT Correspondences</i></li>
 *   <li><i>Extract MOPS Correspondences</i></li>
 *   <li><i>Extract Block Matching Correspondences</i></li>
 * </ul>
 * These come from the mpicbg / Descriptor-based registration update site.
 *
 * <h3>Thread-Safety</h3>
 * Not thread-safe. This class manipulates ImageJ global state (active windows,
 * ROI Manager selections). Call from a controlled thread and never in parallel
 * for multiple image pairs.
 */
public final class FeatureMatching {

    /** Utility class; not instantiable. */
    private FeatureMatching() {}

    /**
     * Attempt to compute landmark correspondences between {@code ref} and {@code target}
     * using SIFT, then MOPS, then Block Matching as a last resort. When matches are found,
     * selections (ROIs) are pushed into the global {@link RoiManager} with stable names:
     * <pre>
     *   &lt;commonMarker&gt;_&lt;pairIndex&gt;_ref
     *   &lt;commonMarker&gt;_&lt;pairIndex&gt;_target
     * </pre>
     *
     * <h4>Success Criteria</h4>
     * After running a method (SIFT/MOPS/Block Matching), both {@code ref} and {@code target}
     * are expected to have a non-null selection ({@link ImagePlus#getRoi()}). If not, the method
     * tries the next fallback.
     *
     * <h4>Side Effects</h4>
     * <ul>
     *   <li>Calls {@code IJ.run(...)} commands ("Extract * Correspondences");</li>
     *   <li>Adds ROIs to the global {@link RoiManager} and renames them;</li>
     *   <li>Calls {@code IJ.run(image, "Remove Overlay", "")} and {@code "Select None"} on images.</li>
     * </ul>
     *
     * @param ref                  the reference (template) image; must be opened and visible.
     * @param target               the target image to be matched against {@code ref}; opened and visible.
     * @param commonMarker         normalized common marker token used for naming (e.g., "hu").
     * @param pairIndex            1-based index used to label the ROI pair (e.g., 1, 2, ...).
     * @param minimalInlierRatio   SIFT parameter; minimum inlier ratio for acceptance (e.g., 0.50).
     * @param stepsPerScaleOctave  SIFT parameter; initial steps per scale octave (increases in a loop).
     * @return {@code true} if any of SIFT/MOPS/Block Matching produced non-empty correspondences on both
     *         images; {@code false} otherwise (no landmarks were found).
     * @throws RuntimeException if underlying {@code IJ.run(...)} commands throw (e.g., plugin missing).
     */
    public static boolean matchWithFallbacks(ImagePlus ref,
                                             ImagePlus target,
                                             String commonMarker,
                                             int pairIndex,
                                             double minimalInlierRatio,
                                             int stepsPerScaleOctave) {

        // ---- SIFT: try with increasing stepsPerScaleOctave up to 30 ----
        int steps = stepsPerScaleOctave;
        boolean found = false;
        while (!found && steps <= 30) {
            String args = String.format(
                    "source_image=[%s] target_image=[%s] initial_gaussian_blur=1.60 steps_per_scale_octave=%d " +
                            "minimum_image_size=32 maximum_image_size=%d feature_descriptor_size=4 " +
                            "feature_descriptor_orientation_bins=8 closest/next_closest_ratio=0.92 " +
                            "filter maximal_alignment_error=25 minimal_inlier_ratio=%.3f " +
                            "minimal_number_of_inliers=7 expected_transformation=Affine",
                    ref.getTitle(), target.getTitle(), steps,
                    Math.max(ref.getWidth(), ref.getHeight()), minimalInlierRatio);

            IJ.run("Extract SIFT Correspondences", args);
            found = hasSelectionOnBoth(ref, target);
            steps += 3;
        }

        // ---- MOPS fallback ----
        if (!found) {
            String args = String.format(
                    "source_image=[%s] target_image=[%s] initial_gaussian_blur=1.60 steps_per_scale_octave=%d " +
                            "minimum_image_size=64 maximum_image_size=%d feature_descriptor_size=16 " +
                            "closest/next_closest_ratio=0.92 maximal_alignment_error=25 inlier_ratio=0.50 " +
                            "expected_transformation=Affine",
                    ref.getTitle(), target.getTitle(), steps,
                    Math.max(ref.getWidth(), ref.getHeight()));
            IJ.run("Extract MOPS Correspondences", args);
            found = hasSelectionOnBoth(ref, target);
        }

        // ---- Block Matching fallback ----
        if (!found) {
            String args = String.format(
                    "source_image=[%s] target_image=[%s] layer_scale=1 search_radius=50 block_radius=50 " +
                            "resolution=24 minimal_pmcc_r=0.10 maximal_curvature_ratio=1000 " +
                            "maximal_second_best_r/best_r=1 use_local_smoothness_filter " +
                            "approximate_local_transformation=Affine local_region_sigma=65 " +
                            "maximal_local_displacement=12 maximal_local_displacement_0=3 export",
                    ref.getTitle(), target.getTitle());
            IJ.run("Extract Block Matching Correspondences", args);
            found = hasSelectionOnBoth(ref, target);
        }

        if (!found) return false;

        // ---- Store ROI landmarks with stable names in the ROI Manager ----
        RoiManager rm = RoiManager.getInstance2();
        if (rm == null) rm = new RoiManager();

        // Add landmarks on the reference image
        selectWindow(ref.getTitle());
        rm.addRoi(ref.getRoi());
        rm.select(rm.getCount() - 1);
        rm.rename(rm.getCount() - 1, commonMarker + "_" + pairIndex + "_ref");
        IJ.run(ref, "Remove Overlay", "");
        IJ.run(ref, "Select None", "");

        // Add landmarks on the target image
        selectWindow(target.getTitle());
        rm.addRoi(target.getRoi());
        rm.select(rm.getCount() - 1);
        rm.rename(rm.getCount() - 1, commonMarker + "_" + pairIndex + "_target");
        IJ.run(target, "Select None", "");

        return true;
    }

    /**
     * @return {@code true} if both images currently have a non-null ROI selection,
     *         indicating that a correspondence extraction step produced landmarks.
     */
    private static boolean hasSelectionOnBoth(ImagePlus ref, ImagePlus target) {
        return ref.getRoi() != null && target.getRoi() != null;
    }
}
