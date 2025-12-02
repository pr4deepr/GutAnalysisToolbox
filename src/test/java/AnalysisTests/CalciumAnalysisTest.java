package AnalysisTests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import Analysis.CalciumAnalysis;
import Features.Core.Params;
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.frame.RoiManager;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalciumAnalysisTest {

    @Mock
    File mockFile;

    @Mock
    ImagePlus mockRawStack, mockMaxProj, mockNormStack;

    @Mock
    RoiManager mockRM;

    @Test
    void testOpenImage() {
        // Initialise relevant variables

        // Non-mocks
        Params params = new Params();
        params.imagePath = "src/test/resources/sampleFiles/2D_enteric_neuron_v4_1.zip";

        // Mock function calls
        doNothing().when(mockRawStack).show();
        when(mockRawStack.getTitle()).thenReturn("title");

        try (
             MockedStatic<IJ> ijMock = Mockito.mockStatic(IJ.class)
        ) {
            ijMock.when(() -> IJ.openImage(params.imagePath)).thenReturn(mockRawStack);
            ijMock.when(() -> IJ.selectWindow("title")).thenAnswer(invocation -> null);
            ijMock.when(() -> IJ.log(anyString())).thenAnswer(invocation -> null);

            // Create CalciumAnalysis instance
            CalciumAnalysis calciumAnalysis = new CalciumAnalysis(params);

            // Call openImage method
            calciumAnalysis.openImage();

            /*
            * Verify the following:
            * maxProjection is set to the ImagePlus image
            * all methods are run
            */

            assertEquals(mockRawStack, calciumAnalysis.maxProj);
            ijMock.verify(() -> IJ.openImage(params.imagePath), times(1));
            verify(mockRawStack, times(1)).show();
            ijMock.verify(() -> IJ.selectWindow("title"), times(1));
            ijMock.verify(() -> IJ.log("Step 1: Image loaded successfully."), times(1));
        }
    }

    @Test
    void testOpenImage_FileNotFound() {
        // Initialise relevant variables

        // Non-mocks
        Params params = new Params();
        params.imagePath = "non_existent_file.zip";

        try (
             MockedStatic<IJ> ijMock = Mockito.mockStatic(IJ.class)
        ) {
            ijMock.when(() -> IJ.openImage(params.imagePath)).thenReturn(null);
            ijMock.when(() -> IJ.log(anyString())).thenAnswer(invocation -> null);

            // Create CalciumAnalysis instance
            CalciumAnalysis calciumAnalysis = new CalciumAnalysis(params);

            // Call openImage method
            calciumAnalysis.openImage();

            /*
            * Verify the following:
            * maxProjection is still null
            * appropriate log message is generated
            */

            assertNull(calciumAnalysis.maxProj);
            ijMock.verify(() -> IJ.openImage(params.imagePath), times(0));
            ijMock.verify(() -> IJ.error("File not found: " + params.imagePath), times(1));
        }
    }
}
