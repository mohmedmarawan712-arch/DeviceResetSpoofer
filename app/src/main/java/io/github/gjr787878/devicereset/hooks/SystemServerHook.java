package io.github.gjr787878.devicereset.hooks;

import android.os.Binder;
import android.os.Bundle;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.gjr787878.devicereset.xposed.Identity;

/**
 * System-Server Stealth Interceptor (نظام المحاكاة الخفي عبر النظام).
 *
 * Runs inside system_server and system service providers.
 * Intercepts Binder IPC calls from target applications (checking Binder.getCallingUid()
 * or callingPackage parameter) and serves spoofed device identifiers directly from the
 * framework, WITHOUT requiring target applications to be present in LSPosed scope.
 */
public class SystemServerHook {

    private static final String DRS_SYSTEM_DIR = "/data/system/devicereset";
    private static final Map<Integer, String> UID_PKG_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, CacheEntry> IDENTITY_CACHE = new ConcurrentHashMap<>();

    private static class CacheEntry {
        final long lastModified;
        final Identity identity;

        CacheEntry(long lastModified, Identity identity) {
            this.lastModified = lastModified;
            this.identity = identity;
        }
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log("[DeviceReset-System] Installing Stealth System-Server Hooks in: " + lpparam.packageName);

        hookSettingsProvider(lpparam);
        hookTelephony(lpparam);
        hookWifi(lpparam);
        hookBluetooth(lpparam);
    }

    // ================= 1. SettingsProvider (Android ID / SSAID) =================

    private static void hookSettingsProvider(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> spClass = XposedHelpers.findClassIfExists("com.android.providers.settings.SettingsProvider", lpparam.classLoader);
            if (spClass == null) {
                return;
            }

            XC_MethodHook callHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        String method = null;
                        String request = null;

                        if (param.args.length >= 3) {
                            if (param.args[0] instanceof String && param.args[1] instanceof String) {
                                method = (String) param.args[0];
                                request = (String) param.args[1];
                            } else if (param.args[1] instanceof String && param.args[2] instanceof String) {
                                method = (String) param.args[1];
                                request = (String) param.args[2];
                            }
                        }

                        if (method != null && method.contains("GET_") && "android_id".equals(request)) {
                            int callingUid = Binder.getCallingUid();
                            if (callingUid < 10000) return; // Skip system UIDs

                            String callingPkg = getPackageNameForUid(lpparam, callingUid);
                            if (callingPkg == null) return;

                            Identity id = getIdentity(callingPkg);
                            if (id != null && id.androidId != null && id.isItemEnabled(Identity.KEY_ANDROID_ID)) {
                                Bundle b = (Bundle) param.getResult();
                                if (b == null) b = new Bundle();
                                b.putString("value", id.androidId);
                                param.setResult(b);
                                XposedBridge.log("[DeviceReset-System] Spoofed Android ID for UID " + callingUid + " (" + callingPkg + ") -> " + id.androidId);
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log("[DeviceReset-System] SettingsProvider hook error: " + t.getMessage());
                    }
                }
            };

            // Hook available overloads of call
            for (java.lang.reflect.Method m : spClass.getDeclaredMethods()) {
                if ("call".equals(m.getName())) {
                    XposedBridge.hookMethod(m, callHook);
                    XposedBridge.log("[DeviceReset-System] Hooked SettingsProvider.call: " + m);
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("[DeviceReset-System] SettingsProvider install failed: " + t.getMessage());
        }
    }

    // ================= 2. Telephony (IMEI / IMSI / ICCID / Carrier) =================

    private static void hookTelephony(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] telephonyClasses = {
                "com.android.internal.telephony.ITelephony$Stub",
                "com.android.phone.PhoneInterfaceManager",
                "com.android.internal.telephony.PhoneInterfaceManager"
        };

