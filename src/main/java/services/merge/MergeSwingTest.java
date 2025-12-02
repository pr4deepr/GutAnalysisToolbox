package services.merge;

import javax.swing.*;
import java.nio.file.Path;
import java.util.List;

public class MergeSwingTest {

    public static void main(String[] args) {
        try {
            // Native look for Windows
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        // Pick folder
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Results Root Directory");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        int result = chooser.showOpenDialog(null);
        if (result != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
            System.out.println("No folder selected. Exiting.");
            return;
        }

        Path root = chooser.getSelectedFile().toPath();

        // Ask mode
        Object[] modes = {"Single-file pattern", "Auto (multi-file)"};
        int modeChoice = JOptionPane.showOptionDialog(
                null,
                "Select merge mode:",
                "Merge Mode",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                modes,
                modes[0]
        );
        if (modeChoice == JOptionPane.CLOSED_OPTION) {
            System.out.println("Cancelled.");
            return;
        }

        CsvMerger merger = new CsvMerger(new DefaultLabelStrategy());

        try {
            if (modeChoice == 0) { // Single mode
                String pattern = JOptionPane.showInputDialog(
                        null,
                        "Enter CSV filename pattern (e.g., Cell_counts.csv):",
                        "Pattern",
                        JOptionPane.PLAIN_MESSAGE
                );
                if (pattern == null || pattern.trim().isEmpty()) {
                    System.out.println("No pattern entered. Aborting.");
                    return;
                }
                Path out = merger.mergeSinglePattern(root, pattern.trim());
                JOptionPane.showMessageDialog(null, "Merged file created:\n" + out);
            } else { // Multi mode
                List<Path> outs = merger.mergeFromFirstSubfolderPatterns(root);
                StringBuilder sb = new StringBuilder("Merged files:\n");
                for (Path p : outs) sb.append(p).append("\n");
                JOptionPane.showMessageDialog(null, sb.toString());
            }
        } catch (MergeException me) {
            JOptionPane.showMessageDialog(null, "Merge error:\n" + me.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, "Unexpected error:\n" + ex, "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
