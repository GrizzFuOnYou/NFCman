package com.nfc.wallet.util;

import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcF;
import android.nfc.tech.NfcV;
import android.util.Log;

import com.nfc.wallet.model.CardModel;

import java.util.Arrays;
import java.util.List;

/**
 * Auto-detects NFC card type from a scanned Tag and populates
 * the appropriate CardModel fields (AID, APDU, UID, type hints).
 */
public class CardTypeDetector {

    private static final String TAG = "CardTypeDetector";

    // Known payment AIDs
    public static final String AID_VISA_CREDIT_DEBIT = "A0000000031010";
    public static final String AID_VISA_ELECTRON = "A0000000032010";
    public static final String AID_VISA_V_PAY = "A0000000038010";
    public static final String AID_MASTERCARD = "A0000000041010";
    public static final String AID_MASTERCARD_MAESTRO = "A0000000043060";
    public static final String AID_AMEX = "A000000025010801";
    public static final String AID_DISCOVER = "A0000001523010";
    public static final String AID_JCB = "A0000000651010";
    public static final String AID_UNIONPAY = "A000000333010101";

    // Transit AIDs
    public static final String AID_SUICA = "0003F2003FC301";
    public static final String AID_PASMO = "0003F2003FC301";
    public static final String AID_OCTOPUS = "8352401";
    public static final String AID_NAVIGO = "A00000001772950001";

    // Access AIDs
    public static final String AID_HID = "A000000172950001";

    // NDEF / NFC Forum AIDs
    public static final String AID_NDEF = "D2760000850101";
    public static final String AID_NDEF_TYPE_2 = "D276000085010100";

    // Standard custom AID used in emulation
    public static final String AID_CUSTOM = "F001020304050607";

    /**
     * Detects card type and populates fields on a CardModel from a scanned Tag.
     * Fills card type, company, primary AID, and technical data hints.
     */
    public static void detectAndPopulate(Tag tag, CardModel card) {
        if (tag == null || card == null) return;

        String[] techList = tag.getTechList();
        card.setTechnologies(String.join(", ", techList));

        // Determine primary card type from tech list
        boolean hasIsoDep = hasTech(techList, IsoDep.class);
        boolean hasMifareClassic = hasTech(techList, MifareClassic.class);
        boolean hasMifareUltralight = hasTech(techList, MifareUltralight.class);
        boolean hasNdef = hasTech(techList, Ndef.class);
        boolean hasNfcA = hasTech(techList, NfcA.class);
        boolean hasNfcB = hasTech(techList, NfcB.class);
        boolean hasNfcF = hasTech(techList, NfcF.class);
        boolean hasNfcV = hasTech(techList, NfcV.class);

        // NfcA SAK-based detection for MIFARE
        if (hasNfcA) {
            NfcA nfcA = NfcA.get(tag);
            if (nfcA != null) {
                byte sak = (byte) nfcA.getSak();
                byte[] atqa = nfcA.getAtqa();
                card.setSak(String.format("%02X", sak));
                card.setAtqa(APDUUtils.bytesToHex(atqa));
                card.setNfcAData("ATQA=" + APDUUtils.bytesToHex(atqa)
                        + " SAK=" + String.format("%02X", sak)
                        + " MaxTransceive=" + nfcA.getMaxTransceiveLength());

                if (hasMifareClassic) {
                    MifareClassic mc = MifareClassic.get(tag);
                    if (mc != null) {
                        switch (mc.getType()) {
                            case MifareClassic.TYPE_CLASSIC:
                                card.setCardType(CardModel.CardType.MIFARE_CLASSIC);
                                break;
                            case MifareClassic.TYPE_PLUS:
                                card.setCardType(CardModel.CardType.MIFARE_CLASSIC);
                                card.setExtraField("MifareSubtype", "PLUS");
                                break;
                            case MifareClassic.TYPE_PRO:
                                card.setCardType(CardModel.CardType.MIFARE_CLASSIC);
                                card.setExtraField("MifareSubtype", "PRO");
                                break;
                            default:
                                card.setCardType(CardModel.CardType.MIFARE_CLASSIC);
                        }
                        card.setExtraField("MifareSectors", String.valueOf(mc.getSectorCount()));
                        card.setExtraField("MifareBlocks", String.valueOf(mc.getBlockCount()));
                        card.setExtraField("MifareSize", String.valueOf(mc.getSize()));
                    }
                } else if (hasMifareUltralight) {
                    MifareUltralight mu = MifareUltralight.get(tag);
                    if (mu != null) {
                        switch (mu.getType()) {
                            case MifareUltralight.TYPE_ULTRALIGHT:
                                card.setCardType(CardModel.CardType.MIFARE_ULTRALIGHT);
                                break;
                            case MifareUltralight.TYPE_ULTRALIGHT_C:
                                card.setCardType(CardModel.CardType.MIFARE_ULTRALIGHT);
                                card.setExtraField("UltralightSubtype", "C");
                                break;
                            default:
                                card.setCardType(CardModel.CardType.NTAG);
                        }
                    }
                }
            }
        }

        if (hasNfcB) {
            NfcB nfcB = NfcB.get(tag);
            if (nfcB != null) {
                card.setNfcBData("AppData=" + APDUUtils.bytesToHex(nfcB.getApplicationData())
                        + " ProtInfo=" + APDUUtils.bytesToHex(nfcB.getProtocolInfo()));
            }
        }

        if (hasNfcF) {
            NfcF nfcF = NfcF.get(tag);
            if (nfcF != null) {
                card.setNfcFData("Manufacturer=" + APDUUtils.bytesToHex(nfcF.getManufacturer())
                        + " SystemCode=" + APDUUtils.bytesToHex(nfcF.getSystemCode()));
                if (card.getCardType() == CardModel.CardType.UNKNOWN) {
                    card.setCardType(CardModel.CardType.FELICA);
                }
            }
        }

        if (hasNfcV) {
            NfcV nfcV = NfcV.get(tag);
            if (nfcV != null) {
                card.setNfcVData("DsfId=" + String.format("%02X", nfcV.getDsfId())
                        + " ResponseFlags=" + String.format("%02X", nfcV.getResponseFlags()));
                if (card.getCardType() == CardModel.CardType.UNKNOWN) {
                    card.setCardType(CardModel.CardType.ISO15693);
                }
            }
        }

        // IsoDep: try to detect payment/access card from AID
        if (hasIsoDep) {
            if (card.getCardType() == CardModel.CardType.UNKNOWN
                    || card.getCardType() == CardModel.CardType.MIFARE_CLASSIC) {
                card.setCardType(CardModel.CardType.ISO14443_4);
            }
            IsoDep iso = IsoDep.get(tag);
            if (iso != null) {
                if (iso.getHistoricalBytes() != null) {
                    card.setHistoricalBytes(APDUUtils.bytesToHex(iso.getHistoricalBytes()));
                }
                if (iso.getHiLayerResponse() != null) {
                    card.setAts(APDUUtils.bytesToHex(iso.getHiLayerResponse()));
                }
            }

            // Default payment AIDs for probing
            card.setPrimaryAid(AID_CUSTOM);
            card.setAdditionalAids(
                    AID_VISA_CREDIT_DEBIT + "\n" +
                    AID_MASTERCARD + "\n" +
                    AID_AMEX + "\n" +
                    AID_HID + "\n" +
                    AID_NDEF
            );
        }

        // NDEF data hint
        if (hasNdef) {
            if (card.getCardType() == CardModel.CardType.UNKNOWN) {
                card.setCardType(CardModel.CardType.NDEF);
            }
        }

        // Suggest card company from SAK/ATQA
        inferCompany(card);

        Log.d(TAG, "Detected card type: " + card.getCardType() + " for UID: " + card.getUid());
    }

