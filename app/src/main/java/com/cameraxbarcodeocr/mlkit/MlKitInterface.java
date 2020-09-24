package com.cameraxbarcodeocr.mlkit;

import com.google.mlkit.vision.barcode.Barcode;
import com.google.mlkit.vision.text.Text;

import java.util.List;

public interface MlKitInterface {
    void onBarcodeAnalyzed(List<Barcode> firebaseVisionBarcodes);
    
    void onTextExtracted(Text firebaseVisionText);
}
