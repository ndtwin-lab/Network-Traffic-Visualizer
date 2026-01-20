package org.example.demo2;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

public class LinkFilter extends VBox {
    private static Stage dialog;
    private final TopologyCanvas topologyCanvas;
    private List<Link> links;
    private List<Node> nodes;
    private List<Flow> flows;
    private final ObservableList<CheckBox> linkCheckBoxes;
    private final CheckBox selectAllCheckBox;
    private final ComboBox<String> sourceNodeCombo;
    private TableView<FlowInfo> flowInfoTable;
    private ObservableList<FlowInfo> flowInfoData = FXCollections.observableArrayList();
    private final VBox linkCheckBoxesContainer;

    public LinkFilter(TopologyCanvas topologyCanvas, List<Link> links, List<Node> nodes, List<Flow> flows) {
        this.topologyCanvas = topologyCanvas;
        this.links = links;
        this.nodes = nodes;
        this.flows = flows;
        this.linkCheckBoxes = FXCollections.observableArrayList();

        // Set basic styles
        setPrefWidth(350);
        setMinWidth(300);
        setMaxWidth(400);
        setStyle("-fx-background-color: white; -fx-border-color: #cccccc; -fx-border-radius: 5;");
        setPadding(new Insets(10));
        setSpacing(8);

        // Title and icon
        HBox titleBox = new HBox(5);
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
        
        Label titleLabel = new Label("Link Filter");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        titleLabel.setStyle("-fx-text-fill: #333333;");
        
        if (filterIcon != null) {
            titleBox.getChildren().addAll(filterIcon, titleLabel);
        } else {
            titleBox.getChildren().add(titleLabel);
        }

        // Select Nodes area
        Label selectNodesLabel = new Label("Select Nodes:");
        selectNodesLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        selectNodesLabel.setStyle("-fx-text-fill: #333333;");
        
        // Source label and dropdown menu
        Label sourceLabel = new Label("Source:");
        sourceLabel.setFont(Font.font("Arial", 11));
        
        sourceNodeCombo = new ComboBox<>();
        sourceNodeCombo.setPromptText("Select source node");
        sourceNodeCombo.setPrefWidth(Double.MAX_VALUE);
        
        // Populate node options
        populateNodeOptions();
        
        // Add event handling
        sourceNodeCombo.setOnAction(e -> updateFlowInfoTable());

        // Select All 複選框
        selectAllCheckBox = new CheckBox("Select All");
        selectAllCheckBox.setSelected(true);
        selectAllCheckBox.setOnAction(e -> toggleAllFlows());

        // Flow Information Table
        Label tableLabel = new Label("Flow Information");
        tableLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        tableLabel.setStyle("-fx-text-fill: #333333;");
        
        flowInfoTable = new TableView<>();
        flowInfoTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        flowInfoTable.setPrefHeight(200);
        flowInfoTable.setStyle("-fx-background-color: white; -fx-border-color: #cccccc;");
        
        // Flow ID 列
        TableColumn<FlowInfo, String> flowIdCol = new TableColumn<>("Flow ID");
        flowIdCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().flowId));
        flowIdCol.setPrefWidth(60);
        
        // Src 列
        TableColumn<FlowInfo, String> srcCol = new TableColumn<>("Src");
        srcCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().srcName));
        srcCol.setPrefWidth(80);
        
        // Dst 列
        TableColumn<FlowInfo, String> dstCol = new TableColumn<>("Dst");
        dstCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().dstName));
        dstCol.setPrefWidth(80);
        
        // Utilization 列
        TableColumn<FlowInfo, String> utilCol = new TableColumn<>("Utilization");
        utilCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().utilization));
        utilCol.setPrefWidth(80);
        
        flowInfoTable.getColumns().addAll(flowIdCol, srcCol, dstCol, utilCol);
        flowInfoTable.setItems(flowInfoData);

        // 底部的鏈路複選框容器
        linkCheckBoxesContainer = new VBox(5);
        linkCheckBoxesContainer.setStyle("-fx-background-color: #f5f5f5; -fx-padding: 5; -fx-border-color: #cccccc; -fx-border-radius: 3;");

        // 添加組件
        getChildren().addAll(
            titleBox, 
            selectNodesLabel, 
            sourceLabel, 
            sourceNodeCombo, 
            selectAllCheckBox, 
            tableLabel, 
            flowInfoTable,
            linkCheckBoxesContainer
        );

        // 初始狀態
        selectAllFlows();
        updateFlowInfoTable();
    }

    // 更新所有數據
    public void updateData(List<Link> newLinks, List<Node> newNodes, List<Flow> newFlows) {
        this.links = newLinks;
        this.nodes = newNodes;
        this.flows = newFlows;
        refreshAllOptions();
    }

    // 刷新所有選項
    private void refreshAllOptions() {
        // 保存當前選擇的 source node
        String currentSelection = sourceNodeCombo.getValue();
        
        // 重新填充 node 選項
        populateNodeOptions();
        
        // 恢復之前的選擇（如果還存在）
        if (currentSelection != null && sourceNodeCombo.getItems().contains(currentSelection)) {
            sourceNodeCombo.setValue(currentSelection);
        } else if (!sourceNodeCombo.getItems().isEmpty()) {
            sourceNodeCombo.setValue(sourceNodeCombo.getItems().get(0));
        }
        
        // 更新表格
        updateFlowInfoTable();
    }
    
    private void populateNodeOptions() {
        ObservableList<String> nodeOptions = FXCollections.observableArrayList();
        
        for (Node node : nodes) {
            String nodeText = node.name + " (" + node.ip + ")";
            nodeOptions.add(nodeText);
        }
        
        sourceNodeCombo.setItems(nodeOptions);
        
        // 設置默認選擇第一個節點
        if (!nodeOptions.isEmpty()) {
            sourceNodeCombo.setValue(nodeOptions.get(0));
        }
    }
    
    private void updateFlowInfoTable() {
        flowInfoData.clear();
        
        String selectedSource = sourceNodeCombo.getValue();
        if (selectedSource == null) {
            return;
        }
        
        // 解析選中的節點名稱
        String selectedNodeName = selectedSource.split(" \\(")[0];
        
        // 找到與選中節點相關的flows
        int flowId = 1;
        for (Flow flow : flows) {
            if (flow.pathNodes == null || flow.pathNodes.size() < 2) continue;
            
            // 檢查flow是否經過選中的節點
            boolean involvesSelectedNode = false;
            String srcName = "", dstName = "";
            
            for (int i = 0; i < flow.pathNodes.size() - 1; i++) {
                String srcIp = flow.pathNodes.get(i);
                String dstIp = flow.pathNodes.get(i + 1);
                
                Node srcNode = topologyCanvas.getNodeByIp(srcIp);
                Node dstNode = topologyCanvas.getNodeByIp(dstIp);
                
                if (srcNode != null && dstNode != null) {
                    if (srcNode.name.equals(selectedNodeName) || dstNode.name.equals(selectedNodeName)) {
                        involvesSelectedNode = true;
                        srcName = srcNode.name;
                        dstName = dstNode.name;
                        break;
                    }
                }
            }
            
            if (involvesSelectedNode) {
                // 計算利用率
                double utilization = 0.0;
                for (Link link : links) {
                    Node lsrc = topologyCanvas.getNodeByIp(link.source);
                    Node ldst = topologyCanvas.getNodeByIp(link.target);
                    if (lsrc != null && ldst != null) {
                        if ((lsrc.name.equals(srcName) && ldst.name.equals(dstName)) ||
                            (lsrc.name.equals(dstName) && ldst.name.equals(srcName))) {
                            utilization = link.link_bandwidth_utilization_percent;
                            break;
                        }
                    }
                }
                
                flowInfoData.add(new FlowInfo(
                    String.valueOf(flowId++),
                    srcName,
                    dstName,
                    String.format("%.2f%%", utilization)
                ));
            }
        }
        
        // 更新底部的鏈路複選框
        updateLinkCheckBoxes(selectedNodeName);
    }

    private void toggleAllFlows() {
        boolean selectAll = selectAllCheckBox.isSelected();
        for (CheckBox checkBox : linkCheckBoxes) {
            checkBox.setSelected(selectAll);
        }
        updateLinkVisibility();
    }

    private void selectAllFlows() {
        selectAllCheckBox.setSelected(true);
    }

    // 更新底部的鏈路複選框
    private void updateLinkCheckBoxes(String selectedNodeName) {
        // 清空現有的複選框
        linkCheckBoxesContainer.getChildren().clear();
        linkCheckBoxes.clear();
        
        // 找到與選中節點相關的所有鏈路
        Set<String> connectedLinks = new HashSet<>();
        
        for (Link link : links) {
            Node srcNode = topologyCanvas.getNodeByIp(link.source);
            Node dstNode = topologyCanvas.getNodeByIp(link.target);
            
            if (srcNode != null && dstNode != null) {
                if (srcNode.name.equals(selectedNodeName) || dstNode.name.equals(selectedNodeName)) {
                    // 標準化鏈路名稱（按字母順序）
                    String linkKey = srcNode.name.compareTo(dstNode.name) < 0 ? 
                        srcNode.name + "," + dstNode.name : 
                        dstNode.name + "," + srcNode.name;
                    
                    if (!connectedLinks.contains(linkKey)) {
                        connectedLinks.add(linkKey);
                        
                        // 創建複選框
                        String linkText = srcNode.name.compareTo(dstNode.name) < 0 ? 
                            srcNode.name + " <-> " + dstNode.name : 
                            dstNode.name + " <-> " + srcNode.name;
                        
                        CheckBox checkBox = new CheckBox(linkText);
                        checkBox.setSelected(true);
                        checkBox.setWrapText(true);
                        checkBox.setMaxWidth(Double.MAX_VALUE);
                        
                        // 添加工具提示
                        String tooltipText = String.format(
                            "Link: %s ↔ %s\nBandwidth: %d bps\nStatus: %s\nUtilization: %.2f%%",
                            srcNode.name, dstNode.name, link.bandwidth, 
                            link.is_up ? "UP" : "DOWN", link.link_bandwidth_utilization_percent
                        );
                        checkBox.setTooltip(new javafx.scene.control.Tooltip(tooltipText));
                        
                        checkBox.setOnAction(e -> {
                            updateSelectAllState();
                            updateLinkVisibility();
                        });
                        
                        linkCheckBoxes.add(checkBox);
                        linkCheckBoxesContainer.getChildren().add(checkBox);
                    }
                }
            }
        }
        
        // 更新全選狀態
        updateSelectAllState();
    }
    
    // 更新全選狀態
    private void updateSelectAllState() {
        if (linkCheckBoxes.isEmpty()) {
            selectAllCheckBox.setIndeterminate(false);
            selectAllCheckBox.setSelected(false);
            return;
        }
        
        long selectedCount = linkCheckBoxes.stream().filter(CheckBox::isSelected).count();
        if (selectedCount == 0) {
            selectAllCheckBox.setIndeterminate(false);
            selectAllCheckBox.setSelected(false);
        } else if (selectedCount == linkCheckBoxes.size()) {
            selectAllCheckBox.setIndeterminate(false);
            selectAllCheckBox.setSelected(true);
        } else {
            selectAllCheckBox.setIndeterminate(true);
        }
    }
    
    // 更新鏈路可見性
    private void updateLinkVisibility() {
        // TODO: 實現鏈路可見性控制邏輯
        // 目前此方法為空，但保留以供未來實現
    }

    // Flow 信息資料結構
    private static class FlowInfo {
        String flowId;
        String srcName;
        String dstName;
        String utilization;
        
        FlowInfo(String flowId, String srcName, String dstName, String utilization) {
            this.flowId = flowId;
            this.srcName = srcName;
            this.dstName = dstName;
            this.utilization = utilization;
        }
    }
    
    // 顯示 Link Filter 視窗
    public static void showLinkFilterDialog(TopologyCanvas topologyCanvas, List<Link> links, List<Node> nodes, List<Flow> flows) {
        if (dialog != null && dialog.isShowing()) {
            dialog.toFront();
            return;
        }
        
        // 創建新的 LinkFilter 實例
        LinkFilter linkFilter = new LinkFilter(topologyCanvas, links, nodes, flows);
        
        // 創建視窗內容
        VBox root = new VBox(10);
        root.setStyle("-fx-background-color: white; -fx-padding: 15;");
        root.setPrefWidth(400);
        root.setPrefHeight(500);
        
        // 添加內容
        root.getChildren().add(linkFilter);
        
        // 創建 Scene 和 Stage
        Scene scene = new Scene(root);
        dialog = new Stage();
        dialog.setTitle("Link Filter");
        dialog.setScene(scene);
        dialog.setResizable(true);
        dialog.setMinWidth(400);
        dialog.setMinHeight(500);
        
        // 設置視窗位置（居中）
        dialog.centerOnScreen();
        
        // 顯示視窗
        dialog.show();
    }
}