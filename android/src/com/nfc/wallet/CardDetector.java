package com.nfc.wallet;

import android.content.Context;
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
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;

public class CardDetector {
    private static final String TAG = "CardDetector";

    public static String detectType(Tag tag) {
        if (tag == null) return "Unknown";
        List<String> techs = Arrays.asList(tag.getTechList());
        if (techs.contains(MifareClassic.class.getName())) return "MIFARE Classic";
        if (techs.contains(MifareUltralight.class.getName())) {
            MifareUltralight mu = MifareUltralight.get(tag);
            if (mu != null) {
                int type = mu.getType();
                if (type == MifareUltralight.TYPE_ULTRALIGHT_C) return "MIFARE Ultralight C";
                if (type == MifareUltralight.TYPE_ULTRALIGHT) return "MIFARE Ultralight";
            }
            return "MIFARE Ultralight";
        }
        if (techs.contains(IsoDep.class.getName())) {
            if (techs.contains(NfcB.class.getName())) return "ISO14443-4B";
            return "ISO14443-4";
        }
        if (techs.contains(NfcF.class.getName())) return "FeliCa";
        if (techs.contains(NfcV.class.getName())) return "ISO15693";
        if (techs.contains(Ndef.class.getName())) return "NDEF";
        if (techs.contains(NfcA.class.getName())) return "NFC-A";
        if (techs.contains(NfcB.class.getName())) return "NFC-B";
        return "Unknown";
    }

    public static String detectCompany(byte[] uid, String[] techs) {
        if (uid == null || uid.length == 0) return "Unknown";
        // Visa/MC/Amex typically on ISO14443-4 cards
        List<String> techList = techs != null ? Arrays.asList(techs) : new ArrayList<>();
        if (techList.contains(IsoDep.class.getName())) {
            // Payment card heuristics from UID prefix
            if (uid.length >= 1) {
                int firstByte = uid[0] & 0xFF;
                // Common NXP prefix for payment cards
                if (firstByte == 0x04) return "NXP/Visa";
            }
            return "Payment/Access";
        }
        if (uid.length >= 1) {
            int b = uid[0] & 0xFF;
            // MIFARE Classic from NXP
            if (techList.contains(MifareClassic.class.getName())) {
                if (b == 0x04) return "NXP";
                return "MIFARE";
            }
            // Transit/Building for FeliCa
            if (techList.contains(NfcF.class.getName())) return "Transit/FeliCa";
        }
        return "Unknown";
    }

    public static String getDefaultAID(String cardType) {
        if (cardType == null) return "";
        switch (cardType) {
            case "ISO14443-4": return "A0000000031010"; // Visa default
            case "ISO14443-4B": return "A0000000041010"; // MC
            case "FeliCa": return "88FE4F";
            default: return "";
        }
    }

    public static CardModel buildCardFromTag(Tag tag, Context ctx) {
        if (tag == null) return null;
        CardModel card = new CardModel();

        byte[] uidBytes = tag.getId();
        card.uid = bytesToHex(uidBytes);

        String[] techList = tag.getTechList();
        card.techs = techList;
        card.cardType = detectType(tag);
        card.company = detectCompany(uidBytes, techList);
        card.aid = getDefaultAID(card.cardType);
        card.name = card.cardType + " " + card.uid.substring(0, Math.min(8, card.uid.length()));

        List<String> techs = Arrays.asList(techList);

        try {
            if (techs.contains(MifareClassic.class.getName())) {
                readMifareClassic(tag, card);
            }
            if (techs.contains(MifareUltralight.class.getName())) {
                readMifareUltralight(tag, card);
            }
            if (techs.contains(IsoDep.class.getName())) {
                readIsoDep(tag, card);
            }
            if (techs.contains(Ndef.class.getName())) {
                readNdef(tag, card);
            }
            if (techs.contains(NfcA.class.getName())) {
                readNfcA(tag, card);
            }
            if (techs.contains(NfcB.class.getName())) {
                readNfcB(tag, card);
            }
            if (techs.contains(NfcF.class.getName())) {
                readNfcF(tag, card);
            }
            if (techs.contains(NfcV.class.getName())) {
                readNfcV(tag, card);
            }
        } catch (Exception e) {
            Log.e(TAG, "buildCardFromTag error: " + e.getMessage());
        }

        return card;
    }

