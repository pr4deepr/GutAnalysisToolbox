package UI.panes.Tools.dialogs;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;

/**
 * Modal dialog for configuring a rescaling-factor sweep of neuron ROIs.
 * <p>
 * The "rescaling factor" here is a multiplicative factor applied to
 * detected neuron outlines (e.g. StarDist polygons). By sweeping
 * rescaleMin → rescaleMax in fixed steps, you can estimate which scaling
 * best fits the true neuron boundary under your staining / imaging
 * conditions.
 * </p>
 *
 * <p>This dialog also lets the user fine-tune the fixed segmentation
 * thresholds that apply across the whole sweep:</p>
 *
 * <ul>
 *   <li>Probability (StarDist object acceptance threshold)</li>
 *   <li>Overlap / NMS threshold</li>
 * </ul>
 *
 * <p>Just like {@link ProbabilityDialog}, this dialog supports two modes:</p>
 * <ul>
 *   <li><b>NEURON</b>: Work with Hu+ neurons directly.</li>
 *   <li><b>SUBTYPE</b>: Work with a specific neuron subtype, requiring a
 *       StarDist model ZIP and a specific channel.</li>
 * </ul>
 *
 * <p>The user selects:</p>
 * <ul>
 *   <li>The image to analys
 *   e</li>
 *   <li>Mode and channel</li>
 *   <li>Rescaling sweep parameters (min / max / step)</li>
 *   <li>Fixed probability and overlap thresholds</li>
 *   <li>(If SUBTYPE mode) the StarDist model ZIP</li>
 *   <li>An output directory for previews/CSVs</li>
 * </ul>
 *
 * <p>When the user hits OK, inputs are validated and captured into a
 * {@link RescaleHuDialog.Config} object. If validation fails, an
 * explanatory warning is shown and the dialog stays open. Use
 * {@link #showAndGet()} to present the dialog and retrieve the config.</p>
 */

public final class RescaleHuDialog extends JDialog {

    public enum Mode { NEURON, SUBTYPE }

    public static final class Config {
        public File   imageFile;            // nullable if imageAlreadyOpen

        public Mode   mode = Mode.NEURON;   // NEW
        public int    channel = 3;          // 1-based (Hu or subtype channel)

        public double rescaleMin = 1.0;
        public double rescaleMax = 1.5;
        public double rescaleStep = 0.25;

        public double prob = 0.50;          // used as fixed threshold during sweep
        public double overlap = 0.30;

        public File   modelZip;             // REQUIRED when mode == SUBTYPE (StarDist zip)
        public File   outDir;               // NEW: where previews/CSV are saved

    }

    private Config result;

    // Controls
    private final JTextField imgTf = new JTextField();
    private final JButton    browseImg = new JButton("Browse");

    private final JRadioButton rbNeuron  = new JRadioButton("Neuron Segmentation", true);
    private final JRadioButton rbSubtype = new JRadioButton("Neuron subtype segmentation", false);
    private final JSpinner     chSp      = new JSpinner(new SpinnerNumberModel(3, 1, 99, 1));

    private final JSpinner   minSp  = new JSpinner(new SpinnerNumberModel(1.0, 0.05, 5.0, 0.05));
    private final JSpinner   maxSp  = new JSpinner(new SpinnerNumberModel(1.5, 0.05, 5.0, 0.05));
    private final JSpinner   stepSp = new JSpinner(new SpinnerNumberModel(0.25, 0.01, 2.0, 0.01));

    private final JSlider    probSl = new JSlider(10, 99, 50);  // 0.10..0.99
    private final JLabel     probVal = new JLabel("0.50");
    private final JSlider    nmsSl  = new JSlider(0, 100, 30);  // 0.00..1.00
    private final JLabel     nmsVal = new JLabel("0.30");

    // Model (only for SUBTYPE)
    private final JTextField modelTf = new JTextField();
    private final JButton    browseZip = new JButton("Browse");

    // Output folder
    private final JTextField outTf = new JTextField();
    private final JButton    browseOut = new JButton("Browse");

    private final JButton ok  = new JButton("OK");
    private final JButton cancel = new JButton("Cancel");

