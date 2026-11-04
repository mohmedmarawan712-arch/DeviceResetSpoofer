package io.github.gjr787878.devicereset.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.gjr787878.devicereset.Config;
import io.github.gjr787878.devicereset.LSPosedScopeHelper;
import io.github.gjr787878.devicereset.R;
import io.github.gjr787878.devicereset.xposed.Identity;
import io.github.gjr787878.devicereset.xposed.IdentityGenerator;

public class AppPickerActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "devicereset_ui";
    private static final String PREF_LANG = "app_language";

    private final ExecutorService backgroundPool = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String currentLang = "ar";

    private ImageButton btnBack;
    private TextView tvCount;
    private EditText etSearch;
    private ProgressBar pbLoading;
    private TextView tvLoading;
    private TextView tvEmpty;
    private RecyclerView rvApps;

    private AppPickerAdapter adapter;
    private final List<AppItem> allApps = new ArrayList<>();
    private final List<AppItem> displayApps = new ArrayList<>();

    static class AppItem {
        String packageName;
        String appName;
        Drawable icon;
        boolean isTarget;
        boolean inScope;
        boolean isLaunchable;
        Identity identity;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentLang = prefs.getString(PREF_LANG, "ar");

        setContentView(R.layout.activity_app_picker);

        initViews();
        loadAllApps();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btn_picker_back);
        tvCount = findViewById(R.id.tv_count);
        etSearch = findViewById(R.id.et_picker_search);
        pbLoading = findViewById(R.id.pb_loading);
        tvLoading = findViewById(R.id.tv_loading);
        tvEmpty = findViewById(R.id.tv_empty);
        rvApps = findViewById(R.id.rv_apps);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        rvApps.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppPickerAdapter();
        rvApps.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Set localized loading text
        if (isAr()) {
            tvLoading.setText("جاري تحميل التطبيقات...");
            tvEmpty.setText("لم يتم العثور على أي تطبيق");
        } else if ("zh".equals(currentLang)) {
            tvLoading.setText("正在加载已安装应用...");
            tvEmpty.setText("未找到应用");
        } else if ("ru".equals(currentLang)) {
            tvLoading.setText("Загрузка приложений...");
            tvEmpty.setText("Приложения не найдены");
        } else {
            tvLoading.setText("Loading installed applications...");
            tvEmpty.setText("No applications found");
        }
    }

    private void loadAllApps() {
        pbLoading.setVisibility(View.VISIBLE);
        tvLoading.setVisibility(View.VISIBLE);
        rvApps.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);

        backgroundPool.execute(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> installed = new ArrayList<>();
            try {
                installed = pm.getInstalledApplications(0);
            } catch (Throwable t) {
                try {
                    List<android.content.pm.PackageInfo> pkgs = pm.getInstalledPackages(0);
                    for (android.content.pm.PackageInfo pi : pkgs) {
                        if (pi.applicationInfo != null) installed.add(pi.applicationInfo);
                    }
                } catch (Throwable ignored) {}
            }

            if (installed == null || installed.isEmpty()) {
                try {
                    Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
                    mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                    List<android.content.pm.ResolveInfo> resolves = pm.queryIntentActivities(mainIntent, 0);
                    for (android.content.pm.ResolveInfo ri : resolves) {
                        if (ri.activityInfo != null && ri.activityInfo.applicationInfo != null) {
                            installed.add(ri.activityInfo.applicationInfo);
                        }
                    }
                } catch (Throwable ignored) {}
            }

            if (installed == null || installed.isEmpty()) {
                try {
                    Process p = Runtime.getRuntime().exec("pm list packages");
                    BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String l;
                    while ((l = r.readLine()) != null) {
                        if (l.startsWith("package:")) {
                            String pkgName = l.substring(8).trim();
                            try {
                                installed.add(pm.getApplicationInfo(pkgName, 0));
                            } catch (Throwable ignored) {}
                        }
                    }
                    r.close();
                    p.waitFor();
                } catch (Throwable ignored) {}
            }

            Set<String> targetPkgs = Config.getTargetPackages(this);
            Set<String> scopePkgs = new HashSet<>();

            if (LSPosedScopeHelper.isConnected()) {
                List<String> s = LSPosedScopeHelper.getScope();
                if (s != null) scopePkgs.addAll(s);
            }
            Config.ScopeDbState dbState = Config.readLSPosedDbState(this);
            if (dbState != null && dbState.targets != null) {
                scopePkgs.addAll(dbState.targets);
            }

            String myPkg = getPackageName();
            List<AppItem> items = new ArrayList<>();

            for (ApplicationInfo ai : installed) {
                if (ai == null || ai.packageName == null || ai.packageName.equals(myPkg)) continue;

                AppItem item = new AppItem();
                item.packageName = ai.packageName;
                try {
                    item.appName = ai.loadLabel(pm).toString();
                } catch (Throwable e) {
                    item.appName = ai.packageName;
                }
                try {
                    item.icon = ai.loadIcon(pm);
                } catch (Throwable e) {
                    item.icon = getResources().getDrawable(android.R.drawable.sym_def_app_icon);
                }

                item.isTarget = targetPkgs.contains(ai.packageName);
                item.inScope = scopePkgs.contains(ai.packageName);
                item.isLaunchable = (pm.getLaunchIntentForPackage(ai.packageName) != null);

                String json = Config.getIdentity(this, ai.packageName);
                if (json != null && json.startsWith("{")) {
                    item.identity = Identity.fromJson(json);
                }

                items.add(item);
            }

            // Sort:
            // 1. Targets first
            // 2. Launchable (user visible) apps next
            // 3. Alphabetical by appName
            Collections.sort(items, (a, b) -> {
                if (a.isTarget != b.isTarget) return a.isTarget ? -1 : 1;
                if (a.isLaunchable != b.isLaunchable) return a.isLaunchable ? -1 : 1;
                return a.appName.compareToIgnoreCase(b.appName);
            });

            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;

                pbLoading.setVisibility(View.GONE);
                tvLoading.setVisibility(View.GONE);

                allApps.clear();
                allApps.addAll(items);

                filter(etSearch.getText().toString());
            });
        });
    }

    private void filter(String query) {
        displayApps.clear();
        if (query == null || query.trim().isEmpty()) {
            displayApps.addAll(allApps);
        } else {
            String q = query.toLowerCase(Locale.ROOT).trim();
            for (AppItem it : allApps) {
                if (it.appName.toLowerCase(Locale.ROOT).contains(q)
                        || it.packageName.toLowerCase(Locale.ROOT).contains(q)) {
                    displayApps.add(it);
                }
            }
        }

        adapter.notifyDataSetChanged();
        updateCount();

        boolean empty = displayApps.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvApps.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void updateCount() {
        int total = allApps.size();
        int targets = 0;
        for (AppItem it : allApps) {
            if (it.isTarget) targets++;
        }

        if (isAr()) {
            tvCount.setText(targets + " في السبوف • إجمالي " + total);
        } else if ("zh".equals(currentLang)) {
            tvCount.setText(targets + " 已添加 • 共 " + total);
        } else if ("ru".equals(currentLang)) {
            tvCount.setText(targets + " выбрано • всего " + total);
        } else {
            tvCount.setText(targets + " added • " + total + " total");
        }
    }

    private void onAppItemClicked(AppItem item) {
        if (!item.isTarget) {
            // 1-Tap Add to Targets!
            Config.addTargetPackage(this, item.packageName);
            if (item.identity == null) {
                item.identity = IdentityGenerator.generateRandom();
                Config.setIdentity(this, item.packageName, item.identity.toJson());
            }
            writeSentinelToApp(item.packageName, item.identity.toJson());

            // Background sync
            backgroundPool.execute(() -> {
                Set<String> targets = Config.getTargetPackages(this);
                Config.syncScopeToLSPosed(this, targets);
            });

            Toast.makeText(this, isAr() ? ("تمت إضافة " + item.appName + " بنجاح") : ("Added " + item.appName), Toast.LENGTH_SHORT).show();
            finish();
        } else {
            // Already added: show quick options
            String titleCustomize = isAr() ? "تخصيص الهوية" : "Customize Identity";
            String titleRemove = isAr() ? "إزالة من قائمة السبوف" : "Remove from Targets";

            String[] options = new String[]{titleCustomize, titleRemove};

            new AlertDialog.Builder(this)
                    .setTitle(item.appName)
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            CustomizeSpoofDialog dlg = new CustomizeSpoofDialog(
                                    this, item.packageName, item.appName, item.icon, item.identity, currentLang,
                                    updatedId -> {
                                        item.identity = updatedId;
                                        Config.setIdentity(this, item.packageName, updatedId.toJson());
                                        writeSentinelToApp(item.packageName, updatedId.toJson());
                                        Toast.makeText(this, isAr() ? "تم حفظ إعدادات السبوف" : "Spoof settings applied", Toast.LENGTH_SHORT).show();
                                    }
                            );
                            dlg.show();
                        } else if (which == 1) {
                            Config.removeTargetPackage(this, item.packageName);
                            item.isTarget = false;
                            adapter.notifyDataSetChanged();
                            updateCount();
                            Toast.makeText(this, isAr() ? "تمت الإزالة" : "Removed", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }
    }

    private void writeSentinelToApp(String packageName, String json) {
        backgroundPool.execute(() -> {
            try {
                String cmd = "mkdir -p /data/system/devicereset\n"
                        + "echo '" + json.replace("'", "'\\''") + "' > /data/system/devicereset/" + packageName + ".json\n"
                        + "chmod 644 /data/system/devicereset/" + packageName + ".json\n"
                        + "mkdir -p /data/data/" + packageName + "/files\n"
                        + "echo '" + json.replace("'", "'\\''") + "' > /data/data/" + packageName + "/files/devicereset_sentinel.json\n"
                        + "chmod 644 /data/data/" + packageName + "/files/devicereset_sentinel.json\n";
                Process p = Runtime.getRuntime().exec("su");
                java.io.DataOutputStream os = new java.io.DataOutputStream(p.getOutputStream());
                os.writeBytes(cmd);
                os.writeBytes("exit\n");
                os.flush();
                p.waitFor();
            } catch (Throwable ignored) {}
        });
    }

    private boolean isAr() {
        return "ar".equals(currentLang);
    }

    // ================= RecyclerView Adapter =================

    private class AppPickerAdapter extends RecyclerView.Adapter<AppPickerAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_picker, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            AppItem item = displayApps.get(position);
            holder.tvName.setText(item.appName);
            holder.tvPkg.setText(item.packageName);

            if (item.icon != null) {
                holder.ivIcon.setImageDrawable(item.icon);
            } else {
                holder.ivIcon.setImageResource(android.R.drawable.sym_def_app_icon);
            }

            if (item.isTarget) {
                holder.btnAction.setText(isAr() ? "مضاف" : "Added");
                holder.btnAction.setTextColor(0xFF10B981);
                holder.btnAction.setStrokeColor(android.content.res.ColorStateList.valueOf(0xFF10B981));
                holder.btnAction.setIconResource(R.drawable.ic_check);
                holder.btnAction.setIconTint(android.content.res.ColorStateList.valueOf(0xFF10B981));
            } else {
                holder.btnAction.setText(isAr() ? "إضافة" : "Add");
                holder.btnAction.setTextColor(0xFF38BDF8);
                holder.btnAction.setStrokeColor(android.content.res.ColorStateList.valueOf(0xFF38BDF8));
                holder.btnAction.setIconResource(R.drawable.ic_add);
                holder.btnAction.setIconTint(android.content.res.ColorStateList.valueOf(0xFF38BDF8));
            }

            holder.itemView.setOnClickListener(v -> onAppItemClicked(item));
            holder.btnAction.setOnClickListener(v -> onAppItemClicked(item));
        }

        @Override
        public int getItemCount() {
            return displayApps.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final ImageView ivIcon;
            final TextView tvName;
            final TextView tvPkg;
            final MaterialButton btnAction;

            VH(@NonNull View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.app_icon);
                tvName = itemView.findViewById(R.id.app_name);
                tvPkg = itemView.findViewById(R.id.app_package);
                btnAction = itemView.findViewById(R.id.btn_item_action);
            }
        }
    }
}
