/*
 * Segment neurons (Hu) and ganglia (PGP/GFAP/NOS) channel
 * Neurons and glia are segmented using StarDist2D
 * Ganglia are segmented using DeepImageJ
 * Both these models are pretrained and are needed to run this macro
 * Get GFAP percentage and NOS counts
*/

//*******
// Author: Pradeep Rajasekhar
// March 2021
// License: BSD3
// 
// Copyright 2020 Pradeep Rajasekhar, Monash Institute of Pharmaceutical Sciences
// 
// Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
// 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
// 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

//check if plugins are installed
print("******Checking if plugins are installed.*******");
checks=newArray("DeepImageJ Run","Shape Smoothing","Area Opening","Command From Macro");
check_plugin(checks);
//check_plugin("DeepImageJ Run");
//check_plugin("Shape Smoothing");
//check_plugin("Area Opening");
//check_plugin("LabelMap to ROI Manager (2D)");
//check_plugin("Command From Macro");
print("******Check complete.*******");


fs = File.separator; //get the file separator for the computer (depending on operating system)
//print("FILE SEPARATOR for the OS is: "+fs);
//path=File.openDialog("Choose the tif file to analyse");
#@ File (style="open", label="Choose the image to segment") path
#@ boolean Neuron (description="Tick to segment neuron")
#@ File (style="open", label="<html>Choose the StarDist model file if segmenting neurons.<br>Enter NA if empty<html>",value="NA", description="Enter NA if nothing") neuron_model_path 
#@ double Scale_Factor_Neuron (description="Scale factor will be used to rescale the image for segmentation with StarDist")
#@ boolean Glia (description="Tick to segment glia")
#@ File (style="open", label="<html>Choose the StarDist model file if segmenting glia.<br>Enter NA if empty<html>",value="NA", description="Enter NA if nothing") glia_model_path 
//#@ double Scale_Factor_Glia (description="Scale factor will be used to rescale the image for segmentation with StarDist")
#@ boolean Modify_StarDist_Values (description="Tick to modify the values within the StarDist plugin or defaults will be used.")
#@ String(value="<html>Default Probability is 0.5 and nmsThresh is 0.4. Tick above to change these values<html>",visibility="MESSAGE") hint1
#@ String(value="<html>Selecting normalise to ganglia will normalise the cell counts to the area of the ganglia (um2)<br/>You can either draw the ganglia manually or use the Hu channel in combination with<br/> a marker labelling the ganglia (PGP9.5/GFAP/NOS)<html>",visibility="MESSAGE") hint2
#@ boolean Normalise_to_ganglia (description="Use a pretrained deepImageJ model to predict ganglia outline")
#@ String(value="<html>Check the documentation for instructions on determining<br/>scaling factor. As a guide, use a factor of 1 for 40X images and 2 for 20X<html>",visibility="MESSAGE") hint3
#@ boolean Save_Image_Mask (description="Save images and masks")
#@ String(value="<html>Save Images and Masks for Training<html>",visibility="MESSAGE") hint4


save_img_mask=Save_Image_Mask;

//change scale factor based to be estimated based on pixel size
//if scale factor is zero, change it to 1 for no scaling
//if(scale_factor_neuron==0) scale_factor_neuron=1;
//if(scale_factor_glia==0) scale_factor_glia=1;
if(!endsWith(path, ".tif"))	exit("Not recognised. Please select a tif file...");

fiji_dir=getDirectory("imagej");
ganglia_model_folder=fiji_dir+"models"+fs+"2D enteric ganglia_RGB"+fs;
if(!File.exists(ganglia_model_folder)) exit("Cannot find ganglia model. Please copy ganglia model into Fiji.app/model folder");
else{ print("Ganglia model found in "+ganglia_model_folder);}



open(path);
run("Select None");
run("Remove Overlay");

file_name=File.nameWithoutExtension; //get file name without extension (.lif)
dir=File.directory;
analysis_dir= dir+"Analysis"+fs;
print("Analysis Directory: "+analysis_dir); 
//if directory doesnt exist make one
if (!File.exists(analysis_dir)) File.makeDirectory(analysis_dir);
print("Analysing: "+file_name);
//Create results directory with file name in "analysis"
results_dir=analysis_dir+file_name+fs; //directory to save images
if (!File.exists(results_dir)) File.makeDirectory(results_dir); //create directory to save images and results file




