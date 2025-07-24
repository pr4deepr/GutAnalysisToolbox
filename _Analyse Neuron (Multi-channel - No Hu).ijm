
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
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
//FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
//BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

//Get neuronal counts from multichannel images without Hu
//Similar to analyze neuron multichannel script but doesn't have Hu detection or spatial analysis option

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

ganglia_model = Table.getString("Values", 12);//deepimagej model for ganglia
neuron_subtype_file = Table.getString("Values", 10);
//neuron_subtype_deepimagej_file = Table.getString("Values", 14);//deepimagej model for neuron subtype
selectWindow("Results");
run("Close");

//deepimagej
neuron_subtype_path = models_dir+fs+neuron_subtype_file;
ganglia_model_path = models_dir+fs+ganglia_model;
if(!File.exists(neuron_subtype_path)) exit("Cannot find models for segmenting neuronal subtypes at these paths:\n"+neuron_subtype_path);
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

//check if ganglia cell count is present
var ganglia_label_cell_count=gat_dir+fs+"Calculate_Neurons_per_Ganglia_label.ijm";
if(!File.exists(ganglia_label_cell_count)) exit("Cannot find ganglia label image cell count script. Returning: "+ganglia_label_cell_count)

//check if ganglia prediction  macro present
var segment_ganglia=gat_dir+fs+"Segment_Ganglia.ijm";
if(!File.exists(segment_ganglia)) exit("Cannot find segment ganglia script. Returning: "+segment_ganglia);


var spatial_two_cell_type=gat_dir+fs+"spatial_two_celltype.ijm";
if(!File.exists(spatial_two_cell_type)) exit("Cannot find spatial analysis script. Returning: "+spatial_two_cell_type);

//check if import custom ganglia rois script is present
var ganglia_custom_roi=gat_dir+fs+"ganglia_custom_roi.ijm";
if(!File.exists(ganglia_custom_roi)) exit("Cannot find single ganglia custom roi script. Returning: "+ganglia_custom_roi);

//check if import save centroids script is present
var save_centroids=gat_dir+fs+"save_centroids.ijm";
if(!File.exists(save_centroids)) exit("Cannot find save_centroids custom roi script. Returning: "+save_centroids);

//check if rename_rois script is present
var rename_rois=gat_dir+fs+"rename_rois.ijm";
if(!File.exists(rename_rois)) exit("Cannot find rename_rois custom roi script. Returning: "+rename_rois);

//check if save_roi_composite_img is present
var save_composite_img=gat_dir+fs+"save_roi_composite_img.ijm";
if(!File.exists(save_composite_img)) exit("Cannot find save_composite_img custom roi script. Returning: "+save_composite_img);

//stardist_subtype_postprocessing = neuron_subtype_deepimagej_path+fs+"stardist_postprocessing.ijm";
//if(!File.exists(stardist_subtype_postprocessing)) exit("Cannot find startdist postprocessing script for neuron subtype. Returning: "+stardist_subtype_postprocessing);

