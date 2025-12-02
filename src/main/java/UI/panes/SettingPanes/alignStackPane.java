package UI.panes.SettingPanes;

import Features.Core.Params;
import Features.Tools.AlignStack;
import Features.Tools.AlignStackBatch;
import UI.Handlers.Navigator;
import UI.panes.WorkflowDashboards.AlignStackDashboard;
import UI.util.InputValidation;
import ij.IJ;
import ij.ImagePlus;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Align Stack pane
 * Provides UI for configuring alignment of image stacks.
 * Supports single stack and batch alignment with SIFT, Template Matching, or StackReg (single only).
 */
public class alignStackPane extends JPanel {
    public static final String Name = "Align Stack";

    private final Window owner;

    // --- UI components ---
    private JTextField tfImagePath;
    private JTextField tfInputDir;
    private JButton btnBrowseImage;
    private JButton btnBrowseInput;
    private JButton btnPreviewImage;

    private JSpinner spReferenceFrame;
    private JSpinner spReferenceFrameBatch;
    private JCheckBox cbUseSIFT;
    private JCheckBox cbUseSIFTBatch;

    private JCheckBox cbUseTemplateMatching;
    private JRadioButton cbUseTemplateMatchingBatch;
    private JRadioButton cbUseStackRegBatch;

    private JTextField tfOutputDir;
    private JTextField tfOutputDirBatch;
    private JTextField tfFileExt;
    private JButton btnBrowseOutput;
    private JButton btnBrowseOutputBatch;

    private JCheckBox cbSaveAlignedStack;
    private JCheckBox cbSaveAlignedStackBatch;

    private JButton runBtn;

    private boolean warnTemplatePlugin = true;

    // Dashboard to display results
    private AlignStackDashboard alignStackDashboard;
    private static final String PLUGIN_INSTALLATION_URL =
        "https://sites.imagej.net/Template_Matching/";

    /**alignStackPane creates a GUI panel for aligning stack settings. */
    public alignStackPane(Navigator navigator, Window owner) {
        super(new BorderLayout(10,10));
        this.owner = owner;
        setBorder(BorderFactory.createEmptyBorder(16,16,16,16));

        // Create tabs for single and batch alignment
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Align Stack (Single) Settings", buildSettingsTab());
        tabs.addTab("Align Stack (Batch) Settings", buildBatchTab());
        add(tabs, BorderLayout.CENTER);

        // Run button at bottom
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        runBtn = new JButton("Run Alignment");
        runBtn.addActionListener(e -> onRun(runBtn, tabs));
        actions.add(runBtn);
        add(actions, BorderLayout.SOUTH);

        loadDefaults();
    }

    /** Build the batch alignment tab UI */
    private JPanel buildBatchTab() {
        JPanel outer = new JPanel(new BorderLayout());
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        // Input folder selection
        tfInputDir = new JTextField(36);
        btnBrowseInput = new JButton("Browse…");
        btnBrowseInput.addActionListener(e -> chooseFile(tfInputDir, JFileChooser.DIRECTORIES_ONLY));
        p.add(box("Input folder with stacks", row(tfInputDir, btnBrowseInput)));

        // Reference frame for alignment
        spReferenceFrameBatch = new JSpinner(new SpinnerNumberModel(1, 1, 1000, 1));
        p.add(box("Reference frame", spReferenceFrameBatch));

        // Alignment method options
        cbUseSIFTBatch = new JCheckBox("Use SIFT alignment if tissue moves significantly", true);
        JRadioButton rbTemplateMatching = new JRadioButton("Template Matching", true);
        JRadioButton rbStackReg = new JRadioButton("StackReg (unsupported in batch)");
        rbStackReg.setEnabled(false); // Disable StackReg since not implemented in batch

        ButtonGroup xyGroup = new ButtonGroup();
        xyGroup.add(rbTemplateMatching);
        xyGroup.add(rbStackReg);

        cbUseTemplateMatchingBatch = rbTemplateMatching;
        cbUseStackRegBatch = rbStackReg;

        p.add(box("Alignment method", column(cbUseSIFTBatch, cbUseTemplateMatchingBatch, cbUseStackRegBatch)));

        // File extension filter
        tfFileExt = new JTextField(".tif", 6);
        p.add(box("File extension", tfFileExt));

        // Output directory and save option
        tfOutputDirBatch = new JTextField(36);
        btnBrowseOutputBatch = new JButton("Browse…");
        btnBrowseOutputBatch.addActionListener(e -> chooseFile(tfOutputDirBatch, JFileChooser.DIRECTORIES_ONLY));
        cbSaveAlignedStackBatch = new JCheckBox("Save aligned stacks (recommended)", true);
        p.add(box("Output location", column(row(tfOutputDirBatch, btnBrowseOutputBatch), cbSaveAlignedStackBatch)));

        JScrollPane scroll = new JScrollPane(p);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        outer.add(scroll, BorderLayout.CENTER);
        return outer;
    }

