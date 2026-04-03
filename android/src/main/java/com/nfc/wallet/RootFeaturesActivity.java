package com.nfc.wallet;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.nfc.wallet.util.RootUtils;

/**
 * Root / Magisk / LSPosed NFC features.
 * Requires root access. Uses shell commands to:
 *  - Read NFC controller info / firmware version
 *  - Query Secure Element status
 *  - Dump NFC routing table
 *  - Read all NFC-related system settings
 *  - Force enable/disable NFC
 *  - List registered AIDs in NFC service
 *  - Read LSPosed and Magisk module status
 *  - Set preferred payment service via root
 *  - Dump NFC logcat
 */
public class RootFeaturesActivity extends Activity {

    private static final String TAG = "RootFeaturesActivity";

    private TextView rootStatusText;
    private TextView outputText;
    private ScrollView outputScroll;
    private EditText commandInput, packageInput;

    private Button checkRootBtn, nfcInfoBtn, seInfoBtn, routingTableBtn,
            nfcSettingsBtn, forceEnableNfcBtn, listAidsBtn, magiskModulesBtn,
            lsposedStatusBtn, deviceInfoBtn, logcatBtn, setPaymentBtn,
            execCommandBtn, backBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_root_features);

        initViews();
        checkRootAndUpdateStatus();
    }

    private void initViews() {
        rootStatusText  = findViewById(R.id.root_status);
        outputText      = findViewById(R.id.root_output);
        outputScroll    = findViewById(R.id.root_output_scroll);
        commandInput    = findViewById(R.id.input_root_command);
        packageInput    = findViewById(R.id.input_package_name);

        checkRootBtn      = findViewById(R.id.btn_check_root);
        nfcInfoBtn        = findViewById(R.id.btn_nfc_info);
        seInfoBtn         = findViewById(R.id.btn_se_info);
        routingTableBtn   = findViewById(R.id.btn_routing_table);
        nfcSettingsBtn    = findViewById(R.id.btn_nfc_settings);
        forceEnableNfcBtn = findViewById(R.id.btn_force_nfc);
        listAidsBtn       = findViewById(R.id.btn_list_aids);
        magiskModulesBtn  = findViewById(R.id.btn_magisk_modules);
        lsposedStatusBtn  = findViewById(R.id.btn_lsposed_status);
        deviceInfoBtn     = findViewById(R.id.btn_device_info);
        logcatBtn         = findViewById(R.id.btn_nfc_logcat);
        setPaymentBtn     = findViewById(R.id.btn_set_payment);
        execCommandBtn    = findViewById(R.id.btn_exec_command);
        backBtn           = findViewById(R.id.btn_back_root);

        if (checkRootBtn != null)    checkRootBtn.setOnClickListener(v -> checkRootAndUpdateStatus());
        if (nfcInfoBtn != null)      nfcInfoBtn.setOnClickListener(v -> runAsync("NFC Controller Info", RootUtils::getNfcControllerInfo));
        if (seInfoBtn != null)       seInfoBtn.setOnClickListener(v -> runAsync("Secure Element Info", RootUtils::getSecureElementInfo));
        if (routingTableBtn != null) routingTableBtn.setOnClickListener(v -> runAsync("NFC Routing Table", RootUtils::getNfcRoutingTable));
        if (nfcSettingsBtn != null)  nfcSettingsBtn.setOnClickListener(v -> runAsync("NFC Settings", RootUtils::getAllNfcSettings));
        if (magiskModulesBtn != null) magiskModulesBtn.setOnClickListener(v -> runAsync("Magisk Modules", RootUtils::getMagiskModules));
        if (lsposedStatusBtn != null) lsposedStatusBtn.setOnClickListener(v -> runAsync("LSPosed Status", RootUtils::getLsposedStatus));
        if (deviceInfoBtn != null)   deviceInfoBtn.setOnClickListener(v -> runAsync("Device Info", RootUtils::getDeviceInfo));
        if (listAidsBtn != null)     listAidsBtn.setOnClickListener(v -> runAsync("Registered AIDs", RootUtils::listRegisteredAids));
        if (backBtn != null)         backBtn.setOnClickListener(v -> finish());

        if (forceEnableNfcBtn != null) {
            forceEnableNfcBtn.setOnClickListener(v -> {
                new Thread(() -> {
                    RootUtils.RootResult result = RootUtils.forceEnableNfc();
                    showOutput("Force Enable NFC", result.toString());
                }).start();
            });
        }

        if (logcatBtn != null) {
            logcatBtn.setOnClickListener(v -> {
                new Thread(() -> {
                    String log = RootUtils.getNfcLogcatDump(100);
                    showOutput("NFC Logcat (last 100 lines)", log);
                }).start();
            });
        }

        if (setPaymentBtn != null) {
            setPaymentBtn.setOnClickListener(v -> {
                String pkg = packageInput != null ? packageInput.getText().toString().trim() : "";
                if (pkg.isEmpty()) pkg = getPackageName() + "/.NfcEmulatorService";
                final String component = pkg;
                new Thread(() -> {
                    String result = RootUtils.setPreferredPaymentService(component);
                    showOutput("Set Payment Service", result);
                }).start();
            });
        }

        if (execCommandBtn != null) {
            execCommandBtn.setOnClickListener(v -> {
                String cmd = commandInput != null ? commandInput.getText().toString().trim() : "";
                if (cmd.isEmpty()) {
                    Toast.makeText(this, "Enter a command", Toast.LENGTH_SHORT).show();
                    return;
                }
                final String finalCmd = cmd;
                new Thread(() -> {
                    RootUtils.RootResult result = RootUtils.exec(finalCmd);
                    showOutput("$ " + finalCmd, result.toString());
                }).start();
            });
        }
    }

    private void checkRootAndUpdateStatus() {
        if (rootStatusText != null) rootStatusText.setText("Checking root access...");
        new Thread(() -> {
            boolean rooted = RootUtils.isRootAvailable();
            String deviceInfo = rooted ? RootUtils.getDeviceInfo() : "";
            runOnUiThread(() -> {
                if (rootStatusText != null) {
                    rootStatusText.setText(rooted ? "✓ Root access available (Magisk)" : "✗ Root not available");
                    rootStatusText.setTextColor(rooted ? 0xFF69F0AE : 0xFFFF5252);
                }
                setButtonsEnabled(rooted);
                if (rooted && !deviceInfo.isEmpty()) {
                    showOutput("Device Info", deviceInfo);
                }
            });
        }).start();
    }

    private void setButtonsEnabled(boolean enabled) {
        Button[] rootButtons = {nfcInfoBtn, seInfoBtn, routingTableBtn, nfcSettingsBtn,
                forceEnableNfcBtn, listAidsBtn, magiskModulesBtn, lsposedStatusBtn,
                logcatBtn, setPaymentBtn, execCommandBtn};
        for (Button b : rootButtons) {
            if (b != null) b.setEnabled(enabled);
        }
    }

    private interface StringSupplier { String get(); }

    private void runAsync(String title, StringSupplier supplier) {
        showOutput(title, "Running...");
        new Thread(() -> {
            String result = supplier.get();
            showOutput(title, result);
        }).start();
    }

    private void showOutput(String title, String content) {
        runOnUiThread(() -> {
            if (outputText != null) {
                outputText.setText("=== " + title + " ===\n\n" + content);
            }
            if (outputScroll != null) {
                outputScroll.post(() -> outputScroll.scrollTo(0, 0));
            }
        });
    }
}
