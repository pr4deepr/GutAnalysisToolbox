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

//define save path based on file name and create a save folder if none
save_path=save_path+fs+"spatial_analysis"+fs;
if(!File.exists(save_path)) File.makeDirectory(save_path);

run("CLIJ2 Macro Extensions", "cl_device=");
Ext.CLIJ2_clear();

Ext.CLIJ2_push(label_cell_img);
// Dilate Labels

//label_dilation=9; //9 micron dilation
//convert to pixels
label_dilation=round(label_dilation/pixelWidth);
print("Expansion in pixels "+label_dilation);
//Ext.CLIJx_morphoLibJDilateLabels(label_cell_img, image_4, label_dilation);
Ext.CLIJ2_dilateLabels(label_cell_img, image_4, label_dilation);
Ext.CLIJ2_pull(label_cell_img);

if(isOpen(ganglia_binary))
{
	Ext.CLIJ2_push(ganglia_binary);
	Ext.CLIJ2_multiplyImages(image_4, ganglia_binary, label_ganglia_restrict);
	Ext.CLIJ2_release(image_4);
	Ext.CLIJ2_release(ganglia_binary);
	Ext.CLIJ2_touchingNeighborCountMap(label_ganglia_restrict, neighbour_count);
	Ext.CLIJ2_release(label_ganglia_restrict);
}
else 
{
	// Touching Neighbor Count Map
	Ext.CLIJ2_touchingNeighborCountMap(image_4, neighbour_count);
	
}

Ext.CLIJ2_pull(neighbour_count);

run("Fire");

run("Set Measurements...", "min redirect=None decimal=3");
selectWindow(neighbour_count);
roiManager("deselect");
roiManager("measure");
selectWindow("Results");
table_name="Neighbours "+file_name;
IJ.renameResults(table_name);
selectWindow(table_name);
Table.deleteColumn("Min");
Table.renameColumn("Max", "No of immediate neighbours");
table_name = "Neighbour_count_single_celltype_"+cell_type;
table_path=save_path+fs+table_name+".csv";
Table.save(table_path);
//close();

if(save_parametric_image)
{
	overlap_cell=get_parameteric_img(neighbour_count,label_cell_img);
	selectWindow(overlap_cell);
	saveAs("Tiff", save_path+fs+table_name);

}
close("*");


exit("Neighbour Analysis complete");


function get_parameteric_img(label_overlap_img,cell_img)
{
		run("CLIJ2 Macro Extensions", "cl_device=");
		Ext.CLIJ2_push(label_overlap_img);
		Ext.CLIJ2_push(cell_img);
		
		// covert label overlap img to binary
		constant = 1.0;
		Ext.CLIJ2_greaterOrEqualConstant(cell_img, label_bin, constant);
		// Multiply Images
		Ext.CLIJ2_multiplyImages(label_overlap_img, label_bin, parametric_img);
		Ext.CLIJ2_release(label_bin);
		Ext.CLIJ2_release(label_overlap_img);
		if(isOpen(label_overlap_img)) close(label_overlap_img);
		
		Ext.CLIJ2_pull(parametric_img);
		selectWindow(parametric_img);
		rename(label_overlap_img);
		parametric_img=getTitle();
		
		run("Fire");
		return parametric_img;


}

