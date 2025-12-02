package UI;

import UI.panes.SettingPanes.*;
import UI.panes.Tools.*;
import ij.IJ;
import ij.plugin.PlugIn;
import mdlaf.MaterialLookAndFeel;
import mdlaf.themes.MaterialOceanicTheme;

import UI.panes.*;
import UI.Handlers.*;
import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
/**
 * Main entry point for the Gut Analysis Toolbox (GAT) ImageJ/Fiji plugin UI.
 *
 * <p>
 * This class is registered as an {@link ij.plugin.PlugIn}, so ImageJ calls
 * {@link #run(String)} when the user launches the plugin. We then:
 * </p>
 *
 * <ol>
 *   <li>Install/confirm the Material look & feel (static initialiser).</li>
 *   <li>Run a preflight environment check via {@link UI.Preflight} to verify
 *       required models, DeepImageJ engines, GPU support, etc.</li>
 *   <li>If preflight is OK, construct and show the main Swing dialog that
 *       hosts all analysis panes (Neuron workflow, Multichannel workflows,
 *       Tools, Spatial Analysis, etc.) using a {@link CardLayout} for navigation.</li>
 * </ol>
 *
 * <p>
 * Navigation between panes is mediated by a simple {@code Navigator} functional
 * interface. We expose one here that just calls {@link CardLayout#show(Container, String)}
 * on the shared {@code cardPanel}.
 * </p>
 *
 * <p>
 * The dialog we build is modeless (it does not block ImageJ) but is effectively
 * treated as the main application window for the toolbox.
 * </p>
 */
public class GatPluginUI implements PlugIn {

    private CardLayout cards = new CardLayout();
    private JPanel cardPanel = new JPanel(cards);
    Navigator navigator = name -> cards.show(cardPanel,name);



    /**
     * Launch hook called by ImageJ/Fiji.
     *
     * <p>
     * Steps:
     * </p>
     * <ol>
     *   <li>Define the expected StarDist model names we want to find under
     *       {@code Fiji/models} (neuron model and subtype model).</li>
     *   <li>Call {@link UI.Preflight#runAll(String, String,String)} to verify the install.
     *       If anything important is missing (DeepImageJ engines not initialized,
     *       required plugins unavailable, required models not found), we abort and
     *       do not show the UI.</li>
     *   <li>If preflight passes, schedule {@link #buildAndShow()} on the EDT to
     *       actually construct and display the Swing UI.</li>
     * </ol>
     *
     * @param arg
     *        Unused ImageJ argument string.
     */
    @Override
    public void run(String arg){
        String expectedNeuronModel  = "2D_enteric_neuron_V4_1.zip";
        String expectedSubtypeModel = "2D_enteric_neuron_subtype_V4.zip";
        String expectedGangliaModel = "2D_Ganglia_RGB_v3.bioimage.io.model";

        if (!UI.Preflight.runAll(expectedNeuronModel, expectedSubtypeModel,expectedGangliaModel)) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            // Remember Fiji's current design so we restore on close
            final String prevLafClass = UIManager.getLookAndFeel().getClass().getName();

            // Install Material globally for the plugin lifetime until we close it
            try {
                UIManager.setLookAndFeel(new MaterialLookAndFeel(new MaterialOceanicTheme()));
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Build the UI under Material
            JDialog dialog = buildAndShow();

            // When the plugin window closes, restore previous L&F
            dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosed(java.awt.event.WindowEvent e) {
                    try {
                        UIManager.setLookAndFeel(prevLafClass);
                    } catch (Exception ignore) {}

                    // Refresh currently open windows so they repaint with the restored L&F
                    for (java.awt.Window w : java.awt.Window.getWindows()) {
                        SwingUtilities.updateComponentTreeUI(w);
                    }
                }
            });
        });
    }

    /**
     * Build and display the main GAT window.
     *
     * <p>
     * This method:
     * </p>
     * <ul>
     *   <li>Creates a fixed-size {@link JDialog} (non-modal) titled "GAT Plugin".</li>
     *   <li>Builds a persistent left-hand vertical button bar. Each button
     *       switches the active center panel via the shared {@link CardLayout}.</li>
     *   <li>Initializes and registers all panes (Home, Neuron Workflow, Multichannel,
     *       Tools, Spatial Analysis, Calcium Imaging, etc.) into {@code cardPanel},
     *       keyed by each pane's static {@code Name} constant.</li>
     *   <li>Adds both the left bar and the {@code cardPanel} to the dialog,
     *       packs it, centers it, shows it, and finally navigates to the Home pane.</li>
     * </ul>
     *
     * <p>
     * The dialog is marked non-resizable and given a fixed preferred/minimum size
     * so layout is predictable.
     * </p>
     */
    private JDialog buildAndShow(){




        //Our main window which will host the plugin
        JDialog dialog = new JDialog(
                IJ.getInstance(),
                "GAT Plugin",
                false
        );
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(8,8));
        Dimension fixedSize = new Dimension(950, 650);
        dialog.setPreferredSize(fixedSize);
        dialog.setMinimumSize(fixedSize);
        dialog.setResizable(false);        // Lock size

        // Our left toolbar with buttons
        JPanel leftBar = new JPanel();
        leftBar.setLayout(new BoxLayout(leftBar,BoxLayout.Y_AXIS));
        leftBar.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        leftBar.add(Box.createVerticalGlue());

        //Register other panels
        cardPanel.add(new HelpAndSupportPane(navigator),HelpAndSupportPane.Name);
        cardPanel.add(new NeuronWorkflowPane(navigator,dialog),NeuronWorkflowPane.Name);
        cardPanel.add(new MultiChannelNoHuPane(navigator),MultiChannelNoHuPane.Name);
        cardPanel.add(new MultichannelPane(dialog),MultichannelPane.Name);

        // register the dashboard pane
        cardPanel.add(new alignStackPane(navigator, dialog), alignStackPane.Name);
        cardPanel.add(new calciumImagingAnalysisPane(navigator, dialog), calciumImagingAnalysisPane.Name);
        cardPanel.add(new TemporalColorPane(navigator, dialog), TemporalColorPane.Name);



        //Register each of our panes in the card panel
        Map<String, JPanel> panes = new LinkedHashMap<>();
        panes.put(HomePane.Name, new HomePane(navigator));
        panes.put(AnalyseNeuronsPane.Name,  new AnalyseNeuronsPane(navigator));
        panes.put(CalciumImagingPane.Name,  new CalciumImagingPane(navigator));
        panes.put(SpatialAnalysisPane.Name, new SpatialAnalysisPane(navigator, dialog));
        panes.put(MultiplexPane.Name,       new MultiplexPane(navigator));
        panes.put(AnalysisPane.Name,        new AnalysisPane(navigator));
        panes.put(ToolsPane.Name,           new ToolsPane(navigator));


        //Register the panes in the card panel and create the button
        for (Map.Entry<String, JPanel> e: panes.entrySet()){
            String name = e.getKey();
            JPanel pane = e.getValue();

            //add to the CardLayout
            cardPanel.add(pane,name);


            JButton btn = new JButton(name);
            btn.setAlignmentX(Component.CENTER_ALIGNMENT);
            btn.setMaximumSize(new Dimension(160,36));
            btn.addActionListener(ae -> cards.show(cardPanel,name));

            leftBar.add(btn);
            leftBar.add(Box.createVerticalStrut(6));

        }

        leftBar.add(Box.createVerticalGlue());

        dialog.add(leftBar, BorderLayout.WEST);
        dialog.add(cardPanel, BorderLayout.CENTER);



        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
        navigator.show(HomePane.Name);

        return dialog;

    }

}
