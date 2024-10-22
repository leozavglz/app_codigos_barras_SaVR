package com.example.app_codigos_barras_savr;

import org.opencv.android.CameraActivity;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;



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
    private boolean scanSuccessful = false; // Bandera para controlar si el escaneo fue exitoso

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

        // Configurar la vista de la cámara
        mOpenCvCameraView = findViewById(R.id.camera_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        // Inicializamos el BarcodeDetector
        barcodeDetector = new BarcodeDetector();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null) mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mOpenCvCameraView != null) mOpenCvCameraView.enableView();
    }

    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(mOpenCvCameraView);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
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

                    runOnUiThread(() -> {
                        scanSuccessful = true; // Cambia la bandera para detener el escaneo
                        mostrarPopup("Producto escaneado correctamente", info);
                        detenerEscaneo(); // Detener la cámara cuando se muestra el popup
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

    // Método para mostrar un popup de confirmación del producto
    private void mostrarPopup(String mensaje, String codigo) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(mensaje);

        // Custom View for popup
        ScrollView layout = (ScrollView) getLayoutInflater().inflate(R.layout.popup_layout, null);
        ImageView productImage = layout.findViewById(R.id.product_image);
        TextView productName = layout.findViewById(R.id.product_name);
        productName.setText(codigo); // Aquí asignamos el código del producto

        // Spinner setup
        Spinner spinnerQuantity = layout.findViewById(R.id.product_quantity_spinner);

        // Array with quantities
        Integer[] quantities = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<Integer> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, quantities);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        spinnerQuantity.setAdapter(adapter);

        builder.setView(layout);

        AlertDialog dialog = builder.create();

        // Mostrar el fondo semitransparente
        FrameLayout blurBackground = findViewById(R.id.blur_background);
        blurBackground.setVisibility(View.VISIBLE);  // Mostrar el fondo oscuro semitransparente

        // Botón para volver a escanear
        Button btnRescan = layout.findViewById(R.id.btn_rescan);
        btnRescan.setOnClickListener(v -> {
            scanSuccessful = false; // Resetear la bandera de escaneo exitoso
            dialog.dismiss(); // Cerrar el popup
            Toast.makeText(this, "Escaneo reiniciado", Toast.LENGTH_SHORT).show();

            // Ocultar el fondo oscuro
            blurBackground.setVisibility(View.GONE);

            if (mOpenCvCameraView != null) {
                mOpenCvCameraView.enableView(); // Reanudar la cámara para un nuevo escaneo
            }
        });

        // Botón para confirmar producto
        Button btnConfirm = layout.findViewById(R.id.btn_confirm);
        btnConfirm.setOnClickListener(v -> {
            scanSuccessful = false; // Resetear la bandera de escaneo exitoso
            dialog.dismiss(); // Cerrar el popup
            Toast.makeText(this, "Producto confirmado", Toast.LENGTH_SHORT).show();

            // Ocultar el fondo oscuro
            blurBackground.setVisibility(View.GONE);

            if (mOpenCvCameraView != null) {
                mOpenCvCameraView.enableView(); // Reanudar la cámara para un nuevo escaneo
            }
        });

        // Mostrar el popup
        dialog.show();

        // Configurar la acción para cuando el popup se cierre (si el usuario lo cierra tocando fuera del diálogo)
        dialog.setOnDismissListener(dialogInterface -> blurBackground.setVisibility(View.GONE));
    }


    // Método para dibujar los contornos de los códigos de barras en la imagen
    private void drawBarcodeContours(Mat points, Mat imgRgba) {
        if (points.empty()) {
            Log.d(TAG, "Points está vacío");
            return;
        }

        MatOfPoint2f matOfPoint2f = new MatOfPoint2f(points);
        Point[] pointArray = matOfPoint2f.toArray();

        int totalPoints = pointArray.length;
        if (totalPoints % 4 != 0) {
            Log.e(TAG, "Número inesperado de puntos: " + totalPoints);
            return;
        }

        List<MatOfPoint> contours = new ArrayList<>();

        for (int i = 0; i < totalPoints; i += 4) {
            Point[] quadPoints = new Point[4];
            System.arraycopy(pointArray, i, quadPoints, 0, 4);

            MatOfPoint contour = new MatOfPoint(quadPoints);
            contours.add(contour);
        }

        if (!contours.isEmpty()) {
            Imgproc.polylines(imgRgba, contours, true, new Scalar(0, 255, 0), 4);
        }
    }
}
