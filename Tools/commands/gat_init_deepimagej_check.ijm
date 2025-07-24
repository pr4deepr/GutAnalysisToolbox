/***
 * Checks if first time running GAT by testing if gat_init_deepimagej_check.txt file exists. If so, runs the first time message, and also saves a file gat_init_deepimagej_check.txt
 * Also checks if deepimagej initialized
 */

fiji_dir=getDirectory("imagej");
fs=File.separator;
gat_dir=fiji_dir+"scripts"+fs+"GAT"+fs+"Tools"+fs+"commands";

file_name = "gat_init_deepimagej_check_file";
table_file_path = gat_dir+fs+file_name;

if(!File.exists(table_file_path))
{
	first_time_msg = gat_dir+fs+"first_time_msg.ijm";
	runMacro(first_time_msg);
	
	Table.create(file_name);
	Table.set("test", 0, "file_check_deepimagej");
	Table.update;
	Table.save(table_file_path);
	
	selectWindow(file_name);
	run("Close");
}


//check if deepimagej initialized. If it has been, there will be a folder `engines` in fiji directory
deepimagej_engines = fiji_dir+fs+"engines";
if(!File.exists(deepimagej_engines))
{
	print("DeepImageJ needs to be initialized first.\n Could you please go to Plugins -> DeepImageJ -> DeepImageJ Run\nThis will download the required files\nWhen finished, run GAT again");
	print("When running DeepImagej, you can see the download progress on top left of the DeepImageJ window");
	exit("DeepImageJ needs to be initialized first.\n Could you please go to Plugins -> DeepImageJ -> DeepImageJ Run\nThis will download the required files\nWhen finished, run GAT again. This message won't appear again if deepimageJ is run correctly.\nThis message is also in the Log Window");
}
else {
	print("DeepImageJ already initialized");
}

