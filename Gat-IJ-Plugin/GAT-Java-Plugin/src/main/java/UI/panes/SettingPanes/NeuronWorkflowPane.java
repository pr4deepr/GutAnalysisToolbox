package UI.panes.SettingPanes;

import Features.AnalyseWorkflows.NeuronsHuPipeline;
import Features.Core.Params;
import UI.Handlers.Navigator;
import UI.util.ConfigIO;
import UI.util.InputValidation;
import ij.IJ;

import javax.swing.*;
import static UI.util.FormUI.*;
import java.awt.*;
import java.io.File;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.util.Locale;
import java.util.Objects;


/**
 * Neuron Workflow pane with Basic & Advanced tabs.
 * - No modal progress window
 * - Builds Params directly from the UI
 * - Runs NeuronsHuPipeline on a SwingWorker
 */
public class NeuronWorkflowPane extends JPanel {
    public static final String Name = "Neuron Workflow";

    private final Window owner;

    // Basic tab fields
    private JTextField tfImagePath;
    private JButton    btnBrowseImage;
    private JButton    btnPreviewImage;

    private JSpinner   spHuChannel;

    private JTextField tfModelZip;
    private JButton    btnBrowseModel;

    private JPanel pnlGangliaModel;
    private JPanel pnlCustomZipBasic;

    private JButton runBtn;

    private JTextField tfCustomGangliaZip;
    private JButton btnBrowseCustomRoiZip;

    private JCheckBox cbDoSpatial;


    private JCheckBox  cbRescaleToTrainingPx;
    private JSpinner   spTrainingPixelSizeUm;   // double
    private JSpinner   spProbThresh;            // 0..1
    private JSpinner   spNmsThresh;             // 0..1
    private JTextField tfOutputDir;
    private JButton    btnBrowseOutput;

    private JCheckBox  cbGangliaAnalysis;       // enable/disable ganglia block
    private JComboBox<Params.GangliaMode> cbGangliaMode;

    // Advanced tab fields
    private JCheckBox  cbRequireMicronUnits;
    private JSpinner   spNeuronSegLowerLimitUm; // double
    private JSpinner   spNeuronSegMinMicron;    // double (kept for parity)

    private JSpinner   spTrainingRescaleFactor; // double

    private JSpinner   spGangliaChannel;        // int (1-based)
    private JTextField tfGangliaModelFolder;    // model folder name (under <Fiji>/models)
    private JButton    btnBrowseGangliaModelFolder;

    private JSpinner   spHuDilationMicron;      // double
    private JSpinner   spGangliaProbThresh01;   // double 0..1
    private JSpinner   spGangliaMinAreaUm2;     // double
    private JSpinner   spGangliaOpenIterations; // int
    private JCheckBox  cbGangliaInteractiveReview;

