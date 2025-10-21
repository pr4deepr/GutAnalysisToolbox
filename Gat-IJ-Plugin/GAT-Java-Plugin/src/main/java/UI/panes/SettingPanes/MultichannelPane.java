package UI.panes.SettingPanes;

import Features.AnalyseWorkflows.NeuronsMultiPipeline;
import Features.Core.Params;
import UI.util.InputValidation;
import ij.IJ;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import static UI.util.FormUI.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MultichannelPane extends JPanel {
    public static final String Name = "Multiplex Workflow";

    private final Window owner;

    // Reuse Hu basics
    private JTextField tfImagePath, tfOutputDir;
    private JButton btnBrowseImage, btnBrowseOutput;
    private JButton btnPreviewImage;

    private JSpinner spHuChannel;
    private JTextField tfHuModelZip;

    private JCheckBox cbDoSpatial;

    private JTextField tfGangliaRoiZip;
    private JButton btnBrowseGangliaRoi;

    private JPanel pnlGangliaModelRow;
    private JTextField tfGangliaModelFolder;
    private JButton btnBrowseGangliaModel;

    // Subtype model + overlap
    private JTextField tfSubtypeModelZip;
    private JSpinner spOverlapFrac, spSubtypeProb, spSubtypeNms;

    // Ganglia + rescale etc. (subset for brevity)
    private JCheckBox cbGangliaAnalysis;
    private JComboBox<Params.GangliaMode> cbGangliaMode;
    private JSpinner spGangliaChannel;
    private JPanel pnlCustomRoiBox;

    //Build marker params
    private JPanel  markersPanel;
    private JButton btnAddMarker, btnRemoveMarker;
    private final java.util.List<MarkerRow> markerRows = new java.util.ArrayList<>();

    private JCheckBox cbRescaleToTrainingPx;
    private JSpinner spTrainingPxUm, spTrainingScale;
    private JCheckBox cbRequireMicronUnits;
    private JSpinner spNeuronSegLowerUm, spNeuronSegMinUm;

    public MultichannelPane(Window owner) {
        super(new BorderLayout(10,10));
        this.owner = owner;
        setBorder(BorderFactory.createEmptyBorder(16,16,16,16));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Basic", buildBasic());
        tabs.add("Markers",buildMarkersTab());
        tabs.addTab("Advanced", buildAdvanced());

        add(tabs, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton runBtn = new JButton("Run Multiplex Analysis");
        runBtn.addActionListener(e -> onRun(runBtn));
        actions.add(runBtn);
        add(actions, BorderLayout.SOUTH);

        loadDefaults();
    }

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
        btnAddMarker.addActionListener(e -> addMarkerRow(null, 1,  false, null));
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

    private void addMarkerRow(String name, int channel, boolean custom, File roiZip) {
        MarkerRow row = new MarkerRow(name, channel, custom, roiZip);
        markerRows.add(row);
        markersPanel.add(row.panel);
        markersPanel.revalidate();
        markersPanel.repaint();
    }

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

    // ------- MarkerRow -------
    private static final class MarkerRow {
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
        NeuronsMultiPipeline.MarkerSpec toSpec() {
            String nm = tfName.getText().trim();
            if (nm.isEmpty()) throw new IllegalArgumentException("Marker name cannot be empty.");
            int ch = ((Number) spChannel.getValue()).intValue();

            NeuronsMultiPipeline.MarkerSpec spec = new NeuronsMultiPipeline.MarkerSpec(nm, ch);

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





    private JPanel buildBasic() {

        JPanel outer = new JPanel(new BorderLayout());


        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        tfImagePath = new JTextField(36);
        btnBrowseImage = new JButton("Browse…");
        btnBrowseImage.addActionListener(e -> chooseFile(tfImagePath, JFileChooser.FILES_ONLY));
        btnPreviewImage = new JButton("Preview");
        btnPreviewImage.addActionListener(e -> previewImage());

        String imgHelp =
                "<b>What to select:</b> image to analyse (must be Hu-Stained).<br/>"
                        + "<b>Tip:</b> click <i>Preview</i> one you have selected an image to view it.";

        p.add(boxWithHelp(
                "Input image (.tif, .lif, etc.)",
                row(tfImagePath, btnBrowseImage, btnPreviewImage),
                imgHelp
        ));

        spHuChannel = new JSpinner(new SpinnerNumberModel(3,1,32,1));
        lockSpinner(spHuChannel);

        String channelHelp =
                "<b>Hu Channel:</b> Select the channel number which corresponds to the hu-stained channel.<br/>";

        p.add(boxWithHelp(
                "Channel Selection",
                leftWrap(grid2(
                        new JLabel("Hu Channel:"),     spHuChannel
                )),
                channelHelp
        ));



        tfOutputDir = new JTextField(36);
        btnBrowseOutput = new JButton("Browse…");
        btnBrowseOutput.addActionListener(e -> chooseFile(tfOutputDir, JFileChooser.DIRECTORIES_ONLY));


        String outputHelp =
                "<b>Output:</b> Choose where you want your images to be saved to (optional; default: Analysis/<basename> at your image location) , and optionally choose to save a flattened image.<br/>";

        p.add(boxWithHelp(
                "Output Location",
                column(row(tfOutputDir, btnBrowseOutput)),
                outputHelp
        ));

        cbGangliaAnalysis = new JCheckBox("Run ganglia analysis");
        cbGangliaMode = new JComboBox<>(Params.GangliaMode.values());
        cbGangliaAnalysis.addActionListener(e -> updateGangliaVisibility());
        cbGangliaMode.addActionListener(e -> updateGangliaVisibility());
        spGangliaChannel = new JSpinner(new SpinnerNumberModel(2,1,32,1));
        lockSpinner(spGangliaChannel);


        String gangliaHelp =
                "<b>Ganglia options:</b> Detect and measure ganglia when you have a Hu-stained channel. "
                        + "These settings apply only if <i>Run ganglia analysis</i> is checked.<br/><br/>"

                        + "<b>Channels (1-based):</b>"
                        + "<ul style='margin-top:4px'>"
                        + "<li><b>Ganglia channel</b> – the channel that best highlights ganglia structures "
                        + "(e.g., perineurial/glial signal or a counterstain used by the detector).</li>"
                        + "</ul>"

                        + "<b>Ganglia mode:</b>"
                        + "<ul style='margin-top:4px'>"
                        + "<li><b>DEEPIMAGEJ</b> – runs a trained model to propose ganglia ROIs. "
                        + "</li>"
                        + "<li><b>IMPORT ROI</b> – reuse ganglia from an existing <code>.zip</code> of ROIs "
                        + "(e.g., from a previous run).</li>"
                        + "<li><b>DEFINE FROM HU</b> – derives ganglia regions from the Hu channel by clustering "
                        + "Hu-positive cells and linking nearby clusters.</li>"
                        + "<li><b>MANUAL</b> – draw the ganglia regions yourself.</li>"
                        + "</ul>";

        p.add(boxWithHelp("Ganglia Options",
                column(
                        leftWrap( grid2Compact(
                                new JLabel("Ganglia Channel:"), limitWidth(spGangliaChannel, 56),
                                new JLabel("Ganglia mode:"),    limitWidth(cbGangliaMode, 180)
                        )),
                        cbGangliaAnalysis
                ),
                gangliaHelp));

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

    private JPanel buildAdvanced() {
        JPanel outer = new JPanel(new BorderLayout());
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        cbRescaleToTrainingPx = new JCheckBox("Rescale to training pixel size");
        spTrainingPxUm = new JSpinner(new SpinnerNumberModel(0.568, 0.01, 100.0, 0.001));
        spTrainingScale= new JSpinner(new SpinnerNumberModel(1.0,   0.01, 100.0, 0.01));
        p.add(box("Rescaling", grid2(
                new JLabel("Training pixel size (µm):"), spTrainingPxUm,
                new JLabel("Training rescale factor:"),  spTrainingScale,
                new JLabel(""), cbRescaleToTrainingPx
        )));

        cbRequireMicronUnits = new JCheckBox("Require microns calibration");
        spNeuronSegLowerUm = new JSpinner(new SpinnerNumberModel(70.0, 0.0, 10000.0, 1.0));
        spNeuronSegMinUm   = new JSpinner(new SpinnerNumberModel(70.0, 0.0, 10000.0, 1.0));
        p.add(box("Size filtering", grid2(
                new JLabel("Hu seg lower limit (µm):"), spNeuronSegLowerUm,
                new JLabel("Subtype min size (µm):"),   spNeuronSegMinUm,
                new JLabel(""), cbRequireMicronUnits
        )));

        tfGangliaModelFolder = new JTextField(28);
        btnBrowseGangliaModel = new JButton("Pick folder…");
        btnBrowseGangliaModel.addActionListener(e -> chooseFolderName(tfGangliaModelFolder));
        pnlGangliaModelRow = box("Ganglia model (DeepImageJ)",
                row(new JLabel("Folder (under <Fiji>/models):"), tfGangliaModelFolder, btnBrowseGangliaModel)
        );
        p.add(pnlGangliaModelRow);

        spSubtypeProb = new JSpinner(new SpinnerNumberModel(0.50, 0.0, 1.0, 0.05));
        spSubtypeNms  = new JSpinner(new SpinnerNumberModel(0.30, 0.0, 1.0, 0.05));
        spOverlapFrac = new JSpinner(new SpinnerNumberModel(0.40, 0.0, 1.0, 0.05));
        tfHuModelZip = new JTextField(28);
        JButton btnBrowseHu = new JButton("Browse…");
        btnBrowseHu.addActionListener(e -> chooseFile(tfHuModelZip, JFileChooser.FILES_ONLY));

        tfSubtypeModelZip = new JTextField(28);
        JButton btnBrowseSubtype = new JButton("Browse…");
        btnBrowseSubtype.addActionListener(e -> chooseFile(tfSubtypeModelZip, JFileChooser.FILES_ONLY));

// (optional) allow shrinking a bit below preferred width
        tfHuModelZip.setMinimumSize(new Dimension(120, tfHuModelZip.getPreferredSize().height));
        tfSubtypeModelZip.setMinimumSize(new Dimension(120, tfSubtypeModelZip.getPreferredSize().height));

        p.add(box("Subtype model & overlap", grid2(
                new JLabel("Hu StarDist model (.zip):"),       growRow(tfHuModelZip, btnBrowseHu),
                new JLabel("Subtype StarDist model (.zip):"),  growRow(tfSubtypeModelZip, btnBrowseSubtype),
                new JLabel("Subtype prob:"),                   spSubtypeProb,
                new JLabel("Subtype NMS:"),                    spSubtypeNms,
                new JLabel("Hu/marker overlap fraction:"),     spOverlapFrac
        )));

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

    private static JPanel growRow(JTextField tf, JButton btn) {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(0, 0, 0, 0);

        // text field grows
        c.gridx = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        p.add(tf, c);

        // button stays compact
        c.gridx = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE; c.insets = new Insets(0,8,0,0);
        p.add(btn, c);

        return p;
    }

    private void onRun(JButton runBtn) {
        runBtn.setEnabled(false);

        if (!ensureValidOutputDir(tfOutputDir.getText())) {
            runBtn.setEnabled(true);
            return;
        }

        if (markerRows.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please add at least one\n marker in the Markers tab.",
                    "No markers", JOptionPane.ERROR_MESSAGE);
            runBtn.setEnabled(true);
            return;
        }

        // Base validations
        if (!InputValidation.validateImageOrShow(this, tfImagePath.getText()) ||
                !InputValidation.validateZipOrShow(this, tfHuModelZip.getText(), "Hu StarDist model") ||
                !InputValidation.validateZipOrShow(this, tfSubtypeModelZip.getText(), "Subtype StarDist model") ||
                !InputValidation.validateOutputDirOrShow(this, tfOutputDir.getText())) {
            runBtn.setEnabled(true);
            return;
        }

        // Mode-aware ganglia validations
        if (cbGangliaAnalysis.isSelected()) {
            Params.GangliaMode mode = (Params.GangliaMode) cbGangliaMode.getSelectedItem();
            switch (Objects.requireNonNull(mode)) {
                case DEEPIMAGEJ:
                    if (!InputValidation.validateModelsFolderOrShow(this, tfGangliaModelFolder.getText())) {
                        runBtn.setEnabled(true);
                        return;
                    }
                    break;
                case IMPORT_ROI:
                    if (!InputValidation.validateZipOrShow(this, tfGangliaRoiZip.getText(), "Ganglia ROI zip")) {
                        runBtn.setEnabled(true);
                        return;
                    }
                    break;
                default:
                    break;
            }
        }


        SwingWorker<Void,Void> w = new SwingWorker() {
            @Override protected Void doInBackground() {
                try {
                    Params base = buildBaseParams();
                    NeuronsMultiPipeline.MultiParams mp = buildMultiParams(base);
                    new NeuronsMultiPipeline().run(mp);
                } catch (Throwable ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            owner,
                            "Multiplex failed:\n" + ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE));
                }
                return null;
            }
            @Override protected void done() { runBtn.setEnabled(true); }
        };
        w.execute();
    }

    private Params buildBaseParams() {
        Params p = new Params();
        p.imagePath = emptyToNull(tfImagePath.getText());
        p.outputDir = emptyToNull(tfOutputDir.getText());

        p.huChannel = ((Number)spHuChannel.getValue()).intValue();
        p.stardistModelZip = tfHuModelZip.getText();

        p.rescaleToTrainingPx   = cbRescaleToTrainingPx.isSelected();
        p.trainingPixelSizeUm   = ((Number)spTrainingPxUm.getValue()).doubleValue();
        p.trainingRescaleFactor = ((Number)spTrainingScale.getValue()).doubleValue();

        p.saveFlattenedOverlay = true;

        p.doSpatialAnalysis     = cbDoSpatial.isSelected();
        p.spatialCellTypeName   = "Hu";

        p.requireMicronUnits     = cbRequireMicronUnits.isSelected();
        p.neuronSegLowerLimitUm  = ((Number)spNeuronSegLowerUm.getValue()).doubleValue();
        p.neuronSegMinMicron     = ((Number)spNeuronSegMinUm.getValue()).doubleValue();

        p.cellCountsPerGanglia = cbGangliaAnalysis.isSelected();
        p.gangliaMode          = (Params.GangliaMode) cbGangliaMode.getSelectedItem();
        p.gangliaChannel       = ((Number)spGangliaChannel.getValue()).intValue();

        if (p.cellCountsPerGanglia) {
            if (p.gangliaMode == Params.GangliaMode.DEEPIMAGEJ) {
                p.gangliaModelFolder = emptyToNull(tfGangliaModelFolder.getText()); // folder name under <Fiji>/models
                p.customGangliaRoiZip = null;
            } else if (p.gangliaMode == Params.GangliaMode.IMPORT_ROI) {
                p.customGangliaRoiZip = emptyToNull(tfGangliaRoiZip.getText());
                p.gangliaModelFolder = null;
            } else {
                p.gangliaModelFolder = null;
                p.customGangliaRoiZip = null;
            }
        } else {
            p.gangliaModelFolder = null;
            p.customGangliaRoiZip = null;
        }

        return p;
    }

    private NeuronsMultiPipeline.MultiParams buildMultiParams(Params base) {
        NeuronsMultiPipeline.MultiParams mp = new NeuronsMultiPipeline.MultiParams();
        mp.base = base;
        mp.subtypeModelZip = tfSubtypeModelZip.getText();
        mp.multiProb   = ((Number)spSubtypeProb.getValue()).doubleValue();
        mp.multiNms    = ((Number)spSubtypeNms.getValue()).doubleValue();
        mp.overlapFrac = ((Number)spOverlapFrac.getValue()).doubleValue();

        if (markerRows.isEmpty()) {
            throw new IllegalArgumentException("Add at least one marker.");
        }
        for (MarkerRow r : markerRows) {
            mp.markers.add(r.toSpec());
        }
        return mp;
    }

    // ---- helpers ----
    private static String[] splitCsv(String s) {
        if (s == null || s.trim().isEmpty()) return new String[0];
        String[] toks = s.split(",");
        List<String> out = new ArrayList<>();
        for (String t : toks) {
            String u = t.trim();
            if (!u.isEmpty()) out.add(u);
        }
        return out.toArray(new String[0]);
    }
    private static int[] parseIntCsv(String s) {
        String[] toks = splitCsv(s);
        int[] out = new int[toks.length];
        for (int i=0; i<toks.length; i++) out[i] = Integer.parseInt(toks[i]);
        return out;
    }

    private void loadDefaults() {
        tfImagePath = ensure(tfImagePath);
        tfImagePath.setText("/path/to/your/image");
        tfOutputDir.setText("");



        spHuChannel.setValue(3);
        tfHuModelZip.setText(new File(new File(IJ.getDirectory("imagej"), "models"), "2D_enteric_neuron_v4_1.zip").getAbsolutePath());

        tfSubtypeModelZip.setText(new File(new File(IJ.getDirectory("imagej"), "models"), "2D_enteric_neuron_subtype_v4.zip").getAbsolutePath());
        spSubtypeProb.setValue(0.50); spSubtypeNms.setValue(0.30); spOverlapFrac.setValue(0.40);

        addMarkerRow("Marker 1", 1, false, null);
        addMarkerRow("Marker 2", 2,  false, null);

        // Defaults for new ganglia UI
        if (tfGangliaRoiZip == null) tfGangliaRoiZip = new JTextField(28);
        tfGangliaRoiZip.setText("");                        // empty by default

        if (tfGangliaModelFolder == null) tfGangliaModelFolder = new JTextField(28);
        tfGangliaModelFolder.setText("2D_Ganglia_RGB_v3.bioimage.io.model");

        // Make the visibility correct on first render
        updateGangliaVisibility();

        cbRescaleToTrainingPx.setSelected(true);
        spTrainingPxUm.setValue(0.568);
        spTrainingScale.setValue(1.0);

        cbRequireMicronUnits.setSelected(true);
        spNeuronSegLowerUm.setValue(70.0);
        spNeuronSegMinUm.setValue(70.0);

        cbGangliaAnalysis.setSelected(true);
        cbGangliaMode.setSelectedItem(Params.GangliaMode.DEEPIMAGEJ);
        spGangliaChannel.setValue(4);
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

    private void chooseFolderName(JTextField target) {
        File models = new File(IJ.getDirectory("imagej"), "models");
        JFileChooser ch = new JFileChooser(models);
        ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int rv = ch.showOpenDialog(this);
        if (rv == JFileChooser.APPROVE_OPTION) {
            target.setText(ch.getSelectedFile().getName()); // store only folder name
        }
    }

    private void updateGangliaVisibility() {
        boolean runGanglia = cbGangliaAnalysis.isSelected();
        Params.GangliaMode mode = (Params.GangliaMode) cbGangliaMode.getSelectedItem();

        boolean showCustom = runGanglia && mode == Params.GangliaMode.IMPORT_ROI;
        boolean showDIJ    = runGanglia && mode == Params.GangliaMode.DEEPIMAGEJ;

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






    private static void chooseFile(JTextField target, int mode) {
        JFileChooser ch = new JFileChooser();
        ch.setFileSelectionMode(mode);
        if (target.getText() != null && !target.getText().isEmpty()) {
            File start = new File(target.getText());
            ch.setCurrentDirectory(start.isDirectory()? start : start.getParentFile());
        }
        if (ch.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            target.setText(ch.getSelectedFile().getAbsolutePath());
        }
    }
    private static JTextField ensure(JTextField tf) { return tf==null? new JTextField(36) : tf; }
    private static String emptyToNull(String s) { if (s==null) return null; s=s.trim(); return s.isEmpty()? null : s; }

    private java.util.Properties toConfigMulti() {
        java.util.Properties p = new java.util.Properties();

        UI.util.ConfigIO.putStr(p, "workflow", "Multichannel");
        UI.util.ConfigIO.putStr(p, "cfgVersion", "1");

        // reuse some Hu keys (this pane needs them)
        UI.util.ConfigIO.putStr(p,"hu.modelZip", tfHuModelZip.getText());
        UI.util.ConfigIO.putInt(p,"hu.channel", ((Number)spHuChannel.getValue()).intValue());

        // subtype & overlap (namespace "multi.")
        UI.util.ConfigIO.putStr(p, "multi.subtypeModelZip", tfSubtypeModelZip.getText());
        UI.util.ConfigIO.putDbl(p, "multi.subtypeProb", ((Number)spSubtypeProb.getValue()).doubleValue());
        UI.util.ConfigIO.putDbl(p, "multi.subtypeNms",  ((Number)spSubtypeNms.getValue()).doubleValue());
        UI.util.ConfigIO.putDbl(p, "multi.overlapFrac", ((Number)spOverlapFrac.getValue()).doubleValue());

        // shared resc/overlay/spatial
        UI.util.ConfigIO.putBool(p,"rescale.enabled", cbRescaleToTrainingPx.isSelected());
        UI.util.ConfigIO.putDbl (p,"rescale.trainingPxUm", ((Number)spTrainingPxUm.getValue()).doubleValue());
        UI.util.ConfigIO.putDbl (p,"rescale.trainingScale", ((Number)spTrainingScale.getValue()).doubleValue());
        UI.util.ConfigIO.putBool(p,"cal.requireMicrons", cbRequireMicronUnits.isSelected());
        UI.util.ConfigIO.putDbl (p,"cal.neuronSegLowerUm", ((Number)spNeuronSegLowerUm.getValue()).doubleValue());
        UI.util.ConfigIO.putDbl (p,"cal.neuronSegMinUm",   ((Number)spNeuronSegMinUm.getValue()).doubleValue());
        UI.util.ConfigIO.putBool(p,"spatial.enabled", cbDoSpatial.isSelected());

        // ganglia here (Hu-based)
        UI.util.ConfigIO.putBool(p,"ganglia.enabled", cbGangliaAnalysis.isSelected());
        UI.util.ConfigIO.putStr (p,"ganglia.mode", String.valueOf(cbGangliaMode.getSelectedItem()));
        UI.util.ConfigIO.putInt (p,"ganglia.channel", ((Number)spGangliaChannel.getValue()).intValue());
        UI.util.ConfigIO.putStr (p,"ganglia.modelFolder", tfGangliaModelFolder.getText());

        return p;
    }

    private void applyConfigMulti(java.util.Properties p) {
        if (UI.util.ConfigIO.has(p,"hu.modelZip")) tfHuModelZip.setText(UI.util.ConfigIO.getStr(p,"hu.modelZip", tfHuModelZip.getText()));
        if (UI.util.ConfigIO.has(p,"hu.channel"))  spHuChannel.setValue(UI.util.ConfigIO.getInt(p,"hu.channel", ((Number)spHuChannel.getValue()).intValue()));

        if (UI.util.ConfigIO.has(p,"multi.subtypeModelZip")) tfSubtypeModelZip.setText(UI.util.ConfigIO.getStr(p,"multi.subtypeModelZip", tfSubtypeModelZip.getText()));
        if (UI.util.ConfigIO.has(p,"multi.subtypeProb"))     spSubtypeProb.setValue(UI.util.ConfigIO.getDbl(p,"multi.subtypeProb", ((Number)spSubtypeProb.getValue()).doubleValue()));
        if (UI.util.ConfigIO.has(p,"multi.subtypeNms"))      spSubtypeNms.setValue(UI.util.ConfigIO.getDbl(p,"multi.subtypeNms",  ((Number)spSubtypeNms.getValue()).doubleValue()));
        if (UI.util.ConfigIO.has(p,"multi.overlapFrac"))     spOverlapFrac.setValue(UI.util.ConfigIO.getDbl(p,"multi.overlapFrac", ((Number)spOverlapFrac.getValue()).doubleValue()));

        if (UI.util.ConfigIO.has(p,"rescale.enabled")) cbRescaleToTrainingPx.setSelected(UI.util.ConfigIO.getBool(p,"rescale.enabled", cbRescaleToTrainingPx.isSelected()));
        if (UI.util.ConfigIO.has(p,"rescale.trainingPxUm")) spTrainingPxUm.setValue(UI.util.ConfigIO.getDbl(p,"rescale.trainingPxUm", ((Number)spTrainingPxUm.getValue()).doubleValue()));
        if (UI.util.ConfigIO.has(p,"rescale.trainingScale")) spTrainingScale.setValue(UI.util.ConfigIO.getDbl(p,"rescale.trainingScale", ((Number)spTrainingScale.getValue()).doubleValue()));
        if (UI.util.ConfigIO.has(p,"cal.requireMicrons"))    cbRequireMicronUnits.setSelected(UI.util.ConfigIO.getBool(p,"cal.requireMicrons", cbRequireMicronUnits.isSelected()));
        if (UI.util.ConfigIO.has(p,"cal.neuronSegLowerUm"))  spNeuronSegLowerUm.setValue(UI.util.ConfigIO.getDbl(p,"cal.neuronSegLowerUm", ((Number)spNeuronSegLowerUm.getValue()).doubleValue()));
        if (UI.util.ConfigIO.has(p,"cal.neuronSegMinUm"))    spNeuronSegMinUm.setValue(UI.util.ConfigIO.getDbl(p,"cal.neuronSegMinUm", ((Number)spNeuronSegMinUm.getValue()).doubleValue()));
        if (UI.util.ConfigIO.has(p,"spatial.enabled"))       cbDoSpatial.setSelected(UI.util.ConfigIO.getBool(p,"spatial.enabled", cbDoSpatial.isSelected()));

        if (UI.util.ConfigIO.has(p,"ganglia.enabled")) cbGangliaAnalysis.setSelected(UI.util.ConfigIO.getBool(p,"ganglia.enabled", cbGangliaAnalysis.isSelected()));
        if (UI.util.ConfigIO.has(p,"ganglia.mode"))    cbGangliaMode.setSelectedItem(Params.GangliaMode.valueOf(UI.util.ConfigIO.getStr(p,"ganglia.mode", String.valueOf(cbGangliaMode.getSelectedItem()))));
        if (UI.util.ConfigIO.has(p,"ganglia.channel")) spGangliaChannel.setValue(UI.util.ConfigIO.getInt(p,"ganglia.channel", ((Number)spGangliaChannel.getValue()).intValue()));
        if (UI.util.ConfigIO.has(p,"ganglia.modelFolder")) tfGangliaModelFolder.setText(UI.util.ConfigIO.getStr(p,"ganglia.modelFolder", tfGangliaModelFolder.getText()));

        updateGangliaVisibility();
    }

    private static final String EXPECTED_WORKFLOW = "Multichannel";

    private void saveConfigToFile() {
        UI.util.ConfigIO.saveConfig(this, EXPECTED_WORKFLOW, this::toConfigMulti);
    }

    private void loadConfigFromFile() {
        UI.util.ConfigIO.loadConfig(this, EXPECTED_WORKFLOW, this::applyConfigMulti);
    }

    private static void lockSpinner(JSpinner sp) {
        JComponent ed = sp.getEditor();
        if (ed instanceof JSpinner.DefaultEditor) {
            JFormattedTextField tf = ((JSpinner.DefaultEditor) ed).getTextField();
            tf.setEditable(false);
            tf.setFocusable(true);
        }
    }

    private boolean ensureValidOutputDir(String path) {
        path = (path == null) ? "" : path.trim();
        if (path.isEmpty()) return true; // allow default Analysis/<basename>

        File f = new File(path);
        if (f.exists()) {
            if (!f.isDirectory()) {
                JOptionPane.showMessageDialog(this,
                        "Please choose a folder (not a file) for the output location.",
                        "Output location", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            if (!f.canWrite()) {
                JOptionPane.showMessageDialog(this,
                        "No write permission for:\n" + f.getAbsolutePath(),
                        "Output location", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            return true;
        }

        // doesn't exist: offer to create
        int choice = JOptionPane.showConfirmDialog(this,
                "The folder doesn’t exist:\n" + f.getAbsolutePath() + "\nCreate it now?",
                "Create output folder", JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) return false;

        if (!f.mkdirs()) {
            JOptionPane.showMessageDialog(this,
                    "Could not create folder:\n" + f.getAbsolutePath(),
                    "Output location", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }





}
