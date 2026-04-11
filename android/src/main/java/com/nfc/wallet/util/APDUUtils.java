package com.nfc.wallet.util;

/**
 * APDU (Application Protocol Data Unit) utility class.
 * Provides constants, builders, and hex/byte conversion helpers for ISO 7816-4 APDU commands.
 */
public class APDUUtils {

    // ISO 7816 Status Words
    public static final byte[] SW_SUCCESS             = {(byte) 0x90, 0x00};
    public static final byte[] SW_FILE_NOT_FOUND      = {0x6A, (byte) 0x82};
    public static final byte[] SW_WRONG_LENGTH        = {0x67, 0x00};
    public static final byte[] SW_COND_NOT_SATISFIED  = {0x69, (byte) 0x85};
    public static final byte[] SW_WRONG_DATA          = {0x6A, (byte) 0x80};
    public static final byte[] SW_INS_NOT_SUPPORTED   = {0x6D, 0x00};
    public static final byte[] SW_CLA_NOT_SUPPORTED   = {0x6E, 0x00};
    public static final byte[] SW_UNKNOWN             = {0x6F, 0x00};
    public static final byte[] SW_SECURITY_STATUS     = {0x69, (byte) 0x82};
    public static final byte[] SW_RECORD_NOT_FOUND    = {0x6A, (byte) 0x83};
    public static final byte[] SW_INCORRECT_P1P2      = {0x6A, (byte) 0x86};
    public static final byte[] SW_NOT_FOUND           = {0x6A, (byte) 0x82};

    // APDU command bytes
    public static final byte CLA_ISO7816   = 0x00;
    public static final byte CLA_PROPRIETARY = (byte) 0x80;
    public static final byte CLA_CONTACTLESS = (byte) 0xFF;

    public static final byte INS_SELECT    = (byte) 0xA4;
    public static final byte INS_READ_BINARY = (byte) 0xB0;
    public static final byte INS_WRITE_BINARY = (byte) 0xD0;
    public static final byte INS_GET_DATA  = (byte) 0xCA;
    public static final byte INS_PUT_DATA  = (byte) 0xDA;
    public static final byte INS_GET_RESPONSE = (byte) 0xC0;
    public static final byte INS_VERIFY    = 0x20;
    public static final byte INS_EXTERNAL_AUTH = (byte) 0x82;
    public static final byte INS_GET_PROCESSING_OPTIONS = (byte) 0xA8;
    public static final byte INS_READ_RECORD = (byte) 0xB2;
    public static final byte INS_GENERATE_AC = (byte) 0xAE;
    public static final byte INS_GET_CHALLENGE = (byte) 0x84;

    // Contactless-specific
    public static final byte INS_LOAD_KEY = (byte) 0x82;
    public static final byte INS_GENERAL_AUTH = (byte) 0x86;
    public static final byte INS_READ_RECORD_MIFARE = 0x30;

    /**
     * Builds a SELECT AID APDU command.
     * Format: 00 A4 04 00 [AID length] [AID bytes] 00
     */
    public static byte[] buildSelectApdu(String aidHex) {
        byte[] aid = hexToBytes(aidHex);
        byte[] apdu = new byte[6 + aid.length];
        apdu[0] = CLA_ISO7816;
        apdu[1] = INS_SELECT;
        apdu[2] = 0x04;  // P1: select by name
        apdu[3] = 0x00;  // P2: first or only occurrence
        apdu[4] = (byte) aid.length; // Lc
        System.arraycopy(aid, 0, apdu, 5, aid.length);
        apdu[apdu.length - 1] = 0x00; // Le
        return apdu;
    }

    /**
     * Builds a GET UID command (FF CA 00 00 00).
     */
    public static byte[] buildGetUidApdu() {
        return new byte[]{(byte) 0xFF, (byte) 0xCA, 0x00, 0x00, 0x00};
    }

    /**
     * Builds a READ BINARY APDU.
     * Format: 00 B0 [P1: offset high] [P2: offset low] [Le: max bytes]
     */
    public static byte[] buildReadBinaryApdu(int offset, int length) {
        return new byte[]{
                CLA_ISO7816,
                INS_READ_BINARY,
                (byte) ((offset >> 8) & 0xFF),
                (byte) (offset & 0xFF),
                (byte) (length & 0xFF)
        };
    }

    /**
     * Builds a GET PROCESSING OPTIONS command (used in EMV/payment cards).
     */
    public static byte[] buildGetProcessingOptions() {
        return new byte[]{(byte) 0x80, (byte) 0xA8, 0x00, 0x00, 0x02, (byte) 0x83, 0x00, 0x00};
    }

    /**
     * Builds a READ RECORD APDU.
     * Format: 00 B2 [P1: record number] [P2: SFI*8+4] [Le]
     */
    public static byte[] buildReadRecordApdu(int recordNumber, int sfi) {
        return new byte[]{
                CLA_ISO7816,
                INS_READ_RECORD,
                (byte) recordNumber,
                (byte) ((sfi << 3) | 4),
                0x00
        };
    }

    /**
     * Builds a VERIFY PIN APDU.
     */
    public static byte[] buildVerifyPinApdu(byte[] pinData) {
        byte[] apdu = new byte[5 + pinData.length];
        apdu[0] = CLA_ISO7816;
        apdu[1] = INS_VERIFY;
        apdu[2] = 0x00;
        apdu[3] = (byte) 0x80; // P2: global PIN
        apdu[4] = (byte) pinData.length;
        System.arraycopy(pinData, 0, apdu, 5, pinData.length);
        return apdu;
    }

