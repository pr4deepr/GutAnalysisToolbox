var fs=File.separator;
setOption("ExpandableArrays", true);

print("\\Clear");
run("Clear Results");

var fiji_dir=getDirectory("imagej");
var gat_dir=fiji_dir+"scripts"+fs+"GAT"+fs+"Tools"+fs+"commands";


gat_settings_path=gat_dir+fs+"gat_settings.ijm";
if(!File.exists(gat_settings_path)) exit("Cannot find settings file. Check: "+gat_settings_path);



run("Results... ", "open="+gat_settings_path);
training_pixel_size=parseFloat(Table.get("Values", 0)); //0.7;
neuron_area_limit=parseFloat(Table.get("Values", 1)); //1500
neuron_seg_lower_limit=parseFloat(Table.get("Values", 2)); //90
neuron_lower_limit=parseFloat(Table.get("Values", 3)); //160
run("Close");


#@ File (style="open", label="Choose the image to segment") path
#@ boolean image_already_open
// String(choices={"Neuron", "Glia"}, style="list") cell_type
#@ File (style="open", label="<html>Choose the StarDist model file based on celltype.<html>",value=fiji_dir) model_file 
#@ String(value="Choose either XY pixel size (microns) or scaling factor (scales images by the specified factor)", visibility="MESSAGE") hint
#@ String(choices={"Use pixel size", "Use a scaling factor"}, style="radioButtonHorizontal",label="Choose mode of segmentation") choice_scaling
#@ String(value="Test a range of values for images to figure out the right one that gives accurate cell segmentation.", visibility="MESSAGE") hint2
#@ Double (label="Enter minimum value", value=1, min=0.0500, max=10.000) scale_factor_1
#@ Double (label="Enter maximum max value", value=2.000, min=0.0500, max=0.950) scale_factor_2
#@ Double (label="Enter increment step/s", value=0.2500) step_scale

//#@ boolean Modify_StarDist_Values (description="Tick to modify the values within the StarDist plugin or defaults will be used.")
#@ String(value="<html>Default Probability is 0.5 and Overlap threshold is 0.5. Leave it as default when first trying this.<br/>More info about below parameters can be found here: https://www.imagej.net/StarDist/<html>",visibility="MESSAGE", required=false) hint34
#@ Double (label="Probability (if staining is weak, use low values)", style="slider", min=0, max=1, stepSize=0.05,value=0.55) probability
#@ Double (label="Overlap Threshold", style="slider", min=0, max=1, stepSize=0.05,value=0.5) overlap

cell_type="Cell";
if(choice_scaling=="Use pixel size") Use_pixel_size=true;
else if(choice_scaling=="Use a scaling factor") Use_pixel_size=false;


print("\\Clear");

if(image_already_open==true)
{
	waitForUser("Select Image to segment (Image already open was selected)");//. Remember to choose output folder in next prompt");
	file_name=getTitle(); //get file name without extension (.lif)
	//dir=getDirectory("Choose Output Folder");
}
else
{
	if(endsWith(path, ".czi")|| endsWith(path, ".lif")) run("Bio-Formats", "open=["+path+"] color_mode=Composite rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT");
	else if (endsWith(path, ".tif")|| endsWith(path, ".tiff")) open(path);
	else exit("File type not recognised.  Tif, Lif and CZI files supported.");
	dir=File.directory;
	file_name=File.nameWithoutExtension; //get file name without extension (.lif)

}


//open(path);
run("Select None");
run("Remove Overlay");


print("**Neuron analysis: \nProbability: "+probability+"\nOverlap threshold: "+overlap);


//file_name=File.nameWithoutExtension; //get file name without extension (.lif)

series_stack=getTitle();
//series_stack=getTitle();

dotIndex = indexOf(series_stack, "." );
if(dotIndex>=0) file_name = substring(series_stack,0, dotIndex);
else file_name=series_stack;

Stack.getDimensions(width, height, sizeC, sizeZ, frames);
getPixelSize(unit, pixelWidth, pixelHeight);

