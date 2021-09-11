/*
 * goes through a folder of 3D tiff stacks, creates a maximum projection and saves it in specified output folder 
 * Using the ImageJ macro template for processing files in directories
 */
#@ String(value="Reads tif stacks in a folder and save maximum projection images in the specified Output Directory", visibility="MESSAGE") hint
#@ File (label = "Input directory for images", style = "directory") input_img
#@ File (label = "Output directory", style = "directory") output
#@ String(value="Open files with the following extension only", visibility="MESSAGE") hint2
#@ String (label = "File suffix (extension)", value = ".tif") suffix

var fs=File.separator;
run("Clear Results");
processFolder(input_img,output);
print("Maximum projection images are saved in the folder max_projection at "+output);
exit("Finished processing images");


// function to scan files to find files with correct suffix
function processFolder(input_img,output)
{
	list = getFileList(input_img);
	list = Array.sort(list);
	output_img=output+fs+"max_projection"+fs;
	if(!File.exists(output_img)) File.makeDirectory(output_img);
	for (i = 0; i < list.length; i++) 
	{
		//process only one level
		if(endsWith(list[i], suffix))
			{
				file=list[i];
			    processFile(input_img,output_img,file);
			 }
	}
}

function processFile(input_img,output_img,file) 
{
	//file=list[i];
	run("Clear Results");
	image=input_img + fs + file;
	print("Processing: " + image);
	open(image);
	curr_img=getTitle();
	run("Select None");
	run("Remove Overlay");
	selectWindow(curr_img);
	run("Z Project...", "projection=[Max Intensity]");
	max_project=getTitle();
	selectWindow(max_project);
	Stack.setDisplayMode("composite");
	saveAs("tif", output_img+"Max_"+file);
	close("*");
}
