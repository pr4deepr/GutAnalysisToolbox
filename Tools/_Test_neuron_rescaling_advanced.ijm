var fs=File.separator;
setOption("ExpandableArrays", true);

print("\\Clear");
run("Clear Results");

var fiji_dir=getDirectory("imagej");
var gat_dir=fiji_dir+"scripts"+fs+"GAT"+fs+"Tools"+fs+"commands";
var models_dir=fiji_dir+"models"+fs;

gat_settings_path=gat_dir+fs+"gat_settings.ijm";
if(!File.exists(gat_settings_path)) exit("Cannot find settings file. Check: "+gat_settings_path);



run("Results... ", "open="+gat_settings_path);
training_pixel_size=parseFloat(Table.get("Values", 0)); //0.7;
neuron_area_limit=parseFloat(Table.get("Values", 1)); //1500
neuron_seg_lower_limit=parseFloat(Table.get("Values", 2)); //90
neuron_lower_limit=parseFloat(Table.get("Values", 3)); //160
//neuron_model_file = Table.getString("Values", 9);
run("Close");

//check if label to roi macro is present
var label_to_roi=gat_dir+fs+"Convert_Label_to_ROIs.ijm";
if(!File.exists(label_to_roi)) exit("Cannot find label to roi script. Returning: "+label_to_roi);

//model_file = models_dir+fs+neuron_model_file;
//if(!File.exists(model_file)) exit("Cannot find models for segmenting neurons at these paths:\n"+model_file);

#@ String(value="<html>The main difference with this macro is you can try a custom StarDist model.<html>",visibility="MESSAGE", required=false) DJ_model_hint
#@ String(value="<html>It expects a StarDist trained model, so nms postprocessing will be applied automatically.<html>",visibility="MESSAGE", required=false) more_hint
#@ File (style="open", label="Choose the image to segment") path
#@ boolean image_already_open
// String(choices={"Neuron", "Glia"}, style="list") cell_type
#@ File (style="open", label="<html>Choose the StarDist model based on celltype.<html>",required=true,value="PATH TO MODEL FILE") model_file 
#@ String(value="Test a range of rescaling factors to get the value with the most accurate cell segmentation. Default is 1.", visibility="MESSAGE") hint2
#@ Float (label="Enter minimum value", value=1.00, min=0.0500, max=10.0) scale_factor_1
#@ Float (label="Enter maximum max value", value=1.50, min=0.0500, max=10.0) scale_factor_2
#@ Float (label="Enter size of each increment step", value=0.2500) step_scale
choice_scaling = "Use a scaling factor";

//#@ boolean Modify_StarDist_Values (description="Tick to modify the values within the StarDist plugin or defaults will be used.")
#@ String(value="<html>Default Probability is 0.5 and Overlap threshold is 0.5. Leave it as default when first trying this.<br/>More info about below parameters can be found here: https://www.imagej.net/StarDist/<html>",visibility="MESSAGE", required=false) hint34
#@ Double (label="Probability (if staining is weak, use low values)", style="slider", min=0, max=1, stepSize=0.05,value=0.55) probability
#@ Double (label="Overlap Threshold", style="slider", min=0, max=1, stepSize=0.05,value=0.5) overlap
#@ String(value="<html>If you are getting out of memory errors, you can try increasing the number of tiles.<html>",visibility="MESSAGE", required=false) tile_hint
#@ boolean Change_Tile
#@ Double (label="No. of tiles", style="spinner", min=1, stepSize=1,value=1) tile_manual 

cell_type="Cell";
print("\\Clear");

if(image_already_open==true)
{
	waitForUser("Select Image to segment (Image already open was selected)");//. Remember to choose output folder in next prompt");
	file_name=getTitle(); 
	selectWindow(file_name);
	close_other_images = getBoolean("Close any other open images?", "Close others", "Keep other images open");
	if(close_other_images)	close("\\Others");
	//dir=getDirectory("Choose Output Folder");
}
else
{
	if(endsWith(path, ".czi")|| endsWith(path, ".lif")) run("Bio-Formats", "open=["+path+"] color_mode=Composite rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT");
	else if (endsWith(path, ".tif")|| endsWith(path, ".tiff")) open(path);
	else exit("File type not recognised.  Tif, Lif and CZI files supported.");
	dir=File.directory;
	file_name=File.nameWithoutExtension; //get file name without extension (.lif)

}


//open(path);
run("Select None");
run("Remove Overlay");


print("**Neuron analysis: \nProbability: "+probability+"\nOverlap threshold: "+overlap);


//file_name=File.nameWithoutExtension; //get file name without extension (.lif)

series_stack=getTitle();
//series_stack=getTitle();

