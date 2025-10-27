// UI/panes/Tools/ToolsPane.java
package UI.panes.Tools;

import UI.Handlers.Navigator;
import UI.panes.Tools.dialogs.RescaleHuDialog;
import UI.util.GatSettings;
import Features.Core.Params;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;

/**
 * "Tuning Tools" panel for expert/advanced parameter sweeps.
 * <p>
 * This panel exposes three tuning workflows that help users calibrate
 * segmentation parameters on their own data:
 * </p>
 * <ul>
 *   <li><b>Test Rescaling</b> – sweep image rescaling factors to match training pixel size.</li>
 *   <li><b>Test Probability</b> – sweep detection probability thresholds (Hu or subtype).</li>
 *   <li><b>Test Ganglia expansion (µm)</b> – sweep spatial expansion radii used to merge
 *       neuron ROIs into putative ganglia outlines.</li>
 * </ul>
 *
 * <p>
 * Each button opens a dedicated dialog (e.g. {@link UI.panes.Tools.dialogs.RescaleHuDialog})
 * that asks for input image(s), sweep ranges, model ZIP paths if needed,
 * and output directory. After the dialog returns, the sweep is executed
 * in the background.
 * </p>
 *
 * <p>
 * While a sweep is running:
 * </p>
 * <ul>
 *   <li>All buttons are disabled.</li>
 *   <li>The cursor switches to a wait cursor.</li>
 *   <li>A status label shows progress or completion.</li>
 * </ul>
 *
 * <p>
 * Results from each sweep include preview PNGs and CSV summaries
 * under a "Tuning" output folder, and the user is prompted to choose
 * a "best" candidate. The chosen settings are cached in
 * {@link UI.util.GatSettings} and can also be exported as a minimal
 * {@code .cfg} that replays those advanced parameters.
 * </p>
 */
public class ToolsPane extends JPanel {

    public static final String Name = "Tools";

    private final GatSettings settings;
    private final JButton btnRescale = new JButton("Test Rescaling");
    private final JButton btnProb    = new JButton("Test Probability");
    private final JButton btnGanglia = new JButton("Test Ganglia expansion (µm)");
    private final JLabel  status     = new JLabel(" ");