    /**
     * Builds and initializes the RescaleHuDialog UI.
     * <p>
     * This dialog configures a sweep over neuron "rescaling factors," which are
     * used to multiply the StarDist polygon size before evaluating neuron masks.
     * The user can also set probability / overlap thresholds that remain fixed
     * during that sweep, choose NEURON vs SUBTYPE mode, pick the relevant channel,
     * and (in SUBTYPE mode) provide a StarDist model ZIP.
     * </p>
     *
     * <p>The dialog is modal. This constructor sets up layout, listeners,
     * validation hooks, and default values, but does not block. Call
     * {@link #showAndGet()} to display it and retrieve the results.</p>
     *
     * @param owner parent window for modality and centering; may be a Frame or Dialog
     */
    public RescaleHuDialog(Window owner) {
        super(owner, "Neuron Rescaling Sweep", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10,10));
        ((JComponent)getContentPane()).setBorder(new EmptyBorder(12,12,12,12));
        setResizable(true);
        setMinimumSize(new Dimension(720, 800));

        //  Image row
        imgTf.setColumns(30);
        JPanel imgRow = rowWithBrowse(imgTf, browseImg);

        //  Mode + channel
        ButtonGroup bg = new ButtonGroup(); bg.add(rbNeuron); bg.add(rbSubtype);
        JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        modeRow.add(new JLabel("Choose mode of segmentation"));
        modeRow.add(rbNeuron);
        modeRow.add(rbSubtype);
        modeRow.add(Box.createHorizontalStrut(16));

        // Sweep controls
        JPanel sweep = new JPanel(new GridLayout(4,2,8,8));
        sweep.add(new JLabel("Channel:"));  sweep.add(chSp);
        sweep.add(new JLabel("Enter minimum value"));        sweep.add(minSp);
        sweep.add(new JLabel("Enter maximum max value"));    sweep.add(maxSp);
        sweep.add(new JLabel("Enter size of each increment step")); sweep.add(stepSp);

        //  Prob + Overlap sliders
        JPanel sliders = new JPanel();
        sliders.setLayout(new BoxLayout(sliders, BoxLayout.Y_AXIS));
        sliders.add(wrapLabelCentered(
                "Default Probability is 0.5 and Overlap threshold is 0.3. " +
                        "Leave as default when first trying.<br/>" +
                        "More info: https://www.imagej.net/StarDist<br/>" +
                        "<b>Large images may freeze or hang.</b>",
                520
        ));

        JPanel probRow = new JPanel(new BorderLayout(8,0));
        probRow.add(new JLabel("Probability (if staining is weak, use low values)"), BorderLayout.NORTH);
        probSl.setPaintTicks(true); probSl.setMajorTickSpacing(10); probSl.setMinorTickSpacing(5);
        probRow.add(probSl, BorderLayout.CENTER);
        probRow.add(wrap(probVal), BorderLayout.EAST);
        sliders.add(probRow);

        JPanel nmsRow = new JPanel(new BorderLayout(8,0));
        nmsRow.add(new JLabel("Overlap Threshold"), BorderLayout.NORTH);
        nmsSl.setPaintTicks(true); nmsSl.setMajorTickSpacing(20); nmsSl.setMinorTickSpacing(5);
        nmsRow.add(nmsSl, BorderLayout.CENTER);
        nmsRow.add(wrap(nmsVal), BorderLayout.EAST);
        sliders.add(Box.createVerticalStrut(6));
        sliders.add(nmsRow);

        // Model (SUBTYPE only)
        modelTf.setColumns(28);
        JPanel modelRow = labeled("Choose the StarDist model based on celltype.", rowWithBrowse(modelTf, browseZip));

        // Output folder
        outTf.setColumns(28);
        JPanel outRow = labeled("Select output location", rowWithBrowse(outTf, browseOut));

        //  Buttons
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(ok); actions.add(cancel);

        // Compose
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.add(labeled("Choose the image to segment", imgRow));
        center.add(Box.createVerticalStrut(6));
        center.add(modeRow);
        center.add(Box.createVerticalStrut(6));
        center.add(sweep);
        center.add(Box.createVerticalStrut(10));
        center.add(sliders);
        center.add(Box.createVerticalStrut(10));
        center.add(modelRow);
        center.add(Box.createVerticalStrut(6));
        center.add(outRow);

        add(center, BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);

        //  Behavior
        rbNeuron.addActionListener(e -> updateModelVisibility());
        rbSubtype.addActionListener(e -> updateModelVisibility());
        updateModelVisibility();

        browseImg.addActionListener(e -> chooseFile(imgTf, "Choose image", "tif","tiff","czi","lif"));
        browseZip.addActionListener(e -> chooseFile(modelTf, "Choose StarDist model (.zip)", "zip"));
        browseOut.addActionListener(e -> chooseDir(outTf));


