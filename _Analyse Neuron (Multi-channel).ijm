
//*******
// Author: Pradeep Rajasekhar
// March 2021
// License: BSD3
// 
// Copyright 2021 Pradeep Rajasekhar, Walter and Eliza Hall Institute of Medical Research, Melbourne, Australia
// 
// Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
// 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
// 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
//FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
//BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

var fs=File.separator;
setOption("ExpandableArrays", true);

print("\\Clear");
run("Clear Results");

//get fiji directory and get the macro folder for GAT
var fiji_dir=getDirectory("imagej");
var gat_dir=fiji_dir+"scripts"+fs+"GAT"+fs+"Tools"+fs+"commands";


//specify directory where StarDist models are stored
var models_dir=fiji_dir+"models"+fs; //"scripts"+fs+"GAT"+fs+"Models"+fs;


//settings for GAT
gat_settings_path=gat_dir+fs+"gat_settings.ijm";
if(!File.exists(gat_settings_path)) exit("Cannot find settings file. Check: "+gat_settings_path);
run("Results... ", "open="+gat_settings_path);
training_pixel_size=parseFloat(Table.get("Values", 0)); //0.7;
neuron_area_limit=parseFloat(Table.get("Values", 1)); //1500
neuron_seg_lower_limit=parseFloat(Table.get("Values", 2)); //90
neuron_lower_limit=parseFloat(Table.get("Values", 3)); //160
backgrnd_radius=parseFloat(Table.get("Values", 4)); 

probability=parseFloat(Table.get("Values", 5)); //prob neuron
probability_subtype=parseFloat(Table.get("Values", 6)); //prob subtype

overlap= parseFloat(Table.get("Values", 7));
overlap_subtype=parseFloat(Table.get("Values", 8));

//get paths of model files
neuron_model_file = Table.getString("Values", 9);
neron_subtype_file = Table.getString("Values", 10);
run("Close");

//Neuron segmentation model
neuron_model_path=models_dir+neuron_model_file;
//Marker segmentation model
subtype_model_path=models_dir+neron_subtype_file;
if(!File.exists(neuron_model_path)||!File.exists(subtype_model_path)) exit("Cannot find models for segmenting neurons at these paths:\n"+neuron_model_path+"\n"+subtype_model_path);





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


fs = File.separator; //get the file separator for the computer (depending on operating system)

#@ File (style="open", label="<html>Choose the image to segment.<br><b>Enter NA if field is empty.</b><html>", value=fiji_dir) path
#@ boolean image_already_open
#@ String(value="<html>If image is already open, tick above box.<html>", visibility="MESSAGE") hint1
// File (style="open", label="<html>Choose the StarDist model file if segmenting neurons.<br>Enter NA if empty<html>",value="NA", description="Enter NA if nothing") neuron_model_path 
#@ String(label="Enter channel number for Hu if you know. Enter NA if not using.", value="NA") cell_channel
#@ String(value="<html>----------------------------------------------------------------------------------------------------------------------------------------<html>",visibility="MESSAGE") divider
#@ String(value="<html><center><b>NEURONAL SUBTYPE ANALYSIS</b></center> <html>",visibility="MESSAGE") hint_subtype
#@ String(value="<html>Tick box below if you want to estimate proportion of neuronal subtypes.<html>", visibility="MESSAGE") hint3
#@ boolean Calculate_Neuron_Subtype
// File (style="open", label="<html>Choose the StarDist model for subtype segmentation.<br>Enter NA if empty<html>",value="NA", description="Enter NA if nothing") subtype_model_path 
cell_type="Hu";
#@ String(value="<html> Tick box below if you know channel name and numbers<br/> The order of channel numbers MUST match with channel name order.<html>",visibility="MESSAGE") hint5
#@ boolean Enter_channel_details_now
#@ String(label="Enter channel names followed by a comma (,). Enter NA if not using.", value="NA") marker_names_manual
#@ String(label="Enter channel numbers with separated by a comma (,). Leave as NA if not using.", value="NA") marker_no_manual
#@ String(value="<html>----------------------------------------------------------------------------------------------------------------------------------------<html>",visibility="MESSAGE") divider
#@ String(value="<html><center><b>DETERMINE GANGLIA OUTLINE</b></center> <html>",visibility="MESSAGE") hint_ganglia
#@ String(value="<html> Cell counts per ganglia will be calculated<br/>Requires a neuron channel & second channel that labels the neuronal fibres.<html>",visibility="MESSAGE") hint4
#@ boolean Cell_counts_per_ganglia (description="Use a pretrained model, Hu or manually define ganglia outline")
#@ String(choices={"DeepImageJ","Define ganglia using Hu","Manually draw ganglia"}, style="radioButtonHorizontal") Ganglia_detection
#@ String(label="<html> Enter the channel number for segmenting ganglia.<br/> Not valid for 'Define ganglia using Hu'.<br/> Enter NA if not using.<html> ", value="NA") ganglia_channel
#@ String(value="<html>------------------------------------******<b>ADVANCED: Finetune analysis by changing custom parameters<b>******------------------------------------<html>",visibility="MESSAGE") hint_adv
//#@ String(value="<html><center><b>Finetune cell detection by changing parameters below.</b></center> <html>",visibility="MESSAGE") hint_stardist
#@ boolean Custom_Rescaling_Factor (description="Enter custom rescaling factor")
#@ Float(label="<html>Enter rescaling factor for segmenting neurons.", value=1) scale
if(Custom_Rescaling_Factor==true) scale=scale;
else scale = 1;
#@ boolean Finetune_probability_overlap
#@ String(value="<html>---------------------------------------------------------******<b>Contribute to improving GAT<b>******-------------------------------------------<html>",visibility="MESSAGE") contrib
#@ String(value="<html> If you are willing to contribute images and masks to improve GAT, tick the box below to save the images and masks in a custom folder.<html>",visibility="MESSAGE") contrib1
#@ boolean Save_Image_Masks
#@ File (style="directory", label="<html>Choose a folder to save the image and masks.<br><b>Enter NA if field is empty.</b><html>", value=fiji_dir) img_masks_path


