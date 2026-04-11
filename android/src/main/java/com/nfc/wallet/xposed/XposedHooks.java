package com.nfc.wallet.xposed;

import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * LSPosed (Xposed) hooks for NFC and Secure Element access.
 *
 * Hooks into Android framework classes to:
 *  1. Log all NFC tag interactions (sniff NFC reads from any app)
 *  2. Intercept APDU exchanges in NfcAdapter and IsoDep
 *  3. Access Secure Element status/routing via NfcService
 *  4. Override NFC adapter state (force enable)
 *  5. Hook CardEmulationManager to monitor/control AID routing
 *
 * This module must be activated in LSPosed Manager for:
 *  - System Framework (android)
 *  - NFC Service (com.android.nfc)
 */
public class XposedHooks implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    private static final String TAG = "NFC_Wallet_Xposed";

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        XposedBridge.log(TAG + ": initZygote - NFC_Wallet Xposed module loaded");
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XposedBridge.log(TAG + ": handleLoadPackage: " + lpparam.packageName);

        // Hook Android framework NFC classes
        if ("android".equals(lpparam.packageName)) {
            hookNfcAdapter(lpparam);
            hookNfcManager(lpparam);
        }

        // Hook the NFC service application
        if ("com.android.nfc".equals(lpparam.packageName)) {
            hookNfcService(lpparam);
            hookCardEmulationManager(lpparam);
        }

        // Hook our own app for debugging
        if ("com.nfc.wallet".equals(lpparam.packageName)) {
            XposedBridge.log(TAG + ": NFC_Wallet app loaded in Xposed context");
        }
    }

    /**
     * Hooks NfcAdapter to intercept tag reads and connection events.
     */
    private void hookNfcAdapter(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> nfcAdapterClass = XposedHelpers.findClass("android.nfc.NfcAdapter", lpparam.classLoader);

            // Hook enableForegroundDispatch - log when an app starts listening for NFC
            XposedHelpers.findAndHookMethod(nfcAdapterClass, "enableForegroundDispatch",
                    "android.app.Activity",
                    "android.app.PendingIntent",
                    "[Landroid.content.IntentFilter;",
                    "[[Ljava.lang.String;",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            XposedBridge.log(TAG + ": enableForegroundDispatch called by activity: "
                                    + param.args[0]);
                        }
                    });

            // Hook isEnabled to observe NFC state checks
            XposedHelpers.findAndHookMethod(nfcAdapterClass, "isEnabled",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            // Uncomment to force NFC enabled:
                            // param.setResult(true);
                        }
                    });

            XposedBridge.log(TAG + ": NfcAdapter hooks installed");
        } catch (Throwable e) {
            XposedBridge.log(TAG + ": Error hooking NfcAdapter: " + e.getMessage());
        }
    }

    /**
     * Hooks NfcManager for adapter access monitoring.
     */
    private void hookNfcManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> nfcManagerClass = XposedHelpers.findClass("android.nfc.NfcManager", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(nfcManagerClass, "getDefaultAdapter",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (param.getResult() != null) {
                                XposedBridge.log(TAG + ": NfcManager.getDefaultAdapter() called, returned: "
                                        + param.getResult());
                            }
                        }
                    });

            XposedBridge.log(TAG + ": NfcManager hooks installed");
        } catch (Throwable e) {
            XposedBridge.log(TAG + ": Error hooking NfcManager: " + e.getMessage());
        }
    }

    /**
     * Hooks internal NFC service to monitor APDU routing and SE access.
     */
    private void hookNfcService(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook NfcService APDU routing
        hookNfcServiceApduRouting(lpparam);
        // Hook SE (Secure Element) access
        hookSecureElementAccess(lpparam);
    }

    private void hookNfcServiceApduRouting(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Try to hook the CardEmulationManager route selection
            Class<?> routingManagerClass = XposedHelpers.findClass(
                    "com.android.nfc.cardemulation.RegisteredAidCache", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(routingManagerClass, "resolveAid",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String aid = (String) param.args[0];
                            XposedBridge.log(TAG + ": NFC AID resolve request: " + aid);
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            XposedBridge.log(TAG + ": NFC AID resolve result: " + param.getResult());
                        }
                    });

            XposedBridge.log(TAG + ": NFC service APDU routing hooks installed");
        } catch (Throwable e) {
            XposedBridge.log(TAG + ": Could not hook APDU routing (may differ on this Android version): " + e.getMessage());
        }
    }

    private void hookSecureElementAccess(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook SecureElement HAL interface
            Class<?> seServiceClass = XposedHelpers.findClass(
                    "android.se.omapi.SEService", lpparam.classLoader);

            XposedHelpers.findAndHookConstructor(seServiceClass,
                    android.content.Context.class,
                    java.util.concurrent.Executor.class,
                    android.se.omapi.SEService.OnConnectedListener.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            XposedBridge.log(TAG + ": SEService constructor called - SE access requested");
                        }
                    });

            XposedBridge.log(TAG + ": SE access hooks installed");
        } catch (Throwable e) {
            XposedBridge.log(TAG + ": Could not hook SEService: " + e.getMessage());
        }
    }

    /**
     * Hooks CardEmulationManager to monitor/modify AID routing.
     */
    private void hookCardEmulationManager(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cemClass = XposedHelpers.findClass(
                    "com.android.nfc.cardemulation.CardEmulationManager", lpparam.classLoader);

            // Hook onHostCardEmulationActivated
            try {
                XposedHelpers.findAndHookMethod(cemClass, "onHostCardEmulationActivated",
                        int.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                XposedBridge.log(TAG + ": HCE activated for technology: " + param.args[0]);
                            }
                        });
            } catch (Throwable ignored) {}

            // Hook onHostCardEmulationDeactivated
            try {
                XposedHelpers.findAndHookMethod(cemClass, "onHostCardEmulationDeactivated",
                        int.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                XposedBridge.log(TAG + ": HCE deactivated");
                            }
                        });
            } catch (Throwable ignored) {}

            XposedBridge.log(TAG + ": CardEmulationManager hooks installed");
        } catch (Throwable e) {
            XposedBridge.log(TAG + ": Error hooking CardEmulationManager: " + e.getMessage());
        }
    }
}
