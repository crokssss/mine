package com.example.chatts;

import net.minecraft.client.Minecraft;
import javazoom.jl.player.Player;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Downloads speech audio from Google Translate's TTS endpoint, caches it locally,
 * and plays it back sequentially so overlapping chat lines don't talk over each other.
 */
public class TTSManager {

    private static final TTSManager INSTANCE = new TTSManager();
    private static final int MAX_CHUNK_LENGTH = 190; // Google Translate TTS truncates longer input.

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "chatts-worker");
        t.setDaemon(true);
        return t;
    });

    private final LinkedBlockingQueue<Runnable> nothingUsed = new LinkedBlockingQueue<>(); // reserved for future use
    private volatile Player currentPlayer;
    private volatile boolean stopRequested = false;

    public static TTSManager getInstance() {
        return INSTANCE;
    }

    /** Queues a line of text to be spoken. Safe to call from the client thread. */
    public void speak(String text) {
        String lang = TTSConfig.LANGUAGE.get();
        List<String> chunks = splitIntoChunks(text, MAX_CHUNK_LENGTH);
        worker.submit(() -> {
            for (String chunk : chunks) {
                if (stopRequested) {
                    stopRequested = false;
                    break;
                }
                try {
                    File audio = getOrDownload(chunk, lang);
                    if (audio != null) {
                        playFile(audio);
                    }
                } catch (Exception e) {
                    System.err.println("[chatts] Failed to speak chunk: " + e.getMessage());
                }
            }
        });
    }

    /** Stops whatever is currently playing and clears anything queued after it. */
    public void stop() {
        stopRequested = true;
        Player p = currentPlayer;
        if (p != null) {
            p.close();
        }
    }

    private void playFile(File file) throws Exception {
        try (InputStream fis = new BufferedInputStream(new FileInputStream(file))) {
            Player player = new Player(fis);
            currentPlayer = player;
            player.play();
        } finally {
            currentPlayer = null;
        }
    }

    private File getOrDownload(String text, String lang) throws Exception {
        File cacheDir = new File(Minecraft.getInstance().gameDirectory, "chatts_cache");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }

        String key = lang + ":" + text;
        String hash = sha256Hex(key);
        File cached = new File(cacheDir, hash + ".mp3");
        if (cached.exists() && cached.length() > 0) {
            return cached;
        }

        byte[] data = fetchFromGoogleTts(text, lang);
        if (data == null || data.length == 0) {
            return null;
        }

        File tmp = new File(cacheDir, hash + ".mp3.tmp");
        Files.write(tmp.toPath(), data);
        tmp.renameTo(cached);
        return cached;
    }

    private byte[] fetchFromGoogleTts(String text, String lang) throws Exception {
        String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
        String urlStr = "https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&q="
                + encoded + "&tl=" + lang;

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(15000);

        int code = conn.getResponseCode();
        if (code != 200) {
            System.err.println("[chatts] TTS request failed with HTTP " + code);
            return null;
        }

        try (InputStream in = conn.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private String sha256Hex(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /** Splits text on sentence/space boundaries so no chunk exceeds maxLen characters. */
    private List<String> splitIntoChunks(String text, int maxLen) {
        List<String> chunks = new ArrayList<>();
        String remaining = text.trim();

        while (remaining.length() > maxLen) {
            int splitAt = -1;
            for (String delim : new String[]{". ", "! ", "? ", ", ", " "}) {
                int idx = remaining.lastIndexOf(delim, maxLen);
                if (idx > 0) {
                    splitAt = idx + delim.length();
                    break;
                }
            }
            if (splitAt <= 0) {
                splitAt = maxLen;
            }
            chunks.add(remaining.substring(0, splitAt).trim());
            remaining = remaining.substring(splitAt).trim();
        }
        if (!remaining.isEmpty()) {
            chunks.add(remaining);
        }
        return chunks;
    }
}
