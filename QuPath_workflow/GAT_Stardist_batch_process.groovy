////This script can be used to batch process an entire project.
//However, user will need to manually specify the path, rescaling factor and channel_choice (channel name)
//The channel name  for segmentation has to be consistent for all images in the project
//
import qupath.ext.stardist.StarDist2D
import qupath.lib.gui.dialogs.Dialogs


//setImageType('FLUORESCENCE');
//createSelectAllObject(true);

//************CHANGE DETAILS HERE*****************

//Enter path to the model here
def pathModel = "D:/qupath_models/2D_enteric_neuron_v4_1.pb"

//Enter rescaling factor
def rescaling_factor = "0.8"

//Enter channel name here
def channel_choice = "Hu"

//*************************************

println "Using StarDist model: $pathModel"

// Run detection for the selected objects
def imageData = getCurrentImageData()

//get image server and the corresponding calibration
def imageserver = getCurrentServer()
print("Processing: "+imageserver.getMetadata().getName())
def cal = imageserver.getPixelCalibration()
pixel_size = cal.getPixelHeightMicrons()

if(pixel_size == Double.NaN)
{
    print ("Pixel size is empty, please enter pixel size in microns");
    return
}

if(rescaling_factor!=1)
{
    target_pixel_size= 0.568/pixel_size;
}
else target_pixel_size= 0.568

println "Using pixel size of $target_pixel_size"

def stardist = StarDist2D.builder(pathModel)
        .threshold(0.7)              // Probability (detection) threshold
        .channels(channel_choice)       // Select detection channel
        .normalizePercentiles(1, 99) // Percentile normalization
        .pixelSize(target_pixel_size)              // Resolution for detection
        .measureShape()              // Add shape measurements
        //.measureIntensity()          // Add cell measurements (in all compartments)
        .ignoreCellOverlaps(true) // Set to true if you don't care if cells expand into one another
        .includeProbability(true)    // Add probability as a measurement (enables later filtering)
        .classify("Hu")  
        .build()

var pathObjects = getSelectedObjects()

stardist.detectObjects(imageData, pathObjects)
println 'Done!'