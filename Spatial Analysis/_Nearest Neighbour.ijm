//Calculate nearest neighbours using CLIJ plugins
//Requires the maximum projection image of the neurons/ganglia. 

/*
 * Calculate nearest neighbours using CLIJ plugins
 * Requires the maximum projection image of the neurons/ganglia. 
 * 
 * Requires, ROI Manager of the cell type of interest, output directory and celltype being analysed
 * The celltype selected will appear in the name of the table saved
 * Reference for nearest neighbour analysis: https://clij.github.io/clij2-docs/md/tables/
 */

 
run("Clear Results");

roiManager("reset");
print("\\Clear");
run("Close All");
run("Clear Results");
fs=File.separator;
// open image
#@ String(value="<html><b>Nearest Neighbour Analysis</b> <br>Find the distance to the nearest neighbouring cell<html>", visibility="MESSAGE") hint
#@ File (label="Select the max projection image") max_proj
#@ File (label="Select roi manager for celltype") roi_path
#@ File (style="directory",label="Select Output Folder") save_path
#@ String(choices={"Neuron", "Glia"}, style="list") cell_type


//open the image
open(max_proj);
file_name=File.nameWithoutExtension;
getPixelSize(unit, pixelWidth, pixelHeight);
temp=getTitle();
roiManager("reset");
roiManager("open", roi_path);

//convert ROIs to label map image. It will use dimensions of max projection image above
run("ROI Manager to LabelMap(2D)");
wait(10);
img=getTitle();


//define save path based on file name and create a save folder if none
save_path=save_path+fs+file_name;
if(!File.exists(save_path)) File.makeDirectory(save_path);


//using CLIJ2 macros for image procession. 
//Although not absolutely essential for 2D images, this same workflow can easily be extended to 3D stacks
//and hence be reused in the future
run("CLIJ2 Macro Extensions", "cl_device=");
Ext.CLIJ2_clear();

Ext.CLIJ2_pushCurrentZStack(img);
points="points";
matrix="matr";
Ext.CLIJ2_centroidsOfLabels(img, centroids);
Ext.CLIJ2_generateDistanceMatrix(centroids, centroids, matrix);
Ext.CLIJ2_release(centroids);
Ext.CLIJ2_averageDistanceOfNClosestPoints(matrix, distance_list, 1);
Ext.CLIJ2_release(matrix);
//convert distance in pixels to microns
distance_microns="distance_microns";
Ext.CLIJ2_multiplyImageAndScalar(distance_list, distance_microns, pixelWidth);

//generate a results table
Ext.CLIJ2_statisticsOfBackgroundAndLabelledPixels(img, img);
//empty results table
headings = split(Table.headings(), "	");

for (i = 0; i < lengthOf(headings); i++) 
{
	column_name = headings[i];
	if (column_name != " ") {
	Table.deleteColumn(column_name);
	}
}

//add column to existing results table with name nearest neighbour
Ext.CLIJx_pullToResultsTableColumn(distance_list, "Nearest neighbour (pixels)", false);
Ext.CLIJx_pullToResultsTableColumn(distance_microns, "Nearest neighbour (microns)", false);
Ext.CLIJ2_clear();

IJ.renameResults("Nearest Neighbour");
selectWindow("Nearest Neighbour");
Table.deleteRows(0, 0); //delete first row which is background
Table.save(save_path+fs+cell_type+"_"+file_name+".csv");
exit("Nearest Neighbour Analysis complete");
//run("Close");

