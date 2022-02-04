/*
 * Macro for annotating cells in a 512 x 512 crop area or user-defined if needed
 * Save ROI for cropping image  
 * Saves ROI for cells
 * Saves cropped image
 * Saves label image generated from ROIs
 * 
 * Pradeep Rajasekhar
 */

List.setCommands;
if (List.get("LabelMap to ROI Manager (2D)")=="") print("Enable update site for SCF MPI CBG in ImageJ updater");


#@ File (style="open", label="Select Image") orig_img_path
#@ String(label="Enter name of marker", description="Neuron", value = "Hu") celltype
#@ File (style="directory", label="Select Save Directory") save_img
#@ String(value="Enter the desired pixel size of the image you would like to save", visibility="MESSAGE") hint
#@ Double(value=0.568) training_pixel_size
#@ String(value="Enter the size of the final images you would like to save.", visibility="MESSAGE") hint2
#@ int(value=512) img_width
#@ int(value=512) img_height

//training_pixel_size=0.568;

//save folder for masks
save_img=save_img+File.separator;

close("*");

if(endsWith(orig_img_path, ".czi")|| endsWith(orig_img_path, ".lif")) run("Bio-Formats", "open=["+orig_img_path+"] color_mode=Composite rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT");
else { open(orig_img_path);}
stack=getTitle();
Stack.getDimensions(width, height, channels, slices, frames);
if(channels>1) Stack.setDisplayMode("color");

getPixelSize(unit, pixelWidth, pixelHeight);
print(unit, pixelWidth, pixelHeight);
if(unit!="microns")
{
	print("Image is not calibrated to microns. Enter pixel size");
	Dialog.create("Pixel calibration");
	Dialog.addNumber("Pixel Width", training_pixel_size);
	Dialog.addNumber("Pixel Height", training_pixel_size);
	Dialog.show();
	pixelWidth=Dialog.getNumber();
	pixelHeight=Dialog.getNumber();
	unit="micron";
	//print(pixelHeight,pixelWidth,unit);
	selectWindow(stack);
	setVoxelSize(pixelWidth, pixelHeight, 1, unit);
}


file_name=File.getNameWithoutExtension(orig_img_path);

roi_save=save_img+"ROI_files"+File.separator;
//create image and masks directory
image_path=save_img+"images"+File.separator;
masks_path=save_img+"masks"+File.separator;
if(!File.exists(image_path)) File.makeDirectory(image_path);
if(!File.exists(masks_path)) File.makeDirectory(masks_path);
if(!File.exists(roi_save)) File.makeDirectory(roi_save);

print("Analysing "+file_name);
Stack.getDimensions(width, height, channels, slices, frames);
run("Remove Overlay");
run("Select None");



if(slices>1)
{
		print(stack+" is a stack");
		roiManager("reset");
		waitForUser("Note the start and end of the stack.\nPress OK when done");
		Dialog.create("Choose slices");
  		Dialog.addNumber("Start slice", 1);
  		Dialog.addNumber("End slice", slices);
  		Dialog.show(); 
  		start=Dialog.getNumber();
  		end=Dialog.getNumber();
		run("Z Project...", "start=&start stop=&end projection=[Max Intensity]");
		temp=getTitle();
		close(stack);
		stack=temp;
}


selectWindow(stack);

if(channels>1)
{
	waitForUser("Multi-channel image detected. Verify the channels for "+celltype+" and enter the channel number in the next box");
	ch=getNumber("Enter channel for "+celltype, 3);
	run("Duplicate...", "title=&celltype duplicate channels=&ch");
  	image=getTitle();
}
else 
{
	image=stack;
}
roiManager("reset");
run("Select None");


//Training images were pixelsize of ~0.378, so scaling images based on this
scale_factor=pixelWidth/training_pixel_size;
if(scale_factor<1.001 && scale_factor>1) scale_factor=1;

//scale image if scaling factor is not equal to 1
if(scale_factor!=1)
{	
	selectWindow(image);
	new_width=round(width*scale_factor); 
	new_height=round(height*scale_factor);
	run("Scale...", "x=- y=- width="+new_width+" height="+new_height+" interpolation=None create title=img_resize");
	close(image);
	selectWindow("img_resize");
	image=getTitle();
	print("Width and height based on scaling are: "+new_width+" & "+new_height);

}


//run this till a selection is made
do
{
	roiManager("reset");
	//image_2d is just Hu channel from OMERO tilescan
	selectWindow(image);
	run("Specify...", "width="+img_width+" height="+img_height+" x=512 y=512 centered");
	waitForUser("Move square to area of interest to annotate. This area will be cropped.\n You can change the size or define a custom bounding box as well.\nCreate a square using the drawing tool if no overlay.");
	roiManager("Add");
}
while(selectionType()<0)

//save the ROI
roiManager("save", roi_save+"scaled_selection_ROI_"+file_name+".zip");

roiManager("select", 0);
run("Duplicate...", "title=cropped_img");
run("Remove Overlay");
run("Select None");
cropped_img=getTitle();

if(channels>1)
{
		selectWindow(stack);
		run("Remove Overlay");
		run("Select None");
		if(scale_factor!=1)
		{	
			run("Scale...", "x=- y=- width="+new_width+" height="+new_height+" interpolation=None create title=stack_resize");
			//stack_resize=getTitle();
		}
		wait(10);
		roiManager("select", 0);
		run("Duplicate...", "title=crop_stack duplicate");
		if(isOpen("stack_resize")) close("stack_resize");

}

roiManager("reset");


images_open=nImages;
setTool("freehand");
if(channels>1)
{
	selectWindow("crop_stack");
}
selectWindow(cropped_img);
setTool("freehand");
roiManager("show all");
waitForUser("Manually draw ROIs around each cell. Once you've finished drawing ROIs, add ROIs to ROI Manager by pressing T.\nUse + or - keys on your keyboard to zoom in or out\nWhen you are done, click OK.");
roiManager("deselect");


roi2label(cropped_img);
rename("label_map_"+file_name);
label_map=getTitle();

roiManager("deselect");
roi_save=roi_save+"ROIS_"+file_name+".zip";
roiManager("save", roi_save);

selectWindow(label_map);
run("Select None");
run("glasbey_on_dark");
file_name=generate_file_name(masks_path,celltype+"_"+file_name,".tif");
masks_path=masks_path+file_name;
saveAs("tiff", masks_path);

selectWindow(cropped_img);
run("Select None");
run("Remove Overlay");
image_path=image_path+file_name;
saveAs("tiff", image_path);

roiManager("reset");

close("*");


function roi2label(img)
{
	selectWindow(img);
	run("Select None");
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
	selectWindow(label_map);
}

//avoid duplicate filenames
function generate_file_name(path,name,ext)
{
	if(!File.exists(path+name+ext)) return name;
	else 
	{
	no=1;
		do 
		{
			new_name=name+"_"+no;
			print(new_name);
			no+=1;
		}
		while(File.exists(path+new_name+ext))
	}
	return new_name;
}
