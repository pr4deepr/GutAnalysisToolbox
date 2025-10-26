package UI.panes.SettingPanes;

import Features.AnalyseWorkflows.NeuronsMultiNoHuPipeline;
import Features.Core.Params;
import UI.Handlers.Navigator;
import static UI.util.FormUI.*;

import UI.util.ConfigIO;
import UI.util.InputValidation;
import ij.IJ;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Settings panel (Swing) for the "Multi-Channel (No Hu)" workflow.
 *
 * Tabs:
 *  • Basic    – input/output, ganglia (no-Hu variants), spatial toggle
 *  • Markers  – dynamic list of markers (channel + optional custom ROI zip)
 *  • Advanced – subtype model, thresholds, rescale controls, DIJ model folder, config load/save
 *
 * Interaction:
 *  • "Run Multi-Channel (No Hu)" triggers input validation and launches a background SwingWorker
 *    to execute {@code new NeuronsMultiNoHuPipeline().run(mp)}.
 *
 * Persisted defaults:
 *  • Uses {@link UI.util.ConfigIO} to save/load a minimal property set (see toConfig/applyConfig).
 */
public class MultiChannelNoHuPane extends JPanel {

    public static final String Name = "Multi-Channel No Hu";

    private final Navigator navigator;
    private final Window owner;

    // --- Basic ---
    private JTextField tfImagePath;
    private JButton    btnBrowseImage;
    private JButton    btnPreviewImage;

    private JCheckBox cbDoSpatial;

    private JTextField tfSubtypeModelZip;
    private JButton    btnBrowseSubtypeModel;

    private JTextField tfGangliaRoiZip;
    private JButton    btnBrowseGangliaRoi;
    private JPanel     pnlCustomRoiBox;

    private JPanel     pnlGangliaModelRow;

    private JCheckBox  cbRescaleToTrainingPx;
    private JSpinner   spTrainingPixelSizeUm;   // double
    private JSpinner   spTrainingRescaleFactor; // double

    private JSpinner   spDefaultProb;           // 0..1
    private JSpinner   spDefaultNms;            // 0..1
    private JSpinner   spOverlapFrac;           // 0..1  (for combinations)

    private JSpinner   spMinMarkerSizeUm;       // double (like neuronSegMinMicron)

    private JCheckBox  cbSaveFlattenedOverlay;

    private JTextField tfOutputDir;
    private JButton    btnBrowseOutput;

    // --- Ganglia ---
    private JCheckBox  cbGangliaAnalysis;
    private JComboBox<Params.GangliaMode> cbGangliaMode;
    private JSpinner   spGangliaChannel;
    private JTextField tfGangliaModelFolder;
    private JButton    btnBrowseGangliaModelFolder;
    private  JSpinner spCellBodyChannel;

    // --- Markers list ---
    private JPanel     markersPanel;            // holds rows
    private JButton    btnAddMarker;
    private JButton    btnRemoveMarker;

    private final List<MarkerRow> markerRows = new ArrayList<>();