neuron_seg_lower_limit=neuron_seg_lower_limit/pixelWidth; 
neuron_max_pixels=neuron_area_limit/pixelWidth; //convert micron to pixels


if(unit!="microns" && Use_pixel_size==true) exit("Image not calibrated in microns. Please go to ANalyse->SetScale or Image->Properties to set it for the image");



roiManager("reset");
if(sizeZ>1)
	{
	print(series_stack+" is a stack");
	roiManager("reset");
	waitForUser("Note the start and end of the stack.\nPress OK when done");
	Dialog.create("Choose slice range");
	Dialog.addNumber("Start slice", 1);
	Dialog.addNumber("End slice", sizeZ);
	Dialog.show(); 
	start=Dialog.getNumber();
	end=Dialog.getNumber();
	run("Z Project...", "start=&start stop=&end projection=[Max Intensity]");
	max_projection=getTitle();
}
else 
{
	print(series_stack+" has only one slice, assuming its max projection");
	max_projection=getTitle();
}



if(sizeC>1)
{
	waitForUser("Check image to select the right channel");
	channel_seg=getNumber("Enter channel number for "+cell_type, 1);
	selectWindow(max_projection);
	run("Select None");
	run("Remove Overlay");
	//run("Duplicate...", "title="+cell_type+" duplicate channels="+channel_seg);
	run("Duplicate...", "title="+cell_type+" duplicate channels="+channel_seg);
	img=getTitle();
}
else {
	selectWindow(max_projection);
	run("Select None");
	run("Remove Overlay");
	run("Duplicate...", "title="+cell_type);
	img=getTitle();
}

//replace file separator so  stardist can identify right file
model_file=replace(model_file, "\\\\","\\\\\\\\\\\\\\\\");

img_seg_array=newArray();
setOption("ExpandableArrays", true);
idx=0;
for(scale=scale_factor_1;scale<=scale_factor_2;scale+=step_scale)
{
	//print("Running segmentation on image scaled by: "+scale);
	roiManager("reset");
	selectWindow(img);
	if(Use_pixel_size == true) 
	{
		//Training images were pixelsize of ~0.378, so scaling images based on this
		scale_factor=pixelWidth/scale;
		if(scale_factor<1.001 && scale_factor>1) scale_factor=1;
		scale_name="Pixel_size";
	}
	else 
	{
		scale_factor=scale;
		scale_name="Scale_factor";
	}

	img_seg=scale_name+"_"+scale+"_"+cell_type;
	print("Running segmentation on image scaled by "+scale_name+" of: "+scale);
	if(scale_factor!=1)
	{	
		new_width=round(width*scale_factor); 
		new_height=round(height*scale_factor);
		//print(img_seg);
		run("Scale...", "x=- y=- width="+new_width+" height="+new_height+" interpolation=None create title="+img_seg);
	}
	else 
	{
		selectWindow(img);
		run("Select None");
		run("Duplicate...", "title="+img_seg);
	}
	//choice=0;
	selectWindow(img_seg);
	tiles=4;
	if(new_width>2000 || new_height>2000) tiles=8;
	if(new_width>5000 || new_height>5000) tiles=12;
	else if (new_width>9000 || new_height>5000) tiles=20;

	//run segmentation
	run("Command From Macro", "command=[de.csbdresden.stardist.StarDist2D],args=['input':'"+img_seg+"', 'modelChoice':'Model (.zip) from File', 'normalizeInput':'true', 'percentileBottom':'1.0', 'percentileTop':'99.8', 'probThresh':'"+probability+"', 'nmsThresh':'"+overlap+"', 'outputType':'Both', 'modelFile':'"+model_file+"', 'nTiles':'"+tiles+"', 'excludeBoundary':'2', 'roiPosition':'Automatic', 'verbose':'false', 'showCsbdeepProgress':'false', 'showProbAndDist':'false'], process=[false]");
	wait(50);

	//make sure cells are detected.. if not exit macro
	if(roiManager("count")==0) exit("No cells detected. Reduce probability or check image.\nAnalysis stopped");
	else roiManager("reset");

	label_image=getTitle();
	selectWindow(label_image);
	run("Remove Overlay");
	run("Label Size Filtering", "operation=Greater_Than_Or_Equal size="+neuron_seg_lower_limit);
	label_filtered=getTitle();
	close(label_image);
	
	//run("Remove Border Labels", "left right top bottom");
	//wait(10);
	//rename("Label-killBorders_"+scale);
	run("glasbey_on_dark");
	//run("LabelMap to ROI Manager (2D)");
	label_to_roi(label_filtered);
	wait(20);
	selectWindow(img_seg);
	run("From ROI Manager");
	//close(label_image);
	selectWindow(img_seg);
	img_seg_array[idx]=img_seg;
	idx+=1;
	print("No of objects: "+roiManager("count"));
	//close("Label-killBorders");
}

