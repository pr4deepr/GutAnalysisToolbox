//rename rois in ROIManager by adding a user-defined string to start of every roi and a sequential number
//Use runMacro("Directory where macro installed//cell_name_prefix.ijm","cell_name");

macro "rename_rois_cell_type"
{
		
	

	if(getArgument()=="")
	{
	waitForUser("This macro renames rois within ROIManager by adding a\nuser-defined string to start of every roi and a sequential number");
	
	cell_name_prefix = getString("Enter prefix", "Hu");


		
	}
	else 
	{
		args=getArgument();
		arg_array=split(args, ",");
		cell_name_prefix=arg_array[0];		

	}
	nrois=roiManager("count");
	roiManager("Remove Channel Info");
	roiManager("Remove Slice Info");
	roiManager("Remove Frame Info");
	if(nrois<1) 
	{
		print("No ROIs in detected in ROIManager. No renaming done");
	}
	else
	{
		for (roi = 0; roi < nrois; roi++)
		{
			roiManager("select", roi);
			roi_no = roi+1;
			roiManager("rename", cell_name_prefix+"_"+roi_no);
		}
	}
	roiManager("deselect");
}
