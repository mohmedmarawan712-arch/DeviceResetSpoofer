package io.github.gjr787878.devicereset.xposed;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 设备身份数据模型，包含所有需要伪装的设备识别码，
 * 支持针对每个特定项目/类别的细粒度启用与关闭。
 */
public class Identity {

    // 细粒度伪装类别常量
    public static final String KEY_ANDROID_ID = "android_id";
    public static final String KEY_AD_ID = "ad_id";
    public static final String KEY_TELEPHONY = "telephony";
    public static final String KEY_CARRIER = "carrier";
    public static final String KEY_BUILD = "build";
    public static final String KEY_WIFI = "wifi_mac";
    public static final String KEY_BLUETOOTH = "bluetooth_mac";
    public static final String KEY_DRM = "drm_id";
    public static final String KEY_GSF = "gsf_id";
    public static final String KEY_SENSOR = "sensor";
    public static final String KEY_SETTINGS = "settings";

    public static final String[] ALL_KEYS = {
            KEY_ANDROID_ID, KEY_AD_ID, KEY_TELEPHONY, KEY_CARRIER,
            KEY_BUILD, KEY_WIFI, KEY_BLUETOOTH, KEY_DRM,
            KEY_GSF, KEY_SENSOR, KEY_SETTINGS
    };

    private Set<String> enabledItems = new HashSet<>();

    public String androidId;
    public String advertisingId;
    public String imei;
    public String meid;
    public String serial;
    public String macAddress;
    public String gsfId;
    public String appSetId;

    // Build 信息
    public String brand;
    public String model;
    public String manufacturer;
    public String device;
    public String product;
    public String hardware;
    public String fingerprint;
    public String bootloader;
    public String radioVersion;
    public String buildId;
    public String buildTime;
    // Build 扩展字段
    public String buildTags;
    public String buildType;
    public String buildUser;
    public String buildHost;
    public String buildDisplay;

    // 硬件级指纹
    public String drmId;        // Widevine DRM 设备唯一 ID
    public String bluetoothMac; // 蓝牙 MAC

    // 传感器指纹
    public String sensorVendor;
    public String sensorName;
    public String sensorVersion;

    // 网络信息
    public String networkOperator;
    public String networkOperatorName;
    public String simOperator;
    public String simOperatorName;
    public String simCountryIso;
    public String networkCountryIso;
    // IMSI / SIM卡序列号(ICCID)
    public String imsi;
    public String iccid;

    public Identity() {
        enableAll();
    }

    public void enableAll() {
        Collections.addAll(enabledItems, ALL_KEYS);
    }

    public void disableAll() {
        enabledItems.clear();
    }

    public boolean isItemEnabled(String key) {
        if (enabledItems == null) return true;
        return enabledItems.contains(key);
    }

    public void setItemEnabled(String key, boolean enabled) {
        if (enabledItems == null) enabledItems = new HashSet<>();
        if (enabled) {
            enabledItems.add(key);
        } else {
            enabledItems.remove(key);
        }
    }

    public Set<String> getEnabledItems() {
        return Collections.unmodifiableSet(enabledItems != null ? enabledItems : Collections.emptySet());
    }

    public int getEnabledCount() {
        return enabledItems != null ? enabledItems.size() : 0;
    }

    /**
     * 判断当前身份是否包含至少一项已启用的有效伪装字段。
     * 防止仅启用部分字段时被错误判定为损坏文件。
     */
    public boolean hasAnyActiveField() {
        if (isItemEnabled(KEY_ANDROID_ID) && androidId != null && !androidId.isEmpty()) return true;
        if (isItemEnabled(KEY_AD_ID) && advertisingId != null && !advertisingId.isEmpty()) return true;
        if (isItemEnabled(KEY_TELEPHONY) && (imei != null || meid != null || imsi != null || iccid != null || serial != null)) return true;
        if (isItemEnabled(KEY_CARRIER) && (networkOperator != null || simOperator != null)) return true;
        if (isItemEnabled(KEY_BUILD) && (model != null || brand != null || fingerprint != null)) return true;
        if (isItemEnabled(KEY_WIFI) && macAddress != null && !macAddress.isEmpty()) return true;
        if (isItemEnabled(KEY_BLUETOOTH) && bluetoothMac != null && !bluetoothMac.isEmpty()) return true;
        if (isItemEnabled(KEY_DRM) && drmId != null && !drmId.isEmpty()) return true;
        if (isItemEnabled(KEY_GSF) && gsfId != null && !gsfId.isEmpty()) return true;
        if (isItemEnabled(KEY_SENSOR) && sensorName != null && !sensorName.isEmpty()) return true;
        if (isItemEnabled(KEY_SETTINGS)) return true;
        return androidId != null || imei != null || model != null || macAddress != null;
    }