    /**
     * Construct the settings pane and wire actions for all controls.
     * Adds tabs, bottom action bar, and loads initial defaults.
     *
     * @param navigator (Optional) app navigator (not used here but kept for parity).
     */
    public MultiChannelNoHuPane(Navigator navigator) {
        super(new BorderLayout(10,10));
        this.navigator = navigator;
        this.owner = SwingUtilities.getWindowAncestor(this);

        setBorder(BorderFactory.createEmptyBorder(16,16,16,16));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Basic", buildBasicTab());
        tabs.addTab("Markers", buildMarkersTab());
        tabs.addTab("Advanced", buildAdvancedTab());

        add(tabs, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton runBtn = new JButton("Run Multi-Channel (No Hu)");
        runBtn.addActionListener(e -> onRun(runBtn));
        actions.add(runBtn);
        add(actions, BorderLayout.SOUTH);

        loadDefaults();
    }

    /**
     * Build and return the "Basic" tab:
     *  - Image chooser + Preview
     *  - Output folder + "Save flattened overlays"
     *  - Ganglia options for no-Hu (mode, channels, model folder / ROI zip)
     *  - Spatial analysis checkbox
     */
    private JPanel buildBasicTab() {
        JPanel outer = new JPanel(new BorderLayout());
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        // Image
        tfImagePath = new JTextField(36);
        btnBrowseImage = new JButton("Browse…");
        btnBrowseImage.addActionListener(e -> chooseFile(tfImagePath, JFileChooser.FILES_ONLY));
        btnPreviewImage = new JButton("Preview");
        btnPreviewImage.addActionListener(e-> previewImage());

        String imgHelp =
                "<b>What to select:</b> image to analyse (must be Hu-Stained).<br/>"
                        + "<b>Tip:</b> click <i>Preview</i> one you have selected an image to view it.";

        p.add(boxWithHelp(
                "Input image (.tif, .lif, etc.)",
                row(tfImagePath, btnBrowseImage, btnPreviewImage),
                imgHelp
        ));



        // Output dir
        tfOutputDir = new JTextField(36);
        btnBrowseOutput = new JButton("Browse…");
        btnBrowseOutput.addActionListener(e -> chooseFile(tfOutputDir, JFileChooser.DIRECTORIES_ONLY));
        cbSaveFlattenedOverlay = new JCheckBox("Save flattened overlays");


        String outputHelp =
                "<b>Output:</b> Choose where you want your images to be saved to (optional; default: Analysis/<basename> at your image location) , and optionally choose to save a flattened image.<br/>";

        p.add(boxWithHelp(
                "Output Location",
                column(row(tfOutputDir, btnBrowseOutput),cbSaveFlattenedOverlay),
                outputHelp
        ));


        cbGangliaAnalysis = new JCheckBox("Run ganglia analysis");
        cbGangliaMode = new JComboBox<>(new Params.GangliaMode[] {
                Params.GangliaMode.DEEPIMAGEJ,
                Params.GangliaMode.IMPORT_ROI,
                Params.GangliaMode.MANUAL
        });

        cbGangliaAnalysis.addActionListener(e -> updateGangliaVisibility());
        cbGangliaMode.addActionListener(e -> updateGangliaVisibility());
        spGangliaChannel   = new JSpinner(new SpinnerNumberModel(2, 1, 64, 1)); // fibres/neurites
        spCellBodyChannel  = new JSpinner(new SpinnerNumberModel(1, 1, 64, 1)); // NEW: cell-body
        tfGangliaModelFolder = new JTextField(28);

        String gangliaHelp =
                "<b>Ganglia (no-Hu) options:</b> Identify and measure ganglia without a Hu channel. "
                        + "These settings are used only if <i>Run ganglia analysis</i> is checked.<br/><br/>"

                        + "<b>Channels (1-based):</b>"
                        + "<ul style='margin-top:4px'>"
                        + "<li><b>Fibres / neurites channel</b> – choose the channel where neurite bundles are most visible "
                        + "(a pan-neuronal fibres marker). Used to trace/connect tissue between cells.</li>"
                        + "<li><b>Cell-body channel</b> – choose the channel where neuronal somas are best visible "
                        + "(the marker expressed by most cells). Used to seed/anchor ganglia in dense regions.</li>"
                        + "</ul>"

                        + "<b>Ganglia mode:</b>"
                        + "<ul style='margin-top:4px'>"
                        + "<li><b>DEEPIMAGEJ</b> – runs a trained model to propose ganglia ROIs. "
                        + "</li>"
                        + "<li><b>IMPORT ROI</b> – reuse ganglia from an existing <code>.zip</code> of ROIs "
                        + "(e.g. from a previous run). No model is used.</li>"
                        + "<li><b>MANUAL</b> – draw the ganglia regions yourself.</li>"
                        + "</ul>";

        p.add(boxWithHelp("Ganglia Options",
                column(
                        leftWrap( grid2Compact(
                                new JLabel("Ganglia mode:"),    limitWidth(cbGangliaMode, 180),
                                new JLabel("Fibres / neurites channel (1-based):"), limitWidth(spGangliaChannel,60),
                                new JLabel("Cell-body (‘most cells’) channel (1-based):"), limitWidth(spCellBodyChannel,60)
                        )),
                        cbGangliaAnalysis
                ),
                gangliaHelp));




        p.add(Box.createVerticalStrut(8));

        cbDoSpatial = new JCheckBox("Perform spatial analysis");

        String spatialHelp =
                "<b>Spatial Analysis:</b> Save a CSV with spatial analysis data of neurons.<br/>";

        p.add(boxWithHelp("Spatial analysis",
                leftWrap(column(cbDoSpatial)),
                spatialHelp
        ));

        tfGangliaRoiZip = new JTextField(28);
        btnBrowseGangliaRoi = new JButton("Browse…");
        btnBrowseGangliaRoi.addActionListener(e -> chooseFile(tfGangliaRoiZip, JFileChooser.FILES_ONLY));

        pnlCustomRoiBox = box("Import ganglia ROIs (.zip)",
                row(new JLabel("Zip file:"), tfGangliaRoiZip, btnBrowseGangliaRoi));
        p.add(pnlCustomRoiBox);



        JScrollPane scroll = new JScrollPane(
                p,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        );
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        outer.add(scroll, BorderLayout.CENTER);
        return outer;
    }

    /**
     * Build the "Markers" tab – a scrollable list of cards, each defining:
     *  name, 1-based channel index, optional “use custom ROI .zip”.
     *  Includes “Add marker” and “Remove selected” controls.
     */
    private JPanel buildMarkersTab() {
        JPanel outer = new JPanel(new BorderLayout(8,8));

        markersPanel = new JPanel();
        markersPanel.setLayout(new BoxLayout(markersPanel, BoxLayout.Y_AXIS));
        markersPanel.setBorder(BorderFactory.createEmptyBorder(4,4,4,4));

        JScrollPane scroll = new JScrollPane(markersPanel);
        scroll.setPreferredSize(new Dimension(640, 260));
        outer.add(scroll, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnAddMarker = new JButton("Add marker");
        btnRemoveMarker = new JButton("Remove selected");
        btnAddMarker.addActionListener(e -> addMarkerRow(null, 1, null, null, false, null));
        btnRemoveMarker.addActionListener(e -> removeSelectedMarkerRow());
        controls.add(btnAddMarker);
        controls.add(btnRemoveMarker);

        outer.add(controls, BorderLayout.SOUTH);
        outer.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Markers",
                TitledBorder.LEFT,
                TitledBorder.TOP
        ));
        return outer;
    }

