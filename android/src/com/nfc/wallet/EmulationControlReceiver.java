package com.nfc.wallet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class EmulationControlReceiver extends BroadcastReceiver {

    public static final String ACTION_START_EMULATION = "com.nfc.wallet.START_EMULATION";
    public static final String ACTION_STOP_EMULATION = "com.nfc.wallet.STOP_EMULATION";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        switch (action) {
            case ACTION_START_EMULATION:
                String cardFilename = intent.getStringExtra("card_filename");
                if (cardFilename != null) {
                    CardManager manager = new CardManager(context);
                    CardModel card = manager.loadCard(cardFilename);
                    if (card != null) {
                        NfcWalletHceService.setActiveCard(card);
                    }
                }
                break;
            case ACTION_STOP_EMULATION:
                NfcWalletHceService.setActiveCard(null);
                NfcWalletHceService.setCustomApduResponse(null);
                NfcWalletHceService.setActiveAid(null);
                break;
        }
    }
}
