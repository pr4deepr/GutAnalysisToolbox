
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

var fiji_dir=getDirectory("imagej");
var gat_dir=fiji_dir+"scripts"+fs+"GAT"+fs+"Tools"+fs+"commands";



//specify directory where StarDist models are stored
var models_dir=fiji_dir+"scripts"+fs+"GAT"+fs+"Models"+fs;
//Neuron segmentation model
neuron_model_path=models_dir+"2D_enteric_neuron_v2.zip";
//Marker segmentation model
subtype_model_path=models_dir+"Neuron_marker_model_v2.zip";
if(!File.exists(neuron_model_path)||!File.exists(subtype_model_path)) exit("Cannot find models for segmenting neurons at these paths:\n"+neuron_model_path+"\n"+subtype_model_path);

//settings for GAT
gat_settings_path=fiji_dir+"scripts"+fs+"GAT"+fs+"gat_settings.txt";
if(!File.exists(gat_settings_path)) exit("Cannot find settings file. Check: "+gat_settings_path);
run("Results... ", "open="+gat_settings_path);
training_pixel_size=parseFloat(Table.get("Values", 0)); //0.7;
neuron_area_limit=parseFloat(Table.get("Values", 1)); //1500
neuron_seg_lower_limit=parseFloat(Table.get("Values", 2)); //90
neuron_lower_limit=parseFloat(Table.get("Values", 3)); //160
run("Close");



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

//check if ganglia prediction post processing macro present
var segment_ganglia=gat_dir+fs+"Segment_Ganglia.ijm";
if(!File.exists(segment_ganglia)) exit("Cannot find segment ganglia script. Returning: "+segment_ganglia);




fs = File.separator; //get the file separator for the computer (depending on operating system)

#@ File (style="open", label="<html>Choose the image to segment.<br>Enter NA if image is open.<html>") path
#@ boolean image_already_open
#@ String(value="<html>If image is already open, tick above box.<html>", visibility="MESSAGE") hint1
// File (style="open", label="<html>Choose the StarDist model file if segmenting neurons.<br>Enter NA if empty<html>",value="NA", description="Enter NA if nothing") neuron_model_path 
#@ String(label="Enter channel number for Hu if you know. Leave as NA if not using.", value="NA") cell_channel
#@ String(value="<html>-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------<html>",visibility="MESSAGE") hint_star
#@ String(value="<html><center><b>NEURONAL SUBTYPE ANALYSIS</b></center> <html>",visibility="MESSAGE") hint_subtype
#@ boolean Calculate_Neuron_Subtype
#@ String(value="<html>Tick above box if you want to estimate proportion of neuronal subtypes.<html>", visibility="MESSAGE") hint3
// File (style="open", label="<html>Choose the StarDist model for subtype segmentation.<br>Enter NA if empty<html>",value="NA", description="Enter NA if nothing") subtype_model_path 
cell_type="Neuron";

#@ String(value="<html>If you already know the channel names and numbers, check the box below and enter them.<br/> The channel numbers MUST match the channel name order.<br/> You have the option of entering them later in the analysis<html>",visibility="MESSAGE") hint5
#@ boolean Enter_channel_details_now
#@ String(label="Enter channel names followed by a comma (,). Leave as NA if not using.", value="NA") marker_names_manual
#@ String(label="Enter channel numbers with separated by a comma (,). Leave as NA if not using.", value="NA") marker_no_manual
#@ String(value="<html>-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------<html>",visibility="MESSAGE") hint_star
#@ String(value="<html><center><b>DETERMINE GANGLIA OUTLINE</b></center> <html>",visibility="MESSAGE") hint_ganglia
#@ String(value="<html> You will get cell counts for each ganglia<br/>If you have a channel for neuron and another channel that labels the ganglia (PGP9.5/GFAP/NOS/Calbindin...)<br/>it should be sufficient to calculate ganglia outline. You can also manually draw the ganglia<html>",visibility="MESSAGE") hint4
#@ String(label="<html> Enter the channel to use for segmenting ganglia.<br/> Preferably a bright marker that labels most of the ganglia.<br/> Leave as NA if not using.<html> ", value="NA") ganglia_channel
#@ boolean Cell_counts_per_ganglia (description="Use a pretrained deepImageJ model to predict ganglia outline")
#@ String(choices={"DeepImageJ","Manually draw ganglia"}, style="radioButtonHorizontal") Ganglia_detection
#@ String(value="<html>--------------------------------------------------------------Advanced------------------------------------------------------------------------------------<html>",visibility="MESSAGE") hint_adv
#@ boolean Change_pixel_size_segmentation (description="Change the pixel size of the scaled image thats used to detect neurons")
#@ Float(label="Enter pixel size for segmenting neurons. Leave as is if unsure.", value=0.7) training_pixel_size_custom
if(Change_pixel_size_segmentation==true) training_pixel_size=training_pixel_size_custom

