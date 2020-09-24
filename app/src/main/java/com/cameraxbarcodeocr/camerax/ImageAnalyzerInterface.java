package com.cameraxbarcodeocr.camerax;

import androidx.camera.core.ImageProxy;

public interface ImageAnalyzerInterface {
    void onImageAnalyzed(ImageProxy inputImage);
}
