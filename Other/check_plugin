//check installation of plugins
macro "check_plugins" 
{
	print("******Checking if plugins are installed.*******");
	checks=newArray("DeepImageJ Run","Area Opening","Command From Macro","ROI Color Coder","CLIJ Macro Extensions");//"Area Opening","Shape Smoothing",
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
				else if (plugin_command[i]=="Command From Macro") {msg="Enable the update site for StarDist";}
				else if (plugin_command[i]=="DeepImageJ Run") {msg="Add the update site for DeepImageJ: https://sites.imagej.net/DeepImageJ/";}
				else {msg=plugin_command[i];}
				print("Error: Install plugin: "+msg);
				error=1;
			}
	
		}
		if(error==1) exit("Plugins not found. Check Log file for details");
	}

}
