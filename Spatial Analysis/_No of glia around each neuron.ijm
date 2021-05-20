////calculate no of glia around each neuron within a range of distances
//between distance_1 and distance_2
//the distance is from centroid of each neuron
//30 microns appears to be good


run("Close All");
run("Clear Results");

// open image
#@ File (label="Select original image") orig_img_path
#@ File (label="Select roi manager for glia") roi_path_glia
#@ File (label="Select roi manager for neuron") roi_path_neuron
#@ Integer (label="Enter minimum neighbour distance (um)", value=30) distance_1
#@ Integer (label="Enter maximum neighbour distance (um)", value=30) distance_2
#@ Integer (label="Enter increment step for neighbour distance (um)", value=1) step_micron
//#@ String(choices={"No of glia around each neuron","No of neuron around each glia", "Both"}, style="radioButtonHorizontal") analysis_choice
#@ String(value="Cells are considered neighbours if they are within the Neighbour Distance specified above", visibility="MESSAGE") hint


open(orig_img_path);
orig_img=getTitle();
file_name=File.nameWithoutExtension;
getPixelSize(unit, pixelWidth, pixelHeight);
Stack.getDimensions(width, height, channels, slices, frames);

roiManager("reset");
//selectWindow("test");
roiManager("open", roi_path_glia);
run("ROI Manager to LabelMap(2D)");
rename("glia_labelmap");
run("glasbey_on_dark");
roiManager("reset");

//selectWindow("test");
roiManager("open", roi_path_neuron);
run("ROI Manager to LabelMap(2D)");
rename("neuron_labelmap");
run("glasbey_on_dark");
// Init GPU
run("CLIJ2 Macro Extensions", "cl_device=");
Ext.CLIJ2_clear();
Ext.CLIJ2_pushCurrentZStack("glia_labelmap");
Ext.CLIJ2_pushCurrentZStack("neuron_labelmap");

//convert microns to pixels
step=Math.floor(step_micron/pixelWidth);
min_distance = 0.0;
//first distance is distance_1 as specified by suer
first_distance= Math.floor(distance_1/pixelWidth);
//last distance is distance_2 as specified by suer
last_distance = Math.ceil(distance_2/pixelWidth); 
//calculate no of glia around each neuron within distance of first_distance and last_distance
//every step (as specified)
table_name="Distances_"+file_name;
Table.create(table_name);
run("Set Measurements...", "min redirect=None decimal=3");
//how many glia around each neuron
roiManager("deselect");
//Plot.create("Histogram", "Bins", "No of Neighbours");
//color_array=newArray("black", "blue", "cyan", "darkGray", "gray", "green", "lightGray", "magenta", "orange", "pink", "red", "white", "yellow");
counter=0;
for(i=first_distance;i<=last_distance;i+=step)
{
	//print(i); 
	run("Clear Results");
	micron_conv=i*pixelWidth;
	//print(micron_conv);
	Ext.CLIJx_labelProximalNeighborCountMap("neuron_labelmap", "glia_labelmap", image7, min_distance, i);
	Ext.CLIJ2_pull(image7);
	distance_name="distance_"+round(micron_conv);
	rename(distance_name);
	run("Fire");
	selectWindow(distance_name);
	roiManager("measure");
	neighbour_values=get_column_results("Max");
	selectWindow(table_name);
	Table.setColumn(distance_name,neighbour_values);
	//Plot.setColor(color_array[counter]);
	//Plot.addHistogram(neighbour_values, 0, 0);
	counter+=1;
	
} 
Table.update;

//return the column of results table
function get_column_results(column_name)
{
	selectWindow("Results");
	arr_column=newArray();
	setOption("ExpandableArrays", true);
	for(i = 0; i < nResults(); i++)
	{
    arr_column[i] = getResult(column_name, i);
	}
return arr_column;

}
//close("distance*");