        probSl.addChangeListener(e -> probVal.setText(String.format(java.util.Locale.US, "%.2f", probSl.getValue()/100.0)));
        nmsSl.addChangeListener(e -> nmsVal.setText(String.format(java.util.Locale.US, "%.2f", nmsSl.getValue()/100.0)));

        cancel.addActionListener(e -> { result = null; dispose(); });
        ok.addActionListener(e -> onOK());

        pack();
        setSize(Math.min(720, getWidth()), Math.min(800,getHeight()));
        setLocationRelativeTo(owner);
    }

    /**
     * Toggles the model ZIP controls based on the selected mode.
     * <p>
     * If the user selected "Neuron subtype segmentation" (SUBTYPE mode),
     * the StarDist model ZIP is required, so the model file text field and
     * Browse button are enabled. Otherwise they are disabled.
     * </p>
     */

    private void updateModelVisibility() {
        boolean subtype = rbSubtype.isSelected();
        modelTf.setEnabled(subtype);
        browseZip.setEnabled(subtype);
    }

    /**
     * Creates a labeled row with a wrapped label on the left (so long text won't
     * force the dialog to expand horizontally) and an arbitrary component (often a
     * textfield+Browse row) on the right.
     *
     * @param label human-readable description for the left column
     * @param comp  component to display in the right column
     * @return a JPanel with BorderLayout.WEST/CENTER layout
     */

    private static JPanel labeled(String label, JComponent comp){
        JPanel p = new JPanel(new BorderLayout(8,0));
        p.add(wrapLabel(label, 260), BorderLayout.WEST); // was: new JLabel(label)
        p.add(comp, BorderLayout.CENTER);
        return p;
    }

    /**
     * Builds a row containing a path text field and a "Browse" button.
     * <p>
     * GridBagLayout is used so the text field can request horizontal stretch
     * (for Material-style full-width underline) while keeping the Browse button
     * at its preferred size.
     * </p>
     *
     * @param tf     the text field that will receive the chosen file/folder path
     * @param browse the button that triggers a chooser
     * @return JPanel suitable for embedding in a labeled(...) row
     */

    private static JPanel rowWithBrowse(JTextField tf, JButton browse){
        JPanel row = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(0,0,0,6);

        // Stretch the text field so Material L&F draws a full-width underline
        gc.gridx = 0;
        gc.weightx = 1.0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        // keep a sensible minimum width
        tf.setColumns(28);
        row.add(tf, gc);

        gc.gridx = 1;
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;
        row.add(browse, gc);

        return row;
    }
    /**
     * Wraps a component in a tiny left-aligned FlowLayout (no vertical padding).
     * <p>
     * This is mainly used to right-align live-updating value labels next to sliders
     * (e.g. "0.50" for probability).
     * </p>
     *
     * @param c the component to wrap
     * @return a JPanel containing {@code c} with a left-aligned FlowLayout
     */

    private static JPanel wrap(JComponent c){ JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0)); p.add(c); return p; }


    /**
     * Opens a file chooser and puts the selected file path into the provided text field.
     * <p>
     * If extensions are supplied, a FileNameExtensionFilter is applied to limit
     * visible file types (for example, TIFF images or StarDist model ZIPs).
     * </p>
     *
     * @param tf    destination text field to receive the selected file path
     * @param title title of the chooser dialog
     * @param exts  optional allowed extensions (without dots), e.g. "tif", "zip"
     */
    private void chooseFile(JTextField tf, String title, String... exts){
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(title);
        if (exts != null && exts.length>0) fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(title, exts));
        int ret = fc.showOpenDialog(this);
        if (ret == JFileChooser.APPROVE_OPTION && fc.getSelectedFile()!=null) tf.setText(fc.getSelectedFile().getAbsolutePath());
    }

    /**
     * Opens a directory chooser (JFileChooser in DIRECTORIES_ONLY mode) and writes
     * the chosen folder path into the provided text field.
     *
     * @param tf destination text field to receive the selected directory path
     */
    private void chooseDir(JTextField tf){
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Choose output folder");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setMultiSelectionEnabled(false);
        int ret = fc.showOpenDialog(this);
        if (ret == JFileChooser.APPROVE_OPTION && fc.getSelectedFile()!=null) tf.setText(fc.getSelectedFile().getAbsolutePath());
    }

    /**
     * Creates a JLabel that displays multi-line, centered HTML text constrained
     * to a fixed maximum width. This is used for explanatory help text in the dialog.
     * <p>
     * Unlike a plain JLabel, this forces wrapping and center alignment without
     * letting the label stretch horizontally in a BoxLayout.
     * </p>
     *
     * @param text     plain text or HTML snippet to show
     * @param widthPx  maximum width in pixels for wrapping
     * @return a JLabel containing the wrapped, centered HTML block
     */
    public static JLabel wrapLabelCentered(String text, int widthPx) {
        String html = "<html><div style='width:" + widthPx +
                "px; white-space:normal; text-align:center; margin:0 auto;'>" +
                text + "</div></html>";
        JLabel lbl = new JLabel(html);
        // Center in BoxLayout (Y-axis) and don't stretch
        lbl.setHorizontalAlignment(SwingConstants.CENTER);
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        lbl.setMaximumSize(lbl.getPreferredSize());
        return lbl;
    }

    /**
     * Validates all user inputs and, if valid:
     * <ul>
     *   <li>Creates a {@link Config} describing the sweep parameters
     *       (rescaleMin/rescaleMax/rescaleStep, fixed prob and overlap, etc.)</li>
     *   <li>Stores it in {@code result}</li>
     *   <li>Disposes the dialog</li>
     * </ul>
     *
     * <p>If any required input is missing or invalid (e.g. no image file,
     * rescale bounds are nonsensical, SUBTYPE mode without a model ZIP,
     * or output directory that can't be created), a warning dialog is shown
     * and the dialog remains open.</p>
     */
    private void onOK() {
        Config c = new Config();

        String p = text(imgTf);
        if (p.isEmpty() || !new File(p).isFile()) {
            JOptionPane.showMessageDialog(this, "Please choose a valid image file .",
                    "Input required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        c.imageFile = new File(p);

        c.mode = rbSubtype.isSelected() ? Mode.SUBTYPE : Mode.NEURON;
        c.channel = ((Number)chSp.getValue()).intValue();

        c.rescaleMin  = ((Number)minSp.getValue()).doubleValue();
        c.rescaleMax  = ((Number)maxSp.getValue()).doubleValue();
        c.rescaleStep = ((Number)stepSp.getValue()).doubleValue();
        if (c.rescaleMin <= 0 || c.rescaleMax < c.rescaleMin || c.rescaleStep <= 0) {
            JOptionPane.showMessageDialog(this, "Please enter valid rescaling bounds and step.",
                    "Input required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        c.prob    = probSl.getValue()/100.0;
        c.overlap = nmsSl.getValue()/100.0;

        if (c.mode == Mode.SUBTYPE) {
            String m = text(modelTf);
            if (m.isEmpty() || !new File(m).isFile()) {
                JOptionPane.showMessageDialog(this, "Please choose a StarDist model ZIP for subtype segmentation.",
                        "Input required", JOptionPane.WARNING_MESSAGE);
                return;
            }
            c.modelZip = new File(m);
        }

        String out = text(outTf);
        if (out.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select an output folder.",
                    "Input required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        File f = new File(out);
        if (!f.isDirectory() && !f.mkdirs()) {
            JOptionPane.showMessageDialog(this, "Cannot create output folder:\n" + out,
                    "Input required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        c.outDir = f;

        result = c;
        dispose();
    }

    /**
     * Wraps the given text in a fixed-width HTML block so it will wrap
     * lines instead of stretching the layout.
     *
     * @param text     plain text or HTML snippet to render
     * @param widthPx  maximum width in pixels
     * @return a JLabel that renders the wrapped text
     */
    private static JLabel wrapLabel(String text, int widthPx) {
        String html = "<html><div style='width:" + widthPx + "px; white-space: normal;'>" + text + "</div></html>";
        return new JLabel(html);
    }

    /**
     * Returns the trimmed text contents of a JTextField.
     *
     * @param tf the text field
     * @return the field's text with leading/trailing whitespace removed,
     *         or the empty string if the text is null
     */
    private static String text(JTextField tf){ return tf.getText()==null? "" : tf.getText().trim(); }

    /**
     * Shows the dialog modally (blocks until the user either clicks OK or Cancel)
     * and returns the final {@link Config}.
     *
     * @return a populated {@link Config} if the user pressed OK and validation
     *         succeeded; {@code null} if the user cancelled or closed the dialog
     */
    public Config showAndGet() { setVisible(true); return result; }
}
