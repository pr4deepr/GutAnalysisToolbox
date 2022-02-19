# Gut Analysis Toolbox

[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.6095822.svg)](https://doi.org/10.5281/zenodo.6095822)


**To get started with using GAT, please go to the** [**Wiki page**](https://github.com/pr4deepr/GutAnalysisToolbox/wiki).

***********
<p align="center">
<img src="https://github.com/pr4deepr/GutAnalysisToolbox/blob/main/wiki_images/figures/summary_figure.png" alt="GAT_overview" width="600" >
</p>

Gut Analysis Toolbox or GAT allows the semi-automated analysis of the cells within the enteric nervous system of the gastrointestinal tract in **2D**. GAT enables quantification of enteric neurons and their subtypes. It runs in FIJI, a popular image analysis software in microscopy and uses deep learning models to segment cells of interest. 

You can also watch tutorials for GAT on [Youtube](https://www.youtube.com/playlist?list=PLmBt1Dumq60p4mIFT4j7TP_PVRjbO55Oi).

If you have any difficulties, suggestions or find any bugs, create a post under [Issues](https://github.com/pr4deepr/GutAnalysisToolbox/issues) or you could use the [Imagesc forum](https://forum.image.sc/). Feel free to use the [discussions](https://github.com/pr4deepr/GutAnalysisToolbox/discussions) if you'd like input on your analysis or would like to discuss relevant topics. 

## Installing and configuring GAT

Click  on this video to watch how to install and configure FIJI and GAT

[![Youtube](https://img.youtube.com/vi/GmE_lz-m0Rg/0.jpg)](https://www.youtube.com/playlist?list=PLmBt1Dumq60p4mIFT4j7TP_PVRjbO55Oi)

GAT requires the following update sites:
* BIG-EPFL
* CSBDeep
* clij
* clij2
* DeepImageJ
* IJBP-Plugins (MorphoLibJ)
* StarDist


GAT update site: https://sites.imagej.net/GutAnalysisToolbox/

***********
## Download model files

### Model files for use in FIJI

The model files are zipped and ready to download here:

[Click to Download GAT FIJI model folders](https://wehieduau-my.sharepoint.com/:u:/g/personal/rajasekhar_p_wehi_edu_au/Ecg001ngdvhBgRaWxVakPecBF8d5Qb361PgXYFrcxp8Azw?download=1)


The GAT FIJI models folder contains the 3 files required for running GAT in FIJI:

- **Enteric neuron model: 2D_enteric_neuron_v4_1.zip**
  
  StarDist model to segment enteric neurons labelled with Hu, a pan-neuronal marker
- **Enteric neuron subtype model: 2D_enteric_neuron_subtype_v4_1.zip**
  
  StarDist model to segment enteric neuronal subtypes. It has been trained on images with labelling for:
  * neuronal nitric oxide synthase (nNOS)
  * Calbindin
  * Calretinin
  * Mu-opioid receptor (MOR) reporter (mCherry)
  * Delta-opioid receptor (DOR) reporter (GFP)
  * Choline acetyltransferase (ChAT)
  * Neurofilament (NFM)
- **Ganglia model folder: 2D_enteric_ganglia_v2.bioimage.io.model**
  
  DeepImageJ-based UNet model to segment ganglia. Needs both Hu and a neuronal/glial marker labelling the ganglia

Click [here](https://www.youtube.com/watch?v=RIvaXL-Q7Go&list=PLmBt1Dumq60p4mIFT4j7TP_PVRjbO55Oi) to see how to configure models after GAT installation in FIJI.

### Model files for use in QuPath

[Click to Download QuPath model and scripts](https://wehieduau-my.sharepoint.com/:u:/g/personal/rajasekhar_p_wehi_edu_au/EdYxRodrJLNJj4wK77erHA0BfVKDJpOktgWQ3iIyLaUU1g?download=1)

Check here for more detail on using [QuPath models](https://github.com/pr4deepr/GutAnalysisToolbox/wiki/4.-QuPath-for-analysing-ENS)

**********************

### Accessing training data

To download the training data, notebooks and associated model reports go to:

[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.6096664.svg)](https://doi.org/10.5281/zenodo.6096664)

**********************
## Citing

Software citation on Zenodo:

Luke Sorensen, Ayame Saito, Sabrina Poon, Myat Noe Han, Adam Humenick, Keith Mutunduwe, Christie Glennan, Narges Mahdavian, Simon JH Brookes, Rachel M McQuade, Jaime PP Foong, Sebastian K. King, Estibaliz Gómez-de-Mariscal, Robert Haase, Simona Carbone, Nicholas A. Veldhuis, Daniel P. Poole, & Pradeep Rajasekhar. (2022). Gut Analysis Toolbox (GAT): 2D models for segmenting enteric neurons, neuronal subtypes and ganglia (1.0). Zenodo. https://doi.org/10.5281/zenodo.6095590

Upon publication, the paper will be included here.