if(Finetune_probability_overlap==true)
{
	print("Using manual probability and overlap threshold for detection");
	Dialog.create("Change Probability and Overlap for Neuron Detection");
  	Dialog.addSlider("Probability of detecting neurons (Hu)", 0, 1,probability );	
  	Dialog.addSlider("Probability of detecting neuronal subtypes", 0, 1,probability);
  	Dialog.addSlider("Overlap threshold", 0, 1,overlap);
	Dialog.show(); 
	probability= Dialog.getNumber();
	probability_subtype_manual= Dialog.getNumber();
	overlap= Dialog.getNumber();
	overlap_subtype=overlap;
	//probability=probability_manual;
	//overlap=overlap_manual;
}


//listing parameters being used for GAT
print("Using parameters\nSegmentation pixel size:"+training_pixel_size+"\nMax neuron area (microns): "+neuron_area_limit+"\nMin Neuron Area (microns): "+neuron_seg_lower_limit+"\nMin marker area (microns): "+neuron_lower_limit);
print("**Neuron\nProbability: "+probability+"\nOverlap threshold: "+overlap);
print("**Neuron subtype\nProbability: "+probability_subtype+"\nOverlap threshold: "+overlap_subtype+"\n");


//add an option for defining a custom scaling factor

marker_subtype=Calculate_Neuron_Subtype;

//checking if no of markers and no of channels match
if(marker_subtype==1 && Enter_channel_details_now==1)
{
	//print(marker_names_manual);
	marker_names_manual=split(marker_names_manual, ",");
	
	//trim space from names
	marker_names_manual=trim_space_arr(marker_names_manual);
	//Array.show(marker_names_manual);
	
	marker_no_manual=split(marker_no_manual, ",");
	if(marker_names_manual.length!=marker_no_manual.length) exit("Number of marker names and marker channels do not match");
}



if(image_already_open==true)
{
	waitForUser("Select Image and choose output folder in next prompt");
	file_name_full=getTitle(); //get file name without extension (.lif)
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
	else exit("File type not recognised.  Tif, Lif and Czi files supported.");
	dir=File.directory;
	file_name_full=File.nameWithoutExtension; //get file name without extension (.lif)
}

//file_name=File.nameWithoutExtension;
file_name_length=lengthOf(file_name_full);
if(file_name_length>50)
{
	file_name=substring(file_name_full, 0, 20); //Restricting file name length as in Windows long path names can cause errors
	suffix = getString("File name is too long, so it will be truncated. Enter custom name to be be added to end of filename", "_1");
	file_name = file_name+suffix;
}
else file_name=file_name_full;
//if delimiters such as , ; or _ are there in file name, split string and join with underscore
file_name_split = split(file_name,",;_-");
file_name =String.join(file_name_split,"_");


img_name=getTitle();
Stack.getDimensions(width, height, sizeC, sizeZ, frames);


run("Select None");
run("Remove Overlay");

getPixelSize(unit, pixelWidth, pixelHeight);

//Check image properties************
//Check if RGB
if (bitDepth()==24)
{
	print("Image is RGB type. It is recommended to NOT\nconvert the image to RGB and use the raw output from the microscope (usually, 8,12 or 16-bit)\n.");
	rgb_prompt = getBoolean("Image is RGB. Recommend to use 8,12 or 16-bit images. Can try converting to 8-bit and proceed.", "Convert to 8-bit", "No, stop analysis");
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
	print("Image not calibrated in microns. This is required for accurate segmentation");
	exit("Image must have pixel size in microns.\nGo to Image -> Properties to set this.\nYou can get this from the microscope settings.\nCannot proceed: STOPPING Analysis");

}
//************


//Define scale factor to be used
target_pixel_size= training_pixel_size/scale;
scale_factor = pixelWidth/target_pixel_size;
if(scale_factor<1.001 && scale_factor>1) scale_factor=1;


print("Analysing: "+file_name);
analysis_dir= dir+"Analysis"+fs;
if (!File.exists(analysis_dir)) File.makeDirectory(analysis_dir);
//Create results directory with file name in "analysis"
results_dir=analysis_dir+file_name+fs; //directory to save images
if (!File.exists(results_dir)) File.makeDirectory(results_dir); //create directory to save results file
print("Files will be saved at: "+results_dir); 

