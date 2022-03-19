var fs=File.separator;
setOption("ExpandableArrays", true);

run("Collect Garbage");

print("\\Clear");
run("Clear Results");

var fiji_dir=getDirectory("imagej");
var gat_dir=fiji_dir+"scripts"+fs+"GAT"+fs+"Tools"+fs+"commands";

//check if roi to label macro is present
var roi_to_label=gat_dir+fs+"Convert_ROI_to_Labels.ijm";
if(!File.exists(roi_to_label)) exit("Cannot find roi to label script. Returning: "+roi_to_label);

#@ File (label="Select the maximum projection or 2D image") max_proj
#@ boolean image_already_open
#@ File (label="Select roi manager for cells") roi_path
#@ String(value="<html>Neuron soma will be expanded by specified distance.<br>Objects touching each other will form a ganglia<html>", visibility="MESSAGE") hint
#@ String(value="Test a range of values for images to figure out the right one that gives accurate ganglia. Default is 12 micron.", visibility="MESSAGE") hint2
#@ Double (label="Enter minimum value", value=10, min=0.0500, max=50.000) label_dilation_1
#@ Double (label="Enter maximum max value", value=15, min=0.0500, max=50.000) label_dilation_2
#@ Double (label="Enter increment step/s", value=0.1) step_scale

cell_type = "Hu";

if(image_already_open==true)
{
	waitForUser("Select Image to segment (Image already open was selected)");//. Remember to choose output folder in next prompt");
	file_name=getTitle(); //get file name without extension (.lif)
	//dir=getDirectory("Choose Output Folder");
}
else
{
	if(endsWith(max_proj, ".czi")|| endsWith(max_proj, ".lif")) run("Bio-Formats", "open=["+max_proj+"] color_mode=Composite rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT");
	else if (endsWith(max_proj, ".tif")|| endsWith(max_proj, ".tiff")) open(max_proj);
	else exit("File type not recognised.  Tif, Lif and CZI files supported.");
	dir=File.directory;
	file_name=File.nameWithoutExtension; //get file name without extension (.lif)

}

series_stack=getTitle();


file_name_length=lengthOf(file_name);
if(file_name_length>50) file_name=substring(file_name, 0, 39); //Restricting file name length as in Windows long path names can cause errors

run("Select None");
run("Remove Overlay");
Stack.getDimensions(width, height, sizeC, sizeZ, frames);
getPixelSize(unit, pixelWidth, pixelHeight);

if(unit!="microns") exit("Image not calibrated in microns. Please go to ANalyse->SetScale or Image->Properties to set it for the image");

roiManager("reset");
//binary image for ganglia
ganglia_binary="NA";
setOption("BlackBackground", true);

if(sizeZ>1)
	{
	print(series_stack+" is a stack");
	roiManager("reset");
	waitForUser("Note the start and end of the stack.\nPress OK when done");
	Dialog.create("Choose slice range");
	Dialog.addNumber("Start slice", 1);
	Dialog.addNumber("End slice", sizeZ);
	Dialog.show(); 
	start=Dialog.getNumber();
	end=Dialog.getNumber();
	run("Z Project...", "start=&start stop=&end projection=[Max Intensity]");
	max_projection=getTitle();
}
else 
{
	print(series_stack+" has only one slice, assuming its max projection");
	max_projection=getTitle();
}



if(sizeC>1)
{
	waitForUser("Check image to select the right channel");
	channel_seg=getNumber("Enter channel number for "+cell_type, 1);
	selectWindow(max_projection);
	run("Select None");
	run("Remove Overlay");
	//run("Duplicate...", "title="+cell_type+" duplicate channels="+channel_seg);
	run("Duplicate...", "title="+cell_type+" duplicate channels="+channel_seg);
	img=getTitle();
}
else 
{
	selectWindow(max_projection);
	run("Select None");
	run("Remove Overlay");
	run("Duplicate...", "title="+cell_type);
	img=getTitle();
}

//replace file separator so  stardist can identify right file

print("Pixel size of this image is: "+pixelWidth);
roiManager("open", roi_path);
//run("ROI Manager to LabelMap(2D)");
runMacro(roi_to_label);
rename("Cell_labels");
wait(10);
run("Select None");
label_cell_img=getTitle();

run("CLIJ2 Macro Extensions", "cl_device=");
Ext.CLIJ2_clear();
selectWindow(label_cell_img);
run("Select None");
dilate_img = "label_dil_temp";
run("Duplicate...", "title="+dilate_img);
Ext.CLIJ2_push(dilate_img);
roiManager("reset");
print("**************");
	
for(dilate=label_dilation_1;dilate<=label_dilation_2;dilate+=step_scale)
{
	roiManager("reset");
	selectWindow(img);
	run("Select None");
	img_ref = "img_dilate_"+d2s(dilate,2);
	run("Duplicate...", "title="+img_ref);
	label_dilation=round(dilate/pixelWidth);
	print("Expansion in pixels "+label_dilation);
	print("Corresponding expansion in microns "+dilate);
	print("**************");
	Ext.CLIJ2_dilateLabels(dilate_img, dilated, label_dilation);
	Ext.CLIJ2_greaterOrEqualConstant(dilated, ganglia_binary, 1);
	Ext.CLIJ2_release(dilated);
	ganglia_labels = "label_dil_"+d2s(dilate,0);
	Ext.CLIJ2_connectedComponentsLabelingDiamond(ganglia_binary, ganglia_labels);
	Ext.CLIJ2_release(ganglia_binary);
	//close(ganglia_binary);
	Ext.CLIJ2_pullLabelsToROIManager(ganglia_labels);
	Ext.CLIJ2_release(ganglia_labels);
	//close(ganglia_labels);
	//waitForUser;
	
	selectWindow(img_ref);
	roiManager("show all with labels");
	selectWindow(img_ref);
	run("From ROI Manager");
		

}

Ext.CLIJ2_clear();

close(dilate_img);
close(label_cell_img);
run("Cascade");
print("Verify the segmentation in the images: ");
close(img);
exit("Done");
