//check installation of plugins
macro "check_plugins" 
{
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
	print("Visit https://github.com/pr4deepr/GutAnalysisToolbox/wiki/Troubleshooting for more info");
	run("Clear Results");
	selectWindow("Results");
	run("Close");
	//****************
	
	print("******Checking if plugins are installed.*******");
	checks=newArray("DeepImageJ Run","Area Opening","Command From Macro","CLIJ Macro Extensions","StackReg");//"Area Opening","Shape Smoothing","ROI Color Coder",
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
				else if (plugin_command[i]=="StackReg") {msg="Enable the update site for BIG-EPFL: https://sites.imagej.net/DeepImageJ/";}
				else {msg=plugin_command[i];}
				print("Error: Install plugin: "+msg);
				error=1;
			}
	
		}
		if(error==1) exit("Plugins not found. Check Log file for details");
	}

}
