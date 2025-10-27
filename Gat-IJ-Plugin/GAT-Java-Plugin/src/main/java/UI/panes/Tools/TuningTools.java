package UI.panes.Tools;

import Features.Core.Params;
import Features.AnalyseWorkflows.NeuronsMultiPipeline;
import Features.Tools.SegOne;
import UI.panes.Tools.dialogs.GangliaExpansionDialog;
import UI.panes.Tools.dialogs.ProbabilityDialog;
import UI.panes.Tools.dialogs.RescaleHuDialog;
import UI.util.GatSettings;
import ij.IJ;
import ij.ImagePlus;

import javax.swing.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Static helpers for running parameter sweeps and capturing user picks.
 * <p>
 * {@code TuningTools} does the heavy lifting behind the "Tuning Tools"
 * UI. For each sweep type (rescale, probability, ganglia expansion), it:
 * </p>
 * <ol>
 *   <li>Opens the user-selected image via Bio-Formats.</li>
 *   <li>Builds or reuses baseline {@link Features.Core.Params}.</li>
 *   <li>Iterates over a numeric range (scale factors, probability cutoffs,
 *       or spatial expansion distances).</li>
 *   <li>Calls low-level segmentation helpers (e.g. {@code SegOne.runHuAtProb(...)})
 *       to generate a mask/overlay for each tested value.</li>
 *   <li>Writes previews and counts to disk in a "Tuning" folder.</li>
 *   <li>Shows an interactive chooser where the user can preview and pick
 *       whichever sweep entry looks most correct.</li>
 *   <li>Saves the chosen value back into {@link UI.util.GatSettings} and
 *       offers to export an Advanced-only config snippet ({@code .cfg}).</li>
 * </ol>
 *
 * <p>
 * The sweeps are designed for expert calibration: they let you dial in
 * model probability thresholds, neuron rescaling to match training pixel
 * size, and ganglia expansion radii without editing global defaults by hand.
 * </p>
 *
 * <p>
 * This class is not meant to be instantiated.
 * </p>
 */
public final class TuningTools {



    /**
     * One candidate setting from a sweep, plus its preview data.
     * <p>
     * A {@code Row} binds together:
     * </p>
     * <ul>
     *   <li>{@code x} – the parameter value tested (e.g. 0.45 probability,
     *       1.25 rescale factor, 8.0 µm expansion).</li>
     *   <li>{@code count} – how many objects / cells / ganglia were found
     *       using that setting (a quick quality proxy).</li>
     *   <li>{@code preview} – a PNG or similar overlay image written to disk
     *       so the user can visually inspect results for that setting.</li>
     * </ul>
     *
     * <p>
     * These rows are displayed in a chooser dialog where the user can scroll,
     * preview each PNG in ImageJ, and then select the row that "looks best".
     * </p>
     */
    public static final class Row {
        public final double x;
        public final int    count;
        public final File   preview;

        /**
         * Creates a single sweep entry representing one tested parameter value.
         * <p>
         * For example, a row might correspond to:
         * </p>
         * <ul>
         *   <li>a specific probability threshold,</li>
         *   <li>a specific rescaling factor,</li>
         *   <li>or a specific ganglia expansion radius in µm,</li>
         * </ul>
         * plus the object count produced at that setting and a preview image
         * saved to disk.
         *
         * @param x        the numeric parameter value tested (e.g. 0.50 prob, 1.25 rescale, 8.0 µm)
         * @param count    how many objects (cells / ganglia / etc.) were detected at this setting
         * @param preview  PNG (or other) preview written during the sweep; may be {@code null}
         */
        public Row(double x, int count, File preview){ this.x=x; this.count=count; this.preview=preview; }

        /**
         * Returns a short label for UI lists, combining the parameter value and count.
         *
         * @return formatted string like {@code "0.500  —  42"}
         */
        @Override public String toString(){ return String.format(java.util.Locale.US, "%.3f  —  %d", x, count); }
    }

