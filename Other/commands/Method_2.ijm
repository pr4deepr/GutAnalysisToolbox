//Method 2 for processing markers
//no background subtraction
//Rescale to match pixel size of model trained images
//removed extension so that it doesn't get displayed in the menu in FIJI

//call this by running the macro with arguments: marker_image name, hu_image name and training pixel size

macro "method_2_processing"
{
	print("Running Method 2a");
	args=getArgument();
	arg_array=split(args, ",");
	//nNOS image (one channeL)
	marker=arg_array[0];
	hu_image=arg_array[1];
	//pixel size by which the model was trained on. 
	//used 0.378 for processing parameters below
	pixel_size_micron=parseFloat(arg_array[2]);

	//nos_image=getArgument();
	selectWindow(hu_image);
	Stack.getDimensions(width, height, channels, slices, frames);
	getPixelSize(unit, pixelWidth, pixelHeight);
	
	//scaling is done so that that any processing stays consistent with cell size
	//Training images were pixelsize of ~0.378, so scaling images based on this
	scale_factor=pixelWidth/pixel_size_micron; //pixelWidth/0.378
	if(scale_factor<1.001 && scale_factor>1) scale_factor=1;
	print(scale_factor);

	run("Select None");
	run("Remove Overlay");
	selectWindow(marker);	
	run("Scale...", "x="+scale_factor+" y="+scale_factor+" width=4012 height=3006 interpolation=None create title=nos_scale");
	wait(5);
	marker_image=getTitle();
	selectWindow(marker_image);
	run("Select None");
	run("Remove Overlay");
	run("Median...", "radius=1");
	//run("Subtract Background...", "rolling=200 sliding"); //not subtracting background for method 2
	run("Enhance Local Contrast (CLAHE)", "blocksize=127 histogram=256 maximum=3 mask=*None*");
	//run("Enhance Contrast...", "saturated=2 normalize");

	
	selectWindow(hu_image);	
	run("Scale...", "x="+scale_factor+" y="+scale_factor+" width=1000 height=1000 interpolation=None create title=hu_scale");
	hu=getTitle();
	selectWindow(hu);
	run("Select None");
	run("Remove Overlay");
	run("Median...", "radius=1");
	//run("Enhance Local Contrast (CLAHE)", "blocksize=127 histogram=256 maximum=3 mask=*None*");
	run("Subtract Background...", "rolling=200 sliding");
	//run("Enhance Contrast...", "saturated=2 normalize");
	
	run("Image CorrelationJ 1o", "target="+hu+" source="+marker_image+" correlation=Target-Source statistic=Average markers=Circle correlation_0 local=3 decimal=2");
	selectWindow("Correlation Maps");
	run("Scale...", "x=- y=- width="+width+" height="+height+" interpolation=None create title=hu_"+marker_image+"_correlation");
	resetMinAndMax;
	
	close("Correlation Maps");
	close(hu);
	close(marker_image);
}	


