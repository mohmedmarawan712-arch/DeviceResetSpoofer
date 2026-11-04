package io.github.gjr787878.devicereset.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.gjr787878.devicereset.R;

public class TargetAppAdapter extends RecyclerView.Adapter<TargetAppAdapter.VH> {

    public interface AppActionListener {
        void onToggleSpoof(TargetAppItem item, boolean enabled);
        void onCustomize(TargetAppItem item);
        void onQuickRandom(TargetAppItem item);
        void onLaunchApp(TargetAppItem item);
        void onClearData(TargetAppItem item);
        void onForceStop(TargetAppItem item);
        void onRemoveTarget(TargetAppItem item);
    }

    private final List<TargetAppItem> originalList = new ArrayList<>();
    private final List<TargetAppItem> displayList = new ArrayList<>();
    private final AppActionListener listener;
    private final String currentLang;

    public TargetAppAdapter(List<TargetAppItem> items, AppActionListener listener, String lang) {
        this.listener = listener;
        this.currentLang = lang;
        setItems(items);
    }

    public void setItems(List<TargetAppItem> items) {
        originalList.clear();
        if (items != null) originalList.addAll(items);
        displayList.clear();
        displayList.addAll(originalList);
        notifyDataSetChanged();
    }

    public void filter(String query) {
        displayList.clear();
        if (query == null || query.trim().isEmpty()) {
            displayList.addAll(originalList);
        } else {
            String q = query.toLowerCase(Locale.ROOT).trim();
            for (TargetAppItem item : originalList) {
                if (item.appName.toLowerCase(Locale.ROOT).contains(q)
                        || item.packageName.toLowerCase(Locale.ROOT).contains(q)) {
                    displayList.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_target_app, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        TargetAppItem item = displayList.get(position);
        holder.tvName.setText(item.appName);
        holder.tvPkg.setText(item.packageName);

        if (item.icon != null) {
            holder.ivIcon.setImageDrawable(item.icon);
        } else {
            holder.ivIcon.setImageResource(android.R.drawable.sym_def_app_icon);
        }

        // Switch
        holder.switchSpoof.setOnCheckedChangeListener(null);
        holder.switchSpoof.setChecked(item.isSpoofEnabled);
        holder.switchSpoof.setOnCheckedChangeListener((buttonView, isChecked) -> {
            item.isSpoofEnabled = isChecked;
            if (listener != null) listener.onToggleSpoof(item, isChecked);
            updateSummary(holder, item);
        });

        // Scope Indicator
        if (item.inScope) {
            holder.tvScopeStatus.setText("LSPosed Scope OK");
            holder.tvScopeStatus.setTextColor(0xFF10B981);
            GradientDrawable greenDot = new GradientDrawable();
            greenDot.setShape(GradientDrawable.OVAL);
            greenDot.setColor(0xFF10B981);
            holder.viewScopeDot.setBackground(greenDot);
        } else {
            holder.tvScopeStatus.setText("Not In Scope");
            holder.tvScopeStatus.setTextColor(0xFF94A3B8);
            GradientDrawable grayDot = new GradientDrawable();
            grayDot.setShape(GradientDrawable.OVAL);
            grayDot.setColor(0xFF64748B);
            holder.viewScopeDot.setBackground(grayDot);
        }

        updateSummary(holder, item);

        // Card click -> Customize directly
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onCustomize(item);
        });

        // Buttons
        holder.btnCustomize.setOnClickListener(v -> {
            if (listener != null) listener.onCustomize(item);
        });

        holder.btnQuickRandom.setOnClickListener(v -> {
            if (listener != null) listener.onQuickRandom(item);
        });

        holder.btnLaunch.setOnClickListener(v -> {
            if (listener != null) listener.onLaunchApp(item);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onRemoveTarget(item);
        });

        holder.btnMore.setOnClickListener(v -> showPopupMenu(v.getContext(), holder.btnMore, item));
    }

    private void updateSummary(VH holder, TargetAppItem item) {
        boolean isAr = "ar".equals(currentLang);
        if (!item.isSpoofEnabled) {
            holder.tvSummary.setText(isAr ? "معطّل" : "Disabled");
            holder.tvSummary.setTextColor(0xFF64748B);
        } else {
            int active = item.getActiveCount();
            int total = item.getTotalCount();
            holder.tvSummary.setText(isAr ? (active + "/" + total + " مفعّل") : (active + "/" + total + " Active"));
            holder.tvSummary.setTextColor(0xFF38BDF8);
        }
    }

    private void showPopupMenu(Context context, View anchor, TargetAppItem item) {
        PopupMenu popup = new PopupMenu(context, anchor);
        boolean isAr = "ar".equals(currentLang);
        boolean isZh = "zh".equals(currentLang);

        String titleLaunch = isAr ? "تشغيل التطبيق" : (isZh ? "打开应用" : "Launch App");
        String titleClear = isAr ? "مسح البيانات" : (isZh ? "清除数据" : "Clear App Data");
        String titleStop = isAr ? "إيقاف إجباري" : (isZh ? "强制停止" : "Force Stop");
        String titleRemove = isAr ? "إزالة من القائمة" : (isZh ? "从列表中移除" : "Remove Target");

        popup.getMenu().add(0, 1, 0, titleLaunch);
        popup.getMenu().add(0, 2, 1, titleClear);
        popup.getMenu().add(0, 3, 2, titleStop);
        popup.getMenu().add(0, 4, 3, titleRemove);

        popup.setOnMenuItemClickListener(menuItem -> {
            switch (menuItem.getItemId()) {
                case 1:
                    if (listener != null) listener.onLaunchApp(item);
                    return true;
                case 2:
                    if (listener != null) listener.onClearData(item);
                    return true;
                case 3:
                    if (listener != null) listener.onForceStop(item);
                    return true;
                case 4:
                    if (listener != null) listener.onRemoveTarget(item);
                    return true;
            }
            return false;
        });
        popup.show();
    }

    @Override
    public int getItemCount() {
        return displayList.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView ivIcon;
        final TextView tvName;
        final TextView tvPkg;
        final SwitchMaterial switchSpoof;
        final View viewScopeDot;
        final TextView tvScopeStatus;
        final TextView tvSummary;
        final MaterialButton btnCustomize;
        final MaterialButton btnQuickRandom;
        final MaterialButton btnLaunch;
        final MaterialButton btnDelete;
        final MaterialButton btnMore;

        VH(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_app_icon);
            tvName = itemView.findViewById(R.id.tv_app_name);
            tvPkg = itemView.findViewById(R.id.tv_app_package);
            switchSpoof = itemView.findViewById(R.id.switch_spoof_enable);
            viewScopeDot = itemView.findViewById(R.id.view_scope_dot);
            tvScopeStatus = itemView.findViewById(R.id.tv_scope_status);
            tvSummary = itemView.findViewById(R.id.tv_active_items_summary);
            btnCustomize = itemView.findViewById(R.id.btn_customize_items);
            btnQuickRandom = itemView.findViewById(R.id.btn_quick_random);
            btnLaunch = itemView.findViewById(R.id.btn_launch_app);
            btnDelete = itemView.findViewById(R.id.btn_delete_app);
            btnMore = itemView.findViewById(R.id.btn_more_menu);
        }
    }
}
