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

/*
 *If neurons are not in ganglia, this macro fixes it by dilating neuron outline, and incorporating it as part of existing ganglia label image
 *returns modified ganglia label image with same title as ganglia image passed to the macro
*/


//Use runMacro("Directory where macro installed//Segment_Ganglia.ijm","multi-channel image,neuron channel, ganglia channel");

var fs=File.separator;
setOption("ExpandableArrays", true);


var fiji_dir=getDirectory("imagej");
var gat_dir=fiji_dir+"scripts"+fs+"GAT"+fs+"Tools"+fs+"commands";


macro "ganglia_fix_missing_neurons"
{
	
	//Checks if CLIJ is installed
	List.setCommands;
	clij_install=List.get("CLIJ2 Macro Extensions");
	if(clij_install=="") 
	{
		print("CLIJ installation link: Please install CLIJ using from: https://clij.github.io/clij2-docs/installationInFiji");
		exit("CLIJ not installed. Check Log for installation details")
	}
	
	morpholibj_install=List.get("Area Opening");
	if(morpholibj_install=="") 
	{
		print("Activate the IJPB-plugins update site");
		exit("MorpholibJ not installed. Check Log for installation details")
	}


	if(getArgument()=="")
	{ 
		//exit("Cannot run independently; need to run with GAT");
		waitForUser("Get neuron label image");
		neuron_label_img = getTitle();
		waitForUser("Get ganglia label image");
		ganglia_label_img = getTitle();
		neuron_seg_lower_limit = getNumber("Enter min area of neuron in micron", 70);
		neuron_dilate_micron = getNumber("Enter dilate radius", 6.5); 
		px_width = getNumber("Enter pixel size", 0.568);
		neuron_seg_lower_limit = neuron_seg_lower_limit/px_width;
		neuron_dilate_px =neuron_dilate_micron/px_width;
	}
	else 
	{
		args=getArgument();
		
		arg_array=split(args, ",");
		neuron_label_img = arg_array[0];
		ganglia_label_img = arg_array[1]; 
		//lower limit of neuron area in pixels
		neuron_seg_lower_limit = parseFloat(arg_array[2]);
		neuron_dilate_px = parseFloat(arg_array[3]);
		
	}
	
	selectWindow(neuron_label_img);
	//dilate neuron image
	run("CLIJ2 Macro Extensions", "cl_device=");
	Ext.CLIJ2_push(neuron_label_img);
	Ext.CLIJ2_dilateLabels(neuron_label_img, neuron_binary, neuron_dilate_px);
	Ext.CLIJ2_pull(neuron_binary);
	Ext.CLIJ2_release(neuron_label_img);
	
	selectWindow(neuron_binary);
	rename("neuron_binary");
	neuron_binary= getTitle();
	
	run("Select None");
	getMinAndMax(min, max);
	//print(min,max);
	setThreshold(1, max);
	run("Convert to Mask");
	resetMinAndMax;
	
	
	selectWindow(ganglia_label_img);
	ganglia_label_img_name = getTitle();
	
	run("Select None");
	run("Duplicate...", "title=ganglia_binary");
	selectWindow("ganglia_binary");
	getMinAndMax(min, max);
	//print(min,max);
	setThreshold(1, max);
	run("Convert to Mask");
	resetMinAndMax;
	
	//add ganglia roi to roi manager
	roiManager("reset");
	selectWindow("ganglia_binary");
	run("Create Selection");
	roiManager("Add");
	
	//user foreground as black
	setForegroundColor(0, 0, 0);
	setBackgroundColor(255, 255, 255);
	selectWindow(neuron_binary);
	roiManager("Show All");
	roiManager("Fill");
	
	setForegroundColor(255, 255, 255);
	setBackgroundColor(0, 0, 0);
	
	//opening radius to be 75% of neuron dilate px so as to not delete cropped neuron area
	//get rid of outlines outside ganglia due to neuron expansion
	open_px = 0.65*neuron_dilate_px;
	run("Morphological Filters", "operation=Opening element=Disk radius="+open_px);
	neuron_opening = getTitle();
	close(neuron_binary);
	
	selectWindow(neuron_opening);
	run("Area Opening", "pixel="+neuron_seg_lower_limit);	
	wait(5);
	rename("neuron_binary1");
	neuron_binary1 = getTitle();
	
	close(neuron_opening);
	
	
	imageCalculator("OR create", "neuron_binary1","ganglia_binary");
	wait(5);
	ganglia_modified_binary = getTitle();
	close(neuron_binary1);
	close("ganglia_binary");
	
	selectWindow(ganglia_modified_binary);
	rename("ganglia_binary");
	run("Options...", "iterations=2 count=3 black pad do=Close");
	close(ganglia_label_img);
	selectWindow("ganglia_binary");
	//run("Connected Components Labeling", "connectivity=8 type=[16 bits]");
	//temp = getTitle();
	//close(ganglia_label_img);
	//close(ganglia_modified_binary);
	
	//selectWindow(temp);
	//run("Label Morphological Filters", "operation=Closing radius=2 from_any_label");
	//wait(5);
	//ganglia_label_new = getTitle();
	
	//selectWindow(ganglia_label_new);
	//rename(ganglia_label_img_name);
	//create composite ROIs so ROIs have holes
	//run("Label image to composite ROIs");
	
	//selectWindow(ganglia_label_new);
	//run("Select None");
	//run("Duplicate...", "title=ganglia_binary");
	//getMinAndMax(min, max);
	//setThreshold(1, max);
	////run("Convert to Mask");
	//resetMinAndMax;
	

}

