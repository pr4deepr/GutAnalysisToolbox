# Gut Analysis Toolbox
[![DOI:10.1101/2024.01.17.576140](http://img.shields.io/badge/DOI-10.1101/2024.01.17.576140-B31B1B.svg)](https://doi.org/10.1101/2024.01.17.576140)


**To get started with using GAT, please go to the** [**Documentation**](https://gut-analysis-toolbox.gitbook.io/docs/).

***********
<p align="center">
<img src="https://github.com/pr4deepr/GutAnalysisToolbox/blob/main/wiki_images/figures/gat_summary_fig.png" alt="GAT_overview" width="700" >
</p>

Gut Analysis Toolbox or GAT allows the semi-automated analysis of the cells within the enteric nervous system of the gastrointestinal tract in **2D**. GAT enables quantification of enteric neurons and their subtypes in **gut wholemounts**. It can run in FIJI or QuPath, popular image analysis softwares in microscopy and uses deep learning models to segment cells of interest. 

The workflows are available as video tutorials on [Youtube in two separate playlists for Fiji and QuPath](https://www.youtube.com/channel/UC03y9hDwDsVAhgeebyWpoew/playlists).


What you can do with GAT:
* Semi-automated analysis of number of enteric neurons: Uses pan-neuronal marker Hu or anything with similar 
 labelling
* Normalise counts to the number of ganglia.
* Count number of neuronal subtypes, such as ChAT, nNOS etc..
* Spatial analysis using number of neighboring cells.
* Calcium imaging analysis: Alignment of images and extraction of normalised traces (Fiji)


## Reporting problems

If you have any difficulties, suggestions or find any bugs, you can either:

* Use [this link](https://forms.gle/oEpFMtQo29Dr9AQT7) to access a Google form for submitting issues or questions.

OR

* Create a post under [Issues](https://github.com/pr4deepr/GutAnalysisToolbox/issues) 

OR

* Post the problem on the [Imagesc forum](https://forum.image.sc/) and tag @pr4deepr


## Installing and configuring GAT in Fiji

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
* PT-BIOP


GAT update site: https://sites.imagej.net/GutAnalysisToolbox/

***********

## Model files for use in FIJI

The GAT models are located in `Fiji.app/models` folder and contains 3 separate model files:

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
- **Ganglia model folder: 2D_Ganglia_RGB_v2**
  
  DeepImageJ-based UNet model to segment ganglia. Needs both Hu and a neuronal/glial marker labelling the ganglia

### Model files for use in QuPath

[Click to Download QuPath model and scripts](https://wehieduau-my.sharepoint.com/:u:/g/personal/rajasekhar_p_wehi_edu_au/EdYxRodrJLNJj4wK77erHA0BfVKDJpOktgWQ3iIyLaUU1g?download=1)

Check here for more detail on using [QuPath models](https://github.com/pr4deepr/GutAnalysisToolbox/wiki/4.-QuPath-for-analysing-ENS)

**********************

### Accessing training data

To download the training data, notebooks and associated models please go to the following Zenodo link:

[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.6096664.svg)](https://doi.org/10.5281/zenodo.6096664)

**********************
## Citing

[Sorensen, L., A. Humenick, S. S. B. Poon, M. N. Han, N. S. Mahdavian, R. Hamnett, E. Gómez-de-Mariscal, P. H. Neckel, A. Saito, K. Mutunduwe, C. Glennan, R. Haase, R. M. McQuade, J. P. P. Foong, S. J. H. Brookes, J. A. Kaltschmidt, A. Muñoz-Barrutia, S. K. King, N. A. Veldhuis, S. E. Carbone, D. P. Poole and P. Rajasekhar (2024). "Gut Analysis Toolbox: Automating quantitative analysis of enteric neurons." bioRxiv: 2024.2001.2017.576140.](https://www.biorxiv.org/content/10.1101/2024.01.17.576140v1)
