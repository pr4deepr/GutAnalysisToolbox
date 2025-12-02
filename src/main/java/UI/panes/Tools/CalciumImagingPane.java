package UI.panes.Tools;

import UI.Handlers.Navigator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import UI.panes.SettingPanes.*;

/**
 * The CalciumImagingPane class creates a panel with options for calcium imaging tasks and a
 *"Go button to navigate to the selected task.
 */
public class CalciumImagingPane extends JPanel {
    public static final String Name = "Calcium imaging";

    /** Constructor is creating a panel for calcium imaging tasks. 
     * 
     * @param navigator the Navigator class variable for UI handling
    */
    public CalciumImagingPane(Navigator navigator) {

        setLayout(new BorderLayout(12,12));
        setBorder(BorderFactory.createEmptyBorder(16,16,16,16));

        JLabel title = new JLabel("Calcium imaging", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        add(title, BorderLayout.NORTH);

        // 2) Option panels
        OptionPanel alignStack = new OptionPanel(
                "Align Stack",
                "Align a stack, or batch, of calcium images.",
                alignStackPane.Name
        );
        OptionPanel calciumImagingAnalysis = new OptionPanel(
                "Calcium Image Analysis",
                "Analyse mean intensity of each cell",
                calciumImagingAnalysisPane.Name
        );
        OptionPanel temporalColour = new OptionPanel(
                "Temporal Colour Imaging",
                "Temporal colour imaging - visualise dyanmics in a stack",
                TemporalColorPane.Name
        );

        OptionPanel[] all = {alignStack, calciumImagingAnalysis, temporalColour};
        Arrays.stream(all).forEach(op -> op.setSiblings(all));
        alignStack.setSelected(true);  // default choice

        JPanel choices = new JPanel(new GridLayout(1,3,10,30));
        choices.add(alignStack);
        choices.add(calciumImagingAnalysis);
        choices.add(temporalColour);
        choices.setPreferredSize(new Dimension(choices.getPreferredSize().width, 100));
        add(choices, BorderLayout.CENTER);

        // 3) Single “Go” button
        JButton go = new JButton("Go");
        go.addActionListener(e -> {
            for (OptionPanel op : all) {
                if (op.isSelected()) {
                    navigator.show(op.getTargetName());
                    break;
                }
            }
        });
        JPanel goPanel = new JPanel();
        goPanel.add(go);
        add(goPanel, BorderLayout.SOUTH);
    }

    private static class OptionPanel extends JPanel {
        private final String targetName;
        private boolean selected = false;
        private OptionPanel[] siblings;
        private final Color defaultBg;
        private final Color highlightBg = new Color(56, 56, 56);

        OptionPanel(String title, String description, String targetName) {
            this.targetName = targetName;

            // Make sure the background fill is visible
            setOpaque(true);
            defaultBg = getBackground();

            setLayout(new BorderLayout(20, 20));
            setBorder(BorderFactory.createEmptyBorder(20, 10, 20, 10)); // padding

            // Title
            JLabel lblTitle = new JLabel(title, SwingConstants.CENTER);
            lblTitle.setFont(lblTitle.getFont().deriveFont(Font.BOLD, 14f));
            add(lblTitle, BorderLayout.NORTH);

            // Description
            JLabel lblDesc = new JLabel(
                    "<html><body style='text-align:center;'>" + description + "</body></html>",
                    SwingConstants.CENTER
            );
            add(lblDesc, BorderLayout.CENTER);

            // Click to select
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    setSelected(true);
                }
            });
        }

        void setSiblings(OptionPanel[] siblings) {
            this.siblings = siblings;
        }

        String getTargetName() {
            return targetName;
        }

        boolean isSelected() {
            return selected;
        }

        void setSelected(boolean sel) {
            // When selecting, deselect the others first
            if (sel && siblings != null) {
                for (OptionPanel op : siblings) {
                    if (op != this) op.updateSelected(false);
                }
            }
            updateSelected(sel);
        }

        private void updateSelected(boolean sel) {
            this.selected = sel;
            // Swap background
            setBackground(sel ? highlightBg : defaultBg);
            repaint();
        }
    }
}