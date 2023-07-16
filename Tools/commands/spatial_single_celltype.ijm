/*
 * calculate number of neighbours for single marker. 
 * 
 */

//Use runMacro("Directory where macro installed//spatial_single_celltype.ijm","name of marker 1, marker1_image_name,
// ganglia_binary_img_name,save_path,label_dilation value, save_parametric_image flag (1 or 0"),pixelWidth;

//if no ganglia_binary, enter "NA" 

var fs=File.separator;
setOption("ExpandableArrays", true);


var fiji_dir=getDirectory("imagej");
var gat_dir=fiji_dir+"scripts"+fs+"GAT"+fs+"Tools"+fs+"commands";



macro "spatial_single_celltype"
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
		exit("No arguments set for spatial analysis (Two cell types");
	}
	else 
	{
		run("CLIJ2 Macro Extensions", "cl_device=");
		args=getArgument();
		arg_array=split(args, ",");
		
		cell_type_1 = arg_array[0].trim();
		cell_1 = arg_array[1].trim();
		//roi_path_1 = arg_array[2].trim();
		
		ganglia_binary_orig = arg_array[2].trim();
	
		save_path = arg_array[3].trim();
		
		label_dilation = parseFloat(arg_array[4].trim());
		
		save_parametric_image = parseFloat(arg_array[5].trim());
		
		pixelWidth = parseFloat(arg_array[6].trim());
		
		print("Getting number of neighbours for "+cell_type_1);
		
		//convert to pixels
		label_dilation=round(label_dilation/pixelWidth);
		
		//print("Expansion in pixels "+label_dilation);
		
		save_path=save_path+fs+"spatial_analysis"+fs;
		if(!File.exists(save_path)) File.makeDirectory(save_path);
		
				
		//binary image for ganglia
		setOption("BlackBackground", true);
		if(ganglia_binary_orig!="NA")
		{
			selectWindow(ganglia_binary_orig);
			run("Select None");
			run("Duplicate...", "title=ganglia_binary_dup");
			ganglia_binary = getTitle();
			setThreshold(0.5, 65535);
			run("Convert to Mask");
			rename("Ganglia_outline");
			ganglia_binary=getTitle();
			run("Divide...", "value=255"); //binary image needs to have 0 and 1 as background and foreground, esp to use in clij
			setMinAndMax(0, 1);
		
			
		}
		else ganglia_binary="NA";
		
		neighbour_count = nearest_neighbour_single_cell(cell_1,label_dilation,ganglia_binary,cell_type_1);
		neighbour_count_no_background = Array.deleteIndex(neighbour_count, 0);
		
		table_name = "Neighbour_count_"+cell_type_1;
		table_path=save_path+fs+table_name+".csv";
		
		
		Table.create("Cell_counts_neighbour_"+cell_type_1+"_only");
		Table.setColumn("No of cells around "+cell_type_1, neighbour_count_no_background);
		Table.update;
		Table.save(table_path);
		close("Cell_counts_neighbour_"+cell_type_1+"_only");
		
		if(save_parametric_image==true)
				
		{

			neighbour_cell_1=get_parameteric_img(neighbour_count,cell_1,cell_type_1);
			

			selectWindow(neighbour_cell_1);
			saveAs("Tiff", save_path+fs+neighbour_cell_1);
			close();

		}
			
			if (ganglia_binary!="NA") close(ganglia_binary);
		
	}
	print("Spatial analysis done for "+cell_type_1);

}

//get nearest neighbours around each cell for only one marker
//uses touching neighbour count function in clij
function nearest_neighbour_single_cell(ref_img,dilate_radius,ganglia_binary,cell_type_1)
{
	
	run("Clear Results");
	run("CLIJ2 Macro Extensions", "cl_device=");
	Ext.CLIJ2_push(ref_img);
	//dilate cells in ref_img
	Ext.CLIJ2_dilateLabels(ref_img, ref_dilate, dilate_radius);
	if (isOpen(ganglia_binary))
	{
		Ext.CLIJ2_push(ganglia_binary);
		Ext.CLIJ2_multiplyImages(ref_dilate, ganglia_binary, ref_dilate_ganglia_restrict);
		//get neighbour count for each cell in ref_img
		Ext.CLIJ2_touchingNeighborCountMap(ref_dilate_ganglia_restrict, ref_neighbour_count);
		Ext.CLIJ2_release(ganglia_binary);
	}
	else 
	{
		Ext.CLIJ2_touchingNeighborCountMap(ref_dilate, ref_neighbour_count);
	}
	
	//reducing labels to centroids to avoid any edge artefacts
	//effectively reading value at  centroid of each label
	Ext.CLIJ2_reduceLabelsToCentroids(ref_img, ref_img_centroid);
	Ext.CLIJ2_statisticsOfBackgroundAndLabelledPixels(ref_neighbour_count, ref_img_centroid);

	selectWindow("Results");
	
	//get no of neighbours from max_intensity column
	no_neighbours = Table.getColumn("MINIMUM_INTENSITY");
	run("Clear Results");
	no_neighbours[0]=0;
	
	
	Ext.CLIJ2_release(ref_neighbour_count);
	Ext.CLIJ2_release(ref_img_centroid);
	
	return no_neighbours;
	
}

//generate parameteric image by replacing label values for each cell in "cell_label_img" with the value for the no of neighbours
function get_parameteric_img(no_neighbours,cell_label_img,cell_type_1)
{
	//no of neighbours array with index 0 as background and cell label image
	
	
	run("CLIJ2 Macro Extensions", "cl_device=");
	Ext.CLIJ2_pushArray2D(vector_neighbours, no_neighbours, no_neighbours.length, 1);
	Ext.CLIJ2_replaceIntensities(cell_label_img, vector_neighbours, parametric_img);
	Ext.CLIJ2_pull(parametric_img);
	new_name = cell_type_1+"_neighbours";
	selectWindow(parametric_img);
	rename(new_name);
	run("Fire");
	return new_name;
			
}