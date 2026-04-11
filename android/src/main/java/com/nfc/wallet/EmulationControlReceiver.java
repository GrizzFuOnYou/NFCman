package com.nfc.wallet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Broadcast receiver for controlling the NFC emulator service.
 * Handles: STOP_EMULATION, START_EMULATION, UPDATE_EMULATION
 */
public class EmulationControlReceiver extends BroadcastReceiver {

    private static final String TAG = "EmulationControlReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();
        Log.d(TAG, "Received broadcast: " + action);

        switch (action) {
            case "com.nfc.wallet.STOP_EMULATION":
                Intent stopService = new Intent(context, NfcEmulatorService.class);
                context.stopService(stopService);
                Log.d(TAG, "Emulation stopped");
                break;

            case "com.nfc.wallet.START_EMULATION":
                String cardFile = intent.getStringExtra(NfcEmulatorService.EXTRA_CARD_FILE);
                if (cardFile != null && !cardFile.isEmpty()) {
                    Intent startService = new Intent(context, NfcEmulatorService.class);
                    startService.putExtra(NfcEmulatorService.EXTRA_CARD_FILE, cardFile);
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(startService);
                    } else {
                        context.startService(startService);
                    }
                    Log.d(TAG, "Emulation started for: " + cardFile);
                }
                break;

            case "com.nfc.wallet.UPDATE_EMULATION":
                String newCardFile = intent.getStringExtra(NfcEmulatorService.EXTRA_CARD_FILE);
                if (newCardFile != null && !newCardFile.isEmpty()) {
                    Intent updateService = new Intent(context, NfcEmulatorService.class);
                    updateService.putExtra(NfcEmulatorService.EXTRA_CARD_FILE, newCardFile);
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(updateService);
                    } else {
                        context.startService(updateService);
                    }
                    Log.d(TAG, "Emulation updated to: " + newCardFile);
                }
                break;

            default:
                Log.w(TAG, "Unknown action: " + action);
        }
    }
}
