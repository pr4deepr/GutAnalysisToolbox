//macro to test the NOS parameters before using them for analysing NOS neurons
//This can be used for studying ChAT or other markers colocalising with neurons 

#@ File (label="Open multi-channel of interest (NOS/ChAT..)",style="open") multi_ch_path
#@ String(choices={"Method 1","Method 2"}, style="radioButtonHorizontal") Marker_Method
#@ String marker_name
//#@ File (label="Open image for neuron (Hu)",style="open") hu
#@ File (label="Open neuron ROI manager",style="open") roi_location 

//#@ double Scale_Factor
//scale_factor=Scale_Factor;
//if(scale_factor==0) scale_factor=1;

var fs=File.separator;
training_pixel_size=0.378;
//for running other macros
//consider moving this into plugins folder so can use getDirectory("plugins")
fiji_dir=getDirectory("imagej");
gat_dir=fiji_dir+"scripts"+fs+"GAT"+fs+"Other"+fs+"commands";

//marker_processing_macro
if(Marker_Method=="Method 1") marker_processing_dir=gat_dir+fs+"NOS_processing.ijm";
else if(Marker_Method=="Method 2") 
{
	marker_processing_dir=gat_dir+fs+"Method_2.ijm";
	print("MARKER 2");
}

if(!File.exists(marker_processing_dir)) exit("Cannot find "+Marker_Method+" processing macro. Returning: "+marker_processing_dir);

//check_plugin_installation
check_plugin=gat_dir+fs+"check_plugin.ijm";
if(!File.exists(check_plugin)) exit("Cannot find check plugin macro. Returning: "+check_plugin);
runMacro(check_plugin);

run("Clear Results");

//if(image_already_open==true)
//{
//	waitForUser("Select Image and choose output folder in next prompt");
//	file_name=getTitle(); //get file name without extension (.lif)
//	dir=getDirectory("Choose Output Folder");
//}
//else
//{
	//file_name=File.getName(multi_ch_path);
	
	
	if(endsWith(multi_ch_path, ".czi")|| endsWith(multi_ch_path, ".lif")) run("Bio-Formats", "open=["+multi_ch_path+"] color_mode=Composite rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT");
	else if (endsWith(multi_ch_path, ".tif")|| endsWith(multi_ch_path, ".tiff")) open(multi_ch_path);
	else exit("Not recognised.  Tif, Lif and CZI files supported.");
	file_name=File.nameWithoutExtension; //get file name without extension (.lif)
	open(multi_ch_path);
//}
save_dir=File.getDirectory(roi_location);
stack=getTitle();
Stack.getDimensions(width, height, channels, slices, frames);
getPixelSize(unit, pixelWidth, pixelHeight);
if(channels<2) exit("ERROR: Need a multichannel image");

selectWindow(stack);
waitForUser("Verify Hu and "+marker_name+" channels. Enter this in the next prompt.");

Dialog.create("Choose channels");
Dialog.addNumber("Enter Hu channel", 1);
Dialog.addNumber("Enter "+marker_name+" channel", 2);
Dialog.show(); 
hu_channel= Dialog.getNumber();
marker_channel=Dialog.getNumber();	

selectWindow(stack);
run("Remove Overlay");
run("Select None");
run("Duplicate...", "title=marker_channel duplicate channels="+marker_channel);
marker=getTitle();

selectWindow(stack);
run("Duplicate...", "title=hu_neuron duplicate channels="+hu_channel);
hu=getTitle();

print("Will save parameters at: "+save_dir);
run("Set Measurements...", "area_fraction redirect=None decimal=3");
roiManager("reset");

//Training images were pixelsize of ~0.378, so scaling images based on this
//generate arguments for passing to macro
arg_string=marker+","+hu+","+d2s(training_pixel_size,3);
print(arg_string);
//use marker_processing_macro
runMacro(marker_processing_dir,arg_string);
wait(10);
correlation_map=getTitle();//output from macro above
selectWindow(correlation_map);
//nos_diff_gauss=getTitle();//output from macro above
//selectWindow(nos_diff_gauss);


do{
	run("Set Measurements...", "mean redirect=None decimal=2");
	

	//setting ROIs to use names
	roiManager("UseNames", "true");
	selectWindow(correlation_map);
	roiManager("open", roi_location);
	cell_count=roiManager("count");
	rename_roi();
	roiManager("deselect");
	roiManager("Measure");
	//IJ.renameResults("Total Area");
	run("ROI Color Coder", "measurement=Mean lut=[Rainbow RGB] width=3 opacity=100 label=%Area range=Min-Max n.=11 decimal=0 ramp=[256 pixels] font=SansSerif font_size=14 draw");
	selectWindow(correlation_map);
	roiManager("Show All without labels");
	waitForUser("The ROIs are colour coded based on correlation coefficient.\nUsing colour scale, decide on most appropriate correlation coeff.\nThis can vary based on image quality and acquisition.");
	
	//count NOS neurons
	response=0;
	marker_value=getNumber("Enter a value for correlation coefficient.", 80);
	neuron_count=roiManager("count");
	count=0;
	setOption("ExpandableArrays", true);
	marker_array=newArray();
	for(i=0;i<neuron_count;i++)
		{
			selectWindow(correlation_map);
			roiManager("Select",i);
			roiManager("Measure");
			corr_coeff=getResult("Mean", 0); //Results are cleared every loop, so only read area in first row
			print("Correlation Coefficient of "+Roi.getName+" cell is "+corr_coeff);
			run("Clear Results"); 
			if(corr_coeff>=marker_value) //Provide separate macros to assess this value; default 80
				{
					roi_name=marker_name+"_"+(count+1);
					roiManager("Rename",roi_name);
					print("Neuron "+(i+1)+" is positive for "+marker_name);
					marker_array[count]=i;
					count+=1;
				}
		}
	selectWindow(marker);
	roiManager("show all with labels");
	waitForUser("Verify the ROIs and ensure that cells are positive for "+marker_name+".");
	response=getBoolean("Are you happy with the results. Press No if you want to try another correlation coefficient value?");
	if(response==0)
	{
		roiManager("reset");
		//close("nos_analysis");
		//close("nos_filtered");
		selectWindow("Results");
		run("Close");
	}
	close("%Area Ramp");
} while(response==0)					

marker=marker_array.length;

print("\\Clear");

//print("The threshold method that works: "+threshold_method);
print("Correlation coefficient value to use: "+corr_coeff);
Table.create("Parameters");
Table.set("File name", 0, file_name);

//Table.set("Threshold Method", 0, threshold_method);
//Table.set("NOS area fraction of Hu to be NOS+ve", 0, nos_value);
Table.set("Total neurons ", 0, neuron_count);
Table.set(marker_name+" number ", 0, marker);
Table.set(marker_name+"/Hu ratio", 0, marker/cell_count);
Table.set("Correlation coefficient for "+marker_name, 0, marker/cell_count);
Table.update;
Table.save(save_dir+"parameters_threshold_"+marker_name+"_"+file_name+".csv");
roiManager("UseNames", "false");
selectWindow("Results");
run("Close");

close("*");

//rename ROIs
function rename_roi()
{
	end=roiManager("count");
	for (i=0; i<end;i++)
		{ 
		roiManager("Select", i);
		roiManager("Rename", i+1);
		}
}