    /**
     * Infers the card company based on known AID prefixes and SAK values.
     */
    public static void inferCompany(CardModel card) {
        String primaryAid = card.getPrimaryAid().toUpperCase();
        String additionalAids = card.getAdditionalAids().toUpperCase();

        if (primaryAid.startsWith("A0000000031") || additionalAids.contains("A0000000031")) {
            card.setCompany(CardModel.Company.VISA);
            card.setCardType(CardModel.CardType.CREDIT_CARD);
        } else if (primaryAid.startsWith("A0000000041") || additionalAids.contains("A0000000041")) {
            card.setCompany(CardModel.Company.MASTERCARD);
            card.setCardType(CardModel.CardType.CREDIT_CARD);
        } else if (primaryAid.startsWith("A000000025") || additionalAids.contains("A000000025")) {
            card.setCompany(CardModel.Company.AMEX);
            card.setCardType(CardModel.CardType.CREDIT_CARD);
        } else if (primaryAid.startsWith("A0000001523") || additionalAids.contains("A0000001523")) {
            card.setCompany(CardModel.Company.DISCOVER);
            card.setCardType(CardModel.CardType.CREDIT_CARD);
        } else if (primaryAid.startsWith("A0000001720001") || additionalAids.contains("A0000001720001")) {
            card.setCompany(CardModel.Company.HID);
            card.setCardType(CardModel.CardType.ACCESS_CARD);
        }

        // FeliCa = transit
        if (card.getCardType() == CardModel.CardType.FELICA) {
            card.setCompany(CardModel.Company.TRANSIT_SUICA);
        }
    }

