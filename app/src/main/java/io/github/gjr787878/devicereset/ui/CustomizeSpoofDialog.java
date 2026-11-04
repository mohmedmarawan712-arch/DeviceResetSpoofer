package io.github.gjr787878.devicereset.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.HashMap;
import java.util.Map;

import io.github.gjr787878.devicereset.Config;
import io.github.gjr787878.devicereset.R;
import io.github.gjr787878.devicereset.xposed.Identity;
import io.github.gjr787878.devicereset.xposed.IdentityGenerator;

public class CustomizeSpoofDialog extends Dialog {

    public interface OnSaveListener {
        void onSaved(Identity updatedIdentity);
    }

    private final String packageName;
    private final String appName;
    private final Drawable appIcon;
    private final Identity workingIdentity;
    private final OnSaveListener saveListener;
    private final String currentLang;

    // View references for each item
    private final Map<String, SwitchMaterial> switchMap = new HashMap<>();
    private final Map<String, LinearLayout> fieldsContainerMap = new HashMap<>();

    // Individual Edit inputs
    private EditText etAndroidId;
    private EditText etAdId;
    private EditText etAppSetId;
    private EditText etImei;
    private EditText etMeid;
    private EditText etSerial;
    private EditText etImsi;
    private EditText etIccid;
    private EditText etCarrierName;
    private EditText etCarrierCode;
    private EditText etBrand;
    private EditText etModel;
    private EditText etFingerprint;
    private EditText etWifiMac;
    private EditText etBtMac;
    private EditText etDrmId;
    private EditText etGsfId;
    private EditText etSensorName;

    public CustomizeSpoofDialog(@NonNull Context context, String packageName, String appName,
                                Drawable appIcon, Identity currentId, String lang, OnSaveListener listener) {
        super(context, R.style.Theme_DRS_Dialog);
        this.packageName = packageName;
        this.appName = appName;
        this.appIcon = appIcon;
        this.currentLang = lang;
        this.saveListener = listener;

        if (currentId != null) {
            this.workingIdentity = Identity.fromJson(currentId.toJson());
        } else {
            this.workingIdentity = IdentityGenerator.generateRandom();
        }
        IdentityGenerator.fillMissing(this.workingIdentity);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_customize_spoof);

