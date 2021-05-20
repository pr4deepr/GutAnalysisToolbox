

/*Takes two label maps; cell_1 and cell_2
 * For each cell_1, will return the distance to the nearest cell_2 
 * Uses CLIJ plugins
 * Requires Maximum projection image (calibrated)
 * Can be extended to 3D with modifications where 3D ROI file is converted to labels
 * /
  *///Depending on the order, will calculate either for the neuron, distance to nearest glia OR
//for glia, the distance to nearest neuron


 

roiManager("reset");
print("\\Clear");
run("Close All");
run("Clear Results");
fs=File.separator;

// open image
#@ String(value="<html><b>Nearest Neighbour Analysis_2</b><br> For cell 1, measure the distance in um to the nearest cell 2<html>", visibility="MESSAGE") hint
#@ File (label="Select the max projection image (calibrated)") max_proj
#@ File (label="Select roi manager for cell 1") roi_path_1
#@ String(choices={"Neuron", "Glia"}, style="list") cell_type_1
#@ File (label="Select roi manager for cell 2") roi_path_2
#@ String(choices={"Neuron", "Glia"}, style="list") cell_type_2
#@ File (style="directory",label="Select Output Folder") save_path
#@ boolean Save_Parametric_Image
#@ String(value="<html>The celltypes of cell_1 and cell_2 can be interchanged to measure distances for either celltypes<html>", visibility="MESSAGE") hint2


if(cell_type_1==cell_type_2) exit("Select different celltypes");


//open the image
open(max_proj);
file_name=File.nameWithoutExtension;
getPixelSize(unit, pixelWidth, pixelHeight);
if(unit!="microns") print("Image is not in calibrated in microns. Output maybe in pixels");
img=getTitle();


//define save path based on file name and create a save folder if none
save_path=save_path+fs+file_name;
if(!File.exists(save_path)) File.makeDirectory(save_path);


roiManager("reset");
roiManager("open", roi_path_1);

//convert ROIs to label map image. It will use dimensions of max projection image above
run("ROI Manager to LabelMap(2D)");
wait(10);
cell_1=getTitle();


roiManager("reset");
roiManager("open", roi_path_2);
run("ROI Manager to LabelMap(2D)");
wait(10);
cell_2=getTitle();
roiManager("reset");


// Init GPU
run("CLIJ2 Macro Extensions", "cl_device=");
Ext.CLIJ2_pushCurrentZStack(cell_1);
Ext.CLIJ2_pushCurrentZStack(cell_2);

Ext.CLIJ2_centroidsOfLabels(cell_1, cell_1_pointlist);
Ext.CLIJ2_centroidsOfLabels(cell_2, cell_2_pointlist);

//distance of glia to nearest neuron
Ext.CLIJx_generateDistanceMatrix(cell_1_pointlist, cell_2_pointlist, matrix);
//set first column as zero
Ext.CLIJ2_setColumn(matrix, 0,0);
//set first row to max as nclosest distances uses minimum of each column. If zero, it will select row 1
Ext.CLIJ2_setRow(matrix, 0, 2147483647); //use Float.max_value in scripts instead

//find closest distances in each column, in this case using 1 as we are only looking at distance of nearest cell
Ext.CLIJ2_nClosestDistances(matrix, distances, idx_matrix, 1);

//shortest distances work for above purpose; just using the index matrix for validation
//Ext.CLIJx_shortestDistances(Image_distance_matrix, Image_destination_minimum_distances);

//convert distances to in microns
Ext.CLIJ2_multiplyImageAndScalar(distances, distance_calibrated, pixelWidth);

//get table output as one column
Ext.CLIJx_transposeXY(distance_calibrated, distance_transpose_xy);


//get this in results table
Ext.CLIJ2_pullToResultsTable(distance_transpose_xy);

//get distance matrix
//Ext.CLIJ2_pull(matrix);
//get point indices of cells
//Ext.CLIJ2_pull(distance_calibrated);

selectWindow("Results");

//selectWindow("Results");
Table.deleteRows(0, 0); //delete first row which is background

Table.renameColumn("X0", "Distance "+cell_type_1+" to "+cell_type_2+"("+unit+")");
Table.save(save_path+fs+"Distance "+cell_type_1+" to "+cell_type_2+"_"+file_name+".csv");

if(Save_Parametric_Image==true)
{
	//parameteric image with each cell having distance value in micron
	Ext.CLIJ2_replaceIntensities(cell_1, distance_calibrated, label_map);
	Ext.CLIJ2_pull(label_map);
	selectWindow(label_map);
	saveAs(".tif", save_path+fs+"Distance "+cell_type_1+" to "+cell_type_2+"_"+file_name);
	//Ext.CLIJ2_saveAsTIF(label_map, save_path+fs+"Distance "+cell_type_1+" to "+cell_type_2+"_"+file_name+".tif");
}
//clear GPU memory
Ext.CLIJ2_clear();