    /** Build the single stack alignment tab UI */
    private JPanel buildSettingsTab() {
        JPanel outer = new JPanel(new BorderLayout());
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        // Input stack selection
        tfImagePath = new JTextField(36);
        btnBrowseImage = new JButton("Browse…");
        btnBrowseImage.addActionListener(e -> chooseFile(tfImagePath, JFileChooser.FILES_ONLY));
        btnPreviewImage = new JButton("Preview");
        btnPreviewImage.addActionListener(e -> previewImage());
        p.add(box("Input stack (.tif)", row(tfImagePath, btnBrowseImage, btnPreviewImage)));

        // Reference frame
        spReferenceFrame = new JSpinner(new SpinnerNumberModel(1, 1, 1000, 1));
        p.add(box("Reference frame", spReferenceFrame));

        // Alignment method options
        cbUseSIFT = new JCheckBox("Use SIFT alignment if tissue moves significantly");
        cbUseTemplateMatching = new JCheckBox("Use Template Matching for XY movement");
        p.add(box("Alignment method", column(cbUseSIFT, cbUseTemplateMatching)));

        // Output directory and save option
        tfOutputDir = new JTextField(36);
        btnBrowseOutput = new JButton("Browse…");
        btnBrowseOutput.addActionListener(e -> chooseFile(tfOutputDir, JFileChooser.DIRECTORIES_ONLY));
        cbSaveAlignedStack = new JCheckBox("Save aligned stack (recommended)");
        p.add(box("Output location", column(row(tfOutputDir, btnBrowseOutput), cbSaveAlignedStack)));

        JScrollPane scroll = new JScrollPane(p);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        outer.add(scroll, BorderLayout.CENTER);
        return outer;
    }

