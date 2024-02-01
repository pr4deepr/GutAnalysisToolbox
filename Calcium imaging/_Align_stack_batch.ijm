//Macro (batchmode) to run the plugin "Linear Stack Alignment with SIFT" and/or Template matching on liff files in a folder
//read files in a tiff or liff  file ; (Acquired from Leica microscope)
//Extract the timeseries files which have atleast more than 10 frames
//Run a combination of the registraiton plugins on each of the Tiff files 
//save the file as tiff, series name_aligned'
//Note: if more than one channel per tiff file, it will only run alignment on the first channel
//Runs in batchmode so images won't be displayed
// Author: Pradeep Rajasekhar
// July 2021
// License: BSD3
// 
// Copyright 2021 Pradeep Rajasekhar, INM Lab, Monash Institute of Pharmaceutical Sciences
// 
// Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
// 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
// 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//


print("Please install this template plugin if you haven't already: https://sites.google.com/site/qingzongtseng/template-matching-ij-plugin#install");

//Clears the Results window
print ("\\Clear");


//function to run image registration; can be modified to pass parameters
//explanation of sift parameters: http://www.ini.uzh.ch/~acardona/howto.html#sift_parameters
//https://imagej.net/plugins/feature-extraction#parameters
function align_sift(sizeX, sizeY,default)
{
	
	size=minOf(sizeX, sizeY);
	maximal_alignment_error = Math.ceil(0.1*size);	
	if(!default)
	{
	//maximal_alignment_error = 5; 
	inlier_ratio=0.7;
	if(size<500) 
	{ //for smaller images these parameters worked well
		feature_desc_size=8;
		maximal_alignment_error = 5;
		inlier_ratio=0.9;
	}
	else feature_desc_size=4;
	run("Linear Stack Alignment with SIFT", "initial_gaussian_blur=1.60 steps_per_scale_octave=4 minimum_image_size=64 maximum_image_size="+size+" feature_descriptor_size="+feature_desc_size+" feature_descriptor_orientation_bins=8 closest/next_closest_ratio=0.92 maximal_alignment_error="+maximal_alignment_error+" inlier_ratio="+inlier_ratio+" expected_transformation=Affine");
	}
	else 
	{
		maximal_alignment_error = Math.ceil(0.1*size);	
		run("Linear Stack Alignment with SIFT", "initial_gaussian_blur=1.60 steps_per_scale_octave=3 minimum_image_size=64 maximum_image_size="+size+" feature_descriptor_size=4 feature_descriptor_orientation_bins=8 closest/next_closest_ratio=0.92 maximal_alignment_error="+maximal_alignment_error+" inlier_ratio=0.05 expected_transformation=Affine");
	}
}

html="<html>"
     +"<font size=+1>"
     +"<br>"
     +"If selecting Template matching, install plugin from website:<a href=https://sites.google.com/site/qingzongtseng/template-matching-ij-plugin#install>Installation</a> <br>"
     +"</font>";



items=newArray("Template Matching","StackReg");

//create directory lists
input = getDirectory("Choose Input Directory with images ");
Dialog.create("Alignment options");
Dialog.addCheckbox("Linear Alignment with SIFT", true);
Dialog.addRadioButtonGroup("Alignment in XY (no warping). Choose one", items, 1, items.length, items[0]);
//Dialog.addCheckbox("Linear Alignment with SIFT", true);
//Dialog.addCheckbox("Template Matching", true);
//Dialog.addCheckbox("StackReg", true);
Dialog.addMessage("Use Linear Alignment with SIFT if your\nimages have warping and lots of deformation. If you only have movement in the\nXY direction (sideways) and no warping, use either Template Matching or StackReg ");
Dialog.addNumber("Choose reference slice for aligning stacks", 1);
Dialog.addMessage("Alignment plugins need a reference image/frame to align the rest of\nthe images. Set the frame here or first frame will be used as reference");
Dialog.addMessage("If alignment is not satisfactory, try ticking this box.");
Dialog.addCheckbox("Default settings", false);
Dialog.addString("File extension: ", ".tif");
Dialog.addMessage("If the files are Leica .lif files, each series within the file will be aligned");
Dialog.addHelp(html);
Dialog.show();
sift=Dialog.getCheckbox();
//template_matching=Dialog.getCheckbox();
alignment_choice=Dialog.getRadioButton();
frame_ref=Dialog.getNumber();
default_settings = Dialog.getCheckbox();
file_ext=Dialog.getString();



input_list = getFileList(input); //get no of files
print("Files in folder: ");
Array.print(input_list);
run("Bio-Formats Macro Extensions");//can use macro extensions (Ext.) for working with lif files and other popular file formats; only tested on .lif and .tif files


