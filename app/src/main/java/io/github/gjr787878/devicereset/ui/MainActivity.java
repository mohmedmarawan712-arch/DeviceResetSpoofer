package io.github.gjr787878.devicereset.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.gjr787878.devicereset.Config;
import io.github.gjr787878.devicereset.FrameworkDetector;
import io.github.gjr787878.devicereset.LSPosedScopeHelper;
import io.github.gjr787878.devicereset.R;
import io.github.gjr787878.devicereset.RootScopeManager;
import io.github.gjr787878.devicereset.xposed.Identity;
import io.github.gjr787878.devicereset.xposed.IdentityGenerator;

public class MainActivity extends AppCompatActivity implements TargetAppAdapter.AppActionListener {

    private static final String PREFS_NAME = "devicereset_ui";
    private static final String PREF_LANG = "app_language";

    private final ExecutorService backgroundPool = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String currentLang = "ar";

    // Views
    private View cardFwStatus;
    private View viewFwDot;
    private TextView tvFwName;
    private TextView tvFwScopeInfo;
    private MaterialButton btnSyncScope;

    private TextView tvStatTargetsCount;
    private TextView tvStatActiveCount;
    private TextView tvStatStatusLabel;
    private MaterialButton btnRandomAll;

    private View pageApps;
    private View pageEngine;
    private View pageLogs;

    private EditText etSearch;
    private ImageButton btnClearSearch;
    private RecyclerView rvTargetApps;
    private View layoutEmptyState;
    private ExtendedFloatingActionButton fabAddApp;
    private BottomNavigationView bottomNav;

    private TargetAppAdapter adapter;
    private final List<TargetAppItem> targetAppItems = new ArrayList<>();
    private final Set<String> scopeCache = new HashSet<>();

    private TextView tvLogsContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentLang = prefs.getString(PREF_LANG, "ar");

        setContentView(R.layout.activity_main);

        initViews();
        setupFrameworkDetection();
        loadTargetApps();
        setupEnginePage();

        LSPosedScopeHelper.init();
        LSPosedScopeHelper.addConnectionCallback(connected -> {
            mainHandler.post(this::refreshScopeAndFramework);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshScopeAndFramework();
        loadTargetApps();
    }

    private void initViews() {
        cardFwStatus = findViewById(R.id.card_framework_status);
        viewFwDot = findViewById(R.id.view_fw_dot);
        tvFwName = findViewById(R.id.tv_fw_name);
        tvFwScopeInfo = findViewById(R.id.tv_fw_scope_info);
        btnSyncScope = findViewById(R.id.btn_sync_scope);

        tvStatTargetsCount = findViewById(R.id.tv_stat_targets_count);
        tvStatActiveCount = findViewById(R.id.tv_stat_active_count);
        tvStatStatusLabel = findViewById(R.id.tv_stat_status_label);
        btnRandomAll = findViewById(R.id.btn_random_all);
        if (btnRandomAll != null) {
            btnRandomAll.setOnClickListener(v -> randomizeAllTargets());
        }

        pageApps = findViewById(R.id.page_apps);
        pageEngine = findViewById(R.id.page_engine);
        pageLogs = findViewById(R.id.page_logs);

        etSearch = findViewById(R.id.et_search_apps);
        btnClearSearch = findViewById(R.id.btn_clear_search);
        rvTargetApps = findViewById(R.id.rv_target_apps);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        fabAddApp = findViewById(R.id.fab_add_app);
        bottomNav = findViewById(R.id.bottom_navigation);

        tvLogsContent = findViewById(R.id.tv_logs_content);

        // RecyclerView
        rvTargetApps.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TargetAppAdapter(targetAppItems, this, currentLang);
        rvTargetApps.setAdapter(adapter);

        // Search text watcher
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s.toString();
                btnClearSearch.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                if (adapter != null) adapter.filter(q);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClearSearch.setOnClickListener(v -> etSearch.setText(""));

        // Add app buttons
        fabAddApp.setOnClickListener(v -> openAppPicker());
        View btnEmptyAdd = findViewById(R.id.btn_empty_add);
        if (btnEmptyAdd != null) {
            btnEmptyAdd.setOnClickListener(v -> openAppPicker());
        }

        // Action bar buttons
        ImageButton btnRefresh = findViewById(R.id.btn_action_refresh);
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> {
                refreshScopeAndFramework();
                loadTargetApps();
                Toast.makeText(this, isAr() ? "تم تحديث البيانات" : "Refreshed", Toast.LENGTH_SHORT).show();
            });
        }

