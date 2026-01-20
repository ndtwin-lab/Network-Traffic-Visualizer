package org.example.demo2;

import java.util.List;

public class Link {
    public String source;
    public String target;
    public List<String> sourceIps;
    public List<String> targetIps;
    public boolean is_up;
    public int bandwidth;
    public boolean is_enabled;
    public double link_bandwidth_utilization_percent;
    public List<Flow> flow_set;
    
    //  Additional data received from API
    public Long srcDpid;
    public Long dstDpid;
    public int dstPort;
    public int srcInterface;
    public int dstInterface;
    public long leftLinkBandwidthBps;
    public long linkBandwidthUsageBps;


    // New: Constructor supporting all fields
    public Link(String source, String target, List<String> sourceIps, List<String> targetIps, boolean is_up, int bandwidth, boolean is_enabled, double link_bandwidth_utilization_percent, List<Flow> flow_set, Long srcDpid, Long dstDpid, int dstPort, int srcInterface, int dstInterface, long leftLinkBandwidthBps, long linkBandwidthUsageBps) {
        this.source = source;
        this.target = target;
        this.sourceIps = sourceIps;
        this.targetIps = targetIps;
        this.is_up = is_up;
        this.bandwidth = bandwidth;
        this.is_enabled = is_enabled;
        this.link_bandwidth_utilization_percent = link_bandwidth_utilization_percent;
        this.flow_set = flow_set;
        this.srcDpid = srcDpid;
        this.dstDpid = dstDpid;
        this.dstPort = dstPort;
        this.srcInterface = srcInterface;
        this.dstInterface = dstInterface;
        this.leftLinkBandwidthBps = leftLinkBandwidthBps;
        this.linkBandwidthUsageBps = linkBandwidthUsageBps;
    }
} 