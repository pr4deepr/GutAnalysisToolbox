//Macro for calcium imaging analysis
//can be used for any tissue or even cells. 
//Option for normalising to baseline depending on user-specified baseline frames

//TODO: Add option to save cell coordinates and get ganglia outline



var fs=File.separator;
setOption("ExpandableArrays", true);

print("\\Clear");

var fiji_dir=getDirectory("imagej");
var gat_dir=fiji_dir+"scripts"+fs+"GAT"+fs+"Tools"+fs+"commands";

//specify directory where StarDist models are stored
var models_dir=fiji_dir+"models"+fs;


//function for normalising stack to F0 and return a 32 bit stack
function f_f0(calcium_stack_name_orig) 
{
	selectWindow(calcium_stack_name_orig);
	waitForUser("Waiting", "Decide on frames to use for baseline. Click OK after you are done");
	selectWindow(calcium_stack_name_orig);
	Dialog.create("Calculate F/F0");
	Dialog.addNumber("Starting frame:", 1);
	Dialog.addNumber("End frame:", 50);
	Dialog.show();
	start=Dialog.getNumber();
	end=Dialog.getNumber();
	run("Z Project...", "start="+start+" stop="+end+" projection=[Average Intensity]");
	f0=getTitle();
	imageCalculator("Divide create 32-bit stack", calcium_stack_name_orig, f0);
	calcium_f0=getTitle(); //32 bit image which can be used for extracting intensity values
	run("mpl-viridis"); //Use Viridis LUT
	run("Enhance Contrast", "saturated=0.35"); //enhance contrast of stack
	return calcium_f0;
}


//function for max projection; adding a function for this as the normal Zproject dialog defaults to the last projection
function fmax(image) 
{
	selectWindow(image);
	Dialog.create("Max Projection");
	Dialog.addNumber("Starting frame:", 1);
	Dialog.addNumber("End frame:", 50);
	Dialog.show();
	start=Dialog.getNumber();
	end=Dialog.getNumber();
	run("Z Project...", "start="+start+" stop="+end+" projection=[Max Intensity]");
}

print ("\\Clear");

//open calcium imaging stack
#@ String(value="<html> <b> Calcium Imaging Analysis<b> </html>", visibility="MESSAGE") hint
#@ File (style="open", label="Open the aligned calcium imaging stack") calcium_path
#@ String(value="<html>F_F0 is the stack normalised to baseline.<br/> If chosen, the mean intensity values extracted <br/>will be normalised to user-specified baseline frames<br/> (before activity or adding any drugs)</html>", visibility="MESSAGE") as
#@ Boolean (value=true, persist=false) Use_F_F0
#@ Boolean (value=false, persist=false,label="Use Stardist model for segmenting neurons") use_stardist
#@ String(value="<html>If you are not sure, leave it checked</html>", visibility="MESSAGE") hint2

scale=1;
probability=0.5;
overlap=0.3;
n_tiles=4;

open(calcium_path);//File.openDialog("Open the aligned calcium imaging file or Max Stack"));
calcium_stack_name_orig=File.name;
file_dir=File.directory;


getDimensions(w, h, channels, slices, frames); //get dimensions so the resolution and frames can be used later on
getPixelSize(unit, pixelWidth, pixelHeight);

//check if unit is microns or micron
unit=String.trim(unit);

if(unit!="microns" && unit!="micron" && unit!="um" && use_stardist==true )
{
	print("Image is not calibrated in microns. This is required for accurate segmentation using stardist");
	waitForUser("Image is not calibrated in microns. This is required for accurate segmentation using stardist");
	pixelWidth = getNumber("Enter pixelsize in microns", 0.568);
}

if(slices>10 && frames==1) //occasionally frames and slices could be interchanged; fixing that
{
	frames=slices;
	slices=1;
	Stack.setDimensions(channels, slices, frames);
}
results_path=file_dir+"RESULTS"+File.separator;
if (!File.exists(results_path)) //if directory doesnt exist make one
	{
	File.makeDirectory(results_path);
	}
