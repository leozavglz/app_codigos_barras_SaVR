/*
 * |                             S O F T x A R T
 * | Proyecto: SaVR
 * | Archivo: app_codigos_barras_SaVR
 * | Clase/modulo a codificar: MO_AR
 * | Descripción general: Manejo e implementacion del modulo de camara.
 * | Funciones especificas:
 * |   -Escaneo del codigo de barras
 * |   -Mandar a la base de datos el codigo escaneado.
 * |   -Mostrar la información relevante del producto.
 * |   -Facilitar al usuario agregar productos al carrito.
 * |
 * | Desarrollador encargado: Leonardo Zavala González
 * | Aprobado por: Marcos Emmanuel Juarez Navarro
 * |
 * | CAMBIOS REALIZADOS DESDE LA ULTIMA VERSION
 * | Nombre:        Fecha:               Cambios Realizados:
 * | LZG            23/10/24             Comencé a pasar mi modulo al patron de diseño de Facade.
 * | LZG            29/10/24             Terminé de implementar el patrón de diseño de Facade.
 * |
 * |
 * |
 * |
 * |
 * */
package com.example.app_codigos_barras_savr;

import org.opencv.android.CameraActivity;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.MatOfPoint2f;

import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends CameraActivity implements CameraBridgeViewBase.CvCameraViewListener2 {
    private static final String TAG = "MainActivity";
    private static final int ANALYSIS_INTERVAL_MS = 500;

    private CameraBridgeViewBase mOpenCvCameraView;
    private boolean isProcessingFrame = false;
    private long lastAnalysisTime = 0;
    private boolean scanSuccessful = false;

    // Facades
    private BarcodeFacade barcodeFacade;
    private PopupFacade popupFacade;
    private FrameLayout blurBackground;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);

        if (OpenCVLoader.initDebug()) {
            Log.i(TAG, "OpenCV loaded successfully");
        } else {
            Log.e(TAG, "OpenCV initialization failed!");
            return;
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        // Configurar la vista de la cámara
        mOpenCvCameraView = findViewById(R.id.camera_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        // Inicializar las Facades
        barcodeFacade = new BarcodeFacade();
        blurBackground = findViewById(R.id.blur_background);
        popupFacade = new PopupFacade(this, blurBackground);
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
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        Mat mRgba = inputFrame.rgba();

        long currentTime = System.currentTimeMillis();

        if (scanSuccessful) {
            return mRgba;
        }

        if (currentTime - lastAnalysisTime >= ANALYSIS_INTERVAL_MS && !isProcessingFrame) {
            lastAnalysisTime = currentTime;
            isProcessingFrame = true;

            Mat gray = inputFrame.gray();

            new Thread(() -> {
                try {
                    List<String> decodedInfo = new ArrayList<>();
                    List<String> decodedType = new ArrayList<>();
                    boolean found = barcodeFacade.detectBarcode(gray, decodedInfo, decodedType);

                    if (found) {
                        for (int i = 0; i < decodedInfo.size(); i++) {
                            if ("EAN_13".equals(decodedType.get(i))) {
                                String barcode = decodedInfo.get(i);
                                runOnUiThread(() -> {
                                    scanSuccessful = true;
                                    popupFacade.showPopup("Producto escaneado correctamente", barcode, this::reanudarEscaneo, this::confirmarProducto);
                                    detenerEscaneo();
                                });
                                break;
                            }
                        }
                    }

                    isProcessingFrame = false;
                } catch (Exception e) {
                    Log.e(TAG, "Error en la detección del código de barras: " + e.getMessage());
                    isProcessingFrame = false;
                }
            }).start();
        }

        return mRgba;
    }

    private void detenerEscaneo() {
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();
        }
    }

    private void reanudarEscaneo() {
        scanSuccessful = false;
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.enableView();
        }
    }

    private void confirmarProducto() {
        reanudarEscaneo();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {}

    @Override
    public void onCameraViewStopped() {}
}
