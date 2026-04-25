package com.nfc.wallet;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class ApduHelper {

    public static byte[] buildSelectAidApdu(byte[] aid) {
        byte[] apdu = new byte[6 + aid.length];
        apdu[0] = 0x00; // CLA
        apdu[1] = (byte) 0xA4; // INS SELECT
        apdu[2] = 0x04; // P1 = select by AID
        apdu[3] = 0x00; // P2
        apdu[4] = (byte) aid.length; // Lc
        System.arraycopy(aid, 0, apdu, 5, aid.length);
        apdu[5 + aid.length] = 0x00; // Le
        return apdu;
    }

    public static byte[] buildGetUidApdu() {
        // APDU to GET DATA - UID
        return new byte[]{(byte) 0xFF, (byte) 0xCA, 0x00, 0x00, 0x00};
    }

    public static byte[] buildReadBinaryApdu(int offset, int length) {
        return new byte[]{
                0x00, // CLA
                (byte) 0xB0, // INS READ BINARY
                (byte) ((offset >> 8) & 0xFF), // P1
                (byte) (offset & 0xFF), // P2
                (byte) length // Le
        };
    }

    public static byte[] buildGetDataApdu() {
        // GET DATA command
        return new byte[]{(byte) 0xFF, (byte) 0xCA, 0x00, 0x00, 0x00};
    }

    public static String apduToHex(byte[] apdu) {
        if (apdu == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < apdu.length; i++) {
            if (i > 0) sb.append(" ");
            sb.append(String.format("%02X", apdu[i]));
        }
        return sb.toString();
    }

    public static byte[] hexToApdu(String hex) {
        if (hex == null || hex.isEmpty()) return new byte[0];
        hex = hex.replaceAll("[\\s:]", "");
        if (hex.length() % 2 != 0) hex = "0" + hex;
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return result;
    }

    public static String parseStatusWord(byte[] response) {
        if (response == null || response.length < 2) return "INVALID";
        int sw1 = response[response.length - 2] & 0xFF;
        int sw2 = response[response.length - 1] & 0xFF;
        String sw = String.format("%02X%02X", sw1, sw2);
        switch (sw) {
            case "9000": return "OK (9000)";
            case "6100": return "Response bytes still available (6100)";
            case "6283": return "Selected file deactivated (6283)";
            case "6300": return "Warning, no info given (6300)";
            case "6400": return "Execution error, no info given (6400)";
            case "6500": return "Execution error, no precise diagnosis (6500)";
            case "6700": return "Wrong length (6700)";
            case "6800": return "Function not supported (6800)";
            case "6900": return "Command not allowed (6900)";
            case "6981": return "Command incompatible with file structure (6981)";
            case "6982": return "Security status not satisfied (6982)";
            case "6983": return "Authentication method blocked (6983)";
            case "6985": return "Conditions of use not satisfied (6985)";
            case "6986": return "Command not allowed (no EF selected) (6986)";
            case "6A80": return "Incorrect data in command (6A80)";
            case "6A81": return "Function not supported (6A81)";
            case "6A82": return "File not found (6A82)";
            case "6A83": return "Record not found (6A83)";
            case "6A86": return "Incorrect P1/P2 (6A86)";
            case "6A88": return "Referenced data not found (6A88)";
            case "6B00": return "Wrong parameter P1/P2 (6B00)";
            case "6C00": return "Wrong Le field (6C00)";
            case "6D00": return "Instruction code not supported (6D00)";
            case "6E00": return "Class not supported (6E00)";
            case "6F00": return "No precise diagnosis (6F00)";
            default:
                if (sw.startsWith("61")) return "Response bytes available: " + sw2 + " (61" + String.format("%02X", sw2) + ")";
                if (sw.startsWith("6C")) return "Wrong Le, use Le=" + sw2;
                if (sw.startsWith("90")) return "Success (" + sw + ")";
                return "Status: " + sw;
        }
    }

    public static boolean isSuccess(byte[] response) {
        if (response == null || response.length < 2) return false;
        int sw1 = response[response.length - 2] & 0xFF;
        int sw2 = response[response.length - 1] & 0xFF;
        return sw1 == 0x90 && sw2 == 0x00;
    }

    public static byte[] appendSw(byte[] data, byte[] sw) {
        if (data == null) data = new byte[0];
        if (sw == null) sw = new byte[]{(byte) 0x90, 0x00};
        byte[] result = new byte[data.length + sw.length];
        System.arraycopy(data, 0, result, 0, data.length);
        System.arraycopy(sw, 0, result, data.length, sw.length);
        return result;
    }

    public static Map<String, byte[]> knownAids() {
        Map<String, byte[]> aids = new LinkedHashMap<>();
        // Payment networks
        aids.put("A0000000031010", hexToApdu("A0000000031010")); // Visa
        aids.put("A0000000032010", hexToApdu("A0000000032010")); // Visa Electron
        aids.put("A0000000033010", hexToApdu("A0000000033010")); // Visa Interlink
        aids.put("A0000000038010", hexToApdu("A0000000038010")); // Visa Plus
        aids.put("A0000000041010", hexToApdu("A0000000041010")); // Mastercard
        aids.put("A0000000043060", hexToApdu("A0000000043060")); // Mastercard Maestro
        aids.put("A0000000044010", hexToApdu("A0000000044010")); // Mastercard Maestro UK
        aids.put("A0000000046000", hexToApdu("A0000000046000")); // Mastercard Cirrus
        aids.put("A000000025010402", hexToApdu("A000000025010402")); // Amex
        aids.put("A0000000651010", hexToApdu("A0000000651010")); // JCB
        aids.put("A0000000333010101", hexToApdu("A0000000333010101")); // Discover
        aids.put("A000000172950001", hexToApdu("A000000172950001")); // UnionPay
        aids.put("A0000001524942", hexToApdu("A0000001524942")); // Interac
        // Access/Transit
        aids.put("D2760000850101", hexToApdu("D2760000850101")); // NFC Forum Type 4
        aids.put("A0000005272110", hexToApdu("A0000005272110")); // Global Platform
        aids.put("F0010203040506", hexToApdu("F0010203040506")); // Test AID
        aids.put("A0000002771010", hexToApdu("A0000002771010")); // PBOC (China)
        aids.put("A0000003241010", hexToApdu("A0000003241010")); // Visa QPboc
        aids.put("A0000001510000", hexToApdu("A0000001510000")); // Maestro
        return aids;
    }
}
