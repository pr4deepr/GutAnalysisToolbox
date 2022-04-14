////https://qupath.readthedocs.io/en/stable/docs/advanced/stardist.html
import qupath.ext.stardist.StarDist2D
import qupath.lib.gui.dialogs.Dialogs


def pathModel = Dialogs.promptForFile("StarDist Model",null,"GAT StarDist Model",".pb")
def rescaling_factor = Dialogs.showInputDialog("GAT", "Enter Rescaling Factor", 1.0)

println "Using StarDist model: $pathModel"

// Run detection for the selected objects
def imageData = getCurrentImageData()

//get image server and the corresponding calibration
def imageserver = getCurrentServer()
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

def channelNames = imageserver.getMetadata().getChannels().collect { c -> c.name }
def channel_choice = Dialogs.showChoiceDialog("Segmentation channel","Choose segmentation channel",channelNames,0)



def stardist = StarDist2D.builder(pathModel.getPath())
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