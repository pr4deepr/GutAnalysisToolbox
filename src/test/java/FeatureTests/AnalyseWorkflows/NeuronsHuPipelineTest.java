package FeatureTests.AnalyseWorkflows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import Features.AnalyseWorkflows.NeuronsHuPipeline;
import Features.Core.Params;
import Features.Core.PluginCalls;
import Features.Tools.ImageOps;
import Features.Tools.OutputIO;
import Features.Tools.ProgressUI;
import Features.Tools.RoiManagerHelper;
import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.plugin.frame.RoiManager;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NeuronsHuPipelineTest {

    @Mock
    ProgressUI progressUI;

    @Test
    void testRun_valid() {
        // Initialise relevant variables in order of call

        // Non-mocks
        Params params = new Params();
        params.stardistModelZip = "model.zip";
        params.huChannel = 1;
        params.cellTypeName = "Neuron";
        params.trainingRescaleFactor = 1.0;
        params.trainingPixelSizeUm = 1.0;
        params.rescaleToTrainingPx = false;
        params.probThresh = 0.5;
        params.nmsThresh = 0.5;
        params.neuronSegLowerLimitUm = 1.0;
        params.outputDir = "output";
        params.requireMicronUnits = false;
        params.useClij2EDF = false;
        params.saveFlattenedOverlay = false;
        params.cellCountsPerGanglia = false;

        // Mocks (non-static)
        ImagePlus imp = mock(ImagePlus.class);
        ImagePlus max = mock(ImagePlus.class);
        ImagePlus hu = mock(ImagePlus.class);
        ImagePlus labels = mock(ImagePlus.class);
        ImagePlus correctedBinary = mock(ImagePlus.class);
        ImagePlus labelsEdited = mock(ImagePlus.class);
        RoiManager rm = mock(RoiManager.class);
        File outDir = mock(File.class);
        Calibration calibration = new Calibration();
        calibration.pixelWidth = 1.0;
        calibration.setUnit("pixel");

        // Mock function calls
        doNothing().when(progressUI).step(anyString());
        when(imp.getTitle()).thenReturn("test.tif");
        when(imp.getCalibration()).thenReturn(calibration);
        when(imp.getNSlices()).thenReturn(1);
        when(imp.duplicate()).thenReturn(max);
        when(max.getCalibration()).thenReturn(calibration);
        when(max.getWidth()).thenReturn(100);
        when(max.getHeight()).thenReturn(100);
        when(hu.duplicate()).thenReturn(hu);
        doNothing().when(hu).show();
        doNothing().when(rm).setVisible(anyBoolean());
        when(rm.runCommand(any(ImagePlus.class), anyString())).thenReturn(true);
        when(rm.getCount()).thenReturn(5);
        when(hu.getCalibration()).thenReturn(calibration);
        when(labels.getWidth()).thenReturn(100);
        when(labels.getHeight()).thenReturn(100);

        // Mock static calls
        try (
                MockedStatic<IJ> ijMock = mockStatic(IJ.class);
                MockedStatic<OutputIO> outputIOMock = mockStatic(OutputIO.class);
                MockedStatic<RoiManager>  roiManagerMock = mockStatic(RoiManager.class);
                MockedStatic<ImageOps> imageOpsMock = mockStatic(ImageOps.class);
                MockedStatic<PluginCalls> pluginCallsMock = mockStatic(PluginCalls.class);
                MockedStatic<RoiManagerHelper> rmHelperMock = mockStatic(RoiManagerHelper.class);
                MockedStatic<NeuronsHuPipeline> neuronsHuPipelineMock = mockStatic(NeuronsHuPipeline.class, CALLS_REAL_METHODS);
                MockedConstruction<ij.gui.WaitForUserDialog> waitForUserDialogMock = Mockito.mockConstruction(ij.gui.WaitForUserDialog.class,
                        (mock, context) -> doNothing().when(mock).show());
                MockedConstruction<File> fileMock = Mockito.mockConstruction(File.class,
                        (fMock, fContext) -> when(fMock.isFile()).thenReturn(true));
        ) {
            ijMock.when(IJ::getImage).thenReturn(imp);
            outputIOMock.when(() -> OutputIO.prepareOutputDir(anyString(), any(ImagePlus.class), anyString())).thenReturn(outDir);
            rmHelperMock.when(RoiManagerHelper::ensureGlobalRM).thenReturn(new RoiManagerHelper.RmHandle(rm, false));
            imageOpsMock.when(() -> ImageOps.extractChannel(any(ImagePlus.class), anyInt())).thenReturn(hu);
            pluginCallsMock.when(() -> PluginCalls.runStarDist2DLabel(any(ImagePlus.class), anyString(), anyDouble(), anyDouble())).thenReturn(labels);
            pluginCallsMock.when(() -> PluginCalls.removeBorderLabels(any(ImagePlus.class))).thenReturn(labels);
            pluginCallsMock.when(() -> PluginCalls.labelMinSizeFilterPx(any(ImagePlus.class), anyInt())).thenReturn(labels);
            pluginCallsMock.when(() -> PluginCalls.labelsToRois(any(ImagePlus.class))).thenAnswer(invocation -> null);
            rmHelperMock.when(() -> RoiManagerHelper.syncToSingleton(any(RoiManager[].class))).thenAnswer(invocation -> null);
            ijMock.when(() -> IJ.run(any(ImagePlus.class), anyString(), anyString())).thenAnswer(invocation -> null);
            ijMock.when(() -> IJ.resetMinAndMax(any(ImagePlus.class))).thenAnswer(invocation -> null);
            pluginCallsMock.when(() -> PluginCalls.roisToBinary(any(ImagePlus.class), any(RoiManager.class))).thenReturn(correctedBinary);
            neuronsHuPipelineMock.when(() -> NeuronsHuPipeline.applyWatershedInPlace(any(ImagePlus.class))).thenAnswer(invocation -> null);
            pluginCallsMock.when(() -> PluginCalls.binaryToLabels(any(ImagePlus.class))).thenReturn(labelsEdited);
            outputIOMock.when(() -> OutputIO.saveRois(any(RoiManager.class), any(File.class))).thenAnswer(invocation -> null);
            outputIOMock.when(() -> OutputIO.saveTiff(any(ImagePlus.class), any(File.class))).thenAnswer(invocation -> null);
            outputIOMock.when(() -> OutputIO.writeCountsCsv(any(File.class), anyString(), anyString(), anyInt())).thenAnswer(invocation -> null);

            ijMock.when(() -> IJ.setTool(anyString())).thenReturn(true);

            // Act
            NeuronsHuPipeline pipeline = new NeuronsHuPipeline();
            NeuronsHuPipeline.HuResult result = pipeline.run(params, true, progressUI);

            /*
             * Verify the following interactions:
             * result is not null
             * The input image is opened
             * Output directory is prepared
             * A max projection is created
             * The HU channel is extracted
             * StarDist is run for neuron segmentation
             * Border labels are removed
             * Conversion from labels to ROIs
             * Show Hu image
             * rm overlay is set to visible
             * user is prompted to edit ROIs
             * rebuild rm from edited labels
             * convert edited ROIs to binary
             * watershed is applied
             * binary is converted back to labels
             * ROIs are counted and saved
             * Images are saved
             * csv is written
             * window is hidden
             * */

            // result is not null
            assertNotNull(result);

            // The input image is opened
            ijMock.verify(IJ::getImage, times(1));

            // Output directory is prepared
            outputIOMock.verify(() -> OutputIO.prepareOutputDir(anyString(), any(ImagePlus.class), anyString()), times(1));

            // A max projection is created (duplicate of imp)
            verify(imp, times(1)).duplicate();

            // The HU channel is extracted
            imageOpsMock.verify(() -> ImageOps.extractChannel(max, params.huChannel), times(1));

            // StarDist is run for neuron segmentation
            pluginCallsMock.verify(() -> PluginCalls.runStarDist2DLabel(hu, params.stardistModelZip, params.probThresh, params.nmsThresh), times(1));

            // Border labels are removed
            pluginCallsMock.verify(() -> PluginCalls.removeBorderLabels(labels), times(1));

            // Conversion from labels to ROIs
            pluginCallsMock.verify(() -> PluginCalls.labelsToRois(labels), times(1));

            // Show Hu image
            verify(hu, times(1)).show();

            // rm overlay is set to visible
            verify(rm, times(1)).setVisible(true);

            // rebuild rm from edited labels
            pluginCallsMock.verify(() -> PluginCalls.binaryToLabels(correctedBinary), times(1));

            // convert edited ROIs to binary
            pluginCallsMock.verify(() -> PluginCalls.roisToBinary(hu, rm), times(1));

            // watershed is applied
            neuronsHuPipelineMock.verify(() -> NeuronsHuPipeline.applyWatershedInPlace(correctedBinary), times(1));

            // binary is converted back to labels
            pluginCallsMock.verify(() -> PluginCalls.binaryToLabels(correctedBinary), times(1));

            // ROIs are counted and saved
            outputIOMock.verify(() -> OutputIO.saveRois(eq(rm), any(File.class)), times(1));

            // Images are saved
            outputIOMock.verify(() -> OutputIO.saveTiff(eq(labelsEdited), any(File.class)), times(1));
            outputIOMock.verify(() -> OutputIO.saveTiff(eq(max), any(File.class)), times(1));

            // csv is written
            outputIOMock.verify(() -> OutputIO.writeCountsCsv(any(File.class), eq("test"), eq(params.cellTypeName), eq(5)), times(1));

            // window is hidden
            verify(rm, times(1)).setVisible(false);

            // Verify result contents
            assertEquals(outDir, result.outDir);
            assertEquals("test", result.baseName);
            assertEquals(max, result.max);
            assertEquals(labelsEdited, result.neuronLabels);
            assertEquals(5, result.totalNeuronCount);
            assertNull(result.gangliaLabels);
            assertNull(result.neuronsPerGanglion);
            assertNull(result.gangliaAreaUm2);
            assertNull(result.nGanglia);
            assertEquals(params.doSpatialAnalysis, result.doSpatialAnalysis);
        }
    }
}
