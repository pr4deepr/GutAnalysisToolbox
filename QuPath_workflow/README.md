## Using GAT workflows in QuPath

You can find more details on using GAT with QuPath here in docs: https://gut-analysis-toolbox.gitbook.io/docs/4.-qupath-for-analysing-ens

### Files in this folder:

**StarDist models for QuPath**
  * **2D_enteric_neuron_v4_1.pb**: Neuronal models for Hu (Tip, if you adjust rescaling factor, you can also detect Sox10)
  * **2D_enteric_neuron_subtype_v4.pb**: Neuronal subtype model
    
**GAT cell detection scripts**
  * **GAT_Cell_detection.groovy**: Cell detection using StarDist. This has a user interface so you don't need to modify values in the script
  * **GAT_Stardist_batch_process.groovy**: Cell detection script using StarDist with no interface. Modify values in script and can use for batch processing.
  * **GAT_divide_image_width.groovy**: Divide a tissue image annoation into tiles. Useful if you want to study cellular distribution along length of the tissue.
  * **export_ROI_class.groov**y: Can export detections or annotations of a specific class as ROIManager files. This can then be imported into the GAT Fiji workflow