    /**
     * Checks if an APDU response ends with SW 9000 (success).
     */
    public static boolean isSuccess(byte[] response) {
        if (response == null || response.length < 2) return false;
        return response[response.length - 2] == (byte) 0x90
                && response[response.length - 1] == (byte) 0x00;
    }

    /**
     * Extracts the data payload from an APDU response (strips trailing status word).
     */
    public static byte[] getResponseData(byte[] response) {
        if (response == null || response.length < 2) return new byte[0];
        if (response.length == 2) return new byte[0];
        byte[] data = new byte[response.length - 2];
        System.arraycopy(response, 0, data, 0, data.length);
        return data;
    }

    /**
     * Returns the status word (last 2 bytes) from a response.
     */
    public static String getStatusWord(byte[] response) {
        if (response == null || response.length < 2) return "????";
        return String.format("%02X%02X",
                response[response.length - 2],
                response[response.length - 1]);
    }

    /**
     * Converts a hex string to byte array.
     */
    public static byte[] hexToBytes(String hex) {
        if (hex == null) return new byte[0];
        hex = hex.replaceAll("\\s", "").toUpperCase();
        if (hex.isEmpty()) return new byte[0];
        if (hex.length() % 2 != 0) hex = "0" + hex;
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    /**
     * Converts a byte array to uppercase hex string.
     */
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /**
     * Converts a byte array to a spaced hex string (e.g., "AA BB CC").
     */
    public static String bytesToHexSpaced(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            sb.append(String.format("%02X", bytes[i]));
            if (i < bytes.length - 1) sb.append(" ");
        }
        return sb.toString();
    }

    /**
     * Reverses a UID byte array (for little-endian UID representation).
     */
    public static byte[] reverseBytes(byte[] input) {
        if (input == null) return new byte[0];
        byte[] reversed = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            reversed[i] = input[input.length - 1 - i];
        }
        return reversed;
    }

    /**
     * Formats APDU bytes as a human-readable command string.
     */
    public static String formatApduCommand(byte[] apdu) {
        if (apdu == null || apdu.length == 0) return "(empty)";
        StringBuilder sb = new StringBuilder();
        sb.append("CLA=").append(String.format("%02X", apdu[0]));
        if (apdu.length > 1) sb.append(" INS=").append(String.format("%02X", apdu[1]));
        if (apdu.length > 2) sb.append(" P1=").append(String.format("%02X", apdu[2]));
        if (apdu.length > 3) sb.append(" P2=").append(String.format("%02X", apdu[3]));
        if (apdu.length > 4) {
            sb.append(" Lc=").append(String.format("%02X", apdu[4]));
            if (apdu.length > 5) {
                sb.append(" Data=").append(bytesToHex(java.util.Arrays.copyOfRange(apdu, 5, apdu.length)));
            }
        }
        return sb.toString();
    }

    /**
     * Parses an AID from a SELECT APDU command bytes.
     */
    public static String parseAidFromSelectApdu(byte[] apdu) {
        if (apdu == null || apdu.length < 6) return "";
        if (apdu[1] != INS_SELECT) return "";
        int aidLength = apdu[4] & 0xFF;
        if (apdu.length < 5 + aidLength) return "";
        return bytesToHex(java.util.Arrays.copyOfRange(apdu, 5, 5 + aidLength));
    }

    /**
     * Builds a TLV (Tag-Length-Value) response containing UID data.
     */
    public static byte[] buildUidTlvResponse(byte[] uid) {
        byte[] response = new byte[2 + uid.length + 2];
        response[0] = (byte) 0x80; // proprietary tag
        response[1] = (byte) uid.length;
        System.arraycopy(uid, 0, response, 2, uid.length);
        response[response.length - 2] = (byte) 0x90;
        response[response.length - 1] = 0x00;
        return response;
    }

    /**
     * Appends SW 9000 to a data array to form a complete APDU response.
     */
    public static byte[] appendSuccess(byte[] data) {
        if (data == null) return SW_SUCCESS;
        byte[] response = new byte[data.length + 2];
        System.arraycopy(data, 0, response, 0, data.length);
        response[data.length] = (byte) 0x90;
        response[data.length + 1] = 0x00;
        return response;
    }

    /**
     * Returns a human-readable description of a status word.
     */
    public static String describeStatusWord(String sw) {
        if (sw == null) return "Unknown";
        switch (sw.toUpperCase()) {
            case "9000": return "Success";
            case "6700": return "Wrong length";
            case "6982": return "Security status not satisfied";
            case "6985": return "Conditions of use not satisfied";
            case "6A80": return "Wrong data";
            case "6A82": return "File or application not found";
            case "6A83": return "Record not found";
            case "6A86": return "Incorrect P1/P2";
            case "6D00": return "INS not supported";
            case "6E00": return "CLA not supported";
            case "6F00": return "Unknown error";
            case "6300": return "Warning: no information";
            case "6283": return "Selected file invalidated";
            case "6100": return "Response bytes available";
            default:
                if (sw.startsWith("61")) return "Response bytes available: " + sw.substring(2);
                if (sw.startsWith("6C")) return "Wrong length, correct Lc=" + sw.substring(2);
                if (sw.startsWith("63")) return "Warning: counter=" + sw.substring(2);
                return "Status: " + sw;
        }
    }
}
