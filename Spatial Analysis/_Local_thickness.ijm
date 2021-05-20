//Determine local thickness within a ganglia for neuron or glia.
//Local thickness computes the area of largest sphere that can be accommodated within space between cells
//If there is cell loss or change in packing density, there should be a change in this value, i.e., 
//more space means larger mean local thickness and possibly larger std deviation.

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





roiManager("reset");
print("\\Clear");
run("Close All");
run("Clear Results");
fs=File.separator;
// open image
#@ String(value="<html><b>Local Thickness</b><br>Measures the diameter of the biggest sphere that fits within the space between cells.<br>Loss of cells or re-arrangement could lead to increased space, and thus higher mean and standard deviation values<html>", visibility="MESSAGE") hint
#@ File (label="Select binary image") bin_img
#@ File (label="Select original image") orig_img_path
#@ File (label="Select roi manager for celltype") roi_path
#@ File (style="directory",label="Select Output Folder") save_path
#@ String(choices={"Neuron", "Glia"}, style="list") cell_type

setForegroundColor(255, 255, 255);
setBackgroundColor(0, 0, 0);

open(orig_img_path);
orig_img=getTitle();
file_name=File.nameWithoutExtension;
getPixelSize(unit, pixelWidth, pixelHeight);
Stack.getDimensions(width, height, channels, slices, frames);
print("Processing Image "+file_name);
print("*****************************");
//close(orig_img);
File.setDefaultDir(save_path);


save_path=save_path+fs+file_name+fs;
if(!File.exists(save_path)) File.makeDirectory(save_path);



setOption("BlackBackground", true);

open(bin_img);
setVoxelSize(pixelWidth, pixelHeight, pixelWidth, unit);
bin=getTitle();
selectWindow(bin);
getMinAndMax(min, max);
setThreshold(1, max);
run("Convert to Mask");



run("Select None");
run("Remove Overlay");
run("Duplicate...", "title=temp_bin");
selectWindow("temp_bin");
roiManager("open", roi_path);
run("ROI Manager to LabelMap(2D)");
wait(10);
roi_label_map=getTitle();
selectWindow(roi_label_map);
run("Fill Holes (Binary/Gray)");
roi_labels_fill=getTitle();
roiManager("reset");

selectWindow(roi_labels_fill);
run("LabelMap to ROI Manager (2D)");

close(roi_label_map);
close(roi_labels_fill);

setForegroundColor(0, 0, 0);
selectWindow("temp_bin");
roiManager("deselect");
roiManager("fill");
roiManager("reset");
setForegroundColor(255, 255, 255);
selectWindow(bin);
run("Select None");
run("Remove Overlay");
run("Create Selection");
roiManager("add");
roiManager("select", 0);

roi_type=1; //1 for composite

do{
	for(i=0;i<roiManager("count");i++)
	{
		selectWindow(bin);
		roiManager("select", i);
		area=getValue("Area");
		//waitForUser;
		if(selectionType()==9) 
			{
			roiManager("Split");
			roi_type=1;
			roiManager("select", i);
			roiManager("delete");
			i-=1;
			}
		else if(area<120)  //less than 120 microns delete
		 {
		 	roiManager("delete");
		 	i-=1;
		 }
		else {
			roi_type=0;
			}
	}
}
while(roi_type==1)

ganglia=roiManager("count");

selectWindow("temp_bin");
run("Local Thickness (complete process)", "threshold=255");
wait(10);
local_thickness=getTitle();
//run("Set Measurements...", "mean standard redirect=None decimal=3");
run("Set Measurements...", "mean standard min redirect=None decimal=3");

setOption("ExpandableArrays", true);
ganglia_array=newArray();
mean_array=newArray();
stdev_array=newArray();
max_array=newArray();
if(ganglia==1)
{
	print("*****************************");
	print("Processing one Ganglia ");
	roiManager("select", 0);
	roiManager("measure");
	
}
else if(ganglia>1)
{
	for (g = 0; g < ganglia; g++) 
	{
		roiManager("select", g);
		mean=getValue("Mean");
		stdev=getValue("StdDev");
		maximum=getValue("Max");
		mean_array[g]=mean*pixelWidth;
		stdev_array[g]=stdev*pixelWidth;
		max_array[g]=maximum*pixelWidth;
		ganglia_array[g]=g;
		//Table.set("Ganglia", g, "Ganglia_"+g);
		//Table.set("Mean (microns)", g, mean);
		//Table.set("Stdev (microns)", g,stdev);
		//Table.update;
		//roiManager("measure");
	}
}


Table.setColumn("Ganglia", ganglia_array);
Table.setColumn("Mean (microns)", mean_array);
Table.setColumn("Maximum (microns)", max_array);
Table.setColumn("Std Deviation (microns)", stdev_array);
Array.getStatistics(mean_array, min, max, mean, stdDev);



Table.set("Ganglia", ganglia, "**");
Table.set("Mean (microns)", ganglia, "**");
Table.set("Maximum (microns)", ganglia, "**");
Table.set("Std Deviation (microns)", ganglia, "**");

Table.set("Mean (microns)", ganglia+1, "Total Mean (microns)");
Table.set("Maximum (microns)", ganglia+1, "Total Maximum (microns)");
Table.set("Std Deviation (microns)", ganglia+1, "StdDev (microns)");

Table.set("Mean (microns)", ganglia+2, mean);
Table.set("Maximum (microns)", ganglia+2, max);
Table.set("Std Deviation (microns)", ganglia+2, stdDev);
Table.update;

IJ.renameResults("local_thickness_table");
selectWindow("local_thickness_table");
Table.save(save_path+"local_thickness_"+file_name+".csv");
selectWindow(local_thickness);
saveAs("Tiff", save_path+"local_thickness_"+file_name);