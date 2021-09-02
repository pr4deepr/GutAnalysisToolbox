//Macro for segmenting Glia using Sox10 
//Need to choose the right model
//Accommodates for a scaling factor

//*******
// Author: Pradeep Rajasekhar
// March 2021
// License: BSD3
// 
// Copyright 2021 Pradeep Rajasekhar, Monash Institute of Pharmaceutical Sciences
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
var gat_dir=fiji_dir+"scripts"+fs+"GAT"+fs+"Other"+fs+"commands";

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

//check if ganglia prediction post processing macro present
var deepimagej_post_processing=gat_dir+fs+"Ganglia_prediction_post_processing.ijm";
if(!File.exists(deepimagej_post_processing)) exit("Cannot find roi to label script. Returning: "+deepimagej_post_processing);
 
var fs = File.separator; //get the file separator for the computer (depending on operating system)

#@ File (style="open", label="<html>Choose the image to segment.<br>Enter NA if image is open.<html>") path
#@ boolean image_already_open
#@ String(value="<html>If image is already open, tick above box.<html>", visibility="MESSAGE") hint1
#@ File (style="open", label="<html>Choose the StarDist model file if segmenting glia.<br>Enter NA if empty<html>",value="NA", description="Enter NA if nothing") neuron_model_path 
cell_type="Glia";
#@ String(value="<html> Cell counts per ganglia will get cell counts for each ganglia<br/>If you have a channel for neuron and another marker that labels the ganglia (PGP9.5/GFAP/NOS)<br/>that should be enough. You can also manually draw the ganglia<html>",visibility="MESSAGE") hint4
#@ boolean Cell_counts_per_ganglia (description="Use a pretrained deepImageJ model to predict ganglia outline")
#@ boolean Use_DeepImageJ
//add an option for defining a custom scaling factor
//Cell_counts_per_ganglia=false;
training_pixel_size=0.378; //Images were trained in StarDist using images of this pixel size. Change this for adult human. ~0.9?



if(image_already_open==true)
{
	waitForUser("Select Image. and choose output folder in next prompt");
	file_name_full=getTitle(); //get file name without extension (.lif)
	dir=getDirectory("Choose Output Folder");
}
else
{
	if(endsWith(path, ".czi")|| endsWith(path, ".lif")) run("Bio-Formats", "open=["+path+"] color_mode=Composite rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT");
	else if (endsWith(path, ".tif")|| endsWith(path, ".tiff")) open(path);
	else exit("File type not recognised.  Tif, Lif and Czi files supported.");
	dir=File.directory;
	file_name_full=File.nameWithoutExtension; //get file name without extension (.lif)
}

//file_name=File.nameWithoutExtension;
file_name_length=lengthOf(file_name_full);
if(file_name_length>50) file_name=substring(file_name_full, 0, 39); //Restricting file name length as in Windows long path names can cause errors
//print(file_name);

img_name=getTitle();
Stack.getDimensions(width, height, sizeC, sizeZ, frames);


run("Select None");
run("Remove Overlay");

getPixelSize(unit, pixelWidth, pixelHeight);

//Training images were pixelsize of ~0.568, so scaling images based on this
scale_factor=pixelWidth/training_pixel_size;
if(scale_factor<1.001 && scale_factor>1) scale_factor=1;


print("Analysing: "+file_name);
analysis_dir= dir+"Analysis"+fs;
if (!File.exists(analysis_dir)) File.makeDirectory(analysis_dir);
print("Files will be saved at: "+analysis_dir); 
print("Analysing: "+file_name);
//Create results directory with file name in "analysis"
results_dir=analysis_dir+file_name+fs; //directory to save images
if (!File.exists(results_dir)) File.makeDirectory(results_dir); //create directory to save results file

//do not include cells greater than 1500 micron in area
glia_area_limit=700; //microns
pixel_area_limit=glia_area_limit/pixelWidth; //convert micron to pixels

