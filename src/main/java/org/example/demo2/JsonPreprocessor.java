package org.example.demo2;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;

/**
 * Preprocessor to convert large single-JSON files to NDJSON format for efficient playback.
 * Handles both flow history and topology history files.
 */
public class JsonPreprocessor {
    
    public static class PreprocessResult {
        public final File ndjsonFile;
        public final int totalEntries;
        public final long timeRangeMs;
        public final long startTime;
        public final long endTime;
        
        public PreprocessResult(File ndjsonFile, int totalEntries, long timeRangeMs, long startTime, long endTime) {
            this.ndjsonFile = ndjsonFile;
            this.totalEntries = totalEntries;
            this.timeRangeMs = timeRangeMs;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }
    
    /**
     * Convert a large flow history JSON file to NDJSON format.
     * Input format: {"timestamp": "1758198998998", "flow_info": [...]}
     * Output format: One line per flow entry with timestamp
     */
    public static PreprocessResult preprocessFlowHistory(File inputFile, File outputDir) throws Exception {
        File outputFile = new File(outputDir, inputFile.getName().replaceAll("\\.(json|JSON)$", ".ndjson"));
        
        AtomicLong totalEntries = new AtomicLong(0);
        AtomicLong startTime = new AtomicLong(Long.MAX_VALUE);
        AtomicLong endTime = new AtomicLong(Long.MIN_VALUE);
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {
            
            Gson gson = new Gson();
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setLenient(true);
            
            JsonObject root = gson.fromJson(jsonReader, JsonObject.class);
            if (root == null) {
                throw new IllegalArgumentException("Invalid JSON file: " + inputFile.getName());
            }
            
            // Extract timestamp
            long timestamp = extractTimestamp(root);
            if (timestamp == 0) {
                System.err.println("[PREPROCESS] No valid timestamp found in flow file. Available fields: " + root.keySet());
                throw new IllegalArgumentException("No valid timestamp found in flow history file");
            }
            
            System.out.println("[PREPROCESS] Flow timestamp: " + timestamp + " (" + new java.util.Date(timestamp) + ")");
            
            // Process flow_info array
            JsonArray flowInfoArray = root.getAsJsonArray("flow_info");
            if (flowInfoArray != null) {
                for (JsonElement element : flowInfoArray) {
                    if (element.isJsonObject()) {
                        JsonObject flowObj = element.getAsJsonObject();
                        
                        // Create NDJSON entry
                        JsonObject ndjsonEntry = new JsonObject();
                        ndjsonEntry.addProperty("t", timestamp);
                        ndjsonEntry.add("flow_info", flowObj);
                        
                        writer.write(gson.toJson(ndjsonEntry));
                        writer.newLine();
                        
                        totalEntries.incrementAndGet();
                        startTime.set(Math.min(startTime.get(), timestamp));
                        endTime.set(Math.max(endTime.get(), timestamp));
                    }
                }
            }
        }
        
        long timeRange = endTime.get() - startTime.get();
        return new PreprocessResult(outputFile, (int)totalEntries.get(), timeRange, startTime.get(), endTime.get());
    }
    
