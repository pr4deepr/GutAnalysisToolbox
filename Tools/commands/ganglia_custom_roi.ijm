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
 *Import Custom ganglia ROI
*/


//Use runMacro("Directory where macro installed//Segment_Ganglia.ijm","multi-channel image,neuron channel, ganglia channel");

var fs=File.separator;
setOption("ExpandableArrays", true);


var fiji_dir=getDirectory("imagej");
var gat_dir=fiji_dir+"scripts"+fs+"GAT"+fs+"Tools"+fs+"commands";

//check if roi to label macro is present
var roi_to_label=gat_dir+fs+"Convert_ROI_to_Labels.ijm";
if(!File.exists(roi_to_label)) exit("Cannot find roi to label script. Returning: "+roi_to_label);




macro "ganglia_custom_roi"
{
		
	

	if(getArgument()=="")
	{
	 //waitForUser("Select image corresponding to ROIs");
	 //max_projection=getTitle();
	 
	 waitForUser("Select label image or max projection");
	 neuron_label_img=getTitle();
	 selectWindow(neuron_label_img);
	}
	else 
	{
		args=getArgument();
		arg_array=split(args, ",");
		//max_projection=arg_array[0];
		neuron_label_img = arg_array[0];
		selectWindow(neuron_label_img);
	}
	getDimensions(width, height, channels, slices, frames);
	ganglia_roi_path = File.openDialog("Choose ROI Manager for Ganglia");
	roiManager("open", ganglia_roi_path);
	roiManager("show all without labels");
	waitForUser("Verify if ganglia outline is correct. If not, go to More ->Open to choose another ROI file");
	roiManager("deselect");
	runMacro(roi_to_label);
	wait(5);
	
	//multichannel image being passed (neuronal subtype analysis)
	if(channels>1)
	{	
		rename("ganglia_binary");
		ganglia_binary = getTitle();
		run("8-bit");
		selectWindow(ganglia_binary);
		setForegroundColor(255, 255, 255);
		roiManager("deselect");
		roiManager("Fill");
		roiManager("reset");
		
		//8-bit conversion above doesn't always work, so ensuring its 8 bit binary
		selectWindow(ganglia_binary);
		setThreshold(1, 255);
		setOption("BlackBackground", true);
		run("Convert to Mask");
		selectWindow(ganglia_binary);
		
		selectWindow(neuron_label_img);
		run("Remove Overlay");
		setOption("BlackBackground", true);
	}
	else if (channels==1)
	{
		rename("ganglia_temp");
		ganglia_temp = getTitle();
		// if its one large roi for ganglia, label image will only have value of 1
		selectWindow(ganglia_temp);
		run("Connected Components Labeling", "connectivity=8 type=[16 bits]");
		//custom ROI may have ganglia with no cells; so filter those ganglia out
	 	ganglia_label_img =getTitle();
	 	close(ganglia_temp);
	 	
	 	
	 	ganglia_binary = "ganglia_binary";
	 	run("CLIJ2 Macro Extensions", "cl_device=");
	 	Ext.CLIJ2_push(neuron_label_img);
	 	Ext.CLIJ2_push(ganglia_label_img);
	 	
	 	Ext.CLIJ2_labelOverlapCountMap(ganglia_label_img, neuron_label_img, label_overlap);
	 	Ext.CLIJ2_release(neuron_label_img);
	 	Ext.CLIJ2_release(ganglia_label_img);
	 	
	 	Ext.CLIJ2_greaterOrEqualConstant(label_overlap, ganglia_temp, 1.0);
	 	Ext.CLIJ2_release(label_overlap);
	 	
	 	Ext.CLIJ2_convertUInt8(ganglia_temp, ganglia_binary);
	 	Ext.CLIJ2_release(ganglia_temp);
	 	//Ext.CLIJ2_multiplyImages(ganglia_label_img, label_binary, ganglia_binary);
	 	
	 	Ext.CLIJ2_pull(ganglia_binary);
	 	
		wait(5);
		close(ganglia_label_img);
		//convert to imagej binar
		selectWindow(ganglia_binary);
		setThreshold(1, 255);
		setOption("BlackBackground", true);
		run("Convert to Mask");
		
		selectWindow(neuron_label_img);
		run("Remove Overlay");
		setOption("BlackBackground", true);
		
		//active window is ganglia binary image
		selectWindow(ganglia_binary);
	}

	 	

		
	
}