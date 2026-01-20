package org.example.demo2;

import com.google.gson.Gson;
import java.io.FileReader;
import java.util.List;
import java.util.ArrayList;

/**
 * Test class to verify flow path parsing without running the GUI
 */
public class FlowPathTest {
    
    public static void main(String[] args) {
        System.out.println("========== FLOW PATH PARSING TEST ==========\n");
        
        try {
            // Read topology data
            Gson gson = new Gson();
            GraphData graphData = gson.fromJson(new FileReader("message-3.txt"), GraphData.class);
            System.out.println("[INFO] Loaded topology: " + graphData.nodes.size() + " nodes, " + 
                             graphData.edges.size() + " edges\n");
            
            // Read flow data
            DetectedFlowData[] flowData = gson.fromJson(new FileReader("flow_data.txt"), DetectedFlowData[].class);
            System.out.println("[INFO] Loaded flows: " + flowData.length + " flows\n");
            
            // Convert nodes (similar to NetworkTopologyApp.convertGraphNodes)
            List<Node> nodes = convertNodes(graphData.nodes);
            System.out.println("[INFO] Converted " + nodes.size() + " GUI nodes\n");
            
            // Test flow conversion
            testFlowConversion(flowData, nodes);
            
        } catch (Exception e) {
            System.err.println("[ERROR] Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static List<Node> convertNodes(List<GraphData.Node> apiNodes) {
        List<Node> nodes = new ArrayList<>();
        
        for (GraphData.Node n : apiNodes) {
            String ip = "";
            if (n.ip != null && !n.ip.isEmpty()) {
                Long ipValue = n.ip.get(0);
                if (ipValue != null) {
                    ip = convertIpToString(ipValue);
                }
            }
            
            Node node = new Node(ip, n.device_name, 100, 100, String.valueOf(n.vertex_type), n.is_up, n.is_enabled);
            node.dpid = n.dpid;
            node.mac = n.mac;
            node.brandName = n.brand_name;
            node.deviceLayer = n.device_layer;
            
            // Store all IPs
            if (n.ip != null && !n.ip.isEmpty()) {
                List<String> allIps = new ArrayList<>();
                for (Long ipVal : n.ip) {
                    allIps.add(convertIpToString(ipVal));
                }
                node.ips = allIps;
            }
            
            nodes.add(node);
        }
        
        return nodes;
    }
    
    private static void testFlowConversion(DetectedFlowData[] apiFlows, List<Node> nodes) {
        System.out.println("========== FLOW PATH ANALYSIS (Total: " + apiFlows.length + " flows) ==========\n");
        
        int flowIndex = 0;
        for (DetectedFlowData f : apiFlows) {
            flowIndex++;
            System.out.println("====== FLOW #" + flowIndex + " ======");
            System.out.println("[DEBUG] Processing flow - src_ip: " + f.src_ip + " (0x" + Long.toHexString(f.src_ip) + 
                             "), dst_ip: " + f.dst_ip + " (0x" + Long.toHexString(f.dst_ip) + ")");
            System.out.println("[DEBUG] Flow path is: " + (f.path == null ? "NULL" : "not null, size=" + f.path.size()));
            System.out.println("[DEBUG] Flow src_port: " + f.src_port + ", dst_port: " + f.dst_port);
            System.out.println("[DEBUG] Flow protocol: " + f.protocol_id);
            
            List<String> pathNodes = new ArrayList<>();
            List<Integer> pathPorts = new ArrayList<>();
            
            if (f.path != null && !f.path.isEmpty()) {
                System.out.println("[DEBUG] Flow has " + f.path.size() + " path nodes");
                
                for (DetectedFlowData.PathNode pn : f.path) {
                    System.out.println("[DEBUG] Path node: " + pn.node + " (0x" + Long.toHexString(pn.node) + 
                                     "), interface: " + pn.interface_id);
                    
                    String nodeIp = null;
                    
                    if (pn.node > 0xFFFFFFFFL) {
                        System.out.println("[DEBUG] Path node is DPID (> 32-bit), looking up node IP...");
                        nodeIp = findNodeIpByDpid(pn.node, nodes);
                        if (nodeIp != null) {
                            System.out.println("[DEBUG] Found node IP for DPID " + pn.node + ": " + nodeIp);
                        } else {
                            System.out.println("[WARN] Could not find node IP for DPID: " + pn.node);
                        }
                    } else {
                        System.out.println("[DEBUG] Path node is IP address (32-bit), converting...");
                        nodeIp = convertIpToString(pn.node);
                        System.out.println("[DEBUG] Converted IP: " + nodeIp);
                    }
                    
                    if (nodeIp != null && !"0.0.0.0".equals(nodeIp)) {
                        pathNodes.add(nodeIp);
                        pathPorts.add(pn.interface_id);
                    } else {
                        System.err.println("[ERROR] ❌ Failed to resolve path node: " + pn.node + 
                                         " (0x" + Long.toHexString(pn.node) + "). This node will be MISSING from path!");
                    }
                }
                
                System.out.println("\n[RESULT] Original path size: " + f.path.size());
                System.out.println("[RESULT] Resolved path size: " + pathNodes.size());
                if (pathNodes.size() < f.path.size()) {
                    System.err.println("[ERROR] ❌❌❌ PATH INCOMPLETE! Lost " + (f.path.size() - pathNodes.size()) + " nodes!");
                    System.err.println("[ERROR] Flow animation will have GAPS in the middle!");
                } else {
                    System.out.println("[SUCCESS] ✅ Complete path preserved - all " + pathNodes.size() + " nodes resolved");
                }
                System.out.println("[RESULT] Flow path IPs: " + pathNodes);
                System.out.println("[RESULT] Flow interfaces: " + pathPorts);
            }
            System.out.println();
        }
        
        System.out.println("========== TEST COMPLETE ==========");
    }
    
    private static String findNodeIpByDpid(long dpid, List<Node> nodes) {
        System.out.println("[DPID_LOOKUP] Searching for DPID: " + dpid + " (0x" + Long.toHexString(dpid) + ")");
        
        int switchCount = 0;
        for (Node node : nodes) {
            if (node.dpid != 0) {
                switchCount++;
                if (node.dpid == dpid) {
                    System.out.println("[DPID_LOOKUP] ✅ FOUND! Node: " + node.name + 
                                     " (dpid=" + node.dpid + ", ip=" + node.ip + ")");
                    if (node.ip != null && !node.ip.isEmpty()) {
                        return node.ip;
                    }
                    if (node.ips != null && !node.ips.isEmpty()) {
                        return node.ips.get(0);
                    }
                }
            }
        }
        
        System.err.println("[DPID_LOOKUP] ❌ NOT FOUND! DPID " + dpid + " not in topology");
        System.err.println("[DPID_LOOKUP] Searched " + switchCount + " switches in topology");
        
        // Show first few switches for debugging
        System.err.println("[DPID_LOOKUP] Sample switches in topology:");
        int count = 0;
        for (Node node : nodes) {
            if (node.dpid != 0 && count < 5) {
                System.err.println("[DPID_LOOKUP]   - " + node.name + ": dpid=" + node.dpid + 
                                 " (0x" + Long.toHexString(node.dpid) + ")");
                count++;
            }
        }
        
        return null;
    }
    
    private static String convertIpToString(long ip) {
        return String.format("%d.%d.%d.%d",
            (ip >> 24) & 0xFF,
            (ip >> 16) & 0xFF,
            (ip >> 8) & 0xFF,
            ip & 0xFF);
    }
}

