package UI.panes.Tools;

import UI.Handlers.Navigator;
import services.merge.CsvMerger;
import services.merge.DefaultLabelStrategy;
import services.merge.FileExtension;
import services.merge.MergeException;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;

public class AnalysisPane extends JPanel {
    public static final String Name = "Merge Analysis";

    private final JRadioButton singleRb   = new JRadioButton("Merge files matching this filename or part of it");
    private final JRadioButton multiRb    = new JRadioButton("Merge multiple CSV types (discover filenames from first subfolder)");
    private final JTextField   patternTf  = new JTextField("results", 20);

    private final JPanel       singleRow  = new JPanel(new GridBagLayout());

    private final JTextField   rootTf     = new JTextField(28);
    private final JButton      browseBtn  = new JButton("Choose Root...");
    private final JButton      runBtn     = new JButton("Run Merge");

    private Path selectedRoot = null;

    public AnalysisPane(Navigator navigator) {
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel title = new JLabel("Merge Analysis", SwingConstants.LEFT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 4, 6, 4);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill   = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;

        // mode
        ButtonGroup group = new ButtonGroup();
        group.add(singleRb); group.add(multiRb);
        singleRb.setSelected(true);

        gc.gridx=0; gc.gridy=0; form.add(new JLabel("Mode:"), gc);
        gc.gridy++; form.add(singleRb, gc);
        gc.gridy++; form.add(multiRb, gc);

        // single row (pattern + fixed extension label)
        gc.gridy++;
        singleRow.setVisible(true);
        GridBagConstraints sgc = new GridBagConstraints();
        sgc.insets = new Insets(0, 0, 0, 4);
        sgc.anchor = GridBagConstraints.WEST;

        singleRow.add(new JLabel("Filename / substring:"), sgc);
        sgc.gridx = 1; sgc.fill = GridBagConstraints.HORIZONTAL; sgc.weightx = 1.0;
        singleRow.add(patternTf, sgc);

        // Fixed extension label (CSV only)
        sgc.gridx = 2; sgc.fill = GridBagConstraints.NONE; sgc.weightx = 0;
        singleRow.add(new JLabel(".csv"), sgc);

        form.add(singleRow, gc);

        // root chooser
        JPanel rootRow = new JPanel(new GridBagLayout());
        GridBagConstraints rgc = new GridBagConstraints();
        rgc.insets = new Insets(0, 0, 0, 4);
        rgc.anchor = GridBagConstraints.WEST;
        rootTf.setEditable(false);
        rootRow.add(new JLabel("Root folder:"), rgc);
        rgc.gridx = 1; rgc.fill = GridBagConstraints.HORIZONTAL; rgc.weightx = 1.0;
        rootRow.add(rootTf, rgc);
        rgc.gridx = 2; rgc.fill = GridBagConstraints.NONE; rgc.weightx = 0;
        rootRow.add(browseBtn, rgc);
        gc.gridy++;
        form.add(rootRow, gc);

        // actions
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(runBtn);
        gc.gridy++;
        form.add(actions, gc);

        add(form, BorderLayout.CENTER);

        // behavior
        singleRb.addActionListener(e -> singleRow.setVisible(true));
        multiRb.addActionListener(e -> singleRow.setVisible(false));

        browseBtn.addActionListener(e -> chooseRootDirectory());

        runBtn.addActionListener(e -> onRun());
    }

    private void onRun() {
        if (selectedRoot == null) {
            JOptionPane.showMessageDialog(this, "Please choose a ROOT folder first.",
                    "Root required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        runBtn.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        // Fixed extension: CSV
        FileExtension ext = FileExtension.CSV;

        if (singleRb.isSelected()) {
            String pattern = patternTf.getText().trim();
            if (pattern.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter a filename or substring.",
                        "Pattern required", JOptionPane.WARNING_MESSAGE);
                runBtn.setEnabled(true);
                setCursor(Cursor.getDefaultCursor());
                return;
            }
            runSingle(selectedRoot, pattern, ext);
        } else {
            runMulti(selectedRoot, ext);
        }
    }

    private void runSingle(Path root, String pattern, FileExtension ext) {
        CsvMerger merger = new CsvMerger(new DefaultLabelStrategy());
        new SwingWorker<Path, Void>() {
            @Override protected Path doInBackground() throws Exception {
                return merger.mergeSinglePattern(root, pattern, ext);
            }
            @Override protected void done() {
                finishRun();
                try {
                    Path out = get();
                    JOptionPane.showMessageDialog(AnalysisPane.this,
                            "Merged file created:\n" + out, "Merge complete",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) { showError(ex); }
            }
        }.execute();
    }

    private void runMulti(Path root, FileExtension ext) {
        CsvMerger merger = new CsvMerger(new DefaultLabelStrategy());
        new SwingWorker<List<Path>, Void>() {
            @Override protected List<Path> doInBackground() throws Exception {
                return merger.mergeFromFirstSubfolderPatterns(root, ext);
            }
            @Override protected void done() {
                finishRun();
                try {
                    List<Path> outs = get();
                    if (outs == null || outs.isEmpty()) {
                        JOptionPane.showMessageDialog(AnalysisPane.this, "No outputs created.",
                                "No files", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    StringBuilder sb = new StringBuilder("Merged files:\n");
                    for (Path p : outs) sb.append(p).append("\n");
                    JOptionPane.showMessageDialog(AnalysisPane.this, sb.toString(),
                            "Merge complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) { showError(ex); }
            }
        }.execute();
    }

    private void chooseRootDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select the ROOT folder for merging");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedRoot = chooser.getSelectedFile().toPath();
            rootTf.setText(selectedRoot.toString());
        }
    }

    private void finishRun() {
        runBtn.setEnabled(true);
        setCursor(Cursor.getDefaultCursor());
    }

    private void showError(Exception ex) {
        String msg = (ex.getMessage() != null ? ex.getMessage() : ex.toString());
        JOptionPane.showMessageDialog(this, "Merge error:\n" + msg, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