dotIndex = indexOf(series_stack, "." );
if(dotIndex>=0) file_name = substring(series_stack,0, dotIndex);
else file_name=series_stack;

Stack.getDimensions(width, height, sizeC, sizeZ, frames);
getPixelSize(unit, pixelWidth, pixelHeight);

neuron_seg_lower_limit=neuron_seg_lower_limit/pixelWidth; 
neuron_max_pixels=neuron_area_limit/pixelWidth; //convert micron to pixels


if(unit!="microns") exit("Image not calibrated in microns. Please go to ANalyse->SetScale or Image->Properties to set it for the image");




roiManager("reset");
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
else {
	selectWindow(max_projection);
	run("Select None");
	run("Remove Overlay");
	run("Duplicate...", "title="+cell_type);
	img=getTitle();
}

//replace file separator so  stardist can identify right file
model_file=replace(model_file, "\\\\","\\\\\\\\\\\\\\\\");

img_seg_array=newArray();
setOption("ExpandableArrays", true);
idx=0;
//multiplying by 10 to avoid errors with using float values
start = scale_factor_1*10;
end = scale_factor_2*10;
increment = step_scale*10;

//scale_factor_2+=0.00001; //makes sure last

if(start==end)
{
	
	increment=1;
}
//otherwise goes in infinite loop
if(increment==0)
{
	increment=1;
}
print(start,end,increment);
for(i=start;i<=end;i+=increment)
{
	//print("Running segmentation on image scaled by: "+scale);
	//dividing by 10 to get actual scale value
	scale = i/10;
	
	roiManager("reset");
	selectWindow(img);
	//Rescale factor applied to training pixel size of 0.568
	//the target image is then rescale to the rescaled pixel size
	scale_name="Recaling_factor";
	target_pixel_size= training_pixel_size/scale;
	scale_factor = pixelWidth/target_pixel_size;
	img_seg=scale_name+"_"+scale+"_"+cell_type;
	//print("Calculated "+scale_name+" of: "+scale_factor);
	img_seg=scale_name+"_"+scale+"_"+cell_type;
	print("Running segmentation on image scaled by "+scale_name+" of: "+scale);
	print("Target pixel size used "+target_pixel_size);
	
	if(scale_factor!=1)
	{	
		new_width=round(width*scale_factor); 
		new_height=round(height*scale_factor);
		//print(img_seg);
		run("Scale...", "x=- y=- width="+new_width+" height="+new_height+" interpolation=None create title="+img_seg);
	}
	else 
	{
		selectWindow(img);
		run("Select None");
		run("Duplicate...", "title="+img_seg);
	}
	//choice=0;
	selectWindow(img_seg);
	if(Change_Tile) tiles = tile_manual;
	else 
	{ 
	tiles=4;
	if(new_width>2000 || new_height>2000) tiles=8;
	if(new_width>5000 || new_height>5000) tiles=12;
	else if (new_width>9000 || new_height>5000) tiles=20;
	}
	print("No. of tiles "+tiles);
	//run segmentation
	run("Command From Macro", "command=[de.csbdresden.stardist.StarDist2D],args=['input':'"+img_seg+"', 'modelChoice':'Model (.zip) from File', 'normalizeInput':'true', 'percentileBottom':'1.0', 'percentileTop':'99.8', 'probThresh':'"+probability+"', 'nmsThresh':'"+overlap+"', 'outputType':'Both', 'modelFile':'"+model_file+"', 'nTiles':'"+tiles+"', 'excludeBoundary':'2', 'roiPosition':'Automatic', 'verbose':'false', 'showCsbdeepProgress':'false', 'showProbAndDist':'false'], process=[false]");

	//make sure cells are detected.. if not exit macro
	if(roiManager("count")==0) exit("No cells detected. Reduce probability or check image.\nAnalysis stopped");
	else roiManager("reset");

	label_image=getTitle();
	selectWindow(label_image);
	run("Remove Overlay");
	run("Label Size Filtering", "operation=Greater_Than_Or_Equal size="+neuron_seg_lower_limit);
	label_filtered=getTitle();
	close(label_image);
	
	//run("Remove Border Labels", "left right top bottom");
	//wait(10);
	//rename("Label-killBorders_"+scale);
	run("glasbey_on_dark");
	//run("LabelMap to ROI Manager (2D)");
	selectWindow(label_filtered);
	runMacro(label_to_roi,label_filtered);
	wait(20);
	selectWindow(img_seg);
	run("From ROI Manager");
	//close(label_image);
	selectWindow(img_seg);
	img_seg_array[idx]=img_seg;
	idx+=1;
	print("No of objects: "+roiManager("count"));
	
}

run("Cascade");
print("Verify the segmentation in the images");
exit("Completed");
//Array.print(img_seg_array);

