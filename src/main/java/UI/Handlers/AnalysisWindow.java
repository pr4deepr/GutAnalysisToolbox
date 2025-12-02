package UI.Handlers;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class AnalysisWindow {
    private static AnalysisWindow instance;
    private final JDialog dialog;
    private final JTabbedPane tabs;
    // map each tab component → its desired image size
    private final Map<Component, Dimension> sizeMap = new HashMap<>();

    private AnalysisWindow(Window owner) {
        dialog = new JDialog(owner, "Analysis Results", Dialog.ModalityType.MODELESS);
        tabs   = new JTabbedPane();
        dialog.getContentPane().add(tabs, BorderLayout.CENTER);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        // listen for tab switches
        tabs.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                Component sel = tabs.getSelectedComponent();
                Dimension d = sizeMap.get(sel);
                if (d != null) {
                    // include any padding/chrome you need
                    dialog.setSize(d.width + 100, d.height + 150);
                    dialog.validate();
                }
            }
        });
    }

    public static AnalysisWindow get(Window owner) {
        if (instance == null || !instance.dialog.isDisplayable()) {
            instance = new AnalysisWindow(owner);
        }
        return instance;
    }

    /**
     * Add a new tab and record its “natural” size.
     */
    public void addTab(String title, JPanel panel, int imageWidth, int imageHeight) {
        tabs.addTab(title, panel);
        // store the raw image dimensions
        sizeMap.put(panel, new Dimension(imageWidth, imageHeight));
        // immediately size for this tab
        dialog.setSize(imageWidth + 100, imageHeight + 150);
        dialog.pack();
        dialog.setLocationRelativeTo(dialog.getOwner());
        dialog.setVisible(true);
    }
}
