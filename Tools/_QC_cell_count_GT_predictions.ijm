/*
 * Compare percentage error for ground truth (manual annotation) vs prediction.
 * Images in each folder should have same name
 */
print("\\Clear");
var fs = File.separator;
#@ String(value="<html>Compare percentage error for ground truth (manual annotation) vs prediction.<br/>Enter folder locations for GT and predictions below.<br/>Images in both folders should have same names<html>",visibility="MESSAGE", required=false) hint34
#@ File (label = "Folder with Ground truth predictions/label images", style = "directory") gt_input
#@ File (label = "Folder with New predictions", style = "directory") pred_output
#@ String (label = "File suffix", value = ".tif") suffix

processFolder(gt_input,pred_output);

// function to scan folders/subfolders/files to find files with correct suffix
function processFolder(gt_input,pred_output) {
	list = getFileList(gt_input);
	list = Array.sort(list);
	Array.print(list);
	idx=0;
	
	for (i = 0; i < list.length; i++) {
		if(i==0)
		{
			table_name="GTvsPred";
			Table.create(table_name);
			
		}
		if(endsWith(list[i], suffix))
		{
			
			processFile(gt_input, pred_output,list[i],table_name,idx);
			idx+=1;
		}
			
	}
}

function processFile(input, pred_output, file,table_name,idx) 
{	
	run("Clear Results");
	print("Processing: " + file);
	
	image=input + File.separator + file;
	open(image);
	gt=getTitle();
	selectWindow(gt);
	run("Label image to ROIs");
	gt_count=roiManager("count");
	roiManager("reset");

	mask=pred_output + File.separator + file;
	if(!File.exists()) exit("File path not found "+mask+". Images in both folders should have same names");
	open(mask);
	pred=getTitle();//"new";
	selectWindow(pred);
	run("Label image to ROIs");
	new_count=roiManager("count");
	roiManager("reset");
	
	//calculate percentage error
	perc_error = (new_count-gt_count)*100/gt_count;
	
	selectWindow(table_name);
	Table.set("File", idx,file );
	Table.set("GT count", idx, gt_count);
	Table.set("New count", idx, new_count);
	Table.set("Percentage_error", idx, perc_error);
	Table.update;
	close("*");
}
	