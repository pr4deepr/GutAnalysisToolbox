package Features.Core;


import java.awt.*;
import java.util.List;

/**
 * Bag of global parameters for all analysis workflows.
 *
 * One instance of this is passed into pipelines (Hu-only, multi-marker, no-Hu, spatial, etc).
 * UI code writes into this, and the pipelines read from it.
 *
 * Key groups:
 *  - I/O / source image config
 *  - neuron segmentation config (StarDist + postprocessing)
 *  - ganglia segmentation config
 *  - spatial analysis config
 *  - calcium imaging analysis config
 *  - temporal color projection config
 *
 * Many fields map to legacy ImageJ macro parameters so we keep names 1:1
 * (like trainingPixelSizeUm, neuronSegLowerLimitUm, etc).
 */
public class Params {

    //Ganglia no hu param
    public Integer gangliaCellChannel;


    /** If null, uses current image in Fiji. */
    // Input/output
    public String imagePath = null;       // if null, uses current ImageJ active image
    public String roiPath;                // Path to ROI zip file (if importing)
    public String outputDir = null;       // parent output dir (optional). We will create Analysis/<baseName> inside

    /** 1-based channel index for Hu segmentation. */
    public int huChannel = 3;


    /** StarDist 2D model (.zip) for neurons. */
    public String stardistModelZip;

    // Rescaling to model training resolution (faithful to macro)
    public boolean rescaleToTrainingPx = true;
    public double trainingPixelSizeUm = 0.568; // matches your settings
    public double trainingRescaleFactor = 1.0;                 // macro “Rescaling Factor” knob (default 1.0)

    // DeepImageJ model dir (folder)
    public String neuronDeepImageJModelDir; // e.g. <Fiji>/models/2D_enteric_neuron.bioimage.io.model

    /** If true and Z>1, use CLIJ2 EDF; else MIP. */
    public boolean useClij2EDF = false;


    /** Require microns/um calibration. */
    public boolean requireMicronUnits = true;

    // Post-processing knobs (macro names: prob_neuron, overlap_neuron)
    public double probThresh = 0.5;
    public double nmsThresh = 0.3;

    // Size filtering (pixel-area threshold derived from microns)
    public Double neuronSegLowerLimitUm = 70.0;   // from settings (in microns)

    /** Save flattened overlay image with ROIs. */
    public boolean saveFlattenedOverlay = true;

    /** For labels/CSVs. */
    public String cellTypeName = "Neuron";

    // Size filter (faithful to macro’s conversion: microns ÷ pixelWidth)
    public Double neuronSegMinMicron = 70.0; // “neuron_seg_lower_limit” in microns


    // Ganglia analysis toggle + mode
    public boolean cellCountsPerGanglia = false;

    public enum GangliaMode { DEEPIMAGEJ, DEFINE_FROM_HU, MANUAL, IMPORT_ROI }
    public GangliaMode gangliaMode = GangliaMode.DEEPIMAGEJ;

    // Channels & models
    public int gangliaChannel = 1;                 // 1-based (required for DEEPIMAGEJ)
    public String gangliaModelFolder = "";   // e.g. Fiji/models/2D_Ganglia_RGB_v2.bioimage.io.model

    // 'Define using Hu' options
    public double huDilationMicron = 12.0;         // radius to grow somata before union

    // Custom ROI
    public String customGangliaRoiZip = null;

    public Double gangliaProbThresh01 = 0.35;   // 0..1 threshold for DIJ prob map
    public Double gangliaMinAreaUm2   = 120.0; // try 800–2000 µm²
    public Boolean gangliaUsePreprocessing = true; // toggle pre script
    public int     gangliaOpenIterations   = 3;
    public boolean gangliaInteractiveReview = true;

    public Boolean doSpatialAnalysis = false;
    public Double  spatialExpansionUm = 6.5;     // microns
    public Boolean spatialSaveParametric = false;
    public String  spatialCellTypeName = "Hu";

