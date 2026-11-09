package com.superboyyy.mosstackedsignal;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SignalInfo {
    static final int ACTIVITY_NONE = 0;
    static final int ACTIVITY_IN = 1;
    static final int ACTIVITY_OUT = 2;
    static final int ACTIVITY_INOUT = 3;

    private static final Pattern SIGNAL_ICON = Pattern.compile("signal_(five_)?(\\d)");
    private static final Pattern TYPE_IN_DESCRIPTION = Pattern.compile("^\\s*([0-9A-Za-z+.\\-]{1,6})\\s*[,，]");
    private static final String NO_SERVICE = "×";

    private SignalInfo() { }

    /** {当前格数, 总格数}；认不出图标时返回 null。 */
    static int[] level(View simView) {
        View signal = StatusBarViews.findIn(simView, StatusBarViews.idMobileSignal);
        if (!(signal instanceof ImageView)) return null;
        String name = StatusBarViews.iconName((ImageView) signal);
        if (name == null) return null;
        Matcher m = SIGNAL_ICON.matcher(name);
        if (m.find()) {
            int levels = m.group(1) != null ? 5 : 4;
            return new int[] {Integer.parseInt(m.group(2)), levels};
        }
        StatusBarViews.logOnChange("unknown signal icon", name);
        return null;
    }

    static int[] activities(LinearLayout combo) {
        int[] result = new int[combo.getChildCount()];
        for (int i = 0; i < result.length; i++) {
            result[i] = -1;
            View inout = StatusBarViews.findIn(combo.getChildAt(i), StatusBarViews.idMobileInout);
            if (inout instanceof ImageView && inout.getVisibility() == View.VISIBLE
                    && ((ImageView) inout).getDrawable() != null) {
                String name = StatusBarViews.iconName((ImageView) inout);
                result[i] = activityFromIconName(name == null ? "" : name);
            }
        }
        return result;
    }

    static int activityFromIconName(String name) {
        if (name.endsWith("inout")) return ACTIVITY_INOUT;
        if (name.endsWith("_out")) return ACTIVITY_OUT;
        if (name.endsWith("_in")) return ACTIVITY_IN;
        return ACTIVITY_NONE;
    }

    static String label(View simView) {
        List<String> names = new ArrayList<>();
        collectIconNames(simView, names);
        String type = null;
        boolean noService = false;
        for (String name : names) {
            if (name.startsWith("stat_sys_signal_") && (name.contains("null") || name.contains("flightmode"))) {
                noService = true;
            }
            if (type == null) type = typeFromIconName(name);
        }
        if (noService) return NO_SERVICE;
        if (type == null) type = typeFromDescription(simView);
        if (Prefs.fiveA && "5GA".equals(type)) return "5A";
        return type;
    }

    private static String typeFromIconName(String name) {
        String rest;
        if (name.startsWith("stat_sys_data_connected_")) {
            rest = name.substring("stat_sys_data_connected_".length());
        } else if (name.startsWith("stat_sys_signal_roam_type_")) {
            rest = name.substring("stat_sys_signal_roam_type_".length());
        } else {
            return name.equals("stat_sys_signal_type_2g") ? "2G" : null;
        }
        if (rest.startsWith("c1_") || rest.startsWith("c2_")) {
            rest = rest.substring(3);
        } else if (rest.startsWith("c_")) {
            rest = rest.substring(2);
        }
        if (rest.startsWith("5ga")) return "5GA";
        if (rest.startsWith("5g_plus")) return "5G+";
        if (rest.startsWith("5g")) return "5G";
        if (rest.startsWith("4gplus")) return "4G+";
        if (rest.startsWith("4g")) return "4G";
        if (rest.startsWith("3gplus")) return "3G+";
        if (rest.startsWith("3g")) return "3G";
        if (rest.startsWith("hplus")) return "H+";
        if (rest.equals("h")) return "H";
        if (rest.startsWith("2g")) return "2G";
        if (rest.startsWith("g_for_gsm")) return "G";
        if (rest.startsWith("lte")) return "LTE";
        return null;
    }

    private static void collectIconNames(View view, List<String> out) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof ImageView) {
            Drawable d = ((ImageView) view).getDrawable();
            String name = d == null ? null : StatusBarViews.iconName((ImageView) view);
            if (name != null) out.add(name);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collectIconNames(group.getChildAt(i), out);
        }
    }

    private static String typeFromDescription(View view) {
        CharSequence desc = view.getContentDescription();
        if (desc == null) return null;
        Matcher m = TYPE_IN_DESCRIPTION.matcher(desc);
        return m.find() ? m.group(1).toUpperCase(Locale.ROOT) : null;
    }

    static int color(LinearLayout combo) {
        View signal = combo.getChildCount() > 0
                ? StatusBarViews.findIn(combo.getChildAt(0), StatusBarViews.idMobileSignal) : null;
        if (!(signal instanceof ImageView)) return 0xFFFFFFFF;
        return StatusBarViews.tintColor((ImageView) signal, 0xFFFFFFFF);
    }
}
