/*
 *Segment ganglia macro using deepImageJ 
*/


//Use runMacro("Directory where macro installed//Segment_Ganglia.ijm","multi-channel image,neuron channel, ganglia channel");

var fs=File.separator;
setOption("ExpandableArrays", true);


var fiji_dir=getDirectory("imagej");
var gat_dir=fiji_dir+"scripts"+fs+"GAT"+fs+"Tools"+fs+"commands";

//check if ganglia prediction post processing macro present
var deepimagej_post_processing=gat_dir+fs+"Ganglia_prediction_post_processing.ijm";
if(!File.exists(deepimagej_post_processing)) exit("Cannot find roi to label script. Returning: "+deepimagej_post_processing);



macro "ganglia_prediction"
{
		
	run("Clear Results");

	//Checks if CLIJ is installed
	List.setCommands;
	clij_install=List.get("CLIJ2 Macro Extensions");
	if(clij_install=="") 
	{
		print("CLIJ installation link: Please install CLIJ using from: https://clij.github.io/clij2-docs/installationInFiji");
		exit("CLIJ not installed. Check Log for installation details")
		
	}


	if(getArgument()=="")
	{
	
	waitForUser("You need a multichannel image with neuron channel and marker that labels the ganglia. Open image and select the channels/images in next prompt");
	//add option for multi-channel selection
	
	//
	//Di
	waitForUser("Select multi-channel image ");
	max_projection=getTitle();
	Dialog.create("Enter channel numbers for neuron and ganglia");
	Dialog.addNumber("Channel for neuron (Hu)", 1);
	Dialog.addNumber("Channel for ganglia marker (PGP/nNOS/NFM)", 2);
	Dialog.show();
	cell_channel=Dialog.getNumber();
	ganglia_channel=Dialog.getNumber();
	selectWindow(max_projection);
	Stack.getDimensions(width, height, channels, slices, frames);
	if(cell_channel>channels || ganglia_channel >channels) exit("Invalid channel number");
		
	}
	else 
	{
		args=getArgument();
		arg_array=split(args, ",");
		max_projection=arg_array[0];
		cell_channel=arg_array[1];
		ganglia_channel=arg_array[2];			

	}

ganglia_deepImageJ(max_projection,cell_channel,ganglia_channel);

}

//use deepimagej to predict ganglia outline and return a binary image
function ganglia_deepImageJ(max_projection,cell_channel,ganglia_channel)
{

	//waitForUser("Select max projection");
	selectWindow(max_projection);
	getPixelSize(unit, pixelWidth, pixelHeight);
	
	//max_projection=getTitle();
	
	
	selectWindow(max_projection);
	run("Select None");
	run("Duplicate...", "title=ganglia_ch duplicate channels="+ganglia_channel);
	run("Green");
	
	selectWindow(max_projection);
	run("Select None");
	run("Duplicate...", "title=cells_ch duplicate channels="+cell_channel);
	run("Magenta");
	
	run("Merge Channels...", "c1=ganglia_ch c2=cells_ch create");
	composite_img=getTitle();
	
	run("RGB Color");
	ganglia_rgb=getTitle();

	selectWindow(ganglia_rgb);
	run("Duplicate...", "title=ganglia_rgb_2"); //use this for verification
	
	close(composite_img);
	
	selectWindow(ganglia_rgb);
	
	run("DeepImageJ Run", "model=2D_enteric_ganglia format=Tensorflow preprocessing=[per_sample_scale_range.ijm] postprocessing=[no postprocessing] axes=Y,X,C tile=768,768,3 logging=normal");
	
	wait(10);
	prediction_output=getTitle();
	
	runMacro(deepimagej_post_processing,prediction_output);
	temp_pred=getTitle();
	
	selectWindow(temp_pred);
	run("Options...", "iterations=3 count=2 black do=Open");
	wait(5);
	
	min_area_ganglia_pixels=500;  //500 microns
	min_area_ganglia=min_area_ganglia_pixels/Math.sqr(pixelWidth);  //area proportional to sqr of radius
	run("Size Opening 2D/3D", "min="+min_area_ganglia);
	ganglia_pred_processed=getTitle();

	selectWindow(ganglia_pred_processed);
	run("Select None");
	run("Image to Selection...", "image=[ganglia_rgb_2] opacity=60");
	waitForUser("Check if the ganglia overlay is good. If not, use the brush tool to delete or add.");
	run("Select None");
	
	run("Size Opening 2D/3D", "min="+min_area_ganglia);
	ganglia_final=getTitle();
	
	close(ganglia_pred_processed);
	close("ganglia_rgb_2");
	close(temp_pred);
	close(ganglia_rgb);
	
	selectWindow(ganglia_final);
	return ganglia_final;
}