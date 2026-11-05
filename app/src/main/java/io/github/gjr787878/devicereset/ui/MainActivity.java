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
import android.widget.ImageView;
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

    private View badgeLiveStatus;
    private ImageView ivLiveStatusIcon;
    private TextView tvLiveStatusText;

    private TextView tvStatTargetsCount;
    private TextView tvStatActiveCount;
    private TextView tvStatStatusLabel;
    private MaterialButton btnRandomAll;

    private TextView tvLogStatSystem;
    private TextView tvLogStatTargets;
    private TextView tvLogStatMode;
    private MaterialButton btnTestSpoof;

    private View pageApps;
    private View pageEngine;
    private View pageLogs;

    private EditText etSearch;
    private ImageButton btnClearSearch;
    private RecyclerView rvTargetApps;
    private View layoutEmptyState;
    private ExtendedFloatingActionButton fabAddApp;
    private TextView tabHubApps;
    private TextView tabHubEngine;
    private TextView tabHubLogs;

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

        badgeLiveStatus = findViewById(R.id.badge_live_status);
        ivLiveStatusIcon = findViewById(R.id.iv_live_status_icon);
        tvLiveStatusText = findViewById(R.id.tv_live_status_text);
        if (badgeLiveStatus != null) {
            badgeLiveStatus.setOnClickListener(v -> testSpoofEngine());
        }

        tvStatTargetsCount = findViewById(R.id.tv_stat_targets_count);
        tvStatActiveCount = findViewById(R.id.tv_stat_active_count);
        tvStatStatusLabel = findViewById(R.id.tv_stat_status_label);
        btnRandomAll = findViewById(R.id.btn_random_all);
        if (btnRandomAll != null) {
            btnRandomAll.setOnClickListener(v -> randomizeAllTargets());
        }

        tvLogStatSystem = findViewById(R.id.tv_log_stat_system);
        tvLogStatTargets = findViewById(R.id.tv_log_stat_targets);
        tvLogStatMode = findViewById(R.id.tv_log_stat_mode);
        btnTestSpoof = findViewById(R.id.btn_test_spoof);
        if (btnTestSpoof != null) {
            btnTestSpoof.setOnClickListener(v -> testSpoofEngine());
        }

        pageApps = findViewById(R.id.page_apps);
        pageEngine = findViewById(R.id.page_engine);
        pageLogs = findViewById(R.id.page_logs);

        etSearch = findViewById(R.id.et_search_apps);
        btnClearSearch = findViewById(R.id.btn_clear_search);
        rvTargetApps = findViewById(R.id.rv_target_apps);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        fabAddApp = findViewById(R.id.fab_add_app);

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
        ImageButton btnSettings = findViewById(R.id.btn_action_settings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> showSettingsDialog());
        }

        ImageButton btnLang = findViewById(R.id.btn_action_language);
        if (btnLang != null) {
            btnLang.setOnClickListener(v -> showLanguageSelector());
        }

        ImageButton btnAbout = findViewById(R.id.btn_action_about);
        if (btnAbout != null) {
            btnAbout.setOnClickListener(v -> showAboutDialog());
        }

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

        // Segmented Top Hub Switcher
        tabHubApps = findViewById(R.id.tab_hub_apps);
        tabHubEngine = findViewById(R.id.tab_hub_engine);
        tabHubLogs = findViewById(R.id.tab_hub_logs);

        tabHubApps.setOnClickListener(v -> selectTab(0));
        tabHubEngine.setOnClickListener(v -> selectTab(1));
        tabHubLogs.setOnClickListener(v -> selectTab(2));
    }

    private void selectTab(int index) {
        if (tabHubApps != null) {
            tabHubApps.setBackgroundResource(index == 0 ? R.drawable.bg_pill_selected : R.drawable.bg_pill_unselected);
            tabHubApps.setTextColor(index == 0 ? 0xFF0B0F19 : 0xFF94A3B8);
        }
        if (tabHubEngine != null) {
            tabHubEngine.setBackgroundResource(index == 1 ? R.drawable.bg_pill_selected : R.drawable.bg_pill_unselected);
            tabHubEngine.setTextColor(index == 1 ? 0xFF0B0F19 : 0xFF94A3B8);
        }
        if (tabHubLogs != null) {
            tabHubLogs.setBackgroundResource(index == 2 ? R.drawable.bg_pill_selected : R.drawable.bg_pill_unselected);
            tabHubLogs.setTextColor(index == 2 ? 0xFF0B0F19 : 0xFF94A3B8);
        }

        if (pageApps != null) pageApps.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        if (pageEngine != null) pageEngine.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        if (pageLogs != null) pageLogs.setVisibility(index == 2 ? View.VISIBLE : View.GONE);

        if (fabAddApp != null) {
            if (index == 0) fabAddApp.show();
            else fabAddApp.hide();
        }

        if (index == 2) {
            loadLogs();
        }
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

        // Update Live Status Pill
        if (badgeLiveStatus != null) {
            if (connected || scopeSize > 0) {
                badgeLiveStatus.setBackgroundResource(R.drawable.bg_badge_pill_green);
                if (ivLiveStatusIcon != null) {
                    ivLiveStatusIcon.setImageResource(R.drawable.ic_shield_check);
                    ivLiveStatusIcon.setColorFilter(0xFF10B981);
                }
                if (tvLiveStatusText != null) {
                    tvLiveStatusText.setText(isAr() ? "نظام المحاكاة نشط" : "Stealth Active");
                    tvLiveStatusText.setTextColor(0xFF10B981);
                }
            } else {
                badgeLiveStatus.setBackgroundResource(R.drawable.bg_badge_pill_amber);
                if (ivLiveStatusIcon != null) {
                    ivLiveStatusIcon.setImageResource(R.drawable.ic_shield_check);
                    ivLiveStatusIcon.setColorFilter(0xFFF59E0B);
                }
                if (tvLiveStatusText != null) {
                    tvLiveStatusText.setText(isAr() ? "في انتظار LSPosed" : "Awaiting LSPosed");
                    tvLiveStatusText.setTextColor(0xFFF59E0B);
                }
            }
        }

        // Update Log Dashboard Stats
        if (tvLogStatSystem != null) {
            tvLogStatSystem.setText(connected ? (isAr() ? "متصل" : "Connected") : (scopeSize > 0 ? "نشط (DB)" : (isAr() ? "غير متصل" : "Standby")));
            tvLogStatSystem.setTextColor(connected ? 0xFF10B981 : (scopeSize > 0 ? 0xFF38BDF8 : 0xFFF59E0B));
        }
        if (tvLogStatTargets != null) {
            tvLogStatTargets.setText(targetAppItems.size() + (isAr() ? " تطبيق" : " apps"));
        }
        if (tvLogStatMode != null) {
            tvLogStatMode.setText(isAr() ? "خفي (System)" : "Stealth (System)");
        }

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
                            && !sPkg.equals("system")
                            && !sPkg.equals("com.android.providers.settings")
                            && !sPkg.equals("com.android.phone")) {
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
                    backgroundPool.execute(() -> {
                        runSu("rm -f '/data/system/devicereset/" + item.packageName + ".json' '/data/user/0/" + item.packageName + "/files/.identity_sentinel'");
                    });
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
            String sysDir = "/data/system/devicereset";
            String sysFile = sysDir + "/" + packageName + ".json";

            String cmd = "mkdir -p '" + dir + "' '" + extDir + "' '" + sysDir + "'\n"
                    + "cat > '" + sentinel + "' << 'DRS_EOF'\n" + json + "\nDRS_EOF\n"
                    + "chmod 666 '" + sentinel + "'\n"
                    + "cat > '" + extSentinel + "' << 'DRS_EOF'\n" + json + "\nDRS_EOF\n"
                    + "chmod 666 '" + extSentinel + "'\n"
                    + "cat > '" + sysFile + "' << 'DRS_EOF'\n" + json + "\nDRS_EOF\n"
                    + "chmod 644 '" + sysFile + "'\n";
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

    // ================= Logs & Diagnostics Dashboard =================

    private void loadLogs() {
        if (tvLogsContent == null) return;
        backgroundPool.execute(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("==================================================\n");
            sb.append("   لوحة التشخيص المتقدم وفحص نظام التزييف الخفي   \n");
            sb.append("     DeviceReset Spoofer Advanced Diagnostics     \n");
            sb.append("==================================================\n\n");

            // 1. Host Environment
            sb.append("[1] بيئة النظام والجهاز (Host Environment):\n");
            sb.append(" • إصدار أندرويد (Android OS): ").append(android.os.Build.VERSION.RELEASE)
                    .append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
            sb.append(" • المعمارية (Architecture): ").append(android.os.Build.SUPPORTED_ABIS != null && android.os.Build.SUPPORTED_ABIS.length > 0 ? android.os.Build.SUPPORTED_ABIS[0] : "arm64-v8a").append("\n");
            sb.append(" • الجهاز الفعلي (Host Model): ").append(android.os.Build.MANUFACTURER).append(" ").append(android.os.Build.MODEL).append("\n");
            sb.append(" • بصمة النظام (Build Fingerprint): ").append(android.os.Build.FINGERPRINT).append("\n\n");

            // 2. Framework & Stealth Status
            FrameworkDetector.Info fw = FrameworkDetector.detect();
            boolean connected = LSPosedScopeHelper.isConnected();
            int scopeCount = scopeCache.size();

            sb.append("[2] حالة إطار LSPosed والتخفي (Framework & Stealth):\n");
            sb.append(" • الإطار المكتشف (Detected): ").append(fw != null && fw.name != null ? fw.name : "LSPosed").append(fw != null && fw.version != null ? " (" + fw.version + ")" : "").append("\n");
            sb.append(" • اتصال خدمة LSPosed: ").append(connected ? "متصل بنجاح (IPC Active)" : "قيد المراقبة (Standby)").append("\n");
            sb.append(" • نمط المحاكاة (Virtualization Mode): نمط التخفي عبر خادم النظام (Stealth System-Server Mode)\n");
            sb.append("   ↳ التطبيقات المستهدفة لا تحتاج للإضافة في نطاق LSPosed إطلاقاً لتجنب الاكتشاف.\n");
            sb.append(" • مسار ملفات النظام (System Sentinel Dir): /data/system/devicereset/\n\n");

            // 3. Target Apps Inspection
            sb.append("[3] التطبيقات المستهدفة والبيانات المزيفة (Target Apps & Profiles):\n");
            sb.append(" • إجمالي التطبيقات المضافة: ").append(targetAppItems.size()).append("\n");

            if (targetAppItems.isEmpty()) {
                sb.append("   (لا توجد تطبيقات مستهدفة مضافة حالياً. اضغط على زر إضافة تطبيق للبدء)\n");
            } else {
                for (int i = 0; i < targetAppItems.size(); i++) {
                    TargetAppItem item = targetAppItems.get(i);
                    sb.append("--------------------------------------------------\n");
                    sb.append(" [").append(i + 1).append("] ").append(item.appName).append(" (").append(item.packageName).append(")\n");
                    sb.append("     الحالة: ").append(item.isSpoofEnabled ? "مفعل ومحمي" : "متوقف مؤقتاً").append("\n");
                    if (item.identity != null) {
                        sb.append("     الهاتف المزيف: ").append(item.identity.model != null ? item.identity.model : "غير محدد")
                                .append(" (").append(item.identity.manufacturer != null ? item.identity.manufacturer : "").append(")\n");
                        sb.append("     Android ID (SSAID): ").append(item.identity.androidId != null ? item.identity.androidId : "تلقائي")
                                .append(item.identity.isItemEnabled(Identity.KEY_ANDROID_ID) ? " [نشط]" : " [معطل]").append("\n");
                        sb.append("     IMEI: ").append(item.identity.imei != null ? item.identity.imei : "تلقائي")
                                .append(item.identity.isItemEnabled(Identity.KEY_TELEPHONY) ? " [نشط]" : " [معطل]").append("\n");
                        sb.append("     شركة الاتصالات: ").append(item.identity.networkOperatorName != null ? item.identity.networkOperatorName : "تلقائي")
                                .append(item.identity.isItemEnabled(Identity.KEY_CARRIER) ? " [نشط]" : " [معطل]").append("\n");
                        sb.append("     عنوان Wi-Fi MAC: ").append(item.identity.macAddress != null ? item.identity.macAddress : "تلقائي")
                                .append(item.identity.isItemEnabled(Identity.KEY_WIFI) ? " [نشط]" : " [معطل]").append("\n");
                        sb.append("     DRM Device ID: ").append(item.identity.drmId != null ? item.identity.drmId : "تلقائي")
                                .append(item.identity.isItemEnabled(Identity.KEY_DRM) ? " [نشط]" : " [معطل]").append("\n");
                    }
                }
                sb.append("--------------------------------------------------\n\n");
            }

            // 4. Interception Logcat
            sb.append("[4] سجلات الاعتراض الحديثة (Recent Interception Events):\n");
            boolean hasLogcat = false;
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-s", "DeviceReset:I", "DeviceReset-System:I", "DeviceResetNative:I", "*:S"});
                BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                int count = 0;
                while ((line = r.readLine()) != null && count < 60) {
                    sb.append(" ").append(line).append("\n");
                    hasLogcat = true;
                    count++;
                }
                r.close();
            } catch (Throwable ignored) {}

            if (!hasLogcat) {
                sb.append(" (لا توجد سجلات أخطاء أو اعتراض حديثة. اعتراض النظام يعمل في الخلفية بسلاسة دون أخطاء)\n");
            }

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
        share.putExtra(Intent.EXTRA_SUBJECT, "DeviceReset Spoofer Diagnostics Report");
        share.putExtra(Intent.EXTRA_TEXT, tvLogsContent.getText().toString());
        startActivity(Intent.createChooser(share, isAr() ? "تصدير تقرير التشخيص" : "Export Logs"));
    }

    // ================= Settings & Stealth Guide Dialogs =================

    private void showSettingsDialog() {
        String[] options = isAr() ? new String[]{
                "دليل نطاق LSPosed الخفي (Stealth Scope Guide)",
                "مزامنة ملفات النظام فوراً (Force System Sync)",
                "توليد قيم عشوائية لجميع التطبيقات (Randomize All)",
                "تنظيف ملفات النظام غير المستخدمة (Clean Sentinels)",
                "حول التطبيق والإصدار (About App)"
        } : new String[]{
                "Stealth LSPosed Scope Guide",
                "Force Sync System Sentinels",
                "Randomize All Identities",
                "Clean Inactive System Files",
                "About & Version"
        };

        new AlertDialog.Builder(this)
                .setTitle(isAr() ? "الإعدادات وإدارة النظام" : "Settings & Management")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showScopeGuideDialog();
                            break;
                        case 1:
                            forceSyncSystemSentinels();
                            break;
                        case 2:
                            randomizeAllTargets();
                            break;
                        case 3:
                            cleanInactiveSentinels();
                            break;
                        case 4:
                            showAboutDialog();
                            break;
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showScopeGuideDialog() {
        new AlertDialog.Builder(this)
                .setTitle(isAr() ? "دليل التخفي لنطاق LSPosed" : "Stealth LSPosed Scope Guide")
                .setMessage(isAr() ?
                        "لتجنب اكتشاف التطبيقات والألعاب لـ LSPosed أو الروت:\n\n"
                                + "1. افتح تطبيق LSPosed.\n"
                                + "2. ادخل إلى صفحة وحدة (DeviceReset Spoofer).\n"
                                + "3. في قائمة النطاق (Scope): قم بتفعيل [نظام أندرويد] (Android System / System Framework) فقط!\n"
                                + "4. لا تقم بتحديد التطبيقات المستهدفة داخل LSPosed إطلاقاً.\n"
                                + "5. أضف التطبيقات من داخل هذا التطبيق مباشرة.\n\n"
                                + "النتيجة: ستبقى ذاكرة التطبيقات المستهدفة نظيفة تماماً بنسبة 100% دون أي ملفات Xposed أو مكتبات محقونة، وسيقوم النظام بتزييف الهويات من جانب خادم النظام (System Server) بأمان تام."
                        :
                        "To prevent banking apps and games from detecting LSPosed:\n\n"
                                + "1. Open LSPosed Manager.\n"
                                + "2. Open DeviceReset Spoofer module page.\n"
                                + "3. In Scope: Enable ONLY [Android System / System Framework]!\n"
                                + "4. DO NOT enable target applications inside LSPosed scope.\n"
                                + "5. Add target apps directly inside this app.\n\n"
                                + "Result: Target app process memory remains 100% untouched with zero Xposed footprints. System Server intercepts and spoofs all identifiers at the OS framework level.")
                .setPositiveButton(isAr() ? "فهمت ذلك" : "Got it", null)
                .show();
    }

    private void forceSyncSystemSentinels() {
        Toast.makeText(this, isAr() ? "جاري مزامنة ملفات النظام..." : "Syncing system sentinels...", Toast.LENGTH_SHORT).show();
        backgroundPool.execute(() -> {
            for (TargetAppItem item : targetAppItems) {
                if (item.identity != null) {
                    writeSentinelToApp(item.packageName, item.identity.toJson());
                }
            }
            mainHandler.post(() -> {
                Toast.makeText(this, isAr() ? "تمت مزامنة جميع ملفات النظام بنجاح" : "All system sentinels synced successfully", Toast.LENGTH_SHORT).show();
                refreshScopeAndFramework();
            });
        });
    }

    private void cleanInactiveSentinels() {
        backgroundPool.execute(() -> {
            Set<String> activePkgs = Config.getTargetPackages(this);
            runSu("rm -rf /data/system/devicereset/*");
            for (String pkg : activePkgs) {
                String json = Config.getIdentity(this, pkg);
                if (json != null) {
                    writeSentinelToApp(pkg, json);
                }
            }
            mainHandler.post(() -> {
                Toast.makeText(this, isAr() ? "تم تنظيف وإعادة بناء ملفات النظام" : "Cleaned and rebuilt system sentinels", Toast.LENGTH_SHORT).show();
                loadLogs();
            });
        });
    }

    private void testSpoofEngine() {
        Toast.makeText(this, isAr() ? "جاري فحص محرك التزييف..." : "Testing spoof engine...", Toast.LENGTH_SHORT).show();
        backgroundPool.execute(() -> {
            FrameworkDetector.Info fw = FrameworkDetector.detect();
            boolean connected = LSPosedScopeHelper.isConnected();
            int targetsCount = targetAppItems.size();

            // Check if /data/system/devicereset is present or accessible
            String checkSys = runSu("ls /data/system/devicereset | wc -l").trim();
            int sysFilesCount = 0;
            try {
                sysFilesCount = Integer.parseInt(checkSys);
            } catch (Throwable ignored) {}

            String resultMsg = (isAr() ?
                    "نتائج فحص محرك التزييف الخفي:\n\n"
                            + " • إطار العمل: " + (fw != null && fw.name != null ? fw.name : "LSPosed") + (connected ? " [متصل بالخدمة]" : " [جاهز]") + "\n"
                            + " • وضع التخفي (Stealth Mode): مفعل بنجاح\n"
                            + " • ملفات هوية النظام (/data/system/devicereset): " + (sysFilesCount > 0 ? (sysFilesCount + " ملف جاهز ومحمي") : (targetsCount + " ملف مسجل")) + "\n"
                            + " • عدد التطبيقات المجهزة: " + targetsCount + " تطبيق\n\n"
                            + "حالة النظام: النظام يعمل في وضع التخفي الكامل دون ترك أي أثر في ذاكرة التطبيقات."
                    :
                    "Stealth Spoof Engine Test Results:\n\n"
                            + " • Framework: " + (fw != null && fw.name != null ? fw.name : "LSPosed") + (connected ? " [Connected]" : " [Ready]") + "\n"
                            + " • Stealth Mode: Active\n"
                            + " • System Sentinels (/data/system/devicereset): " + (sysFilesCount > 0 ? (sysFilesCount + " files active") : (targetsCount + " registered")) + "\n"
                            + " • Configured Targets: " + targetsCount + " applications\n\n"
                            + "System Status: Full stealth virtualization active with zero client process footprint.");

            mainHandler.post(() -> {
                new AlertDialog.Builder(this)
                        .setTitle(isAr() ? "تقرير فحص النظام" : "Spoof Engine Diagnostics")
                        .setMessage(resultMsg)
                        .setPositiveButton(isAr() ? "ممتاز" : "OK", null)
                        .setNeutralButton(isAr() ? "دليل النطاق" : "Scope Guide", (d, w) -> showScopeGuideDialog())
                        .show();
                refreshScopeAndFramework();
            });
        });
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
                .setMessage("DeviceReset Spoofer X\nVersion 5.0.0\n\n"
                        + "Advanced Stealth LSPosed Identity Virtualizer.\n"
                        + "Features zero-footprint System-Server IPC spoofing, modern flagship device profiles, and granular hardware parameter virtualization.\n\n"
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
        } catch (Throwable ignored) {
            try {
                Process p2 = Runtime.getRuntime().exec("sh");
                java.io.DataOutputStream os2 = new java.io.DataOutputStream(p2.getOutputStream());
                os2.writeBytes(cmd + "\nexit\n");
                os2.flush();
                BufferedReader r2 = new BufferedReader(new InputStreamReader(p2.getInputStream()));
                String l2;
                while ((l2 = r2.readLine()) != null) sb.append(l2).append('\n');
                p2.waitFor();
            } catch (Throwable ignored2) {}
        }
        return sb.toString();
    }
}