    public static void readMifareClassic(Tag tag, CardModel card) {
        MifareClassic mc = MifareClassic.get(tag);
        if (mc == null) return;
        try {
            mc.connect();
            int sectorCount = mc.getSectorCount();
            card.extraFields.put("mc_sector_count", String.valueOf(sectorCount));
            card.extraFields.put("mc_block_count", String.valueOf(mc.getBlockCount()));
            card.extraFields.put("mc_size", String.valueOf(mc.getSize()));
            int type = mc.getType();
            String typeName = type == MifareClassic.TYPE_CLASSIC ? "Classic"
                    : type == MifareClassic.TYPE_PLUS ? "Plus"
                    : type == MifareClassic.TYPE_PRO ? "Pro" : "Unknown";
            card.extraFields.put("mc_type", typeName);

            // Try default keys
            byte[][] defaultKeys = {
                    MifareClassic.KEY_DEFAULT,
                    MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY,
                    MifareClassic.KEY_NFC_FORUM
            };

            for (int sector = 0; sector < Math.min(sectorCount, 16); sector++) {
                boolean authed = false;
                for (byte[] key : defaultKeys) {
                    try {
                        if (mc.authenticateSectorWithKeyA(sector, key)) {
                            authed = true;
                            break;
                        }
                    } catch (Exception ignore) {}
                    try {
                        if (mc.authenticateSectorWithKeyB(sector, key)) {
                            authed = true;
                            break;
                        }
                    } catch (Exception ignore) {}
                }
                if (authed) {
                    int firstBlock = mc.sectorToBlock(sector);
                    int blockCount = mc.getBlockCountInSector(sector);
                    StringBuilder sectorData = new StringBuilder();
                    for (int block = firstBlock; block < firstBlock + blockCount; block++) {
                        try {
                            byte[] data = mc.readBlock(block);
                            sectorData.append(bytesToHex(data)).append(" ");
                        } catch (Exception e) {
                            sectorData.append("ERR ");
                        }
                    }
                    card.extraFields.put("sector" + sector, sectorData.toString().trim());
                } else {
                    card.extraFields.put("sector" + sector, "AUTH_FAIL");
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "readMifareClassic: " + e.getMessage());
        } finally {
            try { mc.close(); } catch (Exception ignore) {}
        }
    }

    public static void readMifareUltralight(Tag tag, CardModel card) {
        MifareUltralight mu = MifareUltralight.get(tag);
        if (mu == null) return;
        try {
            mu.connect();
            card.extraFields.put("mu_type", String.valueOf(mu.getType()));
            StringBuilder pages = new StringBuilder();
            for (int page = 0; page < 16; page++) {
                try {
                    byte[] data = mu.readPages(page);
                    pages.append("P").append(page).append(":").append(bytesToHex(data)).append(" ");
                } catch (Exception e) {
                    break;
                }
            }
            card.extraFields.put("mu_pages", pages.toString().trim());
        } catch (IOException e) {
            Log.e(TAG, "readMifareUltralight: " + e.getMessage());
        } finally {
            try { mu.close(); } catch (Exception ignore) {}
        }
    }

    public static void readIsoDep(Tag tag, CardModel card) {
        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null) return;
        try {
            isoDep.connect();
            isoDep.setTimeout(3000);

            // Try well-known AIDs
            for (Map.Entry<String, byte[]> entry : ApduHelper.knownAids().entrySet()) {
                try {
                    byte[] selectCmd = ApduHelper.buildSelectAidApdu(entry.getValue());
                    byte[] response = isoDep.transceive(selectCmd);
                    if (response != null && ApduHelper.isSuccess(response)) {
                        card.aid = entry.getKey();
                        card.apduSelectResponse = bytesToHex(response);
                        card.company = inferCompanyFromAid(entry.getKey());
                        break;
                    }
                } catch (Exception ignore) {}
            }

            // Get ATR/ATS
            byte[] hiLayerResponse = isoDep.getHiLayerResponse();
            if (hiLayerResponse != null) {
                card.atr = bytesToHex(hiLayerResponse);
            }
            byte[] historicalBytes = isoDep.getHistoricalBytes();
            if (historicalBytes != null) {
                card.extraFields.put("historical_bytes", bytesToHex(historicalBytes));
            }

        } catch (IOException e) {
            Log.e(TAG, "readIsoDep: " + e.getMessage());
        } finally {
            try { isoDep.close(); } catch (Exception ignore) {}
        }
    }

