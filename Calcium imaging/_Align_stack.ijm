//Macro (batchmode) to run the plugin "Linear Stack Alignment with SIFT" and/or Template matching on liff files in a folder
//read the tiff file 
//Only run on tiff stack with atleast 10 frames
//Run a combination of the registraiton plugins on each of the Tiff files 
//save the file as tiff, series name_aligned'
//Note: if more than one channel per tiff file, it will only run alignment on the channel chosen by user
//Alignment runs in batchmode for faster alignment
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

var fs=File.separator;

//function to run image registration; can be modified to pass parameters
//explanation of sift parameters: http://www.ini.uzh.ch/~acardona/howto.html#sift_parameters
function align_sift(sizeX, sizeY)
{
	size=sizeX;
	if(sizeY>sizeX) size=sizeY;
	//min=round(size/16);
	//if(min<16) min = 16;
	//if images are not aligned well, then either check frames for empty slices or fine-tune parameters here.
	run("Linear Stack Alignment with SIFT", "initial_gaussian_blur=1.60 steps_per_scale_octave=3 minimum_image_size=64 maximum_image_size="+size+" feature_descriptor_size=4 feature_descriptor_orientation_bins=8 closest/next_closest_ratio=0.92 maximal_alignment_error=25 inlier_ratio=0.05 expected_transformation=Affine interpolate");
}

html="<html>"
     +"<font size=+1>"
     +"<br>"
     +"If selecting Template matching, install plugin from website:<a href=https://sites.google.com/site/qingzongtseng/template-matching-ij-plugin#install>Installation</a> <br>"
     +"</font>";


//create directory lists
//input = getDirectory("Choose Input Directory ");
open(File.openDialog("Choose the file to be aligned"));
name=File.nameWithoutExtension;
rename(name);
input=File.directory;
Stack.getDimensions(sizeX, sizeY, sizeC, slices, sizeT);

//if no of slices > frames, assuming that the metadata is switched
if(slices>sizeT)
{
	print("Swapping slices with frames");
	sizeT=slices;	
}
Dialog.create("Alignment options");
Dialog.addCheckbox("Linear Alignment with SIFT", true);
Dialog.addCheckbox("Template Matching", true);
Dialog.addMessage("Atleast one option is required. Use first and second option if your\nimages have warping and lots of deformation. If you only have movement in the\nXY direction (sideways) and no warping, use only the second option");
Dialog.addMessage("If using Template Matching, the plugin can be installed using the Help button");
Dialog.addNumber("Choose reference slice for aligning stacks", 1);
Dialog.addMessage("Alignment plugins need a reference image/frame to align the rest of\nthe images. Set the frame here or first frame will be used as reference");
Dialog.addHelp(html);
Dialog.show();
sift=Dialog.getCheckbox();
template_matching=Dialog.getCheckbox();
frame_ref=Dialog.getNumber();

if(sift==false && template_matching==false) exit("Choose atleast one option");

if(sizeT>10) //only if it has greater than 10 frames
{
	print("Processing: "+name);
	if(sizeC>1) //if there are more than one channels, it will only work on channel 1
	{
		//Split channel, discard channel 2 and keep channel 1
		channel_array=Array.getSequence(sizeC);
		waitForUser("Multple channels detected. Please verify the channel to be aligned");
		Dialog.create("Choose channal to be aligned");
		Dialog.addChoice("Channel", channel_array);
		Dialog.show();
		channel_align=Dialog.getChoice();
		run("Split Channels");
		selectWindow("C"+channel_align+"-"+name);
		rename(name);
		close("C*");
		wait(10);
		//Split channel, run Linear alignment on one channel and then on the other
		selectWindow(name);
		Stack.setFrame(frame_ref);
		//setBatchMode(true); //batchmode set to True
		if(sift==true)
		{
			align_sift();
			wait(100);
			close(name);
			selectWindow("Aligned "+sizeT+" of "+sizeT);
		}
		Stack.setFrame(frame_ref);
		//second alignment just to make sure the SIFT alignment is steady
		if(template_matching==true)
		{
		x_size=floor(sizeX*0.7);	
		y_size=floor(sizeY*0.7);	
		x0=floor(sizeX/6);
		y0=floor(sizeY/6);
		run("Align slices in stack...", "method=5 windowsizex="+x_size+" windowsizey="+y_size+" x0="+x0+" y0="+y0+" swindow=0 subpixel=false itpmethod=0 ref.slice=3 show=true");
		wait(10);
		}

		print("Saving"+input+name+"_aligned");	
		saveAs("Tiff", input+name+"_aligned");
	}
	else
	{
		selectWindow(name);
		//setBatchMode(true); //batchmode set to True
		//run alignment with SIFT
		if(sift==true)
		{
			align_sift(sizeX, sizeY);
			wait(100);
			close(name);
			selectWindow("Aligned "+sizeT+" of "+sizeT);
		}
		//second alignment just to make sure the SIFT alignment is steady
		if(template_matching==true)
		{
			x_size=floor(sizeX*0.7);	
			y_size=floor(sizeY*0.7);	
			x0=floor(sizeX/6);
			y0=floor(sizeY/6);
			run("Align slices in stack...", "method=5 windowsizex="+x_size+" windowsizey="+y_size+" x0="+x0+" y0="+y0+" swindow=0 subpixel=false itpmethod=0 ref.slice=3 show=true");
			wait(10);
		}
		print("Saving"+input+name+"_aligned");
		saveAs("Tiff", input+name+"_aligned");
	}
	//setBatchMode("exit and display");
}
else{ print("No time series "+"Series: "+s); close(name);}
call("java.lang.System.gc"); //garbage collector
