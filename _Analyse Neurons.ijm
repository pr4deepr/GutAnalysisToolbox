
//*******
// Author: Pradeep Rajasekhar
// March 2023
// License: BSD3
// 
// Copyright 2023 Pradeep Rajasekhar, Walter and Eliza Hall Institute of Medical Research, Melbourne, Australia
// 
// Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
// 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
// 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.



var fs=File.separator;
setOption("ExpandableArrays", true);

print("\\Clear");

var fiji_dir=getDirectory("imagej");
var gat_dir=fiji_dir+"scripts"+fs+"GAT"+fs+"Tools"+fs+"commands";

//specify directory where StarDist models are stored
var models_dir=fiji_dir+"models"+fs;
//var models_dir=fiji_dir+"scripts"+fs+"GAT"+fs+"Models"+fs;


//settings for GAT
gat_settings_path=gat_dir+fs+"gat_settings.ijm";
if(!File.exists(gat_settings_path)) exit("Cannot find settings file. Check: "+gat_settings_path);
run("Results... ", "open="+gat_settings_path);
training_pixel_size=parseFloat(Table.get("Values", 0)); //0.7;
neuron_area_limit=parseFloat(Table.get("Values", 1)); //1500
neuron_seg_lower_limit=parseFloat(Table.get("Values", 2)); //90
neuron_lower_limit=parseFloat(Table.get("Values", 3)); //160
probability=parseFloat(Table.get("Values", 5)); //prob neuron
overlap= parseFloat(Table.get("Values", 7));
//get paths of model files
ganglia_model = Table.getString("Values", 12);
neuron_model_file = Table.getString("Values", 9);
neuron_deepimagej_file = Table.getString("Values", 13);
selectWindow("Results");
run("Close");


//Neuron segmentation model
neuron_model_path=models_dir+fs+neuron_model_file;
//neuron_deepimagej_path = models_dir+fs+neuron_deepimagej_file;
ganglia_model_path = models_dir+fs+ganglia_model;
//print("Deepimagej model for neuron:"+neuron_deepimagej_path);

if(!File.exists(neuron_model_path)) exit("Cannot find models for segmenting neurons at these paths:\n"+neuron_model_path);
if(!File.exists(ganglia_model_path)) exit("Cannot find models for segmenting ganglia at this paths:\n"+ganglia_model_path);


//check if required plugins are installed
var check_plugin=gat_dir+fs+"check_plugin.ijm";
if(!File.exists(check_plugin)) exit("Cannot find check plugin macro. Returning: "+check_plugin);
runMacro(check_plugin);

//check if label to roi macro is present
var label_to_roi=gat_dir+fs+"Convert_Label_to_ROIs.ijm";
if(!File.exists(label_to_roi)) exit("Cannot find label to roi script. Returning: "+label_to_roi);

//check if roi to label macro is present
var roi_to_label=gat_dir+fs+"Convert_ROI_to_Labels.ijm";
if(!File.exists(roi_to_label)) exit("Cannot find roi to label script. Returning: "+roi_to_label);

//check if ganglia cell count is present
var ganglia_cell_count=gat_dir+fs+"Calculate_Neurons_per_Ganglia.ijm";
if(!File.exists(ganglia_cell_count)) exit("Cannot find ganglia cell count script. Returning: "+ganglia_cell_count);

//check if ganglia prediction  macro present
var segment_ganglia=gat_dir+fs+"Segment_Ganglia.ijm";
if(!File.exists(segment_ganglia)) exit("Cannot find segment ganglia script. Returning: "+segment_ganglia);

//check if ganglia hu expansion macro present
var ganglia_hu_expansion=gat_dir+fs+"ganglia_hu.ijm";
if(!File.exists(ganglia_hu_expansion)) exit("Cannot find hu expansion script. Returning: "+ganglia_hu_expansion);
 
 
//check if spatial analysis script is present
var spatial_single_cell_type=gat_dir+fs+"spatial_single_celltype.ijm";
if(!File.exists(spatial_single_cell_type)) exit("Cannot find single cell spatial analysis script. Returning: "+spatial_single_cell_type);

//check if import custom ganglia rois script is present
var ganglia_custom_roi=gat_dir+fs+"ganglia_custom_roi.ijm";
if(!File.exists(ganglia_custom_roi)) exit("Cannot find single ganglia custom roi script. Returning: "+ganglia_custom_roi);

//check if import save centroids script is present
var save_centroids=gat_dir+fs+"save_centroids.ijm";
if(!File.exists(save_centroids)) exit("Cannot find save_centroids custom roi script. Returning: "+save_centroids);

//check if import ganglia fix missing neurons script is present
var ganglia_fix_missing_neurons=gat_dir+fs+"ganglia_fix_missing_neurons.ijm";
if(!File.exists(ganglia_fix_missing_neurons)) exit("Cannot find ganglia_fix_missing_neurons custom roi script. Returning: "+ganglia_fix_missing_neurons);

//check if rename_rois script is present
var rename_rois=gat_dir+fs+"rename_rois.ijm";
if(!File.exists(rename_rois)) exit("Cannot find rename_rois custom roi script. Returning: "+rename_rois);

//check if save_roi_composite_img is present
var save_composite_img=gat_dir+fs+"save_roi_composite_img.ijm";
if(!File.exists(save_composite_img)) exit("Cannot find save_composite_img custom roi script. Returning: "+save_composite_img);

//stardist_postprocessing = neuron_deepimagej_path+fs+"stardist_postprocessing.ijm";
//if(!File.exists(stardist_postprocessing)) exit("Cannot find startdist postprocessing script. Returning: "+stardist_postprocessing);



