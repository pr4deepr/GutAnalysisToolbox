/*
 *Segment ganglia macro using deepImageJ 
*/


//Use runMacro("Directory where macro installed//Segment_Ganglia.ijm","multi-channel image,neuron channel, ganglia channel");

var fs=File.separator;
setOption("ExpandableArrays", true);


var fiji_dir=getDirectory("imagej");
var gat_dir=fiji_dir+"scripts"+fs+"GAT"+fs+"Tools"+fs+"commands";
//specify directory where StarDist models are stored
var models_dir=fiji_dir+"models"+fs; //"scripts"+fs+"GAT"+fs+"Models"+fs;



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
	batch_mode=false;
		
	}
	else 
	{
		args=getArgument();
		arg_array=split(args, ",");
		max_projection=arg_array[0];
		cell_channel=parseInt(arg_array[1]);
		ganglia_channel=parseInt(arg_array[2]);
		ganglia_model = arg_array[3];
		batch_mode=arg_array[4];

	}

ganglia_deepImageJ(max_projection,cell_channel,ganglia_channel,batch_mode);

}

//use deepimagej to predict ganglia outline and return a binary image
function ganglia_deepImageJ(max_projection,cell_channel,ganglia_channel,batch_mode)
{

	//waitForUser("Select max projection");
	selectWindow(max_projection);
	run("Select None");
	getPixelSize(unit, pixelWidth, pixelHeight);
	
	//max_projection=getTitle();
	
	
	selectWindow(max_projection);
	run("Select None");
	Stack.setChannel(ganglia_channel);
	run("Duplicate...", "title=ganglia_ch duplicate channels="+ganglia_channel);
	run("Green");
	
	selectWindow(max_projection);
	run("Select None");
	Stack.setChannel(cell_channel);
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
	
	print("*********Segmenting cells using DeepImageJ********");
	print("When running for the first time, it may take a while for ganglia segmentation as deepimageJ needs to initialize. Check Window -> Console for progress\n");
	print("If you are getting an error during ganglia prediction, please download a new ganglia model or check DeepImageJ version. It should be > v3");
	//location of preprocessing script
	im_preprocessing = models_dir+fs+ganglia_model+fs+"im_preprocessing.ijm";
	//convert to 32 bit stack
	runMacro(im_preprocessing);
		
	print("Using Ganglia model: "+ganglia_model);
	ganglia_model_path = models_dir+fs+ganglia_model;
	run("DeepImageJ Run", "model_path=["+ganglia_model_path+"] input_path=null output_folder=null display_output=all");
	wait(20);
	prediction_output=getTitle();
	if(prediction_output==ganglia_rgb) exit("Ganglia segmentation not successful.\nEither the models are not correct or DeepImageJ needs to be updated");

	selectWindow(prediction_output);
	run("Options...", "iterations=3 count=2 black do=Open");
	wait(5);
	
	min_area_ganglia=200;  //200 microns
	//area proportional to sqr of radius; so use square of pixelwidth as denominator
	min_area_ganglia_pix=Math.ceil(min_area_ganglia/Math.sqr(pixelWidth));
	run("Size Opening 2D/3D", "min="+min_area_ganglia_pix);
	ganglia_pred_processed=getTitle();

	selectWindow(ganglia_pred_processed);
	run("Select None");
	
	if(batch_mode==false)
	{
		run("Image to Selection...", "image=[ganglia_rgb_2] opacity=60");
		waitForUser("Check if the ganglia overlay is good. If not, use the brush tool to delete or add.");
		run("Select None");
	}
	
	run("Size Opening 2D/3D", "min="+min_area_ganglia_pix);
	ganglia_final=getTitle();
	
	close(ganglia_pred_processed);
	close("ganglia_rgb_2");
	close(ganglia_rgb);
	
	selectWindow(ganglia_final);
	return ganglia_final;
}