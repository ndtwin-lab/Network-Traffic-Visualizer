package org.example.demo2;

import java.util.List;

public class Node {
    public String ip;
    public String name;
    public int x;
    public int y;
    public String type;
    public boolean is_up;
    public boolean is_enabled;
    public List<String> ips; // New
    public String layer; // New: Store layer information (core, aggregation, edge, host)
    
    // New: CPU and memory utilization
    public Integer cpuUtilization; // CPU utilization (%)
    public Integer memoryUtilization; // Memory utilization (%)
    
    // New: Additional data received from API (not used for now)
    public long dpid; // Data path identifier (48-bit MAC address)
    public Long mac; // MAC address
    public String brandName; // Brand name
    public Integer deviceLayer; // Device layer
    public String originalDeviceName; // Original device name for grouping purposes

    public Node(String ip, String name, int x, int y, String type, boolean is_up, boolean is_enabled) {
        this.ip = ip;
        this.name = name;
        this.x = x;
        this.y = y;
        this.type = type;
        this.is_up = is_up;
        this.is_enabled = is_enabled;
        this.ips = null;
        this.layer = "unknown"; // Default layer
        this.cpuUtilization = null;
        this.memoryUtilization = null;
        this.dpid = 0;
        this.mac = null;
        this.brandName = null;
        this.deviceLayer = null;
        this.originalDeviceName = name; // Default to name
    }

    // New: Constructor supporting multiple IPs
    public Node(String ip, String name, int x, int y, String type, boolean is_up, boolean is_enabled, List<String> ips) {
        this.ip = ip;
        this.name = name;
        this.x = x;
        this.y = y;
        this.type = type;
        this.is_up = is_up;
        this.is_enabled = is_enabled;
        this.ips = ips;
        this.layer = "unknown"; // Default layer
        this.cpuUtilization = null;
        this.memoryUtilization = null;
        this.dpid = 0;
        this.mac = null;
        this.brandName = null;
        this.deviceLayer = null;
        this.originalDeviceName = name; // Default to name
    }
    
    // New: Constructor supporting layer
    public Node(String ip, String name, int x, int y, String type, boolean is_up, boolean is_enabled, String layer) {
        this.ip = ip;
        this.name = name;
        this.x = x;
        this.y = y;
        this.type = type;
        this.is_up = is_up;
        this.is_enabled = is_enabled;
        this.ips = null;
        this.layer = layer;
        this.cpuUtilization = null;
        this.memoryUtilization = null;
        this.dpid = 0;
        this.mac = null;
        this.brandName = null;
        this.deviceLayer = null;
        this.originalDeviceName = name; // Default to name
    }
    
    // New: Constructor supporting utilization
    public Node(String ip, String name, int x, int y, String type, boolean is_up, boolean is_enabled, String layer, Integer cpuUtilization, Integer memoryUtilization) {
        this.ip = ip;
        this.name = name;
        this.x = x;
        this.y = y;
        this.type = type;
        this.is_up = is_up;
        this.is_enabled = is_enabled;
        this.ips = null;
        this.layer = layer;
        this.cpuUtilization = cpuUtilization;
        this.memoryUtilization = memoryUtilization;
        this.dpid = 0;
        this.mac = null;
        this.brandName = null;
        this.deviceLayer = null;
        this.originalDeviceName = name; // Default to name
    }
    
    // New: Getter and setter methods
    public Integer getCpuUtilization() {
        return cpuUtilization;
    }
    
    public void setCpuUtilization(Integer cpuUtilization) {
        this.cpuUtilization = cpuUtilization;
    }
    
    public Integer getMemoryUtilization() {
        return memoryUtilization;
    }
    
    public void setMemoryUtilization(Integer memoryUtilization) {
        this.memoryUtilization = memoryUtilization;
    }
    
    public boolean hasUtilizationData() {
        return cpuUtilization != null || memoryUtilization != null;
    }
} 