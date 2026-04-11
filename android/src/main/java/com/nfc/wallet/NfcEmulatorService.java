package com.nfc.wallet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.nfc.cardemulation.HostApduService;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.nfc.wallet.model.CardModel;
import com.nfc.wallet.util.APDUUtils;
import com.nfc.wallet.util.CardStorageManager;

import java.util.Arrays;

import java.util.Arrays;
import java.util.List;

/**
 * Enhanced NFC Host Card Emulation (HCE) service.
 * Supports:
 *  - Full ISO 7816-4 APDU processing
 *  - Dynamic AID routing (from loaded card)
 *  - UID spoofing (via GET UID command response)
 *  - MIFARE command emulation
 *  - NDEF response emulation
 *  - Custom APDU command/response mapping
 *  - Payment card simulation (SELECT, GPO, READ RECORD)
 *  - Real-time foreground notification
 */
public class NfcEmulatorService extends HostApduService {

    private static final String TAG = "NfcEmulatorService";
    private static final String CHANNEL_ID = "nfc_emulation";
    private static final int NOTIF_ID = 42;

    public static final String EXTRA_CARD_FILE = "card_file";

    // Current emulation card
    private CardModel activeCard;
    private CardStorageManager storageManager;

    // APDU parsing state
    private String lastSelectedAid = "";
    private boolean cardSelected = false;

    @Override
    public void onCreate() {
        super.onCreate();
        storageManager = new CardStorageManager(this);
        createNotificationChannel();
        Log.d(TAG, "NfcEmulatorService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String cardFile = intent.getStringExtra(EXTRA_CARD_FILE);
            if (cardFile != null && !cardFile.isEmpty()) {
                activeCard = storageManager.loadCard(cardFile);
                if (activeCard != null) {
                    Log.d(TAG, "Loaded card for emulation: " + activeCard.getLabel());
                }
            }
        }
        startForeground(NOTIF_ID, buildNotification());
        return START_STICKY;
    }

    @Override
    public byte[] processCommandApdu(byte[] apdu, Bundle extras) {
        if (apdu == null || apdu.length < 2) {
            Log.d(TAG, "Invalid APDU received");
            return APDUUtils.SW_WRONG_LENGTH;
        }

        Log.d(TAG, "APDU RX: " + APDUUtils.bytesToHex(apdu));
        Log.d(TAG, "       " + APDUUtils.formatApduCommand(apdu));

        byte cla = apdu[0];
        byte ins = apdu[1];
        byte p1 = apdu.length > 2 ? apdu[2] : 0;
        byte p2 = apdu.length > 3 ? apdu[3] : 0;

        byte[] response;

        // Check custom APDU command map first
        if (activeCard != null) {
            response = checkCustomApduMap(apdu);
            if (response != null) {
                Log.d(TAG, "APDU TX (custom): " + APDUUtils.bytesToHex(response));
                return response;
            }
        }

        // ISO 7816 standard command handling
        if (cla == APDUUtils.CLA_ISO7816 && ins == APDUUtils.INS_SELECT) {
            response = handleSelect(apdu);
        } else if (cla == APDUUtils.CLA_CONTACTLESS && ins == APDUUtils.INS_GET_DATA) {
            // GET UID / GET DATA (FF CA ...)
            response = handleGetData(apdu);
        } else if (cla == APDUUtils.CLA_ISO7816 && ins == APDUUtils.INS_READ_BINARY) {
            response = handleReadBinary(apdu);
        } else if (cla == APDUUtils.CLA_ISO7816 && ins == APDUUtils.INS_READ_RECORD) {
            response = handleReadRecord(apdu);
        } else if ((cla == (byte)0x80 || cla == APDUUtils.CLA_ISO7816) && ins == APDUUtils.INS_GET_PROCESSING_OPTIONS) {
            response = handleGetProcessingOptions(apdu);
        } else if (cla == APDUUtils.CLA_ISO7816 && ins == APDUUtils.INS_GENERATE_AC) {
            response = handleGenerateAC(apdu);
        } else if (cla == APDUUtils.CLA_ISO7816 && ins == APDUUtils.INS_GET_CHALLENGE) {
            response = handleGetChallenge();
        } else if (cla == (byte)0x60 || cla == (byte)0x61) {
            // MIFARE Classic command (READ block = 0x30)
            response = handleMifareCommand(apdu);
        } else {
            response = handleDefaultCommand(apdu);
        }

        Log.d(TAG, "APDU TX: " + APDUUtils.bytesToHex(response));
        return response;
    }

