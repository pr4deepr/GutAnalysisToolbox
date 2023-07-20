//get image and mask
//save them in a user-specified folder

//Use runMacro("Directory where macro installed//save_img_mask.ijm","name of image, name of mask, save_dir");

var fs=File.separator;
setOption("ExpandableArrays", true);


var fiji_dir=getDirectory("imagej");
var gat_dir=fiji_dir+"scripts"+fs+"GAT"+fs+"Tools"+fs+"commands";


macro "save_image_mask"
{
	args = getArgument();
	arg_array = split(args, ",");
	img = arg_array[0];
	mask = arg_array[1];
	prefix_name = arg_array[2]; 
	save_dir = arg_array[3]; 
	
	image_path=save_dir+fs+"images"+fs;
	masks_path=save_dir+fs+"masks"+fs;

	if(!File.exists(image_path)) File.makeDirectory(image_path);
	if(!File.exists(masks_path)) File.makeDirectory(masks_path);

	selectWindow(img);
	run("Select None");
	run("Duplicate...", "title=img_save");

	selectWindow(mask);
	run("Select None");
	run("Duplicate...", "title=mask_save");
	
	selectWindow("mask_save");
	run("Select None");
	//run("glasbey_on_dark");
	file_name=generate_file_name(image_path,prefix_name,".tif");
	masks_path=masks_path+file_name;
	saveAs("tiff", masks_path);
	close();
	
	selectWindow("img_save");
	run("Select None");
	run("Remove Overlay");
	image_path=image_path+file_name;
	saveAs("tiff", image_path);
	close();
	print("Image and Masks for "+prefix_name+" saved");

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
			//print(new_name);
			no+=1;
		}
		while(File.exists(path+new_name+ext))
	}
	return new_name;
}