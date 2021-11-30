////https://qupath.readthedocs.io/en/stable/docs/advanced/stardist.html
import qupath.ext.stardist.StarDist2D

// Specify the model .pb file (you will need to change the name and location!)
def pathModel = 'D://2D_enteric_neuron_v4_1.pb'

def stardist = StarDist2D.builder(pathModel)
        .threshold(0.7)              // Probability (detection) threshold
        .channels("Hu")       // Select detection channel
        .normalizePercentiles(1, 99) // Percentile normalization
        .pixelSize(0.568)              // Resolution for detection
        .measureShape()              // Add shape measurements
        //.measureIntensity()          // Add cell measurements (in all compartments)
        .ignoreCellOverlaps(true) // Set to true if you don't care if cells expand into one another
        .includeProbability(true)    // Add probability as a measurement (enables later filtering)
        .classify("Hu")  
        .build()

// Run detection for the selected objects
def imageData = getCurrentImageData()
def pathObjects = getSelectedObjects()
if (pathObjects.isEmpty()) 
{
    Dialogs.showErrorMessage("StarDist", "Please select a parent object!")
    return
}
stardist.detectObjects(imageData, pathObjects)
println 'Done!'