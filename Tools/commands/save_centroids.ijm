//gets centroids of rois and saves them
macro "save_centroids"
{
	fs = File.separator;
	
	//get fiji directory and get the macro folder for GAT
	var fiji_dir=getDirectory("imagej");
	var gat_dir=fiji_dir+"scripts"+fs+"GAT"+fs+"Tools"+fs+"commands";
	//check if label to roi macro is present
	var label_to_roi=gat_dir+fs+"Convert_Label_to_ROIs.ijm";
	if(!File.exists(label_to_roi)) exit("Cannot find label to roi script. Returning: "+label_to_roi);


	roiManager("reset");
	
	if(getArgument()=="")
	{
	
	waitForUser("Open ROI Manager");
	if(roiManager("count")==0) exit("NO ROIS");
	save_dir = getDir("Choose save directory");
	save_name = getString("Enter name of celltype", "Hu");
	
	}
	else 
	{
		args=getArgument();
		arg_array=split(args, ",");
		save_dir=arg_array[0];
		save_name=arg_array[1];
		label_img = arg_array[2];
		
		selectWindow(label_img);
		run("Select None");
		runMacro(label_to_roi,label_img);
		roiManager("deselect");
		
	}
	if(roiManager("count")==0) print("NO ROIS");
	run("Clear Results");
	run("Set Measurements...", "centroid redirect=None decimal=3");
	roiManager("Deselect");
	roiManager("Measure");
	selectWindow("Results");
	
	save_dir=save_dir+fs+"spatial_analysis"+fs;
	if(!File.exists(save_dir)) File.makeDirectory(save_dir);
	
	Table.save(save_dir+fs+save_name+"_coordinates.csv");
	wait(5);
	run("Close");


}