#@ File (style="open", label="<html>Choose the image to segment.<br><b>Enter NA if image is open or if field is empty.</b><html>", value=fiji_dir) path
#@ boolean image_already_open
#@ String(value="<html>If image is already open, tick above box.<html>", visibility="MESSAGE") hint1
#@ String(label="Enter channel number for Hu if you know. Enter NA if not using.", value="NA") cell_channel
#@ String(value="<html>----------------------------------------------------------------------------------------------------------------------------------------<html>",visibility="MESSAGE") hint_star
#@ String(value="<html><center><b>DETERMINE GANGLIA OUTLINE</b></center> <html>",visibility="MESSAGE") hint_ganglia
#@ String(value="<html> Cell counts per ganglia will be calculated<br/>Requires a neuron channel & second channel that labels the neuronal fibres.<html>",visibility="MESSAGE") hint4
#@ boolean(description="<html> Use a pretrained deepImageJ model to predict ganglia outline <html>") Cell_counts_per_ganglia
#@ String(choices={"DeepImageJ","Define ganglia using Hu","Manually draw ganglia","Import custom ROI"}, style="radioButtonHorizontal") Ganglia_detection
#@ String(label="<html> Enter the channel number for segmenting ganglia.<br/> Not valid for 'Define ganglia using Hu and Import custom ROI'.<br/> Enter NA if not using.<html> ", value="NA") ganglia_channel
#@ String(value="<html>----------------------------------------------------------------------------------------------------<html>",visibility="MESSAGE") adv
#@ boolean(description="<html><b>If ticked, it will perform spatial analysis for all markers. Convenient than performing them individually. -> </b><html>") Perform_Spatial_Analysis
#@ boolean (description="<html><b>Adjust Probabilities or import custom ROIs</b><html>") Finetune_Detection_Parameters
#@ boolean(description="<html><b>Contribute to GAT by saving image and masks</b><html>") Contribute_to_GAT
#@ String(description="<html><b>Used for batch analysis, leave as NA if not using</b><html>",value="NA",persist=false) batch_parameters



cell_type="Neuron";
scale = 1;

//print all arguments passed
print("Image path: "+path);
print("Channel for cell: "+cell_channel);
print("Calculate cell count per ganglia: "+Cell_counts_per_ganglia);
print("Ganglia detection method: "+Ganglia_detection);
print("Channel for ganglia: "+ganglia_channel);
print("Perform spatial analysis: "+Perform_Spatial_Analysis);
print("Contribute image masks to GAT?: "+Contribute_to_GAT);
print("Finetune detection parameters: "+Finetune_Detection_Parameters);
print("Batch parameters passed: "+batch_parameters);
print("\n");


//option to accept parameters if calling from a macro/script and want to use batch mode as these values are entered interactively
if(batch_parameters!="NA")
{
//batch parameters are expected in the order
//custom_ganglia_roi_path, label_dilation, save_parameteric_image,scale,probability,overlap,img_masks_path, batch_analysis
 batch_array = split(batch_parameters, ",");
 print("Running in batch mode: ");
 print("Arguments passed");

 //print("BATCH "+batch_array.length);
 batch_length = batch_array.length;
 if(batch_length<7) exit("Batch arguments must be 8. If not using batch mode, leave the batch parameters as NA.\nGot "+batch_length+" parameters and batch arguments:\n "+batch_parameters);
 ganglia_roi_path = batch_array[0];
 label_dilation = batch_array[1];
 label_dilation= parseFloat(label_dilation);
 save_parametric_image = batch_array[2];
 scale = batch_array[3];
 scale=parseFloat(scale);
 probability= batch_array[4];
 probability=parseFloat(probability);
 overlap=batch_array[5];
 overlap=parseFloat(overlap);
 img_masks_path=batch_array[6];
 batch_mode=true;

 print("Ganglia ROI path: "+ganglia_roi_path);
 print("Label dilation value: "+label_dilation);
 print("Save parameteric image from spatial analysis: "+save_parametric_image);
 print("Probability value for segmentation: "+probability);
 print("Overlap factor for segmentation: "+overlap);
 print("If contribute to GAT is selected, path to save image masks: "+img_masks_path);
 
  
}
else batch_mode=false;


if(Finetune_Detection_Parameters==true && batch_parameters=="NA")
{
	print("Using manual probability and overlap threshold for detection");
	Dialog.create("Advanced Parameters");
	Dialog.addMessage("Default values shown below will be used if no changes are made");
	Dialog.addNumber("Rescaling Factor", scale, 3, 8, "") 
	//Dialog.addSlider("Rescaling Factor", 0, 1,1.00);
  	Dialog.addSlider("Probability of detecting neurons (Hu)", 0, 1,probability);	
  	Dialog.addSlider("Overlap threshold", 0, 1,overlap);
  	//add checkbox to same row as slider
  	Dialog.addToSameRow();
  	Dialog.addCheckbox("Custom ROI", 0);
	Dialog.show(); 
	scale = Dialog.getNumber();
	probability= Dialog.getNumber();
	custom_roi_hu = Dialog.getCheckbox();
	overlap= Dialog.getNumber();
}
else custom_roi_hu=false;

if(Contribute_to_GAT==true)
{
	waitForUser("You can contribute to improving GAT by saving images and masks,\nand sharing it so our deep learning models have better accuracy\nGo to 'Help and Support' button under GAT to get in touch");
	img_masks_path = getDirectory("Choose a Folder to save the images and masks");
	Save_Image_Masks = true;
}
else 
{
	Save_Image_Masks = false;
}

//listing parameters being used for GAT
print("Using parameters\nSegmentation pixel size:"+training_pixel_size+"\nMax neuron area (microns): "+neuron_area_limit+"\nMin Neuron Area (microns): "+neuron_seg_lower_limit+"\nMin marker area (microns): "+neuron_lower_limit);
print("**Neuron\nProbability: "+probability+"\nOverlap threshold: "+overlap);