    public static void readNdef(Tag tag, CardModel card) {
        Ndef ndef = Ndef.get(tag);
        if (ndef == null) return;
        try {
            ndef.connect();
            NdefMessage msg = ndef.getNdefMessage();
            if (msg != null) {
                NdefRecord[] records = msg.getRecords();
                StringBuilder payload = new StringBuilder();
                for (NdefRecord rec : records) {
                    payload.append(bytesToHex(rec.getPayload())).append("|");
                }
                card.extraFields.put("ndef_payload", payload.toString());
                card.extraFields.put("ndef_type", ndef.getType());
                card.extraFields.put("ndef_max_size", String.valueOf(ndef.getMaxSize()));
            }
        } catch (Exception e) {
            Log.e(TAG, "readNdef: " + e.getMessage());
        } finally {
            try { ndef.close(); } catch (Exception ignore) {}
        }
    }

    public static void readNfcA(Tag tag, CardModel card) {
        NfcA nfcA = NfcA.get(tag);
        if (nfcA == null) return;
        try {
            nfcA.connect();
            card.extraFields.put("atqa", bytesToHex(nfcA.getAtqa()));
            card.extraFields.put("sak", String.valueOf(nfcA.getSak()));
        } catch (Exception e) {
            Log.e(TAG, "readNfcA: " + e.getMessage());
        } finally {
            try { nfcA.close(); } catch (Exception ignore) {}
        }
    }

    public static void readNfcB(Tag tag, CardModel card) {
        NfcB nfcB = NfcB.get(tag);
        if (nfcB == null) return;
        try {
            nfcB.connect();
            byte[] appData = nfcB.getApplicationData();
            byte[] proto = nfcB.getProtocolInfo();
            if (appData != null) card.extraFields.put("nfcb_app_data", bytesToHex(appData));
            if (proto != null) card.extraFields.put("nfcb_proto_info", bytesToHex(proto));
        } catch (Exception e) {
            Log.e(TAG, "readNfcB: " + e.getMessage());
        } finally {
            try { nfcB.close(); } catch (Exception ignore) {}
        }
    }

    public static void readNfcF(Tag tag, CardModel card) {
        NfcF nfcF = NfcF.get(tag);
        if (nfcF == null) return;
        try {
            nfcF.connect();
            byte[] manufacturer = nfcF.getManufacturer();
            byte[] systemCode = nfcF.getSystemCode();
            if (manufacturer != null) card.extraFields.put("nfcf_manufacturer", bytesToHex(manufacturer));
            if (systemCode != null) card.extraFields.put("nfcf_system_code", bytesToHex(systemCode));
        } catch (Exception e) {
            Log.e(TAG, "readNfcF: " + e.getMessage());
        } finally {
            try { nfcF.close(); } catch (Exception ignore) {}
        }
    }

    public static void readNfcV(Tag tag, CardModel card) {
        NfcV nfcV = NfcV.get(tag);
        if (nfcV == null) return;
        try {
            nfcV.connect();
            card.extraFields.put("nfcv_dsfid", String.valueOf(nfcV.getDsfId()));
            card.extraFields.put("nfcv_response_flags", String.valueOf(nfcV.getResponseFlags()));
        } catch (Exception e) {
            Log.e(TAG, "readNfcV: " + e.getMessage());
        } finally {
            try { nfcV.close(); } catch (Exception ignore) {}
        }
    }

    private static String inferCompanyFromAid(String aidHex) {
        if (aidHex == null) return "Unknown";
        String upper = aidHex.toUpperCase();
        if (upper.contains("000000031010")) return "Visa";
        if (upper.contains("000000041010")) return "Mastercard";
        if (upper.contains("00000025010402")) return "Amex";
        if (upper.contains("0000027710")) return "Maestro";
        if (upper.contains("000000651010")) return "JCB";
        if (upper.contains("00000333010101")) return "Discover";
        if (upper.contains("000001524942")) return "Interac";
        return "Unknown";
    }

    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    public static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) return new byte[0];
        hex = hex.replaceAll("\\s", "");
        if (hex.length() % 2 != 0) hex = "0" + hex;
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return result;
    }

    // Import Map for readIsoDep method
    private static java.util.Map<String, byte[]> aidMap = null;
}
