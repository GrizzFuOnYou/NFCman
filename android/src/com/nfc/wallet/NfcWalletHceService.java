package com.nfc.wallet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

public class NfcWalletHceService extends HostApduService {

    private static final String TAG = "NfcWalletHceService";
    private static final String CHANNEL_ID = "nfc_wallet_hce";
    private static final int NOTIFICATION_ID = 1001;
    private static final int LOG_BUFFER_SIZE = 50;

    // Static state shared with EmulationActivity
    private static CardModel activeCard = null;
    private static byte[] customApduResponse = null;
    private static byte[] activeAid = null;
    private static final Deque<String> apduLog = new ArrayDeque<>(LOG_BUFFER_SIZE);

    private static final byte[] SW_OK = {(byte) 0x90, 0x00};
    private static final byte[] SW_NOT_FOUND = {(byte) 0x6A, (byte) 0x82};
    private static final byte[] SW_CONDITIONS_NOT_SATISFIED = {(byte) 0x69, (byte) 0x85};
    private static final byte[] SW_WRONG_DATA = {(byte) 0x6A, (byte) 0x80};
    private static final byte[] SW_UNKNOWN = {(byte) 0x6F, 0x00};

    public static void setActiveCard(CardModel card) {
        activeCard = card;
        if (card != null && card.aid != null && !card.aid.isEmpty()) {
            activeAid = CardDetector.hexToBytes(card.aid);
        }
    }

    public static void setCustomApduResponse(byte[] response) {
        customApduResponse = response;
    }

    public static void setActiveAid(byte[] aid) {
        activeAid = aid;
    }

    public static String[] getApduLog() {
        synchronized (apduLog) {
            return apduLog.toArray(new String[0]);
        }
    }

    public static void clearLog() {
        synchronized (apduLog) {
            apduLog.clear();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("HCE Active"));
    }

    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        if (commandApdu == null || commandApdu.length == 0) return SW_UNKNOWN;

        String hexCmd = CardDetector.bytesToHex(commandApdu);
        logApdu("CMD: " + hexCmd);
        Log.d(TAG, "APDU CMD: " + hexCmd);

        byte[] response = handleApdu(commandApdu);
        logApdu("RSP: " + CardDetector.bytesToHex(response));
        Log.d(TAG, "APDU RSP: " + CardDetector.bytesToHex(response));

