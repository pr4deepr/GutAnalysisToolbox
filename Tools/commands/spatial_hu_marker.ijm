/*
 * calculate number of neighbours for Hu and a marker image. 
 * Note: This can only be used if one of the images are Hu, i.e., labels all cells
 *  * Calculate the no of immediate neighbours around a user-defined radius for 2 markers; need to provide radius in microns and the pixelsize as well
 * Use this with Hu (pan-neuronal marker)-> ref_img and a second marker -> marker_img
*  Also takes a ganglia image, but optional
 */

//Use runMacro("Directory where macro installed//spatial_two_celltype.ijm","name of marker 1, marker1_image_name,
//name of marker 2,  marker2_image_name,, ganglia_binary_img_name,save_path,label_dilation value, save_parametric_image flag (1 or 0"),pixelWidth;

//if no ganglia_binary, enter "NA" 

var fs=File.separator;
setOption("ExpandableArrays", true);


var fiji_dir=getDirectory("imagej");
var gat_dir=fiji_dir+"scripts"+fs+"GAT"+fs+"Tools"+fs+"commands";



macro "spatial_hu_marker"
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
		exit("No arguments set for spatial analysis (Two cell types)\nNeeds to be run within GAT.");
	}
	else 
	{
		run("CLIJ2 Macro Extensions", "cl_device=");
		args=getArgument();
		arg_array=split(args, ",");
		
		cell_type_1 = arg_array[0].trim();
		cell_1 = arg_array[1].trim();
		
		cell_type_2 = arg_array[2].trim();
		cell_2 = arg_array[3].trim();
		
		ganglia_binary_orig = arg_array[4].trim();
	
		save_path = arg_array[5].trim();
		
		label_dilation = parseFloat(arg_array[6].trim());
		
		save_parametric_image = parseFloat(arg_array[7].trim());
		
		pixelWidth = parseFloat(arg_array[8].trim());
		
		roi_location_cell_1 = arg_array[9].trim();
		roi_location_cell_2 = arg_array[10].trim();
		
		print("Running spatial analysis on "+cell_type_1+" and "+cell_type_2);
		
		if(cell_type_1==cell_type_2) exit("Cell names or ROI managers are the same for both celltypes");
		
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


		no_neighbours_ref_marker=count_ref_around_marker(cell_1,cell_2,label_dilation,ganglia_binary,cell_type_1,cell_type_2);
		counts_ref_marker = Array.deleteIndex(no_neighbours_ref_marker, 0);
		
		no_neighbours_marker_ref=count_marker_around_ref(cell_1,cell_2,label_dilation,ganglia_binary);
		counts_marker_ref = Array.deleteIndex(no_neighbours_marker_ref, 0);
		
		roiManager("reset");
		run("Clear Results");
		table_name = "Neighbour_count_"+cell_type_1+"_"+cell_type_2;
		table_path=save_path+fs+table_name+".csv";

		cell_1_names = get_roi_labels(roi_location_cell_1,cell_1);
		cell_2_names = get_roi_labels(roi_location_cell_2,cell_2);
		
		
		Table.create("Cell_counts_overlap_"+cell_type_1+"_"+cell_type_2);
		Table.setColumn(cell_type_2+"_id", cell_2_names);
		Table.setColumn("No of "+cell_type_1+" around "+cell_type_2, counts_ref_marker);
		Table.setColumn(cell_type_1+"_id", cell_1_names);
		Table.setColumn("No of "+cell_type_2+" around "+cell_type_1, counts_marker_ref);
		Table.update;
		Table.save(table_path);
		close("Cell_counts_overlap_"+cell_type_1+"_"+cell_type_2);
		
		
			
		if(save_parametric_image==true)
				
		{

			overlap_cell_1=get_parameteric_img(no_neighbours_ref_marker,cell_2,cell_type_1,cell_type_2);
			overlap_cell_2=get_parameteric_img(no_neighbours_marker_ref,cell_1,cell_type_2,cell_type_1);

			selectWindow(overlap_cell_1);
			saveAs("Tiff", save_path+fs+overlap_cell_1);
			close();
			selectWindow(overlap_cell_2);
			saveAs("Tiff", save_path+fs+overlap_cell_2);
			close();
		}
		
		if (ganglia_binary!="NA") close(ganglia_binary);
		
	}
	print("Spatial analysis done for "+cell_type_1+" and "+cell_type_2);

}