    /** Trigger alignment when run button is pressed */
    private void onRun(JButton runBtn, JTabbedPane tabs) {
        int selected = tabs.getSelectedIndex(); // 0 = single, 1 = batch
        boolean ok = (selected == 0)
                ? InputValidation.validateImageOrShow(this, tfImagePath.getText())
                : validateDirectoryOrShow(this, tfInputDir.getText());

        if (!ok) {
            runBtn.setEnabled(true);
            return;
        }

        // --- Warn user if Template Matching checkbox selected ---
        if (selected == 0 && cbUseTemplateMatching.isSelected() && warnTemplatePlugin) {
            JCheckBox ignoreBox = new JCheckBox("Don't warn me again");
            Object[] params = {
                "Align Stack XY alignment requires the Template Matching plugin.\n" +
                "Please ensure it's installed.\nURL: " + PLUGIN_INSTALLATION_URL, ignoreBox
            };
            int response = JOptionPane.showConfirmDialog(
                    this, params, "Plugin Required", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE
            );

            warnTemplatePlugin = !ignoreBox.isSelected(); // update flag

            if (response != JOptionPane.OK_OPTION) {
                runBtn.setEnabled(true);
                return;
            }
        }

        // --- Create loading overlay ---
        JDialog loadingDialog = createLoadingDialog(owner, "Running alignment, please wait...");
        runBtn.setEnabled(false);

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            private ImagePlus alignedImage;
            private File csvFile;

            @Override
            protected Void doInBackground() {
                try {
                    Params params = buildParamsFromUI(selected);

                    if (selected == 0) {
                        AlignStack aligner = new AlignStack();
                        AlignStack.AlignResult result = aligner.run(params);

                        csvFile = result.resultCSV;
                        if (params.saveAlignedStack) {
                            String outFile = params.outputDir + File.separator +
                                    new File(params.imagePath).getName().replace(".tif", "_aligned.tif");
                            alignedImage = IJ.openImage(outFile);
                        } else {
                            alignedImage = result.alignedStack;
                        }
                    } else {
                        runBatchAlignment(params);
                    }
                } catch (Throwable ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            owner,
                            "Alignment failed:\n" + ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                    ));
                }
                return null;
            }

            @Override
            protected void done() {
                loadingDialog.dispose();
                runBtn.setEnabled(true);

                if (selected == 0 && alignedImage != null) {
                    // Remove old dashboard tab if present
                    int existingIdx = tabs.indexOfTab("Alignment Dashboard");
                    if (existingIdx >= 0) tabs.removeTabAt(existingIdx);

                    alignStackDashboard = new AlignStackDashboard();
                    tabs.addTab("Alignment Dashboard", alignStackDashboard);
                    tabs.setSelectedComponent(alignStackDashboard);

                    alignStackDashboard.addAlignedStackWithResults(
                            alignedImage,
                            (csvFile != null && csvFile.exists()) ? csvFile : null
                    );
                }
            }
        };

        worker.execute();
        loadingDialog.setVisible(true);
    }

    /** Construct Params object from UI fields */
    private Params buildParamsFromUI(int selectedTab) {
        Params p = new Params();

        if (selectedTab == 0) {
            // Single stack settings
            p.imagePath = tfImagePath.getText();
            p.outputDir = tfOutputDir.getText();
            p.referenceFrame = ((Number) spReferenceFrame.getValue()).intValue();
            p.useSIFT = cbUseSIFT.isSelected();
            p.useTemplateMatching = cbUseTemplateMatching.isSelected();
            p.saveAlignedStack = cbSaveAlignedStack.isSelected();
            p.uiAnchor = SwingUtilities.getWindowAncestor(this);
        } else {
            // Batch settings
            p.inputDir = tfInputDir.getText();
            p.outputDir = tfOutputDirBatch.getText();
            p.referenceFrame = ((Number) spReferenceFrameBatch.getValue()).intValue();
            p.useSIFT = cbUseSIFTBatch.isSelected();
            p.useTemplateMatching = cbUseTemplateMatchingBatch.isSelected();
            p.useStackReg = cbUseStackRegBatch.isSelected();
            p.fileExt = tfFileExt.getText();
        }
        return p;
    }

    /** Run batch alignment for multiple stacks */
    private void runBatchAlignment(Params params) throws Exception {
        if (!validateDirectoryOrShow(this, tfInputDir.getText())) return;

        AlignStackBatch.runBatch(params);
    }

    // --- UI utility functions ---
    private static JPanel box(String title, JComponent inner) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(inner, BorderLayout.CENTER);
        return panel;
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

    /** File chooser utility for browsing files or directories */
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

    /** Preview the selected image stack in ImageJ */
    private void previewImage() {
        final String path = (tfImagePath.getText() == null) ? "" : tfImagePath.getText().trim();
        btnPreviewImage.setEnabled(false);
        new SwingWorker<Void,Void>() {
            @Override protected Void doInBackground() {
                try {
                    if (path.isEmpty()) IJ.open(); else IJ.open(path);
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
            @Override protected void done() { btnPreviewImage.setEnabled(true); }
        }.execute();
    }

    /** Load default UI values */
    private void loadDefaults() {
        tfImagePath.setText("/path/to/stack.tif");
        spReferenceFrame.setValue(1);
        cbUseSIFT.setSelected(false);
        cbSaveAlignedStack.setSelected(true);
        tfOutputDir.setText(System.getProperty("user.home"));
    }

    /** Validate a directory path exists */
    public static boolean validateDirectoryOrShow(JComponent parent, String path) {
        File f = new File(path);
        if (!f.exists() || !f.isDirectory()) {
            JOptionPane.showMessageDialog(parent,
                    "Directory does not exist:\n" + path,
                    "Invalid directory",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    /** Small modal dialog with an indeterminate progress bar */
    private static JDialog createLoadingDialog(Window owner, String message) {
        JDialog dialog = new JDialog(owner, "Please wait", Dialog.ModalityType.MODELESS);
        JPanel panel = new JPanel(new BorderLayout(10,10));
        panel.setBorder(BorderFactory.createEmptyBorder(15,15,15,15));

        JLabel lbl = new JLabel(message);
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);

        panel.add(lbl, BorderLayout.NORTH);
        panel.add(bar, BorderLayout.CENTER);

        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setResizable(false);
        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        return dialog;
    }
}
