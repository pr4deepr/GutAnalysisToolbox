//Get csv files that match name and concatenate them together..
//scan first directorty, get list of csv files
//loop through each csv file and concatenate results
//coult be a second macro
#@ String(value="<html>Merge files from different folders after analysis.<br/>This could be cell counts and spatial analysis csv files</html>", visibility="MESSAGE") hint
#@ String(value="<html>You should point this to a folder containing analysis for single image</html>", visibility="MESSAGE") hint2
#@ File (style="directory") currentDir
#@ String(label="Name of the file to merge (include extension)", value="Cell_counts.csv") namePattern


// Define the name pattern for CSV files you want to match
//namePattern = "Cell_counts.csv"; // Replace "pattern" with your desired name pattern

//csv_pos = indexOf("Cell_counts.csv", ".csv");
//output_name = substring(namePattern, 0,csv_pos);

var experiment_name = File.getNameWithoutExtension(currentDir);
// Define the output CSV file path
outputPath = "\\Merged_"+experiment_name+"_"+namePattern; // Replace "output.csv" with your desired output file path

if(File.exists(currentDir + outputPath) )
{
	print("File already exists at "+currentDir + outputPath);
	exit("Summary file already exists. Please delete or rename");
}

var fs = File.separator;
print("\\Clear");

// Initialize variables for concatenation
var header = "";
var concatenatedData = "";

// Collate csv files in specified directory
collate_csv(currentDir);
// Write the concatenated data to the output CSV file
File.saveString(concatenatedData, currentDir + outputPath);
print("Output file at "+currentDir + outputPath);
exit("Complete");


//Get data from specified csv files in folder and subfolders (Recursive function)
//Processes folders and subfolders (recursive function)
function collate_csv(folderPath) {
    list = getFileList(folderPath);
	for (i = 0; i < list.length; i++) {
	    fileName = list[i];
	    filePath = folderPath + fs+fileName;
	    if (endsWith(fileName, ".csv") && indexOf(fileName, namePattern) >= 0 && fileName!=folderPath + outputPath) 
	    {
	    	print("Processing "+filePath);
	        // Extract the parent folder name
	        parentFolder = File.getParent(filePath);
	        if(indexOf(parentFolder, "spatial_analysis") >= 0)
	        {
	        	parentFolder=File.getParent(folderPath);
	        }
	        // Read the CSV file
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
	        } else if (endsWith(fileName, ".csv")) {
	            // Skip CSV files that don't match the name pattern
	            continue;
	        } else if (File.isDirectory(filePath)) {
	            // Recursively process subfolders
	            collate_csv(filePath);
        }
    }
}
