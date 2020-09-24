package com.cameraxbarcodeocr.camerax;

import androidx.annotation.NonNull;
import androidx.annotation.experimental.UseExperimental;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

public class MyImageAnalyzer implements ImageAnalysis.Analyzer {
    private final String TAG = getClass().getSimpleName();
    private ImageAnalyzerInterface imageAnalyzerInterface;
    public boolean isAnalysing = true;

    public MyImageAnalyzer(ImageAnalyzerInterface imageAnalyzerInterface) {
        this.imageAnalyzerInterface = imageAnalyzerInterface;
    }
    
    
    @UseExperimental(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        if (!isAnalysing) {
            imageProxy.close();
            return;
        }
        imageAnalyzerInterface.onImageAnalyzed(imageProxy);
    }
    
}
