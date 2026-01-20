package org.example.demo2;

import com.google.gson.Gson;
import java.io.FileReader;
import java.util.List;
import java.util.ArrayList;

/**
 * 完整检查flow方向性问题
 */
public class FlowDirectionTest {
    
    public static void main(String[] args) {
        System.out.println("========== FLOW DIRECTION ANALYSIS ==========\n");
        
        try {
            Gson gson = new Gson();
            GraphData graphData = gson.fromJson(new FileReader("message-3.txt"), GraphData.class);
            DetectedFlowData[] flowData = gson.fromJson(new FileReader("flow_data.txt"), DetectedFlowData[].class);
            
            List<Node> nodes = convertNodes(graphData.nodes);
            
            System.out.println("检查项目：");
            System.out.println("1. Flow的src_ip和dst_ip方向");
            System.out.println("2. Path数组的节点顺序");
            System.out.println("3. Path起点是否匹配src_ip");
            System.out.println("4. Path终点是否匹配dst_ip");
            System.out.println("5. 中间节点的连续性\n");
            System.out.println("=" .repeat(80) + "\n");
            
            for (int i = 0; i < flowData.length; i++) {
                DetectedFlowData f = flowData[i];
                System.out.println("====== FLOW #" + (i+1) + " ======");
                
                String srcIpStr = convertIpToString(f.src_ip);
                String dstIpStr = convertIpToString(f.dst_ip);
                
                System.out.println("📌 Flow定义:");
                System.out.println("   SRC: " + srcIpStr + ":" + f.src_port);
                System.out.println("   DST: " + dstIpStr + ":" + f.dst_port);
                System.out.println("   方向: " + srcIpStr + " → " + dstIpStr);
                
                if (f.path == null || f.path.isEmpty()) {
                    System.err.println("   ❌ 错误: Path为空！");
                    continue;
                }
                
                System.out.println("\n📍 Path分析 (共" + f.path.size() + "个节点):");
                
                // 解析path中所有节点
                List<String> pathIps = new ArrayList<>();
                List<String> pathNames = new ArrayList<>();
                
                for (int j = 0; j < f.path.size(); j++) {
                    DetectedFlowData.PathNode pn = f.path.get(j);
                    String nodeIp;
                    String nodeName;
                    
                    if (pn.node > 0xFFFFFFFFL) {
                        // DPID (交换机)
                        Node node = findNodeByDpid(pn.node, nodes);
                        if (node != null) {
                            nodeIp = node.ip;
                            nodeName = node.name;
                        } else {
                            nodeIp = "UNKNOWN_DPID";
                            nodeName = "???";
                        }
                    } else {
                        // IP (主机)
                        nodeIp = convertIpToString(pn.node);
                        Node node = findNodeByIp(nodeIp, nodes);
                        nodeName = (node != null) ? node.name : "???";
                    }
                    
                    pathIps.add(nodeIp);
                    pathNames.add(nodeName);
                    
                    String nodeType = (pn.node > 0xFFFFFFFFL) ? "Switch" : "Host";
                    System.out.println("   [" + j + "] " + nodeName + " (" + nodeIp + ") - " + nodeType + 
                                     " [interface=" + pn.interface_id + "]");
                }
                
                System.out.println("\n🔍 方向性验证:");
                
                // 检查1: Path起点是否匹配src_ip
                boolean startMatches = pathIps.get(0).equals(srcIpStr);
                System.out.println("   1. Path起点 (" + pathIps.get(0) + ") " + 
                                 (startMatches ? "✅ 匹配" : "❌ 不匹配") + " src_ip (" + srcIpStr + ")");
                
                // 检查2: Path终点是否匹配dst_ip
                boolean endMatches = pathIps.get(pathIps.size()-1).equals(dstIpStr);
                System.out.println("   2. Path终点 (" + pathIps.get(pathIps.size()-1) + ") " + 
                                 (endMatches ? "✅ 匹配" : "❌ 不匹配") + " dst_ip (" + dstIpStr + ")");
                
                // 检查3: 是否反向 (起点=dst, 终点=src)
                boolean reversed = pathIps.get(0).equals(dstIpStr) && pathIps.get(pathIps.size()-1).equals(srcIpStr);
                if (reversed) {
                    System.err.println("   3. ⚠️  警告: Path方向与Flow定义相反！");
                    System.err.println("      Path是: " + dstIpStr + " → " + srcIpStr);
                    System.err.println("      Flow定义: " + srcIpStr + " → " + dstIpStr);
                    System.err.println("      动画会往反方向移动！");
                } else if (startMatches && endMatches) {
                    System.out.println("   3. ✅ Path方向正确");
                } else {
                    System.err.println("   3. ❌ Path方向错误: 起点和终点都不匹配！");
                }
                
                // 检查4: 路径可视化
                System.out.println("\n📊 完整路径:");
                System.out.print("   ");
                for (int j = 0; j < pathNames.size(); j++) {
                    System.out.print(pathNames.get(j));
                    if (j < pathNames.size() - 1) {
                        System.out.print(" → ");
                    }
                }
                System.out.println();
                
                // 检查5: 接口连续性（简单检查）
                System.out.println("\n🔌 接口序列:");
                System.out.print("   ");
                for (int j = 0; j < f.path.size(); j++) {
                    System.out.print(f.path.get(j).interface_id);
                    if (j < f.path.size() - 1) {
                        System.out.print(" → ");
                    }
                }
                System.out.println();
                
                // 总结
                if (startMatches && endMatches) {
                    System.out.println("\n✅ 此Flow方向正确，动画应该正向移动");
                } else if (reversed) {
                    System.err.println("\n❌ 此Flow方向反向，动画会反向移动！需要修复！");
                } else {
                    System.err.println("\n❌ 此Flow方向异常，动画可能错误！");
                }
                
                System.out.println("\n" + "=".repeat(80) + "\n");
            }
            
            System.out.println("========== 检查完成 ==========");
            
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static List<Node> convertNodes(List<GraphData.Node> apiNodes) {
        List<Node> nodes = new ArrayList<>();
        for (GraphData.Node n : apiNodes) {
            String ip = "";
            if (n.ip != null && !n.ip.isEmpty()) {
                ip = convertIpToString(n.ip.get(0));
            }
            Node node = new Node(ip, n.device_name, 100, 100, String.valueOf(n.vertex_type), n.is_up, n.is_enabled);
            node.dpid = n.dpid;
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
    
    private static Node findNodeByDpid(long dpid, List<Node> nodes) {
        for (Node node : nodes) {
            if (node.dpid == dpid) {
                return node;
            }
        }
        return null;
    }
    
    private static Node findNodeByIp(String ip, List<Node> nodes) {
        for (Node node : nodes) {
            if (node.ip != null && node.ip.equals(ip)) {
                return node;
            }
            if (node.ips != null && node.ips.contains(ip)) {
                return node;
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






