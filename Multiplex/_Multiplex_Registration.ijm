//Macro for aligning multiplexed images from multiple rounds of immunofluorescence/staining
//All the images should be in one folder
//Each round or multiplexing round should be named as layer 1 or round 1, layer 2, layer 3 etc...
//There is an option to enter a custom name
//Each marker name should be at the end of the file name after an underscore. 
//There should be a common marker in every round of multiplexing which can be used as a reference for aligning all the channels
//This marker should be also in the file name
//For example, 
//H2202Desc_Layer 1_Ganglia1_Hu
//H2202Desc_Layer1_Ganglia1_5HT
//H2202Desc_Layer2_Ganglia1_Hu
//H2202Desc_Layer2_Ganglia1_SP
//H2202Desc_Layer3_Ganglia1_Hu
//H2202Desc_Layer3_Ganglia1_VAChT
// In above, there are 3 rounds of multiplexing and in every round, "Hu" is the common reference marker
//The script seggregates each round of IHC as Layer1, Layer 2, Layer 3
//looks for Hu in each channel, uses Hu in the first round as a reference, aligns Hu in each round to this image
//The alignment information is then used for other markers in each round to align the images.
//The output is aligned stack, stack of all reference marker and an ROI manager with information corresponding to the alignment (landmark correspondences)
// Author: Pradeep Rajasekhar
// July 2023
// License: BSD3
// 
// Copyright 2023 Pradeep Rajasekhar, Walter and Eliza Hall Institute of Medical Research
// 
// Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
// 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
// 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//



print("\\Clear");

#@ File (style="directory",label="Select folder with immuno files") imageFolder
#@ String(label="Enter name of common marker (Hu)") common_marker
#@ Integer(value=2, min=1, max=10,label="Enter number of rounds of multiplexing") multiplex_no
#@ String(label="Enter name that distinguishes each batch of multiplexing (Layer/Round)") layer
#@ boolean(value=false) Choose_Save_Folder
#@ boolean(value=false) Finetune_parameters
if(Choose_Save_Folder)
{
	save_dir = getDirectory("Enter Save Directory");
	
}
else 
{
	save_dir = imageFolder;
}

//default values
minimal_inlier_ratio=0.50;
steps_per_scale_octave = 3;

if(Finetune_parameters)
{
	Dialog.create("Parameters for alignment (SIFT)");
	Dialog.addMessage("Default values shown below will be used if no changes are made");
	Dialog.addNumber("minimal_inlier_ratio", minimal_inlier_ratio);
	Dialog.addNumber("steps per scale octave", steps_per_scale_octave);
	Dialog.show();
	minimal_inlier_ratio = Dialog.getNumber();
	steps_per_scale_octave = Dialog.getNumber();
}


print("Files being saved at "+save_dir);
close("*");

roiManager("reset");
fs=File.separator;

file_list=getFileList(imageFolder);
common_marker=common_marker.toLowerCase;
tissue_array=newArray();
setOption("ExpandableArrays", true);

layer=layer.toLowerCase;
//tissue_name=tissue_name.toLowerCase;
file_name_lower=newArray();
file_name_counter=0;

results_folder=save_dir+fs+"Results"+fs;
print(results_folder);
if(File.exists(results_folder))
{
	exit("Remove Results folder in directory");
	
}
else
{
	File.makeDirectory(results_folder);
}

print("Getting reference images");
for (i = 0; i <file_list.length; i++)
{
	if(endsWith(file_list[i], ".tif"))
	{
	idx=indexOf(file_list[i], ".tif");
	file_name=substring(file_list[i], 0, idx);
	file_name=file_name.trim;
	file_name=file_name.toLowerCase;
	if(matches(file_name, ".*"+common_marker+".*"))
		{
			print(file_list[i]);
			tissue_array[file_name_counter]=file_list[i];
			file_name_counter+=1;
	
		}
	}
}


tissue_array=Array.sort(tissue_array);
Array.print(tissue_array);

//setBatchMode(true);
imageFolder=imageFolder+fs;


open(imageFolder+tissue_array[0]);
ref_image=getTitle();
print("Using "+ref_image+" as reference");
//print(ref_image);
roi_counter=0;
Stack.getDimensions(width, height, channels, slices, frames);
bit_depth=bitDepth();

//max image size for sift
max_image_size=width;
if(height>width) max_image_size=height;


newImage(common_marker+"_stack", bit_depth+"-bit black", width, height, 1);
ref_stack=getTitle();

selectWindow(ref_image);
run("Select All");
run("Copy");
run("Select None");
selectWindow(ref_stack);
run("Paste");
setMetadata("Label", ref_image);	