//get name of file without .tif extension
dotIndex = indexOf(calcium_stack_name_orig, ".tif"); //get index of string where it starts with ".tif"
folder_name = substring(calcium_stack_name_orig, 0, dotIndex); //get substring from zero index (start) till the dot
output=results_path+folder_name+File.separator;
print("Processing: "+folder_name);
//Create Results directory if it does not exist
if (!File.exists(output))
{
	File.makeDirectory(output);
}

//check if tiff image is greater than 10 frames or slices (if its a time series image)
//If so, get max stack image and also get image F/F0
if (frames>10||slices>10)
{
	Stack.setSlice(slices);
	run("Delete Slice");
	slices-=1;
	waitForUser("Waiting", "Delete any drug addition artefacts or blank frames. Also, decide on the frames to use for max_stack. Click OK after you are done");
	fmax(calcium_stack_name_orig); //Function for maximum projection
	//max stack
	max=getImageID();
	selectImage(max);
	run("RGB Color"); //max calcium as a RGB image
	saveAs("Tiff",output+"MAX_"+calcium_stack_name_orig);
	calcium_max=getTitle();
	
}
else
{
	exit("ERROR: The image is less than 10 frames/slices. Please choose the right stack");
}

if(Use_F_F0==true) {calcium_normalise=f_f0(calcium_stack_name_orig);} //calculate f_f0 of stack and return a 32 bit 
else {calcium_normalise=calcium_stack_name_orig;}

//set selection tool to oval for drawing ROIs
setTool("oval");
selectWindow(calcium_stack_name_orig);
selectWindow(calcium_normalise);
roiManager("reset");
cell_types=getNumber("Enter the number of celltypes that will be analysed", 1);
roi_start=0;

for(i=0;i<cell_types;i++)
{
	cell_count=0;
	roi_file=getBoolean("Do you have an ROI Manager file?", "Yes","No, I'll draw manually");
	if(roi_file==true)
	{
		roi_file_path=File.openDialog("Choose ROI Manager file");
		roiManager("open", roi_file_path);
		selectWindow(calcium_max);
		waitForUser("Verify ROIs. Press OK when done.");
		cell_name=getString("Enter name of celltype", "Neuron");
	}
	else
	{
		cell_name=getString("Enter name of celltype", "LEC");
		selectWindow(calcium_max);
		if(use_stardist)
		{
			Dialog.create("Choose model type");
			Dialog.addChoice("Choose StarDist model", newArray("GAT Model","Stardist_Barth"));
			Dialog.addNumber("Rescaling Factor", scale, 3, 8, "") 
		  	Dialog.addSlider("Probability of detecting neurons (Hu)", 0, 1,probability);	
		  	Dialog.addSlider("Overlap threshold", 0, 1,overlap);
		  	Dialog.addNumber("Area of smallest cell to filter", 10);
		  	//add checkbox to same row as slider
		  	Dialog.addToSameRow();
		  	Dialog.addCheckbox("Custom ROI", 0);
			Dialog.show(); 
			
			choice=Dialog.getChoice();
			scale = Dialog.getNumber();
			probability= Dialog.getNumber();
			overlap= Dialog.getNumber();
			neuron_seg_lower_limit = Dialog.getNumber(); 
			if(choice=="GAT Model")
			{
				training_pixel_size=0.568;
				model_file = models_dir+fs+"2D_enteric_neuron_v4_1.zip";
				
			}
			else 
			{
				training_pixel_size=0.568;
				model_file = models_dir+fs+"Barth_2D_StarDist.zip";
			}
			
			target_pixel_size= training_pixel_size/scale;
			scale_factor = pixelWidth/target_pixel_size;
			
			segment_neuron_stardist(calcium_max,model_file,n_tiles,w,h,scale_factor,neuron_seg_lower_limit,probability,overlap)

		}
		waitForUser("Draw ROIs for "+cell_name+". Press OK when done");
	}
	roiManager("deselect");
	for(cell=roi_start;cell<roiManager("count");cell++)
	{
		cell_count+=1;
		roiManager("Select", cell);
		roiManager("Rename", cell_name+"_"+(cell_count));
	}
	roi_start=roiManager("count");
}


