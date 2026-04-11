package com.nfc.wallet.util;

import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for executing root shell commands via 'su'.
 * Provides NFC-specific root operations enabled by Magisk on Android 14.
 */
public class RootUtils {

    private static final String TAG = "RootUtils";

    public static class RootResult {
        public final boolean success;
        public final String output;
        public final String error;
        public final int exitCode;

        public RootResult(boolean success, String output, String error, int exitCode) {
            this.success = success;
            this.output = output;
            this.error = error;
            this.exitCode = exitCode;
        }

        @Override
        public String toString() {
            return "Exit=" + exitCode + " Output=" + output + (error.isEmpty() ? "" : " Error=" + error);
        }
    }

    /**
     * Checks if root access is available (su binary exists and is usable).
     */
    public static boolean isRootAvailable() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            p.waitFor();
            return line != null && (line.contains("uid=0") || line.contains("root"));
        } catch (Exception e) {
            Log.w(TAG, "Root not available: " + e.getMessage());
            return false;
        }
    }

    /**
     * Executes a single root command and returns the result.
     */
    public static RootResult exec(String command) {
        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();
        int exitCode = -1;

        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            BufferedReader outReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            String line;
            while ((line = outReader.readLine()) != null) {
                output.append(line).append("\n");
            }
            while ((line = errReader.readLine()) != null) {
                error.append(line).append("\n");
            }

            exitCode = process.waitFor();
            process.destroy();

        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Error executing root command: " + command, e);
            error.append(e.getMessage());
        }

        boolean success = exitCode == 0;
        Log.d(TAG, "Root command: " + command + " -> " + exitCode);
        return new RootResult(success, output.toString().trim(), error.toString().trim(), exitCode);
    }

    /**
     * Executes multiple commands in a single su session.
     */
    public static RootResult execMultiple(List<String> commands) {
        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();
        int exitCode = -1;

        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream stdin = new DataOutputStream(process.getOutputStream());
            BufferedReader outReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            for (String cmd : commands) {
                stdin.writeBytes(cmd + "\n");
            }
            stdin.writeBytes("exit\n");
            stdin.flush();
            stdin.close();

            String line;
            while ((line = outReader.readLine()) != null) {
                output.append(line).append("\n");
            }
            while ((line = errReader.readLine()) != null) {
                error.append(line).append("\n");
            }

            exitCode = process.waitFor();
            process.destroy();

        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Error executing root commands", e);
            error.append(e.getMessage());
        }

        return new RootResult(exitCode == 0, output.toString().trim(), error.toString().trim(), exitCode);
    }

    // --- NFC-specific root operations ---

    /**
     * Reads the NFC controller state via sysfs or NFC socket.
     */
    public static String getNfcControllerInfo() {
        StringBuilder info = new StringBuilder();

        // Check NFC controller device files
        RootResult r1 = exec("ls -la /dev/nfc* 2>/dev/null || echo 'No /dev/nfc devices'");
        info.append("NFC Devices:\n").append(r1.output).append("\n\n");

        // Check NFC via sysfs
        RootResult r2 = exec("ls /sys/class/nfc/ 2>/dev/null || echo 'No NFC sysfs'");
        info.append("NFC Sysfs:\n").append(r2.output).append("\n\n");

        // Check NFC kernel module
        RootResult r3 = exec("lsmod 2>/dev/null | grep -i nfc || echo 'No NFC modules'");
        info.append("NFC Modules:\n").append(r3.output).append("\n\n");

        // NFC via proc
        RootResult r4 = exec("cat /proc/nfc_info 2>/dev/null || echo 'No /proc/nfc_info'");
        info.append("NFC Proc Info:\n").append(r4.output).append("\n\n");

        // Check NFC HCI state
        RootResult r5 = exec("cat /sys/class/nfc/*/hci_state 2>/dev/null || echo 'HCI state not available'");
        info.append("NFC HCI State:\n").append(r5.output).append("\n\n");

        return info.toString();
    }

    /**
     * Reads the Secure Element (eSE) info and state.
     */
    public static String getSecureElementInfo() {
        StringBuilder info = new StringBuilder();

        // Check eSE device file
        RootResult r1 = exec("ls -la /dev/ese* /dev/se* 2>/dev/null || echo 'No SE device files'");
        info.append("SE Device Files:\n").append(r1.output).append("\n\n");

        // Check SE via sysfs
        RootResult r2 = exec("ls /sys/bus/platform/devices/ 2>/dev/null | grep -i ese || echo 'No eSE platform'");
        info.append("eSE Platform:\n").append(r2.output).append("\n\n");

        // Check SE NFC routing table (requires NFC daemon to be running)
        RootResult r3 = exec("nfc_nci_hal_dump 2>/dev/null || echo 'HAL dump not available'");
        info.append("NFC HAL dump:\n").append(r3.output).append("\n\n");

        // Check Pixel NFC service
        RootResult r4 = exec("dumpsys nfc 2>/dev/null | head -60 || echo 'NFC service dump unavailable'");
        info.append("NFC Service Info:\n").append(r4.output).append("\n\n");

        return info.toString();
    }

    /**
     * Gets the NFC routing table from the NFC service.
     */
    public static String getNfcRoutingTable() {
        RootResult r = exec("dumpsys nfc 2>/dev/null | grep -A 200 'Routing'");
        if (r.success && !r.output.isEmpty()) return r.output;
        return exec("dumpsys nfc 2>/dev/null").output;
    }

    /**
     * Dumps NFC-related logcat entries.
     */
    public static String getNfcLogcatDump(int lines) {
        RootResult r = exec("logcat -d -s NFC:* nfc:* '*:E' 2>/dev/null | tail -" + lines);
        return r.success ? r.output : "Logcat unavailable: " + r.error;
    }

    /**
     * Sets the preferred payment service via root (bypasses system UI requirement).
     */
    public static String setPreferredPaymentService(String componentName) {
        // Use settings command to set default payment application
        RootResult r = exec("settings put secure nfc_payment_default_component " + componentName);
        return r.success ? "Set default payment to: " + componentName : "Failed: " + r.error;
    }

    /**
     * Gets the current NFC preferred payment service.
     */
    public static String getPreferredPaymentService() {
        RootResult r = exec("settings get secure nfc_payment_default_component");
        return r.output;
    }

    /**
     * Reads all NFC-related settings.
     */
    public static String getAllNfcSettings() {
        StringBuilder sb = new StringBuilder();
        RootResult r1 = exec("settings list global 2>/dev/null | grep -i nfc");
        sb.append("Global NFC Settings:\n").append(r1.output).append("\n\n");
        RootResult r2 = exec("settings list secure 2>/dev/null | grep -i nfc");
        sb.append("Secure NFC Settings:\n").append(r2.output).append("\n\n");
        RootResult r3 = exec("settings list system 2>/dev/null | grep -i nfc");
        sb.append("System NFC Settings:\n").append(r3.output).append("\n\n");
        return sb.toString();
    }

    /**
     * Forcibly enables NFC via root (useful for testing when UI toggle is stuck).
     */
    public static RootResult forceEnableNfc() {
        return exec("svc nfc enable 2>/dev/null || am broadcast -a android.nfc.action.ADAPTER_STATE_CHANGED --ei android.nfc.extra.ADAPTER_STATE 3 2>/dev/null");
    }

    /**
     * Grants the NFC_WALLET app a specific permission via pm.
     */
    public static RootResult grantPermission(String permission) {
        return exec("pm grant com.nfc.wallet " + permission);
    }

    /**
     * Checks installed Magisk modules list.
     */
    public static String getMagiskModules() {
        RootResult r = exec("ls /data/adb/modules/ 2>/dev/null || ls /sbin/.magisk/modules/ 2>/dev/null || echo 'Magisk modules not found'");
        return r.output;
    }

    /**
     * Checks if LSPosed is installed and active.
     */
    public static String getLsposedStatus() {
        StringBuilder sb = new StringBuilder();
        RootResult r1 = exec("ls /data/adb/modules/ 2>/dev/null | grep -i lsposed || echo 'LSPosed module not found'");
        sb.append("LSPosed Module: ").append(r1.output).append("\n");
        RootResult r2 = exec("cat /data/adb/lspd/misc_info.ini 2>/dev/null || echo 'LSPosed config not found'");
        sb.append("LSPosed Config: ").append(r2.output).append("\n");
        RootResult r3 = exec("ls /data/misc/lspd/ 2>/dev/null || echo 'LSPosed data not found'");
        sb.append("LSPosed Data: ").append(r3.output).append("\n");
        return sb.toString();
    }

    /**
     * Gets the NFC UID via direct hardware access (Pixel 7 Pro NFC controller).
     */
    public static String readNfcUidDirect() {
        RootResult r = exec("cat /sys/class/nfc/nfc0/device/firmware_version 2>/dev/null");
        if (!r.success || r.output.isEmpty()) {
            r = exec("cat /proc/driver/nfc 2>/dev/null || echo 'Direct NFC UID read not available'");
        }
        return r.output;
    }

    /**
     * Lists all app AIDs registered in the NFC service.
     */
    public static String listRegisteredAids() {
        RootResult r = exec("dumpsys nfc 2>/dev/null | grep -A 2 'AID\\|aid'");
        return r.success ? r.output : "Unable to list AIDs: " + r.error;
    }

    /**
     * Returns device info useful for NFC debugging.
     */
    public static String getDeviceInfo() {
        StringBuilder sb = new StringBuilder();
        RootResult r;
        r = exec("getprop ro.product.model"); sb.append("Model: ").append(r.output).append("\n");
        r = exec("getprop ro.build.version.release"); sb.append("Android: ").append(r.output).append("\n");
        r = exec("getprop ro.build.version.sdk"); sb.append("SDK: ").append(r.output).append("\n");
        r = exec("getprop ro.build.fingerprint"); sb.append("Fingerprint: ").append(r.output).append("\n");
        r = exec("getprop ro.serialno 2>/dev/null || getprop ro.boot.serialno"); sb.append("Serial: ").append(r.output).append("\n");
        r = exec("getprop persist.nfc.se_enabled 2>/dev/null"); sb.append("SE Enabled: ").append(r.output).append("\n");
        r = exec("getprop ro.nfc.fw_filename 2>/dev/null"); sb.append("NFC FW: ").append(r.output).append("\n");
        return sb.toString();
    }

    /**
     * Wipes LSPosed module scope for a package (useful for re-configuration).
     */
    public static RootResult clearLsposedScope(String packageName) {
        return exec("rm -rf /data/misc/lspd/config/" + packageName + " 2>/dev/null");
    }
}
