package org.example.demo2;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class NetworkTopologyApp extends Application {
    private static final String SETTINGS_FILE = "settings.json";
    private static final String NODE_POSITIONS_REALTIME = "node_positions.json";
    private static final String NODE_POSITIONS_PLAYBACK = "node_positions_playback.json";
    
    // New member variables to manage API client and executor
    private NDTApiClient apiClient;
    private ScheduledExecutorService executor;
    private volatile int lastNodeCount = 0; // Record the previous node count (volatile for thread safety)
    private volatile boolean isPlaybackMode = false; // Flag to control API updates
    
    // Playback panel
    private PlaybackPanel playbackPanel;
    private TopologyCanvas topologyCanvas;
    private VBox mainContent;
    private BorderPane root;
    private SideBar sideBar;
    private StackPane centerPane; // Store centerPane reference for dark mode updates
    
    // Loading overlay components
    private VBox loadingOverlay;
    private ProgressIndicator loadingIndicator;
    private Label loadingLabel;
    private ProgressBar progressBar;


    @Override
    public void start(Stage primaryStage) {
        List<Node> nodes = new ArrayList<>();
        List<Link> links = new ArrayList<>();
        List<Flow> flows = new ArrayList<>();

        // Load settings if file exists
        double savedFlowSpeed = loadSettings();

        // Initialize empty nodes, links, flows, will be filled by API later

        // Create UI components
        this.topologyCanvas = new TopologyCanvas(nodes, links, flows);
        
        // ===== flowBallPane related code =====
        Pane flowBallPane = new Pane();
        flowBallPane.setMouseTransparent(false);
        flowBallPane.setPickOnBounds(false);
        flowBallPane.setVisible(false); // Hidden by default

        int startX = 200;
        int startY = 270;
        int spacing = 40;
        
        // Store flow balls and corresponding flows for size updates
        List<Circle> flowBalls = new ArrayList<>();
        List<Flow> flowList = new ArrayList<>(flows);
        
        for (int i = 0; i < flows.size(); i++) {
            Flow flow = flows.get(i);
            
            Color flowColor = topologyCanvas.getColorForFlow(flows.get(i));
            Circle ball = new Circle(13, flowColor);
            ball.setStroke(Color.BLACK);
            ball.setStrokeWidth(1.7);
            ball.setLayoutX(startX);
            ball.setLayoutY(startY + i * spacing);
             
            String tip = flow.srcIp + " → " + flow.dstIp;
            Tooltip tooltip = new Tooltip(tip);
            Tooltip.install(ball, tooltip);
            
            ball.setOnMouseClicked(e -> {
                topologyCanvas.flickerLinksForFlow(flow);
                // Update all ball sizes
                updateFlowBallSizes(flowBalls, flowList, topologyCanvas);
            });
            
            flowBalls.add(ball);
            flowBallPane.getChildren().add(ball);
        }
        
        this.sideBar = new SideBar(topologyCanvas, flows, this);
        
        // Set SideBar reference in TopologyCanvas (for Top-K button updates)
        topologyCanvas.setSideBar(sideBar);
        
        
        sideBar.setPrimaryStage(primaryStage);
        
        // Create PlaybackPanel
        playbackPanel = new PlaybackPanel(topologyCanvas, this);
        playbackPanel.hide(); // Initially hidden
        
        // Set PlaybackPanel reference in SideBar
        sideBar.setPlaybackPanel(playbackPanel);
        
        // Create InfoDialog and set it to TopologyCanvas
        InfoDialog infoDialog = new InfoDialog(topologyCanvas, flows);
        topologyCanvas.setInfoDialog(infoDialog);

        // Auto-display flow animation
        topologyCanvas.setShowFlows(!flows.isEmpty());
        
        // Apply saved speed settings
        if (savedFlowSpeed > 0) {
            topologyCanvas.setFlowMoveSpeed(savedFlowSpeed);
        }

        // Create main layout
        this.root = new BorderPane();
        root.setPadding(new Insets(0)); // No margins
        
        // Create StackPane to overlay image and canvas
        this.centerPane = new StackPane();
        // Initial background color will be set based on dark mode state
        // Check if topologyCanvas already has dark mode set
        boolean isDarkMode = topologyCanvas != null && topologyCanvas.darkMode;
        centerPane.setStyle(isDarkMode ? "-fx-background-color: #23272e; -fx-border-width: 0; -fx-padding: 0;" : "-fx-background-color: white; -fx-border-width: 0; -fx-padding: 0;");
        
        // Add TopologyCanvas
        centerPane.getChildren().add(topologyCanvas);
        
        // Create loading overlay for TopologyCanvas
        this.loadingOverlay = new VBox(40);
        this.loadingOverlay.setAlignment(Pos.CENTER);
        this.loadingOverlay.setStyle("-fx-background-color: rgba(255, 255, 255, 0.95); -fx-background-radius: 20; -fx-border-color: #3498db; -fx-border-width: 2; -fx-border-radius: 20; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 25, 0, 0, 8);");
        this.loadingOverlay.setPadding(new Insets(80, 120, 80, 120));
        this.loadingOverlay.setVisible(false);
        
        // Create loading components
        this.loadingIndicator = new ProgressIndicator();
        this.loadingIndicator.setPrefSize(100, 100);
        this.loadingIndicator.setStyle("-fx-progress-color: #3498db; -fx-background-color: transparent;");
        
        this.loadingLabel = new Label("Loading...");
        this.loadingLabel.setStyle("-fx-font-size: 24px; -fx-text-fill: #2c3e50; -fx-font-weight: 600; -fx-font-family: 'Segoe UI', Arial, sans-serif;");
        
        this.progressBar = new ProgressBar(0.0);
        this.progressBar.setPrefWidth(400);
        this.progressBar.setPrefHeight(20);
        this.progressBar.setStyle("-fx-accent: #3498db; -fx-background-color: #ecf0f1; -fx-background-radius: 10; -fx-border-radius: 10; -fx-effect: innershadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 1); -fx-border-color: #bdc3c7; -fx-border-width: 1;");
        
        this.loadingOverlay.getChildren().addAll(loadingIndicator, loadingLabel, progressBar);
        
        // Add loading overlay to centerPane
        centerPane.getChildren().add(loadingOverlay);

        // Create VBox to contain PlaybackPanel on top and centerPane below
        this.mainContent = new VBox(0); // No spacing between elements
        mainContent.setSpacing(0); // Ensure no spacing
        mainContent.getChildren().add(centerPane); // Initially only add centerPane
        VBox.setVgrow(centerPane, Priority.ALWAYS);

        root.setCenter(mainContent);
        root.setLeft(sideBar);
        BorderPane.setMargin(mainContent, new Insets(0)); // No margins
        BorderPane.setMargin(sideBar, new Insets(0)); // No margins for sidebar
        topologyCanvas.widthProperty().bind(root.widthProperty().subtract(sideBar.getWidth()));
        
        // Initially bind height without PlaybackPanel
        topologyCanvas.heightProperty().bind(root.heightProperty());
        
        // Set callback to update centerPane background color when dark mode changes
        topologyCanvas.setDarkModeCallback(dark -> {
            if (centerPane != null) {
                centerPane.setStyle(dark ? "-fx-background-color: #23272e; -fx-border-width: 0; -fx-padding: 0;" : "-fx-background-color: white; -fx-border-width: 0; -fx-padding: 0;");
            }
        });
        
        // Center the loading overlay
        StackPane.setAlignment(loadingOverlay, Pos.CENTER);

        // Create AnchorPane as scene root
        AnchorPane anchorPane = new AnchorPane();
        anchorPane.getChildren().addAll(root, flowBallPane);

        // Let root (BorderPane) automatically fill the entire window
        AnchorPane.setTopAnchor(root, 0.0);
        AnchorPane.setBottomAnchor(root, 0.0);
        AnchorPane.setLeftAnchor(root, 0.0);
        AnchorPane.setRightAnchor(root, 0.0);

        // Create scene
        Scene scene = new Scene(anchorPane, 1400, 900);
        primaryStage.setTitle("Network Traffic Visualizer");
        primaryStage.setScene(scene);
        primaryStage.setMaximized(true);

        // Apply circular layout after scene creation (if no saved positions)
        // Removed: if (savedPositions.isEmpty()) {
        // Wait for scene layout to complete before applying layout
        Platform.runLater(() -> {
            // applyCircularLayout(nodes, savedPositions, canvasWidth, canvasHeight); // Removed calls related to node_positions.json
            topologyCanvas.draw(); // Redraw
        });

        // Save positions when closing
        primaryStage.setOnCloseRequest((WindowEvent event) -> {
            // saveNodePositions(nodes); // Removed calls related to node_positions.json
            saveSettings(topologyCanvas.getFlowMoveSpeed());
            
            
            if (sideBar != null) {
                sideBar.closeAllDialogs();
            }
            
            // Stop API update tasks
            if (executor != null && !executor.isShutdown()) {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            
            // Close HTTP client
            if (apiClient != null) {
                try {
                    apiClient.close();
                } catch (Exception e) {
                    System.err.println("Error closing API client: " + e.getMessage());
                }
            }
            
            Platform.exit();
        });

        primaryStage.show();

        // ====== API Auto Update ======
        // Read API URL from environment variable, use default if not set
        String apiUrl = System.getenv("NDT_API_URL");
        if (apiUrl == null || apiUrl.trim().isEmpty()) {
            apiUrl = "http://localhost:8000"; // Default value
            System.out.println("[INFO] NDT_API_URL environment variable not set, using default: " + apiUrl);
        } else {
            System.out.println("[INFO] Using NDT_API_URL from environment: " + apiUrl);
        }
        this.apiClient = new NDTApiClient(apiUrl);
        
        int threadPoolSize = Math.max(4, Runtime.getRuntime().availableProcessors() / 2);
        this.executor = Executors.newScheduledThreadPool(threadPoolSize);
        executor.scheduleAtFixedRate(() -> {
            // Skip API updates if in playback mode
            if (isPlaybackMode) {
                return;
            }
            
            
            java.util.concurrent.CompletableFuture<GraphData> graphDataFuture = 
                CompletableFuture.supplyAsync(() -> apiClient.getGraphData(), executor);
            java.util.concurrent.CompletableFuture<DetectedFlowData[]> detectedFlowsFuture = 
                CompletableFuture.supplyAsync(() -> apiClient.getDetectedFlowData(), executor);
            java.util.concurrent.CompletableFuture<Map<String, Integer>> cpuUtilizationFuture = 
                CompletableFuture.supplyAsync(() -> apiClient.getCpuUtilization(), executor);
            java.util.concurrent.CompletableFuture<Map<String, Integer>> memoryUtilizationFuture = 
                CompletableFuture.supplyAsync(() -> apiClient.getMemoryUtilization(), executor);
            
            
            GraphData graphData = graphDataFuture.join();
            DetectedFlowData[] detectedFlows = detectedFlowsFuture.join();
            Map<String, Integer> cpuUtilization = cpuUtilizationFuture.join();
            Map<String, Integer> memoryUtilization = memoryUtilizationFuture.join();
            
            // Add debug output
            System.out.println("[DEBUG] API Update - graphData: " + (graphData != null ? "OK" : "NULL") + 
                             ", detectedFlows: " + (detectedFlows != null ? "OK" : "NULL") + 
                             ", cpuUtilization: " + (cpuUtilization != null ? "OK" : "NULL") + 
                             ", memoryUtilization: " + (memoryUtilization != null ? "OK" : "NULL"));
            
            if (graphData != null && detectedFlows != null) {
                // Check if node count has changed
                int currentNodeCount = graphData.nodes.size();
                boolean topologyChanged = (currentNodeCount != lastNodeCount);
                
                if (topologyChanged) {
                    System.out.println("[DEBUG] Topology changed! Node count: " + lastNodeCount + " -> " + currentNodeCount);
                    lastNodeCount = currentNodeCount;
                }
                
                List<Node> apiNodes = convertGraphNodes(graphData.nodes, graphData.edges, topologyCanvas.getWidth(), topologyCanvas.getHeight());
                // First convert detected flows to get complete path information (pass apiNodes for DPID lookup)
                List<Flow> apiFlows = convertDetectedFlows(detectedFlows, apiNodes);
                // Then convert links with detected flows for complete path info in flow_set
                List<Link> apiLinks = convertGraphLinks(graphData.edges, apiNodes, apiFlows);
                
                
                
                
                assignFlowsToLinks(apiFlows, apiLinks, apiNodes);
                
                // New: Update node utilization information
                if (cpuUtilization != null || memoryUtilization != null) {
                    System.out.println("[DEBUG] CPU utilization data: " + (cpuUtilization != null ? cpuUtilization.size() + " entries" : "null"));
                    System.out.println("[DEBUG] Memory utilization data: " + (memoryUtilization != null ? memoryUtilization.size() + " entries" : "null"));
                    
                    // Display some utilization data samples
                    if (cpuUtilization != null && !cpuUtilization.isEmpty()) {
                        System.out.println("[DEBUG] Sample CPU data: " + cpuUtilization.entrySet().stream().limit(3).toList());
                    }
                    if (memoryUtilization != null && !memoryUtilization.isEmpty()) {
                        System.out.println("[DEBUG] Sample Memory data: " + memoryUtilization.entrySet().stream().limit(3).toList());
                    }
                    
                } else {
                    System.out.println("[DEBUG] No utilization data available");
                }
                
                Platform.runLater(() -> {
                    
                    if (isPlaybackMode) {
                        System.out.println("[DEBUG] Skipping API update - switched to playback mode");
                        return;
                    }
                    
                    System.out.println("[DEBUG] updateTopology, nodes=" + apiNodes.size() + ", links=" + apiLinks.size() + ", flows=" + apiFlows.size());
                    topologyCanvas.updateTopology(apiNodes, apiLinks, apiFlows);
                    
                    // Update SideBar filter data
                    sideBar.updateData(apiFlows, apiLinks, apiNodes);
                    
                    // After topology update, update node utilization data
                    if (cpuUtilization != null || memoryUtilization != null) {
                        List<Node> guiNodes = topologyCanvas.getNodes();
                        if (guiNodes != null && !guiNodes.isEmpty()) {
                            updateNodeUtilization(guiNodes, cpuUtilization, memoryUtilization);
                            System.out.println("[DEBUG] Updated utilization data for " + guiNodes.size() + " GUI nodes");
                        }
                    }
                });
            } else {
                // Add debug output, handle case when API data is null
                System.out.println("[DEBUG] API data is null, skipping topology update");
                if (graphData == null) {
                    System.out.println("[DEBUG] graphData is null - API connection issue?");
                }
                if (detectedFlows == null) {
                    System.out.println("[DEBUG] detectedFlows is null - API connection issue?");
                }
            }
        }, 0, 1, TimeUnit.SECONDS);

        // Force to flow only mode
        topologyCanvas.setShowFlows(true);
        topologyCanvas.setShowLinks(false);
    }


    private double loadSettings() {
        File file = new File(SETTINGS_FILE);
        if (!file.exists()) {
            return 0.0;
        }
           
        try (FileReader reader = new FileReader(file)) {
            Gson gson = new Gson();
            JsonObject obj = gson.fromJson(reader, JsonObject.class);
            return obj.get("flow_move_speed").getAsDouble();
        } catch (IOException e) {
            System.err.println("Error loading settings: " + e.getMessage());
            return 0.0;
        }
    }

    private void saveSettings(double flowMoveSpeed) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(SETTINGS_FILE))) {
            writer.println("{");
            writer.println("  \"flow_move_speed\": " + flowMoveSpeed);
            writer.println("}");
        } catch (IOException e) {
            System.err.println("Error saving settings: " + e.getMessage());
        }
    }
    
    // Read node position file and auto-scale center
    private Map<String, int[]> loadNodePositions() {
        
        String positionFile = isPlaybackMode ? NODE_POSITIONS_PLAYBACK : NODE_POSITIONS_REALTIME;
        return loadNodePositions(positionFile);
    }
    
    private Map<String, int[]> loadNodePositions(String filename) {
        Map<String, int[]> posMap = new HashMap<>();
        List<int[]> allPositions = new ArrayList<>();
        
        try (FileReader reader = new FileReader(filename)) {
            Gson gson = new Gson();
            JsonObject obj = gson.fromJson(reader, JsonObject.class);
            if (obj.has("nodes")) {
                JsonArray arr = obj.getAsJsonArray("nodes");
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject n = arr.get(i).getAsJsonObject();
                    String ip = n.get("ip").getAsString();
                    int x = n.get("x").getAsInt();
                    int y = n.get("y").getAsInt();
                    allPositions.add(new int[]{x, y});
                    
                    // Convert little-endian integer IP to standard IP format
                    try {
                        long littleEndianIp = Long.parseLong(ip);
                        String standardIp = convertLittleEndianToIp(littleEndianIp);
                        posMap.put(standardIp, new int[]{x, y});
                        System.out.println("[DEBUG] Converted IP " + ip + " to " + standardIp + " for position loading");
                    } catch (NumberFormatException e) {
                        // If IP is not numeric format, use original value directly
                        posMap.put(ip, new int[]{x, y});
                        System.out.println("[DEBUG] Using original IP " + ip + " for position loading");
                    }
                }
            }
        } catch (Exception e) {
            // Ignore when file doesn't exist or format error
            System.out.println("[DEBUG] Could not load node_positions.json: " + e.getMessage());
            return posMap;
        }
        
                    // Calculate boundaries
        if (!allPositions.isEmpty()) {
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            
            for (int[] pos : allPositions) {
                minX = Math.min(minX, pos[0]);
                maxX = Math.max(maxX, pos[0]);
                minY = Math.min(minY, pos[1]);
                maxY = Math.max(maxY, pos[1]);
            }
            
            // Calculate scale and offset
            double canvasWidth = 1200; // Default canvas width
            double canvasHeight = 800; // Default canvas height
            double margin = 100; // Margin
            
            double contentWidth = maxX - minX;
            double contentHeight = maxY - minY;
            
            double scaleX = (canvasWidth - 2 * margin) / contentWidth;
            double scaleY = (canvasHeight - 2 * margin) / contentHeight;
            double scale = Math.min(scaleX, scaleY);
            
            // Calculate center offset
            double offsetX = (canvasWidth - contentWidth * scale) / 2 - minX * scale;
            double offsetY = (canvasHeight - contentHeight * scale) / 2 - minY * scale;
            

            
            // Apply scale and offset to all positions
            for (String ip : posMap.keySet()) {
                int[] originalPos = posMap.get(ip);
                int newX = (int) (originalPos[0] * scale + offsetX);
                int newY = (int) (originalPos[1] * scale + offsetY);
                posMap.put(ip, new int[]{newX, newY});
            }
        }
        
        return posMap;
    }
    
    // New: Method to update flow ball sizes
    private void updateFlowBallSizes(List<Circle> flowBalls, List<Flow> flowList, TopologyCanvas topologyCanvas) {
        Flow flickeredFlow = topologyCanvas.getFlickeredFlow();
        for (int i = 0; i < flowBalls.size(); i++) {
            Circle ball = flowBalls.get(i);
            Flow flow = flowList.get(i);
            
            if (flickeredFlow != null && flickeredFlow.equals(flow)) {
                // Flickered flow ball becomes larger
                ball.setRadius(18); // From 13 to 18
                ball.setStrokeWidth(2.5);

            } else {
                // Restore normal
                ball.setRadius(13);
                ball.setStrokeWidth(1.7);
            }
        }
    }

    // New: Convert integer IP to standard IP address format (Big-Endian / Network Byte Order)
    private String convertLittleEndianToIp(long intIp) {
        // Handle values that exceed 32-bit range by extracting lower 32 bits
        // This happens in testbed data where additional info may be encoded in upper bits
        if (intIp < 0) {
            System.err.println("[WARNING] Negative IP value: " + intIp + ". Using 0.0.0.0 as fallback.");
            return "0.0.0.0";
        }
        
        // Check if this might be a MAC address (48 bits) or other non-IP identifier
        if (intIp > 0xFFFFFFFFL && intIp <= 0xFFFFFFFFFFFFL) {
            // This is likely a MAC address (48 bits)
            String macAddress = String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                (intIp >> 40) & 0xFF, (intIp >> 32) & 0xFF,
                (intIp >> 24) & 0xFF, (intIp >> 16) & 0xFF,
                (intIp >> 8) & 0xFF, intIp & 0xFF);
            System.out.println("[INFO] Value appears to be a MAC address: " + intIp + " (0x" + 
                             Long.toHexString(intIp) + ") -> " + macAddress);
            
            // For display purposes, convert MAC to a pseudo-IP format or use a special marker
            // Extract lower 32 bits and convert as big-endian
            long low32 = intIp & 0xFFFFFFFFL;
            int b1 = (int) ((low32 >> 24) & 0xFF);
            int b2 = (int) ((low32 >> 16) & 0xFF);
            int b3 = (int) ((low32 >> 8) & 0xFF);
            int b4 = (int) (low32 & 0xFF);
            String pseudoIp = String.format("%d.%d.%d.%d", b1, b2, b3, b4);
            System.out.println("[INFO] Using lower 32 bits as pseudo-IP: " + pseudoIp + 
                             " (Note: This may not be a valid IP)");
            return pseudoIp;
        } else if (intIp > 0xFFFFFFFFFFFFL) {
            // Value exceeds 48 bits, extract lower 32 bits
            long extraInfo = intIp >> 32;
            System.out.println("[INFO] IP value exceeds 48-bit: " + intIp + " (0x" + Long.toHexString(intIp) + 
                             "). Extracting lower 32 bits. Extra info: " + extraInfo + " (0x" + Long.toHexString(extraInfo) + ")");
            intIp = intIp & 0xFFFFFFFFL;  // Extract lower 32 bits
        }
        
        // Convert integer IP to standard IP address format (Big-Endian / Network Byte Order)
        // Example: 1895934144 (0x7101A8C0) -> 113.1.168.192
        int b1 = (int) ((intIp >> 24) & 0xFF);   // First byte (most significant)
        int b2 = (int) ((intIp >> 16) & 0xFF);   // Second byte
        int b3 = (int) ((intIp >> 8) & 0xFF);    // Third byte
        int b4 = (int) (intIp & 0xFF);           // Fourth byte (least significant)
        
        return String.format("%d.%d.%d.%d", b1, b2, b3, b4);
    }
    
    /**
     * Parse time string from API format "2025-11-11 10:30:35" to milliseconds
     * Returns milliseconds from start of day (HH:MM:SS only)
     */
    private int parseTimeStringToMs(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return 0;
        }
        
        try {
            // Format: "2025-11-11 10:30:35"
            // Extract time part "10:30:35"
            String[] parts = timeStr.split(" ");
            if (parts.length < 2) {
                System.out.println("[WARN] Invalid time format: " + timeStr);
                return 0;
            }
            
            String timePart = parts[1]; // "10:30:35"
            String[] timeParts = timePart.split(":");
            if (timeParts.length != 3) {
                System.out.println("[WARN] Invalid time part format: " + timePart);
                return 0;
            }
            
            int hours = Integer.parseInt(timeParts[0]);
            int minutes = Integer.parseInt(timeParts[1]);
            int seconds = Integer.parseInt(timeParts[2]);
            
            // Convert to milliseconds
            int totalMs = (hours * 3600 + minutes * 60 + seconds) * 1000;
            
            return totalMs;
        } catch (Exception e) {
            System.out.println("[ERROR] Failed to parse time string: " + timeStr + ", error: " + e.getMessage());
            return 0;
        }
    }



    // New: Find corresponding utilization data based on standard IP
    private Integer findUtilizationByStandardIp(String standardIp, Map<String, Integer> utilizationMap) {
        return utilizationMap.get(standardIp);
    }

    // New: Update node utilization information
    private void updateNodeUtilization(List<Node> nodes, Map<String, Integer> cpuUtilization, Map<String, Integer> memoryUtilization) {
        System.out.println("[DEBUG] updateNodeUtilization called with " + nodes.size() + " nodes");
        int cpuMatches = 0;
        int memoryMatches = 0;
        
        for (Node node : nodes) {
            if (node.ip != null && !node.ip.isEmpty()) {
                // Node IPs are now in standard format, direct matching
                Integer cpu = findUtilizationByStandardIp(node.ip, cpuUtilization);
                Integer memory = findUtilizationByStandardIp(node.ip, memoryUtilization);
                
                // Update node utilization information
                if (cpu != null) {
                    node.setCpuUtilization(cpu);
                    cpuMatches++;
                    System.out.println("[DEBUG] Matched CPU utilization for " + node.ip + ": " + cpu + "%");
                }
                if (memory != null) {
                    node.setMemoryUtilization(memory);
                    memoryMatches++;
                    System.out.println("[DEBUG] Matched Memory utilization for " + node.ip + ": " + memory + "%");
                }
                
                // Debug output: Show matching status of first few nodes
                if (cpuMatches + memoryMatches <= 6) {
                    System.out.println("[DEBUG] Node " + node.ip + " - CPU: " + cpu + ", Memory: " + memory);
                }
            }
        }
        
        System.out.println("[DEBUG] Utilization update complete - CPU matches: " + cpuMatches + ", Memory matches: " + memoryMatches);
    }

    // Convert API GraphData.Node to GUI Node, with automatic Fat-Tree layout
    private List<Node> convertGraphNodes(List<GraphData.Node> apiNodes, List<GraphData.Edge> apiEdges, double canvasWidth, double canvasHeight) {
        // Calculate actual total node count (unified approach: one node per device)
        int actualNodeCount = apiNodes.size();
        
        // Check if layout needs to be reinitialized
        boolean shouldReinitialize = (actualNodeCount != lastNodeCount);
        List<Node> nodes = new ArrayList<>();
        
        // Classify nodes
        List<Node> coreNodes = new ArrayList<>();
        List<Node> aggregationNodes = new ArrayList<>();
        List<Node> edgeNodes = new ArrayList<>();
        List<Node> hostNodes = new ArrayList<>();
        
        for (GraphData.Node n : apiNodes) {
            String baseName = n.device_name;
            String type = String.valueOf(n.vertex_type);
            boolean is_up = n.is_up;
            boolean is_enabled = n.is_enabled;
            
            if (n.vertex_type == 1) {
                // Unified approach: Create ONE node per device, use device_name
                // All IPs will map to this single node
                if (n.ip != null && !n.ip.isEmpty()) {
                    System.out.println("[DEBUG] Processing host node: " + baseName + " with " + n.ip.size() + " IP(s) - will create unified node");
                    
                    // Find first valid IP as primary IP
                    String primaryIp = null;
                    List<String> allValidIps = new ArrayList<>();
                    
                    for (int i = 0; i < n.ip.size(); i++) {
                        Long ipValue = n.ip.get(i);
                        if (ipValue != null) {
                            System.out.println("[DEBUG] Raw IP value[" + i + "]: " + ipValue + " (0x" + Long.toHexString(ipValue) + ")");
                            String ip = convertLittleEndianToIp(ipValue);
                            
                            // Skip invalid IPs (those that returned 0.0.0.0 as fallback)
                            if (!"0.0.0.0".equals(ip)) {
                                allValidIps.add(ip);
                                if (primaryIp == null) {
                                    primaryIp = ip;  // Use first valid IP as primary
                                }
                                System.out.println("[DEBUG] Valid IP[" + i + "]: " + ip);
                            } else {
                                System.out.println("[DEBUG] Skipping invalid IP for " + baseName);
                            }
                        }
                    }
                    
                    // Create single unified node if we have at least one valid IP
                    if (primaryIp != null) {
                        String nodeName = baseName;  // Use device_name directly (e.g., h29)
                        int x = 100, y = 100; // Default, auto-layout later
                        
                        Node node = new Node(primaryIp, nodeName, x, y, type, is_up, is_enabled);
                        node.layer = "host";
                        
                        // Store additional API data
                        node.dpid = n.dpid;
                        node.mac = n.mac;
                        node.brandName = n.brand_name;
                        node.deviceLayer = n.device_layer;
                        node.originalDeviceName = baseName;
                        
                        // Store all IPs for this node (for later mapping)
                        node.ips = allValidIps;
                        
                        nodes.add(node);
                        hostNodes.add(node);
                        
                        System.out.println("[DEBUG] Created unified host node: " + nodeName + 
                            " with primary IP: " + primaryIp + 
                            " (total " + allValidIps.size() + " IPs: " + allValidIps + ")" +
                            ", DPID: " + n.dpid + ", Brand: " + n.brand_name + ", Layer: " + n.device_layer);
                    } else {
                        System.out.println("[DEBUG] No valid IPs found for " + baseName + ", skipping node creation");
                    }
                } else {
                    // If no IP, create a default node
                    String ip = "";
                    int x = 100, y = 100;
                    Node node = new Node(ip, baseName, x, y, type, is_up, is_enabled);
                    node.layer = "host";
                    
                    // Store additional API data
                    node.dpid = n.dpid;
                    node.mac = n.mac;
                    node.brandName = n.brand_name;
                    node.deviceLayer = n.device_layer;
                    node.originalDeviceName = baseName;
                    
                    nodes.add(node);
                    hostNodes.add(node);
                    System.out.println("[DEBUG] Created default host node: " + baseName + 
                        ", DPID: " + n.dpid + ", Brand: " + n.brand_name + ", Layer: " + n.device_layer);
                }
            } else if (n.vertex_type == 0) {

                String ip = "";
                if (n.ip != null && !n.ip.isEmpty()) {
                    Long ipValue = n.ip.getFirst();
                    if (ipValue != null) {
                        ip = convertLittleEndianToIp(ipValue);
                    }
                }
                int x = 100, y = 100;
                Node node = new Node(ip, baseName, x, y, type, is_up, is_enabled);
                
                // Store additional API data (not used for now)
                node.dpid = n.dpid;
                node.mac = n.mac;
                node.brandName = n.brand_name;
                node.deviceLayer = n.device_layer;
                
                nodes.add(node);
                
                switch (n.device_layer) {
                    case 0:
                        // device_layer = 0 is Core layer (highest layer)
                        node.layer = "core";
                        coreNodes.add(node);
                        System.out.println("[DEBUG] Classified " + baseName + " as Core Switch (layer=0), DPID: " + n.dpid + ", Brand: " + n.brand_name);
                        break;
                    case 1:
                        // device_layer = 1 is Aggregation layer (middle layer)
                        node.layer = "aggregation";
                        aggregationNodes.add(node);
                        System.out.println("[DEBUG] Classified " + baseName + " as Aggregation Switch (layer=1), DPID: " + n.dpid + ", Brand: " + n.brand_name);
                        break;
                    case 2:
                        // device_layer = 2 is Edge layer (lowest layer)
                        node.layer = "edge";
                        edgeNodes.add(node);
                        System.out.println("[DEBUG] Classified " + baseName + " as Edge Switch (layer=2), DPID: " + n.dpid + ", Brand: " + n.brand_name);
                        break;
                    default:
                        // Unknown layer, default to Edge
                        node.layer = "edge";
                        edgeNodes.add(node);
                        System.out.println("[DEBUG] Classified " + baseName + " as Edge Switch (unknown layer=" + n.device_layer + "), DPID: " + n.dpid + ", Brand: " + n.brand_name);
                        break;
                }
            }
        }
        
        // Prioritize node position file, use automatic layout if none exists
        Map<String, int[]> savedPositions = loadNodePositions();
        boolean useSavedPositions = !savedPositions.isEmpty();
        
        // Check if ALL nodes can match saved positions (complete match required)
        boolean allNodesMatched = false;
        if (useSavedPositions) {
            int matchedCount = 0;
            for (Node node : nodes) {
                if (savedPositions.containsKey(node.ip)) {
                    matchedCount++;
                }
            }
            allNodesMatched = (matchedCount == nodes.size());
            System.out.println("[DEBUG] Position matching: " + matchedCount + "/" + nodes.size() + 
                             " nodes matched (complete match: " + allNodesMatched + ")");
        }
        
        if (useSavedPositions && allNodesMatched) {
            System.out.println("[DEBUG] Using saved node positions from file (complete match)");
            // Use saved positions
            for (Node node : nodes) {
                int[] pos = savedPositions.get(node.ip);
                if (pos != null) {
                    node.x = pos[0];
                    node.y = pos[1];
                    System.out.println("[DEBUG] Applied saved position for " + node.ip + ": (" + node.x + ", " + node.y + ")");
                }
            }
        } else {
            if (useSavedPositions && !allNodesMatched) {
                System.out.println("[WARN] Incomplete match detected, switching to automatic layout");
            } else {
                System.out.println("[DEBUG] No saved positions found, using automatic layout");
            }
            
            // If topology changes, force reapply Fat-Tree layout
            if (shouldReinitialize) {
                System.out.println("[DEBUG] Reinitializing Fat-Tree layout due to topology change");
                // Clear all node positions, force re-layout
                for (Node node : nodes) {
                    node.x = 100;
                    node.y = 100;
                }
            }
            
            // Fat-Tree automatic layout
            applyFatTreeLayout(coreNodes, aggregationNodes, edgeNodes, hostNodes, canvasWidth, canvasHeight);
        }
        
        // Update lastNodeCount to actual node count
        lastNodeCount = nodes.size();
        
        return nodes;
    }

    // Fat-Tree topology automatic layout - fix right bias issue
    private void applyFatTreeLayout(List<Node> coreNodes, List<Node> aggregationNodes, 
                                   List<Node> edgeNodes, List<Node> hostNodes, 
                                   double canvasWidth, double canvasHeight) {
        int layerSpacing = 120;
        int startY = 80;
        final int GRID_SIZE = 15; 

        // Core - center layout
        if (!coreNodes.isEmpty()) {
            double totalWidth = canvasWidth * 0.8; // Use 80% of canvas width
            double spacing = totalWidth / (coreNodes.size() - 1);
            double startX = (canvasWidth - totalWidth) / 2; // Center start position
            for (int i = 0; i < coreNodes.size(); i++) {
                coreNodes.get(i).x = (int) (startX + i * spacing);
                coreNodes.get(i).y = startY;
                
                coreNodes.get(i).x = Math.round(coreNodes.get(i).x / (float)GRID_SIZE) * GRID_SIZE;
                coreNodes.get(i).y = Math.round(coreNodes.get(i).y / (float)GRID_SIZE) * GRID_SIZE;
            }
        }

        // Aggregation - center layout
        int aggY = startY + layerSpacing;
        if (!aggregationNodes.isEmpty()) {
            double totalWidth = canvasWidth * 0.8;
            double spacing = totalWidth / (aggregationNodes.size() - 1);
            double startX = (canvasWidth - totalWidth) / 2;
            for (int i = 0; i < aggregationNodes.size(); i++) {
                aggregationNodes.get(i).x = (int) (startX + i * spacing);
                aggregationNodes.get(i).y = aggY;
                
                aggregationNodes.get(i).x = Math.round(aggregationNodes.get(i).x / (float)GRID_SIZE) * GRID_SIZE;
                aggregationNodes.get(i).y = Math.round(aggregationNodes.get(i).y / (float)GRID_SIZE) * GRID_SIZE;
            }
        }

        // Edge - center layout
        int edgeY = aggY + layerSpacing;
        if (!edgeNodes.isEmpty()) {
            double totalWidth = canvasWidth * 0.8;
            double spacing = totalWidth / (edgeNodes.size() - 1);
            double startX = (canvasWidth - totalWidth) / 2;
            for (int i = 0; i < edgeNodes.size(); i++) {
                edgeNodes.get(i).x = (int) (startX + i * spacing);
                edgeNodes.get(i).y = edgeY;
                
                edgeNodes.get(i).x = Math.round(edgeNodes.get(i).x / (float)GRID_SIZE) * GRID_SIZE;
                edgeNodes.get(i).y = Math.round(edgeNodes.get(i).y / (float)GRID_SIZE) * GRID_SIZE;
            }
        }

        // Host - single row horizontal layout (all hosts in one line)
        int hostY = edgeY + layerSpacing;
        if (!hostNodes.isEmpty()) {
            
            
            double totalWidth = canvasWidth * 0.95; // Use 95% of canvas width (increased from 0.8)
            double minSpacing = 25; 
            double spacing;
            if (hostNodes.size() == 1) {
                spacing = 0; // Single node doesn't need spacing
            } else {
                double calculatedSpacing = totalWidth / (hostNodes.size() - 1);
                spacing = Math.max(calculatedSpacing, minSpacing); 
            }
            
            double actualTotalWidth = spacing * (hostNodes.size() - 1);
            double startX = (canvasWidth - actualTotalWidth) / 2; // Center start position
            
            for (int i = 0; i < hostNodes.size(); i++) {
                hostNodes.get(i).x = (int) (startX + i * spacing);
                hostNodes.get(i).y = hostY; 
                
                hostNodes.get(i).x = Math.round(hostNodes.get(i).x / (float)GRID_SIZE) * GRID_SIZE;
                hostNodes.get(i).y = Math.round(hostNodes.get(i).y / (float)GRID_SIZE) * GRID_SIZE;
            }
        }
    }
    // Convert API GraphData.Edge to GUI Link
    private List<Link> convertGraphLinks(List<GraphData.Edge> apiEdges, List<Node> nodes, List<Flow> detectedFlows) {
        List<Link> links = new ArrayList<>();
        
        // Create IP to node mapping (unified approach: all IPs map to same node)
        Map<String, Node> ipToNodeMap = new HashMap<>();
        for (Node node : nodes) {
            // Map primary IP
            if (node.ip != null && !node.ip.isEmpty()) {
                ipToNodeMap.put(node.ip, node);
            }
            // Map all secondary IPs to the same node
            if (node.ips != null && !node.ips.isEmpty()) {
                for (String ip : node.ips) {
                    ipToNodeMap.put(ip, node);
                    System.out.println("[DEBUG] Mapped IP " + ip + " to node " + node.name);
                }
            }
        }
        System.out.println("[DEBUG] Created IP to node mapping with " + ipToNodeMap.size() + " entries for " + nodes.size() + " nodes");
        
        // Create a map for quick flow lookup: key = "srcIp_dstIp" (relaxed matching)
        Map<String, Flow> detectedFlowMap = new HashMap<>();
        if (detectedFlows != null) {
            for (Flow flow : detectedFlows) {
                String key = flow.srcIp + "_" + flow.dstIp;
                detectedFlowMap.put(key, flow);
            }
            System.out.println("[DEBUG] Created detected flow map with " + detectedFlowMap.size() + " entries (relaxed matching: src_ip + dst_ip only)");
        }
        
        int totalDuplicatesSkipped = 0;
        
        for (GraphData.Edge e : apiEdges) {
            // Track processed node pairs for this edge to avoid creating duplicate links
            // when multiple IPs map to the same node pair
            Set<String> processedNodePairs = new HashSet<>();
            int duplicatesInThisEdge = 0;
            
            // Handle multiple IPs in src_ip and dst_ip
            if (e.src_ip != null && !e.src_ip.isEmpty() && e.dst_ip != null && !e.dst_ip.isEmpty()) {
                // Create links for all combinations of src_ip and dst_ip
                for (Long srcIpValue : e.src_ip) {
                    System.out.println("[DEBUG] Processing edge - src_ip: " + srcIpValue + " (0x" + Long.toHexString(srcIpValue) + ")");
                    String srcIp = convertLittleEndianToIp(srcIpValue);
                    
                    // Skip invalid source IPs
                    if ("0.0.0.0".equals(srcIp)) {
                        System.out.println("[DEBUG] Skipping edge with invalid source IP");
                        continue;
                    }
                    
                    Node sourceNode = ipToNodeMap.get(srcIp);
                    
                    for (Long dstIpValue : e.dst_ip) {
                        System.out.println("[DEBUG] Processing edge - dst_ip: " + dstIpValue + " (0x" + Long.toHexString(dstIpValue) + ")");
                        String dstIp = convertLittleEndianToIp(dstIpValue);
                        
                        // Skip invalid destination IPs
                        if ("0.0.0.0".equals(dstIp)) {
                            System.out.println("[DEBUG] Skipping edge with invalid destination IP");
                            continue;
                        }
                        
                        Node targetNode = ipToNodeMap.get(dstIp);
                        
                        // If both nodes found, create connection
                        if (sourceNode != null && targetNode != null) {
                            // Skip self-loop (when source and target are the same node)
                            if (sourceNode == targetNode) {
                                System.out.println("[DEBUG] Skipping self-loop: " + srcIp + " -> " + dstIp + " (both map to " + sourceNode.name + ")");
                                continue;
                            }
                            // ✅ FIX: Use actual IP from edge, not node's primary IP
                            // This is critical when a node (especially host) has multiple IP aliases
                            String source = srcIp;
                            String target = dstIp;
                            
                            // ✅ FIX: Generate unique key based on actual IPs (not node pair)
                            // This ensures each IP pair gets its own link, even if they map to the same nodes
                            String ipPairKey = source + " -> " + target;  // Directional key
                            
                            // Check if we've already created a link for this IP pair in this edge
                            if (processedNodePairs.contains(ipPairKey)) {
                                duplicatesInThisEdge++;
                                System.out.println("[DEDUP] Skipping duplicate link for IP pair: " + ipPairKey);
                                continue;
                            }
                            
                            // Mark this IP pair as processed
                            processedNodePairs.add(ipPairKey);
                            
                            // Convert src_ip/dst_ip (List<Long>) to List<String>, using standard IP format
                            List<String> sourceIps = new ArrayList<>();
                            if (e.src_ip != null) for (Long l : e.src_ip) sourceIps.add(convertLittleEndianToIp(l));
                            List<String> targetIps = new ArrayList<>();
                            if (e.dst_ip != null) for (Long l : e.dst_ip) targetIps.add(convertLittleEndianToIp(l));
                            boolean is_up = e.is_up;
                            int bandwidth = (int) e.link_bandwidth_bps;
                            boolean is_enabled = e.is_enabled;
                            double utilization = e.link_bandwidth_utilization_percent;
                            
                            // Parse flow_set
                            List<Flow> flowSetList = new ArrayList<>();
                            if (e.flow_set != null) {
                                for (GraphData.FlowSet fs : e.flow_set) {
                                    // Convert little-endian integer IP to standard IP format
                                    String srcIpStandard = convertLittleEndianToIp(fs.src_ip);
                                    String dstIpStandard = convertLittleEndianToIp(fs.dst_ip);
                                    
                                    // Try to find matching detected flow with complete path info (relaxed matching: only src_ip and dst_ip)
                                    String flowKey = srcIpStandard + "_" + dstIpStandard;
                                    Flow detectedFlow = detectedFlowMap.get(flowKey);
                                    
                                    if (detectedFlow != null && detectedFlow.pathNodes != null && detectedFlow.pathNodes.size() >= 2) {
                                        // Use complete flow information from detected flows (with full path)
                                        System.out.println("[DEBUG] Found matching detected flow with " + detectedFlow.pathNodes.size() + " path nodes for: " + srcIpStandard + " -> " + dstIpStandard + " (port " + fs.src_port + ":" + fs.dst_port + ")");
                                        flowSetList.add(detectedFlow);
                                    } else {
                                        // Fallback: Create simple flow with only 2 nodes (src and dst)
                                        List<String> pathNodes = new ArrayList<>();
                                        pathNodes.add(srcIpStandard);
                                        pathNodes.add(dstIpStandard);
                                        
                                        List<Integer> pathPorts = new ArrayList<>();
                                        pathPorts.add(fs.src_port);
                                        pathPorts.add(fs.dst_port);
                                        
                                        System.out.println("[DEBUG] No detected flow found, using simple 2-node path for: " + srcIpStandard + " -> " + dstIpStandard);
                                        flowSetList.add(new Flow(
                                            pathNodes,
                                            pathPorts,
                                            srcIpStandard,
                                            dstIpStandard,
                                            fs.src_port,
                                            fs.dst_port,
                                            fs.protocol_number,
                                            0, 0, 0, 0, 0, 0 // Other fields set to 0
                                        ));
                                    }
                                }
                            }
                            
                            // Create Link and store additional API data (not used for now)
                            Link link = new Link(source, target, sourceIps, targetIps, is_up, bandwidth, is_enabled, utilization, flowSetList, 
                                               e.src_dpid, e.dst_dpid, e.dst_port, e.src_interface, e.dst_interface, e.left_link_bandwidth_bps, e.link_bandwidth_usage_bps);
                            links.add(link);
                            
                            System.out.println("[DEBUG] Created link: " + source + " -> " + target);
                        } else {
                    System.out.println("[DEBUG] Unable to find node connection: src_ip=" + srcIp + ", dst_ip=" + dstIp);
                        }
                    }
                }
            } else {
                System.out.println("[DEBUG] Edge has null or empty src_ip/dst_ip: src_ip=" + 
                    (e.src_ip != null ? e.src_ip.size() : "null") + 
                    ", dst_ip=" + (e.dst_ip != null ? e.dst_ip.size() : "null"));
            }
            
            // Accumulate duplicate statistics
            totalDuplicatesSkipped += duplicatesInThisEdge;
            if (duplicatesInThisEdge > 0) {
                System.out.println("[DEDUP] Edge skipped " + duplicatesInThisEdge + " duplicate links");
            }
        }
        
        System.out.println("[DEDUP] ========== Link Deduplication Summary ==========");
        System.out.println("[DEDUP] Total links created: " + links.size());
        System.out.println("[DEDUP] Total duplicate links skipped: " + totalDuplicatesSkipped);
        if (totalDuplicatesSkipped > 0) {
            int wouldHaveCreated = links.size() + totalDuplicatesSkipped;
            double reductionPercent = (totalDuplicatesSkipped * 100.0) / wouldHaveCreated;
            System.out.println("[DEDUP] Without deduplication would have created: " + wouldHaveCreated + " links");
            System.out.println("[DEDUP] Reduction: " + String.format("%.1f", reductionPercent) + "%");
        }
        System.out.println("[DEDUP] ================================================");
        return links;
    }
    
    // Convert API DetectedFlowData to GUI Flow
    private List<Flow> convertDetectedFlows(DetectedFlowData[] apiFlows, List<Node> nodes) {
        List<Flow> flows = new ArrayList<>();
        
        // If no API data, return empty list instead of adding test data
        if (apiFlows == null || apiFlows.length == 0) {
            System.out.println("[DEBUG] No API flows detected, returning empty list");
            return flows;
        }
        
        
        
        Map<Long, String> dpidToIpMap = new HashMap<>();
        for (Node node : nodes) {
            if (node.dpid != 0) {
                
                String ip = null;
                if (node.ip != null && !node.ip.isEmpty()) {
                    ip = node.ip;
                } else if (node.ips != null && !node.ips.isEmpty()) {
                    ip = node.ips.get(0);
                }
                if (ip != null) {
                    dpidToIpMap.put(node.dpid, ip);
                }
            }
        }
        System.out.println("[OPTIMIZATION] Created DPID to IP map with " + dpidToIpMap.size() + " entries");
        
        System.out.println("========== FLOW PATH ANALYSIS (Total: " + apiFlows.length + " flows) ==========");
        int flowIndex = 0;
        for (DetectedFlowData f : apiFlows) {
            flowIndex++;
            System.out.println("\n====== FLOW #" + flowIndex + " ======");
            // Log raw flow data for debugging
            System.out.println("[DEBUG] Processing flow - src_ip: " + f.src_ip + " (0x" + Long.toHexString(f.src_ip) + 
                             "), dst_ip: " + f.dst_ip + " (0x" + Long.toHexString(f.dst_ip) + ")");
            
            List<String> pathNodes = new ArrayList<>();
            List<Integer> pathPorts = new ArrayList<>();
            
            // Correctly handle path data
            if (f.path != null && !f.path.isEmpty()) {
                System.out.println("[DEBUG] Flow has " + f.path.size() + " path nodes");
                for (DetectedFlowData.PathNode pn : f.path) {
                    System.out.println("[DEBUG] Path node: " + pn.node + " (0x" + Long.toHexString(pn.node) + 
                                     "), interface: " + pn.interface_id);
                    
                    String nodeIp = null;
                    
                    
                    
                    
                    
                    String dpidIp = dpidToIpMap.get(pn.node);
                    if (dpidIp != null) {
                        System.out.println("[DEBUG] Path node matched DPID in topology, treat as SWITCH. dpid=" 
                                           + pn.node + " (0x" + Long.toHexString(pn.node) + "), ip=" + dpidIp);
                        nodeIp = dpidIp;
                    } else if (pn.node > 0xFFFFFFFFL) {
                        
                        System.out.println("[DEBUG] Path node is large value (>32-bit) but not found in DPID table, treat as SWITCH without IP mapping: " 
                                           + pn.node + " (0x" + Long.toHexString(pn.node) + ")");
                        
                    } else {
                        
                        System.out.println("[DEBUG] Path node is treated as HOST IP (32-bit), converting...");
                        nodeIp = convertLittleEndianToIp(pn.node);
                        System.out.println("[DEBUG] Converted IP: " + nodeIp);
                    }
                    
                    // Add to path if valid
                    if (nodeIp != null && !"0.0.0.0".equals(nodeIp)) {
                        pathNodes.add(nodeIp);
                        pathPorts.add(pn.interface_id); // Use interface_id instead of port
                    } else {
                        System.err.println("[ERROR] ❌ Failed to resolve path node: " + pn.node + 
                                         " (0x" + Long.toHexString(pn.node) + "). This node will be MISSING from path!");
                    }
                }
                
                // Debug output: show path information
                System.out.println("[RESULT] Original path size: " + f.path.size());
                System.out.println("[RESULT] Resolved path size: " + pathNodes.size());
                if (pathNodes.size() < f.path.size()) {
                    System.err.println("[ERROR] ❌❌❌ PATH INCOMPLETE! Lost " + (f.path.size() - pathNodes.size()) + " nodes!");
                    System.err.println("[ERROR] Flow animation will have GAPS in the middle!");
                } else {
                    System.out.println("[SUCCESS] ✅ Complete path preserved - all " + pathNodes.size() + " nodes resolved");
                }
                System.out.println("[RESULT] Flow path IPs: " + pathNodes);
                System.out.println("[RESULT] Flow interfaces: " + pathPorts);
            } else {
                // If no path data, at least add source and target nodes
                // Convert little-endian integer IP to standard IP format
                String srcIpStandard = convertLittleEndianToIp(f.src_ip);
                String dstIpStandard = convertLittleEndianToIp(f.dst_ip);
                pathNodes.add(srcIpStandard);
                pathNodes.add(dstIpStandard);
                pathPorts.add(0);
                pathPorts.add(0);
            }
            
            // Convert little-endian IP to standard IP format
            String srcIp = convertLittleEndianToIp(f.src_ip);
            String dstIp = convertLittleEndianToIp(f.dst_ip);
            
            // Skip flows with invalid IPs
            if ("0.0.0.0".equals(srcIp) || "0.0.0.0".equals(dstIp)) {
                System.out.println("[DEBUG] Skipping flow with invalid IP addresses");
                continue;
            }
            int srcPort = f.src_port;
            int dstPort = f.dst_port;
            int protocolId = f.protocol_id;
            // Parse time strings from API format "2025-11-11 10:30:35" to milliseconds
            int startTimeMs = parseTimeStringToMs(f.first_sampled_time);
            int endTimeMs = parseTimeStringToMs(f.latest_sampled_time);
            double estimatedFlowSendingRateBpsInTheLastSec = f.estimated_flow_sending_rate_bps_in_the_last_sec;
            double estimatedFlowSendingRateBpsInTheProceeding1secTimeslot = f.estimated_flow_sending_rate_bps_in_the_proceeding_1sec_timeslot;
            int estimatedPacketRateInTheLastSec = (int) Math.min(f.estimated_packet_rate_in_the_last_sec, Integer.MAX_VALUE);
            int estimatedPacketRateInTheProceeding1secTimeslot = (int) Math.min(f.estimated_packet_rate_in_the_proceeding_1sec_timeslot, Integer.MAX_VALUE);
            
            Flow flow = new Flow(
                pathNodes, pathPorts, srcIp, dstIp, srcPort, dstPort, protocolId,
                startTimeMs, endTimeMs,
                estimatedFlowSendingRateBpsInTheLastSec,
                estimatedFlowSendingRateBpsInTheProceeding1secTimeslot,
                estimatedPacketRateInTheLastSec,
                estimatedPacketRateInTheProceeding1secTimeslot
            );
            
            flows.add(flow);
        }
        
        System.out.println("\n========== FLOW CONVERSION COMPLETE ==========");
        System.out.println("Total flows created: " + flows.size() + "/" + apiFlows.length);
        return flows;
    }

    // Method to update topology with playback data
    public void updateTopologyWithPlaybackData(PlaybackData.PlaybackFrame frame) {
        // This method will be called by PlaybackPanel to update the topology
        // with data from a specific playback frame
        
        System.out.println("[PLAYBACK] Updating topology with frame data:");
        System.out.println("  Time: " + frame.time);
        System.out.println("  Edges: " + (frame.edges != null ? frame.edges.size() : 0));
        System.out.println("  Nodes: " + (frame.nodes != null ? frame.nodes.size() : 0));
        System.out.println("  Flows: " + (frame.flow != null ? frame.flow.size() : 0));
        
        // Convert playback frame data to topology data
        List<Node> playbackNodes = convertPlaybackNodes(frame);
        List<Link> playbackLinks = convertPlaybackLinks(frame);
        List<Flow> playbackFlows = convertPlaybackFlows(frame);
        
        // Update the topology canvas with playback data
        Platform.runLater(() -> {
            
            if (!isPlaybackMode) {
                System.out.println("[PLAYBACK] Skipping playback update - switched to real-time mode");
                return;
            }
            
            topologyCanvas.updateTopology(playbackNodes, playbackLinks, playbackFlows);
            
            // Update SideBar with playback data
            sideBar.updateData(playbackFlows, playbackLinks, playbackNodes);
            System.out.println("[PLAYBACK] Updated SideBar with " + playbackFlows.size() + " flows");
        });
    }
    
    // New method for direct topology update from PlaybackPanel
    public void updateTopologyWithPlaybackData(List<Node> nodes, List<Link> links, List<Flow> flows) {
        System.out.println("[PLAYBACK] Updating topology directly:");
        System.out.println("  Nodes: " + (nodes != null ? nodes.size() : 0));
        System.out.println("  Links: " + (links != null ? links.size() : 0));
        System.out.println("  Flows: " + (flows != null ? flows.size() : 0));
        
        
        if (nodes != null && !nodes.isEmpty()) {
            applyPlaybackNodeLayout(nodes, links);
        }
        
        // Update the topology canvas directly
        Platform.runLater(() -> {
            
            if (!isPlaybackMode) {
                System.out.println("[PLAYBACK] Skipping playback update - switched to real-time mode");
                return;
            }
            
            topologyCanvas.updateTopology(nodes, links, flows);
            
            // Update SideBar with playback data
            sideBar.updateData(flows, links, nodes);
            System.out.println("[PLAYBACK] Updated SideBar with " + (flows != null ? flows.size() : 0) + " flows");
        });
    }
    
    



    private void applyPlaybackNodeLayout(List<Node> nodes, List<Link> links) {
        System.out.println("[PLAYBACK] Applying node layout for " + nodes.size() + " nodes");
        
        
        Map<String, int[]> savedPositions = loadNodePositions(NODE_POSITIONS_PLAYBACK);
        
        // Check if ALL nodes can match saved positions (complete match required)
        boolean allNodesMatched = false;
        if (savedPositions != null && !savedPositions.isEmpty()) {
            System.out.println("[PLAYBACK] Loaded " + savedPositions.size() + " saved positions from " + NODE_POSITIONS_PLAYBACK);
            int matchedCount = 0;
            for (Node node : nodes) {
                if (savedPositions.containsKey(node.ip)) {
                    matchedCount++;
                }
            }
            allNodesMatched = (matchedCount == nodes.size());
            System.out.println("[PLAYBACK] Position matching: " + matchedCount + "/" + nodes.size() + 
                             " nodes matched (complete match: " + allNodesMatched + ")");
        }
        
        if (savedPositions != null && !savedPositions.isEmpty() && allNodesMatched) {
            
            System.out.println("[PLAYBACK] Using saved positions (complete match)");
            for (Node node : nodes) {
                int[] pos = savedPositions.get(node.ip);
                if (pos != null && pos.length >= 2) {
                    node.x = pos[0];
                    node.y = pos[1];
                }
            }
        } else {
            
            if (savedPositions != null && !savedPositions.isEmpty() && !allNodesMatched) {
                System.out.println("[PLAYBACK] Incomplete match detected, switching to Fat-Tree auto-layout");
            } else {
                System.out.println("[PLAYBACK] No saved positions found, using Fat-Tree auto-layout");
            }
            applyPlaybackAutoLayout(nodes, topologyCanvas.getWidth(), topologyCanvas.getHeight());
        }
    }
    
    


    private void applyPlaybackAutoLayout(List<Node> nodes, double canvasWidth, double canvasHeight) {
        
        List<Node> coreNodes = new ArrayList<>();
        List<Node> aggregationNodes = new ArrayList<>();
        List<Node> edgeNodes = new ArrayList<>();
        List<Node> hostNodes = new ArrayList<>();
        
        for (Node node : nodes) {
            
            if ("host".equals(node.layer) || "2".equals(node.type) || "1".equals(node.type)) {
                hostNodes.add(node);
                node.layer = "host";
            } else if ("core".equals(node.layer)) {
                coreNodes.add(node);
                node.layer = "core";
            } else if ("aggregation".equals(node.layer)) {
                aggregationNodes.add(node);
                node.layer = "aggregation";
            } else if ("edge".equals(node.layer)) {
                edgeNodes.add(node);
                node.layer = "edge";
            } else {
                
                if (node.name.startsWith("s") || node.name.startsWith("S")) {
                    
                    try {
                        String numStr = node.name.replaceAll("[^0-9]", "");
                        if (!numStr.isEmpty()) {
                            int num = Integer.parseInt(numStr);
                            if (num <= 50) {
                                coreNodes.add(node);
                                node.layer = "core";
                            } else if (num <= 100) {
                                aggregationNodes.add(node);
                                node.layer = "aggregation";
                            } else {
                                edgeNodes.add(node);
                                node.layer = "edge";
                            }
                        } else {
                            edgeNodes.add(node);
                            node.layer = "edge";
                        }
                    } catch (NumberFormatException e) {
                        edgeNodes.add(node);
                        node.layer = "edge";
                    }
                } else if (node.name.startsWith("h") || node.name.startsWith("H")) {
                    hostNodes.add(node);
                    node.layer = "host";
                } else {
                    edgeNodes.add(node);
                    node.layer = "edge";
                }
            }
        }
        
        System.out.println("[PLAYBACK] Node classification: Core=" + coreNodes.size() + 
                         ", Agg=" + aggregationNodes.size() + ", Edge=" + edgeNodes.size() + ", Host=" + hostNodes.size());
        
        
        applyFatTreeLayout(coreNodes, aggregationNodes, edgeNodes, hostNodes, canvasWidth, canvasHeight);
    }
    
    // Convert playback node data to Node objects
    private List<Node> convertPlaybackNodes(PlaybackData.PlaybackFrame frame) {
        List<Node> nodes = new ArrayList<>();
        
        if (frame.nodes != null) {
            for (PlaybackData.NodeData nodeData : frame.nodes) {
                // Create a simple node representation
                // In a real implementation, you would map the node data more accurately
                String nodeName = "Node_" + nodeData.number;
                String nodeType = "0"; // Default to switch
                Node node = new Node("", nodeName, 100, 100, nodeType, true, true);
                node.brandName = nodeData.brand_name;
                nodes.add(node);
            }
        }
        
        return nodes;
    }
    
    // Convert playback edge data to Link objects
    private List<Link> convertPlaybackLinks(PlaybackData.PlaybackFrame frame) {
        List<Link> links = new ArrayList<>();
        
        if (frame.edges != null) {
            for (int i = 0; i < frame.edges.size(); i++) {
                // Create a simple link representation
                // In a real implementation, you would map the edge data more accurately
                Link link = new Link("", "", new ArrayList<>(), new ArrayList<>(), 
                    true, 1000000, true, 0.0, new ArrayList<>(), 
                    null, null, 0, 0, 0, 0, 0);
                links.add(link);
            }
        }
        
        return links;
    }
    
    // Convert playback flow data to Flow objects
    private List<Flow> convertPlaybackFlows(PlaybackData.PlaybackFrame frame) {
        List<Flow> flows = new ArrayList<>();
        
        if (frame.flow != null) {
            for (int i = 0; i < frame.flow.size(); i++) {
                // Create a simple flow representation
                // In a real implementation, you would map the flow data more accurately
                List<String> pathNodes = new ArrayList<>();
                List<Integer> pathPorts = new ArrayList<>();
                
                Flow flow = new Flow(pathNodes, pathPorts, "", "", 
                    0, 0, 0, 0, 0, 0.0, 0.0, 0, 0);
                flows.add(flow);
            }
        }
        
        return flows;
    }

    // Method to show/hide PlaybackPanel
    public void togglePlaybackPanel() {
        if (playbackPanel != null) {
            boolean isCurrentlyInLayout = mainContent.getChildren().contains(playbackPanel);
            
            if (isCurrentlyInLayout) {
                // Hide PlaybackPanel - return to real-time mode
                System.out.println("[MODE_SWITCH] Starting switch to real-time mode...");
                
                
                try {
                    if (playbackPanel != null) {
                        playbackPanel.stopPlaybackExternal();
                    }
                } catch (Exception ignore) {}
                
                
                resumeApiUpdates();
                System.out.println("[MODE_SWITCH] API updates resumed");
                
                
                topologyCanvas.setPlaybackMode(false);
                topologyCanvas.setNodePositionFile(NODE_POSITIONS_REALTIME);
                System.out.println("[MODE_SWITCH] Canvas switched to real-time mode");
                
                
                mainContent.getChildren().remove(playbackPanel);
                topologyCanvas.heightProperty().unbind();
                topologyCanvas.heightProperty().bind(root.heightProperty());
                
                System.out.println("[MODE_SWITCH] Switched back to real-time mode - COMPLETE");
                
            } else {
                // Show PlaybackPanel - enter playback mode
                System.out.println("[MODE_SWITCH] Starting switch to playback mode...");
                
                
                pauseApiUpdates();
                System.out.println("[MODE_SWITCH] API updates paused");
                
                
                topologyCanvas.setPlaybackMode(true);
                topologyCanvas.setNodePositionFile(NODE_POSITIONS_PLAYBACK);
                System.out.println("[MODE_SWITCH] Canvas switched to playback mode");
                
                
                clearTopologyForPlayback();
                
                
                mainContent.getChildren().add(0, playbackPanel);
                playbackPanel.show();
                topologyCanvas.heightProperty().unbind();
                topologyCanvas.heightProperty().bind(
                    root.heightProperty().subtract(playbackPanel.heightProperty())
                );
                
                
                topologyCanvas.setShowFlows(true);
                topologyCanvas.setShowLinks(false);
                
                System.out.println("[MODE_SWITCH] Switched to playback mode - COMPLETE");
            }
        }
    }
    
    // Pause API updates when entering playback mode
    private void pauseApiUpdates() {
        isPlaybackMode = true;
        System.out.println("[PLAYBACK] API updates paused");
    }
    
    // Resume API updates when exiting playback mode
    private void resumeApiUpdates() {
        isPlaybackMode = false;
        System.out.println("[PLAYBACK] API updates resumed");
    }
    
    // Clear topology when entering playback mode
    private void clearTopologyForPlayback() {
        Platform.runLater(() -> {
            // Show empty topology with grid background (same as real-time mode but no data)
            topologyCanvas.updateTopology(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
            
            // Clear sidebar data as well
            if (sideBar != null) {
                sideBar.updateData(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
                System.out.println("[PLAYBACK] Cleared sidebar data for playback mode");
            }
            
            // Force redraw to show the grid background
            topologyCanvas.draw();
            System.out.println("[PLAYBACK] Cleared topology for playback mode - showing empty grid");
        });
    }
    
    // Loading overlay control methods
    public void showLoading(String message) {
        Platform.runLater(() -> {
            loadingLabel.setText(message);
            progressBar.setProgress(0.0);
            loadingOverlay.setVisible(true);
            loadingIndicator.setVisible(true);
            System.out.println("[LOADING] Showing loading overlay: " + message);
        });
    }
    
    public void updateProgress(int percentage) {
        Platform.runLater(() -> {
            progressBar.setProgress(percentage / 100.0);
            System.out.println("[LOADING] Progress: " + percentage + "%");
        });
    }
    
    public void hideLoading() {
        Platform.runLater(() -> {
            loadingOverlay.setVisible(false);
            loadingIndicator.setVisible(false);
            System.out.println("[LOADING] Hiding loading overlay");
        });
    }
    
    
    public void notifyPlaybackStateChanged() {
        System.out.println("[DEBUG] NetworkTopologyApp.notifyPlaybackStateChanged called");
        if (playbackPanel != null) {
            boolean isPlaying = playbackPanel.isPlaying();
            System.out.println("[DEBUG] Playback state: " + (isPlaying ? "playing" : "paused"));
            
            
            if (sideBar != null) {
                Platform.runLater(() -> {
                    sideBar.updatePlaybackButtonStyle(isPlaying);
                    System.out.println("[DEBUG] Updated SideBar playback button style");
                });
            }
        }
    }
    
    // Getter for topologyCanvas
    public TopologyCanvas getTopologyCanvas() {
        return topologyCanvas;
    }

    /**
     * Assign flows to links based on flow.pathNodes
     * This method ensures that all flows are displayed on their complete paths,
     * not just relying on API's edge.flow_set which may be incomplete.
     * 
     * Similar to PlaybackPanel.assignFlowsToLinks()
     */
    private void assignFlowsToLinks(List<Flow> flows, List<Link> links, List<Node> nodes) {
        System.out.println("[REALTIME-ASSIGN] ========== Flow Assignment Start ==========");
        System.out.println("[REALTIME-ASSIGN] Total flows: " + flows.size());
        System.out.println("[REALTIME-ASSIGN] Total links: " + links.size());
        
        // Clear existing flow_set from all links first
        for (Link link : links) {
            if (link.flow_set == null) {
                link.flow_set = new ArrayList<>();
            } else {
                link.flow_set.clear();
            }
        }
        
        
        
        
        Map<String, Link> linkMap = new HashMap<>();
        for (Link link : links) {
            String key = link.source + "->" + link.target;
            linkMap.put(key, link);
        }
        System.out.println("[OPTIMIZATION] Created Link lookup map with " + linkMap.size() + " entries");
        
        // Filter flows with valid paths
        int assignedFlowInstances = 0;
        int flowsWithNoPath = 0;
        int flowsWithShortPath = 0;
        int linksNotFound = 0;
        
        for (Flow flow : flows) {
            if (flow.pathNodes == null) {
                flowsWithNoPath++;
                continue;
            }
            if (flow.pathNodes.size() < 2) {
                flowsWithShortPath++;
                continue;
            }
            
            // For each flow, find all links it passes through based on pathNodes
            for (int i = 0; i < flow.pathNodes.size() - 1; i++) {
                String nodeA = flow.pathNodes.get(i);
                String nodeB = flow.pathNodes.get(i + 1);
                
                // pathNodes already in standard IP format (converted in convertDetectedFlows)
                String ipA = nodeA;
                String ipB = nodeB;
                
                
                // Find the link between these two nodes (STRICT DIRECTIONAL - no bidirectional)
                String linkKey = ipA + "->" + ipB;
                Link link = linkMap.get(linkKey);
                
                if (link != null) {
                    // Add this flow to the link's flow_set
                    link.flow_set.add(flow);
                    assignedFlowInstances++;
                } else {
                    linksNotFound++;
                    System.out.println("[REALTIME-ASSIGN] ✗ No link found for path segment: " + ipA + " -> " + ipB +
                                     " (Flow: " + flow.srcIp + ":" + flow.srcPort + " -> " + flow.dstIp + ":" + flow.dstPort + ")");
                }
            }
        }
        
        int linksWithFlows = (int) links.stream().filter(l -> l.flow_set != null && !l.flow_set.isEmpty()).count();
        
        System.out.println("[REALTIME-ASSIGN] ========== Flow Assignment Complete ==========");
        System.out.println("[REALTIME-ASSIGN] Valid flows: " + (flows.size() - flowsWithNoPath - flowsWithShortPath));
        System.out.println("[REALTIME-ASSIGN] Flows with no path: " + flowsWithNoPath);
        System.out.println("[REALTIME-ASSIGN] Flows with short path (< 2 nodes): " + flowsWithShortPath);
        System.out.println("[REALTIME-ASSIGN] Assigned flow instances to links: " + assignedFlowInstances);
        System.out.println("[REALTIME-ASSIGN] Path segments without matching link: " + linksNotFound);
        System.out.println("[REALTIME-ASSIGN] Links with flows: " + linksWithFlows + "/" + links.size());
    }

    public static void main(String[] args) {
        launch(args);
    }
}

