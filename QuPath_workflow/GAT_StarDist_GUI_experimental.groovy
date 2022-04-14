//GUI script for running StarDist segmentation in GAT (Experimental)
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
var pathObjects = getSelectedObjects()
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
Label label = new Label("Choose GAT StarDist Model");
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

//Define field for rescale factor
Label label2 = new Label("Enter Rescale Factor");
//global
def TextField rescale_factor_txt = new TextField("1");
rescale_factor_txt.setPrefWidth(100);
PaneTools.addGridRow(pane, 1, 0, "Rescale Factor",label2,rescale_factor_txt);
pane.setHgap(5);

//Define field for Probability
Label label3 = new Label("Enter Probability");
//global
def TextField prob_txt = new TextField("0.7");
prob_txt.setPrefWidth(100);
PaneTools.addGridRow(pane, 2, 0, "Probability",label3,prob_txt);
pane.setHgap(5);

Button runButton = new Button()
runButton.setText('Run')

PaneTools.addGridRow(pane, 3, 1, "Run",runButton);
pane.setHgap(5);

Label info = new Label("Close window when done.");
PaneTools.addGridRow(pane, 4, 1, "Close",info);

Platform.runLater  {
    def stage = new Stage()
    stage.initOwner(QuPathGUI.getInstance().getStage())
    stage.setScene(new Scene( pane))
    stage.setTitle("GAT Segmentation")
    stage.setWidth(400);
    stage.setHeight(175);
    //stage.setResizable(false);
    stage.show()
}


runButton.setOnAction 
{
    try {
        def double rescaling_factor = Double.parseDouble(rescale_factor_txt.getText());
        def double probability  = Double.parseDouble(prob_txt.getText());
    } catch (NumberFormatException nfe) {
        rescale_factor_txt.setText("ENTER A NUMBNER")
        return false;
    }
         
         
         
   def double rescaling_factor = Double.parseDouble(rescale_factor_txt.getText());
   def double probability  = Double.parseDouble(prob_txt.getText());
    print(file_path);
    print(rescaling_factor);
    print(probability);
    
    
    println "Using StarDist model: $file_path"
    


    
    if(rescaling_factor!=1)
    {
        target_pixel_size= 0.568/pixel_size;
    }
    else target_pixel_size= 0.568
    
    println "Using pixel size of $target_pixel_size"
    
    def channelNames = imageserver.getMetadata().getChannels().collect { c -> c.name }
    def channel_choice = Dialogs.showChoiceDialog("Segmentation channel","Choose segmentation channel",channelNames,0)
    print(channel_choice)
    
    print('Running Detection!')
    def stardist = StarDist2D.builder(file_path)
            .threshold(probability)              // Probability (detection) threshold
            .channels(channel_choice)       // Select detection channel
            .normalizePercentiles(1, 99) // Percentile normalization
            .pixelSize(target_pixel_size)              // Resolution for detection
            .measureShape()              // Add shape measurements
            //.measureIntensity()          // Add cell measurements (in all compartments)
            .ignoreCellOverlaps(true) // Set to true if you don't care if cells expand into one another
            .includeProbability(true)    // Add probability as a measurement (enables later filtering)
            .classify("Hu")  
            .build()
    
    stardist.detectObjects(imageData, pathObjects)
    print('Done!')
}
