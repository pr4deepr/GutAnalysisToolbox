package services.multiplex.core;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.NewImage;
import ij.plugin.frame.RoiManager;
import services.multiplex.config.MultiplexConfig;
import services.multiplex.util.NamingUtils;

import ij.macro.Interpreter;      // batch mode flag (ImageJ macro engine)
import javax.swing.*;             // for the final popup (already imported below but safe)
import java.awt.Desktop;          // to open the Results folder in OS file explorer


import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static services.multiplex.util.IJUtils.ensureRoiManagerReset;

/**
 * Orchestrates the full multiplex registration workflow, ported from the ImageJ macro.
 * <p>
 * <b>Purpose</b><br>
 * Aligns multi-round immunostaining/IF images that share a common reference marker (e.g., Hu/DAPI) across
 * rounds (Layer1, Layer2, ...). The service:
 * <ol>
 *   <li>Discovers input images from a folder;</li>
 *   <li>Builds a stack of the common marker and computes landmark correspondences
 *       between the first (reference) and each subsequent round;</li>
 *   <li>Applies those landmark-derived transforms to every non-common channel in later rounds;</li>
 *   <li>Saves an aligned composite stack and the landmark ROI correspondences.</li>
 * </ol>
 *
 * <p><b>Inputs</b><br>
 * Provided via {@link MultiplexConfig}:
 * <ul>
 *   <li><i>imageFolder</i> — directory containing .tif/.tiff files;</li>
 *   <li><i>commonMarker</i> — substring that identifies the reference marker present in every round;</li>
 *   <li><i>multiplexRounds</i> — number of rounds (Layer1..LayerN) to traverse;</li>
 *   <li><i>layerKeyword</i> — token that distinguishes each round (e.g., "Layer" or "Round");</li>
 *   <li><i>saveFolder</i> — where to write outputs (a "Results" subfolder is created);</li>
 *   <li>Optional SIFT tuning parameters.</li>
 * </ul>
 *
 * <p><b>Outputs</b> (written to {@code <saveFolder>/Results/})<br>
 * <ul>
 *   <li>{@code <common>_stack.tif} — stack of the common marker across rounds (for QC);</li>
 *   <li>{@code Aligned_Stack.tif} — the final aligned, composite hyperstack (C = number of channels);</li>
 *   <li>{@code landmark_correspondences.zip} — ROI Manager export of landmark pairs used for warping.</li>
 * </ul>
 *
 * <p><b>Assumptions & Conventions</b><br>
 * <ul>
 *   <li>Filenames contain the layer token + index (e.g., {@code Layer1}, {@code Layer2}) and the marker suffix.</li>
 *   <li>All rounds contain an image of the common marker; the first occurrence becomes the reference.</li>
 *   <li>Landmark extraction runs via {@code FeatureMatching.matchWithFallbacks}, which tries SIFT → MOPS → BlockMatching.</li>
 *   <li>Per-round landmark ROI pairs are pushed into the global {@link RoiManager} in the order:
 *       {@code <common>_<k>_ref}, then {@code <common>_<k>_target}. For round r&gt;1, we select pair offset {@code 2*(r-1)}.</li>
 * </ul>
 *
 * <p><b>Thread-safety</b><br>
 * This class manipulates ImageJ global state (windows, ROI Manager) and is <b>not</b> thread-safe.
 * Execute on the EDT only to trigger, but run the heavy processing off-EDT (e.g., {@code SwingWorker}).
 *
 * <p><b>Failure modes</b><br>
 * <ul>
 *   <li>Existing {@code Results} folder → early fail to avoid overwriting;</li>
 *   <li>No .tif files or no common marker files → {@link IllegalArgumentException};</li>
 *   <li>Cannot open the reference image → {@link IllegalStateException};</li>
 *   <li>Landmark extraction fails for a round → {@link IllegalStateException};</li>
 *   <li>ROI pairs missing for later rounds → {@link IllegalStateException}.</li>
 * </ul>
 */
public class MultiplexRegistrationService {

    private final MultiplexConfig cfg;

