/***Calling Analyze_Neurons from another macro
 * will need to be called as run(" Analyse Neurons"); with a space at start of the command. This will bring up the dialog box for entering values
 * 
 * If arguments need to specified, they can be specified in a variable args and called as run(" Analyse Neurons",args);
 * 
 * boolean needs to be passed as a string, i.e., true should be "true"
 * The dialogboxes won't work when calling from another macro, so will need to manually specify values if you want to specify custom
 * probability and overlap values, and if you want to do spatial analysis. Otherwise, defaults end up being used
 * Defaults are:
 * probability = 0.5
 * overlap = 0.3
 * For spatial analysis, if set to true, defaults are
 * label_dilation = 6.5  #micron
 * 
***/
//cannot use dialogbox when calling from macro as we use script parameters in main code; some incompatibility

//Specify values for the Dialog box when clicking "Analyze Neuron"
//The defaults are the last value you used when running the code

//Choose image to segment -> specify image path
path = "C:/GAT/Sample Images/2D_enteric_neuron_IF/181107_ms_distal_colon_GFAP_Hu_40X.tif";

///specify if image is already open: "true" or "false"
image_already_open="false";

//Channel number for Hu (pan-neuronal marker)
cell_channel=2;

//checkbox if you want to count cells per ganglia: "true" or "false". 
cell_counts_per_ganglia="true";

//Ganglia detection method. Either of ["DeepImageJ","Define ganglia using Hu","Import custom ROI"]. In this mode we are not using "Manually draw ganglia"
ganglia_detection="DeepImageJ"; 

//channel number in image for segmenting ganglia; only valid for DeepImageJ
ganglia_channel=1;

//If "Import custom ROI" is used, we need to specify path for imagej roi file containing ganglia rois
ganglia_roi_path= "";

//if "Define ganglia using Hu" is chosen, need to specify the cell expansion distance to define the ganglia. Leave as is for defaults
cell_expansion=12; //micron

// if specifying perform_spatial_analysis or finetune_detection_parameters as true, batch parameters have to be used to pass the values; otherwise defaults will be used
//perform_spatial_analysis: "true" or "false". 
perform_spatial_analysis="false";//leave as "false" if not using
//specify the label_dilation value (nearest neighbour distance) in micron; default is 6.5 micron
label_dilation=6.5;
//save_parametric_image:"true" or "false", which if true will save colour coded image with colour specifying number of nearest neighbours
save_parametric_image="true";

//finetune_detection_parameters; specify custom probability and overlap values for segmenting neurons
//if true, need to specify probability, overlap and scale (rescaling factor ) or defaults are used 
finetune_detection_parameters="false"; //"true" or "false". leave as "false" if not using
//probability value: value between 0 to 1
probability=0.5;
//overlap: value between 0 to 1
overlap=0.3;
//scaling factor. value of 1 will rescale image to match images from training dataset of the model (0.568 micron per pixel)
scale=1;
//cannot specify custom roi for hu yet

//if you want to save image masks for your analysis; leave as is if not using
contribute_to_gat="false"; //"true" or "false
img_masks_path="NA"; //directory location to save image and masks"


//batch parameters can be "NA" or specify values corresponding to spatial analysis, finetune detection parameters,  contribute_to_gat and ganglia segmentation (ROI or define ganglia using Hu)
//to pass values for spatial analysis or finetune_detection parameters, they need to be specified under batch_parameters
//they will have to be passed as a string and separated by comma
//They have to be passed in the order
//ganglia_roi_path, label_dilation,save_parameteric_image,scale,probability,overlap,img_masks_path,cell_expansion
//you can pass the defaults shown above if you don't want to change anything 
batch_parameters = ganglia_roi_path+","+label_dilation +","+save_parametric_image +","+scale +","+probability +","+overlap +","+img_masks_path+","+cell_expansion;
//if not using, pass "NA"
//batch_parameters="NA";

//all arguments together in one long string. Note that strings with spaces will need  to be enclosed in brackets, such as path, ganglia_detection and batch_parameters
args = "path=["+path+"] image_already_open=false cell_channel="+cell_channel+" cell_counts_per_ganglia="+cell_counts_per_ganglia+" ganglia_detection=["+ganglia_detection+"] ganglia_channel="+ganglia_channel+" perform_spatial_analysis="+perform_spatial_analysis+" finetune_detection_parameters="+finetune_detection_parameters+" contribute_to_gat="+contribute_to_gat+" batch_parameters=["+batch_parameters+"]";

//call Analyse Neurons -> Note that the command has a space at the start
run(" Analyse Neurons",args);