print("Using parameters\nSegmentation pixel size:"+training_pixel_size+"\nMax neuron area (microns): "+neuron_area_limit+"\nMin Neuron Area (microns): "+neuron_seg_lower_limit+"\nMin marker area (microns): "+neuron_lower_limit);


//add an option for defining a custom scaling factor

marker_subtype=Calculate_Neuron_Subtype;

//checking if no of markers and no of channels match
if(marker_subtype==1 && Enter_channel_details_now==1)
{
	marker_names_manual=split(marker_names_manual, ",");	
	marker_no_manual=split(marker_no_manual, ",");
	if(marker_names_manual.length!=marker_no_manual.length) exit("Number of marker names and marker channels do not match");
}


//training_pixel_size=0.7; //Images were trained in StarDist using images of this pixel size. Change this for adult human. ~0.9?


if(image_already_open==true)
{
	waitForUser("Select Image. and choose output folder in next prompt");
	file_name=getTitle(); //get file name without extension (.lif)
	dir=getDirectory("Choose Output Folder");
	//file_name=File.nameWithoutExtension;
}
else
{
	if(endsWith(path, ".czi")|| endsWith(path, ".lif")) run("Bio-Formats", "open=["+path+"] color_mode=Composite rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT");
	else if (endsWith(path, ".tif")|| endsWith(path, ".tiff")) open(path);
	else exit("File type not recognised.  Tif, Lif and CZI files supported.");
	dir=File.directory;
	file_name=File.nameWithoutExtension; //get file name without extension (.lif)
}

file_name_length=lengthOf(file_name);
if(file_name_length>50) file_name=substring(file_name, 0, 39); //Restricting file name length as in Windows long path names can cause errors
//print(file_name);

img_name=getTitle();
Stack.getDimensions(width, height, sizeC, sizeZ, frames);


run("Select None");
run("Remove Overlay");

getPixelSize(unit, pixelWidth, pixelHeight);

//Training images were pixelsize of ~0.568, so scaling images based on this; 
//Pixel size of 0.7 works for segmentation. Makes sure small objects aren't detected
scale_factor=pixelWidth/training_pixel_size;
if(scale_factor<1.001 && scale_factor>1) scale_factor=1;


print("Analysing: "+file_name);
analysis_dir= dir+"Analysis"+fs;
if (!File.exists(analysis_dir)) File.makeDirectory(analysis_dir);
print("Files will be saved at: "+analysis_dir); 

//Create results directory with file name in "analysis"
results_dir=analysis_dir+file_name+fs; //directory to save images
if (!File.exists(results_dir)) File.makeDirectory(results_dir); //create directory to save results file

//do not include cells greater than 1000 micron in area
//neuron_area_limit=1500; //microns
neuron_max_pixels=neuron_area_limit/pixelWidth; //convert micron to pixels

//using limit when segmenting neurons
//neuron_seg_lower_limit=90;//microns
neuron_seg_lower_limit=neuron_seg_lower_limit/pixelWidth; 


//using limit for marker multiplication and delineation
//neuron_lower_limit= 160;//microns
neuron_min_pixels=neuron_lower_limit/pixelWidth; //convert micron to pixels

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
if(sizeC>1)
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
segment_cells(max_projection,seg_image,neuron_model_path,n_tiles,width,height,scale_factor,neuron_seg_lower_limit);


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
saveAs("Tiff", results_dir+"Neuron_label_"+max_save_name);
rename("Neuron_label"); //saving the file will change the name, so renaming it and getting image name again
neuron_label_image=getTitle();
selectWindow(neuron_label_image);
run("Select None");

