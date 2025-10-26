// UI/panes/Tools/dialogs/GangliaExpansionDialog.java
package UI.panes.Tools.dialogs;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;

import static UI.panes.Tools.dialogs.RescaleHuDialog.wrapLabelCentered;

public final class GangliaExpansionDialog extends JDialog {

    public static final class Config {
        public File imageFile;            // nullable if imageAlreadyOpen


        // Optional ROI zip (not required)
        public File huRoiZip;

        public double minUm  = 10.0;
        public double maxUm  = 15.0;
        public double stepUm = 0.1;

        // NEW: output folder
        public File outDir;
    }

    private Config result;

    private final JTextField imgTf = new JTextField();
    private final JButton    browseImg = new JButton("Browse");

    private final JTextField roiTf = new JTextField();
    private final JButton    browseRoi = new JButton("Browse");

    private final JSpinner minSp  = new JSpinner(new SpinnerNumberModel(12.0, 0.1, 200.0, 0.1));
    private final JSpinner maxSp  = new JSpinner(new SpinnerNumberModel(15.0, 0.1, 500.0, 0.1));
    private final JSpinner stepSp = new JSpinner(new SpinnerNumberModel(0.1, 0.01, 50.0, 0.01));

    // NEW: output
    private final JTextField outTf  = new JTextField();
    private final JButton    browseOut = new JButton("Browse");

    private final JButton ok = new JButton("OK");
    private final JButton cancel = new JButton("Cancel");

    public GangliaExpansionDialog(Window owner) {
        super(owner, "Test ganglia segmentation Hu", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(true);
        setMinimumSize(new Dimension(800, 500));
        setLayout(new BorderLayout(10,10));
        ((JComponent)getContentPane()).setBorder(new EmptyBorder(12,12,12,12));

        // Image row
        imgTf.setColumns(30);
        JPanel imgRow = rowWithBrowse(imgTf, browseImg);

        // ROI zip row
        roiTf.setColumns(30);
        JPanel roiRow = rowWithBrowse(roiTf, browseRoi);

        JPanel sweep = new JPanel(new GridLayout(3,2,8,8));
        sweep.add(new JLabel("Enter minimum value"));   sweep.add(minSp);
        sweep.add(new JLabel("Enter maximum max value")); sweep.add(maxSp);
        sweep.add(new JLabel("Enter increment step/s"));  sweep.add(stepSp);

        // Output row
        outTf.setColumns(28);
        JPanel outRow = labeled("Select output location", rowWithBrowse(outTf, browseOut));

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.add(wrapLabelCentered("<html>Evaluate a range of values to expand segmented cells to get accurate ganglia outlines.<br>" +
                "<b>You will need an ROI Zip File with segmented cells to run this.</b></html>",520));
        center.add(labeled("Select the maximum projection or 2D image", imgRow));
        center.add(labeled("Select roi ZIP to load", roiRow));
        center.add(Box.createVerticalStrut(10));
        center.add(sweep);
        center.add(Box.createVerticalStrut(10));
        center.add(outRow);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(ok);
        actions.add(cancel);

        add(center, BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);

        browseImg.addActionListener(e -> chooseFile(imgTf, "Choose image", "tif","tiff","czi","lif"));
        browseRoi.addActionListener(e -> chooseFile(roiTf, "Choose ROI zip", "zip"));
        browseOut.addActionListener(e -> chooseDir(outTf));

        cancel.addActionListener(e -> { result = null; dispose(); });
        ok.addActionListener(e -> onOK());

        pack();
        setSize(Math.min(820, getWidth()), getHeight());
        setLocationRelativeTo(owner);
    }

    private static JLabel wrapLabel(String text, int widthPx) {
        String html = "<html><div style='width:" + widthPx + "px; white-space: normal;'>" + text + "</div></html>";
        return new JLabel(html);
    }

    private static JPanel labeled(String label, JComponent comp){
        JPanel p = new JPanel(new BorderLayout(8,0));
        p.add(wrapLabel(label, 260), BorderLayout.WEST); // was: new JLabel(label)
        p.add(comp, BorderLayout.CENTER);
        return p;
    }
    private static JPanel rowWithBrowse(JTextField tf, JButton browse){
        JPanel row = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(0,0,0,6);

        // don't let the textfield gobble all the width
        gc.gridx=0; gc.weightx=0; gc.fill=GridBagConstraints.NONE;
        tf.setColumns(28);
        row.add(tf, gc);

        gc.gridx=1; gc.weightx=0; gc.fill=GridBagConstraints.NONE;
        row.add(browse, gc);
        return row;
    }

    private void chooseFile(JTextField tf, String title, String... exts){
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(title);
        if (exts != null && exts.length>0) fc.setFileFilter(new FileNameExtensionFilter(title, exts));
        int ret = fc.showOpenDialog(this);
        if (ret == JFileChooser.APPROVE_OPTION && fc.getSelectedFile() != null) {
            tf.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }
    private void chooseDir(JTextField tf){
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Choose output folder");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int ret = fc.showOpenDialog(this);
        if (ret == JFileChooser.APPROVE_OPTION && fc.getSelectedFile() != null) {
            tf.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void onOK() {
        Config c = new Config();

        String p = text(imgTf);
        if (p.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please choose an image file or tick 'Image_already_open'.",
                    "Input required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        File f = new File(p);
        if (!f.isFile()) {
            JOptionPane.showMessageDialog(this, "Image file does not exist:\n" + p,
                    "Input required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        c.imageFile = f;


        String rz = text(roiTf);
        if (!rz.isEmpty()) {
            File fz = new File(rz);
            if (fz.isFile()) c.huRoiZip = fz; // optional
        }

        c.minUm  = ((Number)minSp.getValue()).doubleValue();
        c.maxUm  = ((Number)maxSp.getValue()).doubleValue();
        c.stepUm = ((Number)stepSp.getValue()).doubleValue();
        if (c.minUm <= 0 || c.maxUm <= c.minUm || c.stepUm <= 0) {
            JOptionPane.showMessageDialog(this, "Please enter a valid µm range/step.",
                    "Input required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String out = text(outTf);
        if (out.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select an output folder.",
                    "Input required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        File of = new File(out);
        if (!of.isDirectory() && !of.mkdirs()) {
            JOptionPane.showMessageDialog(this, "Cannot create output folder:\n" + out,
                    "Input required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        c.outDir = of;

        result = c;
        dispose();
    }

    private static String text(JTextField tf){ return tf.getText()==null? "" : tf.getText().trim(); }

    public Config showAndGet() { setVisible(true); return result; }
}
