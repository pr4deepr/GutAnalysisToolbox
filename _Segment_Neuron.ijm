//Macro for segmenting neurons 
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

//run("Close All");
//check if plugins are installed
//for running other macros
//consider moving this into plugins folder so can use getDirectory("plugins")
fiji_dir=getDirectory("imagej");
gat_dir=fiji_dir+"scripts\\GAT\\Other";

//nos_processing_macro
nos_processing_dir=gat_dir+"\\NOS_processing";
if(!File.exists(nos_processing_dir)) exit("Cannot find NOS processing macro. Returning: "+nos_processing_dir);

//check_plugin_installation
check_plugin=gat_dir+"\\check_plugin";
if(!File.exists(check_plugin)) exit("Cannot find check plugin macro. Returning: "+check_plugin);
runMacro(check_plugin);


//check if label to roi is installed
label_roi_dir=gat_dir+"\\_Convert_Label_to_ROIs.ijm";
if(!File.exists(label_roi_dir)) exit("Cannot find label to roi script. Returning: "+label_roi_dir);



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

#@ File (style="open", label="<html>Choose the image to segment.<br>Enter NA if image is open.<html>") path
#@ boolean image_already_open
#@ String(value="<html>If image is already open, tick above box.<html>", visibility="MESSAGE") hint1
#@ File (style="open", label="<html>Choose the StarDist model file if segmenting neurons.<br>Enter NA if empty<html>",value="NA", description="Enter NA if nothing") neuron_model_path 
#@ boolean Calculate_nNOS_neurons
#@ String(value="<html>Tick above box if you want to calculate % of nNOS neurons.<html>", visibility="MESSAGE") hint2
cell_type="Neuron";
#@ boolean Normalise_to_ganglia (description="Use a pretrained deepImageJ model to predict ganglia outline")
#@ String(value="<html>Selecting normalise to ganglia will normalise the cell counts to the area of the ganglia (um2)<br/>You can either draw the ganglia manually or use the Hu channel in combination with<br/> a marker labelling the ganglia (PGP9.5/GFAP/NOS)<html>",visibility="MESSAGE") hint4
#@ boolean Modify_StarDist_Values (description="Tick to modify the values within the StarDist plugin if the default segmentation does not work well.")
#@ String(value="<html>Default Probability is 0.5 and nmsThresh is 0.4. Tick above to change these values if<br/>the default segmentation does not work well.<html>",visibility="MESSAGE") hint3


get_nos=Calculate_nNOS_neurons;

if(image_already_open==true)
{
	waitForUser("Select Image. and choose output folder in next prompt");
	file_name=getTitle(); //get file name without extension (.lif)
	dir=getDirectory("Choose Output Folder");
}
else
{
	if(!endsWith(path, ".tif"))	exit("Not recognised. Please select a tif file...");
	open(path);
	file_name=File.nameWithoutExtension; //get file name without extension (.lif)
}

run("Select None");
run("Remove Overlay");
getPixelSize(unit, pixelWidth, pixelHeight);

//Training images were pixelsize of ~0.378, so scaling images based on this
scale_factor=pixelWidth/0.378;
if(scale_factor<1.001) scale_factor=1;

dir=File.directory;
print("Analysing: "+file_name);
analysis_dir= dir+"Analysis"+fs;
if (!File.exists(analysis_dir)) File.makeDirectory(analysis_dir);
print("Files willl be saved at: "+analysis_dir); 
print("Analysing: "+file_name);
//Create results directory with file name in "analysis"
results_dir=analysis_dir+file_name+fs; //directory to save images
if (!File.exists(results_dir)) File.makeDirectory(results_dir); //create directory to save results file

//do not include cells greater than 1500 micron in area
neuron_area_limit=1500; //microns
pixel_area_limit=neuron_area_limit/pixelWidth; //convert micron to pixels



series_name=getTitle();
Stack.getDimensions(width, height, sizeC, sizeZ, frames);
table_name="Morphology_"+cell_type+"_"+file_name;
Table.create(table_name);//Final Results Table
row=0; //row counter for the table
image_counter=0;