roiManager("deselect");
selectWindow(calcium_normalise);
run("Set Measurements...", "mean redirect=None decimal=2");
roiManager("Multi Measure");
wait(50);
//Set the results save file type to .csv
run("Input/Output...", "jpeg=85 gif=-1 file=.csv use_file copy_row save_column save_row");
saveAs("Results",output+"RESULTS_"+folder_name+".csv");
selectWindow("Results");
run("Close");

//if user wants to use F_F0, save normalised stack
if(Use_F_F0==true)
{
	selectWindow(calcium_normalise);
	run("Select None");
	roiManager("deselect");
	print("Saving F_F0 stack");
	saveAs("Tiff",output+"F_F0_"+calcium_stack_name_orig);
	wait(10);
	run("Close");
}

print("Saving ROI");
roiManager("deselect");
roiManager("save", output+"ROIS_"+calcium_stack_name_orig+"_CELLS.zip");

run("Close All");

function segment_neuron_stardist(img_seg,model_file,n_tiles,width,height,scale_factor,neuron_seg_lower_limit,probability,overlap)

{
	//need to have the file separator as \\\\ in the file path when passing to StarDist Command from Macro. 
	//regex uses \ as an escape character, so \\ gives one backslash \, \\\\ gives \\.
	//Windows file separator \ is actually \\ as one backslash is an escape character
	//StarDist command takes the escape character as well, so pass 16 backlash to get 4xbackslash in the StarDIst macro command (which is then converted into 2)
	model_file=replace(model_file, "\\\\","\\\\\\\\\\\\\\\\");
	choice=0;
	roiManager("reset");
	selectWindow(img_seg);
	wait(10);

	//runMacro(gat_dir+fs+"gat_stardist_batch.py",arg_stardist); //this downloads jython.. see if this doesn't exit script
	//run("gat stardist batch",arg_stardist);
	run("Command From Macro", "command=[de.csbdresden.stardist.StarDist2D],args=['input':'"+img_seg+"', 'modelChoice':'Model (.zip) from File', 'normalizeInput':'true', 'percentileBottom':'1.0', 'percentileTop':'99.8', 'probThresh':'"+probability+"', 'nmsThresh':'"+overlap+"', 'outputType':'Both', 'modelFile':'"+model_file+"', 'nTiles':'"+n_tiles+"', 'excludeBoundary':'2', 'roiPosition':'Automatic', 'verbose':'false', 'showCsbdeepProgress':'false', 'showProbAndDist':'false'], process=[false]");
	wait(10);
	//make sure cells are detected for Hu.. if not exit macro
	if(roiManager("count")==0) exit("No cells detected. Reduce probability or check image.\nAnalysis stopped");
	else roiManager("reset");
	
	wait(50);
	temp=getTitle();
	run("Duplicate...", "title=label_image");
	label_image=getTitle();
	run("Remove Overlay");
	close(temp);
	roiManager("reset"); 
	selectWindow(label_image);
	wait(20);
	//remove all labels touching the borders
	run("Remove Border Labels", "left right top bottom");
	wait(10);
	rename("Label-killBorders"); //renaming as the remove border labels gives names with numbers in brackets
	//revert labelled image back to original size
	if(scale_factor!=1)
	{
		selectWindow("Label-killBorders");
		//run("Duplicate...", "title=label_original");
		run("Scale...", "x=- y=- width="+width+" height="+height+" interpolation=None create title=label_original");
		close("Label-killBorders");
	}
	else
	{
		selectWindow("Label-killBorders");
		rename("label_original");
	}
	wait(10);
	//rename("label_original");
	//size filtering
	selectWindow("label_original");
	run("Label Size Filtering", "operation=Greater_Than_Or_Equal size="+neuron_seg_lower_limit);
	label_filter=getTitle();
	resetMinAndMax();
	close("label_original");

	//convert the labels to ROIs
	runMacro(label_to_roi,label_filter);
	wait(10);
	close(label_image);
	selectWindow(max_projection);
	roiManager("show all");
	close(label_filter);
	print("Segmentation done");
}


