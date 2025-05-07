//check installation of plugins
macro "check_plugins" 
{
	run("Console");
	getDateAndTime(year, month, dayOfWeek, dayOfMonth, hour, minute, second, msec);
	print("Date: "+year+"/"+month+"/"+dayOfMonth+" Time:"+hour+":"+minute+":"+second);
	print("System Config:");
	run("Clear Results");
	
	//print system config (separate out to a different macro
	run("CLIJ2 Macro Extensions", "cl=");
	Ext.CLIJ2_GPUProperties();
	
	gpu = Table.getString("GPUName",0);
	gpu_memory=Table.get("Global_memory_in_bytes",0);
	print("OpenCL Device: "+gpu+"\nOpenCL memory(GB):"+gpu_memory/(1024000000));
	
	memory = IJ.freeMemory();
	print("RAM: "+memory);

	memory = parseInt(IJ.maxMemory())/1024000000;
	if(memory < 20) print("Your FIJI has only ~"+round(memory)+" of RAM allocated. Cell segmentation may not work on large images.\nIt is recommended to allocate atleast 32GB RAM");
	print("Visit https://gut-analysis-toolbox.gitbook.io/docs for more info");
	run("Clear Results");
	selectWindow("Results");
	run("Close");
	//****************

	//check if model files are present
	var fs = File.separator;
	//models
	//get settings for GAT
	//get fiji directory and get the macro folder for GAT
	var fiji_dir=getDirectory("imagej");
	var gat_dir=fiji_dir+"scripts"+fs+"GAT"+fs+"Tools"+fs+"commands";
	
	//specify directory where StarDist models are stored
	var models_dir=fiji_dir+"models"+fs;
	
	gat_settings_path=gat_dir+fs+"gat_settings.ijm";
	if(!File.exists(gat_settings_path)) exit("Cannot find settings file. Check: "+gat_settings_path);
	run("Results... ", "open="+gat_settings_path);

	//get paths of model files
	neuron_model_file = Table.getString("Values", 13);
	neuron_subtype_file = Table.getString("Values", 14);
	//ganglia_model_dir = Table.getString("Values", 12);
	run("Close");

	//Neuron segmentation model
	neuron_model_path=models_dir+neuron_model_file;
	//Marker segmentation model
	subtype_model_path=models_dir+neuron_subtype_file;
	//ganglia_model_path=models_dir+ganglia_model_dir+fs;

	
	
	if(!File.exists(neuron_model_path)||!File.exists(subtype_model_path))//||!File.exists(ganglia_model_path)) 
	{
		print("Cannot find models for segmenting. It is possible that the model has not been copied or even updated");
		print("Current model for neuron segmentation: "+neuron_model_file);
		print("Current model for neuron subtype segmentation: "+neuron_subtype_file);
		//print("Current model for ganglia segmentation: "+ganglia_model_path);
		print("Models found in the folder:");
		files_list = getFileList(models_dir);
		if(files_list.length >0)
		{
			for (i = 0; i < files_list.length; i++) 
			{
				file_name = File.getNameWithoutExtension(files_list[i]);
				if(startsWith(file_name, "2D_enteric_neuron"))
				{
					print(file_name);
				}
				
				else if(startsWith(file_name, "2D_enteric_ganglia_v2"))
				{
					print(file_name);
					print("Ganglia model needs to be updated to 2D_Ganglia_RGB_v2.bioimage.io.model as current model is not compatible with DeepImageJ v3");
				}
				else if(indexOf(file_name, "2D_Ganglia")>=0)
				{
					print("Ganglia model folder with name 2D_Ganglia: ");
					print(file_name);
				}

			}
		}
		else { print("No files found");
		       exit("Cannot find models for segmenting neurons. Check log for details");
	     }
	}


	
	print("******Checking if plugins are installed.*******");
	checks=newArray("DeepImageJ Run","Area Opening","Command From Macro","CLIJ Macro Extensions","StackReg ");//"Area Opening","Shape Smoothing","ROI Color Coder",//space after StackReg
	check_plugin_install(checks);
	print("******Plugins installed.*******");
	//takes an array of commands as strings and checks if plugins are installed
	function check_plugin_install(plugin_command)
	{
		List.setCommands;
		error=0;
		for (i = 0; i < plugin_command.length; i++) 
		{
			if (List.get(plugin_command[i])!="") 
			{
				if(plugin_command[i]=="Command From Macro") {msg="StarDist";}
				else {msg=plugin_command[i];}
				print(msg+"... OK!");
			}
			else 
			{ 
				if(plugin_command[i]=="ROI Color Coder"){msg="Enable the BAR update site";}
				else if (plugin_command[i]=="Area Opening") {msg="Activate the IJPB-plugins update site";}
				else if (plugin_command[i]=="CLIJ Macro Extensions") {msg="Enable the  update site for CLIJ and CLIJ2: https://clij.github.io/clij2-docs/installationInFiji";}
				else if (plugin_command[i]=="Command From Macro") {msg="Enable the update site for StarDist and CSBDeep";}
				else if (plugin_command[i]=="DeepImageJ Run") {msg="Add the update site for DeepImageJ: https://sites.imagej.net/DeepImageJ/";}
				else if (plugin_command[i]=="StackReg ") {msg="Enable the update site for BIG-EPFL";} //added a space after StackReg
				else if (plugin_command[i]=="Label image to ROIs"){msg="Enable the update site for PT-BIOP: https://biop.epfl.ch/Fiji-Update/";}
				else {msg=plugin_command[i];}
				print("Error: Install plugin: "+msg);
				error=1;
			}
	
		}
		if(error==1) exit("Plugins not found. Check Log file for details");
	}
	
	print("******DeepImageJ Initialization checks.*******");
	gat_deepimagej_path = gat_dir+fs+"gat_init_deepimagej_check.ijm";
	runMacro(gat_deepimagej_path);
	print("******DONE.*******");

}
