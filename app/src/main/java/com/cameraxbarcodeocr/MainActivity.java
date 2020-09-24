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
import android.util.Log;
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
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.FocusMeteringResult;
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
   private static final String TAG = "MainActivity";
   private PreviewView previewView;
   private final int REQUEST_CODE = 111;
   private Camera camera;
   private MyImageAnalyzer imageAnalyzer;
   private Preview preview;
   private OcrAnalyzer ocrAnalyzer;
   private BarcodeAnalyzer barcodeAnalyzer;
   private boolean isAnalysingBarcode = true, isDoingOcr = false;
   private long frameCount = 0;
   private SurfaceHolder transparentHolder;
   private float scaleX;
   private float scaleY;
   private int screenWidth, previewWidth, previewHeight, screenHeight;

   @SuppressLint("ClickableViewAccessibility")
   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_main);

      previewView = findViewById(R.id.previewView);

      SurfaceView transparentSurface = findViewById(R.id.surfaceViewTransparent);
      transparentSurface.setZOrderOnTop(true);
      transparentHolder = transparentSurface.getHolder();
      transparentHolder.setFormat(PixelFormat.TRANSPARENT);

      if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
         setCameraProviderListener();
      else
         ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CODE);

      ocrAnalyzer = new OcrAnalyzer(this);
      barcodeAnalyzer = new BarcodeAnalyzer(this);

      previewView.setOnTouchListener(getTapToFocusOnTouchListener());

      findViewById(R.id.buttonOcr).setOnClickListener(view -> {
         isAnalysingBarcode = false;
         imageAnalyzer.isAnalysing = true;
         focus(screenWidth / 2f, screenHeight / 2f).addListener(() ->
                         isDoingOcr = true
                 , Executors.newSingleThreadExecutor());
      });

      findViewById(R.id.buttonBarcode).setOnClickListener(view -> {
         isAnalysingBarcode = true;
         imageAnalyzer.isAnalysing = true;
      });
   }

   @SuppressLint("UnsafeExperimentalUsageError")
   @Override
   public void onImageAnalyzed(ImageProxy imageProxy) {
      if (scaleX == 0)
         calculateScales();
      if (isAnalysingBarcode) {
         if (frameCount++ % 1 != 0) {
            imageProxy.close();
            return;
         }
         barcodeAnalyzer.analyzeBarcode(imageProxy);
      } else if (isDoingOcr) {
         imageAnalyzer.isAnalysing = false;
         isDoingOcr = false;
         Bitmap bitmap = InputImage.fromMediaImage(imageProxy.getImage(),
                 imageProxy.getImageInfo().getRotationDegrees()).getBitmapInternal();

         runOnUiThread(() -> ((ImageView) findViewById(R.id.imageViewOcrPreview)).setImageBitmap(bitmap));
         ocrAnalyzer.extractText(imageProxy);
      }
   }

   @Override
   public void onBarcodeAnalyzed(List<Barcode> barcodes) {
      for (Barcode barcode : barcodes) {
         clearCanvas();
         if (barcode.getBoundingBox().height() <= 35)
            continue;
         Log.d(TAG, "standart :"
                 + barcode.getBoundingBox().top + " " + barcode.getBoundingBox().bottom + " "
                 + barcode.getBoundingBox().left + " " + barcode.getBoundingBox().right);
         drawRect(getScaledRect(barcode.getBoundingBox(), scaleX, scaleY));
         break;
      }

   }

   @Override
   public void onTextExtracted(Text firebaseVisionText) {

   }

   private void drawRect(RectF rectangle) {
      Log.d(TAG, "drawRect: " + rectangle.top + " " + rectangle.bottom + " " + rectangle.left + " " + rectangle.right);
      Canvas canvas = transparentHolder.lockCanvas();
      if (canvas == null)
         return;
      Paint myPaint = new Paint();
      myPaint.setColor(Color.rgb(255, 200, 0));
      myPaint.setStrokeWidth(10);
      myPaint.setStyle(Paint.Style.STROKE);
      canvas.drawRect(rectangle, myPaint);

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
      preview.setSurfaceProvider(previewView.createSurfaceProvider());
      // Seting target AR doesn't give highest resolution sometimes
      // Default backpressure strategy drops latest frames
      ImageAnalysis imageAnalysis = new ImageAnalysis.Builder().setTargetResolution(new Size(1080, 1920)).build();
      ExecutorService executorService = Executors.newSingleThreadExecutor();
      imageAnalyzer = new MyImageAnalyzer(this);
      imageAnalysis.setAnalyzer(executorService, imageAnalyzer);

      camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
   }

   @SuppressLint("RestrictedApi")
   private void calculateScales() {
      Size previewSize = preview.getAttachedSurfaceResolution();
      previewWidth = previewSize.getHeight();// inverted because preview is rotated
      previewHeight = previewSize.getWidth();
      screenWidth = previewView.getWidth();
      screenHeight = previewView.getHeight();
      scaleX = previewWidth / (float) screenWidth;
      scaleY = previewHeight / (float) screenHeight;
      scaleX = new BigDecimal(scaleX).setScale(2, BigDecimal.ROUND_CEILING).floatValue();
      scaleY = new BigDecimal(scaleY).setScale(2, BigDecimal.ROUND_CEILING).floatValue();
   }

   private RectF getScaledRect(Rect rect, float scaleX, float scaleY) {
      RectF rectF = new RectF(rect);
      Matrix matrix = new Matrix();
      matrix.postScale(1 / scaleX, 1 / scaleY);
      matrix.mapRect(rectF);
//      rect.right = (int) ((rect.right - previewWidth / 2) * scaleX + screenWidth / 2);
//      rect.left = (int) ((rect.left - previewWidth / 2) * scaleX + screenWidth / 2);
//      rect.top = (int) ((rect.top - previewHeight / 2) * scaleY + screenHeight / 2);
//      rect.bottom = (int) ((rect.bottom - previewHeight / 2) * scaleY + screenHeight / 2);
      return rectF;
   }

   @Override
   public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
      super.onRequestPermissionsResult(requestCode, permissions, grantResults);
      if (requestCode == REQUEST_CODE)
         if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
            setCameraProviderListener();
   }

   protected ListenableFuture<FocusMeteringResult> focus(float x, float y) {
      Log.d(TAG, "focus: tap to focus");
      MeteringPointFactory factory = new SurfaceOrientedMeteringPointFactory(1.0f, 1.0f);
      MeteringPoint point = factory.createPoint(x, y);
      FocusMeteringAction action = new FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
              .setAutoCancelDuration(5, TimeUnit.SECONDS)
              .build();

      return camera != null ? camera.getCameraControl().startFocusAndMetering(action) : null;
   }

   public View.OnTouchListener getTapToFocusOnTouchListener() {
      return (View v, MotionEvent event) -> {
         switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
               v.performClick();
               break;
            case MotionEvent.ACTION_UP:
               focus(event.getX(), event.getY());
               return true;
            default:
               return false;
         }
         return true;
      };
   }
}