        updateNotification();
        return response;
    }

    private byte[] handleApdu(byte[] apdu) {
        if (apdu.length < 4) return SW_UNKNOWN;

        int ins = apdu[1] & 0xFF;
        int cla = apdu[0] & 0xFF;

        // SELECT by AID
        if (cla == 0x00 && ins == 0xA4 && apdu[2] == 0x04) {
            return handleSelectAid(apdu);
        }

        // GET UID (FFCA0000xx)
        if (cla == 0xFF && ins == 0xCA) {
            return handleGetUid();
        }

        // READ BINARY
        if (cla == 0x00 && ins == 0xB0) {
            return handleReadBinary(apdu);
        }

        // GET PROCESSING OPTIONS (for payment)
        if (cla == 0x80 && ins == 0xA8) {
            return handleGetProcessingOptions(apdu);
        }

        // GET DATA
        if (cla == 0x80 && ins == 0xCA) {
            return handleGetData(apdu);
        }

        // GENERATE APPLICATION CRYPTOGRAM
        if (cla == 0x80 && ins == 0xAE) {
            return handleGenerateAc(apdu);
        }

        // Custom response if set
        if (customApduResponse != null) {
            return ApduHelper.appendSw(customApduResponse, SW_OK);
        }

        return SW_CONDITIONS_NOT_SATISFIED;
    }

    private byte[] handleSelectAid(byte[] apdu) {
        if (apdu.length < 5) return SW_WRONG_DATA;
        int aidLen = apdu[4] & 0xFF;
        if (apdu.length < 5 + aidLen) return SW_WRONG_DATA;
        byte[] requestedAid = Arrays.copyOfRange(apdu, 5, 5 + aidLen);

        // Check if matches active AID
        if (activeAid != null && Arrays.equals(requestedAid, activeAid)) {
            if (activeCard != null && !activeCard.apduSelectResponse.isEmpty()) {
                byte[] resp = CardDetector.hexToBytes(activeCard.apduSelectResponse);
                return ApduHelper.appendSw(resp, SW_OK);
            }
            // Minimal FCI response
            return buildFciResponse();
        }

        // Check known AIDs
        String requestedHex = CardDetector.bytesToHex(requestedAid).toUpperCase();
        for (java.util.Map.Entry<String, byte[]> e : ApduHelper.knownAids().entrySet()) {
            if (e.getKey().equalsIgnoreCase(requestedHex)) {
                return buildFciResponse();
            }
        }

        return SW_NOT_FOUND;
    }

    private byte[] buildFciResponse() {
        // Minimal FCI template
        byte[] fci = {
                0x6F, 0x17, // FCI Template
                (byte) 0x84, 0x07, // DF Name
                (byte) 0xA0, 0x00, 0x00, 0x00, 0x03, 0x10, 0x10,
                (byte) 0xA5, 0x0C, // FCI Proprietary
                0x50, 0x04, 'V', 'I', 'S', 'A', // App Label
                (byte) 0x87, 0x01, 0x01, // App Priority
                0x5F, 0x2D, 0x02, 0x65, 0x6E // Language Preference: en
        };
        return ApduHelper.appendSw(fci, SW_OK);
    }

    private byte[] handleGetUid() {
        if (activeCard != null && !activeCard.uid.isEmpty()) {
            byte[] uid = CardDetector.hexToBytes(activeCard.uid);
            return ApduHelper.appendSw(uid, SW_OK);
        }
        return SW_CONDITIONS_NOT_SATISFIED;
    }

    private byte[] handleReadBinary(byte[] apdu) {
        // Return card UID/data as binary if available
        if (activeCard != null && !activeCard.uid.isEmpty()) {
            byte[] data = CardDetector.hexToBytes(activeCard.uid);
            return ApduHelper.appendSw(data, SW_OK);
        }
        return SW_CONDITIONS_NOT_SATISFIED;
    }

    private byte[] handleGetProcessingOptions(byte[] apdu) {
        // Minimal GPO response (Format 1)
        byte[] aip = {0x58, 0x00}; // Application Interchange Profile
        byte[] afl = {0x08, 0x01, 0x01, 0x00}; // Application File Locator
        byte[] gpoResp = new byte[2 + aip.length + 2 + afl.length];
        gpoResp[0] = (byte) 0x80;
        gpoResp[1] = (byte) (aip.length + afl.length);
        System.arraycopy(aip, 0, gpoResp, 2, aip.length);
        System.arraycopy(afl, 0, gpoResp, 2 + aip.length, afl.length);
        return ApduHelper.appendSw(gpoResp, SW_OK);
    }

    private byte[] handleGetData(byte[] apdu) {
        // ATC (Application Transaction Counter)
        if (apdu.length >= 4 && apdu[2] == (byte) 0x9F && apdu[3] == 0x36) {
            byte[] atc = {0x00, 0x01};
            return ApduHelper.appendSw(new byte[]{(byte)0x9F, 0x36, 0x02, atc[0], atc[1]}, SW_OK);
        }
        return SW_NOT_FOUND;
    }

    private byte[] handleGenerateAc(byte[] apdu) {
        // Stub: return AAC (Application Authentication Cryptogram)
        byte[] aac = new byte[8]; // Zero-filled AAC
        byte[] resp = new byte[]{(byte) 0x80, 0x12, 0x00, 0x00, 0x00, 0x01,
                aac[0], aac[1], aac[2], aac[3], aac[4], aac[5], aac[6], aac[7],
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        return ApduHelper.appendSw(resp, SW_OK);
    }

    @Override
    public void onDeactivated(int reason) {
        String reasonStr = reason == DEACTIVATION_LINK_LOSS ? "Link lost" : "Different AID selected";
        logApdu("Deactivated: " + reasonStr);
        Log.d(TAG, "Deactivated: " + reasonStr);
    }

    private void logApdu(String entry) {
        synchronized (apduLog) {
            if (apduLog.size() >= LOG_BUFFER_SIZE) apduLog.removeFirst();
            apduLog.addLast(entry);
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "NFC HCE Service", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("NFC card emulation active");
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, EmulationActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String cardName = activeCard != null ? activeCard.name : "No card";
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setContentTitle("NFC Wallet — Emulating")
                .setContentText(cardName + " | " + text)
                .setContentIntent(pi)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification("HCE Active"));
    }
}