//select reference image from first multiplex layer and ref image from next layer, find SIFT features and save as 2 ROIs
for(i=1;i<tissue_array.length;i++)
{
	
	open(imageFolder+tissue_array[i]);
	target_img=getTitle();
	print(target_img);
	wait(10);
	//https://imagej.net/plugins/feature-extraction
	//steps_per_scale_octave; More steps result in more but eventually less stable keypoint candidates
	//iter = 0
	//steps_per_scale_octave = 3;
	do
	{
		run("Extract SIFT Correspondences", "source_image=["+ref_image+"] target_image=["+target_img+"] initial_gaussian_blur=1.60 steps_per_scale_octave="+steps_per_scale_octave+" minimum_image_size=32 maximum_image_size="+height+" feature_descriptor_size=4 feature_descriptor_orientation_bins=8 closest/next_closest_ratio=0.92 filter maximal_alignment_error=25 minimal_inlier_ratio="+minimal_inlier_ratio+" minimal_number_of_inliers=7 expected_transformation=Affine");
		wait(10);
		//iter+=1;
		steps_per_scale_octave+=3;
		//if(steps_per_scale_octave==30) break; //10 iterations
	} while((selectionType()<0) && (steps_per_scale_octave<=30)) 
	
	//if no features identified, try MOPS
	if(selectionType()<0)
	{
		run("Extract MOPS Correspondences", "source_image=["+ref_image+"] target_image=["+target_img+"] initial_gaussian_blur=1.60 steps_per_scale_octave="+steps_per_scale_octave+" minimum_image_size=64 maximum_image_size="+height+" feature_descriptor_size=16 closest/next_closest_ratio=0.92 maximal_alignment_error=25 inlier_ratio=0.50 expected_transformation=Affine");
	}

	getSelectionCoordinates(xpoints, ypoints);
	if((xpoints.length == 0) || (xpoints.length<5))
	{
		run("Select None");
		run("Extract Block Matching Correspondences", "source_image=["+ref_image+"] target_image=["+target_img+"] layer_scale=1 search_radius=50 block_radius=50 resolution=24 minimal_pmcc_r=0.10 maximal_curvature_ratio=1000 maximal_second_best_r/best_r=1 use_local_smoothness_filter approximate_local_transformation=Affine local_region_sigma=65 maximal_local_displacement=12 maximal_local_displacement_0=3 export");
	}

	if(selectionType()<0) exit("couldn't find matches for "+ref_image);
	
	selectWindow(ref_image);
	roiManager("add");
	roiManager("select",roi_counter);
	roiManager("rename", common_marker+"_"+(i+1)+"_ref");
	selectWindow(ref_image);
	run("Remove Overlay");
	run("Select None");
	selectWindow(target_img);
	roiManager("add");
	roiManager("select",roi_counter+1);
	roiManager("rename", common_marker+"_"+(i+1)+"_target");
	run("Select None");
	selectWindow(target_img);
	run("Select All");
	run("Copy");
	run("Select None");
	selectWindow(ref_stack);
	run("Add Slice");
	run("Paste");
	setMetadata("Label", target_img);	
	//roiManager("select",roi_counter+1);
	//selectWindow(target_img);
	//run("Remove Overlay");
	//run("Select None");
	close(target_img);
	roi_counter+=2;
}

selectWindow(ref_stack);
saveAs("tif", results_folder+common_marker+"_stack.tif");
close();

roi_counter=0;

newImage("STACK", bit_depth+"-bit black", width, height, 1);
stack=getTitle();

for(i=0;i<multiplex_no;i++)
{

	j=0;//counter for files
	layer_name=layer+d2s(i+1,0);//name for each layer/multiplex batch
	
	do 
	{
		idx=indexOf(file_list[j], ".tif");
		file_name=substring(file_list[j], 0, idx);
		file_name=file_name.trim; //remove leading and trailing whitespace
		file_name=file_name.toLowerCase; //make lowercase
		file_name=replace(file_name," ", ""); //remove spaces in name
		//if it has layer_name (layer1 or layer2 in it) and not Hu, open it
		if(matches(file_name, ".*"+layer_name+".*") && !matches(file_name, ".*"+common_marker+".*")) 
		{
			open(imageFolder+file_list[j]);
			temp=getTitle();
			if(i==0) //if reference image, no need for alignment
			{
				print("Stacking images from first multiplex layer");
				selectWindow(stack);
				if(nSlices<2)
				{
					selectWindow(ref_image); //add Hu
					run("Select All");
					run("Copy");
					run("Select None");
					selectWindow(stack);
					run("Paste");
					setMetadata("Label", ref_image);
				}
				selectWindow(temp);
				run("Select All");
				run("Copy");
				selectWindow(stack);
				run("Add Slice");
				run("Paste");
				setMetadata("Label", temp);	
				close(temp);
					
			}
			else 
			{
				
				print("Stacking images from multiplex layer no. "+(i+1));
				selectWindow(ref_image); 
				//print(ref_image);
				roiManager("select", roi_counter);
				//print(Roi.getName);
				//wait(1000);
				selectWindow(temp);
				roiManager("select", roi_counter+1);
				//print(temp);
				//print(Roi.getName);
				//wait(1000);
				run("Landmark Correspondences", "source_image=["+temp+"] template_image=["+ref_image+"] transformation_method=[Least Squares] alpha=1 mesh_resolution=32 transformation_class=Affine interpolate");
				wait(10);
				aligned=getTitle();
				run("Select All");
				run("Copy");
				selectWindow(stack);
				run("Add Slice");
				run("Paste");
				setMetadata("Label", temp);					
				close(temp);
				close(aligned);
			}
			//print(file_list[j]);
			
				
		//print(temp);
		}
		j+=1;

	} while (j<file_list.length) //no of files in file_list
	if(i>0) roi_counter+=2;
}

//setBatchMode("exit and display");

selectWindow(stack);
no_channels=nSlices;
Stack.setDimensions(no_channels, 1, 1);
run("Make Composite", "display=Grayscale");
saveAs("tif", results_folder+"Aligned_Stack.tif");
roiManager("deselect");
roiManager("save", results_folder+"landmark_correspondences.zip");
close(ref_image);
exit("Done");

//quality assessment using SSIM plugin

