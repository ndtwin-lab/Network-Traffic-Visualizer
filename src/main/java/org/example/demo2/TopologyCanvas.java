package org.example.demo2;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

public class TopologyCanvas extends Canvas {
    private final List<org.example.demo2.Node> nodes;
    private final List<Link> links;
    private final List<org.example.demo2.Flow> flows;
    private double[] flowPos;
    // 顏色快取：index -> Color（實際顏色調色盤）
    private final Map<Integer, Color> flowColorMap = new HashMap<>();
    private boolean showFlows = true; // 初始模式为flow only
    private boolean showLinks = false;
    private double flowMoveSpeed = 5.0; // 控制flow在link上移动的时间（秒）
    private double animationTime = 0.0; // 动画时间，避免使用System.currentTimeMillis()
    private InfoPanel infoPanel;
    
    // Flow 過濾功能
    private Set<Integer> visibleFlowIndices = new HashSet<>();
    private Set<String> visibleLinkKeys = new HashSet<>(); // 新增：可見的 link keys
    
    // Performance optimization: HashMap cache for flow index lookup
    private Map<String, Integer> flowIndexCache = new HashMap<>();
    
    // Flow color assignment：使用 hash-based 穩定分配
    // Key: flowKey (srcIp_dstIp_srcPort_dstPort_protocolId), Value: colorIndex (0-23)
    private Map<String, Integer> flowColorAssignmentMap = new HashMap<>();
    
    // Top-K flows 設置 - 為 Real-time 和 Playback 分別維護
    private boolean topKEnabledRealtime = false; // Real-time 模式的 Top-K 啟用狀態
    private int topKValueRealtime = 0; // Real-time 模式的 K 值
    private boolean topKEnabledPlayback = false; // Playback 模式的 Top-K 啟用狀態
    private int topKValuePlayback = 0; // Playback 模式的 K 值
    private SideBar sideBar = null; // SideBar 引用，用於通知按鈕更新
    
    // 當前使用的 Top-K 設置（根據模式動態獲取）
    private boolean getTopKEnabled() {
        return isPlaybackMode ? topKEnabledPlayback : topKEnabledRealtime;
    }
    
    private int getTopKValue() {
        return isPlaybackMode ? topKValuePlayback : topKValueRealtime;
    }
    
    private void setTopKEnabled(boolean enabled) {
        if (isPlaybackMode) {
            topKEnabledPlayback = enabled;
        } else {
            topKEnabledRealtime = enabled;
        }
    }
    
    private void setTopKValue(int value) {
        if (isPlaybackMode) {
            topKValuePlayback = value;
        } else {
            topKValueRealtime = value;
        }
    }

    // Drag functionality
    private org.example.demo2.Node draggedNode = null;
    private double dragOffsetX = 0;
    private double dragOffsetY = 0;
    private boolean isDragging = false;
    private static final int GRID_SIZE = 15; // 更精細的格線
    public boolean darkMode = false;
    private static final boolean DEBUG = true;

    // Range selection functionality
    private boolean isRangeSelecting = false;
    private double rangeStartX = 0;
    private double rangeStartY = 0;
    private double rangeEndX = 0;
    private double rangeEndY = 0;
    private java.util.Set<org.example.demo2.Node> selectedNodes = new java.util.HashSet<>();
    private boolean isGroupDragging = false;
    private double groupDragOffsetX = 0;
    private double groupDragOffsetY = 0;

    // 平移與縮放參數
    private double offsetX = 0;
    private double offsetY = 0;
    private double scale = 1.0; // 改為可變的縮放比例
    private double minScale = 0.1; // 最小縮放比例
    private double maxScale = 5.0; // 最大縮放比例


    private java.util.function.Consumer<Boolean> showInfoPanelCallback;

    // ====== 新增：link 閃爍狀態 ======
    private final Set<String> flickerLinks = new HashSet<>();
    private boolean flickerOn = false;
    private boolean flickerEnabled = true; // 閃爍功能開關
    private Flow flickeredFlow = null; // 新增：當前被flicker的flow
    private double flickerAlpha = 1.0; // 新增：flicker透明度動畫
    private double flickerPulse = 0.0; // 新增：flicker脈衝動畫
    private Color flickeredFlowColor = null; // 新增：當前被flicker的flow的顏色

    // 新增：記錄最近移動過的 node
    private String recentlyMovedNodeIp = null;
    private long recentlyMovedTimestamp = 0;
    
    // DPID to IP mapping for playback mode node ID conversion
    private Map<String, String> dpidToIpMap = new HashMap<>();


