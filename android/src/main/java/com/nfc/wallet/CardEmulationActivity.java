package com.nfc.wallet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nfc.wallet.model.CardModel;
import com.nfc.wallet.util.CardStorageManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Card emulation control activity.
 * Lets the user select a saved card (or enter details inline),
 * configure AID and UID spoofing, then start/stop HCE emulation.
 */
public class CardEmulationActivity extends Activity {

    private static final String TAG = "CardEmulationActivity";

    private CardStorageManager storageManager;
    private CardModel activeCard;
    private boolean isEmulating = false;

    private RecyclerView savedCardsList;
    private CardPickerAdapter cardAdapter;
    private List<CardModel> cardList = new ArrayList<>();

    private TextView emulationStatusText, activeCardLabel, activeCardUid, activeCardAid;
    private Button startStopBtn, configureBtn, backBtn;
    private Switch uidSpoofSwitch, aidSpoofSwitch;
    private EditText spoofUidInput, spoofAidInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emulation);

        storageManager = new CardStorageManager(this);
        initViews();

        // If launched from a specific card
        String cardFilePath = getIntent().getStringExtra("card_file");
        if (cardFilePath != null && !cardFilePath.isEmpty()) {
            CardModel card = storageManager.loadCard(cardFilePath);
            if (card != null) selectCard(card);
        }
    }

    private void initViews() {
        emulationStatusText = findViewById(R.id.emulation_status);
        activeCardLabel     = findViewById(R.id.active_card_label);
        activeCardUid       = findViewById(R.id.active_card_uid);
        activeCardAid       = findViewById(R.id.active_card_aid);
        startStopBtn        = findViewById(R.id.btn_start_stop);
        configureBtn        = findViewById(R.id.btn_configure_card);
        backBtn             = findViewById(R.id.btn_back);
        uidSpoofSwitch      = findViewById(R.id.switch_uid_spoof);
        aidSpoofSwitch      = findViewById(R.id.switch_aid_spoof);
        spoofUidInput       = findViewById(R.id.input_spoof_uid);
        spoofAidInput       = findViewById(R.id.input_spoof_aid);
        savedCardsList      = findViewById(R.id.saved_cards_list);

        // Saved cards list
        cardAdapter = new CardPickerAdapter(cardList);
        savedCardsList.setLayoutManager(new LinearLayoutManager(this));
        savedCardsList.setAdapter(cardAdapter);

        startStopBtn.setOnClickListener(v -> toggleEmulation());
        backBtn.setOnClickListener(v -> finish());

        if (configureBtn != null) {
            configureBtn.setOnClickListener(v -> {
                if (activeCard != null) {
                    Intent intent = new Intent(this, ManualCardActivity.class);
                    intent.putExtra("card_file", activeCard.getFilePath());
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "Select a card first", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // UID spoof toggle
        if (uidSpoofSwitch != null) {
            uidSpoofSwitch.setOnCheckedChangeListener((btn, checked) -> {
                if (spoofUidInput != null) spoofUidInput.setVisibility(checked ? View.VISIBLE : View.GONE);
                if (checked && activeCard != null && spoofUidInput != null
                        && spoofUidInput.getText().toString().isEmpty()) {
                    spoofUidInput.setText(activeCard.getUid());
                }
            });
        }

        // AID spoof toggle
        if (aidSpoofSwitch != null) {
            aidSpoofSwitch.setOnCheckedChangeListener((btn, checked) -> {
                if (spoofAidInput != null) spoofAidInput.setVisibility(checked ? View.VISIBLE : View.GONE);
                if (checked && activeCard != null && spoofAidInput != null
                        && spoofAidInput.getText().toString().isEmpty()) {
                    spoofAidInput.setText(activeCard.getPrimaryAid());
                }
            });
        }

        updateEmulationStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSavedCards();
        updateActiveCardDisplay();
    }

    private void refreshSavedCards() {
        cardList.clear();
        cardList.addAll(storageManager.loadAllCards());
        cardAdapter.notifyDataSetChanged();
    }

    private void selectCard(CardModel card) {
        activeCard = card;
        updateActiveCardDisplay();
        startStopBtn.setEnabled(true);
        // Pre-fill spoof fields
        if (spoofUidInput != null) spoofUidInput.setText(card.getUid());
        if (spoofAidInput != null) spoofAidInput.setText(card.getPrimaryAid());
    }

    private void updateActiveCardDisplay() {
        if (activeCard == null) {
            if (activeCardLabel != null) activeCardLabel.setText("No card selected");
            if (activeCardUid != null) activeCardUid.setText("UID: —");
            if (activeCardAid != null) activeCardAid.setText("AID: —");
            startStopBtn.setEnabled(false);
            return;
        }
        if (activeCardLabel != null) activeCardLabel.setText(activeCard.getLabel());
        if (activeCardUid != null) activeCardUid.setText("UID: " + (activeCard.getUid().isEmpty() ? "—" : activeCard.getUid()));
        if (activeCardAid != null) activeCardAid.setText("AID: " + (activeCard.getPrimaryAid().isEmpty() ? "—" : activeCard.getPrimaryAid()));
        startStopBtn.setEnabled(true);
    }

    private void toggleEmulation() {
        if (isEmulating) {
            stopEmulation();
        } else {
            startEmulation();
        }
    }

    private void startEmulation() {
        if (activeCard == null) {
            Toast.makeText(this, "Select a card to emulate", Toast.LENGTH_SHORT).show();
            return;
        }

        // Apply UID/AID spoofing if enabled
        CardModel emulationCard = activeCard;
        if (uidSpoofSwitch != null && uidSpoofSwitch.isChecked() && spoofUidInput != null) {
            String spoofedUid = spoofUidInput.getText().toString().trim().toUpperCase();
            if (!spoofedUid.isEmpty()) {
                emulationCard.setEmulationUid(spoofedUid);
                Log.d(TAG, "UID spoofing enabled: " + spoofedUid);
            }
        }
        if (aidSpoofSwitch != null && aidSpoofSwitch.isChecked() && spoofAidInput != null) {
            String spoofedAid = spoofAidInput.getText().toString().trim().toUpperCase();
            if (!spoofedAid.isEmpty()) {
                emulationCard.setPrimaryAid(spoofedAid);
                Log.d(TAG, "AID spoofing enabled: " + spoofedAid);
            }
        }

        // Save potentially modified card
        storageManager.updateCard(emulationCard);

        // Start emulation service
        Intent serviceIntent = new Intent(this, NfcEmulatorService.class);
        serviceIntent.putExtra(NfcEmulatorService.EXTRA_CARD_FILE, emulationCard.getFilePath());
        startForegroundService(serviceIntent);

        isEmulating = true;
        updateEmulationStatus();
        Toast.makeText(this, "Emulation started: " + emulationCard.getLabel(), Toast.LENGTH_SHORT).show();
    }

    private void stopEmulation() {
        sendBroadcast(new Intent("com.nfc.wallet.STOP_EMULATION"));
        Intent serviceIntent = new Intent(this, NfcEmulatorService.class);
        stopService(serviceIntent);

        isEmulating = false;
        updateEmulationStatus();
        Toast.makeText(this, "Emulation stopped", Toast.LENGTH_SHORT).show();
    }

    private void updateEmulationStatus() {
        if (emulationStatusText != null) {
            emulationStatusText.setText(isEmulating ? "● EMULATING" : "○ Stopped");
            emulationStatusText.setTextColor(isEmulating ? 0xFF69F0AE : 0xFF9E9E9E);
        }
        if (startStopBtn != null) {
            startStopBtn.setText(isEmulating ? "Stop Emulation" : "Start Emulation");
        }
    }

    // --- Card Picker Adapter ---

    private class CardPickerAdapter extends RecyclerView.Adapter<CardPickerAdapter.VH> {
        private final List<CardModel> items;
        CardPickerAdapter(List<CardModel> items) { this.items = items; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            CardModel card = items.get(position);
            holder.text1.setText(card.getLabel());
            holder.text2.setText(card.getCardType().getDisplayName() + (card.getUid().isEmpty() ? "" : " | UID: " + card.getUid()));
            holder.itemView.setOnClickListener(v -> {
                selectCard(card);
                notifyDataSetChanged();
            });
            // Highlight active card
            boolean isActive = activeCard != null && activeCard.getId().equals(card.getId());
            holder.itemView.setBackgroundColor(isActive ? 0xFF1E3A2E : 0xFF1A1A1A);
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView text1, text2;
            VH(View v) {
                super(v);
                text1 = v.findViewById(android.R.id.text1);
                text2 = v.findViewById(android.R.id.text2);
                if (text1 != null) text1.setTextColor(0xFFFFFFFF);
                if (text2 != null) text2.setTextColor(0xFFAAAAAA);
                v.setBackgroundColor(0xFF1A1A1A);
            }
        }
    }
}
