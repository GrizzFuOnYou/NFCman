# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keep class com.nfc.wallet.xposed.** { *; }
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**