//add minimum area limit too

table_name="Analysis_"+cell_type+"_"+file_name;
Table.create(table_name);//Final Results Table
row=0; //row counter for the table
image_counter=0;


if(sizeC>1)
{
 waitForUser("Note the channels for each marker.");

 if (Cell_counts_per_ganglia==true)
	{
		Dialog.create("Choose channels for "+cell_type+" ganglia and neurons");
		Dialog.addNumber("Enter "+cell_type+" channel", 3);
  		Dialog.addNumber("Enter neuron channel", 3);
  		Dialog.addNumber("Enter channel for segmenting ganglia", 2);
  		Dialog.show(); 
		cell_channel= Dialog.getNumber();
		neuron_channel= Dialog.getNumber();
		ganglia_channel=Dialog.getNumber();
		Stack.setChannel(cell_channel);
		resetMinAndMax();
		Stack.setChannel(ganglia_channel);
		resetMinAndMax();		
	}

	else 
		{
			Dialog.create("Choose  channel for "+cell_type);
	  		Dialog.addNumber("Enter "+cell_type+" channel", 3);
	  	    Dialog.show(); 
			cell_channel= Dialog.getNumber();
			Stack.setChannel(cell_channel);
			resetMinAndMax();
		}
}



//add option for extended depth of field projection for widefield images
if(sizeZ>1)
{
		print(img_name+" is a stack");
		roiManager("reset");
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
		print(img_name+" has only one slice, using as max projection");
		max_projection=getTitle();
}

max_save_name="MAX_"+file_name;


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

n_tiles=2;
new_width=round(width*scale_factor); 
if(new_width>1200) n_tiles=4;
else if(new_width>4000) n_tiles=10;

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
segment_cells(max_projection,seg_image,neuron_model_path,n_tiles,width,height,scale_factor);
close(seg_image);

//manually correct or verify if needed
waitForUser("Correct "+cell_type+" ROIs if needed. You can delete or add ROIs using ROI Manager");
cell_count=roiManager("count");
rename_roi(); //rename ROIs
roiManager("deselect");


print("No of "+cell_type+" in "+max_projection+" : "+cell_count);
roiManager("deselect");
roi_location=results_dir+cell_type+"_ROIs_"+file_name+".zip";
roiManager("save",roi_location );




selectWindow(max_projection);

//uses roi to label macro code; clij is a dependency
runMacro(roi_to_label);
wait(5);
glia_label_image=getTitle();




selectWindow(max_projection);
run("Select None");
run("Remove Overlay");

 if (Cell_counts_per_ganglia==true)
	{
	 if(Use_DeepImageJ==true)
	 {
	 	ganglia_binary=ganglia_deepImageJ(max_projection,neuron_channel,ganglia_channel);
	 	draw_ganglia_outline(ganglia_binary,true);
	 	
	 }
	 else ganglia_binary=draw_ganglia_outline(ganglia_img,false);
	 
	args=glia_label_image+","+ganglia_binary;
	//get cell count per ganglia
	runMacro(ganglia_cell_count,args);
	//output from above is table named neuron_ganglia_count; need to rename it to cell_ganglia_count
	selectWindow("neuron_ganglia_count");
	cell_count_per_ganglia=Table.getColumn("Cell counts");

	roiManager("deselect");
	roi_location=results_dir+"Ganglia_ROIs_"+file_name+".zip";
	roiManager("save",roi_location );
	}

selectWindow(glia_label_image);
saveAs("Tiff", results_dir+"Neuron_label_"+max_save_name);
close();

selectWindow(table_name);
Table.set("File name",row,file_name_full);
Table.set("Total "+cell_type, row, cell_count); //set total count of neurons after nos analysis if nos selected
if (Cell_counts_per_ganglia==true) Table.setColumn("Cell counts per ganglia", cell_count_per_ganglia);
Table.update;
Table.save(results_dir+cell_type+"_"+file_name+".csv");


