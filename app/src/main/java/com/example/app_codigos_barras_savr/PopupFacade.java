package com.example.app_codigos_barras_savr;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.view.View;
import android.widget.Toast;

public class PopupFacade {

    private Context context;
    private FrameLayout blurBackground;

    public PopupFacade(Context context, FrameLayout blurBackground) {
        this.context = context;
        this.blurBackground = blurBackground;
    }

    public void showPopup(String message, String productCode, Runnable onRescan, Runnable onConfirm) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(message);

        // Custom View para el popup
        ScrollView layout = (ScrollView) ((MainActivity) context).getLayoutInflater().inflate(R.layout.popup_layout, null);
        TextView productName = layout.findViewById(R.id.product_name);
        productName.setText(productCode);

        Spinner spinnerQuantity = layout.findViewById(R.id.product_quantity_spinner);
        Integer[] quantities = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        ArrayAdapter<Integer> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, quantities);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerQuantity.setAdapter(adapter);

        builder.setView(layout);

        AlertDialog dialog = builder.create();

        // Mostrar fondo oscuro detrás del popup
        blurBackground.setVisibility(View.VISIBLE);

        Button btnRescan = layout.findViewById(R.id.btn_rescan);
        btnRescan.setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(context, "Reiniciando escaneo...", Toast.LENGTH_SHORT).show();
            blurBackground.setVisibility(View.GONE);
            onRescan.run();
        });

        Button btnConfirm = layout.findViewById(R.id.btn_confirm);
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(context, "Producto confirmado", Toast.LENGTH_SHORT).show();
            blurBackground.setVisibility(View.GONE);
            onConfirm.run();
        });

        dialog.show();

        dialog.setOnDismissListener(dialogInterface -> blurBackground.setVisibility(View.GONE));
    }
}
