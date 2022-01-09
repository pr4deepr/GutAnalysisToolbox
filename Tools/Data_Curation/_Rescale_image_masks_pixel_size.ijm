/*
 * goes through a folder of label images and rescales images to user-specified scaling factor
 */
#@ String(value="Resize the images in a folder to match user-specified pixel size (default=0.568 micron/px)", visibility="MESSAGE") hint
#@ File (label = "Directory for Training images", style = "directory") input_img
#@ File (label = "Directory for Mask images", style = "directory") input_mask
#@ File (label = "Output directory", style = "directory") output
#@ Double(label = "Enter desired pixel size (micron)",value=0.568, min=0.00, max=1.00, stepSize=0.05, style="slider") training_pixel_size
#@ String (label = "File suffix (extension)", value = ".tif") suffix
// double Scale_Factor

var fs = File.separator;

save_path_image=output+fs+"image"+fs;
save_path_mask=output+fs+"mask"+fs;

if(!File.exists(save_path_image)) File.makeDirectory(save_path_image);
if(!File.exists(save_path_mask)) File.makeDirectory(save_path_mask);


//scale_factor=Scale_Factor;


run("Clear Results");
processFolder(input_img,input_mask);
run("Set Measurements...", "area redirect=None decimal=3");

// function to scan files to find files with correct suffix
function processFolder(input_img,input_mask) {
	list = getFileList(input_img);
	list = Array.sort(list);

	for (i = 0; i < list.length; i++) 
	{
		if(endsWith(list[i], suffix))
			{
				print(list[i]);
				file=list[i];
			    processFile(input_img,input_mask,save_path_image,save_path_mask,file);
			    }
	}
}

function processFile(input_img,input_mask,save_path_image,save_path_mask,file) 
{
	//file=list[i];

	run("Clear Results");
	image=input_img + fs + file;
	print("Processing: " + image);
	open(image);
	curr_img=getTitle();
	run("Select None");
	run("Remove Overlay");
	//run("Set Scale...", "distance=0 known=0 unit=pixel");
	getDimensions(width, height, channels, slices, frames);
	getPixelSize(unit, pixelWidth, pixelHeight);
	print(width, height, channels, slices, frames);
	print(unit);
	//String.show(unit);
	if(toLowerCase(unit)=="pixel") //|| toLowerCase(unit)!="microns") 
	{exit("Image not calibrated");}
	image=input_mask + fs + file;
	open(image);
	curr_mask=getTitle();
	run("Select None");
	run("Remove Overlay");
	
	//training_pixel_size=0.378;
	scale_factor=pixelWidth/training_pixel_size;
	if(scale_factor<1.001 && scale_factor>1) scale_factor=1;
	
	
	new_width=round(width*scale_factor); 
	new_height=round(height*scale_factor);
	if(scale_factor!=1)
	{
		selectWindow(curr_img);
		run("Scale...", "x=- y=- width="+new_width+" height="+new_height+" interpolation=None create title=img_resize");
		close(curr_img);
		selectWindow("img_resize");
		curr_img=getTitle();

		selectWindow(curr_mask);
		run("Scale...", "x=- y=- width="+new_width+" height="+new_height+" interpolation=None create title=mask_resize");
		close(curr_mask);
		selectWindow("mask_resize");
		curr_mask=getTitle();
		

	}
		selectWindow(curr_img);
		saveAs("tif", save_path_image+file);

		selectWindow(curr_mask);
		saveAs("tif", save_path_mask+file);
		
	
	close("*");
}


exit("FINISHED");