        for (String clsName : telephonyClasses) {
            Class<?> cls = XposedHelpers.findClassIfExists(clsName, lpparam.classLoader);
            if (cls == null) continue;

            hookTelephonyMethod(cls, "getImeiForSubscriber", (id) -> id.imei);
            hookTelephonyMethod(cls, "getDeviceIdWithFeature", (id) -> id.imei);
            hookTelephonyMethod(cls, "getDeviceId", (id) -> id.imei);
            hookTelephonyMethod(cls, "getSubscriberId", (id) -> id.imsi);
            hookTelephonyMethod(cls, "getSubscriberIdWithFeature", (id) -> id.imsi);
            hookTelephonyMethod(cls, "getSimSerialNumber", (id) -> id.iccid);
            hookTelephonyMethod(cls, "getLine1NumberForDisplay", (id) -> "13800138000");
        }
    }

    private interface ValueExtractor {
        String extract(Identity id);
    }

    private static void hookTelephonyMethod(Class<?> cls, String methodName, ValueExtractor extractor) {
        for (java.lang.reflect.Method m : cls.getDeclaredMethods()) {
            if (methodName.equals(m.getName())) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            String callingPkg = null;
                            for (Object arg : param.args) {
                                if (arg instanceof String) {
                                    String str = (String) arg;
                                    if (str.contains(".") && !str.startsWith("android")) {
                                        callingPkg = str;
                                        break;
                                    }
                                }
                            }

                            if (callingPkg == null) {
                                int callingUid = Binder.getCallingUid();
                                if (callingUid >= 10000) {
                                    callingPkg = UID_PKG_CACHE.get(callingUid);
                                }
                            }

                            if (callingPkg != null) {
                                Identity id = getIdentity(callingPkg);
                                if (id != null && id.isItemEnabled(Identity.KEY_TELEPHONY)) {
                                    String val = extractor.extract(id);
                                    if (val != null && !val.isEmpty()) {
                                        param.setResult(val);
                                        XposedBridge.log("[DeviceReset-System] Telephony." + methodName + " spoofed for " + callingPkg + " -> " + val);
                                    }
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                });
            }
        }
    }

    // ================= 3. Wi-Fi (MAC / SSID / BSSID) =================

    private static void hookWifi(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> wifiService = XposedHelpers.findClassIfExists("com.android.server.wifi.WifiServiceImpl", lpparam.classLoader);
        if (wifiService == null) return;

        for (java.lang.reflect.Method m : wifiService.getDeclaredMethods()) {
            if ("getConnectionInfo".equals(m.getName())) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Object wifiInfo = param.getResult();
                            if (wifiInfo == null) return;

                            String callingPkg = null;
                            if (param.args.length > 0 && param.args[0] instanceof String) {
                                callingPkg = (String) param.args[0];
                            } else {
                                int uid = Binder.getCallingUid();
                                if (uid >= 10000) callingPkg = UID_PKG_CACHE.get(uid);
                            }

                            if (callingPkg != null) {
                                Identity id = getIdentity(callingPkg);
                                if (id != null && id.macAddress != null && id.isItemEnabled(Identity.KEY_WIFI)) {
                                    try {
                                        XposedHelpers.setObjectField(wifiInfo, "mMacAddress", id.macAddress);
                                    } catch (Throwable ignored) {}
                                    try {
                                        XposedHelpers.setObjectField(wifiInfo, "mBSSID", id.macAddress);
                                    } catch (Throwable ignored) {}
                                    XposedBridge.log("[DeviceReset-System] WifiInfo spoofed for " + callingPkg + " -> MAC=" + id.macAddress);
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                });
            }
        }
    }

    // ================= 4. Bluetooth (MAC Address) =================

    private static void hookBluetooth(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> btService = XposedHelpers.findClassIfExists("com.android.server.BluetoothManagerService", lpparam.classLoader);
        if (btService == null) return;

        for (java.lang.reflect.Method m : btService.getDeclaredMethods()) {
            if ("getAddress".equals(m.getName())) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            int uid = Binder.getCallingUid();
                            if (uid < 10000) return;
                            String pkg = UID_PKG_CACHE.get(uid);
                            if (pkg != null) {
                                Identity id = getIdentity(pkg);
                                if (id != null && id.bluetoothMac != null && id.isItemEnabled(Identity.KEY_BLUETOOTH)) {
                                    param.setResult(id.bluetoothMac);
                                    XposedBridge.log("[DeviceReset-System] Bluetooth getAddress spoofed for " + pkg + " -> " + id.bluetoothMac);
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                });
            }
        }
    }

    // ================= Helper: Resolve UID to Package Name =================

    private static String getPackageNameForUid(XC_LoadPackage.LoadPackageParam lpparam, int uid) {
        String cached = UID_PKG_CACHE.get(uid);
        if (cached != null) return cached;

        try {
            Class<?> appGlobals = XposedHelpers.findClass("android.app.AppGlobals", lpparam.classLoader);
            Object ipm = XposedHelpers.callStaticMethod(appGlobals, "getPackageManager");
            if (ipm != null) {
                String pkg = (String) XposedHelpers.callMethod(ipm, "getNameForUid", uid);
                if (pkg != null) {
                    UID_PKG_CACHE.put(uid, pkg);
                    return pkg;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // ================= Helper: Read Identity from /data/system/devicereset/ =================

    private static Identity getIdentity(String packageName) {
        if (packageName == null || packageName.isEmpty()) return null;

        File f = new File(DRS_SYSTEM_DIR, packageName + ".json");
        if (!f.exists()) return null;

        long lm = f.lastModified();
        CacheEntry entry = IDENTITY_CACHE.get(packageName);
        if (entry != null && entry.lastModified == lm) {
            return entry.identity;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            Identity id = Identity.fromJson(sb.toString());
            if (id != null) {
                IDENTITY_CACHE.put(packageName, new CacheEntry(lm, id));
                return id;
            }
        } catch (Throwable t) {
            XposedBridge.log("[DeviceReset-System] Failed to read identity for " + packageName + ": " + t.getMessage());
        }
        return null;
    }
}