table_name="Morphology2_"+file_name;
Table.create(table_name);//Final Results Table
row=0; //row counter for the table
image_counter=0; //counter for batch process


row=0;
s=1;
series_stack=getTitle();
series_name=getTitle();
Stack.getDimensions(width, height, sizeC, sizeZ, frames);
getPixelSize(unit, pixelWidth, pixelHeight);
Stack.setDisplayMode("composite");

selectWindow(table_name);
Table.set("File name",row,file_name);

scale_factor=pixelWidth/0.378;
if(scale_factor<1.001) scale_factor=1;
scale_factor_neuron=scale_factor;
//scale_factor_glia=Scale_Factor_Glia;

waitForUser("Check the channels for the markers.\nPress OK when done");
//get the channels for Hu and NOS; run it only once when loading the lif as all the files will be from same animal/replicate
if(image_counter==0)
	{
		ganglia_choice = newArray("GFAP", "PGP9.5", "NOS", "NA");
		Dialog.create("Choose channels");
		Dialog.addMessage("Enter 0 if you do not have any of these channels");
		Dialog.addNumber("Enter channel for the ganglia (GFAP/PGP/NOS)", 0);
		Dialog.addRadioButtonGroup("Ganglia marker", ganglia_choice, 1, 4, "NA");
		Dialog.addMessage("Enter channels for the neurons and/or glia");
  		Dialog.addNumber("Enter Sox10 channel (glia)", 0);
  		Dialog.addNumber("Enter Hu channel", 0);
  		Dialog.addMessage("If NOS/GFAP are separate from the ganglia channel and you want to measure these parameters\nenter the channel number");
  		Dialog.addNumber("Enter NOS channel", 0);
  		Dialog.addNumber("Enter GFAP channel", 0);
  		Dialog.show(); 
		//dapi_channel=Dialog.getNumber();
		ganglia_channel= Dialog.getNumber();
		ganglia_marker=Dialog.getRadioButton();
		sox10_channel=Dialog.getNumber();
		hu_channel=Dialog.getNumber();
		nos_channel=Dialog.getNumber();
		gfap_channel=Dialog.getNumber();

		if(ganglia_marker=="GFAP") gfap_channel=ganglia_channel;
		if(ganglia_marker=="NOS") nos_channel=ganglia_channel;

		if(sox10_channel==0 && hu_channel==0 && gfap_channel==0) exit("Neuron and Sox10 channel are empty");
		//print("Channels for Sox10: "+sox10_channel+"; Hu: "+hu_channel+"; Ganglia: "+ganglia_channel+"; GFAP: "+gfap_channel+"; NOS: "+nos_channel);
	}
		
		//create maximum projection
		roiManager("reset");
		if(sizeZ>1)
		{
			print(series_name+" is a stack");
			roiManager("reset");
			waitForUser("Note the start and end of the stack.\nPress OK when done");
			Dialog.create("Choose channels");
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
			print(series_name+" has only one slice, assuming its max projection");
			max_projection=getTitle();
		}

max_save_name="MAX_"+file_name;



selectWindow(max_projection);

//IF NORMALISE TO GANGLIA IS TRUE; BRING IT UP HERE
//OR MANUALLY SELECT AREA TO RESTRICT GLIAL ANALYSIS??


