/*
 * Macro for annotating ganglia
 * Saves ROI for ganglia
 * Saves image as rgb (HU: magenta and ganglia: green) and mask as binary
 * Saves multichannel image for reference purposes
 * Option to crop the images 
 * Pradeep Rajasekhar
 */

#@ File (style="open", label="Select Multi-channel Image") orig_img_path
// String(label="Enter name of celltype", description="Neuron") celltype
#@ File (style="directory", label="Select Save Directory") save_img

//save folder for masks
save_img=save_img+File.separator;
close("*");


if(endsWith(orig_img_path, ".czi")|| endsWith(orig_img_path, ".lif")) run("Bio-Formats", "open=["+orig_img_path+"] color_mode=Composite rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT");
else { open(orig_img_path);}
stack=getTitle();
getPixelSize(unit, pixelWidth, pixelHeight);
print(unit, pixelWidth, pixelHeight);
file_name=File.getNameWithoutExtension(orig_img_path);

image_path=save_img+"images"+File.separator;
multi_ch_path=save_img+"multi_channel_images"+File.separator;
masks_path=save_img+"masks"+File.separator;
roi_save=save_img+"ROI_files"+File.separator;

if(!File.exists(image_path)) File.makeDirectory(image_path);
if(!File.exists(masks_path)) File.makeDirectory(masks_path);
if(!File.exists(roi_save)) File.makeDirectory(roi_save);
if(!File.exists(multi_ch_path)) File.makeDirectory(multi_ch_path);

print("Analysing "+file_name);
Stack.getDimensions(width, height, channels, slices, frames);
run("Remove Overlay");
run("Select None");



selectWindow(stack);
if(channels==1 && slices>1)
{
	channels=slices;
	slices=1;
	Stack.setDimensions(channels, slices, frames);
	run("Make Composite", "display=Composite");
}


//modify to account for slices and frames
if(channels==1)
{
	exit("Need multichannel image");
}

Stack.getDimensions(width, height, channels, slices, frames);

if(slices>1)
{
	run("Z Project...", "projection=[Max Intensity]");
	temp=getTitle();
	close(stack);
	selectWindow(temp);
	stack=getTitle();
}

waitForUser("Check Image and verify the channels for ganglia and Hu, and if you'd like to crop it");
Dialog.create("Channels");
Dialog.addNumber("Ganglia channel", 2);
Dialog.addNumber("Hu channel", 3);
Dialog.show();
ganglia_channel=Dialog.getNumber();
hu_channel=Dialog.getNumber();



selectWindow(stack);
run("Duplicate...", "title=Neuron duplicate channels=&hu_channel");
hu=getTitle();
run("Magenta");
run("Enhance Contrast", "saturated=0.35");

selectWindow(stack);
run("Duplicate...", "title=Ganglia duplicate channels=&ganglia_channel");
ganglia=getTitle();
run("Green");
resetMinAndMax();

run("Merge Channels...", "c1=&ganglia c2=&hu create");
rename("ganglia_multich");
ganglia_multich=getTitle();
run("RGB Color");
ganglia_rgb=getTitle();

roiManager("reset");
run("Select None");



crop=getBoolean("Do you want to crop the image?", "Crop", "Don't Crop");
if(crop==true)
{
	do
	{
		roiManager("reset");
		//image_2d is just Hu channel from OMERO tilescan
		selectWindow(ganglia_rgb);
		run("Specify...", "width="+width/2+" height="+width/2+" x=512 y=512 centered");
		waitForUser("Move square to area of interest to annotate. If resizing, press shift and drag corners of the box. This area will be cropped.");
		roiManager("Add");
	}
	while(selectionType()<0)


	selectWindow(ganglia_rgb);
	roiManager("select", 0);
	run("Crop");
	run("Remove Overlay");
	run("Select None");
	
	Stack.getDimensions(width, height, channels, slices, frames);
	
	selectWindow(ganglia_multich);
	roiManager("select", 0);
	run("Crop");
	//remove crop roi
	roiManager("reset");
	run("Remove Overlay");
	run("Select None");
}

setTool("freehand");

//run this till an ROI is added
do
{
	selectWindow(ganglia_multich);
	waitForUser("Manually draw regions around ganglia. Once you've finished drawing ROIs, add ROIs to ROI Manager by pressing T.\nUse + or - keys on your keyboard to zoom in or out\nWhen you are done, click OK.");
	roiManager("deselect");
	roiManager("show all without labels");
	
}
while(roiManager("count")==0)

nROIs=roiManager("count");
///if multiple ROI, make composite
if(nROIs>1)
{
	roi_array=newArray(nROIs); 
	for(i=0; i<nROIs;i++) roi_array[i] = i;
	roiManager("Select", roi_array); 
	roiManager("XOR");
	roiManager("Add");
	roiManager("Select", roi_array); 
	roiManager("delete");
}

newImage("ganglia_mask", "8-bit black",width, height, 1);
ganglia_mask=getTitle();
setForegroundColor(255,255,255);
roiManager("Select", 0);
roiManager("Fill");

roiManager("deselect");
roi_save=roi_save+"ganglia_ROI_"+file_name+".zip";
roiManager("save", roi_save);

selectWindow(ganglia_mask);
masks_path=masks_path+file_name;
saveAs("tiff", masks_path);

selectWindow(ganglia_rgb);
run("Select None");
run("Remove Overlay");
image_path=image_path+file_name;
saveAs("tiff", image_path);

selectWindow(ganglia_multich);
run("Select None");
run("Remove Overlay");
multi_ch_path=multi_ch_path+file_name;
saveAs("tiff", multi_ch_path);


roiManager("reset");

close("*");