if(image_already_open==true)
{
	waitForUser("Select an open Image to analyze, then choose where to save the data");
	file_name_full=getTitle(); //get file name without extension (.lif)
	selectWindow(file_name_full);
	close_other_images = getBoolean("Close any other open images?", "Close others", "Keep other images open");
	if(close_other_images)	close("\\Others");
	dir=getDirectory("Choose Output Folder");
}
else
{
	if(endsWith(path, ".czi")) run("Bio-Formats", "open=["+path+"] color_mode=Composite rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT");
	else if (endsWith(path, ".lif"))
	{
		run("Bio-Formats Macro Extensions");
		Ext.setId(path);
		Ext.getSeriesCount(seriesCount);
		print("Opening lif file, detected series count of "+seriesCount+". Leave options in bioformats importer unticked");
		open(path);
		
	}
	else if (endsWith(path, ".tif")|| endsWith(path, ".tiff")) open(path);
	else exit("File type not recognised.  GAT is compatible with Tif, Lif and Czi files.");
	
	dir=File.directory;
	file_name_full=File.nameWithoutExtension; //get file name without extension (.lif)
}


img_name=getTitle();
Stack.getDimensions(width, height, sizeC, sizeZ, frames);

run("Select None");
run("Remove Overlay");

getPixelSize(unit, pixelWidth, pixelHeight);

//Check image properties************
//Check if RGB
if (bitDepth()==24)
{
	print("Image type is RGB. It is NOT recommended to\nconvert the image to RGB. Instead, use the raw \noutput from the microscope (which is usually in 8,12 or 16-bit)\n.");
	rgb_prompt = getBoolean("Image is RGB. It is recommended to use 8,12 or 16-bit images. Would you like to try converting to 8-bit and proceed?", "Convert to 8-bit", "No, stop analysis");
	if(rgb_prompt ==1)
	{
		print("Converting to 8-bit");
		selectWindow(img_name);
		run("8-bit");
	}
	else exit("User terminated analysis as Image is RGB.");
}


//check if unit is microns or micron
unit=String.trim(unit);

if(unit!="microns" && unit!="micron" && unit!="um" )
{
	print("Image is not calibrated in microns. This is required for accurate segmentation");
	exit("Image must have pixel size in microns.\nTo fix this: Go to Image -> Properties: And enter the correct pixel size in microns.\nYou can get this information from the microscope settings.\nCannot proceed: STOPPING Analysis");
}
//************

//Training images were pixelsize of ~0.568,
//scale_factor=pixelWidth/training_pixel_size;
target_pixel_size= training_pixel_size/scale;
scale_factor = pixelWidth/target_pixel_size;
if(scale_factor<1.001 && scale_factor>1) scale_factor=1;


//file_name=File.nameWithoutExtension;
file_name_length=lengthOf(file_name_full);
if(file_name_length>50)
{
	file_name=substring(file_name_full, 0, 20); //Restricting file name length as in Windows long path names can cause errors
	Dialog.create("The file name is too long, instead write a Custom Identifier for this Image");
	Dialog.addString("Custom Identifier", "_1");
	Dialog.addMessage("For example, writing '_1' as the custom identifier \n will name the final data output as ImageName_1");
	Dialog.show();
	suffix = Dialog.getString();
	file_name = file_name+suffix;
}
else file_name=file_name_full;

//create analysis directory if it doesn't exist
analysis_dir= dir+"Analysis"+fs;
if (!File.exists(analysis_dir)) File.makeDirectory(analysis_dir);


//file_name=File.nameWithoutExtension;
file_name_length=lengthOf(file_name_full);
//if delimiters such as , ; or _ are there in file name, split string and join with underscore
file_name_split = split(file_name_full,",;_-");
file_name_full =String.join(file_name_split,"_");

//check if save location exists. if it does, ask user to enter a suffix to append to directory name
save_location_exists = 1;

if(batch_parameters!="NA")
{
	print("Filename will be shortened if its too long");
	if(file_name_full.length>20) file_name_full=substring(file_name_full, 0, 20); //Restricting file name length as in Windows long path names can cause errors
	suffix = "_batch";
	file_name = file_name_full+suffix;
	suffix_no=1;
	//make save_dir and if it already exists, add a number at the end and increment till its 
	do 
	{
		
		results_dir=analysis_dir+file_name+fs; //directory to save images
		if (!File.exists(results_dir)) 
		{
			File.makeDirectory(results_dir); //create directory to save results file
			save_location_exists = 0;
		}
		else 
		{
			print("The save folder already exists. Creating a new save folder path");
			file_name = file_name_full+suffix+"_"+suffix_no;
			save_location_exists = 1;
			suffix_no+=1;
		}
	}
	while(save_location_exists==1)

}
else 
{
	
	do
	{
		if(file_name_length>50 ||save_location_exists == 1)
		{		
			print("Filename will be shortened if its too long");
			if(file_name_full.length>20) file_name_full=substring(file_name_full, 0, 20); //Restricting file name length as in Windows long path names can cause errors
			// if save location already exists, then this logic can also be used to add suffix to filename
			if(save_location_exists == 1)
			{ 
				dialog_title = "Save location already exists ";
				dialog_message_1 = "Save location exists, use a custom identifier.\n For example, writing '_1' as the custom identifier \n will name the final folder as ImageName_1";
			}
			else if(file_name_length>50)
			{
				dialog_title = "Filename too long";
				dialog_message = "Shortening it to 20 characters.\n Use a custom identifier. For example, writing '_1' as the custom identifier \n will name the final folder as ImageName_1";
			}
			Dialog.create(dialog_title);
			Dialog.addString("Custom Identifier", "_1");
			Dialog.addMessage(dialog_message_1);
			Dialog.show();
			suffix = Dialog.getString();
	
			file_name = file_name_full+suffix;
			save_location_exists = 0;
		}
		else file_name=file_name_full;
		
		results_dir=analysis_dir+file_name+fs; //directory to save images
		//if file exists in location, create one and set save_location_exists flag to zero to exit the loop
		if (!File.exists(results_dir)) 
		{
			File.makeDirectory(results_dir); //create directory to save results file
			save_location_exists = 0;
		}
		else 
		{
			waitForUser("The save folder already exists, enter a new name in next prompt");
			save_location_exists = 1;
		}
	
	}
	while(save_location_exists==1)
}