        ImageButton btnLang = findViewById(R.id.btn_action_language);
        if (btnLang != null) {
            btnLang.setOnClickListener(v -> showLanguageSelector());
        }

        ImageButton btnAbout = findViewById(R.id.btn_action_about);
        if (btnAbout != null) {
            btnAbout.setOnClickListener(v -> showAboutDialog());
        }

        // Scope sync button
        btnSyncScope.setOnClickListener(v -> syncScope());

        // Logs buttons
        MaterialButton btnClearLogs = findViewById(R.id.btn_clear_logs);
        if (btnClearLogs != null) {
            btnClearLogs.setOnClickListener(v -> {
                if (tvLogsContent != null) tvLogsContent.setText("");
                Toast.makeText(this, isAr() ? "تم مسح السجلات" : "Logs cleared", Toast.LENGTH_SHORT).show();
            });
        }

        MaterialButton btnExportLogs = findViewById(R.id.btn_export_logs);
        if (btnExportLogs != null) {
            btnExportLogs.setOnClickListener(v -> exportLogs());
        }

        // Bottom Navigation
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_apps) {
                pageApps.setVisibility(View.VISIBLE);
                pageEngine.setVisibility(View.GONE);
                pageLogs.setVisibility(View.GONE);
                fabAddApp.show();
                return true;
            } else if (itemId == R.id.nav_engine) {
                pageApps.setVisibility(View.GONE);
                pageEngine.setVisibility(View.VISIBLE);
                pageLogs.setVisibility(View.GONE);
                fabAddApp.hide();
                return true;
            } else if (itemId == R.id.nav_logs) {
                pageApps.setVisibility(View.GONE);
                pageEngine.setVisibility(View.GONE);
                pageLogs.setVisibility(View.VISIBLE);
                fabAddApp.hide();
                loadLogs();
                return true;
            }
            return false;
        });
    }

    private void openAppPicker() {
        Intent intent = new Intent(this, AppPickerActivity.class);
        startActivity(intent);
    }

    private void setupFrameworkDetection() {
        refreshScopeAndFramework();
    }

    private void refreshScopeAndFramework() {
        backgroundPool.execute(() -> {
            FrameworkDetector.Info fw = FrameworkDetector.detect();
            boolean connected = LSPosedScopeHelper.isConnected();
            Set<String> scope = new HashSet<>();
            if (connected) {
                List<String> s = LSPosedScopeHelper.getScope();
                if (s != null) scope.addAll(s);
            }

            Config.ScopeDbState dbState = Config.readLSPosedDbState(this);
            if (dbState != null && dbState.targets != null) {
                scope.addAll(dbState.targets);
            }

            synchronized (scopeCache) {
                scopeCache.clear();
                scopeCache.addAll(scope);
            }

            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                updateFrameworkUI(fw, connected, scope.size());
            });
        });
    }

    private void updateFrameworkUI(FrameworkDetector.Info fw, boolean connected, int scopeSize) {
        String fwName = (fw != null && fw.name != null) ? fw.name : "LSPosed / Vector";
        String fwVersion = (fw != null && fw.version != null) ? " (" + fw.version + ")" : "";
        tvFwName.setText(fwName + fwVersion);

        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);

        if (connected) {
            dot.setColor(0xFF10B981); // Emerald
            tvFwScopeInfo.setText(isAr() ? "متصل بالخدمة • " + scopeSize + " تطبيق في النطاق" :
                    "Service Connected • " + scopeSize + " apps in scope");
            tvFwScopeInfo.setTextColor(0xFF10B981);
        } else if (scopeSize > 0) {
            dot.setColor(0xFF38BDF8); // Cyan
            tvFwScopeInfo.setText(isAr() ? "النطاق مسجل بالـ DB (" + scopeSize + " تطبيق)" :
                    "Database Scope Active (" + scopeSize + " apps)");
            tvFwScopeInfo.setTextColor(0xFF38BDF8);
        } else {
            dot.setColor(0xFFF59E0B); // Amber
            tvFwScopeInfo.setText(isAr() ? "غير متصل بالخدمة • يرجى التحقق من LSPosed" :
                    "Not Connected • Please verify in LSPosed Manager");
            tvFwScopeInfo.setTextColor(0xFFF59E0B);
        }
        viewFwDot.setBackground(dot);
        updateStatCards(connected, scopeSize);
    }

    private void updateStatCards(boolean connected, int scopeSize) {
        if (tvStatTargetsCount != null) {
            tvStatTargetsCount.setText(String.valueOf(targetAppItems.size()));
        }
        if (tvStatActiveCount != null) {
            int activeTotal = 0;
            for (TargetAppItem item : targetAppItems) {
                if (item.isSpoofEnabled) {
                    activeTotal += item.getActiveCount();
                }
            }
            tvStatActiveCount.setText(String.valueOf(activeTotal));
        }
        if (tvStatStatusLabel != null) {
            if (connected) {
                tvStatStatusLabel.setText(isAr() ? "متصل" : "Connected");
                tvStatStatusLabel.setTextColor(0xFF10B981);
            } else if (scopeSize > 0) {
                tvStatStatusLabel.setText(isAr() ? "نشط (DB)" : "Active (DB)");
                tvStatStatusLabel.setTextColor(0xFF38BDF8);
            } else {
                tvStatStatusLabel.setText(isAr() ? "غير متصل" : "Not Active");
                tvStatStatusLabel.setTextColor(0xFFF59E0B);
            }
        }
    }

    private void randomizeAllTargets() {
        if (targetAppItems.isEmpty()) {
            Toast.makeText(this, isAr() ? "لا توجد تطبيقات مستهدفة لتوليدها" : "No target apps to randomize", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(isAr() ? "توليد قيم عشوائية للكل" : "Randomize All Targets")
                .setMessage(isAr() ? "هل تريد توليد معرّفات عشوائية جديدة لجميع التطبيقات المضافة؟" : "Generate fresh random identities for all target applications?")
                .setPositiveButton(isAr() ? "توليد الآن" : "Randomize", (dialog, which) -> {
                    backgroundPool.execute(() -> {
                        for (TargetAppItem item : targetAppItems) {
                            if (item.identity == null) {
                                item.identity = IdentityGenerator.generateRandom();
                            } else {
                                IdentityGenerator.randomizeEnabledOnly(item.identity);
                            }
                            item.isSpoofEnabled = true;
                            Config.addTargetPackage(this, item.packageName);
                            Config.setIdentity(this, item.packageName, item.identity.toJson());
                            writeSentinelToApp(item.packageName, item.identity.toJson());
                        }

                        mainHandler.post(() -> {
                            adapter.notifyDataSetChanged();
                            updateStatCards(LSPosedScopeHelper.isConnected(), scopeCache.size());
                            Toast.makeText(this, isAr() ? "تم توليد قيم جديدة لجميع التطبيقات بنجاح" : "All targets refreshed with new identities", Toast.LENGTH_SHORT).show();
                        });
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void syncScope() {
        Toast.makeText(this, isAr() ? "جاري مزامنة النطاق..." : "Syncing scope...", Toast.LENGTH_SHORT).show();
        backgroundPool.execute(() -> {
            Set<String> targets = Config.getTargetPackages(this);
            Config.syncScopeToLSPosed(this, targets);
            mainHandler.post(() -> {
                refreshScopeAndFramework();
                Toast.makeText(this, isAr() ? "اكتملت مزامنة النطاق بنجاح" : "Scope sync complete", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void loadTargetApps() {
        backgroundPool.execute(() -> {
            Set<String> pkgs = Config.getTargetPackages(this);
            Set<String> idPkgs = Config.getIdentityPackages(this);
            Set<String> all = new HashSet<>(pkgs);
            all.addAll(idPkgs);

            // Automatically import apps that the user enabled in LSPosed Scope
            synchronized (scopeCache) {
                for (String sPkg : scopeCache) {
                    if (sPkg != null && !sPkg.isEmpty()
                            && !sPkg.equals(getPackageName())
                            && !sPkg.equals("android")
                            && !sPkg.equals("system")) {
                        all.add(sPkg);
                        Config.addTargetPackage(this, sPkg);
                    }
                }
            }

            PackageManager pm = getPackageManager();
            List<TargetAppItem> list = new ArrayList<>();

            for (String pkg : all) {
                if (pkg == null || pkg.isEmpty() || pkg.equals(getPackageName())) continue;
                String label = pkg;
                Drawable icon = null;
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                    label = pm.getApplicationLabel(ai).toString();
                    icon = pm.getApplicationIcon(ai);
                } catch (Throwable ignored) {}

                String json = Config.getIdentity(this, pkg);
                Identity id = (json != null && json.startsWith("{")) ? Identity.fromJson(json) : null;
                if (id == null) {
                    id = IdentityGenerator.generateRandom();
                    Config.setIdentity(this, pkg, id.toJson());
                    writeSentinelToApp(pkg, id.toJson());
                }

                boolean inScope;
                synchronized (scopeCache) {
                    inScope = scopeCache.contains(pkg);
                }

                boolean isSpoofEnabled = Config.isTargetPackage(this, pkg);
                list.add(new TargetAppItem(pkg, label, icon, inScope, isSpoofEnabled, id));
            }

            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                targetAppItems.clear();
                targetAppItems.addAll(list);
                adapter.setItems(targetAppItems);
                updateStatCards(LSPosedScopeHelper.isConnected(), scopeCache.size());

                boolean empty = targetAppItems.isEmpty();
                layoutEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
                rvTargetApps.setVisibility(empty ? View.GONE : View.VISIBLE);
            });
        });
    }

    // ================= App Action Callbacks =================

    @Override
    public void onToggleSpoof(TargetAppItem item, boolean enabled) {
        if (enabled) {
            Config.addTargetPackage(this, item.packageName);
            if (item.identity == null) {
                item.identity = IdentityGenerator.generateRandom();
                Config.setIdentity(this, item.packageName, item.identity.toJson());
                writeSentinelToApp(item.packageName, item.identity.toJson());
            }
        } else {
            Config.removeTargetPackage(this, item.packageName);
        }
        item.isSpoofEnabled = enabled;
        adapter.notifyDataSetChanged();
        updateStatCards(LSPosedScopeHelper.isConnected(), scopeCache.size());
    }

    @Override
    public void onCustomize(TargetAppItem item) {
        CustomizeSpoofDialog dlg = new CustomizeSpoofDialog(
                this, item.packageName, item.appName, item.icon, item.identity, currentLang,
                updatedIdentity -> {
                    item.identity = updatedIdentity;
                    item.isSpoofEnabled = true;
                    Config.addTargetPackage(this, item.packageName);
                    Config.setIdentity(this, item.packageName, updatedIdentity.toJson());
                    writeSentinelToApp(item.packageName, updatedIdentity.toJson());
                    adapter.notifyDataSetChanged();
                    updateStatCards(LSPosedScopeHelper.isConnected(), scopeCache.size());

                    Snackbar.make(rvTargetApps, isAr() ? "تم حفظ إعدادات السبوف بنجاح" : "Spoof settings applied",
                            Snackbar.LENGTH_SHORT).show();
                }
        );
        dlg.show();
    }

    @Override
    public void onQuickRandom(TargetAppItem item) {
        if (item.identity == null) {
            item.identity = IdentityGenerator.generateRandom();
        } else {
            IdentityGenerator.randomizeEnabledOnly(item.identity);
        }
        item.isSpoofEnabled = true;
        Config.addTargetPackage(this, item.packageName);
        Config.setIdentity(this, item.packageName, item.identity.toJson());
        writeSentinelToApp(item.packageName, item.identity.toJson());
        adapter.notifyDataSetChanged();

        Toast.makeText(this, (isAr() ? "تم توليد قيم عشوائية جديدة: " : "Generated fresh identity for: ") + item.appName,
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onLaunchApp(TargetAppItem item) {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(item.packageName);
            if (launch != null) {
                startActivity(launch);
            } else {
                Toast.makeText(this, isAr() ? "تعذر تشغيل التطبيق" : "Cannot launch app", Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable t) {
            Toast.makeText(this, t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onClearData(TargetAppItem item) {
        new AlertDialog.Builder(this)
                .setTitle(isAr() ? "مسح بيانات التطبيق" : "Clear App Data")
                .setMessage((isAr() ? "هل تريد مسح بيانات " : "Clear data for ") + item.appName + "?")
                .setPositiveButton(isAr() ? "مسح" : "Clear", (dialog, which) -> {
                    backgroundPool.execute(() -> {
                        runSu("pm clear " + item.packageName);
                        // Re-write sentinel so new identity applies automatically
                        if (item.identity != null) {
                            writeSentinelToApp(item.packageName, item.identity.toJson());
                        }
                        mainHandler.post(() -> {
                            Toast.makeText(this, isAr() ? "تم مسح بيانات التطبيق بنجاح" : "App data cleared", Toast.LENGTH_SHORT).show();
                        });
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onForceStop(TargetAppItem item) {
        backgroundPool.execute(() -> {
            runSu("am force-stop " + item.packageName);
            mainHandler.post(() -> {
                Toast.makeText(this, isAr() ? "تم إيقاف التطبيق" : "App stopped", Toast.LENGTH_SHORT).show();
            });
        });
    }

    @Override
    public void onRemoveTarget(TargetAppItem item) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.remove_target)
                .setMessage((isAr() ? "إزالة " : "Remove ") + item.appName + (isAr() ? " من قائمة السبوف؟" : " from spoof list?"))
                .setPositiveButton(isAr() ? "إزالة" : "Remove", (dialog, which) -> {
                    Config.removeTargetPackage(this, item.packageName);
                    Config.removeIdentity(this, item.packageName);
                    loadTargetApps();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void writeSentinelToApp(String packageName, String json) {
        backgroundPool.execute(() -> {
            String dir = "/data/user/0/" + packageName + "/files";
            String sentinel = dir + "/.identity_sentinel";
            String extDir = "/sdcard/Android/data/" + packageName + "/files";
            String extSentinel = extDir + "/.identity_sentinel";

            String cmd = "mkdir -p '" + dir + "' '" + extDir + "'\n"
                    + "cat > '" + sentinel + "' << 'DRS_EOF'\n" + json + "\nDRS_EOF\n"
                    + "chmod 666 '" + sentinel + "'\n"
                    + "cat > '" + extSentinel + "' << 'DRS_EOF'\n" + json + "\nDRS_EOF\n"
                    + "chmod 666 '" + extSentinel + "'\n";
            runSu(cmd);
        });
    }

    // ================= Engine Page Setup =================

    private void setupEnginePage() {
        LinearLayout engineLayout = findViewById(R.id.layout_engine_switches);
        if (engineLayout == null) return;
        engineLayout.removeAllViews();

        addEngineSection(engineLayout, isAr() ? "السلوك العام" : "General Behavior");
        addEngineSwitch(engineLayout, isAr() ? "إعادة التوليد التلقائي عند مسح البيانات" : "Auto Reset on Clear Data",
                "Generate new values when app data is erased", Config.isAutoReset(this), (v, c) -> Config.setAutoReset(this, c));

        addEngineSection(engineLayout, isAr() ? "مفاتيح الحماية والاعتراض العامة" : "Global Hook Interceptors");
        addEngineSwitch(engineLayout, "Android ID (SSAID)", "Hook Settings.Secure.getString(android_id)",
                Config.isHookAndroidId(this), (v, c) -> Config.setHookAndroidId(this, c));
        addEngineSwitch(engineLayout, "Advertising ID (AAID)", "Hook Google AdvertisingIdClient & AppSetId",
                Config.isHookAdId(this), (v, c) -> Config.setHookAdId(this, c));
        addEngineSwitch(engineLayout, "Telephony (IMEI / MEID / IMSI)", "Hook TelephonyManager identifiers",
                Config.isHookImei(this), (v, c) -> Config.setHookImei(this, c));
        addEngineSwitch(engineLayout, "Device Model (Build Info)", "Hook Build class static fields",
                Config.isHookBuild(this), (v, c) -> Config.setHookBuild(this, c));
        addEngineSwitch(engineLayout, "Wi-Fi & Bluetooth MAC", "Hook WifiInfo and BluetoothAdapter",
                Config.isHookMac(this), (v, c) -> Config.setHookMac(this, c));
        addEngineSwitch(engineLayout, "Google Services (GSF ID)", "Hook Google Services Framework content provider",
                Config.isHookGsf(this), (v, c) -> Config.setHookGsf(this, c));
        addEngineSwitch(engineLayout, "Carrier Info", "Hook Network & SIM operator details",
                Config.isHookCarrier(this), (v, c) -> Config.setHookCarrier(this, c));
    }

    private void addEngineSection(LinearLayout parent, String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(0xFF38BDF8);
        tv.setTextSize(14);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding(0, 16, 0, 8);
        parent.addView(tv);
    }

    private void addEngineSwitch(LinearLayout parent, String title, String subtitle,
                                 boolean checked, SwitchMaterial.OnCheckedChangeListener listener) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(0xFF1E273E);
        card.setStrokeColor(0xFF2E3D59);
        card.setStrokeWidth(1);
        card.setRadius(12 * getResources().getDisplayMetrics().density);

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        card.setLayoutParams(clp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int pad = (int) (14 * getResources().getDisplayMetrics().density);
        row.setPadding(pad, pad, pad, pad);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(tlp);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(0xFFF8FAFC);
        tvTitle.setTextSize(14);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        textCol.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText(subtitle);
        tvSub.setTextColor(0xFF94A3B8);
        tvSub.setTextSize(11);
        textCol.addView(tvSub);

        row.addView(textCol);

        SwitchMaterial sm = new SwitchMaterial(this);
        sm.setChecked(checked);
        sm.setOnCheckedChangeListener(listener);
        row.addView(sm);

        card.addView(row);
        parent.addView(card);
    }

    // ================= Logs & Diagnostics =================

    private void loadLogs() {
        if (tvLogsContent == null) return;
        backgroundPool.execute(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("=== DeviceReset Spoofer Diagnostics ===\n");
            sb.append("Timestamp: ").append(System.currentTimeMillis()).append("\n");
            sb.append("Active Targets: ").append(targetAppItems.size()).append("\n\n");

            // Read logcat grep for DeviceReset
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-s", "DeviceReset:I", "DeviceResetNative:I", "*:S"});
                BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                int count = 0;
                while ((line = r.readLine()) != null && count < 100) {
                    sb.append(line).append("\n");
                    count++;
                }
                r.close();
            } catch (Throwable ignored) {}

            String content = sb.toString();
            mainHandler.post(() -> {
                if (tvLogsContent != null) tvLogsContent.setText(content);
            });
        });
    }

    private void exportLogs() {
        if (tvLogsContent == null) return;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "DeviceReset Spoof Diagnostics");
        share.putExtra(Intent.EXTRA_TEXT, tvLogsContent.getText().toString());
        startActivity(Intent.createChooser(share, "Export Logs"));
    }

    // ================= Language & Dialogs =================

    private void showLanguageSelector() {
        String[] langs = {"العربية (Arabic)", "English", "Русский (Russian)", "中文 (Chinese)"};
        String[] codes = {"ar", "en", "ru", "zh"};

        new AlertDialog.Builder(this)
                .setTitle(isAr() ? "اختر اللغة" : "Select Language")
                .setItems(langs, (dialog, which) -> {
                    String selected = codes[which];
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(PREF_LANG, selected).apply();
                    currentLang = selected;
                    recreate();
                })
                .show();
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("DeviceReset Spoofer")
                .setMessage("DeviceReset Spoofer X\nVersion 4.8.4\n\n"
                        + "Advanced LSPosed Identity Virtualizer with Granular Spoof Control.\n"
                        + "GitHub: https://github.com/mohmedmarawan712-arch/DeviceResetSpoofer")
                .setPositiveButton("OK", null)
                .show();
    }

    private boolean isAr() {
        return "ar".equals(currentLang);
    }

    private static String runSu(String cmd) {
        StringBuilder sb = new StringBuilder();
        try {
            Process p = Runtime.getRuntime().exec("su");
            java.io.DataOutputStream os = new java.io.DataOutputStream(p.getOutputStream());
            os.writeBytes(cmd + "\nexit\n");
            os.flush();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String l;
            while ((l = r.readLine()) != null) sb.append(l).append('\n');
            p.waitFor();
        } catch (Throwable ignored) {}
        return sb.toString();
    }
}
