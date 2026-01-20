package org.example.demo2;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Reader for playback data using NDJSON indexes.
 * Provides efficient time-based queries without loading entire files.
 */
public class PlaybackDataReader {
    
    private final NdjsonIndexUtil.BuiltIndex flowIndex;
    private final NdjsonIndexUtil.BuiltIndex topoIndex;
    private final File flowFile;
    private final File topoFile;
    private final Gson gson;
    
    public NdjsonIndexUtil.BuiltIndex getTopoIndex() {
        return topoIndex;
    }
    
    public PlaybackDataReader(NdjsonIndexUtil.BuiltIndex flowIndex, NdjsonIndexUtil.BuiltIndex topoIndex, 
                             File flowFile, File topoFile) {
        this.flowIndex = flowIndex;
        this.topoIndex = topoIndex;
        this.flowFile = flowFile;
        this.topoFile = topoFile;
        this.gson = new Gson();
    }
    
    /**
     * Get the time range covered by both files
     */
    public TimeRange getTimeRange() {
        // Combine both flow and topology timestamps for complete time range
        long startTime = Long.MAX_VALUE;
        long endTime = Long.MIN_VALUE;
        
        if (flowIndex != null && !flowIndex.entries.isEmpty()) {
            long flowStart = flowIndex.entries.get(0).timestamp;
            long flowEnd = flowIndex.entries.get(flowIndex.entries.size() - 1).timestamp;
            startTime = Math.min(startTime, flowStart);
            endTime = Math.max(endTime, flowEnd);
            System.out.println("[PLAYBACK] TimeRange calculation (flow): Flow range: " + 
                flowStart + " to " + flowEnd + " (" + flowIndex.entries.size() + " entries)");
        }
        
        if (topoIndex != null && !topoIndex.entries.isEmpty()) {
            long topoStart = topoIndex.entries.get(0).timestamp;
            long topoEnd = topoIndex.entries.get(topoIndex.entries.size() - 1).timestamp;
            startTime = Math.min(startTime, topoStart);
            endTime = Math.max(endTime, topoEnd);
            System.out.println("[PLAYBACK] TimeRange calculation (topology): Topo range: " + 
                topoStart + " to " + topoEnd + " (" + topoIndex.entries.size() + " entries)");
        }
        
        long duration = endTime - startTime;
        System.out.println("[PLAYBACK] Final combined range: " + startTime + " to " + endTime + " (duration: " + duration + " ms)");
        
        return new TimeRange(startTime, endTime);
    }
    
    /**
     * Get topology data at or before the specified timestamp.
     * Returns the most recent topology snapshot.
     */
    public TopologySnapshot getTopologyAt(long timestamp) throws Exception {
        // Find the latest topology entry <= timestamp
        NdjsonIndexUtil.IndexEntry entry = findLatestTopologyEntry(timestamp);
        if (entry == null) {
            return null;
        }
        
        // Read the entry
        String jsonLine = NdjsonIndexUtil.readLineAt(topoFile, entry.offset, entry.length);
        if (jsonLine == null) {
            return null;
        }
        
        JsonObject obj = gson.fromJson(jsonLine, JsonObject.class);
        return parseTopologySnapshot(obj);
    }
    
    /**
     * Get flow data within a time window around the specified timestamp.
     * Returns flows that are active within the window.
     */
    public List<FlowSnapshot> getFlowsAt(long timestamp, long windowMs) throws Exception {
        long windowStart = timestamp - windowMs / 2;
        long windowEnd = timestamp + windowMs / 2;
        
        List<FlowSnapshot> flows = new ArrayList<>();
        
        // Find all flow entries within the window
        List<NdjsonIndexUtil.IndexEntry> entries = findFlowEntriesInRange(windowStart, windowEnd);
        
        for (NdjsonIndexUtil.IndexEntry entry : entries) {
            String jsonLine = NdjsonIndexUtil.readLineAt(flowFile, entry.offset, entry.length);
            if (jsonLine != null) {
                JsonObject obj = gson.fromJson(jsonLine, JsonObject.class);
                FlowSnapshot flow = parseFlowSnapshot(obj);
                if (flow != null) {
                    flows.add(flow);
                }
            }
        }
        
        return flows;
    }
    
