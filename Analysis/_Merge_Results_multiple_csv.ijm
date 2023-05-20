//Merge multiple csv files


#@ String(value="<html>Merge files from different folders after analysis.<br/>This macro will detect files in first subfolder and summarise them across all folders</html>", visibility="MESSAGE") hint
#@ String(value="<html>You should point this to the folder containing subfolders with cell counts and spatial analysis for multiple images</html>", visibility="MESSAGE") hint2
#@ String(value="<html>All folders should have same number of csv files</html>", visibility="MESSAGE") hint3
#@ File (style="directory") results_dir

var experiment_name = File.getNameWithoutExtension(results_dir);

var fs = File.separator;
print("\\Clear");

// global variables
var header = "";
//variable to store concatenated csv file data
var concatenatedData = "";

setOption("ExpandableArrays", true);

// Scan all subfolders within the first folder of the directory for CSV files
list = getFileList(results_dir);
subFolderPath = results_dir + fs+list[0];
print(subFolderPath);
//all csv files in first folder used for summarising
csvFileNames = scanSubfolders(subFolderPath);
print("Merging the following files:");
Array.print(csvFileNames);
//make sure summary files do not already exist
check_files(results_dir,csvFileNames);

//generate merged files
for (i = 0; i < csvFileNames.length; i++) 
{
	
    merge_results(results_dir,csvFileNames[i]);
    showProgress(i,csvFileNames.length);
}



//Get the csv files from  only the files and subfolder in  the first folder
//These csv files will be used to determine the files that need to ebe summarised


function scanSubfolders(folderPath) {
    list = getFileList(folderPath);
    //Array.print(list);
    fileNames = newArray(0);
    for (i = 0; i < list.length; i++) {
        fileName = list[i];
        filePath = folderPath +fileName;
        //print(filePath);
        if (File.isDirectory(filePath)) 
        {
        	
            // Recursively scan subfolders
            subfolderFileNames = scanSubfolders(filePath);
            fileNames = Array.concat(fileNames, subfolderFileNames);
        } else if (endsWith(fileName, ".csv")) {
        	//print(fileName);
            fileNames = Array.concat(fileNames, newArray(fileName));
        }
    }
    return fileNames;
}



//check if summary file exists
function check_files(currentDir,csvFileNames)
{
	for (i = 0; i < csvFileNames.length; i++) 
	{
	  outputPath = "\\Merged_"+experiment_name+"_"+csvFileNames[i]; 

	  if(File.exists(currentDir + outputPath) )
		{
			print("File already exists at "+currentDir + outputPath);
			exit("Merged file already exists. Please delete or rename");
		}
	}
}


function merge_results(currentDir,namePattern)
{
	outputPath = "\\Merged_"+experiment_name+"_"+namePattern; // Replace "output.csv" with your desired output file path

	collate_csv(currentDir,namePattern);
	File.saveString(concatenatedData, currentDir + outputPath);
	print("Output file at "+currentDir + outputPath);
	
	//reset header and concatenated Data
	header = "";
	concatenatedData = "";
}

function collate_csv(folderPath,namePattern) 
{
    list = getFileList(folderPath);
    //Array.show(list);
	for (i = 0; i < list.length; i++) {
	    fileName = list[i];
	    filePath = folderPath + fs+fileName;
	    //should be csv, match the namepattern, should not be the name of the summary file and not have summary in its name
	    if (endsWith(fileName, ".csv") && indexOf(fileName, namePattern) >= 0 && fileName!=(folderPath + outputPath)) 
	    {
	    	print("Processing "+filePath);
	        // Extract the parent folder name
	        parentFolder = File.getParent(filePath);
	        if(indexOf(parentFolder, "spatial_analysis") >= 0)
	        {
	        	parentFolder=File.getParent(folderPath);
	        }
	        //Read csv file and get the lines
	        content = File.openAsString(filePath);
	        lines = split(content, "\n");
	        
	        for (j = 0; j < lines.length; j++) {
	                line = lines[j];
	                if (j == 0) {
	                    if (header == "") {
	                        header = line;
	                        concatenatedData += "Experiment," + header + "\n";
	                    }
	                } else {
	                    concatenatedData += parentFolder + "," + line + "\n";
	                }
	            }
	        } 
	        else if (endsWith(fileName, ".csv")) 
	        {
	            // skip csv files that don't match specified pattern
	            continue;
	        } 
	        else if (File.isDirectory(filePath)) 
	        {
	            // Recursively process subfolders
	            collate_csv(filePath,namePattern);
        }
    }
}