package org.example.demo2;

import java.util.List;

public class GraphData {
    public List<Node> nodes;
    public List<Edge> edges;

    public static class Node {
        public String device_name;
        public List<Long> ip;
        public boolean is_enabled;
        public boolean is_up;
        public long mac;
        public int vertex_type;
        public int device_layer; // New: Fat Tree layer, 0=Core, 1=Aggregation, 2=Edge
        
        // New: Additional data received from API
        public long dpid; // Data path identifier (48-bit MAC address)
        public String brand_name; // Brand name
    }

    public static class Edge {

        public List<Long> dst_ip;

        public List<FlowSet> flow_set;
        public boolean is_enabled;
        public boolean is_up;
        public long link_bandwidth_bps;
        public double link_bandwidth_utilization_percent;
        public List<Long> src_ip;
        
        // New: Additional data received from API
        public Long dst_dpid; // Target device data path identifier
        public Long src_dpid; // Source device data path identifier
        public int dst_port; // Target port
        public int src_interface; // Source interface/port
        public int dst_interface; // Target interface/port
        public long left_link_bandwidth_bps; // Remaining link bandwidth
        public long link_bandwidth_usage_bps; // Link bandwidth usage
    }

    public static class FlowSet {
        public long dst_ip;
        public int dst_port;
        public int protocol_number;
        public long src_ip;
        public int src_port;
    }
} 