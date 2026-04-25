package com.nfc.wallet;

import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;

public class MainActivity extends AppCompatActivity {

    private CardManager cardManager;
    private NfcAdapter nfcAdapter;
    private TextView tvCardCount;
    private Chip chipNfcStatus;
    private Chip chipRootStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cardManager = new CardManager(this);
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        setupToolbar();
        setupBottomNavigation();
        updateStatusChips();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("NFC Wallet");
        }
        chipNfcStatus = findViewById(R.id.chip_nfc_status);
        chipRootStatus = findViewById(R.id.chip_root_status);
        tvCardCount = findViewById(R.id.tv_card_count);
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_wallet) {
                startActivity(new Intent(this, CardListActivity.class));
                return true;
            } else if (id == R.id.nav_scan) {
                startActivity(new Intent(this, CardScanActivity.class));
                return true;
            } else if (id == R.id.nav_emulate) {
                startActivity(new Intent(this, EmulationActivity.class));
                return true;
            } else if (id == R.id.nav_tools) {
                showToolsMenu();
                return true;
            }
            return false;
        });
    }

    private void showToolsMenu() {
        CharSequence[] options = {"Advanced Test", "Root Tools"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Tools")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        startActivity(new Intent(this, AdvancedTestActivity.class));
                    } else {
                        startActivity(new Intent(this, RootToolsActivity.class));
                    }
                })
                .show();
    }

    private void updateStatusChips() {
        // NFC status
        if (nfcAdapter == null) {
            chipNfcStatus.setText("NFC: N/A");
            chipNfcStatus.setChipBackgroundColorResource(R.color.color_error);
        } else if (!nfcAdapter.isEnabled()) {
            chipNfcStatus.setText("NFC: OFF");
            chipNfcStatus.setChipBackgroundColorResource(R.color.color_warning);
            chipNfcStatus.setOnClickListener(v -> {
                Toast.makeText(this, "Enable NFC in Settings", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(android.provider.Settings.ACTION_NFC_SETTINGS));
            });
        } else {
            chipNfcStatus.setText("NFC: ON");
            chipNfcStatus.setChipBackgroundColorResource(R.color.color_success);
        }

        // Root status
        new Thread(() -> {
            boolean rooted = RootUtils.isRooted();
            runOnUiThread(() -> {
                if (rooted) {
                    chipRootStatus.setText("ROOT: YES");
                    chipRootStatus.setChipBackgroundColorResource(R.color.color_success);
                } else {
                    chipRootStatus.setText("ROOT: NO");
                    chipRootStatus.setChipBackgroundColorResource(R.color.text_disabled);
                }
            });
        }).start();

        // Card count
        new Thread(() -> {
            int count = cardManager.getCardCount();
            runOnUiThread(() -> tvCardCount.setText(count + " card" + (count != 1 ? "s" : "") + " saved"));
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatusChips();
    }
}
