package com.example.app_codigos_barras_savr;

import org.opencv.android.CameraActivity;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;

import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.Collections;
import java.util.List;

import org.opencv.core.Point;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.BarcodeDetector;

import java.util.ArrayList;

public class MainActivity extends CameraActivity implements CvCameraViewListener2 {
    private static final String TAG = "OCVSample::Activity";

    private CameraBridgeViewBase mOpenCvCameraView;

    private BarcodeDetector barcodeDetector;

    private boolean isProcessingFrame = false;

    public MainActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);

        if (OpenCVLoader.initDebug()) {
            Log.i(TAG, "OpenCV loaded successfully");
        } else {
            Log.e(TAG, "OpenCV initialization failed!");
            Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_LONG).show();
            return;
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.tutorial1_activity_java_surface_view);

        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);

        mOpenCvCameraView.setCvCameraViewListener(this);

        // Inicializamos el BarcodeDetector
        barcodeDetector = new BarcodeDetector();
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.enableView();
    }

    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(mOpenCvCameraView);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        // Puedes inicializar aquí si es necesario
    }

    @Override
    public void onCameraViewStopped() {
        // Libera recursos si es necesario
    }

    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        Mat mRgba = inputFrame.rgba();

        if (!isProcessingFrame) {
            isProcessingFrame = true;

            Mat gray = inputFrame.gray();

            // Procesa el frame en un hilo separado
            new Thread(() -> {
                try {
                    detectBarcodeAndDrawContours(gray, mRgba);
                } catch (Exception e) {
                    Log.e(TAG, "Error en detección de código de barras: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    isProcessingFrame = false;
                }
            }).start();
        }

        return mRgba;
    }

    // Método para detectar el código de barras y dibujar contornos
    private void detectBarcodeAndDrawContours(Mat imgGray, Mat imgRgba) {
        Mat points = new Mat();
        boolean found = barcodeDetector.detect(imgGray, points);

        Log.d(TAG, "Barcode detection result: " + found);

        if (found && !points.empty()) {
            Log.d(TAG, "Barcodes detected: " + points.rows());
            // Dibuja los contornos del código de barras en la imagen
            drawBarcodeContours(points, imgRgba);
        } else {
            Log.d(TAG, "No barcodes detected");
        }
    }

    // Método para dibujar los contornos del código de barras en la imagen
    private void drawBarcodeContours(Mat points, Mat imgRgba) {
        if (points.empty()) {
            Log.d(TAG, "Points está vacío");
            return;
        }

        // Convertimos 'points' a un MatOfPoint2f
        MatOfPoint2f matOfPoint2f = new MatOfPoint2f(points);

        // Obtenemos los puntos como un array de Point
        Point[] pointArray = matOfPoint2f.toArray();

        // Cada código de barras debería tener 4 puntos
        int totalPoints = pointArray.length;
        if (totalPoints % 4 != 0) {
            Log.e(TAG, "Número inesperado de puntos: " + totalPoints);
            return;
        }

        List<MatOfPoint> contours = new ArrayList<>();

        // Agrupamos cada 4 puntos para formar los contornos
        for (int i = 0; i < totalPoints; i += 4) {
            Point[] quadPoints = new Point[4];
            System.arraycopy(pointArray, i, quadPoints, 0, 4);

            MatOfPoint contour = new MatOfPoint(quadPoints);
            contours.add(contour);
        }

        // Dibuja los contornos en la imagen imgRgba
        if (!contours.isEmpty()) {
            Imgproc.polylines(imgRgba, contours, true, new Scalar(0, 255, 0), 4);
        }
    }
}

