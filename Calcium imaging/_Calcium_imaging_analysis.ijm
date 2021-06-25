//Macro for calcium imaging analysis
//can be used for any tissue or even cells. 
//Option for normalising to baseline depending on user-specified baseline frames

//TODO: Add option to save cell coordinates and get ganglia outline


var fs = File.separator;//Returns the file name separator character ("/" or "\").

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
#@ String(value="<html>If you are not sure, leave it checked</html>", visibility="MESSAGE") hint2

open(calcium_path);//File.openDialog("Open the aligned calcium imaging file or Max Stack"));
calcium_stack_name_orig=File.name;
file_dir=File.directory;


getDimensions(w, h, channels, slices, frames); //get dimensions so the resolution and frames can be used later on

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