    public Window uiAnchor;


    // Calcium imaging alignment defaults
    public int referenceFrame = 1;            // default first frame
    public boolean normalizeToBaseline = false; // default: no normalization
    public int baselineStart = 1;             // default start frame
    public int baselineEnd = 5;               // default end frame
    public boolean saveAlignedStack = false;  // default: don't save aligned stack
    public boolean subpixel = false;
    public boolean useSIFT = false;
    public boolean useTemplateMatching = true;
    public boolean useStackReg = true;
    public String inputDir = null; 
    public String fileExt = null; 


    // Calcium Imaging Analysis
    public boolean useFF0 = true;          // F/F₀ normalization
    public boolean useStarDist = false;    // use StarDist segmentation
    public int cellTypes = 1;              // cell type
    public boolean importROIs = false;
    public int maxStart = 1;
    public int maxEnd = 50;
    public int numCellTypes = 1;   
    public List<String> cellNames;

    // Temporal Colour Analysis
    public int referenceFrameEnd = 5;  
    public String lutName = null;
    public String projectionMethod = null; 
    public boolean createColorScale = false;
    public boolean batchMode = false;




    /**
     * Deep copy (clone) of this Params.
     * Only copies fields that are currently used by the pipelines/UI.
     *
     * This is used when we branch a set of Params to run a slightly different
     * pipeline (e.g. per-marker) without mutating the original object
     * that the GUI is still editing.
     *
     * @return a new Params with duplicated scalar values and references
     *         to mutable objects where appropriate (e.g. uiAnchor is carried over,
     *         Lists are NOT deeply copied here except where handled externally).
     */
    public Params copy() {
      Params c = new Params();
      c.gangliaCellChannel = this.gangliaCellChannel;

      c.imagePath = this.imagePath;
      c.outputDir = this.outputDir;
      c.huChannel = this.huChannel;

      c.stardistModelZip = this.stardistModelZip;

      c.rescaleToTrainingPx   = this.rescaleToTrainingPx;
      c.trainingPixelSizeUm   = this.trainingPixelSizeUm;
      c.trainingRescaleFactor = this.trainingRescaleFactor;

      c.neuronDeepImageJModelDir = this.neuronDeepImageJModelDir;

      c.useClij2EDF = this.useClij2EDF;
      c.requireMicronUnits = this.requireMicronUnits;

      c.probThresh = this.probThresh;
      c.nmsThresh  = this.nmsThresh;

      c.neuronSegLowerLimitUm = this.neuronSegLowerLimitUm;
      c.saveFlattenedOverlay  = this.saveFlattenedOverlay;
      c.cellTypeName = this.cellTypeName;
      c.neuronSegMinMicron = this.neuronSegMinMicron;

      c.cellCountsPerGanglia = this.cellCountsPerGanglia;
      c.gangliaMode = this.gangliaMode;
      c.gangliaChannel = this.gangliaChannel;
      c.gangliaModelFolder = this.gangliaModelFolder;
      c.huDilationMicron = this.huDilationMicron;
      c.customGangliaRoiZip = this.customGangliaRoiZip;

      c.gangliaProbThresh01 = this.gangliaProbThresh01;
      c.gangliaMinAreaUm2   = this.gangliaMinAreaUm2;
      c.gangliaUsePreprocessing = this.gangliaUsePreprocessing;
      c.gangliaOpenIterations   = this.gangliaOpenIterations;
      c.gangliaInteractiveReview = this.gangliaInteractiveReview;

      c.doSpatialAnalysis = this.doSpatialAnalysis;
      c.spatialExpansionUm = this.spatialExpansionUm;
      c.spatialSaveParametric = this.spatialSaveParametric;
      c.spatialCellTypeName = this.spatialCellTypeName;

      c.uiAnchor = this.uiAnchor;
      return c;
  }

}