//run a for loop iteratively on each file within the folder
for (i=0; i<input_list.length; i++)
{
	path=input+input_list[i];
	if (endsWith(path, file_ext)) //only execute following code if file ends with lif// could adapt to other microscopy formats
		{
			if(file_ext==".lif")
			{
				print(path);
				Ext.setId(path); //set file id to the desired lif file to access it
				Ext.getSeriesCount(seriesCount); //no of files in the lif
				print("series count= "+seriesCount);
				for (s=1; s<=seriesCount; s++) //need to set it to start from 1 as the bioformats importer only recognises from 1 onwards and not 0
				{
					run("Bio-Formats Importer", "open=["+path+"] color_mode=Default rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT series_"+s);
					name=getTitle();
					print(name);
					//setSeries will set a corresponding series within the liff file as active, so it can be accessed
					//confusingly enough, setSeries starts from 0 onwards, so for Series 1, it has to be setSeries(0); 
					Ext.setSeries(s-1);
					process_file_align(name,input,frame_ref,alignment_choice);
				}
	
		}
		else 
		{
			//open(path);
			//using bioformats so that it can work on different file types
			Ext.setId(path); //set file id to the desired lif file to access it
			run("Bio-Formats Importer", "open=["+path+"] autoscale color_mode=Default rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT");
			name=getTitle();
			name=File.nameWithoutExtension;
			rename(name);
			process_file_align(name,input,frame_ref,alignment_choice);
		}
	}
	else //if file does not end with the specified extension
	{
	print("skipping "+path+"as it does not match the extension: "+file_ext);
	}
}
Ext.close();
exit("Alignment Finished");


function process_file_align(name,input,frame_ref,alignment_choice)
{
	selectWindow(name);
	Stack.setFrame(frame_ref);
	//get number of frames
	Ext.getSizeT(sizeT);
	Ext.getSizeZ(slices);
	if(slices>sizeT)
	{
		sizeT=slices;
		print("Swapping slices with frames");
	}
	print("No of frames:"+sizeT);
	
	if(sizeT>10) //only if it has greater than 10 frames
	{
		print("Processing: "+name);
		Ext.getSizeC(sizeC);
		Ext.getSizeX(sizeX);
    	Ext.getSizeY(sizeY);
		if(sizeC>1) //if there are more than one channels, it will only work on channel 1
		{
		//Split channel, discard channel 2 and keep channel 1
    	print("Multichannel image, only aligning channel 1");
		run("Split Channels");
		wait(500);
		close("C2-"+name);//assuming first channel is calcium data/FLuo8
		selectWindow("C1-"+name);
		Stack.setFrame(frame_ref);
		//Split channel, run Linear alignment on one channel and then on the other
		aligned=align_images("C1-"+name,alignment_choice,sizeX,sizeY,sizeT,frame_ref);
		selectWindow(aligned);
		print("Saving"+input+name+"_aligned");
		saveAs("Tiff", input+name+"_aligned");
		close(name+"_aligned.tif");
		}
		else
		{
			aligned=align_images(name,alignment_choice,sizeX,sizeY,sizeT,frame_ref);
			selectWindow(aligned);
			print("Saving"+input+name+"_aligned.tif");
			saveAs("Tiff", input+name+"_aligned");
			close(name+"_aligned.tif");
		}
	}
	else{ print("Not a time series"); close(name);}
	call("java.lang.System.gc"); //garbage collector
}


//function to run the alignment based on choice
function align_images(name,alignment_choice,sizeX,sizeY,sizeT,frame_ref)
{
	selectWindow(name);
	setBatchMode(true); //batchmode set to True
	//run alignment with SIFT
	if(sift)
	{
		print("Running SIFT");
		Stack.setFrame(frame_ref);
		align_sift(sizeX, sizeY,default_settings);
		wait(100);
		close(name);
		selectWindow("Aligned "+sizeT+" of "+sizeT);
	}
	if(alignment_choice=="Template Matching")
	{
		Stack.setFrame(frame_ref);
		x_size=floor(sizeX*0.7);	
		y_size=floor(sizeY*0.7);	
		x0=floor(sizeX/6);
		y0=floor(sizeY/6);
		run("Align slices in stack...", "method=5 windowsizex="+x_size+" windowsizey="+y_size+" x0="+x0+" y0="+y0+" swindow=0 subpixel=false itpmethod=0 ref.slice=3 show=true");
		wait(10);
	}
	else if(alignment_choice=="StackReg")
	{
		Stack.setFrame(frame_ref);
		run("StackReg", "transformation=[Rigid Body]");
	}
	aligned=getTitle();
	setBatchMode("exit and display");
	return aligned;
}

