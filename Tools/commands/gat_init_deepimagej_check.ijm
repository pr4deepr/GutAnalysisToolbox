fiji_dir=getDirectory("imagej");
fs=File.separator;
gat_dir=fiji_dir+"scripts"+fs+"GAT"+fs+"Tools"+fs+"commands";

file_name = "gat_init_deepimagej_check_counts.txt";

deepimagej_check_path = gat_dir+fs+file_name;
run("Table... ", "open=["+deepimagej_check_path+"]");
selectWindow(file_name);

no_times_gat_init = Table.get("no_times_gat", 0);
if(no_times_gat_init==0)
{
	first_time_msg = gat_dir+fs+"first_time_msg.ijm";
	runMacro(first_time_msg);
	no_times_gat_init+=1;
	selectWindow(file_name);
	Table.set("no_times_gat",0, no_times_gat_init);
	Table.save(deepimagej_check_path);
	selectWindow(file_name);
	run("Close");
	
}
else 
{
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

