package org.example.demo2;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class DetectedFlowData {
    public long dst_ip;
    public int dst_port;
    public long estimated_flow_sending_rate_bps_in_the_last_sec;
    public long estimated_flow_sending_rate_bps_in_the_proceeding_1sec_timeslot;
    public long estimated_packet_rate_in_the_last_sec;
    public long estimated_packet_rate_in_the_proceeding_1sec_timeslot;
    public String first_sampled_time;  // Changed to String to match API format "2025-11-11 10:30:35"
    public String latest_sampled_time; // Changed to String to match API format "2025-11-11 11:27:26"
    public List<PathNode> path;
    public int protocol_id;
    public long src_ip;
    public int src_port;

    public static class PathNode {
        public long node;
        
        @SerializedName("interface")  // JSON field name is "interface", but Java field name is interface_id (interface is a keyword)
        public int interface_id; // Changed to interface_id to match API data structure
    }
} 