    public TopologyCanvas(List<org.example.demo2.Node> nodes, List<Link> links, List<org.example.demo2.Flow> flows) {
        this.nodes = nodes;
        this.links = links;
        this.flows = flows;
        this.flowPos = new double[flows.size()];
        Arrays.fill(flowPos, 0);
        
        setWidth(1000);
        setHeight(800);
        setStyle("-fx-background-color: white;");
        
        // 修復：確保 Canvas 可以接收焦點和滾輪事件（Ubuntu/Linux 兼容性）
        setFocusTraversable(true);
        setMouseTransparent(false);
        setPickOnBounds(true);

        // 讓畫布隨視窗自動拉伸並自動重繪
        this.widthProperty().addListener((obs, oldVal, newVal) -> draw());
        this.heightProperty().addListener((obs, oldVal, newVal) -> draw());
        
        // 當 Canvas 被添加到場景時，自動請求焦點
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                // 延遲請求焦點，確保場景已完全初始化
                javafx.application.Platform.runLater(() -> {
                    requestFocus();
                });
            }
        });

        setupAnimation();
        setupMouseHandlers();
        setupZoomAndPan();
        
        // 初始繪製
        draw();
    }

    private void setupAnimation() {

        AnimationTimer timer = new AnimationTimer() {
            private long lastUpdate = 0;
            private double elapsedTime = 0;
            @Override
            public void handle(long now) {
                if (lastUpdate == 0) {
                    lastUpdate = now;
                    return;
                }
                double deltaTime = (now - lastUpdate) / 1_000_000_000.0; // 秒
                elapsedTime += deltaTime;
                animationTime += deltaTime; // 更新动画时间
                lastUpdate = now;

                // ====== 美化：flicker 動畫效果 ======
                if (flickerEnabled && !flickerLinks.isEmpty()) {
                    // 使用更平滑的動畫效果
                    flickerPulse += 0.15; // 控制脈衝速度
                    if (flickerPulse > Math.PI * 2) {
                        flickerPulse = 0;
                    }
                    
                    // 使用正弦波創建平滑的透明度變化
                    flickerAlpha = 0.4 + 0.6 * Math.abs(Math.sin(flickerPulse));
                    
                    // 每400毫秒切換一次閃爍狀態（稍微快一點）
                    boolean newFlickerOn = (System.currentTimeMillis() / 400) % 2 == 0;
                    if (newFlickerOn != flickerOn) {
                        flickerOn = newFlickerOn;
                    }
                } else if (flickerLinks.isEmpty()) {
                    // 如果沒有flicker links，重置動畫狀態
                    if (flickerOn) {
                        flickerOn = false;
                        flickerAlpha = 1.0;
                        flickerPulse = 0.0;
                    }
                }

                // 使用 flowMoveSpeed 來控制 flow 在每條 link 上的移動時間

                double cycleTime = flowMoveSpeed; // 一個循環就是走完一條 link 的時間
                
                for (int i = 0; i < flowPos.length; i++) {
                    double t = animationTime % cycleTime;
                    flowPos[i] = t / cycleTime;
                }
                
                // 重绘：当有flow动画时，或者当显示links时，或者在flow only mode时，或者有flicker时
                if ((showFlows && hasActiveFlows()) || showLinks || !flickerLinks.isEmpty() || (showFlows && !showLinks)) {
                    draw();
                }
            }
        };
        timer.start();
    }

    private void setupMouseHandlers() {

    }

    private void setupZoomAndPan() {
        // 實現鼠標滾輪縮放功能
        // 修復：支持 Ubuntu/Linux 系統的滾輪事件
        setOnScroll(e -> {
            double zoomFactor = 1.05;
            
            // 獲取滾輪增量值，優先使用像素單位，如果為0則使用文本單位
            double deltaY = e.getDeltaY();
            
            // 在 Ubuntu/Linux 上，getDeltaY() 可能返回 0，需要檢查其他單位
            if (Math.abs(deltaY) < 0.001) {
                // 嘗試使用文本單位（行數）
                double textDeltaY = e.getTextDeltaY();
                if (Math.abs(textDeltaY) > 0.001) {
                    deltaY = textDeltaY * 10; // 將文本單位轉換為像素單位（粗略轉換）
                } else {
                    // 如果所有增量都為0，跳過此次事件
                    // 這在 Ubuntu 上可能發生，因為某些系統配置可能導致滾輪事件不正確
                    System.out.println("[DEBUG] Scroll event with zero delta, skipping. DeltaY=" + e.getDeltaY() + ", TextDeltaY=" + e.getTextDeltaY());
                    return;
                }
            }
            
            // 確保 Canvas 獲得焦點以接收滾輪事件
            if (!isFocused()) {
                requestFocus();
            }
            
            if (deltaY < 0) {
                // 縮小
                scale = Math.max(minScale, scale / zoomFactor);
            } else {
                // 放大
                scale = Math.min(maxScale, scale * zoomFactor);
            }
            
            // 以鼠標位置為中心進行縮放
            double mouseX = e.getX();
            double mouseY = e.getY();
            
            // 調整偏移量以保持鼠標位置不變
            offsetX = mouseX - (mouseX - offsetX) * (deltaY > 0 ? zoomFactor : 1.0 / zoomFactor);
            offsetY = mouseY - (mouseY - offsetY) * (deltaY > 0 ? zoomFactor : 1.0 / zoomFactor);
            
            draw(); // 重新繪製
            e.consume();
        });
        
        // 實現雙指縮放功能
        setOnZoom(e -> {
            double zoomFactor = e.getZoomFactor();
            double oldScale = scale;
            
            // 計算新的縮放比例
            scale = Math.max(minScale, Math.min(maxScale, scale * zoomFactor));
            
            // 以縮放手勢的中心點為中心進行縮放
            double centerX = e.getX();
            double centerY = e.getY();
            
            // 調整偏移量以保持縮放中心點不變
            offsetX = centerX - (centerX - offsetX) * (scale / oldScale);
            offsetY = centerY - (centerY - offsetY) * (scale / oldScale);
            
            draw(); // 重新繪製
            e.consume();
        });
        
        // 實現平移功能（按住Ctrl鍵拖拽）
        setOnMousePressed(this::handleMousePressed);
        setOnMouseDragged(this::handleMouseDragged);
        setOnMouseReleased(this::handleMouseReleased);
        
        // 修復：當鼠標進入 Canvas 時自動獲得焦點（Ubuntu/Linux 兼容性）
        // 這確保滾輪事件可以被正確接收
        setOnMouseEntered(e -> {
            if (!isFocused()) {
                requestFocus();
            }
        });
    }

    private InfoDialog infoDialog;
    
    public void setInfoDialog(InfoDialog infoDialog) {
        this.infoDialog = infoDialog;
    }
    
    private void handleMousePressed(MouseEvent e) {
        // 記錄鼠標按下的位置
        mousePressX = e.getX();
        mousePressY = e.getY();
        
        // 轉換鼠標座標到實際座標系統
        double actualX = (e.getX() - offsetX) / scale;
        double actualY = (e.getY() - offsetY) / scale;
        
        // 檢查是否右鍵點擊 - 右鍵用於視圖操作和選單
        if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
            handleRightClick(e, actualX, actualY);
            return;
        }
        
        // 只處理左鍵事件
        if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) {
            return;
        }
        
        // 左鍵操作 - 節點和範圍選擇
        // 檢查是否按住Ctrl鍵進行視圖平移
        if (e.isControlDown()) {
            // 視圖平移模式
            isDragging = false;
            draggedNode = null;
            isRangeSelecting = false;
            isGroupDragging = false;
            return;
        }
        
        // 檢查是否點擊了已選中的節點（群組拖拽）
        if (!selectedNodes.isEmpty()) {
            org.example.demo2.Node clickedNode = getNodeAt(actualX, actualY);
            if (clickedNode != null && selectedNodes.contains(clickedNode)) {
                // 開始群組拖拽
                isGroupDragging = true;
                groupDragOffsetX = actualX - clickedNode.x;
                groupDragOffsetY = actualY - clickedNode.y;
                isDragging = false;
                draggedNode = null;
                return;
            }
        }
        
        org.example.demo2.Node clickedNode = getNodeAt(actualX, actualY);
        if (clickedNode != null) {
            // 如果按住Shift鍵，添加到選擇範圍
            if (e.isShiftDown()) {
                if (selectedNodes.contains(clickedNode)) {
                    selectedNodes.remove(clickedNode);
                } else {
                    selectedNodes.add(clickedNode);
                }
                draw();
                return;
            }
            
            // 單個節點拖拽
            if (selectedNodes.isEmpty() || selectedNodes.contains(clickedNode)) {
                draggedNode = clickedNode;
                dragOffsetX = actualX - clickedNode.x;
                dragOffsetY = actualY - clickedNode.y;
                isDragging = true;
                isRangeSelecting = false;
                isGroupDragging = false;
                return;
            }
        }

        List<Link> clickedLinks = getLinksAt(actualX, actualY);
        if (!clickedLinks.isEmpty()) {
            // 根據不同的顯示模式顯示不同的信息
            if (showFlows && !showLinks) {
                // Flow Only模式：顯示flow set的所有信息
                InfoDialog dialog = new InfoDialog(this, flows);
                dialog.showFlowSetInfo(clickedLinks);
            } else if (!showFlows && showLinks) {
                // Link Only模式：顯示link信息和使用率長條圖
                showLinkOnlyInfo(clickedLinks);
            }
            return;
        }

        // 如果沒有點擊到節點或連接，開始範圍選擇
        if (e.isShiftDown()) {
            // 按住Shift鍵開始範圍選擇
            isRangeSelecting = true;
            rangeStartX = actualX;
            rangeStartY = actualY;
            rangeEndX = actualX;
            rangeEndY = actualY;
            isDragging = false;
            draggedNode = null;
            isGroupDragging = false;
        } else {
            // 只有在點擊空白區域時才清除選擇
            // 如果點擊了節點，不要清除選擇
            if (clickedNode == null) {
                selectedNodes.clear();
                if (infoPanel != null) {
                    infoPanel.clear();
                }
                if (showInfoPanelCallback != null) showInfoPanelCallback.accept(Boolean.FALSE);
            }
            isDragging = false;
            draggedNode = null;
            isRangeSelecting = false;
            isGroupDragging = false;
        }
    }
    
    private void showFlowOnlyInfo(List<Link> clickedLinks) {
        // 直接調用 showFlowSetInfo 來顯示 "Flow Information (Live)" 視窗
        if (infoDialog != null) {
            infoDialog.showFlowSetInfo(clickedLinks);
        } else {
            // 如果 infoDialog 為 null，則創建一個新的
            infoDialog = new InfoDialog(this, flows);
            infoDialog.showFlowSetInfo(clickedLinks);
        }
    }
    
    private void showLinkOnlyInfo(List<Link> clickedLinks) {
        // 顯示link信息和使用率長條圖
        if (infoDialog != null) {
            infoDialog.showLinkOnlyInfo(clickedLinks);
        } else {
            // 如果 infoDialog 為 null，則創建一個新的
            infoDialog = new InfoDialog(this, flows);
            infoDialog.showLinkOnlyInfo(clickedLinks);
        }
    }
    


    private double mousePressX = 0;
    private double mousePressY = 0;
    private static final double CLICK_THRESHOLD = 5.0; // 5像素的移動閾值
    
    private void handleMouseReleased(MouseEvent e) {
        // 只處理左鍵釋放事件
        if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) {
            return;
        }
        
        if (isRangeSelecting) {
            // 結束範圍選擇，保持選中的節點
            isRangeSelecting = false;
            // 不需要清除 selectedNodes，讓它們保持選中狀態
            draw();
        } else if (isGroupDragging) {
            // 結束群組拖拽
            isGroupDragging = false;
            saveNodePositions();
        } else if (isDragging && draggedNode != null) {
            // 檢查是否只是點擊（沒有移動）
            double moveDistance = Math.sqrt(
                Math.pow(e.getX() - mousePressX, 2) + 
                Math.pow(e.getY() - mousePressY, 2)
            );
            
            if (moveDistance < CLICK_THRESHOLD) {
                // 這是點擊，顯示node信息
                if (infoDialog != null) {
                    infoDialog.showNodeInfo(draggedNode);
                }
                
                // 保留原有的InfoPanel功能（如果需要的話）
                if (infoPanel != null) {
                    infoPanel.showNode(draggedNode);
                }
                if (showInfoPanelCallback != null) showInfoPanelCallback.accept(Boolean.TRUE);
            }
            
            isDragging = false;
            // 新增：記錄最近移動的 node ip 和時間
            if (draggedNode != null) {
                recentlyMovedNodeIp = draggedNode.ip;
                recentlyMovedTimestamp = System.currentTimeMillis();
            }
            draggedNode = null;
            saveNodePositions();
        } else {
            // 檢查是否點擊了 link（當沒有拖拽 node 時）
            double moveDistance = Math.sqrt(
                Math.pow(e.getX() - mousePressX, 2) + 
                Math.pow(e.getY() - mousePressY, 2)
            );
            
            if (moveDistance < CLICK_THRESHOLD) {
                // 檢查是否點擊了 link
                List<Link> clickedLinks = getLinksAt(e.getX(), e.getY());
                if (!clickedLinks.isEmpty()) {
                    if (showFlows && !showLinks) {
                        // Flow Only Mode
                        showFlowOnlyInfo(clickedLinks);
                    } else if (showLinks) {
                        // Link Only Mode
                        showLinkOnlyInfo(clickedLinks);
                    }
                }
            }
        }
    }

    // 處理右鍵點擊
    private void handleRightClick(MouseEvent e, double actualX, double actualY) {
        // 檢查是否點擊了選中的節點
        org.example.demo2.Node clickedNode = getNodeAt(actualX, actualY);
        if (clickedNode != null && selectedNodes.contains(clickedNode)) {
            showContextMenu(e, clickedNode);
        } else if (!selectedNodes.isEmpty()) {
            // 右鍵點擊空白區域，顯示群組操作選單
            showGroupContextMenu(e);
        } else {
            // 右鍵點擊空白區域，開始視圖平移模式
            // 設置視圖平移標記
            isDragging = false;
            draggedNode = null;
            isRangeSelecting = false;
            isGroupDragging = false;
        }
    }

    // 顯示單個節點的右鍵選單
    private void showContextMenu(MouseEvent e, org.example.demo2.Node node) {
        javafx.scene.control.ContextMenu contextMenu = new javafx.scene.control.ContextMenu();
        
        javafx.scene.control.MenuItem moveItem = new javafx.scene.control.MenuItem("移動節點");
        moveItem.setOnAction(event -> {
            // 單個節點移動（已有功能）
        });
        
        javafx.scene.control.MenuItem selectAllItem = new javafx.scene.control.MenuItem("選擇所有節點");
        selectAllItem.setOnAction(event -> {
            selectedNodes.clear();
            selectedNodes.addAll(nodes);
            draw();
        });
        
        javafx.scene.control.MenuItem clearSelectionItem = new javafx.scene.control.MenuItem("清除選擇");
        clearSelectionItem.setOnAction(event -> {
            selectedNodes.clear();
            draw();
        });
        
        contextMenu.getItems().addAll(moveItem, selectAllItem, clearSelectionItem);
        contextMenu.show(this, e.getScreenX(), e.getScreenY());
    }

    // 顯示群組操作的右鍵選單
    private void showGroupContextMenu(MouseEvent e) {
        javafx.scene.control.ContextMenu contextMenu = new javafx.scene.control.ContextMenu();
        
        javafx.scene.control.MenuItem groupMoveItem = new javafx.scene.control.MenuItem("群組移動 (" + selectedNodes.size() + " 個節點)");
        groupMoveItem.setOnAction(event -> {
            // 群組移動功能（已在拖拽中實現）
        });
        
        javafx.scene.control.MenuItem alignLeftItem = new javafx.scene.control.MenuItem("左對齊");
        alignLeftItem.setOnAction(event -> alignNodes("left"));
        
        javafx.scene.control.MenuItem alignRightItem = new javafx.scene.control.MenuItem("右對齊");
        alignRightItem.setOnAction(event -> alignNodes("right"));
        
        javafx.scene.control.MenuItem alignTopItem = new javafx.scene.control.MenuItem("頂部對齊");
        alignTopItem.setOnAction(event -> alignNodes("top"));
        
        javafx.scene.control.MenuItem alignBottomItem = new javafx.scene.control.MenuItem("底部對齊");
        alignBottomItem.setOnAction(event -> alignNodes("bottom"));
        
        javafx.scene.control.MenuItem distributeHorizontallyItem = new javafx.scene.control.MenuItem("水平分布");
        distributeHorizontallyItem.setOnAction(event -> distributeNodes("horizontal"));
        
        javafx.scene.control.MenuItem distributeVerticallyItem = new javafx.scene.control.MenuItem("垂直分布");
        distributeVerticallyItem.setOnAction(event -> distributeNodes("vertical"));
        
        javafx.scene.control.MenuItem clearSelectionItem = new javafx.scene.control.MenuItem("清除選擇");
        clearSelectionItem.setOnAction(event -> {
            selectedNodes.clear();
            draw();
        });
        
        contextMenu.getItems().addAll(
            groupMoveItem, 
            new javafx.scene.control.SeparatorMenuItem(),
            alignLeftItem, alignRightItem, alignTopItem, alignBottomItem,
            new javafx.scene.control.SeparatorMenuItem(),
            distributeHorizontallyItem, distributeVerticallyItem,
            new javafx.scene.control.SeparatorMenuItem(),
            clearSelectionItem
        );
        contextMenu.show(this, e.getScreenX(), e.getScreenY());
    }

    // 更新範圍選擇
    private void updateRangeSelection() {
        selectedNodes.clear();
        
        double minX = Math.min(rangeStartX, rangeEndX);
        double maxX = Math.max(rangeStartX, rangeEndX);
        double minY = Math.min(rangeStartY, rangeEndY);
        double maxY = Math.max(rangeStartY, rangeEndY);
        
        for (org.example.demo2.Node node : nodes) {
            if (node.x >= minX && node.x <= maxX && node.y >= minY && node.y <= maxY) {
                selectedNodes.add(node);
            }
        }
    }

    // 對齊節點
    private void alignNodes(String alignment) {
        if (selectedNodes.isEmpty()) return;
        
        switch (alignment) {
            case "left":
                int leftX = selectedNodes.stream().mapToInt(n -> n.x).min().orElse(0);
                selectedNodes.forEach(n -> n.x = leftX);
                break;
            case "right":
                int rightX = selectedNodes.stream().mapToInt(n -> n.x).max().orElse(0);
                selectedNodes.forEach(n -> n.x = rightX);
                break;
            case "top":
                int topY = selectedNodes.stream().mapToInt(n -> n.y).min().orElse(0);
                selectedNodes.forEach(n -> n.y = topY);
                break;
            case "bottom":
                int bottomY = selectedNodes.stream().mapToInt(n -> n.y).max().orElse(0);
                selectedNodes.forEach(n -> n.y = bottomY);
                break;
        }
        
        saveNodePositions();
        draw();
    }

    // 分布節點
    private void distributeNodes(String direction) {
        if (selectedNodes.size() < 3) return;
        
        List<org.example.demo2.Node> sortedNodes = new ArrayList<>(selectedNodes);
        
        if ("horizontal".equals(direction)) {
            sortedNodes.sort((a, b) -> Integer.compare(a.x, b.x));
            int minX = sortedNodes.get(0).x;
            int maxX = sortedNodes.get(sortedNodes.size() - 1).x;
            int step = (maxX - minX) / (sortedNodes.size() - 1);
            
            for (int i = 0; i < sortedNodes.size(); i++) {
                sortedNodes.get(i).x = minX + i * step;
            }
        } else if ("vertical".equals(direction)) {
            sortedNodes.sort((a, b) -> Integer.compare(a.y, b.y));
            int minY = sortedNodes.get(0).y;
            int maxY = sortedNodes.get(sortedNodes.size() - 1).y;
            int step = (maxY - minY) / (sortedNodes.size() - 1);
            
            for (int i = 0; i < sortedNodes.size(); i++) {
                sortedNodes.get(i).y = minY + i * step;
            }
        }
        
        saveNodePositions();
        draw();
    }

    private void handleMouseDragged(MouseEvent e) {
        // 右鍵拖拽用於視圖平移
        if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY || 
            (e.getButton() == javafx.scene.input.MouseButton.PRIMARY && e.isControlDown())) {
            // 平移整個視圖
            double deltaX = e.getX() - mousePressX;
            double deltaY = e.getY() - mousePressY;
            
            offsetX += deltaX;
            offsetY += deltaY;
            
            mousePressX = e.getX();
            mousePressY = e.getY();
            
            draw();
            return;
        }
        
        // 只處理左鍵拖拽（節點操作）
        if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) {
            return;
        }
        
        // 轉換鼠標座標到實際座標系統
        double actualX = (e.getX() - offsetX) / scale;
        double actualY = (e.getY() - offsetY) / scale;
        
        if (isRangeSelecting) {
            // 範圍選擇拖拽
            rangeEndX = actualX;
            rangeEndY = actualY;
            updateRangeSelection();
            draw();
        } else if (isGroupDragging && !selectedNodes.isEmpty()) {
            // 群組拖拽
            // 找到第一個選中的節點作為參考點
            org.example.demo2.Node referenceNode = selectedNodes.iterator().next();
            
            // 計算參考節點的新位置
            int newRefX = (int) (actualX - groupDragOffsetX);
            int newRefY = (int) (actualY - groupDragOffsetY);
            
            // 網格吸附功能（只對參考節點進行吸附）
            newRefX = Math.round(newRefX / (float)GRID_SIZE) * GRID_SIZE;
            newRefY = Math.round(newRefY / (float)GRID_SIZE) * GRID_SIZE;
            
            // 計算相對偏移量
            int deltaX = newRefX - referenceNode.x;
            int deltaY = newRefY - referenceNode.y;
            
            // 移動所有選中的節點（保持相對位置，不再單獨吸附網格）
            for (org.example.demo2.Node node : selectedNodes) {
                node.x = node.x + deltaX;
                node.y = node.y + deltaY;
            }
            draw();
        } else if (isDragging && draggedNode != null) {
            // 單個節點拖拽
            int newX = (int) (actualX - dragOffsetX);
            int newY = (int) (actualY - dragOffsetY);
            // 網格吸附功能
            newX = Math.round(newX / (float)GRID_SIZE) * GRID_SIZE;
            newY = Math.round(newY / (float)GRID_SIZE) * GRID_SIZE;
            // 移除邊界限制，允許自由拖拽
            draggedNode.x = newX;
            draggedNode.y = newY;
            draw();
        } else {
            // 在網格區域拖拽進行視圖平移
            double deltaX = e.getX() - mousePressX;
            double deltaY = e.getY() - mousePressY;
            
            offsetX += deltaX;
            offsetY += deltaY;
            
            mousePressX = e.getX();
            mousePressY = e.getY();
            
            draw();
        }
    }

    public void draw() {
        if (DEBUG) System.out.println("[DEBUG] TopologyCanvas.draw() - nodes: " + nodes.size() + ", links: " + links.size() + ", flows: " + flows.size());
        if (DEBUG) System.out.println("[DEBUG] Canvas size: " + getWidth() + "x" + getHeight());
        if (DEBUG) System.out.println("[DEBUG] Offset: (" + offsetX + ", " + offsetY + "), Scale: " + scale);
        
        GraphicsContext gc = getGraphicsContext2D();
        gc.setTransform(1, 0, 0, 1, 0, 0); // 重置
        // 先畫背景色
        if (darkMode) {
            gc.setFill(Color.web("#23272e"));
        } else {
            gc.setFill(Color.WHITE);
        }
        gc.fillRect(0, 0, getWidth(), getHeight());
        gc.save();

        // 應用縮放和平移變換
        gc.translate(offsetX, offsetY);
        gc.scale(scale, scale);
        
        // 添加調試輸出
        if (DEBUG) System.out.println("[DEBUG] TopologyCanvas.draw() - nodes: " + nodes.size() + ", links: " + links.size() + ", flows: " + flows.size());
        if (DEBUG) System.out.println("[DEBUG] Canvas size: " + getWidth() + "x" + getHeight());
        if (DEBUG) System.out.println("[DEBUG] Offset: (" + offsetX + ", " + offsetY + "), Scale: " + scale);
        
        drawGrid(gc);
        Map<String, Integer> linkFlowCount = calculateLinkFlowCounts();
        // 當 showLinks 為 true 時，或者當 showFlows 為 true 且 showLinks 為 false 時（Flow Only 模式），都要畫連接線
        if (showLinks || showFlows) {
            drawLinks(gc, linkFlowCount);
        }
        
        if (showFlows) drawFlows(gc);
        drawNodes(gc);
        
        // 如果有 flicker links，單獨繪製它們（在最後，確保 flicker 在最上層）
        if (!flickerLinks.isEmpty()) {
            if (DEBUG) System.out.println("[FLICKER] Drawing flicker links, count: " + flickerLinks.size() + ", flickerOn: " + flickerOn);
            drawFlickerLinks(gc);
        }
        
        // 繪製範圍選擇框
        if (isRangeSelecting) {
            drawRangeSelection(gc);
        }
        
        drawInfo(gc);
        gc.restore();
    }

    private void drawGrid(GraphicsContext gc) {
        gc.save();
        // 讓網格線更不明顯
        if (darkMode) {
            gc.setStroke(Color.web("#2a2a2a")); // 更暗的顏色
        } else {
            gc.setStroke(Color.web("#f0f0f0")); // 更淺的顏色
        }
        gc.setLineWidth(0.5); // 更細的線條
        gc.setLineDashes(8, 8); // 更長的虛線間距
        
        // 計算網格範圍
        double w = getWidth() / scale;
        double h = getHeight() / scale;
        double startX = -offsetX / scale;
        double startY = -offsetY / scale;
        
        // 計算網格間距
        double gridStep = GRID_SIZE;
        
        // 繪製垂直線
        for (double x = startX - (startX % gridStep); x < startX + w; x += gridStep) {
            gc.strokeLine(x, startY, x, startY + h);
        }
        
        // 繪製水平線
        for (double y = startY - (startY % gridStep); y < startY + h; y += gridStep) {
            gc.strokeLine(startX, y, startX + w, y);
        }
        
        gc.setLineDashes((double[]) null);
        gc.restore();
    }

    private Map<String, Integer> calculateLinkFlowCounts() {
        Map<String, Integer> linkFlowCount = new HashMap<>();
        for (int i = 0; i < flows.size(); i++) {
            if (flowPos[i] > 1 || flowPos[i] <= 0) continue;
            Flow flow = flows.get(i);
            List<String> path = flow.pathNodes;
            if (path == null || path.size() < 2) continue; // 防呆：path 為 null 或長度不足
            boolean canPass = true;
            for (int j = 0; j < path.size() - 1; j++) {
                Link link = getLinkBetween(path.get(j), path.get(j + 1));
                if (link == null || !link.is_up || !link.is_enabled) {
                    canPass = false;
                    break;
                }
                org.example.demo2.Node src = getNodeByIp(path.get(j));
                org.example.demo2.Node tgt = getNodeByIp(path.get(j + 1));
                if (src == null || tgt == null || !src.is_enabled || !src.is_up || !tgt.is_enabled || !tgt.is_up) {
                    canPass = false;
                    break;
                }
            }
            if (!canPass) continue;
            int seg = (int) (flowPos[i] * (path.size() - 1));
            if (seg >= path.size() - 1) seg = path.size() - 2;
            if (seg < 0 || seg + 1 >= path.size()) continue; // 防呆：避免越界
            String a = path.get(seg), b = path.get(seg + 1);
            String key = a.compareTo(b) < 0 ? a + "," + b : b + "," + a;
            linkFlowCount.put(key, linkFlowCount.getOrDefault(key, 0) + 1);
        }
        return linkFlowCount;
    }

    private void drawLinks(GraphicsContext gc, Map<String, Integer> linkFlowCount) {
        if (showLinks && !showFlows) {
            // Link Only模式：根據links清單的方向畫線和箭頭，支持雙向不同狀態
            for (Link link : links) {
                // 檢查 link 是否可見
                String linkKey = generateLinkKey(link.source, link.target);
                if (!isLinkVisible(linkKey)) continue;
                
                Node src = getNodeByIp(link.source);
                Node tgt = getNodeByIp(link.target);
                if (src == null || tgt == null) continue;
                double dx = tgt.x - src.x;
                double dy = tgt.y - src.y;
                double length = Math.sqrt(dx * dx + dy * dy);
                if (length == 0) continue;
                double perpX = -dy / length;
                double perpY = dx / length;
                double offset = 4; // 增加offset以支持雙向顯示
                
                // ✅ FIX: 繪製正向 link (src → tgt)
                Color colorAB = (link.is_up && link.is_enabled) 
                    ? getUtilizationColor(link.link_bandwidth_utilization_percent) 
                    : Color.LIGHTGRAY;
                gc.setStroke(colorAB);
                gc.setLineWidth(4.0);
                double x1 = src.x + perpX * offset;
                double y1 = src.y + perpY * offset;
                double x2 = tgt.x + perpX * offset;
                double y2 = tgt.y + perpY * offset;
                gc.strokeLine(x1, y1, x2, y2);
                drawArrow(gc, x1, y1, x2, y2, colorAB);
                
                // ✅ FIX: 繪製反向 link (tgt → src)
                Link linkBA = getLinkBetween(link.target, link.source);
                if (linkBA != null) {
                    Color colorBA = (linkBA.is_up && linkBA.is_enabled)
                        ? getUtilizationColor(linkBA.link_bandwidth_utilization_percent)
                        : Color.LIGHTGRAY;
                    gc.setStroke(colorBA);
                    gc.setLineWidth(4.0);
                    double x3 = src.x - perpX * offset;
                    double y3 = src.y - perpY * offset;
                    double x4 = tgt.x - perpX * offset;
                    double y4 = tgt.y - perpY * offset;
                    // ✅ 反向線和箭頭應該從tgt到src（x4,y4 到 x3,y3）
                    gc.strokeLine(x4, y4, x3, y3);
                    drawArrow(gc, x4, y4, x3, y3, colorBA);
                }
            }
            return;
        }
        // 其餘模式維持原本偏移雙線顯示
        for (Link link : links) {
            // 檢查 link 是否可見
            String linkKey = generateLinkKey(link.source, link.target);
            if (!isLinkVisible(linkKey)) continue;
            Node src = getNodeByIp(link.source);
            Node tgt = getNodeByIp(link.target);
            if (src == null || tgt == null) continue;
            double dx = tgt.x - src.x;
            double dy = tgt.y - src.y;
            double length = Math.sqrt(dx * dx + dy * dy);
            if (length == 0) continue;
            double perpX = -dy / length;
            double perpY = dx / length;
            double offset = 4;
            String key = link.source.compareTo(link.target) < 0 ? link.source + "," + link.target : link.target + "," + link.source ;
            boolean isFlicker = flickerLinks.contains(key);
            
            // 如果是閃爍狀態的link，跳過繪製原本的線條，讓drawFlickerLinks處理
            if (isFlicker && flickerOn) {
                continue;
            }
            
            Color colorAB;
            double lineWidth;
            if (showFlows && !showLinks) {
                // Flow Only模式：隱藏原本的link線條，不繪製任何線條
                continue;
            } else {
                double percentAB = link.link_bandwidth_utilization_percent;
                colorAB = (link.is_up && link.is_enabled)
                    ? getUtilizationColor(percentAB)
                    : Color.LIGHTGRAY;
                lineWidth = 4.0;
            }
            gc.setStroke(colorAB);
            gc.setLineWidth(lineWidth);
            gc.strokeLine(
                src.x + perpX * offset, src.y + perpY * offset,
                tgt.x + perpX * offset, tgt.y + perpY * offset
            );
            // 反方向
            Link linkBA = getLinkBetween(link.target, link.source);
            if (linkBA != null) {
                boolean isFlickerBA = flickerLinks.contains(key);
                
                // 如果是閃爍狀態的link，跳過繪製原本的線條
                if (isFlickerBA && flickerOn) {
                    continue;
                }
                
                Color colorBA;
                if (showFlows && !showLinks) {
                    // Flow Only模式：隱藏原本的link線條，不繪製任何線條
                    continue;
                } else {
                    double percentBA = linkBA.link_bandwidth_utilization_percent;
                    colorBA = (linkBA.is_up && linkBA.is_enabled)
                        ? getUtilizationColor(percentBA)
                        : Color.LIGHTGRAY;
                    lineWidth = 4.0;
                }
                gc.setStroke(colorBA);
                gc.setLineWidth(lineWidth);
                gc.strokeLine(
                    src.x - perpX * offset, src.y - perpY * offset,
                    tgt.x - perpX * offset, tgt.y - perpY * offset
                );
            }
        }
    }
    
    // 美化：專門繪製 flicker 的 link - 使用動態閃爍效果和漸變
    private void drawFlickerLinks(GraphicsContext gc) {
        if (!flickerOn || flickerLinks.isEmpty()) {
            return;
        }
        
        // 使用更細的線條和更美的顏色
        double rectWidth = 8; // 減少寬度，讓flicker更細
        double strokeWidth = 2; // 更細的邊框
        
        // 創建動態漸變效果 - 使用動畫透明度
        // 使用flow的顏色，如果沒有則使用默認黃色
        Color baseColor = (flickeredFlowColor != null) ? flickeredFlowColor : Color.YELLOW;
        
        // 從flow顏色獲取RGB值
        int r = (int) (baseColor.getRed() * 255);
        int g = (int) (baseColor.getGreen() * 255);
        int b = (int) (baseColor.getBlue() * 255);
        
        // 創建三種漸變顏色：中心色、邊緣色、邊框色
        Color centerColor = Color.rgb(r, g, b, flickerAlpha * 0.8); // flow顏色，動態透明度
        
        // 邊緣色：稍微調暗（乘以0.7）
        Color edgeColor = Color.rgb(
            Math.max(0, (int)(r * 0.7)), 
            Math.max(0, (int)(g * 0.7)), 
            Math.max(0, (int)(b * 0.7)), 
            flickerAlpha * 0.6
        );
        
        // 邊框色：稍微調亮（加20，但不超過255）
        Color strokeColor = Color.rgb(
            Math.min(255, r + 20), 
            Math.min(255, g + 20), 
            Math.min(255, b + 20), 
            flickerAlpha
        );
        
        for (String linkKey : flickerLinks) {
            String[] parts = linkKey.split(",");
            if (parts.length != 2) continue;
            
            String sourceIp = parts[0];
            String targetIp = parts[1];
            
            Node src = getNodeByIp(sourceIp);
            Node tgt = getNodeByIp(targetIp);
            if (src == null || tgt == null) {
                continue;
            }
            
            // 計算連接線的方向
            double dx = tgt.x - src.x;
            double dy = tgt.y - src.y;
            double length = Math.sqrt(dx * dx + dy * dy);
            if (length == 0) continue;
            
            // 計算垂直於連接線的方向
            double perpX = -dy / length;
            double perpY = dx / length;
            
            // 計算長方形的四個頂點
            double[] xPoints = new double[4];
            double[] yPoints = new double[4];
            
            xPoints[0] = src.x + perpX * rectWidth/2;
            yPoints[0] = src.y + perpY * rectWidth/2;
            xPoints[1] = src.x - perpX * rectWidth/2;
            yPoints[1] = src.y - perpY * rectWidth/2;
            xPoints[2] = tgt.x - perpX * rectWidth/2;
            yPoints[2] = tgt.y - perpY * rectWidth/2;
            xPoints[3] = tgt.x + perpX * rectWidth/2;
            yPoints[3] = tgt.y + perpY * rectWidth/2;
            
            // 繪製漸變填充 - 創建多層次效果
            // 外層：較大的半透明區域
            double outerWidth = rectWidth + 4;
            double[] outerXPoints = new double[4];
            double[] outerYPoints = new double[4];
            
            outerXPoints[0] = src.x + perpX * outerWidth/2;
            outerYPoints[0] = src.y + perpY * outerWidth/2;
            outerXPoints[1] = src.x - perpX * outerWidth/2;
            outerYPoints[1] = src.y - perpY * outerWidth/2;
            outerXPoints[2] = tgt.x - perpX * outerWidth/2;
            outerYPoints[2] = tgt.y - perpY * outerWidth/2;
            outerXPoints[3] = tgt.x + perpX * outerWidth/2;
            outerYPoints[3] = tgt.y + perpY * outerWidth/2;
            
            // 繪製外層光暈 - 使用flow顏色
            gc.setFill(Color.rgb(r, g, b, flickerAlpha * 0.3));
            gc.fillPolygon(outerXPoints, outerYPoints, 4);
            
            // 繪製內層主體
            gc.setFill(centerColor);
            gc.fillPolygon(xPoints, yPoints, 4);
            
            // 繪製邊框
            gc.setStroke(strokeColor);
            gc.setLineWidth(strokeWidth);
            gc.strokePolygon(xPoints, yPoints, 4);
            
            // 添加動態發光效果 - 在兩端添加小圓點 - 使用flow顏色
            double glowRadius = 3 + Math.sin(flickerPulse) * 1; // 脈衝大小的發光效果
            gc.setFill(Color.rgb(r, g, b, flickerAlpha * 0.9));
            gc.fillOval(src.x - glowRadius, src.y - glowRadius, glowRadius * 2, glowRadius * 2);
            gc.fillOval(tgt.x - glowRadius, tgt.y - glowRadius, glowRadius * 2, glowRadius * 2);
        }
    }

    private void drawNodes(GraphicsContext gc) {
        // 動態調整節點大小：節點數越多，size 越小
        double minSize = 10;
        double maxSize = 16;
        double size = Math.max(minSize, maxSize - nodes.size() * 0.2);
        
        if (DEBUG) {
            System.out.println("[DEBUG] drawNodes() - Drawing " + nodes.size() + " nodes, size: " + size);
            
            if (nodes.isEmpty()) {
                System.out.println("[DEBUG] WARNING: No nodes to draw!");
                return;
            }
            
            // 输出前3个节点的详细信息
            for (int i = 0; i < Math.min(3, nodes.size()); i++) {
                Node node = nodes.get(i);
                System.out.println("[DEBUG] Node " + i + ": " + node.name + " at (" + node.x + ", " + node.y + "), type=" + node.type);
            }
        }
        
        if (nodes.isEmpty()) {
            return;
        }
        
        for (Node node : nodes) {
            // 根據狀態決定顏色（只看is_up和is_enabled，不看layer）
            Color nodeColor;
            if (!node.is_enabled || !node.is_up) {
                // 任何節點（switch或host），只要is_enabled或is_up為false就顯示紅色
                nodeColor = Color.RED;
            } else {
                // 正常狀態下，所有節點都顯示淺綠色
                nodeColor = Color.LIGHTGREEN;
            }
            
            // 如果節點被選中，改變顏色和邊框
            if (selectedNodes.contains(node)) {
                nodeColor = Color.CYAN; // 選中的節點顯示青色
                gc.setFill(nodeColor);
                gc.setStroke(Color.BLUE);
                gc.setLineWidth(3); // 選中的節點邊框更粗
            } else {
                gc.setFill(nodeColor);
                gc.setStroke(Color.BLACK);
                gc.setLineWidth(2);
            }
            
            // 根據type決定形狀（type存儲的是vertex_type的值）
            if ("1".equals(node.type)) {
                // Host節點（type="1"，即vertex_type=1）繪製為方形
                gc.fillRect(node.x - size, node.y - size, size * 2, size * 2);
                gc.strokeRect(node.x - size, node.y - size, size * 2, size * 2);
            } else {
                // Switch節點（type="0"，即vertex_type=0）繪製為圓形
                gc.fillOval(node.x - size, node.y - size, size * 2, size * 2);
                gc.strokeOval(node.x - size, node.y - size, size * 2, size * 2);
            }
            
            // Draw node label inside the shape
            if (!node.is_enabled || !node.is_up) {
                gc.setFill(Color.WHITE); // 不可用節點上的文字為白色
            } else {
                gc.setFill(Color.BLACK); // 正常節點上的文字為黑色
            }
            gc.setFont(Font.font("Arial", 8));
            String label = node.name.length() > 8 ? node.name.substring(0, 8) : node.name;
            // Calculate text position to center it in the shape
            double textWidth = gc.getFont().getSize() * label.length() * 0.6; // Approximate text width
            double textX = node.x - textWidth / 2;
            double textY = node.y + 4; // Slightly below center for better visual balance
            gc.fillText(label, textX, textY);
        }
    }

    // 繪製範圍選擇框
    private void drawRangeSelection(GraphicsContext gc) {
        gc.save();
        
        // 計算選擇框的座標
        double x = Math.min(rangeStartX, rangeEndX);
        double y = Math.min(rangeStartY, rangeEndY);
        double width = Math.abs(rangeEndX - rangeStartX);
        double height = Math.abs(rangeEndY - rangeStartY);
        
        // 繪製半透明的選擇框
        gc.setFill(Color.rgb(0, 100, 255, 0.2)); // 半透明藍色填充
        gc.fillRect(x, y, width, height);
        
        // 繪製選擇框邊框
        gc.setStroke(Color.BLUE);
        gc.setLineWidth(2);
        gc.setLineDashes(5, 5); // 虛線邊框
        gc.strokeRect(x, y, width, height);
        
        // 重置線條樣式
        gc.setLineDashes((double[]) null);
        
        gc.restore();
    }

    /**
     * 在Flow Only模式下繪製簡單的連接線
     */
    private void drawSimpleConnectionLines(GraphicsContext gc) {
        // 收集所有連接（不管有沒有flow）
        Set<String> allConnections = new HashSet<>();
        
        for (Link link : links) {
            // 生成連接key（無方向性）
            String connectionKey = generateLinkKey(link.source, link.target);
            allConnections.add(connectionKey);
        }
        
        // 繪製所有連接線
        for (String connectionKey : allConnections) {
            String[] parts = connectionKey.split(",");
            if (parts.length == 2) {
                String sourceIp = parts[0];
                String targetIp = parts[1];
                
                Node srcNode = getNodeByIp(sourceIp);
                Node tgtNode = getNodeByIp(targetIp);
                
                if (srcNode != null && tgtNode != null) {
                    // 繪製簡單的連接線
                    // 在 Dark Mode 下使用白色，在 Light Mode 下使用黑色
                    if (darkMode) {
                        gc.setStroke(Color.WHITE);
                    } else {
                        gc.setStroke(Color.BLACK);
                    }
                    gc.setLineWidth(1.0);
                    gc.strokeLine(srcNode.x, srcNode.y, tgtNode.x, tgtNode.y);
                }
            }
        }
    }

    // 判断当前是否为 playback 模式的标志
    private boolean isPlaybackMode = false;
    
    public void setPlaybackMode(boolean playbackMode) {
        this.isPlaybackMode = playbackMode;
        if (DEBUG) System.out.println("[DEBUG] Canvas playback mode set to: " + playbackMode);
    }
    
    public boolean isPlaybackMode() {
        return isPlaybackMode;
    }
    
    private void drawFlows(GraphicsContext gc) {
        if (!showFlows) {
            if (DEBUG) System.out.println("[DEBUG] drawFlows: showFlows is false, skipping");
            return;
        }
        
        if (DEBUG) System.out.println("[DEBUG] drawFlows: showFlows=true, showLinks=" + showLinks + ", playbackMode=" + isPlaybackMode);
        
        // Flow Only模式：繪製簡單的連接線
        if (showFlows && !showLinks) {
            if (DEBUG) System.out.println("[DEBUG] drawFlows: Drawing simple connection lines (Flow Only mode)");
            drawSimpleConnectionLines(gc);
        }
        
        // 根据模式调用不同的绘制方法
        if (isPlaybackMode) {
            drawPlaybackFlows(gc);
        } else {
            drawRealtimeFlows(gc);
        }
    }
    
    /**
     * 绘制 Real-time 模式的 flows
     * Real-time 数据来自 API，flow 对象已经完整，可以直接使用
     */
    private void drawRealtimeFlows(GraphicsContext gc) {
        if (DEBUG) System.out.println("[DEBUG] drawRealtimeFlows: Processing " + links.size() + " links");
        if (DEBUG) System.out.println("[DEBUG] drawRealtimeFlows: Top-K enabled=" + getTopKEnabled() + 
                                     ", visibleFlowIndices.size=" + visibleFlowIndices.size() + 
                                     ", total flows=" + flows.size());
        
        int linksWithFlows = 0;
        int totalFlowsProcessed = 0;
        int flowsFiltered = 0;
        
        for (Link link : links) {
            if (link.flow_set == null || link.flow_set.isEmpty()) {
                continue;
            }
            
            if (DEBUG) System.out.println("[DEBUG] drawRealtimeFlows: Link " + link.source + " -> " + link.target + " has " + link.flow_set.size() + " flows");
            linksWithFlows++;
            
            // 找到源節點和目標節點
            Node srcNode = getNodeByIp(link.source);
            Node tgtNode = getNodeByIp(link.target);
            if (srcNode == null || tgtNode == null) continue;
            
            // Real-time: 按方向分组 flows
            List<Flow> forwardFlows = new ArrayList<>();  // link.source -> link.target
            List<Flow> reverseFlows = new ArrayList<>();  // link.target -> link.source
            
            for (Flow flowInSet : link.flow_set) {
                totalFlowsProcessed++;
                
                // 检查 flow 是否在全局 flows 列表中
                int flowIndex = findFlowIndex(flowInSet);
                if (flowIndex < 0) {
                    // Flow不在全局flows列表中（可能只在flow_set中，没有对应的detected flow data）
                    // 跳过以避免显示"幽灵flow"
                    flowsFiltered++;
                    continue;
                }
                
                // 检查 flow 是否可见（应用 flow filter）
                if (!isFlowVisible(flowIndex)) {
                    flowsFiltered++;
                    continue; // 跳过不可见的 flow
                }
                
                // 判断 flow 的方向：优先使用pathNodes（与Playback模式一致）
                boolean directionFound = false;
                
                if (flowInSet.pathNodes != null && flowInSet.pathNodes.size() >= 2) {
                    // 遍历 pathNodes，找到包含 link.source 和 link.target 的相邻节点对
                    // Real-time模式下，pathNodes已经是标准IP格式（在convertDetectedFlows中已转换）
                    for (int i = 0; i < flowInSet.pathNodes.size() - 1; i++) {
                        String node1 = flowInSet.pathNodes.get(i);
                        String node2 = flowInSet.pathNodes.get(i + 1);
                        
                        // pathNodes在Real-time模式下已经是标准IP格式，直接使用
                        String node1Ip = node1;
                        String node2Ip = node2;
                        
                        // 正向：pathNodes 顺序是 [link.source, link.target]
                        if (link.source.equals(node1Ip) && link.target.equals(node2Ip)) {
                            forwardFlows.add(flowInSet);
                            directionFound = true;
                            if (DEBUG) System.out.println("[DEBUG] drawRealtimeFlows: FORWARD Flow (from path) " + flowInSet.srcIp + ":" + flowInSet.srcPort + 
                                             " -> " + flowInSet.dstIp + ":" + flowInSet.dstPort + 
                                             ", path segment: " + node1Ip + " -> " + node2Ip);
                            break;
                        }
                        // 反向：pathNodes 顺序是 [link.target, link.source]
                        else if (link.target.equals(node1Ip) && link.source.equals(node2Ip)) {
                            reverseFlows.add(flowInSet);
                            directionFound = true;
                            if (DEBUG) System.out.println("[DEBUG] drawRealtimeFlows: REVERSE Flow (from path) " + flowInSet.srcIp + ":" + flowInSet.srcPort + 
                                             " -> " + flowInSet.dstIp + ":" + flowInSet.dstPort + 
                                             ", path segment: " + node1Ip + " -> " + node2Ip);
                            break;
                        }
                    }
                }
                
                // 如果无法从 path 中确定方向，使用 srcIp 作为备用方案
                if (!directionFound) {
                    if (flowInSet.srcIp.equals(link.source)) {
                        forwardFlows.add(flowInSet);
                        if (DEBUG) System.out.println("[DEBUG] drawRealtimeFlows: FORWARD Flow (from srcIp) " + flowInSet.srcIp + ":" + flowInSet.srcPort + 
                                         " -> " + flowInSet.dstIp + ":" + flowInSet.dstPort);
                    } else if (flowInSet.srcIp.equals(link.target)) {
                        reverseFlows.add(flowInSet);
                        if (DEBUG) System.out.println("[DEBUG] drawRealtimeFlows: REVERSE Flow (from srcIp) " + flowInSet.srcIp + ":" + flowInSet.srcPort + 
                                         " -> " + flowInSet.dstIp + ":" + flowInSet.dstPort);
                    } else {
                        // 默认为正向，但记录警告
                        forwardFlows.add(flowInSet);
                        if (DEBUG) System.out.println("[DEBUG] drawRealtimeFlows: UNKNOWN direction, treating as FORWARD: " + flowInSet.srcIp + ":" + flowInSet.srcPort + 
                                         " -> " + flowInSet.dstIp + ":" + flowInSet.dstPort + 
                                         " on link " + link.source + " -> " + link.target);
                    }
                }
            }
            
            // 繪製正向 flows
            if (!forwardFlows.isEmpty()) {
                double forwardSendingRate = 0;
                for (Flow flow : forwardFlows) {
                    forwardSendingRate += flow.estimatedFlowSendingRateBpsInTheLastSec;
                }
                drawMixedFlowAnimation(gc, srcNode, tgtNode, forwardFlows, forwardSendingRate, link);
            }
            
            // 繪製反向 flows
            if (!reverseFlows.isEmpty()) {
                double reverseSendingRate = 0;
                for (Flow flow : reverseFlows) {
                    reverseSendingRate += flow.estimatedFlowSendingRateBpsInTheLastSec;
                }
                // 注意：反向流动，所以 srcNode 和 tgtNode 交换
                drawMixedFlowAnimation(gc, tgtNode, srcNode, reverseFlows, reverseSendingRate, link);
            }
        }
        
        if (DEBUG) System.out.println("[DEBUG] drawRealtimeFlows: Processed " + linksWithFlows + " links with flows");
        System.out.println("[TOP-K] drawRealtimeFlows: Processed=" + totalFlowsProcessed + 
                         ", Filtered=" + flowsFiltered + 
                         ", Shown=" + (totalFlowsProcessed - flowsFiltered));
    }
    
    /**
     * 绘制 Playback 模式的 flows
     * Playback 数据来自历史文件，数据格式不同，需要特殊处理
     */
    private void drawPlaybackFlows(GraphicsContext gc) {
        if (DEBUG) System.out.println("[DEBUG] ========== drawPlaybackFlows START ==========");
        if (DEBUG) System.out.println("[DEBUG] drawPlaybackFlows: Processing " + links.size() + " links");
        if (DEBUG) System.out.println("[DEBUG] drawPlaybackFlows: Top-K enabled=" + getTopKEnabled() + 
                                     ", visibleFlowIndices.size=" + visibleFlowIndices.size() + 
                                     ", total flows=" + flows.size());
        
        int linksWithFlows = 0;
        int nodesNotFound = 0;
        int actuallyDrawn = 0;
        int totalFlowsProcessed = 0;
        int flowsFiltered = 0;
        
        for (Link link : links) {
            if (link.flow_set == null || link.flow_set.isEmpty()) {
                continue;
            }
            
            if (DEBUG) System.out.println("[DEBUG] drawPlaybackFlows: Link " + link.source + " -> " + link.target + " has " + link.flow_set.size() + " flows");
            linksWithFlows++;
            
            // 找到源節點和目標節點
            Node srcNode = getNodeByIp(link.source);
            Node tgtNode = getNodeByIp(link.target);
            if (srcNode == null || tgtNode == null) {
                if (DEBUG) System.out.println("[DEBUG] drawPlaybackFlows: WARNING - Node not found! srcNode=" + (srcNode != null) + ", tgtNode=" + (tgtNode != null) + 
                                         " for link " + link.source + " -> " + link.target);
                nodesNotFound++;
                continue;
            }
            
            // Playback: 按方向分组 flows - 根据 flow 的 path 判断在这个 link 上的实际方向
            List<Flow> forwardFlows = new ArrayList<>();  // link.source -> link.target
            List<Flow> reverseFlows = new ArrayList<>();  // link.target -> link.source
            
            for (Flow flowInSet : link.flow_set) {
                totalFlowsProcessed++;
                // 检查 flow 是否可见（应用 flow filter）
                int flowIndex = findFlowIndex(flowInSet);
                if (flowIndex >= 0 && !isFlowVisible(flowIndex)) {
                    flowsFiltered++;
                    if (DEBUG) System.out.println("[DEBUG] drawPlaybackFlows: Flow " + flowInSet.srcIp + ":" + flowInSet.srcPort + 
                                     " -> " + flowInSet.dstIp + ":" + flowInSet.dstPort + " is filtered out");
                    continue; // 跳过不可见的 flow
                }
                
                // 通过检查 flow 的 pathNodes 来判断在这个 link 上的方向
                boolean directionFound = false;
                
                if (flowInSet.pathNodes != null && flowInSet.pathNodes.size() >= 2) {
                    // 遍历 pathNodes，找到包含 link.source 和 link.target 的相邻节点对
                    for (int i = 0; i < flowInSet.pathNodes.size() - 1; i++) {
                        String node1 = flowInSet.pathNodes.get(i);
                        String node2 = flowInSet.pathNodes.get(i + 1);
                        
                        // Convert node IDs to IP addresses for comparison
                        // pathNodes may contain DPID (like "4", "7") or integer IP format (like "1828716554")
                        // while link.source and link.target are in standard IP format (like "192.168.1.1")
                        String node1Ip = convertNodeIdToIp(node1);
                        String node2Ip = convertNodeIdToIp(node2);
                        
                        // 检查这一对节点是否对应当前 link（使用精确匹配）
                        boolean node1MatchesSource = link.source.equals(node1Ip);
                        boolean node1MatchesTarget = link.target.equals(node1Ip);
                        boolean node2MatchesSource = link.source.equals(node2Ip);
                        boolean node2MatchesTarget = link.target.equals(node2Ip);
                        
                        // 正向：pathNodes 顺序是 [link.source, link.target]
                        if (node1MatchesSource && node2MatchesTarget) {
                            forwardFlows.add(flowInSet);
                            directionFound = true;
                            if (DEBUG) System.out.println("[DEBUG] drawPlaybackFlows: FORWARD Flow (from path) " + flowInSet.srcIp + ":" + flowInSet.srcPort + 
                                             " -> " + flowInSet.dstIp + ":" + flowInSet.dstPort + 
                                             ", path segment: " + node1 + "(" + node1Ip + ") -> " + node2 + "(" + node2Ip + ")");
                            break;
                        }
                        // 反向：pathNodes 顺序是 [link.target, link.source]
                        else if (node1MatchesTarget && node2MatchesSource) {
                            reverseFlows.add(flowInSet);
                            directionFound = true;
                            if (DEBUG) System.out.println("[DEBUG] drawPlaybackFlows: REVERSE Flow (from path) " + flowInSet.srcIp + ":" + flowInSet.srcPort + 
                                             " -> " + flowInSet.dstIp + ":" + flowInSet.dstPort + 
                                             ", path segment: " + node1 + "(" + node1Ip + ") -> " + node2 + "(" + node2Ip + ")");
                            break;
                        }
                    }
                }
                
                // 如果无法从 path 中确定方向，则使用 flow 的 srcIp 作为备用方案
                if (!directionFound) {
                    if (flowInSet.srcIp.equals(link.source)) {
                        forwardFlows.add(flowInSet);
                        if (DEBUG) System.out.println("[DEBUG] drawPlaybackFlows: FORWARD Flow (from srcIp) " + flowInSet.srcIp + ":" + flowInSet.srcPort + 
                                         " -> " + flowInSet.dstIp + ":" + flowInSet.dstPort);
                    } else if (flowInSet.srcIp.equals(link.target)) {
                        reverseFlows.add(flowInSet);
                        if (DEBUG) System.out.println("[DEBUG] drawPlaybackFlows: REVERSE Flow (from srcIp) " + flowInSet.srcIp + ":" + flowInSet.srcPort + 
                                         " -> " + flowInSet.dstIp + ":" + flowInSet.dstPort);
                    } else {
                        // 默认为正向
                        forwardFlows.add(flowInSet);
                        if (DEBUG) System.out.println("[DEBUG] drawPlaybackFlows: UNKNOWN direction, treating as FORWARD: " + flowInSet.srcIp + ":" + flowInSet.srcPort + 
                                         " -> " + flowInSet.dstIp + ":" + flowInSet.dstPort);
                    }
                }
            }
            
            // 繪製正向 flows
            if (!forwardFlows.isEmpty()) {
                double forwardSendingRate = 0;
                for (Flow flow : forwardFlows) {
                    forwardSendingRate += flow.estimatedFlowSendingRateBpsInTheLastSec;
                }
                
                if (DEBUG) System.out.println("[DEBUG] drawPlaybackFlows: Drawing " + forwardFlows.size() + " FORWARD flows with totalRate=" + forwardSendingRate);
                drawMixedFlowAnimation(gc, srcNode, tgtNode, forwardFlows, forwardSendingRate, link);
                actuallyDrawn++;
            }
            
            // 繪製反向 flows
            if (!reverseFlows.isEmpty()) {
                double reverseSendingRate = 0;
                for (Flow flow : reverseFlows) {
                    reverseSendingRate += flow.estimatedFlowSendingRateBpsInTheLastSec;
                }
                
                if (DEBUG) System.out.println("[DEBUG] drawPlaybackFlows: Drawing " + reverseFlows.size() + " REVERSE flows with totalRate=" + reverseSendingRate);
                // 注意：反向流动，所以 srcNode 和 tgtNode 交换
                drawMixedFlowAnimation(gc, tgtNode, srcNode, reverseFlows, reverseSendingRate, link);
                actuallyDrawn++;
            }
        }
        
        if (DEBUG) {
            System.out.println("[DEBUG] drawPlaybackFlows: Summary:");
            System.out.println("[DEBUG]   Total links: " + links.size());
            System.out.println("[DEBUG]   Links with flows: " + linksWithFlows);
            System.out.println("[DEBUG]   Nodes not found: " + nodesNotFound);
            System.out.println("[DEBUG]   Actually drawn: " + actuallyDrawn);
            System.out.println("[DEBUG] ========== drawPlaybackFlows END ==========");
        }
        
        System.out.println("[TOP-K] drawPlaybackFlows: Processed=" + totalFlowsProcessed + 
                         ", Filtered=" + flowsFiltered + 
                         ", Shown=" + (totalFlowsProcessed - flowsFiltered));
    }
    
    /**
     * 從 detected flow data 中找到與 flow_set 中的 flow 匹配的完整 flow 信息
     * 匹配條件：src_ip + dst_ip + src_port + dst_port + protocol_number 完全匹配
     */
    private Flow findCompleteFlowInfo(Flow flowInSet) {
        for (Flow detectedFlow : flows) {
            // 檢查五個字段是否完全匹配
            if (detectedFlow.srcIp.equals(flowInSet.srcIp) &&
                detectedFlow.dstIp.equals(flowInSet.dstIp) &&
                detectedFlow.srcPort == flowInSet.srcPort &&
                detectedFlow.dstPort == flowInSet.dstPort &&
                detectedFlow.protocolId == flowInSet.protocolId) {
                return detectedFlow;
            }
        }
        return null; // 未找到匹配的完整 flow 信息
    }

    private void drawMixedFlowAnimation(GraphicsContext gc, Node srcNode, Node tgtNode, List<Flow> flows, double totalSendingRate, Link link) {
        // 計算 link 的方向和長度
        double dx = tgtNode.x - srcNode.x;
        double dy = tgtNode.y - srcNode.y;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length == 0) return;
        
        // 使用link的utilization百分比來計算動畫長度
        double totalUtilization = link.link_bandwidth_utilization_percent / 100.0; // 轉換為0-1範圍
        totalUtilization = Math.max(0.1, Math.min(1.0, totalUtilization)); // 確保在10%-100%範圍內
        
        // 計算動畫應該顯示的長度（基於link utilization）
        double animationLength = length * totalUtilization;
        
        // 動畫參數
        double segmentDuration = flowMoveSpeed;
        double segProgress = (animationTime % segmentDuration) / segmentDuration;
        
        // 繪製多個移動的長方形
        double rectLength = 8; // 每個長方形的長度
        double rectWidth = 4;   // 每個長方形的寬度
        double spacing = rectLength * 1.2; // 長方形之間的間距
        // 修正：確保動畫長度準確反映使用率
        int numRectangles = Math.max(1, (int) Math.floor(animationLength / spacing));
        
        // 計算垂直偏移
        double perpX = dy / length;
        double perpY = -dx / length;
        double offset = -3.5; // 固定偏移量
        
        // 計算每個 flow 的顏色和比例
        List<Color> flowColors = new ArrayList<>();
        List<Double> flowRatios = new ArrayList<>();
        
        if (flows.isEmpty()) {
            // 沒有 flow，使用灰色
            flowColors.add(Color.GRAY);
            flowRatios.add(1.0);
        } else {
            // 計算每個 flow 的顏色和發送速率比例
            double totalRate = 0;
            for (Flow flow : flows) {
                totalRate += flow.estimatedFlowSendingRateBpsInTheLastSec;
            }
            
            // 如果總發送速率為 0，則平均分配
            if (totalRate == 0) {
                totalRate = flows.size();
            }
            
            for (Flow flow : flows) {
                // ✅ 使用 hash five-tuple + 穩定 palette 取得基礎色，
                //   再依照 sending rate 做亮度強調，讓大流更鮮豔
                Color flowColor = getEmphasizedColorForFlow(flow);
                flowColors.add(flowColor);
                
                if (DEBUG) System.out.println("[DEBUG] Flow color assignment (hash-based + emphasis): " + 
                    flow.srcIp + ":" + flow.srcPort + " -> " + flow.dstIp + ":" + flow.dstPort +
                    " rate=" + flow.estimatedFlowSendingRateBpsInTheLastSec + " color=" + flowColor);
                
                // 計算該 flow 的比例
                double ratio = totalRate > 0 ? flow.estimatedFlowSendingRateBpsInTheLastSec / totalRate : 1.0 / flows.size();
                flowRatios.add(ratio);
            }
        }
        
        // 繪製長方形
        for (int j = 0; j < numRectangles; j++) {
            double flowProgress = segProgress - (j * spacing / length);
            
            // 實現連續流動
            if (flowProgress < 0) {
                flowProgress = 1.0 + flowProgress;
            }
            
            // 計算長方形位置
            double centerX = srcNode.x + (tgtNode.x - srcNode.x) * flowProgress;
            double centerY = srcNode.y + (tgtNode.y - srcNode.y) * flowProgress;
            centerX += perpX * offset;
            centerY += perpY * offset;
            
            // 繪製長方形
            double dirX = dx / length;
            double dirY = dy / length;
            double[] xPoints = new double[4];
            double[] yPoints = new double[4];
            
            xPoints[0] = centerX + dirX * rectLength/2 + perpX * rectWidth/2;
            yPoints[0] = centerY + dirY * rectLength/2 + perpY * rectWidth/2;
            xPoints[1] = centerX + dirX * rectLength/2 - perpX * rectWidth/2;
            yPoints[1] = centerY + dirY * rectLength/2 - perpY * rectWidth/2;
            xPoints[2] = centerX - dirX * rectLength/2 - perpX * rectWidth/2;
            yPoints[2] = centerY - dirY * rectLength/2 - perpY * rectWidth/2;
            xPoints[3] = centerX - dirX * rectLength/2 + perpX * rectWidth/2;
            yPoints[3] = centerY - dirY * rectLength/2 + perpY * rectWidth/2;
            
            // 根據長方形在動畫中的位置決定顏色
            // 計算該長方形應該使用哪個 flow 的顏色
            Color segmentColor = determineSegmentColor(j, numRectangles, flowColors, flowRatios);
            
            // 設置顏色和繪製
            gc.setFill(segmentColor);
            gc.setStroke(segmentColor);
            gc.setLineWidth(2);
            gc.fillPolygon(xPoints, yPoints, 4);
            gc.strokePolygon(xPoints, yPoints, 4);
        }
    }
    
    /**
     * 根據長方形在動畫中的位置決定應該使用哪個 flow 的顏色
     * @param rectangleIndex 當前長方形的索引
     * @param totalRectangles 總長方形數量
     * @param flowColors 每個 flow 的顏色列表
     * @param flowRatios 每個 flow 的比例列表
     * @return 該長方形應該使用的顏色
     */
    private Color determineSegmentColor(int rectangleIndex, int totalRectangles, List<Color> flowColors, List<Double> flowRatios) {
        if (flowColors.isEmpty() || flowRatios.isEmpty()) {
            return Color.GRAY;
        }
        
        if (flowColors.size() == 1) {
            return flowColors.get(0);
        }
        
        // 計算累積比例，用於確定每個長方形屬於哪個 flow
        double cumulativeRatio = 0.0;
        double rectanglePosition = (double) rectangleIndex / totalRectangles;
        
        for (int i = 0; i < flowRatios.size(); i++) {
            cumulativeRatio += flowRatios.get(i);
            if (rectanglePosition < cumulativeRatio) {
                return flowColors.get(i);
            }
        }
        
        // 如果沒有找到匹配的 flow（理論上不會發生），返回最後一個 flow 的顏色
        return flowColors.get(flowColors.size() - 1);
    }


    private void drawInfo(GraphicsContext gc) {
        // 圖例已移動到SideBar，這裡不再繪製
    }


    private Node getNodeAt(double x, double y) {
        for (Node n : nodes) {
            double dx = n.x - x, dy = n.y - y;
            // 根據縮放調整檢測範圍
            double detectionRadius = 30 / scale;
            if (dx * dx + dy * dy <= detectionRadius * detectionRadius) return n;
        }
        return null;
    }

    private List<Link> getLinksAt(double x, double y) {
        List<Link> result = new ArrayList<>();
        for (Link l : links) {
            Node src = getNodeByIp(l.source);
            Node tgt = getNodeByIp(l.target);
            if (src == null || tgt == null) continue;
            
            double dist = ptSegDist(src.x, src.y, tgt.x, tgt.y, x, y);
            // 根據縮放調整檢測範圍
            double detectionRadius = 10 / scale;
            if (dist < detectionRadius) {
                result.add(l);
            }
        }
        return result;
    }


    private double ptSegDist(double x1, double y1, double x2, double y2, double px, double py) {
        double dx = x2 - x1, dy = y2 - y1;
        if (dx == 0 && dy == 0) return Math.hypot(px - x1, py - y1);
        double t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy);
        t = Math.max(0, Math.min(1, t));
        double projX = x1 + t * dx, projY = y1 + t * dy;
        return Math.hypot(px - projX, py - projY);
    }

    public org.example.demo2.Node getNodeByIp(String ip) {
        // Unified approach: check both primary IP and all secondary IPs
        for (Node n : nodes) {
            // Check primary IP
            if (n.ip != null && n.ip.equals(ip)) {
                return n;
            }
            // Check all secondary IPs
            if (n.ips != null) {
                for (String secondaryIp : n.ips) {
                    if (secondaryIp.equals(ip)) {
                        return n;
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Convert node ID (string number) to IP address format
     * Used for playback mode to match pathNode IDs with link source/target IPs
     */
    public String convertNodeIdToIp(String nodeId) {
        try {
            // If it's already an IP address format, return as is
            if (nodeId.contains(".")) {
                return nodeId;
            }
            
            // Parse as long
            long nodeIdLong = Long.parseLong(nodeId);
            
            // Heuristic: If the number is small (< 1000), it's likely a DPID
            // If it's large, it's likely an IP address in integer format
            if (nodeIdLong < 1000) {
                // This is a DPID - look up the corresponding node's IP
                String mappedIp = dpidToIpMap.get(nodeId);
                if (mappedIp != null) {
                    return mappedIp;
                }
                // If not found in map, still convert it (fallback)
                if (DEBUG) System.out.println("[DEBUG] WARNING: DPID " + nodeId + " not found in dpidToIpMap");
            }
            
            // This is an IP address in integer format - convert directly
            return convertIpToString(nodeIdLong);
        } catch (NumberFormatException e) {
            // If it's not a number, return as is (might be an IP already)
            return nodeId;
        }
    }
    
    /**
     * Convert IP integer to dotted decimal string
     */
    private String convertIpToString(long ip) {
        return String.format("%d.%d.%d.%d",
            (ip >> 24) & 0xFF,
            (ip >> 16) & 0xFF,
            (ip >> 8) & 0xFF,
            ip & 0xFF);
    }

    private Link getLinkBetween(String a, String b) {
        // First try to find exact direction match (a -> b)
        Link exactMatch = links.stream()
            .filter(l -> l.source.equals(a) && l.target.equals(b))
            .findFirst()
            .orElse(null);
        
        if (exactMatch != null) {
            return exactMatch;
        }
        
        // If no exact match, try reverse direction (b -> a)
        return links.stream()
            .filter(l -> l.source.equals(b) && l.target.equals(a))
            .findFirst()
            .orElse(null);
    }

    public void setShowFlows(boolean show) {
        this.showFlows = show;
        draw();
    }
    
    public void setShowLinks(boolean show) {
        this.showLinks = show;
        draw();
    }
    
    public void setFlowMoveSpeed(double moveSpeed) {
        this.flowMoveSpeed = moveSpeed;
        // 觸發重繪以立即看到效果
        draw();
    }
    
    public double getFlowMoveSpeed() {
        return flowMoveSpeed;
    }
    
    private boolean hasActiveFlows() {
        for (double flowPo : flowPos) {
            if (flowPo > 0 && flowPo <= 1) {
                return true;
            }
        }
        return false;
    }

    // 取得明顯不同的顏色
    private Color getDistinctColor(int index, int total) {
        // Debug輸出：檢查顏色分配請求
        System.out.println("[DEBUG] getDistinctColor called with index=" + index + ", total=" + total);
        
        // 使用完全對比的高對比度顏色組合，確保每個顏色都完全不同
        Color[] distinctColors = {
            Color.RED,           // 紅色
            Color.CYAN,          // 青色（與紅色對比）
            Color.GREEN,         // 綠色
            Color.MAGENTA,       // 洋紅色（與綠色對比）
            Color.BLUE,          // 藍色
            Color.YELLOW,        // 黃色（與藍色對比）
            Color.ORANGE,        // 橙色
            Color.DARKVIOLET,    // 深紫色（與橙色對比）
            Color.LIME,          // 青檸色
            Color.PINK,          // 粉色（與青檸色對比）
            Color.DARKBLUE,      // 深藍色
            Color.GOLD,          // 金色（與深藍色對比）
            Color.DARKRED,       // 深紅色
            Color.TEAL,          // 藍綠色（與深紅色對比）
            Color.DARKGREEN,     // 深綠色
            Color.CORAL,         // 珊瑚色（與深綠色對比）
            Color.INDIGO,        // 靛藍色
            Color.DARKORANGE,    // 深橙色（與靛藍色對比）
            Color.SILVER,        // 銀色
            Color.BROWN,         // 棕色（與銀色對比）
            Color.CHARTREUSE,    // 黃綠色
            Color.DEEPPINK       // 深粉色
        };
        
        // 如果顏色不夠，使用完全對比的HSB色彩空間生成
        if (index < distinctColors.length) {
            Color color = distinctColors[index];
            System.out.println("[DEBUG] Using predefined color for index " + index + ": " + color);
            return color;
        } else {
            // 使用完全對比的色相分配
            double hue = (index * 180.0) % 360.0; // 使用180度間隔，確保對比色
            
            // 使用更極端的飽和度和亮度組合，確保與預定義顏色有差異
            int colorGroup = (index - distinctColors.length) % 12;
            double saturation, brightness;
            
            switch (colorGroup) {
                case 0: // 極高飽和度，中等亮度
                    saturation = 1.0;
                    brightness = 0.5;
                    break;
                case 1: // 中等飽和度，極高亮度
                    saturation = 0.5;
                    brightness = 1.0;
                    break;
                case 2: // 極高飽和度，低亮度
                    saturation = 1.0;
                    brightness = 0.3;
                    break;
                case 3: // 低飽和度，極高亮度
                    saturation = 0.3;
                    brightness = 1.0;
                    break;
                case 4: // 高飽和度，低亮度
                    saturation = 0.8;
                    brightness = 0.3;
                    break;
                case 5: // 低飽和度，中等亮度
                    saturation = 0.3;
                    brightness = 0.5;
                    break;
                case 6: // 中等飽和度，低亮度
                    saturation = 0.5;
                    brightness = 0.3;
                    break;
                case 7: // 低飽和度，高亮度
                    saturation = 0.3;
                    brightness = 0.8;
                    break;
                case 8: // 極高飽和度，高亮度
                    saturation = 1.0;
                    brightness = 0.8;
                    break;
                case 9: // 高飽和度，極高亮度
                    saturation = 0.8;
                    brightness = 1.0;
                    break;
                case 10: // 極低飽和度，中等亮度
                    saturation = 0.2;
                    brightness = 0.5;
                    break;
                case 11: // 中等飽和度，極低亮度
                    saturation = 0.5;
                    brightness = 0.2;
                    break;
                default:
                    saturation = 0.7;
                    brightness = 0.6;
            }
            
            Color color = Color.hsb(hue, saturation, brightness);
            System.out.println("[DEBUG] Generated color for index " + index + ": Hue=" + hue + 
                             ", Saturation=" + saturation + ", Brightness=" + brightness + 
                             ", RGB=(" + (int)(color.getRed()*255) + "," + 
                             (int)(color.getGreen()*255) + "," + 
                             (int)(color.getBlue()*255) + ")");
            return color;
        }
    }

    /**
     * 生成flow的唯一标识key（包含protocolId）
     * 這個 key 也是顏色與 Color specification 的統一依據
     */
    private String generateFlowKey(Flow flow) {
        return flow.srcIp + "_" + flow.dstIp + "_" +
               flow.srcPort + "_" + flow.dstPort + "_" + flow.protocolId;
    }
    
    /**
     * 找到 targetFlow 在 flows 列表中的索引
     * 使用 HashMap 缓存优化性能（从 O(n) 降到 O(1)）
     */
    private int findFlowIndex(Flow targetFlow) {
        String key = generateFlowKey(targetFlow);
        return flowIndexCache.getOrDefault(key, -1);
    }
    
    /**
     * 重建 flow index 缓存
     * 当 flows 列表更新时调用
     */
    private void rebuildFlowIndexCache() {
        flowIndexCache.clear();
        for (int i = 0; i < flows.size(); i++) {
            Flow flow = flows.get(i);
            String key = generateFlowKey(flow);
            flowIndexCache.put(key, i);
        }
        if (flows.size() > 0) {
            System.out.println("[CACHE] Rebuilt flow index cache with " + flowIndexCache.size() + " entries for " + flows.size() + " flows");
        }
    }
    
    // =============================
    // 顏色相關輔助方法（Color specification + hash-based 穩定顏色）
    // =============================
    
    /**
     * 將當前的 colorSpecificationMap 儲存回檔案，供下次啟動使用。
     */
    //（自訂顏色功能已移除，保留 hash-based 顏色即可）
    
    /**
     * 取得某個 flow 對應的「穩定顏色索引」：
     * - 優先從快取 map 取
     * - 若沒有，就用 five-tuple key 的 hash 決定 index，並快取
     * 如此一來，即便 flows 被清空／重建，只要 key 不變，顏色索引就不會改變。
     */
    private int getStableColorIndex(String flowKey) {
        Integer cached = flowColorAssignmentMap.get(flowKey);
        if (cached != null) {
            return cached;
        }
        int index = Math.abs(flowKey.hashCode()) % 24;
        flowColorAssignmentMap.put(flowKey, index);
        return index;
    }
    
    /**
     * 取得某個 flow 的基礎顏色：
     * 直接使用穩定的 hash-based palette 顏色（不帶強調）。
     */
    public Color getColorForFlow(Flow flow) {
        if (flow == null) {
            return Color.GRAY;
        }
        String key = generateFlowKey(flow);
        int colorIndex = getStableColorIndex(key);
        return getFlowColor(colorIndex);
    }

    /**
     * 取得某個 flow 的「強調顏色」：
     * - 先根據 hash five-tuple 取穩定基礎色；
     * - 再依照 sending rate 的區間調整亮度，讓大流更鮮豔，小流略暗。
     *
     * 採用固定門檻，而不是即時 Top-N 排名，避免顏色在排序上下波動時頻繁跳動。
     */
    private Color getEmphasizedColorForFlow(Flow flow) {
        Color base = getColorForFlow(flow);
        if (flow == null) {
            return base;
        }

        double rate = flow.estimatedFlowSendingRateBpsInTheLastSec;

        // 門檻可以依實際流量調整，這裡先用一個大致級距：
        // 高流量：>= 1 Gbps -> 顏色變亮兩級
        // 中等流量：介於 100 Mbps 和 1 Gbps -> 保持原色
        // 低流量：< 100 Mbps -> 顏色略微變暗
        final double HIGH_RATE_THRESHOLD_BPS = 1_000_000_000.0;   // 1 Gbps
        final double LOW_RATE_THRESHOLD_BPS  =   100_000_000.0;   // 100 Mbps

        if (rate >= HIGH_RATE_THRESHOLD_BPS) {
            // 大流：強調兩次，讓顏色更鮮豔
            return base.brighter().brighter();
        } else if (rate < LOW_RATE_THRESHOLD_BPS) {
            // 小流：略暗一點，避免搶走視覺焦點
            return base.darker();
        } else {
            // 中等流量：維持原本顏色
            return base;
        }
    }

    public Color getFlowColor(int flowIndex) {
        // 為每個 flow 生成獨特的顏色
        Color color = flowColorMap.computeIfAbsent(flowIndex, k -> getDistinctColor(flowIndex, 24)); // 使用24種顏色循環
        
        // Debug輸出：檢查每個flow的顏色分配
        if (DEBUG) System.out.println("[DEBUG] Flow " + flowIndex + " assigned color: " + 
                          "Hue=" + color.getHue() + 
                          ", Saturation=" + color.getSaturation() + 
                          ", Brightness=" + color.getBrightness() + 
                          ", RGB=(" + (int)(color.getRed()*255) + "," + 
                          (int)(color.getGreen()*255) + "," + 
                          (int)(color.getBlue()*255) + ")");
        
        return color;
    }


    // 节点位置文件名（可以被外部设置）
    private String nodePositionFile = "node_positions.json";
    
    // 设置节点位置文件名
    public void setNodePositionFile(String filename) {
        this.nodePositionFile = filename;
    }
    
    // 获取当前使用的节点位置文件名
    public String getNodePositionFile() {
        return nodePositionFile;
    }
    
    private void saveNodePositions() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(nodePositionFile))) {
            positionrecord(writer, nodes);
            System.out.println("[INFO] Node positions saved to: " + nodePositionFile);
        } catch (IOException e) {
            System.err.println("Error saving node positions to " + nodePositionFile + ": " + e.getMessage());
        }
    }

    static void positionrecord(PrintWriter writer, List<Node> nodes) {
        writer.println("{");
        writer.println("  \"nodes\": [");
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            writer.println("    {");
            writer.println("      \"ip\": \"" + node.ip + "\",");
            writer.println("      \"x\": " + node.x + ",");
            writer.println("      \"y\": " + node.y);
            if (i < nodes.size() - 1) {
                writer.println("    },");
            } else {
                writer.println("    }");
            }
        }
        writer.println(" ]");
        writer.println("}");
    }


    // 新增：取得帶方向的 flow 資訊
    public static class FlowWithDirection {
        public final Flow flow;
        public final String direction; // "A→B" 或 "B→A"
        public final String sourceIp;
        public final String targetIp;
        
        public FlowWithDirection(Flow flow, String direction, String sourceIp, String targetIp) {
            this.flow = flow;
            this.direction = direction;
            this.sourceIp = sourceIp;
            this.targetIp = targetIp;
        }
    }

    public List<Link> getLinks() {
        return links;
    }
    
    public List<Node> getNodes() {
        return nodes;
    }

    public void setDarkMode(boolean dark) {
        this.darkMode = dark;
        setStyle(!dark ? "-fx-background-color: white;" : "-fx-background-color: #23272e;");
        draw();
    }

    public void resetZoom() {
        scale = 1.0;
        offsetX = 0;
        offsetY = 0;
        draw();
    }
    
    public void fitToWindow() {
        // 計算所有節點的邊界
        if (nodes.isEmpty()) return;
        
        // 動態調整節點大小：節點數越多，size 越小
        double minSize = 10;
        double maxSize = 16;
        double nodeSize = Math.max(minSize, maxSize - nodes.size() * 0.2);
        
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;
        
        // 1. 計算所有節點的邊界（包括標籤）
        for (Node node : nodes) {
            double nodeRadius = nodeSize;
            double labelWidth = node.name.length() > 8 ? 8 * 8 * 0.6 : node.name.length() * 8 * 0.6;
            
            minX = Math.min(minX, node.x - nodeRadius - labelWidth / 2);
            minY = Math.min(minY, node.y - nodeRadius);
            maxX = Math.max(maxX, node.x + nodeRadius + labelWidth / 2);
            maxY = Math.max(maxY, node.y + nodeRadius + 12);
        }
        
        // 2. 考慮連接線的範圍（如果有顯示連接線）
        // 注意：連接線的端點就是節點位置，所以這裡主要是為了確保邊界計算的完整性
        if (showLinks || showFlows) {
            for (Link link : links) {
                Node src = getNodeByIp(link.source);
                Node tgt = getNodeByIp(link.target);
                if (src != null && tgt != null) {
                    // 連接線端點就是節點位置，這裡主要是為了確保所有節點都被包含
                    // 實際的邊界計算已經在節點循環中完成
                }
            }
        }
        
        // 3. 添加適當的邊距以確保所有內容都在視窗內
        // 根據節點數量動態調整邊距，但不要過大
        double baseMargin = 0; // 減少基礎邊距
        double dynamicMargin = Math.min(10, nodes.size() * 4); // 減少動態邊距，設置上限
        double margin = baseMargin + dynamicMargin;
        
        minX -= margin;
        minY -= margin;
        maxX += margin;
        maxY += margin;
        
        // 4. 計算適合的縮放比例
        double contentWidth = maxX - 2.5*minX;
        double contentHeight = maxY - minY;
        
        // 確保內容尺寸不為零
        if (contentWidth <= 0) contentWidth = 1;
        if (contentHeight <= 0) contentHeight = 1;
        
        double scaleX = getWidth() / contentWidth;
        double scaleY = getHeight() / contentHeight;
        scale = Math.min(scaleX, scaleY);
        
        // 5. 確保縮放比例在合理範圍內，並設置最小縮放限制
        double minFitScale = 0.1; // 最小適合縮放比例，避免過小
        scale = Math.max(minFitScale, Math.min(maxScale, scale));
        
        // 6. 計算居中偏移
        offsetX = (getWidth() - contentWidth * scale) / 2 - minX * scale;
        offsetY = (getHeight() - contentHeight * scale) / 2 - minY * scale;
        
        // 7. 添加調試輸出
        System.out.println("[FIT_TO_WINDOW] Canvas size: " + getWidth() + "x" + getHeight());
        System.out.println("[FIT_TO_WINDOW] Content bounds: " + minX + "," + minY + " to " + maxX + "," + maxY);
        System.out.println("[FIT_TO_WINDOW] Content size: " + contentWidth + "x" + contentHeight);
        System.out.println("[FIT_TO_WINDOW] Calculated scale: " + scale);
        System.out.println("[FIT_TO_WINDOW] Offset: " + offsetX + "," + offsetY);
        System.out.println("[FIT_TO_WINDOW] Margin used: " + margin);
        
        draw();
    }

    // ===== Path Flicker 功能 =====
    public void flickerLinksForFlow(Flow flow) {
        if (!flickerEnabled || flow == null || flow.pathNodes == null || flow.pathNodes.size() < 2) {
            return;
        }
        
        // 檢查目前是否已經在閃爍這條路徑
        boolean isAlreadyFlickering = isFlowPathCurrentlyFlickering(flow);
        
        if (isAlreadyFlickering) {
            // 已經在閃爍，再點一次就關掉
            stopFlickering();
        } else {
            // 沒有閃爍，開啟閃爍
            startFlickering(flow);
        }
        
        draw();
    }
    
    // 檢查指定 flow 的路徑是否正在閃爍
    private boolean isFlowPathCurrentlyFlickering(Flow flow) {
        if (flickeredFlow == null || !flickeredFlow.equals(flow)) {
            return false;
        }
        
        // 檢查路徑上的所有 link 是否都在閃爍
        for (int i = 0; i < flow.pathNodes.size() - 1; i++) {
            String a = flow.pathNodes.get(i);
            String b = flow.pathNodes.get(i + 1);
            String key = generateLinkKey(a, b);
            if (!flickerLinks.contains(key)) {
                return false;
            }
        }
        return true;
    }
    
    // 開始閃爍指定 flow 的路徑
    public void startFlickering(Flow flow) {
        if (flow == null) {
            System.out.println("[DEBUG] Cannot start flickering: flow is null");
            return;
        }
        
        flickerLinks.clear();
        flickeredFlow = flow;
        
        // 獲取flow的顏色（使用已分配的颜色）
        String flowKey = generateFlowKey(flow);
        int colorIndex;
        
        // 使用統一的 hash-based 顏色邏輯，確保與主動畫顏色一致
        flickeredFlowColor = getColorForFlow(flow);
        // 同時維持一份索引快取（非必要，但方便除錯）
        colorIndex = getStableColorIndex(flowKey);
        System.out.println("[DEBUG] Starting flicker for flow with stable color index " + colorIndex);
        
        System.out.println("[DEBUG] Starting flicker for flow: " + flow.srcIp + " -> " + flow.dstIp + " with color: " + flickeredFlowColor);
        
        // 直接從flow.pathNodes判斷路徑（保證正確性）
        if (flow.pathNodes != null && flow.pathNodes.size() >= 2) {
            System.out.println("[DEBUG] Using flow.pathNodes to determine flicker path (length: " + flow.pathNodes.size() + ")");
            
            // 遍歷path中的每一段link
            for (int i = 0; i < flow.pathNodes.size() - 1; i++) {
                String nodeA = flow.pathNodes.get(i);
                String nodeB = flow.pathNodes.get(i + 1);
                
                // 生成link key（標準化：較小的IP在前）
                String key = generateLinkKey(nodeA, nodeB);
                flickerLinks.add(key);
                
                System.out.println("[DEBUG] Added flicker link from pathNodes[" + i + "]: " + 
                    nodeA + " -> " + nodeB + " (key: " + key + ")");
            }
            
            System.out.println("[DEBUG] Total flicker links from pathNodes: " + flickerLinks.size());
        } else {
            System.out.println("[DEBUG] Warning: Flow has no valid pathNodes (pathNodes is null or too short)");
        }
        
        flickerOn = true;
        System.out.println("[DEBUG] Set flickerOn to: " + flickerOn);
        
        // 強制重繪
        draw();
    }
    
    // 停止閃爍
    public void stopFlickering() {
        flickerLinks.clear();
        flickerOn = false;
        flickeredFlow = null;
        flickeredFlowColor = null;
        flickerAlpha = 1.0;
        flickerPulse = 0.0;
        
        // 強制重繪
        draw();
    }
    
    // 生成標準化的 link key
    private String generateLinkKey(String a, String b) {
        return a.compareTo(b) < 0 ? a + "," + b : b + "," + a;
    }


    // 新增：獲取當前被flicker的flow
    public Flow getFlickeredFlow() {
        return flickeredFlow;
    }
    
    // 新增：測試path flicker功能
    public void testPathFlicker() {
        System.out.println("[DEBUG] === Testing Path Flicker ===");
        System.out.println("[DEBUG] Available flows: " + flows.size());
        System.out.println("[DEBUG] Current flicker state - flickerEnabled: " + flickerEnabled + ", flickerOn: " + flickerOn + ", flickerLinks size: " + flickerLinks.size());
        
        if (flows.isEmpty()) {
            System.out.println("[DEBUG] ERROR: No flows available for testing!");
            return;
        }
        
        // 找到第一个有有效路径的flow
        Flow targetFlow = null;
        for (int i = 0; i < flows.size(); i++) {
            Flow flow = flows.get(i);
            System.out.println("[DEBUG] Flow " + i + ": " + flow.srcIp + " -> " + flow.dstIp);
            System.out.println("[DEBUG]   Path nodes: " + flow.pathNodes);
            
            // 检查是否有有效的路径（至少两个节点）
            if (flow.pathNodes != null && flow.pathNodes.size() >= 2) {
                // 检查第一个和最后一个节点是否存在
                String firstNode = flow.pathNodes.get(0);
                String lastNode = flow.pathNodes.get(flow.pathNodes.size() - 1);
                Node first = getNodeByIp(firstNode);
                Node last = getNodeByIp(lastNode);
                
                if (first != null && last != null) {
                    targetFlow = flow;
                    System.out.println("[DEBUG] Found valid flow for testing: " + firstNode + " -> " + lastNode);
                    break;
                } else {
                    System.out.println("[DEBUG] Flow " + i + " has invalid nodes: " + firstNode + " or " + lastNode + " not found");
                }
            }
        }
        
        if (targetFlow != null) {
            System.out.println("[DEBUG] Testing flicker for flow: " + targetFlow.srcIp + " -> " + targetFlow.dstIp);
            startFlickering(targetFlow);
            System.out.println("[DEBUG] After startFlickering - flickerOn: " + flickerOn + ", flickerLinks size: " + flickerLinks.size());
        } else {
            System.out.println("[DEBUG] No valid flow found for testing!");
        }
        
        System.out.println("[DEBUG] === End Testing ===");
    }

    // 檢查 flow 是否可見
    private boolean isFlowVisible(int flowIndex) {
        boolean visible = visibleFlowIndices.isEmpty() || visibleFlowIndices.contains(flowIndex);
        // 只在有過濾器時打印調試信息（減少日誌量）
        if (!visibleFlowIndices.isEmpty() && DEBUG) {
            System.out.println("[TOP-K] isFlowVisible(" + flowIndex + ") = " + visible + 
                             " (visibleFlowIndices.size=" + visibleFlowIndices.size() + ")");
        }
        return visible;
    }
    
    // 新增：設置可見的 flow 索引
    public void setVisibleFlowIndices(Set<Integer> indices) {
        String mode = isPlaybackMode ? "Playback" : "Real-time";
        boolean topKEnabled = getTopKEnabled();
        
        // 調試：如果 Top-K 啟用了，記錄是誰在調用 setVisibleFlowIndices
        if (topKEnabled && (indices == null || indices.isEmpty())) {
            System.out.println("\n[WARNING] setVisibleFlowIndices called with null/empty while Top-K is enabled!");
            System.out.println("[WARNING] Mode: " + mode + ", topKValue=" + getTopKValue());
            System.out.println("[WARNING] Call stack:");
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            for (int i = 2; i < Math.min(8, stackTrace.length); i++) {
                System.out.println("[WARNING]   " + stackTrace[i]);
            }
            System.out.println();
        }
        
        if (indices == null) {
            System.out.println("[FLOW-INDICES] [" + mode + "] Clearing visibleFlowIndices (showing all)");
            visibleFlowIndices.clear(); // 顯示所有 flows
        } else {
            System.out.println("[FLOW-INDICES] [" + mode + "] Setting visibleFlowIndices to " + indices.size() + " flows");
            visibleFlowIndices.clear();
            visibleFlowIndices.addAll(indices);
        }
    }
    
    /**
     * 設置顯示 Top-K flows
     * @param topKFlows 要顯示的 Top-K flows 列表
     * @param k 用戶設定的 K 值
     */
    public void setTopKFlows(List<Flow> topKFlows, int k) {
        String mode = isPlaybackMode ? "Playback" : "Real-time";
        
        if (topKFlows == null || topKFlows.isEmpty()) {
            // 如果列表為空，清空過濾器，顯示所有 flows
            setTopKEnabled(false);
            setTopKValue(0);
            visibleFlowIndices.clear();
            System.out.println("[TOP-K] [" + mode + "] Cleared flow filter - showing all flows");
        } else {
            // 保存 Top-K 設置（使用用戶設定的 K 值，而不是實際 flows 數量）
            setTopKEnabled(true);
            setTopKValue(k);
            
            System.out.println("[TOP-K] [" + mode + "] Setting Top-K: enabled=true, K=" + k);
            
            // 找出 topKFlows 在主 flows 列表中的索引
            Set<Integer> topKIndices = new HashSet<>();
            
            for (Flow topKFlow : topKFlows) {
                // 在主 flows 列表中搜尋匹配的 flow
                for (int i = 0; i < flows.size(); i++) {
                    Flow flow = flows.get(i);
                    // 使用完整的 flow 識別條件來匹配
                    if (flow.srcIp.equals(topKFlow.srcIp) && 
                        flow.dstIp.equals(topKFlow.dstIp) &&
                        flow.srcPort == topKFlow.srcPort &&
                        flow.dstPort == topKFlow.dstPort &&
                        flow.protocolId == topKFlow.protocolId) {
                        topKIndices.add(i);
                        System.out.println("[TOP-K] Flow " + i + " matched: " + 
                                         flow.srcIp + ":" + flow.srcPort + " -> " + 
                                         flow.dstIp + ":" + flow.dstPort + 
                                         " (Rate: " + flow.estimatedFlowSendingRateBpsInTheLastSec + " bps)");
                        break; // 找到匹配就跳出內層循環
                    }
                }
            }
            
            // 設置可見的 flow 索引
            setVisibleFlowIndices(topKIndices);
            System.out.println("[TOP-K] [" + mode + "] Applied filter (K=" + k + ") - showing " + topKIndices.size() + " flows out of " + flows.size() + " total");
        }
        
        // 重繪畫布
        draw();
    }
    
    /**
     * 設置顯示 Top-K flows（兼容舊版本，不帶 K 參數）
     * @param topKFlows 要顯示的 Top-K flows 列表
     */
    public void setTopKFlows(List<Flow> topKFlows) {
        setTopKFlows(topKFlows, topKFlows != null ? topKFlows.size() : 0);
    }
    
    /**
     * 重新應用 Top-K 過濾（在 flows 列表更新後調用）
     */
    private void reapplyTopKFilter() {
        String mode = isPlaybackMode ? "Playback" : "Real-time";
        boolean topKEnabled = getTopKEnabled();
        int topKValue = getTopKValue();
        
        System.out.println("\n[TOP-K] ========== reapplyTopKFilter START ==========");
        System.out.println("[TOP-K] [" + mode + "] topKEnabled=" + topKEnabled + ", topKValue=" + topKValue);
        System.out.println("[TOP-K] [" + mode + "] flows.size=" + flows.size());
        System.out.println("[TOP-K] [" + mode + "] visibleFlowIndices.size (before)=" + visibleFlowIndices.size());
        
        if (!topKEnabled || topKValue <= 0) {
            System.out.println("[TOP-K] [" + mode + "] Top-K not enabled or K <= 0, skipping reapply");
            System.out.println("[TOP-K] ========== reapplyTopKFilter END (skipped) ==========\n");
            return; // 沒有啟用 Top-K，不需要重新應用
        }
        
        System.out.println("[TOP-K] [" + mode + "] Reapplying Top-K filter (K=" + topKValue + ") after topology update");
        
        if (flows.isEmpty()) {
            System.out.println("[TOP-K] [" + mode + "] No flows available, but keeping Top-K state enabled");
            // 不清除 visibleFlowIndices，只是暫時沒有可顯示的 flow
            // 當 flows 重新有數據時，會自動重新應用過濾
            visibleFlowIndices.clear(); // 清空索引，但保持 topKEnabled=true
            notifySideBarToUpdateButton();
            System.out.println("[TOP-K] ========== reapplyTopKFilter END (no flows, state preserved) ==========\n");
            return;
        }
        
        // 根據 sending rate 排序
        List<Flow> sortedFlows = new ArrayList<>(flows);
        sortedFlows.sort((f1, f2) -> Double.compare(
            f2.estimatedFlowSendingRateBpsInTheLastSec, 
            f1.estimatedFlowSendingRateBpsInTheLastSec
        ));
        
        // 取前 K 個 flows
        int actualK = Math.min(topKValue, sortedFlows.size());
        List<Flow> topKFlows = sortedFlows.subList(0, actualK);
        
        System.out.println("[TOP-K] [" + mode + "] Sorting complete, top " + actualK + " flows selected");
        System.out.println("[TOP-K] [" + mode + "] Top-3 flows by sending rate:");
        for (int i = 0; i < Math.min(3, topKFlows.size()); i++) {
            Flow f = topKFlows.get(i);
            System.out.println("[TOP-K] [" + mode + "]   #" + (i+1) + ": " + f.srcIp + ":" + f.srcPort + " -> " + 
                             f.dstIp + ":" + f.dstPort + " | Rate: " + f.estimatedFlowSendingRateBpsInTheLastSec + " bps");
        }
        
        // 找出這些 flows 在當前 flows 列表中的索引
        Set<Integer> topKIndices = new HashSet<>();
        for (Flow topKFlow : topKFlows) {
            for (int i = 0; i < flows.size(); i++) {
                Flow flow = flows.get(i);
                if (flow.srcIp.equals(topKFlow.srcIp) && 
                    flow.dstIp.equals(topKFlow.dstIp) &&
                    flow.srcPort == topKFlow.srcPort &&
                    flow.dstPort == topKFlow.dstPort &&
                    flow.protocolId == topKFlow.protocolId) {
                    topKIndices.add(i);
                    break;
                }
            }
        }
        
        System.out.println("[TOP-K] [" + mode + "] Matched " + topKIndices.size() + " flows in current list");
        System.out.println("[TOP-K] [" + mode + "] Flow indices: " + topKIndices);
        
        // 更新可見的 flow 索引
        setVisibleFlowIndices(topKIndices);
        System.out.println("[TOP-K] [" + mode + "] visibleFlowIndices.size (after)=" + visibleFlowIndices.size());
        System.out.println("[TOP-K] [" + mode + "] Reapplied filter - showing " + topKIndices.size() + " flows out of " + flows.size() + " total");
        
        // 通知 SideBar 更新按鈕
        notifySideBarToUpdateButton();
        System.out.println("[TOP-K] ========== reapplyTopKFilter END (success) ==========\n");
    }

    // Getter methods for debugging
    public boolean isShowFlows() {
        return showFlows;
    }
    
    public boolean isShowLinks() {
        return showLinks;
    }
    
    public double getAnimationTime() {
        return animationTime;
    }
    
    /**
     * 獲取當前可見的 flow 數量
     * @return 可見的 flow 數量
     */
    public int getVisibleFlowCount() {
        boolean topKEnabled = getTopKEnabled();
        int topKValue = getTopKValue();
        String mode = isPlaybackMode ? "Playback" : "Real-time";
        
        int count;
        // 如果啟用了 Top-K 模式
        if (topKEnabled && topKValue > 0) {
            // 返回 Top-K 設定值和實際可見數量的較小值
            count = Math.min(topKValue, Math.min(visibleFlowIndices.size(), flows.size()));
            // 如果當前沒有數據，返回 0 而不是全部
            if (flows.isEmpty() || visibleFlowIndices.isEmpty()) {
                count = 0;
            }
        } else if (visibleFlowIndices.isEmpty()) {
            // 如果沒有設置過濾器，返回全部 flows 數量
            count = flows.size();
        } else {
            // 返回可見的 flow 數量
            count = visibleFlowIndices.size();
        }
        System.out.println("[TOP-K] [" + mode + "] getVisibleFlowCount() called - topKEnabled=" + topKEnabled + 
                         ", returning " + count + 
                         " (visibleFlowIndices.size=" + visibleFlowIndices.size() + 
                         ", flows.size=" + flows.size() + 
                         ", topKValue=" + topKValue + ")");
        return count;
    }
    
    /**
     * 檢查是否啟用了 Top-K 模式
     * @return true 如果啟用了 Top-K 模式
     */
    public boolean isTopKEnabled() {
        return getTopKEnabled();
    }
    
    /**
     * 設置 SideBar 引用（用於通知按鈕更新）
     * @param sideBar SideBar 實例
     */
    public void setSideBar(SideBar sideBar) {
        this.sideBar = sideBar;
    }
    
    /**
     * 通知 SideBar 更新 Top-K 按鈕
     */
    private void notifySideBarToUpdateButton() {
        if (sideBar != null) {
            // 直接調用，因為我們已經在 UI 線程中（updateTopology 是在 Platform.runLater 中調用的）
            sideBar.updateTopKButtonText();
            System.out.println("[TOP-K] Notified SideBar to update button");
        }
    }
    
    // 檢查 link 是否可見
    private boolean isLinkVisible(String linkKey) {
        return visibleLinkKeys.isEmpty() || visibleLinkKeys.contains(linkKey);
    }
    
    /**
     * 根據使用率百分比生成漸變色彩
     * 0%: 深藍色 (低使用率)
     * 100%: 紅色 (高使用率)
     * 中間: 淺藍→綠→黃→橙→紅
     */
     private Color getUtilizationColor(double utilizationPercent) {
        // 確保使用率在0-100範圍內
        utilizationPercent = Math.max(0.0, Math.min(100.0, utilizationPercent));
        
        // 定義顏色漸變點（每5%一個變化）
        if (utilizationPercent <= 5.0) {
            // 0-5%: 深藍
            return Color.rgb(0, 100, 200);
        } else if (utilizationPercent <= 10.0) {
            // 5-10%: 深藍到淺藍
            double t = (utilizationPercent - 5.0) / 5.0;
            return Color.rgb(
                (int) (0 + t * 50),     // R: 0 -> 50
                (int) (100 + t * 77),   // G: 100 -> 177
                (int) (200 + t * 27)    // B: 200 -> 227
            );
        } else if (utilizationPercent <= 15.0) {
            // 10-15%: 淺藍到更淺藍
            double t = (utilizationPercent - 10.0) / 5.0;
            return Color.rgb(
                (int) (50 + t * 25),    // R: 50 -> 75
                (int) (177 + t * 39),   // G: 177 -> 216
                (int) (227 + t * 14)    // B: 227 -> 241
            );
        } else if (utilizationPercent <= 20.0) {
            // 15-20%: 更淺藍到綠色
            double t = (utilizationPercent - 15.0) / 5.0;
            return Color.rgb(
                (int) (75 + t * 25),    // R: 75 -> 100
                (int) (216 + t * 39),   // G: 216 -> 255
                (int) (241 - t * 86)    // B: 241 -> 155
            );
        } else if (utilizationPercent <= 25.0) {
            // 20-25%: 綠色到淺綠
            double t = (utilizationPercent - 20.0) / 5.0;
            return Color.rgb(
                (int) (100 + t * 0),    // R: 100 -> 100
                (int) (255 + t * 0),    // G: 255 -> 255
                (int) (155 - t * 31)    // B: 155 -> 124
            );
        } else if (utilizationPercent <= 30.0) {
            // 25-30%: 淺綠到黃綠
            double t = (utilizationPercent - 25.0) / 5.0;
            return Color.rgb(
                (int) (100 + t * 31),   // R: 100 -> 131
                (int) (255 + t * 0),    // G: 255 -> 255
                (int) (124 - t * 62)    // B: 124 -> 62
            );
        } else if (utilizationPercent <= 35.0) {
            // 30-35%: 黃綠到黃色
            double t = (utilizationPercent - 30.0) / 5.0;
            return Color.rgb(
                (int) (131 + t * 62),   // R: 131 -> 193
                (int) (255 + t * 0),    // G: 255 -> 255
                (int) (62 - t * 31)     // B: 62 -> 31
            );
        } else if (utilizationPercent <= 40.0) {
            // 35-40%: 黃色到亮黃
            double t = (utilizationPercent - 35.0) / 5.0;
            return Color.rgb(
                (int) (193 + t * 62),   // R: 193 -> 255
                (int) (255 + t * 0),    // G: 255 -> 255
                (int) (31 - t * 31)     // B: 31 -> 0
            );
        } else if (utilizationPercent <= 45.0) {
            // 40-45%: 亮黃到橙黃
            double t = (utilizationPercent - 40.0) / 5.0;
            return Color.rgb(
                (int) (255 + t * 0),    // R: 255 -> 255
                (int) (255 - t * 20),   // G: 255 -> 235
                (int) (0 + t * 0)       // B: 0 -> 0
            );
        } else if (utilizationPercent <= 50.0) {
            // 45-50%: 橙黃到橙色
            double t = (utilizationPercent - 45.0) / 5.0;
            return Color.rgb(
                (int) (255 + t * 0),    // R: 255 -> 255
                (int) (235 - t * 20),   // G: 235 -> 215
                (int) (0 + t * 0)       // B: 0 -> 0
            );
        } else if (utilizationPercent <= 55.0) {
            // 50-55%: 橙色到深橙
            double t = (utilizationPercent - 50.0) / 5.0;
            return Color.rgb(
                (int) (255 + t * 0),    // R: 255 -> 255
                (int) (215 - t * 20),   // G: 215 -> 195
                (int) (0 + t * 0)       // B: 0 -> 0
            );
        } else if (utilizationPercent <= 60.0) {
            // 55-60%: 深橙到紅橙
            double t = (utilizationPercent - 55.0) / 5.0;
            return Color.rgb(
                (int) (255 + t * 0),    // R: 255 -> 255
                (int) (195 - t * 20),   // G: 195 -> 175
                (int) (0 + t * 0)       // B: 0 -> 0
            );
        } else if (utilizationPercent <= 65.0) {
            // 60-65%: 紅橙到淺紅
            double t = (utilizationPercent - 60.0) / 5.0;
            return Color.rgb(
                (int) (255 + t * 0),    // R: 255 -> 255
                (int) (175 - t * 35),   // G: 175 -> 140
                (int) (0 + t * 0)       // B: 0 -> 0
            );
        } else if (utilizationPercent <= 70.0) {
            // 65-70%: 淺紅到紅色
            double t = (utilizationPercent - 65.0) / 5.0;
            return Color.rgb(
                (int) (255 + t * 0),    // R: 255 -> 255
                (int) (140 - t * 35),   // G: 140 -> 105
                (int) (0 + t * 0)       // B: 0 -> 0
            );
        } else if (utilizationPercent <= 75.0) {
            // 70-75%: 紅色到深紅
            double t = (utilizationPercent - 70.0) / 5.0;
            return Color.rgb(
                (int) (255 + t * 0),    // R: 255 -> 255
                (int) (105 - t * 26),   // G: 105 -> 79
                (int) (0 + t * 0)       // B: 0 -> 0
            );
        } else if (utilizationPercent <= 80.0) {
            // 75-80%: 深紅到更深紅
            double t = (utilizationPercent - 75.0) / 5.0;
            return Color.rgb(
                (int) (255 + t * 0),    // R: 255 -> 255
                (int) (79 - t * 26),    // G: 79 -> 53
                (int) (0 + t * 0)       // B: 0 -> 0
            );
        } else if (utilizationPercent <= 85.0) {
            // 80-85%: 更深紅到暗紅
            double t = (utilizationPercent - 80.0) / 5.0;
            return Color.rgb(
                (int) (255 + t * 0),    // R: 255 -> 255
                (int) (53 - t * 18),    // G: 53 -> 35
                (int) (0 + t * 0)       // B: 0 -> 0
            );
        } else if (utilizationPercent <= 90.0) {
            // 85-90%: 暗紅到更暗紅
            double t = (utilizationPercent - 85.0) / 5.0;
            return Color.rgb(
                (int) (255 + t * 0),    // R: 255 -> 255
                (int) (35 - t * 12),    // G: 35 -> 23
                (int) (0 + t * 0)       // B: 0 -> 0
            );
        } else if (utilizationPercent <= 95.0) {
            // 90-95%: 更暗紅到最暗紅
            double t = (utilizationPercent - 90.0) / 5.0;
            return Color.rgb(
                (int) (255 + t * 0),    // R: 255 -> 255
                (int) (23 - t * 8),     // G: 23 -> 15
                (int) (0 + t * 0)       // B: 0 -> 0
            );
        } else {
            // 95-100%: 最暗紅到純紅
            double t = (utilizationPercent - 95.0) / 5.0;
            return Color.rgb(
                (int) (255 + t * 0),    // R: 255 -> 255
                (int) (15 - t * 15),    // G: 15 -> 0
                (int) (0 + t * 0)       // B: 0 -> 0
            );
        }
    }

    // 讀取本地 node_positions.json，回傳 ip->(x,y) map
    private Map<String, int[]> loadLocalNodePositions() {
        Map<String, int[]> posMap = new HashMap<>();
        try (FileReader reader = new FileReader("node_positions.json")) {
            Gson gson = new Gson();
            JsonObject obj = gson.fromJson(reader, JsonObject.class);
            if (obj.has("nodes")) {
                JsonArray arr = obj.getAsJsonArray("nodes");
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject n = arr.get(i).getAsJsonObject();
                    String ip = n.get("ip").getAsString();
                    int x = n.get("x").getAsInt();
                    int y = n.get("y").getAsInt();
                    posMap.put(ip, new int[]{x, y});
                }
            }
        } catch (Exception e) {
            // 檔案不存在或格式錯誤時忽略
        }
        return posMap;
    }

    // 輔助方法：取得所有ip字串
    private List<String> getAllIpStrings(Node n) {
        List<String> ips = new ArrayList<>();
        if (n.ip != null) ips.add(n.ip);
        if (n instanceof Node && n.ips != null) {
            ips.addAll(n.ips);
        }
        return ips;
    }

    public void updateTopology(List<Node> newNodes, List<Link> newLinks, List<Flow> newFlows) {
        System.out.println("[DEBUG] TopologyCanvas.updateTopology called, nodes=" + (newNodes == null ? "null" : newNodes.size()) + ", links=" + (newLinks == null ? "null" : newLinks.size()) + ", flows=" + (newFlows == null ? "null" : newFlows.size()));
        
        // 詳細調試輸出
        if (newNodes != null && !newNodes.isEmpty()) {
            System.out.println("[DEBUG] First node details: " + newNodes.get(0).name + " (" + newNodes.get(0).ip + ")");
        }
        if (newLinks != null && !newLinks.isEmpty()) {
            System.out.println("[DEBUG] First link details: " + newLinks.get(0).source + " -> " + newLinks.get(0).target);
        }
        if (newFlows != null && !newFlows.isEmpty()) {
            System.out.println("[DEBUG] First flow details: " + newFlows.get(0).srcIp + " -> " + newFlows.get(0).dstIp);
        }
        // 拖拽進行中時，不更新nodes，避免拖拽中斷
        if (isDragging && draggedNode != null) {
            this.links.clear();
            this.links.addAll(newLinks);
            this.flows.clear();
            this.flows.addAll(newFlows);
            
            // Rebuild flow index cache for performance optimization
            rebuildFlowIndexCache();
            
            // 如果没有 flows，清空所有 links 的 flow_set 以避免显示幽灵 flow
            if (newFlows.isEmpty()) {
                for (Link link : this.links) {
                    if (link.flow_set != null) {
                        link.flow_set.clear();
                    }
                }
            // 重置颜色分配状态（如果flows被清空，下次重新初始化）
            flowColorAssignmentMap.clear();
            System.out.println("[DEBUG] (dragging) No flows - cleared all link flow_sets and color assignments");
            }
            
            // 重新應用 Top-K 過濾（即使在拖拽中也要保持過濾狀態）
            reapplyTopKFilter();
            
            draw();
            return;
        }
        if (newNodes == null || newLinks == null || newFlows == null) {
            this.nodes.clear();
            this.links.clear();
            this.flows.clear();
            selectedNodes.clear(); // 清除選擇狀態
            // 重置颜色分配状态（如果flows被清空，下次重新初始化）
            if (this.flows.isEmpty()) {
                flowColorAssignmentMap.clear();
            }
            draw();
            return;
        }
        
        // 如果沒有節點，清空所有資料
        if (newNodes.isEmpty()) {
            this.nodes.clear();
            this.links.clear();
            this.flows.clear();
            selectedNodes.clear();
            // 重置颜色分配状态
            flowColorAssignmentMap.clear();
            draw();
            return;
        }
        
        // 保存當前選中節點的IP列表，以便在更新後重新選中
        Set<String> selectedNodeIps = new HashSet<>();
        for (Node selectedNode : selectedNodes) {
            selectedNodeIps.add(selectedNode.ip);
        }
        
        Map<String, int[]> localPos = loadLocalNodePositions();
        this.nodes.clear();
        selectedNodes.clear(); // 清空舊的選擇
        
        // Unified approach: Do not split nodes, keep one node per device
        for (Node n : newNodes) {
            int x = n.x, y = n.y;
            
            // Check if we have saved position for this node (by primary IP)
            if (localPos.containsKey(n.ip)) {
                x = localPos.get(n.ip)[0];
                y = localPos.get(n.ip)[1];
            }
            
            // 拖拽進行中時，保留拖拽節點的位置
            if (isDragging && draggedNode != null && draggedNode.ip.equals(n.ip)) {
                x = draggedNode.x;
                y = draggedNode.y;
            }
            
            // Create single node (do not split by IP)
            Node newNode = new Node(n.ip, n.name, x, y, n.type, n.is_up, n.is_enabled, n.ips);
            
            // Copy API data from original node
            newNode.dpid = n.dpid;
            newNode.mac = n.mac;
            newNode.brandName = n.brandName;
            newNode.deviceLayer = n.deviceLayer;
            newNode.cpuUtilization = n.cpuUtilization;
            newNode.memoryUtilization = n.memoryUtilization;
            newNode.layer = n.layer;
            newNode.originalDeviceName = n.originalDeviceName;
            
            this.nodes.add(newNode);
        }
        
        // 重新選中之前選中的節點
        for (Node node : this.nodes) {
            if (selectedNodeIps.contains(node.ip)) {
                selectedNodes.add(node);
            }
        }
        
        // Build DPID to IP mapping for playback mode flow direction detection
        dpidToIpMap.clear();
        for (Node node : this.nodes) {
            if (node.ip != null && !node.ip.isEmpty()) {
                // Store mapping using DPID as string key
                dpidToIpMap.put(String.valueOf(node.dpid), node.ip);
            }
        }
        if (DEBUG && !dpidToIpMap.isEmpty()) {
            System.out.println("[DEBUG] Built DPID to IP mapping with " + dpidToIpMap.size() + " entries");
            // Show first few entries for debugging
            dpidToIpMap.entrySet().stream().limit(3).forEach(entry -> 
                System.out.println("[DEBUG]   DPID " + entry.getKey() + " -> " + entry.getValue()));
        }
        
        this.links.clear();
        this.links.addAll(newLinks);
        
        this.flows.clear();
        this.flows.addAll(newFlows);
        
        // Rebuild flow index cache for performance optimization
        rebuildFlowIndexCache();
        
        // 如果没有 flows，清空所有 links 的 flow_set 以避免显示幽灵 flow
        if (newFlows.isEmpty()) {
            for (Link link : this.links) {
                if (link.flow_set != null) {
                    link.flow_set.clear();
                }
            }
            // 重置颜色分配状态（如果flows被清空，下次重新初始化）
            flowColorAssignmentMap.clear();
            System.out.println("[DEBUG] No flows - cleared all link flow_sets and color assignments");
        }
        
        if (this.flowPos.length != this.flows.size()) {
            double[] newFlowPos = new double[this.flows.size()];
            System.arraycopy(this.flowPos, 0, newFlowPos, 0, Math.min(this.flowPos.length, newFlowPos.length));
            this.flowPos = newFlowPos;
        }
        
        // 重新應用 Top-K 過濾（如果已啟用）
        reapplyTopKFilter();
        
        draw();
    }

    // 新增：畫箭頭方法
    private void drawArrow(GraphicsContext gc, double x1, double y1, double x2, double y2, Color color) {
        // ✅ 改小箭頭尺寸，更清晰
        double arrowLength = 10;  // 從16改為10
        double arrowWidth = 6;    // 從8改為6
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length == 0) return;
        double unitX = dx / length;
        double unitY = dy / length;
        // ✅ 箭頭位置改到中間（50%），這樣雙向箭頭不會擠在一起
        double arrowX = x1 + dx * 0.5;  // 在線的正中間
        double arrowY = y1 + dy * 0.5;
        // 箭頭三角形三個點
        double leftX = arrowX - unitX * arrowLength + unitY * arrowWidth / 2;
        double leftY = arrowY - unitY * arrowLength - unitX * arrowWidth / 2;
        double rightX = arrowX - unitX * arrowLength - unitY * arrowWidth / 2;
        double rightY = arrowY - unitY * arrowLength + unitX * arrowWidth / 2;
        gc.setFill(color);
        gc.setStroke(color.darker());
        gc.setLineWidth(1.0);  // 從1.5改為1.0，邊框更細
        gc.fillPolygon(new double[]{arrowX, leftX, rightX}, new double[]{arrowY, leftY, rightY}, 3);
        gc.strokePolygon(new double[]{arrowX, leftX, rightX}, new double[]{arrowY, leftY, rightY}, 3);
    }
} 