//ref_img is hu and should label all cells
//marker_img should be a subset
function count_ref_around_marker(ref_img,marker_img,dilate_radius,ganglia_binary,cell_type_1,cell_type_2)
{
		// Init GPU
	
	run("Clear Results");
	run("CLIJ2 Macro Extensions", "cl_device=");
	//Ext.CLIJ2_clear();
	Ext.CLIJ2_push(ref_img);
	Ext.CLIJ2_push(marker_img);
	
	//dilate cells in ref_img
	Ext.CLIJ2_dilateLabels(ref_img, ref_dilate, dilate_radius);
	if (isOpen(ganglia_binary))
	{
		Ext.CLIJ2_push(ganglia_binary);
		Ext.CLIJ2_multiplyImages(ref_dilate, ganglia_binary, ref_dilate_ganglia_restrict);
		//get neighbour count for each cell in ref_img
		Ext.CLIJ2_touchingNeighborCountMap(ref_dilate_ganglia_restrict, ref_neighbour_count);
	}
	else 
	{
		Ext.CLIJ2_touchingNeighborCountMap(ref_dilate, ref_neighbour_count);
	}
	
	
	Ext.CLIJ2_reduceLabelsToCentroids(marker_img, marker_img_centroid);
	
	Ext.CLIJ2_statisticsOfBackgroundAndLabelledPixels(ref_neighbour_count, marker_img_centroid);

	selectWindow("Results");
	
	//get no of neighbours from max_intensity column
	no_neighbours = Table.getColumn("MINIMUM_INTENSITY");
	run("Clear Results");
	no_neighbours[0]=0;
	
	//Ext.CLIJ2_release(ref_img);
	//Ext.CLIJ2_release(marker_img);
	//Ext.CLIJ2_release(ref_dilate);
	Ext.CLIJ2_release(ganglia_binary);
	Ext.CLIJ2_release(ref_neighbour_count);
	Ext.CLIJ2_release(marker_img_centroid);
	
	return no_neighbours;
	
}


