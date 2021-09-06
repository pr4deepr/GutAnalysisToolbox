
#@ File (label="Select the maximum projection image") max_proj
#@ String (label="Name of celltype 1", description="Cell 1",value="cell_1") cell_type_1
#@ File (label="Select roi manager for cell 1") roi_path_1
#@ String (label="Name of celltype 2", description="Cell 2",value="cell_2") cell_type_2
#@ File (label="Select roi manager for cell 2") roi_path_2
#@ File (style="directory",label="Select Output Folder") save_path
#@ File (label="Select roi manager for ganglia (Enter NA if none)",value="NA") roi_ganglia_path
#@ String(value="<html>Expand cells by a certain distance so that they touch each other <br> and then count immediate neighbours (9 micron is default)<html>", visibility="MESSAGE") hint;
#@ Double (value=10, min=1, max=15, style="slider",label="Cell expansion distance for cells (microns)") label_dilation
#@ boolean save_parametric_image

run("Clear Results");
print("\\Clear");
run("Close All");

var fs=File.separator;

var fiji_dir=getDirectory("imagej");
var gat_dir=fiji_dir+"scripts"+fs+"GAT"+fs+"Other"+fs+"commands";


//check if label to roi macro is present
var label_to_roi=gat_dir+fs+"Convert_Label_to_ROIs.ijm";
if(!File.exists(label_to_roi)) exit("Cannot find label to roi script. Returning: "+label_to_roi);

//check if roi to label macro is present
var roi_to_label=gat_dir+fs+"Convert_ROI_to_Labels.ijm";
if(!File.exists(roi_to_label)) exit("Cannot find roi to label script. Returning: "+roi_to_label);



if(cell_type_1==cell_type_2 || roi_path_1== roi_path_2) exit("Same name or ROI Manager selected");


//open the image
open(max_proj);
file_name=File.nameWithoutExtension;
file_name_length=lengthOf(file_name);
if(file_name_length>50) file_name=substring(file_name, 0, 39); //Restricting file name length as in Windows long path names can cause errors


getPixelSize(unit, pixelWidth, pixelHeight);
if(unit!="microns") print("Image is not in calibrated in microns. Output maybe in pixels");
img=getTitle();	


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
	getMinAndMax(min, max);
	setThreshold(1, max);
	run("Convert to Mask");
	ganglia_binary=getTitle();
	run("Divide...", "value=255"); //binary image needs to have 0 and 1 as background and foreground, esp to use in clij
	resetMinAndMax;
	roiManager("reset");
	
}
else ganglia_binary="NA";



//define save path based on file name and create a save folder if none
save_path=save_path+fs+file_name;
if(!File.exists(save_path)) File.makeDirectory(save_path);


roiManager("reset");
roiManager("open", roi_path_1);
runMacro(roi_to_label);
wait(10);
rename(cell_type_1);
cell_1=getTitle();


roiManager("reset");
roiManager("open", roi_path_2);
runMacro(roi_to_label);
wait(10);
rename(cell_type_2);
cell_2=getTitle();
roiManager("reset");

//label_dilation=9; //9 micron dilation or whatever user enters
//convert to pixels
label_dilation=round(label_dilation/pixelWidth);

label_overlap_1=neighbour_count(cell_1,cell_2,label_dilation);
roiManager("open", roi_path_1);
run("Set Measurements...", "min redirect=None decimal=3");
roiManager("Measure");
selectWindow("Results");
cell_count_1=Table.getColumn("Max");

run("Clear Results");


label_overlap_2=neighbour_count(cell_2,cell_1,label_dilation);
roiManager("open", roi_path_2);
run("Set Measurements...", "min redirect=None decimal=3");
roiManager("Measure");
selectWindow("Results");
cell_count_2=Table.getColumn("Max");

run("Clear Results");

Table.create("Cell counts_overlap");
Table.setColumn("No of "+cell_1+" around "+cell_2, cell_count_1);
Table.setColumn("No of "+cell_2+" around "+cell_1, cell_count_2);
Table.update;


CONFIRM IF COUNTS ARE correct 
pass ganglia outline and use it in the neighbour count function 



function neighbour_count(ref_img,marker_img,dilate_radius,ganglia_outline)
{
	
	// Init GPU
	run("CLIJ2 Macro Extensions", "cl_device=");
	//Ext.CLIJ2_clear();
	Ext.CLIJ2_push(ref_img);
	Ext.CLIJ2_push(marker_img);

 if ganglia outline then multiply, 
	
	// Dilate Labels
	Ext.CLIJ2_dilateLabels(ref_img, ref_dilate, dilate_radius);
	
	// Dilate Labels
	Ext.CLIJ2_dilateLabels(marker_img, marker_dilate, dilate_radius);

	
	// Label Overlap Count Map
	Ext.CLIJ2_labelOverlapCountMap(ref_dilate, marker_dilate, label_overlap_count);
	
	Ext.CLIJ2_pull(label_overlap_count);

	run("Fire");
	return label_overlap_count;
}



same workflow as single celltype
but need to find no of cell 1 around 2 and 2 around 1