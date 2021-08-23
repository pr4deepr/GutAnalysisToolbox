//Gets probability map from deepimageJ which is 3 channel output
//first channel has the prediction
//duplicate that and apply a threshold of 0.8 on the probability map


macro "ganglia_prediction_post"
{
	
	//check if argument is passed, if not asks user to select label image
	if(getArgument()=="")
	{
		waitForUser("Select the prediction from deepImageJ");
		multi_ch_prediction=getTitle(); 
		
	}
	else 
	{
		multi_ch_prediction=getArgument(); 
	}

	selectWindow(multi_ch_prediction);
	run("Select None");
	run("Duplicate...", "title=ganglia_prediction duplicate channels=1");
	close(multi_ch_prediction);

	selectWindow("ganglia_prediction");
	setThreshold(0.8, 1);
	setOption("BlackBackground", true);
	run("Convert to Mask");

	resetMinAndMax;
	
	
}