    /**
     * Builds the "Tuning Tools" panel.
     * <p>
     * This panel exposes three tuning helpers:
     * </p>
     * <ul>
     *   <li>"Test Rescaling" – sweep rescaling factors for neuron shapes</li>
     *   <li>"Test Probability" – sweep detection probability thresholds</li>
     *   <li>"Test Ganglia expansion (µm)" – sweep ganglionic expansion radius</li>
     * </ul>
     *
     * <p>
     * Clicking any button launches the corresponding dialog to collect
     * parameters and then runs the tuning workflow in the background.
     * While a workflow runs, buttons are disabled and a status label shows
     * progress.
     * </p>
     *
     * @param navigator navigation handle (not currently used to switch panes
     *                  here, but provided for consistency with other panes)
     */
    public ToolsPane(Navigator navigator) {
        this.settings = GatSettings.loadOrDefaults();

        setLayout(new GridLayout(0, 1, 8, 8));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("Tuning Tools", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        add(title);

        add(btnRescale);
        add(btnProb);
        add(btnGanglia);

        status.setHorizontalAlignment(SwingConstants.CENTER);
        add(status);

        btnRescale.addActionListener(e -> startRescale());
        btnProb.addActionListener(e -> startProbability());
        btnGanglia.addActionListener(e -> startGanglia());
    }

    /**
     * Launches the "Rescaling sweep" dialog and, if confirmed, runs the
     * rescaling sweep in the background.
     * <p>
     * Steps:
     * </p>
     * <ol>
     *   <li>Create a baseline {@link Params} with sensible defaults.</li>
     *   <li>Show {@link RescaleHuDialog} to collect sweep parameters.</li>
     *   <li>Ensure we know where the neuron (Hu) StarDist model ZIP is.</li>
     *   <li>Copy thresholds/properties from the dialog config into {@code base}.</li>
     *   <li>Normalize / create the output directory.</li>
     *   <li>Kick off {@link TuningTools#runRescaleSweep} on a background thread.</li>
     * </ol>
     */
    private void startRescale() {
        Params base = defaultBaseParams();

        RescaleHuDialog dlg = new RescaleHuDialog(SwingUtilities.getWindowAncestor(this));
        RescaleHuDialog.Config cfg = dlg.showAndGet();
        if (cfg == null) return;

        // supply model (Hu) if you keep an ensure method
        ensureNeuronModelZip(base);

        base.probThresh = cfg.prob;
        base.nmsThresh  = cfg.overlap;
        base.huChannel  = cfg.channel;

        File outDir = ensureDir(cfg.outDir);

        runAsync("Rescaling", () ->
                TuningTools.runRescaleSweep(base, outDir, settings, cfg));
    }

    /**
     * Ensures the given directory exists and returns a usable output directory.
     * <p>
     * If {@code dir} is {@code null}, a default location under
     * {@code ~/Analysis/Tuning} is created/returned. If {@code dir} is not
     * {@code null} but does not exist, the directory (and parents if needed)
     * is created.
     * </p>
     *
     * @param dir user-chosen directory (may be {@code null})
     * @return a directory guaranteed to exist and be writable (best effort)
     */
    private static File ensureDir(File dir) {
        if (dir == null) {
            File def = new File(new File(System.getProperty("user.home"), "Analysis"), "Tuning");
            if (!def.isDirectory()) def.mkdirs();
            return def;
        }
        if (!dir.isDirectory()) dir.mkdirs();
        return dir;
    }

    /**
     * Launches the "Probability sweep" dialog and, if confirmed, runs the
     * probability sweep in the background.
     * <p>
     * Steps:
     * </p>
     * <ol>
     *   <li>Create a baseline {@link Params} with defaults.</li>
     *   <li>Show {@link UI.panes.Tools.dialogs.ProbabilityDialog} to
     *       configure sweep range and thresholds.</li>
     *   <li>If the user chose NEURON mode, ensure we can locate the Hu model
     *       ZIP before proceeding.</li>
     *   <li>Run {@link TuningTools#runProbSweep} asynchronously, passing the
     *       dialog config.</li>
     * </ol>
     */
    private void startProbability() {
        Params base = defaultBaseParams();

        // open dialog
        UI.panes.Tools.dialogs.ProbabilityDialog dlg =
                new UI.panes.Tools.dialogs.ProbabilityDialog(SwingUtilities.getWindowAncestor(this));
        UI.panes.Tools.dialogs.ProbabilityDialog.Config cfg = dlg.showAndGet();
        if (cfg == null) return;

        // For NEURON probability we still need the neuron model
        if (cfg.mode == UI.panes.Tools.dialogs.ProbabilityDialog.Mode.NEURON) {
            if (!ensureNeuronModelZip(base)) return;
        }
        // For SUBTYPE probability, the dialog gives us modelZip in cfg; runner handles it.

        runAsync("Probability sweep", () ->
                TuningTools.runProbSweep(base, null, settings, cfg));
    }

    /**
     * Launches the "Ganglia expansion sweep" dialog and, if confirmed,
     * runs the expansion sweep in the background.
     * <p>
     * Ganglia expansion tuning uses Hu neuron segmentation as input,
     * so we first ensure that a neuron (Hu) StarDist model is available.
     * </p>
     *
     * @see TuningTools#runGangliaExpansionSweep(Params, File, UI.util.GatSettings,
     *      UI.panes.Tools.dialogs.GangliaExpansionDialog.Config)
     */
    private void startGanglia() {
        Params base = defaultBaseParams();
        if (!ensureNeuronModelZip(base)) return; // Hu segmentation required

        UI.panes.Tools.dialogs.GangliaExpansionDialog dlg =
                new UI.panes.Tools.dialogs.GangliaExpansionDialog(SwingUtilities.getWindowAncestor(this));
        UI.panes.Tools.dialogs.GangliaExpansionDialog.Config cfg = dlg.showAndGet();
        if (cfg == null) return;

        runAsync("Ganglia expansion sweep", () ->
                TuningTools.runGangliaExpansionSweep(base, null, settings, cfg));
    }

    /**
     * Runs a potentially long tuning task (e.g. rescale sweep) on a background
     * {@link SwingWorker}, while updating UI state.
     * <p>
     * This method:
     * </p>
     * <ol>
     *   <li>Disables the tuning buttons and shows a "Running..." message.</li>
     *   <li>Executes the provided {@code task} on a worker thread.</li>
     *   <li>If the task throws, shows an error dialog on the EDT.</li>
     *   <li>When finished, re-enables the UI and marks the task as done.</li>
     * </ol>
     *
     * @param label short human-readable label for status messages
     * @param task  code to run in the background
     */
    private void runAsync(String label, Runnable task) {
        setBusy(true, "Running: " + label + " … (this might take a while)");
        SwingWorker<Void, Void> w = new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                try { task.run(); } catch (Throwable t) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            ToolsPane.this, "Error during " + label + ":\n" + t.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE));
                }
                return null;
            }
            @Override protected void done() { setBusy(false, "Done: " + label); }
        };
        w.execute();
    }

    /**
     * Enables or disables the tuning buttons and updates the status label /
     * cursor to reflect "busy" or "idle".
     *
     * @param b   {@code true} to enter busy state (disable controls, wait cursor),
     *            {@code false} to return to idle state
     * @param msg message to display in the status label (may be {@code null}
     *            to clear)
     */
    private void setBusy(boolean b, String msg) {
        setCursor(Cursor.getPredefinedCursor(b ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
        btnRescale.setEnabled(!b);
        btnProb.setEnabled(!b);
        btnGanglia.setEnabled(!b);
        status.setText(msg != null ? msg : " ");
    }

    /**
     * Builds a fresh {@link Params} object populated with baseline/default
     * values used by tuning sweeps.
     * <p>
     * These defaults cover channel indices, rescaling assumptions, minimum
     * neuron size, and ganglia post-processing parameters. Callers are free
     * to override fields before passing the {@link Params} onward.
     * </p>
     *
     * @return a new {@link Params} pre-filled with standard neuron settings
     */
    private static Params defaultBaseParams() {
        Params p = new Params();
        p.huChannel = 3;
        p.rescaleToTrainingPx = true;
        p.trainingPixelSizeUm = 0.568;
        p.trainingRescaleFactor = 1.0;
        p.probThresh = 0.50;
        p.nmsThresh  = 0.30;
        p.neuronSegMinMicron = 70.0;
        p.saveFlattenedOverlay = true;
        p.cellTypeName = "Neuron";
        p.gangliaInteractiveReview = true;
        p.gangliaOpenIterations = 3;
        p.gangliaMinAreaUm2 = 200.0;
        p.spatialExpansionUm = 6.5;
        p.spatialSaveParametric = false;
        return p;
    }

    /**
     * Tries to locate the StarDist neuron (Hu) model ZIP on disk and attach
     * it to the supplied {@link Params}.
     * <p>
     * By convention this looks under ImageJ's "models" directory for a file
     * like {@code 2D_enteric_neuron_V4_1.zip} (or a known fallback). The
     * absolute path of the first match found is stored in
     * {@code p.stardistModelZip}.
     * </p>
     *
     * @param p params object to update with the discovered model path
     * @return {@code true} if a plausible model ZIP was found and recorded,
     *         {@code false} otherwise
     */
    private static boolean ensureNeuronModelZip(Params p) {
        // Point this at your Hu (neuron) StarDist model zip.
        // If your Hu model is "2D_enteric_neuron_V4_1.zip", use that.
        File modelsDir = new File(ij.IJ.getDirectory("imagej"), "models");
        File model = new File(modelsDir, "2D_enteric_neuron_V4_1.zip");
        if (!model.isFile()) {
            // fallback to subtype name if that's what you actually ship
            model = new File(modelsDir, "2D_enteric_neuron_subtype_v4.zip");
        }
        p.stardistModelZip = model.getAbsolutePath();
        return model.isFile();  // <— return true if we found a model
    }

    /**
     * Lets the user pick an output directory, or falls back to a default.
     * <p>
     * Shows a {@link JFileChooser} starting in {@code preselect}. If the user
     * approves a directory, that directory is returned. Otherwise the method
     * falls back to {@code ~/Analysis}.
     * </p>
     *
     * @param preselect directory to open the chooser in initially
     * @return the user-selected directory, or {@code ~/Analysis} if cancelled
     */
    private static File chooseOutDirOrDefault(File preselect) {
        JFileChooser fc = new JFileChooser(preselect);
        fc.setDialogTitle("Choose output folder (optional)");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setMultiSelectionEnabled(false);
        int ret = fc.showOpenDialog(null);
        if (ret == JFileChooser.APPROVE_OPTION && fc.getSelectedFile() != null) return fc.getSelectedFile();
        return new File(System.getProperty("user.home"), "Analysis");
    }

    /**
     * Ensures there is a {@code Tuning/} subfolder and returns it.
     * <p>
     * If {@code parent} is {@code null}, {@code ~/Analysis} is used first,
     * and then {@code Tuning/} is created under it. If {@code parent} is
     * non-null, the {@code Tuning/} subfolder is created under that parent.
     * </p>
     *
     * @param parent base directory to contain the {@code Tuning/} folder
     * @return a {@link File} pointing to {@code parent/Tuning}, guaranteed
     *         to exist (best effort)
     */
    private static File ensureTuningDir(File parent) {
        File base = (parent != null) ? parent : new File(System.getProperty("user.home"), "Analysis");
        File tuning = new File(base, "Tuning");
        if (!tuning.isDirectory()) tuning.mkdirs();
        return tuning;
    }
}
