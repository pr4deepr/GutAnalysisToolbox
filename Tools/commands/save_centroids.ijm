/*
 * gets centroids of rois and saves them with roinames
 require the save directory, save name and roizip file location
 * 
 */
var fs = File.separator;

macro "save_centroids"
{
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
		roi_location = arg_array[2];
		
		roiManager("open", roi_location);
		roiManager("deselect");
		
	}
	if(roiManager("count")==0) print("NO ROIS");
	run("Clear Results");
	run("Set Measurements...", "centroid display redirect=None decimal=3");
	roiManager("Deselect");
	roiManager("Measure");
	selectWindow("Results");
	rename_roi_name_result_table();
	save_dir=save_dir+fs+"spatial_analysis"+fs;
	if(!File.exists(save_dir)) File.makeDirectory(save_dir);
	
	Table.save(save_dir+fs+save_name+"_coordinates.csv");
	wait(5);
	run("Close");
	
	roiManager("reset");


}

//https://stackoverflow.com/questions/20800207/imagej-how-to-put-label-names-into-results-table-generated-by-roi-manager
//rename the label column in results table
function rename_roi_name_result_table()
{
	RoiManager.useNamesAsLabels(true);
	if(nResults==0) print("No rois in results table for spatial analysis");
	else
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