print("Analysing: "+file_name);
print("Files will be saved at: "+results_dir); 

//do not include cells greater than 1000 micron in area
//neuron_area_limit=1500; //microns
neuron_max_pixels=neuron_area_limit/pixelWidth; //convert micron to pixels

//using limit when segmenting neurons
//neuron_seg_lower_limit=90;//microns
neuron_seg_lower_limit=neuron_seg_lower_limit/pixelWidth; 


table_name="Analysis_"+cell_type+"_"+file_name;
Table.create(table_name);//Final Results Table
row=0; //row counter for the table
image_counter=0;

//parse cell and ganglia channels and check if value is Integer
if(cell_channel!="NA")
{
	cell_channel=parseInt(cell_channel);
	if(isNaN(cell_channel)) exit("Enter which channel number to use for "+cell_type+" segmentation. If leaving empty, type NA in the value");
	
}



channel_list = Array.getSequence(sizeC);
//add 1 to every value so channel no starts at 1
channel_list = add_value_array(channel_list,1);

//if more than one channel , check if appropriate values entered
if(sizeC>1 && Ganglia_detection!="Define ganglia using Hu")
{
 if (Cell_counts_per_ganglia==true && cell_channel=="NA" && ganglia_channel=="NA") //count cells per ganglia but don't know channels for ganglia or neuron
	{
		waitForUser("Enter which channels to use for NEURON and GANGLIA segmentation in the next prompt.");
		//get active channel
		Stack.getPosition(active_channel, active_slice, active_frame);
		Dialog.create("Choose Segmentation Channels");
		Dialog.addChoice("Enter which channel to use for "+cell_type+" segmentation", channel_list, active_channel);
		Dialog.addChoice("Enter which channel to use for ganglia segmentation", channel_list, active_channel);
  		//Dialog.addNumber("Enter which channel to use for "+cell_type+" segmentation", 3);
  		//Dialog.addNumber("Enter which channel to use for ganglia segmentation", 2);
  		Dialog.show(); 
		cell_channel= parseInt(Dialog.getChoice());//Dialog.getNumber();
		ganglia_channel=parseInt(Dialog.getChoice());//Dialog.getNumber();
		Stack.setChannel(cell_channel);
		resetMinAndMax();
		Stack.setChannel(ganglia_channel);
		resetMinAndMax();		
	}
	else if(Cell_counts_per_ganglia==true && cell_channel!="NA" && ganglia_channel=="NA") //count cells per ganglia but don't know channels for ganglia
	{
		waitForUser("Enter which channels to use for GANGLIA segmentation in the next prompt.");
		//get active channel
		Stack.getPosition(active_channel, active_slice, active_frame);
		Dialog.create("Choose Segmentation Channels");
  		//Dialog.addNumber("Enter which channel to use for ganglia segmentation", 2);
  		Dialog.addChoice("Enter which channel to use for ganglia segmentation", channel_list, active_channel);
  		Dialog.show(); 
		//cell_channel= Dialog.getNumber();
		ganglia_channel=parseInt(Dialog.getChoice());//Dialog.getNumber();
		//Stack.setChannel(cell_channel);
		//resetMinAndMax();
		Stack.setChannel(ganglia_channel);
		resetMinAndMax();	
	}
		else if(Cell_counts_per_ganglia==true && cell_channel=="NA" && ganglia_channel!="NA") //count cells per ganglia but don't know channels for neuron
	{
			waitForUser("Enter which channels to use for "+cell_type+" segmentation in the next prompt.");
			//get active channel
			Stack.getPosition(active_channel, active_slice, active_frame);
			Dialog.create("Choose Segmentation Channels");
	  		Dialog.addChoice("Enter which channel to use for "+cell_type+" segmentation", channel_list, active_channel);
	  		//Dialog.addNumber("Enter which channel to use for "+cell_type+" segmentation", 3);
	  	    Dialog.show(); 
			cell_channel= parseInt(Dialog.getChoice());//Dialog.getNumber();
			Stack.setChannel(cell_channel);
			resetMinAndMax();
	}
	else if(Cell_counts_per_ganglia==true && cell_channel!="NA" && ganglia_channel!="NA")
	{

		ganglia_channel=parseInt(ganglia_channel);
		if(isNaN(ganglia_channel)) exit("Enter channel number for Ganglia. If leaving empty, type NA in the value");

	}

}
else if(Ganglia_detection=="Define ganglia using Hu" && cell_channel=="NA")
{
	waitForUser("Enter which channel to use for BOTH "+cell_type+" and ganglia segmentation in the next prompt.");
	Stack.getPosition(active_channel, active_slice, active_frame);
	Dialog.create("Choose Segmentation Channels");
	Dialog.addChoice("Enter which channel to use for BOTH "+cell_type+" and GANGLIA segmentation", channel_list, active_channel);
	//Dialog.addNumber("Enter which channel to use for BOTH "+cell_type+" and GANGLIA segmentation", 3);
	Dialog.show(); 
	cell_channel= parseInt(Dialog.getChoice());//Dialog.getNumber();
	ganglia_channel = cell_channel;
	Stack.setChannel(cell_channel);
	resetMinAndMax();
}
else  if(Ganglia_detection=="Define ganglia using Hu") ganglia_channel = cell_channel;
else cell_channel = 1;




//add option for extended depth of field projection for widefield images
if(sizeZ>1)
{
		print(img_name+" is a stack");
		roiManager("reset");
		
		//in batch mode MIP is used by default
		if(batch_mode==true) projection_method=1;
		else
		{
			waitForUser("Verify which type of Z-stack projection to use(Maximum Intensity Projection or Extended Depth of Field\nYou can select in the next prompt.");
			projection_method=getBoolean("3D stack detected. Which projection method would you like to use?", "Maximum Intensity Projection", "Extended Depth of Field (Variance)");
		
		}
		
		if(projection_method==1)
		{
			Dialog.create("Set Z Slice Ends");
			Dialog.addMessage("Define the starting and ending slice \nto use for the maximum intesntiy projection");
	  		Dialog.addNumber("Start slice", 1);
	  		Dialog.addNumber("End slice", sizeZ);
	  		Dialog.show(); 
	  		start=Dialog.getNumber();
	  		end=Dialog.getNumber();
			run("Z Project...", "start="+start+" stop="+end+" projection=[Max Intensity]");
			max_projection=getTitle();
			
			
		}
		else 
		{
			max_projection=extended_depth_proj(img_name);
		}
}
else 
{
		print(img_name+" has only one slice, using as max projection");
		max_projection=getTitle();
}

