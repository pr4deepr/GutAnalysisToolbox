//THIS IS DIFFERENT FROM Calculate_Neurons_per_Ganglia.ijm as it expects ganglia image to be label image
//Counts number of cells in different ganglia
//Uses ganglia label image 
//Uses CLIJ overlap count to measure no of objects in each label image
//returns a table with name neuron_ganglia_count and no of cells in each ganglia

//Use runMacro("Directory where macro installed//Calculate_Neurons_per_Ganglia_label.ijm","name of cell label image,name of ganglia label image");

macro "count_cells_per_ganglia_label"
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
	waitForUser("Select  image with cell labels");
	cell_img=getTitle();

	waitForUser("Select  image with ganglia as a label image");
	ganglia_label_img=getTitle();	
		
	}
	else 
	{
		args=getArgument();
		arg_array=split(args, ",");
		cell_img=arg_array[0];
		ganglia_label_img=arg_array[1];		

	}

	run("CLIJ2 Macro Extensions", "cl_device=");
	Ext.CLIJ2_push(ganglia_label_img);
	Ext.CLIJ2_push(cell_img);

	// Label Overlap Count Map
	Ext.CLIJ2_labelOverlapCountMap(ganglia_label_img, cell_img, label_overlap);
	Ext.CLIJ2_release(cell_img);
	Ext.CLIJ2_pull(ganglia_label_img);
	Ext.CLIJ2_pull(label_overlap);
	
	run("Set Measurements...", "min redirect=None decimal=3");
	//we get ganglia roi from ganglia label image
	selectWindow(ganglia_label_img);
	run("Select None");
	run("Label image to ROIs");
	
	//measure it from the label-overlap img
	selectWindow(label_overlap);
	
	if(roiManager("count")>0)
	{
		roiManager("Deselect");
		roiManager("Measure");
	}
	else exit("No ganglia detected");
	
	selectWindow("Results");
	//IJ.renameResults("Ganglia cell counts");
	cells_ganglia_count=Table.getColumn("Max");
	Array.show(cells_ganglia_count);
	
	selectWindow("cells_ganglia_count");
	Table.renameColumn("Value", "Cell counts");
	//Table.renameColumn(oldName, newName);
	selectWindow("Results");
	run("Close");
	//close(label_overlap);
	close(label_overlap);
}
