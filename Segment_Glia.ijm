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

run("Close All");

//check if plugins are installed
print("******Checking if plugins are installed.*******");
checks=newArray("DeepImageJ Run","Shape Smoothing","Area Opening","Command From Macro");
check_plugin(checks);
print("******Check complete.*******");

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

 
fs = File.separator; //get the file separator for the computer (depending on operating system)
print("FILE SEPARATOR for the OS is: "+fs);

#@ File (style="open", label="Choose the image to segment") path
//#@ String(choices={"Neuron","Glia"}, style="radioButtonHorizontal",label="Choose celltype for analysis") 
cell_type="Glia";
#@ double(value=1, min=0, max=20, style="spinner") Scale_Factor
scale_factor=Scale_Factor;
#@ String(value="<html>If unsure of scale factor, use 1 for 40X images and 2 for 20X images.<br>Refer to documentation for measuring scaling factor<html>", visibility="MESSAGE") hint
if(scale_factor==0) scale_factor=1;

if(!endsWith(path, ".tif"))	exit("Not recognised. Please select a tif file...");



open(path);
run("Select None");
run("Remove Overlay");
file_name=File.nameWithoutExtension; //get file name without extension (.lif)
dir=File.directory;
print("Analysing: "+file_name);
analysis_dir= dir+"Analysis"+fs;
print("Analysis Directory: "+analysis_dir); 
if (!File.exists(analysis_dir)) File.makeDirectory(analysis_dir);
print("Analysing: "+file_name);
//Create results directory with file name in "analysis"
results_dir=analysis_dir+file_name+fs; //directory to save images
if (!File.exists(results_dir)) File.makeDirectory(results_dir); //create directory to save results file

series_stack=getTitle();
series_name=getTitle();
Stack.getDimensions(width, height, sizeC, sizeZ, frames);


table_name="Morphology_"+cell_type+"_"+file_name;
Table.create(table_name);//Final Results Table
row=0; //row counter for the table
image_counter=0;

if(sizeC>1)
{
			Dialog.create("Choose channel for "+cell_type);
  			Dialog.addNumber("Enter "+cell_type+" channel", 4);
  		    Dialog.show(); 
			//dapi_channel=Dialog.getNumber();
			cell_channel= Dialog.getNumber();
}

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

//Segment Glia
selectWindow(max_projection);
run("Select None");
run("Remove Overlay");
if(sizeC>1)
{
	Stack.setChannel(cell_channel);
}
run("Duplicate...", "title="+cell_type+"_segmentation");
seg_image=getTitle();
roiManager("reset");

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


// run StarDIst segmentation till user is happy with the segmentation results
choice=0;
do{
		selectWindow(seg_image);
		
		print("Select Label Image in the StarDist 2D window");
		run("StarDist 2D");
		wait(50);
		temp=getTitle();
		run("Duplicate...", "title=label_image");
		label_image=getTitle();
		close(temp);
		if(isOpen(label_image))
		{
			roiManager("reset"); // In case "Both Option" was selected in StarDist 2D, i.e, get both Label and ROIs
			 //remove border labels only works on 8 bit
				selectWindow(label_image);
				wait(20);
				//run("3D Exclude Borders", " ");
				//remove all labels touching the borders
				run("Remove Border Labels", "left right top bottom");
				wait(20);
				rename("Label-killBorders");
				resetMinAndMax();
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
  
waitForUser("Correct "+cell_type+" ROIs if needed");
cell_count=roiManager("count");
roiManager("deselect");
print("No of "+cell_type+" in "+max_projection+" : "+cell_count);
roiManager("deselect");
roi_location=results_dir+cell_type+"_ROIs_"+file_name+".zip";
roiManager("save",roi_location );

selectWindow(table_name);
Table.set("File name",row,file_name);
Table.set("Total "+cell_type, row, cell_count);
Table.update;
Table.save(results_dir+cell_type+"_"+file_name+".csv");

//save max projection if its scaled image, can use this for furthe processing later
selectWindow(max_projection);
saveAs("Tiff", results_dir+max_save_name);

run("Close");
exit("Glia analysis complete");


//convert label map to ROI
//works for circular objects
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
