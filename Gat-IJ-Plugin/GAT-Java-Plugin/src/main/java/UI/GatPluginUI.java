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

public class GatPluginUI implements PlugIn {

    private CardLayout cards = new CardLayout();
    private JPanel cardPanel = new JPanel(cards);
    Navigator navigator = name -> cards.show(cardPanel,name);



    @Override
    public void run(String arg){
        String expectedNeuronModel  = "2D_enteric_neuron_V4_1.zip";
        String expectedSubtypeModel = "2D_enteric_neuron_subtype_V4.zip";

        if (!UI.Preflight.runAll(expectedNeuronModel, expectedSubtypeModel)) {
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

        // register your dashboard pane
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