if(Normalise_to_ganglia==true && Neuron==true)
{
	print("Processing ganglia region");
	selectWindow(max_projection);
	run("Select None");
	Stack.setChannel(ganglia_channel);
	//need to convert the images to RGB , where hu is magenta (so its blue and red; ganglia is green)
	run("Duplicate...", "title=ganglia");
	selectWindow("ganglia");
	run("Green");
	
	selectWindow(max_projection);
	Stack.setChannel(hu_channel);
	//need to convert the images to RGB , where hu is magenta (so its blue and red; ganglia is green)
	run("Duplicate...", "title=hu");

	selectWindow("hu");
	run("Magenta");
	run("Merge Channels...", "c2=[ganglia] c6=[hu] create keep");
	selectWindow("Composite");
	rename("rgb_seg_ganglia_composite");
	//run("RGB Color");
	ganglia_seg=getTitle();
	run("Duplicate...", "duplicate");
	run("RGB Color");
	rename("ganglia_rgb");
	ganglia_rgb=getTitle();
	selectWindow(ganglia_rgb);
	run("Select None");
	run("Duplicate...", "title=ganglia_rgb_overlay");
	//Segmenting ganglia using a DeepImageJ model
	selectWindow(ganglia_rgb);
	print("Running segmentation on ganglia");
	//waitForUser("Ensure the ganglia model file is in the Fiji.app/models folder");
	run("DeepImageJ Run", "model=[2D enteric ganglia_RGB] preprocessing=preprocessing.ijm postprocessing=[no postprocessing] patch=512 overlap=95 logging=normal");
	wait(10);
	ganglia_classify=getTitle();
	selectWindow(ganglia_classify);
	setOption("BlackBackground", true);
	run("Threshold...");
	setAutoThreshold("Minimum dark no-reset");
	run("Convert to Mask");
	//remove small holes
	//this depends on the size of the image, revisit this to customize the size based on scaling factor
	//run("Invert");
	//run("Analyze Particles...", "size=0-100 add");
	//setForegroundColor(0, 0, 0);
	//roiManager("deselect");
	//roiManager("Fill");
	//roiManager("reset");
	//run("Invert");
	//run("Options...", "iterations=3 count=4 black do=Open");
	setForegroundColor(0, 0, 0);
	setBackgroundColor(255, 255, 255);
	setTool("Paintbrush Tool"); //set tool to paintbrush //toolID
	selectWindow(ganglia_classify);
	run("Select None");
	run("Image to Selection...", "image=[ganglia_rgb_overlay] opacity=60");
	waitForUser("Check if the ganglia overlay is good. If not, use the brush tool to delete or add.");
	run("Select None");
	saveAs("Tiff", results_dir+"ganglia_image_"+series_name);
	ganglia_classify=getTitle(); //use this later to add neuron ROI
	setForegroundColor(255, 255, 255);
	setBackgroundColor(0, 0, 0);
	setOption("BlackBackground", true); 
	roiManager("reset");
	run("Select None");
	run("Create Selection");
	roiManager("Add"); //ganglia ROI
	//close(ganglia_classify);use this image to get neuron ROIs.
	close("ganglia_rgb_overlay");
	run("Set Measurements...", "area redirect=None decimal=3");
	selectWindow(max_projection);
	run("Clear Results");
	roiManager("select", 0);
	roiManager("Measure");
	area_of_ganglia=getResult("Area", 0);
	selectWindow(table_name);
	Table.set("Ganglia Area um^2", row, area_of_ganglia);
	Table.update;
	run("Clear Results");
	roiManager("deselect");
	//save GANGLIA ROI
	roi_ganglia_location=results_dir+series_name+"_ganglia.zip";
	roiManager("save",roi_ganglia_location);
}
else if (Normalise_to_ganglia==true)
{
	print("No Hu channel selected. Manually estimating the ganglia outline");
	setTool("freehand");
	ganglia_index_roi=draw_ganglia_outline(max_projection,table_name,results_dir,series_name);
	newImage("ganglia_classify", "8-bit black", width, height, 1);
	setVoxelSize(pixelWidth, pixelHeight, pixelWidth, unit);
	ganglia_classify=getTitle(); //use this image to get neuron ROIs.
	setForegroundColor(255, 255, 255);
	setBackgroundColor(0, 0, 0);
	roiManager("select", ganglia_index_roi);
	roiManager("Fill");
	setOption("BlackBackground", true);
	setThreshold(1, 255);
	run("Convert to Mask");	
	roiManager("reset");
	run("Select None");
	run("Create Selection");
	roiManager("Add"); //ganglia ROI
	//close(ganglia_classify);use this image to get neuron ROIs.
	run("Set Measurements...", "area redirect=None decimal=3");
	selectWindow(max_projection);
	run("Clear Results");
	roiManager("select", 0);
	roiManager("Measure");
	area_of_ganglia=getResult("Area", 0);
	selectWindow(table_name);
	Table.set("Ganglia Area um^2", row, area_of_ganglia);
	Table.update;
	run("Clear Results");
	roiManager("deselect");
	//save GANGLIA ROI
	roi_ganglia_location=results_dir+series_name+"_ganglia.zip";
	roiManager("save",roi_ganglia_location);
	
}
else if (Normalise_to_ganglia==false)
{
	ganglia_classify=-1;
}


