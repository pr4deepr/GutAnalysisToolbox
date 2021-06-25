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

var fs=File.separator;


//consider moving this into plugins folder so can use getDirectory("plugins")
fiji_dir=getDirectory("imagej");
gat_dir=fiji_dir+"scripts"+fs+"GAT"+fs+"Other"+fs+"commands";

//nos_processing_macro
nos_processing_dir=gat_dir+fs+"NOS_processing.ijm";
if(!File.exists(nos_processing_dir)) exit("Cannot find NOS processing macro. Returning: "+nos_processing_dir);

//check_plugin_installation
check_plugin=gat_dir+fs+"check_plugin.ijm";
if(!File.exists(check_plugin)) exit("Cannot find check plugin macro. Returning: "+check_plugin);
runMacro(check_plugin);


//check if label to roi is installed
label_roi_dir=gat_dir+fs+"Convert_Label_to_ROIs.ijm";
if(!File.exists(label_roi_dir)) exit("Cannot find label to roi script. Returning: "+label_roi_dir);

//check if label to roi is installed
roi_to_label=gat_dir+fs+"Convert_ROI_to_Labels.ijm";
if(!File.exists(roi_to_label)) exit("Cannot find roi to label script. Returning: "+roi_to_label);

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

training_pixel_size=0.378;

get_nos=Calculate_nNOS_neurons;

if(image_already_open==true)
{
	waitForUser("Select Image. and choose output folder in next prompt");
	file_name=getTitle(); //get file name without extension (.lif)
	dir=getDirectory("Choose Output Folder");
}
else
{
	if(endsWith(path, ".czi")|| endsWith(path, ".lif")) run("Bio-Formats", "open=["+path+"] color_mode=Composite rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT");
	else if (endsWith(path, ".tif")|| endsWith(path, ".tiff")) open(path);
	else exit("Not recognised.  Tif, Lif and CZI files supported.");
	
	file_name=File.nameWithoutExtension; //get file name without extension (.lif)
}



run("Select None");
run("Remove Overlay");

getPixelSize(unit, pixelWidth, pixelHeight);

//Training images were pixelsize of ~0.378, so scaling images based on this
scale_factor=pixelWidth/training_pixel_size;
if(scale_factor<1.001 && scale_factor>1) scale_factor=1;

dir=File.directory;
print("Analysing: "+file_name);
analysis_dir= dir+"Analysis"+fs;
if (!File.exists(analysis_dir)) File.makeDirectory(analysis_dir);
print("Files will be saved at: "+analysis_dir); 
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

waitForUser("Note the channels for Hu, and NOS if needed");

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
	
	else if(get_nos==true)
	{

		Dialog.create("Choose channels for "+cell_type);
  		Dialog.addNumber("Enter "+cell_type+" channel", 3);
  		Dialog.addNumber("Enter NOS channel", 2);
  		Dialog.show(); 
		cell_channel= Dialog.getNumber();
		nos_channel=Dialog.getNumber();
		Stack.setChannel(cell_channel);
		resetMinAndMax();
		Stack.setChannel(nos_channel);
		resetMinAndMax();		
		
	}
	else 
	{
		Dialog.create("Choose channel for "+cell_type);
  		Dialog.addNumber("Enter "+cell_type+" channel", 3);
  	    Dialog.show(); 
		cell_channel= Dialog.getNumber();
		Stack.setChannel(cell_channel);
		resetMinAndMax();
	}
}



if(sizeZ>1)
{
		print(series_name+" is a stack");
		roiManager("reset");
		waitForUser("Note the start and end of the stack.\nPress OK when done");
		Dialog.create("Choose slices");
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
n_tiles=2;

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
	if(new_width>1200) n_tiles=4;
	else if(new_width>4000) n_tiles=8;
}

roiManager("UseNames", "false");

selectWindow("Log");
print("*********Segmenting Neurons using StarDist********");
//segment neurons
segment_cells(max_projection, seg_image,neuron_model_path,Modify_StarDist_Values,n_tiles,width,height);

close(seg_image);
//manually correct or verify if needed
waitForUser("Correct "+cell_type+" ROIs if needed");
cell_count=roiManager("count");
roiManager("deselect");

selectWindow(max_projection);
runMacro(roi_to_label);
wait(5);
neuron_label_image=getTitle();
selectWindow(neuron_label_image);
saveAs("Tiff", results_dir+"Neuron_label_"+max_save_name);

//using this image window to get ROI names for results table
rename("Neuron");
neuron_label=getTitle();

//run("Close");

