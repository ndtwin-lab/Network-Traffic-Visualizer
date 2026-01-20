package org.example.demo2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class InfoDialog {
    private final TopologyCanvas topologyCanvas;
    private final List<Flow> allFlows;
    private Stage dialog; // Add a Stage member variable
    
    // Create a data class containing flow and direction information
    private static class FlowTableItem {
        final Flow flow;
        final String direction;
        final String convertedSrcIp;
        final String convertedDstIp;
        final double sendingRate;
        final int srcPort;
        final int dstPort;
        final int protocol;
        final String startTime;
        final String endTime;
        
        FlowTableItem(Flow flow, String direction, String convertedSrcIp, String convertedDstIp) {
            this.flow = flow;
            this.direction = direction;
            this.convertedSrcIp = convertedSrcIp;
            this.convertedDstIp = convertedDstIp;
            this.sendingRate = flow.estimatedFlowSendingRateBpsInTheLastSec;
            this.srcPort = flow.srcPort;
            this.dstPort = flow.dstPort;
            this.protocol = flow.protocolId;
            this.startTime = convertMsToTimeFormat(flow.startTimeMs);
            this.endTime = convertMsToTimeFormat(flow.endTimeMs);
        }

    }



    // New: Convert milliseconds to hh:mm:ss format (seconds only)
    private static String convertMsToTimeFormat(int milliseconds) {
        int hours = milliseconds / (1000 * 60 * 60);
        int minutes = (milliseconds % (1000 * 60 * 60)) / (1000 * 60);
        int seconds = (milliseconds % (1000 * 60)) / 1000;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    // New: Convert IP string to standard format (if it's a little-endian integer)
    private String convertLittleEndianIpStringToStandard(String ipString) {
        try {
            // Try to parse as integer (little-endian)
            int littleEndianIp = Integer.parseInt(ipString);
            return convertLittleEndianToIp(littleEndianIp);
        } catch (NumberFormatException e) {
            // If not an integer, return the original string directly
            return ipString;
        }
    }

    // New: Convert int to standard IP string (little-endian)
    private String convertLittleEndianToIp(int ip) {
        return String.format("%d.%d.%d.%d",
            ip & 0xFF,
            (ip >> 8) & 0xFF,
            (ip >> 16) & 0xFF,
            (ip >> 24) & 0xFF
        );
    }
    
    // New: Convert IP string for display with reversed byte order (10.0.0.100 instead of 100.0.0.10)
    // Used in Link Information and Flow Information windows
    // Handles both integer strings (little-endian) and already-formatted IP strings (like "1.0.0.10")
    private String convertIpStringForDisplay(String ipString) {
        if (ipString == null || ipString.isEmpty()) {
            return ipString;
        }
        
        // First, try to parse as integer (little-endian format)
        try {
            int littleEndianIp = Integer.parseInt(ipString);
            // Reverse byte order for display: 10.0.0.100 instead of 100.0.0.10
            return String.format("%d.%d.%d.%d",
                (littleEndianIp >> 24) & 0xFF,
                (littleEndianIp >> 16) & 0xFF,
                (littleEndianIp >> 8) & 0xFF,
                littleEndianIp & 0xFF
            );
        } catch (NumberFormatException e) {
            // If not an integer, check if it's already in dot-decimal format (like "1.0.0.10")
            // If so, reverse the byte order
            if (ipString.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                String[] parts = ipString.split("\\.");
                if (parts.length == 4) {
                    try {
                        // Reverse the byte order: "1.0.0.10" -> "10.0.0.1"
                        return String.format("%s.%s.%s.%s", parts[3], parts[2], parts[1], parts[0]);
                    } catch (Exception ex) {
                        // If parsing fails, return original
                        return ipString;
                    }
                }
            }
            // If not in expected format, return the original string
            return ipString;
        }
    }
    
    // Alias for backward compatibility with Link Information window
    private String convertIpStringForLinkInfoDisplay(String ipString) {
        return convertIpStringForDisplay(ipString);
    }

    // Utility: Convert Object to int (supports Integer/Long/String)
    private int getIntFromObject(Object obj) {
        if (obj instanceof Integer) return (Integer) obj;
        if (obj instanceof Long) return ((Long) obj).intValue();
        if (obj instanceof String) return Integer.parseInt((String) obj);
        return 0;
    }

    // Format rate display (Gbps, Mbps, Kbps, bps)
    public static String formatRate(double bps) {
        if (bps >= 1_000_000_000) return String.format("%.2f Gbps", bps / 1_000_000_000);
        if (bps >= 1_000_000) return String.format("%.2f Mbps", bps / 1_000_000);
        if (bps >= 1_000) return String.format("%.2f Kbps", bps / 1_000);
        return String.format("%.0f bps", bps);
    }
    
    // Convert protocol numbers to text
    private String convertProtocolNumberToText(int protocolNumber) {
        switch (protocolNumber) {
            case 1: return "ICMP";
            case 6: return "TCP";
            case 17: return "UDP";
            case 50: return "ESP";
            case 51: return "AH";
            default: return String.valueOf(protocolNumber);
        }
    }
    
    /**
     * Find complete flow information from detected flow data that matches the flow in flow_set
     * Matching criteria: src_ip + dst_ip + src_port + dst_port + protocol_number must completely match
     */
    private Flow findCompleteFlowInfo(Flow flowInSet) {
        for (Flow detectedFlow : allFlows) {
            // Check if all five fields completely match
            if (detectedFlow.srcIp.equals(flowInSet.srcIp) &&
                detectedFlow.dstIp.equals(flowInSet.dstIp) &&
                detectedFlow.srcPort == flowInSet.srcPort &&
                detectedFlow.dstPort == flowInSet.dstPort &&
                detectedFlow.protocolId == flowInSet.protocolId) {
                return detectedFlow;
            }
        }
        return null; // No matching complete flow information found
    }


    
    public InfoDialog(TopologyCanvas topologyCanvas, List<Flow> allFlows) {
        this.topologyCanvas = topologyCanvas;
        this.allFlows = allFlows;
    }
    
    private Stage createDialog() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.NONE); // Non-modal dialog
        dialog.initStyle(StageStyle.DECORATED);
        dialog.setTitle("Information");
        dialog.setResizable(true);
        dialog.setMinWidth(500);
        dialog.setMinHeight(400);
        
        // Create main container
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: #f9f9f9;");
        
        // Title
        Label titleLabel = new Label("Information");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        titleLabel.setAlignment(Pos.CENTER);
        titleLabel.setMaxWidth(Double.MAX_VALUE);

        // Close button
        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> dialog.close());
        closeButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");

        HBox buttonBox = new HBox();
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.getChildren().add(closeButton);
        
        // Content area - only create flowBox, don't create TextArea
        VBox flowBox = new VBox(8);
        flowBox.setPadding(new Insets(10));
        
        // Use ScrollPane to wrap content
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setContent(flowBox);
        scrollPane.setFitToWidth(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        
        root.getChildren().addAll(titleLabel, scrollPane, buttonBox);
        
        Scene scene = new Scene(root, 600, 500);
        dialog.setScene(scene);
        
        return dialog;
    }
    
    public void showNodeInfo(Node node) {
        System.out.println("[DEBUG] showNodeInfo called for node: " + node.name + ", DPID: " + node.dpid + ", Brand: " + node.brandName + ", Layer: " + node.deviceLayer);
        Stage dialog = createDialog();
        dialog.setTitle("Device Information");
        
        // Get components from dialog
        VBox root = (VBox) dialog.getScene().getRoot();
        ScrollPane scrollPane = (ScrollPane) root.getChildren().get(1);
        VBox flowBox = (VBox) scrollPane.getContent();
        
        // Clear previous content
        flowBox.getChildren().clear();
        
        // Create title and action button area
        HBox headerBox = new HBox();
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setPadding(new Insets(10.0, 15.0, 15.0, 15.0));
        headerBox.setSpacing(10);
        
        Label titleLabel = new Label("Device Information");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        titleLabel.setStyle("-fx-text-fill: #2c3e50;");
        
        HBox buttonBox = new HBox();
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setSpacing(10);
        HBox.setHgrow(buttonBox, Priority.ALWAYS);
        
        Button hidePortsButton = new Button("Show Ports");
        hidePortsButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 16;");
        hidePortsButton.setOnAction(e -> togglePortsInfo(node));
        
        buttonBox.getChildren().addAll(hidePortsButton);
        headerBox.getChildren().addAll(titleLabel, buttonBox);
        
        // Create main content area
        VBox contentBox = new VBox();
        contentBox.setStyle("-fx-background-color: white; -fx-padding: 20; -fx-border-color: #ecf0f1; -fx-border-width: 1; -fx-border-radius: 5;");
        contentBox.setSpacing(15);
        
        // Create device detailed information
        VBox deviceInfoBox = new VBox();
        deviceInfoBox.setSpacing(12);
        
        // Device name
        HBox nameBox = createInfoRow("Device Name", node.name, false);
        deviceInfoBox.getChildren().add(nameBox);
        
        // Management IP (show all IP aliases)
        String displayIp = "";
        if (node.ips != null && !node.ips.isEmpty()) {
            // Display all IP aliases
            List<String> allIps = new ArrayList<>();
            for (String ip : node.ips) {
                allIps.add(convertLittleEndianIpStringToStandard(ip));
            }
            displayIp = String.join(", ", allIps);
        } else if (node.ip != null && !node.ip.isEmpty()) {
            displayIp = convertLittleEndianIpStringToStandard(node.ip);
        }
        HBox ipBox = createInfoRow("Management IP", displayIp, false);
        deviceInfoBox.getChildren().add(ipBox);
        
        // Device DPID
        String dpidText = node.dpid != 0 ? String.valueOf(node.dpid) : "N/A";
        HBox dpidBox = createInfoRow("Device DPID", dpidText, false);
        deviceInfoBox.getChildren().add(dpidBox);
        
        // Brand Name
        String brandText = (node.brandName != null && !node.brandName.trim().isEmpty()) ? node.brandName : "N/A";
        HBox brandBox = createInfoRow("Brand Name", brandText, false);
        deviceInfoBox.getChildren().add(brandBox);
        
        // Device Layer
        String layerText = node.deviceLayer != null ? String.valueOf(node.deviceLayer) : "N/A";
        HBox layerBox = createInfoRow("Device Layer", layerText, false);
        deviceInfoBox.getChildren().add(layerBox);
        
        // CPU utilization
        String cpuText = node.getCpuUtilization() != null ? node.getCpuUtilization() + "%" : "N/A";
        HBox cpuBox = createInfoRow("CPU Utilization", cpuText, false);
        deviceInfoBox.getChildren().add(cpuBox);
        
        // Memory utilization
        String memText = node.getMemoryUtilization() != null ? node.getMemoryUtilization() + "%" : "N/A";
        HBox memBox = createInfoRow("Memory Utilization", memText, false);
        deviceInfoBox.getChildren().add(memBox);
        
        // Temperature (temporarily set to N/A)
        HBox tempBox = createInfoRow("Temperature", "N/A", false);
        deviceInfoBox.getChildren().add(tempBox);
        
        // is_enabled
        String enabledText = node.is_enabled ? "YES" : "NO";
        String enabledColor = node.is_enabled ? "#27ae60" : "#e74c3c";
        HBox enabledBox = createInfoRow("Enabled", enabledText, false, enabledColor);
        deviceInfoBox.getChildren().add(enabledBox);
        
        // is_up
        String upText = node.is_up ? "UP" : "DOWN";
        String upColor = node.is_up ? "#27ae60" : "#e74c3c";
        HBox upBox = createInfoRow("Status", upText, false, upColor);
        deviceInfoBox.getChildren().add(upBox);
        
        contentBox.getChildren().add(deviceInfoBox);
        
        // Create port information area (initially hidden)
        VBox portsInfoBox = createPortsInfoBox(node);
        portsInfoBox.setVisible(false);
        portsInfoBox.setManaged(false);
        contentBox.getChildren().add(portsInfoBox);
        
        // Add content to main container
        flowBox.getChildren().addAll(headerBox, contentBox);
        dialog.show();
    }
    
    // Toggle port information display
    private void togglePortsInfo(org.example.demo2.Node node) {
        // Create new port connection window
        showPortConnectionsWindow(node);
    }
    
    // Show port connection window
    private void showPortConnectionsWindow(org.example.demo2.Node node) {
        Stage portWindow = new Stage();
        portWindow.initModality(Modality.NONE); // Non-modal window
        portWindow.initStyle(StageStyle.DECORATED);
        portWindow.setTitle("Port Information - " + node.name);
        portWindow.setWidth(800);
        portWindow.setHeight(700);
        
        // Create main container
        VBox root = new VBox();
        root.setStyle("-fx-background-color: #f8f9fa; -fx-padding: 20;");
        root.setSpacing(15);
        
        // Title
        Label titleLabel = new Label("Port Information");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        titleLabel.setStyle("-fx-text-fill: #2c3e50;");
        titleLabel.setAlignment(Pos.CENTER);
        
        // Device information summary
        VBox deviceSummary = new VBox();
        deviceSummary.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-border-color: #e9ecef; -fx-border-width: 1; -fx-border-radius: 5;");
        deviceSummary.setSpacing(8);
        
        Label deviceNameLabel = new Label("Device: " + node.name);
        deviceNameLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        
        String displayIp = "";
        if (node.ips != null && !node.ips.isEmpty()) {
            displayIp = convertLittleEndianIpStringToStandard(node.ips.get(0));
        } else if (node.ip != null && !node.ip.isEmpty()) {
            displayIp = convertLittleEndianIpStringToStandard(node.ip);
        }
        Label deviceIpLabel = new Label("IP: " + displayIp);
        
        deviceSummary.getChildren().addAll(deviceNameLabel, deviceIpLabel);
        
        // Create Ingress and Egress tables
        HBox tablesContainer = new HBox(20);
        tablesContainer.setAlignment(Pos.TOP_CENTER);
        
        // Ingress Table
        VBox ingressContainer = createPortTable("Ingress Ports", node, true);
        
        // Egress Table
        VBox egressContainer = createPortTable("Egress Ports", node, false);
        
        tablesContainer.getChildren().addAll(ingressContainer, egressContainer);
        
        // Create a container to place buttons and use VBox.setVgrow to make it automatically expand to the bottom
        VBox buttonContainer = new VBox();
        buttonContainer.setAlignment(Pos.BOTTOM_CENTER);
        buttonContainer.setPadding(new Insets(20, 0, 0, 0));
        VBox.setVgrow(buttonContainer, Priority.ALWAYS);
        
        // Close button
        Button closeButton = new Button("Close");
        closeButton.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20;");
        closeButton.setOnAction(e -> portWindow.close());
        
        HBox buttonBox = new HBox();
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setSpacing(10);
        buttonBox.getChildren().add(closeButton);
        
        buttonContainer.getChildren().add(buttonBox);
        
        // Assemble all components
        root.getChildren().addAll(titleLabel, deviceSummary, tablesContainer, buttonContainer);
        
        // Set scene
        Scene scene = new Scene(root);
        portWindow.setScene(scene);
        
        // Show window
        portWindow.show();
        
        // Immediately update port tables with data
        updatePortTable(ingressContainer, node, true);
        updatePortTable(egressContainer, node, false);
    }
    
    // Update port connection information
    private void updatePortConnections(VBox connectionsList, org.example.demo2.Node node) {
        connectionsList.getChildren().clear();
        
        // Get topology canvas to access connection information
        if (topologyCanvas != null) {
            List<Link> links = topologyCanvas.getLinks();
            if (links != null) {
                int connectionCount = 0;
                for (Link link : links) {
                    // Check if related to current node
                    if (isNodeConnectedToLink(node, link)) {
                        VBox connectionBox = createConnectionInfoBox(node, link);
                        connectionsList.getChildren().add(connectionBox);
                        connectionCount++;
                    }
                }
                
                // Display connection count
                if (connectionCount > 0) {
                    Label countLabel = new Label("Total connections: " + connectionCount);
                    countLabel.setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold; -fx-padding: 5 0;");
                    connectionsList.getChildren().add(0, countLabel);
                }
            }
        }
        
        // If no connections, display prompt
        if (connectionsList.getChildren().isEmpty()) {
            Label noConnectionsLabel = new Label("No port found");
            noConnectionsLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-style: italic; -fx-padding: 20;");
            noConnectionsLabel.setAlignment(Pos.CENTER);
            connectionsList.getChildren().add(noConnectionsLabel);
        }
    }
    
    // Create port information area
    private VBox createPortsInfoBox(org.example.demo2.Node node) {
        VBox portsInfoBox = new VBox();
        portsInfoBox.setSpacing(12);
        portsInfoBox.setStyle("-fx-background-color: #f8f9fa; -fx-padding: 15; -fx-border-color: #e9ecef; -fx-border-width: 1; -fx-border-radius: 5;");
        
        // Title
        Label portsTitleLabel = new Label("Port Information");
        portsTitleLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        portsTitleLabel.setStyle("-fx-text-fill: #2c3e50;");
        
        // Connection information list
        VBox connectionsList = new VBox();
        connectionsList.setSpacing(8);
        connectionsList.setId("connectionsList"); // For subsequent updates
        
        portsInfoBox.getChildren().addAll(portsTitleLabel, connectionsList);
        return portsInfoBox;
    }
    
    // Update port information
    private void updatePortsInfo(VBox portsInfoBox, org.example.demo2.Node node) {
        VBox connectionsList = (VBox) portsInfoBox.getChildren().get(1);
        connectionsList.getChildren().clear();
        
        // Get topology canvas to access connection information
        if (topologyCanvas != null) {
            List<Link> links = topologyCanvas.getLinks();
            if (links != null) {
                for (Link link : links) {
                    // Check if related to current node
                    if (isNodeConnectedToLink(node, link)) {
                        VBox connectionBox = createConnectionInfoBox(node, link);
                        connectionsList.getChildren().add(connectionBox);
                    }
                }
            }
        }
        
        // If no connections, display prompt
        if (connectionsList.getChildren().isEmpty()) {
            Label noConnectionsLabel = new Label("No port found");
            noConnectionsLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-style: italic;");
            connectionsList.getChildren().add(noConnectionsLabel);
        }
    }
    
    // Check if node is related to connection
    private boolean isNodeConnectedToLink(org.example.demo2.Node node, Link link) {
        // ✅ FIX: Check if the node's IP is in the source or target IP list of the connection
        // Need to check for null to avoid NullPointerException
        
        // First check link.source and link.target (primary IPs)
        if (node.ip != null && !node.ip.isEmpty()) {
            if (node.ip.equals(link.source) || node.ip.equals(link.target)) {
                return true;
            }
        }
        
        // Check node.ips against link.sourceIps and link.targetIps
        if (node.ips != null && !node.ips.isEmpty()) {
            for (String ip : node.ips) {
                // Check against link primary IPs
                if (ip.equals(link.source) || ip.equals(link.target)) {
                    return true;
                }
                // Check against link IP lists (if they exist)
                if (link.sourceIps != null && link.sourceIps.contains(ip)) {
                    return true;
                }
                if (link.targetIps != null && link.targetIps.contains(ip)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    // Create connection information display box
    private VBox createConnectionInfoBox(org.example.demo2.Node node, Link link) {
        VBox connectionBox = new VBox();
        connectionBox.setSpacing(8);
        connectionBox.setStyle("-fx-background-color: white; -fx-padding: 10; -fx-border-color: #dee2e6; -fx-border-width: 1; -fx-border-radius: 3;");
        
        // Get node name (simplified display)
        String currentNodeName = getNodeDisplayName(node);
        String otherNodeName = getNodeDisplayName(link.source.equals(node.name) ? link.target : link.source);
        
        // If otherNodeName is an IP address, try to find the corresponding node name from the topology canvas
        if (isIpAddress(otherNodeName)) {
            otherNodeName = findNodeNameByIp(otherNodeName);
        }
        
        // Get port information
        int srcPort = link.srcInterface;
        int dstPort = link.dstInterface;
        
        // Determine if current node is source or target
        boolean isSource = isNodeSourceInLink(node, link);
        
        // Create connection description label
        String connectionText;
        if (isSource) {
            // Current node is source, display src name(src port) -> dst name
            connectionText = String.format("%s(port:%d) -> %s", 
                currentNodeName, 
                srcPort != 0 ? srcPort : 0, 
                otherNodeName);
        } else {
            // Current node is target, display dst name(dst port) <- src name
            connectionText = String.format("%s(port:%d) <- %s", 
                currentNodeName, 
                dstPort != 0 ? dstPort : 0, 
                otherNodeName);
        }
        
        // Create connection label
        Label connectionLabel = new Label(connectionText);
        connectionLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        connectionLabel.setStyle("-fx-text-fill: #2c3e50;");
        
        // Create detailed information (IP address)
        String srcIp, dstIp;
        
        // Handle different Link data structures (real-time vs playback)
        if (link.sourceIps != null && !link.sourceIps.isEmpty() && 
            link.targetIps != null && !link.targetIps.isEmpty()) {
            // Real-time mode: use sourceIps and targetIps lists
            srcIp = convertLittleEndianIpStringToStandard(link.sourceIps.get(0));
            dstIp = convertLittleEndianIpStringToStandard(link.targetIps.get(0));
        } else if (link.source != null && link.target != null) {
            // Playback mode: use source and target strings
            srcIp = link.source;
            dstIp = link.target;
        } else {
            // Fallback
            srcIp = "N/A";
            dstIp = "N/A";
        }
        
        Label ipLabel = new Label(String.format("IP: %s -> %s", srcIp, dstIp));
        ipLabel.setFont(Font.font("System", 10));
        ipLabel.setStyle("-fx-text-fill: #7f8c8d;");
        
        // Add components to connection box
        connectionBox.getChildren().addAll(connectionLabel, ipLabel);
        
        return connectionBox;
    }
    
    // Get node display name (simplified display)
    private String getNodeDisplayName(org.example.demo2.Node node) {
        if (node.name != null && !node.name.isEmpty()) {
            // If name contains IP, only take the name part
            String name = node.name;
            if (name.contains("-")) {
                name = name.substring(0, name.lastIndexOf("-"));
            }
            return name;
        }
        return "Unknown";
    }
    
    // Get node display name (from name string)
    private String getNodeDisplayName(String nodeName) {
        if (nodeName != null && !nodeName.isEmpty()) {
            // If name contains IP, only take the name part
            if (nodeName.contains("-")) {
                return nodeName.substring(0, nodeName.lastIndexOf("-"));
            }
            return nodeName;
        }
        return "Unknown";
    }
    
    // Check if string is an IP address
    private boolean isIpAddress(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        // Simple IP address check: contains dots and is not pure numbers
        return str.contains(".") && !str.matches("^\\d+$");
    }
    
    // Find node name by IP address
    private String findNodeNameByIp(String ip) {
        if (topologyCanvas != null) {
            List<org.example.demo2.Node> nodes = topologyCanvas.getNodes();
            if (nodes != null) {
                for (org.example.demo2.Node node : nodes) {
                    // Check node's IP list
                    if (node.ips != null && !node.ips.isEmpty()) {
                        for (String nodeIp : node.ips) {
                            String standardNodeIp = convertLittleEndianIpStringToStandard(nodeIp);
                            if (standardNodeIp.equals(ip)) {
                                return getNodeDisplayName(node.name);
                            }
                        }
                    }
                    // Check node's main IP
                    if (node.ip != null && !node.ip.isEmpty()) {
                        String standardNodeIp = convertLittleEndianIpStringToStandard(node.ip);
                        if (standardNodeIp.equals(ip)) {
                            return getNodeDisplayName(node.name);
                        }
                    }
                }
            }
        }
        // If not found, return the IP address itself
        return ip;
    }
    
    // Determine if node is source in connection
    private boolean isNodeSourceInLink(org.example.demo2.Node node, Link link) {
        // ✅ FIX: Check if node is the source of the link
        // Need to check for null to avoid NullPointerException
        
        // First check link.source (primary IP)
        if (node.ip != null && !node.ip.isEmpty()) {
            if (node.ip.equals(link.source)) {
                return true;
            }
        }
        
        // Check node.ips against link.source and link.sourceIps
        if (node.ips != null && !node.ips.isEmpty()) {
            for (String ip : node.ips) {
                // Check against link primary source IP
                if (ip.equals(link.source)) {
                    return true;
                }
                // Check against link source IP list (if it exists)
                if (link.sourceIps != null && link.sourceIps.contains(ip)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    // Helper method to create information rows
    private HBox createInfoRow(String label, String value, boolean editable) {
        return createInfoRow(label, value, editable, null);
    }
    
    private HBox createInfoRow(String label, String value, boolean editable, String valueColor) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.setSpacing(15);
        row.setPadding(new Insets(5.0, 0.0, 5.0, 0.0));
        
        Label labelNode = new Label(label);
        labelNode.setFont(Font.font("System", FontWeight.NORMAL, 12));
        labelNode.setStyle("-fx-text-fill: #7f8c8d; -fx-min-width: 120;");
        
        Label valueNode = new Label(value);
        valueNode.setFont(Font.font("System", FontWeight.NORMAL, 12));
        if (valueColor != null) {
            valueNode.setStyle("-fx-text-fill: " + valueColor + "; -fx-font-weight: bold;");
        } else {
            valueNode.setStyle("-fx-text-fill: #2c3e50; -fx-font-weight: bold;");
        }
        
        HBox valueBox = new HBox();
        valueBox.setAlignment(Pos.CENTER_LEFT);
        valueBox.setSpacing(8);
        valueBox.getChildren().add(valueNode);
        
        if (editable) {
            Button editButton = new Button("✎");
            editButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #95a5a6; -fx-font-size: 12; -fx-padding: 2;");
            editButton.setMinSize(20, 20);
            valueBox.getChildren().add(editButton);
        }
        
        HBox.setHgrow(valueBox, Priority.ALWAYS);
        row.getChildren().addAll(labelNode, valueBox);
        
        return row;
    }


            // Flow Only mode: Display all flow information (table format)
    public void showFlowOnlyInfo(List<TopologyCanvas.FlowWithDirection> flowsWithDirection) {
        System.out.println("[DEBUG] showFlowOnlyInfo called with " + (flowsWithDirection != null ? flowsWithDirection.size() : "null") + " flows");
        System.out.println("[DEBUG] showFlowOnlyInfo called, flowsWithDirection=" + (flowsWithDirection != null ? flowsWithDirection.size() : "null"));
        
        // If dialog is null or closed, create a new one
        if (dialog == null || !dialog.isShowing()) {
            dialog = new Stage();
                          dialog.initModality(Modality.NONE); // Change to non-modal dialog
            dialog.setTitle("Flow Information - Flow Only Mode (Live)");
            dialog.setMinWidth(1050);
        dialog.setMinHeight(600);
        } else {
            // If dialog already exists and is showing, clear content and reuse
            dialog.setTitle("Flow Information - Flow Only Mode (Live)");
        }

        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: #f9f9f9;");

        Label title = new Label("Active Flows on Selected Links:");
        title.setFont(Font.font("Monospaced", FontWeight.BOLD, 18));
        root.getChildren().add(title);

        // Add LIVE indicator
        Label liveLabel = new Label("LIVE");
        liveLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        liveLabel.setStyle("-fx-text-fill: #ff4444; -fx-background-color: #ffeeee; -fx-padding: 2 6; -fx-border-color: #ff4444; -fx-border-width: 1; -fx-border-radius: 3;");
        
        // Create animation effect for LIVE indicator
        Timeline liveAnimation = new Timeline(
            new KeyFrame(Duration.ZERO, e -> liveLabel.setOpacity(1.0)),
            new KeyFrame(Duration.seconds(0.5), e -> liveLabel.setOpacity(0.3)),
            new KeyFrame(Duration.seconds(1.0), e -> liveLabel.setOpacity(1.0))
        );
        liveAnimation.setCycleCount(Timeline.INDEFINITE);
        liveAnimation.play();
        
        // TableView
        TableView<FlowTableItem> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefWidth(1000);
        
        // Flow color column
        TableColumn<FlowTableItem, FlowTableItem> flowCol = new TableColumn<>("Flow");
        flowCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue()));
        flowCol.setCellFactory(column -> new TableCell<FlowTableItem, FlowTableItem>() {
            @Override
            protected void updateItem(FlowTableItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.flow == null) {
                    setGraphic(null);
                } else {
                    // 使用 TopologyCanvas 的統一顏色邏輯（hash five-tuple + Color specification）
                    Color flowColor = topologyCanvas.getColorForFlow(item.flow);
                    javafx.scene.shape.Rectangle colorRectangle = new javafx.scene.shape.Rectangle(16, 12, flowColor);
                    colorRectangle.setStroke(Color.BLACK);
                    colorRectangle.setStrokeWidth(1);
                    setGraphic(colorRectangle);
                }
            }
        });
        flowCol.setPrefWidth(80);
        flowCol.setMinWidth(60);
        flowCol.setResizable(true);
        flowCol.setVisible(true);

        TableColumn<FlowTableItem, String> srcIpCol = new TableColumn<>("Src IP");
        srcIpCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().convertedSrcIp));
        srcIpCol.setPrefWidth(130);

        TableColumn<FlowTableItem, String> dstIpCol = new TableColumn<>("Dst IP");
        dstIpCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().convertedDstIp));
        dstIpCol.setPrefWidth(130);

        TableColumn<FlowTableItem, Number> srcPortCol = new TableColumn<>("Src Port");
        srcPortCol.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().flow.srcPort));
        srcPortCol.setPrefWidth(85);

        TableColumn<FlowTableItem, Number> dstPortCol = new TableColumn<>("Dst Port");
        dstPortCol.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().flow.dstPort));
        dstPortCol.setPrefWidth(85);

        TableColumn<FlowTableItem, Number> protoCol = new TableColumn<>("Protocol");
        protoCol.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().flow.protocolId));
        protoCol.setPrefWidth(85);

        TableColumn<FlowTableItem, String> sendingRateCol = new TableColumn<>("Sending Rate");
        sendingRateCol.setCellValueFactory(cellData -> new SimpleStringProperty(formatRate(cellData.getValue().flow.estimatedFlowSendingRateBpsInTheLastSec)));
        sendingRateCol.setPrefWidth(130);

        TableColumn<FlowTableItem, String> startTimeCol = new TableColumn<>("First Sample Time");
        startTimeCol.setCellValueFactory(cellData -> new SimpleStringProperty(convertMsToTimeFormat(cellData.getValue().flow.startTimeMs)));
        startTimeCol.setPrefWidth(130);

        TableColumn<FlowTableItem, String> endTimeCol = new TableColumn<>("Latest Sample Time");
        endTimeCol.setCellValueFactory(cellData -> new SimpleStringProperty(convertMsToTimeFormat(cellData.getValue().flow.endTimeMs)));
        endTimeCol.setPrefWidth(130);

        table.getColumns().addAll(flowCol, srcIpCol, dstIpCol, srcPortCol, dstPortCol, protoCol, sendingRateCol, startTimeCol, endTimeCol);
        
        // Enable sorting for all columns
        flowCol.setSortable(true);
        srcIpCol.setSortable(true);
        dstIpCol.setSortable(true);
        srcPortCol.setSortable(true);
        dstPortCol.setSortable(true);
        protoCol.setSortable(true);
        sendingRateCol.setSortable(true);
        startTimeCol.setSortable(true);
        endTimeCol.setSortable(true);
        
        // Debug info: Confirm columns are added
        System.out.println("[DEBUG] Flow Information Table Columns:");
        for (int i = 0; i < table.getColumns().size(); i++) {
            System.out.println("[DEBUG] Column " + i + ": " + table.getColumns().get(i).getText());
        }
        table.setPrefHeight(400);

        // Button area
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        
        // Save original flowsWithDirection for real-time updates
        final List<TopologyCanvas.FlowWithDirection> originalFlowsWithDirection = new ArrayList<>(flowsWithDirection);
        
        // Add toggle state variable
        final boolean[] showAllFlows = {false};
        
        // All Flow Information button - now a toggle button
        Button toggleBtn = new Button("Show All Flows");
        toggleBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
        toggleBtn.setOnAction(e -> {
            showAllFlows[0] = !showAllFlows[0];
            if (showAllFlows[0]) {
                toggleBtn.setText("Show Selected Link Flows");
                updateAllFlowData(table, title);
            } else {
                toggleBtn.setText("Show All Flows");
                updateFlowData(originalFlowsWithDirection, table, title);
            }
        });
        
        // Close button
        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> dialog.close());
        closeBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        
        // ===== New: Port Table button =====
        Button portTableBtn = new Button("Port Table");
        portTableBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
        portTableBtn.setOnAction(e -> showPortTableDialog());
        buttonBox.getChildren().addAll(toggleBtn, portTableBtn, closeBtn);
        
        root.getChildren().addAll(liveLabel, table, buttonBox);

        // Create real-time update timer - update based on current mode
        Timeline updateTimer = new Timeline(
            new KeyFrame(Duration.seconds(1), e -> {
                if (showAllFlows[0]) {
                    updateAllFlowData(table, title);
                } else {
                    updateFlowData(originalFlowsWithDirection, table, title);
                }
            })
        );
        updateTimer.setCycleCount(Timeline.INDEFINITE);
        updateTimer.play();

                // Stop timer when dialog closes
        dialog.setOnCloseRequest(e -> {
            updateTimer.stop();
            liveAnimation.stop();
        });
        
        // Initial display - show flows on selected links
        updateFlowData(originalFlowsWithDirection, table, title);
        
        // If dialog already exists and is showing, only update content
        if (dialog.isShowing()) {
            // Get existing Scene and root
            Scene existingScene = dialog.getScene();
            if (existingScene != null) {
                VBox existingRoot = (VBox) existingScene.getRoot();
                // Clear existing content and add new content
                existingRoot.getChildren().clear();
                existingRoot.getChildren().addAll(title, liveLabel, table, buttonBox);
            }
        } else {
            // If dialog doesn't exist or is not showing, create new Scene
            Scene scene = new Scene(root, 1050, 600);
            dialog.setScene(scene);
            dialog.show();
        }
    }

    private void updateFlowData(List<TopologyCanvas.FlowWithDirection> flowsWithDirection, TableView<FlowTableItem> table, Label title) {
        System.out.println("[DEBUG] updateFlowData called, flowsWithDirection=" + (flowsWithDirection != null ? flowsWithDirection.size() : "null"));
            table.getItems().clear();
        if (flowsWithDirection == null || flowsWithDirection.isEmpty()) {
            title.setText("No Flows on Selected Links");
            return;
        }
        List<FlowTableItem> flowItems = new ArrayList<>();
        List<Link> currentLinks = topologyCanvas.getLinks();
        StringBuilder linkNames = new StringBuilder();
        for (TopologyCanvas.FlowWithDirection flowWithDir : flowsWithDirection) {
            String srcKey = flowWithDir.sourceIp;
            String dstKey = flowWithDir.targetIp;

            // 只顯示「這條帶方向的 flow」本身，避免把同一條 link 上其他 direction 或其他 flow 一起加進來
            for (Link l : currentLinks) {
                if (l.sourceIps == null || l.targetIps == null) continue;

                for (String lsrc : l.sourceIps) {
                    for (String ldst : l.targetIps) {
                        String lsrcStr = lsrc; // 已是標準 IP
                        String ldstStr = ldst;

                        // 僅當此 link 的端點與 flowWithDir 的 source/target 對上時，才視為這條 link
                        if ((lsrcStr.equals(srcKey) && ldstStr.equals(dstKey)) ||
                            (lsrcStr.equals(dstKey) && ldstStr.equals(srcKey))) {

                            if (linkNames.length() > 0) linkNames.append(", ");
                            linkNames.append(lsrcStr + " → " + ldstStr);

                            if (l.flow_set != null) {
                                for (Flow flowInSet : l.flow_set) {
                                    // 僅加入與這個 flowWithDir 對應的那一條 flow
                                    if (flowInSet == flowWithDir.flow) {
                                        Flow completeFlow = findCompleteFlowInfo(flowInSet);
                                        Flow displayFlow = (completeFlow != null) ? completeFlow : flowInSet;

                                        String showSrcIp = convertIpStringForDisplay(displayFlow.srcIp);
                                        String showDstIp = convertIpStringForDisplay(displayFlow.dstIp);

                                        flowItems.add(new FlowTableItem(displayFlow, flowWithDir.direction, showSrcIp, showDstIp));
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (linkNames.length() > 0) {
            if (flowItems.size() > 0) {
                title.setText("Active Flows on Selected Link [" + linkNames + "] (" + flowItems.size() + " flows):");
            } else {
                title.setText("No Flows on Selected Link [" + linkNames + "]");
            }
        } else {
            if (flowItems.size() > 0) {
                title.setText("Active Flows on Selected Link (" + flowItems.size() + " flows):");
            } else {
                title.setText("No Flows on Selected Link");
            }
        }
        table.getItems().addAll(flowItems);
        table.refresh();
    }

    private void updateAllFlowData(TableView<FlowTableItem> table, Label title) {
            table.getItems().clear();
        
        // 檢查是否有 flows 數據
        if (allFlows == null || allFlows.isEmpty()) {
            title.setText("No Flows Detected on the Network");
            return;
        }
        
            List<FlowTableItem> allFlowItems = allFlows.stream()
                .map(flow -> {
                    // 使用 pathNodes 來確定正確的方向
                    String direction;
                    if (flow.pathNodes != null && flow.pathNodes.size() >= 2) {
                        // 使用路徑的第一個和最後一個節點
                        String firstNodeIp = flow.pathNodes.getFirst();
                        String lastNodeIp = flow.pathNodes.getLast();
                        Node firstNode = topologyCanvas.getNodeByIp(firstNodeIp);
                        Node lastNode = topologyCanvas.getNodeByIp(lastNodeIp);
                        direction = (firstNode != null ? firstNode.name : firstNodeIp) + " → " + (lastNode != null ? lastNode.name : lastNodeIp);
                    } else {
                        // 如果沒有路徑信息，回退到使用 srcIp 和 dstIp
                        Node srcNode = topologyCanvas.getNodeByIp(flow.srcIp);
                        Node dstNode = topologyCanvas.getNodeByIp(flow.dstIp);
                        direction = (srcNode != null ? srcNode.name : flow.srcIp) + " → " + (dstNode != null ? dstNode.name : flow.dstIp);
                    }
                return new FlowTableItem(flow, direction, convertIpStringForDisplay(flow.srcIp), convertIpStringForDisplay(flow.dstIp));
                })
                .toList();
        
            table.getItems().addAll(allFlowItems);
        title.setText("All Flows Detected on the Network (" + allFlowItems.size() + " flows):");
        
        // 刷新表格
        table.refresh();
    }



    // Link Only模式：顯示link信息和使用率長條圖
    public void showLinkOnlyInfo(List<Link> clickedLinks) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.NONE);
        dialog.setTitle("Link Information");
        dialog.setWidth(800);
        dialog.setHeight(600);
        dialog.setResizable(false); // 固定大小，不能調整

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #f9f9f9;");

        // 方向選擇狀態
        final String[] selectedDirection = {"forward"}; // "forward", "backward"

        // 創建內容區域（使用 ScrollPane）
        VBox contentArea = new VBox(10);
        contentArea.setPadding(new Insets(10));
        
        // 使用 ScrollPane 包裝內容
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setContent(contentArea);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        // 創建按鈕區域
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(20, 0, 0, 0));

        // 獲取第一個 link 的節點名稱來設置按鈕文字
        String forwardButtonText = "";
        String backwardButtonText = "";
        if (!clickedLinks.isEmpty()) {
            Link firstLink = clickedLinks.get(0);
            // 從 TopologyCanvas 獲取最新的 link 數據來確保一致性
            List<Link> currentLinks = topologyCanvas.getLinks();
            Link currentFirstLink = currentLinks.stream()
                .filter(l -> l.source.equals(firstLink.source) && l.target.equals(firstLink.target))
                .findFirst()
                .orElse(firstLink);
            
            Node srcNode = topologyCanvas.getNodeByIp(currentFirstLink.source);
            Node tgtNode = topologyCanvas.getNodeByIp(currentFirstLink.target);
            String srcName = srcNode != null ? srcNode.name : convertIpStringForLinkInfoDisplay(currentFirstLink.source);
            String tgtName = tgtNode != null ? tgtNode.name : convertIpStringForLinkInfoDisplay(currentFirstLink.target);
            forwardButtonText = srcName + " → " + tgtName;
            backwardButtonText = tgtName + " → " + srcName;
        } else {
            forwardButtonText = "forward";
            backwardButtonText = "backward";
        }
        
        // 方向按鈕
        Button forwardButton = new Button(forwardButtonText);
        Button backwardButton = new Button(backwardButtonText);
        
        // 設置按鈕樣式
        String buttonStyle = "-fx-background-color: linear-gradient(to bottom, #4a90e2, #357abd); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12; -fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-padding: 8 16; -fx-background-radius: 6; -fx-border-radius: 6; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 3, 0, 0, 1);";
        String selectedButtonStyle = "-fx-background-color: linear-gradient(to bottom, #28a745, #1e7e34); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12; -fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-padding: 8 16; -fx-background-radius: 6; -fx-border-radius: 6; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 4, 0, 0, 2);";
        
        forwardButton.setStyle(selectedButtonStyle); // 預設選中
        backwardButton.setStyle(buttonStyle);
        
        // 按鈕事件
        forwardButton.setOnAction(e -> {
            selectedDirection[0] = "forward";
            forwardButton.setStyle(selectedButtonStyle);
            backwardButton.setStyle(buttonStyle);
            updateLinkData(clickedLinks, contentArea, selectedDirection[0]);
        });
        
        backwardButton.setOnAction(e -> {
            selectedDirection[0] = "backward";
            forwardButton.setStyle(buttonStyle);
            backwardButton.setStyle(selectedButtonStyle);
            updateLinkData(clickedLinks, contentArea, selectedDirection[0]);
        });

        // Close 按鈕
        Button closeButton = new Button("Close");
        closeButton.setStyle("-fx-background-color: linear-gradient(to bottom, #dc3545, #c82333); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12; -fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-padding: 8 16; -fx-background-radius: 6; -fx-border-radius: 6; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 3, 0, 0, 1);");
        closeButton.setOnAction(e -> dialog.close());

        buttonBox.getChildren().addAll(forwardButton, backwardButton, closeButton);

        // 創建實時更新定時器
        Timeline updateTimer = new Timeline(
            new KeyFrame(Duration.seconds(1), e -> updateLinkData(clickedLinks, contentArea, selectedDirection[0]))
        );
        updateTimer.setCycleCount(Timeline.INDEFINITE);
        updateTimer.play();

        // 當對話框關閉時停止定時器
        dialog.setOnCloseRequest(e -> updateTimer.stop());

        // 初始顯示
        updateLinkData(clickedLinks, contentArea, selectedDirection[0]);

        root.getChildren().addAll(scrollPane, buttonBox);
        
        Scene scene = new Scene(root);
        dialog.setScene(scene);
        dialog.show();
    }

    private void updateLinkData(List<Link> clickedLinks, VBox contentArea, String direction) {
        // 清除所有內容
        contentArea.getChildren().clear();

        // 從 TopologyCanvas 獲取最新的 link 數據
        List<Link> currentLinks = topologyCanvas.getLinks();

        for (Link originalLink : clickedLinks) {
            // 找到對應的最新 link 數據
            Link currentLink = currentLinks.stream()
                .filter(l -> l.source.equals(originalLink.source) && l.target.equals(originalLink.target))
                .findFirst()
                .orElse(originalLink); // 如果找不到，使用原始數據

            // 找到對應的反向 link 數據
            Link reverseLink = currentLinks.stream()
                .filter(l -> l.source.equals(originalLink.target) && l.target.equals(originalLink.source))
                .findFirst()
                .orElse(null);

            // 根據方向選擇顯示對應的節點名稱和link信息
            Node srcNode, tgtNode;
            String linkName;
            Link displayLink;
            
            if ("backward".equals(direction) && reverseLink != null) {
                // Backward方向：顯示反向link
                srcNode = topologyCanvas.getNodeByIp(reverseLink.source);
                tgtNode = topologyCanvas.getNodeByIp(reverseLink.target);
                linkName = (srcNode != null ? srcNode.name : convertIpStringForLinkInfoDisplay(reverseLink.source)) + " → " + (tgtNode != null ? tgtNode.name : convertIpStringForLinkInfoDisplay(reverseLink.target));
                displayLink = reverseLink;
            } else {
                // Forward方向或both：顯示原始link
                srcNode = topologyCanvas.getNodeByIp(currentLink.source);
                tgtNode = topologyCanvas.getNodeByIp(currentLink.target);
                linkName = (srcNode != null ? srcNode.name : convertIpStringForLinkInfoDisplay(currentLink.source)) + " → " + (tgtNode != null ? tgtNode.name : convertIpStringForLinkInfoDisplay(currentLink.target));
                displayLink = currentLink;
            }
            
            // Information 標題
            Label infoTitleLabel = new Label("Information");
            infoTitleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
            infoTitleLabel.setStyle("-fx-text-fill: #2c3e50; -fx-padding: 5;");
            contentArea.getChildren().add(infoTitleLabel);
            
            // Link基本信息
            Label linkNameLabel = new Label("Link: " + linkName);
            linkNameLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
            linkNameLabel.setStyle("-fx-text-fill: #2c3e50; -fx-background-color: #ecf0f1; -fx-padding: 5; -fx-border-color: #bdc3c7; -fx-border-width: 1;");
            contentArea.getChildren().add(linkNameLabel);
            
            // 根據方向選擇顯示對應的使用率
            double utilization = 0.0;
            String utilizationInfo = "";
            
            if ("forward".equals(direction)) {
                utilization = currentLink.link_bandwidth_utilization_percent;
                utilizationInfo = String.format("Forward Utilization: %.2f%%", utilization);
            } else if ("backward".equals(direction)) {
                if (reverseLink != null) {
                    utilization = reverseLink.link_bandwidth_utilization_percent;
                    utilizationInfo = String.format("Backward Utilization: %.2f%%", utilization);
                } else {
                    utilization = 0.0;
                    utilizationInfo = "Backward Utilization: No data available";
                }
            } else { // both
                if (reverseLink != null) {
                    double forwardUtil = currentLink.link_bandwidth_utilization_percent;
                    double backwardUtil = reverseLink.link_bandwidth_utilization_percent;
                    utilization = Math.max(forwardUtil, backwardUtil); // 顯示較高的使用率
                    utilizationInfo = String.format("Forward: %.2f%%, Backward: %.2f%%", forwardUtil, backwardUtil);
                } else {
                    utilization = currentLink.link_bandwidth_utilization_percent;
                    utilizationInfo = String.format("Forward: %.2f%%, Backward: No data", utilization);
                }
            }
            
            // 計算flow numbers
            int flowNumbers = 0;
            if ("forward".equals(direction)) {
                // Forward: count flows in current link
                flowNumbers = (currentLink.flow_set != null) ? currentLink.flow_set.size() : 0;
            } else if ("backward".equals(direction)) {
                // Backward: count flows in reverse link
                flowNumbers = (reverseLink != null && reverseLink.flow_set != null) ? reverseLink.flow_set.size() : 0;
            } else {
                // Both: count flows in both directions
                int forwardFlows = (currentLink.flow_set != null) ? currentLink.flow_set.size() : 0;
                int backwardFlows = (reverseLink != null && reverseLink.flow_set != null) ? reverseLink.flow_set.size() : 0;
                flowNumbers = forwardFlows + backwardFlows;
            }
            
            // Link詳細信息
            String linkInfo = "Source IP: " + convertIpStringForLinkInfoDisplay(displayLink.source) + "\n" +
                    "Target IP: " + convertIpStringForLinkInfoDisplay(displayLink.target) + "\n" +
                    "Bandwidth: " + displayLink.bandwidth + " bps\n" +
                    "Status: " + (displayLink.is_up ? "UP" : "DOWN") + "\n" +
                    "Enabled: " + (displayLink.is_enabled ? "YES" : "NO") + "\n" +
                    "Flow Numbers: " + flowNumbers + "\n" +
                    utilizationInfo;
            
            Label linkInfoLabel = new Label(linkInfo);
            linkInfoLabel.setFont(Font.font("Monospaced", 11));
            linkInfoLabel.setStyle("-fx-background-color: white; -fx-padding: 5; -fx-border-color: #cccccc; -fx-border-width: 1;");
            linkInfoLabel.setWrapText(true);
            contentArea.getChildren().add(linkInfoLabel);
            
                        // 水平堆疊長條圖
            NumberAxis xAxis = new NumberAxis(0, 100, 10);
            CategoryAxis yAxis = new CategoryAxis();
            xAxis.setLabel("Utilization (%)");
            
            BarChart<Number, String> barChart = new BarChart<>(xAxis, yAxis);
            barChart.setTitle("Bandwidth Utilization - " + linkName + " (Live)");
            barChart.setLegendVisible(true);
            barChart.setMinSize(300, 150);
            barChart.setPrefSize(400, 200);
            
            // 添加 LIVE 指示器
            Label liveLabel = new Label("LIVE");
            liveLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
            liveLabel.setStyle("-fx-text-fill: #ff4444; -fx-background-color: #ffeeee; -fx-padding: 2 6; -fx-border-color: #ff4444; -fx-border-width: 1; -fx-border-radius: 3;");
            
            // 創建 LIVE 指示器的動畫效果
            Timeline liveAnimation = new Timeline(
                new KeyFrame(Duration.ZERO, e -> liveLabel.setOpacity(1.0)),
                new KeyFrame(Duration.seconds(0.5), e -> liveLabel.setOpacity(0.3)),
                new KeyFrame(Duration.seconds(1.0), e -> liveLabel.setOpacity(1.0))
            );
            liveAnimation.setCycleCount(Timeline.INDEFINITE);
            liveAnimation.play();
            
            // 處理使用率數據：使用根據方向選擇計算的 utilization
            double unused = 100.0 - utilization;
            
            // 創建單一長條，包含兩個堆疊部分
            XYChart.Series<Number, String> series = new XYChart.Series<>();
            series.setName("Bandwidth");
            
            // 先添加未使用部分（作為基礎）
            XYChart.Data<Number, String> unusedData = new XYChart.Data<>(unused, "Bandwidth");
            series.getData().add(unusedData);
            
            // 再添加已使用部分（堆疊在上面）
            XYChart.Data<Number, String> usedData = new XYChart.Data<>(utilization, "Bandwidth");
            series.getData().add(usedData);
            
            barChart.getData().add(series);
            
            // 添加實際利用率信息
            Label actualUtilizationLabel = new Label(String.format("Actual Utilization: %.2f%%", utilization));
            actualUtilizationLabel.setFont(Font.font("Arial", 10));
            actualUtilizationLabel.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 3; -fx-border-color: #cccccc; -fx-border-width: 1;");
            
            VBox chartContainer = new VBox(5);
            chartContainer.getChildren().addAll(liveLabel, actualUtilizationLabel, barChart);
            chartContainer.setPadding(new Insets(10));
            chartContainer.setStyle("-fx-background-color: white; -fx-border-color: #cccccc; -fx-border-width: 1;");
            contentArea.getChildren().add(chartContainer);
            
            // 分隔線
            if (clickedLinks.size() > 1) {
                Separator separator = new Separator();
                separator.setPadding(new Insets(10, 0, 10, 0));
                contentArea.getChildren().add(separator);
            }
        }
    }



    // ===== 新增：Port Table 彈窗 =====
    private void showPortTableDialog() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Port Table");
        dialog.setMinWidth(400);
        dialog.setMinHeight(600);
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: #f9f9f9;");
        TableView<Map<String, String>> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        // 讀取porttable.json
        List<Map<String, String>> portRows = new ArrayList<>();
        try (java.io.FileReader reader = new java.io.FileReader("porttable.json")) {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            com.google.gson.reflect.TypeToken<List<Map<String, String>>> typeToken =
                (com.google.gson.reflect.TypeToken<List<Map<String, String>>>) 
                com.google.gson.reflect.TypeToken.getParameterized(List.class, Map.class);
            portRows = gson.fromJson(reader, typeToken.getType());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        // 動態產生欄位
        if (!portRows.isEmpty()) {
            for (String key : portRows.getFirst().keySet()) {
                if (key.equals("port")) {
                    // Port column should be integer
                    TableColumn<Map<String, String>, Number> col = new TableColumn<>(key);
                    col.setCellValueFactory(data -> {
                        String value = data.getValue().get(key);
                        try {
                            return new SimpleIntegerProperty(Integer.parseInt(value));
                        } catch (NumberFormatException e) {
                            return new SimpleIntegerProperty(0);
                        }
                    });
                    col.setSortable(true);
                    table.getColumns().add(col);
                } else {
                    // Other columns remain as string
                    TableColumn<Map<String, String>, String> col = new TableColumn<>(key);
                    col.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().get(key)));
                    col.setSortable(true);
                    
                    // 設置欄位寬度和文字換行
                    if (key.equals("description") || key.equals("protocol")) {
                        // 對於 description 和 protocol 欄位，設置較寬的寬度並允許文字換行
                        col.setPrefWidth(150);
                        col.setMinWidth(120);
                        col.setMaxWidth(200);
                        
                        // 設置文字換行
                        col.setCellFactory(tc -> new TableCell<Map<String, String>, String>() {
                            @Override
                            protected void updateItem(String item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty || item == null) {
                                    setText(null);
                                    setGraphic(null);
                                } else {
                                    setText(item);
                                    setWrapText(true);
                                    setStyle("-fx-alignment: CENTER_LEFT; -fx-padding: 5px;");
                                }
                            }
                        });
                    } else {
                        // 其他欄位使用默認設置
                        col.setPrefWidth(80);
                        col.setMinWidth(60);
                    }
                    
                    table.getColumns().add(col);
                }
            }
        }
        table.getItems().addAll(portRows);
        root.getChildren().add(table);
        Button closeBtn = new Button("Close");
        closeBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        closeBtn.setOnAction(e -> dialog.close());
        HBox btnBox = new HBox(closeBtn);
        btnBox.setAlignment(Pos.CENTER_RIGHT);
        btnBox.setPadding(new Insets(10, 0, 0, 0));
        root.getChildren().add(btnBox);
        Scene scene = new Scene(root, 450, 600);
        dialog.setScene(scene);
        dialog.show();
    }

    public void showFlowSetInfo(List<Link> clickedLinks) {
        System.out.println("[TEMP] showFlowSetInfo called with " + clickedLinks.size() + " links");
        if (clickedLinks.isEmpty()) return;
        
        // 檢查是否為 playback 模式
        boolean isPlaybackMode = topologyCanvas != null && topologyCanvas.isPlaybackMode();
        String dialogTitle = isPlaybackMode ? "Flow Information (Playback)" : "Flow Information (Live)";
        
        // 如果 dialog 不存在或未顯示，則創建新的
        if (dialog == null || !dialog.isShowing()) {
            if (dialog == null) {
                dialog = new Stage();
            }
            dialog.initModality(Modality.NONE);
            dialog.setTitle(dialogTitle);
            dialog.setWidth(1050);
            dialog.setHeight(800);
            
            VBox root = new VBox(15);
            root.setPadding(new Insets(20, 25, 25, 25));
            root.setStyle("-fx-background-color: linear-gradient(to bottom, #f8f9fa, #ffffff); -fx-border-color: #e9ecef; -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8;");
            
            createFlowSetInfoContent(clickedLinks, root, isPlaybackMode);
            
            Scene scene = new Scene(root, 1050, 800);
            dialog.setScene(scene);
            dialog.show();
        } else {
            // 更新對話框標題
            dialog.setTitle(dialogTitle);
            // 如果 dialog 已經存在且正在顯示，則只更新數據而不重新創建表格
            // 這樣可以保持排序狀態
            updateFlowSetInfoData(clickedLinks);
        }
    }
    
    private void updateFlowSetInfoData(List<Link> clickedLinks) {
        // 只更新表格數據，不重新創建表格，保持排序狀態
        if (dialog != null && dialog.isShowing()) {
            Scene scene = dialog.getScene();
            if (scene != null) {
                // 找到表格和標題標籤並更新數據
                VBox root = (VBox) scene.getRoot();
                Label titleLabel = null;
                TableView<FlowTableItem> table = null;
                
                // 查找 headerContainer 中的 titleLabel
                for (javafx.scene.Node node : root.getChildren()) {
                    if (node instanceof VBox) {
                        VBox headerContainer = (VBox) node;
                        // 檢查是否是 headerContainer（包含 titleLabel）
                        for (javafx.scene.Node headerNode : headerContainer.getChildren()) {
                            if (headerNode instanceof HBox) {
                                // 跳過 HBox（包含 LIVE 指示器和方向按鈕）
                                continue;
                            } else if (headerNode instanceof Label) {
                                titleLabel = (Label) headerNode;
                            }
                        }
                    }
                }
                
                // 查找表格
                for (javafx.scene.Node node : root.getChildren()) {
                    if (node instanceof VBox) {
                        VBox contentArea = (VBox) node;
                        for (javafx.scene.Node contentNode : contentArea.getChildren()) {
                            if (contentNode instanceof TableView) {
                                @SuppressWarnings("unchecked")
                                TableView<FlowTableItem> foundTable = (TableView<FlowTableItem>) contentNode;
                                table = foundTable;
                                break;
                            }
                        }
                    }
                }
                
                if (table != null) {
                    // 更新表格數據
                    updateFlowSetTableData(clickedLinks, table);
                    
                    // 更新標題中的 flow 數量（保留原有標題格式，只更新 flow 數量）
                    if (titleLabel != null) {
                        int flowCount = table.getItems().size();
                        String currentText = titleLabel.getText();
                        
                        // 如果標題中已經有 flow 數量，則替換它；否則添加 flow 數量
                        if (currentText.contains(" flows)")) {
                            // 找到最後一個 "(" 的位置，替換 flow 數量
                            int lastOpenParen = currentText.lastIndexOf(" (");
                            if (lastOpenParen > 0) {
                                String baseText = currentText.substring(0, lastOpenParen);
                                titleLabel.setText(baseText + " (" + flowCount + " flows)");
                            } else {
                                titleLabel.setText(currentText.replaceAll("\\(\\d+ flows\\)", "(" + flowCount + " flows)"));
                            }
                        } else {
                            // 如果標題中沒有 flow 數量，則添加
                            titleLabel.setText(currentText + " (" + flowCount + " flows)");
                        }
                    }
                }
            }
        }
    }
    
    private void updateFlowSetTableData(List<Link> clickedLinks, TableView<FlowTableItem> table) {
        // 保存當前的排序狀態
        List<TableColumn<FlowTableItem, ?>> sortOrder = new ArrayList<>(table.getSortOrder());
        
        // 清空並重新填充數據
        table.getItems().clear();
        
        // 收集所有經過選中links的flows
        List<FlowTableItem> flowItems = new ArrayList<>();
        for (Link link : clickedLinks) {
            if (link.flow_set != null) {
                for (Flow flowInSet : link.flow_set) {
                    Flow completeFlow = findCompleteFlowInfo(flowInSet);
                    if (completeFlow != null) {
                        // 使用反轉字節順序顯示
                        String convertedSrcIp = convertIpStringForDisplay(completeFlow.srcIp);
                        String convertedDstIp = convertIpStringForDisplay(completeFlow.dstIp);
                        flowItems.add(new FlowTableItem(completeFlow, "→", convertedSrcIp, convertedDstIp));
                    }
                }
            }
        }
        
        table.getItems().addAll(flowItems);
        
        // 恢復排序狀態
        table.getSortOrder().clear();
        table.getSortOrder().addAll(sortOrder);
    }
    
    private void createFlowSetInfoContent(List<Link> clickedLinks, VBox root, boolean isPlaybackMode) {
        // Header container with gradient background
        VBox headerContainer = new VBox(8);
        headerContainer.setPadding(new Insets(15, 20, 15, 20));
        headerContainer.setStyle("-fx-background-color: linear-gradient(to right, #4a90e2, #357abd); -fx-background-radius: 6; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 4, 0, 0, 2);");
        
        // Mode indicator with enhanced styling - only show LIVE for real-time mode
        HBox liveContainer = new HBox(8);
        liveContainer.setAlignment(Pos.CENTER_LEFT);
        
        if (!isPlaybackMode) {
            // Real-time mode: show LIVE indicator
            Circle liveDot = new Circle(4, Color.RED);
            liveDot.setStyle("-fx-effect: dropshadow(gaussian, rgba(255,0,0,0.6), 3, 0, 0, 0);");
            
            Label liveLabel = new Label("LIVE");
            liveLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12; -fx-font-family: 'Segoe UI', Arial, sans-serif;");
            
            liveContainer.getChildren().addAll(liveDot, liveLabel);
        } else {
            // Playback mode: show PLAYBACK indicator
            Circle playbackDot = new Circle(4, Color.ORANGE);
            playbackDot.setStyle("-fx-effect: dropshadow(gaussian, rgba(255,165,0,0.6), 3, 0, 0, 0);");
            
            Label playbackLabel = new Label("PLAYBACK");
            playbackLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12; -fx-font-family: 'Segoe UI', Arial, sans-serif;");
            
            liveContainer.getChildren().addAll(playbackDot, playbackLabel);
        }
        
        // Create fade animation only for real-time mode LIVE indicator
        if (!isPlaybackMode && liveContainer.getChildren().size() > 0) {
            Circle dotToAnimate = (Circle) liveContainer.getChildren().get(0);
            FadeTransition fadeTransition = new FadeTransition(Duration.seconds(1), dotToAnimate);
            fadeTransition.setFromValue(1.0);
            fadeTransition.setToValue(0.3);
            fadeTransition.setCycleCount(Animation.INDEFINITE);
            fadeTransition.setAutoReverse(true);
            fadeTransition.play();
        }
        
        // Create content area that can be switched
        VBox contentArea = new VBox(15);
        contentArea.setPadding(new Insets(0, 5, 0, 5));
        
        // Track current display mode
        final String[] currentMode = {"selected_link"}; // "all_flows", "selected_link", "port_table"
        
        // Track current direction filter
        final String[] currentDirection = {"both"}; // "both", "forward", "backward"
        
        // Create table for flow data with enhanced styling
        TableView<FlowTableItem> table = new TableView<>();
        table.setPrefHeight(550);
        table.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 2, 0, 0, 1);");
        table.setFixedCellSize(35); // Consistent row height
        
        // Create table columns
        TableColumn<FlowTableItem, FlowTableItem> flowCol = new TableColumn<>("Flow");
        flowCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue()));
        flowCol.setCellFactory(column -> new TableCell<FlowTableItem, FlowTableItem>() {
            @Override
            protected void updateItem(FlowTableItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.flow == null) {
                    setGraphic(null);
                } else {
                    // 使用 TopologyCanvas 的統一顏色邏輯（hash five-tuple + Color specification）
                    Color flowColor = topologyCanvas.getColorForFlow(item.flow);
                    javafx.scene.shape.Rectangle colorRectangle = new javafx.scene.shape.Rectangle(16, 12, flowColor);
                    colorRectangle.setStroke(Color.BLACK);
                    colorRectangle.setStrokeWidth(1);
                    setGraphic(colorRectangle);
                }
            }
        });
        flowCol.setPrefWidth(80);
        flowCol.setMinWidth(60);
        flowCol.setResizable(true);
        flowCol.setVisible(true);
        
        TableColumn<FlowTableItem, String> srcIpCol = new TableColumn<>("Src IP");
        TableColumn<FlowTableItem, String> dstIpCol = new TableColumn<>("Dst IP");
        TableColumn<FlowTableItem, String> srcPortCol = new TableColumn<>("Src Port");
        TableColumn<FlowTableItem, String> dstPortCol = new TableColumn<>("Dst Port");
        TableColumn<FlowTableItem, String> protocolCol = new TableColumn<>("Protocol");
        TableColumn<FlowTableItem, String> sendingRateCol = new TableColumn<>("Sending Rate");
        TableColumn<FlowTableItem, String> startTimeCol = new TableColumn<>("First Sample Time");
        TableColumn<FlowTableItem, String> endTimeCol = new TableColumn<>("Latest Sample Time");
        
        // Set column widths and styling
        srcIpCol.setPrefWidth(130);
        dstIpCol.setPrefWidth(130);
        srcPortCol.setPrefWidth(85);
        dstPortCol.setPrefWidth(85);
        protocolCol.setPrefWidth(85);
        sendingRateCol.setPrefWidth(130);
        startTimeCol.setPrefWidth(130);
        endTimeCol.setPrefWidth(130);
        
        // Apply consistent styling to all columns with center alignment
        srcIpCol.setStyle("-fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-font-size: 11; -fx-alignment: center;");
        dstIpCol.setStyle("-fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-font-size: 11; -fx-alignment: center;");
        srcPortCol.setStyle("-fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-font-size: 11; -fx-alignment: center;");
        dstPortCol.setStyle("-fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-font-size: 11; -fx-alignment: center;");
        protocolCol.setStyle("-fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-font-size: 11; -fx-alignment: center;");
        sendingRateCol.setStyle("-fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-font-size: 11; -fx-alignment: center;");
        startTimeCol.setStyle("-fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-font-size: 11; -fx-alignment: center;");
        endTimeCol.setStyle("-fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-font-size: 11; -fx-alignment: center;");
        
        // Set cell value factories
        srcIpCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().convertedSrcIp));
        dstIpCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().convertedDstIp));
        srcPortCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().srcPort)));
        dstPortCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().dstPort)));
        protocolCol.setCellValueFactory(data -> new SimpleStringProperty(convertProtocolNumberToText(data.getValue().protocol)));
        startTimeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().startTime));
        endTimeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().endTime));
        sendingRateCol.setCellValueFactory(data -> new SimpleStringProperty(formatRate(data.getValue().sendingRate)));
        
        table.getColumns().addAll(flowCol, srcIpCol, dstIpCol, srcPortCol, dstPortCol, protocolCol, sendingRateCol, startTimeCol, endTimeCol);
        
        // Create port table using Map<String, String> like in showPortTableDialog
        TableView<Map<String, Object>> portTable = new TableView<>();
        portTable.setPrefHeight(600);
        
        // Create protocol table using Map<String, Object>
        TableView<Map<String, Object>> protocolTable = new TableView<>();
        protocolTable.setPrefHeight(600);
        
        // Set table styling with center alignment
        table.setStyle("-fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-font-size: 11; -fx-alignment: center;");
        
        // Initially show flow table
        contentArea.getChildren().add(table);
        
        // Create title label that can be updated
        Label titleLabel = new Label();
        titleLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: white; -fx-font-family: 'Segoe UI', Arial, sans-serif;");
        titleLabel.setAlignment(Pos.CENTER_LEFT);
        
        // Function to update flow table（依 pathNodes 嚴格判斷方向，並從 allFlows 中過濾）
        Runnable refreshFlowTable = () -> {
            table.getItems().clear();
            List<FlowTableItem> flowItems = new ArrayList<>();
            
            System.out.println("[TEMP] Current direction: " + currentDirection[0]);
            
            if (allFlows == null || allFlows.isEmpty() || clickedLinks.isEmpty()) {
                titleLabel.setText("Link Flows information [N/A] (0 flows)");
                return;
            }

            // 以第一條被點選的 link 作為基準（IP 方向由這條 link 決定）
            Link baseLink = clickedLinks.get(0);
            String linkSrcIp;
            String linkDstIp;

            // Real-time 模式：使用 sourceIps / targetIps；Playback：使用 source / target
            if (baseLink.sourceIps != null && !baseLink.sourceIps.isEmpty() &&
                baseLink.targetIps != null && !baseLink.targetIps.isEmpty()) {
                linkSrcIp = topologyCanvas.convertNodeIdToIp(baseLink.sourceIps.get(0));
                linkDstIp = topologyCanvas.convertNodeIdToIp(baseLink.targetIps.get(0));
            } else {
                linkSrcIp = baseLink.source;
                linkDstIp = baseLink.target;
            }

            Node linkSrcNode = topologyCanvas.getNodeByIp(linkSrcIp);
            Node linkDstNode = topologyCanvas.getNodeByIp(linkDstIp);
            String linkSrcName = (linkSrcNode != null ? linkSrcNode.name : linkSrcIp);
            String linkDstName = (linkDstNode != null ? linkDstNode.name : linkDstIp);

            for (Flow flow : allFlows) {
                // 先取得完整 flow（若有 detected_flow 補充）
                Flow completeFlow = findCompleteFlowInfo(flow);
                Flow flowToUse = (completeFlow != null) ? completeFlow : flow;

                boolean passesForward  = isFlowInDirection(flowToUse, linkSrcIp, linkDstIp, linkSrcName, linkDstName);
                boolean passesBackward = isFlowInDirection(flowToUse, linkDstIp, linkSrcIp, linkDstName, linkSrcName);

                // 沒有經過這條 link，任何方向都不顯示
                if (!passesForward && !passesBackward) {
                    continue;
                }

                String displaySrcIp = convertIpStringForDisplay(flowToUse.srcIp);
                String displayDstIp = convertIpStringForDisplay(flowToUse.dstIp);

                switch (currentDirection[0]) {
                    case "forward":
                        if (passesForward) {
                            flowItems.add(new FlowTableItem(flowToUse, "Forward", displaySrcIp, displayDstIp));
                        }
                        break;
                    case "backward":
                        if (passesBackward) {
                            flowItems.add(new FlowTableItem(flowToUse, "Backward", displaySrcIp, displayDstIp));
                        }
                        break;
                    case "both":
                        // 在 both 模式下，同一條 flow 只在「實際方向」那一邊顯示一次，不重複
                        if (passesForward && !passesBackward) {
                            flowItems.add(new FlowTableItem(flowToUse, "Forward", displaySrcIp, displayDstIp));
                        } else if (passesBackward && !passesForward) {
                            flowItems.add(new FlowTableItem(flowToUse, "Backward", displaySrcIp, displayDstIp));
                        } else if (passesForward && passesBackward) {
                            // 理論上很少見；預設只當作 Forward 顯示一次，避免重複
                            flowItems.add(new FlowTableItem(flowToUse, "Forward", displaySrcIp, displayDstIp));
                        }
                        break;
                    default:
                        break;
                }
            }

            table.getItems().addAll(flowItems);
            
            // 計算 flow 數量
            int flowCount = flowItems.size();
            
            // Update title with link information and flow count（不再顯示 Forward/Backward 文案）
            if (!clickedLinks.isEmpty()) {
                Link firstLink = clickedLinks.get(0);
                String srcIp, dstIp;
                
                // Handle different Link data structures (real-time vs playback)
                if (firstLink.sourceIps != null && !firstLink.sourceIps.isEmpty() && 
                    firstLink.targetIps != null && !firstLink.targetIps.isEmpty()) {
                    // Real-time mode: use sourceIps and targetIps lists (使用反轉字節順序顯示)
                    srcIp = convertIpStringForDisplay(String.valueOf(firstLink.sourceIps.get(0)));
                    dstIp = convertIpStringForDisplay(String.valueOf(firstLink.targetIps.get(0)));
                } else if (firstLink.source != null && firstLink.target != null) {
                    // Playback mode: use source and target strings (使用反轉字節順序顯示)
                    srcIp = convertIpStringForDisplay(firstLink.source);
                    dstIp = convertIpStringForDisplay(firstLink.target);
                } else {
                    // Fallback
                    srcIp = "Unknown";
                    dstIp = "Unknown";
                }
                
                String flowCountText = " (" + flowCount + " flows)";
                // 只顯示 link 兩端 IP 與 flow 數量，不再加上 Forward/Backward 的括號文字
                titleLabel.setText("Link Flows information [" + srcIp + " ↔ " + dstIp + "]" + flowCountText);
            }
        };
        
        // Function to update all flows table
        Runnable refreshAllFlowsTable = () -> {
            table.getItems().clear();
            List<FlowTableItem> flowItems = new ArrayList<>();
            
            for (Flow flow : allFlows) {
                // 使用反轉字節順序顯示
                String srcIp = convertIpStringForDisplay(flow.srcIp);
                String dstIp = convertIpStringForDisplay(flow.dstIp);
                
                flowItems.add(new FlowTableItem(
                    flow, "All Flows", srcIp, dstIp
                ));
            }
            
            table.getItems().addAll(flowItems);
            // 顯示全部 flow 數量
            int totalFlowCount = flowItems.size();
            titleLabel.setText("All Flow Detected on the Network (" + totalFlowCount + " flows)");
        };
        
        // Function to update port table
        Runnable refreshPortTable = () -> {
            portTable.getItems().clear();
            List<Map<String, Object>> portRows = new ArrayList<>();
            try (java.io.FileReader reader = new java.io.FileReader("porttable.json")) {
                com.google.gson.Gson gson = new com.google.gson.Gson();
                com.google.gson.reflect.TypeToken<List<Map<String, Object>>> typeToken =
                    (com.google.gson.reflect.TypeToken<List<Map<String, Object>>>) 
                    com.google.gson.reflect.TypeToken.getParameterized(List.class, Map.class);
                portRows = gson.fromJson(reader, typeToken.getType());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            
            // Create columns dynamically
            portTable.getColumns().clear();
            if (!portRows.isEmpty()) {
                for (String key : portRows.getFirst().keySet()) {
                    if (key.equals("port")) {
                        // Port column should be integer
                        TableColumn<Map<String, Object>, Number> col = new TableColumn<>(key);
                        col.setCellValueFactory(data -> {
                            Object value = data.getValue().get(key);
                            if (value instanceof Number) {
                                return new SimpleIntegerProperty(((Number) value).intValue());
                            } else if (value instanceof String) {
                                try {
                                    return new SimpleIntegerProperty(Integer.parseInt((String) value));
                                } catch (NumberFormatException e) {
                                    return new SimpleIntegerProperty(0);
                                }
                            }
                            return new SimpleIntegerProperty(0);
                        });
                        portTable.getColumns().add(col);
                    } else {
                        // Other columns remain as string
                        TableColumn<Map<String, Object>, String> col = new TableColumn<>(key);
                        col.setCellValueFactory(data -> {
                            Object value = data.getValue().get(key);
                            String stringValue = (value != null) ? value.toString() : "";
                            return new SimpleStringProperty(stringValue);
                        });
                        portTable.getColumns().add(col);
                    }
                }
            }
            
            portTable.getItems().addAll(portRows);
            titleLabel.setText("Port Table");
        };
        
        // Function to update protocol table
        Runnable refreshProtocolTable = () -> {
            protocolTable.getItems().clear();
            List<Map<String, Object>> protocolRows = new ArrayList<>();
            try (java.io.FileReader reader = new java.io.FileReader("protocoltable.json")) {
                com.google.gson.Gson gson = new com.google.gson.Gson();
                com.google.gson.reflect.TypeToken<List<Map<String, Object>>> typeToken =
                    (com.google.gson.reflect.TypeToken<List<Map<String, Object>>>) 
                    com.google.gson.reflect.TypeToken.getParameterized(List.class, Map.class);
                protocolRows = gson.fromJson(reader, typeToken.getType());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            
            // Create columns dynamically
            protocolTable.getColumns().clear();
            if (!protocolRows.isEmpty()) {
                for (String key : protocolRows.getFirst().keySet()) {
                    if (key.equals("Protocol Number")) {
                        // Protocol Number column should be integer
                        TableColumn<Map<String, Object>, Number> col = new TableColumn<>(key);
                        col.setCellValueFactory(data -> {
                            Object value = data.getValue().get(key);
                            if (value instanceof Number) {
                                return new SimpleIntegerProperty(((Number) value).intValue());
                            } else if (value instanceof String) {
                                try {
                                    return new SimpleIntegerProperty(Integer.parseInt((String) value));
                                } catch (NumberFormatException e) {
                                    return new SimpleIntegerProperty(0);
                                }
                            }
                            return new SimpleIntegerProperty(0);
                        });
                        protocolTable.getColumns().add(col);
                    } else {
                        // Other columns remain as string
                        TableColumn<Map<String, Object>, String> col = new TableColumn<>(key);
                        col.setCellValueFactory(data -> {
                            Object value = data.getValue().get(key);
                            String stringValue = (value != null) ? value.toString() : "";
                            return new SimpleStringProperty(stringValue);
                        });
                        protocolTable.getColumns().add(col);
                    }
                }
            }
            
            protocolTable.getItems().addAll(protocolRows);
            titleLabel.setText("Protocol Table");
        };
        

        
        // Set up real-time updates
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            if (contentArea.getChildren().contains(table)) {
                if (currentMode[0].equals("all_flows")) {
                    refreshAllFlowsTable.run();
                } else if (currentMode[0].equals("selected_link")) {
                    refreshFlowTable.run();
                }
            }
            // Port table doesn't need real-time updates
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
        
        // Initial load - show selected link flows by default
        refreshFlowTable.run();
        
        // Create buttons with enhanced styling
        HBox buttonBox = new HBox(12);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(20, 0, 0, 0));
        
        // Create direction filter buttons for top right (must be created before toggleFlowBtn)
        HBox directionButtonBox = new HBox(8);
        directionButtonBox.setAlignment(Pos.CENTER_RIGHT);
        
        // Get link display names for button labels using device names
        String forwardText = "Forward";
        String backwardText = "Backward";
        if (!clickedLinks.isEmpty()) {
            Link firstLink = clickedLinks.get(0);
            // Get device names instead of IP addresses
            Node srcNode = topologyCanvas.getNodeByIp(firstLink.source);
            Node dstNode = topologyCanvas.getNodeByIp(firstLink.target);
            
            String srcName = (srcNode != null) ? srcNode.name : firstLink.source;
            String dstName = (dstNode != null) ? dstNode.name : firstLink.target;
            
            forwardText = srcName + " → " + dstName;
            backwardText = dstName + " → " + srcName;
        }
        
        // Both directions button
        Button bothBtn = new Button("Both");
        bothBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #28a745, #1e7e34); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11; -fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-padding: 6 12; -fx-background-radius: 4; -fx-border-radius: 4;");
        
        // Forward direction button
        Button forwardBtn = new Button(forwardText);
        forwardBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #6c757d, #5a6268); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11; -fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-padding: 6 12; -fx-background-radius: 4; -fx-border-radius: 4;");
        
        // Backward direction button
        Button backwardBtn = new Button(backwardText);
        backwardBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #6c757d, #5a6268); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11; -fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-padding: 6 12; -fx-background-radius: 4; -fx-border-radius: 4;");
        
        directionButtonBox.getChildren().addAll(bothBtn, forwardBtn, backwardBtn);
        
        // Helper function to update button styles
        final Button[] bothBtnRef = {bothBtn};
        final Button[] forwardBtnRef = {forwardBtn};
        final Button[] backwardBtnRef = {backwardBtn};
        
        Runnable updateDirectionButtonStyles = () -> {
            String selectedStyle = "-fx-background-color: linear-gradient(to bottom, #28a745, #1e7e34); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11; -fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-padding: 6 12; -fx-background-radius: 4; -fx-border-radius: 4;";
            String unselectedStyle = "-fx-background-color: linear-gradient(to bottom, #6c757d, #5a6268); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11; -fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-padding: 6 12; -fx-background-radius: 4; -fx-border-radius: 4;";
            
            bothBtnRef[0].setStyle(currentDirection[0].equals("both") ? selectedStyle : unselectedStyle);
            forwardBtnRef[0].setStyle(currentDirection[0].equals("forward") ? selectedStyle : unselectedStyle);
            backwardBtnRef[0].setStyle(currentDirection[0].equals("backward") ? selectedStyle : unselectedStyle);
        };
        
        // Update button action handlers
        bothBtn.setOnAction(e -> {
            System.out.println("[TEMP] Both button clicked");
            currentDirection[0] = "both";
            updateDirectionButtonStyles.run();
            refreshFlowTable.run();
        });
        
        forwardBtn.setOnAction(e -> {
            System.out.println("[TEMP] Forward button clicked");
            currentDirection[0] = "forward";
            updateDirectionButtonStyles.run();
            refreshFlowTable.run();
        });
        
        backwardBtn.setOnAction(e -> {
            System.out.println("[TEMP] Backward button clicked");
            currentDirection[0] = "backward";
            updateDirectionButtonStyles.run();
            refreshFlowTable.run();
        });
        
        // Initial button style update
        updateDirectionButtonStyles.run();
        
        // Create toggle button for flows
        Button toggleFlowBtn = new Button("Show All Flows");
        toggleFlowBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #4a90e2, #357abd); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12; -fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-padding: 8 16; -fx-background-radius: 6; -fx-border-radius: 6; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 3, 0, 0, 1);");
        toggleFlowBtn.setOnMouseEntered(e -> toggleFlowBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #5ba0f2, #4a90e2); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12; -fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-padding: 8 16; -fx-background-radius: 6; -fx-border-radius: 6; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 4, 0, 0, 2);"));
        toggleFlowBtn.setOnMouseExited(e -> toggleFlowBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #4a90e2, #357abd); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12; -fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-padding: 8 16; -fx-background-radius: 6; -fx-border-radius: 6; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 3, 0, 0, 1);"));
        toggleFlowBtn.setOnAction(e -> {
            if (currentMode[0].equals("selected_link")) {
                // Switch to all flows
                currentMode[0] = "all_flows";
                toggleFlowBtn.setText("Show Selected Link Flows");
                // 隱藏方向按鈕（在 all_flows 模式下不需要）
                directionButtonBox.setVisible(false);
                directionButtonBox.setManaged(false);
                refreshAllFlowsTable.run();
                // Restart timeline for real-time updates
                timeline.play();
            } else if (currentMode[0].equals("all_flows")) {
                // Switch to selected link flows
                currentMode[0] = "selected_link";
                toggleFlowBtn.setText("Show All Flows");
                // 顯示方向按鈕（在 selected_link 模式下需要）
                directionButtonBox.setVisible(true);
                directionButtonBox.setManaged(true);
                refreshFlowTable.run();
                // Restart timeline for real-time updates
                timeline.play();
            }
        });
        
        Button portTableBtn = new Button("Port Table");
        portTableBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #6c757d, #5a6268); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12; -fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-padding: 8 16; -fx-background-radius: 6; -fx-border-radius: 6; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 3, 0, 0, 1);");
        portTableBtn.setOnMouseEntered(e -> portTableBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #7d848a, #6c757d); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12; -fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-padding: 8 16; -fx-background-radius: 6; -fx-border-radius: 6; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 4, 0, 0, 2);"));
        portTableBtn.setOnMouseExited(e -> portTableBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #6c757d, #5a6268); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12; -fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-padding: 8 16; -fx-background-radius: 6; -fx-border-radius: 6; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 3, 0, 0, 1);"));
        // Create protocol table button
        Button protocolTableBtn = new Button("Protocol Table");
        protocolTableBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #fd7e14, #e55a00); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12; -fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-padding: 8 16; -fx-background-radius: 6; -fx-border-radius: 6; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 3, 0, 0, 1);");
        protocolTableBtn.setOnMouseEntered(e -> protocolTableBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #ff8c1a, #fd7e14); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12; -fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-padding: 8 16; -fx-background-radius: 6; -fx-border-radius: 6; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 4, 0, 0, 2);"));
        protocolTableBtn.setOnMouseExited(e -> protocolTableBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #fd7e14, #e55a00); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12; -fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-padding: 8 16; -fx-background-radius: 6; -fx-border-radius: 6; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 3, 0, 0, 1);"));
        
        // Set up button actions after all buttons are created
        portTableBtn.setOnAction(e -> {
            showPortTableWindow();
        });
        
        protocolTableBtn.setOnAction(e -> {
            showProtocolTableWindow();
        });
        
        Button closeBtn = new Button("Close");
        closeBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #28a745, #1e7e34); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12; -fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-padding: 8 16; -fx-background-radius: 6; -fx-border-radius: 6; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 3, 0, 0, 1);");
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #34ce57, #28a745); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12; -fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-padding: 8 16; -fx-background-radius: 6; -fx-border-radius: 6; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 4, 0, 0, 2);"));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #28a745, #1e7e34); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12; -fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-padding: 8 16; -fx-background-radius: 6; -fx-border-radius: 6; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 3, 0, 0, 1);"));
        closeBtn.setOnAction(e -> {
            timeline.stop();
            dialog.close();
        });
        
        buttonBox.getChildren().addAll(toggleFlowBtn, portTableBtn, protocolTableBtn, closeBtn);
        
        // Add direction buttons to a separate container
        HBox mainButtonContainer = new HBox(20);
        mainButtonContainer.setAlignment(Pos.CENTER);
        mainButtonContainer.getChildren().addAll(directionButtonBox, buttonBox);
        
        // Create header row with LIVE indicator on left and direction buttons on right
        HBox headerRow = new HBox();
        headerRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(liveContainer, Priority.ALWAYS);
        headerRow.getChildren().addAll(liveContainer, directionButtonBox);
        
        // Add header elements to header container
        headerContainer.getChildren().addAll(headerRow, titleLabel);
        
        // Add all elements to root
        root.getChildren().addAll(headerContainer, contentArea, buttonBox);
        
        Scene scene = new Scene(root);
        dialog.setScene(scene);
        dialog.show();
        System.out.println("[TEMP] Dialog created and shown with direction buttons");
    }
    
    private void showPortTableWindow() {
        Stage portWindow = new Stage();
        portWindow.setTitle("Port Table");
        portWindow.setWidth(600);
        portWindow.setHeight(400);
        portWindow.setResizable(false);
        
        VBox root = new VBox(20);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #f8f9fa;");
        
        // Create table
        TableView<Map<String, Object>> portTable = new TableView<>();
        portTable.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-radius: 5;");
        
        // Load port table data
        List<Map<String, Object>> portRows = new ArrayList<>();
        try (java.io.FileReader reader = new java.io.FileReader("porttable.json")) {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            com.google.gson.reflect.TypeToken<List<Map<String, Object>>> typeToken =
                (com.google.gson.reflect.TypeToken<List<Map<String, Object>>>) 
                com.google.gson.reflect.TypeToken.getParameterized(List.class, Map.class);
            portRows = gson.fromJson(reader, typeToken.getType());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        
        // Create columns dynamically
        if (!portRows.isEmpty()) {
            for (String key : portRows.getFirst().keySet()) {
                if (key.equals("port")) {
                    // Port column should be integer
                    TableColumn<Map<String, Object>, Number> col = new TableColumn<>(key);
                    col.setCellValueFactory(data -> {
                        Object value = data.getValue().get(key);
                        if (value instanceof Number) {
                            return new SimpleIntegerProperty(((Number) value).intValue());
                        } else if (value instanceof String) {
                            try {
                                return new SimpleIntegerProperty(Integer.parseInt((String) value));
                            } catch (NumberFormatException e) {
                                return new SimpleIntegerProperty(0);
                            }
                        }
                        return new SimpleIntegerProperty(0);
                    });
                    portTable.getColumns().add(col);
                } else {
                    // Other columns remain as string
                    TableColumn<Map<String, Object>, String> col = new TableColumn<>(key);
                    col.setCellValueFactory(data -> {
                        Object value = data.getValue().get(key);
                        String stringValue = (value != null) ? value.toString() : "";
                        return new SimpleStringProperty(stringValue);
                    });
                    portTable.getColumns().add(col);
                }
            }
        }
        
        portTable.getItems().addAll(portRows);
        
        // Create close button
        Button closeBtn = new Button("Close");
        closeBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #28a745, #1e7e34); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12; -fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-padding: 8 16; -fx-background-radius: 6; -fx-border-radius: 6; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 3, 0, 0, 1);");
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #34ce57, #28a745); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12; -fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-padding: 8 16; -fx-background-radius: 6; -fx-border-radius: 6; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 4, 0, 0, 2);"));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #28a745, #1e7e34); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12; -fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-padding: 8 16; -fx-background-radius: 6; -fx-border-radius: 6; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 3, 0, 0, 1);"));
        closeBtn.setOnAction(e -> portWindow.close());
        
        HBox buttonBox = new HBox();
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.getChildren().add(closeBtn);
        
        root.getChildren().addAll(portTable, buttonBox);
        
        Scene scene = new Scene(root);
        portWindow.setScene(scene);
        portWindow.show();
    }
    
    private void showProtocolTableWindow() {
        Stage protocolWindow = new Stage();
        protocolWindow.setTitle("Protocol Table");
        protocolWindow.setWidth(600);
        protocolWindow.setHeight(400);
        protocolWindow.setResizable(false);
        
        VBox root = new VBox(20);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #f8f9fa;");
        
        // Create table
        TableView<Map<String, Object>> protocolTable = new TableView<>();
        protocolTable.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-radius: 5;");
        
        // Load protocol table data
        List<Map<String, Object>> protocolRows = new ArrayList<>();
        try (java.io.FileReader reader = new java.io.FileReader("protocoltable.json")) {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            com.google.gson.reflect.TypeToken<List<Map<String, Object>>> typeToken =
                (com.google.gson.reflect.TypeToken<List<Map<String, Object>>>) 
                com.google.gson.reflect.TypeToken.getParameterized(List.class, Map.class);
            protocolRows = gson.fromJson(reader, typeToken.getType());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        
        // Create columns in specific order with custom headers
        if (!protocolRows.isEmpty()) {
            // Protocol Number column
            TableColumn<Map<String, Object>, Number> protocolNumberCol = new TableColumn<>("Protocol Number");
            protocolNumberCol.setCellValueFactory(data -> {
                Object value = data.getValue().get("Protocol Number");
                if (value instanceof Number) {
                    return new SimpleIntegerProperty(((Number) value).intValue());
                } else if (value instanceof String) {
                    try {
                        return new SimpleIntegerProperty(Integer.parseInt((String) value));
                    } catch (NumberFormatException e) {
                        return new SimpleIntegerProperty(0);
                    }
                }
                return new SimpleIntegerProperty(0);
            });
            protocolNumberCol.setSortable(true);
            protocolTable.getColumns().add(protocolNumberCol);
            
            // Protocol column (Keyword)
            TableColumn<Map<String, Object>, String> protocolCol = new TableColumn<>("Protocol");
            protocolCol.setCellValueFactory(data -> {
                Object value = data.getValue().get("Keyword");
                String stringValue = (value != null) ? value.toString() : "";
                return new SimpleStringProperty(stringValue);
            });
            protocolCol.setSortable(true);
            protocolTable.getColumns().add(protocolCol);
            
            // Protocol Full Name column
            TableColumn<Map<String, Object>, String> protocolFullNameCol = new TableColumn<>("Protocol Full Name");
            protocolFullNameCol.setCellValueFactory(data -> {
                Object value = data.getValue().get("Protocol");
                String stringValue = (value != null) ? value.toString() : "";
                return new SimpleStringProperty(stringValue);
            });
            protocolFullNameCol.setSortable(true);
            protocolTable.getColumns().add(protocolFullNameCol);
        }
        
        protocolTable.getItems().addAll(protocolRows);
        
        // Create close button
        Button closeBtn = new Button("Close");
        closeBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #28a745, #1e7e34); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12; -fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-padding: 8 16; -fx-background-radius: 6; -fx-border-radius: 6; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 3, 0, 0, 1);");
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #34ce57, #28a745); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12; -fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-padding: 8 16; -fx-background-radius: 6; -fx-border-radius: 6; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 4, 0, 0, 2);"));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #28a745, #1e7e34); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12; -fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-padding: 8 16; -fx-background-radius: 6; -fx-border-radius: 6; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 3, 0, 0, 1);"));
        closeBtn.setOnAction(e -> protocolWindow.close());
        
        HBox buttonBox = new HBox();
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.getChildren().add(closeBtn);
        
        root.getChildren().addAll(protocolTable, buttonBox);
        
        Scene scene = new Scene(root);
        protocolWindow.setScene(scene);
        protocolWindow.show();
    }
    
    // Create port table for Ingress or Egress
    private VBox createPortTable(String title, org.example.demo2.Node node, boolean isIngress) {
        VBox tableContainer = new VBox();
        tableContainer.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-border-color: #e9ecef; -fx-border-width: 1; -fx-border-radius: 5;");
        tableContainer.setSpacing(10);
        tableContainer.setPrefWidth(350);
        
        // Title
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        titleLabel.setStyle("-fx-text-fill: #2c3e50;");
        titleLabel.setAlignment(Pos.CENTER);
        
        // Create table
        TableView<PortTableRow> table = new TableView<>();
        table.setPrefHeight(400);
        table.setId(isIngress ? "ingressTable" : "egressTable");
        
        // Create columns
        TableColumn<PortTableRow, String> portCol = new TableColumn<>("Port");
        portCol.setCellValueFactory(new PropertyValueFactory<>("port"));
        portCol.setPrefWidth(80);
        
        TableColumn<PortTableRow, String> nodeCol = new TableColumn<>("Connected Node");
        nodeCol.setCellValueFactory(new PropertyValueFactory<>("connectedNode"));
        nodeCol.setPrefWidth(120);
        
        TableColumn<PortTableRow, VBox> flowsCol = new TableColumn<>("Active Flows");
        flowsCol.setCellValueFactory(new PropertyValueFactory<>("flows"));
        flowsCol.setCellFactory(param -> new TableCell<PortTableRow, VBox>() {
            @Override
            protected void updateItem(VBox item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    setGraphic(item);
                }
            }
        });
        flowsCol.setPrefWidth(120);
        
        table.getColumns().addAll(portCol, nodeCol, flowsCol);
        
        // Add table to container
        tableContainer.getChildren().addAll(titleLabel, table);
        
        return tableContainer;
    }
    
    // Update port table with data
    private void updatePortTable(VBox tableContainer, org.example.demo2.Node node, boolean isIngress) {
        TableView<PortTableRow> table = (TableView<PortTableRow>) tableContainer.getChildren().get(1);
        table.getItems().clear();
        
        if (topologyCanvas != null) {
            List<Link> links = topologyCanvas.getLinks();
            if (links != null) {
                for (Link link : links) {
                    if (isNodeConnectedToLink(node, link)) {
                        boolean isSource = isNodeSourceInLink(node, link);
                        
                        // For Ingress: node is target (receiving)
                        // For Egress: node is source (sending)
                        if ((isIngress && !isSource) || (!isIngress && isSource)) {
                            String port = isSource ? String.valueOf(link.srcInterface) : String.valueOf(link.dstInterface);
                            String connectedNode = isSource ? link.target : link.source;
                            
                            // Get flows for this link
                            VBox flowsBox = createFlowsBox(link);
                            
                            PortTableRow row = new PortTableRow(port, connectedNode, flowsBox);
                            table.getItems().add(row);
                        }
                    }
                }
            }
        }
        
        // If no data, add placeholder
        if (table.getItems().isEmpty()) {
            Label noDataLabel = new Label("No " + (isIngress ? "ingress" : "egress") + " ports found");
            noDataLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-style: italic; -fx-padding: 20;");
            noDataLabel.setAlignment(Pos.CENTER);
            table.setPlaceholder(noDataLabel);
        }
    }
    
    // Create flows box with colored rectangles
    private VBox createFlowsBox(Link link) {
        VBox flowsBox = new VBox(2);
        flowsBox.setAlignment(Pos.CENTER_LEFT);
        flowsBox.setPadding(new Insets(5));
        
        if (link.flow_set != null && !link.flow_set.isEmpty()) {
            for (Flow flowInSet : link.flow_set) {
                // 使用完整的 Flow 資訊，確保和拓樸上一樣的五元組（src/dst IP + port + protocol）一致
                Flow completeFlow = findCompleteFlowInfo(flowInSet);
                Flow flowForColor = completeFlow != null ? completeFlow : flowInSet;
                
                // ✅ 與拓樸畫面完全共用同一套顏色邏輯：
                // 直接呼叫 TopologyCanvas.getColorForFlow，裡面會用 generateFlowKey + getStableColorIndex
                // 這樣「Node Information → Show Port」中的顏色就會和連結上的 flow 顏色一致（同一條 flow 同一個顏色）。
                javafx.scene.paint.Color flowColor = topologyCanvas.getColorForFlow(flowForColor);
                
                // Create colored rectangle
                javafx.scene.shape.Rectangle colorRect = new javafx.scene.shape.Rectangle(12, 12);
                colorRect.setFill(flowColor);
                colorRect.setStroke(javafx.scene.paint.Color.BLACK);
                colorRect.setStrokeWidth(0.5);
                colorRect.setArcWidth(2);
                colorRect.setArcHeight(2);
                
                // Add tooltip with flow info (use complete flow if available)
                Flow flowForTooltip = completeFlow != null ? completeFlow : flowInSet;
                String tooltipText = String.format("%s:%d → %s:%d", 
                    flowForTooltip.srcIp, flowForTooltip.srcPort, flowForTooltip.dstIp, flowForTooltip.dstPort);
                Tooltip.install(colorRect, new Tooltip(tooltipText));
                
                flowsBox.getChildren().add(colorRect);
            }
        } else {
            Label noFlowsLabel = new Label("No flows");
            noFlowsLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 10;");
            flowsBox.getChildren().add(noFlowsLabel);
        }
        
        return flowsBox;
    }
    

    
    // Data class for port table rows
    public static class PortTableRow {
        private final String port;
        private final String connectedNode;
        private final VBox flows;
        
        public PortTableRow(String port, String connectedNode, VBox flows) {
            this.port = port;
            this.connectedNode = connectedNode;
            this.flows = flows;
        }
        
        public String getPort() { return port; }
        public String getConnectedNode() { return connectedNode; }
        public VBox getFlows() { return flows; }
    }
    
    /**
     * 安全地獲取 Link 的源 IP 地址，支持 real-time 和 playback 模式
     */
    private String getLinkSourceIp(Link link) {
        if (link.sourceIps != null && !link.sourceIps.isEmpty()) {
            // Real-time mode: use sourceIps list
            return convertLittleEndianIpStringToStandard(link.sourceIps.get(0));
        } else if (link.source != null) {
            // Playback mode: use source string
            return link.source;
        }
        return "Unknown";
    }
    
    /**
     * 安全地獲取 Link 的目標 IP 地址，支持 real-time 和 playback 模式
     */
    private String getLinkTargetIp(Link link) {
        if (link.targetIps != null && !link.targetIps.isEmpty()) {
            // Real-time mode: use targetIps list
            return convertLittleEndianIpStringToStandard(link.targetIps.get(0));
        } else if (link.target != null) {
            // Playback mode: use target string
            return link.target;
        }
        return "Unknown";
    }
    
    /**
     * 檢查flow是否經過指定方向的link
     * @param flow 要檢查的flow
     * @param linkSrcIp link的source IP
     * @param linkDstIp link的target IP
     * @param linkSrcName link的source設備名稱
     * @param linkDstName link的target設備名稱
     * @return true如果flow經過這個方向的link
     */
    private boolean isFlowInDirection(Flow flow, String linkSrcIp, String linkDstIp, String linkSrcName, String linkDstName) {
        if (flow == null || flow.pathNodes == null || flow.pathNodes.isEmpty()) {
            System.out.println("[TEMP] Flow has no path nodes");
            return false;
        }
        
        System.out.println("[TEMP] Checking if flow passes through: " + linkSrcName + "(" + linkSrcIp + ") -> " + linkDstName + "(" + linkDstIp + ")");
        System.out.println("[TEMP] Flow path has " + flow.pathNodes.size() + " nodes");
        
        // 檢查flow的path中是否包含這個link（linkSrcIp/Name -> linkDstIp/Name的順序）
        for (int i = 0; i < flow.pathNodes.size() - 1; i++) {
            String pathNodeId1 = flow.pathNodes.get(i);
            String pathNodeId2 = flow.pathNodes.get(i + 1);
            
            // 將pathNode ID轉換為IP地址（使用TopologyCanvas的轉換邏輯）
            String pathNodeIp1 = topologyCanvas.convertNodeIdToIp(pathNodeId1);
            String pathNodeIp2 = topologyCanvas.convertNodeIdToIp(pathNodeId2);
            
            // 通過IP查找Node以獲取設備名稱
            Node pathNode1Obj = topologyCanvas.getNodeByIp(pathNodeIp1);
            Node pathNode2Obj = topologyCanvas.getNodeByIp(pathNodeIp2);
            
            String pathNode1Name = (pathNode1Obj != null) ? pathNode1Obj.name : pathNodeIp1;
            String pathNode2Name = (pathNode2Obj != null) ? pathNode2Obj.name : pathNodeIp2;
            
            System.out.println("[TEMP] Path segment [" + i + "]: " + pathNodeId1 + " (" + pathNode1Name + "/" + pathNodeIp1 + ") -> " + 
                             pathNodeId2 + " (" + pathNode2Name + "/" + pathNodeIp2 + ")");
            
            // 比較：檢查path segment是否匹配link的source->target（比較設備名稱或IP）
            boolean match1 = pathNodeIp1.equals(linkSrcIp) || pathNode1Name.equals(linkSrcName);
            boolean match2 = pathNodeIp2.equals(linkDstIp) || pathNode2Name.equals(linkDstName);
            
            if (match1 && match2) {
                System.out.println("[TEMP] MATCH FOUND! " + pathNode1Name + " matches " + linkSrcName + ", " + pathNode2Name + " matches " + linkDstName);
                return true;
            }
        }
        
        System.out.println("[TEMP] No match found in flow path");
        return false;
    }

} 