package UI.panes.Tools;

import UI.Handlers.Navigator;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import UI.panes.SettingPanes.*;

/**
 * High-level entry panel for neuron analysis workflows.
 * <p>
 * This Swing panel presents three mutually exclusive analysis modes:
 * </p>
 * <ul>
 *   <li><b>Analyse Neurons</b> – run the standard neuron workflow.</li>
 *   <li><b>Multichannel – No HU</b> – process multichannel images without Hu staining.</li>
 *   <li><b>Multichannel – With Hu</b> – run the full multichannel workflow including Hu neurons.</li>
 * </ul>
 *
 * <p>
 * The user picks exactly one of these options (they act like radio buttons),
 * then clicks "Go". When "Go" is pressed, the panel uses the provided
 * {@link UI.Handlers.Navigator} to navigate to the pane that implements
 * the chosen workflow.
 * </p>
 *
 * <p>
 * Visually, the pane consists of:
 * </p>
 * <ol>
 *   <li>A title ("Neuron Analysis Options").</li>
 *   <li>A row of clickable {@code OptionPanel} tiles, one per workflow mode.</li>
 *   <li>A single "Go" button to continue with the selected mode.</li>
 * </ol>
 *
 * <p>
 * This pane does not itself run image analysis; it just gathers intent
 * from the user and forwards them to the appropriate workflow UI.
 * </p>
 */
public class AnalyseNeuronsPane extends JPanel {
    public static final String Name = "Analyse Neurons";


    /**
     * Creates the "Analyse Neurons" options panel.
     * <p>
     * The panel shows three mutually exclusive pipeline choices (Analyse Neurons,
     * Multichannel – No HU, Multichannel – With Hu) and a single "Go" button.
     * Clicking "Go" navigates to the pane associated with the currently selected
     * option.
     * </p>
     *
     * @param navigator the app-level navigation controller. Used to switch to
     *                  the pane associated with the user's chosen option.
     */
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
                "Run the neuron analysis pipeline",
                NeuronWorkflowPane.Name
        );
        OptionPanel noHu = new OptionPanel(
                "Multichannel – No HU",
                "Process only multichannel images",
                MultiChannelNoHuPane.Name
        );
        OptionPanel multi = new OptionPanel(
                "Multichannel - With Hu",
                "Run full multichannel pipeline",
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


    /**
     * A clickable, self-contained tile representing one analysis workflow option.
     * <p>
     * An {@code OptionPanel} shows:
     * </p>
     * <ul>
     *   <li>A short title (e.g. "Multichannel – No HU").</li>
     *   <li>A brief description (rendered as wrapped HTML text).</li>
     * </ul>
     *
     * <p>
     * Clicking the tile marks it as selected and visually highlights it. Only one
     * tile in a group may be selected at a time. The currently selected tile
     * controls which downstream workflow pane will be shown when the user
     * presses "Go".
     * </p>
     *
     * <p>
     * Internally each {@code OptionPanel} remembers a {@code targetName}, which
     * corresponds to the {@link UI.Handlers.Navigator} pane name that should
     * be shown for this option.
     * </p>
     */
    private static class OptionPanel extends JPanel {
        private final String targetName;
        private boolean selected = false;
        private OptionPanel[] siblings;
        private final Color defaultBg;
        private final Color highlightBg = new Color(56, 56, 56); // grey for now

        /**
         * Builds a clickable option tile representing one analysis workflow.
         * <p>
         * Each tile renders a title and short description, highlights when selected,
         * and remembers the logical target pane name so the caller can navigate
         * there when the user presses "Go".
         * </p>
         *
         * @param title         short label shown at the top of the tile
         * @param description   explanatory text shown under the title
         * @param targetName    the Navigator pane name to activate if this option
         *                      ends up selected when the user clicks "Go"
         */
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

        /**
         * Declares the other {@code OptionPanel}s in the group.
         * <p>
         * Panels in the same group behave like radio buttons: when this panel
         * becomes selected, all of its siblings are automatically deselected.
         * </p>
         *
         * @param siblings array of peer {@code OptionPanel}s, including this one
         */
        void setSiblings(OptionPanel[] siblings) {
            this.siblings = siblings;
        }

        /**
         * Returns the logical destination pane name associated with this option.
         *
         * @return the {@link Navigator} target name this panel represents
         */
        String getTargetName() {
            return targetName;
        }

        /**
         * Reports whether this option is currently selected.
         *
         * @return {@code true} if this tile is highlighted/active,
         *         {@code false} otherwise
         */
        boolean isSelected() {
            return selected;
        }

        /**
         * Marks (or unmarks) this option as selected and updates visual state.
         * <p>
         * When selecting this option ({@code sel == true}), all sibling options
         * are first told to deselect themselves so only one remains active at
         * a time.
         * </p>
         *
         * @param sel {@code true} to select this option, {@code false} to deselect it
         */
        void setSelected(boolean sel) {
            // When selecting, deselect the others first
            if (sel && siblings != null) {
                for (OptionPanel op : siblings) {
                    if (op != this) op.updateSelected(false);
                }
            }
            updateSelected(sel);
        }

        /**
         * Applies the visual "selected" state to this tile.
         * <p>
         * This updates the internal {@code selected} flag and swaps the panel
         * background color between the default and the highlight color.
         * It does not affect sibling panels.
         * </p>
         *
         * @param sel {@code true} to mark as selected and show the highlight
         */
        private void updateSelected(boolean sel) {
            this.selected = sel;
            // Swap background
            setBackground(sel ? highlightBg : defaultBg);
            repaint();
        }
    }
}