//do not include cells greater than 1000 micron in area
//neuron_area_limit=1500; //microns
neuron_max_pixels=neuron_area_limit/pixelWidth; //convert micron to pixels

//using limit when segmenting neurons
//neuron_seg_lower_limit=90;//microns
neuron_seg_lower_limit=neuron_seg_lower_limit/pixelWidth; 


//using limit for marker multiplication and delineation
//neuron_lower_limit= 160;//microns
neuron_min_pixels=neuron_lower_limit/pixelWidth; //convert micron to pixels

backgrnd_radius=backgrnd_radius/pixelWidth;//convert micron to pixels

//print(neuron_max_pixels,neuron_seg_lower_limit,neuron_min_pixels,backgrnd_radius);

table_name="Analysis_"+cell_type+"_"+file_name;
Table.create(table_name);//Final Results Table
row=0; //row counter for the table
image_counter=0;

if(cell_channel!="NA")
{
	if(Enter_channel_details_now==true && marker_names_manual.length>1) //delete Hu from channel list as we are not using it for marker classification
	{
		//find index of cell_channel;; keep it as string
		idx_Hu=find_str_array(marker_no_manual,cell_channel);
		if(idx_Hu!="NA") //if Hu found in the channel entries, delete that corresponding channel
		{
			marker_names_manual=Array.deleteIndex(marker_names_manual, idx_Hu);
			marker_no_manual=Array.deleteIndex(marker_no_manual,idx_Hu);
		}
	}
	cell_channel=parseInt(cell_channel);
	if(isNaN(cell_channel)) exit("Enter channel number for cell. If leaving empty, type NA in the value");
	
}

if(ganglia_channel!="NA")
{
	ganglia_channel=parseInt(ganglia_channel);
	if(isNaN(ganglia_channel)) exit("Enter channel number for Ganglia. If leaving empty, type NA in the value");
	
}

//Array.show(marker_names_manual);
//Array.show(marker_no_manual);
//exit("test");
if(sizeC>1 && Ganglia_detection!="Define ganglia using Hu")
{
 if (Cell_counts_per_ganglia==true && cell_channel=="NA" && ganglia_channel=="NA") //count cells per ganglia but don't know channels for ganglia or neuron
	{
		waitForUser("Note the channels for neuron and ganglia and enter in the next box.");
		
		Dialog.create("Choose channels for "+cell_type);
  		Dialog.addNumber("Enter "+cell_type+" channel", 3);
  		Dialog.addNumber("Enter channel for segmenting ganglia", 2);
  		Dialog.show(); 
		cell_channel= Dialog.getNumber();
		ganglia_channel=Dialog.getNumber();
		Stack.setChannel(cell_channel);
		resetMinAndMax();
		Stack.setChannel(ganglia_channel);
		resetMinAndMax();		
	}
	else if(Cell_counts_per_ganglia==true && cell_channel!="NA" && ganglia_channel=="NA") //count cells per ganglia but don't know channels for ganglia
	{
		waitForUser("Note the channels for ganglia and enter in the next box.");
		Dialog.create("Choose channel for ganglia");
  		Dialog.addNumber("Enter channel for segmenting ganglia", 2);
  		Dialog.show(); 
		//cell_channel= Dialog.getNumber();
		ganglia_channel=Dialog.getNumber();
		//Stack.setChannel(cell_channel);
		//resetMinAndMax();
		Stack.setChannel(ganglia_channel);
		resetMinAndMax();	
	}
		else if(Cell_counts_per_ganglia==true && cell_channel=="NA" && ganglia_channel!="NA") //count cells per ganglia but don't know channels for neuron
	{
			waitForUser("Note the channels for "+cell_type+" and enter in the next box.");
			Dialog.create("Choose  channel for "+cell_type);
	  		Dialog.addNumber("Enter "+cell_type+" channel", 3);
	  	    Dialog.show(); 
			cell_channel= Dialog.getNumber();
			Stack.setChannel(cell_channel);
			resetMinAndMax();
	}

}
else if(Ganglia_detection=="Define ganglia using Hu" && cell_channel=="NA")
{
	waitForUser("Note the channels for "+cell_type+" and enter in the next box.");
	Dialog.create("Choose  channel for "+cell_type+);
	Dialog.addNumber("Enter "+cell_type+" and ganglia channel", 3);
	Dialog.show(); 
	cell_channel= Dialog.getNumber();
	ganglia_channel = cell_channel;
	Stack.setChannel(cell_channel);
	resetMinAndMax();
}
else  if(Ganglia_detection=="Define ganglia using Hu") ganglia_channel = cell_channel;
else exit("Need multiple channels, only one channel found");