print("No of "+cell_type+" in "+max_projection+" : "+cell_count);
roiManager("deselect");
roi_location=results_dir+cell_type+"_ROIs_"+file_name+".zip";
roiManager("save",roi_location );

selectWindow(table_name);
Table.set("File name",row,file_name);
if(get_nos==false) Table.set("Total "+cell_type, row, cell_count); //set total count of neurons after nos analysis if nos selected
Table.update;


if(get_nos==true)
{
	selectWindow(max_projection);
	Stack.setChannel(nos_channel);
	
	run("Duplicate...", "title=NOS_segmentation");
	nos_image=getTitle();

	selectWindow(max_projection);
	run("Select None");
	run("Remove Overlay");
	Stack.setChannel(cell_channel);
	run("Duplicate...", "title="+cell_type+"_segmentation");
	hu_image=getTitle();
	roiManager("reset");
	nos=segment_nos(hu_image,nos_image,training_pixel_size,roi_location,nos_processing_dir);
	cell_count=roiManager("count"); // in case any neurons added after nos analysis
	Table.set("Total "+cell_type, row, cell_count);
	Table.set("NOS "+cell_type, row, nos);
	Table.set("NOS/Hu "+cell_type, row, nos/cell_count);
}


run("Clear Results");

//measure area and display the name of the roi as well
//run("Set Measurements...", "area display redirect=None decimal=3");
//selectWindow(neuron_label);

//roiManager("deselect");
//roiManager("Measure");
//selectWindow("Results");
//neuron_area=newArray();
//neuron_names=newArray();
//setOption("ExpandableArrays", true);

//neuron_names=Table.getColumn("Label"); //getResult("Label"); 
//neuron_area=Table.getColumn("Area");//getResult("Area");

//run("Close");

selectWindow(table_name);
//Table.setColumn("Neurons", neuron_names);
//Table.setColumn("Area of Neurons (um2)", neuron_area);
Table.update;
selectWindow(table_name);
Table.save(results_dir+cell_type+"_"+file_name+".csv");

//save max projection if its scaled image, can use this for further processing later
selectWindow(max_projection);
saveAs("Tiff", results_dir+max_save_name);
//run("Close");


roiManager("UseNames", "false");
close("*");
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
		runMacro(label_roi_dir,"label_original");
		wait(10);
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
				else 
				 {
					waitForUser("Error! Please select Label Image in the StarDist 2D interface instead of ROI Manager");
					roiManager("reset");
				 }
	
			}

	  } while(choice==0)

	close("label_original");
}