run("Cascade");
print("Verify the segmentation in the images: ");
//Array.print(img_seg_array);


function label_to_roi(label_image)
{
	roiManager("reset");
	//	label_image=getTitle();
	run("CLIJ2 Macro Extensions", "cl_device=");
	Ext.CLIJ2_push(label_image);
	//reindex the labels to make labels sequential
	Ext.CLIJ2_closeIndexGapsInLabelMap(label_image, reindex);
		//statistics of labelled pixels
	Ext.CLIJ2_statisticsOfLabelledPixels(reindex, reindex);
	Ext.CLIJ2_pull(label_image);
	Ext.CLIJ2_pull(reindex);
	Ext.CLIJ2_clear();


	//get centroid of each label
	selectWindow("Results");
	x=Table.getColumn("CENTROID_X");
	y=Table.getColumn("CENTROID_Y");

	//getting the identifiers as the values correspond to the label values
	identifier=Table.getColumn("IDENTIFIER");
	
	x1=Table.getColumn("BOUNDING_BOX_X");
	y1=Table.getColumn("BOUNDING_BOX_Y");
	x2=Table.getColumn("BOUNDING_BOX_END_X");
	y2=Table.getColumn("BOUNDING_BOX_END_Y");

	//use wand tool to create selection at each label centroid and add the selection to ROI manager
	//will not add it if there is no selection or if the background is somehow selected
	selectWindow(reindex);
	for(i=0;i<x.length;i++)
	{
		//use wand tool; quicker than the threshold and selection method
		doWand(x[i], y[i]);	
		intensity=getValue(x[i], y[i]);
		//if there is a selection and if intensity >0 (not background), add ROI
		if(selectionType()>0 && intensity>0) { roiManager("add"); }
		//if there is no intensity value at the centroid, its probably coz the object is not circular
		// and centroid is not in the object
		else{
			//get the width of the bounding box
			x_b=x2[i]-x1[i];
			//get the height of the bounding box
			y_b=y2[i]-y1[i];
			//get y coordinate
			//y_temp=y1[i];
			
			//parameters for  (Archimedean) spiral 
			pitch = 4;
			angle = 0; 
			r = 0; 
			a=0;
			//https://forum.image.sc/t/clij-label-map-to-roi-fast-version/51356/11
			//spiral search instead brute force search of every pixel
			while(r <= x_b/2 || r <= y_b/2) 
			{
			    r = sqrt(a)*pitch;
			    angle += atan(1/r);
			    x_spiral = (r)*cos(angle*pitch);
			    y_spiral = (r)*sin(angle*pitch);
			    intensity=getValue(x[i] + x_spiral, y[i] + y_spiral);
			    a++;

			    if(intensity>0 && intensity==identifier[i])
				{
					doWand(x[i] + x_spiral, y[i] + y_spiral);
					roiManager("add");
					print(r);
					print("AFTER");
					r = x_b+1000;
				}
			}
			if(r!=x_b+1000) print("search not successful for "+i);

		}
	}
	close("Results");
	close(reindex);
	close(label_image);
}
