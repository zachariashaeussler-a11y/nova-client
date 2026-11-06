package gg.nova.client;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Timed lyrics overlay. It reads YOUR OWN .lrc file (config/novaclient-lyrics.lrc), lines like
 * "[00:12.50]text", and shows the current and next line. Press the lyrics key to start and stop.
 */
public class Lyrics {
    static final List<Long> T = new ArrayList<>();
    static final List<String> L = new ArrayList<>();
    static final Pattern TAG = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
    static final String[] IDLE = {"Lyrics ready - press the lyrics key", ""};
    public static boolean running;
    static long start;
    public static String status = "File not loaded yet";

    static final String SAMPLE = "[ti:Sample file]\n"
            + "[00:02.00]Put your own lyrics file here\n"
            + "[00:06.00]One line per timestamp, like this\n"
            + "[00:10.00]Press the lyrics key to start and stop\n";

    public static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("novaclient-lyrics.lrc"); }

    public static void load() {
        T.clear();
        L.clear();
        List<Object[]> items = new ArrayList<>();
        try {
            Path p = file();
            if (!Files.exists(p)) Files.writeString(p, SAMPLE);
            for (String line : Files.readAllLines(p)) {
                Matcher m = TAG.matcher(line);
                List<Long> times = new ArrayList<>();
                int end = 0;
                while (m.find()) {
                    long ms = Long.parseLong(m.group(1)) * 60000L + Long.parseLong(m.group(2)) * 1000L;
                    String f = m.group(3);
                    if (f != null) ms += f.length() == 1 ? Long.parseLong(f) * 100 : f.length() == 2 ? Long.parseLong(f) * 10 : Long.parseLong(f);
                    times.add(ms);
                    end = m.end();
                }
                if (times.isEmpty()) continue;
                String text = line.substring(end).trim();
                for (long ms : times) items.add(new Object[]{ms, text});
            }
            items.sort((a, b) -> Long.compare((Long) a[0], (Long) b[0]));
            for (Object[] it : items) { T.add((Long) it[0]); L.add((String) it[1]); }
            status = L.isEmpty() ? "No timed lines found in the file" : "Loaded " + L.size() + " lines";
        } catch (Exception e) {
            status = "Could not read the file";
        }
    }

    public static void toggle() {
        if (running) { running = false; return; }
        load();
        if (!L.isEmpty()) { running = true; start = System.currentTimeMillis(); }
    }

    /** {current line, next line} */
    public static String[] current(Config.Settings s) {
        if (!running || L.isEmpty()) return IDLE;
        long el = System.currentTimeMillis() - start + s.lyricsOffset;
        if (el > T.get(T.size() - 1) + 8000) { running = false; return IDLE; }
        int i = -1;
        for (int k = 0; k < T.size(); k++) { if (T.get(k) <= el) i = k; else break; }
        return new String[]{i < 0 ? "" : L.get(i), i + 1 < L.size() ? L.get(i + 1) : ""};
    }
}
