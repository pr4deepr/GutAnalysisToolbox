//Converts a ROIs to label map
//uses PT-BIO plugin
// if a multichannel image, the label image also becomes multichannel
//we only keep the first channel which has the labels
macro "roi_to_label_map"
{
		img=getTitle();
		selectWindow(img);
		getDimensions(width, height, channels, slices, frames);
		
		if(roiManager("count")==0) 
		{
			print("No ROIs");
			newImage("label_mapss", "8-bit black", width, height, 1);
		}
		else 
		{
			
			roiManager("show all");
			//to ensure overlays are there
			run("Show Overlay");
			
			run("ROIs to Label image");
			wait(10);
			temp = getTitle();
			if(temp==img) {exit("ROI conversion didn't work; error with plugin");}
			else 
			{
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
			}
		}
	resetMinAndMax();
	run("Select None");
}