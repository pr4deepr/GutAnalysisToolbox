/*
 * Calculate the no of immediate neighbours around a user-defined radius (default: 10 micron) for 2 markers
 * Ideally, use cell_1 as the reference or pan cell marker, like Hu
 * and cell_2 as the marker labelling subset of cells
 * This can be interchanged as long as the names and roi manager files are selected appropriately
 * There is option to save parametric image
 */
#@ String(value="<html>If using a pan-neuronal marker, make sure to assign the marker that is pan-neuronal<html>", visibility="MESSAGE") hint23
#@ File (label="Select the maximum projection image") max_proj
#@ String (label="Name of celltype 1 ", description="Cell 1",value="cell_1") cell_type_1
#@ File (label="Select roi manager for cell 1") roi_path_1
#@ String (label="Name of celltype 2", description="Cell 2",value="cell_2") cell_type_2
#@ File (label="Select roi manager for cell 2") roi_path_2
#@ File (style="directory",label="Select Output Folder") save_path
#@ File (label="Select roi manager for ganglia (Enter NA if none)",value="NA") roi_ganglia_path

#@ String(value="<html>If using a pan-neuronal marker, tick the box and select the corresponding celltype<html>", visibility="MESSAGE") hint69
#@ boolean Assign_as_pan_neuronal
#@ String (choices={"Cell 1", "Cell 2"}, style="radioButtonHorizontal") pan_neuronal_choice

#@ String(value="<html>Expand cells by a certain distance so that they touch each other <br> and then count immediate neighbours (6.5 micron is default)<html>", visibility="MESSAGE") hint
#@ Double (value=6.5, min=1, max=15, style="slider",label="Cell expansion distance for cells (microns)") label_dilation
#@ boolean save_parametric_image

run("Clear Results");
print("\\Clear");
run("Close All");

roiManager("reset");

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
var spatial_two_cell_type=gat_dir+fs+"spatial_two_celltype.ijm";
if(!File.exists(spatial_two_cell_type)) exit("Cannot find spatial analysis script. Returning: "+spatial_two_cell_type);

//check if spatial analysis script is present
var spatial_hu_marker_cell_type=gat_dir+fs+"spatial_hu_marker.ijm";
if(!File.exists(spatial_hu_marker_cell_type)) exit("Cannot find hu_marker spatial analysis script. Returning: "+spatial_hu_marker_cell_type);


if(cell_type_1==cell_type_2 || roi_path_1== roi_path_2) exit("Cell names or ROI managers are the same for both celltypes");

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




roiManager("reset");
roiManager("open", roi_path_1);
runMacro(roi_to_label);
wait(10);
rename(cell_type_1);
cell_1=getTitle();
roiManager("show none");
run("Select None");
run("Remove Overlay");
roiManager("reset");

roiManager("open", roi_path_2);
runMacro(roi_to_label);
wait(10);
rename(cell_type_2);
cell_2=getTitle();
roiManager("show none");
roiManager("reset");
run("Select None");
run("Remove Overlay");

//Assign cells as pan-neuronal

if(Assign_as_pan_neuronal) 
{
	print("Pan-neuronal spatial analysis enabled");
	if(pan_neuronal_choice == "Cell 1")
	{
		hu_name = cell_type_1;
		hu_label_img = cell_1;
		
	}
	else
	{
		hu_name = cell_type_2;
		hu_label_img = cell_2;
		
		cell_type_2 = cell_type_1;
		cell_2 = cell_1;
	}
	args=hu_name+","+hu_label_img+","+cell_type_2+","+cell_2+","+ganglia_binary+","+save_path+","+label_dilation+","+save_parametric_image+","+pixelWidth+","+roi_path_1;
	runMacro(spatial_hu_marker_cell_type,args);
	wait(5);
}
else 
{
	print("Spatial analysis for two celltypes");
	args=cell_type_1+","+cell_1+","+cell_type_2+","+cell_2+","+ganglia_binary+","+save_path+","+label_dilation+","+save_parametric_image+","+pixelWidth+","+roi_path_1+","+roi_path_2;
	runMacro(spatial_two_cell_type,args);
	wait(5);
}






exit("Neighbour Analysis complete (Two celltypes)");