max_save_name="MAX_"+file_name;
selectWindow(max_projection);
rename(max_save_name);
max_projection = max_save_name;

//Segment Neurons
selectWindow(max_projection);
run("Select None");
run("Remove Overlay");

//if more than one channel, set on cell_channel or reference channel
if(sizeC>1)
{
	Stack.setChannel(cell_channel);
}

roiManager("show none");
run("Duplicate...", "title="+cell_type+"_segmentation");
seg_image=getTitle();
roiManager("reset");


//calculate no. of tiles
new_width=round(width*scale_factor); 
new_height=round(height*scale_factor);
n_tiles=4;
if(new_width>2000 || new_height>2000) n_tiles=5;
if(new_width>4500 || new_height>4500) n_tiles=8;
if (new_width>9000 || new_height>9000) n_tiles=16;
if (new_width>15000 || new_height>15000) n_tiles=24;


print("No. of tiles: "+n_tiles);


//scale image if scaling factor is not equal to 1
if(scale_factor!=1)
{	
	selectWindow(seg_image);
	new_width=round(width*scale_factor); 
	new_height=round(height*scale_factor);
	run("Scale...", "x=- y=- width="+new_width+" height="+new_height+" interpolation=None create title=img_resize");
	close(seg_image);
	selectWindow("img_resize");
	seg_image=getTitle();

}

roiManager("UseNames", "false");

selectWindow("Log");

//if custom ROIs for Hu, import ROI here
if(custom_roi_hu)
{
	print("Importing ROIs for Hu");
	custom_hu_roi_path = File.openDialog("Choose the custom ROI file to use for Hu segmentation");
	roiManager("open", custom_hu_roi_path);
}
else
{

	print("*********Segmenting cells using StarDist********");

	//segment neurons using StarDist model
	segment_cells(max_projection,seg_image,neuron_model_path,n_tiles,width,height,scale_factor,neuron_seg_lower_limit,probability,overlap);
}

//close(seg_image);
wait(10);
//if cell count zero, check with user if they want to terminate the analysis
cell_count=roiManager("count");

if(batch_mode==false)
{
	if(cell_count == 0)
	{
		print("No cells detected");
		proceed = getBoolean("NO cells detected, do you still want to continue analysis?");
		if(!proceed) 
		{
			print("Analysis stopped as no cells detected");
			exit("Analysis stopped as no cells detected");
		}
	}
}
else 
{
	if(cell_count==0) print("No cells detected");
}




if(batch_mode==false) 
{
	selectWindow(max_projection);
	roiManager("UseNames", "false");
	roiManager("show all");
	roiManager("deselect");
	roi_location = results_dir+cell_type+"_unmodified_ROIs_"+file_name+".zip";
	roiManager("save",roi_location);
	print("Saved unmodified ROIs from GAT detection at "+roi_location);

	waitForUser("Correct "+cell_type+" ROIs if needed. You can use the ROI Manager to add and delete ROIs\nWhen you are satisfied with the ROIs selected, press OK to continue");
}

cell_count=roiManager("count");
roiManager("deselect");

wait(5);
//rename rois
args=cell_type;
runMacro(rename_rois,args);

print("No of "+cell_type+" in "+max_projection+" : "+cell_count);
roiManager("deselect");
roi_location_cell=results_dir+cell_type+"_ROIs_"+file_name+".zip";
roiManager("save",roi_location_cell);
print("Saved ROIs from GAT detection at "+roi_location_cell);

//save composite image with roi overlay
args = max_projection+","+results_dir+","+cell_type;
runMacro(save_composite_img,args);

wait(5);
//need single channel image; multichannel can throw errors
selectWindow(max_projection);
//uses roi to label macro code
runMacro(roi_to_label);
wait(5);
neuron_label_image=getTitle();

close(seg_image);

selectWindow(table_name);
Table.set("File name",row,file_name_full);
Table.set("Total "+cell_type, row, cell_count); //set total count of neurons after nos analysis if nos selected
Table.update;

selectWindow(max_projection);
run("Select None");
run("Remove Overlay");

//wrap this up in a function and also pass batch mode as a flag

