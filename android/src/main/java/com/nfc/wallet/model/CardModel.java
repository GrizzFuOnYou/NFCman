package com.nfc.wallet.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a stored NFC card (scanned or manually entered).
 * All data stored as plain text fields for easy debugging/inspection.
 */
public class CardModel {

    public enum CardType {
        CREDIT_CARD("Credit Card"),
        DEBIT_CARD("Debit Card"),
        TRANSIT_CARD("Transit Card"),
        ACCESS_CARD("Access / Key Card"),
        LOYALTY_CARD("Loyalty Card"),
        ID_CARD("ID Card"),
        MIFARE_CLASSIC("MIFARE Classic"),
        MIFARE_ULTRALIGHT("MIFARE Ultralight"),
        NTAG("NTAG 213/215/216"),
        FELICA("FeliCa"),
        ISO14443_4("ISO 14443-4 (IsoDep)"),
        ISO15693("ISO 15693 (NfcV)"),
        NDEF("NDEF Tag"),
        CUSTOM("Custom"),
        UNKNOWN("Unknown");

        private final String displayName;

        CardType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum Company {
        VISA("Visa"),
        MASTERCARD("Mastercard"),
        AMEX("American Express"),
        DISCOVER("Discover"),
        JCB("JCB"),
        UNIONPAY("UnionPay"),
        PAYPASS("PayPass"),
        PAYWAVE("payWave"),
        TRANSIT_OCTOPUS("Octopus"),
        TRANSIT_OYSTER("Oyster"),
        TRANSIT_SUICA("Suica"),
        TRANSIT_PASMO("Pasmo"),
        TRANSIT_NAVIGO("Navigo"),
        HID("HID Global"),
        LEGIC("LEGIC"),
        OTHER("Other"),
        NONE("None");

        private final String displayName;

        Company(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // Core identification
    private String id;
    private String label;
    private CardType cardType;
    private Company company;

    // NFC technical data
    private String uid;
    private String uidReversed;
    private String atqa;
    private String sak;
    private String ats;
    private String technologies;
    private String historicalBytes;

    // Protocol-specific data
    private String ndefData;
    private String mifareData;
    private String isoDepData;
    private String nfcAData;
    private String nfcBData;
    private String nfcFData;
    private String nfcVData;

    // Emulation data
    private String primaryAid;
    private String additionalAids;
    private String customApduCommands;
    private String customApduResponses;
    private String emulationUid;
    private String defaultResponse;

    // Card display info (for wallet-like UI)
    private String cardNumber;
    private String cardholderName;
    private String expiryDate;
    private String cardColor;

    // Metadata
    private long timestamp;
    private String filePath;
    private boolean isManualEntry;
    private String rawDump;
    private Map<String, String> extraFields;

    public CardModel() {
        this.id = String.valueOf(System.currentTimeMillis());
        this.timestamp = System.currentTimeMillis();
        this.cardType = CardType.UNKNOWN;
        this.company = Company.NONE;
        this.extraFields = new HashMap<>();
        this.defaultResponse = "9000";
        this.cardColor = "#1A237E";
    }

    public CardModel(String id) {
        this();
        this.id = id;
    }

    // --- Getters and Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getLabel() { return label != null ? label : "Unnamed Card"; }
    public void setLabel(String label) { this.label = label; }

    public CardType getCardType() { return cardType != null ? cardType : CardType.UNKNOWN; }
    public void setCardType(CardType cardType) { this.cardType = cardType; }

    public Company getCompany() { return company != null ? company : Company.NONE; }
    public void setCompany(Company company) { this.company = company; }

    public String getUid() { return uid != null ? uid : ""; }
    public void setUid(String uid) { this.uid = uid; }

    public String getUidReversed() { return uidReversed != null ? uidReversed : ""; }
    public void setUidReversed(String uidReversed) { this.uidReversed = uidReversed; }

    public String getAtqa() { return atqa != null ? atqa : ""; }
    public void setAtqa(String atqa) { this.atqa = atqa; }

    public String getSak() { return sak != null ? sak : ""; }
    public void setSak(String sak) { this.sak = sak; }

    public String getAts() { return ats != null ? ats : ""; }
    public void setAts(String ats) { this.ats = ats; }

    public String getTechnologies() { return technologies != null ? technologies : ""; }
    public void setTechnologies(String technologies) { this.technologies = technologies; }

    public String getHistoricalBytes() { return historicalBytes != null ? historicalBytes : ""; }
    public void setHistoricalBytes(String historicalBytes) { this.historicalBytes = historicalBytes; }

    public String getNdefData() { return ndefData != null ? ndefData : ""; }
    public void setNdefData(String ndefData) { this.ndefData = ndefData; }

    public String getMifareData() { return mifareData != null ? mifareData : ""; }
    public void setMifareData(String mifareData) { this.mifareData = mifareData; }

    public String getIsoDepData() { return isoDepData != null ? isoDepData : ""; }
    public void setIsoDepData(String isoDepData) { this.isoDepData = isoDepData; }

    public String getNfcAData() { return nfcAData != null ? nfcAData : ""; }
    public void setNfcAData(String nfcAData) { this.nfcAData = nfcAData; }

    public String getNfcBData() { return nfcBData != null ? nfcBData : ""; }
    public void setNfcBData(String nfcBData) { this.nfcBData = nfcBData; }

    public String getNfcFData() { return nfcFData != null ? nfcFData : ""; }
    public void setNfcFData(String nfcFData) { this.nfcFData = nfcFData; }

    public String getNfcVData() { return nfcVData != null ? nfcVData : ""; }
    public void setNfcVData(String nfcVData) { this.nfcVData = nfcVData; }

    public String getPrimaryAid() { return primaryAid != null ? primaryAid : ""; }
    public void setPrimaryAid(String primaryAid) { this.primaryAid = primaryAid; }

    public String getAdditionalAids() { return additionalAids != null ? additionalAids : ""; }
    public void setAdditionalAids(String additionalAids) { this.additionalAids = additionalAids; }

    public String getCustomApduCommands() { return customApduCommands != null ? customApduCommands : ""; }
    public void setCustomApduCommands(String customApduCommands) { this.customApduCommands = customApduCommands; }

    public String getCustomApduResponses() { return customApduResponses != null ? customApduResponses : ""; }
    public void setCustomApduResponses(String customApduResponses) { this.customApduResponses = customApduResponses; }

    public String getEmulationUid() {
        return (emulationUid != null && !emulationUid.isEmpty()) ? emulationUid : uid;
    }
    public void setEmulationUid(String emulationUid) { this.emulationUid = emulationUid; }

    public String getDefaultResponse() { return defaultResponse != null ? defaultResponse : "9000"; }
    public void setDefaultResponse(String defaultResponse) { this.defaultResponse = defaultResponse; }

    public String getCardNumber() { return cardNumber != null ? cardNumber : ""; }
    public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }

    public String getCardholderName() { return cardholderName != null ? cardholderName : ""; }
    public void setCardholderName(String cardholderName) { this.cardholderName = cardholderName; }

    public String getExpiryDate() { return expiryDate != null ? expiryDate : ""; }
    public void setExpiryDate(String expiryDate) { this.expiryDate = expiryDate; }

    public String getCardColor() { return cardColor != null ? cardColor : "#1A237E"; }
    public void setCardColor(String cardColor) { this.cardColor = cardColor; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getFilePath() { return filePath != null ? filePath : ""; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public boolean isManualEntry() { return isManualEntry; }
    public void setManualEntry(boolean manualEntry) { isManualEntry = manualEntry; }

    public String getRawDump() { return rawDump != null ? rawDump : ""; }
    public void setRawDump(String rawDump) { this.rawDump = rawDump; }

    public Map<String, String> getExtraFields() {
        if (extraFields == null) extraFields = new HashMap<>();
        return extraFields;
    }
    public void setExtraField(String key, String value) {
        if (extraFields == null) extraFields = new HashMap<>();
        extraFields.put(key, value);
    }
    public String getExtraField(String key) {
        if (extraFields == null) return "";
        return extraFields.getOrDefault(key, "");
    }

    /**
     * Returns all AIDs as a list (primary + additional).
     */
    public List<String> getAllAids() {
        List<String> aids = new ArrayList<>();
        if (primaryAid != null && !primaryAid.isEmpty()) {
            aids.add(primaryAid.trim().toUpperCase());
        }
        if (additionalAids != null && !additionalAids.isEmpty()) {
            for (String aid : additionalAids.split("[,\\n]")) {
                String trimmed = aid.trim().toUpperCase();
                if (!trimmed.isEmpty() && !aids.contains(trimmed)) {
                    aids.add(trimmed);
                }
            }
        }
        return aids;
    }

    /**
     * Serializes this card to a plain text format for file storage.
     */
    public String toTextFormat() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== NFC_Wallet Card ===\n");
        sb.append("ID: ").append(id).append("\n");
        sb.append("Label: ").append(getLabel()).append("\n");
        sb.append("Type: ").append(getCardType().name()).append("\n");
        sb.append("Company: ").append(getCompany().name()).append("\n");
        sb.append("Timestamp: ").append(timestamp).append("\n");
        sb.append("ManualEntry: ").append(isManualEntry).append("\n");
        sb.append("\n--- NFC Technical Data ---\n");
        sb.append("UID: ").append(uid).append("\n");
        sb.append("UID_Reversed: ").append(uidReversed).append("\n");
        sb.append("ATQA: ").append(atqa).append("\n");
        sb.append("SAK: ").append(sak).append("\n");
        sb.append("ATS: ").append(ats).append("\n");
        sb.append("HistoricalBytes: ").append(historicalBytes).append("\n");
        sb.append("Technologies: ").append(technologies).append("\n");
        sb.append("\n--- Protocol Data ---\n");
        sb.append("NDEF: ").append(ndefData).append("\n");
        sb.append("MIFARE: ").append(mifareData).append("\n");
        sb.append("ISO_DEP: ").append(isoDepData).append("\n");
        sb.append("NFC_A: ").append(nfcAData).append("\n");
        sb.append("NFC_B: ").append(nfcBData).append("\n");
        sb.append("NFC_F: ").append(nfcFData).append("\n");
        sb.append("NFC_V: ").append(nfcVData).append("\n");
        sb.append("\n--- Emulation Data ---\n");
        sb.append("PrimaryAID: ").append(primaryAid).append("\n");
        sb.append("AdditionalAIDs: ").append(additionalAids).append("\n");
        sb.append("EmulationUID: ").append(emulationUid).append("\n");
        sb.append("DefaultResponse: ").append(defaultResponse).append("\n");
        sb.append("CustomAPDUCommands: ").append(customApduCommands).append("\n");
        sb.append("CustomAPDUResponses: ").append(customApduResponses).append("\n");
        sb.append("\n--- Display Info ---\n");
        sb.append("CardNumber: ").append(cardNumber).append("\n");
        sb.append("CardholderName: ").append(cardholderName).append("\n");
        sb.append("ExpiryDate: ").append(expiryDate).append("\n");
        sb.append("CardColor: ").append(cardColor).append("\n");
        if (!getExtraFields().isEmpty()) {
            sb.append("\n--- Extra Fields ---\n");
            for (Map.Entry<String, String> e : extraFields.entrySet()) {
                sb.append(e.getKey()).append(": ").append(e.getValue()).append("\n");
            }
        }
        if (rawDump != null && !rawDump.isEmpty()) {
            sb.append("\n--- Raw Dump ---\n");
            sb.append(rawDump).append("\n");
        }
        sb.append("=== End Card ===\n");
        return sb.toString();
    }

    /**
     * Parses a card from the plain text format.
     */
    public static CardModel fromTextFormat(String text) {
        CardModel card = new CardModel();
        if (text == null || text.isEmpty()) return card;

        for (String line : text.split("\n")) {
            int colonIdx = line.indexOf(": ");
            if (colonIdx < 0) continue;
            String key = line.substring(0, colonIdx).trim();
            String value = line.substring(colonIdx + 2).trim();

            switch (key) {
                case "ID": card.id = value; break;
                case "Label": card.label = value; break;
                case "Type": {
                    try { card.cardType = CardType.valueOf(value); } catch (Exception ignored) {}
                    break;
                }
                case "Company": {
                    try { card.company = Company.valueOf(value); } catch (Exception ignored) {}
                    break;
                }
                case "Timestamp": {
                    try { card.timestamp = Long.parseLong(value); } catch (Exception ignored) {}
                    break;
                }
                case "ManualEntry": card.isManualEntry = Boolean.parseBoolean(value); break;
                case "UID": card.uid = value; break;
                case "UID_Reversed": card.uidReversed = value; break;
                case "ATQA": card.atqa = value; break;
                case "SAK": card.sak = value; break;
                case "ATS": card.ats = value; break;
                case "HistoricalBytes": card.historicalBytes = value; break;
                case "Technologies": card.technologies = value; break;
                case "NDEF": card.ndefData = value; break;
                case "MIFARE": card.mifareData = value; break;
                case "ISO_DEP": card.isoDepData = value; break;
                case "NFC_A": card.nfcAData = value; break;
                case "NFC_B": card.nfcBData = value; break;
                case "NFC_F": card.nfcFData = value; break;
                case "NFC_V": card.nfcVData = value; break;
                case "PrimaryAID": card.primaryAid = value; break;
                case "AdditionalAIDs": card.additionalAids = value; break;
                case "EmulationUID": card.emulationUid = value; break;
                case "DefaultResponse": card.defaultResponse = value; break;
                case "CustomAPDUCommands": card.customApduCommands = value; break;
                case "CustomAPDUResponses": card.customApduResponses = value; break;
                case "CardNumber": card.cardNumber = value; break;
                case "CardholderName": card.cardholderName = value; break;
                case "ExpiryDate": card.expiryDate = value; break;
                case "CardColor": card.cardColor = value; break;
                default:
                    if (!value.isEmpty() && !key.startsWith("===") && !key.startsWith("---")) {
                        card.setExtraField(key, value);
                    }
                    break;
            }
        }
        return card;
    }

    @Override
    public String toString() {
        return "CardModel{id='" + id + "', label='" + getLabel() + "', type=" + getCardType().name() + ", uid='" + uid + "'}";
    }
}
