
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
neuron_model_file = Table.getString("Values", 9);
selectWindow("Results");
run("Close");


//Neuron segmentation model
neuron_model_path=models_dir+neuron_model_file;
if(!File.exists(neuron_model_path)) exit("Cannot find models for segmenting neurons at these paths:\n"+neuron_model_path);

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




#@ File (style="open", label="<html>Choose the image to segment.<br><b>Enter NA if image is open or if field is empty.</b><html>", value=fiji_dir) path
#@ boolean image_already_open
#@ String(value="<html>If image is already open, tick above box.<html>", visibility="MESSAGE") hint1
#@ String(label="Enter channel number for Hu if you know. Enter NA if not using.", value="NA") cell_channel
// File (style="open", label="<html>Choose the StarDist model file if segmenting neurons.<br>Enter NA if empty<html>",value="NA", description="Enter NA if nothing") neuron_model_path 
cell_type="Neuron";
#@ String(value="<html>----------------------------------------------------------------------------------------------------------------------------------------<html>",visibility="MESSAGE") hint_star
#@ String(value="<html><center><b>DETERMINE GANGLIA OUTLINE</b></center> <html>",visibility="MESSAGE") hint_ganglia
#@ String(value="<html> Cell counts per ganglia will be calculated<br/>Requires a neuron channel & second channel that labels the neuronal fibres.<html>",visibility="MESSAGE") hint4
#@ boolean Cell_counts_per_ganglia (description="Use a pretrained deepImageJ model to predict ganglia outline")
#@ String(choices={"DeepImageJ","Define ganglia using Hu","Manually draw ganglia","Import custom ROI"}, style="radioButtonHorizontal") Ganglia_detection
#@ String(label="<html> Enter the channel number for segmenting ganglia.<br/> Not valid for 'Define ganglia using Hu and Import custom ROI'.<br/> Enter NA if not using.<html> ", value="NA") ganglia_channel
#@ String(value="<html>----------------------------------------------------------------------------------------------------<html>",visibility="MESSAGE") adv
#@ boolean Perform_Spatial_Analysis(description="<html><b>If ticked, it will perform spatial analysis for all markers. Convenient than performing them individually. -> </b><html>")
#@ boolean Finetune_Detection_Parameters(description="<html><b>Adjust Probabilities or import custom ROIs</b><html>")
#@ boolean Contribute_to_GAT(description="<html><b>Contribute to GAT by saving image and masks</b><html>") 

scale = 1;

if(Finetune_Detection_Parameters==true)
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
	img_masks_path = getDirectory("Choose Folder to save images and masks");
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

print(file_name);

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

//Training images were pixelsize of ~0.568,
//scale_factor=pixelWidth/training_pixel_size;
target_pixel_size= training_pixel_size/scale;
scale_factor = pixelWidth/target_pixel_size;
if(scale_factor<1.001 && scale_factor>1) scale_factor=1;


print("Analysing: "+file_name);
analysis_dir= dir+"Analysis"+fs;
if (!File.exists(analysis_dir)) File.makeDirectory(analysis_dir);
print("Analysing: "+file_name);
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


table_name="Analysis_"+cell_type+"_"+file_name;
Table.create(table_name);//Final Results Table
row=0; //row counter for the table
image_counter=0;

//parse cell and ganglia channels and check if value is Integer
if(cell_channel!="NA")
{
	cell_channel=parseInt(cell_channel);
	if(isNaN(cell_channel)) exit("Enter channel number for cell. If leaving empty, type NA in the value");
	
}