roiManager("reset");
if(Neuron==true)
{
	cell_type="Neurons";
	neuron_count=segment_cells(max_projection, hu_channel, cell_type,results_dir,ganglia_classify,neuron_model_path,Modify_StarDist_Values,scale_factor_neuron);
	roiManager("deselect");
	//roi manager gets used for cleaning up ganglia segmentation
	roi_location_neurons=results_dir+series_name+"_"+cell_type+"_ROIs.zip";
	roiManager("save",roi_location_neurons);
	roiManager("reset");
	selectWindow(table_name);
	Table.set("Total "+cell_type, row, neuron_count);
	Table.update;
}

roiManager("reset");
if(Glia==true)
{
	cell_type="Glia";
	glia_count=segment_cells(max_projection, sox10_channel, cell_type,results_dir,ganglia_classify,glia_model_path,Modify_StarDist_Values,scale_factor_glia);
	roiManager("deselect");
	//roi manager gets used for cleaning up ganglia segmentation
	roi_location_glia=results_dir+series_name+"_"+cell_type+"_ROIs.zip";
	roiManager("save",roi_location_glia);
	roiManager("reset");
	selectWindow(table_name);
	Table.set("Total "+cell_type, row, glia_count);
	Table.update;
}

if(Neuron==true && Glia==true)
{
	neuron_glia_ratio=neuron_count/glia_count;
	selectWindow(table_name);
	Table.set("Neuron to Sox10 ratio", row, neuron_glia_ratio);
	Table.update;	
}


roiManager("reset");

if(Normalise_to_ganglia==true)
{
	roi_ganglia_location=results_dir+series_name+"_ganglia.zip";
	
	if(Neuron==true)
	{
		neuron_density_ganglia=neuron_count/area_of_ganglia;
		Table.set("Neuron density (per um2 of ganglia area)", row, d2s(neuron_density_ganglia,5));
		Table.update;
	}
	
	if(Glia==true)
	{
		glia_density_ganglia=glia_count/area_of_ganglia;
		Table.set("Sox10 density (per um2 of ganglia area)", row, d2s(glia_density_ganglia,5));
		Table.update;
	}	
			
	//GFAP_analysis
	if(gfap_channel>0)
	{
		
		run("Set Measurements...", "area_fraction redirect=None decimal=3");
		print("******Analysing GFAP******");
		run("Clear Results"); 
		selectWindow(max_projection);
		run("Select None");
		run("Duplicate...", "title=gfap duplicate channels="+gfap_channel);
		selectWindow("gfap");
		run("Median...", "radius=1");//run("Gaussian Blur...", "sigma=0.50");
		run("Threshold...");
		waitForUser("Set threshold using the dropdown box. It is preferable select the thresholding algorithms instead of a manual value.\nKeep this consistent with all the images. Do not click Apply, just press Ok after selecting the appropriate threshold");
		//setAutoThreshold("Moments dark");// Thresholding for glia
		setOption("BlackBackground", true);
		run("Convert to Mask");
		roiManager("reset");
		roiManager("open", roi_ganglia_location);
		roiManager("deselect");
		selectWindow("gfap");
		roiManager("select", 0);
		run("Clear Results");
		roiManager("Measure");
		gfap_area=getResult("%Area", 0);
		run("Clear Results"); 
		close("gfap");
		selectWindow(table_name);
	    Table.set("% Area of GFAP", row, gfap_area);
		Table.update;
	}
}

if(nos_channel>0)

{
	roiManager("reset");
	nos=segment_nos(max_projection,nos_channel,roi_location_neurons);
	selectWindow(table_name);
	Table.set("Total NOS Neurons", row, nos);
	nos_ratio=nos/neuron_count;
	Table.set("NOS_to_neuron_ratio", row, nos_ratio);
	Table.update;
}


selectWindow(table_name);
Table.save(results_dir+series_name+".csv");

//save max projection 
selectWindow(max_projection);
saveAs("Tiff", results_dir+max_save_name);

exit("Analysis complete");



