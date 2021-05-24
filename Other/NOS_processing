//NOS Processing macro
//Rescale to match pixel size of model trained images
//removed extension so that it doesn't get displayed in the menu in FIJI


macro "NOS_processing"
{
	args=getArgument();
	arg_array=split(args, ",");
	//nNOS image (one channeL)
	nos_image=arg_array[0];
	//pixel size by which the model was trained on. 
	//used 0.378 for processing parameters below
	pixel_size_micron=parseFloat(arg_array[1]);

	//nos_image=getArgument();
	selectWindow(nos_image);

	Stack.getDimensions(width, height, channels, slices, frames);
	getPixelSize(unit, pixelWidth, pixelHeight);
	
	//scaling is done so that the parameters in the median filtering, diff of gaussian and area opening remain the same
	//Training images were pixelsize of ~0.378, so scaling images based on this
	scale_factor=pixelWidth/pixel_size_micron; //pixelWidth/0.378
	if(scale_factor<1.001) scale_factor=1;
	//print(scale_factor);

	run("Select None");
	run("Remove Overlay");
	
	run("Scale...", "x="+scale_factor+" y="+scale_factor+" width=4012 height=3006 interpolation=None create title=nos");
	
	wait(5);
	nos="nos";
	
	run("Subtract Background...", "rolling=100");
	run("Enhance Local Contrast (CLAHE)", "blocksize=127 histogram=256 maximum=2 mask=*None* fast_(less_accurate)");
	
	
	// Init GPU
	run("CLIJ2 Macro Extensions", "cl_device=");
	Ext.CLIJ2_pushCurrentZStack(nos);
	
	// Median2D Sphere
	radiusX = 2.0;
	radiusY = 2.0;
	Ext.CLIJ2_median2DSphere(nos, median2d, radiusX, radiusY);
	Ext.CLIJ2_pull(nos);
	
	
	// Difference Of Gaussian2D
	sigma1x = 3.0;
	sigma1y = 3.0;
	sigma2x = 20.0;
	sigma2y = 20.0;
	Ext.CLIJ2_differenceOfGaussian2D(median2d, nos_diff_gauss, sigma1x, sigma1y, sigma2x, sigma2y);
	Ext.CLIJ2_release(median2d);
	Ext.CLIJ2_pull(nos_diff_gauss);
	Ext.CLIJ2_clear();

	close(nos);

//creates diff gauss image that can be used for thresholding
	
}