    /**
     * Ensures and returns the directory to use for sweep outputs.
     * <p>
     * The general rule is:
     * </p>
     * <ul>
     *   <li>If {@code outDir} is {@code null}, fall back to {@code ~/Analysis}.</li>
     *   <li>If that directory is already named {@code Tuning}, use it directly.</li>
     *   <li>Otherwise create (or reuse) a {@code Tuning/} subfolder.</li>
     * </ul>
     *
     * @param outDir preferred output directory from the caller; may be {@code null}
     * @return a {@link File} representing the sweep output folder (created if needed)
     */
    private static File ensureSweepDir(File outDir) {
        File base = outDir;
        if (base == null) {
            base = new File(System.getProperty("user.home"), "Analysis");
        }
        // If caller already passed .../Tuning, use it directly (avoid Tuning/Tuning)
        if ("Tuning".equalsIgnoreCase(base.getName())) {
            if (!base.isDirectory()) base.mkdirs();
            return base;
        }
        File t = new File(base, "Tuning");
        if (!t.isDirectory()) t.mkdirs();
        return t;
    }

    /**
     * Rounds a double to three decimal places.
     * <p>
     * Used when writing sweep values (probability, scale factor, µm expansion)
     * so filenames and CSVs look consistent.
     * </p>
     *
     * @param d input value
     * @return {@code d} rounded to 3 decimal places
     */
    private static double round3(double d){ return Math.round(d*1000.0)/1000.0; }


    /**
     * Produces a maximum-intensity projection of an image stack.
     * <p>
     * If {@code src} is already 2D (single slice), this returns a duplicate.
     * Otherwise:
     * </p>
     * <ul>
     *   <li>If {@code base.useClij2EDF} is true, uses the application's
     *       CLIJ2-based EDF routine.</li>
     *   <li>Otherwise calls ImageJ's "Z Project..." with "Max Intensity".</li>
     * </ul>
     *
     * <p>
     * The returned {@link ImagePlus} is hidden (not shown) if created here.
     * </p>
     *
     * @param src  input {@link ImagePlus}, possibly multi-slice
     * @param base baseline {@link Params} whose flags decide which projection path to use
     * @return a new {@link ImagePlus} representing a 2D max projection of {@code src}
     * @throws IllegalArgumentException if {@code src} is {@code null}
     */
    private static ImagePlus toMaxProjection(ImagePlus src, Params base) {
        if (src == null) throw new IllegalArgumentException("Image is null");
        if (src.getNSlices() <= 1) return src.duplicate();
        if (base.useClij2EDF) {
            // EDF path using your helper
            return Features.Core.PluginCalls.clij2EdfVariance(src);
        } else {
            src.show();
            IJ.run("Z Project...", "projection=[Max Intensity]");
            ImagePlus out = IJ.getImage();
            out.hide();
            return out;
        }
    }


    /**
     * Writes the sweep summary rows to a CSV file.
     * <p>
     * The CSV has columns:
     * </p>
     * <pre>
     * x,count,preview
     * </pre>
     * where {@code x} is the tested parameter value, {@code count} is the
     * number of detections, and {@code preview} is the absolute path to
     * the preview image for that row (if any).
     *
     * @param rows list of {@link Row} entries to export
     * @param csv  destination file; any existing file will be overwritten
     */
    private static void saveRowsCsv(List<Row> rows, File csv) {
        try {
            java.io.PrintWriter pw = new java.io.PrintWriter(csv, "UTF-8");
            try {
                pw.println("x,count,preview");
                for (Row r : rows) {
                    String path = (r.preview != null) ? r.preview.getAbsolutePath().replace('\\','/') : "";
                    pw.println(String.format(Locale.US, "%.3f,%d,%s", r.x, r.count, path));
                }
            } finally { pw.close(); }
        } catch (Exception ignore) { }
    }



