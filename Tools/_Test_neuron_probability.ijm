var fs=File.separator;
setOption("ExpandableArrays", true);

run("Collect Garbage");


print("\\Clear");
run("Clear Results");

var fiji_dir=getDirectory("imagej");
var gat_dir=fiji_dir+"scripts"+fs+"GAT"+fs+"Tools"+fs+"commands";


gat_settings_path=gat_dir+fs+"gat_settings.ijm";
if(!File.exists(gat_settings_path)) exit("Cannot find settings file. Check: "+gat_settings_path);


//specify directory where StarDist models are stored
var models_dir=fiji_dir+"models"+fs;
//var models_dir=fiji_dir+"scripts"+fs+"GAT"+fs+"Models"+fs;


//check if label to roi macro is present
var label_to_roi=gat_dir+fs+"Convert_Label_to_ROIs.ijm";
if(!File.exists(label_to_roi)) exit("Cannot find label to roi script. Returning: "+label_to_roi);

//check if roi to label macro is present
var roi_to_label=gat_dir+fs+"Convert_ROI_to_Labels.ijm";
if(!File.exists(roi_to_label)) exit("Cannot find roi to label script. Returning: "+roi_to_label);




run("Results... ", "open="+gat_settings_path);
training_pixel_size=parseFloat(Table.get("Values", 0)); //0.7;
neuron_area_limit=parseFloat(Table.get("Values", 1)); //1500
neuron_seg_lower_limit=parseFloat(Table.get("Values", 2)); //90
neuron_lower_limit=parseFloat(Table.get("Values", 3)); //160

//probability=parseFloat(Table.get("Values", 5)); //prob neuron
//probability_subtype=parseFloat(Table.get("Values", 6)); //prob subtype

//overlap= parseFloat(Table.get("Values", 7));
//overlap_subtype=parseFloat(Table.get("Values", 8));

//get paths of model files
neuron_model_file = Table.getString("Values", 9);
neuron_subtype_file = Table.getString("Values", 10);
//neuron_deepimagej_file = Table.getString("Values", 13);
//neuron_subtype_deepimagej_file = Table.getString("Values", 14);//deepimagej model for neuron subtype

run("Close");

//Neuron segmentation model
neuron_subtype_path = models_dir+fs+neuron_subtype_file;
neuron_path = models_dir+fs+neuron_model_file;
if(!File.exists(neuron_subtype_path)) exit("Cannot find models for segmenting neuronal subtypes at these paths:\n"+neuron_subtype_path);
if(!File.exists(neuron_path)) exit("Cannot find models for segmenting neurons at these paths:\n"+neuron_path);

//stardist_postprocessing = neuron_path+fs+"stardist_postprocessing.ijm";
//if(!File.exists(stardist_postprocessing)) exit("Cannot find startdist postprocessing script. Returning: "+stardist_postprocessing);

//stardist_subtype_postprocessing = neuron_subtype_path+fs+"stardist_postprocessing.ijm";
//if(!File.exists(stardist_subtype_postprocessing)) exit("Cannot find startdist postprocessing script for neuron subtype. Returning: "+stardist_subtype_postprocessing);


#@ String(value="Evaluate Probability Thresholds", visibility="MESSAGE") prob_hint
#@ File (style="open", label="Choose the image to segment",value=fiji_dir) path
#@ boolean image_already_open
#@ String(choices={"Neuron Segmentation", "Neuron subtype segmentation"}, style="radioButtonHorizontal",label="Choose mode of segmentation") segmentation_type
// File (style="open", label="<html>Choose the StarDist model file based on celltype.<html>",value="NA") model_file 
//#@ String(value="Choose either XY pixel size (microns) or scaling factor (scales images by the specified factor)", visibility="MESSAGE") hint
//#@ String(choices={"Use pixel size", "Use a scaling factor"}, style="radioButtonHorizontal",label="Choose mode of segmentation") choice_scaling
#@ String(value="Test a range of probability thresholds to get the value with the most accurate cell segmentation. Default is 0.4.", visibility="MESSAGE") hint2
#@ Float (label="Enter minimum value", value=0.4, min=0.0500, max=0.99) prob_1
#@ Float (label="Enter maximum max value", value=0.9, min=0.0500, max=0.99) prob_2
#@ Float (label="Enter size of each increment step", value=0.05) step_prob

choice_scaling = "Use a scaling factor";

//#@ boolean Modify_StarDist_Values (description="Tick to modify the values within the StarDist plugin or defaults will be used.")
#@ String(value="<html>Default rescaling_factor is 1.0 and Overlap threshold is 0.3. Leave it as default when first trying this.<br/>More info about below parameters can be found here: https://www.imagej.net/StarDist/<html>",visibility="MESSAGE", required=false) hint34
#@ Double (label="Rescaling Factor", value=1.00, min=0.0500, max=10.0, stepSize=0.05) scale
#@ Double (label="Overlap Threshold", min=0, max=1, stepSize=0.05,value=0.3) overlap
#@ String(value="<html>If you are getting out of memory errors, you can try increasing the number of tiles.</html>",visibility="MESSAGE", required=false) tile_hint
#@ boolean Change_Tile
#@ Double (label="No. of tiles", style="spinner", min=1, stepSize=1,value=1) tile_manual 

if(prob_1>prob_2){
	print("Max probability should be greater than Min Probability. Check settings");
	exit("Max probability should be greater than Min Probability. Check settings");
}



