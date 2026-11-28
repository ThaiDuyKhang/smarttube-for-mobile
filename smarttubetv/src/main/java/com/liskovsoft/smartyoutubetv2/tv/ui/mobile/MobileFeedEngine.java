package com.liskovsoft.smartyoutubetv2.tv.ui.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mobile build: what makes the feeds feel fresh.<br/>
 * - remembers the videos already shown on the screen (kept two days, across app launches): the next opens and refreshes
 *   put the ones not seen yet first<br/>
 * - a stable order per page that changes on every refresh, with the videos left deep in the list brought up<br/>
 * - topic of a video from its title (music, sports, gaming, news) for the rows of each chip page<br/>
 * - views and age read from the "1.2M views • 3 days ago" line, to rank the "New to you" page<br/>
 * - mixes (radio playlists "RD" + video id) made from a video, like the YouTube app's mixes
 */
public final class MobileFeedEngine {
    private static final String PREFS = "mobile_feed_engine";
    private static final String KEY_SHOWN = "shown";
    private static final long SHOWN_KEEP_MS = 2L * 24 * 60 * 60 * 1000;
    private static final int SHOWN_MAX = 3000;
    private static LinkedHashMap<String, Long> sShown;
    private static int sUnsaved;

    private MobileFeedEngine() {
    }

    // ------------------------------- Keys -------------------------------

    public static String keyOf(Video video) {
        if (video == null) {
            return null;
        }
        return video.videoId != null ? video.videoId : video.getPlaylistId() != null ? "p:" + video.getPlaylistId() :
                video.channelId != null ? "c:" + video.channelId : null;
    }

    // ------------------------------- Shown videos -------------------------------

    private static LinkedHashMap<String, Long> shown(Context context) {
        if (sShown == null) {
            sShown = new LinkedHashMap<>();
            String data = prefs(context).getString(KEY_SHOWN, "");
            long now = System.currentTimeMillis();
            for (String entry : data.split(",")) {
                int sep = entry.lastIndexOf('|');
                if (sep <= 0) {
                    continue;
                }
                try {
                    long time = Long.parseLong(entry.substring(sep + 1));
                    if (now - time < SHOWN_KEEP_MS) {
                        sShown.put(entry.substring(0, sep), time);
                    }
                } catch (NumberFormatException e) {
                    // skip
                }
            }
        }
        return sShown;
    }

    /**
     * A card was drawn on the screen
     */
    public static void markShown(Context context, Video video) {
        String key = keyOf(video);
        if (key == null) {
            return;
        }
        LinkedHashMap<String, Long> shown = shown(context);
        if (!shown.containsKey(key)) {
            shown.put(key, System.currentTimeMillis());
            while (shown.size() > SHOWN_MAX) {
                shown.remove(shown.keySet().iterator().next());
            }
            if (++sUnsaved >= 20) {
                flush(context);
            }
        }
    }

    public static boolean wasShown(Context context, Video video) {
        String key = keyOf(video);
        return key != null && shown(context).containsKey(key);
    }