    /**
     * Infers card type from manually entered data (AID, card number, etc.)
     */
    public static void detectFromManualData(CardModel card) {
        String cardNum = card.getCardNumber().replaceAll("\\s", "");
        if (!cardNum.isEmpty()) {
            String firstDigit = cardNum.substring(0, 1);
            String firstTwo = cardNum.length() >= 2 ? cardNum.substring(0, 2) : "";
            String firstFour = cardNum.length() >= 4 ? cardNum.substring(0, 4) : "";

            // Visa: starts with 4
            if (firstDigit.equals("4")) {
                card.setCompany(CardModel.Company.VISA);
                if (card.getCardType() == CardModel.CardType.UNKNOWN || card.getCardType() == CardModel.CardType.CUSTOM) {
                    card.setCardType(CardModel.CardType.CREDIT_CARD);
                }
                if (card.getPrimaryAid().isEmpty()) {
                    card.setPrimaryAid(AID_VISA_CREDIT_DEBIT);
                }
            }
            // Mastercard: 51-55 or 2221-2720
            else if (firstTwo.compareTo("51") >= 0 && firstTwo.compareTo("55") <= 0) {
                card.setCompany(CardModel.Company.MASTERCARD);
                if (card.getCardType() == CardModel.CardType.UNKNOWN || card.getCardType() == CardModel.CardType.CUSTOM) {
                    card.setCardType(CardModel.CardType.CREDIT_CARD);
                }
                if (card.getPrimaryAid().isEmpty()) {
                    card.setPrimaryAid(AID_MASTERCARD);
                }
            }
            // Amex: 34 or 37
            else if (firstTwo.equals("34") || firstTwo.equals("37")) {
                card.setCompany(CardModel.Company.AMEX);
                if (card.getCardType() == CardModel.CardType.UNKNOWN || card.getCardType() == CardModel.CardType.CUSTOM) {
                    card.setCardType(CardModel.CardType.CREDIT_CARD);
                }
                if (card.getPrimaryAid().isEmpty()) {
                    card.setPrimaryAid(AID_AMEX);
                }
            }
            // Discover: 6011, 622126-622925, 644-649, 65
            else if (firstFour.equals("6011") || firstTwo.equals("65")) {
                card.setCompany(CardModel.Company.DISCOVER);
                if (card.getCardType() == CardModel.CardType.UNKNOWN || card.getCardType() == CardModel.CardType.CUSTOM) {
                    card.setCardType(CardModel.CardType.CREDIT_CARD);
                }
                if (card.getPrimaryAid().isEmpty()) {
                    card.setPrimaryAid(AID_DISCOVER);
                }
            }
        }

        // Also infer from primary AID
        inferCompany(card);
    }

    /**
     * Returns the default APDU SELECT command for an AID.
     */
    public static byte[] buildSelectApdu(String aid) {
        byte[] aidBytes = APDUUtils.hexToBytes(aid);
        byte[] apdu = new byte[6 + aidBytes.length];
        apdu[0] = 0x00; // CLA
        apdu[1] = (byte) 0xA4; // INS: SELECT
        apdu[2] = 0x04; // P1: by name
        apdu[3] = 0x00; // P2
        apdu[4] = (byte) aidBytes.length; // Lc
        System.arraycopy(aidBytes, 0, apdu, 5, aidBytes.length);
        apdu[apdu.length - 1] = 0x00; // Le
        return apdu;
    }

    /**
     * Returns the GET UID APDU command.
     */
    public static byte[] buildGetUidApdu() {
        return new byte[]{(byte) 0xFF, (byte) 0xCA, 0x00, 0x00, 0x00};
    }

    // Helper to check if a tech is present
    private static boolean hasTech(String[] techList, Class<?> techClass) {
        for (String tech : techList) {
            if (tech.equals(techClass.getName())) return true;
        }
        return false;
    }

    /**
     * Returns a list of known payment AIDs to probe during IsoDep reading.
     */
    public static List<String> getPaymentAids() {
        return Arrays.asList(
                AID_VISA_CREDIT_DEBIT, AID_VISA_ELECTRON, AID_VISA_V_PAY,
                AID_MASTERCARD, AID_MASTERCARD_MAESTRO,
                AID_AMEX, AID_DISCOVER, AID_JCB, AID_UNIONPAY,
                AID_HID, AID_NDEF, AID_CUSTOM
        );
    }

    /**
     * Returns a color hex string appropriate for the detected card type/company.
     */
    public static String getSuggestedCardColor(CardModel card) {
        switch (card.getCompany()) {
            case VISA: return "#1565C0";
            case MASTERCARD: return "#B71C1C";
            case AMEX: return "#00695C";
            case DISCOVER: return "#E65100";
            case JCB: return "#1A237E";
            case UNIONPAY: return "#880E4F";
            case HID: return "#4A148C";
            default:
                switch (card.getCardType()) {
                    case MIFARE_CLASSIC: return "#1B5E20";
                    case MIFARE_ULTRALIGHT: return "#0D47A1";
                    case NTAG: return "#006064";
                    case FELICA: return "#BF360C";
                    case ACCESS_CARD: return "#37474F";
                    case TRANSIT_CARD: return "#00695C";
                    default: return "#263238";
                }
        }
    }
}
