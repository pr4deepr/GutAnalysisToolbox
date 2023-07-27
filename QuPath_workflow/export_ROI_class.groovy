//enter name of class you want to export
cell_class = "CalRet"

import ij.plugin.frame.RoiManager
def imageData = getCurrentImageData()

def name = GeneralTools.getNameWithoutExtension(imageData.getServer().getMetadata().getName())
def path = buildFilePath(PROJECT_BASE_DIR, cell_class+"_"+name+"_rois.zip")

calb = getDetectionObjects().findAll{it.getPathClass() == getPathClass(cell_class)}
def roiMan = new RoiManager(false)
double x = 0
double y = 0
double downsample = 1 // Increase if you want to export to work at a lower resolution
calb.each {
  def roi = IJTools.convertToIJRoi(it.getROI(), x, y, downsample)
  roiMan.addRoi(roi)
}
roiMan.runCommand("Save", path)