cell_type="Cell";

if(segmentation_type=="Neuron Segmentation") 
{
	model_file=neuron_path;
	//stardist_postprocess = stardist_postprocessing;
}
if(segmentation_type=="Neuron subtype segmentation") 
{
	model_file=neuron_subtype_path;
	//stardist_postprocess = stardist_subtype_postprocessing;
}
//if(Use_pixel_size && Use_scaling_factor == true) exit("Choose only one option: Pixel size or Scaling factor");

//if(choice_scaling=="Use pixel size") Use_pixel_size=true;
//else if(choice_scaling=="Use a scaling factor") Use_pixel_size=false;

//modify_stardist=Modify_StarDist_Values;
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


run("Select None");
run("Remove Overlay");

print("**Neuron analysis: \nProbability range: "+prob_1+" to "+prob_2+"\nOverlap threshold: "+overlap);


series_stack=getTitle();


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

print("Pixel size of this image is: "+pixelWidth);
img_seg_array=newArray();
setOption("ExpandableArrays", true);
idx=0;

//multiplying by 10 to avoid errors with using float values
//start = scale_factor_1*10;
//end = scale_factor_2*10;
//increment = step_scale*10;

start = prob_1;
end = prob_2;
increment = step_prob;

if(start==end)
{
	
	increment=1;
}
//otherwise goes in infinite loop
if(increment==0)
{
	increment=0.1;
}

//Rescale factor applied to training pixel size of 0.568
//the target image is then rescale to the rescaled pixel size
//calculate scale_factor to be used
target_pixel_size= training_pixel_size/scale;
scale_factor = pixelWidth/target_pixel_size;
print("Target pixel size used "+target_pixel_size);

selectWindow(img);
img_seg = "Rescaled_"+cell_type;
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
	new_width=width;
	new_height=height;
}
selectWindow(img_seg);
getPixelSize(unit, pixelWidth_new, pixelHeight_new);
print(pixelWidth_new);

//choice=0;
if(Change_Tile) tiles = tile_manual;
else 
{ 
tiles=4;
if(new_width>2000 || new_height>2000) tiles=8;
if(new_width>5000 || new_height>5000) tiles=12;
else if (new_width>9000 || new_height>5000) tiles=30;
}
print("No. of tiles "+tiles);
	

print(start,end,increment);
//scale_factor_2+=0.00001; //makes sure last

for(i=start;i<=end;i+=increment)
{
	roiManager("reset");
	new_name = "Probability_"+i+"_"+cell_type;
	selectWindow(img_seg);
	run("Select None");
	run("Duplicate...", "title="+new_name);
	print("Running segmentation on image with probability "+i);
	//Pixel size decreases by scale factor
	//run segmentation
	run("Command From Macro", "command=[de.csbdresden.stardist.StarDist2D], args=['input':'"+new_name+"', 'modelChoice':'Model (.zip) from File', 'normalizeInput':'true', 'percentileBottom':'1.0', 'percentileTop':'99.8', 'probThresh':'"+i+"', 'nmsThresh':'"+overlap+"', 'outputType':'Both', 'modelFile':'"+model_file+"', 'nTiles':'"+tiles+"', 'excludeBoundary':'2', 'roiPosition':'Automatic', 'verbose':'false', 'showCsbdeepProgress':'false', 'showProbAndDist':'false'], process=[false]");
	//print("de.csbdresden.stardist.StarDist2D], args=['input':'"+new_name+"', 'modelChoice':'Model (.zip) from File', 'normalizeInput':'true', 'percentileBottom':'1.0', 'percentileTop':'99.8', 'probThresh':'"+i+"', 'nmsThresh':'"+overlap+"', 'outputType':'Both', 'modelFile':'"+model_file+"', 'nTiles':'"+tiles+"', 'excludeBoundary':'2', 'roiPosition':'Automatic', 'verbose':'false', 'showCsbdeepProgress':'false', 'showProbAndDist':'false'], process=[false]");
	
	//run("DeepImageJ Run", "modelPath=["+model_file+"] inputPath=null outputFolder=null displayOutput=all");
	wait(50);

	//make sure cells are detected.. if not exit macro
	if(roiManager("count")==0)
	{
		print("No cells detected for Proability: "+i);
		label_image=getTitle();
		selectWindow(new_name);
		run("Remove Overlay");
		roiManager("reset");
		close(label_image);
	}
	else 
	{
		roiManager("reset");
	 	wait(10);
		
		label_image=getTitle();
		selectWindow(label_image);
		run("Remove Overlay");
		//remove neurons below size limit
		run("Label Size Filtering", "operation=Greater_Than_Or_Equal size="+neuron_seg_lower_limit);
		label_filtered=getTitle();
		close(label_image);
		//run("glasbey_on_dark");
		selectWindow(label_filtered);
		runMacro(label_to_roi,label_filtered);
		//label_to_roi(label_filtered);
		wait(20);
		selectWindow(new_name);
		run("From ROI Manager");
		//close(label_image);
		selectWindow(new_name);
		img_seg_array[idx]=new_name;
		idx+=1;
		print("Probability: "+i);
		print("No of objects: "+roiManager("count"));
		close(label_filtered);
	}
}
run("Tile");
print("Verify the segmentation in the images: ");
close(img);

exit("Completed");