    /**
     * Convert a large topology history JSON file to NDJSON format.
     * Input format: {"timestamp": "1758198998004", "nodes": [...], "edges": [...]}
     * Output format: One line per topology snapshot with timestamp
     */
    public static PreprocessResult preprocessTopologyHistory(File inputFile, File outputDir) throws Exception {
        File outputFile = new File(outputDir, inputFile.getName().replaceAll("\\.(json|JSON)$", ".ndjson"));
        
        AtomicLong totalEntries = new AtomicLong(0);
        AtomicLong startTime = new AtomicLong(Long.MAX_VALUE);
        AtomicLong endTime = new AtomicLong(Long.MIN_VALUE);

        // Helper to locate an object that actually contains nodes/edges arrays
        java.util.function.Function<JsonObject, JsonObject> findGraphObj = (JsonObject candidate) -> {
            if (candidate == null) return null;
            if ((candidate.has("nodes") && candidate.get("nodes").isJsonArray()) ||
                (candidate.has("edges") && candidate.get("edges").isJsonArray())) {
                return candidate;
            }
            if (candidate.has("graph") && candidate.get("graph").isJsonObject()) {
                JsonObject g = candidate.getAsJsonObject("graph");
                if ((g.has("nodes") && g.get("nodes").isJsonArray()) || (g.has("edges") && g.get("edges").isJsonArray()))
                    return g;
            }
            if (candidate.has("topology") && candidate.get("topology").isJsonObject()) {
                JsonObject g = candidate.getAsJsonObject("topology");
                if ((g.has("nodes") && g.get("nodes").isJsonArray()) || (g.has("edges") && g.get("edges").isJsonArray()))
                    return g;
            }
            return null;
        };

        // First, try to detect NDJSON (one JSON object per line). If we can parse
        // more than one valid object with timestamp, treat as NDJSON and normalize.
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {

            Gson gson = new Gson();
            String line;
            int ndjsonCount = 0;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                try {
                    JsonObject obj = gson.fromJson(trimmed, JsonObject.class);
                    if (obj == null) continue;
                    long ts = extractTimestamp(obj);
                    JsonObject graphObj = findGraphObj.apply(obj);
                    if (ts != 0 && graphObj != null) {
                        JsonObject nd = new JsonObject();
                        nd.addProperty("t", ts);
                        nd.addProperty("type", "topology");
                        if (graphObj.has("nodes")) nd.add("nodes", graphObj.get("nodes"));
                        if (graphObj.has("edges")) nd.add("edges", graphObj.get("edges"));
                        writer.write(gson.toJson(nd));
                        writer.newLine();
                        ndjsonCount++;
                        totalEntries.incrementAndGet();
                        startTime.set(Math.min(startTime.get(), ts));
                        endTime.set(Math.max(endTime.get(), ts));
                    }
                } catch (Exception ignored) {
                    // Not a single-object JSON line; fall through to structured parse later
                }
            }

            if (ndjsonCount > 1) {
                long timeRange = endTime.get() - startTime.get();
                return new PreprocessResult(outputFile, (int)totalEntries.get(), timeRange, startTime.get(), endTime.get());
            }
        }

        // If not NDJSON with multiple lines, fallback to structured single-root parsing
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {
            
            Gson gson = new Gson();
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setLenient(true);
            
            JsonObject root = gson.fromJson(jsonReader, JsonObject.class);
            if (root == null) {
                throw new IllegalArgumentException("Invalid JSON file: " + inputFile.getName());
            }
            
            // Strategy:
            // 1) Try to detect arrays of snapshots anywhere under root and write one NDJSON line per snapshot
            // 2) If no snapshot array detected, fallback to treating the whole file as a single snapshot
            
            // Try common array keys, else scan all arrays under root
            java.util.List<JsonArray> candidateArrays = new java.util.ArrayList<>();
            String[] possibleKeys = new String[] {"history", "histories", "snapshots", "topologies", "topology_history", "graphs", "data", "records"};
            for (String k : possibleKeys) {
                if (root.has(k) && root.get(k).isJsonArray()) {
                    candidateArrays.add(root.getAsJsonArray(k));
                }
            }
            // Fallback: collect any arrays directly under root
            for (java.util.Map.Entry<String, JsonElement> e : root.entrySet()) {
                if (e.getValue().isJsonArray() && !candidateArrays.contains(e.getValue().getAsJsonArray())) {
                    candidateArrays.add(e.getValue().getAsJsonArray());
                }
            }
            
            int written = 0;
            for (JsonArray arr : candidateArrays) {
                for (JsonElement el : arr) {
                    if (!el.isJsonObject()) continue;
                    JsonObject obj = el.getAsJsonObject();
                    long ts = extractTimestamp(obj);
                    if (ts == 0 && obj.has("graph") && obj.get("graph").isJsonObject()) {
                        ts = extractTimestamp(obj.getAsJsonObject("graph"));
                    }
                    JsonObject graphObj = findGraphObj.apply(obj);
                    if (ts != 0 && graphObj != null) {
                        JsonObject nd = new JsonObject();
                        nd.addProperty("t", ts);
                        nd.addProperty("type", "topology");
                        if (graphObj.has("nodes")) nd.add("nodes", graphObj.get("nodes"));
                        if (graphObj.has("edges")) nd.add("edges", graphObj.get("edges"));
                        writer.write(gson.toJson(nd));
                        writer.newLine();
                        totalEntries.incrementAndGet();
                        startTime.set(Math.min(startTime.get(), ts));
                        endTime.set(Math.max(endTime.get(), ts));
                        written++;
                    }
                }
            }
            
            // If nothing was written, fallback to single-snapshot behavior
            if (written == 0) {
                long timestamp = extractTimestamp(root);
                if (timestamp == 0) {
                    System.err.println("[PREPROCESS] No valid timestamp found in topology file. Available fields: " + root.keySet());
                    throw new IllegalArgumentException("No valid timestamp found in topology history file");
                }
                System.out.println("[PREPROCESS] Topology timestamp: " + timestamp + " (" + new java.util.Date(timestamp) + ")");
                JsonObject graphObj = findGraphObj.apply(root);
                JsonObject ndjsonEntry = new JsonObject();
                ndjsonEntry.addProperty("t", timestamp);
                ndjsonEntry.addProperty("type", "topology");
                if (graphObj != null) {
                    if (graphObj.has("nodes")) ndjsonEntry.add("nodes", graphObj.get("nodes"));
                    if (graphObj.has("edges")) ndjsonEntry.add("edges", graphObj.get("edges"));
                } else {
                    if (root.has("nodes")) ndjsonEntry.add("nodes", root.get("nodes"));
                    if (root.has("edges")) ndjsonEntry.add("edges", root.get("edges"));
                }
                writer.write(gson.toJson(ndjsonEntry));
                writer.newLine();
                totalEntries.incrementAndGet();
                startTime.set(Math.min(startTime.get(), timestamp));
                endTime.set(Math.max(endTime.get(), timestamp));
            }
        }
        
