//Converts a ROIs to label map
//image needs to be open
macro "roi_to_label_map"
{
	roi_count=roiManager("count");
	Stack.getDimensions(width, height, channels, slices, frames);
	
	newImage("label_mapss", "16-bit black", width, height, 1);
	
	label_map=getTitle();
	selectWindow(label_map);
	for (i = 0; i < roi_count; i++) 
	{
		roiManager("select", i);
		setColor(i+1);
		run("Fill");
	}
	setForegroundColor(255, 255, 255);
	resetMinAndMax();
	run("Select None");
}