package org.example.demo2;

import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class NDTApiClient {
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;

    public NDTApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.objectMapper = new ObjectMapper();

        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        RequestConfig config = RequestConfig.custom()
            .setConnectTimeout(5000)
            .setSocketTimeout(10000)
            .build();
        this.httpClient = HttpClients.custom().setDefaultRequestConfig(config).build();
    }

    public GraphData getGraphData() {
        try {
            HttpGet request = new HttpGet(baseUrl + "/ndt/get_graph_data");
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    String result = EntityUtils.toString(entity);
                    return objectMapper.readValue(result, GraphData.class);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("[API] get_graph_data error: " + e.getMessage());
        }
        return null;
    }

    public DetectedFlowData[] getDetectedFlowData() {
        try {
            HttpGet request = new HttpGet(baseUrl + "/ndt/get_detected_flow_data");
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    String result = EntityUtils.toString(entity);
                    return objectMapper.readValue(result, DetectedFlowData[].class);
                }
            }
        } catch (Exception e) {
            e.printStackTrace(); // Show detailed exception
            System.err.println("[API] get_detected_flow_data error: " + e.getMessage());
        }
        return null;
    }
    

    public java.util.Map<String, Integer> getCpuUtilization() {
        try {
            HttpGet request = new HttpGet(baseUrl + "/ndt/get_cpu_utilization");
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    String result = EntityUtils.toString(entity);
                    return objectMapper.readValue(result, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Integer>>() {});
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("[API] get_cpu_utilization error: " + e.getMessage());
        }
        return null;
    }
    

    public java.util.Map<String, Integer> getMemoryUtilization() {
        try {
            HttpGet request = new HttpGet(baseUrl + "/ndt/get_memory_utilization");
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    String result = EntityUtils.toString(entity);
                    return objectMapper.readValue(result, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Integer>>() {});
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("[API] get_memory_utilization error: " + e.getMessage());
        }
        return null;
    }
    

    public void close() {
        try {
            if (httpClient != null) {
                httpClient.close();
            }
        } catch (Exception e) {
            System.err.println("Error closing HTTP client: " + e.getMessage());
        }
    }
} 