//segment_nos(nos_image,training_pixel_size,roi_location,nos_processing_dir)
function segment_nos(hu_image,nos_image,training_pixel_size,roi_neurons,nos_processing_dir)
{
		//threshold_methods=getList("threshold.methods");
		//Dialog.create("NOS parameters");
		//Dialog.addChoice("Choose Threshold determined from before", threshold_methods);
		////Dialog.addNumber("Area fraction NOS within Hu -> NOS neuron", 40);
		//Dialog.show();
		//nos_threshold_method=Dialog.getChoice();
		//nos_value=Dialog.getNumber();
		nos_value=getNumber("Enter a value for NOS correlation coefficient. Default is 80.", 80);
		//arg_string=nos_image+","+d2s(training_pixel_size,3); //pass image name and decimal size
		arg_string=nos_image+","+hu_image+","+d2s(training_pixel_size,3);
		//selectWindow(nos_image);
		//use NOS_processing macro
		runMacro(nos_processing_dir,arg_string);
		wait(10);
		correlation_map=getTitle();//output from macro above
		
		run("Enhance Contrast", "saturated=0.35");
		waitForUser;
		//get neuron ROIs again
		roiManager("open", roi_neurons);
		roiManager("show none");
		wait(5);
		neuron_count=roiManager("count");
		//selectWindow(nos_diff_gauss);
		nos=0;
		setOption("ExpandableArrays", true);
		nos_array=newArray();
		//setting ROIs to use names
		roiManager("UseNames", "true");
		//roiManager("UseNames", "false");
		selectWindow("Log");
		print("*********Detecting NOS neurons********");
		run("Set Measurements...", "mean redirect=None decimal=2");
		//count NOS neurons
		for(i=0;i<neuron_count;i++)
		{
			selectWindow(correlation_map);		
			roiManager("Select",i);
			roiManager("Measure");
			corr_coeff=getResult("Mean", 0); //Results are cleared every loop, so only read area in first row
			print(corr_coeff);
			run("Clear Results"); 
			if(corr_coeff>=nos_value) //Provide separate macros to assess this value; default 80
			{
				print("Renaming");
				nos_name="NOS_"+(nos+1);
				roiManager("Rename",nos_name);
				//print("Neuron "+(i+1)+" is NOS");
				nos_array[nos]=i;
				nos+=1;
			}
		}
		//wait(100);
		run("Clear Results"); 
		//option to correct if a Neuron or add a NOS neuron has been erroneously identified as NOS
		//nos=0; //used as a counter below
		selectWindow(max_projection);
		response=1;
		do
		{
			roiManager("deselect");
			selectWindow(max_projection);
			roiManager("Show All with labels");
			Stack.setDisplayMode("composite");
			run("Channels Tool...");
			Stack.setChannel(nos_channel);
			run("Grays");
			//bring ROI Manager to the front
			selectWindow("ROI Manager");
			//need to make adding neurons more intuitive or efficient
			waitForUser("Verify NOS ROIs and changes can be made in next prompt");
			items = newArray("Correct to NOS positive", "Convert to NOS negative", "Exit/Done");
			Dialog.create("Correct NOS classification");
			Dialog.addRadioButtonGroup("Choose to", items, 3, 1, "Exit/Done");
			Dialog.show();
			choice=Dialog.getRadioButton();
			if(choice=="Convert to NOS negative")
			{
				waitForUser("You have chosen to correct erroneously labelled NOS neurons. Select the ROI in ROI Manager or multiple ROIs by pressing Ctrl and selecting ROIs. Press OK when done.\nHowever, if everything looks good, do not select an ROI, just press OK. Press Deselect if you have selected an ROI by mistake");
				not_NOS=split(call("ij.plugin.frame.RoiManager.getIndexesAsString")); //get multiple rois if selected
			  	if(not_NOS.length==0)
				{
					print("Nothing selected");
				}
				else 
				{
					
					for(r=0;r<not_NOS.length;r++)
					{
						roiManager("select", not_NOS[r]);
						not_name="normal_"+random;
						roiManager("Rename",not_name);
						nos-=1;
					}
				roiManager("deselect");
				}
			}
			
		else if (choice=="Correct to NOS positive")
		{
	
			roiManager("deselect");
			//bring ROI Manager to the front
			selectWindow("ROI Manager");
			waitForUser("You have chosen to reclassify neurons as  NOS neurons. Select the corresponding ROI/s and press OK\n.You can also add a NOS neuron by drawing around the cell, adding to ROI Manager and clicking OK.\nHowever, if everything looks good, press Deselect and just press OK.");
			cell_count_updated=roiManager("count");
			// if nos neuron added, select that cell
			if(cell_count_updated>neuron_count) roiManager("select", (cell_count_updated-1));
			nos_select=split(call("ij.plugin.frame.RoiManager.getIndexesAsString")); //get multiple rois if selected
			if(nos_select.length==0)
				{
					print("Nothing selected");
				}
				else 
				{ 
					
					for(r=0;r<nos_select.length;r++)
					{
						nos+=1;
						roiManager("select", nos_select[r]);
						nos_name="NOS_"+nos;
						roiManager("Rename",nos_name);
						//nos_array=Array.deleteValue(nos_array, not_NOS); 
				//print(response);
					}
				}
			roiManager("deselect");

		//find ROIs that have name NOS
		
		}
		else if (choice=="Exit/Done")
		{
			response=0;
		}
		//response=getBoolean("Do you want to continue editing neurons?");
		} while(response==1)
		nos=find_ROI_name("NOS");
		return nos;
}

//Based on macro by Olivier Burri https://forum.image.sc/t/selecting-roi-based-on-name/3809
//finds rois that contain a string; converts it to lowercase, so its case-insensitive
function find_ROI_name(roiName)
{

	roiName=toLowerCase(roiName);
	nR = roiManager("Count"); 
	roiIdx = newArray(nR); 
	k=0; 
	clippedIdx = newArray(0); 
	
	regex=".*"+roiName+".*";
	
	for (i=0; i<nR; i++) 
	{ 
		roiManager("Select", i); 
		rName = toLowerCase(Roi.getName()); 
		if (matches(rName, regex)) 
		{ 
			roiIdx[k] = i; 
			k++; 
			print(i);
		} 
	} 
	if (k>0) 
	{ 
		clippedIdx = Array.trim(roiIdx,k); 
		//roiManager("select", clippedIdx);
	} 
	//else roiManager("deselect");
	return k;
}
