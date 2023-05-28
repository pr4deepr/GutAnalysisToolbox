
//can also use this from runMacro command. 
//using PT-BIOP version as it works better and is faster
//Use runMacro("Directory where macro installed//Convert_Label_to_ROIs.ijm","image_name");
macro "label_map_to_roi"
{
	
	roiManager("reset");
	//check if argument is passed, if not asks user to select label image
	if(getArgument()=="")
	{
		waitForUser("Select Label Image");
		label_image=getTitle(); 
		
	}
	else 
	{
	label_image=getArgument(); 
	}
	
	selectWindow(label_image);
	run("Select None");
	run("Label image to ROIs");
	roiManager("Remove Channel Info");
	roiManager("Remove Slice Info");
	roiManager("Remove Frame Info");
	if(roiManager("count")==0) print("No labels or cells detected in image");
}

	
		
			
	//OLD CODE For label to ROI archiving it, but keeping for reference purposes
	//Converts a label image into ROIs
	//Needs CLIJ to work 
	//check this post: https://forum.image.sc/t/clij-label-map-to-roi-fast-version/51356
	//faster method for converting labelmap to ROI in FIJI
	//uses doWand function and CLIJ for this.
	//uses CLIJ to get the centroids and bounding boxes for each label
	//use doWand at centroid of the label. If a selection is made, add to ROI Manager
	//if not, it could be a non-circular object as centroids do not lie within the label
	//uses (Archimedean) spiral search within the bounding box to get a selection and then add to ROI Manager

	
function old_label_to_roi(label_img)
{
	//Checks if CLIJ is installed
	List.setCommands;
	clij_install=List.get("CLIJ2 Macro Extensions");
	if(clij_install=="") 
	{
		print("CLIJ installation link: Please install CLIJ using from: https://clij.github.io/clij2-docs/installationInFiji");
		exit("CLIJ not installed. Check Log for installation details")
		
	}
	
	run("CLIJ2 Macro Extensions", "cl_device=");
	Ext.CLIJ2_push(label_image);
	//reindex the labels to make labels sequential
	Ext.CLIJ2_closeIndexGapsInLabelMap(label_image, reindex);
		//statistics of labelled pixels
	Ext.CLIJ2_statisticsOfLabelledPixels(reindex, reindex);
	Ext.CLIJ2_release(label_image);
	Ext.CLIJ2_pull(reindex);
	Ext.CLIJ2_clear();


	//get centroid of each label
	selectWindow("Results");
	if(nResults!=0)
	{
		x=Table.getColumn("CENTROID_X");
		y=Table.getColumn("CENTROID_Y");
	
		//getting the identifiers as the values correspond to the label values
		identifier=Table.getColumn("IDENTIFIER");
		
		x1=Table.getColumn("BOUNDING_BOX_X");
		y1=Table.getColumn("BOUNDING_BOX_Y");
		x2=Table.getColumn("BOUNDING_BOX_END_X");
		y2=Table.getColumn("BOUNDING_BOX_END_Y");
	
		//use wand tool to create selection at each label centroid and add the selection to ROI manager
		//will not add it if there is no selection or if the background is somehow selected
		selectWindow(reindex);
		for(i=0;i<x.length;i++)
		{
			//use wand tool; quicker than the threshold and selection method
			doWand(x[i], y[i]);	
			intensity=getValue(x[i], y[i]);
			//if there is a selection and if intensity >0 (not background), add ROI
			if(selectionType()>0 && intensity>0) { roiManager("add"); }
			//if there is no intensity value at the centroid, its probably coz the object is not circular
			// and centroid is not in the object
			else{
				//get the width of the bounding box
				x_b=x2[i]-x1[i];
				//get the height of the bounding box
				y_b=y2[i]-y1[i];
				//get y coordinate
				//y_temp=y1[i];
				
				//parameters for  (Archimedean) spiral 
				pitch = 4;
				angle = 0; 
				r = 0; 
				a=0;
				//https://forum.image.sc/t/clij-label-map-to-roi-fast-version/51356/11
				//spiral search instead brute force search of every pixel
				while(r <= x_b/2 || r <= y_b/2) 
				{
				    r = sqrt(a)*pitch;
				    angle += atan(1/r);
				    x_spiral = (r)*cos(angle*pitch);
				    y_spiral = (r)*sin(angle*pitch);
				    intensity=getValue(x[i] + x_spiral, y[i] + y_spiral);
				    a++;
	
				    if(intensity>0 && intensity==identifier[i])
					{
						doWand(x[i] + x_spiral, y[i] + y_spiral);
						roiManager("add");
						r = x_b+1000; //escape "while" condition as the label has been found
					}
				}
				if(r!=x_b+1000) print("search not successful for "+i);  //not the most elegant way to do this
	
			}
		}
	}
	else 
	{
		print("No labels or cells detected in image");
	}
	close("Results");
	close(reindex);

}