//ADD A CLIJ MACRO TO FIGURE OUT NOS THRESHOLD PROCEDURE and MAYBE ADD THIS FOR GFAP
function segment_nos(max_projection,nos_channel,roi_neurons)
{
		threshold_methods=getList("threshold.methods");
		Dialog.create("NOS parameters");
		Dialog.addChoice("Choose Threshold", threshold_methods);
		Dialog.addNumber("Area fraction NOS within Hu -> NOS neuron", 40);
		Dialog.show();
		nos_threshold_method=Dialog.getChoice();
		nos_value=Dialog.getNumber();

			run("Set Measurements...", "area_fraction redirect=None decimal=3");
			//get neuron ROIs again
			roiManager("open", roi_neurons);
			wait(5);
			neuron_count=roiManager("count");
			selectWindow(max_projection);
			run("Select None");
			run("Remove Overlay");
			Stack.setChannel(nos_channel);
			run("Duplicate...", "title=nos");
			selectWindow("nos");
			run("Enhance Local Contrast (CLAHE)", "blocksize=127 histogram=256 maximum=2 mask=*None* fast_(less_accurate)");
			area=100;//*Math.pow(scale_factor, 2);
			run("Gray Scale Attribute Filtering", "operation=Opening attribute=Area minimum=&area connectivity=4");
			selectWindow("nos-attrFilt");
			radius=0.2;//*scale_factor;
			run("Median...", "radius=&radius");
			//setAutoThreshold("Triangle dark");//setAutoThreshold("Intermodes dark");
			setAutoThreshold(nos_threshold_method+" dark no-reset");
			setOption("BlackBackground", true);
			run("Convert to Mask");
			run("Options...", "iterations=3 count=4 black edm=8-bit do=Open");
			run("Area Opening", "pixel=300");
			rename("nos_analysis");
			run("Clear Results"); 
			nos=0;
			setOption("ExpandableArrays", true);
			nos_array=newArray();
	
			//setting ROIs to use names
			roiManager("UseNames", "true");
			//count NOS neurons
			for(i=0;i<neuron_count;i++)
			{
					selectWindow("nos_analysis");
					roiManager("Select",i);
					roiManager("Measure");
					perc_area=getResult("%Area", 0); //Results are cleared every loop, so only read area in first row
					print("Percent Area of "+Roi.getName+" cell is "+perc_area);
					run("Clear Results"); 
					if(perc_area>=nos_value) //Provide separate macros to assess this value; default 40
					{
								nos_name="NOS_"+(nos+1);
								roiManager("Rename",nos_name);
								print("Neuron "+(i+1)+" is NOS");
								nos_array[nos]=i;
								nos+=1;
					}
				}
				//wait(100);
				run("Clear Results"); 
				
				//option to correct if a Neuron has been erroneously identified as NOS
				nos=0; //used as a counter below
				do
				{
					roiManager("deselect");
					selectWindow(max_projection);
					roiManager("Show All with labels");
					Stack.setDisplayMode("composite");
					run("Channels Tool...");
					Stack.setChannel(nos_channel);
					run("Grays");
					selectWindow("nos");
					roiManager("Show All with labels");
					waitForUser("Verify NOS ROIs. If they are not NOS neurons, select the ROI in ROI Manager and it will be corrected.\nHowever, if everything looks good, do not select an ROI, just press OK. Press Deselect if you have selected an ROI");
				  	not_NOS=roiManager("index");
	
				  	if(not_NOS==-1)
					{
						response=0; 
					}
					else {
							nos_name="not_NOS_"+nos;
							roiManager("Rename",nos_name);
							nos_array=Array.deleteValue(nos_array, not_NOS);
							nos-=1;
						response=getBoolean("Do you want to continue?");
						//print(response);
						}
				} while(response==1);
				//Array.print(nos_array);
				//nos array is updated to contain only nos neurons
				nos=nos_array.length;
				return nos;

				close("nos");
				close("nos-attrFilt");
				close("nos_analysis");
}


//takes an array of commands as strings and checks if plugins are installed
function check_plugin(plugin_command)
{
	List.setCommands;
	error=0;
	for (i = 0; i < plugin_command.length; i++) 
	{
		if (List.get(plugin_command[i])!="") 
		{
			if(plugin_command[i]=="Command From Macro") {msg="StarDist";}
			else {msg=plugin_command[i];}
			print(msg+"... OK!");
		}
		else 
		{ 
			if(plugin_command[i]=="Shape Smoothing"){msg="Enable the Biomedgroup update site";}
			else if (plugin_command[i]=="Area Opening") {msg="Activate the IJPB-plugins update site";}
			//else if (plugin_command[i]=="LabelMap to ROI Manager (2D)") {msg="Enable the  update site for SCF";}
			else if (plugin_command[i]=="Command From Macro") {msg="Enable the update site for StarDist";}
			else if (plugin_command[i]=="DeepImageJ Run") {msg="Add the update site for DeepImageJ: https://sites.imagej.net/DeepImageJ/";}
			else {msg=plugin_command[i];}
			print("Error: Install plugin: "+msg);
			error=1;
		}

	}
	if(error==1) exit("Plugins not found. Check Log file for details");
}