print("No of "+cell_type+" in "+max_projection+" : "+cell_count);
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
		
		//channel_combinations=channel_numbers[0];
	}
	channel_position=newArray();
	marker_label_arr=newArray(); //store names of label images generated from StarDist
	
	//count=0;
	//Array.show(channel_numbers);
	//Array.show(channel_names);
	//Array.show(channel_numbers);

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
			//print(channel_no);
			//print(channel_name);
				
			selectWindow(max_projection);
			Stack.setChannel(channel_no);
			run("Select None");
			
			run("Duplicate...", "title="+channel_name+"_segmentation");
			marker_image=getTitle();
			//waitForUser;
			//scaling marker images so we can segment them using same size as images used for training the model. Also, ensures consistent size exclusion area
			seg_marker_img=scale_image(marker_image,scale_factor,channel_name);
			//print(seg_marker_img);
			//print(max_projection);
			//marker_label_img[i]=scale_image(temp,scale_factor,channel_names[i]+"_label_img"); //returns image with _rescale in name
			roiManager("reset");
			//segment cells and return image with normal scaling
			print("Segmenting marker "+channel_name);
			segment_cells(max_projection,seg_marker_img,subtype_model_path,n_tiles,width,height,scale_factor,neuron_seg_lower_limit);
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
			//decide on how to work with multiplication, neuron multiply labl or other way around?
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
			waitForUser("Verify ROIs for "+channel_name+". Delete or add ROIs as needed. Press OK when done.");
			roiManager("deselect");
			//convert roi manager to label image
			runMacro(roi_to_label);
			selectWindow("label_mapss");
			rename("label_img_"+channel_name);
			label_marker=getTitle();
			
			//store marker name in an array to call when analysing marker combinations
			if(no_markers>1) marker_label_arr[i]=label_marker;

			marker_count=roiManager("count"); // in case any neurons added after manual verification of markers
			selectWindow(table_name);
			//Table.set("Total "+cell_type, row, cell_count);
			Table.set("Marker Combinations", row, channel_name);
			Table.set("Number of cells per marker combination", row, marker_count);
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
						//runMacro(label_to_roi,temp_label);
						//close(temp_label);
						close(img1);
						close(img2);
						wait(5);
						//selectWindow(max_projection);
						//roiManager("deselect");
						//roiManager("show all");
						//selectWindow(max_projection);
						//waitForUser("Verify ROIs for "+channel_name+". Delete or add ROIs as needed. Press OK when done.");
						//roiManager("deselect");
						//convert roi manager to label image
						//runMacro(roi_to_label);
						//selectWindow("label_mapss");
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
					//runMacro(label_to_roi,temp_label);
					//close(temp_label);
					close(img1);
					close(img2);
					wait(5);
					result="img "+d2s(j,0);
					
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
				roiManager("save",roi_location_marker);

				marker_count=roiManager("count"); // in case any neurons added after analysis of markers
				selectWindow(table_name);
				//Table.set("Total "+cell_type, row, cell_count);
				Table.set("Marker Combinations", row, channel_combinations[i]);
				Table.set("Number of cells per marker combination", row, marker_count);
				//Table.set(""+cell_type, row, marker_count/cell_count);
				Table.update;
				row+=1;


				
				roiManager("reset");
				
				if (Cell_counts_per_ganglia==true)
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
	
					selectWindow(table_name);
					Table.setColumn(channel_combinations[i]+" counts per ganglia", cell_count_per_ganglia);
					Table.update;
					
					selectWindow("cells_ganglia_count");
					run("Close");
					roiManager("reset");
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

//remove zeroes in the array
selectWindow(table_name);
marker_combinations=Table.getColumn("Marker Combinations"); 
marker_combinations=Array.deleteValue(marker_combinations, 0);
Table.setColumn("Marker Combinations", marker_combinations);



}
close("label_img_*");

