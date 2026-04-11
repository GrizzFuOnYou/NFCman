package com.nfc.wallet;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nfc.wallet.model.CardModel;
import com.nfc.wallet.util.CardStorageManager;
import com.nfc.wallet.util.RootUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Main wallet dashboard activity.
 * Shows all saved cards in a scrollable list and provides navigation to all features.
 */
public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private static final int PERM_REQUEST_CODE = 1001;
    private static final int MANAGE_STORAGE_REQUEST_CODE = 1002;

    private RecyclerView cardRecyclerView;
    private CardAdapter cardAdapter;
    private List<CardModel> cardList = new ArrayList<>();
    private TextView emptyView;
    private TextView nfcStatusText;
    private CardStorageManager storageManager;
    private NfcAdapter nfcAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        storageManager = new CardStorageManager(this);
        initViews();
        checkPermissions();
        checkNfcState();
        checkRootStatus();
    }

    private void initViews() {
        cardRecyclerView = findViewById(R.id.card_recycler);
        emptyView = findViewById(R.id.empty_view);
        nfcStatusText = findViewById(R.id.nfc_status_text);

        // Set up RecyclerView
        cardAdapter = new CardAdapter(cardList);
        cardRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        cardRecyclerView.setAdapter(cardAdapter);

        // Navigation buttons
        Button scanBtn = findViewById(R.id.btn_scan);
        Button manualBtn = findViewById(R.id.btn_manual);
        Button emulateBtn = findViewById(R.id.btn_emulate);
        Button testBtn = findViewById(R.id.btn_test);
        Button rootBtn = findViewById(R.id.btn_root);

        if (scanBtn != null) scanBtn.setOnClickListener(v ->
                startActivity(new Intent(this, NFCScanActivity.class)));
        if (manualBtn != null) manualBtn.setOnClickListener(v ->
                startActivity(new Intent(this, ManualCardActivity.class)));
        if (emulateBtn != null) emulateBtn.setOnClickListener(v ->
                startActivity(new Intent(this, CardEmulationActivity.class)));
        if (testBtn != null) testBtn.setOnClickListener(v ->
                startActivity(new Intent(this, TestingToolsActivity.class)));
        if (rootBtn != null) rootBtn.setOnClickListener(v ->
                startActivity(new Intent(this, RootFeaturesActivity.class)));
    }

    private void refreshCards() {
        List<CardModel> cards = storageManager.loadAllCards();
        cardList.clear();
        cardList.addAll(cards);
        cardAdapter.notifyDataSetChanged();
        emptyView.setVisibility(cardList.isEmpty() ? View.VISIBLE : View.GONE);
        cardRecyclerView.setVisibility(cardList.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void checkNfcState() {
        try {
            NfcManager nfcManager = (NfcManager) getSystemService(NFC_SERVICE);
            nfcAdapter = nfcManager.getDefaultAdapter();
            if (nfcAdapter == null) {
                updateNfcStatus("NFC: Not Available", 0xFFFF5252);
            } else if (!nfcAdapter.isEnabled()) {
                updateNfcStatus("NFC: Disabled", 0xFFFFB300);
            } else {
                updateNfcStatus("NFC: Ready ✓", 0xFF69F0AE);
            }
        } catch (Exception e) {
            updateNfcStatus("NFC: Error", 0xFFFF5252);
        }
    }

    private void updateNfcStatus(String msg, int color) {
        if (nfcStatusText != null) {
            nfcStatusText.setText(msg);
            nfcStatusText.setTextColor(color);
        }
    }

    private void checkRootStatus() {
        new Handler(Looper.getMainLooper()).post(() -> {
            new Thread(() -> {
                boolean rooted = RootUtils.isRootAvailable();
                runOnUiThread(() -> {
                    TextView rootStatus = findViewById(R.id.root_status_text);
                    if (rootStatus != null) {
                        rootStatus.setText(rooted ? "Root: Active ✓" : "Root: Not available");
                        rootStatus.setTextColor(rooted ? 0xFF69F0AE : 0xFFFFB300);
                    }
                });
            }).start();
        });
    }

    private void checkPermissions() {
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), PERM_REQUEST_CODE);
        } else {
            checkManageStoragePermission();
        }
    }

    private void checkManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            new AlertDialog.Builder(this)
                    .setTitle("Storage Permission")
                    .setMessage("Grant 'All files access' for full card storage functionality.")
                    .setPositiveButton("Grant", (d, w) -> {
                        try {
                            Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:" + getPackageName()));
                            startActivityForResult(i, MANAGE_STORAGE_REQUEST_CODE);
                        } catch (Exception e) {
                            startActivityForResult(
                                    new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                                    MANAGE_STORAGE_REQUEST_CODE);
                        }
                    })
                    .setNegativeButton("Skip", null)
                    .show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        checkManageStoragePermission();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkNfcState();
        refreshCards();
    }

    // --- Card Adapter ---

    private class CardAdapter extends RecyclerView.Adapter<CardAdapter.ViewHolder> {
        private final List<CardModel> items;

        CardAdapter(List<CardModel> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_card, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CardModel card = items.get(position);
            holder.bind(card);
        }

        @Override
        public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView labelText, typeText, uidText, dateText;
            LinearLayout cardBackground;
            Button emulateBtn, deleteBtn;

            ViewHolder(View v) {
                super(v);
                labelText = v.findViewById(R.id.card_label);
                typeText = v.findViewById(R.id.card_type);
                uidText = v.findViewById(R.id.card_uid);
                dateText = v.findViewById(R.id.card_date);
                cardBackground = v.findViewById(R.id.card_background);
                emulateBtn = v.findViewById(R.id.btn_card_emulate);
                deleteBtn = v.findViewById(R.id.btn_card_delete);
            }

            void bind(CardModel card) {
                labelText.setText(card.getLabel());
                typeText.setText(card.getCardType().getDisplayName()
                        + (card.getCompany() != CardModel.Company.NONE
                        ? " · " + card.getCompany().getDisplayName() : ""));
                uidText.setText(card.getUid().isEmpty() ? "No UID" : "UID: " + card.getUid());
                dateText.setText(new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US)
                        .format(new Date(card.getTimestamp())));

                try {
                    int color = (int) Long.parseLong(card.getCardColor().replace("#", ""), 16);
                    if (cardBackground != null) {
                        cardBackground.setBackgroundColor(0xFF000000 | color);
                    }
                } catch (Exception ignored) {}

                emulateBtn.setOnClickListener(v -> {
                    Intent intent = new Intent(MainActivity.this, CardEmulationActivity.class);
                    intent.putExtra("card_file", card.getFilePath());
                    startActivity(intent);
                });

                deleteBtn.setOnClickListener(v -> {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Delete Card")
                            .setMessage("Delete \"" + card.getLabel() + "\"?")
                            .setPositiveButton("Delete", (d, w) -> {
                                storageManager.deleteCard(card);
                                int pos = getAdapterPosition();
                                if (pos != RecyclerView.NO_ID) {
                                    items.remove(pos);
                                    notifyItemRemoved(pos);
                                    updateEmptyViewState();
                                }
                                Toast.makeText(MainActivity.this, "Card deleted", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });

                itemView.setOnClickListener(v -> {
                    Intent intent = new Intent(MainActivity.this, ManualCardActivity.class);
                    intent.putExtra("card_file", card.getFilePath());
                    intent.putExtra("view_mode", true);
                    startActivity(intent);
                });
            }
        }
    }

    private void updateEmptyViewState() {
        emptyView.setVisibility(cardList.isEmpty() ? View.VISIBLE : View.GONE);
        cardRecyclerView.setVisibility(cardList.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
