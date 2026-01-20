package org.example.demo2;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class FlowFilter extends VBox {
    private final TopologyCanvas topologyCanvas;
    private List<Flow> flows;
    private final ObservableList<CheckBox> flowCheckBoxes;
    private final CheckBox selectAllCheckBox;
    private final Map<String, Integer> flowIdToIndexMap; // New: Flow ID to index mapping
    private final ScrollPane scrollPane; // New: Scroll panel
    private final VBox flowContentBox; // New: Flow content container

    public FlowFilter(TopologyCanvas topologyCanvas, List<Flow> flows) {
        this.topologyCanvas = topologyCanvas;
        this.flows = flows;
        this.flowCheckBoxes = FXCollections.observableArrayList();
        this.flowIdToIndexMap = new HashMap<>();
        
        // Initialize scroll panel and content container
        this.flowContentBox = new VBox(2);
        this.flowContentBox.setPadding(new Insets(5));
        
        this.scrollPane = new ScrollPane(flowContentBox);
        this.scrollPane.setPrefViewportHeight(200); // Set default height, display about 10 items
        this.scrollPane.setMaxHeight(200);
        this.scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        this.scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        this.scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        // Set basic styles
        setPrefWidth(280);
        setMinWidth(160);
        setMaxWidth(320);
        setStyle("-fx-background-color: rgba(255, 255, 255, 0.95); -fx-border-color: #cccccc; -fx-border-radius: 5;");
        setPadding(new Insets(8));
        setSpacing(2);

        // Title and icon
        javafx.scene.layout.HBox titleBox = new javafx.scene.layout.HBox(5);
        titleBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        
        // Add icon
        ImageView filterIcon = null;
        try {
            // Use getResource to load resources from JAR
            Image iconImage = new Image(getClass().getResourceAsStream("/images/filter.png"));
            filterIcon = new ImageView(iconImage);
            filterIcon.setFitWidth(16);
            filterIcon.setFitHeight(16);
            filterIcon.setPreserveRatio(true);
            filterIcon.setSmooth(true);
        } catch (Exception e) {
            System.err.println("Failed to load filter icon: " + e.getMessage());
        }
        
                           Label titleLabel = new Label("Flow Information (Live)");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        titleLabel.setStyle("-fx-text-fill: #333333;");
        
        if (filterIcon != null) {
            titleBox.getChildren().addAll(filterIcon, titleLabel);
        } else {
            titleBox.getChildren().add(titleLabel);
        }

        // Select all / Deselect all
        selectAllCheckBox = new CheckBox("Select All Flows");
        selectAllCheckBox.setSelected(true);
        selectAllCheckBox.setStyle("-fx-font-size: 11px; -fx-padding: 2px 0px;");
        selectAllCheckBox.setOnAction(e -> toggleAllFlows());

        // Separator
        Separator separator = new Separator();

        // Create flow options
        createFlowOptions();

        // Add components
        getChildren().addAll(titleBox, selectAllCheckBox, separator, scrollPane);

        // Initial state: all selected
        selectAllFlows();
    }

            // Generate unique identifier for flow
    private String generateFlowId(Flow flow) {
        return flow.srcIp + "_" + flow.dstIp + "_" + flow.srcPort + "_" + flow.dstPort;
    }

            // Update flows data
    public void updateFlows(List<Flow> newFlows) {
        this.flows = newFlows;
        refreshFlowOptions();
    }

            // Refresh flow options
    private void refreshFlowOptions() {
        // Save current check state
        Map<String, Boolean> checkedStates = new HashMap<>();
        
        for (int i = 0; i < flowCheckBoxes.size(); i++) {
            CheckBox checkBox = flowCheckBoxes.get(i);
            String flowText = checkBox.getText(); // Get text directly from CheckBox
            
            if (flowText != null && !flowText.isEmpty()) {
                checkedStates.put(flowText, checkBox.isSelected());
            }
        }
        
        // Clear existing options
        flowCheckBoxes.clear();
        flowIdToIndexMap.clear();
        
        // Recreate options and pass saved state
        createFlowOptions(checkedStates);
        
        // Update select all state
        updateSelectAllState();
        updateFlowVisibility();
    }

    private void createFlowOptions() {
        createFlowOptions(null);
    }
    
    private void createFlowOptions(Map<String, Boolean> checkedStates) {
        // Clear existing CheckBoxes
        flowCheckBoxes.clear();
        flowContentBox.getChildren().clear();
        
        // Show all Flows
        for (int i = 0; i < flows.size(); i++) {
            Flow flow = flows.get(i);
            String flowId = generateFlowId(flow);
            flowIdToIndexMap.put(flowId, i);
            
            // ✅ 與拓樸顏色完全一致：直接用 TopologyCanvas.getColorForFlow
            // 不再自行維護 colorIndex，完全交給 TopologyCanvas 依 five-tuple 穩定 hash 決定顏色。
            Color flowColor = topologyCanvas.getColorForFlow(flow);
            
            // Create icon with colored rectangle
            javafx.scene.shape.Rectangle colorRectangle = new javafx.scene.shape.Rectangle(14, 10, flowColor);
            colorRectangle.setStroke(Color.BLACK);
            colorRectangle.setStrokeWidth(0.5);
            colorRectangle.setArcWidth(1);
            colorRectangle.setArcHeight(1);
            
            // Get device names for src and dst
            String srcDeviceName = getDeviceName(flow.srcIp);
            String dstDeviceName = getDeviceName(flow.dstIp);
            
            // Create flow description (including device name)
            String flowText = String.format("%s → %s", srcDeviceName, dstDeviceName);
            
            // Create CheckBox
            CheckBox checkBox = new CheckBox();
            
            // Set check state
            if (checkedStates != null && checkedStates.containsKey(flowText)) {
                checkBox.setSelected(checkedStates.get(flowText));
            } else {
                checkBox.setSelected(true); // Default to checked
            }
            
            // Directly set CheckBox text and icon
            checkBox.setText(flowText);
            checkBox.setGraphic(colorRectangle);
            checkBox.setWrapText(false);
            checkBox.setMaxWidth(Double.MAX_VALUE);
            checkBox.setStyle("-fx-font-size: 11px; -fx-alignment: CENTER_LEFT; -fx-padding: 2px 0px; -fx-background-insets: 0; -fx-border-insets: 0; -fx-text-fill: #333333;");
            
            // Ensure icon and text alignment
            checkBox.setGraphicTextGap(5);
            
            // Add tooltip (including detailed information)
            String pathInfo = "無路徑信息";
            if (flow.pathNodes != null && !flow.pathNodes.isEmpty()) {
                List<String> deviceNames = flow.pathNodes.stream()
                    .map(this::getDeviceName)
                    .collect(Collectors.toList());
                pathInfo = String.join(" → ", deviceNames);
            }
            
            String tooltipText = String.format(
                "Source Device: %s (%s:%d)\nDestination Device: %s (%s:%d)\nProtocol: %d\nRate: %.2f bps\nPath: %s",
                srcDeviceName, flow.srcIp, flow.srcPort, 
                dstDeviceName, flow.dstIp, flow.dstPort, 
                flow.protocolId, flow.estimatedFlowSendingRateBpsInTheLastSec, pathInfo
            );
            Tooltip tooltip = new Tooltip(tooltipText);
            Tooltip.install(checkBox, tooltip);
            
            // Add event handling
            checkBox.setOnAction(e -> {
                updateFlowVisibility();
                updateSelectAllState();
            });
            
            flowCheckBoxes.add(checkBox);
            flowContentBox.getChildren().add(checkBox);
        }
        
        // Display Flow total information
        if (flows.size() > 0) {
            Label infoLabel = new Label("Total " + flows.size() + "  Flows");
            infoLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666666; -fx-font-style: italic;");
            infoLabel.setPadding(new Insets(5, 0, 0, 0));
            flowContentBox.getChildren().add(infoLabel);
        }
        

    }
    
            // Get device name
    private String getDeviceName(String ip) {
        Node node = topologyCanvas.getNodeByIp(ip);
        String deviceName;
        if (node != null) {
            // If name is null or empty string, use IP
            if (node.name == null || node.name.trim().isEmpty()) {
                deviceName = ip;
            } else {
                deviceName = node.name;
            }
        } else {
            deviceName = ip;
        }
        return deviceName;
    }

    private void toggleAllFlows() {
        boolean selectAll = selectAllCheckBox.isSelected();
        for (CheckBox checkBox : flowCheckBoxes) {
            checkBox.setSelected(selectAll);
        }
        updateFlowVisibility();
    }

    private void selectAllFlows() {
        selectAllCheckBox.setSelected(true);
        for (CheckBox checkBox : flowCheckBoxes) {
            checkBox.setSelected(true);
        }
        updateFlowVisibility();
    }

    private void updateSelectAllState() {
        long selectedCount = flowCheckBoxes.stream().filter(CheckBox::isSelected).count();
        if (selectedCount == 0) {
            selectAllCheckBox.setIndeterminate(false);
            selectAllCheckBox.setSelected(false);
        } else if (selectedCount == flowCheckBoxes.size()) {
            selectAllCheckBox.setIndeterminate(false);
            selectAllCheckBox.setSelected(true);
        } else {
            selectAllCheckBox.setIndeterminate(true);
        }
    }

    private void updateFlowVisibility() {
        // 檢查是否啟用了 Top-K 模式
        if (topologyCanvas.isTopKEnabled()) {
            System.out.println("[FLOW-FILTER] Top-K is enabled, skipping FlowFilter update to avoid conflict");
            return; // Top-K 啟用時，不覆蓋 Top-K 的過濾設置
        }
        
        // Update flow visibility in topology
        Set<Integer> visibleIndices = new HashSet<>();
        
        for (int i = 0; i < flowCheckBoxes.size(); i++) {
            CheckBox checkBox = flowCheckBoxes.get(i);
            if (checkBox.isSelected()) {
                // Find corresponding flow index
                String flowText = checkBox.getText();
                if (flowText.isEmpty()) {
                    // If text is empty, get from graphic
                    javafx.scene.layout.HBox hbox = (javafx.scene.layout.HBox) checkBox.getGraphic();
                    if (hbox != null && hbox.getChildren().size() > 1) {
                        javafx.scene.control.Label label = (javafx.scene.control.Label) hbox.getChildren().get(1);
                        flowText = label.getText();
                    }
                }
                
                // Find corresponding flow index based on flow description
                for (int j = 0; j < flows.size(); j++) {
                    Flow flow = flows.get(j);
                    String srcDeviceName = getDeviceName(flow.srcIp);
                    String dstDeviceName = getDeviceName(flow.dstIp);
                    String expectedText = String.format("%s → %s", srcDeviceName, dstDeviceName);
                    
                    if (expectedText.equals(flowText)) {
                        visibleIndices.add(j);
                        break;
                    }
                }
            }
        }
        
        System.out.println("[FLOW-FILTER] Updating flow visibility: " + visibleIndices.size() + " flows visible");
        // Set topology flow visibility
        topologyCanvas.setVisibleFlowIndices(visibleIndices);
    }
}