package Features.Tools;

import javax.swing.*;
import java.awt.*;


/**
 * Small Swing progress dialog + ImageJ status mirroring.
 *
 * Pipelines create this to show users what's happening during long, multi-step
 * analysis runs. It supports:
 *
 *  - determinate mode (N total steps known)
 *  - step() to advance
 *  - pulse() / stopPulse() for indeterminate work
 *  - start() to set how many steps we expect
 *  - close() (via AutoCloseable) to dispose dialog and clear IJ status
 *
 * Threading behavior:
 *  - All UI updates happen on the Swing EDT using SwingUtilities.invokeLater(...)
 *  - Long-running pipeline work stays off the EDT
 *
 * Typical usage:
 *   ProgressUI p = new ProgressUI("Neuron/Hu pipeline");
 *   p.start(estimateSteps(...));
 *   p.step("Opening image");
 *   ...
 *   p.close();
 */
public final class ProgressUI implements AutoCloseable {
    private final JDialog dialog;
    private final JProgressBar bar;
    private final JLabel label;
    private int total = 100;
    private int current = 0;


    /**
     * Create and show a non-modal, always-on-top progress dialog.
     *
     * This immediately constructs and displays the dialog with an initial
     * "Starting..." message, but does not yet know how many steps there are
     * until you call start().
     *
     * @param title Window title for the dialog (e.g. "Hu pipeline").
     */
    public ProgressUI(String title) {
        dialog = new JDialog((Frame) null, title, false);

        bar = new JProgressBar(0, 100);
        bar.setStringPainted(true);
        bar.setStringPainted(true);
        bar.setPreferredSize(new Dimension(300, 22));
        label = new JLabel("Starting...");
        JPanel p = new JPanel(new BorderLayout(10,10));
        p.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        p.add(label, BorderLayout.NORTH);
        p.add(bar, BorderLayout.CENTER);
        Dimension min = new Dimension(360, 100);
        p.setPreferredSize(min);
        dialog.setContentPane(p);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setAlwaysOnTop(true);
        dialog.setVisible(true);
    }

    /**
     * Initialize the determinate progress bar with a known number of steps.
     * After calling this, call step("message") to advance.
     *
     * @param totalSteps total number of discrete steps you're planning on reporting.
     */
    public void start(int totalSteps) {
        this.total = Math.max(1, totalSteps);
        set(0, "Starting...");
    }

    /**
     * Advance progress by 1 step and update the UI message.
     *
     * Calling this repeatedly will:
     *  - increment the logical step counter
     *  - recompute % complete
     *  - update Swing label + bar
     *  - also push status/progress to ImageJ's status bar
     *
     * @param msg short description of the new step ("Segmenting with StarDist", etc).
     */
    public void step(String msg) {
        set(current + 1, msg);
    }

    /**
     * Manually set the current step index and message.
     * You usually won't call this directly, but it's what start() and step() use.
     *
     * @param step new logical step index (0..total).
     * @param msg  message for both Swing and IJ status bar.
     */
    public void set(int step, String msg) {
        this.current = Math.max(0, Math.min(step, total));
        int pct = (int)Math.round(100.0 * current / total);
        SwingUtilities.invokeLater(() -> {
            label.setText(msg);
            bar.setValue(pct);
            bar.setString(pct + "%");
        });
        // Also mirror to ImageJ status bar:
        ij.IJ.showStatus(msg);
        ij.IJ.showProgress(current, total);
    }

    /**
     * Switch the bar to indeterminate mode and update the message.
     *
     * Use this for long-running operations where we can't estimate progress
     * precisely (e.g. "Running DeepImageJ...").
     *
     * @param msg message to show while pulsing.
     */
    public void pulse(String msg) {
        SwingUtilities.invokeLater(() -> {
            label.setText(msg);
            bar.setIndeterminate(true);
        });
        ij.IJ.showStatus(msg);
    }

    /**
     * Return to determinate mode after pulse().
     *
     * Sets the label text and turns off the indeterminate animation.
     *
     * @param msg final message to show at the end of that pulse stage.
     */

    public void stopPulse(String msg) {
        SwingUtilities.invokeLater(() -> {
            label.setText(msg);
            bar.setIndeterminate(false);
        });
        ij.IJ.showStatus(msg);
    }

    /**
     * Dispose of the dialog and reset ImageJ's status/progress UI.
     *
     * Safe to call multiple times. Called automatically in try-with-resources:
     *
     * try (ProgressUI p = new ProgressUI("...")) {
     *     ...
     * }
     */
    @Override public void close() {
        SwingUtilities.invokeLater(() -> dialog.dispose());
        ij.IJ.showProgress(1.0);
        ij.IJ.showStatus("");
    }

    /**
     * Show or hide the dialog.
     *
     * Pipelines sometimes hide the progress bar during interactive review
     * dialogs so that the correction window isn't covered.
     *
     * @param visible true to show, false to hide.
     */
    public void setVisible(boolean visible) {
        SwingUtilities.invokeLater(() -> dialog.setVisible(visible));
    }
}
