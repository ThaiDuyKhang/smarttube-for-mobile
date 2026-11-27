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
            {"dislike", R.drawable.mobile_ic_m_dislike},
            {"like", R.drawable.mobile_ic_m_like},
            {"unsubscribe", R.drawable.mobile_ic_m_block},
            {"subscri", R.drawable.mobile_ic_m_bell},
            {"notification", R.drawable.mobile_ic_m_bell},
            {"not_interested", R.drawable.mobile_ic_m_eye_off},
            {"dont_like", R.drawable.mobile_ic_m_eye_off},
            {"exclude", R.drawable.mobile_ic_m_eye_off},
            {"hide", R.drawable.mobile_ic_m_eye_off},
            {"block", R.drawable.mobile_ic_m_block},
            {"sponsor", R.drawable.mobile_ic_m_shield},
            {"dearrow", R.drawable.mobile_ic_m_dearrow},
            {"remove", R.drawable.mobile_ic_m_trash},
            {"delete", R.drawable.mobile_ic_m_trash},
            {"clear", R.drawable.mobile_ic_m_trash},
            {"unpin", R.drawable.mobile_ic_m_pin},
            {"pin", R.drawable.mobile_ic_m_pin},
            {"playlist", R.drawable.mobile_ic_m_playlist},
            {"queue", R.drawable.mobile_ic_m_playlist},
            {"channel", R.drawable.mobile_ic_m_channel},
            {"share", R.drawable.mobile_ic_m_share},
            {"link", R.drawable.mobile_ic_m_share},
            {"download", R.drawable.mobile_ic_m_download},
            {"comment", R.drawable.mobile_ic_m_comment},
            {"chat", R.drawable.mobile_ic_m_comment},
            {"description", R.drawable.mobile_ic_m_info},
            {"subtitle", R.drawable.mobile_ic_m_captions},
            {"caption", R.drawable.mobile_ic_m_captions},
            {"audio_lang", R.drawable.mobile_ic_m_language},
            {"audio_shift", R.drawable.mobile_ic_m_clock},
            {"audio_delay", R.drawable.mobile_ic_m_clock},
            {"pitch", R.drawable.mobile_ic_m_music},
            {"audio", R.drawable.mobile_ic_m_volume},
            {"sound", R.drawable.mobile_ic_m_volume},
            {"volume", R.drawable.mobile_ic_m_volume},
            {"speed", R.drawable.mobile_ic_m_gauge},
            {"frame_rate", R.drawable.mobile_ic_m_gauge},
            {"afr", R.drawable.mobile_ic_m_gauge},
            {"quality", R.drawable.mobile_ic_m_quality},
            {"video_format", R.drawable.mobile_ic_m_quality},
            {"video_preset", R.drawable.mobile_ic_m_quality},
            {"format", R.drawable.mobile_ic_m_quality},
            {"preset", R.drawable.mobile_ic_m_quality},
            {"zoom", R.drawable.mobile_ic_m_zoom},
            {"aspect", R.drawable.mobile_ic_m_zoom},
            {"resize", R.drawable.mobile_ic_m_zoom},
            {"rotat", R.drawable.mobile_ic_m_zoom},
            {"flip", R.drawable.mobile_ic_m_zoom},
            {"buffer", R.drawable.mobile_ic_m_network},
            {"network", R.drawable.mobile_ic_m_network},
            {"engine", R.drawable.mobile_ic_m_network},
            {"sleep", R.drawable.mobile_ic_m_timer},
            {"timer", R.drawable.mobile_ic_m_timer},
            {"time", R.drawable.mobile_ic_m_clock},
            {"repeat", R.drawable.mobile_ic_m_repeat},
            {"loop", R.drawable.mobile_ic_m_repeat},
            {"pip", R.drawable.mobile_ic_m_pip},
            {"background", R.drawable.mobile_ic_m_headphones},
            {"screen_off", R.drawable.mobile_ic_m_lock},
            {"dimming", R.drawable.mobile_ic_m_lock},
            {"stats", R.drawable.mobile_ic_m_stats},
            {"debug", R.drawable.mobile_ic_m_stats},
            {"history", R.drawable.mobile_ic_m_history},
            {"watched", R.drawable.mobile_ic_m_eye},
            {"search", R.drawable.mobile_ic_m_search},
            {"voice", R.drawable.mobile_ic_m_keyboard},
            {"keyboard", R.drawable.mobile_ic_m_keyboard},
            {"language", R.drawable.mobile_ic_m_language},
            {"country", R.drawable.mobile_ic_m_language},
            {"locale", R.drawable.mobile_ic_m_language},
            {"account", R.drawable.mobile_ic_m_user},
            {"password", R.drawable.mobile_ic_m_lock},
            {"color", R.drawable.mobile_ic_m_palette},
            {"style", R.drawable.mobile_ic_m_palette},
            {"theme", R.drawable.mobile_ic_m_palette},
            {"font", R.drawable.mobile_ic_m_text},
            {"text", R.drawable.mobile_ic_m_text},
            {"layout", R.drawable.mobile_ic_m_layout},
            {"ui", R.drawable.mobile_ic_m_layout},
            {"interface", R.drawable.mobile_ic_m_layout},
            {"menu", R.drawable.mobile_ic_m_layout},
            {"backup", R.drawable.mobile_ic_m_cloud},
            {"restore", R.drawable.mobile_ic_m_cloud},
            {"update", R.drawable.mobile_ic_m_download},
            {"about", R.drawable.mobile_ic_m_info},
            {"info", R.drawable.mobile_ic_m_info},
            {"open", R.drawable.mobile_ic_m_open},
            {"add", R.drawable.mobile_ic_m_add},
            {"create", R.drawable.mobile_ic_m_add},
            {"move", R.drawable.mobile_ic_m_move},
            {"sort", R.drawable.mobile_ic_m_move},
            {"order", R.drawable.mobile_ic_m_move},
            {"play", R.drawable.mobile_ic_m_play},
            {"player", R.drawable.mobile_ic_m_video},
            {"video", R.drawable.mobile_ic_m_video},
            {"full", R.drawable.mobile_ic_m_fullscreen},
            {"setting", R.drawable.mobile_ic_m_settings},
            {"general", R.drawable.mobile_ic_m_settings},
    };

    private MobileMenuIcons() {
    }

    /**
     * Icon for a menu row by its title. Never 0: rows without a match get a neutral icon.
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

        return R.drawable.mobile_ic_m_list;
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