    /**
     * Build the "Advanced" tab – subtype model path, default thresholds (prob/NMS/overlap),
     * rescale controls, DIJ model folder, and config load/save buttons.
     */
    private JPanel buildAdvancedTab() {
        JPanel outer = new JPanel(new BorderLayout());
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        // Subtype model (.zip)
        tfSubtypeModelZip = new JTextField(36);
        btnBrowseSubtypeModel = new JButton("Browse…");
        btnBrowseSubtypeModel.addActionListener(e -> chooseFile(tfSubtypeModelZip, JFileChooser.FILES_ONLY));
        p.add(box("Subtype StarDist model (.zip)", row(tfSubtypeModelZip, btnBrowseSubtypeModel)));

        // Thresholds
        spDefaultProb = new JSpinner(new SpinnerNumberModel(0.50, 0.0, 1.0, 0.05));
        spDefaultNms  = new JSpinner(new SpinnerNumberModel(0.30, 0.0, 1.0, 0.05));
        spOverlapFrac = new JSpinner(new SpinnerNumberModel(0.40, 0.0, 1.0, 0.05));
        spMinMarkerSizeUm = new JSpinner(new SpinnerNumberModel(160.0, 0.0, 10000.0, 1.0));

        p.add(box("Detection", grid2(
                new JLabel("Default probability:"), spDefaultProb,
                new JLabel("Default NMS:"),         spDefaultNms,
                new JLabel("Min marker size (µm):"), spMinMarkerSizeUm,
                new JLabel("Overlap (combos):"),    spOverlapFrac
        )));

        // Rescale group
        cbRescaleToTrainingPx = new JCheckBox("Rescale to training pixel size");
        spTrainingPixelSizeUm = new JSpinner(new SpinnerNumberModel(0.568, 0.01, 100.0, 0.001));
        spTrainingRescaleFactor = new JSpinner(new SpinnerNumberModel(1.0, 0.01, 100.0, 0.01));

        p.add(box("Projection & Rescaling", column(
                row(new JLabel("Training pixel size (µm):"), spTrainingPixelSizeUm),
                row(new JLabel("Training rescale factor:"),   spTrainingRescaleFactor),
                cbRescaleToTrainingPx
        )));

        tfGangliaModelFolder = new JTextField(28);
        btnBrowseGangliaModelFolder = new JButton("Browse…");
        btnBrowseGangliaModelFolder.addActionListener(e -> chooseFolderName(tfGangliaModelFolder));

        pnlGangliaModelRow = box("Ganglia model (DeepImageJ)",
                row(new JLabel("Folder (under <Fiji>/models):"), tfGangliaModelFolder, btnBrowseGangliaModelFolder));
        p.add(pnlGangliaModelRow);

        JButton btnLoadCfg = new JButton("Load config…");
        btnLoadCfg.addActionListener(e -> loadConfigFromFile());
        JButton btnSaveCfg = new JButton("Save config…");
        btnSaveCfg.addActionListener(e -> saveConfigToFile());
        p.add(box("Config file", row(btnLoadCfg, btnSaveCfg)));



        p.add(Box.createVerticalStrut(8));

        JScrollPane scroll = new JScrollPane(
                p,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        );
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        outer.add(scroll, BorderLayout.CENTER);
        return outer;
    }

