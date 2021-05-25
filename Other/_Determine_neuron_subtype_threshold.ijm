//macro to test the NOS parameters before using them for analysing NOS neurons
//This can be used for studying ChAT or other markers colocalising with neurons 
#@ File (label="Open channel of interest (NOS/ChAT..)",style="open") nos
#@ boolean image_already_open
#@ String(value="<html>If image is already open, tick above box.<html>", visibility="MESSAGE") hint2
#@ File (label="Open neuron ROI manager",style="open") roi_location 
//#@ double Scale_Factor
//scale_factor=Scale_Factor;
//if(scale_factor==0) scale_factor=1;


//for running other macros
//consider moving this into plugins folder so can use getDirectory("plugins")
fiji_dir=getDirectory("imagej");
gat_dir=fiji_dir+"scripts\\GAT\\Other";

//nos_processing_macro
nos_processing_dir=gat_dir+"\\NOS_processing"
if(!File.exists(nos_processing_dir)) exit("Cannot find NOS processing macro. Returning: "+nos_processing_dir);

//check_plugin_installation
check_plugin=gat_dir+"\\check_plugin"
if(!File.exists(check_plugin)) exit("Cannot find check plugin macro. Returning: "+check_plugin);

runMacro(check_plugin);



if(image_already_open==true)
{
	waitForUser("Select Image. and choose output folder in next prompt");
	file_name=getTitle(); //get file name without extension (.lif)
	dir=getDirectory("Choose Output Folder");
}
else
{
	file_name=File.getName(nos);
	open(nos);
}
save_dir=File.getDirectory(roi_location);

	rename("nos_orig");
	selectWindow("nos_orig");


print("Will save parameters at: "+save_dir);
run("Set Measurements...", "area_fraction redirect=None decimal=3");
roiManager("reset");
Stack.getDimensions(width, height, channels, slices, frames);
getPixelSize(unit, pixelWidth, pixelHeight);

//scaling is done so that the parameters in the median filtering, diff of gaussian and area opening remain the same
//Training images were pixelsize of ~0.378, so scaling images based on this
//generate arguments for passing to macro
arg_string="nos_orig"+","+d2s(0.378,3);
print(arg_string);
//use NOS_processing macro
runMacro(nos_processing_dir,arg_string);
wait(10);
nos_diff_gauss=getTitle();//output from macro above
//selectWindow(nos_diff_gauss);


do{
	selectWindow(nos_diff_gauss);
	run("Duplicate...", "title=nos_filtered");
	selectWindow("nos_filtered");
	//setAutoThreshold("Triangle dark");//setAutoThreshold("Intermodes dark");
	setAutoThreshold("Default dark no-reset");
	setOption("BlackBackground", true);
	run("Threshold...");
	waitForUser("Select threshold method that gets most of the NOS");
	run("Convert to Mask");
	threshold_method=getInfo("threshold.method");
	//run("Options...", "iterations=3 count=4 black edm=8-bit do=Open");
	run("Area Opening", "pixel=100");
	temp=getTitle();
	//scale back to original size
	run("Scale...", "x=- y=- width="+width+" height="+height+" interpolation=None create title=nos_analysis");
	close(temp);
	//rename("nos_analysis");
	run("Set Measurements...", "area_fraction redirect=None decimal=3");
	run("Clear Results"); 
	nos=0;
	setOption("ExpandableArrays", true);
	nos_array=newArray();
	//setting ROIs to use names
	roiManager("UseNames", "true");
	
	selectWindow("nos_analysis");
	roiManager("open", roi_location);
	rename_roi();
	roiManager("deselect");
	roiManager("Measure");
	//IJ.renameResults("Total Area");
	run("ROI Color Coder", "measurement=%Area lut=[16 colors] width=3 opacity=100 label=%Area range=Min-100 n.=11 decimal=0 ramp=[256 pixels] font=SansSerif font_size=14 draw");
	selectWindow("nos_orig");
	roiManager("Show All without labels");
	waitForUser("The ROIs are colour coded based on the thresholding selected.\nUsing colour scale, an approximate %area value can be chosen.");
	
	//count NOS neurons
	response=0;
	nos_value=getNumber("Enter a value between 0 and 100 for determining area percentage of NOS", 40);
	neuron_count=roiManager("count");
	for(i=0;i<neuron_count;i++)
		{
			selectWindow("nos_analysis");
			roiManager("Select",i);
			roiManager("Measure");
			perc_area=getResult("%Area", 0); //Results are cleared every loop, so only read area in first row
			print("Percent Area of "+Roi.getName+" cell is "+perc_area);
			run("Clear Results"); 
			if(perc_area>=nos_value) //Provide separate macros to assess this value; default 40
				{
					nos_name="NOS_"+(nos+1);
					roiManager("Rename",nos_name);
					print("Neuron "+(i+1)+" is NOS");
					nos_array[nos]=i;
					nos+=1;
				}
		}
	selectWindow("nos_orig");
	roiManager("show all with labels");
	waitForUser("Verify the ROIs. You can repeat this step again with different threshold and area percentages");
	response=getBoolean("Are you happy with the results. Press No if you want to try again?");
	if(response==0)
	{
		roiManager("reset");
		close("nos_analysis");
		close("nos_filtered");
		selectWindow("Results");
		run("Close");
	}
	close("%Area Ramp");
} while(response==0)					

nos=nos_array.length;

print("\\Clear");

print("The threshold method that works: "+threshold_method);
print("NOS value to use: "+nos_value);
Table.create("Parameters");
Table.set("Threshold Method", 0, threshold_method);
Table.set("NOS area fraction of Hu to be NOS+ve", 0, nos_value);
Table.update;
Table.save(save_dir+"parameters_threshold_NOS_"+file_name+".csv");

selectWindow("Results");
run("Close");
close("nos_analysis");
close("nos_filtered");

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