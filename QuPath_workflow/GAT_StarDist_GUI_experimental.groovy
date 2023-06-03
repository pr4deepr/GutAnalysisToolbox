//GUI script for running StarDist segmentation in GAT (Experimental)

//add option to combine multiple class in same annotation
//Hu:Chat etc...
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
//Define field for Probability
Label resc_label = new Label("Enter Rescale Factor");
//min,max,initialvalue,amount to step by
Spinner resc_Spinner = new Spinner(new SpinnerValueFactory.DoubleSpinnerValueFactory(0, 5, 1.0, 0.05));
resc_Spinner.setEditable(true);
//global
def rescaling_factor= resc_Spinner.valueProperty();
PaneTools.addGridRow(pane, 1, 0, "Rescale Factor",resc_label,resc_Spinner);
pane.setHgap(5);

//Define field for Probability
Label prob_label = new Label("Enter Probability");
//min,max,initialvalue,amount to step by
Spinner probSpinner = new Spinner(new SpinnerValueFactory.DoubleSpinnerValueFactory(0, 1, 0.7, 0.05));
probSpinner.setEditable(true);
//global
def probability = probSpinner.valueProperty();
PaneTools.addGridRow(pane, 2, 0, "Probability",prob_label,probSpinner);
pane.setVgap(5);

Label cell_class_label = new Label("Enter name of cell");
def TextField cell_class = new TextField();
cell_class.setPrefWidth(50);
PaneTools.addGridRow(pane, 4, 0, "Cell name", cell_class_label, cell_class);
pane.setHgap(5);

Label ch_label = new Label("Choose channel for segmentation");
def channelList = imageserver.getMetadata().getChannels().collect { c -> c.name }
def channelCombo = new ComboBox();
channelCombo.getItems().addAll(channelList);
PaneTools.addGridRow(pane, 5, 0, "Channels", ch_label, channelCombo);
pane.setHgap(5);

//get classes  set for getting one label and convert to a list
def classList = new ArrayList(getCurrentHierarchy().getDetectionObjects().collect{it.getPathClass()} as Set)
classList.add (0,"Base_Class") //Base Class is when running for first time 
Label class_label = new Label("Choose the class (If first detection, leave as base class)");
def classCombo = new ComboBox();
classCombo.getItems().addAll(classList);
PaneTools.addGridRow(pane, 6, 0, "Classes", class_label, classCombo);
pane.setHgap(5);

Button runButton = new Button()
runButton.setText('Run')
PaneTools.addGridRow(pane, 7, 1, "Run",runButton);
pane.setHgap(5);



Platform.runLater  {
    def stage = new Stage()
    stage.initOwner(QuPathGUI.getInstance().getStage())
    stage.setScene(new Scene( pane))
    stage.setTitle("GAT Segmentation")
    stage.setWidth(600);
    stage.setHeight(270);
    //stage.setResizable(false);
    stage.show()
}


runButton.setOnAction 
{

   //def double probability  = Double.parseDouble(prob_txt.getText());
    print(file_path);
    print("Detecting cells of class "+cell_class.getText())
    println "Rescaling Factor: "+ rescaling_factor.get()
    println "Probability: "+ probability.get()
    println "Channel: "+ channelCombo.getValue()
    
    println "Using StarDist model: $file_path"
    

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
            //.cellExpansion(0.0)          // Approximate cells based upon nucleus expansion
            //.cellConstrainScale(1.5)     // Constrain cell expansion using nucleus size  
            .build()
    
    stardist.detectObjects(imageData, pathObjects)
    viewer = getCurrentViewer()
    detections = viewer.getHierarchy().getDetectionObjects()
    def path_class_user = classCombo.getValue().toString()
    //FIX THIS BIT
    //https://www.imagescientist.com/creating-a-classifier
    class_choice = getPathClass(path_class_user)
    println class_choice.toString()
    if(class_choice.toString()!="Base_Class")
    {
        //FIX THIS TO ASSIGN VALUES TO EACH CELL
        println "CHoice is not base"
        //define segmented cell class
        //segmented_class = getPathClass(cell_class.getText())
        parent_class = detections.findAll{it.getPathClass() == class_choice}
        
        segmented_class = cell_class.getText()
        println parent_class
        detections.each{
     //it.getPathClass()
         if (it.getPathClass() == class_choice.toString() )
              {it.setPathClass(getDerivedPathClass(it.getPathClass(), segmented_class, getColorRGB(0,150,150)) )}
                      }
    }
    else 
    //assign each detection to the base class as defined by user
    {
    detections.each{
                baseClass = getPathClass(cell_class.getText())
                it.setPathClass(baseClass)
                    }
}


}
fireHierarchyUpdate()
println "DONE"
//classList = getCurrentHierarchy().getDetectionObjects().collect{it.getPathClass()} as Set