    /**
     * Handles SELECT AID command.
     */
    private byte[] handleSelect(byte[] apdu) {
        String incomingAid = APDUUtils.parseAidFromSelectApdu(apdu);
        Log.d(TAG, "SELECT AID: " + incomingAid);

        if (activeCard == null) {
            return APDUUtils.SW_FILE_NOT_FOUND;
        }

        List<String> myAids = activeCard.getAllAids();

        // Check if requested AID matches any of our AIDs
        boolean aidMatched = false;
        if (!incomingAid.isEmpty()) {
            for (String myAid : myAids) {
                if (myAid.equalsIgnoreCase(incomingAid)
                        || myAid.startsWith(incomingAid)
                        || incomingAid.startsWith(myAid)) {
                    aidMatched = true;
                    lastSelectedAid = myAid;
                    break;
                }
            }
        }

        if (aidMatched || myAids.isEmpty() || incomingAid.isEmpty()) {
            cardSelected = true;
            // Return FCI (File Control Information) with AID
            byte[] aidBytes = APDUUtils.hexToBytes(incomingAid.isEmpty() && !myAids.isEmpty() ? myAids.get(0) : incomingAid);
            return buildSelectResponse(aidBytes);
        } else {
            return APDUUtils.SW_FILE_NOT_FOUND;
        }
    }

    /**
     * Builds a SELECT response with FCI template.
     */
    private byte[] buildSelectResponse(byte[] selectedAid) {
        // FCI template: 6F [len] 84 [AID len] [AID bytes] A5 [len] 50 [label len] [label bytes] 9000
        String label = activeCard != null ? activeCard.getLabel() : "NFC_Wallet";
        if (label.length() > 16) label = label.substring(0, 16);
        byte[] labelBytes = label.getBytes();

        // Build FCI
        byte[] fciContent = new byte[4 + selectedAid.length + 4 + labelBytes.length];
        int pos = 0;
        fciContent[pos++] = (byte) 0x84; // DF Name tag
        fciContent[pos++] = (byte) selectedAid.length;
        System.arraycopy(selectedAid, 0, fciContent, pos, selectedAid.length);
        pos += selectedAid.length;
        fciContent[pos++] = (byte) 0xA5; // Proprietary data
        fciContent[pos++] = (byte) (2 + labelBytes.length);
        fciContent[pos++] = 0x50; // Application label tag
        fciContent[pos++] = (byte) labelBytes.length;
        System.arraycopy(labelBytes, 0, fciContent, pos, labelBytes.length);

        byte[] fci = new byte[2 + fciContent.length + 2];
        fci[0] = 0x6F;
        fci[1] = (byte) fciContent.length;
        System.arraycopy(fciContent, 0, fci, 2, fciContent.length);
        fci[fci.length - 2] = (byte) 0x90;
        fci[fci.length - 1] = 0x00;
        return fci;
    }

    /**
     * Handles GET DATA / GET UID command (FF CA P1 P2 Le).
     */
    private byte[] handleGetData(byte[] apdu) {
        if (apdu.length >= 4 && apdu[2] == 0x00 && apdu[3] == 0x00) {
            // GET UID
            String uidHex = activeCard != null ? activeCard.getEmulationUid() : "";
            if (uidHex == null || uidHex.isEmpty()) {
                return APDUUtils.SW_FILE_NOT_FOUND;
            }
            byte[] uid = APDUUtils.hexToBytes(uidHex);
            return APDUUtils.buildUidTlvResponse(uid);
        }
        return APDUUtils.SW_INS_NOT_SUPPORTED;
    }

