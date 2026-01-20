package org.example.demo2;

import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;

public class ApiConnectionTest {
    
    @Test
    public void testApiConnection() {
        // 測試默認API URL
        String apiUrl = "http://localhost:8000";
        System.out.println("Testing API connection to: " + apiUrl);
        
        try {
            NDTApiClient apiClient = new NDTApiClient(apiUrl);
            
            // 測試getGraphData
            System.out.println("Testing getGraphData...");
            GraphData graphData = apiClient.getGraphData();
            if (graphData != null) {
                System.out.println("getGraphData: SUCCESS");
                System.out.println("  - Nodes: " + (graphData.nodes != null ? graphData.nodes.size() : "null"));
                System.out.println("  - Edges: " + (graphData.edges != null ? graphData.edges.size() : "null"));
            } else {
                System.out.println("getGraphData: FAILED (returned null)");
            }
            
            // 測試getDetectedFlowData
            System.out.println("Testing getDetectedFlowData...");
            DetectedFlowData[] detectedFlows = apiClient.getDetectedFlowData();
            if (detectedFlows != null) {
                System.out.println("getDetectedFlowData: SUCCESS");
                System.out.println("  - Flows: " + detectedFlows.length);
            } else {
                System.out.println("getDetectedFlowData: FAILED (returned null)");
            }
            
            // 測試getCpuUtilization
            System.out.println("Testing getCpuUtilization...");
            java.util.Map<String, Integer> cpuUtilization = apiClient.getCpuUtilization();
            if (cpuUtilization != null) {
                System.out.println("getCpuUtilization: SUCCESS");
                System.out.println("  - CPU data entries: " + cpuUtilization.size());
            } else {
                System.out.println("getCpuUtilization: FAILED (returned null)");
            }
            
            // 測試getMemoryUtilization
            System.out.println("Testing getMemoryUtilization...");
            java.util.Map<String, Integer> memoryUtilization = apiClient.getMemoryUtilization();
            if (memoryUtilization != null) {
                System.out.println("getMemoryUtilization: SUCCESS");
                System.out.println("  - Memory data entries: " + memoryUtilization.size());
            } else {
                System.out.println("getMemoryUtilization: FAILED (returned null)");
            }
            
            // 關閉客戶端
            apiClient.close();
            
        } catch (Exception e) {
            System.err.println("API connection test failed with exception: " + e.getMessage());
            e.printStackTrace();
            fail("API connection test failed: " + e.getMessage());
        }
    }
}