//how many cells in marker_img around ref
function count_marker_around_ref(ref_img,marker_img,dilate_radius,ganglia_binary)
{
		// Init GPU
	run("CLIJ2 Macro Extensions", "cl_device=");
	//Ext.CLIJ2_clear();
	Ext.CLIJ2_push(ref_img);
	Ext.CLIJ2_push(marker_img);
	
	//dilate cells in ref_img
	Ext.CLIJ2_dilateLabels(ref_img, ref_dilate_init, dilate_radius);
	
	
	if (isOpen(ganglia_binary))
	{
		Ext.CLIJ2_push(ganglia_binary);
		Ext.CLIJ2_multiplyImages(ref_dilate_init, ganglia_binary, ref_dilate);
		
		//get neighbour count for each cell in ref_img

	}
	else 
	{
		ref_dilate = ref_dilate_init;
	}
	
	
	//generate touch matrix where touching pixels that match label indices of touching cells are a value of 1
	// for example of cell 3 and 4 touch, then pixel at (3,4)  as a value of 1
	//Ext.CLIJ2_generateTouchMatrix(ref_dilate, touch_mat);
	
	run("Clear Results");
	
	//get value of marker corresponding to ref_img
	
	//Ext.CLIJ2_reduceLabelsToCentroids(marker_img, marker_img_centroid);
	
	Ext.CLIJ2_statisticsOfLabelledPixels(ref_dilate, marker_img);
	selectWindow("Results");
	
	marker_label_ref_ids = Table.getColumn("MAXIMUM_INTENSITY");
	
	run("Clear Results");
	
	setOption("ExpandableArrays", true);
	
	//get ids of all cells in ref_img
	Ext.CLIJ2_statisticsOfBackgroundAndLabelledPixels(ref_dilate, ref_dilate);
	selectWindow("Results");
	ref_ids = Table.getColumn("IDENTIFIER");
	
	run("Clear Results");
	Ext.CLIJ2_pull(ref_dilate);

	selectWindow(ref_dilate);
	run("Region Adjacency Graph", "  image="+ref_dilate);
	rag_table_name = ref_dilate+"-RAG";
	wait(5);
	selectWindow(rag_table_name);
	label_1 = Table.getColumn("Label 1");
	label_2 = Table.getColumn("Label 2");
	//array of zeros with length ref_ids
	counts = newArray(ref_ids.length);
	//Array+++.show(ref_ids);
	
	for (i = 0; i < label_1.length; i++) 
	{
		neighbour_marker = val_in_arr(marker_label_ref_ids,label_2[i]);
		if(neighbour_marker==1)
		{
			idx = label_1[i];
			counts[idx]+=1;
		}
	}
	
	
	for (i = 0; i < label_2.length; i++) 
	{
		neighbour_marker = val_in_arr(marker_label_ref_ids,label_1[i]);
		if(neighbour_marker==1)
		{
			idx = label_2[i];
			counts[idx]+=1;
		}
	}

	run("Clear Results");

	close(ref_dilate);
	selectWindow(rag_table_name);
	run("Close");
	//Ext.CLIJ2_release(ref_img);
	//Ext.CLIJ2_release(marker_img);
	//Ext.CLIJ2_release(ref_dilate_init);
	//Ext.CLIJ2_release(marker_img_centroid);
	
	return counts;
}




//check if val in array, if so return 1
function val_in_arr(arr,val)
	{
		
		for (i = 0; i < arr.length; i++) 
		{
			if (arr[i]==val)
			{ 
				//print(arr[i],val);
				return 1;
			}
		}
		return 0;
}

//generate parameteric image by replacing label values for each cell in "cell_label_img" with the value for the no of neighbours
function get_parameteric_img(no_neighbours,cell_label_img,cell_type_1,cell_type_2)
{
	//no of neighbours array with index 0 as background and cell label image
	
	
	run("CLIJ2 Macro Extensions", "cl_device=");
	Ext.CLIJ2_pushArray2D(vector_neighbours, no_neighbours, no_neighbours.length, 1);
	Ext.CLIJ2_replaceIntensities(cell_label_img, vector_neighbours, parametric_img);
	Ext.CLIJ2_pull(parametric_img);
	new_name = cell_type_1+"_around_"+cell_type_2;
	selectWindow(parametric_img);
	rename(new_name);
	run("Fire");
	return new_name;
			
}


//https://stackoverflow.com/questions/20800207/imagej-how-to-put-label-names-into-results-table-generated-by-roi-manager
//rename the label column in results table
function rename_roi_name_result_table()
{
	RoiManager.useNamesAsLabels(true);
	if(nResults==0) print("No rois in results table for spatial analysis");
	{
		for (i=0; i<nResults; i++) 
		{
	    oldLabel = getResultLabel(i);
	    delimiter = indexOf(oldLabel, ":");
	    newLabel = substring(oldLabel, delimiter+1);
	    setResult("Label", i, newLabel);
	  }
	}
}

//get roi label ids from results table
function get_roi_labels(roi_location,cell_image)
{
		roiManager("reset");
		roiManager("open", roi_location);
		selectWindow(cell_image);
		run("Set Measurements...", "centroid display redirect=None decimal=3");
		roiManager("Deselect");
		roiManager("Measure");
		rename_roi_name_result_table();
		selectWindow("Results");
		cell_names = Table.getColumn("Label");
		run("Clear Results");
		return cell_names;
}