    public String toJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("androidId", androidId);
            json.put("advertisingId", advertisingId);
            json.put("imei", imei);
            json.put("meid", meid);
            json.put("serial", serial);
            json.put("macAddress", macAddress);
            json.put("gsfId", gsfId);
            json.put("appSetId", appSetId);
            json.put("brand", brand);
            json.put("model", model);
            json.put("manufacturer", manufacturer);
            json.put("device", device);
            json.put("product", product);
            json.put("hardware", hardware);
            json.put("fingerprint", fingerprint);
            json.put("bootloader", bootloader);
            json.put("radioVersion", radioVersion);
            json.put("buildId", buildId);
            json.put("buildTime", buildTime);
            json.put("buildTags", buildTags);
            json.put("buildType", buildType);
            json.put("buildUser", buildUser);
            json.put("buildHost", buildHost);
            json.put("buildDisplay", buildDisplay);
            json.put("drmId", drmId);
            json.put("bluetoothMac", bluetoothMac);
            json.put("sensorVendor", sensorVendor);
            json.put("sensorName", sensorName);
            json.put("sensorVersion", sensorVersion);
            json.put("networkOperator", networkOperator);
            json.put("networkOperatorName", networkOperatorName);
            json.put("simOperator", simOperator);
            json.put("simOperatorName", simOperatorName);
            json.put("simCountryIso", simCountryIso);
            json.put("networkCountryIso", networkCountryIso);
            json.put("imsi", imsi);
            json.put("iccid", iccid);

            JSONArray arr = new JSONArray();
            if (enabledItems != null) {
                for (String k : enabledItems) arr.put(k);
            }
            json.put("enabledItems", arr);

            return json.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    public static Identity fromJson(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) return null;
        try {
            JSONObject json = new JSONObject(jsonStr);
            Identity id = new Identity();
            id.androidId = json.optString("androidId", null);
            id.advertisingId = json.optString("advertisingId", null);
            id.imei = json.optString("imei", null);
            id.meid = json.optString("meid", null);
            id.serial = json.optString("serial", null);
            id.macAddress = json.optString("macAddress", null);
            id.gsfId = json.optString("gsfId", null);
            id.appSetId = json.optString("appSetId", null);
            id.brand = json.optString("brand", null);
            id.model = json.optString("model", null);
            id.manufacturer = json.optString("manufacturer", null);
            id.device = json.optString("device", null);
            id.product = json.optString("product", null);
            id.hardware = json.optString("hardware", null);
            id.fingerprint = json.optString("fingerprint", null);
            id.bootloader = json.optString("bootloader", null);
            id.radioVersion = json.optString("radioVersion", null);
            id.buildId = json.optString("buildId", null);
            id.buildTime = json.optString("buildTime", null);
            id.buildTags = json.optString("buildTags", null);
            id.buildType = json.optString("buildType", null);
            id.buildUser = json.optString("buildUser", null);
            id.buildHost = json.optString("buildHost", null);
            id.buildDisplay = json.optString("buildDisplay", null);
            id.drmId = json.optString("drmId", null);
            id.bluetoothMac = json.optString("bluetoothMac", null);
            id.sensorVendor = json.optString("sensorVendor", null);
            id.sensorName = json.optString("sensorName", null);
            id.sensorVersion = json.optString("sensorVersion", null);
            id.networkOperator = json.optString("networkOperator", null);
            id.networkOperatorName = json.optString("networkOperatorName", null);
            id.simOperator = json.optString("simOperator", null);
            id.simOperatorName = json.optString("simOperatorName", null);
            id.simCountryIso = json.optString("simCountryIso", null);
            id.networkCountryIso = json.optString("networkCountryIso", null);
            id.imsi = json.optString("imsi", null);
            id.iccid = json.optString("iccid", null);

            JSONArray arr = json.optJSONArray("enabledItems");
            if (arr != null) {
                id.enabledItems = new HashSet<>();
                for (int i = 0; i < arr.length(); i++) {
                    id.enabledItems.add(arr.getString(i));
                }
            } else {
                id.enableAll();
            }

            return id;
        } catch (Exception e) {
            return null;
        }
    }
}