    /**
     * Presents a modeless chooser dialog that lets the user inspect and pick
     * one {@link Row} (a sweep entry).
     * <p>
     * The dialog shows all rows in a list, a Preview button that opens the
     * row's PNG in ImageJ, and OK/Cancel. The chosen {@link Row} is returned.
     * </p>
     *
     * <p>
     * Threading details:
     * </p>
     * <ul>
     *   <li>If called off the EDT (typical case), this method spins up
     *       the chooser on the EDT and then blocks the caller using a
     *       {@link java.util.concurrent.CountDownLatch} until the dialog
     *       closes.</li>
     *   <li>If called on the EDT (unexpected in normal usage), it falls
     *       back to a standard {@link JOptionPane#showInputDialog} to
     *       avoid deadlock.</li>
     * </ul>
     *
     * @param title title for the chooser window / dialog
     * @param rows  list of sweep rows to present to the user
     * @return the {@link Row} the user picked, or {@code null} if the user
     *         cancelled or the list was empty
     */
    private static Row pickWithPreview(final String title, final java.util.List<Row> rows) {
        if (rows == null || rows.isEmpty()) return null;

        // If we are (unexpectedly) on EDT, fall back to a simple modal chooser to avoid deadlock
        if (SwingUtilities.isEventDispatchThread()) {
            Object choice = JOptionPane.showInputDialog(
                    null, "Choose the setting that looks best (previews saved in Tuning/).",
                    title, JOptionPane.QUESTION_MESSAGE, null,
                    rows.toArray(new Row[0]), rows.get(Math.max(0, rows.size()/2)));
            return (choice instanceof Row) ? (Row) choice : null;
        }

        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final Row[] result = new Row[1];

        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                final JDialog dlg = new JDialog((java.awt.Frame) null, title, java.awt.Dialog.ModalityType.MODELESS);
                dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                dlg.setLayout(new java.awt.BorderLayout(10, 10));
                dlg.getRootPane().setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
                dlg.setAlwaysOnTop(false); // allow ImageJ windows to get focus

                final DefaultListModel<Row> model = new DefaultListModel<Row>();
                for (Row r : rows) model.addElement(r);

                final JList<Row> list = new JList<Row>(model);
                list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                list.setVisibleRowCount(Math.min(rows.size(), 10));
                list.setSelectedIndex(Math.max(0, rows.size()/2));

                final JScrollPane scroll = new JScrollPane(list);
                final JLabel pathLabel = new JLabel(" ");
                pathLabel.setFont(pathLabel.getFont().deriveFont(11f));

                final JButton previewBtn = new JButton("Preview");
                final JButton previewAllBtn  = new JButton("Preview All");
                final JButton okBtn      = new JButton("OK");
                final JButton cancelBtn  = new JButton("Cancel");

                list.addListSelectionListener(e -> {
                    Row sel = list.getSelectedValue();
                    String p = (sel != null && sel.preview != null) ? sel.preview.getAbsolutePath() : "(no preview)";
                    pathLabel.setText(p);
                });
                list.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                        if (e.getClickCount() == 2) previewBtn.doClick();
                    }
                });

                previewBtn.addActionListener(e -> {
                    Row sel = list.getSelectedValue();
                    if (sel == null || sel.preview == null || !sel.preview.isFile()) {
                        JOptionPane.showMessageDialog(dlg, "No preview image available for this option.");
                        return;
                    }
                    ij.ImagePlus imp = ij.IJ.openImage(sel.preview.getAbsolutePath());
                    if (imp != null) imp.show();
                    else JOptionPane.showMessageDialog(dlg, "Failed to open:\n" + sel.preview.getAbsolutePath());
                });

                previewAllBtn.addActionListener(e -> {
                    showAllPreviews(title + " – All Options", rows);
                });

                okBtn.addActionListener(e -> {
                    result[0] = list.getSelectedValue();
                    dlg.dispose();
                    latch.countDown();
                });
                cancelBtn.addActionListener(e -> {
                    result[0] = null;
                    dlg.dispose();
                    latch.countDown();
                });

                JPanel top = new JPanel(new java.awt.BorderLayout(6,6));
                top.add(new JLabel("Choose the setting that looks best (previews are PNGs in Tuning/)."),
                        java.awt.BorderLayout.NORTH);
                top.add(scroll, java.awt.BorderLayout.CENTER);
                top.add(pathLabel, java.awt.BorderLayout.SOUTH);

                JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
                actions.add(previewBtn);
                actions.add(previewAllBtn);
                actions.add(okBtn);
                actions.add(cancelBtn);

                dlg.add(top, java.awt.BorderLayout.CENTER);
                dlg.add(actions, java.awt.BorderLayout.SOUTH);
                dlg.pack();
                dlg.setSize(Math.max(520, dlg.getWidth()), Math.min(480, dlg.getHeight()+80));
                dlg.setLocationRelativeTo(null);
                dlg.setVisible(true);
            }
        });

        try { latch.await(); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        return result[0];
    }


    /**
     * Convenience: puts a key/value pair into a {@link Properties} if
     * the value is non-null.
     *
     * @param pr   target properties
     * @param key  property key
     * @param val  value to store (ignored if {@code null})
     */
    private static void put(Properties pr, String key, Object val) {
        if (val == null) return;
        pr.setProperty(key, String.valueOf(val));
    }

    /**
     * Marks a {@link Properties} bundle as an "Advanced-only testing config".
     * <p>
     * This stamps fields so downstream config loaders/validators know that
     * this is not a full workflow config, but rather a tuning snapshot
     * created by the sweeps.
     * </p>
     *
     * @param pr properties object to tag
     */
    private static void markAsTesting(Properties pr) {
        pr.setProperty("workflow", "test");
        if (!pr.containsKey("cfgVersion")) pr.setProperty("cfgVersion", "1");
    }

    /**
     * Prompts the user to save a minimal {@code .cfg} file containing only
     * "Advanced" keys relevant to the specific sweep they just ran.
     * <p>
     * The file is created next to the sweep outputs and can later be loaded
     * via "Load Config" in the UI to quickly reapply the tuned values.
     * </p>
     *
     * @param pr               properties to write
     * @param sweepDir         directory where sweep results were stored
     * @param baseNameNoExt    suggested base filename (without extension)
     */
    private static void saveRunCfg(Properties pr, File sweepDir, String baseNameNoExt) {
        markAsTesting(pr);
        JFileChooser fc = new JFileChooser(sweepDir);
        fc.setSelectedFile(new File(sweepDir, baseNameNoExt + ".cfg"));
        int ret = fc.showSaveDialog(null);
        if (ret == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            if (!f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".cfg")) {
                f = new File(f.getParentFile(), f.getName() + ".cfg");
            }
            try (java.io.OutputStream os = new java.io.FileOutputStream(f);
                 java.io.OutputStreamWriter w = new java.io.OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                pr.store(w, "GAT testing config (Advanced-only)");
                JOptionPane.showMessageDialog(null,
                        "Saved config:\n" + f.getAbsolutePath() + "\n\nUse 'Load Config' on any pane.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null,
                        "Failed to save config:\n" + ex.getMessage(),
                        "Save error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Builds the minimal "Advanced-only" config for a Hu rescale sweep.
     * <p>
     * Captures:
     * </p>
     * <ul>
     *   <li>Hu model ZIP path</li>
     *   <li>Probability / NMS thresholds used during the sweep</li>
     *   <li>The chosen rescale factor (the output of the sweep)</li>
     *   <li>Pixel scaling assumptions</li>
     * </ul>
     *
     * @param base baseline params in effect during the sweep
     * @param cfg  dialog config from {@link RescaleHuDialog}
     * @param pick the row the user selected as "best", may be {@code null}
     * @return {@link Properties} containing only advanced/tunable keys
     */
    private static Properties testCfgForRescale(Params base,
                                                RescaleHuDialog.Config cfg,
                                                Row pick) {
        Properties pr = new Properties();

        // Neuron (Hu) advanced bits only
        put(pr, "hu.modelZip", base.stardistModelZip); // advanced in Neuron/Multichannel panes
        put(pr, "hu.prob", cfg.prob);
        put(pr, "hu.nms",  cfg.overlap);

        // Rescale group (advanced everywhere)
        put(pr, "rescale.enabled", base.rescaleToTrainingPx);
        put(pr, "rescale.trainingPxUm", base.trainingPixelSizeUm);
        // The thing we actually tuned:
        put(pr, "rescale.trainingScale", (pick != null ? pick.x : base.trainingRescaleFactor));

        return pr;
    }

    /**
     * Builds the minimal "Advanced-only" config for a Hu probability sweep.
     * <p>
     * Captures:
     * </p>
     * <ul>
     *   <li>Model ZIP path</li>
     *   <li>The user's chosen probability threshold</li>
     *   <li>NMS / overlap threshold</li>
     *   <li>Rescale assumptions used in that sweep</li>
     * </ul>
     *
     * @param base baseline params in effect during the sweep
     * @param cfg  dialog config from {@link ProbabilityDialog} (NEURON mode)
     * @param pick the row the user chose as best, may be {@code null}
     * @return {@link Properties} representing only the advanced keys
     */
    private static Properties testCfgForProbHu(Params base,
                                               ProbabilityDialog.Config cfg,
                                               Row pick) {
        Properties pr = new Properties();

        put(pr, "hu.modelZip", base.stardistModelZip);
        // The thing we tuned:
        put(pr, "hu.prob", (pick != null ? pick.x : cfg.probMin));
        put(pr, "hu.nms",  cfg.overlap);

        // We also record rescale bits used for this sweep (advanced)
        put(pr, "rescale.enabled", base.rescaleToTrainingPx);
        put(pr, "rescale.trainingPxUm", base.trainingPixelSizeUm);
        put(pr, "rescale.trainingScale", cfg.rescaleFactor);

        return pr;
    }

    /**
     * Builds the minimal "Advanced-only" config for a subtype probability sweep.
     * <p>
     * Captures:
     * </p>
     * <ul>
     *   <li>Subtype model ZIP</li>
     *   <li>Chosen probability threshold for that subtype</li>
     *   <li>NMS / overlap for subtype objects</li>
     * </ul>
     *
     * @param cfg  dialog config from {@link ProbabilityDialog} (SUBTYPE mode)
     * @param pick the row the user picked as best, may be {@code null}
     * @return {@link Properties} with subtype-related advanced keys
     */
    private static Properties testCfgForProbSubtype(ProbabilityDialog.Config cfg,
                                                    Row pick) {
        Properties pr = new Properties();
        // Multichannel advanced keys
        if (cfg.modelZip != null) put(pr, "multi.subtypeModelZip", cfg.modelZip.getAbsolutePath());
        put(pr, "multi.subtypeProb", (pick != null ? pick.x : cfg.probMin));
        put(pr, "multi.subtypeNms",  cfg.overlap);
        return pr;
    }

    /**
     * Builds the minimal "Advanced-only" config for a ganglia expansion sweep.
     * <p>
     * The only advanced knob here is how far (in µm) Hu-positive neurons
     * are spatially expanded to approximate the ganglion boundary.
     * </p>
     *
     * @param pick the chosen sweep row, which encodes the preferred expansion µm
     * @return {@link Properties} storing the picked ganglia expansion distance
     */
    private static Properties testCfgForGanglia(Row pick) {
        Properties pr = new Properties();
        // Neuron advanced: the dilation (µm) control in Ganglia post-processing
        if (pick != null) put(pr, "ganglia.huDilationUm", pick.x);
        return pr;
    }

    /**
     * Runs the Hu rescale sweep for neuron detection.
     * <p>
     * High-level flow:
     * </p>
     * <ol>
     *   <li>Open the image with Bio-Formats, create a max projection.</li>
     *   <li>For each rescale factor in {@code cfg} (min → max with step):</li>
     *   <li>Call {@code SegOne.runHuAtScale(...)} to segment at that scale and
     *       capture a {@link Row} containing the result count and preview PNG.</li>
     *   <li>Let the user pick their favorite row with {@link #pickWithPreview}.</li>
     *   <li>Save a tiny .cfg snapshot for re-use and write a CSV of all rows.</li>
     * </ol>
     *
     * <p>
     * This method toggles ImageJ batch mode on/off around the sweep to
     * avoid repeated UI redraw.
     * </p>
     *
     * @param base      baseline neuron params (will be mutated for thresholds)
     * @param outDir    optional output directory from the dialog; may be {@code null}
     * @param settings  app settings store; updated with the chosen rescale factor
     * @param cfg       user-entered sweep configuration from {@link RescaleHuDialog}
     */
    public static void runRescaleSweep(Params base,
                                       File outDir,
                                       GatSettings settings,
                                       RescaleHuDialog.Config cfg) {
        ij.ImagePlus imp = null;
        boolean closeImp = false;
        try {
            imp = Features.Core.PluginCalls.openWithBioFormats(cfg.imageFile.getAbsolutePath());
            closeImp = true;

            ij.ImagePlus max = toMaxProjection(imp, base);

            // Use dialog’s fixed thresholds for this run
            base.probThresh = cfg.prob;
            base.nmsThresh  = cfg.overlap;
            ij.macro.Interpreter.batchMode = true;
            File sweepDir = ensureSweepDir(outDir);
            List<Row> rows = new ArrayList<Row>();
            for (double f = cfg.rescaleMin; f <= cfg.rescaleMax + 1e-12; f += cfg.rescaleStep) {
                rows.add(SegOne.runHuAtScale(max, cfg.channel, base, round3(f), sweepDir));
            }
            ij.macro.Interpreter.batchMode = false;

            Row pick = pickWithPreview("Pick best Hu rescale", rows);
            if (pick != null) {
                // quick-cache for session
                settings.setHuTrainingRescale(pick.x);
                // Save a tiny Advanced-only testing cfg
                saveRunCfg(testCfgForRescale(base, cfg, pick), sweepDir, "tuning_hu_rescale");
            }

            saveRowsCsv(rows, new File(sweepDir, "hu_rescale_sweep.csv"));

            if (max != null) { max.changes = false; max.close(); }
        } finally {
            if (closeImp && imp != null) { imp.changes = false; imp.close(); }
        }
    }

    /**
     * Runs a probability sweep for either Hu neurons or a specific neuron subtype.
     * <p>
     * High-level flow:
     * </p>
     * <ol>
     *   <li>Open the source image and make a max projection.</li>
     *   <li>For each probability in {@code cfg.probMin..probMax}:</li>
     *   <li>
     *     <ul>
     *       <li>If NEURON mode: call {@code SegOne.runHuAtProb(...)}.</li>
     *       <li>If SUBTYPE mode: call {@code SegOne.runSubtypeAtProb(...)} with
     *           a {@link NeuronsMultiPipeline.MultiParams} that includes the
     *           subtype model ZIP.</li>
     *     </ul>
     *   </li>
     *   <li>Show the picker so the user can choose the "best" row.</li>
     *   <li>Persist tuned values to {@link GatSettings} and write a .cfg snapshot
     *       + CSV summary.</li>
     * </ol>
     *
     * @param base          baseline neuron params (prob/NMS/etc. will be set)
     * @param unusedOutDir  unused; kept for API symmetry with other sweeps
     * @param s             settings store to update with the chosen probability
     * @param cfg           dialog config describing sweep bounds and mode
     */
    public static void runProbSweep(Params base,
                                    File unusedOutDir,
                                    GatSettings s,
                                    ProbabilityDialog.Config cfg) {
        ij.ImagePlus imp = Features.Core.PluginCalls.openWithBioFormats(cfg.imageFile.getAbsolutePath());
        if (imp == null) throw new IllegalStateException("No image available.");
        boolean closeImp = true;

        try {
            base.rescaleToTrainingPx   = true;
            base.trainingRescaleFactor = cfg.rescaleFactor;
            base.nmsThresh             = cfg.overlap;

            ij.ImagePlus max = toMaxProjection(imp, base);
            List<Row> rows = new ArrayList<Row>();
            File tdir = ensureSweepDir(cfg.outDir);

            ij.macro.Interpreter.batchMode = true;
            for (double p = cfg.probMin; p <= cfg.probMax + 1e-12; p += cfg.probStep) {
                double pp = round3(p);
                if (cfg.mode == ProbabilityDialog.Mode.NEURON) {
                    rows.add(SegOne.runHuAtProb(max, cfg.channel, base, cfg.rescaleFactor, pp, tdir));
                } else {
                    NeuronsMultiPipeline.MultiParams mp = new NeuronsMultiPipeline.MultiParams();
                    mp.base = base;
                    mp.multiProb = pp;
                    mp.multiNms  = cfg.overlap;
                    mp.subtypeModelZip = cfg.modelZip.getAbsolutePath();
                    rows.add(SegOne.runSubtypeAtProb(max, cfg.channel, mp, pp, tdir));
                }
            }
            ij.macro.Interpreter.batchMode = false;

            Row pick = pickWithPreview("Pick best probability", rows);
            if (pick != null) {
                Properties pr;
                if (cfg.mode == ProbabilityDialog.Mode.NEURON) {
                    s.setHuProb(pick.x);
                    s.setOverlapFrac(cfg.overlap);
                    pr = testCfgForProbHu(base, cfg, pick);
                } else {
                    s.setSubtypeProb(pick.x);
                    s.setOverlapFrac(cfg.overlap);
                    pr = testCfgForProbSubtype(cfg, pick);
                }
                saveRunCfg(pr, tdir, "tuning_probability");
            }

            saveRowsCsv(rows, new File(tdir, "prob_sweep.csv"));
        } finally {
            if (closeImp) { imp.changes = false; imp.close(); }
        }
    }

    /**
     * Runs a ganglia expansion sweep (in micrometers).
     * <p>
     * A series of expansion radii (µm) is tested. For each one, Hu-positive
     * neurons are expanded spatially to approximate ganglion boundaries,
     * and a {@link Row} is recorded with object counts and a preview.
     * The user then picks the expansion distance that looks best.
     * </p>
     *
     * <p>
     * The chosen expansion distance is stored in {@link GatSettings} and a
     * minimal tuning .cfg is offered for saving.
     * </p>
     *
     * @param base          baseline params, including Hu channel index
     * @param unusedOutDir  unused; reserved for future symmetry
     * @param s             settings store to update with the chosen expansion µm
     * @param cfg           dialog config defining min/max/step expansion radii and output directory
     */
    public static void runGangliaExpansionSweep(Params base,
                                                File unusedOutDir,
                                                GatSettings s,
                                                GangliaExpansionDialog.Config cfg) {
        ij.ImagePlus imp = Features.Core.PluginCalls.openWithBioFormats(cfg.imageFile.getAbsolutePath());
        if (imp == null) throw new IllegalStateException("No image available.");
        boolean closeImp = true;

        try {
            ij.ImagePlus max = toMaxProjection(imp, base);
            List<Row> rows = new ArrayList<Row>();
            File tdir = ensureSweepDir(cfg.outDir);

            ij.macro.Interpreter.batchMode = true;
            for (double um = cfg.minUm; um <= cfg.maxUm + 1e-12; um += cfg.stepUm) {
                double uu = round3(um);
                rows.add(SegOne.runGangliaFromHuExpansion(max, base.huChannel, base, uu, tdir));
            }
            ij.macro.Interpreter.batchMode = false;

            Row pick = pickWithPreview("Pick ganglia expansion (µm)", rows);
            if (pick != null) {
                s.setGangliaExpandUm(pick.x);
                saveRunCfg(testCfgForGanglia(pick), tdir, "tuning_ganglia");
            }

            saveRowsCsv(rows, new File(tdir, "ganglia_expand_sweep.csv"));
        } finally {
            if (closeImp) { imp.changes = false; imp.close(); }
        }
    }

    // ===================== Gallery (preview all) =====================
    private static void showAllPreviews(final String title, final java.util.List<Row> rows) {
        if (rows == null || rows.isEmpty()) return;

        SwingUtilities.invokeLater(() -> {
            final JDialog dlg = new JDialog((java.awt.Frame) null, title, java.awt.Dialog.ModalityType.MODELESS);
            dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            dlg.setLayout(new java.awt.BorderLayout(8,8));

            final JPanel listPanel = new JPanel();
            listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
            listPanel.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));

            final JScrollPane scroll = new JScrollPane(
                    listPanel,
                    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            );
            scroll.getVerticalScrollBar().setUnitIncrement(24);
            dlg.add(scroll, java.awt.BorderLayout.CENTER);

            dlg.setSize(1000, 720);
            dlg.setLocationRelativeTo(null);
            dlg.setVisible(true);

            // Stream cards in without blocking the EDT
            new SwingWorker<Void, JPanel>() {
                @Override protected Void doInBackground() {
                    for (Row r : rows) {
                        publish(buildPreviewCard(r));
                    }
                    return null;
                }
                @Override protected void process(java.util.List<JPanel> chunks) {
                    for (JPanel card : chunks) {
                        listPanel.add(card);
                        listPanel.add(Box.createVerticalStrut(8));
                    }
                    listPanel.revalidate();
                    listPanel.repaint();
                }
            }.execute();
        });
    }

    private static JPanel buildPreviewCard(Row r) {
        JPanel card = new JPanel(new java.awt.BorderLayout(6,6));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new java.awt.Color(220,220,220)),
                BorderFactory.createEmptyBorder(8,8,8,8)
        ));

        // Header: x/count
        String hdr = String.format(java.util.Locale.US, "x = %.3f    count = %d", r.x, r.count);
        JLabel header = new JLabel(hdr);
        header.setFont(header.getFont().deriveFont(header.getFont().getSize2D()+1.0f));
        card.add(header, java.awt.BorderLayout.NORTH);

        // Image placeholder
        JLabel imageLabel = new JLabel("Loading preview…", SwingConstants.CENTER);
        imageLabel.setVerticalAlignment(SwingConstants.TOP);
        imageLabel.setOpaque(true);
        imageLabel.setBackground(java.awt.Color.WHITE);
        imageLabel.setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
        card.add(imageLabel, java.awt.BorderLayout.CENTER);

        // Footer: path + open button
        JPanel footer = new JPanel(new java.awt.BorderLayout(6,6));
        String path = (r.preview != null) ? r.preview.getAbsolutePath() : "(no preview file)";
        JLabel pathLabel = new JLabel(path);
        pathLabel.setFont(pathLabel.getFont().deriveFont(11f));
        footer.add(pathLabel, java.awt.BorderLayout.CENTER);

        JButton openBtn = new JButton("Open in IJ");
        openBtn.addActionListener(e -> {
            if (r.preview != null && r.preview.isFile()) {
                ij.ImagePlus imp = ij.IJ.openImage(r.preview.getAbsolutePath());
                if (imp != null) imp.show();
                else JOptionPane.showMessageDialog(card, "Failed to open:\n" + r.preview.getAbsolutePath());
            } else {
                JOptionPane.showMessageDialog(card, "No preview image available for this option.");
            }
        });
        footer.add(openBtn, java.awt.BorderLayout.EAST);
        card.add(footer, java.awt.BorderLayout.SOUTH);

        // Async load + scale thumbnail to fit nicely
        if (r.preview != null && r.preview.isFile()) {
            new SwingWorker<ImageIcon,Void>() {
                @Override protected ImageIcon doInBackground() {
                    try {
                        java.awt.image.BufferedImage full = javax.imageio.ImageIO.read(r.preview);
                        if (full == null) return null;

                        // Scale to a sane card size while keeping aspect ratio
                        int maxW = 940;   // a bit less than dialog width to leave margins
                        int maxH = 600;   // tall enough, users can scroll
                        int w = full.getWidth(), h = full.getHeight();
                        double scale = Math.min(1.0, Math.min(maxW/(double)w, maxH/(double)h));
                        int nw = Math.max(1, (int)Math.round(w*scale));
                        int nh = Math.max(1, (int)Math.round(h*scale));

                        java.awt.image.BufferedImage scaled =
                                new java.awt.image.BufferedImage(nw, nh, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        java.awt.Graphics2D g2 = scaled.createGraphics();
                        try {
                            g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                            g2.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,     java.awt.RenderingHints.VALUE_RENDER_QUALITY);
                            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,  java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                            g2.drawImage(full, 0, 0, nw, nh, null);
                        } finally {
                            g2.dispose();
                        }
                        return new ImageIcon(scaled);
                    } catch (Exception ex) {
                        return null;
                    }
                }
                @Override protected void done() {
                    try {
                        ImageIcon icon = get();
                        if (icon != null) {
                            imageLabel.setText(null);
                            imageLabel.setIcon(icon);
                        } else {
                            imageLabel.setText("(failed to load preview)");
                        }
                    } catch (Exception ex) {
                        imageLabel.setText("(failed to load preview)");
                    }
                }
            }.execute();
        } else {
            imageLabel.setText("(no preview available)");
        }

        return card;
    }

      /**
     * Private constructor to prevent instantiation.
     * <p>
     * {@code TuningTools} is a static utility holder and should not be created.
     * </p>
     */
    private TuningTools(){}
}
