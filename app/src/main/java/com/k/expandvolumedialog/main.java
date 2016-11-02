package com.k.expandvolumedialog;

/**
 * Created by Kaushik on 02-11-2016.
 */
//**********from gravity box*******
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.XModuleResources;
import android.content.res.XResources;
import android.media.AudioManager;
import android.view.View;
import android.widget.ImageButton;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;

//*****from volume panel expand**********

import java.lang.reflect.Field;
import java.util.Map;

import android.content.Context;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class main {
    private static BroadcastReceiver mBrodcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_VOLUME_PANEL_MODE_CHANGED)) {
                if (intent.hasExtra(GravityBoxSettings.EXTRA_AUTOEXPAND)) {
                    mAutoExpand = intent.getBooleanExtra(GravityBoxSettings.EXTRA_AUTOEXPAND, false);
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_VIBRATE_MUTED)) {
                    mVolumeAdjustVibrateMuted = intent.getBooleanExtra(GravityBoxSettings.EXTRA_VIBRATE_MUTED, false);
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_TIMEOUT)) {
                    mTimeout = intent.getIntExtra(GravityBoxSettings.EXTRA_TIMEOUT, 0);
                }
            }
            else if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_LINK_VOLUMES_CHANGED)) {
                mVolumesLinked = intent.getBooleanExtra(GravityBoxSettings.EXTRA_LINKED, true);
            }
        }

    };

    public static void initResources(XSharedPreferences prefs, InitPackageResourcesParam resparam) {
        XModuleResources modRes = XModuleResources.createInstance(GravityBox.MODULE_PATH, resparam.res);

        mIconNotifResId = XResources.getFakeResId(modRes, R.drawable.ic_audio_notification);
        resparam.res.setReplacement(mIconNotifResId, modRes.fwd(R.drawable.ic_audio_notification));
        mIconNotifMuteResId = XResources.getFakeResId(modRes, R.drawable.ic_audio_notification_mute);
        resparam.res.setReplacement(mIconNotifMuteResId, modRes.fwd(R.drawable.ic_audio_notification_mute));
    }

    public static void init(final XSharedPreferences prefs, final ClassLoader classLoader) {
        try {
            final Class<?> classVolumePanel = XposedHelpers.findClass(CLASS_VOLUME_PANEL, classLoader);

            mVolumeAdjustVibrateMuted = prefs.getBoolean(GravityBoxSettings.PREF_KEY_VOLUME_ADJUST_VIBRATE_MUTE, false);
            mAutoExpand = prefs.getBoolean(GravityBoxSettings.PREF_KEY_VOLUME_PANEL_AUTOEXPAND, false);
            mVolumesLinked = prefs.getBoolean(GravityBoxSettings.PREF_KEY_LINK_VOLUMES, true);

            XposedBridge.hookAllConstructors(classVolumePanel, new XC_MethodHook() {

                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    mVolumePanel = param.thisObject;
                    Context context = (Context) XposedHelpers.getObjectField(mVolumePanel, "mContext");
                    if (DEBUG) log("VolumePanel constructed; mVolumePanel set");

                    mTimeout = prefs.getInt(
                            GravityBoxSettings.PREF_KEY_VOLUME_PANEL_TIMEOUT, 0);

                    prepareNotificationRow(context.getResources());

                    IntentFilter intentFilter = new IntentFilter();
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_VOLUME_PANEL_MODE_CHANGED);
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_LINK_VOLUMES_CHANGED);
                    context.registerReceiver(mBrodcastReceiver, intentFilter);
                }
            });

            XposedHelpers.findAndHookMethod(classVolumePanel, "showH", int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    if (mAutoExpand && !XposedHelpers.getBooleanField(param.thisObject, "mExpanded")) {
                        ImageButton expandBtn = (ImageButton) XposedHelpers.getObjectField(
                                param.thisObject, "mExpandButton");
                        expandBtn.performClick();
                    }
                }
            });

            XposedHelpers.findAndHookMethod(classVolumePanel, "computeTimeoutH", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    if (mTimeout != 0) {
                        param.setResult(mTimeout);
                    }
                }
            });

            if (!Utils.isSamsungRom()) {
                XposedHelpers.findAndHookMethod(CLASS_VOLUME_PANEL_CTRL, classLoader, "vibrate", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                        if (mVolumeAdjustVibrateMuted) {
                            param.setResult(null);
                        }
                    }
                });
            }

            XposedHelpers.findAndHookMethod(classVolumePanel, "isVisibleH",
                    CLASS_VOLUME_ROW, boolean.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                            if (XposedHelpers.getAdditionalInstanceField(
                                    param.args[0], "gbNotifSlider") != null) {
                                boolean visible = (boolean) param.getResult();
                                visible &= !mVolumesLinked;
                                param.setResult(visible);
                            }
                        }
                    });

            XposedHelpers.findAndHookMethod(classVolumePanel, "updateVolumeRowSliderH",
                    CLASS_VOLUME_ROW, boolean.class, int.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                            if (!mVolumesLinked && XposedHelpers.getAdditionalInstanceField(
                                    param.args[0], "gbNotifSlider") != null) {
                                View slider = (View) XposedHelpers.getObjectField(param.args[0], "slider");
                                slider.setEnabled(isRingerSliderEnabled());
                                View icon = (View) XposedHelpers.getObjectField(param.args[0], "icon");
                                icon.setEnabled(slider.isEnabled());
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void prepareNotificationRow(Resources res) {
        try {
            XposedHelpers.callMethod(mVolumePanel, "addRow",
                    AudioManager.STREAM_NOTIFICATION,
                    mIconNotifResId, mIconNotifMuteResId, true);
            List<?> rows = (List<?>) XposedHelpers.getObjectField(mVolumePanel, "mRows");
            Object row = rows.get(rows.size()-1);
            XposedHelpers.setAdditionalInstanceField(row, "gbNotifSlider", true);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static boolean isRingerSliderEnabled() {
        try {
            List<?> rows = (List<?>) XposedHelpers.getObjectField(mVolumePanel, "mRows");
            for (Object row : rows) {
                if (XposedHelpers.getIntField(row, "stream") == AudioManager.STREAM_RING) {
                    return ((View)XposedHelpers.getObjectField(row, "slider")).isEnabled();
                }
            }
            return true;
        } catch (Throwable t) {
            XposedBridge.log(t);
            return true;
        }
    }
}