    /**
     * Get all available timestamps for timeline navigation
     */
    public List<Long> getAllTimestamps() {
        List<Long> timestamps = new ArrayList<>();
        
        // Add flow timestamps
        for (NdjsonIndexUtil.IndexEntry entry : flowIndex.entries) {
            timestamps.add(entry.timestamp);
        }
        
        // Add topology timestamps
        for (NdjsonIndexUtil.IndexEntry entry : topoIndex.entries) {
            timestamps.add(entry.timestamp);
        }
        
        // Sort and deduplicate
        Collections.sort(timestamps);
        List<Long> unique = new ArrayList<>();
        Long last = null;
        for (Long ts : timestamps) {
            if (last == null || !ts.equals(last)) {
                unique.add(ts);
                last = ts;
            }
        }
        
        return unique;
    }
    
    private NdjsonIndexUtil.IndexEntry findLatestTopologyEntry(long timestamp) {
        // Binary search for the latest topology entry <= timestamp
        int left = 0;
        int right = topoIndex.entries.size() - 1;
        NdjsonIndexUtil.IndexEntry result = null;
        
        while (left <= right) {
            int mid = (left + right) / 2;
            NdjsonIndexUtil.IndexEntry entry = topoIndex.entries.get(mid);
            
            if (entry.timestamp <= timestamp) {
                result = entry;
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        
        return result;
    }
    
    private List<NdjsonIndexUtil.IndexEntry> findFlowEntriesInRange(long startTime, long endTime) {
        List<NdjsonIndexUtil.IndexEntry> result = new ArrayList<>();
        
        for (NdjsonIndexUtil.IndexEntry entry : flowIndex.entries) {
            if (entry.timestamp >= startTime && entry.timestamp <= endTime) {
                result.add(entry);
            }
        }
        
        return result;
    }
    
    private TopologySnapshot parseTopologySnapshot(JsonObject obj) {
        TopologySnapshot snapshot = new TopologySnapshot();
        
        // Support both "t" and "timestamp" fields
        if (obj.has("t")) {
            snapshot.timestamp = obj.get("t").getAsLong();
        } else if (obj.has("timestamp")) {
            snapshot.timestamp = obj.get("timestamp").getAsLong();
        } else {
            // Default timestamp if none found
            snapshot.timestamp = System.currentTimeMillis();
            System.out.println("[PLAYBACK] No timestamp found, using current time: " + snapshot.timestamp);
        }
        
        System.out.println("[PLAYBACK] Parsing topology snapshot at timestamp: " + snapshot.timestamp);
        System.out.println("[PLAYBACK] Available fields in topology data: " + obj.keySet());
        
        // Build helper maps to resolve links to node IPs
        java.util.Map<Long, String> dpidToIp = new java.util.HashMap<>();
        java.util.Map<String, String> nameToIp = new java.util.HashMap<>();
        
        // Parse nodes - handle both direct nodes array and nested structure
        if (obj.has("nodes") && obj.get("nodes").isJsonArray()) {
            JsonArray nodesArray = obj.getAsJsonArray("nodes");
            System.out.println("[PLAYBACK] Found " + nodesArray.size() + " nodes in topology data");
            for (JsonElement element : nodesArray) {
                if (element.isJsonObject()) {
                    Node node = parseNode(element.getAsJsonObject());
                    if (node != null) {
                        snapshot.nodes.add(node);
                        System.out.println("[PLAYBACK] Parsed node: " + node.name + " (" + node.ip + ")");
                        // Record mappings for link resolution
                        if (node.dpid != 0) {
                            dpidToIp.put((long) node.dpid, node.ip);
                        }
                        if (node.originalDeviceName != null) {
                            nameToIp.put(node.originalDeviceName, node.ip);
                        }
                    }
                }
            }
        } else {
            System.out.println("[PLAYBACK] No nodes array found in topology data");
        }
        
        // Parse edges - handle both direct edges array and nested structure
        if (obj.has("edges") && obj.get("edges").isJsonArray()) {
            JsonArray edgesArray = obj.getAsJsonArray("edges");
            System.out.println("[PLAYBACK] Found " + edgesArray.size() + " edges in topology data");
            for (int i = 0; i < edgesArray.size(); i++) {
                JsonElement element = edgesArray.get(i);
                if (element.isJsonObject()) {
                    JsonObject linkObj = element.getAsJsonObject();
                    System.out.println("[PLAYBACK] Edge " + i + " fields: " + linkObj.keySet());
                    Link link = parseLink(linkObj, dpidToIp, nameToIp);
                    if (link != null) {
                        snapshot.links.add(link);
                        System.out.println("[PLAYBACK] Parsed link: " + link.source + " -> " + link.target);
                    } else {
                        System.out.println("[PLAYBACK] Failed to parse link " + i);
                    }
                }
            }
        } else {
            System.out.println("[PLAYBACK] No edges array found in topology data");
        }
        
        System.out.println("[PLAYBACK] Final topology snapshot: " + snapshot.nodes.size() + " nodes, " + snapshot.links.size() + " links");
        return snapshot;
    }
    
    private FlowSnapshot parseFlowSnapshot(JsonObject obj) {
        FlowSnapshot snapshot = new FlowSnapshot();
        
        // Support both "t" and "timestamp" fields
        if (obj.has("t")) {
            snapshot.timestamp = obj.get("t").getAsLong();
        } else if (obj.has("timestamp")) {
            snapshot.timestamp = obj.get("timestamp").getAsLong();
        } else {
            // Default timestamp if none found
            snapshot.timestamp = System.currentTimeMillis();
            System.out.println("[PLAYBACK] No timestamp found in flow data, using current time: " + snapshot.timestamp);
        }
        
        System.out.println("[PLAYBACK] Parsing flow snapshot at timestamp: " + snapshot.timestamp);
        System.out.println("[PLAYBACK] Available fields in flow data: " + obj.keySet());
        
        if (obj.has("flow_info") && obj.get("flow_info").isJsonObject()) {
            JsonObject flowInfo = obj.getAsJsonObject("flow_info");
            System.out.println("[PLAYBACK] Found flow_info object");
            Flow flow = parseFlow(flowInfo);
            if (flow != null) {
                snapshot.flows.add(flow);
                System.out.println("[PLAYBACK] Parsed flow: " + flow.srcIp + " -> " + flow.dstIp);
            }
        } else if (obj.has("flow_info") && obj.get("flow_info").isJsonArray()) {
            JsonArray flowInfoArray = obj.getAsJsonArray("flow_info");
            System.out.println("[PLAYBACK] Found " + flowInfoArray.size() + " flows in flow_info array");
            for (JsonElement element : flowInfoArray) {
                if (element.isJsonObject()) {
                    Flow flow = parseFlow(element.getAsJsonObject());
                    if (flow != null) {
                        snapshot.flows.add(flow);
                        System.out.println("[PLAYBACK] Parsed flow: " + flow.srcIp + " -> " + flow.dstIp);
                    }
                }
            }
        } else {
            System.out.println("[PLAYBACK] No flow_info found in flow data");
        }
        
        System.out.println("[PLAYBACK] Final flow snapshot: " + snapshot.flows.size() + " flows");
        return snapshot;
    }
    
    private Node parseNode(JsonObject nodeObj) {
        // Parse node based on real-time topology format
        try {
            String deviceName = nodeObj.has("device_name") ? nodeObj.get("device_name").getAsString() : "unknown";
            int vertexType = nodeObj.has("vertex_type") ? nodeObj.get("vertex_type").getAsInt() : 0;
            boolean isUp = nodeObj.has("is_up") ? nodeObj.get("is_up").getAsBoolean() : true;
            boolean isEnabled = nodeObj.has("is_enabled") ? nodeObj.get("is_enabled").getAsBoolean() : true;
            int dpid = nodeObj.has("dpid") ? nodeObj.get("dpid").getAsInt() : 0;
            Long mac = nodeObj.has("mac") ? nodeObj.get("mac").getAsLong() : null;
            String brandName = nodeObj.has("brand_name") ? nodeObj.get("brand_name").getAsString() : null;
            Integer deviceLayer = nodeObj.has("device_layer") ? nodeObj.get("device_layer").getAsInt() : null;
            
            // Parse IP list
            String ip = "";
            if (nodeObj.has("ip") && nodeObj.get("ip").isJsonArray()) {
                JsonArray ipArray = nodeObj.getAsJsonArray("ip");
                if (!ipArray.isEmpty()) {
                    Long ipValue = ipArray.get(0).getAsLong();
                    ip = convertIpToString(ipValue);
                }
            }
            
            int x = 100, y = 100; // Default position, will be auto-layouted
            String type = String.valueOf(vertexType);
            
            Node node = new Node(ip, deviceName, x, y, type, isUp, isEnabled);
            
            // Set layer based on device_layer
            if (deviceLayer != null) {
                switch (deviceLayer) {
                    case 0: node.layer = "core"; break;
                    case 1: node.layer = "aggregation"; break;
                    case 2: node.layer = "edge"; break;
                    default: node.layer = "unknown"; break;
                }
            } else {
                node.layer = vertexType == 1 ? "host" : "switch";
            }
            
            // Store additional data
            node.dpid = dpid;
            node.mac = mac;
            node.brandName = brandName;
            node.deviceLayer = deviceLayer;
            node.originalDeviceName = deviceName;
            
            return node;
            
        } catch (Exception e) {
            System.err.println("[PLAYBACK] Failed to parse node: " + e.getMessage());
            return null;
        }
    }
    
    private Link parseLink(JsonObject linkObj, java.util.Map<Long, String> dpidToIp, java.util.Map<String, String> nameToIp) {
        // Parse link based on real-time topology format
        try {
            System.out.println("[PLAYBACK] Parsing link with fields: " + linkObj.keySet());
            
            // Try different possible field names for source and destination
            String src = "";
            String dst = "";
            
            if (linkObj.has("src")) {
                src = linkObj.get("src").getAsString();
            } else if (linkObj.has("source")) {
                src = linkObj.get("source").getAsString();
            } else if (linkObj.has("from")) {
                src = linkObj.get("from").getAsString();
            } else             if (linkObj.has("src_ip")) {
                // Convert numeric IP to dotted string if needed
                try {
                    if (linkObj.get("src_ip").isJsonArray()) {
                        // Handle array format: "src_ip": [192653504]
                        JsonArray srcIpArray = linkObj.getAsJsonArray("src_ip");
                        if (!srcIpArray.isEmpty()) {
                            long v = srcIpArray.get(0).getAsLong();
                            src = convertIpToString(v);
                        }
                    } else {
                        // Handle single value format
                        long v = linkObj.get("src_ip").getAsLong();
                        src = convertIpToString(v);
                    }
                } catch (Exception e) {
                    try {
                        src = linkObj.get("src_ip").getAsString();
                    } catch (Exception e2) {
                        System.err.println("[PLAYBACK] Failed to parse src_ip: " + e2.getMessage());
                    }
                }
            }
            
            if (linkObj.has("dst")) {
                dst = linkObj.get("dst").getAsString();
            } else if (linkObj.has("destination")) {
                dst = linkObj.get("destination").getAsString();
            } else if (linkObj.has("to")) {
                dst = linkObj.get("to").getAsString();
            } else             if (linkObj.has("dst_ip")) {
                // Convert numeric IP to dotted string if needed
                try {
                    if (linkObj.get("dst_ip").isJsonArray()) {
                        // Handle array format: "dst_ip": [259762368]
                        JsonArray dstIpArray = linkObj.getAsJsonArray("dst_ip");
                        if (!dstIpArray.isEmpty()) {
                            long v = dstIpArray.get(0).getAsLong();
                            dst = convertIpToString(v);
                        }
                    } else {
                        // Handle single value format
                        long v = linkObj.get("dst_ip").getAsLong();
                        dst = convertIpToString(v);
                    }
                } catch (Exception e) {
                    try {
                        dst = linkObj.get("dst_ip").getAsString();
                    } catch (Exception e2) {
                        System.err.println("[PLAYBACK] Failed to parse dst_ip: " + e2.getMessage());
                    }
                }
            }
            
            // Try resolve via DPID mapping if available
            if ((src == null || src.isEmpty()) && linkObj.has("src_dpid")) {
                try {
                    long sd = linkObj.get("src_dpid").getAsLong();
                    if (dpidToIp.containsKey(sd)) src = dpidToIp.get(sd);
                } catch (Exception ignore) {}
            }
            if ((dst == null || dst.isEmpty()) && linkObj.has("dst_dpid")) {
                try {
                    long dd = linkObj.get("dst_dpid").getAsLong();
                    if (dpidToIp.containsKey(dd)) dst = dpidToIp.get(dd);
                } catch (Exception ignore) {}
            }
            
            // Try resolve via name mapping if available
            if ((src == null || src.isEmpty()) && linkObj.has("src_name")) {
                String n = linkObj.get("src_name").getAsString();
                if (nameToIp.containsKey(n)) src = nameToIp.get(n);
            }
            if ((dst == null || dst.isEmpty()) && linkObj.has("dst_name")) {
                String n = linkObj.get("dst_name").getAsString();
                if (nameToIp.containsKey(n)) dst = nameToIp.get(n);
            }

            System.out.println("[PLAYBACK] Extracted src: '" + src + "', dst: '" + dst + "'");
            
            // Create minimal link with required fields
            List<String> sourceIps = new ArrayList<>();
            List<String> targetIps = new ArrayList<>();
            List<Flow> flowSet = new ArrayList<>();
            
            boolean isUp = linkObj.has("is_up") ? linkObj.get("is_up").getAsBoolean() : true;
            int bandwidth = linkObj.has("bandwidth") ? linkObj.get("bandwidth").getAsInt() : 1000;
            boolean isEnabled = linkObj.has("is_enabled") ? linkObj.get("is_enabled").getAsBoolean() : true;
            double utilization = linkObj.has("link_bandwidth_utilization_percent") ? 
                               linkObj.get("link_bandwidth_utilization_percent").getAsDouble() : 0.0;
            
            Long srcDpid = linkObj.has("src_dpid") ? linkObj.get("src_dpid").getAsLong() : null;
            Long dstDpid = linkObj.has("dst_dpid") ? linkObj.get("dst_dpid").getAsLong() : null;
            int dstPort = linkObj.has("dst_port") ? linkObj.get("dst_port").getAsInt() : 0;
            int srcInterface = linkObj.has("src_interface") ? linkObj.get("src_interface").getAsInt() : 0;
            int dstInterface = linkObj.has("dst_interface") ? linkObj.get("dst_interface").getAsInt() : 0;
            long leftBandwidth = linkObj.has("left_link_bandwidth_bps") ? 
                               linkObj.get("left_link_bandwidth_bps").getAsLong() : 0;
            long bandwidthUsage = linkObj.has("link_bandwidth_usage_bps") ? 
                                linkObj.get("link_bandwidth_usage_bps").getAsLong() : 0;
            
            return new Link(src, dst, sourceIps, targetIps, isUp, bandwidth, isEnabled, 
                          utilization, flowSet, srcDpid, dstDpid, dstPort, srcInterface, 
                          dstInterface, leftBandwidth, bandwidthUsage);
                          
        } catch (Exception e) {
            System.err.println("[PLAYBACK] Failed to parse link: " + e.getMessage());
            return null;
        }
    }
    
    private Flow parseFlow(JsonObject flowObj) {
        // Parse flow based on your provided format
        if (flowObj.has("src_ip") && flowObj.has("dst_ip")) {
            // Convert integer IPs to string format
            long srcIp = flowObj.get("src_ip").getAsLong();
            long dstIp = flowObj.get("dst_ip").getAsLong();
            String srcIpStr = convertIpToString(srcIp);
            String dstIpStr = convertIpToString(dstIp);
            
            int srcPort = flowObj.has("src_port") ? flowObj.get("src_port").getAsInt() : 0;
            int dstPort = flowObj.has("dst_port") ? flowObj.get("dst_port").getAsInt() : 0;
            int protocolId = flowObj.has("protocol_id") ? flowObj.get("protocol_id").getAsInt() : 0;
            
            // Parse path if available
            List<String> pathNodes = new ArrayList<>();
            List<Integer> pathPorts = new ArrayList<>();
            if (flowObj.has("path") && flowObj.get("path").isJsonArray()) {
                JsonArray pathArray = flowObj.getAsJsonArray("path");
                for (JsonElement element : pathArray) {
                    if (element.isJsonObject()) {
                        JsonObject pathNode = element.getAsJsonObject();
                        if (pathNode.has("node")) {
                            String node = pathNode.get("node").getAsString();
                            pathNodes.add(node);
                        }
                        if (pathNode.has("interface")) {
                            int port = pathNode.get("interface").getAsInt();
                            pathPorts.add(port);
                        }
                    }
                }
            }
            
            // Parse flow rates
            double lastSecRate = 0.0;
            double proceedingRate = 0.0;
            int lastSecPacketRate = 0;
            int proceedingPacketRate = 0;
            
            if (flowObj.has("estimated_flow_sending_rate_bps_in_the_last_sec")) {
                try {
                    lastSecRate = Double.parseDouble(flowObj.get("estimated_flow_sending_rate_bps_in_the_last_sec").getAsString());
                } catch (Exception e) {
                    lastSecRate = 0.0;
                }
            }
            if (flowObj.has("estimated_flow_sending_rate_bps_in_the_proceeding_1sec_timeslot")) {
                try {
                    proceedingRate = Double.parseDouble(flowObj.get("estimated_flow_sending_rate_bps_in_the_proceeding_1sec_timeslot").getAsString());
                } catch (Exception e) {
                    proceedingRate = 0.0;
                }
            }
            if (flowObj.has("estimated_packet_rate_in_the_last_sec")) {
                try {
                    lastSecPacketRate = Integer.parseInt(flowObj.get("estimated_packet_rate_in_the_last_sec").getAsString());
                } catch (Exception e) {
                    lastSecPacketRate = 0;
                }
            }
            if (flowObj.has("estimated_packet_rate_in_the_proceeding_1sec_timeslot")) {
                try {
                    proceedingPacketRate = Integer.parseInt(flowObj.get("estimated_packet_rate_in_the_proceeding_1sec_timeslot").getAsString());
                } catch (Exception e) {
                    proceedingPacketRate = 0;
                }
            }
            
            // Create flow with all required parameters
            return new Flow(pathNodes, pathPorts, srcIpStr, dstIpStr, srcPort, dstPort, 
                          protocolId, 0, 0, lastSecRate, proceedingRate, lastSecPacketRate, proceedingPacketRate);
        }
        return null;
    }
    
    private String convertIpToString(long ip) {
        // Convert IP integer to dotted decimal string
        return String.format("%d.%d.%d.%d",
            (ip >> 24) & 0xFF,
            (ip >> 16) & 0xFF,
            (ip >> 8) & 0xFF,
            ip & 0xFF);
    }
    
    // Data classes
    public static class TimeRange {
        public final long startTime;
        public final long endTime;
        public final long duration;
        
        public TimeRange(long startTime, long endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.duration = endTime - startTime;
        }
    }
    
    public static class TopologySnapshot {
        public long timestamp;
        public List<Node> nodes = new ArrayList<>();
        public List<Link> links = new ArrayList<>();
    }
    
    public static class FlowSnapshot {
        public long timestamp;
        public List<Flow> flows = new ArrayList<>();
    }
    
    /**
     * 獲取所有topology快照的索引信息（用於智能對齊，不載入實際數據）
     */
    public List<NdjsonIndexUtil.IndexEntry> getAllTopologyIndexEntries() {
        if (topoIndex == null || topoIndex.entries.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(topoIndex.entries);
    }
    
    /**
     * 獲取所有flow快照的索引信息（用於智能對齊，不載入實際數據）
     */
    public List<NdjsonIndexUtil.IndexEntry> getAllFlowIndexEntries() {
        if (flowIndex == null || flowIndex.entries.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(flowIndex.entries);
    }
}