//added option for extended depth of field projection for widefield images
if(sizeZ>1)
{
		print(img_name+" is a stack");
		roiManager("reset");
		waitForUser("Verify the type of image projection you'd like (MIP or Extended depth of field\nYou can select in the next prompt.");
		projection_method=getBoolean("3D stack detected. Which projection method would you like?", "Maximum Intensity Projection", "Extended Depth of Field (Variance)");
		if(projection_method==1)
		{
			waitForUser("Note the start and end of the stack.\nPress OK when done");
			Dialog.create("Choose slices");
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
if(new_width>5000 || new_height>5000) n_tiles=8;
else if (new_width>9000 || new_height>5000) n_tiles=12;

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
print("*********Segmenting cells using StarDist********");

//segment neurons using StarDist model
segment_cells(max_projection,seg_image,neuron_model_path,n_tiles,width,height,scale_factor,neuron_seg_lower_limit,probability,overlap);

//if cell count zero, check with user if they want to terminate the analysis
cell_count=roiManager("count");
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

//manually correct or verify if needed
waitForUser("Correct "+cell_type+" ROIs if needed. You can delete or add ROIs using ROI Manager");
cell_count=roiManager("count");
rename_roi(); //rename ROIs
roiManager("deselect");

selectWindow(max_projection);
//uses roi to label macro code; clij is a dependency
runMacro(roi_to_label);
wait(5);
neuron_label_image=getTitle();
//using this image to detect neuron subtypes by label overlap
selectWindow(neuron_label_image);
max_save_name="MAX_"+file_name;
saveAs("Tiff", results_dir+cell_type+"n_label_"+max_save_name);
rename("Neuron_label"); //saving the file will change the name, so renaming it and getting image name again
neuron_label_image=getTitle();
selectWindow(neuron_label_image);
run("Select None");

print("No of "+cell_type+" in "+max_projection+" : "+cell_count);

//select all rois in roi manager
selection_indexes = Array.getSequence(roiManager("count"));
roiManager("select", selection_indexes);
group_id = 1;
set_all_rois_group_id(group_id);
roiManager("deselect");
roi_location=results_dir+cell_type+"_ROIs_"+file_name+".zip";
roiManager("save",roi_location );

selectWindow(table_name);
Table.set("File name",row,file_name);
Table.set("Total "+cell_type, row, cell_count); //set total count of neurons after nos analysis if nos selected
Table.update;

//Segment ganglia
selectWindow(max_projection);
run("Select None");
run("Remove Overlay");

if (Cell_counts_per_ganglia==true)
{
	roiManager("reset");
	if(Ganglia_detection=="DeepImageJ")
	 {
	 	//ganglia_binary=ganglia_deepImageJ(max_projection,cell_channel,ganglia_channel);
		args=max_projection+","+cell_channel+","+ganglia_channel;
		//get ganglia outline
		runMacro(segment_ganglia,args);
	 	wait(5);
	 	ganglia_binary=getTitle();
	 	//draw_ganglia_outline(ganglia_binary,true);
	 	
	 }
	 else if(Ganglia_detection=="Define ganglia using Hu")
	 {
	 	
	 	selectWindow(max_projection);
	 	args1=max_projection+","+cell_channel+","+neuron_label_image+","+pixelWidth;
		//get ganglia outline
		runMacro(ganglia_hu_expansion,args1);
		wait(5);
		ganglia_binary=getTitle();
		draw_ganglia_outline(ganglia_binary,true);
	 }
	 else ganglia_binary=draw_ganglia_outline(ganglia_img,false);
	 
	args=neuron_label_image+","+ganglia_binary;
	//get cell count per ganglia and returns a table as well as ganglia label window
	runMacro(ganglia_cell_count,args);

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

	selectWindow("cells_ganglia_count");
	cell_count_per_ganglia=Table.getColumn("Cell counts");
	cell_count_per_ganglia=Array.deleteValue(cell_count_per_ganglia, 0);
	roiManager("deselect");
	ganglia_number=roiManager("count");
	roi_location=results_dir+"Ganglia_ROIs_"+file_name+".zip";
	roiManager("save",roi_location );
	roiManager("reset");
	selectWindow(table_name);
	Table.set("No of ganglia",0, ganglia_number);
	Table.setColumn("Neuron counts per ganglia", cell_count_per_ganglia);
	Table.update;
	selectWindow("cells_ganglia_count");
	run("Close");
}

//save images and masks if user selects to save them for Hu and ganglia
if(Save_Image_Masks == true)
{
	
	//print("Saving Image and Masks");
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


//neuron_subtype_matrix=0;
no_markers=0;
//if user wants to enter markers before hand, can do that at the start
//otherwise, option to enter them manually here
if(marker_subtype==1) 
{
	
	arr=Array.getSequence(sizeC);
	arr=add_value_array(arr,1);
	if(Enter_channel_details_now==true)
	{
		channel_names=marker_names_manual;//split(marker_names_manual, ",");
		channel_numbers=marker_no_manual;//split(marker_no_manual, ",");
		channel_numbers=convert_array_int(marker_no_manual);
		no_markers=channel_names.length;
		//Array.show(channel_names);
		//Array.show(channel_numbers);
	}
	else 
	{
		no_markers=getNumber("How many markers would you like to analyse?", 1);
		string=getString("Enter names of markers separated by comma (,)", "Names");
		channel_names=split(string, ",");	
		if(channel_names.length!=no_markers) exit("Channel names do not match the no of markers");
		channel_numbers=newArray(sizeC);
		marker_label_img=newArray(sizeC);
		Dialog.create("Select channels for each marker");
		for(i=0;i<no_markers;i++)
		{
			Dialog.addChoice("Choose Channel for "+channel_names[i], arr, arr[0]);
			//Dialog.addCheckbox("Determine if expression is high or low", false);
		}
		Dialog.show();

	
		for(i=0;i<no_markers;i++)
		{
			channel_numbers[i]=Dialog.getChoice();
			//hi_lo[i]=Dialog.getCheckbox();
		}
	}
	if(no_markers>1)
	{
		channel_combinations=combinations(channel_names); //get all possible combinations and adds an underscore between name labels if multiple markers
		channel_combinations=sort_marker_array(channel_combinations);
	}
	else
	{
		channel_combinations=channel_names; // pass single combination

	}
	channel_position=newArray();
	marker_label_arr=newArray(); //store names of label images generated from StarDist

	selectWindow(max_projection);
	Stack.setDisplayMode("color");
	row=0;
	for(i=0;i<channel_combinations.length;i++)
	{
	print("Getting counts for cells positive for marker/s: "+channel_combinations[i]);
	//get channel names as an array
	channel_arr=split(channel_combinations[i], ",");
	
	if(channel_arr.length==1) //if first marker or if only one marker combination
	{
		if(no_markers==1) //if only one marker
		{
			//channel_position=channel_numbers[i];
			channel_no=channel_numbers[i];
			channel_name=channel_names[i];
		}
		else  //multiple markers
		{
			//find position index of the channel by searching for name
			channel_idx=find_str_array(channel_names,channel_combinations[i]); //find channel the marker is in by searching for the name in array with name combinations
			//use index of position to get the channel number and the channel name
			channel_no=channel_numbers[channel_idx]; //idx fro above is returned as 0 indexed, so using this indx to find channel no
			channel_name=channel_names[channel_idx];
		}			//Array.print(channel_position);

				
		selectWindow(max_projection);
		Stack.setChannel(channel_no);
		run("Select None");
		run("Duplicate...", "title="+channel_name+"_segmentation");
			//waitForUser;
		marker_image=getTitle();

		//scaling marker images so we can segment them using same size as images used for training the model. Also, ensures consistent size exclusion area
		seg_marker_img=scale_image(marker_image,scale_factor,channel_name);
		roiManager("reset");
		//segment cells and return image with normal scaling
		print("Segmenting marker "+channel_name);
		selectWindow(seg_marker_img);
		//run("Subtract Background...", "rolling="+backgrnd_radius+" sliding");
		segment_cells(max_projection,seg_marker_img,subtype_model_path,n_tiles,width,height,scale_factor,neuron_seg_lower_limit,probability_subtype,overlap_subtype);
		selectWindow(max_projection);
		roiManager("deselect");
		runMacro(roi_to_label);
		wait(5);
		rename("label_img_temp");
		run("glasbey_on_dark");
		//selectWindow("label_mapss");
		run("Select None");
		roiManager("reset");
		
		//multiply with HU to verify if its a neuron. multiply above label image with image of Neuron label
		//if Hu and marker are present, keeps label, else it becomes background
		//keep objects within size range 400 to 3000; may need to alter this later depending on cell size
		//Manual step to verify
		temp_label=multiply_markers(neuron_label_image,"label_img_temp",neuron_min_pixels,neuron_max_pixels);//getting smaller objects, so inc min size to 400
		
		//selectWindow(temp_label);
		selectWindow(temp_label);
		run("Select None");
		runMacro(label_to_roi,temp_label);
		close(temp_label);
		close("label_img_temp");
		wait(5);
		selectWindow(max_projection);
		//roiManager("deselect");
		roiManager("show all");
		//selectWindow(max_projection);
		//add option to draw ROI on channel name OR
		//display Hu and other channel as overlay; assign groups with different colours; 
		//get user to click
		waitForUser("Verify ROIs for "+channel_name+". Delete or add ROIs as needed. Press OK when done.");
		
		group_id+=1;
		set_all_rois_group_id(group_id);
		roiManager("deselect");
		//convert roi manager to label image
		runMacro(roi_to_label);
		selectWindow("label_mapss");
		rename("label_img_"+channel_name);
		label_marker=getTitle();
		//store resized label images for analysing label co-expression
		label_name = "label_"+channel_name;
		label_rescaled_img=scale_image(label_marker,scale_factor,label_name);
		selectWindow(label_marker);	
			//save images and masks if user selects to save them for the marker
			if(Save_Image_Masks == true)
			{
				
				//cells_img_masks_path = img_masks_path+fs+"Cells"+fs;
				//if (!File.exists(cells_img_masks_path)) File.makeDirectory(cells_img_masks_path); //create directory to save img masks for cells
				//save_img_mask_macro_path = gat_dir+fs+"save_img_mask.ijm";
				args=marker_image+","+label_marker+","+channel_name+","+cells_img_masks_path;
				//save img masks for cells
				runMacro(save_img_mask_macro_path,args);
				close(marker_image);
			}
			else close(marker_image);
			
			//store marker name in an array to call when analysing marker combinations
			if(no_markers>1) marker_label_arr[i]=label_rescaled_img;//label_marker;

			marker_count=roiManager("count"); // in case any neurons added after manual verification of markers
			selectWindow(table_name);
			//Table.set("Total "+cell_type, row, cell_count);
			Table.set("Marker Combinations", row, channel_name);
			Table.set("Number of cells per marker combination", row, marker_count);
			Table.set("|", row, "|");

			//Table.set(""+cell_type, row, marker_count/cell_count);
			Table.update;
			row+=1;
			
			//selectWindow(max_projection);
			roiManager("deselect");
			//roi_file_name= String.join(channel_arr, "_");
			roi_location_marker=results_dir+channel_name+"_ROIs.zip";
			roiManager("save",roi_location_marker);
			close(seg_marker_img);
			roiManager("reset");
			//Array.print(marker_label_arr);

			if (Cell_counts_per_ganglia==true)
			{
				selectWindow(label_marker);
				run("Remove Overlay");
				run("Select None");
				
				args=label_marker+","+ganglia_binary;
				//get cell count per ganglia
				runMacro(ganglia_cell_count,args);
				close("label_overlap");
				selectWindow("cells_ganglia_count");
				cell_count_per_ganglia=Table.getColumn("Cell counts");

				selectWindow(table_name);
				Table.setColumn(channel_name+" counts per ganglia", cell_count_per_ganglia);
				Table.update;
				
				selectWindow("cells_ganglia_count");
				run("Close");
				roiManager("reset");
			}
			close(label_marker);
			
		}
		//if more than one marker to analyse; then it multiplies the marker labels from above to find coexpressing cells
		else if(channel_arr.length>=1) 
		{
	
			for(j=0;j<channel_arr.length;j++)
			{
				marker_name="_"+channel_arr[j];
				channel_pos=find_str_array(marker_label_arr,marker_name); //look for marker with _ in its name.returns index of marker_label image
				if(j==0) //if first time running the loop
				{		//Array.print(marker_label_arr);
						//multiply_markers
						marker_name="_"+channel_arr[1]; //get img2
						channel_pos2=find_str_array(marker_label_arr,marker_name);
						img1=marker_label_arr[channel_pos];
						img2=marker_label_arr[channel_pos2];
						//print(channel_pos2);
						print("Processing "+img1+" * "+img2);
						temp_label=multiply_markers(img1,img2,400,3000);
						selectWindow(temp_label);
						run("Select None");
						if(scale_factor!=1)
						{
								selectWindow(temp_label);
								label_temp_name = img1+"_*_"+img2+"_label";
								//run("Duplicate...", "title=label_original");
								run("Scale...", "x=- y=- width="+width+" height="+height+" interpolation=None create title="+label_temp_name);
								close(temp_label);
								//selectWindow(label_temp_name);
								temp_label = label_temp_name;
						}
						wait(5);
						selectWindow(temp_label);
						run("Select None");
						rename(img1+"_"+img2);
						result=img1+"_"+img2;
						j=j+1;
						//print(j);
				}
				else 
				{
					//print(j);
					img2=marker_label_arr[channel_pos];
					img1=result;
					//print("Processing "+img1+" * "+img2);
					temp_label=multiply_markers(img1,img2,400,3000);
					selectWindow(temp_label);
					run("Select None");
					if(scale_factor!=1)
					{
						selectWindow(temp_label);
						label_temp_name = img1+"_*_"+img2+"_label";
						run("Scale...", "x=- y=- width="+width+" height="+height+" interpolation=None create title="+label_temp_name);
						close(temp_label);
						temp_label = label_temp_name;
					}
					//runMacro(label_to_roi,temp_label);
					//close(temp_label);
					close(img1);
					close(img2);
					wait(5);
					result="img "+d2s(j,0);
					selectWindow(temp_label);
					rename(result);
					
				}
			if(j==channel_arr.length-1) //when reaching end of arr length, get ROI counts
			{
				selectWindow(result);
				run("Select None");
				roiManager("reset");
				runMacro(label_to_roi,result);
				//save with name of channel_combinations[i]
				wait(10);
				//selectWindow(max_projection);

				roiManager("deselect");
				roi_file_name= String.join(channel_arr, "_");
				roi_location_marker=results_dir+roi_file_name+"_ROIs.zip";
				//if no cells in marker combination
				if(roiManager("count")>0)
				{
					roiManager("save",roi_location_marker);
				}

				marker_count=roiManager("count"); // in case any neurons added after analysis of markers
				selectWindow(table_name);
				//Table.set("Total "+cell_type, row, cell_count);
				Table.set("Marker Combinations", row, roi_file_name);
				Table.set("Number of cells per marker combination", row, marker_count);
				Table.set("|", row, "|");
				//Table.set(""+cell_type, row, marker_count/cell_count);
				Table.update;
				row+=1;


				
				roiManager("reset");
				
				if (Cell_counts_per_ganglia==true)
				{
					if(roiManager("count")>0)
					{
						selectWindow(result);
						run("Remove Overlay");
						run("Select None");
						
						args=result+","+ganglia_binary;
						//get cell count per ganglia
						runMacro(ganglia_cell_count,args);
						close("label_overlap");
						selectWindow("cells_ganglia_count");
						cell_count_per_ganglia=Table.getColumn("Cell counts");
						selectWindow("cells_ganglia_count");
						run("Close");
						roiManager("reset");
						selectWindow(table_name);
						Table.setColumn(roi_file_name+" counts per ganglia", cell_count_per_ganglia);

					}
					else{
						cell_count_per_ganglia = 0;
						Table.set(roi_file_name+" counts per ganglia", 0,cell_count_per_ganglia);
						
					}
	

					Table.update;

				}

				

				
			}
		}
		close(result);
	  }
   }

//remove zeroes in the array
selectWindow(table_name);
marker_combinations=Table.getColumn("Marker Combinations"); 
marker_combinations=Array.deleteValue(marker_combinations, 0);
Table.setColumn("Marker Combinations", marker_combinations);

//trim marker combination column to remove zeroes
marker_comb_length=marker_combinations.length;
no_cells_marker=Table.getColumn("Number of cells per marker combination");
no_cells_marker=Array.trim(no_cells_marker,marker_comb_length);
Table.setColumn("Number of cells per marker combination", no_cells_marker);


//replace zeroes in divider column with divider
file_array=Table.getColumn("|"); 
file_array=replace_str_arr(file_array,0,"|");
Table.setColumn("|", file_array);
Table.update;


}
close("label_img_*");

//remove zeroes in the file name
selectWindow(table_name);
file_array=Table.getColumn("File name"); 
file_array=Array.deleteValue(file_array, 0);
Table.setColumn("File name", file_array);

//remove zeroes in neuron array
file_array=Table.getColumn("Total "+cell_type); 
file_array=Array.deleteValue(file_array, 0);
Table.setColumn("Total "+cell_type, file_array);






selectWindow(table_name);
Table.save(results_dir+"Cell_counts.csv");

//save max projection if its scaled image, can use this for further processing later
selectWindow(max_projection);
run("Remove Overlay");
run("Select None");

saveAs("Tiff", results_dir+max_save_name);
//run("Close");
roiManager("UseNames", "false");

print("Files saved at: "+results_dir);
close("*");
exit("Multi-channel Neuron analysis complete");

//close("Image correlation. Local region size = 3 pixels");


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
	//model_file="D:\\\\Gut analysis toolbox\\\\models\\\\2d_enteric_neuron\\\\TF_SavedModel.zip";
	selectWindow(img_seg);
	run("Command From Macro", "command=[de.csbdresden.stardist.StarDist2D],args=['input':'"+img_seg+"', 'modelChoice':'Model (.zip) from File', 'normalizeInput':'true', 'percentileBottom':'1.0', 'percentileTop':'99.8', 'probThresh':'"+probability+"', 'nmsThresh':'"+overlap+"', 'outputType':'Label Image', 'modelFile':'"+model_file+"', 'nTiles':'"+n_tiles+"', 'excludeBoundary':'2', 'roiPosition':'Automatic', 'verbose':'false', 'showCsbdeepProgress':'false', 'showProbAndDist':'false'], process=[false]");
	wait(50);
	temp=getTitle();
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
	wait(5);
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
}

//multiply label images of markers to get double positive cells
//exclude based no size and only keep cells if there is overlap
function multiply_markers(marker1,marker2,minimum_size,maximum_size)
{
	//marker 1 is ref channel For neuron segmentation, its Hu and then marker of choice in marker2
	// Init GPU
	run("CLIJ2 Macro Extensions", "cl_device=");
	Ext.CLIJ2_clear();
	Ext.CLIJ2_pushCurrentZStack(marker1);
	Ext.CLIJ2_pushCurrentZStack(marker2);
	
	
	// Multiply Images
	//Ext.CLIJ2_multiplyImages(marker1, marker2, image_5);
	//Ext.CLIJ2_release(marker1);
	//Ext.CLIJ2_release(marker2);
	
	// Exclude Labels Outside Size Range
	//minimum_size = 300.0;
	//maximum_size = 3000.0;
	Ext.CLIJ2_excludeLabelsOutsideSizeRange(marker2, marker2_filt, minimum_size, maximum_size);


	// Greater Or Equal Constant; convert label image to binary
	constant = 1.0;
	Ext.CLIJ2_greaterOrEqualConstant(marker2_filt, marker2_bin, constant);
	Ext.CLIJ2_release(marker2_filt);

	
	// Mean Intensity Map -> Area fraction
	Ext.CLIJ2_meanIntensityMap(marker2_bin, marker1, area_frac);
	Ext.CLIJ2_release(marker2_bin);

	// Greater Or Equal Constant
	constant = 0.4;
	Ext.CLIJ2_greaterOrEqualConstant(area_frac, marker2_area_filt, constant);
	Ext.CLIJ2_release(area_frac);
	

	// Label Overlap Count Map; 
	//marker1 is ref image and counting how many labels in 6 overlap with image_11
	//Ext.CLIJ2_labelOverlapCountMap(marker1,image_6, image_7);
	//Ext.CLIJ2_release(image_6);
	
	// get cells with overlap of greater than 1 cell
	//constant = 1.0;
	//Ext.CLIJ2_greaterOrEqualConstant(image_7, image_8, constant);
	//Ext.CLIJ2_release(image_7);
	
	// Multiply Images
	Ext.CLIJ2_multiplyImages(marker1, marker2_area_filt, marker2_processed);
	Ext.CLIJ2_release(marker2_area_filt);
	Ext.CLIJ2_release(marker1);
	
	Ext.CLIJ2_closeIndexGapsInLabelMap(marker2_processed, marker2_idx);
	Ext.CLIJ2_release(marker2_processed);
	Ext.CLIJ2_pull(marker2_idx);
	//waitForUser;
	setTool(3); //freehand tool
	return marker2_idx;
}


//remove space from strings in array
function trim_space_arr(arr)
{
	for (i = 0; i < arr.length; i++)
	{
		temp=String.trim(arr[i]); //arr[i]+val;
		arr[i]=temp;
	}
return arr;
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

//convert arr to int
function convert_array_int(arr)
{
	for (i = 0; i < arr.length; i++)
	{
		arr[i]=parseInt(arr[i]);
	}
return arr;
}


//function to scale imag
function scale_image(img,scale_factor,name)
{
	if(scale_factor!=1)
		{	
			selectWindow(img);
			Stack.getDimensions(width, height, channels, slices, frames);
			new_width=round(width*scale_factor); 
			new_height=round(height*scale_factor);
			scaled_img=name+"_resize";
			run("Scale...", "x=- y=- width="+new_width+" height="+new_height+" interpolation=None create title="+scaled_img);
			//close(img);
			selectWindow(name+"_resize");
			
		}
	else 
		{
			scaled_img=img;
		}
		return scaled_img;
}

//generate every possible combination of markers
function combinations(arr)
{
	len=arr.length;
	str="";
	p=Math.pow(2,len);
	arr_str=newArray();
	for(i=0;i<p;i++)
	{
		twopower=p;
		for(j=0;j<len;j++)
		{
			twopower=twopower/2;
			//& does the bit-wise AND of two integers and produces a third integer whose 
			//bit are set to 1 if both corresponding bits in the two source integers are both set to 1; 0 otherwise.
			if(i&twopower>0) //bitwise AND comparison
			{
				//print(arr[j]);
				str+=arr[j]+",";
			}
			//else print("Nothing");
		}
		if(str!="") str=substring(str,0,str.length-1); //if not empty string, remove the comma at the end
		arr_str[i]=str;
		str="";
		//str+="\n";
	}
	
	return arr_str;
}

//sort the string array based on the number of strings/markers
function sort_marker_array(arr)
{
		
	//print(no_combinations);
	rank_idx=1;
	rank_array=newArray();
	no_markers=1;
	//first value is empty string, so deleting that
	arr=Array.deleteValue(arr,"");
	no_combinations=arr.length;
	do
	{
		for (i = 0; i < no_combinations; i++) 	
		{
				arr_str=split(arr[i], ",");
			 if(arr_str.length==no_markers)
				{
					rank_array[i]=rank_idx;
					rank_idx+=1;
				}
	
		}
		  
		no_markers+=1;
	}
	while (rank_idx<=no_combinations)
	
	//Array.show(arr);
	//Array.show(rank_array);
	//change order of markers based on the order specified in rank_array
	Array.sort(rank_array,arr);
	//Array.show(arr1);
	return arr;
}

//find if a string is contained within an array of strings
//case insensitive
function find_str_array(arr,name)
{

	name=".*"+toLowerCase(name)+".*";
	no_str=arr.length;
	position="NA";
	for (i=0; i<no_str; i++) 
	{ 
		if (matches(toLowerCase(arr[i]), name)) 
		{ 
		 position=i;
		} 
	} 
	return position;
}


//replace value in array
function replace_str_arr(arr,old,new)
{

	name=".*"+toLowerCase(old)+".*";
	no_str=arr.length;
	for (i=0; i<no_str; i++) 
	{ 
		if (matches(toLowerCase(arr[i]), name)) 
		{ 
		 arr[i]=new;
		} 
	} 
	return arr;
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

//Draw outline for ganglia or edit the predicted outline
function draw_ganglia_outline(ganglia_img,edit_flag)
{
	if(edit_flag==false)
	{
		roiManager("reset");
		setTool("freehand");
		selectWindow(ganglia_img);
		Stack.getDimensions(width, height, channels, slices, frames);
		waitForUser("Ganglia outline", "Draw outline of the ganglia. Press T every time you finish drawing an outline");
		roiManager("Deselect");
		newImage("Ganglia_outline", "8-bit black", width, height, 1);
		roiManager("Deselect");
		roiManager("Fill");
		return 	"Ganglia_outline";
	}
	else 
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
	getVoxelSize(vox_width, vox_height, vox_depth, vox_unit);
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
	setVoxelSize(vox_width, vox_height, vox_depth, vox_unit);
	close(img);
	return max_name;
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

//set group id for rois
function set_all_rois_group_id(group_id)
{
	
//select all rois in roi manager
	selection_indexes = Array.getSequence(roiManager("count"));
	roiManager("select", selection_indexes);
	RoiManager.setGroup(0);
}

