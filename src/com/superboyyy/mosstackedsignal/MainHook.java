package com.superboyyy.mosstackedsignal;

import android.view.View;
import android.widget.LinearLayout;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    private static final String[] SIGNAL_UNIT_VIEWS = {"SignalUnitNormalView", "SignalUnitUCTView", "SignalUnitTelecomView"};

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!StatusBarViews.SYSTEMUI.equals(lpparam.packageName)) return;
        if (lpparam.processName != null && !StatusBarViews.SYSTEMUI.equals(lpparam.processName)) return;
        StatusBarViews.log("handleLoadPackage hit for " + lpparam.processName);
        ClassLoader cl = lpparam.classLoader;

        hook(LinearLayout.class, "onMeasure", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                SignalStacker.beforeMeasure((LinearLayout) param.thisObject, param);
            }
        });

        XC_MethodHook onCluster = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                SignalStacker.schedule((View) param.thisObject);
            }
        };
        Class<?> cluster = findClass("com.android.systemui.statusbar.SignalClusterView", cl);
        hook(cluster, "onFinishInflate", onCluster);
        hook(cluster, "onAttachedToWindow", onCluster);
        hook(cluster, "onDarkChanged", onCluster);
        hook(cluster, "applyIconTint$1", onCluster);

        hook(findClass("com.android.systemui.statusbar.SignalClusterView$2", cl), "addSignalView", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object outer = XposedHelpers.getObjectField(param.thisObject, "this$0");
                if (outer instanceof View) SignalStacker.schedule((View) outer);
            }
        });

        XC_MethodHook onSimView = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object parent = ((View) param.thisObject).getParent();
                if (parent instanceof View) SignalStacker.schedule((View) parent);
            }
        };
        for (String name : SIGNAL_UNIT_VIEWS) {
            Class<?> unit = findClass("com.android.systemui.statusbar." + name, cl);
            hook(unit, "refreshView", onSimView);
            hook(unit, "updateView", onSimView);
        }

        XC_MethodHook onIconBound = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                for (Object arg : param.args) {
                    if (!(arg instanceof View)) continue;
                    View combo = SignalStacker.comboAncestor((View) arg);
                    if (combo != null) SignalStacker.schedule(combo);
                    return;
                }
            }
        };
        hook(findClass("com.android.systemui.common.ui.binder.IconViewBinder", cl), "bind", onIconBound);
        hook(findClass("com.android.systemui.utils.HnSignalUtils", cl), "setIconForView", onIconBound);
        hook(findClass("com.android.systemui.utils.HwSignalUtil", cl), "setIconForView", onIconBound);
    }

    private static Class<?> findClass(String name, ClassLoader cl) {
        try {
            return XposedHelpers.findClass(name, cl);
        } catch (Throwable t) {
            XposedBridge.log(StatusBarViews.TAG + ": class not found " + name);
            return null;
        }
    }

    private static void hook(Class<?> cls, String method, XC_MethodHook callback) {
        if (cls == null) return;
        try {
            if (XposedBridge.hookAllMethods(cls, method, callback).isEmpty()) {
                XposedBridge.log(StatusBarViews.TAG + ": no method " + cls.getName() + "#" + method);
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
