package org.example.demo2;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Minimal NDJSON index/reader utilities for large playback files.
 *
 * Index format (TSV): timestamp\toffset\tlength\ttype
 */
public class NdjsonIndexUtil {
    public static class IndexEntry {
        public final long timestamp;
        public final long offset;
        public final int length;
        public final String type; // "flow" or "topology" if present; may be null
        public IndexEntry(long timestamp, long offset, int length, String type) {
            this.timestamp = timestamp;
            this.offset = offset;
            this.length = length;
            this.type = type;
        }
    }

    public static class BuiltIndex {
        public final List<IndexEntry> entries;
        public BuiltIndex(List<IndexEntry> entries) { this.entries = entries; }
        public List<Long> timestamps() {
            List<Long> ts = new ArrayList<>();
            for (IndexEntry e : entries) ts.add(e.timestamp);
            return ts;
        }
    }

    /**
     * Build an index for a single JSON file (not NDJSON). The file should contain a timestamp field
     * named either "t" or "timestamp". This creates a single index entry for the entire file.
     */
    public static BuiltIndex buildIndexForSingleJson(File jsonFile, File outIdxFile) throws Exception {
        List<IndexEntry> entries = new ArrayList<>();
        Gson gson = new Gson();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(jsonFile), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outIdxFile), StandardCharsets.UTF_8))) {
            
            // Read the entire file as a single JSON object
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            
            String jsonContent = content.toString();
            JsonObject obj = gson.fromJson(jsonContent, JsonObject.class);
            
            if (obj != null) {
                Long ts = extractTimestamp(obj);
                if (ts != null) {
                    String type = inferType(obj);
                    int byteLen = jsonContent.getBytes(StandardCharsets.UTF_8).length;
                    entries.add(new IndexEntry(ts, 0, byteLen, type));
                    writer.write(ts + "\t0\t" + byteLen + "\t" + (type == null ? "" : type));
                    writer.newLine();
                    System.out.println("[INDEX] Created single entry for " + jsonFile.getName() + " at timestamp " + ts);
                } else {
                    System.out.println("[INDEX] No timestamp found in " + jsonFile.getName());
                }
            }
        }

        return new BuiltIndex(entries);
    }

    /**
     * Build an index for a NDJSON file. Each line must contain a timestamp field named either
     * "t" (preferred) or "timestamp" (string or number). Lines without timestamp are skipped.
     */
    public static BuiltIndex buildIndex(File ndjsonFile, File outIdxFile) throws Exception {
        List<IndexEntry> entries = new ArrayList<>();
        Gson gson = new Gson();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(ndjsonFile), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outIdxFile), StandardCharsets.UTF_8))) {
            String line;
            long offset = 0L;
            while ((line = reader.readLine()) != null) {
                int byteLen = line.getBytes(StandardCharsets.UTF_8).length;
                try {
                    JsonObject obj = gson.fromJson(line, JsonObject.class);
                    if (obj != null) {
                        Long ts = extractTimestamp(obj);
                        if (ts != null) {
                            String type = inferType(obj);
                            entries.add(new IndexEntry(ts, offset, byteLen, type));
                            writer.write(ts + "\t" + offset + "\t" + byteLen + "\t" + (type == null ? "" : type));
                            writer.newLine();
                        }
                    }
                } catch (Exception ignore) {
                    // skip malformed lines
                }
                // +1 assumes single "\n" line separator in the NDJSON. Acceptable for files we generate.
                offset += byteLen + 1;
            }
        }

        // Ensure sorted by timestamp
        Collections.sort(entries, (a, b) -> Long.compare(a.timestamp, b.timestamp));
        return new BuiltIndex(entries);
    }

    public static String readLineAt(File file, long offset, int length) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(offset);
            byte[] buf = new byte[length];
            int read = raf.read(buf);
            if (read <= 0) return null;
            return new String(buf, 0, read, StandardCharsets.UTF_8);
        }
    }

    private static Long extractTimestamp(JsonObject obj) {
        // prefer "t" as number; fallback to "timestamp" as string/number
        if (obj.has("t")) {
            JsonElement e = obj.get("t");
            if (e != null && e.isJsonPrimitive()) {
                try { return e.getAsLong(); } catch (Exception ignored) {}
            }
        }
        if (obj.has("timestamp")) {
            JsonElement e = obj.get("timestamp");
            if (e != null && e.isJsonPrimitive()) {
                try { return Long.parseLong(e.getAsString()); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private static String inferType(JsonObject obj) {
        if (obj.has("flow_info")) return "flow";
        if (obj.has("nodes") || obj.has("edges")) return "topology";
        if (obj.has("type")) try { return obj.get("type").getAsString(); } catch (Exception ignored) {}
        return null;
    }
}