    public NeuronWorkflowPane(Navigator navigator, Window owner) {
        super(new BorderLayout(10,10));
        this.owner     = owner;
        setBorder(BorderFactory.createEmptyBorder(16,16,16,16));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Basic", buildBasicTab());
        tabs.addTab("Advanced", buildAdvancedTab());
        add(tabs, BorderLayout.CENTER);

        ToolTipManager.sharedInstance().setInitialDelay(200);
        ToolTipManager.sharedInstance().setDismissDelay(15000);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton runBtn = new JButton("Run Analysis");
        runBtn.addActionListener(e -> onRun(runBtn));
        actions.add(runBtn);
        add(actions, BorderLayout.SOUTH);

        loadDefaults();


        cbGangliaMode.addActionListener(e -> { updateGangliaUI(); updateRunButtonEnabled(); });
        cbGangliaAnalysis.addActionListener(e -> { updateGangliaUI(); updateRunButtonEnabled(); });

        if (tfCustomGangliaZip != null) {
            tfCustomGangliaZip.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { updateRunButtonEnabled(); }
                @Override public void removeUpdate(DocumentEvent e) { updateRunButtonEnabled(); }
                @Override public void changedUpdate(DocumentEvent e) { updateRunButtonEnabled(); }
            });
        }

        updateGangliaUI();
        updateRunButtonEnabled();
    }

    /**
     * Build the "Basic" tab (image, Hu channel, output, ganglia options, spatial, ROI zip chooser).
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
        btnPreviewImage.addActionListener(e -> previewImage());

        String imgHelp =
                "<b>What to select:</b> image to analyse (must be Hu-Stained).<br/>"
                        + "<b>Tip:</b> click <i>Preview</i> one you have selected an image to view it.";

        p.add(boxWithHelp(
                "Input image (.tif, .lif, etc.)",
                row(tfImagePath, btnBrowseImage, btnPreviewImage),
                imgHelp
        ));


        // Hu channel
        spHuChannel = new JSpinner(new SpinnerNumberModel(3, 1, 16, 1));

        String channelHelp =
                "<b>Hu Channel:</b> Select the channel number which corresponds to the hu-stained channel.<br/>";

        p.add(boxWithHelp(
                "Channel Selection",
                leftWrap(grid2(
                        new JLabel("Hu Channel:"),     spHuChannel
                )),
                channelHelp
        ));

        // Output dir
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


        // Ganglia settings
        spGangliaChannel     = new JSpinner(new SpinnerNumberModel(2, 1, 16, 1));
        cbGangliaAnalysis = new JCheckBox("Run ganglia analysis");
        cbGangliaMode = new JComboBox<>(Params.GangliaMode.values());


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



        tfCustomGangliaZip = new JTextField(28);
        btnBrowseCustomRoiZip = new JButton("Browse…");
        btnBrowseCustomRoiZip.addActionListener(e -> chooseFile(tfCustomGangliaZip, JFileChooser.FILES_ONLY));

        // Keep a handle so we can show/hide from updateGangliaUI()
        pnlCustomZipBasic = box("Import ganglia ROIs (.zip)",
                row(tfCustomGangliaZip, btnBrowseCustomRoiZip));
        p.add(pnlCustomZipBasic);

        String spatialHelp =
                "<b>Spatial Analysis:</b> Save a CSV with spatial analysis data of neurons.<br/>";

        p.add(boxWithHelp("Spatial analysis",
                leftWrap(column(cbDoSpatial)),
                spatialHelp
        ));

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
     * Build the "Advanced" tab (rescale, calibration, size filters, DIJ folder, model zips,
     * subtype thresholds, overlap fraction, config load/save).
     */
    private JPanel buildAdvancedTab() {

        JPanel outer = new JPanel(new BorderLayout());

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        cbRequireMicronUnits     = new JCheckBox("Require microns calibration");
        spNeuronSegLowerLimitUm  = new JSpinner(new SpinnerNumberModel(70.0, 0.0, 10000.0, 1.0));
        spNeuronSegMinMicron     = new JSpinner(new SpinnerNumberModel(70.0, 0.0, 10000.0, 1.0));
        spTrainingRescaleFactor  = new JSpinner(new SpinnerNumberModel(1.0, 0.01, 100.0, 0.01));

        p.add(box("Calibration & size filtering", column(
                cbRequireMicronUnits,
                grid2(new JLabel("Neuron seg lower limit (µm):"), spNeuronSegLowerLimitUm,
                        new JLabel("Neuron seg min (µm)       :"),   spNeuronSegMinMicron,
                        new JLabel("Training rescale factor:"), spTrainingRescaleFactor)
        )));

        // StarDist model (.zip)
        tfModelZip = new JTextField(36);
        btnBrowseModel = new JButton("Browse…");
        btnBrowseModel.addActionListener(e -> chooseFile(tfModelZip, JFileChooser.FILES_ONLY));
        p.add(box("StarDist model (.zip)", row(tfModelZip, btnBrowseModel)));

        // StarDist thresholds
        spProbThresh = new JSpinner(new SpinnerNumberModel(0.5, 0.0, 1.0, 0.05));
        spNmsThresh  = new JSpinner(new SpinnerNumberModel(0.3, 0.0, 1.0, 0.05));
        p.add(box("StarDist thresholds", grid2(
                new JLabel("Probability:"), spProbThresh,
                new JLabel("NMS:"),         spNmsThresh
        )));


        // Rescale + training px size
        cbRescaleToTrainingPx = new JCheckBox("Rescale to training pixel size");
        spTrainingPixelSizeUm = new JSpinner(new SpinnerNumberModel(0.568, 0.01, 100.0, 0.001));
        p.add(box("Rescaling", column(cbRescaleToTrainingPx, row(new JLabel("Training pixel size (µm):"), spTrainingPixelSizeUm))));


        tfGangliaModelFolder = new JTextField(28);
        btnBrowseGangliaModelFolder = new JButton("Browse…");
        btnBrowseGangliaModelFolder.addActionListener(e -> chooseFolderName(tfGangliaModelFolder));

        pnlGangliaModel = box("Ganglia model",
                row(new JLabel(""), tfGangliaModelFolder, btnBrowseGangliaModelFolder));
        p.add(pnlGangliaModel);

        spHuDilationMicron       = new JSpinner(new SpinnerNumberModel(12.0, 0.0, 1000.0, 0.5));
        spGangliaProbThresh01    = new JSpinner(new SpinnerNumberModel(0.35, 0.0, 1.0, 0.01));
        spGangliaMinAreaUm2      = new JSpinner(new SpinnerNumberModel(200.0, 0.0, 100000.0, 5.0));
        spGangliaOpenIterations  = new JSpinner(new SpinnerNumberModel(3, 0, 50, 1));
        cbGangliaInteractiveReview = new JCheckBox("Interactive review overlay");

        p.add(box("Ganglia post-processing", grid2(
                new JLabel("Hu dilation (µm):"),      spHuDilationMicron,
                new JLabel("Prob threshold (0–1):"),  spGangliaProbThresh01,
                new JLabel("Min area (µm²):"),        spGangliaMinAreaUm2,
                new JLabel("Open iterations:"),       spGangliaOpenIterations
        )));
        p.add(box("Review", cbGangliaInteractiveReview));


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
     * Run handler:
     * 1) Validate output dir and required files
     * 2) Validate ganglia resources based on selected mode
     * 3) Launch the pipeline in a background SwingWorker
     *
     * UI is re-enabled when background work completes.
     */
    private void onRun(JButton runBtn) {
        runBtn.setEnabled(false);

        Params.GangliaMode mode = (Params.GangliaMode) cbGangliaMode.getSelectedItem();

        boolean ok =
                InputValidation.validateImageOrShow(this, tfImagePath.getText()) &&
                        InputValidation.validateZipOrShow(this, tfModelZip.getText(), "StarDist model") &&
                        InputValidation.validateOutputDirOrShow(this, tfOutputDir.getText());

        if (ok && cbGangliaAnalysis.isSelected()) {
            switch (Objects.requireNonNull(mode)) {
                case DEEPIMAGEJ:
                    ok = InputValidation.validateModelsFolderOrShow(this, tfGangliaModelFolder.getText());
                    break;
                case IMPORT_ROI:
                    ok = InputValidation.validateZipOrShow(this, tfCustomGangliaZip.getText(), "Ganglia ROI zip");
                    break;
                case DEFINE_FROM_HU:
                case MANUAL:
                    break;
            }
        }

        if (!ok) {
            runBtn.setEnabled(true);
            return;
        }

        SwingWorker<Void,Void> worker = new SwingWorker() {
            @Override protected Void doInBackground() {
                try {
                    Params p = buildParamsFromUI();
                    if (p.gangliaMode == Params.GangliaMode.IMPORT_ROI) {
                        p.customGangliaRoiZip = emptyToNull(tfCustomGangliaZip.getText());
                    } else {
                        p.customGangliaRoiZip = null;
                    }
                    new NeuronsHuPipeline().run(p,false);
                } catch (Throwable ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            owner,
                            "Analysis failed:\n" + ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                    ));
                }
                return null;
            }
            @Override protected void done() { runBtn.setEnabled(true); }
        };
        worker.execute();
    }

    private Params buildParamsFromUI() {
        Params p = new Params();

        p.imagePath = emptyToNull(tfImagePath.getText());
        p.outputDir = emptyToNull(tfOutputDir.getText());

        p.doSpatialAnalysis     = cbDoSpatial.isSelected();
        p.spatialCellTypeName   = "Hu";

        p.huChannel = (int) spHuChannel.getValue();

        p.stardistModelZip = tfModelZip.getText();

        p.rescaleToTrainingPx  = cbRescaleToTrainingPx.isSelected();
        p.trainingPixelSizeUm  = ((Number) spTrainingPixelSizeUm.getValue()).doubleValue();
        p.trainingRescaleFactor= ((Number) spTrainingRescaleFactor.getValue()).doubleValue();

        p.probThresh = ((Number) spProbThresh.getValue()).doubleValue();
        p.nmsThresh  = ((Number) spNmsThresh.getValue()).doubleValue();

        p.saveFlattenedOverlay = true;

        p.requireMicronUnits = cbRequireMicronUnits.isSelected();
        p.neuronSegLowerLimitUm = ((Number) spNeuronSegLowerLimitUm.getValue()).doubleValue();
        p.neuronSegMinMicron    = ((Number) spNeuronSegMinMicron.getValue()).doubleValue();

        p.cellCountsPerGanglia = cbGangliaAnalysis.isSelected();
        p.gangliaMode = (Params.GangliaMode) cbGangliaMode.getSelectedItem();
        p.gangliaChannel = ((Number) spGangliaChannel.getValue()).intValue();
        p.gangliaModelFolder = tfGangliaModelFolder.getText();
        p.huDilationMicron = ((Number) spHuDilationMicron.getValue()).doubleValue();
        p.gangliaProbThresh01 = ((Number) spGangliaProbThresh01.getValue()).doubleValue();
        p.gangliaMinAreaUm2   = ((Number) spGangliaMinAreaUm2.getValue()).doubleValue();
        p.gangliaOpenIterations = ((Number) spGangliaOpenIterations.getValue()).intValue();
        p.gangliaInteractiveReview = cbGangliaInteractiveReview.isSelected();
        p.uiAnchor = SwingUtilities.getWindowAncestor(this);

        return p;
    }

    private void previewImage() {
        final String path = (tfImagePath.getText() == null) ? "" : tfImagePath.getText().trim();
        btnPreviewImage.setEnabled(false);

        new SwingWorker<Void,Void>() {
            @Override protected Void doInBackground() {
                try {
                    if (path.isEmpty()) {

                        IJ.open();
                    } else {

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
        // Fill with your known-good defaults (same as your working run)
        if (tfCustomGangliaZip != null) tfCustomGangliaZip.setText("");
        tfImagePath.setText("/path/to/image");
        spHuChannel.setValue(1);

        String modelZip = new File(new File(IJ.getDirectory("imagej"), "models"), "2D_enteric_neuron_v4_1.zip").getAbsolutePath();
        tfModelZip.setText(modelZip);

        cbRescaleToTrainingPx.setSelected(true);
        spTrainingPixelSizeUm.setValue(0.568);
        spProbThresh.setValue(0.5);
        spNmsThresh.setValue(0.3);



        cbRequireMicronUnits.setSelected(true);
        spNeuronSegLowerLimitUm.setValue(70.0);
        spNeuronSegMinMicron.setValue(70.0);
        spTrainingRescaleFactor.setValue(1.0);

        cbGangliaAnalysis.setSelected(true);
        cbGangliaMode.setSelectedItem(Params.GangliaMode.DEEPIMAGEJ);
        spGangliaChannel.setValue(1);
        tfGangliaModelFolder.setText("2D_Ganglia_RGB_v3.bioimage.io.model");

        spHuDilationMicron.setValue(12.0);
        spGangliaProbThresh01.setValue(0.35);
        spGangliaMinAreaUm2.setValue(200.0);
        spGangliaOpenIterations.setValue(3);
        cbGangliaInteractiveReview.setSelected(true);
    }

    // ---------------- Small helpers ----------------

    private static String emptyToNull(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }



    private static JPanel column(JComponent... comps) {
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        for (JComponent c : comps) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            col.add(c);
            col.add(Box.createVerticalStrut(6));
        }
        return col;
    }

    private static JPanel row(JComponent... comps) {
        JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        for (JComponent c : comps) r.add(c);
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        return r;
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

    /**
     * Lets you pick a folder name under <Fiji>/models (for DIJ model folder).
     * This writes only the folder name into the field, matching your Params usage.
     */
    private void chooseFolderName(JTextField target) {
        File models = new File(IJ.getDirectory("imagej"), "models");
        JFileChooser ch = new JFileChooser(models);
        ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int rv = ch.showOpenDialog(this);
        if (rv == JFileChooser.APPROVE_OPTION) {
            File sel = ch.getSelectedFile();
            // store only folder name relative to models dir (parity with your current Params)
            String name = sel.getName();
            target.setText(name);
        }
    }

    private void updateGangliaUI() {
        boolean gangliaOn = cbGangliaAnalysis.isSelected();
        Params.GangliaMode mode = (Params.GangliaMode) cbGangliaMode.getSelectedItem();

        if (pnlGangliaModel != null) {
            pnlGangliaModel.setVisible(gangliaOn && mode == Params.GangliaMode.DEEPIMAGEJ);
        }
        if (pnlCustomZipBasic != null) {
            pnlCustomZipBasic.setVisible(gangliaOn && mode == Params.GangliaMode.IMPORT_ROI);
        }
        revalidate();
        repaint();
    }

    private void updateRunButtonEnabled() {
        if (runBtn == null) return;

        boolean enable = true;

        if (cbGangliaAnalysis.isSelected() &&
                cbGangliaMode.getSelectedItem() == Params.GangliaMode.IMPORT_ROI) {

            String path = (tfCustomGangliaZip != null) ? tfCustomGangliaZip.getText().trim() : "";
            enable = isValidZipPath(path); // silent gating for the button
        }

        runBtn.setEnabled(enable);
    }

    private static boolean isValidZipPath(String path) {
        if (path == null || path.isEmpty()) return false;
        File f = new File(path);
        String name = f.getName().toLowerCase(Locale.ROOT);
        return f.isFile() && name.endsWith(".zip");
    }

    // ---- Config mapping (Hu pane) ----
    private java.util.Properties toConfigHu() {
        java.util.Properties p = new java.util.Properties();
        UI.util.ConfigIO.putStr(p, "workflow", "HuWorkflow");
        UI.util.ConfigIO.putStr(p, "cfgVersion", "1");
        UI.util.ConfigIO.putStr(p, "hu.modelZip", tfModelZip.getText());
        UI.util.ConfigIO.putInt(p, "hu.channel", ((Number)spHuChannel.getValue()).intValue());
        UI.util.ConfigIO.putDbl(p, "hu.prob", ((Number)spProbThresh.getValue()).doubleValue());
        UI.util.ConfigIO.putDbl(p, "hu.nms",  ((Number)spNmsThresh.getValue()).doubleValue());

        UI.util.ConfigIO.putBool(p, "rescale.enabled", cbRescaleToTrainingPx.isSelected());
        UI.util.ConfigIO.putDbl(p,  "rescale.trainingPxUm", ((Number)spTrainingPixelSizeUm.getValue()).doubleValue());
        UI.util.ConfigIO.putDbl(p,  "rescale.trainingScale", ((Number)spTrainingRescaleFactor.getValue()).doubleValue());


        UI.util.ConfigIO.putBool(p, "cal.requireMicrons", cbRequireMicronUnits.isSelected());
        UI.util.ConfigIO.putDbl(p,  "cal.neuronSegLowerUm", ((Number)spNeuronSegLowerLimitUm.getValue()).doubleValue());
        UI.util.ConfigIO.putDbl(p,  "cal.neuronSegMinUm",   ((Number)spNeuronSegMinMicron.getValue()).doubleValue());

        UI.util.ConfigIO.putBool(p, "ganglia.enabled", cbGangliaAnalysis.isSelected());
        UI.util.ConfigIO.putStr (p, "ganglia.mode", String.valueOf(cbGangliaMode.getSelectedItem()));
        UI.util.ConfigIO.putInt (p, "ganglia.channel", ((Number)spGangliaChannel.getValue()).intValue());
        UI.util.ConfigIO.putStr (p, "ganglia.modelFolder", tfGangliaModelFolder.getText());
        UI.util.ConfigIO.putDbl (p, "ganglia.huDilationUm", ((Number)spHuDilationMicron.getValue()).doubleValue());
        UI.util.ConfigIO.putDbl (p, "ganglia.prob01", ((Number)spGangliaProbThresh01.getValue()).doubleValue());
        UI.util.ConfigIO.putDbl (p, "ganglia.minAreaUm2", ((Number)spGangliaMinAreaUm2.getValue()).doubleValue());
        UI.util.ConfigIO.putInt (p, "ganglia.openIters", ((Number)spGangliaOpenIterations.getValue()).intValue());
        UI.util.ConfigIO.putBool(p, "ganglia.interactiveReview", cbGangliaInteractiveReview.isSelected());

        UI.util.ConfigIO.putBool(p, "spatial.enabled", cbDoSpatial.isSelected());
        return p;
    }

    private void applyConfigHu(java.util.Properties p) {
        // Only set if a key exists (keeps it tolerant across panes)
        if (UI.util.ConfigIO.has(p, "hu.modelZip")) tfModelZip.setText(UI.util.ConfigIO.getStr(p, "hu.modelZip", tfModelZip.getText()));
        if (UI.util.ConfigIO.has(p, "hu.channel"))  spHuChannel.setValue(UI.util.ConfigIO.getInt(p, "hu.channel", ((Number)spHuChannel.getValue()).intValue()));
        if (UI.util.ConfigIO.has(p, "hu.prob"))     spProbThresh.setValue(UI.util.ConfigIO.getDbl(p, "hu.prob", ((Number)spProbThresh.getValue()).doubleValue()));
        if (UI.util.ConfigIO.has(p, "hu.nms"))      spNmsThresh.setValue(UI.util.ConfigIO.getDbl(p, "hu.nms",  ((Number)spNmsThresh.getValue()).doubleValue()));

        if (UI.util.ConfigIO.has(p, "rescale.enabled")) cbRescaleToTrainingPx.setSelected(UI.util.ConfigIO.getBool(p,"rescale.enabled", cbRescaleToTrainingPx.isSelected()));
        if (UI.util.ConfigIO.has(p, "rescale.trainingPxUm")) spTrainingPixelSizeUm.setValue(UI.util.ConfigIO.getDbl(p,"rescale.trainingPxUm", ((Number)spTrainingPixelSizeUm.getValue()).doubleValue()));
        if (UI.util.ConfigIO.has(p, "rescale.trainingScale")) spTrainingRescaleFactor.setValue(UI.util.ConfigIO.getDbl(p,"rescale.trainingScale", ((Number)spTrainingRescaleFactor.getValue()).doubleValue()));



        if (UI.util.ConfigIO.has(p, "cal.requireMicrons")) cbRequireMicronUnits.setSelected(UI.util.ConfigIO.getBool(p,"cal.requireMicrons", cbRequireMicronUnits.isSelected()));
        if (UI.util.ConfigIO.has(p, "cal.neuronSegLowerUm")) spNeuronSegLowerLimitUm.setValue(UI.util.ConfigIO.getDbl(p,"cal.neuronSegLowerUm", ((Number)spNeuronSegLowerLimitUm.getValue()).doubleValue()));
        if (UI.util.ConfigIO.has(p, "cal.neuronSegMinUm"))   spNeuronSegMinMicron.setValue(UI.util.ConfigIO.getDbl(p,"cal.neuronSegMinUm",   ((Number)spNeuronSegMinMicron.getValue()).doubleValue()));

        if (UI.util.ConfigIO.has(p, "ganglia.enabled")) cbGangliaAnalysis.setSelected(UI.util.ConfigIO.getBool(p,"ganglia.enabled", cbGangliaAnalysis.isSelected()));
        if (UI.util.ConfigIO.has(p, "ganglia.mode"))    cbGangliaMode.setSelectedItem(Params.GangliaMode.valueOf(UI.util.ConfigIO.getStr(p,"ganglia.mode", String.valueOf(cbGangliaMode.getSelectedItem()))));
        if (UI.util.ConfigIO.has(p, "ganglia.channel")) spGangliaChannel.setValue(UI.util.ConfigIO.getInt(p,"ganglia.channel", ((Number)spGangliaChannel.getValue()).intValue()));
        if (UI.util.ConfigIO.has(p, "ganglia.modelFolder")) tfGangliaModelFolder.setText(UI.util.ConfigIO.getStr(p,"ganglia.modelFolder", tfGangliaModelFolder.getText()));
        if (UI.util.ConfigIO.has(p, "ganglia.huDilationUm")) spHuDilationMicron.setValue(UI.util.ConfigIO.getDbl(p,"ganglia.huDilationUm", ((Number)spHuDilationMicron.getValue()).doubleValue()));
        if (UI.util.ConfigIO.has(p, "ganglia.prob01"))       spGangliaProbThresh01.setValue(UI.util.ConfigIO.getDbl(p,"ganglia.prob01", ((Number)spGangliaProbThresh01.getValue()).doubleValue()));
        if (UI.util.ConfigIO.has(p, "ganglia.minAreaUm2"))   spGangliaMinAreaUm2.setValue(UI.util.ConfigIO.getDbl(p,"ganglia.minAreaUm2", ((Number)spGangliaMinAreaUm2.getValue()).doubleValue()));
        if (UI.util.ConfigIO.has(p, "ganglia.openIters"))    spGangliaOpenIterations.setValue(UI.util.ConfigIO.getInt(p,"ganglia.openIters", ((Number)spGangliaOpenIterations.getValue()).intValue()));
        if (UI.util.ConfigIO.has(p, "ganglia.interactiveReview")) cbGangliaInteractiveReview.setSelected(UI.util.ConfigIO.getBool(p,"ganglia.interactiveReview", cbGangliaInteractiveReview.isSelected()));

        if (UI.util.ConfigIO.has(p, "spatial.enabled")) cbDoSpatial.setSelected(UI.util.ConfigIO.getBool(p,"spatial.enabled", cbDoSpatial.isSelected()));

        updateGangliaUI(); // keep visibility consistent after loading
    }

    private static final String EXPECTED_WORKFLOW = "HuWorkflow";

    private void saveConfigToFile() {
        ConfigIO.saveConfig(this, EXPECTED_WORKFLOW, this::toConfigHu);
    }

    private void loadConfigFromFile() {
        ConfigIO.loadConfig(this, EXPECTED_WORKFLOW, this::applyConfigHu);
    }





}
