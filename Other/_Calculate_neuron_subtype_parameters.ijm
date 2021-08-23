//macro to test the NOS parameters before using them for analysing NOS neurons
//This can be used for studying ChAT or other markers colocalising with neurons 

#@ File (label="Open multi-channel of interest (NOS/ChAT..)",style="open") multi_ch_path
#@ String(choices={"Method 1","Method 2"}, style="radioButtonHorizontal",label="Choose Method for identifying cell subset") Marker_Method
#@ String(value="<html>Use Method1 if the markers are bright, <br>Use Method 2 if the staining is dull and non-uniform<html>", visibility="MESSAGE") hint4
#@ String marker_name
#@ boolean High_Low_Expression
#@ String(value="<html>Enter Name of the marker you want to test<html>", visibility="MESSAGE") hint2
//#@ File (label="Open image for neuron (Hu)",style="open") hu
#@ File (label="Open ROI manager for neuron",style="open") roi_location 
#@ String(value="<html>Run Segment Neuron macro to get neuron ROIs<html>", visibility="MESSAGE") hint1
//#@ double Scale_Factor
//scale_factor=Scale_Factor;
//if(scale_factor==0) scale_factor=1;

var fs=File.separator;

training_pixel_size=0.568;//training_pixel_size=0.378;
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

hi_lo=High_Low_Expression;

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

save_dir=File.getDirectory(roi_location);
stack=getTitle();
Stack.getDimensions(width, height, channels, width, frames);
getPixelSize(unit, pixelWidth, pixelHeight);
if(channels<2) exit("ERROR: Need a multichannel image");

if(width>1)
{
		selectWindow(stack);
		
		if(frames>width) width=frames;
		print(stack+" is a stack");
		roiManager("reset");
		waitForUser("Note the start and end of the stack.\nPress OK when done");
		Dialog.create("Choose slices");
  		Dialog.addNumber("Start slice", 1);
  		Dialog.addNumber("End slice", width);
  		Dialog.show(); 
  		start=Dialog.getNumber();
  		end=Dialog.getNumber();
		run("Z Project...", "start=&start stop=&end projection=[Max Intensity]");
		max_projection=getTitle();
}
else 
{
		print(stack+" has only one slice, using as max projection");
		max_projection=getTitle();
}



selectWindow(max_projection);
waitForUser("Verify Hu and "+marker_name+" channels. Enter this in the next prompt.");

Dialog.create("Choose channels");
Dialog.addNumber("Enter Hu channel", 1);
Dialog.addNumber("Enter "+marker_name+" channel", 2);
Dialog.show(); 
hu_channel= Dialog.getNumber();
marker_channel=Dialog.getNumber();	

selectWindow(max_projection);
run("Remove Overlay");
run("Select None");
run("Duplicate...", "title=marker_channel duplicate channels="+marker_channel);
marker=getTitle();

selectWindow(max_projection);
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
	//selectWindow(correlation_map);
	selectWindow(max_projection);
	Stack.setDisplayMode("color");
	Stack.setChannel(marker_channel);
	roiManager("Show All without labels");
	waitForUser("The ROIs are colour coded based on correlation coefficient.\nUsing colour scale, decide on most appropriate correlation coeff.\nThis can vary based on image quality and acquisition.");
	
	//count NOS neurons
	response=0;
	marker_value=getNumber("Enter a value for correlation coefficient.", 80);
	if(hi_lo==true)
	{
		waitForUser("In previous prompt,"+marker_value+" was entered for "+marker_name+" positive cells.\nChoose a value in the next prompt as threshold between high and low "+marker_name+" cells." );
		hi_lo_threshold=getNumber("Enter a value for correlation coefficient to distinguish between high and low "+marker_name+" positive cells.", 30);
	}
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
					if(hi_lo==true)
					{
						if(corr_coeff>hi_lo_threshold) roi_name=marker_name+"_HIGH_"+(count+1);
						else roi_name=marker_name+"_LOW_"+(count+1);
					}
					else 
					{
						roi_name=marker_name+"_"+(count+1);
					}
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
Table.set("Correlation coefficient for "+marker_name, 0, marker_value);
if(hi_lo==true)
{
	high=find_ROI_name("HIGH");
	Table.set(marker_name+" High", 0, high);
	low=find_ROI_name("LOW");
	Table.set(marker_name+" Low", 0, low);
	Table.set("Threshold for distinguishing High Low expressing "+marker_name , 0, hi_lo_threshold);
}
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

//Based on macro by Olivier Burri https://forum.image.sc/t/selecting-roi-based-on-name/3809
//finds rois that contain a string; converts it to lowercase, so its case-insensitive
function find_ROI_name(roiName)
{

	roiName=toLowerCase(roiName);
	nR = roiManager("Count"); 
	roiIdx = newArray(nR); 
	k=0; 
	clippedIdx = newArray(0); 
	
	regex=".*"+roiName+".*";
	
	for (i=0; i<nR; i++) 
	{ 
		roiManager("Select", i); 
		rName = toLowerCase(Roi.getName()); 
		if (matches(rName, regex)) 
		{ 
			roiIdx[k] = i; 
			k++; 
			print(i);
		} 
	} 
	if (k>0) 
	{ 
		clippedIdx = Array.trim(roiIdx,k); 
		//roiManager("select", clippedIdx);
	} 
	//else roiManager("deselect");
	return k;
}