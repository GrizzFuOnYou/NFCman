package com.nfc.wallet;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

public class RootToolsActivity extends AppCompatActivity {

    private TextView tvRootStatus, tvMagiskStatus, tvLsposedStatus;
    private TextView tvNfcControllerInfo, tvNciLog, tvSeInfo, tvMagiskModules;
    private TextInputEditText etUidClone;
    private Button btnWriteUid, btnReadNfcInfo, btnReadNci, btnReadSe, btnReadMagisk;
    private ScrollView scrollContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_root_tools);

        bindViews();
        setupButtons();
        checkRootStatus();
    }

    private void bindViews() {
        tvRootStatus = findViewById(R.id.tv_root_status);
        tvMagiskStatus = findViewById(R.id.tv_magisk_status);
        tvLsposedStatus = findViewById(R.id.tv_lsposed_status);
        tvNfcControllerInfo = findViewById(R.id.tv_nfc_controller_info);
        tvNciLog = findViewById(R.id.tv_nci_log);
        tvSeInfo = findViewById(R.id.tv_se_info);
        tvMagiskModules = findViewById(R.id.tv_magisk_modules);
        etUidClone = findViewById(R.id.et_uid_clone);
        btnWriteUid = findViewById(R.id.btn_write_uid);
        btnReadNfcInfo = findViewById(R.id.btn_read_nfc_info);
        btnReadNci = findViewById(R.id.btn_read_nci);
        btnReadSe = findViewById(R.id.btn_read_se);
        btnReadMagisk = findViewById(R.id.btn_read_magisk);
        scrollContent = findViewById(R.id.scroll_content);
    }

    private void setupButtons() {
        if (btnWriteUid != null) {
            btnWriteUid.setOnClickListener(v -> writeUid());
        }
        if (btnReadNfcInfo != null) {
            btnReadNfcInfo.setOnClickListener(v -> readNfcControllerInfo());
        }
        if (btnReadNci != null) {
            btnReadNci.setOnClickListener(v -> readNciLog());
        }
        if (btnReadSe != null) {
            btnReadSe.setOnClickListener(v -> readSeInfo());
        }
        if (btnReadMagisk != null) {
            btnReadMagisk.setOnClickListener(v -> readMagiskModules());
        }
    }

    private void checkRootStatus() {
        new Thread(() -> {
            boolean rooted = RootUtils.isRooted();
            boolean magisk = RootUtils.isMagiskAvailable();
            boolean lsposed = RootUtils.isLSPosedAvailable();

            runOnUiThread(() -> {
                if (tvRootStatus != null) {
                    tvRootStatus.setText("Root: " + (rooted ? "✓ Available" : "✗ Not found"));
                    tvRootStatus.setTextColor(getResources().getColor(
                            rooted ? R.color.color_success : R.color.color_error, null));
                }
                if (tvMagiskStatus != null) {
                    tvMagiskStatus.setText("Magisk: " + (magisk ? "✓ Available" : "✗ Not found"));
                    tvMagiskStatus.setTextColor(getResources().getColor(
                            magisk ? R.color.color_success : R.color.text_secondary, null));
                }
                if (tvLsposedStatus != null) {
                    tvLsposedStatus.setText("LSPosed: " + (lsposed ? "✓ Available" : "✗ Not found"));
                    tvLsposedStatus.setTextColor(getResources().getColor(
                            lsposed ? R.color.color_success : R.color.text_secondary, null));
                }

                if (!rooted) {
                    disableRootButtons();
                }
            });
        }).start();
    }

    private void disableRootButtons() {
        Button[] rootButtons = {btnWriteUid, btnReadNfcInfo, btnReadNci, btnReadSe, btnReadMagisk};
        for (Button btn : rootButtons) {
            if (btn != null) {
                btn.setAlpha(0.4f);
                btn.setEnabled(false);
            }
        }
        Toast.makeText(this, "Root not available — some features disabled", Toast.LENGTH_LONG).show();
    }

    private void writeUid() {
        String uid = etUidClone != null && etUidClone.getText() != null
                ? etUidClone.getText().toString().trim() : "";
        if (uid.isEmpty()) {
            Toast.makeText(this, "Enter a UID to write", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            byte[] uidBytes = CardDetector.hexToBytes(uid);
            boolean ok = RootUtils.spoofUidViaNci(uidBytes);
            runOnUiThread(() -> {
                String msg = ok ? "UID write: SUCCESS (" + uid + ")"
                        : "UID write: FAILED (unsupported or no root)";
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                if (tvNciLog != null) tvNciLog.append(msg + "\n");
            });
        }).start();
    }

    private void readNfcControllerInfo() {
        if (tvNfcControllerInfo != null) tvNfcControllerInfo.setText("Loading...");
        new Thread(() -> {
            String info = RootUtils.getNfcControllerInfo();
            runOnUiThread(() -> {
                if (tvNfcControllerInfo != null) tvNfcControllerInfo.setText(info);
            });
        }).start();
    }

    private void readNciLog() {
        if (tvNciLog != null) tvNciLog.setText("Loading NCI logs...");
        new Thread(() -> {
            String log = RootUtils.getNciLogDump();
            runOnUiThread(() -> {
                if (tvNciLog != null) {
                    tvNciLog.setText(log);
                    if (scrollContent != null)
                        scrollContent.post(() -> scrollContent.fullScroll(View.FOCUS_DOWN));
                }
            });
        }).start();
    }

    private void readSeInfo() {
        if (tvSeInfo != null) tvSeInfo.setText("Loading SE info...");
        new Thread(() -> {
            String info = RootUtils.readSecureElement();
            runOnUiThread(() -> {
                if (tvSeInfo != null) tvSeInfo.setText(info);
            });
        }).start();
    }

    private void readMagiskModules() {
        if (tvMagiskModules != null) tvMagiskModules.setText("Loading...");
        new Thread(() -> {
            String result = RootUtils.runRootCommand(
                    "ls /data/adb/modules/ 2>/dev/null | while read mod; do " +
                    "echo \"$mod: $(cat /data/adb/modules/$mod/module.prop 2>/dev/null | grep -E '^(name|version|description)=' | head -3)\"; " +
                    "done"
            );
            if (result.isEmpty() || result.contains("[Error")) {
                result = "No Magisk modules found or root unavailable";
            }
            final String finalResult = result;
            runOnUiThread(() -> {
                if (tvMagiskModules != null) tvMagiskModules.setText(finalResult);
            });
        }).start();
    }
}
