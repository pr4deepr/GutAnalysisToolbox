//Converts a ROIs to label map
//uses PT-BIOP plugin
// if a multichannel image, the label image also becomes multichannel
//we only keep the first channel which has the labels
//PTBIOP plugin can't handle multichannel images as of now; so create single channel image for roi to label conv

macro "roi_to_label_map"
{
		img=getTitle();
		selectWindow(img);
		if(bitDepth()==255) img_bit = "8-bit";
		else if(bitDepth()==65535) img_bit = "16-bit";
		else img_bit = "32-bit";
		getDimensions(width, height, channels, slices, frames);
		
		if(roiManager("count")==0) 
		{
			print("No ROIs");
			newImage("label_mapss", img_bit+" black", width, height, 1);
		}
		else 
		
		{
			
			//handle multichannel image input
			if(channels>1)
			{
				run("Select None");
				run("Duplicate...", "title=temp channels=1");
				selectWindow("temp");
				roiManager("show all");
				//to ensure overlays are there
				run("Show Overlay");
				run("ROIs to Label image");
				close("temp");
				
			}
			else 
			{
				roiManager("show all");
				//to ensure overlays are there
				run("Show Overlay");
				run("ROIs to Label image");
			}

			
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