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
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcF;
import android.nfc.tech.NfcV;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

public class AdvancedTestActivity extends AppCompatActivity {

    private NfcAdapter nfcAdapter;
    private PendingIntent pendingIntent;
    private Tag currentTag = null;

    private TextInputEditText etApduInput;
    private TextView tvApduLog, tvTagInfo, tvNdefLog, tvNciLog, tvSeLog;
    private ScrollView scrollApdu, scrollTag, scrollNdef, scrollNci, scrollSe;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_advanced_test);

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        setupNfc();
        bindViews();
        setupButtons();
        loadRootSections();
    }

    private void setupNfc() {
        Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void bindViews() {
        etApduInput = findViewById(R.id.et_apdu_input);
        tvApduLog = findViewById(R.id.tv_apdu_log);
        tvTagInfo = findViewById(R.id.tv_tag_info);
        tvNdefLog = findViewById(R.id.tv_ndef_log);
        tvNciLog = findViewById(R.id.tv_nci_log);
        tvSeLog = findViewById(R.id.tv_se_log);
        scrollApdu = findViewById(R.id.scroll_apdu);
        scrollTag = findViewById(R.id.scroll_tag);
        scrollNdef = findViewById(R.id.scroll_ndef);
        scrollNci = findViewById(R.id.scroll_nci);
        scrollSe = findViewById(R.id.scroll_se);
    }

    private void setupButtons() {
        // APDU Console send button
        Button btnSend = findViewById(R.id.btn_send_apdu);
        if (btnSend != null) {
            btnSend.setOnClickListener(v -> sendApdu());
        }

        // Pre-built commands
        Button btnSelect = findViewById(R.id.btn_cmd_select);
        if (btnSelect != null) {
            btnSelect.setOnClickListener(v -> {
                etApduInput.setText("00A4040007A0000000031010");
                sendApdu();
            });
        }
        Button btnGetUid = findViewById(R.id.btn_cmd_getuid);
        if (btnGetUid != null) {
            btnGetUid.setOnClickListener(v -> {
                etApduInput.setText("FFCA000000");
                sendApdu();
            });
        }
        Button btnReadBin = findViewById(R.id.btn_cmd_readbin);
        if (btnReadBin != null) {
            btnReadBin.setOnClickListener(v -> {
                etApduInput.setText("00B0000020");
                sendApdu();
            });
        }
        Button btnGetData = findViewById(R.id.btn_cmd_getdata);
        if (btnGetData != null) {
            btnGetData.setOnClickListener(v -> {
                etApduInput.setText("80CA9F3600");
                sendApdu();
            });
        }

        // NCI Log (root)
        Button btnNciLog = findViewById(R.id.btn_load_nci);
        if (btnNciLog != null) {
            btnNciLog.setOnClickListener(v -> loadNciLog());
        }

        // SE Discovery (root)
        Button btnSeDiscover = findViewById(R.id.btn_se_discover);
        if (btnSeDiscover != null) {
            btnSeDiscover.setOnClickListener(v -> discoverSe());
        }

        // NDEF Write
        Button btnNdefWrite = findViewById(R.id.btn_ndef_write);
        if (btnNdefWrite != null) {
            btnNdefWrite.setOnClickListener(v -> writeNdef());
        }

        // Tag dump
        Button btnTagDump = findViewById(R.id.btn_tag_dump);
        if (btnTagDump != null) {
            btnTagDump.setOnClickListener(v -> dumpTag());
        }
    }

    private void sendApdu() {
        if (currentTag == null) {
            appendApduLog("[Error] No card tapped. Tap a card first.");
            return;
        }
        String apduHex = etApduInput.getText() != null ? etApduInput.getText().toString().trim() : "";
        if (apduHex.isEmpty()) {
            Toast.makeText(this, "Enter APDU hex", Toast.LENGTH_SHORT).show();
            return;
        }

        byte[] apduBytes = ApduHelper.hexToApdu(apduHex);
        appendApduLog(">>> " + ApduHelper.apduToHex(apduBytes));

        new Thread(() -> {
            IsoDep isoDep = IsoDep.get(currentTag);
            if (isoDep == null) {
                runOnUiThread(() -> appendApduLog("[Error] IsoDep not available on this tag"));
                return;
            }
            try {
                isoDep.connect();
                isoDep.setTimeout(5000);
                byte[] response = isoDep.transceive(apduBytes);
                String rspHex = ApduHelper.apduToHex(response);
                String status = ApduHelper.parseStatusWord(response);
                runOnUiThread(() -> {
                    appendApduLog("<<< " + rspHex);
                    appendApduLog("    [" + status + "]");
                });
            } catch (Exception e) {
                runOnUiThread(() -> appendApduLog("[Error] " + e.getMessage()));
            } finally {
                try { isoDep.close(); } catch (Exception ignore) {}
            }
        }).start();
    }

    private void dumpTag() {
        if (currentTag == null) {
            Toast.makeText(this, "Tap a card first", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            CardModel card = CardDetector.buildCardFromTag(currentTag, this);
            runOnUiThread(() -> {
                if (card == null) {
                    tvTagInfo.setText("Failed to read tag");
                    return;
                }
                StringBuilder sb = new StringBuilder();
                sb.append("UID: ").append(card.uid).append("\n");
                sb.append("Type: ").append(card.cardType).append("\n");
                sb.append("Company: ").append(card.company).append("\n");
                sb.append("AID: ").append(card.aid).append("\n");
                sb.append("ATR: ").append(card.atr).append("\n");
                sb.append("Techs: ").append(String.join(", ", card.techs)).append("\n\n");
                sb.append("=== Extra Data ===\n");
                for (java.util.Map.Entry<String, String> e : card.extraFields.entrySet()) {
                    sb.append(e.getKey()).append(": ").append(e.getValue()).append("\n");
                }
                tvTagInfo.setText(sb.toString());
            });
        }).start();
    }

    private void writeNdef() {
        appendNdefLog("NDEF write not yet implemented for this tag.");
        // TODO: Full NDEF write implementation
    }

    private void loadNciLog() {
        new Thread(() -> {
            String log = RootUtils.getNciLogDump();
            runOnUiThread(() -> {
                tvNciLog.setText(log);
                if (scrollNci != null) scrollNci.post(() -> scrollNci.fullScroll(View.FOCUS_DOWN));
            });
        }).start();
    }

    private void discoverSe() {
        new Thread(() -> {
            String info = RootUtils.readSecureElement();
            runOnUiThread(() -> tvSeLog.setText(info));
        }).start();
    }

    private void loadRootSections() {
        new Thread(() -> {
            boolean rooted = RootUtils.isRooted();
            runOnUiThread(() -> {
                if (!rooted) {
                    if (tvNciLog != null) tvNciLog.setText("Root required for NCI logs");
                    if (tvSeLog != null) tvSeLog.setText("Root required for SE discovery");
                }
            });
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null && nfcAdapter.isEnabled()) {
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
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag != null) {
            currentTag = tag;
            appendApduLog("Card tapped: UID=" + CardDetector.bytesToHex(tag.getId()));
            appendApduLog("Techs: " + String.join(", ", tag.getTechList()));
        }
    }

    private void appendApduLog(String text) {
        tvApduLog.append(text + "\n");
        if (scrollApdu != null) scrollApdu.post(() -> scrollApdu.fullScroll(View.FOCUS_DOWN));
    }

    private void appendNdefLog(String text) {
        if (tvNdefLog != null) tvNdefLog.append(text + "\n");
    }
}