        long timeRange = endTime.get() - startTime.get();
        return new PreprocessResult(outputFile, (int)totalEntries.get(), timeRange, startTime.get(), endTime.get());
    }
    
    /**
     * Split NDJSON file into time-based chunks for better performance.
     * Each chunk covers a specified time window (e.g., 5 minutes).
     */
    public static List<File> splitNdjsonByTime(File ndjsonFile, File outputDir, long chunkSizeMs) throws Exception {
        List<File> chunkFiles = new ArrayList<>();
        List<String> currentChunk = new ArrayList<>();
        long currentChunkStart = 0;
        long currentChunkEnd = 0;
        int chunkIndex = 0;
        
        Gson gson = new Gson();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(ndjsonFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                
                try {
                    JsonObject obj = gson.fromJson(line, JsonObject.class);
                    long timestamp = obj.get("t").getAsLong();
                    
                    if (currentChunk.isEmpty()) {
                        currentChunkStart = timestamp;
                        currentChunkEnd = timestamp;
                    }
                    
                    // Check if we need to start a new chunk
                    if (!currentChunk.isEmpty() && (timestamp - currentChunkStart) > chunkSizeMs) {
                        // Write current chunk
                        File chunkFile = writeChunk(currentChunk, outputDir, ndjsonFile.getName(), chunkIndex++, currentChunkStart, currentChunkEnd);
                        chunkFiles.add(chunkFile);
                        
                        // Start new chunk
                        currentChunk.clear();
                        currentChunkStart = timestamp;
                    }
                    
                    currentChunk.add(line);
                    currentChunkEnd = Math.max(currentChunkEnd, timestamp);
                    
                } catch (Exception e) {
                    // Skip malformed lines
                    System.err.println("Skipping malformed line: " + e.getMessage());
                }
            }
            
            // Write final chunk
            if (!currentChunk.isEmpty()) {
                File chunkFile = writeChunk(currentChunk, outputDir, ndjsonFile.getName(), chunkIndex++, currentChunkStart, currentChunkEnd);
                chunkFiles.add(chunkFile);
            }
        }
        
        return chunkFiles;
    }
    
    private static File writeChunk(List<String> lines, File outputDir, String baseName, int chunkIndex, long startTime, long endTime) throws Exception {
        String chunkFileName = baseName.replaceAll("\\.(ndjson|NDJSON)$", "") + 
                              String.format("_chunk_%d_%d_%d.ndjson", chunkIndex, startTime, endTime);
        File chunkFile = new File(outputDir, chunkFileName);
        
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(chunkFile), StandardCharsets.UTF_8))) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }
        
        return chunkFile;
    }
    
    private static long extractTimestamp(JsonObject obj) {
        // Try "timestamp" field first (string format)
        if (obj.has("timestamp")) {
            JsonElement ts = obj.get("timestamp");
            if (ts.isJsonPrimitive()) {
                try {
                    return Long.parseLong(ts.getAsString());
                } catch (NumberFormatException e) {
                    // Try as number
                    try {
                        return ts.getAsLong();
                    } catch (Exception ignored) {}
                }
            }
        }
        
        // Try "t" field
        if (obj.has("t")) {
            JsonElement ts = obj.get("t");
            if (ts.isJsonPrimitive()) {
                try {
                    return ts.getAsLong();
                } catch (Exception ignored) {}
            }
        }
        
        return 0;
    }
}