if (Cell_counts_per_ganglia==true)
{
	
	if(batch_mode==true)
	{
		ganglia_binary = ganglia_segment(Ganglia_detection,max_projection, cell_channel, neuron_label_image, ganglia_channel,pixelWidth,ganglia_roi_path,batch_mode);
	}
	else 
	{
		ganglia_seg_complete = false; //flag for ganglia segmentation QC checking
		
		//do while statement that checks if ganglia binary image occupies greater than 85% of image 
		//If so, issue a warning and ask if user would like to select a different ganglia seg option
		do 
		{	
		ganglia_roi_path="";
		ganglia_binary = ganglia_segment(Ganglia_detection,max_projection, cell_channel, neuron_label_image, ganglia_channel,pixelWidth,ganglia_roi_path,batch_mode);
		
		//get area fraction of ganglia_binary. 
		selectWindow(ganglia_binary);
		run("Select None");
		area_fraction = getValue("%Area");
		if(area_fraction>=85)
		{
			waitForUser("Ganglia covers >85% of image.If ganglia segmentation\nisn't accurate, click No and choose another option\n in the next prompt");
			ganglia_seg_complete = getBoolean("Is Ganglia segmentation accurate? If so, click Continue", "Continue", "No,Redo");
		}
		else ganglia_seg_complete=true;
		//choose another ganglia segmentation option and redo
	 	if(ganglia_seg_complete==false)
	 	{
	 		Ganglia_detection="DeepImageJ";
	 		print("Redoing ganglia segmentation as "+Ganglia_detection+" option was not satisfactory");
			Dialog.create("Redo ganglia segmentation\nChoose ganglia segmentation option");
			ganglia_seg_options=newArray("DeepImageJ","Define ganglia using Hu","Manually draw ganglia","Import custom ROI");
			Dialog.addRadioButtonGroup("Ganglia segmentation:", ganglia_seg_options, 4, 1, "DeepImageJ");
			Dialog.show();
			Ganglia_detection = Dialog.getRadioButton();
			print("Ganglia detection option chosen: "+Ganglia_detection);
	 	}
	
	  }
	  while(ganglia_seg_complete==false)
	}
		
		

	
	
	//get cell count per ganglia
	print("Counting cells per ganglia. This may take some time for large images.");
	args=neuron_label_image+","+ganglia_binary;
	runMacro(ganglia_cell_count,args);
	
	//label_overlap is the ganglia where each of them are labels
	selectWindow("label_overlap");
	run("Select None");
	
	selectWindow("cells_ganglia_count");
	cell_count_per_ganglia=Table.getColumn("Cell counts");
	
	//check if neuron count per ganglia matches total neuron count;
	sum_cells_ganglia = sum_arr_values(cell_count_per_ganglia);
	if(sum_cells_ganglia!=cell_count)
	{
		print("No. of neurons in ganglia "+sum_cells_ganglia+" does not equal the total neurons detected "+cell_count+".\nThis means that the ganglia outlines are not accurate and neurons are missing");
		print("Using neuron detection to fix ganglia outline");
		close(ganglia_binary);//getting new ganglia binary from script
		selectWindow("cells_ganglia_count");
     	run("Close");

		neuron_dilate_px = 6.5/pixelWidth; //using 6.5 micron for dilating cells
		args=neuron_label_image+",label_overlap,"+neuron_seg_lower_limit+","+neuron_dilate_px;
		//return modified ganglia_binary image
		runMacro(ganglia_fix_missing_neurons,args);	
		selectWindow("ganglia_binary");
		ganglia_binary = getTitle();
		args=neuron_label_image+","+ganglia_binary;
		print("Retrying cell counting per ganglia.");
		//get cell count per ganglia and returns a table as well as ganglia label window
		runMacro(ganglia_cell_count,args);
		
		//label_overlap is the ganglia where each of them are labels
		selectWindow("label_overlap");
		run("Select None");
	
		selectWindow("cells_ganglia_count");
		cell_count_per_ganglia=Table.getColumn("Cell counts");
		sum_cells_ganglia = sum_arr_values(cell_count_per_ganglia);
		print("No. of neurons in ganglia "+sum_cells_ganglia+" Total No. of neurons detected: "+cell_count);
		
	}	
	
	//label_overlap is the ganglia where each of them are labels
	selectWindow("label_overlap");
	run("Select None");
	//make ganglia binary image with ganglia having atleast 1 neuron
	selectWindow("label_overlap");
	//getMinAndMax(min, max);
	setThreshold(1, 65535);
	run("Convert to Mask");
	resetMinAndMax;
	close(ganglia_binary);
	selectWindow("label_overlap");
	rename("ganglia_binary");
	selectWindow("ganglia_binary");
	ganglia_binary=getTitle();
	

	
	
	roiManager("deselect");
	ganglia_number=roiManager("count");
	run("Set Measurements...", "area redirect=None decimal=3");
	run("Clear Results");
	roiManager("Deselect");
	selectWindow(max_projection);
	roiManager("Measure");
	selectWindow("Results");
	ganglia_area = Table.getColumn("Area");
	
	wait(5);
    //rename rois
    runMacro(rename_rois,"Ganglia");
    
    //save composite image with ganglia overlay
    args = max_projection+","+results_dir+",Ganglia";
    runMacro(save_composite_img,args);
    
	roi_location=results_dir+"Ganglia_ROIs_"+file_name+".zip";
	roiManager("save",roi_location );
	roiManager("reset");
	selectWindow(table_name);
	Table.set("No of ganglia",0, ganglia_number);
	Table.setColumn("Neuron counts per ganglia", cell_count_per_ganglia);
	Table.setColumn("Area_per_ganglia_um2", ganglia_area);
	Table.update;
	selectWindow("cells_ganglia_count");
	run("Close");
}
else ganglia_binary = "NA";

//update table
Table.update;
selectWindow(table_name);
Table.save(results_dir+table_name+"_cell_counts.csv");


selectWindow(neuron_label_image);
saveAs("Tiff", results_dir+"Neuron_label_"+max_save_name);

//using this image to detect neuron subtypes by label overlap
rename("Neuron_label");
neuron_label_image=getTitle();
selectWindow(neuron_label_image);
run("Select None");
roiManager("UseNames", "false");

//save images and masks if user selects to save them
if(Save_Image_Masks == true)
{
	
	print("Saving Image and Masks");
	if (!File.exists(img_masks_path)) File.makeDirectory(img_masks_path); //create directory to save img masks
	
	cells_img_masks_path = img_masks_path+fs+"Cells"+fs;
	if (!File.exists(cells_img_masks_path)) File.makeDirectory(cells_img_masks_path); //create directory to save img masks for cells
	save_img_mask_macro_path = gat_dir+fs+"save_img_mask.ijm";
	
	args=max_projection+","+neuron_label_image+","+"Hu,"+cells_img_masks_path;
	//save img masks for cells
	runMacro(save_img_mask_macro_path,args);

	//ganglia save
	if (Cell_counts_per_ganglia==true)
	{
 		ganglia_img = create_ganglia_img(max_projection,ganglia_channel,cell_channel);
 		ganglia_img_masks_path = img_masks_path+fs+"Ganglia"+fs;
 		if (!File.exists(ganglia_img_masks_path)) File.makeDirectory(ganglia_img_masks_path); //create directory to save img masks
 		
 		args=ganglia_img+","+ganglia_binary+","+"ganglia,"+ganglia_img_masks_path;
		runMacro(save_img_mask_macro_path,args);

	}
}