    /**
     * Create a new service with the provided configuration.
     *
     * @param cfg validated configuration (see {@link MultiplexConfig.Builder#build()} for validation rules).
     * @throws NullPointerException if {@code cfg} is null.
     */
    public MultiplexRegistrationService(MultiplexConfig cfg) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
    }

    /**
     * Execute the multiplex registration pipeline end-to-end.
     *
     * <h4>Preconditions</h4>
     * <ul>
     *   <li>{@code cfg.imageFolder()} exists and contains .tif/.tiff files;</li>
     *   <li>At least one filename matches the configured common marker substring;</li>
     *   <li>{@code Results/} does not already exist under {@code cfg.saveFolder()}.</li>
     * </ul>
     *
     * <h4>Side effects</h4>
     * <ul>
     *   <li>Creates {@code Results/} under the configured save folder;</li>
     *   <li>Opens/closes ImageJ image windows; resets and writes to the global {@link RoiManager};</li>
     *   <li>Shows an ImageJ info dialog on success.</li>
     * </ul>
     *
     * @throws IllegalArgumentException if the input folder cannot be listed, contains no TIF files,
     *                                  or no file matches the common marker.
     * @throws IllegalStateException    for failed reference open, failed landmark extraction,
     *                                  missing ROI correspondences, or pre-existing {@code Results/}.
     */
    public void run() {
        // --- Hide ImageJ windows during processing (batch mode) ---
        final boolean prevBatch = Interpreter.batchMode;  // remember prior state
        Interpreter.batchMode = true;

        try {

            // ---- 1) Prepare Results/ ----
        File resultsDir = new File(cfg.saveFolder(), "Results");
        if (resultsDir.exists()) {
            throw new IllegalStateException("Remove Results folder in directory: " + resultsDir.getAbsolutePath());
        }
        // Attempt creation (ignore boolean; failure will be caught when saving).
        resultsDir.mkdirs();

        // ---- 2) Reset ROI Manager (fresh landmark store) ----
        ensureRoiManagerReset();

        // ---- 3) Discover TIF files ----
        File[] files = cfg.imageFolder().listFiles();
        if (files == null) {
            throw new IllegalArgumentException("Unable to list files in: " + cfg.imageFolder());
        }

        List<File> allTifs = Arrays.stream(files)
                .filter(NamingUtils::hasTifExt)
                .sorted(Comparator.comparing(File::getName))
                .collect(Collectors.toList());

        if (allTifs.isEmpty()) {
            throw new IllegalArgumentException("No .tif files found in: " + cfg.imageFolder());
        }

        // ---- 4) Collate common-marker images (reference + targets) ----
        List<File> commonMarkerFiles = allTifs.stream()
                .filter(f -> NamingUtils.normalize(NamingUtils.baseNameNoExt(f))
                        .contains(cfg.commonMarker()))
                .sorted(Comparator.comparing(File::getName))
                .collect(Collectors.toList());

        if (commonMarkerFiles.isEmpty()) {
            throw new IllegalArgumentException("No files matching common marker '" + cfg.commonMarker() + "'");
        }

        // ---- 5) Open first common-marker as the reference ----
        ImagePlus ref = IJ.openImage(commonMarkerFiles.get(0).getAbsolutePath());
        if (ref == null) {
            throw new IllegalStateException("Failed to open reference image: " + commonMarkerFiles.get(0).getName());
        }
        ref.show(); // IJ.run target by title (Copy/Paste, etc.)

        final String refTitle = ref.getTitle();
        final int width = ref.getWidth();
        final int height = ref.getHeight();
        final int bitDepth = ref.getBitDepth();

        // ---- 6) Build <common>_stack and compute landmarks vs. reference ----
        ImagePlus commonStack = NewImage.createImage(
                cfg.commonMarker() + "_stack", width, height, 1, bitDepth, NewImage.FILL_BLACK);
        commonStack.show();

        // Seed first slice with the reference image.
        IJ.run(ref, "Select All", "");
        IJ.run(ref, "Copy", "");
        IJ.run(ref, "Select None", "");
        IJ.selectWindow(commonStack.getTitle());
        IJ.run("Paste", "");
        commonStack.getStack().setSliceLabel(refTitle, commonStack.getStackSize());

        // For every additional common marker, compute correspondences and append to stack.
        for (int i = 1; i < commonMarkerFiles.size(); i++) {
            ImagePlus target = IJ.openImage(commonMarkerFiles.get(i).getAbsolutePath());
            if (target == null) {
                // Skip unreadable file (alternatively: fail fast here).
                continue;
            }
            target.show();

            boolean ok = FeatureMatching.matchWithFallbacks(
                    ref, target, cfg.commonMarker(), i, cfg.minimalInlierRatio(), cfg.stepsPerScaleOctave());

            if (!ok) {
                target.close();
                throw new IllegalStateException(
                        "Couldn't find matches for " + refTitle + " vs " + target.getTitle());
            }

            // Append as a new slice.
            IJ.selectWindow(commonStack.getTitle());
            IJ.run("Add Slice", "");
            IJ.selectWindow(target.getTitle());
            IJ.run("Select All", "");
            IJ.run("Copy", "");
            IJ.run("Select None", "");
            IJ.selectWindow(commonStack.getTitle());
            IJ.run("Paste", "");
            commonStack.getStack().setSliceLabel(target.getTitle(), commonStack.getStackSize());

            target.close();
        }

        // Persist and close the common marker stack (for QC).
        IJ.saveAs(commonStack, "Tiff",
                new File(resultsDir, cfg.commonMarker() + "_stack.tif").getAbsolutePath());
        commonStack.close();

        // ---- 7) Prepare the final stack (destination for all aligned channels) ----
        ImagePlus finalStack = NewImage.createImage(
                "STACK", width, height, 1, bitDepth, NewImage.FILL_BLACK);
        finalStack.show();

        // ---- 8) Iterate rounds; stack R1 as-is, align R2..N with stored landmarks ----
        int roiPairOffset = 0; // For round r, landmark pair index = 2*(r-1)
        for (int round = 1; round <= cfg.multiplexRounds(); round++) {
            final String layerName = cfg.layerKeyword() + round; // e.g., "layer1", "layer2"

            // Round file set = contains layerName but not the common marker.
            List<File> roundFiles = allTifs.stream()
                    .filter(f -> {
                        String base = NamingUtils.normalize(NamingUtils.baseNameNoExt(f));
                        return base.contains(layerName) && !base.contains(cfg.commonMarker());
                    })
                    .sorted(Comparator.comparing(File::getName))
                    .collect(Collectors.toList());

            if (round == 1) {
                // Seed with the reference in slice #1.
                IJ.selectWindow(refTitle);
                IJ.run("Select All", "");
                IJ.run("Copy", "");
                IJ.run("Select None", "");
                IJ.selectWindow(finalStack.getTitle());
                IJ.run("Paste", "");
                finalStack.getStack().setSliceLabel(refTitle, finalStack.getStackSize());

                // Append the remaining R1 markers (no warping).
                for (File f : roundFiles) {
                    ImagePlus imp = IJ.openImage(f.getAbsolutePath());
                    if (imp == null) continue;
                    imp.show();

                    IJ.selectWindow(finalStack.getTitle());
                    IJ.run("Add Slice", "");
                    IJ.selectWindow(imp.getTitle());
                    IJ.run("Select All", "");
                    IJ.run("Copy", "");
                    IJ.selectWindow(finalStack.getTitle());
                    IJ.run("Paste", "");
                    finalStack.getStack().setSliceLabel(imp.getTitle(), finalStack.getStackSize());

                    imp.close();
                }
            } else {
                // For later rounds, enforce availability of the landmark pair for this round.
                RoiManager rm = RoiManager.getInstance2();
                if (rm == null || rm.getCount() < roiPairOffset + 2) {
                    throw new IllegalStateException("ROI correspondences missing for round " + round);
                }

                for (File f : roundFiles) {
                    ImagePlus imp = IJ.openImage(f.getAbsolutePath());
                    if (imp == null) continue;
                    imp.show();

                    // Select the landmark pair for this round:
                    //   roiPairOffset     -> reference landmarks
                    //   roiPairOffset + 1 -> target landmarks
                    rm.select(roiPairOffset);
                    IJ.selectWindow(refTitle);
                    rm.select(roiPairOffset + 1);
                    IJ.selectWindow(imp.getTitle());

                    // Apply warp using Landmark Correspondences; plugin usually opens an aligned copy.
                    String args = String.format(
                            "source_image=[%s] template_image=[%s] transformation_method=[Least Squares] alpha=1 " +
                                    "mesh_resolution=32 transformation_class=Affine interpolate",
                            imp.getTitle(), refTitle);
                    IJ.run("Landmark Correspondences", args);

                    ImagePlus aligned = WindowManager.getCurrentImage();
                    if (aligned == null) aligned = imp; // Fallback: if plugin didn't create a new window.

                    aligned.show();

                    // Append aligned slice.
                    IJ.selectWindow(finalStack.getTitle());
                    IJ.run("Add Slice", "");
                    IJ.selectWindow(aligned.getTitle());
                    IJ.run("Select All", "");
                    IJ.run("Copy", "");
                    IJ.selectWindow(finalStack.getTitle());
                    IJ.run("Paste", "");
                    finalStack.getStack().setSliceLabel(imp.getTitle(), finalStack.getStackSize());

                    aligned.close();
                    imp.close();
                }

                // Move to the next landmark pair for the next round.
                roiPairOffset += 2;
            }
        }

        // ---- 9) Convert to hyperstack (C = number of slices), grayscale composite, then save ----
        IJ.selectWindow(finalStack.getTitle());
        IJ.run(finalStack, "Stack to Hyperstack...",
                "order=xyzct channels=" + finalStack.getStackSize() + " slices=1 frames=1 display=Grayscale");
        IJ.saveAs(finalStack, "Tiff",
                new File(resultsDir, "Aligned_Stack.tif").getAbsolutePath());

        // ---- 10) Persist landmark correspondences from ROI Manager ----
        RoiManager rm = RoiManager.getInstance2();
        if (rm != null) {
            rm.deselect();
            rm.runCommand("Save",
                    new File(resultsDir, "landmark_correspondences.zip").getAbsolutePath());
        }
// ---- Offer to open results (Aligned_Stack + <common>_stack), batch/EDT safe ----
            final File alignedTif = new File(resultsDir, "Aligned_Stack.tif");
            final File commonTif  = new File(resultsDir, cfg.commonMarker() + "_stack.tif");

// Temporarily leave batch mode so windows can appear
            final boolean wasBatch = ij.macro.Interpreter.batchMode;
            ij.macro.Interpreter.batchMode = false;

            javax.swing.SwingUtilities.invokeLater(() -> {
                JPanel panel = new JPanel(new java.awt.GridLayout(0, 1, 0, 6));
                panel.add(new javax.swing.JLabel("<html><b>Multiplex Registration finished.</b><br/>"
                        + "Results saved in:<br/>" + resultsDir.getAbsolutePath() + "</html>"));

                javax.swing.JCheckBox openAligned = new javax.swing.JCheckBox("Open Aligned_Stack.tif", true);
                javax.swing.JCheckBox openCommon  = new javax.swing.JCheckBox("Open " + cfg.commonMarker() + "_stack.tif", true);
                panel.add(openAligned);
                panel.add(openCommon);

                int choice = javax.swing.JOptionPane.showConfirmDialog(
                        null, panel, "Open results?", javax.swing.JOptionPane.OK_CANCEL_OPTION,
                        javax.swing.JOptionPane.INFORMATION_MESSAGE);

                java.util.List<ij.ImagePlus> opened = new java.util.ArrayList<>(2);

                if (choice == javax.swing.JOptionPane.OK_OPTION) {
                    if (openAligned.isSelected() && alignedTif.exists()) {
                        ij.ImagePlus imp = ij.IJ.openImage(alignedTif.getAbsolutePath());
                        if (imp != null) { imp.show(); opened.add(imp); }
                    }
                    if (openCommon.isSelected() && commonTif.exists()) {
                        ij.ImagePlus imp = ij.IJ.openImage(commonTif.getAbsolutePath());
                        if (imp != null) { imp.show(); opened.add(imp); }
                    }
                    // If both opened, tile for convenience
                    if (opened.size() > 1) {
                        ij.IJ.run("Tile");
                    } else if (opened.size() == 1) {
                        ij.IJ.selectWindow(opened.get(0).getTitle());
                    }
                }

                // Restore prior batch mode after user interaction
                ij.macro.Interpreter.batchMode = wasBatch;
            });

            // ---- 11) Cleanup + notify ----
        finalStack.close();
        ref.close();
        IJ.showMessage("Multiplex Registration", "Done.\nSaved to: " + resultsDir.getAbsolutePath());
    } finally {
// Restore the previous batch mode so ImageJ UI behaves normally afterwards
            Interpreter.batchMode = prevBatch;
        }

    }
}