#@ File (style="open", label="<html>Choose the image to segment.<br><b>Enter NA if field is empty.</b><html>", value=fiji_dir) path
#@ boolean image_already_open
#@ String(value="<html>If image is already open, tick above box.<html>", visibility="MESSAGE") hint1
#@ String(value="<html> Tick box below if you know channel name and numbers<br/> The order of channel numbers MUST match with channel name order.<html>",visibility="MESSAGE") hint5
#@ boolean Enter_channel_details_now
#@ String(label="Enter channel names followed by a comma (,). Enter NA if not using.", value="NA") marker_names_manual
#@ String(label="Enter channel numbers with separated by a comma (,). Leave as NA if not using.", value="NA") marker_no_manual
#@ String(value="<html>----------------------------------------------------------------------------------------------------------------------------------------<html>",visibility="MESSAGE") divider
#@ String(value="<html><center><b>DETERMINE GANGLIA OUTLINE</b></center> <html>",visibility="MESSAGE") hint_ganglia
#@ String(value="<html> Cell counts per ganglia will be calculated<br/>Requires a neuron channel & second channel that labels the neuronal fibres.<html>",visibility="MESSAGE") hint4
#@ boolean Cell_counts_per_ganglia (description="Use a pretrained deepImageJ model to predict ganglia outline")
#@ String(label="<html> Enter the channel NUMBER that labels neuronal/glial fibres.<br/> Enter NA if not using.<html> ", value="NA") ganglia_channel
#@ String(label="<html> Enter the channel NUMBER for marker that labels most cells.<br/> Enter NA if not using.<html> ", value="NA") cell_channel
#@ String(choices={"DeepImageJ","Manually draw ganglia","Import custom ROI"}, style="radioButtonHorizontal") Ganglia_detection
#@ String(value="<html>----------------------------------------------------------------------------------------------------<html>",visibility="MESSAGE") adv
#@ String(value="<html>Finetune detection parameters are enabled by default<html>",visibility="MESSAGE") finentune_hint
#@ boolean Perform_Spatial_Analysis(description="<html><b>If ticked, it will perform spatial analysis for all markers. Convenient than performing them individually. -> </b><html>")
//#@ boolean Finetune_Detection_Parameters(description="<html><b>Enter custom rescaling factor and probabilities</b><html>")
#@ boolean Contribute_to_GAT(description="<html><b>Contribute to GAT by saving image and masks</b><html>") 


scale = 1;
//adjust probabilities by default
Finetune_Detection_Parameters=true;


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

//error catch if channel name or number is empty
if(Enter_channel_details_now==true && marker_names_manual=="NA" || marker_names_manual=="") exit("Enter channel name or untick Enter channel details option");
if(Enter_channel_details_now==true && marker_no_manual=="NA" || marker_no_manual=="") exit("Enter channel numbers or untick Enter channel details option");

if(Perform_Spatial_Analysis==true)
{
	Dialog.create("Spatial Analysis parameters");
  	Dialog.addSlider("Cell expansion distance (microns)", 0.0, 20.0, 6.5);
	Dialog.addCheckbox("Save parametric image/s?", true);
  	Dialog.show(); 
	label_dilation= Dialog.getNumber();
	save_parametric_image = Dialog.getCheckbox();
}

//listing parameters being used for GAT
print("Using parameters\nSegmentation pixel size:"+training_pixel_size+"\nMax neuron area (microns): "+neuron_area_limit+"\nMin marker area (microns): "+neuron_lower_limit);
print("**Neuron subtype\nProbability: "+probability_subtype+"\nOverlap threshold: "+overlap_subtype+"\n");




