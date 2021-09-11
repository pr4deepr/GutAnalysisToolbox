
//based on macro by Olivier Burri: https://forum.image.sc/t/selecting-roi-based-on-name/3809
//pass roi name as argument
//selects ROIs which match the string

macro "find_roi_name"
{
	args=getArgument();
	roiName=toLowerCase(args);
	print(roiName);
	nR = roiManager("Count"); 
	roiIdx = newArray(nR); 
	k=0; 
	clippedIdx = newArray(0); 
	regex=".*"+roiName+".*";
	for (i=0; i<nR; i++) 
	{ 
		roiManager("Select", i); 
		rName = toLowerCase(Roi.getName()); 
		if (matches(rName, regex)) 
		{ 
			roiIdx[k] = i; 
			k++; 
			print(i);
		} 
	} 
	if (k>0) 
	{ 
		clippedIdx = Array.trim(roiIdx,k); 
		roiManager("select", clippedIdx);
	} 
	else roiManager("deselect");
	
}
