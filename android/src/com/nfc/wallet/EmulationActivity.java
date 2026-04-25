package com.nfc.wallet;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class EmulationActivity extends AppCompatActivity {

    private Spinner spinnerCard;
    private TextInputEditText etUid, etAid, etCustomApdu;
    private Button btnToggleEmulation, btnSpoofUid;
    private TextView tvEmulationStatus, tvEmulationLog;
    private ScrollView scrollLog;
    private View statusIndicator;

    private CardManager cardManager;
    private List<CardModel> cards = new ArrayList<>();
    private List<String> cardNames = new ArrayList<>();
    private boolean emulationActive = false;
    private CardModel selectedCard = null;

    private static final int LOG_REFRESH_INTERVAL_MS = 1000;
    private android.os.Handler logHandler = new android.os.Handler();
    private Runnable logRefreshRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emulation);

        cardManager = new CardManager(this);

        bindViews();
        loadCards();
        setupButtons();
        checkRootAvailability();

        // Handle intent from CardListActivity or CardDetailActivity
        String cardFilename = getIntent().getStringExtra("card_filename");
        if (cardFilename != null) {
            CardModel card = cardManager.loadCard(cardFilename);
            if (card != null) {
                selectCard(card);
            }
        }

        setupLogRefresh();
    }

    private void bindViews() {
        spinnerCard = findViewById(R.id.spinner_card);
        etUid = findViewById(R.id.et_uid);
        etAid = findViewById(R.id.et_aid);
        etCustomApdu = findViewById(R.id.et_custom_apdu);
        btnToggleEmulation = findViewById(R.id.btn_toggle_emulation);
        btnSpoofUid = findViewById(R.id.btn_spoof_uid);
        tvEmulationStatus = findViewById(R.id.tv_emulation_status);
        tvEmulationLog = findViewById(R.id.tv_emulation_log);
        scrollLog = findViewById(R.id.scroll_log);
        statusIndicator = findViewById(R.id.status_indicator);
    }

    private void loadCards() {
        new Thread(() -> {
            List<CardModel> loaded = cardManager.listCards();
            runOnUiThread(() -> {
                cards.clear();
                cardNames.clear();
                cards.add(null); // "Select a card" placeholder
                cardNames.add("— Select a Card —");
                for (CardModel c : loaded) {
                    cards.add(c);
                    cardNames.add(c.name + " (" + c.uid + ")");
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_item, cardNames);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerCard.setAdapter(adapter);
                spinnerCard.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                        if (pos > 0 && pos < cards.size()) {
                            selectCard(cards.get(pos));
                        }
                    }
                    @Override public void onNothingSelected(AdapterView<?> parent) {}
                });
            });
        }).start();
    }

    private void selectCard(CardModel card) {
        selectedCard = card;
        if (card == null) return;
        if (etUid != null) etUid.setText(card.uid);
        if (etAid != null) etAid.setText(card.aid);
    }

    private void setupButtons() {
        btnToggleEmulation.setOnClickListener(v -> toggleEmulation());
        btnSpoofUid.setOnClickListener(v -> spoofUid());
    }

    private void toggleEmulation() {
        if (!emulationActive) {
            startEmulation();
        } else {
            stopEmulation();
        }
    }

    private void startEmulation() {
        String uid = etUid.getText() != null ? etUid.getText().toString().trim() : "";
        String aid = etAid.getText() != null ? etAid.getText().toString().trim() : "";
        String customApdu = etCustomApdu.getText() != null ? etCustomApdu.getText().toString().trim() : "";

        CardModel emulCard = selectedCard != null ? selectedCard : new CardModel();
        if (!uid.isEmpty()) emulCard.uid = uid;
        if (!aid.isEmpty()) emulCard.aid = aid;

        NfcWalletHceService.setActiveCard(emulCard);
        if (!customApdu.isEmpty()) {
            NfcWalletHceService.setCustomApduResponse(CardDetector.hexToBytes(customApdu));
        }

        emulationActive = true;
        btnToggleEmulation.setText("■ Stop Emulation");
        btnToggleEmulation.setBackgroundColor(getResources().getColor(R.color.color_error, null));
        tvEmulationStatus.setText("Emulating: " + emulCard.name);
        if (statusIndicator != null) statusIndicator.setBackgroundColor(
                getResources().getColor(R.color.color_success, null));
        appendLog("=== Emulation Started ===");
        appendLog("Card: " + emulCard.name);
        appendLog("UID: " + emulCard.uid);
        appendLog("AID: " + emulCard.aid);
    }

    private void stopEmulation() {
        NfcWalletHceService.setActiveCard(null);
        NfcWalletHceService.setCustomApduResponse(null);
        NfcWalletHceService.setActiveAid(null);
        NfcWalletHceService.clearLog();

        emulationActive = false;
        btnToggleEmulation.setText("▶ Start Emulation");
        btnToggleEmulation.setBackgroundColor(getResources().getColor(R.color.color_primary, null));
        tvEmulationStatus.setText("Emulation stopped");
        if (statusIndicator != null) statusIndicator.setBackgroundColor(
                getResources().getColor(R.color.text_disabled, null));
        appendLog("=== Emulation Stopped ===");
    }

    private void spoofUid() {
        String uid = etUid.getText() != null ? etUid.getText().toString().trim() : "";
        if (uid.isEmpty()) {
            Toast.makeText(this, "Enter a UID to spoof", Toast.LENGTH_SHORT).show();
            return;
        }
        appendLog("Attempting UID spoof: " + uid + " (requires root)");
        new Thread(() -> {
            byte[] uidBytes = CardDetector.hexToBytes(uid);
            boolean ok = RootUtils.spoofUidViaNci(uidBytes);
            runOnUiThread(() -> {
                if (ok) {
                    appendLog("UID spoof: SUCCESS");
                    Toast.makeText(this, "UID spoof successful", Toast.LENGTH_SHORT).show();
                } else {
                    appendLog("UID spoof: FAILED (root required or unsupported hardware)");
                    Toast.makeText(this, "UID spoof failed — root required", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void checkRootAvailability() {
        new Thread(() -> {
            boolean rooted = RootUtils.isRooted();
            runOnUiThread(() -> {
                if (!rooted && btnSpoofUid != null) {
                    btnSpoofUid.setAlpha(0.5f);
                    btnSpoofUid.setEnabled(false);
                    btnSpoofUid.setText("Spoof UID (Root Required)");
                }
            });
        }).start();
    }

    private void setupLogRefresh() {
        logRefreshRunnable = () -> {
            if (emulationActive) {
                String[] log = NfcWalletHceService.getApduLog();
                if (log.length > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (String entry : log) sb.append(entry).append("\n");
                    tvEmulationLog.setText(sb.toString());
                    scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
                }
            }
            logHandler.postDelayed(logRefreshRunnable, LOG_REFRESH_INTERVAL_MS);
        };
        logHandler.postDelayed(logRefreshRunnable, LOG_REFRESH_INTERVAL_MS);
    }

    private void appendLog(String text) {
        tvEmulationLog.append(text + "\n");
        scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        logHandler.removeCallbacks(logRefreshRunnable);
    }
}
