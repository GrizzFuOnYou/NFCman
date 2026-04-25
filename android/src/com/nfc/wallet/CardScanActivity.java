package com.nfc.wallet;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcF;
import android.nfc.tech.NfcV;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class CardScanActivity extends AppCompatActivity {

    private NfcAdapter nfcAdapter;
    private PendingIntent pendingIntent;
    private IntentFilter[] intentFilters;
    private String[][] techLists;

    private TextView tvScanStatus;
    private TextView tvScanLog;
    private ScrollView scrollLog;
    private View layoutButtons;
    private Button btnSave;
    private Button btnEditSave;
    private View nfcIndicator;

    private CardModel scannedCard = null;
    private CardManager cardManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_card_scan);

        cardManager = new CardManager(this);
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        tvScanStatus = findViewById(R.id.tv_scan_status);
        tvScanLog = findViewById(R.id.tv_scan_log);
        scrollLog = findViewById(R.id.scroll_log);
        layoutButtons = findViewById(R.id.layout_buttons);
        btnSave = findViewById(R.id.btn_save_card);
        btnEditSave = findViewById(R.id.btn_edit_save);
        nfcIndicator = findViewById(R.id.nfc_indicator);

        setupNfcDispatch();
        setupButtons();

        if (nfcAdapter == null) {
            tvScanStatus.setText("NFC not available on this device");
        } else if (!nfcAdapter.isEnabled()) {
            tvScanStatus.setText("NFC is disabled. Please enable NFC in Settings.");
        } else {
            tvScanStatus.setText("Ready to scan — hold card near device");
            startPulseAnimation();
        }
    }

    private void setupNfcDispatch() {
        Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        IntentFilter[] filters = {
                new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
                new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
                new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        };
        intentFilters = filters;

        techLists = new String[][] {
                {NfcA.class.getName()},
                {NfcB.class.getName()},
                {NfcF.class.getName()},
                {NfcV.class.getName()},
                {IsoDep.class.getName()},
                {MifareClassic.class.getName()},
                {MifareUltralight.class.getName()},
                {Ndef.class.getName()},
                {NdefFormatable.class.getName()}
        };
    }

    private void setupButtons() {
        btnSave.setOnClickListener(v -> saveCard());
        btnEditSave.setOnClickListener(v -> editBeforeSave());
    }

    private void startPulseAnimation() {
        if (nfcIndicator == null) return;
        nfcIndicator.animate().scaleX(1.2f).scaleY(1.2f).setDuration(700)
                .withEndAction(() ->
                        nfcIndicator.animate().scaleX(1.0f).scaleY(1.0f).setDuration(700)
                                .withEndAction(this::startPulseAnimation).start()
                ).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null && nfcAdapter.isEnabled()) {
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFilters, techLists);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (tag != null) {
                handleTag(tag);
            }
        }
    }

    private void handleTag(Tag tag) {
        tvScanStatus.setText("Card detected! Reading...");
        appendLog("=== New Card Scan ===");
        appendLog("UID: " + CardDetector.bytesToHex(tag.getId()));
        appendLog("Tech list: " + String.join(", ", tag.getTechList()));

        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null) vibrator.vibrate(100);

        new Thread(() -> {
            CardModel card = CardDetector.buildCardFromTag(tag, this);
            runOnUiThread(() -> {
                if (card != null) {
                    scannedCard = card;
                    appendLog("Card Type: " + card.cardType);
                    appendLog("Company: " + card.company);
                    appendLog("AID: " + card.aid);
                    appendLog("ATR: " + card.atr);
                    if (!card.apduSelectResponse.isEmpty()) {
                        appendLog("APDU SELECT Response: " + card.apduSelectResponse);
                    }
                    appendLog("Techs: " + String.join(", ", card.techs));
                    for (java.util.Map.Entry<String, String> e : card.extraFields.entrySet()) {
                        appendLog(e.getKey() + ": " + e.getValue());
                    }
                    appendLog("=== Scan complete ===");
                    tvScanStatus.setText("Scan complete! UID: " + card.uid);
                    layoutButtons.setVisibility(View.VISIBLE);
                } else {
                    appendLog("Failed to read card");
                    tvScanStatus.setText("Read failed — try again");
                }
            });
        }).start();
    }

    private void saveCard() {
        if (scannedCard == null) return;
        boolean ok = cardManager.saveCard(scannedCard);
        if (ok) {
            Toast.makeText(this, "Card saved: " + scannedCard.name, Toast.LENGTH_SHORT).show();
            layoutButtons.setVisibility(View.GONE);
            tvScanStatus.setText("Card saved! Scan another card.");
            scannedCard = null;
        } else {
            Toast.makeText(this, "Failed to save card", Toast.LENGTH_SHORT).show();
        }
    }

    private void editBeforeSave() {
        if (scannedCard == null) return;
        Intent intent = new Intent(this, ManualEntryActivity.class);
        intent.putExtra("card_name", scannedCard.name);
        intent.putExtra("card_uid", scannedCard.uid);
        intent.putExtra("card_type", scannedCard.cardType);
        intent.putExtra("card_company", scannedCard.company);
        intent.putExtra("card_aid", scannedCard.aid);
        intent.putExtra("card_atr", scannedCard.atr);
        intent.putExtra("card_apdu_response", scannedCard.apduSelectResponse);
        intent.putExtra("card_notes", scannedCard.notes);
        startActivity(intent);
    }

    private void appendLog(String text) {
        tvScanLog.append(text + "\n");
        scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
    }
}
