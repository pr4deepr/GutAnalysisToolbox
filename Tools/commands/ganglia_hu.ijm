//*******
// Author: Pradeep Rajasekhar
// March 2022
// License: BSD3
// 
// Copyright 2021 Pradeep Rajasekhar, Walter and Eliza Hall Institute of Medical Research, Melbourne, Australia
// 
// Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
// 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
// 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

/*
 *Segment ganglia macro using deepImageJ 
*/


//Use runMacro("Directory where macro installed//Segment_Ganglia.ijm","multi-channel image,neuron channel, ganglia channel");

var fs=File.separator;
setOption("ExpandableArrays", true);


var fiji_dir=getDirectory("imagej");
var gat_dir=fiji_dir+"scripts"+fs+"GAT"+fs+"Tools"+fs+"commands";



macro "ganglia_hu"
{
		
	run("Clear Results");

	//Checks if CLIJ is installed
	List.setCommands;
	clij_install=List.get("CLIJ2 Macro Extensions");
	if(clij_install=="") 
	{
		print("CLIJ installation link: Please install CLIJ using from: https://clij.github.io/clij2-docs/installationInFiji");
		exit("CLIJ not installed. Check Log for installation details")
		
	}


	if(getArgument()=="")
	{
	 exit("Doesn't run independently");
	}
	else 
	{
		args=getArgument();
		arg_array=split(args, ",");
		max_projection=arg_array[0];
		cell_channel=parseInt(arg_array[1]);
		neuron_label_image=arg_array[2];
		pixelWidth=arg_array[3];
		selectWindow(max_projection);
		getDimensions(width, height, channels, slices, frames);
		run("Select None");
		if(channels>1) Stack.setChannel(cell_channel);
		else cell_channel = 1;
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
		Ext.CLIJ2_greaterOrEqualConstant(dilated, ganglia_binary, 1);
		Ext.CLIJ2_release(dilated);
		Ext.CLIJ2_pull(neuron_label_image);
		Ext.CLIJ2_pull(ganglia_binary);
		selectWindow(ganglia_binary);
		
	}
}