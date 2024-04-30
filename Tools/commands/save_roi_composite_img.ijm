//create composite image with rois overlaid using the rois and save in results folder under  composite_images
//Use runMacro("Directory where macro installed//save_roi_composite_img.ijm","multichannel_max_proj_img","results_dir","cell_type");

macro "save_roi_composite_img"
{
		
	var fs = File.separator;

	if(getArgument()=="")
	{
	waitForUser("This macro creates a composite image from multichannel image, overlays rois and saves them");
	waitForUser("Select image");
	max_proj = getTitle();
	save_dir = getDir("Choose directory to save composite image");
	cell_type = getString("Enter cell type name", "Hu");


		
	}
	else 
	{
		args=getArgument();
		arg_array=split(args, ",");
		max_proj = arg_array[0];
		save_dir = arg_array[1];
		cell_type = arg_array[2];

	}
	nrois=roiManager("count");
	if(nrois<1) 
	{
		print("No ROIs in detected in ROIManager. No overlay done");
	}
	else 
	{
		selectWindow(max_proj);
		getDimensions(width, height, channels, slices, frames);
		if(channels>1) Stack.setDisplayMode("composite");
		run("Select None");
		roiManager("Show All");
		run("Flatten");
		flattened=getTitle();
		selectWindow(flattened);
		save_file_name = cell_type+"_roi_overlay_composite.jpg";
		save_dir = save_dir+fs+"segmentation_preview";
		if(!File.exists(save_dir)) File.makeDirectory(save_dir);
		save_path = save_dir+fs+save_file_name;
		saveAs("Jpeg", save_path);
		close();
		
	}
	selectWindow(max_proj);
	roiManager("show none");
	
	roiManager("deselect");
	

}
