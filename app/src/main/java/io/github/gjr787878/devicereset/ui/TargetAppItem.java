package io.github.gjr787878.devicereset.ui;

import android.graphics.drawable.Drawable;
import io.github.gjr787878.devicereset.xposed.Identity;

/**
 * Model class for target apps displayed on the main screen.
 */
public class TargetAppItem {
    public final String packageName;
    public final String appName;
    public Drawable icon;
    public boolean inScope;
    public boolean isSpoofEnabled;
    public Identity identity;

    public TargetAppItem(String packageName, String appName, Drawable icon,
                         boolean inScope, boolean isSpoofEnabled, Identity identity) {
        this.packageName = packageName;
        this.appName = appName;
        this.icon = icon;
        this.inScope = inScope;
        this.isSpoofEnabled = isSpoofEnabled;
        this.identity = identity;
    }

    public int getActiveCount() {
        return (identity != null && isSpoofEnabled) ? identity.getEnabledCount() : 0;
    }

    public int getTotalCount() {
        return Identity.ALL_KEYS.length;
    }
}