if(image_already_open==true)
{
	waitForUser("Select Image to analyze");
	file_name_full=getTitle(); //get file name without extension (.lif)
	selectWindow(file_name_full);
	close_other_images = getBoolean("Close any other open images?", "Close others", "Keep other images open");
	if(close_other_images)	close("\\Others");
	dir=getDirectory("Choose Output Folder");
	//file_name=File.nameWithoutExtension;
}
else
{
	if(endsWith(path, ".czi")|| endsWith(path, ".lif")) run("Bio-Formats", "open=["+path+"] color_mode=Composite rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT");
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

//Create results directory with file name in "analysis"
analysis_dir= dir+"Analysis"+fs;
if (!File.exists(analysis_dir)) File.makeDirectory(analysis_dir);



file_name_length=lengthOf(file_name_full); //length of filename
//if delimiters such as , ; or _ are there in file name, split string and join with underscore
file_name_split = split(file_name_full,",;_-");
file_name_full =String.join(file_name_split,"_");


//check if save location exists
save_location_exists = 1;
do
{
	if(file_name_length>50 ||save_location_exists == 1)
	{
	
		print("Filename will be shortened if its too long");
		if(file_name_length>50)
		{
			file_name_full=substring(file_name_full, 0, 20); //Restricting file name length as in Windows long path names can cause errors
		}
		else file_name_full = file_name_full;
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

print("Analysing: "+file_name);
print("Files will be saved at: "+results_dir); 

img_name=getTitle();
Stack.getDimensions(width, height, sizeC, sizeZ, frames);

max_save_name="MAX_"+file_name;


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
//Define scale factor to be used
target_pixel_size= training_pixel_size/scale;
scale_factor = pixelWidth/target_pixel_size;
if(scale_factor<1.001 && scale_factor>1) scale_factor=1;



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


table_name="Analysis_"+file_name;
Table.create(table_name);//Final Results Table
selectWindow(table_name);
Table.set("File name",0,file_name);
Table.update;

row=0; //row counter for the table
image_counter=0;


//added option for extended depth of field projection for widefield images
if(sizeZ>1)
{
		print(img_name+" is a stack");
		roiManager("reset");
		waitForUser("Verify the type of image projection you'd like (MIP or Extended depth of field\nYou can select in the next prompt.");
		projection_method=getBoolean("3D stack detected. Which projection method would you like?", "Maximum Intensity Projection", "Extended Depth of Field (Variance)");
		if(projection_method==1)
		{
			waitForUser("Note the starting and ending slice number of the Z-stack.\nThe slices used to create a Maximum Intensity Projection can be defined in the next prompt.\nPress OK when ready");
			Dialog.create("Choose Z-slices");
	  		Dialog.addNumber("Start slice:", 1);
	  		Dialog.addNumber("End slice:", sizeZ);
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


//calculate no. of tiles
new_width=round(width*scale_factor); 
new_height=round(height*scale_factor);
n_tiles=4;

if(new_width>2000 || new_height>2000) n_tiles=5;
if(new_width>4500 || new_height>4500) n_tiles=8;
if (new_width>9000 || new_height>9000) n_tiles=16;
if (new_width>15000 || new_height>15000) n_tiles=24;

print("No. of tiles: "+n_tiles);

//Segment ganglia
selectWindow(max_projection);
run("Select None");
run("Remove Overlay");

if (Cell_counts_per_ganglia==true)
{
	
		ganglia_seg_complete = false; //flag for ganglia segmentation QC checking
		
		//do while statement that checks if ganglia binary image occupies greater than 85% of image 
		//If so, issue a warning and ask if user would like to select a different ganglia seg option
		do 
		{	
		ganglia_roi_path=""; //ganglia_roi_path and batch_mode arguments not used here for now, but keeping it here for consistency with analyse_neurons macro
		batch_mode=false;
		
		//Segment ganglia
		selectWindow(max_projection);
		run("Select None");
		run("Remove Overlay");
		
		
		ganglia_binary = ganglia_segment(Ganglia_detection,max_projection, cell_channel, ganglia_channel,pixelWidth,ganglia_roi_path,batch_mode);
		                 
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
			ganglia_seg_options=newArray("DeepImageJ","Manually draw ganglia","Import custom ROI");
			Dialog.addRadioButtonGroup("Ganglia segmentation:", ganglia_seg_options, 3, 1, "DeepImageJ");
			Dialog.show();
			Ganglia_detection = Dialog.getRadioButton();
			print("Ganglia detection option chosen: "+Ganglia_detection);
			
	 	}
	
	  }
	  while(ganglia_seg_complete==false)
	 

	selectWindow(ganglia_binary);	
	run("Connected Components Labeling", "connectivity=8 type=[16 bits]");
	wait(5);
	ganglia_label = getTitle();
	// flag of 1 is for ganglia segmentation
	args = ganglia_label+","+1;
	runMacro(label_to_roi,args);
	roiManager("deselect");
	    
    wait(5);
    //rename rois
    runMacro(rename_rois,"Ganglia");
	
	//save composite image with ganglia overlay
    args = max_projection+","+results_dir+",Ganglia";
    runMacro(save_composite_img,args);
	
	ganglia_number=roiManager("count");
	roi_location=results_dir+"Ganglia_ROIs_"+file_name+".zip";
	roiManager("save",roi_location );
	roiManager("reset");
	close(ganglia_label);
	
}
else ganglia_binary = "NA";





//neuron_subtype_matrix=0;
no_markers=0;
//if user wants to enter markers before hand, can do that at the start
//otherwise, option to enter them manually here
arr=Array.getSequence(sizeC);
arr=add_value_array(arr,1);
if(Enter_channel_details_now==true)
{
	if(marker_names_manual!="NA")
	{
		//get user-entered markers into a array of strings
		marker_names_manual=split(marker_names_manual, ",");
		//trim space from names
		marker_names_manual=trim_space_arr(marker_names_manual);
		//get channel numbers into an array
		marker_no_manual=split(marker_no_manual, ",");
		if(marker_names_manual.length!=marker_no_manual.length) exit("Number of marker names and marker channels do not match");

		channel_names=marker_names_manual;//split(marker_names_manual, ",");
		channel_numbers=marker_no_manual;//split(marker_no_manual, ",");
		//get channel numbers by parsing array and converting values to integer
		channel_numbers=convert_array_int(marker_no_manual);
		no_markers=channel_names.length;
	}
	else exit("Marker names not defined");

}
else 
{
	waitForUser("Define the channel names and numbers for analysis in the next prompt");
	no_markers=getNumber("How many markers would you like to analyse?", 1);
	string=getString("Enter names of markers separated by comma (,)", "Names");
	channel_names=split(string, ",");	
	if(channel_names.length!=no_markers) exit("The number of marker names does not match the number of marker channels. Check the entry and retry");
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
	}
	
}

//custom probability for subtypes
//create dialog box based on number of markers
probability_subtype_arr=newArray(channel_names.length);
custom_roi_subtype_arr=newArray(channel_names.length);

if(Finetune_Detection_Parameters==true)
{
	print("Using manual probability and overlap threshold for detection");
	Dialog.create("Advanced Parameters");
	Dialog.addMessage("Default values shown below will be used if no changes are made");
	Dialog.addNumber("Rescaling Factor", scale, 3, 8, "") 
  	
  	for ( i = 0; i < marker_names_manual.length; i++) 
  	{
		
	    Dialog.addSlider("Probability for "+marker_names_manual[i], 0, 1,probability_subtype);
	    Dialog.addToSameRow();
  		Dialog.addCheckbox("Custom ROI", 0);	    
	}
	
  	Dialog.addSlider("Overlap threshold", 0, 1,overlap);
	Dialog.show(); 
	scale = Dialog.getNumber();
	

	for ( i = 0; i < marker_names_manual.length; i++) 
  	{
	    probability_subtype_arr[i]= Dialog.getNumber();
	    custom_roi_subtype_arr[i]=Dialog.getCheckbox();
	}
	
	overlap_subtype= Dialog.getNumber();
}

else 
{ //assign probability subtype default values to all of them
	for ( i = 0; i < marker_names_manual.length; i++) 
  	{
		
		custom_roi_subtype_arr[i]=false;
		probability_subtype_arr[i]=probability_subtype;
	    
	}
}

print("**Neuron subtype\nProbability for");;
Array.print(marker_names_manual);
Array.print(probability_subtype_arr);
print("Overlap threshold: "+overlap_subtype+"\n");


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
//array to store the channel names displayed in table
display_ch_names = newArray();

//iterate through all the channel combinations
//perform segmentation and update table
for(i=0;i<channel_combinations.length;i++)
{
	print("Getting counts for cells positive for marker/s: "+channel_combinations[i]);
	//get channel names as an array
	channel_arr=split(channel_combinations[i], ",");
	
	if(channel_arr.length==1) //if first marker or cycles through each individual marker
	{
		if(no_markers==1) //if only one marker
		{
			//channel_position=channel_numbers[i];
			channel_no=channel_numbers[i];
			channel_name=channel_names[i];
			probability_subtype_val = probability_subtype_arr[i];
		}
		else  //multiple markers
		{
			//find position index of the channel by searching for name
			channel_idx=find_str_array(channel_names,channel_combinations[i]); //find channel the marker is in by searching for the name in array with name combinations
			//use index of position to get the channel number and the channel name
			channel_no=channel_numbers[channel_idx]; //idx fro above is returned as 0 indexed, so using this indx to find channel no
			channel_name=channel_names[channel_idx];
			probability_subtype_val = probability_subtype_arr[channel_idx];
		}			
				
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
			getPixelSize(unit, rescaled_pixelWidth, rescaled_pixelHeight);
			

			
			if(custom_roi_subtype_arr[i])
			{
				print("Importing ROIs for "+channel_name);
				custom_subtype_roi_path = File.openDialog("Choose custom ROI for "+channel_name);
				roiManager("open", custom_subtype_roi_path);
				runMacro(roi_to_label);
				wait(5);
				rename("label_img_temp");
			}
			else
			{
			
			print("Probability for detection "+probability_subtype_val);
			segment_cells(max_projection,seg_marker_img,neuron_subtype_path,n_tiles,width,height,scale_factor,neuron_seg_lower_limit,probability_subtype_val,overlap_subtype);
			selectWindow(max_projection);
			roiManager("deselect");
			runMacro(roi_to_label);
			wait(5);
			rename("label_img_temp");
						
			}
			close(seg_marker_img);
			
			selectWindow("label_img_temp");
			run("glasbey_on_dark");
			//selectWindow("label_mapss");
			run("Select None");
			roiManager("reset");

			selectWindow("label_img_temp");

			run("Select None");
			runMacro(label_to_roi,"label_img_temp");
			close("label_img_temp");
			wait(5);
			
			//save original rois so any modified rois can be verified later
			roiManager("deselect");
	        roi_location_marker=results_dir+channel_name+"_unmodified_ROIs_"+file_name+".zip";    
	        roiManager("save",roi_location_marker);
	        print("Saved unmodified ROIs for "+channel_name+" from GAT detection at "+roi_location_marker);
	        
			selectWindow(max_projection);
			run("Remove Overlay");
			//roiManager("deselect");
			
			
			roiManager("UseNames", "false");
			roiManager("show all");

			//selectWindow(max_projection);
			waitForUser("Verify ROIs for "+channel_name+". Add or delete ROIs as needed using the ROI Manager.\nIf no cells were detected, the ROI Manager will be empty.\nPress OK when done.");
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
			
			//Table.set("Marker Combinations", row, channel_name);
			//Table.set("Number of cells per marker combination", row, marker_count);
			//Table.set("|", row, "|");
			Table.set(channel_name, 0,marker_count);
			display_ch_names[i]=channel_name;
			
			Table.update;
			row+=1;
			
			//selectWindow(max_projection);
			roiManager("deselect");
			//roi_file_name= String.join(channel_arr, "_");
			if(roiManager("count")>0)
			{
				//rename rois
                runMacro(rename_rois,channel_name);
                roi_location_marker=results_dir+channel_name+"_ROIs_"+file_name+".zip";
				roiManager("save",roi_location_marker);
				
				//save composite image with roi overlay
                args = max_projection+","+results_dir+","+channel_name;
                runMacro(save_composite_img,args);
			}
			
			
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
				print("Counting the number of "+channel_name+" cells per ganglia. This may take some time for large images.");
				
				//label_overlap is the ganglia where each of them are labels
				selectWindow("label_overlap");
				run("Select None");
				run("Duplicate...", "title=ganglia_label_img");	
				//using this for neuronal subtype analysis
				ganglia_label_img = "ganglia_label_img";
				
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
				//cell_count_per_ganglia=Array.deleteValue(cell_count_per_ganglia, 0);

				
				selectWindow(table_name);
				Table.set("No of ganglia",0, ganglia_number);
				Table.setColumn(channel_name+" counts per ganglia", cell_count_per_ganglia);
				Table.update;
				
				selectWindow("cells_ganglia_count");
				run("Close");
				roiManager("reset");
			}
			//as there is no hu, not performing spatial analysis between Hu and marker
			//only save centroids
			if(Perform_Spatial_Analysis==true)
			{
				//save centroids of rois; this can be used for spatial analysis
				//get centroids in microns
				selectWindow(label_marker);
				setVoxelSize(pixelWidth, pixelHeight, 1, unit);
				args=results_dir+","+channel_name+","+roi_location_marker;
				runMacro(save_centroids,args);
				print("Centroids saved");
			}
		close(label_marker);
			
		}
		//if more than one marker to analyse; if more than one marker, then it multiplies the marker labels from above to find coexpressing cells
		else if(channel_arr.length>=1) 
		{
	
			for(j=0;j<channel_arr.length;j++)
			{
				marker_name="_"+channel_arr[j];
				//print("Getting counts for cells positive for markers: "+String.join(channel_arr, " "));
				channel_pos=find_str_array(marker_label_arr,marker_name); //look for marker with _ in its name.returns index of marker_label image
				//channel_as=channel_numbers[channel_pos-1]; //use index to get the channel number value
				//print(marker_label_arr[channel_pos]+"\t");
				//print(channel_pos);
				//print("Processing: "+marker_label_arr[channel_pos]+"\t"); 
				if(j==0) //if first time running the loop
				{		

						//multiply_markers
						marker_name="_"+channel_arr[1]; //get img2
						channel_pos2=find_str_array(marker_label_arr,marker_name);
						img1=marker_label_arr[channel_pos];
						img2=marker_label_arr[channel_pos2];
						//print(channel_pos2);
						print("Processing "+img1+" * "+img2);
						temp_label=multiply_markers(img1,img2,neuron_min_pixels,neuron_max_pixels);
						selectWindow(temp_label);
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

						run("Select None");
						//runMacro(label_to_roi,temp_label);
						//close(temp_label);
						//close(img1);
						//close(img2);
						wait(5);
						selectWindow(temp_label);
						run("Select None");
						rename(img1+"_"+img2);
						result=img1+"_"+img2;
						j=j+1;
						
						//image names are label_markername_resize; , we are exracting markername by splitting at "_"
						img1_name_arr = split(img1, "_");
						img1_name = img1_name_arr[1];
						img2_name_arr = split(img2, "_");
						img2_name = img2_name_arr[1];
						roi_file_name = img1_name+"_"+img2_name;	
						
					    if(Perform_Spatial_Analysis==true)
						{
							print("Performing Spatial Analysis for "+img1+" and "+img2+" done");
							if(Cell_counts_per_ganglia==true)
							{
								ganglia_binary_rescaled = ganglia_binary+"_resize";
								if(!isOpen(ganglia_binary_rescaled))
								{
									ganglia_binary_rescaled=scale_image(ganglia_binary,scale_factor,ganglia_binary);
								}
							}
							else ganglia_binary_rescaled="NA";
							//get roi locations for spatial analysis
							roi_location_img1 = results_dir+img1_name+"_ROIs_"+file_name+".zip";
							roi_location_img2 = results_dir+img2_name+"_ROIs_"+file_name+".zip";
							
							args=img1_name+","+img1+","+img2_name+","+img2+","+ganglia_binary_rescaled+","+results_dir+","+label_dilation+","+save_parametric_image+","+rescaled_pixelWidth+","+roi_location_img1+","+roi_location_img2;
							runMacro(spatial_two_cell_type,args);
							//not saving centroids as roi saving is later in code; for now, will need to get them from ROI manager file
							print("Spatial Done");
							
						}
				}
				else 
				{
					img2=marker_label_arr[channel_pos];
					img1=result;
					print("Processing "+img1+" * "+img2);
					temp_label=multiply_markers(img1,img2,neuron_min_pixels,neuron_max_pixels);
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
					
											
					//image names are label_markername_resize; , we are extracting markername by splitting at "_"
					img1_name_arr = split(img1, "_");
					img1_name = img1_name_arr[1];
					img2_name_arr = split(img2, "_");
					img2_name = img2_name_arr[1];
					roi_file_name = img1_name+"_"+img2_name;
					
					if(Perform_Spatial_Analysis==true)
					{
						print("Performing Spatial Analysis for "+img1+" and "+img2+" done");
						roi_location_img1 = results_dir+img1_name+"_ROIs_"+file_name+".zip";
						roi_location_img2 = results_dir+img2_name+"_ROIs_"+file_name+".zip";
						//rescaled images being passed here so, we need to change pixelWidth to make sure label dilation in pixels is accurate
						args=img1_name+","+img1+","+img2_name+","+img2+","+ganglia_binary_rescaled+","+results_dir+","+label_dilation+","+save_parametric_image+","+rescaled_pixelWidth+","+roi_location_img1+","+roi_location_img2;
						runMacro(spatial_two_cell_type,args);
						print("Spatial Done");
						
					}
					close(img1);
					close(img2);

					wait(5);
					result="img "+d2s(j,0);
					selectWindow(temp_label);
					rename(result);
					
				}
			//if(j==channel_arr.length-1) //when reaching end of arr length, get ROI counts
			//{
			//save rois generated from above
				selectWindow(result);
				run("Select None");
				roiManager("reset");
				runMacro(label_to_roi,result);
				//save with name of channel_combinations[i]
				wait(10);
				//selectWindow(max_projection);

				roiManager("deselect");
				//roi_file_name= String.join(channel_arr, "_");
				roi_location_marker=results_dir+roi_file_name+"_ROIs_"+file_name+".zip";
				//if no cells in marker combination
				if(roiManager("count")>0)
				{
					//rename and save
                    runMacro(rename_rois,roi_file_name);
					roiManager("save",roi_location_marker);
					//save composite image with roi_overlay
                    args = max_projection+","+results_dir+","+roi_file_name;
                    runMacro(save_composite_img,args);
				}

				marker_count=roiManager("count"); // in case any neurons added after analysis of markers
				selectWindow(table_name);
				Table.set(roi_file_name, 0, marker_count);
				display_ch_names[i]=roi_file_name;
				Table.update;
				row+=1;


				
				
				
				if (Cell_counts_per_ganglia==true)
				{
					if(roiManager("count")>0)
					{
						selectWindow(result);
						run("Remove Overlay");
						run("Select None");
						
						//pass label image for ganglia
						args=result+","+ganglia_label_img;
						///use label image from above
						//get cell count per ganglia
						runMacro(ganglia_label_cell_count,args);
						
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

				
roiManager("reset");

				
			//}
		}
		close(result);
	  }
   }


close("label_img_*");

//remove zeroes in the array
//Array.show(display_ch_names);
print(display_ch_names.length);
for(name=0;name<display_ch_names.length;name++)
{
	//remove zeroes in the array
	selectWindow(table_name);
	//print(name);
	//print(display_ch_names[name]);
	marker_combinations=Table.getColumn(display_ch_names[name]); 
	marker_combinations=Array.deleteValue(marker_combinations, 0);
	//if all values zero, make sure first value is set to 0 (for the table)
	if(marker_combinations.length==0) marker_combinations[0]=0;
	Table.setColumn(display_ch_names[name], marker_combinations);
}



//remove zeroes in the file name
selectWindow(table_name);
file_array=Table.getColumn("File name"); 
file_array=Array.deleteValue(file_array, 0);
Table.setColumn("File name", file_array);



//get ganglia area
if (Cell_counts_per_ganglia==true)
{
	
	args = ganglia_label_img+","+1;
	runMacro(label_to_roi,args);
	run("Set Measurements...", "area redirect=None decimal=3");
	run("Clear Results");
	roiManager("Deselect");
	//get measurements in microns
	selectWindow(max_projection);
	roiManager("Measure");
	selectWindow("Results");
	ganglia_area = Table.getColumn("Area");
	selectWindow(table_name);
	Table.setColumn("Area_per_ganglia_um2", ganglia_area);
}

selectWindow(table_name);
Table.save(results_dir+table_name+"_cell_counts.csv");

//save max projection if its scaled image, can use this for further processing later
selectWindow(max_projection);
run("Remove Overlay");
run("Select None");

saveAs("Tiff", results_dir+max_save_name);
//run("Close");
roiManager("UseNames", "false");

print("Files saved at: "+results_dir);
run("Clear Results");

selectWindow("Log");
saveAs("Text", results_dir+"Log.txt");

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
	//choice=0;

	roiManager("reset");
	selectWindow(img_seg);
	run("Command From Macro", "command=[de.csbdresden.stardist.StarDist2D],args=['input':'"+img_seg+"', 'modelChoice':'Model (.zip) from File', 'normalizeInput':'true', 'percentileBottom':'1.0', 'percentileTop':'99.8', 'probThresh':'"+probability+"', 'nmsThresh':'"+overlap+"', 'outputType':'Label Image', 'modelFile':'"+model_file+"', 'nTiles':'"+n_tiles+"', 'excludeBoundary':'2', 'roiPosition':'Automatic', 'verbose':'false', 'showCsbdeepProgress':'false', 'showProbAndDist':'false'], process=[false]");
	
	//run("DeepImageJ Run", "modelPath=["+neuron_deepimagej_file+"] inputPath=null outputFolder=null displayOutput=all");
	
	wait(50);
	// generate label image from deepimagej output
    temp=getTitle(); 
    selectWindow(temp);
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
	//marker 1 is ref channel For neuron segmentation with Hu, its Hu and then marker of choice in marker2
	// Init GPU
	run("CLIJ2 Macro Extensions", "cl_device=");
	Ext.CLIJ2_clear();
	Ext.CLIJ2_pushCurrentZStack(marker1);
	Ext.CLIJ2_pushCurrentZStack(marker2);
	
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
function draw_ganglia_outline(max,cell_channel,ganglia_channel,edit_flag)
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
	ganglia_img=getTitle();

	close(composite_img);

	if(edit_flag==false)
	{
		roiManager("reset");
		setTool("freehand");
		selectWindow(ganglia_img);
		Stack.getDimensions(width, height, channels, slices, frames);
		nrois = roiManager("count");
		do 
		{
		waitForUser("Ganglia outline", "Draw an outline of the ganglia.\nMake sure to press T every time you finish drawing an outline\nThis adds the ROI to the ROI Manager");
		nrois = roiManager("count");
		}
		while(nrois==0)
		
		roiManager("Deselect");
		newImage("Ganglia_outline", "8-bit black", width, height, 1);
		roiManager("Deselect");
		roiManager("Fill");
		close(ganglia_img);
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

//segment ganglia
function ganglia_segment(Ganglia_detection,max_projection, cell_channel, ganglia_channel,pixelWidth,ganglia_roi_path,batch_mode)
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
	 	//draw_ganglia_outline(ganglia_binary,true);
	 	
	 }

	 else if(Ganglia_detection=="Import custom ROI")
	 {
		print("Importing custom ROI");
		args1=max_projection;
		runMacro(ganglia_custom_roi,args1);
		ganglia_binary=getTitle();
	 	
	 }
	 else if(Ganglia_detection=="Manually draw ganglia")
	 {
	 	print("Manually draw ganglia");
	 	ganglia_binary=draw_ganglia_outline(max_projection,cell_channel,ganglia_channel,false);

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