    /**
     * Handles READ BINARY command.
     */
    private byte[] handleReadBinary(byte[] apdu) {
        if (!cardSelected) return APDUUtils.SW_COND_NOT_SATISFIED;

        int offset = ((apdu[2] & 0xFF) << 8) | (apdu[3] & 0xFF);
        int length = apdu.length > 4 ? (apdu[4] & 0xFF) : 0x10;
        if (length == 0) length = 0x10;

        // Return dummy data from card if available
        String isoDepData = activeCard != null ? activeCard.getIsoDepData() : "";
        if (!isoDepData.isEmpty()) {
            byte[] data = buildBinaryResponse(offset, length);
            return APDUUtils.appendSuccess(data);
        }

        // Return zero-filled data
        byte[] data = new byte[Math.min(length, 16)];
        return APDUUtils.appendSuccess(data);
    }

    private byte[] buildBinaryResponse(int offset, int length) {
        // Build a basic response based on card data
        byte[] data = new byte[Math.min(length, 32)];
        if (activeCard != null && !activeCard.getUid().isEmpty()) {
            byte[] uid = APDUUtils.hexToBytes(activeCard.getEmulationUid());
            System.arraycopy(uid, 0, data, 0, Math.min(uid.length, data.length));
        }
        return data;
    }

    /**
     * Handles READ RECORD command (used by EMV payment cards).
     */
    private byte[] handleReadRecord(byte[] apdu) {
        if (!cardSelected) return APDUUtils.SW_COND_NOT_SATISFIED;
        if (activeCard == null) return APDUUtils.SW_RECORD_NOT_FOUND;

        int sfi = (apdu[3] >> 3) & 0x1F;
        int recordNum = apdu[2] & 0xFF;

        // Build a minimal EMV record response
        String cardNumber = activeCard.getCardNumber();
        String cardHolder = activeCard.getCardholderName();
        String expiry = activeCard.getExpiryDate();

        if (!cardNumber.isEmpty() && sfi <= 2) {
            return buildEmvRecordResponse(cardNumber, cardHolder, expiry);
        }
        return APDUUtils.SW_RECORD_NOT_FOUND;
    }

    private byte[] buildEmvRecordResponse(String pan, String name, String expiry) {
        // Minimal EMV record: 70 [len] 57 [PAN+expiry len] [PAN+expiry] 5A [PAN len] [PAN] 9000
        String cleanPan = pan.replaceAll("\\s|-", "");
        if (cleanPan.isEmpty()) cleanPan = "0000000000000000";
        if (cleanPan.length() < 16) cleanPan = cleanPan + "0".repeat(16 - cleanPan.length());

        byte[] panBytes = APDUUtils.hexToBytes(cleanPan);
        byte[] resp = new byte[4 + panBytes.length + 2];
        resp[0] = 0x70; // Record template
        resp[1] = (byte) (2 + panBytes.length);
        resp[2] = 0x5A; // PAN tag
        resp[3] = (byte) panBytes.length;
        System.arraycopy(panBytes, 0, resp, 4, panBytes.length);
        resp[resp.length - 2] = (byte) 0x90;
        resp[resp.length - 1] = 0x00;
        return resp;
    }

    /**
     * Handles GET PROCESSING OPTIONS (GPO) - EMV payment initiation.
     */
    private byte[] handleGetProcessingOptions(byte[] apdu) {
        if (!cardSelected) return APDUUtils.SW_COND_NOT_SATISFIED;
        // Return minimal AIP + AFL
        byte[] resp = new byte[]{
                (byte) 0x80, // Response message template
                0x06,
                0x00, 0x40, // AIP: SDA supported
                0x08, 0x01, 0x01, 0x00, // AFL: SFI=1, first record=1, last record=1
                (byte) 0x90, 0x00
        };
        return resp;
    }