//spatial analysis for Hu (gets no of neighbours around each neuron (Hu).

if(Perform_Spatial_Analysis==true)
{	
	if(batch_parameters=="NA")
	{
		Dialog.create("Select Parameters for Spatial Analysis");
	  	Dialog.addSlider("Cell expansion distance (microns)", 0.0, 20.0, 6.5);
		Dialog.addCheckbox("Save parametric image/s?", true);
	  	Dialog.show(); 
		label_dilation= Dialog.getNumber();
		save_parametric_image = Dialog.getCheckbox();
	}
	args=cell_type+","+neuron_label_image+","+ganglia_binary+","+results_dir+","+label_dilation+","+save_parametric_image+","+pixelWidth+","+roi_location_cell;
	runMacro(spatial_single_cell_type,args);
	
	//save centroids of rois; this can be used for spatial analysis
	//make sure an image is active before running save centroids
	selectWindow(neuron_label_image);
	
	setVoxelSize(pixelWidth, pixelHeight, 1, unit);
	args=results_dir+","+cell_type+","+roi_location_cell;
	runMacro(save_centroids,args);	

}

//save max projection if its scaled image, can use this for further processing later
selectWindow(max_projection);
run("Remove Overlay");
run("Select None");
saveAs("Tiff", results_dir+max_save_name);
run("Clear Results");

selectWindow("Log");
saveAs("Text", results_dir+"Log.txt");


close("*");
print("DATA saved at "+results_dir);

if(batch_mode==false)
{
	exit("Neuron analysis complete");
}
else print("Neuron analysis complete");


//function to segment cells using max projection, image to segment, model file location
//no of tiles for stardist, width and height of image
//returns the ROI manager with ROIs overlaid on the image.
function segment_cells(max_projection,img_seg,model_file,n_tiles,width,height,scale_factor,neuron_seg_lower_limit,probability,overlap)

{
	//need to have the file separator as \\\\ in the file path when passing to StarDist Command from Macro. 
	//regex uses \ as an escape character, so \\ gives one backslash \, \\\\ gives \\.
	//Windows file separator \ is actually \\ as one backslash is an escape character
	//StarDist command takes the escape character as well, so pass 16 backlash to get 4xbackslash in the StarDIst macro command (which is then converted into 2)
	model_file=replace(model_file, "\\\\","\\\\\\\\\\\\\\\\");
	choice=0;
	roiManager("reset");

	//arg_stardist = "probability=["+probability+"], overlap=["+overlap+"], model_file=["+model_file+"], n_tiles="+n_tiles;
	selectWindow(img_seg);
	wait(10);

	run("Command From Macro", "command=[de.csbdresden.stardist.StarDist2D],args=['input':'"+img_seg+"', 'modelChoice':'Model (.zip) from File', 'normalizeInput':'true', 'percentileBottom':'1.0', 'percentileTop':'99.8', 'probThresh':'"+probability+"', 'nmsThresh':'"+overlap+"', 'outputType':'Both', 'modelFile':'"+model_file+"', 'nTiles':'"+n_tiles+"', 'excludeBoundary':'2', 'roiPosition':'Automatic', 'verbose':'false', 'showCsbdeepProgress':'false', 'showProbAndDist':'false'], process=[false]");
	//run("DeepImageJ Run", "model_path=["+neuron_deepimagej_file+"] input_path=null output_folder=null display_output=all");
	wait(50);

	temp=getTitle();
	selectWindow(temp);	
	//make sure cells are detected for Hu.. if not exit macro
	if(roiManager("count")==0) exit("No cells detected. Reduce probability or check image.\nAnalysis stopped");
	else roiManager("reset");
	
	run("Duplicate...", "title=label_image");
	label_image=getTitle();
	run("Remove Overlay");
	close(temp);
	roiManager("reset"); 
	selectWindow(label_image);
	wait(20);
	//remove all labels touching the borders
	run("Remove Border Labels", "left right top bottom");
	wait(10);
	rename("Label-killBorders"); //renaming as the remove border labels gives names with numbers in brackets
	//revert labelled image back to original size
	if(scale_factor!=1)
	{
		selectWindow("Label-killBorders");
		//run("Duplicate...", "title=label_original");
		run("Scale...", "x=- y=- width="+width+" height="+height+" interpolation=None create title=label_original");
		close("Label-killBorders");
	}
	else
	{
		selectWindow("Label-killBorders");
		rename("label_original");
	}
	wait(10);
	//rename("label_original");
	//size filtering
	selectWindow("label_original");
	run("Label Size Filtering", "operation=Greater_Than_Or_Equal size="+neuron_seg_lower_limit);
	label_filter=getTitle();
	resetMinAndMax();
	close("label_original");

	//convert the labels to ROIs
	runMacro(label_to_roi,label_filter);
	wait(10);
	close(label_image);
	selectWindow(max_projection);
	roiManager("show all");
	close(label_filter);
	print("Segmentation done");
}

//rename ROIs as consecutive numbers
function rename_roi()
{
	for (i=0; i<roiManager("count");i++)
		{ 
		roiManager("Select", i);
		roiManager("Rename", i+1);
		}
}

//function to scale images
function scale_image(img,scale_factor,name)
{
	if(scale_factor!=1)
		{	
			selectWindow(img);
			Stack.getDimensions(width, height, channels, slices, frames);
			new_width=round(width*scale_factor); 
			new_height=round(height*scale_factor);
			run("Scale...", "x=- y=- width="+new_width+" height="+new_height+" interpolation=None create title="+name+"_resize");
			close(img);
			//selectWindow(name+"_resize");
			scaled_img=name+"_resize";
		}
	else 
	{
		scaled_img=img;
	}
		return scaled_img;
}