roiManager("UseNames", "false");
close("*");
exit(cell_type+" analysis complete");



//function to segment cells using max projection, image to segment, model file location
//no of tiles for stardist, width and height of image
//returns the ROI manager with ROIs overlaid on the image.
function segment_cells(max_projection,img_seg,model_file,n_tiles,width,height,scale_factor)
{
	//need to have the file separator as \\\\ in the file path when passing to StarDist Command from Macro. 
	//regex uses \ as an escape character, so \\ gives one backslash \, \\\\ gives \\.
	//Windows file separator \ is actually \\ as one backslash is an escape character
	//StarDist command takes the escape character as well, so pass 16 backlash to get 4xbackslash in the StarDIst macro command (which is then converted into 2)
	model_file=replace(model_file, "\\\\","\\\\\\\\\\\\\\\\");
	choice=0;
	print(img_seg);
	print(max_projection);
	roiManager("reset");
	//model_file="D:\\\\Gut analysis toolbox\\\\models\\\\2d_enteric_neuron\\\\TF_SavedModel.zip";
	selectWindow(img_seg);
	run("Command From Macro", "command=[de.csbdresden.stardist.StarDist2D],args=['input':'"+img_seg+"', 'modelChoice':'Model (.zip) from File', 'normalizeInput':'true', 'percentileBottom':'1.0', 'percentileTop':'99.8', 'probThresh':'0.5', 'nmsThresh':'0.45', 'outputType':'Label Image', 'modelFile':'"+model_file+"', 'nTiles':'"+n_tiles+"', 'excludeBoundary':'2', 'roiPosition':'Automatic', 'verbose':'false', 'showCsbdeepProgress':'false', 'showProbAndDist':'false'], process=[false]");
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
	resetMinAndMax();
		//convert the labels to ROIs
	runMacro(label_to_roi,"label_original");
	wait(10);
	close(label_image);
	selectWindow(max_projection);
	roiManager("show all");
	close("label_original");
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

//use deepimagej to predict ganglia outline and return a binary image
function ganglia_deepImageJ(max_projection,cell_channel,ganglia_channel)
{

	//waitForUser("Select max projection");
	selectWindow(max_projection);
	getPixelSize(unit, pixelWidth, pixelHeight);
	
	//max_projection=getTitle();
	
	
	selectWindow(max_projection);
	run("Duplicate...", "title=ganglia_ch duplicate channels="+ganglia_channel);
	run("Green");
	
	selectWindow(max_projection);
	run("Duplicate...", "title=cells_ch duplicate channels="+cell_channel);
	run("Magenta");
	
	run("Merge Channels...", "c1=ganglia_ch c2=cells_ch create");
	composite_img=getTitle();
	
	run("RGB Color");
	ganglia_rgb=getTitle();
	
	close(composite_img);
	selectWindow(ganglia_rgb);
	
	run("DeepImageJ Run", "model=2D_enteric_ganglia format=Tensorflow preprocessing=[per_sample_scale_range.ijm] postprocessing=[no postprocessing] axes=Y,X,C tile=768,768,3 logging=normal");
	
	wait(10);
	prediction_output=getTitle();
	
	runMacro(deepimagej_post_processing,prediction_output);
	temp_pred=getTitle();
	
	close(ganglia_rgb);
	
	selectWindow(temp_pred);
	run("Options...", "iterations=3 count=2 black do=Open");
	wait(5);
	
	min_area_ganglia_pixels=500;  //500 microns
	min_area_ganglia=500/Math.sqr(pixelWidth);  //area proportional to sqr of radius
	run("Size Opening 2D/3D", "min="+min_area_ganglia);
	ganglia_pred_processed=getTitle();
	
	close(temp_pred);
	
	return ganglia_pred_processed;
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
		waitForUser("Verify if ganglia outline is satisfactory?. Use paintbrush tool to fill or delete areas. Press OK when done.");
		
	}
	
}