/*
 // Threshold 1 to max for marker channel
Ext.CLIJ2_thresholdHuang(image_4, image_6);
Ext.CLIJ2_release(image_4);

mean intensity of neuron label map and marker binary
// Mean Intensity Map
Ext.CLIJ2_meanIntensityMap(image_6, image_5, image_7);
Ext.CLIJ2_release(image_6);

// Greater Or Equal Constant 0.5, only accept neurons of % area >0.5, 0.6?
constant = 0.5;
Ext.CLIJ2_greaterOrEqualConstant(image_7, image_8, constant);
Ext.CLIJ2_release(image_7);

// Multiply Images neuron label multiply image_8 above
Ext.CLIJ2_multiplyImages(image_5, image_8, image_9);
Ext.CLIJ2_release(image_5);
Ext.CLIJ2_release(image_8);

reindex

output label image for marker
 * 
ADD CODE TO set measurements???

run("Clear Results");

//measure area and display the name of the roi as well
run("Set Measurements...", "area display redirect=None decimal=3");
selectWindow(neuron_label);

roiManager("deselect");
roiManager("Measure");
selectWindow("Results");
neuron_area=newArray();
neuron_names=newArray();
//setOption("ExpandableArrays", true);

neuron_names=Table.getColumn("Label"); //getResult("Label"); 
neuron_area=Table.getColumn("Area");//getResult("Area");

run("Close");

selectWindow(table_name);
Table.setColumn("Neurons", neuron_names);
Table.setColumn("Area of Neurons (um2)", neuron_area);
Table.update;

//generate a matrix for subtype expression
///setOption("ExpandableArrays", true);
if(neuron_subtype_matrix>=2)
{
	if(get_nos==true) 
	{
		channel_names[channel_names.length]="NOS"; //add NOS to the end
		no_markers+=1; //if more than 1 marker, likely that no_markers is >1
		print("NO markers matrix "+no_markers);
	}
	for (i = 0; i < no_markers; i++)
	{
		roi_name_table(channel_names[i],table_name);
		
	}
}


*/

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
function segment_cells(max_projection,img_seg,model_file,n_tiles,width,height,scale_factor,neuron_seg_lower_limit)
{
	//need to have the file separator as \\\\ in the file path when passing to StarDist Command from Macro. 
	//regex uses \ as an escape character, so \\ gives one backslash \, \\\\ gives \\.
	//Windows file separator \ is actually \\ as one backslash is an escape character
	//StarDist command takes the escape character as well, so pass 16 backlash to get 4xbackslash in the StarDIst macro command (which is then converted into 2)
	model_file=replace(model_file, "\\\\","\\\\\\\\\\\\\\\\");
	choice=0;
	//print(img_seg);
	//print(max_projection);
	roiManager("reset");
	//model_file="D:\\\\Gut analysis toolbox\\\\models\\\\2d_enteric_neuron\\\\TF_SavedModel.zip";
	selectWindow(img_seg);
	run("Command From Macro", "command=[de.csbdresden.stardist.StarDist2D],args=['input':'"+img_seg+"', 'modelChoice':'Model (.zip) from File', 'normalizeInput':'true', 'percentileBottom':'1.0', 'percentileTop':'99.8', 'probThresh':'0.4', 'nmsThresh':'0.45', 'outputType':'Label Image', 'modelFile':'"+model_file+"', 'nTiles':'"+n_tiles+"', 'excludeBoundary':'2', 'roiPosition':'Automatic', 'verbose':'false', 'showCsbdeepProgress':'false', 'showProbAndDist':'false'], process=[false]");
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
	Ext.CLIJ2_multiplyImages(marker1, marker2, image_5);
	//Ext.CLIJ2_release(marker1);
	Ext.CLIJ2_release(marker2);
	
	// Exclude Labels Outside Size Range
	//minimum_size = 300.0;
	//maximum_size = 3000.0;
	Ext.CLIJ2_excludeLabelsOutsideSizeRange(image_5, image_6, minimum_size, maximum_size);
	Ext.CLIJ2_release(image_5);

	// Label Overlap Count Map; 
	//marker1 is ref image and counting how many labels in 6 overlap with image_11
	Ext.CLIJ2_labelOverlapCountMap(marker1,image_6, image_7);
	Ext.CLIJ2_release(image_6);
	
	// get cells with overlap of greater than 1 cell
	constant = 1.0;
	Ext.CLIJ2_greaterOrEqualConstant(image_7, image_8, constant);
	Ext.CLIJ2_release(image_7);
	
	// Multiply Images
	Ext.CLIJ2_multiplyImages(marker1, image_8, image_9);
	Ext.CLIJ2_release(image_8);
	Ext.CLIJ2_release(marker1);
	
	Ext.CLIJ2_closeIndexGapsInLabelMap(image_9, image_10);
	Ext.CLIJ2_release(image_9);
	Ext.CLIJ2_pull(image_10);
	
	return image_10;
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
			//print(i);
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



//rename ROIs as consecutive numbers
function rename_roi()
{
	for (i=0; i<roiManager("count");i++)
		{ 
		roiManager("Select", i);
		roiManager("Rename", i+1);
		}
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

	selectWindow(ganglia_rgb);
	run("Duplicate...", "title=ganglia_rgb_2"); //use this for verification
	
	close(composite_img);
	
	selectWindow(ganglia_rgb);
	
	run("DeepImageJ Run", "model=2D_enteric_ganglia format=Tensorflow preprocessing=[per_sample_scale_range.ijm] postprocessing=[no postprocessing] axes=Y,X,C tile=768,768,3 logging=normal");
	
	wait(10);
	prediction_output=getTitle();
	
	runMacro(deepimagej_post_processing,prediction_output);
	temp_pred=getTitle();
	
	selectWindow(temp_pred);
	run("Options...", "iterations=3 count=2 black do=Open");
	wait(5);
	
	min_area_ganglia_pixels=200;  //200 microns
	min_area_ganglia=min_area_ganglia_pixels/Math.sqr(pixelWidth);  //area proportional to sqr of radius
	run("Size Opening 2D/3D", "min="+min_area_ganglia);
	ganglia_pred_processed=getTitle();

	selectWindow(ganglia_pred_processed);
	run("Select None");
	run("Image to Selection...", "image=[ganglia_rgb_2] opacity=60");
	waitForUser("Check if the ganglia overlay is good. If not, use the brush tool to delete or add.");
	run("Select None");
	
	close("ganglia_rgb_2");
	close(temp_pred);
	close(ganglia_rgb);
	
	selectWindow(ganglia_pred_processed);
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