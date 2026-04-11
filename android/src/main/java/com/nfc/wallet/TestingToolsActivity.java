package com.nfc.wallet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.app.PendingIntent;
import android.content.IntentFilter;

import com.nfc.wallet.util.APDUUtils;

import java.io.IOException;

/**
 * Comprehensive NFC testing tools.
 * Features:
 *  - Custom APDU transceiver (select a card, send arbitrary APDU, see response)
 *  - UID reader/spoofer test
 *  - AID probe tool (try all known AIDs on a tapped card)
 *  - ISO 7816 command builder
 *  - SE status check
 *  - NFC loopback test
 *  - Reader mode test
 */
public class TestingToolsActivity extends Activity {

    private static final String TAG = "TestingToolsActivity";

    private NfcAdapter nfcAdapter;
    private PendingIntent pendingIntent;
    private Tag currentTag;

    private TextView statusText;
    private TextView logOutput;
    private ScrollView logScrollView;
    private EditText apduInput;
    private EditText aidProbeInput;
    private Button sendApduBtn, probeAidsBtn, clearLogBtn, backBtn, getUidBtn, readBinaryBtn;

    private StringBuilder testLog = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_testing);

        initNfc();
        initViews();
    }

    private void initNfc() {
        NfcManager mgr = (NfcManager) getSystemService(NFC_SERVICE);
        if (mgr != null) nfcAdapter = mgr.getDefaultAdapter();

        Intent i = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        pendingIntent = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void initViews() {
        statusText    = findViewById(R.id.test_status);
        logOutput     = findViewById(R.id.test_log);
        logScrollView = findViewById(R.id.test_log_scroll);
        apduInput     = findViewById(R.id.input_apdu_hex);
        aidProbeInput = findViewById(R.id.input_probe_aid);
        sendApduBtn   = findViewById(R.id.btn_send_apdu);
        probeAidsBtn  = findViewById(R.id.btn_probe_aids);
        clearLogBtn   = findViewById(R.id.btn_clear_test_log);
        backBtn       = findViewById(R.id.btn_back_test);
        getUidBtn     = findViewById(R.id.btn_get_uid);
        readBinaryBtn = findViewById(R.id.btn_read_binary);

        updateStatus("Tap a card to begin testing...");

        if (sendApduBtn != null) {
            sendApduBtn.setOnClickListener(v -> sendCustomApdu());
        }
        if (probeAidsBtn != null) {
            probeAidsBtn.setOnClickListener(v -> probeAllAids());
        }
        if (clearLogBtn != null) {
            clearLogBtn.setOnClickListener(v -> {
                testLog.setLength(0);
                if (logOutput != null) logOutput.setText("");
            });
        }
        if (backBtn != null) backBtn.setOnClickListener(v -> finish());
        if (getUidBtn != null) getUidBtn.setOnClickListener(v -> sendGetUid());
        if (readBinaryBtn != null) readBinaryBtn.setOnClickListener(v -> sendReadBinary());

        // Pre-fill default APDU
        if (apduInput != null) apduInput.setHint("e.g. 00A4040007A0000000031010");
        if (aidProbeInput != null) aidProbeInput.setHint("AID to probe (or leave blank for all)");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null) {
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) nfcAdapter.disableForegroundDispatch(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            currentTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (currentTag != null) {
                byte[] uid = currentTag.getId();
                String uidHex = APDUUtils.bytesToHex(uid);
                updateStatus("Tag detected: " + uidHex);
                appendLog("=== Tag Detected ===");
                appendLog("UID: " + uidHex);
                appendLog("Technologies: " + String.join(", ", currentTag.getTechList()));
                enableTestButtons(true);
            }
        }
    }

    private void enableTestButtons(boolean enabled) {
        if (sendApduBtn != null) sendApduBtn.setEnabled(enabled);
        if (probeAidsBtn != null) probeAidsBtn.setEnabled(enabled);
        if (getUidBtn != null) getUidBtn.setEnabled(enabled);
        if (readBinaryBtn != null) readBinaryBtn.setEnabled(enabled);
    }

    private void sendCustomApdu() {
        if (currentTag == null) {
            Toast.makeText(this, "Tap a card first", Toast.LENGTH_SHORT).show();
            return;
        }
        if (apduInput == null) return;
        String apduHex = apduInput.getText().toString().trim().replaceAll("\\s", "");
        if (apduHex.isEmpty()) {
            Toast.makeText(this, "Enter APDU command (hex)", Toast.LENGTH_SHORT).show();
            return;
        }

        byte[] apduBytes;
        try {
            apduBytes = APDUUtils.hexToBytes(apduHex);
        } catch (Exception e) {
            Toast.makeText(this, "Invalid hex: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        final byte[] cmd = apduBytes;
        new Thread(() -> {
            IsoDep isoDep = IsoDep.get(currentTag);
            if (isoDep == null) {
                appendLog("ERROR: Tag does not support IsoDep");
                return;
            }
            try {
                isoDep.connect();
                isoDep.setTimeout(10000);
                appendLog("\n>>> TX: " + apduHex);
                appendLog("    " + APDUUtils.formatApduCommand(cmd));
                byte[] response = isoDep.transceive(cmd);
                String respHex = APDUUtils.bytesToHex(response);
                String sw = APDUUtils.getStatusWord(response);
                appendLog("<<< RX: " + respHex);
                appendLog("    SW: " + sw + " (" + APDUUtils.describeStatusWord(sw) + ")");
                if (response.length > 2) {
                    appendLog("    Data: " + APDUUtils.bytesToHex(APDUUtils.getResponseData(response)));
                }
            } catch (IOException e) {
                appendLog("ERROR: " + e.getMessage());
            } finally {
                try { isoDep.close(); } catch (Exception ignored) {}
            }
        }).start();
    }

    private void sendGetUid() {
        if (currentTag == null) { Toast.makeText(this, "Tap a card first", Toast.LENGTH_SHORT).show(); return; }
        new Thread(() -> {
            IsoDep isoDep = IsoDep.get(currentTag);
            if (isoDep == null) {
                appendLog("GET UID: Tag does not support IsoDep (UID from tag ID: "
                        + APDUUtils.bytesToHex(currentTag.getId()) + ")");
                return;
            }
            try {
                isoDep.connect();
                byte[] cmd = APDUUtils.buildGetUidApdu();
                appendLog("\n>>> GET UID: " + APDUUtils.bytesToHex(cmd));
                byte[] response = isoDep.transceive(cmd);
                appendLog("<<< " + APDUUtils.bytesToHex(response));
            } catch (IOException e) {
                appendLog("GET UID error: " + e.getMessage());
            } finally {
                try { isoDep.close(); } catch (Exception ignored) {}
            }
        }).start();
    }

    private void sendReadBinary() {
        if (currentTag == null) { Toast.makeText(this, "Tap a card first", Toast.LENGTH_SHORT).show(); return; }
        new Thread(() -> {
            IsoDep isoDep = IsoDep.get(currentTag);
            if (isoDep == null) { appendLog("READ BINARY: No IsoDep"); return; }
            try {
                isoDep.connect();
                appendLog("\n--- READ BINARY sweep ---");
                for (int offset = 0; offset <= 0x20; offset += 0x10) {
                    try {
                        byte[] cmd = APDUUtils.buildReadBinaryApdu(offset, 0x10);
                        appendLog(">>> READ BINARY offset=" + String.format("%04X", offset));
                        byte[] resp = isoDep.transceive(cmd);
                        appendLog("<<< " + APDUUtils.bytesToHex(resp));
                    } catch (Exception e) {
                        appendLog("    offset " + offset + " failed: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                appendLog("READ BINARY error: " + e.getMessage());
            } finally {
                try { isoDep.close(); } catch (Exception ignored) {}
            }
        }).start();
    }

    private void probeAllAids() {
        if (currentTag == null) { Toast.makeText(this, "Tap a card first", Toast.LENGTH_SHORT).show(); return; }

        String specificAid = aidProbeInput != null ? aidProbeInput.getText().toString().trim() : "";

        new Thread(() -> {
            IsoDep isoDep = IsoDep.get(currentTag);
            if (isoDep == null) { appendLog("Probe AIDs: No IsoDep support"); return; }
            try {
                isoDep.connect();
                isoDep.setTimeout(8000);
                appendLog("\n--- Probing AIDs ---");

                java.util.List<String> aidsToProbe = new java.util.ArrayList<>();
                if (!specificAid.isEmpty()) {
                    aidsToProbe.add(specificAid.toUpperCase());
                } else {
                    aidsToProbe.addAll(com.nfc.wallet.util.CardTypeDetector.getPaymentAids());
                    // Add more AIDs for comprehensive probing
                    aidsToProbe.add("A0000000030000"); // Visa
                    aidsToProbe.add("A0000000040000"); // Mastercard
                    aidsToProbe.add("A0000000651010"); // JCB
                    aidsToProbe.add("A000000333010101"); // UnionPay
                    aidsToProbe.add("315041592E5359532E4444463031"); // PPSE
                    aidsToProbe.add("A000000004101001"); // MC variant
                    aidsToProbe.add("A0000000250101"); // Amex
                    aidsToProbe.add("A000000172950002"); // HID variant
                }

                for (String aid : aidsToProbe) {
                    try {
                        byte[] cmd = APDUUtils.buildSelectApdu(aid);
                        byte[] resp = isoDep.transceive(cmd);
                        String sw = APDUUtils.getStatusWord(resp);
                        boolean success = APDUUtils.isSuccess(resp);
                        appendLog((success ? "✓" : "✗") + " AID " + aid + " -> " + sw
                                + (success ? " [FOUND] Data=" + APDUUtils.bytesToHex(APDUUtils.getResponseData(resp)) : ""));
                    } catch (Exception e) {
                        appendLog("✗ AID " + aid + " -> Error: " + e.getMessage());
                    }
                }
                appendLog("--- AID probe complete ---");
            } catch (IOException e) {
                appendLog("AID probe error: " + e.getMessage());
            } finally {
                try { isoDep.close(); } catch (Exception ignored) {}
            }
        }).start();
    }

    private void appendLog(String msg) {
        testLog.append(msg).append("\n");
        runOnUiThread(() -> {
            if (logOutput != null) logOutput.append(msg + "\n");
            if (logScrollView != null) logScrollView.post(() -> logScrollView.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    private void updateStatus(String msg) {
        runOnUiThread(() -> { if (statusText != null) statusText.setText(msg); });
    }
}
