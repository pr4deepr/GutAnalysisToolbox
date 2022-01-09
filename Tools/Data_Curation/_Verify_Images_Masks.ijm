/*
 * Macro to go through image and mask folder. Verify each ROI by deleting, adding ROIs
 * Convert it back into a labelmap; 
 * save the iamge and modified mask separately into a new image and mask folder
 */
var fs = File.separator;
#@ String(value="<html><b>Validate Images and Masks.</b><br>Go through images and corresponding masks to verify annotation accuracy<br>Can also specify the pixel size or scaling factor if you need to rescale images<html>", visibility="MESSAGE") hint3
#@ File (label = "Choose Image", style = "open") input
#@ File (label = "Choose mask/label image (.tif) or ROI Manager file (.zip)", style = "open") output
#@ File (label = "Save folder", style = "directory") save_folder
#@ String (label = "Name of marker", value = "Hu") celltype_name
#@ String(value="Choose either pixel size for scaling OR enter a scaling factor", visibility="MESSAGE") hint2
#@ boolean Use_Scaling
#@ String(choices={"Pixel size", "Scaling Factor"}, style="radioButtonHorizontal") scale_choice
#@ Double(label = "Enter desired pixel size (micron)",value=0.568, min=0.00, max=10.00, style="spinner") scaling_factor
// Double(label = "Enter desired scaling factor",value=2, min=0, max=10, style="spinner") manual_scaling_factor




if(Use_Scaling!=true)
{
	scale_choice=0;
	scaling_factor=0;
}

save_path_image=save_folder+fs+"images"+fs;

save_path_mask=save_folder+fs+"masks"+fs;

if(!File.exists(save_path_image)) File.makeDirectory(save_path_image);
if(!File.exists(save_path_mask)) File.makeDirectory(save_path_mask);
 
 processFile(input, output, save_path_image, save_path_mask,Use_Scaling,scale_choice,scaling_factor);

run("Clear Results");
run("Set Measurements...", "area redirect=None decimal=3");
exit("Finished");


function processFile(input, output, save_path_image, save_path_mask,Use_Scaling,scale_choice,scaling_factor)
{
	// Do the processing here by adding your own code.
	// Leave the print statements until things work, then remove them.
	print("Processing: " + input);
	open(input);
	roiManager("reset");
	
	orig_stack=getTitle();
	
	//ref_id=getImageID();
	run("Remove Overlay");
	run("Select None");
	print("Opening matching mask/ROI " + output);
	Stack.getDimensions(width, height, channels, slices, frames);
	if(slices>1)
	{
		run("Z Project...", "projection=[Max Intensity]");
		stack=getTitle();
		close(orig_stack);
	}
	else 
	{
		stack=orig_stack;
	}
	end_idx=lengthOf(output);
	//taking last 10 characters of file name and getting dotindex from there
	output_substring=substring(output, end_idx-10, end_idx);
	dotIndex = indexOf(output_substring, "." );
	end_idx=lengthOf(output_substring);
	extension = substring(output_substring, dotIndex, end_idx);
	print("Extension: "+extension);
	//exit;
	file_name=File.nameWithoutExtension;
	if(extension==".zip")
	{
		print("Detected ROI manager file as extension is "+extension);
		roiManager("open", output);
		//run("ROI Manager to LabelMap(2D)");
		//wait(5);
		//run("glasbey_on_dark");
		
	}
	else if(extension==".tif" || extension==".tiff")
	{
		print("Detected Image mask file as extension is "+extension);
		open(output);
		wait(5);
		mask=getImageID();
		selectImage(mask);
		run("Select None");
		run("Remove Overlay");
		run("Remap Labels");
		resetMinAndMax;	
		run("LabelMap to ROI Manager (2D)");
		wait(10);
		roiManager("deselect");
	}
	else exit("Unrecognised extension for output file");
	roiManager("show none");
	
	if(channels>1)
	{
		waitForUser("Multi-channel image detected. Verify the channels and enter the channel number of celltype in the next box");
		ch=getNumber("Enter channel", 3);
		run("Duplicate...", "title=celltype duplicate channels=&ch");
	  	ref=getTitle();
	  	if(slices>1)
	  	{
	  		
	  	}
	}
	else 
	{
		ref=stack;
	}
	
	no_rois=roiManager("count");
	print("No of ROIs before verification: "+no_rois);
	selectWindow(ref);
	run("Grays");
	run("Invert LUT");
	setTool("freehand");
	if(no_rois>0)
	{	
	//run("From ROI Manager");
	roiManager("Show All");
	waitForUser("Verify ROIs. Delete ROIs or draw new ones using the drawing tool and press T to add");
	selectWindow(ref);
	run("Remove Overlay");
	no_rois=roiManager("count");
	print("No of ROIs after verification: "+no_rois);	

	//selectImage(mask);
	//run("Select All");
	//setBackgroundColor(0, 0, 0);
	//run("Clear", "slice");
	run("ROI Manager to LabelMap(2D)");	
	wait(10);
	mask_processed=getTitle();
	}
	else
	{
		print("No cells");
		selectImage(mask);
		mask_processed=getTitle();
	}

	selectWindow(ref);
	run("Invert LUT");
	run("Select None");
	run("Remove Overlay");
	//run("Set Scale...", "distance=0 known=0 unit=pixel");
	getDimensions(width, height, channels, slices, frames);
	getPixelSize(unit, pixelWidth, pixelHeight);
	print(width, height, channels, slices, frames);
	print(unit);
	String.show(unit);
	if(toLowerCase(unit)=="pixel") //|| toLowerCase(unit)!="microns") 
	{exit("Image not calibrated");}	

	
	if(Use_Scaling==true)
	{
		if(scale_choice=="Pixel size")
		{
			scale_factor=pixelWidth/scaling_factor;
		}
		else if(scale_choice=="Scaling Factor")
		{
			scale_factor=scaling_factor;
		}
	}
	else 
	{
		scale_factor=1;//if no scaling selected
	}

	//if scaling factor between 1 and 1.001, round it to 1
	if(scale_factor<1.001 && scale_factor>1) scale_factor=1;
	
	
	new_width=round(width*scale_factor); 
	new_height=round(height*scale_factor);
	if(scale_factor!=1)
	{
		selectWindow(ref);
		run("Scale...", "x=- y=- width="+new_width+" height="+new_height+" interpolation=None create title=img_resize");
		close(ref);
		selectWindow("img_resize");
		ref=getTitle();

		selectWindow(mask_processed);
		run("Scale...", "x=- y=- width="+new_width+" height="+new_height+" interpolation=None create title=mask_resize");
		close(mask_processed);
		selectWindow("mask_resize");
		mask_processed=getTitle();
		

	}

	file_name=celltype_name+"_"+file_name;
	//run("From ROI Manager");
	print("Saving images");	
	//save_path=save_folder+File.separator+file;
	selectWindow(ref);
	saveAs("Tiff",save_path_image+file_name);
	selectImage(mask_processed);
	run("glasbey_on_dark");
	saveAs("Tiff",save_path_mask+file_name);
	close("*");
	
}
