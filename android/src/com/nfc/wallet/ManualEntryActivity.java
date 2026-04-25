package com.nfc.wallet;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

import java.util.Map;

public class ManualEntryActivity extends AppCompatActivity {

    private TextInputEditText etName, etUid, etAid, etAtr, etApduResponse, etNotes;
    private Spinner spinnerType, spinnerCompany;
    private CardManager cardManager;

    private static final String[] CARD_TYPES = {
            "MIFARE Classic", "MIFARE Ultralight", "NTAG213", "NTAG215", "NTAG216",
            "ISO14443-4", "FeliCa", "NDEF", "Other"
    };

    private static final String[] COMPANIES = {
            "Unknown", "Visa", "Mastercard", "Amex", "Discover",
            "UnionPay", "JCB", "Maestro", "Interac",
            "Transit/Building", "NXP", "Other"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual_entry);

        cardManager = new CardManager(this);
        bindViews();
        setupSpinners();
        setupListeners();
        prefillFromIntent();
    }

    private void bindViews() {
        etName = findViewById(R.id.et_card_name);
        etUid = findViewById(R.id.et_uid);
        etAid = findViewById(R.id.et_aid);
        etAtr = findViewById(R.id.et_atr);
        etApduResponse = findViewById(R.id.et_apdu_response);
        etNotes = findViewById(R.id.et_notes);
        spinnerType = findViewById(R.id.spinner_card_type);
        spinnerCompany = findViewById(R.id.spinner_company);
    }

    private void setupSpinners() {
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, CARD_TYPES);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerType.setAdapter(typeAdapter);

        ArrayAdapter<String> companyAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, COMPANIES);
        companyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCompany.setAdapter(companyAdapter);
    }

    private void setupListeners() {
        // Auto-detect card type from UID pattern
        etUid.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                String uid = s.toString().trim();
                if (uid.length() >= 8) {
                    autoDetectFromUid(uid);
                }
            }
        });

        // Auto-fill AID when company is selected
        spinnerCompany.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int pos, long id) {
                autoFillAidFromCompany(COMPANIES[pos]);
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        Button btnSave = findViewById(R.id.btn_save);
        btnSave.setOnClickListener(v -> saveCard());

        Button btnCancel = findViewById(R.id.btn_cancel);
        btnCancel.setOnClickListener(v -> finish());
    }

    private void autoDetectFromUid(String uid) {
        // Basic heuristics
        uid = uid.replaceAll("\\s", "").toUpperCase();
        if (uid.startsWith("04") && uid.length() == 14) {
            // NXP 7-byte UID (common for MIFARE/ISO14443)
            setSpinnerByValue(spinnerType, "MIFARE Classic");
        } else if (uid.length() == 8) {
            // 4-byte UID
            setSpinnerByValue(spinnerType, "MIFARE Classic");
        } else if (uid.length() == 20) {
            // 10-byte UID
            setSpinnerByValue(spinnerType, "ISO14443-4");
        }
    }

    private void autoFillAidFromCompany(String company) {
        String aidHex = "";
        Map<String, byte[]> aids = ApduHelper.knownAids();
        switch (company) {
            case "Visa": aidHex = "A0000000031010"; break;
            case "Mastercard": aidHex = "A0000000041010"; break;
            case "Amex": aidHex = "A000000025010402"; break;
            case "Discover": aidHex = "A0000000333010101"; break;
            case "UnionPay": aidHex = "A000000172950001"; break;
            case "JCB": aidHex = "A0000000651010"; break;
            case "Maestro": aidHex = "A0000000043060"; break;
            case "Interac": aidHex = "A000000152414341"; break;
            default: return;
        }
        if (etAid != null && (etAid.getText() == null || etAid.getText().toString().isEmpty())) {
            etAid.setText(aidHex);
        }
    }

    private void setSpinnerByValue(Spinner spinner, String value) {
        ArrayAdapter adapter = (ArrayAdapter) spinner.getAdapter();
        for (int i = 0; i < adapter.getCount(); i++) {
            if (adapter.getItem(i).toString().equals(value)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private void prefillFromIntent() {
        Intent intent = getIntent();
        if (intent == null) return;
        String name = intent.getStringExtra("card_name");
        String uid = intent.getStringExtra("card_uid");
        String type = intent.getStringExtra("card_type");
        String company = intent.getStringExtra("card_company");
        String aid = intent.getStringExtra("card_aid");
        String atr = intent.getStringExtra("card_atr");
        String apduResp = intent.getStringExtra("card_apdu_response");
        String notes = intent.getStringExtra("card_notes");

        if (name != null) etName.setText(name);
        if (uid != null) etUid.setText(uid);
        if (type != null) setSpinnerByValue(spinnerType, type);
        if (company != null) setSpinnerByValue(spinnerCompany, company);
        if (aid != null) etAid.setText(aid);
        if (atr != null) etAtr.setText(atr);
        if (apduResp != null) etApduResponse.setText(apduResp);
        if (notes != null) etNotes.setText(notes);
    }

    private void saveCard() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String uid = etUid.getText() != null ? etUid.getText().toString().trim() : "";

        if (name.isEmpty() && uid.isEmpty()) {
            Toast.makeText(this, "Please enter at least a name or UID", Toast.LENGTH_SHORT).show();
            return;
        }
        if (name.isEmpty()) name = "Card_" + uid.substring(0, Math.min(8, uid.length()));

        CardModel card = new CardModel();
        card.name = name;
        card.uid = uid;
        card.cardType = spinnerType.getSelectedItem().toString();
        card.company = spinnerCompany.getSelectedItem().toString();
        card.aid = etAid.getText() != null ? etAid.getText().toString().trim() : "";
        card.atr = etAtr.getText() != null ? etAtr.getText().toString().trim() : "";
        card.apduSelectResponse = etApduResponse.getText() != null ? etApduResponse.getText().toString().trim() : "";
        card.notes = etNotes.getText() != null ? etNotes.getText().toString().trim() : "";
        card.isManual = true;

        boolean ok = cardManager.saveCard(card);
        if (ok) {
            Toast.makeText(this, "Card saved!", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "Failed to save card", Toast.LENGTH_SHORT).show();
        }
    }
}