    /**
     * Append a new marker row to the list.
     *
     * @param name    Marker display name (nullable → "marker").
     * @param channel 1-based channel index to prefill.
     * @param prob    Unused here (kept for parity); can be null.
     * @param nms     Unused here (kept for parity); can be null.
     * @param custom  Whether "custom ROI zip" is initially checked.
     * @param roiZip  Optional initial zip file.
     */
    private void addMarkerRow(String name, int channel, Double prob, Double nms, boolean custom, File roiZip) {
        MarkerRow row = new MarkerRow(name, channel, custom, roiZip);
        markerRows.add(row);
        markersPanel.add(row.panel);
        markersPanel.revalidate();
        markersPanel.repaint();
    }

    /** Remove all selected marker rows; if none selected, remove the last row. */
    private void removeSelectedMarkerRow() {
        boolean removedAny = false;

        // walk from bottom to top so indices stay valid while removing
        for (int i = markerRows.size() - 1; i >= 0; i--) {
            MarkerRow row = markerRows.get(i);
            if (row.cbSelect.isSelected()) {
                markersPanel.remove(row.panel);
                markerRows.remove(i);
                removedAny = true;
            }
        }

        // if nothing was selected, remove the last row as a fallback
        if (!removedAny && !markerRows.isEmpty()) {
            int last = markerRows.size() - 1;
            markersPanel.remove(markerRows.get(last).panel);
            markerRows.remove(last);
        }

        // layout update once
        markersPanel.revalidate();
        markersPanel.repaint();
    }

    /**
     * Row widget representing one marker input. Produces a {@code MarkerSpec} for the pipeline.
     * Validates custom ROI zip if enabled.
     */
    private final class MarkerRow {
        final JPanel panel = new JPanel(new GridBagLayout());
        final JCheckBox cbSelect = new JCheckBox();
        final JTextField tfName  = new JTextField(12);
        final JSpinner  spChannel = new JSpinner(new SpinnerNumberModel(1, 1, 64, 1));
        final JCheckBox cbCustom = new JCheckBox("Use custom ROI .zip");
        final JTextField tfRoiZip = new JTextField(18);
        final JButton   btnBrowseRoi = new JButton("…");

