package com.nfc.wallet;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CardModel {
    public String name = "";
    public String uid = "";
    public String cardType = "";
    public String company = "";
    public String aid = "";
    public String atr = "";
    public String apduSelectResponse = "";
    public String notes = "";
    public String scanDate = "";
    public String[] techs = new String[0];
    public boolean isManual = false;
    public Map<String, String> extraFields = new HashMap<>();

    public CardModel() {
        scanDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    public String toTxt() {
        StringBuilder sb = new StringBuilder();
        sb.append("CARD_NAME=").append(safe(name)).append("\n");
        sb.append("CARD_TYPE=").append(safe(cardType)).append("\n");
        sb.append("CARD_COMPANY=").append(safe(company)).append("\n");
        sb.append("UID=").append(safe(uid)).append("\n");
        sb.append("ATR=").append(safe(atr)).append("\n");
        sb.append("AID=").append(safe(aid)).append("\n");
        sb.append("APDU_SELECT_RESPONSE=").append(safe(apduSelectResponse)).append("\n");
        if (techs != null && techs.length > 0) {
            sb.append("TECHS=").append(String.join(",", techs)).append("\n");
        } else {
            sb.append("TECHS=\n");
        }
        sb.append("IS_MANUAL=").append(isManual).append("\n");
        sb.append("SCAN_DATE=").append(safe(scanDate)).append("\n");
        sb.append("NOTES=").append(safe(notes)).append("\n");
        if (extraFields != null) {
            for (Map.Entry<String, String> e : extraFields.entrySet()) {
                sb.append("EXTRA_").append(e.getKey()).append("=").append(safe(e.getValue())).append("\n");
            }
        }
        return sb.toString();
    }

    public static CardModel fromTxt(String content) {
        CardModel card = new CardModel();
        if (content == null || content.isEmpty()) return card;
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            int idx = line.indexOf('=');
            if (idx < 0) continue;
            String key = line.substring(0, idx).trim();
            String val = line.substring(idx + 1).trim();
            switch (key) {
                case "CARD_NAME": card.name = val; break;
                case "CARD_TYPE": card.cardType = val; break;
                case "CARD_COMPANY": card.company = val; break;
                case "UID": card.uid = val; break;
                case "ATR": card.atr = val; break;
                case "AID": card.aid = val; break;
                case "APDU_SELECT_RESPONSE": card.apduSelectResponse = val; break;
                case "TECHS":
                    if (!val.isEmpty()) {
                        card.techs = val.split(",");
                    }
                    break;
                case "IS_MANUAL": card.isManual = "true".equalsIgnoreCase(val); break;
                case "SCAN_DATE": card.scanDate = val; break;
                case "NOTES": card.notes = val; break;
                default:
                    if (key.startsWith("EXTRA_")) {
                        String extraKey = key.substring(6);
                        card.extraFields.put(extraKey, val);
                    }
                    break;
            }
        }
        return card;
    }

    private String safe(String s) {
        return s == null ? "" : s.replace("\n", " ");
    }

    public String getSafeFilename() {
        String base = (!uid.isEmpty() ? uid : name);
        return base.replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".txt";
    }
}
