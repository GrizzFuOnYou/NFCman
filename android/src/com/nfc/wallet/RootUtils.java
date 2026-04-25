package com.nfc.wallet;

import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class RootUtils {
    private static final String TAG = "RootUtils";

    public static boolean isRooted() {
        // Check multiple indicators
        if (checkSuBinary()) return true;
        if (checkRootApps()) return true;
        if (checkWritablePaths()) return true;
        return false;
    }

    private static boolean checkSuBinary() {
        String[] paths = {
                "/system/bin/su", "/system/xbin/su", "/sbin/su",
                "/data/local/xbin/su", "/data/local/bin/su", "/data/local/su",
                "/system/sd/xbin/su", "/system/bin/failsafe/su", "/su/bin/su"
        };
        for (String path : paths) {
            if (new File(path).exists()) return true;
        }
        return false;
    }

    private static boolean checkRootApps() {
        String[] packages = {
                "com.topjohnwu.magisk", "eu.chainfire.supersu",
                "com.koushikdutta.superuser", "com.noshufou.android.su"
        };
        for (String pkg : packages) {
            if (new File("/data/data/" + pkg).exists()) return true;
        }
        return false;
    }

    private static boolean checkWritablePaths() {
        return new File("/system").canWrite();
    }

    public static String runRootCommand(String cmd) {
        StringBuilder output = new StringBuilder();
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(cmd + "\n");
            os.writeBytes("exit\n");
            os.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            while ((line = errReader.readLine()) != null) {
                output.append("[ERR] ").append(line).append("\n");
            }
            process.waitFor();
        } catch (Exception e) {
            Log.e(TAG, "runRootCommand error: " + e.getMessage());
            return "[Error: " + e.getMessage() + "]";
        } finally {
            if (process != null) process.destroy();
        }
        return output.toString();
    }

    public static boolean isLSPosedAvailable() {
        // Check for LSPosed framework files
        String[] paths = {
                "/data/adb/lspd", "/system/framework/lspd.jar",
                "/data/misc/lspd", "/proc/net/unix"
        };
        for (String path : paths) {
            if (new File(path).exists()) return true;
        }
        // Try checking via root
        String result = runRootCommand("ls /data/adb/lspd 2>/dev/null && echo FOUND");
        return result.contains("FOUND");
    }

    public static boolean isMagiskAvailable() {
        String[] paths = {
                "/sbin/.magisk", "/data/adb/magisk",
                "/sbin/magisk", "/system/xbin/magisk"
        };
        for (String path : paths) {
            if (new File(path).exists()) return true;
        }
        String result = runRootCommand("magisk --version 2>/dev/null && echo FOUND");
        return result.contains("FOUND") || result.matches(".*\\d+\\.\\d+.*");
    }

    public static boolean spoofUidViaNci(byte[] uid) {
        if (uid == null || uid.length == 0) return false;
        StringBuilder uidHex = new StringBuilder();
        for (byte b : uid) uidHex.append(String.format("%02x", b));

        // Try various NCI debug interfaces
        String[] commands = {
                "echo " + uidHex + " > /sys/kernel/debug/nfc/uid",
                "nci_hal_ctrl uid " + uidHex,
                "nfc_debug uid " + uidHex,
                "echo " + uidHex + " > /proc/nfc/uid"
        };

        for (String cmd : commands) {
            String result = runRootCommand(cmd);
            if (!result.contains("[Error") && !result.contains("Permission denied")
                    && !result.contains("No such file")) {
                Log.d(TAG, "spoofUidViaNci succeeded with: " + cmd);
                return true;
            }
        }
        Log.w(TAG, "spoofUidViaNci: no working interface found");
        return false;
    }

    public static String readSecureElement() {
        String result = runRootCommand(
                "cat /proc/nfc/se_info 2>/dev/null || " +
                "spi_nfc se_list 2>/dev/null || " +
                "cat /dev/nfc_se 2>/dev/null | head -100 || " +
                "echo 'No SE interface found'"
        );
        return result.isEmpty() ? "No SE data available" : result;
    }

    public static String getNfcControllerInfo() {
        StringBuilder info = new StringBuilder();
        String[] sysFiles = {
                "/sys/class/nfc/", "/sys/bus/platform/drivers/nfc/",
                "/sys/kernel/debug/nfc/"
        };
        for (String path : sysFiles) {
            String result = runRootCommand("ls -la " + path + " 2>/dev/null && cat " + path + "* 2>/dev/null | head -50");
            if (!result.isEmpty() && !result.contains("[Error")) {
                info.append("=== ").append(path).append(" ===\n").append(result).append("\n");
            }
        }
        String propResult = runRootCommand("getprop | grep -i nfc 2>/dev/null");
        if (!propResult.isEmpty()) {
            info.append("=== NFC Properties ===\n").append(propResult).append("\n");
        }
        return info.length() == 0 ? "No NFC controller info found" : info.toString();
    }

    public static String getNciLogDump() {
        String result = runRootCommand(
                "ls /data/misc/nfc/ 2>/dev/null && " +
                "cat /data/misc/nfc/nfcsnoop_log 2>/dev/null | head -200 || " +
                "cat /data/misc/nfc/*.log 2>/dev/null | head -200 || " +
                "logcat -d -s NfcNci:V NfcAdaptation:V NfcHalSnoop:V 2>/dev/null | tail -200"
        );
        return result.isEmpty() ? "No NCI logs found" : result;
    }

    public static boolean setNfcPollingMode(String mode) {
        String cmd;
        switch (mode.toLowerCase()) {
            case "on":
                cmd = "svc nfc enable 2>/dev/null || nfc_hal_cmd polling_on";
                break;
            case "off":
                cmd = "svc nfc disable 2>/dev/null || nfc_hal_cmd polling_off";
                break;
            case "p2p":
                cmd = "nfc_hal_cmd p2p_mode 2>/dev/null";
                break;
            default:
                cmd = "svc nfc enable 2>/dev/null";
        }
        String result = runRootCommand(cmd);
        return !result.contains("[Error");
    }

    public static boolean writeToNfcDevice(byte[] data) {
        if (data == null || data.length == 0) return false;
        StringBuilder hexData = new StringBuilder();
        for (byte b : data) hexData.append(String.format("\\x%02x", b));
        String cmd = "printf '" + hexData + "' > /dev/nfc0 2>/dev/null || " +
                     "printf '" + hexData + "' > /dev/bcm2079x 2>/dev/null";
        String result = runRootCommand(cmd);
        return !result.contains("[Error") && !result.contains("Permission denied");
    }
}
