//Counts number of cells in different ganglia
//Ganglia is considered separate if they are not connected  together
//Converts binary image of ganglia to label image using connected components labelling
//Uses CLIJ overlap count to measure no of objects in each label image
//returns a table with name neuron_ganglia_count and no of cells in each ganglia

//Use runMacro("Directory where macro installed//Calculate_Neurons_per_Ganglia.ijm","name of cell label image,name of ganglia binary image");

macro "count_cells_per_ganglia"
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

	waitForUser("Select  image with ganglia as a binary image");
	ganglia_binary=getTitle();	
		
	}
	else 
	{
		args=getArgument();
		arg_array=split(args, ",");
		cell_img=arg_array[0];
		ganglia_binary=arg_array[1];		

	}


	
	selectWindow(ganglia_binary);
	getMinAndMax(min, max);
	if(max>255) exit("Ganglia image not a binary image");
	else if (max == 255)
	{
		selectWindow(ganglia_binary);
		run("Divide...", "value=255");
		setMinAndMax(0, 1);
	}
	
	//connected components labeling using morpholibj
	selectWindow(ganglia_binary);
	run("Connected Components Labeling", "connectivity=8 type=[16 bits]");
	wait(5);
	ganglia_labels=getTitle();

	run("CLIJ2 Macro Extensions", "cl_device=");
	Ext.CLIJ2_push(ganglia_labels);
	Ext.CLIJ2_push(cell_img);

	// Flood Fill Components Labeling
	//Ext.CLIJ2_connectedComponentsLabelingDiamond(ganglia_binary, ganglia_labels);
	//Ext.CLIJx_morphoLibJFloodFillComponentsLabeling(ganglia_binary, ganglia_labels);
	//Ext.CLIJ2_release(ganglia_binary);

	// Label Overlap Count Map
	Ext.CLIJ2_labelOverlapCountMap(ganglia_labels, cell_img, label_overlap);
	Ext.CLIJ2_release(cell_img);
	Ext.CLIJ2_release(ganglia_labels);
	//return a copy where ganglia are numbered sequentially; will use this as ganglia label map
	Ext.CLIJ2_closeIndexGapsInLabelMap(label_overlap, label_overlap_ordered);
	Ext.CLIJ2_pull(label_overlap_ordered);
	Ext.CLIJ2_pull(label_overlap);
	
	//convert labels to rois
	//Ext.CLIJ2_pullLabelsToROIManager(ganglia_labels);
	
	roiManager("reset");
	selectWindow(label_overlap);
	run("Select None");
	run("Label image to ROIs");
	
	run("Set Measurements...", "min redirect=None decimal=3");
	//each ganglia will have number of neurons calculated form labeloverlapcount command above
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
	if(isOpen(ganglia_labels)) close(ganglia_labels);
	if(isOpen(label_overlap)) close(label_overlap);
	
	selectWindow(label_overlap_ordered);
	rename("label_overlap");
	
}
