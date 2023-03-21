//measure number of immediate neighbours for a single celltype

#@ File (label="Select the maximum projection image") max_proj
#@ File (label="Select roi manager for cells") roi_path
#@ File (label="Select roi manager for ganglia (Enter NA if none)",value="NA") roi_ganglia_path
#@ File (style="directory",label="Select Output Folder") save_path
#@ String (label="Name of celltype 1", description="Cell 1",value="Hu") cell_type
#@ String(value="<html>Expand cells by a certain distance so that they touch each other <br> and then count immediate neighbours (6.5 micron is default)<html>", visibility="MESSAGE") hint
#@ Double (value=6.5, min=1, max=15, stepSize = 0.5, style="slider",label="Cell expansion (microns)") label_dilation
#@ boolean save_parametric_image

run("Clear Results");
print("\\Clear");
run("Close All");

var fs=File.separator;

var fiji_dir=getDirectory("imagej");
var gat_dir=fiji_dir+"scripts"+fs+"GAT"+fs+"Tools"+fs+"commands";


//check if label to roi macro is present
var label_to_roi=gat_dir+fs+"Convert_Label_to_ROIs.ijm";
if(!File.exists(label_to_roi)) exit("Cannot find label to roi script. Returning: "+label_to_roi);

//check if roi to label macro is present
var roi_to_label=gat_dir+fs+"Convert_ROI_to_Labels.ijm";
if(!File.exists(roi_to_label)) exit("Cannot find roi to label script. Returning: "+roi_to_label);

//check if spatial analysis script is present
var spatial_single_cell_type=gat_dir+fs+"spatial_single_celltype.ijm";
if(!File.exists(spatial_single_cell_type)) exit("Cannot find single cell spatial analysis script. Returning: "+spatial_single_cell_type);


//open the image
open(max_proj);
file_name=File.nameWithoutExtension;
file_name_length=lengthOf(file_name);
if(file_name_length>50) file_name=substring(file_name, 0, 39); //Restricting file name length as in Windows long path names can cause errors


getPixelSize(unit, pixelWidth, pixelHeight);
temp=getTitle();
roiManager("reset");
//binary image for ganglia
ganglia_binary="";
setOption("BlackBackground", true);
if(File.exists(roi_ganglia_path))
{
	roiManager("open", roi_ganglia_path);
	//convert ROIs to label map image. It will use dimensions of max projection image above
	//run("ROI Manager to LabelMap(2D)");
	runMacro(roi_to_label);
	wait(10);
	run("Select None");
	run("Remove Overlay");
	
	//getMinAndMax(min, max);
	setThreshold(0.5, 65535);
	run("Convert to Mask");
	rename("Ganglia_outline");
	ganglia_binary=getTitle();
	run("Divide...", "value=255"); //binary image needs to have 0 and 1 as background and foreground, esp to use in clij
	setMinAndMax(0, 1);
	//waitForUser;
	roiManager("reset");
	
	
}
else ganglia_binary="NA";

roiManager("open", roi_path);
//run("ROI Manager to LabelMap(2D)");
runMacro(roi_to_label);
rename("Cell_labels");
wait(10);
run("Select None");
label_cell_img=getTitle();

//run script for single cell spatial analysis
args=cell_type+","+label_cell_img+","+ganglia_binary+","+save_path+","+label_dilation+","+save_parametric_image+","+pixelWidth;
runMacro(spatial_single_cell_type,args);
wait(5);
print("Files saved at "+save_path);

close("*");


exit("Neighbour Analysis complete");
