/*
 * Calculate the no of immediate neighbours around a user-defined radius (default: 10 micron) for 2 markers
 * Ideally, use cell_1 as the reference or pan cell marker, like Hu
 * and cell_2 as the marker labelling subset of cells
 * This can be interchanged as long as the names and roi manager files are selected appropriately
 * There is option to save parametric image
 */

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

roiManager("reset");

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


//label_dilation=9; //9 micron dilation or whatever user enters
//convert to pixels
label_dilation=round(label_dilation/pixelWidth);

label_overlap_1=neighbour_count(cell_1,cell_2,label_dilation,ganglia_binary,cell_type_1,cell_type_2);

roiManager("open", roi_path_1);
run("Set Measurements...", "min redirect=None decimal=3");
roiManager("Measure");
selectWindow("Results");
cell_count_1=Table.getColumn("Max");

run("Clear Results");


label_overlap_2=neighbour_count(cell_2,cell_1,label_dilation,ganglia_binary,cell_type_2,cell_type_1);
roiManager("open", roi_path_2);
run("Set Measurements...", "min redirect=None decimal=3");
roiManager("Measure");
selectWindow("Results");
cell_count_2=Table.getColumn("Max");

run("Clear Results");
table_path=save_path+fs+file_name+".csv";

Table.create("Cell counts_overlap");
Table.setColumn("No of "+cell_type_2+" around "+cell_type_1, cell_count_1);
Table.setColumn("No of "+cell_type_1+" around "+cell_type_2, cell_count_2);
Table.update;
Table.save(table_path);


if(save_parametric_image==true)
{
	overlap_cell_1=get_parameteric_img(label_overlap_1,cell_1);
	overlap_cell_2=get_parameteric_img(label_overlap_2,cell_2);


	selectWindow(overlap_cell_1);
	saveAs("Tiff", save_path+fs+overlap_cell_1);
	selectWindow(overlap_cell_2);
	saveAs("Tiff", save_path+fs+overlap_cell_2);
}
close("*");

function neighbour_count(ref_img,marker_img,dilate_radius,ganglia_outline,cell_type_1,cell_type_2)
{
	
	// Init GPU
	run("CLIJ2 Macro Extensions", "cl_device=");
	//Ext.CLIJ2_clear();
	Ext.CLIJ2_push(ref_img);
	Ext.CLIJ2_push(marker_img);
 	//
	
	// Dilate Labels
	Ext.CLIJ2_dilateLabels(ref_img, ref_dilate, dilate_radius);
	
	// Dilate Labels
	Ext.CLIJ2_dilateLabels(marker_img, marker_dilate, dilate_radius);

	if(ganglia_outline!="NA")
	{
		Ext.CLIJ2_push(ganglia_outline);
		
		Ext.CLIJ2_mask(ref_dilate, ganglia_outline, ref_dil_ganglia); 
		Ext.CLIJ2_mask(marker_dilate, ganglia_outline, marker_dil_ganglia);

		//Ext.CLIJ2_pull(ref_dil_ganglia);
		//Ext.CLIJ2_pull(marker_dil_ganglia);
		//exit("error message");
		Ext.CLIJ2_release(ref_dilate);
		Ext.CLIJ2_release(marker_dilate);
		
		//as we are using a mask, it may clip cells and we want to get rid of any incomplete cells . We are using circularity as a way for this
		//Any cells that are less than 0.4 circularity; get rid of them
		Ext.CLIJx_morphoLibJCircularityMap(ref_dil_ganglia, ref_circularity);
		
		// Exclude Labels With Values Out Of Range
		minimum_value_range = 0.4;
		maximum_value_range = 1.0;
		Ext.CLIJ2_excludeLabelsWithValuesOutOfRange(ref_circularity, ref_dil_ganglia, ref_dilate1, minimum_value_range, maximum_value_range);

		//image_2
		Ext.CLIJx_morphoLibJCircularityMap(marker_dil_ganglia, marker_circularity);
		Ext.CLIJ2_excludeLabelsWithValuesOutOfRange(marker_circularity, marker_dil_ganglia, marker_dilate1, minimum_value_range, maximum_value_range);
		
		Ext.CLIJ2_release(marker_circularity);
		Ext.CLIJ2_release(ref_circularity);
		// Label Overlap Count Map
		Ext.CLIJ2_labelOverlapCountMap(ref_dilate1, marker_dilate1, label_overlap_count);
		
	}
	else  Ext.CLIJ2_labelOverlapCountMap(ref_dilate, marker_dilate, label_overlap_count);

	Ext.CLIJ2_pull(ref_img);
	Ext.CLIJ2_pull(marker_img);
	
	Ext.CLIJ2_pull(label_overlap_count);
	rename("No of "+cell_type_2+" around "+cell_type_1);
	label_overlap_count=getTitle();
	run("Fire");
	return label_overlap_count;
}


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
		
		return parametric_img;


}
