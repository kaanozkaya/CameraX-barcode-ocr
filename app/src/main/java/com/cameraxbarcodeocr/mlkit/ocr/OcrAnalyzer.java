package com.cameraxbarcodeocr.mlkit.ocr;

import android.annotation.SuppressLint;

import com.cameraxbarcodeocr.mlkit.MlKitInterface;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;

import androidx.camera.core.ImageProxy;

public class OcrAnalyzer {
   private TextRecognizer recognizer;
   private MlKitInterface mlKitInterface;

   public OcrAnalyzer(MlKitInterface mlKitInterface) {
      this.mlKitInterface = mlKitInterface;
      recognizer = TextRecognition.getClient();
   }

   @SuppressLint("UnsafeExperimentalUsageError")
   public void extractText(ImageProxy image) {
      InputImage inputImage = InputImage.fromMediaImage(image.getImage(), image.getImageInfo().getRotationDegrees());
      recognizer.process(inputImage).addOnSuccessListener(text -> {
         mlKitInterface.onTextExtracted(text);
         image.close();
      }).addOnFailureListener(e -> image.close());
   }
}
