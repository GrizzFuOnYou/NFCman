package com.nfc.wallet;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class CardDetailActivity extends AppCompatActivity {

    private CardModel card;
    private CardManager cardManager;
    private String cardFilename;
    private boolean rawExpanded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_card_detail);

        cardManager = new CardManager(this);
        cardFilename = getIntent().getStringExtra("card_filename");

        if (cardFilename == null) {
            Toast.makeText(this, "No card specified", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        card = cardManager.loadCard(cardFilename);
        if (card == null) {
            Toast.makeText(this, "Card not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        populateViews();
        setupButtons();
    }

    private void populateViews() {
        setField(R.id.tv_detail_name, card.name);
        setField(R.id.tv_detail_uid, card.uid);
        setField(R.id.tv_detail_type, card.cardType);
        setField(R.id.tv_detail_company, card.company);
        setField(R.id.tv_detail_aid, card.aid);
        setField(R.id.tv_detail_atr, card.atr);
        setField(R.id.tv_detail_apdu_response, card.apduSelectResponse);
        setField(R.id.tv_detail_techs, card.techs != null ? String.join(", ", card.techs) : "");
        setField(R.id.tv_detail_scan_date, card.scanDate);
        setField(R.id.tv_detail_notes, card.notes);
        setField(R.id.tv_detail_manual, card.isManual ? "Yes (manually entered)" : "No (scanned)");

        // Extra fields
        if (!card.extraFields.isEmpty()) {
            StringBuilder extras = new StringBuilder();
            for (java.util.Map.Entry<String, String> e : card.extraFields.entrySet()) {
                extras.append(e.getKey()).append(": ").append(e.getValue()).append("\n");
            }
            setField(R.id.tv_detail_extras, extras.toString().trim());
            findViewById(R.id.layout_extras).setVisibility(View.VISIBLE);
        } else {
            View extrasLayout = findViewById(R.id.layout_extras);
            if (extrasLayout != null) extrasLayout.setVisibility(View.GONE);
        }

        // Raw content (expandable)
        TextView tvRaw = findViewById(R.id.tv_raw_content);
        if (tvRaw != null) {
            String rawContent = cardManager.getRawContent(cardFilename);
            tvRaw.setText(rawContent);
        }
    }

    private void setField(int viewId, String value) {
        TextView tv = findViewById(viewId);
        if (tv == null) return;
        if (value == null || value.isEmpty()) {
            tv.setText("—");
            tv.setAlpha(0.4f);
        } else {
            tv.setText(value);
            tv.setAlpha(1.0f);
        }
    }

    private void setupButtons() {
        Button btnEdit = findViewById(R.id.btn_edit);
        btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(this, ManualEntryActivity.class);
            intent.putExtra("card_name", card.name);
            intent.putExtra("card_uid", card.uid);
            intent.putExtra("card_type", card.cardType);
            intent.putExtra("card_company", card.company);
            intent.putExtra("card_aid", card.aid);
            intent.putExtra("card_atr", card.atr);
            intent.putExtra("card_apdu_response", card.apduSelectResponse);
            intent.putExtra("card_notes", card.notes);
            startActivity(intent);
        });

        Button btnDelete = findViewById(R.id.btn_delete);
        btnDelete.setOnClickListener(v ->
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Delete Card")
                        .setMessage("Delete \"" + card.name + "\"?")
                        .setPositiveButton("Delete", (d, w) -> {
                            cardManager.deleteCard(cardFilename);
                            Toast.makeText(this, "Card deleted", Toast.LENGTH_SHORT).show();
                            finish();
                        })
                        .setNegativeButton("Cancel", null)
                        .show());

        Button btnEmulate = findViewById(R.id.btn_emulate);
        btnEmulate.setOnClickListener(v -> {
            Intent intent = new Intent(this, EmulationActivity.class);
            intent.putExtra("card_filename", cardFilename);
            startActivity(intent);
        });

        Button btnExport = findViewById(R.id.btn_export);
        btnExport.setOnClickListener(v -> {
            java.io.File exportDir = new java.io.File(getExternalFilesDir(null), "exported");
            java.io.File exportFile = new java.io.File(exportDir, cardFilename);
            boolean ok = cardManager.exportCard(card, exportFile);
            Toast.makeText(this, ok ? "Exported to: " + exportFile.getAbsolutePath() : "Export failed",
                    Toast.LENGTH_LONG).show();
        });

        Button btnToggleRaw = findViewById(R.id.btn_toggle_raw);
        View layoutRaw = findViewById(R.id.layout_raw_content);
        if (btnToggleRaw != null && layoutRaw != null) {
            btnToggleRaw.setOnClickListener(v -> {
                rawExpanded = !rawExpanded;
                layoutRaw.setVisibility(rawExpanded ? View.VISIBLE : View.GONE);
                btnToggleRaw.setText(rawExpanded ? "Hide Raw" : "Show Raw");
            });
        }
    }
}
