package com.liskovsoft.smartyoutubetv2.tv.ui.mobile;

import android.content.Context;
import android.text.TextUtils;

import com.liskovsoft.smartyoutubetv2.tv.R;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Mobile build: an icon for every row of the menus and dialogs.<br/>
 * The title is matched to its string resource (any language), the resource name tells what the row is about.
 */
public final class MobileMenuIcons {
    private static Map<String, String> sNameByText;
    private static Locale sLocale;

    // Resource name keywords -> icon (the first match wins, the specific ones first)
    private static final Object[][] RULES = {
            // Actions on a video or a channel: icons of their own
            {"dislike", R.drawable.mobile_ic_m_dislike},
            {"like", R.drawable.mobile_ic_m_like},
            {"unsubscribe", R.drawable.mobile_ic_m_block},
            {"subscri", R.drawable.mobile_ic_m_bell},
            {"not_interested", R.drawable.mobile_ic_m_eye_off},
            {"dont_like", R.drawable.mobile_ic_m_eye_off},
            {"exclude", R.drawable.mobile_ic_m_eye_off},
            {"remove", R.drawable.mobile_ic_m_trash},
            {"delete", R.drawable.mobile_ic_m_trash},
            {"unpin", R.drawable.mobile_ic_m_pin},
            {"pin_", R.drawable.mobile_ic_m_pin},
            {"add_to_playlist", R.drawable.mobile_ic_m_playlist},
            {"playlist_add", R.drawable.mobile_ic_m_playlist},
            {"open_playlist", R.drawable.mobile_ic_m_playlist},
            {"open_channel", R.drawable.mobile_ic_m_channel},
            {"share", R.drawable.mobile_ic_m_share},
            {"download", R.drawable.mobile_ic_m_download},
            {"comment", R.drawable.mobile_ic_m_comment},
            {"mark_as_watched", R.drawable.mobile_ic_m_eye},
            {"play_video", R.drawable.mobile_ic_m_play},
            {"add_account", R.drawable.mobile_ic_m_user},
            {"remove_account", R.drawable.mobile_ic_m_block},
    };

    private MobileMenuIcons() {
    }

    /**
     * Icon for a menu row by its title: the actions (like, share, add to playlist...) have their own icon,
     * the other rows share one icon. Never 0.
     */
    public static int forTitle(Context context, CharSequence title) {
        String name = findResourceName(context, title);

        if (name != null) {
            String lower = name.toLowerCase(Locale.US);
            for (Object[] rule : RULES) {
                if (lower.contains((String) rule[0])) {
                    return (int) rule[1];
                }
            }
        }

        // Settings and the other rows: one icon for all, like the lists of a settings page
        return R.drawable.mobile_ic_m_uniform;
    }

    private static String findResourceName(Context context, CharSequence title) {
        if (TextUtils.isEmpty(title)) {
            return null;
        }

        Locale locale = context.getResources().getConfiguration().locale;

        if (sNameByText == null || !locale.equals(sLocale)) {
            sLocale = locale;
            sNameByText = buildIndex(context);
        }

        String text = title.toString().trim();
        String name = sNameByText.get(text);

        if (name == null) {
            // Titles with a value after them ("Speed: 1.25") or before them
            for (Map.Entry<String, String> entry : sNameByText.entrySet()) {
                if (entry.getKey().length() > 3 && text.startsWith(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }

        return name;
    }

    private static Map<String, String> buildIndex(Context context) {
        Map<String, String> index = new HashMap<>();

        // The app texts are in the common module (and the TV app one)
        for (Class<?> strings : new Class<?>[] {com.liskovsoft.smartyoutubetv2.common.R.string.class, R.string.class}) {
            for (Field field : strings.getFields()) {
                try {
                    String text = context.getString(field.getInt(null)).trim();
                    if (!text.isEmpty() && !text.contains("%") && !index.containsKey(text)) {
                        index.put(text, field.getName());
                    }
                } catch (Exception e) {
                    // Skip
                }
            }
        }

        return index;
    }
}