//Draw outline for ganglia or edit the predicted outline
function draw_ganglia_outline(ganglia_img,edit_flag)
{
	if(edit_flag==false)
	{
		roiManager("reset");
		setTool("freehand");
		selectWindow(ganglia_img);
		Stack.setDisplayMode("composite");
		Stack.getDimensions(width, height, channels, slices, frames);
		waitForUser("Ganglia Outline", "Draw an outline of each ganglia. Press 'T' every time you finish drawing an outline to add it to the ROI Manager");
		roiManager("Deselect");
		newImage("Ganglia_outline", "8-bit black", width, height, 1);
		roiManager("Deselect");
		roiManager("Fill");
		return 	"Ganglia_outline";
	}
	else //deprecated; used for confirming ganglia outline from Deepimagej, but now included in the deepimagej function
	{
		setForegroundColor(255,255, 255);
		setTool(3);//set freehand tool
		//setting paintbrush tool earlier may cause user to draw on image unknowingly
		//setTool(19); //set paintbrush tool
		//waitForUser("Verify if ganglia outline is satisfactory?. Use paintbrush tool to fill or delete areas. Press OK when done.");
		
	}
	
}

//extended depth of field projection to acccount for out of focus planes. 
function extended_depth_proj(img)
{
	run("CLIJ2 Macro Extensions", "cl_device=");
	concat_ch="";
	selectWindow(img);
	Stack.getDimensions(width, height, channels, slices, frames);
	if(channels>1)
	{
		for(ch=1;ch<=channels;ch++)
		{
			selectWindow(img);
			Stack.setChannel(ch);
			getLut(reds, greens, blues);
			Ext.CLIJ2_push(img);
			radius_x = 2.0;
			radius_y = 2.0;
			sigma = 10.0;
			proj_img="proj_img"+ch;
			Ext.CLIJ2_extendedDepthOfFocusVarianceProjection(img, proj_img, radius_x, radius_y, sigma);
			Ext.CLIJ2_pull(proj_img);
			setLut(reds, greens, blues);
			//Ext.CLIJ2_pull(img);
			concat_ch=concat_ch+"c"+ch+"="+proj_img+" ";
			
		}
		Ext.CLIJ2_clear();
		//print(concat_ch);
		run("Merge Channels...", concat_ch+" create");
		Stack.setDisplayMode("color");

	}
	else
	{
		selectWindow(img);
		getLut(reds, greens, blues);
		Ext.CLIJ2_push(img);
		radius_x = 2.0;
		radius_y = 2.0;
		sigma = 10.0;
		proj_img="proj_img";
		Ext.CLIJ2_extendedDepthOfFocusVarianceProjection(img, proj_img, radius_x, radius_y, sigma);
		Ext.CLIJ2_pull(proj_img);
		setLut(reds, greens, blues);
	}
	max_name="MAX_"+img;
	rename(max_name);
	close(img);
	return max_name;
}


function ganglia_segment(Ganglia_detection,max_projection, cell_channel, neuron_label_image, ganglia_channel,pixelWidth,ganglia_roi_path,batch_mode)
{
	print("Ganglia segmentation");
	roiManager("reset");
	if(Ganglia_detection=="DeepImageJ")
	 {
	 	print("Using pretrained model in DeepImageJ for segmentation");
	 	args=max_projection+","+cell_channel+","+ganglia_channel+","+ganglia_model+","+batch_mode;
		//get ganglia outline
		runMacro(segment_ganglia,args);
	 	wait(5);
	 	ganglia_binary=getTitle();
	 	draw_ganglia_outline(ganglia_binary,true);
	 	
	 }
	 else if(Ganglia_detection=="Define ganglia using Hu")
	 {
	 	print("Defining ganglia using Hu");
	 	selectWindow(max_projection);
	 	args1=max_projection+","+cell_channel+","+neuron_label_image+","+pixelWidth;
		//get ganglia outline
		runMacro(ganglia_hu_expansion,args1);
		wait(5);
		ganglia_binary=getTitle();
		draw_ganglia_outline(ganglia_binary,true);

	 }
	 else if(Ganglia_detection=="Import custom ROI")
	 {
		print("Importing custom ROI");
		//batch mode argument is detected if two arguments are passed to the macro
		if(batch_parameters!="NA"){args1=neuron_label_image+","+ganglia_roi_path;}
		else {args1=neuron_label_image;}
		print(args1);
		//get ganglia outline
		runMacro(ganglia_custom_roi,args1);
		ganglia_binary=getTitle();
	 	
	 }
	 else if(Ganglia_detection=="Manually draw ganglia")
	 {
	 	print("Manually draw ganglia");
	 	ganglia_binary=draw_ganglia_outline(max_projection,false);
	 }
	 else exit("Ganglia detection method not valid. Got "+Ganglia_detection);
	return ganglia_binary;
}


//function to create ganglia image for saving annotations; move this to separate file later on
function create_ganglia_img(max_projection,ganglia_channel,cell_channel)
{
	
	selectWindow(max_projection);
	run("Select None");
	Stack.setChannel(ganglia_channel);
	run("Duplicate...", "title=ganglia_ch duplicate channels="+ganglia_channel);
	run("Green");
	
	selectWindow(max_projection);
	run("Select None");
	Stack.setChannel(cell_channel);
	run("Duplicate...", "title=cells_ch duplicate channels="+cell_channel);
	run("Magenta");
	
	run("Merge Channels...", "c1=ganglia_ch c2=cells_ch create");
	composite_img=getTitle();
	
	run("RGB Color");
	ganglia_rgb=getTitle();

	return ganglia_rgb;
	

}

//get sum of all values in an array
function sum_arr_values(arr)
{
	sum_val = 0;
	for (i = 0; i < arr.length; i++)
	{
		sum_val+=arr[i];
	}
return sum_val;
}


//add a value to every element of an array
function add_value_array(arr,val)
{
	for (i = 0; i < arr.length; i++)
	{
		temp=arr[i]+val;
		arr[i]=parseInt(temp);
	}
return arr;
}
