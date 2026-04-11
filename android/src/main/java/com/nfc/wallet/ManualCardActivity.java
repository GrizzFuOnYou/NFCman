package com.nfc.wallet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.nfc.wallet.model.CardModel;
import com.nfc.wallet.util.CardStorageManager;
import com.nfc.wallet.util.CardTypeDetector;

import java.util.ArrayList;
import java.util.List;

/**
 * Manual card entry activity.
 * Allows user to enter card data directly, choose card type and company,
 * or have the app auto-detect based on card number / AID.
 * Also used for viewing/editing saved cards.
 */
public class ManualCardActivity extends Activity {

    private CardStorageManager storageManager;
    private CardModel currentCard;
    private boolean isViewMode = false;
    private boolean isEditMode = false;

    // UI fields
    private EditText labelInput, uidInput, atqaInput, sakInput, aidInput,
            additionalAidsInput, cardNumberInput, cardholderInput, expiryInput,
            apduCommandsInput, apduResponsesInput, customResponseInput,
            emulationUidInput, rawNdefInput, rawMifareInput, rawIsoDepInput;
    private Spinner typeSpinner, companySpinner;
    private TextView detectedTypeText;
    private Button saveBtn, cancelBtn, autoDetectBtn, emulateBtn;
    private View cardColorPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual_card);

        storageManager = new CardStorageManager(this);
        initViews();

        // Check if we're opening an existing card
        String cardFilePath = getIntent().getStringExtra("card_file");
        String prefillText = getIntent().getStringExtra("prefill_card");
        isViewMode = getIntent().getBooleanExtra("view_mode", false);

        if (cardFilePath != null && !cardFilePath.isEmpty()) {
            currentCard = storageManager.loadCard(cardFilePath);
            if (currentCard != null) {
                populateFields(currentCard);
                if (isViewMode) setViewOnlyMode();
            }
        } else if (prefillText != null && !prefillText.isEmpty()) {
            currentCard = CardModel.fromTextFormat(prefillText);
            populateFields(currentCard);
            isEditMode = true;
        } else {
            currentCard = new CardModel();
            isEditMode = true;
        }

        setupAutoDetect();
    }

    private void initViews() {
        labelInput         = findViewById(R.id.input_label);
        uidInput           = findViewById(R.id.input_uid);
        atqaInput          = findViewById(R.id.input_atqa);
        sakInput           = findViewById(R.id.input_sak);
        aidInput           = findViewById(R.id.input_aid);
        additionalAidsInput = findViewById(R.id.input_additional_aids);
        cardNumberInput    = findViewById(R.id.input_card_number);
        cardholderInput    = findViewById(R.id.input_cardholder);
        expiryInput        = findViewById(R.id.input_expiry);
        apduCommandsInput  = findViewById(R.id.input_apdu_commands);
        apduResponsesInput = findViewById(R.id.input_apdu_responses);
        customResponseInput = findViewById(R.id.input_custom_response);
        emulationUidInput  = findViewById(R.id.input_emulation_uid);
        rawNdefInput       = findViewById(R.id.input_ndef);
        rawMifareInput     = findViewById(R.id.input_mifare);
        rawIsoDepInput     = findViewById(R.id.input_isodep);
        detectedTypeText   = findViewById(R.id.detected_type_text);
        cardColorPreview   = findViewById(R.id.card_color_preview);

        typeSpinner    = findViewById(R.id.spinner_type);
        companySpinner = findViewById(R.id.spinner_company);

        saveBtn      = findViewById(R.id.btn_save);
        cancelBtn    = findViewById(R.id.btn_cancel);
        autoDetectBtn = findViewById(R.id.btn_auto_detect);
        emulateBtn   = findViewById(R.id.btn_emulate_direct);

        // Populate spinners
        setupSpinners();

        saveBtn.setOnClickListener(v -> saveCard());
        cancelBtn.setOnClickListener(v -> finish());
        autoDetectBtn.setOnClickListener(v -> runAutoDetect());

        if (emulateBtn != null) {
            emulateBtn.setOnClickListener(v -> {
                collectFields();
                if (!storageManager.saveCard(currentCard)) {
                    Toast.makeText(this, "Could not save card before emulation", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(this, CardEmulationActivity.class);
                intent.putExtra("card_file", currentCard.getFilePath());
                startActivity(intent);
            });
        }

        // Color preview click to choose a color
        if (cardColorPreview != null) {
            cardColorPreview.setOnClickListener(v -> chooseCardColor());
        }
    }

    private void setupSpinners() {
        // Card type spinner
        List<String> typeNames = new ArrayList<>();
        for (CardModel.CardType t : CardModel.CardType.values()) typeNames.add(t.getDisplayName());
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, typeNames);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(typeAdapter);

        // Company spinner
        List<String> companyNames = new ArrayList<>();
        for (CardModel.Company c : CardModel.Company.values()) companyNames.add(c.getDisplayName());
        ArrayAdapter<String> companyAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, companyNames);
        companyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        companySpinner.setAdapter(companyAdapter);

        typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (currentCard != null) {
                    currentCard.setCardType(CardModel.CardType.values()[position]);
                    updateColorPreview();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        companySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (currentCard != null) {
                    currentCard.setCompany(CardModel.Company.values()[position]);
                    updateColorPreview();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void populateFields(CardModel card) {
        if (labelInput != null) labelInput.setText(card.getLabel());
        if (uidInput != null) uidInput.setText(card.getUid());
        if (atqaInput != null) atqaInput.setText(card.getAtqa());
        if (sakInput != null) sakInput.setText(card.getSak());
        if (aidInput != null) aidInput.setText(card.getPrimaryAid());
        if (additionalAidsInput != null) additionalAidsInput.setText(card.getAdditionalAids());
        if (cardNumberInput != null) cardNumberInput.setText(card.getCardNumber());
        if (cardholderInput != null) cardholderInput.setText(card.getCardholderName());
        if (expiryInput != null) expiryInput.setText(card.getExpiryDate());
        if (apduCommandsInput != null) apduCommandsInput.setText(card.getCustomApduCommands());
        if (apduResponsesInput != null) apduResponsesInput.setText(card.getCustomApduResponses());
        if (customResponseInput != null) customResponseInput.setText(card.getDefaultResponse());
        if (emulationUidInput != null) emulationUidInput.setText(card.getEmulationUid());
        if (rawNdefInput != null) rawNdefInput.setText(card.getNdefData());
        if (rawMifareInput != null) rawMifareInput.setText(card.getMifareData());
        if (rawIsoDepInput != null) rawIsoDepInput.setText(card.getIsoDepData());

        // Set spinner positions
        if (typeSpinner != null) {
            CardModel.CardType[] types = CardModel.CardType.values();
            for (int i = 0; i < types.length; i++) {
                if (types[i] == card.getCardType()) {
                    typeSpinner.setSelection(i);
                    break;
                }
            }
        }
        if (companySpinner != null) {
            CardModel.Company[] companies = CardModel.Company.values();
            for (int i = 0; i < companies.length; i++) {
                if (companies[i] == card.getCompany()) {
                    companySpinner.setSelection(i);
                    break;
                }
            }
        }

        if (detectedTypeText != null) {
            detectedTypeText.setText("Type: " + card.getCardType().getDisplayName()
                    + (card.getCompany() != CardModel.Company.NONE
                    ? " (" + card.getCompany().getDisplayName() + ")" : ""));
        }

        updateColorPreview();
    }

    private void collectFields() {
        if (currentCard == null) currentCard = new CardModel();
        if (labelInput != null) currentCard.setLabel(labelInput.getText().toString().trim());
        if (uidInput != null) currentCard.setUid(uidInput.getText().toString().trim().toUpperCase());
        if (atqaInput != null) currentCard.setAtqa(atqaInput.getText().toString().trim().toUpperCase());
        if (sakInput != null) currentCard.setSak(sakInput.getText().toString().trim().toUpperCase());
        if (aidInput != null) currentCard.setPrimaryAid(aidInput.getText().toString().trim().toUpperCase());
        if (additionalAidsInput != null) currentCard.setAdditionalAids(additionalAidsInput.getText().toString().trim());
        if (cardNumberInput != null) currentCard.setCardNumber(cardNumberInput.getText().toString().trim());
        if (cardholderInput != null) currentCard.setCardholderName(cardholderInput.getText().toString().trim());
        if (expiryInput != null) currentCard.setExpiryDate(expiryInput.getText().toString().trim());
        if (apduCommandsInput != null) currentCard.setCustomApduCommands(apduCommandsInput.getText().toString().trim());
        if (apduResponsesInput != null) currentCard.setCustomApduResponses(apduResponsesInput.getText().toString().trim());
        if (customResponseInput != null) currentCard.setDefaultResponse(customResponseInput.getText().toString().trim());
        if (emulationUidInput != null) currentCard.setEmulationUid(emulationUidInput.getText().toString().trim().toUpperCase());
        if (rawNdefInput != null) currentCard.setNdefData(rawNdefInput.getText().toString().trim());
        if (rawMifareInput != null) currentCard.setMifareData(rawMifareInput.getText().toString().trim());
        if (rawIsoDepInput != null) currentCard.setIsoDepData(rawIsoDepInput.getText().toString().trim());
        if (typeSpinner != null) currentCard.setCardType(CardModel.CardType.values()[typeSpinner.getSelectedItemPosition()]);
        if (companySpinner != null) currentCard.setCompany(CardModel.Company.values()[companySpinner.getSelectedItemPosition()]);
        currentCard.setManualEntry(true);
    }

    private void saveCard() {
        collectFields();

        if (currentCard.getLabel().isEmpty() || currentCard.getLabel().equals("Unnamed Card")) {
            if (!currentCard.getUid().isEmpty()) {
                currentCard.setLabel("Card_" + currentCard.getUid());
            } else if (!currentCard.getPrimaryAid().isEmpty()) {
                currentCard.setLabel("Card_AID_" + currentCard.getPrimaryAid());
            } else {
                currentCard.setLabel("Card_" + System.currentTimeMillis());
            }
        }

        boolean saved = storageManager.saveCard(currentCard);
        if (saved) {
            Toast.makeText(this, "Card saved: " + currentCard.getLabel(), Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "Error saving card", Toast.LENGTH_SHORT).show();
        }
    }

    private void runAutoDetect() {
        collectFields();
        CardTypeDetector.detectFromManualData(currentCard);
        populateFields(currentCard);
        if (detectedTypeText != null) {
            detectedTypeText.setText("Auto-detected: " + currentCard.getCardType().getDisplayName()
                    + (currentCard.getCompany() != CardModel.Company.NONE
                    ? " (" + currentCard.getCompany().getDisplayName() + ")" : ""));
        }
        Toast.makeText(this, "Auto-detection applied", Toast.LENGTH_SHORT).show();
    }

    private void setupAutoDetect() {
        if (cardNumberInput != null) {
            cardNumberInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void afterTextChanged(Editable s) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String num = s.toString().replaceAll("\\s", "");
                    if (num.length() >= 4) {
                        if (currentCard == null) currentCard = new CardModel();
                        currentCard.setCardNumber(num);
                        CardTypeDetector.detectFromManualData(currentCard);
                        updateSpinnerFromCard();
                        updateColorPreview();
                    }
                }
            });
        }
    }

    private void updateSpinnerFromCard() {
        if (typeSpinner != null && currentCard != null) {
            CardModel.CardType[] types = CardModel.CardType.values();
            for (int i = 0; i < types.length; i++) {
                if (types[i] == currentCard.getCardType()) { typeSpinner.setSelection(i); break; }
            }
        }
        if (companySpinner != null && currentCard != null) {
            CardModel.Company[] companies = CardModel.Company.values();
            for (int i = 0; i < companies.length; i++) {
                if (companies[i] == currentCard.getCompany()) { companySpinner.setSelection(i); break; }
            }
        }
        if (aidInput != null && currentCard != null && !currentCard.getPrimaryAid().isEmpty()) {
            aidInput.setText(currentCard.getPrimaryAid());
        }
    }

    private void updateColorPreview() {
        if (cardColorPreview != null && currentCard != null) {
            String suggestedColor = CardTypeDetector.getSuggestedCardColor(currentCard);
            currentCard.setCardColor(suggestedColor);
            try {
                cardColorPreview.setBackgroundColor(Color.parseColor(suggestedColor));
            } catch (Exception ignored) {}
        }
    }

    private void chooseCardColor() {
        String[] colors = {"#1565C0", "#B71C1C", "#00695C", "#E65100", "#4A148C",
                "#1B5E20", "#0D47A1", "#006064", "#BF360C", "#37474F", "#263238", "#1A237E"};
        String[] colorNames = {"Blue", "Red", "Teal", "Orange", "Purple",
                "Dark Green", "Dark Blue", "Dark Teal", "Dark Orange", "Gray", "Dark Gray", "Navy"};
        new AlertDialog.Builder(this)
                .setTitle("Choose Card Color")
                .setItems(colorNames, (d, which) -> {
                    if (currentCard != null) currentCard.setCardColor(colors[which]);
                    updateColorPreview();
                })
                .show();
    }

    private void setViewOnlyMode() {
        if (labelInput != null) labelInput.setEnabled(false);
        if (uidInput != null) uidInput.setEnabled(false);
        if (atqaInput != null) atqaInput.setEnabled(false);
        if (sakInput != null) sakInput.setEnabled(false);
        if (aidInput != null) aidInput.setEnabled(false);
        if (additionalAidsInput != null) additionalAidsInput.setEnabled(false);
        if (cardNumberInput != null) cardNumberInput.setEnabled(false);
        if (cardholderInput != null) cardholderInput.setEnabled(false);
        if (expiryInput != null) expiryInput.setEnabled(false);
        if (apduCommandsInput != null) apduCommandsInput.setEnabled(false);
        if (apduResponsesInput != null) apduResponsesInput.setEnabled(false);
        if (customResponseInput != null) customResponseInput.setEnabled(false);
        if (emulationUidInput != null) emulationUidInput.setEnabled(false);
        if (rawNdefInput != null) rawNdefInput.setEnabled(false);
        if (rawMifareInput != null) rawMifareInput.setEnabled(false);
        if (rawIsoDepInput != null) rawIsoDepInput.setEnabled(false);
        if (typeSpinner != null) typeSpinner.setEnabled(false);
        if (companySpinner != null) companySpinner.setEnabled(false);
        if (autoDetectBtn != null) autoDetectBtn.setEnabled(false);
        if (saveBtn != null) {
            saveBtn.setText("Edit");
            saveBtn.setOnClickListener(v -> {
                isViewMode = false;
                setViewOnlyMode(); // re-evaluate to enable
                saveBtn.setText("Save");
                saveBtn.setOnClickListener(v2 -> saveCard());
            });
        }
    }
}
