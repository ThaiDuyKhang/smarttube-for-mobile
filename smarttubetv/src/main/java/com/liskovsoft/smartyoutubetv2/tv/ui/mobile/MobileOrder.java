package com.liskovsoft.smartyoutubetv2.tv.ui.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mobile build: the order the user gave to a list by dragging (menu, settings, chips...).<br/>
 * Items unknown to the saved order (new sections, new settings) keep their place after the known ones.
 */
public final class MobileOrder {
    private static final String PREFS = "mobile_order";
    private static final String SEPARATOR = "\u001F";

    public interface KeyOf<T> {
        String key(T item);
    }

    private MobileOrder() {
    }

    public static List<String> load(Context context, String listName) {
        String data = prefs(context).getString(listName, null);
        return TextUtils.isEmpty(data) ? new ArrayList<>() : new ArrayList<>(Arrays.asList(data.split(SEPARATOR)));
    }

    public static void save(Context context, String listName, List<String> keys) {
        prefs(context).edit().putString(listName, TextUtils.join(SEPARATOR, keys)).apply();
    }

    /**
     * Sorts the items (in place) by the saved order
     */
    public static <T> void sort(Context context, String listName, List<T> items, KeyOf<T> keyOf) {
        List<String> saved = load(context, listName);

        if (saved.isEmpty() || items.size() < 2) {
            return;
        }

        Map<T, Integer> rank = new HashMap<>();
        for (int i = 0; i < items.size(); i++) {
            int index = saved.indexOf(keyOf.key(items.get(i)));
            rank.put(items.get(i), index >= 0 ? index : saved.size() + i);
        }

        Collections.sort(items, (a, b) -> Integer.compare(rank.get(a), rank.get(b)));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