        Window window = getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        initHeader();
        initPresets();
        populateCategories();
        initBottomActions();
    }

    private void initHeader() {
        ImageView ivIcon = findViewById(R.id.dlg_app_icon);
        TextView tvTitle = findViewById(R.id.dlg_app_title);
        TextView tvPkg = findViewById(R.id.dlg_app_pkg);
        ImageButton btnClose = findViewById(R.id.dlg_btn_close);

        if (appIcon != null) ivIcon.setImageDrawable(appIcon);
        else ivIcon.setImageResource(android.R.drawable.sym_def_app_icon);

        tvTitle.setText(appName);
        tvPkg.setText(packageName);

        btnClose.setOnClickListener(v -> dismiss());
    }

    private void initPresets() {
        MaterialButton btnAll = findViewById(R.id.btn_preset_all);
        MaterialButton btnNone = findViewById(R.id.btn_preset_none);
        MaterialButton btnRec = findViewById(R.id.btn_preset_recommended);
        MaterialButton btnIds = findViewById(R.id.btn_preset_ids);

        btnAll.setOnClickListener(v -> setAllSwitches(true));
        btnNone.setOnClickListener(v -> setAllSwitches(false));

        btnRec.setOnClickListener(v -> {
            setAllSwitches(false);
            setSwitch(Identity.KEY_ANDROID_ID, true);
            setSwitch(Identity.KEY_TELEPHONY, true);
            setSwitch(Identity.KEY_BUILD, true);
            setSwitch(Identity.KEY_WIFI, true);
            setSwitch(Identity.KEY_CARRIER, true);
        });

        btnIds.setOnClickListener(v -> {
            setAllSwitches(false);
            setSwitch(Identity.KEY_ANDROID_ID, true);
            setSwitch(Identity.KEY_AD_ID, true);
            setSwitch(Identity.KEY_TELEPHONY, true);
            setSwitch(Identity.KEY_GSF, true);
        });
    }

    private void setAllSwitches(boolean checked) {
        for (SwitchMaterial s : switchMap.values()) {
            s.setChecked(checked);
        }
    }

    private void setSwitch(String key, boolean checked) {
        SwitchMaterial s = switchMap.get(key);
        if (s != null) s.setChecked(checked);
    }

    private void populateCategories() {
        LinearLayout container = findViewById(R.id.dlg_items_container);
        container.removeAllViews();

        // 1. Android ID
        addCategoryCard(container, Identity.KEY_ANDROID_ID, "📱 " + getStr(R.string.spoof_item_android_id),
                "SSAID - Android 64-bit unique hex ID", () -> {
                    etAndroidId = addInputField(fieldsContainerMap.get(Identity.KEY_ANDROID_ID), "Android ID (Hex)", workingIdentity.androidId);
                }, () -> {
                    IdentityGenerator.randomizeItem(workingIdentity, Identity.KEY_ANDROID_ID);
                    if (etAndroidId != null) etAndroidId.setText(workingIdentity.androidId);
                });

        // 2. Advertising ID
        addCategoryCard(container, Identity.KEY_AD_ID, "📢 " + getStr(R.string.spoof_item_ad_id),
                "Google Play Services AAID & AppSet ID", () -> {
                    etAdId = addInputField(fieldsContainerMap.get(Identity.KEY_AD_ID), "AAID (UUID)", workingIdentity.advertisingId);
                    etAppSetId = addInputField(fieldsContainerMap.get(Identity.KEY_AD_ID), "AppSet ID (UUID)", workingIdentity.appSetId);
                }, () -> {
                    IdentityGenerator.randomizeItem(workingIdentity, Identity.KEY_AD_ID);
                    if (etAdId != null) etAdId.setText(workingIdentity.advertisingId);
                    if (etAppSetId != null) etAppSetId.setText(workingIdentity.appSetId);
                });

        // 3. Telephony
        addCategoryCard(container, Identity.KEY_TELEPHONY, "📶 " + getStr(R.string.spoof_item_telephony),
                "IMEI (Luhn valid), MEID, IMSI, ICCID, Serial", () -> {
                    etImei = addInputField(fieldsContainerMap.get(Identity.KEY_TELEPHONY), "IMEI", workingIdentity.imei);
                    etMeid = addInputField(fieldsContainerMap.get(Identity.KEY_TELEPHONY), "MEID", workingIdentity.meid);
                    etSerial = addInputField(fieldsContainerMap.get(Identity.KEY_TELEPHONY), "Serial Number", workingIdentity.serial);
                    etImsi = addInputField(fieldsContainerMap.get(Identity.KEY_TELEPHONY), "IMSI", workingIdentity.imsi);
                    etIccid = addInputField(fieldsContainerMap.get(Identity.KEY_TELEPHONY), "SIM ICCID", workingIdentity.iccid);
                }, () -> {
                    IdentityGenerator.randomizeItem(workingIdentity, Identity.KEY_TELEPHONY);
                    if (etImei != null) etImei.setText(workingIdentity.imei);
                    if (etMeid != null) etMeid.setText(workingIdentity.meid);
                    if (etSerial != null) etSerial.setText(workingIdentity.serial);
                    if (etImsi != null) etImsi.setText(workingIdentity.imsi);
                    if (etIccid != null) etIccid.setText(workingIdentity.iccid);
                });

        // 4. Carrier
        addCategoryCard(container, Identity.KEY_CARRIER, "🏢 " + getStr(R.string.spoof_item_carrier),
                "Network & SIM Operator, MCC/MNC, Country ISO", () -> {
                    LinearLayout fields = fieldsContainerMap.get(Identity.KEY_CARRIER);
                    MaterialButton btnPickCarrier = new MaterialButton(getContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                    btnPickCarrier.setText("🌐 Select Carrier Profile");
                    btnPickCarrier.setCornerRadius(10);
                    btnPickCarrier.setOnClickListener(v -> showCarrierSelector());
                    fields.addView(btnPickCarrier);

                    etCarrierName = addInputField(fields, "Operator Name", workingIdentity.networkOperatorName);
                    etCarrierCode = addInputField(fields, "MCC+MNC Code", workingIdentity.networkOperator);
                }, () -> {
                    IdentityGenerator.randomizeItem(workingIdentity, Identity.KEY_CARRIER);
                    if (etCarrierName != null) etCarrierName.setText(workingIdentity.networkOperatorName);
                    if (etCarrierCode != null) etCarrierCode.setText(workingIdentity.networkOperator);
                });

        // 5. Build & Hardware
        addCategoryCard(container, Identity.KEY_BUILD, "💻 " + getStr(R.string.spoof_item_build),
                "Brand, Model, Product, Hardware, Fingerprint", () -> {
                    LinearLayout fields = fieldsContainerMap.get(Identity.KEY_BUILD);
                    MaterialButton btnPickDevice = new MaterialButton(getContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                    btnPickDevice.setText("📱 Select Device Model Preset");
                    btnPickDevice.setCornerRadius(10);
                    btnPickDevice.setOnClickListener(v -> showDevicePresetSelector());
                    fields.addView(btnPickDevice);

                    etBrand = addInputField(fields, "Brand", workingIdentity.brand);
                    etModel = addInputField(fields, "Model", workingIdentity.model);
                    etFingerprint = addInputField(fields, "Fingerprint", workingIdentity.fingerprint);
                }, () -> {
                    IdentityGenerator.randomizeItem(workingIdentity, Identity.KEY_BUILD);
                    if (etBrand != null) etBrand.setText(workingIdentity.brand);
                    if (etModel != null) etModel.setText(workingIdentity.model);
                    if (etFingerprint != null) etFingerprint.setText(workingIdentity.fingerprint);
                });

        // 6. Wi-Fi MAC
        addCategoryCard(container, Identity.KEY_WIFI, "🌐 " + getStr(R.string.spoof_item_wifi),
                "Standard Wi-Fi Interface MAC Address", () -> {
                    etWifiMac = addInputField(fieldsContainerMap.get(Identity.KEY_WIFI), "Wi-Fi MAC", workingIdentity.macAddress);
                }, () -> {
                    IdentityGenerator.randomizeItem(workingIdentity, Identity.KEY_WIFI);
                    if (etWifiMac != null) etWifiMac.setText(workingIdentity.macAddress);
                });

        // 7. Bluetooth MAC
        addCategoryCard(container, Identity.KEY_BLUETOOTH, "🔵 " + getStr(R.string.spoof_item_bluetooth),
                "Bluetooth Adapter Hardware Address", () -> {
                    etBtMac = addInputField(fieldsContainerMap.get(Identity.KEY_BLUETOOTH), "Bluetooth MAC", workingIdentity.bluetoothMac);
                }, () -> {
                    IdentityGenerator.randomizeItem(workingIdentity, Identity.KEY_BLUETOOTH);
                    if (etBtMac != null) etBtMac.setText(workingIdentity.bluetoothMac);
                });

        // 8. DRM ID
        addCategoryCard(container, Identity.KEY_DRM, "🔒 " + getStr(R.string.spoof_item_drm),
                "Widevine DRM 32-Byte Hardware Unique ID", () -> {
                    etDrmId = addInputField(fieldsContainerMap.get(Identity.KEY_DRM), "DRM Unique ID (Hex)", workingIdentity.drmId);
                }, () -> {
                    IdentityGenerator.randomizeItem(workingIdentity, Identity.KEY_DRM);
                    if (etDrmId != null) etDrmId.setText(workingIdentity.drmId);
                });

        // 9. GSF ID
        addCategoryCard(container, Identity.KEY_GSF, "🔍 " + getStr(R.string.spoof_item_gsf),
                "Google Services Framework Account ID", () -> {
                    etGsfId = addInputField(fieldsContainerMap.get(Identity.KEY_GSF), "GSF ID (Hex)", workingIdentity.gsfId);
                }, () -> {
                    IdentityGenerator.randomizeItem(workingIdentity, Identity.KEY_GSF);
                    if (etGsfId != null) etGsfId.setText(workingIdentity.gsfId);
                });

        // 10. Sensors
        addCategoryCard(container, Identity.KEY_SENSOR, "🧭 " + getStr(R.string.spoof_item_sensor),
                "Hardware Sensor Vendor, Name & Version", () -> {
                    etSensorName = addInputField(fieldsContainerMap.get(Identity.KEY_SENSOR), "Sensor Name", workingIdentity.sensorName);
                }, () -> {
                    IdentityGenerator.randomizeItem(workingIdentity, Identity.KEY_SENSOR);
                    if (etSensorName != null) etSensorName.setText(workingIdentity.sensorName);
                });

        // 11. System Settings & Native Props
        addCategoryCard(container, Identity.KEY_SETTINGS, "⚙️ " + getStr(R.string.spoof_item_settings),
                "System.Global Settings & Native PLT Property Hook", null, null);
    }

    private void addCategoryCard(LinearLayout parent, String itemKey, String title,
                                 String subtitle, Runnable setupFields, Runnable onRandomize) {
        MaterialCardView card = new MaterialCardView(getContext());
        card.setCardBackgroundColor(0xFF1E273E);
        card.setStrokeColor(0xFF2E3D59);
        card.setStrokeWidth(1);
        card.setRadius(14 * getContext().getResources().getDisplayMetrics().density);

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = (int) (10 * getContext().getResources().getDisplayMetrics().density);
        card.setLayoutParams(clp);

        LinearLayout cardLayout = new LinearLayout(getContext());
        cardLayout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (14 * getContext().getResources().getDisplayMetrics().density);
        cardLayout.setPadding(pad, pad, pad, pad);

        // Header Row: Title & Switch
        LinearLayout headerRow = new LinearLayout(getContext());
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        LinearLayout textCol = new LinearLayout(getContext());
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(tlp);

        TextView tvTitle = new TextView(getContext());
        tvTitle.setText(title);
        tvTitle.setTextColor(0xFFF8FAFC);
        tvTitle.setTextSize(15);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        textCol.addView(tvTitle);

        TextView tvSub = new TextView(getContext());
        tvSub.setText(subtitle);
        tvSub.setTextColor(0xFF94A3B8);
        tvSub.setTextSize(11);
        tvSub.setPadding(0, 2, 0, 0);
        textCol.addView(tvSub);

        headerRow.addView(textCol);

        if (onRandomize != null) {
            MaterialButton btnDice = new MaterialButton(getContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btnDice.setText("🎲");
            btnDice.setTextSize(14);
            btnDice.setCornerRadius(10);
            btnDice.setLayoutParams(new LinearLayout.LayoutParams(
                    (int) (44 * getContext().getResources().getDisplayMetrics().density),
                    (int) (38 * getContext().getResources().getDisplayMetrics().density)));
            btnDice.setOnClickListener(v -> onRandomize.run());
            headerRow.addView(btnDice);
        }

        SwitchMaterial switchItem = new SwitchMaterial(getContext());
        switchItem.setChecked(workingIdentity.isItemEnabled(itemKey));
        headerRow.addView(switchItem);
        switchMap.put(itemKey, switchItem);

        cardLayout.addView(headerRow);

        // Fields Container
        LinearLayout fieldsContainer = new LinearLayout(getContext());
        fieldsContainer.setOrientation(LinearLayout.VERTICAL);
        fieldsContainer.setPadding(0, (int) (8 * getContext().getResources().getDisplayMetrics().density), 0, 0);
        fieldsContainer.setVisibility(switchItem.isChecked() ? View.VISIBLE : View.GONE);
        fieldsContainerMap.put(itemKey, fieldsContainer);

        switchItem.setOnCheckedChangeListener((btn, isChecked) -> {
            fieldsContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        if (setupFields != null) {
            setupFields.run();
        }

        cardLayout.addView(fieldsContainer);
        card.addView(cardLayout);
        parent.addView(card);
    }

    private EditText addInputField(LinearLayout parent, String hint, String initialValue) {
        if (parent == null) return null;
        EditText et = new EditText(getContext());
        et.setHint(hint);
        et.setText(initialValue != null ? initialValue : "");
        et.setTextColor(0xFFF8FAFC);
        et.setHintTextColor(0xFF64748B);
        et.setTextSize(12);
        et.setBackgroundResource(R.drawable.bg_search_box);
        int pad = (int) (10 * getContext().getResources().getDisplayMetrics().density);
        et.setPadding(pad, pad, pad, pad);
        et.setSingleLine(true);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (6 * getContext().getResources().getDisplayMetrics().density);
        et.setLayoutParams(lp);

        parent.addView(et);
        return et;
    }

    private void showDevicePresetSelector() {
        String[][] profiles = IdentityGenerator.getDeviceProfiles();
        String[] names = new String[profiles.length];
        for (int i = 0; i < profiles.length; i++) {
            names[i] = profiles[i][0] + " " + profiles[i][1];
        }

        new AlertDialog.Builder(getContext())
                .setTitle("Select Device Preset")
                .setItems(names, (dialog, which) -> {
                    IdentityGenerator.applyDeviceProfile(workingIdentity, which);
                    if (etBrand != null) etBrand.setText(workingIdentity.brand);
                    if (etModel != null) etModel.setText(workingIdentity.model);
                    if (etFingerprint != null) etFingerprint.setText(workingIdentity.fingerprint);
                    Toast.makeText(getContext(), "Selected: " + names[which], Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showCarrierSelector() {
        String[][] carriers = IdentityGenerator.getCarrierProfiles();
        String[] names = new String[carriers.length];
        for (int i = 0; i < carriers.length; i++) {
            names[i] = carriers[i][1] + " (" + carriers[i][0] + " - " + carriers[i][2].toUpperCase() + ")";
        }

        new AlertDialog.Builder(getContext())
                .setTitle("Select Carrier Profile")
                .setItems(names, (dialog, which) -> {
                    IdentityGenerator.applyCarrierProfile(workingIdentity, which);
                    if (etCarrierName != null) etCarrierName.setText(workingIdentity.networkOperatorName);
                    if (etCarrierCode != null) etCarrierCode.setText(workingIdentity.networkOperator);
                    Toast.makeText(getContext(), "Selected: " + carriers[which][1], Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void initBottomActions() {
        MaterialButton btnRandom = findViewById(R.id.dlg_btn_random_selected);
        MaterialButton btnSave = findViewById(R.id.dlg_btn_save);

        btnRandom.setOnClickListener(v -> {
            applyInputsToIdentity();
            for (String key : Identity.ALL_KEYS) {
                if (workingIdentity.isItemEnabled(key)) {
                    IdentityGenerator.randomizeItem(workingIdentity, key);
                }
            }
            refreshAllInputFields();
            Toast.makeText(getContext(), "Selected items refreshed! 🎲", Toast.LENGTH_SHORT).show();
        });

        btnSave.setOnClickListener(v -> {
            applyInputsToIdentity();
            // Update enabled state in workingIdentity
            for (String key : Identity.ALL_KEYS) {
                SwitchMaterial sw = switchMap.get(key);
                if (sw != null) {
                    workingIdentity.setItemEnabled(key, sw.isChecked());
                }
            }

            if (saveListener != null) {
                saveListener.onSaved(workingIdentity);
            }
            dismiss();
        });
    }

    private void applyInputsToIdentity() {
        if (etAndroidId != null) workingIdentity.androidId = etAndroidId.getText().toString().trim();
        if (etAdId != null) workingIdentity.advertisingId = etAdId.getText().toString().trim();
        if (etAppSetId != null) workingIdentity.appSetId = etAppSetId.getText().toString().trim();
        if (etImei != null) workingIdentity.imei = etImei.getText().toString().trim();
        if (etMeid != null) workingIdentity.meid = etMeid.getText().toString().trim();
        if (etSerial != null) workingIdentity.serial = etSerial.getText().toString().trim();
        if (etImsi != null) workingIdentity.imsi = etImsi.getText().toString().trim();
        if (etIccid != null) workingIdentity.iccid = etIccid.getText().toString().trim();
        if (etCarrierName != null) workingIdentity.networkOperatorName = etCarrierName.getText().toString().trim();
        if (etCarrierCode != null) workingIdentity.networkOperator = etCarrierCode.getText().toString().trim();
        if (etBrand != null) workingIdentity.brand = etBrand.getText().toString().trim();
        if (etModel != null) workingIdentity.model = etModel.getText().toString().trim();
        if (etFingerprint != null) workingIdentity.fingerprint = etFingerprint.getText().toString().trim();
        if (etWifiMac != null) workingIdentity.macAddress = etWifiMac.getText().toString().trim();
        if (etBtMac != null) workingIdentity.bluetoothMac = etBtMac.getText().toString().trim();
        if (etDrmId != null) workingIdentity.drmId = etDrmId.getText().toString().trim();
        if (etGsfId != null) workingIdentity.gsfId = etGsfId.getText().toString().trim();
        if (etSensorName != null) workingIdentity.sensorName = etSensorName.getText().toString().trim();
    }

    private void refreshAllInputFields() {
        if (etAndroidId != null) etAndroidId.setText(workingIdentity.androidId);
        if (etAdId != null) etAdId.setText(workingIdentity.advertisingId);
        if (etAppSetId != null) etAppSetId.setText(workingIdentity.appSetId);
        if (etImei != null) etImei.setText(workingIdentity.imei);
        if (etMeid != null) etMeid.setText(workingIdentity.meid);
        if (etSerial != null) etSerial.setText(workingIdentity.serial);
        if (etImsi != null) etImsi.setText(workingIdentity.imsi);
        if (etIccid != null) etIccid.setText(workingIdentity.iccid);
        if (etCarrierName != null) etCarrierName.setText(workingIdentity.networkOperatorName);
        if (etCarrierCode != null) etCarrierCode.setText(workingIdentity.networkOperator);
        if (etBrand != null) etBrand.setText(workingIdentity.brand);
        if (etModel != null) etModel.setText(workingIdentity.model);
        if (etFingerprint != null) etFingerprint.setText(workingIdentity.fingerprint);
        if (etWifiMac != null) etWifiMac.setText(workingIdentity.macAddress);
        if (etBtMac != null) etBtMac.setText(workingIdentity.bluetoothMac);
        if (etDrmId != null) etDrmId.setText(workingIdentity.drmId);
        if (etGsfId != null) etGsfId.setText(workingIdentity.gsfId);
        if (etSensorName != null) etSensorName.setText(workingIdentity.sensorName);
    }

    private String getStr(int resId) {
        return getContext().getString(resId);
    }
}
