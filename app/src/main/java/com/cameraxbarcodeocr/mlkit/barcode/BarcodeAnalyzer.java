package com.cameraxbarcodeocr.mlkit.barcode;

import android.annotation.SuppressLint;
import android.media.Image;

import com.cameraxbarcodeocr.mlkit.MlKitInterface;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;

import androidx.camera.core.ImageProxy;

public class BarcodeAnalyzer {
   private static final String TAG = "BarcodeAnalyzer";
   private BarcodeScanner scanner;
   private MlKitInterface mlKitInterface;

   public BarcodeAnalyzer(MlKitInterface mlKitInterface) {
      this.mlKitInterface = mlKitInterface;
      scanner = BarcodeScanning.getClient();
   }

   @SuppressLint("UnsafeExperimentalUsageError")
   public void analyzeBarcode(ImageProxy imageProxy) {
      Image mediaImage = imageProxy.getImage();
      InputImage inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

      scanner.process(inputImage).addOnFailureListener(e -> imageProxy.close());
      scanner.process(inputImage).addOnSuccessListener(barcodes -> {
         mlKitInterface.onBarcodeAnalyzed(barcodes);
         imageProxy.close();
      });
   }
}