if(sizeC>1)
{
	if(Normalise_to_ganglia==true && get_nos==true)
	{
		Dialog.create("Choose channels for "+cell_type);
  		Dialog.addNumber("Enter "+cell_type+" channel", 4);
  		Dialog.addNumber("Enter NOS channel", 4);
  		Dialog.addNumber("Enter channel for segmenting ganglia", 4);
  		Dialog.show(); 
		cell_channel= Dialog.getNumber();
		nos_channel=Dialog.getNumber();	
		ganglia_channel=Dialog.getNumber();	
	}
	else if (Normalise_to_ganglia==true)
	{
		Dialog.create("Choose channels for "+cell_type);
  		Dialog.addNumber("Enter "+cell_type+" channel", 4);
  		Dialog.addNumber("Enter channel for segmenting ganglia", 4);
  		Dialog.show(); 
		cell_channel= Dialog.getNumber();
		ganglia_channel=Dialog.getNumber();		
	}
	
	else if(get_nos==true)
	{
		Dialog.create("Choose channels for "+cell_type);
  		Dialog.addNumber("Enter "+cell_type+" channel", 4);
  		Dialog.addNumber("Enter NOS channel", 4);
  		Dialog.show(); 
		cell_channel= Dialog.getNumber();
		nos_channel=Dialog.getNumber();
	}
	else 
	{
		Dialog.create("Choose channel for "+cell_type);
  		Dialog.addNumber("Enter "+cell_type+" channel", 4);
  	    Dialog.show(); 
		cell_channel= Dialog.getNumber();
	}
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
		print(series_name+" has only one slice, using as max projection");
		max_projection=getTitle();
}

max_save_name="MAX_"+file_name;


//Segment Neurons
selectWindow(max_projection);
run("Select None");
run("Remove Overlay");

//if more than one channel, set on cell_channel
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



// run StarDist segmentation till user is happy with the segmentation results
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
				
				//runs macro which convert the labels to ROIs (In folder Other->_Convert_Label_to_ROIs.ijm
				runMacro(label_roi_dir,"label_original");
				//label_map_to_roi("label_original");
				
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

//manually correct or verify if needed
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

//save max projection if its scaled image, can use this for further processing later
selectWindow(max_projection);
saveAs("Tiff", results_dir+max_save_name);

run("Close");
exit("Neuron analysis complete");



function segment_cells(max_projection, img_seg,model_file,modify_stardist,n_tiles,width,height)
{
	//need to have the file separator as \\\\ in the file path when passing to StarDist Command from Macro. 
	//regex uses \ as an escape character, so \\ gives one backslash \, \\\\ gives \\.
	//Windows file separator \ is actually \\ as one backslash is an escape character
	//StarDist command takes the escape character as well, so pass 16 backlash to get 4xbackslash in the StarDIst macro command (which is then converted into 2)
	model_file=replace(model_file, "\\\\","\\\\\\\\\\\\\\\\");
	choice=0;
	roiManager("reset");
	do{
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
		//convert the labels to ROIs
		runMacro(label_roi_dir,"label_original");
		wait(20);
		close(label_image);
		selectWindow(max_projection);
		roiManager("show all");
		waitForUser("Check segmentation");
		choice=getBoolean("Are you happy with segmentation? If not, adjust probability and/or overlap in StarDist", "Yes", "No, try again");
		if(choice==0) 
		{
			roiManager("reset"); 
			close("label_original");
		}

	}
	else {
		
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
					
				}
			else 
			 {
				waitForUser("Error! Please select Label Image in the StarDist 2D interface instead of ROI Manager");
				roiManager("reset");
			 }
					//convert the labels to ROIs
				runMacro(label_roi_dir,"label_original");
				wait(20);
				close(label_image);
				selectWindow(max_projection);
				roiManager("show all");
				waitForUser("Check segmentation");
				choice=getBoolean("Are you happy with segmentation? If not, adjust probability and/or overlap in StarDist", "Yes", "No, try again");
				if(choice==0) 
				{
					roiManager("reset"); 
					close("label_original");
				}
	
			}

	  } while(choice==0)
	}	
		
}
