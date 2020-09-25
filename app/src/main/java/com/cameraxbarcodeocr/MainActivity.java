package com.cameraxbarcodeocr;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Size;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;

import com.cameraxbarcodeocr.camerax.ImageAnalyzerInterface;
import com.cameraxbarcodeocr.camerax.MyImageAnalyzer;
import com.cameraxbarcodeocr.mlkit.MlKitInterface;
import com.cameraxbarcodeocr.mlkit.barcode.BarcodeAnalyzer;
import com.cameraxbarcodeocr.mlkit.ocr.OcrAnalyzer;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.Barcode;
import com.google.mlkit.vision.text.Text;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity implements ImageAnalyzerInterface, MlKitInterface {
   private PreviewView previewView;
   private final int REQUEST_CODE = 111;
   private Camera camera;
   private MyImageAnalyzer imageAnalyzer;
   private Preview preview;
   private OcrAnalyzer ocrAnalyzer;
   private BarcodeAnalyzer barcodeAnalyzer;
   private boolean isAnalysingBarcode = true, isDoingOcr = false;
   private SurfaceHolder transparentHolder;
   private ImageView imageViewPreview;
   private float scaleX;
   private float scaleY;

   @SuppressLint("ClickableViewAccessibility")
   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_main);

      previewView = findViewById(R.id.previewView);
      imageViewPreview = findViewById(R.id.imageViewOcrPreview);

      SurfaceView transparentSurface = findViewById(R.id.surfaceViewTransparent);
      // Blocks camera preview if not on top
      transparentSurface.setZOrderOnTop(true);
      transparentHolder = transparentSurface.getHolder();
      // Required to make surfaceView transparent
      transparentHolder.setFormat(PixelFormat.TRANSPARENT);

      ocrAnalyzer = new OcrAnalyzer(this);
      barcodeAnalyzer = new BarcodeAnalyzer(this);

      previewView.setOnTouchListener((View v, MotionEvent event) -> {
         switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
               v.performClick();
               return true;
            case MotionEvent.ACTION_UP:
               focus(event.getX(), event.getY());
               return true;
            default:
               return false;
         }
      });

      findViewById(R.id.buttonOcr).setOnClickListener(view -> {
         isAnalysingBarcode = false;
         isDoingOcr = true;
         imageAnalyzer.isAnalysing = true;

         Bitmap bitmap = previewView.getBitmap();
         imageViewPreview.setImageBitmap(bitmap);
         imageViewPreview.setVisibility(View.VISIBLE);
      });

      findViewById(R.id.buttonBarcode).setOnClickListener(view -> {
         isAnalysingBarcode = true;
         imageAnalyzer.isAnalysing = true;
      });
      findViewById(R.id.buttonClearOcr).setOnClickListener(view -> {
         clearCanvas();
         imageViewPreview.setVisibility(View.GONE);
      });

   }

   @Override
   protected void onResume() {
      super.onResume();
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
         setCameraProviderListener();
      else
         ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CODE);
   }

   @SuppressLint("UnsafeExperimentalUsageError")
   @Override
   public void onImageAnalyzed(ImageProxy imageProxy) {
      if (scaleX == 0)
         calculateScales();
      if (isAnalysingBarcode)
         barcodeAnalyzer.analyzeBarcode(imageProxy);
      else if (isDoingOcr) {
         imageAnalyzer.isAnalysing = false;
         isDoingOcr = false;
         ocrAnalyzer.extractText(imageProxy);
      }
   }

   @Override
   public void onBarcodeAnalyzed(List<Barcode> barcodes) {
      for (Barcode barcode : barcodes) {
         clearCanvas();
         if (barcode.getBoundingBox().height() <= 35)
            continue;
         drawRect(getScaledRect(barcode.getBoundingBox()));
         break;
      }

   }

   @Override
   public void onTextExtracted(Text firebaseVisionText) {
      List<String> texts = new ArrayList<>();
      List<RectF> boundingBoxes = new ArrayList<>();
      for (Text.TextBlock textBlock : firebaseVisionText.getTextBlocks())
         for (Text.Line line : textBlock.getLines())
            for (Text.Element element : line.getElements()) {
               texts.add(element.getText());
               boundingBoxes.add(getScaledRect(element.getBoundingBox()));
            }

      drawTexts(texts, boundingBoxes);
   }

   private void drawRect(RectF rectangle) {
      Canvas canvas = transparentHolder.lockCanvas();

      Paint myPaint = new Paint();
      myPaint.setColor(Color.rgb(0, 255, 0));
      myPaint.setStrokeWidth(5);
      myPaint.setStyle(Paint.Style.STROKE);
      canvas.drawRect(rectangle, myPaint);

      transparentHolder.unlockCanvasAndPost(canvas);
   }

   private void drawTexts(List<String> texts, List<RectF> boundingBoxes) {
      Canvas canvas = transparentHolder.lockCanvas();

      Paint myPaint = new Paint();
      myPaint.setColor(Color.rgb(255, 0, 0));
      myPaint.setStyle(Paint.Style.FILL);
      myPaint.setTextSize(convertDpToPixel(13));
      for (int i = 0; i < texts.size(); i++)
         canvas.drawText(texts.get(i), boundingBoxes.get(i).left, boundingBoxes.get(i).bottom, myPaint);

      transparentHolder.unlockCanvasAndPost(canvas);
   }

   private void clearCanvas() {
      transparentHolder.setFormat(PixelFormat.OPAQUE);
      transparentHolder.setFormat(PixelFormat.TRANSPARENT);
   }

   private void setCameraProviderListener() {
      ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
      cameraProviderFuture.addListener(() -> {
         try {
            ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
            bindUseCases(cameraProvider);
         } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
         }
      }, ContextCompat.getMainExecutor(this));
   }

   @SuppressLint("RestrictedApi")
   private void bindUseCases(ProcessCameraProvider cameraProvider) {
      previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
      preview = new Preview.Builder().setTargetResolution(new Size(1080, 1920)).build();
      CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
      preview.setSurfaceProvider(previewView.getSurfaceProvider());
      // Setting target AR may not give highest resolution
      // Default back pressure strategy drops latest frames
      ImageAnalysis imageAnalysis = new ImageAnalysis.Builder().setTargetResolution(new Size(1080, 1920)).build();
      ExecutorService executorService = Executors.newSingleThreadExecutor();
      imageAnalyzer = new MyImageAnalyzer(this);
      imageAnalysis.setAnalyzer(executorService, imageAnalyzer);

      camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
   }

   @SuppressLint("RestrictedApi")
   private void calculateScales() {
      Size previewSize = preview.getAttachedSurfaceResolution();
      int previewWidth = previewSize.getHeight();// inverted because preview is rotated
      int previewHeight = previewSize.getWidth();
      int usableWidth = previewView.getWidth();
      int usableHeight = previewView.getHeight();
      scaleX = usableWidth / (float) previewWidth;
      scaleY = usableHeight / (float) previewHeight;
      scaleX = new BigDecimal(scaleX).setScale(2, BigDecimal.ROUND_CEILING).floatValue();
      scaleY = new BigDecimal(scaleY).setScale(2, BigDecimal.ROUND_CEILING).floatValue();
   }

   private RectF getScaledRect(Rect rect) {
      RectF rectF = new RectF(rect);
      Matrix matrix = new Matrix();
      matrix.postScale(scaleX, scaleY);
      matrix.mapRect(rectF);
      return rectF;
   }

   @Override
   public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
      super.onRequestPermissionsResult(requestCode, permissions, grantResults);
      if (requestCode == REQUEST_CODE)
         if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
            setCameraProviderListener();
   }

   protected void focus(float x, float y) {
      MeteringPointFactory factory = new SurfaceOrientedMeteringPointFactory(1.0f, 1.0f);
      MeteringPoint point = factory.createPoint(x, y);
      FocusMeteringAction action = new FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF).build();
      camera.getCameraControl().startFocusAndMetering(action);
   }

   public float convertDpToPixel(float dp) {
      return dp * ((float) getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT);
   }
}