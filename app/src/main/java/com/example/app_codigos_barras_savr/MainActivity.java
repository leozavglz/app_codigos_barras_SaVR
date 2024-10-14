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
    private static final int ANALYSIS_INTERVAL_MS = 500; // Intervalo de análisis de 500 ms (2 veces por segundo)

    private CameraBridgeViewBase mOpenCvCameraView;
    private BarcodeDetector barcodeDetector;

    private boolean isProcessingFrame = false;
    private long lastAnalysisTime = 0; // Almacena el tiempo del último análisis

    public MainActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);

        // Inicializa OpenCV y muestra un mensaje si falla
        if (OpenCVLoader.initDebug()) {
            Log.i(TAG, "OpenCV loaded successfully");
        } else {
            Log.e(TAG, "OpenCV initialization failed!");
            Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_LONG).show();
            return;
        }

        // Mantener la pantalla encendida mientras se usa la cámara
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        // Configura la vista de la cámara
        mOpenCvCameraView = findViewById(R.id.camera_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        // Inicializamos el BarcodeDetector
        barcodeDetector = new BarcodeDetector();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Deshabilitar la cámara cuando la actividad está en pausa
        if (mOpenCvCameraView != null) mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Habilitar la cámara cuando la actividad se reanuda
        if (mOpenCvCameraView != null) mOpenCvCameraView.enableView();
    }

    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        // Devuelve la lista de vistas de cámara (solo una en este caso)
        return Collections.singletonList(mOpenCvCameraView);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Deshabilitar la cámara cuando la actividad se destruye
        if (mOpenCvCameraView != null) mOpenCvCameraView.disableView();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        // Inicialización adicional si es necesaria cuando la vista de la cámara empieza
    }

    @Override
    public void onCameraViewStopped() {
        // Libera recursos si es necesario cuando la vista de la cámara se detiene
    }

    private boolean scanSuccessful = false; // Bandera para controlar si el escaneo fue exitoso

    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        Mat mRgba = inputFrame.rgba();

        long currentTime = System.currentTimeMillis();

        // Si el escaneo ya fue exitoso, no seguir procesando frames
        if (scanSuccessful) {
            return mRgba;
        }

        // Verificar si ha pasado el intervalo de análisis (cada 500 ms)
        if (currentTime - lastAnalysisTime >= ANALYSIS_INTERVAL_MS && !isProcessingFrame) {
            lastAnalysisTime = currentTime; // Actualiza el tiempo del último análisis
            isProcessingFrame = true;

            Mat gray = inputFrame.gray(); // Convierte el frame a escala de grises

            // Procesa el frame en un hilo separado para evitar bloquear la UI
            new Thread(() -> {
                try {
                    detectBarcodeAndDrawContours(gray, mRgba);
                } catch (Exception e) {
                    Log.e(TAG, "Error en detección de código de barras: " + e.getMessage());
                } finally {
                    isProcessingFrame = false; // Marca que el procesamiento ha terminado
                }
            }).start();
        }

        return mRgba; // Devolver el frame actual (con o sin contornos)
    }

    // Método para detectar el código de barras y dibujar contornos
    private void detectBarcodeAndDrawContours(Mat imgGray, Mat imgRgba) {
        Mat points = new Mat();
        List<String> decodedInfo = new ArrayList<>();
        List<String> decodedType = new ArrayList<>();
        boolean found = barcodeDetector.detect(imgGray, points); // Detecta códigos de barras en la imagen

        Log.d(TAG, "Barcode detection result: " + found);

        if (found && !points.empty()) {
            // Decodifica la información de los códigos de barras detectados
            barcodeDetector.decodeWithType(imgGray, points, decodedInfo, decodedType);

            for (int i = 0; i < decodedInfo.size(); i++) {
                String info = decodedInfo.get(i);
                String type = decodedType.get(i);

                // Mostrar la información del código de barras en el Logcat
                Log.d(TAG, "Código de barras detectado: " + info + ", Tipo: " + type);

                // Verifica si el tipo es EAN-13, comúnmente usado en México
                if ("EAN_13".equals(type)) {
                    Log.d(TAG, "Código EAN_13 detectado correctamente: " + info);

                    // Detener el escaneo y mostrar el popup en la UI
                    runOnUiThread(() -> {
                        scanSuccessful = true; // Cambia la bandera para detener el escaneo
                        mostrarPopup("Producto escaneado correctamente: " + info);
                        detenerEscaneo();
                    });
                    break; // Salir del bucle ya que no necesitamos procesar más
                }
            }

            drawBarcodeContours(points, imgRgba); // Dibuja contornos del código de barras en la imagen
        } else {
            Log.d(TAG, "No barcodes detected");
        }
    }

    // Método para detener el escaneo y deshabilitar la vista de la cámara
    private void detenerEscaneo() {
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();  // Deshabilita la cámara
        }
    }

    // Método para mostrar un mensaje tipo popup (Toast) en la pantalla
    private void mostrarPopup(String mensaje) {
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();
    }

    // Método para dibujar los contornos de los códigos de barras en la imagen
    private void drawBarcodeContours(Mat points, Mat imgRgba) {
        if (points.empty()) {
            Log.d(TAG, "Points está vacío");
            return;
        }

        // Convertimos 'points' a un MatOfPoint2f para manejar los puntos
        MatOfPoint2f matOfPoint2f = new MatOfPoint2f(points);
        Point[] pointArray = matOfPoint2f.toArray();

        // Cada código de barras debería tener 4 puntos para definir el área
        int totalPoints = pointArray.length;
        if (totalPoints % 4 != 0) {
            Log.e(TAG, "Número inesperado de puntos: " + totalPoints);
            return;
        }

        List<MatOfPoint> contours = new ArrayList<>();

        // Agrupamos cada 4 puntos para formar los contornos de los códigos de barras
        for (int i = 0; i < totalPoints; i += 4) {
            Point[] quadPoints = new Point[4];
            System.arraycopy(pointArray, i, quadPoints, 0, 4);

            MatOfPoint contour = new MatOfPoint(quadPoints);
            contours.add(contour);
        }

        // Dibuja los contornos en la imagen imgRgba con un color verde
        if (!contours.isEmpty()) {
            Imgproc.polylines(imgRgba, contours, true, new Scalar(0, 255, 0), 4);
        }
    }
}
