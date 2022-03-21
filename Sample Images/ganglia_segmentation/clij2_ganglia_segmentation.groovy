//reloadable clij script for demonstrating how  
// Read more: https://clij.github.io/clij2-assistant/save_and_load

// Generator version: 2.5.1.4



import ij.IJ;
import net.haesleinhuepf.clij2.CLIJ2;
#@ String(value="<html>Demonstration of ganglia segmentation using Hu labelling.<br>Choose a label image where each neuron cell body as a unique colour.<html>", visibility="MESSAGE") hint1

#@ File (label="Select the label image with segmented neurons") label_img_path

// clean up first
IJ.run("Close All");

// Init GPU
clij2 = CLIJ2.getInstance();
clij2.clear();

// disable automatic window positioning 
was_auto_position = net.haesleinhuepf.clij2.assistant.AbstractAssistantGUIPlugin.isAutoPosition();
net.haesleinhuepf.clij2.assistant.AbstractAssistantGUIPlugin.setAutoPosition(false);

// Load image from disc 
image_1 = net.haesleinhuepf.clij2.assistant.utilities.AssistantUtilities.openImage(label_img_path.getAbsolutePath());
//image_1 = clij2.push(label_img);
image_1.setC(1);
image_1.setZ(1);
image_1.setT(1);
image_1.setTitle("Neuron_label_map");
image_1.show();
// copy
node = new net.haesleinhuepf.clij2.assistant.AssistantGUIStartingPoint();
node.setSources(image_1);
node.run("");
node.refreshDialogFromArguments();
node.setTargetInvalid();
// set window position and size
window = node.getTarget().getWindow();
window.setLocation(125, 139);
window.setSize(367, 401);
window.getCanvas().setMagnification(0.3333333333333333);
image_copy_2 = node.getTarget();
java.lang.Thread.sleep(500);
IJ.run("In [+]");
IJ.run("Out [-]");

// dilateLabels
node = new net.haesleinhuepf.clij2.assistant.interactive.generic.GenericAssistantGUIPlugin(new net.haesleinhuepf.clij2.plugins.DilateLabels());
node.setSources(image_copy_2);
node.run("");
node.getArgs()[2] = 10.0;
node.refreshDialogFromArguments();
node.setTargetInvalid();
// set window position and size
window = node.getTarget().getWindow();
window.setLocation(561, 136);
window.setSize(367, 401);
window.getCanvas().setMagnification(0.3333333333333333);
image_dilate_labels_3 = node.getTarget();
java.lang.Thread.sleep(500);
IJ.run("In [+]");
IJ.run("Out [-]");

// greaterOrEqualConstant
node = new net.haesleinhuepf.clij2.assistant.interactive.generic.GenericAssistantGUIPlugin(new net.haesleinhuepf.clij2.plugins.GreaterOrEqualConstant());
node.setSources(image_dilate_labels_3);
node.run("");
node.getArgs()[2] = 1.0;
node.refreshDialogFromArguments();
node.setTargetInvalid();
// set window position and size
window = node.getTarget().getWindow();
window.setLocation(834, 236);
window.setSize(538, 572);
window.getCanvas().setMagnification(0.3333333333);
image_greater_or_equal_constant_4 = node.getTarget();
java.lang.Thread.sleep(500);
IJ.run("In [+]");
IJ.run("Out [-]");

// connectedComponentsLabelingDiamond
node = new net.haesleinhuepf.clij2.assistant.interactive.generic.GenericAssistantGUIPlugin(new net.haesleinhuepf.clij2.plugins.ConnectedComponentsLabelingDiamond());
node.setSources(image_greater_or_equal_constant_4);
node.run("");
node.refreshDialogFromArguments();
node.setTargetInvalid();
// set window position and size
window = node.getTarget().getWindow();
window.setLocation(924, 283);
window.setSize(367, 401);
window.getCanvas().setMagnification(0.3333333333333333);
image_connected_components_labeling_diamond_5 = node.getTarget();
java.lang.Thread.sleep(500);
IJ.run("In [+]");
IJ.run("Out [-]");


// reset auto-positioning
IJ.wait(500);
net.haesleinhuepf.clij2.assistant.AbstractAssistantGUIPlugin.setAutoPosition(was_auto_position);