//rename ROIs
function rename_roi(start_index,end_index,celltype)
{
	z=1;
	for (i=start_index; i<end_index;i++)
		{ 
		roiManager("Select", i);
		roiManager("Rename", celltype+"_"+z);
		z+=1;
		}
}


//Function to segment neurons or glia
function segment_cells(max_projection, channel, cell_type,results_dir,ganglia_classify, model_file,modify_stardist,scale_factor)
{		
	//this is a funny one.. need to have the file separator as \\\\ in the file path when passing to StarDist Command from Macro. 
	//regex uses \ as an escape character, so \\ gives one backslash \, \\\\ gives \\.
	//Windows file separator \ is actually \\ as one backslash is an escape character
	//StarDist command takes the escape character as well, so pass 16 backlash to get 4xbackslash in the StarDIst macro command (which is then converted into 2)
	print("Segmenting "+cell_type+" with scaling factor "+scale_factor);
	model_file=replace(model_file, "\\\\","\\\\\\\\\\\\\\\\");

	selectWindow(max_projection);
	Stack.getDimensions(width, height, channels, slices, frames);
	if(scale_factor!=1)
	{	
	selectWindow(max_projection);
	new_width=round(width*scale_factor);  
	new_height=round(height*scale_factor);
	print(new_width);
	print(new_height);
	//check if multi-channel
	if(channels>1)
		{
			run("Scale...", "x=- y=- z=1.0 width="+new_width+" height="+new_height+" depth="+channels+" interpolation=None create title=img_resize");
		}
	else{
			run("Scale...", "x=- y=- width="+new_width+" height="+new_height+" interpolation=None create title=img_resize");
		}
		max_project_resize="img_resize";
	}
	else 
	{
		selectWindow(max_projection);
		run("Select None");
		run("Remove Overlay");
		//run("Duplicate...", "title=img_resize");
		run("Duplicate...", "title=img_resize duplicate");
		max_project_resize="img_resize";
	}
	//waitForUser("SDFJDH");
	roiManager("reset");
	
	//Segment Neurons
	selectWindow(max_project_resize);
	Stack.setChannel(channel);
	run("Remove Overlay");
	run("Select None");
	run("Duplicate...", "title=img_seg");
	img_seg=getTitle();
	roiManager("reset");
	choice=0;
	selectWindow(img_seg);
	
	if(modify_stardist==false)
	{
	//model_file="D:\\\\Google Drive\\\\ImageJ+Python scripts\\\\Gut analysis toolbox\\\\models\\\\2d_enteric_neuron_aug (1)\\\\TF_SavedModel.zip";
		selectWindow(img_seg);
		run("Command From Macro", "command=[de.csbdresden.stardist.StarDist2D],args=['input':'"+img_seg+"', 'modelChoice':'Model (.zip) from File', 'normalizeInput':'true', 'percentileBottom':'1.0', 'percentileTop':'99.8', 'probThresh':'0.5', 'nmsThresh':'0.45', 'outputType':'Label Image', 'modelFile':'"+model_file+"', 'nTiles':'2', 'excludeBoundary':'2', 'roiPosition':'Automatic', 'verbose':'false', 'showCsbdeepProgress':'false', 'showProbAndDist':'false'], process=[false]");
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
	}
	else {
		do{		
			waitForUser("Segmenting "+cell_type+" now. Select appropriate model file in next prompt");
			print("Select Label Image option as output type in the StarDist 2D window");
			run("StarDist 2D");
			wait(50);
			temp=getTitle();
			run("Duplicate...", "title=label_image");
			label_image=getTitle();
			run("Remove Overlay");
			close(temp);
			if(isOpen(label_image))
				{
					roiManager("reset"); // In case "Both Option" was selected in StarDist 2D, i.e, get both Label and ROIs
					//remove border labels only works on 8 bit
					selectWindow(label_image);
					wait(20);
					//remove all labels touching the borders
					run("Remove Border Labels", "left right top bottom");
					wait(10);
					rename("Label-killBorders"); //renaming as the remove border labels gives names with numbers in brackets
					
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
					selectWindow("label_original");
					resetMinAndMax();
					//convert the labels to ROIs
					//run("LabelMap to ROI Manager (2D)");
					label_map_to_roi("label_original");
					wait(20);
					close(label_image);
					selectWindow(max_projection);
					roiManager("show all");
					waitForUser("Check segmentation");
					choice=getBoolean("Are you happy with segmentation? If not, adjust probability and/or overlap in StarDist", "Yes", "No, try again");
							if(choice==0) {
								roiManager("reset"); 
								close("label_original");
								
								}
				}
			else 
			 {
				waitForUser("Error! Please select Label Image in the StarDist 2D interface instead of ROI Manager");
				roiManager("reset");
			 }
	  } while(choice==0)
	}
	
	selectWindow("label_original");
	cell_labels=getTitle();
	run("Remove Overlay");
	run("Select None");
	
	if(ganglia_classify!=-1)
	{
		roiManager("reset");
		imageCalculator("Multiply create 32-bit", cell_labels,ganglia_classify);
		label_result=getTitle();
		//close(cell_labels);
		//cell_labels=temp;
		selectWindow(label_result);
		run("Remove Overlay");
		run("Select None");
		run("16-bit");
		resetMinAndMax();
		run("Remap Labels");
		//run("LabelMap to ROI Manager (2D)");
		label_map_to_roi(label_result);
		wait(20);
		close(label_result);
	}
	selectWindow(max_projection);
	roiManager("show all");	
	waitForUser("Correct "+cell_type+" ROIs if needed  using add/delete or redraw the cells");
	cell_count=roiManager("count");
	close("img_seg");
	close(cell_labels);
	close(label_image);
	close(max_project_resize);
	return cell_count;
}

