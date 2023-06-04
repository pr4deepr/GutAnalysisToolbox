/*
 * calculate number of neighbours for two markers; 
 * code is meant for markers other than Hu, i.e., NOT pan-neuronal markers
 * it uses labeloverlapcountmap method from CLIJ. 
 * 
 */

//Use runMacro("Directory where macro installed//spatial_single_celltype.ijm","name of marker 1, marker1_image_name,
// ganglia_binary_img_name,save_path,label_dilation value, save_parametric_image flag (1 or 0"),pixelWidth);

//if no ganglia_binary, enter "NA" 

var fs=File.separator;
setOption("ExpandableArrays", true);


var fiji_dir=getDirectory("imagej");
var gat_dir=fiji_dir+"scripts"+fs+"GAT"+fs+"Tools"+fs+"commands";



macro "spatial_two_celltype"
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
		
		cell_type_2 = arg_array[2].trim();
		cell_2 = arg_array[3].trim();
		//roi_path_2 = arg_array[5].trim();
		
		ganglia_binary_orig = arg_array[4].trim();
	
		save_path = arg_array[5].trim();
		
		label_dilation = parseFloat(arg_array[6].trim());
		
		save_parametric_image = parseFloat(arg_array[7].trim());
		
		pixelWidth = parseFloat(arg_array[8].trim());

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


		// cell 2 neigbour around cell 1
		no_neighbours_cell_2_around_1=count_neighbour_around_ref_img(cell_1,cell_2,label_dilation,ganglia_binary);
		counts_cell_2_around_1 = Array.deleteIndex(no_neighbours_cell_2_around_1, 0);
		
		// cell 1 neigbour around cell 2
		no_neighbours_cell_1_around_2=count_neighbour_around_ref_img(cell_2,cell_1,label_dilation,ganglia_binary);
		counts_cell_1_around_2 = Array.deleteIndex(no_neighbours_cell_1_around_2, 0);

		roiManager("reset");
		run("Clear Results");
		table_name = "Neighbour_count_"+cell_type_1+"_"+cell_type_2;
		table_path=save_path+fs+table_name+".csv";
		
		
		Table.create("Cell_counts_overlap_"+cell_type_1+"_"+cell_type_2);
		Table.setColumn("No of "+cell_type_2+" around "+cell_type_1, counts_cell_2_around_1);
		Table.setColumn("No of "+cell_type_1+" around "+cell_type_2, counts_cell_1_around_2);
		Table.update;
		Table.save(table_path);
		close("Cell_counts_overlap_"+cell_type_1+"_"+cell_type_2);
		

		if(save_parametric_image==true)
				
		{

			overlap_1=get_parameteric_img(no_neighbours_cell_2_around_1,cell_1,cell_type_2,cell_type_1);
			overlap_2=get_parameteric_img(no_neighbours_cell_1_around_2,cell_2,cell_type_1,cell_type_2);

			selectWindow(overlap_1);
			saveAs("Tiff", save_path+fs+overlap_1);
			close();
			selectWindow(overlap_2);
			saveAs("Tiff", save_path+fs+overlap_2);
			close();
		}
		
		if (ganglia_binary!="NA") close(ganglia_binary);
		
	}
	print("Spatial analysis done for "+cell_type_1+" and "+cell_type_2);

}		



function count_neighbour_around_ref_img(ref_img,marker_img,dilate_radius,ganglia_binary)
{
	run("Clear Results");
	run("CLIJ2 Macro Extensions", "cl_device=");

	Ext.CLIJ2_push(ref_img);
	Ext.CLIJ2_push(marker_img);
	
	Ext.CLIJ2_dilateLabels(ref_img, ref_dilate, dilate_radius);
	
	if (isOpen(ganglia_binary))
	{
		Ext.CLIJ2_push(ganglia_binary);
		Ext.CLIJ2_multiplyImages(ref_dilate, ganglia_binary, ref_dilate_ganglia_restrict);
		// Label Overlap Count Map
		Ext.CLIJ2_labelOverlapCountMap(ref_dilate_ganglia_restrict, marker_img, label_overlap_count);
		Ext.CLIJ2_reduceLabelsToCentroids(ref_dilate_ganglia_restrict, ref_img_centroid);
	
	}
	else 
	{
		Ext.CLIJ2_labelOverlapCountMap(ref_dilate, marker_img, label_overlap_count);
		Ext.CLIJ2_reduceLabelsToCentroids(ref_dilate, ref_img_centroid);
		
	}

	
	// Label Overlap Count Map
	//Ext.CLIJ2_labelOverlapCountMap(ref_dilate, marker_img, label_overlap_count);
	
	// Greater Or Equal Constant
	constant = 1.0;
	
	Ext.CLIJ2_greaterOrEqualConstant(marker_img, marker_img_binary, constant);
	Ext.CLIJ2_greaterOrEqualConstant(ref_img, ref_img_binary, constant);
	
	// Subtract Images
	Ext.CLIJ2_subtractImages(label_overlap_count, marker_img_binary, label_overlap_count_corrected);
	Ext.CLIJ2_statisticsOfBackgroundAndLabelledPixels(label_overlap_count_corrected, ref_img_centroid);
	//Ext.CLIJ2_pull(label_overlap_count);
	//Ext.CLIJ2_pull(label_overlap_count_corrected);
	
	//get intensity at centroid/  min intensity
	overlap_count = Table.getColumn("MINIMUM_INTENSITY");
	//background is zero
	overlap_count[0]=0;

	return overlap_count;	
	
		
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
