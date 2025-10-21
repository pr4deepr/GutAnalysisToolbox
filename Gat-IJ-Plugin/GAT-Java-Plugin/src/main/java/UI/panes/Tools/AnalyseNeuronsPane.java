package UI.panes.Tools;

import UI.Handlers.Navigator;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import UI.panes.SettingPanes.*;

public class AnalyseNeuronsPane extends JPanel {
    public static final String Name = "Analyse Neurons";

    public AnalyseNeuronsPane(Navigator navigator) {
        setLayout(new BorderLayout(10,10));
        setBorder(new EmptyBorder(10,10,10,10));

        // 1) Pane title
        JLabel paneTitle = new JLabel("Neuron Analysis Options", SwingConstants.CENTER);
        paneTitle.setFont(paneTitle.getFont().deriveFont(Font.BOLD, 18f));
        add(paneTitle, BorderLayout.NORTH);

        // 2) Option panels
        OptionPanel neurons = new OptionPanel(
                "Analyse Neurons",
                "Analyse image with hu and ganglia channel.",
                NeuronWorkflowPane.Name
        );
        OptionPanel noHu = new OptionPanel(
                "Multichannel – No HU",
                "Process multichannel images that do not have Hu staining",
                MultiChannelNoHuPane.Name
        );
        OptionPanel multi = new OptionPanel(
                "Multichannel - With Hu",
                "Process multichannel images that do have Hu staining",
                MultichannelPane.Name
        );

        // tie them together so only one can be selected
        OptionPanel[] all = {neurons, noHu, multi};
        Arrays.stream(all).forEach(op -> op.setSiblings(all));
        neurons.setSelected(true);  // default choice

        JPanel choices = new JPanel(new GridLayout(1,3,10,30));
        choices.add(neurons);
        choices.add(noHu);
        choices.add(multi);
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

    /** A clickable panel that highlights when selected. */
    private static class OptionPanel extends JPanel {
        private final String targetName;
        private boolean selected = false;
        private OptionPanel[] siblings;
        private final Color defaultBg;
        private final Color highlightBg = new Color(56, 56, 56); // grey for now

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
