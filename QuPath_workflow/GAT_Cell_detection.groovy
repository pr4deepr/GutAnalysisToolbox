//GUI for working with StarDist segmentation
//This script only works with H-DAB images and uses both channels for 
//detecting cells. 

import qupath.lib.gui.dialogs.Dialogs
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.layout.GridPane;
import javafx.scene.control.Button;
import qupath.lib.gui.tools.PaneTools;
import javafx.scene.control.TextField;
import javafx.event.EventHandler;
import javafx.event.ActionEvent;
import javafx.scene.Scene
import qupath.ext.stardist.StarDist2D
import javafx.application.Platform
import javafx.scene.control.Spinner
import javafx.scene.control.ComboBox
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.CheckBox
import qupath.lib.gui.scripting.QPEx.*
// Run detection for the selected objects
def imageData = getCurrentImageData()
    
//get image server and the corresponding calibration
def imageserver = getCurrentServer()
if(imageserver == null)
{
        print ("No Image opened");
        return
}


def cal = imageserver.getPixelCalibration()
def pathObjects = getSelectedObjects()
if (pathObjects.isEmpty()) {
Dialogs.showErrorMessage("StarDist", "Please select a parent object!")
return
}
pixel_size = cal.getPixelHeightMicrons()
    
if(pixel_size == Double.NaN)
    {
        print ("Pixel size is empty, please enter pixel size in microns");
        return
}

GridPane pane = new GridPane();
//Label for the Choose File field
Label label = new Label("Choose StarDist Model");
//Create a text field for Choose File
TextField tf = new TextField();
tf.setPrefWidth(50);
 
Button button = new Button("Choose File");
        button.setOnAction(new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent e) {
    			File file = promptForFile(null);
            	if (file != null)
            		tf.setText(file.getAbsolutePath());
            		file_path = file.getAbsolutePath()
            }
        });

PaneTools.addGridRow(pane, 0, 0, "Input URL or choose file", label, tf, button);
pane.setHgap(5);


//Define field for Probability
Label prob_label = new Label("Enter Probability");
//min,max,initialvalue,amount to step by
Spinner probSpinner = new Spinner(new SpinnerValueFactory.DoubleSpinnerValueFactory(0, 1, 0.7, 0.05));
probSpinner.setEditable(true);
//global
def probability = probSpinner.valueProperty();
PaneTools.addGridRow(pane, 1, 0, "Probability",prob_label,probSpinner);
pane.setVgap(5);

Label resc_label = new Label("Enter Rescale Factor");
//min,max,initialvalue,amount to step by
Spinner resc_Spinner = new Spinner(new SpinnerValueFactory.DoubleSpinnerValueFactory(0, 5, 1.0, 0.05));
resc_Spinner.setEditable(true);
//global
def rescaling_factor= resc_Spinner.valueProperty();
PaneTools.addGridRow(pane, 2, 0, "Rescale Factor",resc_label,resc_Spinner);
pane.setHgap(5);


Label ch_label = new Label("Choose channel for segmentation");
def channelList = imageserver.getMetadata().getChannels().collect { c -> c.name }
def channelCombo = new ComboBox();
channelCombo.getItems().addAll(channelList);
PaneTools.addGridRow(pane, 3, 0, "Channels", ch_label, channelCombo);
pane.setHgap(5);

//Filter by Area
Label size_filtering = new Label("Filter cells by area below");
PaneTools.addGridRow(pane, 4, 0, "minimum area (um2)", size_filtering);
pane.setHgap(5);

//min_area
Label min_area_label = new Label("Enter minimum area of cells (um2)");
Spinner min_area_Spinner = new Spinner(new SpinnerValueFactory.DoubleSpinnerValueFactory(1, 200, 90, 1));
min_area_Spinner.setEditable(true);
//global
def min_area = min_area_Spinner.valueProperty();
PaneTools.addGridRow(pane, 5, 0, "minimum area (um2)", min_area_label, min_area_Spinner);
pane.setHgap(5);

//max_area
Label max_area_label = new Label("Enter maximum area of cells (um2)");
Spinner max_area_Spinner = new Spinner(new SpinnerValueFactory.DoubleSpinnerValueFactory(1, 10000, 1000, 1));
max_area_Spinner.setEditable(true);
//global
def max_area = max_area_Spinner.valueProperty();
PaneTools.addGridRow(pane, 6, 0, "maximum area (um2)", max_area_label, max_area_Spinner);
pane.setHgap(5);

//Name of cell
Label cell_class_label = new Label("Enter name of cell");
def TextField cell_class = new TextField("Hu");
cell_class.setPrefWidth(50);
PaneTools.addGridRow(pane, 7, 0, "Cell name", cell_class_label, cell_class);
pane.setHgap(5);

def Button runButton = new Button()
runButton.setText('Run')
PaneTools.addGridRow(pane, 8, 1, "Run",runButton);
pane.setHgap(5);



runButton.setOnAction{
 
    println file_path
    print("Detecting cells of class "+cell_class.getText())
    println "Rescaling Factor: "+ rescaling_factor.get()
    println "Probability: "+ probability.get()
    println "Using StarDist model: $file_path"
    println "Channel: "+ channelCombo.getValue()
    
    // calculate target pixel size by dividing the pixel sized used for training images in GAT (0.568 um/pixel) by rescaling factor
    target_pixel_size= 0.568/rescaling_factor.get();
    
    println "Using pixel size of $target_pixel_size"

    println 'Running Detection!'
    def stardist = StarDist2D.builder(file_path)
                    .threshold(probability.get())              // Probability (detection) threshold
                    .channels(channelCombo.getValue())       // Select detection channel
                    .normalizePercentiles(1, 99) // Percentile normalization
                    .pixelSize(target_pixel_size)              // Resolution for detection
                    .measureShape()              // Add shape measurements
                    .measureIntensity()          // Add cell measurements (in all compartments)
                    .ignoreCellOverlaps(true) // Set to true if you don't care if cells expand into one another
                    .includeProbability(true)    // Add probability as a measurement (enables later filtering)
                    .classify(cell_class.getText())
                    .build()
    
    stardist.detectObjects(imageData, pathObjects)
    println 'Detection done!'
    
    var min_size = min_area.get()
    var max_size = max_area.get()
    println "Min area: $min_size"
    println "Min area: $max_size"

    
    viewer = getCurrentViewer()
    detections = viewer.getHierarchy().getDetectionObjects()
    println detections
    def toDelete = detections.findAll {measurement(it, 'Area µm^2') < min_size || measurement(it, 'Area µm^2') > max_size}
    println toDelete
    viewer.getHierarchy().removeObjects(toDelete, true)
    println toDelete.size() + ' cells deleted based on size'
    
    
    Dialogs.showMessageDialog("StarDist","Detection complete");

}

Platform.runLater  {
    def stage = new Stage()
    stage.initOwner(QuPathGUI.getInstance().getStage())
    stage.setScene(new Scene( pane))
    stage.setTitle("GAT: Cell Detection")
    stage.setWidth(600);
    stage.setHeight(300);
    stage.show()
}