        MarkerRow(String name, int channel, boolean custom, File roiZip) {
            // card-like border
            panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(230,230,230)),
                    BorderFactory.createEmptyBorder(6,6,6,6)
            ));

            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(4,4,4,4);
            c.anchor = GridBagConstraints.WEST;

            // Row 0: select | Name: [.....] | Channel: [#]
            c.gridy = 0; c.gridx = 0;                           panel.add(cbSelect, c);

            c.gridx = 1;                                        panel.add(new JLabel("Name:"), c);
            c.gridx = 2; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
            tfName.setText(name != null ? name : "marker");
            tfName.setMaximumSize(new Dimension(Integer.MAX_VALUE, tfName.getPreferredSize().height));
            panel.add(tfName, c);

            c.gridx = 3; c.weightx = 0; c.fill = GridBagConstraints.NONE;
            panel.add(new JLabel("Channel:"), c);
            c.gridx = 4;                                        spChannel.setValue(channel); panel.add(spChannel, c);

            // Row 1: Custom ROI zip: [x]  [path...............]  […]
            c.gridy = 1; c.gridx = 1;                           panel.add(new JLabel("Custom ROI zip:"), c);
            c.gridx = 2;                                        cbCustom.setSelected(custom); panel.add(cbCustom, c);

            c.gridx = 3; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
            tfRoiZip.setEnabled(custom);
            tfRoiZip.setText(roiZip != null ? roiZip.getAbsolutePath() : "");
            tfRoiZip.setMaximumSize(new Dimension(Integer.MAX_VALUE, tfRoiZip.getPreferredSize().height));
            panel.add(tfRoiZip, c);

            c.gridx = 4; c.weightx = 0; c.fill = GridBagConstraints.NONE;
            btnBrowseRoi.setEnabled(custom);
            btnBrowseRoi.addActionListener(e -> chooseFile(tfRoiZip, JFileChooser.FILES_ONLY));
            panel.add(btnBrowseRoi, c);

            cbCustom.addActionListener(e -> {
                boolean on = cbCustom.isSelected();
                tfRoiZip.setEnabled(on);
                btnBrowseRoi.setEnabled(on);
            });

            // Make the card width follow the container (prevents horizontal scroll)
            panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
            panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        // Convert row -> pipeline spec (no per-marker prob/nms)
        NeuronsMultiNoHuPipeline.MarkerSpec toSpec() {
            String nm = tfName.getText().trim();
            if (nm.isEmpty()) throw new IllegalArgumentException("Marker name cannot be empty.");
            int ch = ((Number) spChannel.getValue()).intValue();

            NeuronsMultiNoHuPipeline.MarkerSpec spec = new NeuronsMultiNoHuPipeline.MarkerSpec(nm, ch);

            if (cbCustom.isSelected()) {
                String z = tfRoiZip.getText().trim();
                if (z.isEmpty()) throw new IllegalArgumentException(
                        "Custom ROI selected for '"+nm+"' but no zip chosen.");
                File f = new File(z);
                if (!f.isFile() || !z.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
                    throw new IllegalArgumentException("Custom ROI for '"+nm+"' must be a .zip on disk.");
                }
                spec.withCustomRois(f);
            }
            return spec;
        }
    }

    /**
     * Validate inputs, construct {@link NeuronsMultiNoHuPipeline.MultiParams}, and run the pipeline
     * in a background {@link SwingWorker}. Errors are shown via JOptionPane.
     *
     * Disables the run button while work is in progress.
     */
    private void onRun(JButton runBtn) {
        runBtn.setEnabled(false);

        if (markerRows.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please add at least one marker in the Markers tab.",
                    "No markers", JOptionPane.ERROR_MESSAGE);
            runBtn.setEnabled(true);
            return;
        }

        if (cbGangliaAnalysis.isSelected()) {
            Params.GangliaMode mode = (Params.GangliaMode) cbGangliaMode.getSelectedItem();
            if (mode == Params.GangliaMode.DEEPIMAGEJ) {
                if (!InputValidation.validateModelsFolderOrShow(this, tfGangliaModelFolder.getText())) {
                    runBtn.setEnabled(true); return;
                }
            } else if (mode == Params.GangliaMode.IMPORT_ROI) {
                if (!InputValidation.validateZipOrShow(this, tfGangliaRoiZip.getText(), "Ganglia ROI zip")) {
                    runBtn.setEnabled(true); return;
                }
            }
        }

        SwingWorker<Void,Void> worker = new SwingWorker() {
            @Override protected Void doInBackground() {
                try {
                    // Build params and run
                    NeuronsMultiNoHuPipeline.MultiParams mp = buildMultiParamsFromUI();
                    new NeuronsMultiNoHuPipeline().run(mp);
                } catch (Throwable ex) {
                    JOptionPane.showMessageDialog(
                            SwingUtilities.getWindowAncestor(MultiChannelNoHuPane.this),
                            "Analysis failed:\n" + ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
                return null;
            }
            @Override protected void done() { runBtn.setEnabled(true); }
        };
        worker.execute();
    }

    /**
     * Build {@link NeuronsMultiNoHuPipeline.MultiParams} from current UI state.
     * Populates {@code mp.base} with generic {@link Params} (rescale, overlays, ganglia, etc.),
     * then fills subtype model thresholds and marker list.
     *
     * @return Parameter bundle for the No-Hu pipeline.
     */

    private NeuronsMultiNoHuPipeline.MultiParams buildMultiParamsFromUI() {
        // base Params reused by all pipelines (projection, rescale, ganglia, etc.)
        Params base = new Params();
        base.imagePath = emptyToNull(tfImagePath.getText());
        base.outputDir = emptyToNull(tfOutputDir.getText());

        base.doSpatialAnalysis     = cbDoSpatial.isSelected();
        base.spatialCellTypeName   = "Hu";

        base.rescaleToTrainingPx   = cbRescaleToTrainingPx.isSelected();
        base.trainingPixelSizeUm   = ((Number) spTrainingPixelSizeUm.getValue()).doubleValue();
        base.trainingRescaleFactor = ((Number) spTrainingRescaleFactor.getValue()).doubleValue();

        base.neuronSegMinMicron    = ((Number) spMinMarkerSizeUm.getValue()).doubleValue(); // reuse field name
        base.saveFlattenedOverlay  = cbSaveFlattenedOverlay.isSelected();

        // ganglia options (optional)
        base.cellCountsPerGanglia = cbGangliaAnalysis.isSelected();
        base.gangliaMode = (Params.GangliaMode) cbGangliaMode.getSelectedItem();
        base.gangliaChannel     = ((Number) spGangliaChannel.getValue()).intValue();   // fibres
        base.gangliaCellChannel = ((Number) spCellBodyChannel.getValue()).intValue();

        if (base.cellCountsPerGanglia) {
            if (base.gangliaMode == Params.GangliaMode.DEEPIMAGEJ) {
                base.gangliaModelFolder = emptyToNull(tfGangliaModelFolder.getText()); // folder name under <Fiji>/models
                base.customGangliaRoiZip = null;
            } else if (base.gangliaMode == Params.GangliaMode.IMPORT_ROI) {
                base.customGangliaRoiZip = emptyToNull(tfGangliaRoiZip.getText());
                base.gangliaModelFolder = null;
            } else {
                base.gangliaModelFolder = null;
                base.customGangliaRoiZip = null;
            }
        } else {
            base.gangliaModelFolder = null;
            base.customGangliaRoiZip = null;
        }

        // build MultiParams
        NeuronsMultiNoHuPipeline.MultiParams mp = new NeuronsMultiNoHuPipeline.MultiParams();
        mp.base = base;
        mp.subtypeModelZip = tfSubtypeModelZip.getText();
        mp.multiProb = ((Number) spDefaultProb.getValue()).doubleValue();
        mp.multiNms  = ((Number) spDefaultNms.getValue()).doubleValue();
        mp.overlapFrac = ((Number) spOverlapFrac.getValue()).doubleValue();

        if (markerRows.isEmpty()) throw new IllegalStateException("Add at least one marker.");
        for (MarkerRow r : markerRows) {
            mp.markers.add(r.toSpec());
        }
        return mp;
    }

    // ---------------- Helpers ----------------

    private static String emptyToNull(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private void chooseFile(JTextField target, int mode) {
        JFileChooser ch = new JFileChooser();
        ch.setFileSelectionMode(mode);
        if (target.getText() != null && !target.getText().isEmpty()) {
            File start = new File(target.getText());
            ch.setCurrentDirectory(start.isDirectory() ? start : start.getParentFile());
        }
        int rv = ch.showOpenDialog(this);
        if (rv == JFileChooser.APPROVE_OPTION) {
            target.setText(ch.getSelectedFile().getAbsolutePath());
        }
    }

    private void chooseFolderName(JTextField target) {
        File models = new File(IJ.getDirectory("imagej"), "models");
        JFileChooser ch = new JFileChooser(models);
        ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int rv = ch.showOpenDialog(this);
        if (rv == JFileChooser.APPROVE_OPTION) {
            File sel = ch.getSelectedFile();
            target.setText(sel.getName()); // store folder name only (parity with your Params usage)
        }
    }

    private void previewImage() {
        final String path = (tfImagePath.getText() == null) ? "" : tfImagePath.getText().trim();
        btnPreviewImage.setEnabled(false);

        new SwingWorker<Void,Void>() {
            @Override protected Void doInBackground() {
                try {
                    if (path.isEmpty()) {
                        // Show the standard ImageJ file chooser (File ▸ Open…)
                        IJ.open();
                    } else {
                        // Open exactly the way ImageJ would from File ▸ Open…
                        IJ.open(path);
                    }
                } catch (Throwable ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            owner,
                            "Preview failed:\n" + ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                            "Preview error",
                            JOptionPane.ERROR_MESSAGE
                    ));
                }
                return null;
            }
            @Override protected void done() {
                btnPreviewImage.setEnabled(true);
            }
        }.execute();
    }

    private void loadDefaults() {
        // You can customize these defaults to your environment
        tfSubtypeModelZip.setText(new File(new File(IJ.getDirectory("imagej"), "models"), "2D_enteric_neuron_subtype_v4.zip").getAbsolutePath());
        cbRescaleToTrainingPx.setSelected(true);
        spTrainingPixelSizeUm.setValue(0.568);
        spTrainingRescaleFactor.setValue(1.0);

        spDefaultProb.setValue(0.50);
        spDefaultNms.setValue(0.30);
        spOverlapFrac.setValue(0.40);
        spMinMarkerSizeUm.setValue(160.0);

        cbSaveFlattenedOverlay.setSelected(true);

        cbGangliaAnalysis.setSelected(false);
        cbGangliaMode.setSelectedItem(Params.GangliaMode.DEEPIMAGEJ);
        spGangliaChannel.setValue(1);
        spCellBodyChannel.setValue(!markerRows.isEmpty()
                ? ((Number) markerRows.get(0).spChannel.getValue()).intValue()
                : 1);

        if (tfGangliaRoiZip == null) tfGangliaRoiZip = new JTextField(28);
        tfGangliaRoiZip.setText("");
        tfGangliaModelFolder.setText("2D_Ganglia_RGB_v3.bioimage.io.model");
        updateGangliaVisibility();

        // one example row
        addMarkerRow("Marker 1", 1, null, null, false, null);
        addMarkerRow("Marker 2", 2, null, null, false, null);
    }

    private void updateGangliaVisibility() {
        boolean run = cbGangliaAnalysis.isSelected();
        Params.GangliaMode mode = (Params.GangliaMode) cbGangliaMode.getSelectedItem();

        boolean showCustom = run && mode == Params.GangliaMode.IMPORT_ROI;
        boolean showDIJ    = run && mode == Params.GangliaMode.DEEPIMAGEJ;

        if (pnlCustomRoiBox != null) {
            pnlCustomRoiBox.setVisible(showCustom);
            if (!showCustom && tfGangliaRoiZip != null) tfGangliaRoiZip.setText("");
        }
        if (pnlGangliaModelRow != null) {
            pnlGangliaModelRow.setVisible(showDIJ);
        }
        revalidate();
        repaint();
    }
    private java.util.Properties toConfigNoHu() {
        java.util.Properties p = new java.util.Properties();
        UI.util.ConfigIO.putStr(p, "workflow", "noHuWorkflow");
        UI.util.ConfigIO.putStr(p, "cfgVersion", "1");
        UI.util.ConfigIO.putStr(p, "nohu.subtypeModelZip", tfSubtypeModelZip.getText());
        UI.util.ConfigIO.putDbl(p, "nohu.defaultProb", ((Number)spDefaultProb.getValue()).doubleValue());
        UI.util.ConfigIO.putDbl(p, "nohu.defaultNms",  ((Number)spDefaultNms.getValue()).doubleValue());
        UI.util.ConfigIO.putDbl(p, "nohu.overlapFrac", ((Number)spOverlapFrac.getValue()).doubleValue());
        UI.util.ConfigIO.putDbl(p, "nohu.minMarkerSizeUm", ((Number)spMinMarkerSizeUm.getValue()).doubleValue());

        UI.util.ConfigIO.putBool(p,"rescale.enabled", cbRescaleToTrainingPx.isSelected());
        UI.util.ConfigIO.putDbl (p,"rescale.trainingPxUm", ((Number)spTrainingPixelSizeUm.getValue()).doubleValue());
        UI.util.ConfigIO.putDbl (p,"rescale.trainingScale", ((Number)spTrainingRescaleFactor.getValue()).doubleValue());
        UI.util.ConfigIO.putBool(p,"overlay.saveFlattened", cbSaveFlattenedOverlay.isSelected());
        UI.util.ConfigIO.putBool(p,"spatial.enabled", cbDoSpatial.isSelected());

        // ganglia (no-Hu variant)
        UI.util.ConfigIO.putBool(p,"ganglia.enabled", cbGangliaAnalysis.isSelected());
        UI.util.ConfigIO.putStr (p,"ganglia.mode", String.valueOf(cbGangliaMode.getSelectedItem()));
        UI.util.ConfigIO.putInt (p,"ganglia.fibresChannel", ((Number)spGangliaChannel.getValue()).intValue());
        UI.util.ConfigIO.putInt (p,"ganglia.cellBodyChannel", ((Number)spCellBodyChannel.getValue()).intValue());
        UI.util.ConfigIO.putStr (p,"ganglia.modelFolder", tfGangliaModelFolder.getText());
        return p;
    }

    private void applyConfigNoHu(java.util.Properties p) {
        if (UI.util.ConfigIO.has(p,"nohu.subtypeModelZip")) tfSubtypeModelZip.setText(UI.util.ConfigIO.getStr(p,"nohu.subtypeModelZip", tfSubtypeModelZip.getText()));
        if (UI.util.ConfigIO.has(p,"nohu.defaultProb"))     spDefaultProb.setValue(UI.util.ConfigIO.getDbl(p,"nohu.defaultProb", ((Number)spDefaultProb.getValue()).doubleValue()));
        if (UI.util.ConfigIO.has(p,"nohu.defaultNms"))      spDefaultNms.setValue(UI.util.ConfigIO.getDbl(p,"nohu.defaultNms",  ((Number)spDefaultNms.getValue()).doubleValue()));
        if (UI.util.ConfigIO.has(p,"nohu.overlapFrac"))     spOverlapFrac.setValue(UI.util.ConfigIO.getDbl(p,"nohu.overlapFrac", ((Number)spOverlapFrac.getValue()).doubleValue()));
        if (UI.util.ConfigIO.has(p,"nohu.minMarkerSizeUm")) spMinMarkerSizeUm.setValue(UI.util.ConfigIO.getDbl(p,"nohu.minMarkerSizeUm", ((Number)spMinMarkerSizeUm.getValue()).doubleValue()));

        if (UI.util.ConfigIO.has(p,"rescale.enabled")) cbRescaleToTrainingPx.setSelected(UI.util.ConfigIO.getBool(p,"rescale.enabled", cbRescaleToTrainingPx.isSelected()));
        if (UI.util.ConfigIO.has(p,"rescale.trainingPxUm")) spTrainingPixelSizeUm.setValue(UI.util.ConfigIO.getDbl(p,"rescale.trainingPxUm", ((Number)spTrainingPixelSizeUm.getValue()).doubleValue()));
        if (UI.util.ConfigIO.has(p,"rescale.trainingScale")) spTrainingRescaleFactor.setValue(UI.util.ConfigIO.getDbl(p,"rescale.trainingScale", ((Number)spTrainingRescaleFactor.getValue()).doubleValue()));
        if (UI.util.ConfigIO.has(p,"overlay.saveFlattened")) cbSaveFlattenedOverlay.setSelected(UI.util.ConfigIO.getBool(p,"overlay.saveFlattened", cbSaveFlattenedOverlay.isSelected()));
        if (UI.util.ConfigIO.has(p,"spatial.enabled")) cbDoSpatial.setSelected(UI.util.ConfigIO.getBool(p,"spatial.enabled", cbDoSpatial.isSelected()));

        if (UI.util.ConfigIO.has(p,"ganglia.enabled")) cbGangliaAnalysis.setSelected(UI.util.ConfigIO.getBool(p,"ganglia.enabled", cbGangliaAnalysis.isSelected()));
        if (UI.util.ConfigIO.has(p,"ganglia.mode"))    cbGangliaMode.setSelectedItem(Params.GangliaMode.valueOf(UI.util.ConfigIO.getStr(p,"ganglia.mode", String.valueOf(cbGangliaMode.getSelectedItem()))));
        if (UI.util.ConfigIO.has(p,"ganglia.fibresChannel")) spGangliaChannel.setValue(UI.util.ConfigIO.getInt(p,"ganglia.fibresChannel", ((Number)spGangliaChannel.getValue()).intValue()));
        if (UI.util.ConfigIO.has(p,"ganglia.cellBodyChannel")) spCellBodyChannel.setValue(UI.util.ConfigIO.getInt(p,"ganglia.cellBodyChannel", ((Number)spCellBodyChannel.getValue()).intValue()));
        if (UI.util.ConfigIO.has(p,"ganglia.modelFolder")) tfGangliaModelFolder.setText(UI.util.ConfigIO.getStr(p,"ganglia.modelFolder", tfGangliaModelFolder.getText()));

        updateGangliaVisibility();
    }


    private static final String EXPECTED_WORKFLOW = "noHuWorkflow";

    private void saveConfigToFile() {
        UI.util.ConfigIO.saveConfig(this, EXPECTED_WORKFLOW, this::toConfigNoHu);
    }

    private void loadConfigFromFile() {
        UI.util.ConfigIO.loadConfig(this, EXPECTED_WORKFLOW, this::applyConfigNoHu);
    }


}