function draw_ganglia_outline(ref_img,table_name, results_dir,series_name)
{
	roiManager("reset");
	setTool("freehand");
	selectWindow(max_projection);
	waitForUser("Ganglia outline", "Draw outline of the ganglia. Press T every time you finish drawing an outline");
	roiManager("Deselect");
	nROIs=roiManager("count");
	if(nROIs>1) //if more than one ROI, combine them
		{
			roi_array=Array.getSequence(nROIs);
			roiManager("Select", roi_array); //add the non ganglionic areas and make one ROI
			roiManager("XOR");
			roiManager("Add");
			roiManager("Select", nROIs);
			roiManager("delete");
		}
		
	roiManager("select", 0);// neuron_count);
	roiManager("rename", "Ganglia_outline");
	ganglia_index_roi=roiManager("index");
	setTool("rectangle");
	selectWindow(max_projection);
	//run("Set Measurements...", "area_fraction redirect=None decimal=3");//can use this for both NOS and gfap
	run("Set Measurements...", "area redirect=None decimal=3");
	roiManager("select", ganglia_index_roi);// neuron_count);
	run("Clear Results");
	roiManager("measure");
	area_of_ganglia=getResult("Area", 0);
	selectWindow(table_name);
	Table.set("Ganglia Area um^2", row, area_of_ganglia);
	Table.update;
	run("Clear Results");
	roiManager("deselect");
	//save GANGLIA ROI
	roi_ganglia_location=results_dir+series_name+"_ganglia.zip";
	roiManager("save",roi_ganglia_location);
	return ganglia_index_roi;
}

//convert label map to ROI
function label_map_to_roi(label_image)
{
	roiManager("reset");
	run("Clear Results");
	
	// Init GPU
	run("CLIJ2 Macro Extensions", "cl_device=");
	Ext.CLIJ2_push(label_image);
	//statistics of labelled pixels
	Ext.CLIJ2_statisticsOfLabelledPixels(label_image, label_image);
	Ext.CLIJ2_release(label_image);
	Ext.CLIJ2_clear();
	
	//get centroid of each label
	selectWindow("Results");
	x=Table.getColumn("CENTROID_X");
	y=Table.getColumn("CENTROID_Y");
	
	//use wand tool to create selection at each label centroid and add the selection to ROI manager
	//will not add it if there is no selection or if the background is somehow selected
	selectWindow(label_image);
	for(i=0;i<x.length;i++)
	{
		//use wand tool; quicker than the threshold and selection method
		doWand(x[i], y[i]);	
		intensity=getValue(x[i], y[i]);
		//if there is a selection and if intensity >0 (not background), add ROI
		if(selectionType()>0 && intensity>0) roiManager("add");
	}
	run("Clear Results");
}