    /**
     * Handles GENERATE AC command.
     */
    private byte[] handleGenerateAC(byte[] apdu) {
        // Return a dummy cryptogram
        byte[] tc = new byte[]{
                (byte) 0x80, 0x0A,
                0x40, // Transaction Cryptogram type
                0x00, // ATC[1]
                0x01, // ATC[2]
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // Cryptogram (8 bytes)
                (byte) 0x90, 0x00
        };
        return tc;
    }

    /**
     * Handles GET CHALLENGE command.
     */
    private byte[] handleGetChallenge() {
        byte[] challenge = new byte[10];
        new java.util.Random().nextBytes(challenge);
        return APDUUtils.appendSuccess(challenge);
    }

    /**
     * Handles MIFARE Classic-style commands.
     */
    private byte[] handleMifareCommand(byte[] apdu) {
        if (apdu[0] == 0x30) {
            // READ command - return block data
            int block = apdu.length > 1 ? (apdu[1] & 0xFF) : 0;
            if (activeCard != null && !activeCard.getMifareData().isEmpty()) {
                // Parse block data from card
                String blockKey = "S" + (block / 4) + "B" + block + "=";
                String mifareData = activeCard.getMifareData();
                int idx = mifareData.indexOf(blockKey);
                if (idx >= 0) {
                    int end = mifareData.indexOf('\n', idx);
                    String blockHex = mifareData.substring(idx + blockKey.length(),
                            end > idx ? end : mifareData.length());
                    byte[] blockData = APDUUtils.hexToBytes(blockHex);
                    return APDUUtils.appendSuccess(blockData);
                }
            }
            // Return empty block
            return APDUUtils.appendSuccess(new byte[16]);
        }
        return APDUUtils.SW_INS_NOT_SUPPORTED;
    }

    /**
     * Checks if the incoming APDU matches any custom command mapping.
     */
    private byte[] checkCustomApduMap(byte[] apdu) {
        String customCmds = activeCard.getCustomApduCommands();
        String customResps = activeCard.getCustomApduResponses();
        if (customCmds.isEmpty() || customResps.isEmpty()) return null;

        String[] cmds = customCmds.split("[,\\n]");
        String[] resps = customResps.split("[,\\n]");
        String apduHex = APDUUtils.bytesToHex(apdu).toUpperCase();

        for (int i = 0; i < cmds.length && i < resps.length; i++) {
            String cmd = cmds[i].trim().toUpperCase();
            if (!cmd.isEmpty() && apduHex.startsWith(cmd)) {
                String resp = resps[i].trim();
                if (!resp.isEmpty()) {
                    return APDUUtils.hexToBytes(resp);
                }
            }
        }
        return null;
    }

    /**
     * Handles unrecognized APDU commands - returns default response.
     */
    private byte[] handleDefaultCommand(byte[] apdu) {
        if (activeCard != null) {
            String defaultResp = activeCard.getDefaultResponse();
            if (!defaultResp.isEmpty()) {
                try {
                    return APDUUtils.hexToBytes(defaultResp);
                } catch (Exception ignored) {}
            }
        }
        return APDUUtils.SW_INS_NOT_SUPPORTED;
    }

    @Override
    public void onDeactivated(int reason) {
        cardSelected = false;
        lastSelectedAid = "";
        String reasonStr = reason == DEACTIVATION_LINK_LOSS ? "LINK_LOSS" : "DESELECTED";
        Log.d(TAG, "NFC deactivated: " + reasonStr);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "NFC Emulation", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("NFC card emulation status");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent("com.nfc.wallet.STOP_EMULATION");
        PendingIntent stopPending = PendingIntent.getBroadcast(this, 0, stopIntent,
                PendingIntent.FLAG_IMMUTABLE);

        Intent openIntent = new Intent(this, CardEmulationActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_IMMUTABLE);

        String cardName = activeCard != null ? activeCard.getLabel() : "Unknown card";

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentTitle("NFC_Wallet: Emulating")
                .setContentText(cardName)
                .setContentIntent(openPending)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending)
                .setOngoing(true)
                .build();
    }
}
