//Converts a ROIs to label map
//uses PT-BIO plugin
// if a multichannel image, the label image also becomes multichannel
//we only keep the first channel which has the labels
macro "roi_to_label_map"
{
	
		roiManager("show all");
		run("ROIs to Label image");
		wait(5);
		temp = getTitle();
		selectWindow(temp);
		getDimensions(width, height, channels, slices, frames);
		if(channels>1)
		{
		run("Select None");
		run("Duplicate...", "title=label_mapss channels=1");
		close(temp);
		}
		else 
		{
			selectWindow(temp);
			rename("label_mapss");
		}
	resetMinAndMax();
	run("Select None");
}