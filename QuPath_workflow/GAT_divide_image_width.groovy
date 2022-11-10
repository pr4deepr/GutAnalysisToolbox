
//If you'd like to study distribution of cells across the lenght of the tissue, you can use this script to divide image annotation into tiles
//Create full image annotation
//Perform detection using GAT script first
// Run this script to divide the annotation into tiles of specified width (microns). You can specify tile width below



//CHANGE WIDTH HERE (//in microns)
//***************
user_width = 100 
//***************

import qupath.lib.gui.dialogs.Dialogs

println("Make sure you have a full image annotation using Objects -> Annotations ->Create full image annotation")

def pathObjects = getSelectedObjects()
if (pathObjects.isEmpty()) {
Dialogs.showErrorMessage("GAT", "Please select a full image annotation!")
return
}
//Create some sort of non-rectangular annotation first!

def imageserver = getCurrentServer()
def cal = imageserver.getPixelCalibration()
pixel_size = cal.getPixelHeightMicrons()

tissue = getAnnotationObjects()[0]

roi = tissue.getROI()

def plane = getCurrentViewer().getImagePlane()

total_width = roi.getBoundsWidth()
total_height = roi.getBoundsHeight()
println(user_width)
user_width = user_width/pixel_size
println(user_width)
no_tiles_init = total_width/user_width
no_tiles= Math.ceil(no_tiles_init)
last_tile_frac = 1-(no_tiles - no_tiles_init)
println(no_tiles)
println(no_tiles_init)
println(last_tile_frac)
x_top = 0
y_top = 0
for (i = 0; i < no_tiles; i++) {
if(i==no_tiles-1 && last_tile_frac>0)
{
  user_width*=last_tile_frac
}

boundingROI = ROIs.createRectangleROI(
            x_top, //top left corner
            0, //top left corner
            user_width, //width
            total_height, //height
            plane)
x_top+=user_width;
boundingAnnotation = PathObjects.createAnnotationObject(boundingROI)
if(i<9)
{
  tile_name = 'Tile 00' 
}
else if (i<100)
{
  tile_name = 'Tile 0'
}
else tile_name = 'Tile ' 

boundingAnnotation.setName(tile_name + (i+1))
addObject(boundingAnnotation)
}

//resolve heirarchy to cells belong to each tile
resolveHierarchy()