//if more than one channel , check if appropriate values entered
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
	else if(Cell_counts_per_ganglia==true && cell_channel!="NA" && ganglia_channel!="NA")
	{

		ganglia_channel=parseInt(ganglia_channel);
		if(isNaN(ganglia_channel)) exit("Enter channel number for Ganglia. If leaving empty, type NA in the value");

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
else cell_channel = 1;




//add option for extended depth of field projection for widefield images
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

//if custom ROIs for Hu, import ROI here
if(custom_roi_hu)
{
	print("Importing ROIs for Hu");
	custom_hu_roi_path = File.openDialog("Choose custom ROI for Hu");
	roiManager("open", custom_hu_roi_path);
}
else
{

	print("*********Segmenting cells using StarDist********");

	//segment neurons using StarDist model
	segment_cells(max_projection,seg_image,neuron_model_path,n_tiles,width,height,scale_factor,neuron_seg_lower_limit,probability,overlap);
}

//close(seg_image);

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

selectWindow(max_projection);
roiManager("show all");
//manually correct or verify if needed
waitForUser("Correct "+cell_type+" ROIs if needed. You can delete or add ROIs using ROI Manager");
cell_count=roiManager("count");
roiManager("deselect");


print("No of "+cell_type+" in "+max_projection+" : "+cell_count);
roiManager("deselect");
roi_location=results_dir+cell_type+"_ROIs_"+file_name+".zip";
roiManager("save",roi_location);

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
if (Cell_counts_per_ganglia==true)
{
	roiManager("reset");
	if(Ganglia_detection=="DeepImageJ")
	 {
	 	args=max_projection+","+cell_channel+","+ganglia_channel;
		//get ganglia outline
		runMacro(segment_ganglia,args);
	 	wait(5);
	 	ganglia_binary=getTitle();
	 	draw_ganglia_outline(ganglia_binary,true);
	 	
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
		
	 	/*
		run("Select None");
		Stack.setChannel(cell_channel);
		run("Duplicate...", "title=ganglia_hu duplicate channels="+cell_channel);
		Dialog.create("Choose cell expansion distance (um)");
		Dialog.addMessage("Choose cell expansion distance to define the ganglia");
	  	Dialog.addNumber("Cell expansion (um)", 10);
	  	Dialog.show(); 
	  	cell_expansion=Dialog.getNumber();
	  	
		label_dilation=round(cell_expansion/pixelWidth);
		print("******Ganglia segmentation using user-defined cell expansion radius********");
		print("Expansion in pixels "+label_dilation);
		print("Corresponding expansion in microns "+cell_expansion);
		print("**************");
		
		run("CLIJ2 Macro Extensions", "cl_device=");

		Ext.CLIJ2_push(neuron_label_image);
		Ext.CLIJ2_dilateLabels(neuron_label_image, dilated, label_dilation);
		Ext.CLIJ2_greaterConstant(dilated, ganglia_binary, 1);
		Ext.CLIJ2_release(dilated);
		Ext.CLIJ2_pull(ganglia_binary);
		Ext.CLIJ2_pull(neuron_label_image);*/
	 }
	 else if(Ganglia_detection=="Import custom ROI")
	 {
		args1=neuron_label_image;
		//get ganglia outline
		runMacro(ganglia_custom_roi,args1);
		ganglia_binary=getTitle();
	 	
	 }
	 else
	 {
	 	ganglia_binary=draw_ganglia_outline(ganglia_img,false);
	 }
	 
	args=neuron_label_image+","+ganglia_binary;
	
	//get cell count per ganglia
	print("Getting Cell count per ganglia. May take some time for large images.");
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
	roiManager("deselect");
	ganglia_number=roiManager("count");
	run("Set Measurements...", "area redirect=None decimal=3");
	run("Clear Results");
	roiManager("Deselect");
	selectWindow(max_projection);
	roiManager("Measure");
	selectWindow("Results");
	ganglia_area = Table.getColumn("Area");
	
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

//update table
Table.update;
Table.save(results_dir+cell_type+"_"+file_name+".csv");

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
	Dialog.create("Spatial Analysis parameters");
  	Dialog.addSlider("Cell expansion distance (microns)", 0.0, 20.0, 6.5);
	Dialog.addCheckbox("Save parametric image/s?", true);
  	Dialog.show(); 
	label_dilation= Dialog.getNumber();
	save_parametric_image = Dialog.getCheckbox();
	args=cell_type+","+neuron_label_image+","+ganglia_binary+","+results_dir+","+label_dilation+","+save_parametric_image+","+pixelWidth;
	runMacro(spatial_single_cell_type,args);
	
	//save centroids of rois; this can be used for spatial analysis
	selectWindow(neuron_label_image);
	setVoxelSize(pixelWidth, pixelHeight, 1, unit);
	args=results_dir+","+cell_type+","+neuron_label_image;
	runMacro(save_centroids,args);	
	
	
	print("Spatial Analysis for "+cell_type+" done");
}

//save max projection if its scaled image, can use this for further processing later
selectWindow(max_projection);
run("Remove Overlay");
run("Select None");
saveAs("Tiff", results_dir+max_save_name);
run("Clear Results");

close("*");
print("DATA saved at "+results_dir);
exit("Neuron analysis complete");


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
	run("Command From Macro", "command=[de.csbdresden.stardist.StarDist2D],args=['input':'"+img_seg+"', 'modelChoice':'Model (.zip) from File', 'normalizeInput':'true', 'percentileBottom':'1.0', 'percentileTop':'99.8', 'probThresh':'"+probability+"', 'nmsThresh':'"+overlap+"', 'outputType':'Both', 'modelFile':'"+model_file+"', 'nTiles':'"+n_tiles+"', 'excludeBoundary':'2', 'roiPosition':'Automatic', 'verbose':'false', 'showCsbdeepProgress':'false', 'showProbAndDist':'false'], process=[false]");
	
	//make sure cells are detected for Hu.. if not exit macro
	if(roiManager("count")==0) exit("No cells detected. Reduce probability or check image.\nAnalysis stopped");
	else roiManager("reset");
	
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
		Stack.getDimensions(width, height, channels, slices, frames);
		waitForUser("Ganglia outline", "Draw outline of the ganglia. Press T every time you finish drawing an outline");
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