    public static void flush(Context context) {
        if (sShown == null || sUnsaved == 0) {
            return;
        }
        StringBuilder data = new StringBuilder();
        for (Map.Entry<String, Long> entry : sShown.entrySet()) {
            if (data.length() > 0) {
                data.append(',');
            }
            data.append(entry.getKey()).append('|').append(entry.getValue());
        }
        prefs(context).edit().putString(KEY_SHOWN, data.toString()).apply();
        sUnsaved = 0;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ------------------------------- Order of a page -------------------------------

    /**
     * The order of one page. Videos keep their place once shown in the list (no jumps while scrolling); the new ones of
     * each loaded page go after, the ones never shown before first, shuffled with the page seed. A refresh makes a new
     * seed, and the videos that were loaded but never reached on the screen come first.
     */
    public static final class Mixer {
        private final List<String> mOrder = new ArrayList<>();
        private final Map<String, Video> mPool = new HashMap<>();
        private final Set<String> mCarry = new HashSet<>();
        private long mSeed = new Random().nextLong();

        public List<Video> arrange(Context context, List<Video> videos) {
            List<Video> fresh = new ArrayList<>();
            List<Video> seen = new ArrayList<>();
            List<Video> carried = new ArrayList<>();
            Set<String> added = new HashSet<>();

            for (Video video : videos) {
                String key = keyOf(video);
                if (key == null || mPool.containsKey(key) || !added.add(key)) {
                    if (key != null && mPool.containsKey(key)) {
                        mPool.put(key, video); // newer data (e.g. watched state)
                    }
                    continue;
                }
                if (mCarry.contains(key)) {
                    carried.add(video);
                } else if (wasShown(context, video)) {
                    seen.add(video);
                } else {
                    fresh.add(video);
                }
            }

            Random random = new Random(mSeed + mOrder.size());
            Collections.shuffle(carried, random);
            Collections.shuffle(fresh, random);
            Collections.shuffle(seen, random);
            for (List<Video> group : new List[] {carried, fresh, seen}) {
                for (Video video : group) {
                    String key = keyOf(video);
                    mOrder.add(key);
                    mPool.put(key, video);
                }
            }

            List<Video> result = new ArrayList<>(mOrder.size());
            for (String key : mOrder) {
                result.add(mPool.get(key));
            }
            return result;
        }

        /**
         * Like {@link #arrange} but the new videos keep the given order (already ranked), the ones never shown first
         */
        public List<Video> arrangeRanked(Context context, List<Video> ranked) {
            List<Video> fresh = new ArrayList<>();
            List<Video> seen = new ArrayList<>();
            for (Video video : ranked) {
                String key = keyOf(video);
                if (key == null) {
                    continue;
                }
                if (mPool.containsKey(key)) {
                    mPool.put(key, video);
                    continue;
                }
                if (fresh.contains(video) || seen.contains(video)) {
                    continue;
                }
                (wasShown(context, video) && !mCarry.contains(key) ? seen : fresh).add(video);
            }
            for (List<Video> group : new List[] {fresh, seen}) {
                for (Video video : group) {
                    String key = keyOf(video);
                    if (!mPool.containsKey(key)) {
                        mOrder.add(key);
                        mPool.put(key, video);
                    }
                }
            }
            List<Video> result = new ArrayList<>(mOrder.size());
            for (String key : mOrder) {
                result.add(mPool.get(key));
            }
            return result;
        }

        /**
         * Pull to refresh: a new order; what was loaded but not reached comes first next time
         */
        public void reshuffle(Context context) {
            mCarry.clear();
            for (Map.Entry<String, Video> entry : mPool.entrySet()) {
                if (!wasShown(context, entry.getValue())) {
                    mCarry.add(entry.getKey());
                }
            }
            mOrder.clear();
            mPool.clear();
            mSeed = new Random().nextLong();
        }

        public long getSeed() {
            return mSeed;
        }

        public void clear() {
            mOrder.clear();
            mPool.clear();
            mCarry.clear();
        }
    }

    // ------------------------------- Topics -------------------------------

    private static final String[] MUSIC = {"music video", "official mv", " mv", "m/v", "lyric", "audio", "remix", "cover", "karaoke",
            "acoustic", "nhạc", "bài hát", "ca khúc", "song", "album", "vevo", "beat", "lofi", "mashup", "nonstop", "bolero", "edm",
            "rap", "hát", "playlist", "live session", "concert", "liveshow", "official music", "ost", "ballad", "piano", "guitar"};
    private static final String[] SPORTS = {"bóng đá", "highlight", "trận", "giải", " cup", "league", "football", "soccer", "goal",
            "bàn thắng", "cầu lông", "tennis", "nba", "f1 ", "thể thao", "v-league", "ngoại hạng", "champions", "sea games", "boxing",
            "ufc", "pickleball", "bóng chuyền", "olympic", "world cup", "premier league"};
    private static final String[] GAMING = {"game", "gaming", "minecraft", "liên quân", "free fire", "pubg", "roblox", "gameplay",
            "esports", "valorant", "genshin", "tốc chiến", "lol ", "liên minh", "fifa", "fc online", "play together", "gta", "ps5",
            "nintendo", "xbox", "speedrun", "walkthrough", "mobile legends"};
    private static final String[] NEWS = {"tin tức", "thời sự", "bản tin", "news", "tin nhanh", "tin nóng", "vtv", "vtc", "báo ",
            "chính phủ", "quốc hội", "thế giới", "cảnh báo", "điểm tin", "60 giây", "breaking", "phóng sự", "thời tiết"};

    /**
     * The video belongs to the topic of the chip page (by its title and channel name)
     */
    public static boolean matchesSection(int sectionId, Video video) {
        if (video == null) {
            return false;
        }
        if (sectionId == MediaGroup.TYPE_LIVE) {
            return video.isLive;
        }
        String[] words = wordsOf(sectionId);
        if (words == null) {
            return false;
        }
        String text = (" " + video.getTitle() + " " + video.getAuthor() + " ").toLowerCase(Locale.ROOT);
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasTopic(int sectionId) {
        return sectionId == MediaGroup.TYPE_LIVE || wordsOf(sectionId) != null;
    }

    private static String[] wordsOf(int sectionId) {
        switch (sectionId) {
            case MediaGroup.TYPE_MUSIC: return MUSIC;
            case MediaGroup.TYPE_SPORTS: return SPORTS;
            case MediaGroup.TYPE_GAMING: return GAMING;
            case MediaGroup.TYPE_NEWS: return NEWS;
            default: return null;
        }
    }

    // ------------------------------- Views and age -------------------------------

    private static final Pattern VIEWS = Pattern.compile("([\\d.,]+)\\s*(tr|n|k|m|b|tỷ|nghìn|triệu)?\\.?\\s*(lượt xem|views?|lượt)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AGE = Pattern.compile("(\\d+)\\s*(giây|phút|giờ|ngày|tuần|tháng|năm|second|minute|hour|day|week|month|year)",
            Pattern.CASE_INSENSITIVE);

    /**
     * "1,2 Tr lượt xem" / "13 N lượt xem" / "1.2M views" -> number of views (0 when unknown)
     */
    public static long parseViews(CharSequence line) {
        if (line == null) {
            return 0;
        }
        Matcher matcher = VIEWS.matcher(line.toString());
        if (!matcher.find()) {
            return 0;
        }
        String number = matcher.group(1);
        String unit = matcher.group(2) != null ? matcher.group(2).toLowerCase(Locale.ROOT) : "";
        // "1,2" (vi) and "1.2" (en) are decimals; "1.234" / "1,234" thousands separators
        String normalized = number.replace(" ", "");
        double value;
        try {
            if (unit.isEmpty()) {
                value = Double.parseDouble(normalized.replace(".", "").replace(",", ""));
            } else {
                value = Double.parseDouble(normalized.replace(",", "."));
            }
        } catch (NumberFormatException e) {
            return 0;
        }
        switch (unit) {
            case "n": case "k": case "nghìn": return (long) (value * 1_000);
            case "tr": case "m": case "triệu": return (long) (value * 1_000_000);
            case "b": case "tỷ": return (long) (value * 1_000_000_000);
            default: return (long) value;
        }
    }

    /**
     * "3 ngày trước" / "2 weeks ago" -> age in days (-1 when unknown)
     */
    public static double parseAgeDays(CharSequence line) {
        if (line == null) {
            return -1;
        }
        Matcher matcher = AGE.matcher(line.toString());
        if (!matcher.find()) {
            return -1;
        }
        double count = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2).toLowerCase(Locale.ROOT);
        switch (unit) {
            case "giây": case "second": return count / 86400;
            case "phút": case "minute": return count / 1440;
            case "giờ": case "hour": return count / 24;
            case "ngày": case "day": return count;
            case "tuần": case "week": return count * 7;
            case "tháng": case "month": return count * 30;
            default: return count * 365;
        }
    }

    /**
     * "New to you" score: popular (views per day since upload) and recent, a little randomness per refresh
     */
    public static double discoveryScore(Video video, Random random) {
        long views = parseViews(video.getSecondTitle());
        double age = parseAgeDays(video.getSecondTitle());
        double ageDays = age < 0 ? 30 : Math.max(0.2, age);
        double perDay = views / ageDays;
        double score = Math.log10(perDay + 10) + Math.log10(views + 10) * 0.5;
        if (age >= 0 && age <= 7) {
            score += 1.0; // this week
        } else if (age > 365) {
            score -= 1.0;
        }
        if (video.isLive) {
            score += 0.5;
        }
        return score + random.nextDouble() * 0.8;
    }

    // ------------------------------- Mixes -------------------------------

    /**
     * A mix made from a video (YouTube's radio playlist "RD" + video id), shown as a stack card
     */
    public static Video mixOf(Context context, Video video) {
        if (video == null || video.videoId == null) {
            return null;
        }
        Video mix = new Video();
        mix.videoId = video.videoId;
        mix.playlistId = "RD" + video.videoId;
        mix.title = context.getString(R.string.mobile_mix_title, video.getTitle());
        mix.secondTitle = !TextUtils.isEmpty(video.getAuthor()) ? context.getString(R.string.mobile_mix_artists, video.getAuthor()) : null;
        mix.cardImageUrl = video.cardImageUrl;
        mix.bgImageUrl = video.bgImageUrl;
        mix.channelId = video.channelId;
        mix.badge = context.getString(R.string.mobile_mix);
        return mix;
    }
}
