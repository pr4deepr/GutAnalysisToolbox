package FeatureTests.AnalyseWorkflows;

import Features.AnalyseWorkflows.GangliaOps;
import Features.Core.Params;
import Features.Core.PluginCalls;
import Features.Tools.ProgressUI;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.WaitForUserDialog;
import ij.measure.Calibration;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static Features.AnalyseWorkflows.GangliaOps.segment;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GangliaOpsTest {

    // Mocks for segment function
    @Mock
    ImagePlus neuronLabels;

    @Mock
    ImagePlus maxProjection;

    @Mock
    ProgressUI progressUI;

    /**
     * Test the segment method of GangliaOps class with ganglia mode DEFINE_FROM_HU.
     */
    @Test
    void testSegmentDefineFromHu() {
        // Mock calibration for maxCalibration image
        Calibration calibration = new Calibration();
        calibration.pixelWidth = 2.0; // 2 microns per pixel
        when(maxProjection.getCalibration()).thenReturn(calibration);

        // Set up Params with desired dilation
        Params p = new Params();
        p.huDilationMicron = 4.0; // Dilate by 4 microns
        p.gangliaMode = Params.GangliaMode.DEFINE_FROM_HU;

        // Mock static IJ and PluginCalls methods
        try (MockedStatic<IJ> ijMock = mockStatic(IJ.class);
             MockedStatic<PluginCalls> pluginMock = mockStatic(PluginCalls.class)) {

            // Duplicate neuronLabels returns itself for simplicity
            when(neuronLabels.duplicate()).thenReturn(neuronLabels);

            // PluginCalls.binaryToLabels returns a new mock ImagePlus
            ImagePlus labels = mock(ImagePlus.class);
            pluginMock.when(() -> PluginCalls.binaryToLabels(any(ImagePlus.class))).thenReturn(labels);

            // Run the method under test
            ImagePlus result = segment(p, maxProjection, neuronLabels, progressUI);

            /*
             * Verify the following interactions:
             * result is not null
             * labels.setCalibration is called with the correct calibration
             * IJ.run is called the expected number of times for dilation
             * IllegalArgumentException is thrown for negative dilation
             */

            // Assert result is not null and calibration is set
            assertNotNull(result);
            verify(labels).setCalibration(calibration);

            // Check that the number of dilations matches expected iterations
            int expectedIters = 2; // 4 microns / 2 microns per pixel
            ijMock.verify(() -> IJ.run(neuronLabels, "Dilate", ""), times(expectedIters));

            // Assert IllegalArgumentException is thrown for negative dilation
            calibration.pixelWidth = -1.0; // Invalid calibration
            assertThrows(IllegalStateException.class, () -> segment(p, maxProjection, neuronLabels, progressUI));
        }
    }

    /**
     * Test the segment method of GangliaOps class with ganglia mode IMPORT_ROI_TO_LABELS.
     */
    @Test
    void testSegmentImportRoiToLabels() {
        // Mock calibration for maxCalibration image
        Calibration calibration = new Calibration();
        calibration.pixelWidth = 1.0; // 1 micron per pixel
        when(maxProjection.getCalibration()).thenReturn(calibration);

        // Set up Params with custom ROI zip path
        Params p = new Params();
        p.customGangliaRoiZip = "path/to/roi.zip";
        p.gangliaMode = Params.GangliaMode.IMPORT_ROI;

        // Mock static PluginCalls methods
        try (MockedStatic<PluginCalls> pluginMock = mockStatic(PluginCalls.class);
             MockedStatic<RoiManager> roiManagerMock = mockStatic(RoiManager.class)) {

            // Mock RoiManager instance
            RoiManager rm = mock(RoiManager.class);
            roiManagerMock.when(RoiManager::getInstance2).thenReturn(rm);

            // PluginCalls.roisToBinary returns a mock binary ImagePlus
            ImagePlus bin = mock(ImagePlus.class);
            pluginMock.when(() -> PluginCalls.roisToBinary(maxProjection, rm)).thenReturn(bin);

            // PluginCalls.binaryToLabels returns a new mock ImagePlus for labels
            ImagePlus labels = mock(ImagePlus.class);
            pluginMock.when(() -> PluginCalls.binaryToLabels(bin)).thenReturn(labels);

            // Run the method under test
            ImagePlus result = segment(p, maxProjection, neuronLabels, progressUI);

            /*
             * Verify the following interactions:
             * result is not null
             * PluginCalls.roisToBinary is called with maxProjection and RoiManager
             * PluginCalls.binaryToLabels is called with the binary image
             * labels.setCalibration is called with the correct calibration
             * IllegalArgumentException is thrown for empty ROI path
             */

            // Assert result is not null
            assertNotNull(result);

            // Verify roisToBinary and binaryToLabels are called
            pluginMock.verify(() -> PluginCalls.roisToBinary(maxProjection, rm));
            pluginMock.verify(() -> PluginCalls.binaryToLabels(bin));

            // Verify calibration is set correctly
            verify(labels).setCalibration(calibration);

            // Assert IllegalArgumentException is thrown for empty ROI path
            p.customGangliaRoiZip = "";
            assertThrows(IllegalArgumentException.class, () -> segment(p, maxProjection, null, progressUI));
        }
    }

    /**
     * Test the segment method of GangliaOps class with ganglia mode MANUAL.
     */
    @Test
    void testSegmentManualDrawToLabels() {
        // Initialise relevant variables in order of call

        // Non-mocks
        Params p = new Params();                                            // params           (upon function call)
        p.gangliaMode = Params.GangliaMode.MANUAL;
        Calibration calibration = new Calibration();                        // calibration      (line 327)
        calibration.pixelWidth = 1.0; // 1 micron per pixel


        // Mocks (non-static)
        RoiManager rm = mock(RoiManager.class);
        ImagePlus review =  mock(ImagePlus.class);
        ImagePlus bin = mock(ImagePlus.class);
        ImagePlus labels = mock(ImagePlus.class);

        // Mock function calls
        when(maxProjection.getCalibration()).thenReturn(calibration);

        // Mock static variables and function calls
        try (MockedConstruction<WaitForUserDialog> mockedDialog = mockConstruction(
                WaitForUserDialog.class,
                (mock, context) -> doNothing().when(mock).show());
             MockedStatic<PluginCalls> pluginMock = mockStatic(PluginCalls.class);
             MockedStatic<RoiManager> roiManagerMock = mockStatic(RoiManager.class);
             MockedStatic<IJ> ijMock = mockStatic(IJ.class)) {

            // Mock static function calls in order
            roiManagerMock.when(RoiManager::getInstance2).thenReturn(rm);
            pluginMock.when(() -> PluginCalls.buildGangliaRgbForOverlay(maxProjection, p.gangliaChannel, p.huChannel)).thenReturn(review);
            ijMock.when(() -> IJ.setTool(anyString())).thenAnswer(invocation -> null);
            pluginMock.when(() -> PluginCalls.roisToBinary(review, rm)).thenReturn(bin);
            pluginMock.when(() -> PluginCalls.binaryToLabels(bin)).thenReturn(labels);

            // Run the method under test
            ImagePlus result = segment(p, maxProjection, neuronLabels, progressUI);

            /*
             * Verify the following interactions:
             * result is not null
             * PluginCalls.roisToBinary is called with review and RoiManager
             * PluginCalls.binaryToLabels is called with the binary image
             * labels.setCalibration is called with the correct calibration
             */

            // Assert result is not null
            assertNotNull(result);

            // Verify roisToBinary is called
            pluginMock.verify(() -> PluginCalls.roisToBinary(review, rm));

            // Verify binaryToLabels is called
            pluginMock.verify(() -> PluginCalls.binaryToLabels(bin));

            // Verify calibration is set correctly
            verify(labels).setCalibration(calibration);
        }
    }

    /**
     * Test the segment method of GangliaOps class with ganglia mode DEEPIMAGEJ.
     */
    @Test
    void testSegmentDeepImageJ() {
        // Mock calibration for maxCalibration image
        Calibration calibration = new Calibration();
        calibration.pixelWidth = 1.0; // 1 micron per pixel
        when(maxProjection.getCalibration()).thenReturn(calibration);

        // Set up Params with ganglia mode DEEPIMAGEJ
        Params p = new Params();
        p.gangliaMode = Params.GangliaMode.DEEPIMAGEJ;
        p.gangliaModelFolder = "path/to/model";

        // Mock static PluginCalls methods
        try (MockedStatic<PluginCalls> pluginMock = mockStatic(PluginCalls.class)) {

            // PluginCalls.deepImageJForGanglia returns a mock binary ImagePlus
            ImagePlus bin = mock(ImagePlus.class);
            pluginMock.when(() -> PluginCalls.runDeepImageJForGanglia(eq(maxProjection), anyInt(), anyInt(), anyString(), anyDouble(), eq(p), eq(progressUI))).thenReturn(bin);

            // PluginCalls.binaryToLabels returns a new mock ImagePlus for labels
            ImagePlus labels = mock(ImagePlus.class);
            pluginMock.when(() -> PluginCalls.binaryToLabels(bin)).thenReturn(labels);

            // Run the method under test
            ImagePlus result = segment(p, maxProjection, neuronLabels, progressUI);

            /*
             * Verify the following interactions:
             * result is not null
             * PluginCalls.runDeepImageJForGanglia is called with correct parameters
             * PluginCalls.binaryToLabels is called with the binary image
             * labels.setCalibration is called with the correct calibration
             */

            // Assert result is not null
            assertNotNull(result);

            // Verify runDeepImageJForGanglia is called with correct parameters
            pluginMock.verify(() -> PluginCalls.runDeepImageJForGanglia(
                    maxProjection, p.gangliaChannel, p.huChannel, p.gangliaModelFolder, 120.0, p, progressUI));

            // Verify binaryToLabels is called
            pluginMock.verify(() -> PluginCalls.binaryToLabels(bin));

            // Verify calibration is set correctly
            verify(labels).setCalibration(calibration);
        }
    }

    /**
     * Test for countPerGanglion method of GangliaOps class.
     */
    @Test
    void testCountPerGanglion() {
        // Mock ImagePlus for neuronLabels and gangliaLabels
        ImagePlus gangliaLabels = mock(ImagePlus.class);

        // Set up image dimensions
        when(neuronLabels.getWidth()).thenReturn(4);
        when(neuronLabels.getHeight()).thenReturn(4);

        // Mock pixel arrays: neuronLabels has two neurons (id 1 and 2), gangliaLabels has two ganglia (id 1 and 2)
        short[] nl = {
                0, 1, 0, 1,
                2, 0, 0, 2,
                0, 0, 0, 1,
                1, 2, 2, 0,
        };
        short[] gl = {
                1, 1, 2, 0,
                1, 2, 2, 1,
                2, 2, 2, 1,
                0, 1, 1, 1,
        };

        // Mock processors
        ImageProcessor neuronProcessor = mock(ij.process.ImageProcessor.class);
        ImageProcessor gangliaProcessor = mock(ij.process.ImageProcessor.class);
        when(neuronLabels.getProcessor()).thenReturn(neuronProcessor);
        when(gangliaLabels.getProcessor()).thenReturn(gangliaProcessor);
        when(neuronProcessor.convertToShort(false)).thenReturn(neuronProcessor);
        when(gangliaProcessor.convertToShort(false)).thenReturn(gangliaProcessor);
        when(neuronProcessor.getPixels()).thenReturn(nl);
        when(gangliaProcessor.getPixels()).thenReturn(gl);

        // Mock calibration: 2 µm per pixel
        Calibration calibration = new ij.measure.Calibration();
        calibration.pixelWidth = 2.0;
        when(gangliaLabels.getCalibration()).thenReturn(calibration);

        // Run method under test
        GangliaOps.Result result = Features.AnalyseWorkflows.GangliaOps.countPerGanglion(neuronLabels, gangliaLabels);

        /*
         * Verify the following interactions:
         * result is not null
         * maxGanglionId is correct
         * countsPerGanglion are correct
         * areaUm2 are correct
         */

        // Assert result is not null
        assertNotNull(result);

        // maxGanglionId should be 2
        assertEquals(2, result.maxGanglionId);

        // Neuron 1 centroid is at (2,1) -> ganglion 2, Neuron 2 centroid is at (2,2) -> ganglion 2
        assertEquals(0, result.countsPerGanglion[1]);
        assertEquals(2, result.countsPerGanglion[2]);

        // Area: ganglion 1 has 8 pixels, ganglion 2 has 6 pixels
        assertEquals(32.0, result.areaUm2[1]); // 8 * 2 * 2
        assertEquals(24.0, result.areaUm2[2]); // 6 * 2 * 2
    }

    /**
     * Test for areaPerGanglionUm2 method of GangliaOps class.
     */
    @Test
    void testAreaPerGanglionUm2() {
        // Mock ImagePlus and its processor
        ImagePlus gangliaLabels = mock(ImagePlus.class);
        ImageProcessor processor = mock(ImageProcessor.class);

        // 4x4 image, ganglion 4 has 5 pixels, ganglion 2 has 3 pixels
        short[] gl = {
                1, 1, 0, 2,
                1, 0, 2, 2,
                0, 1, 0, 0,
                0, 0, 0, 0
        };
        when(gangliaLabels.getProcessor()).thenReturn(processor);
        when(processor.convertToShort(false)).thenReturn(processor);
        when(processor.getPixels()).thenReturn(gl);

        // Set calibration: 2 µm per pixel
        Calibration calibration = new Calibration();
        calibration.pixelWidth = 2.0;
        when(gangliaLabels.getCalibration()).thenReturn(calibration);

        // Call the function
        double[] areaUm2 = Features.AnalyseWorkflows.GangliaOps.areaPerGanglionUm2(gangliaLabels);

        /**
         * Verify the following:
         * areaUm2 is not null
         * areaUm2 length is correct
         * areaUm2 values are correct
         */

        // Assert areaUm2 is not null
        assertNotNull(areaUm2);

        // There are 2 ganglia, so length should be 3 (index 0 unused)
        assertEquals(3, areaUm2.length);

        // Ganglion 1: 4 pixels, Ganglion 2: 3 pixels, each pixel is 4 µm²
        assertEquals(4 * 4.0, areaUm2[1]);
        assertEquals(3 * 4.0, areaUm2[2]);
    }

    /**
     * Test for keepGangliaWithAtLeast method of GangliaOps class.
     */
    @Test
     void testKeepGangliaWithAtLeast() {
        // Create a mock ganglia label image (4x4 pixels)
        ImagePlus gangliaLabels = mock(ImagePlus.class);
        ImageProcessor processor = mock(ImageProcessor.class);

        // Ganglion IDs: 1 and 2, distributed in the image
        short[] gl = {
                1, 1, 0, 2,
                1, 0, 2, 2,
                0, 1, 0, 0,
                0, 0, 0, 0
        };
        when(gangliaLabels.getWidth()).thenReturn(4);
        when(gangliaLabels.getHeight()).thenReturn(4);
        when(gangliaLabels.getProcessor()).thenReturn(processor);
        when(processor.convertToShort(false)).thenReturn(processor);
        when(processor.getPixels()).thenReturn(gl);

        // Set calibration (not strictly needed for mask, but for completeness)
        Calibration calibration = new Calibration();
        calibration.pixelWidth = 2.0;
        when(gangliaLabels.getCalibration()).thenReturn(calibration);

        // countsPerGanglion: ganglion 1 has 3 neurons, ganglion 2 has 1 neuron
        int[] countsPerGanglion = new int[] {0, 3, 1};


        // minCount = 2, so only ganglion 1 should be kept
        ImagePlus result = GangliaOps.keepGangliaWithAtLeast(gangliaLabels, countsPerGanglion, 2);

        /**
         * Verify the following:
         * result is not null
         * result pixels are correct (only ganglion 1 pixels are kept)
         * calibration is copied
         */

        // Assert result is not null
        assertNotNull(result);

        // The result should be a binary image (8-bit) with pixels set to 255 where ganglion 1 is present
        byte[] bp = (byte[]) result.getProcessor().getPixels();

        // Check that only pixels belonging to ganglion 1 are set to 255
        for (int i = 0; i < gl.length; i++) {
            if (gl[i] == 1) {
                assertEquals((byte)255, bp[i], "Ganglion 1 pixel should be white");
            } else {
                assertEquals((byte)0, bp[i], "Non-ganglion 1 pixel should be black");
            }
        }
    }
}