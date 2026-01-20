package org.example.demo2;

import com.google.gson.Gson;
import java.io.FileReader;

/**
 * Simple test to verify Gson parsing of flow_data.txt
 */
public class GsonParseTest {
    public static void main(String[] args) {
        try {
            Gson gson = new Gson();
            DetectedFlowData[] flows = gson.fromJson(new FileReader("flow_data.txt"), DetectedFlowData[].class);
            
            System.out.println("Parsed " + flows.length + " flows");
            
            for (int i = 0; i < flows.length; i++) {
                DetectedFlowData f = flows[i];
                System.out.println("\n=== Flow " + (i+1) + " ===");
                System.out.println("src_ip: " + f.src_ip);
                System.out.println("dst_ip: " + f.dst_ip);
                System.out.println("src_port: " + f.src_port);
                System.out.println("dst_port: " + f.dst_port);
                System.out.println("path: " + (f.path == null ? "null" : "size=" + f.path.size()));
                
                if (f.path != null && !f.path.isEmpty()) {
                    System.out.println("Path nodes:");
                    for (int j = 0; j < f.path.size(); j++) {
                        DetectedFlowData.PathNode pn = f.path.get(j);
                        System.out.println("  [" + j + "] node=" + pn.node + ", interface=" + pn.interface_id);
                    }
                } else {
                    System.out.println("⚠️  Path is empty or null!");
                }
            }
            
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}






