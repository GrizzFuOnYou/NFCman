package com.nfc.wallet;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
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
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.nfc.wallet.model.CardModel;
import com.nfc.wallet.util.APDUUtils;
import com.nfc.wallet.util.CardStorageManager;
import com.nfc.wallet.util.CardTypeDetector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Comprehensive NFC card scanner.
 * Reads all NFC tag types: MIFARE Classic/Ultralight, NTAG, IsoDep, NfcA/B/F/V, NDEF.
 * Auto-detects card type and injects APDU/AID/UID data accordingly.
 * Saves card as a text file for later use.
 */
public class NFCScanActivity extends Activity {

    private static final String TAG = "NFCScanActivity";

    private TextView statusText;
    private TextView logText;
    private ScrollView logScrollView;
    private ProgressBar progressBar;
    private Button clearBtn, saveBtn, backBtn;

    private NfcAdapter nfcAdapter;
    private PendingIntent pendingIntent;
    private IntentFilter[] intentFilters;
    private String[][] techLists;

    private CardModel currentCard;
    private CardStorageManager storageManager;
    private StringBuilder scanLog = new StringBuilder();

    // Standard MIFARE Classic keys
    private static final byte[][] MIFARE_KEYS = {
            MifareClassic.KEY_DEFAULT,
            MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY,
            new byte[]{(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF},
            new byte[]{(byte)0xA0,(byte)0xA1,(byte)0xA2,(byte)0xA3,(byte)0xA4,(byte)0xA5},
            new byte[]{(byte)0xD3,(byte)0xF7,(byte)0xD3,(byte)0xF7,(byte)0xD3,(byte)0xF7},
            new byte[]{(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00},
            new byte[]{(byte)0xB0,(byte)0xB1,(byte)0xB2,(byte)0xB3,(byte)0xB4,(byte)0xB5},
            new byte[]{(byte)0x4D,(byte)0x3A,(byte)0x99,(byte)0xC3,(byte)0x51,(byte)0xDD},
            new byte[]{(byte)0x1A,(byte)0x98,(byte)0x2C,(byte)0x7E,(byte)0x45,(byte)0x9A},
            new byte[]{(byte)0xAA,(byte)0xBB,(byte)0xCC,(byte)0xDD,(byte)0xEE,(byte)0xFF},
            new byte[]{(byte)0x71,(byte)0x4C,(byte)0x5C,(byte)0x88,(byte)0x6E,(byte)0x97},
            new byte[]{(byte)0x58,(byte)0x7E,(byte)0xE5,(byte)0xF9,(byte)0x35,(byte)0x0F}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nfc_scan);

        storageManager = new CardStorageManager(this);
        initViews();
        initNfc();
    }

    private void initViews() {
        statusText = findViewById(R.id.scan_status);
        logText = findViewById(R.id.scan_log);
        logScrollView = findViewById(R.id.log_scroll);
        progressBar = findViewById(R.id.scan_progress);

        clearBtn = findViewById(R.id.btn_clear_log);
        saveBtn = findViewById(R.id.btn_save_card);
        backBtn = findViewById(R.id.btn_back);

        clearBtn.setOnClickListener(v -> {
            scanLog.setLength(0);
            logText.setText("");
            currentCard = null;
            saveBtn.setEnabled(false);
            statusText.setText("Cleared. Tap an NFC card to scan...");
        });

        saveBtn.setOnClickListener(v -> saveCurrentCard());
        saveBtn.setEnabled(false);

        backBtn.setOnClickListener(v -> finish());

        updateStatus("Tap an NFC card to scan...");
    }

    private void initNfc() {
        NfcManager nfcManager = (NfcManager) getSystemService(NFC_SERVICE);
        if (nfcManager != null) {
            nfcAdapter = nfcManager.getDefaultAdapter();
        }

        if (nfcAdapter == null) {
            updateStatus("NFC not available on this device.");
            return;
        }

        // Set up foreground dispatch
        Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        IntentFilter allTags = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        try { ndef.addDataType("*/*"); } catch (Exception ignored) {}

        intentFilters = new IntentFilter[]{ndef, allTags};
        techLists = new String[][]{
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

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null) {
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
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (tag != null) {
                vibrate();
                processTag(tag);
            }
        }
    }

    private void vibrate() {
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null) v.vibrate(150);
        } catch (Exception ignored) {}
    }

    private void processTag(Tag tag) {
        scanLog.setLength(0);
        currentCard = new CardModel();
        currentCard.setManualEntry(false);

        progressBar.setVisibility(View.VISIBLE);
        saveBtn.setEnabled(false);
        updateStatus("Reading tag...");

        new Thread(() -> {
            try {
                // Read basic UID
                byte[] uid = tag.getId();
                String uidHex = APDUUtils.bytesToHex(uid);
                String uidReversed = APDUUtils.bytesToHex(APDUUtils.reverseBytes(uid));
                currentCard.setUid(uidHex);
                currentCard.setUidReversed(uidReversed);
                currentCard.setLabel("Card_" + uidHex);

                appendLog("=== NFC Tag Detected ===");
                appendLog("UID: " + uidHex + " (" + uid.length + " bytes)");
                appendLog("UID Reversed: " + uidReversed);
                appendLog("Technologies: " + String.join(", ", tag.getTechList()));

                // Auto-detect card type
                CardTypeDetector.detectAndPopulate(tag, currentCard);
                appendLog("Detected Type: " + currentCard.getCardType().getDisplayName());

                // Read NfcA parameters
                readNfcAData(tag);
                // Read NfcB parameters
                readNfcBData(tag);
                // Read NfcF parameters
                readNfcFData(tag);
                // Read NfcV parameters
                readNfcVData(tag);
                // Read MIFARE data
                readMifareClassicData(tag);
                // Read MIFARE Ultralight
                readMifareUltralightData(tag);
                // Read NDEF
                readNdefData(tag);
                // Read IsoDep / payment card data
                readIsoDepData(tag);

                currentCard.setRawDump(scanLog.toString());
                currentCard.setCardColor(CardTypeDetector.getSuggestedCardColor(currentCard));

                appendLog("\n=== Scan Complete ===");
                appendLog("Card type: " + currentCard.getCardType().getDisplayName());
                if (!currentCard.getPrimaryAid().isEmpty()) {
                    appendLog("Primary AID: " + currentCard.getPrimaryAid());
                }

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    saveBtn.setEnabled(true);
                    updateStatus("Scan complete: " + currentCard.getCardType().getDisplayName()
                            + " | UID: " + uidHex);
                    scrollLogToBottom();
                });

            } catch (Exception e) {
                Log.e(TAG, "Error processing tag", e);
                appendLog("ERROR: " + e.getMessage());
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    updateStatus("Error reading tag: " + e.getMessage());
                });
            }
        }).start();
    }

    private void readNfcAData(Tag tag) {
        NfcA nfcA = NfcA.get(tag);
        if (nfcA == null) return;
        appendLog("\n--- NFC-A (ISO 14443-3A) ---");
        appendLog("ATQA: " + APDUUtils.bytesToHex(nfcA.getAtqa()));
        appendLog("SAK: " + String.format("%02X", (byte) nfcA.getSak()));
        appendLog("Max Transceive Length: " + nfcA.getMaxTransceiveLength());
        currentCard.setNfcAData("ATQA=" + APDUUtils.bytesToHex(nfcA.getAtqa())
                + " SAK=" + String.format("%02X", (byte) nfcA.getSak()));
    }

    private void readNfcBData(Tag tag) {
        NfcB nfcB = NfcB.get(tag);
        if (nfcB == null) return;
        appendLog("\n--- NFC-B (ISO 14443-3B) ---");
        appendLog("Application Data: " + APDUUtils.bytesToHex(nfcB.getApplicationData()));
        appendLog("Protocol Info: " + APDUUtils.bytesToHex(nfcB.getProtocolInfo()));
        currentCard.setNfcBData("AppData=" + APDUUtils.bytesToHex(nfcB.getApplicationData())
                + " ProtInfo=" + APDUUtils.bytesToHex(nfcB.getProtocolInfo()));
    }

    private void readNfcFData(Tag tag) {
        NfcF nfcF = NfcF.get(tag);
        if (nfcF == null) return;
        appendLog("\n--- NFC-F (FeliCa) ---");
        appendLog("Manufacturer: " + APDUUtils.bytesToHex(nfcF.getManufacturer()));
        appendLog("System Code: " + APDUUtils.bytesToHex(nfcF.getSystemCode()));
        currentCard.setNfcFData("Manufacturer=" + APDUUtils.bytesToHex(nfcF.getManufacturer())
                + " SystemCode=" + APDUUtils.bytesToHex(nfcF.getSystemCode()));
    }

    private void readNfcVData(Tag tag) {
        NfcV nfcV = NfcV.get(tag);
        if (nfcV == null) return;
        appendLog("\n--- NFC-V (ISO 15693) ---");
        appendLog("DSF ID: " + String.format("%02X", nfcV.getDsfId()));
        appendLog("Response Flags: " + String.format("%02X", nfcV.getResponseFlags()));
        currentCard.setNfcVData("DsfId=" + String.format("%02X", nfcV.getDsfId())
                + " ResponseFlags=" + String.format("%02X", nfcV.getResponseFlags()));
    }

    private void readMifareClassicData(Tag tag) {
        MifareClassic mifare = MifareClassic.get(tag);
        if (mifare == null) return;
        appendLog("\n--- MIFARE Classic ---");
        appendLog("Type: " + getMifareTypeName(mifare.getType()));
        appendLog("Size: " + mifare.getSize() + " bytes");
        appendLog("Sectors: " + mifare.getSectorCount());
        appendLog("Blocks: " + mifare.getBlockCount());

        StringBuilder mifareData = new StringBuilder();
        mifareData.append("Type=").append(getMifareTypeName(mifare.getType()))
                .append(" Sectors=").append(mifare.getSectorCount())
                .append(" Size=").append(mifare.getSize()).append("\n");

        try {
            mifare.connect();
            mifare.setTimeout(5000);

            for (int sector = 0; sector < mifare.getSectorCount(); sector++) {
                boolean authenticated = false;
                for (byte[] key : MIFARE_KEYS) {
                    try {
                        if (mifare.authenticateSectorWithKeyA(sector, key)) {
                            authenticated = true;
                            break;
                        }
                    } catch (Exception ignored) {}
                    try {
                        if (mifare.authenticateSectorWithKeyB(sector, key)) {
                            authenticated = true;
                            break;
                        }
                    } catch (Exception ignored) {}
                }

                appendLog("Sector " + sector + ": " + (authenticated ? "Auth OK" : "Auth FAILED"));
                if (authenticated) {
                    int firstBlock = mifare.sectorToBlock(sector);
                    int blockCount = mifare.getBlockCountInSector(sector);
                    for (int block = firstBlock; block < firstBlock + blockCount; block++) {
                        try {
                            byte[] data = mifare.readBlock(block);
                            String blockHex = APDUUtils.bytesToHex(data);
                            appendLog("  Block " + block + ": " + blockHex);
                            mifareData.append("S").append(sector).append("B").append(block)
                                    .append("=").append(blockHex).append("\n");
                        } catch (Exception e) {
                            appendLog("  Block " + block + ": Read error");
                        }
                    }
                }
            }
            currentCard.setMifareData(mifareData.toString());
        } catch (IOException e) {
            appendLog("MIFARE connect error: " + e.getMessage());
        } finally {
            try { mifare.close(); } catch (Exception ignored) {}
        }
    }

    private void readMifareUltralightData(Tag tag) {
        MifareUltralight ultralight = MifareUltralight.get(tag);
        if (ultralight == null) return;
        appendLog("\n--- MIFARE Ultralight ---");

        String subtypeName;
        switch (ultralight.getType()) {
            case MifareUltralight.TYPE_ULTRALIGHT: subtypeName = "Ultralight"; break;
            case MifareUltralight.TYPE_ULTRALIGHT_C: subtypeName = "Ultralight C"; break;
            default: subtypeName = "Unknown";
        }
        appendLog("Subtype: " + subtypeName);

        StringBuilder ulData = new StringBuilder("Subtype=").append(subtypeName).append("\n");
        try {
            ultralight.connect();
            ultralight.setTimeout(5000);
            for (int page = 0; page < 45; page++) {
                try {
                    byte[] data = ultralight.readPages(page);
                    String pageHex = APDUUtils.bytesToHex(Arrays.copyOfRange(data, 0, 4));
                    appendLog("Page " + String.format("%02d", page) + ": " + pageHex);
                    ulData.append("P").append(page).append("=").append(pageHex).append("\n");
                } catch (Exception e) {
                    break; // End of readable pages
                }
            }
            currentCard.setMifareData(ulData.toString());
        } catch (IOException e) {
            appendLog("Ultralight connect error: " + e.getMessage());
        } finally {
            try { ultralight.close(); } catch (Exception ignored) {}
        }
    }

    private void readNdefData(Tag tag) {
        Ndef ndef = Ndef.get(tag);
        if (ndef == null) return;
        appendLog("\n--- NDEF ---");
        appendLog("Type: " + ndef.getType());
        appendLog("Max size: " + ndef.getMaxSize() + " bytes");
        appendLog("Writable: " + ndef.isWritable());

        StringBuilder ndefData = new StringBuilder();
        ndefData.append("Type=").append(ndef.getType())
                .append(" MaxSize=").append(ndef.getMaxSize())
                .append(" Writable=").append(ndef.isWritable()).append("\n");

        try {
            ndef.connect();
            android.nfc.NdefMessage msg = ndef.getNdefMessage();
            if (msg != null) {
                android.nfc.NdefRecord[] records = msg.getRecords();
                appendLog("NDEF Records: " + records.length);
                for (int i = 0; i < records.length; i++) {
                    android.nfc.NdefRecord r = records[i];
                    String payload = APDUUtils.bytesToHex(r.getPayload());
                    String type = new String(r.getType(), StandardCharsets.UTF_8);
                    appendLog("Record " + i + ": TNF=" + r.getTnf() + " Type=" + type + " Payload=" + payload);
                    ndefData.append("R").append(i).append("_TNF=").append(r.getTnf()).append("\n");
                    ndefData.append("R").append(i).append("_Type=").append(type).append("\n");
                    ndefData.append("R").append(i).append("_Payload=").append(payload).append("\n");
                    // Try to decode text record
                    if (r.getTnf() == android.nfc.NdefRecord.TNF_WELL_KNOWN) {
                        try {
                            String text = new String(r.getPayload(), 3, r.getPayload().length - 3, StandardCharsets.UTF_8);
                            appendLog("  Decoded text: " + text);
                            ndefData.append("R").append(i).append("_Text=").append(text).append("\n");
                        } catch (Exception ignored) {}
                    }
                }
            } else {
                appendLog("NDEF message: empty");
            }
            currentCard.setNdefData(ndefData.toString());
        } catch (Exception e) {
            appendLog("NDEF read error: " + e.getMessage());
        } finally {
            try { ndef.close(); } catch (Exception ignored) {}
        }
    }

    private void readIsoDepData(Tag tag) {
        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null) return;
        appendLog("\n--- ISO-DEP (ISO 14443-4) ---");

        StringBuilder isoData = new StringBuilder();

        try {
            isoDep.connect();
            isoDep.setTimeout(5000);

            if (isoDep.getHistoricalBytes() != null) {
                String hist = APDUUtils.bytesToHex(isoDep.getHistoricalBytes());
                appendLog("Historical Bytes: " + hist);
                currentCard.setHistoricalBytes(hist);
                isoData.append("HistBytes=").append(hist).append("\n");
            }
            if (isoDep.getHiLayerResponse() != null) {
                String ats = APDUUtils.bytesToHex(isoDep.getHiLayerResponse());
                appendLog("ATS: " + ats);
                currentCard.setAts(ats);
                isoData.append("ATS=").append(ats).append("\n");
            }

            // Probe known AIDs
            for (String aid : CardTypeDetector.getPaymentAids()) {
                try {
                    byte[] selectCmd = APDUUtils.buildSelectApdu(aid);
                    byte[] response = isoDep.transceive(selectCmd);
                    String respHex = APDUUtils.bytesToHex(response);
                    String sw = APDUUtils.getStatusWord(response);
                    if (APDUUtils.isSuccess(response) || response.length > 2) {
                        appendLog("AID " + aid + " -> " + respHex + " (" + APDUUtils.describeStatusWord(sw) + ")");
                        isoData.append("AID_").append(aid).append("=").append(respHex).append("\n");
                        // Set primary AID to the first one that responds
                        if (currentCard.getPrimaryAid().isEmpty() || currentCard.getPrimaryAid().equals(CardTypeDetector.AID_CUSTOM)) {
                            if (APDUUtils.isSuccess(response)) {
                                currentCard.setPrimaryAid(aid);
                                // Also try GET PROCESSING OPTIONS
                                try {
                                    byte[] gpoResp = isoDep.transceive(APDUUtils.buildGetProcessingOptions());
                                    appendLog("GPO -> " + APDUUtils.bytesToHex(gpoResp));
                                    isoData.append("GPO_").append(aid).append("=").append(APDUUtils.bytesToHex(gpoResp)).append("\n");
                                } catch (Exception ignored) {}
                                // Try READ RECORDs
                                for (int sfi = 1; sfi <= 10; sfi++) {
                                    for (int rec = 1; rec <= 3; rec++) {
                                        try {
                                            byte[] readCmd = APDUUtils.buildReadRecordApdu(rec, sfi);
                                            byte[] readResp = isoDep.transceive(readCmd);
                                            if (APDUUtils.isSuccess(readResp)) {
                                                String recData = APDUUtils.bytesToHex(readResp);
                                                appendLog("SFI=" + sfi + " Rec=" + rec + " -> " + recData);
                                                isoData.append("SFI").append(sfi).append("_R").append(rec).append("=").append(recData).append("\n");
                                            }
                                        } catch (Exception ignored) {}
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.d(TAG, "AID " + aid + " failed: " + e.getMessage());
                }
            }

            // GET UID command
            try {
                byte[] uidResp = isoDep.transceive(APDUUtils.buildGetUidApdu());
                appendLog("GET UID response: " + APDUUtils.bytesToHex(uidResp));
                isoData.append("GetUID=").append(APDUUtils.bytesToHex(uidResp)).append("\n");
            } catch (Exception ignored) {}

            currentCard.setIsoDepData(isoData.toString());

        } catch (IOException e) {
            appendLog("ISO-DEP connect error: " + e.getMessage());
        } finally {
            try { isoDep.close(); } catch (Exception ignored) {}
        }

        // Update card type detection based on AID responses
        CardTypeDetector.inferCompany(currentCard);
    }

    private void saveCurrentCard() {
        if (currentCard == null) {
            Toast.makeText(this, "No card data to save", Toast.LENGTH_SHORT).show();
            return;
        }
        // Let user edit label before saving
        new AlertDialog.Builder(this)
                .setTitle("Save Card")
                .setMessage("Save card with label: " + currentCard.getLabel() + "?")
                .setPositiveButton("Save", (d, w) -> {
                    boolean saved = storageManager.saveCard(currentCard);
                    if (saved) {
                        Toast.makeText(this, "Card saved: " + currentCard.getLabel(), Toast.LENGTH_SHORT).show();
                        saveBtn.setEnabled(false);
                    } else {
                        Toast.makeText(this, "Error saving card", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Edit Label", (d, w) -> {
                    Intent intent = new Intent(this, ManualCardActivity.class);
                    intent.putExtra("prefill_card", currentCard.toTextFormat());
                    startActivity(intent);
                })
                .setNeutralButton("Cancel", null)
                .show();
    }

    private void appendLog(String msg) {
        scanLog.append(msg).append("\n");
        runOnUiThread(() -> {
            logText.append(msg + "\n");
            scrollLogToBottom();
        });
    }

    private void updateStatus(String msg) {
        runOnUiThread(() -> {
            if (statusText != null) statusText.setText(msg);
        });
    }

    private void scrollLogToBottom() {
        if (logScrollView != null) {
            logScrollView.post(() -> logScrollView.fullScroll(ScrollView.FOCUS_DOWN));
        }
    }

    private String getMifareTypeName(int type) {
        switch (type) {
            case MifareClassic.TYPE_CLASSIC: return "Classic";
            case MifareClassic.TYPE_PLUS: return "Plus";
            case MifareClassic.TYPE_PRO: return "Pro";
            default: return "Unknown";
        }
    }
}
