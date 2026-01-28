package org.example.demo2;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.util.Duration;

public class TopologyCanvas extends Canvas {
    private final List<org.example.demo2.Node> nodes;
    private final List<Link> links;
    private final List<org.example.demo2.Flow> flows;
    private double[] flowPos;
    
    private final Map<Integer, Color> flowColorMap = new HashMap<>();
    private boolean showFlows = true; 
    private boolean showLinks = false;
    private double flowMoveSpeed = 5.0; 
    private double animationTime = 0.0; 
    private InfoPanel infoPanel;
    
    
    private Set<Integer> visibleFlowIndices = new HashSet<>();
    private Set<String> visibleLinkKeys = new HashSet<>(); 
    
    // Performance optimization: HashMap cache for flow index lookup
    private Map<String, Integer> flowIndexCache = new HashMap<>();
    
    
    // Key: flowKey (srcIp_dstIp_srcPort_dstPort_protocolId), Value: colorIndex (0-23)
    private Map<String, Integer> flowColorAssignmentMap = new HashMap<>();
    
    
    private boolean topKEnabledRealtime = false; 
    private int topKValueRealtime = 0; 
    private boolean topKEnabledPlayback = false; 
    private int topKValuePlayback = 0; 
    private SideBar sideBar = null; 
    
    
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
    private static final int GRID_SIZE = 15; 
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

    
    private double offsetX = 0;
    private double offsetY = 0;
    private double scale = 1.0; 
    private double minScale = 0.1; 
    private double maxScale = 5.0; 
    private boolean autoFitOnResize = false;
    private PauseTransition autoFitDebounce;
    private boolean pendingAutoFit = false;
    private boolean initialResetZoomPending = true;


    private java.util.function.Consumer<Boolean> showInfoPanelCallback;

    
    private final Set<String> flickerLinks = new HashSet<>();
    private boolean flickerOn = false;
    private boolean flickerEnabled = true; 
    private Flow flickeredFlow = null; 
    private double flickerAlpha = 1.0; 
    private double flickerPulse = 0.0; 
    private Color flickeredFlowColor = null; 

    
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
        
        
        setFocusTraversable(true);
        setMouseTransparent(false);
        setPickOnBounds(true);

        
        this.autoFitDebounce = new PauseTransition(Duration.millis(120));
        this.autoFitDebounce.setOnFinished(e -> {
            if (autoFitOnResize && pendingAutoFit && !nodes.isEmpty()) {
                pendingAutoFit = false;
                fitToWindow();
            } else {
                draw();
            }
        });
        this.widthProperty().addListener((obs, oldVal, newVal) -> scheduleAutoFitOnResize());
        this.heightProperty().addListener((obs, oldVal, newVal) -> scheduleAutoFitOnResize());
        
        
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                
                javafx.application.Platform.runLater(() -> {
                    requestFocus();
                });
            }
        });

        setupAnimation();
        setupMouseHandlers();
        setupZoomAndPan();
        
        
        draw();
    }

    private void setupAnimation() {

        AnimationTimer timer = new AnimationTimer() {
            private long lastUpdate = 0;
            @Override
            public void handle(long now) {
                if (lastUpdate == 0) {
                    lastUpdate = now;
                    return;
                }
                double deltaTime = (now - lastUpdate) / 1_000_000_000.0; 
                animationTime += deltaTime; 
                lastUpdate = now;

                
                if (flickerEnabled && !flickerLinks.isEmpty()) {
                    
                    flickerPulse += 0.15; 
                    if (flickerPulse > Math.PI * 2) {
                        flickerPulse = 0;
                    }
                    
                    
                    flickerAlpha = 0.4 + 0.6 * Math.abs(Math.sin(flickerPulse));
                    
                    
                    boolean newFlickerOn = (System.currentTimeMillis() / 400) % 2 == 0;
                    if (newFlickerOn != flickerOn) {
                        flickerOn = newFlickerOn;
                    }
                } else if (flickerLinks.isEmpty()) {
                    
                    if (flickerOn) {
                        flickerOn = false;
                        flickerAlpha = 1.0;
                        flickerPulse = 0.0;
                    }
                }

                

                double cycleTime = flowMoveSpeed; 
                
                for (int i = 0; i < flowPos.length; i++) {
                    double t = animationTime % cycleTime;
                    flowPos[i] = t / cycleTime;
                }
                
                
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
        
        
        setOnScroll(e -> {
            double zoomFactor = 1.05;
            
            
            double deltaY = e.getDeltaY();
            
            
            if (Math.abs(deltaY) < 0.001) {
                
                double textDeltaY = e.getTextDeltaY();
                if (Math.abs(textDeltaY) > 0.001) {
                    deltaY = textDeltaY * 10; 
                } else {
                    
                    
                    System.out.println("[DEBUG] Scroll event with zero delta, skipping. DeltaY=" + e.getDeltaY() + ", TextDeltaY=" + e.getTextDeltaY());
                    return;
                }
            }
            
            
            if (!isFocused()) {
                requestFocus();
            }
            
            if (deltaY < 0) {
                
                scale = Math.max(minScale, scale / zoomFactor);
            } else {
                
                scale = Math.min(maxScale, scale * zoomFactor);
            }
            
            
            double mouseX = e.getX();
            double mouseY = e.getY();
            
            
            offsetX = mouseX - (mouseX - offsetX) * (deltaY > 0 ? zoomFactor : 1.0 / zoomFactor);
            offsetY = mouseY - (mouseY - offsetY) * (deltaY > 0 ? zoomFactor : 1.0 / zoomFactor);
            
            draw(); 
            e.consume();
        });
        
        
        setOnZoom(e -> {
            double zoomFactor = e.getZoomFactor();
            double oldScale = scale;
            
            
            scale = Math.max(minScale, Math.min(maxScale, scale * zoomFactor));
            
            
            double centerX = e.getX();
            double centerY = e.getY();
            
            
            offsetX = centerX - (centerX - offsetX) * (scale / oldScale);
            offsetY = centerY - (centerY - offsetY) * (scale / oldScale);
            
            draw(); 
            e.consume();
        });
        
        
        setOnMousePressed(this::handleMousePressed);
        setOnMouseDragged(this::handleMouseDragged);
        setOnMouseReleased(this::handleMouseReleased);
        
        
        
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
        
        mousePressX = e.getX();
        mousePressY = e.getY();
        
        
        double actualX = (e.getX() - offsetX) / scale;
        double actualY = (e.getY() - offsetY) / scale;
        
        
        if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
            handleRightClick(e, actualX, actualY);
            return;
        }
        
        
        if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) {
            return;
        }
        
        
        
        if (e.isControlDown()) {
            
            isDragging = false;
            draggedNode = null;
            isRangeSelecting = false;
            isGroupDragging = false;
            return;
        }
        
        
        if (!selectedNodes.isEmpty()) {
            org.example.demo2.Node clickedNode = getNodeAt(actualX, actualY);
            if (clickedNode != null && selectedNodes.contains(clickedNode)) {
                
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
            
            if (e.isShiftDown()) {
                if (selectedNodes.contains(clickedNode)) {
                    selectedNodes.remove(clickedNode);
                } else {
                    selectedNodes.add(clickedNode);
                }
                draw();
                return;
            }
            
            
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
            
            if (showFlows && !showLinks) {
                
                InfoDialog dialog = new InfoDialog(this, flows);
                dialog.showFlowSetInfo(clickedLinks);
            } else if (!showFlows && showLinks) {
                
                showLinkOnlyInfo(clickedLinks);
            }
            return;
        }

        
        if (e.isShiftDown()) {
            
            isRangeSelecting = true;
            rangeStartX = actualX;
            rangeStartY = actualY;
            rangeEndX = actualX;
            rangeEndY = actualY;
            isDragging = false;
            draggedNode = null;
            isGroupDragging = false;
        } else {
            
            
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
        
        if (infoDialog != null) {
            infoDialog.showFlowSetInfo(clickedLinks);
        } else {
            
            infoDialog = new InfoDialog(this, flows);
            infoDialog.showFlowSetInfo(clickedLinks);
        }
    }
    
    private void showLinkOnlyInfo(List<Link> clickedLinks) {
        
        if (infoDialog != null) {
            infoDialog.showLinkOnlyInfo(clickedLinks);
        } else {
            
            infoDialog = new InfoDialog(this, flows);
            infoDialog.showLinkOnlyInfo(clickedLinks);
        }
    }
    


    private double mousePressX = 0;
    private double mousePressY = 0;
    private static final double CLICK_THRESHOLD = 5.0; 
    
    private void handleMouseReleased(MouseEvent e) {
        
        if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) {
            return;
        }
        
        if (isRangeSelecting) {
            
            isRangeSelecting = false;
            
            draw();
        } else if (isGroupDragging) {
            
            isGroupDragging = false;
            saveNodePositions();
        } else if (isDragging && draggedNode != null) {
            
            double moveDistance = Math.sqrt(
                Math.pow(e.getX() - mousePressX, 2) + 
                Math.pow(e.getY() - mousePressY, 2)
            );
            
            if (moveDistance < CLICK_THRESHOLD) {
                
                if (infoDialog != null) {
                    infoDialog.showNodeInfo(draggedNode);
                }
                
                
                if (infoPanel != null) {
                    infoPanel.showNode(draggedNode);
                }
                if (showInfoPanelCallback != null) showInfoPanelCallback.accept(Boolean.TRUE);
            }
            
            isDragging = false;
            draggedNode = null;
            saveNodePositions();
        } else {
            
            double moveDistance = Math.sqrt(
                Math.pow(e.getX() - mousePressX, 2) + 
                Math.pow(e.getY() - mousePressY, 2)
            );
            
            if (moveDistance < CLICK_THRESHOLD) {
                
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

    
    private void handleRightClick(MouseEvent e, double actualX, double actualY) {
        
        org.example.demo2.Node clickedNode = getNodeAt(actualX, actualY);
        if (clickedNode != null && selectedNodes.contains(clickedNode)) {
            showContextMenu(e, clickedNode);
        } else if (!selectedNodes.isEmpty()) {
            
            showGroupContextMenu(e);
        } else {
            
            
            isDragging = false;
            draggedNode = null;
            isRangeSelecting = false;
            isGroupDragging = false;
        }
    }

    
    private void showContextMenu(MouseEvent e, org.example.demo2.Node node) {
        javafx.scene.control.ContextMenu contextMenu = new javafx.scene.control.ContextMenu();
        
        javafx.scene.control.MenuItem moveItem = new javafx.scene.control.MenuItem("Move node");
        moveItem.setOnAction(event -> {
            
        });
        
        javafx.scene.control.MenuItem selectAllItem = new javafx.scene.control.MenuItem("Select all nodes");
        selectAllItem.setOnAction(event -> {
            selectedNodes.clear();
            selectedNodes.addAll(nodes);
            draw();
        });
        
        javafx.scene.control.MenuItem clearSelectionItem = new javafx.scene.control.MenuItem("Clear selection");
        clearSelectionItem.setOnAction(event -> {
            selectedNodes.clear();
            draw();
        });
        
        contextMenu.getItems().addAll(moveItem, selectAllItem, clearSelectionItem);
        contextMenu.show(this, e.getScreenX(), e.getScreenY());
    }

    
    private void showGroupContextMenu(MouseEvent e) {
        javafx.scene.control.ContextMenu contextMenu = new javafx.scene.control.ContextMenu();
        
        javafx.scene.control.MenuItem groupMoveItem = new javafx.scene.control.MenuItem("Group move (" + selectedNodes.size() + " nodes)");
        groupMoveItem.setOnAction(event -> {
            
        });
        
        javafx.scene.control.MenuItem alignLeftItem = new javafx.scene.control.MenuItem("Align left");
        alignLeftItem.setOnAction(event -> alignNodes("left"));
        
        javafx.scene.control.MenuItem alignRightItem = new javafx.scene.control.MenuItem("Align right");
        alignRightItem.setOnAction(event -> alignNodes("right"));
        
        javafx.scene.control.MenuItem alignTopItem = new javafx.scene.control.MenuItem("Align top");
        alignTopItem.setOnAction(event -> alignNodes("top"));
        
        javafx.scene.control.MenuItem alignBottomItem = new javafx.scene.control.MenuItem("Align bottom");
        alignBottomItem.setOnAction(event -> alignNodes("bottom"));
        
        javafx.scene.control.MenuItem distributeHorizontallyItem = new javafx.scene.control.MenuItem("Distribute horizontally");
        distributeHorizontallyItem.setOnAction(event -> distributeNodes("horizontal"));
        
        javafx.scene.control.MenuItem distributeVerticallyItem = new javafx.scene.control.MenuItem("Distribute vertically");
        distributeVerticallyItem.setOnAction(event -> distributeNodes("vertical"));
        
        javafx.scene.control.MenuItem clearSelectionItem = new javafx.scene.control.MenuItem("Clear selection");
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
        
        if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY || 
            (e.getButton() == javafx.scene.input.MouseButton.PRIMARY && e.isControlDown())) {
            
            double deltaX = e.getX() - mousePressX;
            double deltaY = e.getY() - mousePressY;
            
            offsetX += deltaX;
            offsetY += deltaY;
            
            mousePressX = e.getX();
            mousePressY = e.getY();
            
            draw();
            return;
        }
        
        
        if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) {
            return;
        }
        
        
        double actualX = (e.getX() - offsetX) / scale;
        double actualY = (e.getY() - offsetY) / scale;
        
        if (isRangeSelecting) {
            
            rangeEndX = actualX;
            rangeEndY = actualY;
            updateRangeSelection();
            draw();
        } else if (isGroupDragging && !selectedNodes.isEmpty()) {
            
            
            org.example.demo2.Node referenceNode = selectedNodes.iterator().next();
            
            
            int newRefX = (int) (actualX - groupDragOffsetX);
            int newRefY = (int) (actualY - groupDragOffsetY);
            
            
            newRefX = Math.round(newRefX / (float)GRID_SIZE) * GRID_SIZE;
            newRefY = Math.round(newRefY / (float)GRID_SIZE) * GRID_SIZE;
            
            
            int deltaX = newRefX - referenceNode.x;
            int deltaY = newRefY - referenceNode.y;
            
            
            for (org.example.demo2.Node node : selectedNodes) {
                node.x = node.x + deltaX;
                node.y = node.y + deltaY;
            }
            draw();
        } else if (isDragging && draggedNode != null) {
            
            int newX = (int) (actualX - dragOffsetX);
            int newY = (int) (actualY - dragOffsetY);
            
            newX = Math.round(newX / (float)GRID_SIZE) * GRID_SIZE;
            newY = Math.round(newY / (float)GRID_SIZE) * GRID_SIZE;
            
            draggedNode.x = newX;
            draggedNode.y = newY;
            draw();
        } else {
            
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
        gc.setTransform(1, 0, 0, 1, 0, 0); 
        
        if (darkMode) {
            gc.setFill(Color.web("#23272e"));
        } else {
            gc.setFill(Color.WHITE);
        }
        gc.fillRect(0, 0, getWidth(), getHeight());
        gc.save();

        
        gc.setTransform(scale, 0, 0, scale, offsetX, offsetY);
        
        
        if (DEBUG) System.out.println("[DEBUG] TopologyCanvas.draw() - nodes: " + nodes.size() + ", links: " + links.size() + ", flows: " + flows.size());
        if (DEBUG) System.out.println("[DEBUG] Canvas size: " + getWidth() + "x" + getHeight());
        if (DEBUG) System.out.println("[DEBUG] Offset: (" + offsetX + ", " + offsetY + "), Scale: " + scale);
        
        drawGrid(gc);
        Map<String, Integer> linkFlowCount = calculateLinkFlowCounts();
        
        if (showLinks || showFlows) {
            drawLinks(gc, linkFlowCount);
        }
        
        if (showFlows) drawFlows(gc);
        drawNodes(gc);
        
        
        if (!flickerLinks.isEmpty()) {
            if (DEBUG) System.out.println("[FLICKER] Drawing flicker links, count: " + flickerLinks.size() + ", flickerOn: " + flickerOn);
            drawFlickerLinks(gc);
        }
        
        
        if (isRangeSelecting) {
            drawRangeSelection(gc);
        }
        
        drawInfo(gc);
        gc.restore();
    }

    private void scheduleAutoFitOnResize() {
        if (nodes.isEmpty()) {
            draw();
            return;
        }
        if (autoFitOnResize) {
            pendingAutoFit = true;
            autoFitDebounce.playFromStart();
        } else {
            draw();
        }
    }

    private void drawGrid(GraphicsContext gc) {
        gc.save();
        
        if (darkMode) {
            gc.setStroke(Color.web("#2a2a2a")); 
        } else {
            gc.setStroke(Color.web("#f0f0f0")); 
        }
        gc.setLineWidth(0.5); 
        gc.setLineDashes(8, 8); 
        
        
        double w = getWidth() / scale;
        double h = getHeight() / scale;
        double startX = -offsetX / scale;
        double startY = -offsetY / scale;
        
        
        double gridStep = GRID_SIZE;
        
        
        for (double x = startX - (startX % gridStep); x < startX + w; x += gridStep) {
            gc.strokeLine(x, startY, x, startY + h);
        }
        
        
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
            if (path == null || path.size() < 2) continue; 
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
            if (seg < 0 || seg + 1 >= path.size()) continue; 
            String a = path.get(seg), b = path.get(seg + 1);
            String key = a.compareTo(b) < 0 ? a + "," + b : b + "," + a;
            linkFlowCount.put(key, linkFlowCount.getOrDefault(key, 0) + 1);
        }
        return linkFlowCount;
    }

    private void drawLinks(GraphicsContext gc, Map<String, Integer> linkFlowCount) {
        if (showLinks && !showFlows) {
            
            for (Link link : links) {
                
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
                    
                    gc.strokeLine(x4, y4, x3, y3);
                    drawArrow(gc, x4, y4, x3, y3, colorBA);
                }
            }
            return;
        }
        
        for (Link link : links) {
            
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
            
            
            if (isFlicker && flickerOn) {
                continue;
            }
            
            Color colorAB;
            double lineWidth;
            if (showFlows && !showLinks) {
                
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
            
            Link linkBA = getLinkBetween(link.target, link.source);
            if (linkBA != null) {
                boolean isFlickerBA = flickerLinks.contains(key);
                
                
                if (isFlickerBA && flickerOn) {
                    continue;
                }
                
                Color colorBA;
                if (showFlows && !showLinks) {
                    
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
    
    
    private void drawFlickerLinks(GraphicsContext gc) {
        if (!flickerOn || flickerLinks.isEmpty()) {
            return;
        }
        
        
        double rectWidth = 8; 
        double strokeWidth = 2; 
        
        
        
        Color baseColor = (flickeredFlowColor != null) ? flickeredFlowColor : Color.YELLOW;
        
        
        int r = (int) (baseColor.getRed() * 255);
        int g = (int) (baseColor.getGreen() * 255);
        int b = (int) (baseColor.getBlue() * 255);
        
        
        Color centerColor = Color.rgb(r, g, b, flickerAlpha * 0.8); 
        
        
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
            
            
            double dx = tgt.x - src.x;
            double dy = tgt.y - src.y;
            double length = Math.sqrt(dx * dx + dy * dy);
            if (length == 0) continue;
            
            
            double perpX = -dy / length;
            double perpY = dx / length;
            
            
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
            
            
            gc.setFill(Color.rgb(r, g, b, flickerAlpha * 0.3));
            gc.fillPolygon(outerXPoints, outerYPoints, 4);
            
            
            gc.setFill(centerColor);
            gc.fillPolygon(xPoints, yPoints, 4);
            
            
            gc.setStroke(strokeColor);
            gc.setLineWidth(strokeWidth);
            gc.strokePolygon(xPoints, yPoints, 4);
            
            
            double glowRadius = 3 + Math.sin(flickerPulse) * 1; 
            gc.setFill(Color.rgb(r, g, b, flickerAlpha * 0.9));
            gc.fillOval(src.x - glowRadius, src.y - glowRadius, glowRadius * 2, glowRadius * 2);
            gc.fillOval(tgt.x - glowRadius, tgt.y - glowRadius, glowRadius * 2, glowRadius * 2);
        }
    }

    private void drawNodes(GraphicsContext gc) {
        
        double minSize = 10;
        double maxSize = 16;
        double size = Math.max(minSize, maxSize - nodes.size() * 0.2);
        
        if (DEBUG) {
            System.out.println("[DEBUG] drawNodes() - Drawing " + nodes.size() + " nodes, size: " + size);
            
            if (nodes.isEmpty()) {
                System.out.println("[DEBUG] WARNING: No nodes to draw!");
                return;
            }
            
            
            for (int i = 0; i < Math.min(3, nodes.size()); i++) {
                Node node = nodes.get(i);
                System.out.println("[DEBUG] Node " + i + ": " + node.name + " at (" + node.x + ", " + node.y + "), type=" + node.type);
            }
        }
        
        if (nodes.isEmpty()) {
            return;
        }
        
        for (Node node : nodes) {
            
            Color nodeColor;
            if (!node.is_enabled || !node.is_up) {
                
                nodeColor = Color.RED;
            } else {
                
                nodeColor = Color.LIGHTGREEN;
            }
            
            
            if (selectedNodes.contains(node)) {
                nodeColor = Color.CYAN; 
                gc.setFill(nodeColor);
                gc.setStroke(Color.BLUE);
                gc.setLineWidth(3); 
            } else {
                gc.setFill(nodeColor);
                gc.setStroke(Color.BLACK);
                gc.setLineWidth(2);
            }
            
            
            if ("1".equals(node.type)) {
                
                gc.fillRect(node.x - size, node.y - size, size * 2, size * 2);
                gc.strokeRect(node.x - size, node.y - size, size * 2, size * 2);
            } else {
                
                gc.fillOval(node.x - size, node.y - size, size * 2, size * 2);
                gc.strokeOval(node.x - size, node.y - size, size * 2, size * 2);
            }
            
            // Draw node label inside the shape
            if (!node.is_enabled || !node.is_up) {
                gc.setFill(Color.WHITE); 
            } else {
                gc.setFill(Color.BLACK); 
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

    
    private void drawRangeSelection(GraphicsContext gc) {
        gc.save();
        
        
        double x = Math.min(rangeStartX, rangeEndX);
        double y = Math.min(rangeStartY, rangeEndY);
        double width = Math.abs(rangeEndX - rangeStartX);
        double height = Math.abs(rangeEndY - rangeStartY);
        
        
        gc.setFill(Color.rgb(0, 100, 255, 0.2)); 
        gc.fillRect(x, y, width, height);
        
        
        gc.setStroke(Color.BLUE);
        gc.setLineWidth(2);
        gc.setLineDashes(5, 5); 
        gc.strokeRect(x, y, width, height);
        
        
        gc.setLineDashes((double[]) null);
        
        gc.restore();
    }

    


    private void drawSimpleConnectionLines(GraphicsContext gc) {
        
        Set<String> allConnections = new HashSet<>();
        
        for (Link link : links) {
            
            String connectionKey = generateLinkKey(link.source, link.target);
            allConnections.add(connectionKey);
        }
        
        
        for (String connectionKey : allConnections) {
            String[] parts = connectionKey.split(",");
            if (parts.length == 2) {
                String sourceIp = parts[0];
                String targetIp = parts[1];
                
                Node srcNode = getNodeByIp(sourceIp);
                Node tgtNode = getNodeByIp(targetIp);
                
                if (srcNode != null && tgtNode != null) {
                    
                    
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
        
        
        if (showFlows && !showLinks) {
            if (DEBUG) System.out.println("[DEBUG] drawFlows: Drawing simple connection lines (Flow Only mode)");
            drawSimpleConnectionLines(gc);
        }
        
        
        if (isPlaybackMode) {
            drawPlaybackFlows(gc);
        } else {
            drawRealtimeFlows(gc);
        }
    }
    
    



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
            
            
            Node srcNode = getNodeByIp(link.source);
            Node tgtNode = getNodeByIp(link.target);
            if (srcNode == null || tgtNode == null) continue;
            
            
            List<Flow> forwardFlows = new ArrayList<>();  // link.source -> link.target
            List<Flow> reverseFlows = new ArrayList<>();  // link.target -> link.source
            
            for (Flow flowInSet : link.flow_set) {
                totalFlowsProcessed++;
                
                
                int flowIndex = findFlowIndex(flowInSet);
                if (flowIndex < 0) {
                    
                    
                    flowsFiltered++;
                    continue;
                }
                
                
                if (!isFlowVisible(flowIndex)) {
                    flowsFiltered++;
                    continue; 
                }
                
                
                boolean directionFound = false;
                
                if (flowInSet.pathNodes != null && flowInSet.pathNodes.size() >= 2) {
                    
                    
                    for (int i = 0; i < flowInSet.pathNodes.size() - 1; i++) {
                        String node1 = flowInSet.pathNodes.get(i);
                        String node2 = flowInSet.pathNodes.get(i + 1);
                        
                        
                        String node1Ip = node1;
                        String node2Ip = node2;
                        
                        
                        if (link.source.equals(node1Ip) && link.target.equals(node2Ip)) {
                            forwardFlows.add(flowInSet);
                            directionFound = true;
                            if (DEBUG) System.out.println("[DEBUG] drawRealtimeFlows: FORWARD Flow (from path) " + flowInSet.srcIp + ":" + flowInSet.srcPort + 
                                             " -> " + flowInSet.dstIp + ":" + flowInSet.dstPort + 
                                             ", path segment: " + node1Ip + " -> " + node2Ip);
                            break;
                        }
                        
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
                        
                        forwardFlows.add(flowInSet);
                        if (DEBUG) System.out.println("[DEBUG] drawRealtimeFlows: UNKNOWN direction, treating as FORWARD: " + flowInSet.srcIp + ":" + flowInSet.srcPort + 
                                         " -> " + flowInSet.dstIp + ":" + flowInSet.dstPort + 
                                         " on link " + link.source + " -> " + link.target);
                    }
                }
            }
            
            
            if (!forwardFlows.isEmpty()) {
                double forwardSendingRate = 0;
                for (Flow flow : forwardFlows) {
                    forwardSendingRate += flow.estimatedFlowSendingRateBpsInTheLastSec;
                }
                drawMixedFlowAnimation(gc, srcNode, tgtNode, forwardFlows, forwardSendingRate, link);
            }
            
            
            if (!reverseFlows.isEmpty()) {
                double reverseSendingRate = 0;
                for (Flow flow : reverseFlows) {
                    reverseSendingRate += flow.estimatedFlowSendingRateBpsInTheLastSec;
                }
                
                drawMixedFlowAnimation(gc, tgtNode, srcNode, reverseFlows, reverseSendingRate, link);
            }
        }
        
        if (DEBUG) System.out.println("[DEBUG] drawRealtimeFlows: Processed " + linksWithFlows + " links with flows");
        System.out.println("[TOP-K] drawRealtimeFlows: Processed=" + totalFlowsProcessed + 
                         ", Filtered=" + flowsFiltered + 
                         ", Shown=" + (totalFlowsProcessed - flowsFiltered));
    }
    
    



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
            
            
            Node srcNode = getNodeByIp(link.source);
            Node tgtNode = getNodeByIp(link.target);
            if (srcNode == null || tgtNode == null) {
                if (DEBUG) System.out.println("[DEBUG] drawPlaybackFlows: WARNING - Node not found! srcNode=" + (srcNode != null) + ", tgtNode=" + (tgtNode != null) + 
                                         " for link " + link.source + " -> " + link.target);
                nodesNotFound++;
                continue;
            }
            
            
            List<Flow> forwardFlows = new ArrayList<>();  // link.source -> link.target
            List<Flow> reverseFlows = new ArrayList<>();  // link.target -> link.source
            
            for (Flow flowInSet : link.flow_set) {
                totalFlowsProcessed++;
                
                int flowIndex = findFlowIndex(flowInSet);
                if (flowIndex >= 0 && !isFlowVisible(flowIndex)) {
                    flowsFiltered++;
                    if (DEBUG) System.out.println("[DEBUG] drawPlaybackFlows: Flow " + flowInSet.srcIp + ":" + flowInSet.srcPort + 
                                     " -> " + flowInSet.dstIp + ":" + flowInSet.dstPort + " is filtered out");
                    continue; 
                }
                
                
                boolean directionFound = false;
                
                if (flowInSet.pathNodes != null && flowInSet.pathNodes.size() >= 2) {
                    
                    for (int i = 0; i < flowInSet.pathNodes.size() - 1; i++) {
                        String node1 = flowInSet.pathNodes.get(i);
                        String node2 = flowInSet.pathNodes.get(i + 1);
                        
                        // Convert node IDs to IP addresses for comparison
                        // pathNodes may contain DPID (like "4", "7") or integer IP format (like "1828716554")
                        // while link.source and link.target are in standard IP format (like "192.168.1.1")
                        String node1Ip = convertNodeIdToIp(node1);
                        String node2Ip = convertNodeIdToIp(node2);
                        
                        
                        boolean node1MatchesSource = link.source.equals(node1Ip);
                        boolean node1MatchesTarget = link.target.equals(node1Ip);
                        boolean node2MatchesSource = link.source.equals(node2Ip);
                        boolean node2MatchesTarget = link.target.equals(node2Ip);
                        
                        
                        if (node1MatchesSource && node2MatchesTarget) {
                            forwardFlows.add(flowInSet);
                            directionFound = true;
                            if (DEBUG) System.out.println("[DEBUG] drawPlaybackFlows: FORWARD Flow (from path) " + flowInSet.srcIp + ":" + flowInSet.srcPort + 
                                             " -> " + flowInSet.dstIp + ":" + flowInSet.dstPort + 
                                             ", path segment: " + node1 + "(" + node1Ip + ") -> " + node2 + "(" + node2Ip + ")");
                            break;
                        }
                        
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
                        
                        forwardFlows.add(flowInSet);
                        if (DEBUG) System.out.println("[DEBUG] drawPlaybackFlows: UNKNOWN direction, treating as FORWARD: " + flowInSet.srcIp + ":" + flowInSet.srcPort + 
                                         " -> " + flowInSet.dstIp + ":" + flowInSet.dstPort);
                    }
                }
            }
            
            
            if (!forwardFlows.isEmpty()) {
                double forwardSendingRate = 0;
                for (Flow flow : forwardFlows) {
                    forwardSendingRate += flow.estimatedFlowSendingRateBpsInTheLastSec;
                }
                
                if (DEBUG) System.out.println("[DEBUG] drawPlaybackFlows: Drawing " + forwardFlows.size() + " FORWARD flows with totalRate=" + forwardSendingRate);
                drawMixedFlowAnimation(gc, srcNode, tgtNode, forwardFlows, forwardSendingRate, link);
                actuallyDrawn++;
            }
            
            
            if (!reverseFlows.isEmpty()) {
                double reverseSendingRate = 0;
                for (Flow flow : reverseFlows) {
                    reverseSendingRate += flow.estimatedFlowSendingRateBpsInTheLastSec;
                }
                
                if (DEBUG) System.out.println("[DEBUG] drawPlaybackFlows: Drawing " + reverseFlows.size() + " REVERSE flows with totalRate=" + reverseSendingRate);
                
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
    
    private void drawMixedFlowAnimation(GraphicsContext gc, Node srcNode, Node tgtNode, List<Flow> flows, double totalSendingRate, Link link) {
        
        double dx = tgtNode.x - srcNode.x;
        double dy = tgtNode.y - srcNode.y;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length == 0) return;
        
        
        double totalUtilization = link.link_bandwidth_utilization_percent / 100.0; 
        totalUtilization = Math.max(0.1, Math.min(1.0, totalUtilization)); 
        
        
        double animationLength = length * totalUtilization;
        
        
        double segmentDuration = flowMoveSpeed;
        double segProgress = (animationTime % segmentDuration) / segmentDuration;
        
        
        double rectLength = 8; 
        double rectWidth = 4;   
        double spacing = rectLength * 1.2; 
        
        int numRectangles = Math.max(1, (int) Math.floor(animationLength / spacing));
        
        
        double perpX = dy / length;
        double perpY = -dx / length;
        double offset = -3.5; 
        
        
        List<Color> flowColors = new ArrayList<>();
        List<Double> flowRatios = new ArrayList<>();
        
        if (flows.isEmpty()) {
            
            flowColors.add(Color.GRAY);
            flowRatios.add(1.0);
        } else {
            
            double totalRate = 0;
            for (Flow flow : flows) {
                totalRate += flow.estimatedFlowSendingRateBpsInTheLastSec;
            }
            
            
            if (totalRate == 0) {
                totalRate = flows.size();
            }
            
            for (Flow flow : flows) {
                
                
                Color flowColor = getEmphasizedColorForFlow(flow);
                flowColors.add(flowColor);
                
                if (DEBUG) System.out.println("[DEBUG] Flow color assignment (hash-based + emphasis): " + 
                    flow.srcIp + ":" + flow.srcPort + " -> " + flow.dstIp + ":" + flow.dstPort +
                    " rate=" + flow.estimatedFlowSendingRateBpsInTheLastSec + " color=" + flowColor);
                
                
                double ratio = totalRate > 0 ? flow.estimatedFlowSendingRateBpsInTheLastSec / totalRate : 1.0 / flows.size();
                flowRatios.add(ratio);
            }
        }
        
        
        for (int j = 0; j < numRectangles; j++) {
            double flowProgress = segProgress - (j * spacing / length);
            
            
            if (flowProgress < 0) {
                flowProgress = 1.0 + flowProgress;
            }
            
            
            double centerX = srcNode.x + (tgtNode.x - srcNode.x) * flowProgress;
            double centerY = srcNode.y + (tgtNode.y - srcNode.y) * flowProgress;
            centerX += perpX * offset;
            centerY += perpY * offset;
            
            
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
            
            
            
            Color segmentColor = determineSegmentColor(j, numRectangles, flowColors, flowRatios);
            
            
            gc.setFill(segmentColor);
            gc.setStroke(segmentColor);
            gc.setLineWidth(2);
            gc.fillPolygon(xPoints, yPoints, 4);
            gc.strokePolygon(xPoints, yPoints, 4);
        }
    }
    
    







    private Color determineSegmentColor(int rectangleIndex, int totalRectangles, List<Color> flowColors, List<Double> flowRatios) {
        if (flowColors.isEmpty() || flowRatios.isEmpty()) {
            return Color.GRAY;
        }
        
        if (flowColors.size() == 1) {
            return flowColors.get(0);
        }
        
        
        double cumulativeRatio = 0.0;
        double rectanglePosition = (double) rectangleIndex / totalRectangles;
        
        for (int i = 0; i < flowRatios.size(); i++) {
            cumulativeRatio += flowRatios.get(i);
            if (rectanglePosition < cumulativeRatio) {
                return flowColors.get(i);
            }
        }
        
        
        return flowColors.get(flowColors.size() - 1);
    }


    private void drawInfo(GraphicsContext gc) {
        
    }


    private Node getNodeAt(double x, double y) {
        if (nodes.isEmpty()) {
            return null;
        }
        
        double minSize = 10;
        double maxSize = 16;
        double size = Math.max(minSize, maxSize - nodes.size() * 0.2);

        Node closest = null;
        double closestDist = Double.MAX_VALUE;
        for (Node n : nodes) {
            double dx = x - n.x;
            double dy = y - n.y;

            boolean hit;
            if ("1".equals(n.type)) {
                
                hit = Math.abs(dx) <= size && Math.abs(dy) <= size;
            } else {
                
                hit = (dx * dx + dy * dy) <= size * size;
            }

            if (hit) {
                double dist = dx * dx + dy * dy;
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = n;
                }
            }
        }
        return closest;
    }

    private List<Link> getLinksAt(double x, double y) {
        List<Link> result = new ArrayList<>();
        for (Link l : links) {
            Node src = getNodeByIp(l.source);
            Node tgt = getNodeByIp(l.target);
            if (src == null || tgt == null) continue;
            
            double dist = ptSegDist(src.x, src.y, tgt.x, tgt.y, x, y);
            
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

    
    private Color getDistinctColor(int index, int total) {
        
        System.out.println("[DEBUG] getDistinctColor called with index=" + index + ", total=" + total);
        
        
        Color[] distinctColors = {
            Color.RED,           
            Color.CYAN,          
            Color.GREEN,         
            Color.MAGENTA,       
            Color.BLUE,          
            Color.YELLOW,        
            Color.ORANGE,        
            Color.DARKVIOLET,    
            Color.LIME,          
            Color.PINK,          
            Color.DARKBLUE,      
            Color.GOLD,          
            Color.DARKRED,       
            Color.TEAL,          
            Color.DARKGREEN,     
            Color.CORAL,         
            Color.INDIGO,        
            Color.DARKORANGE,    
            Color.SILVER,        
            Color.BROWN,         
            Color.CHARTREUSE,    
            Color.DEEPPINK       
        };
        
        
        if (index < distinctColors.length) {
            Color color = distinctColors[index];
            System.out.println("[DEBUG] Using predefined color for index " + index + ": " + color);
            return color;
        } else {
            
            double hue = (index * 180.0) % 360.0; 
            
            
            int colorGroup = (index - distinctColors.length) % 12;
            double saturation, brightness;
            
            switch (colorGroup) {
                case 0: 
                    saturation = 1.0;
                    brightness = 0.5;
                    break;
                case 1: 
                    saturation = 0.5;
                    brightness = 1.0;
                    break;
                case 2: 
                    saturation = 1.0;
                    brightness = 0.3;
                    break;
                case 3: 
                    saturation = 0.3;
                    brightness = 1.0;
                    break;
                case 4: 
                    saturation = 0.8;
                    brightness = 0.3;
                    break;
                case 5: 
                    saturation = 0.3;
                    brightness = 0.5;
                    break;
                case 6: 
                    saturation = 0.5;
                    brightness = 0.3;
                    break;
                case 7: 
                    saturation = 0.3;
                    brightness = 0.8;
                    break;
                case 8: 
                    saturation = 1.0;
                    brightness = 0.8;
                    break;
                case 9: 
                    saturation = 0.8;
                    brightness = 1.0;
                    break;
                case 10: 
                    saturation = 0.2;
                    brightness = 0.5;
                    break;
                case 11: 
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

    



    private String generateFlowKey(Flow flow) {
        return flow.srcIp + "_" + flow.dstIp + "_" +
               flow.srcPort + "_" + flow.dstPort + "_" + flow.protocolId;
    }
    
    



    private int findFlowIndex(Flow targetFlow) {
        String key = generateFlowKey(targetFlow);
        return flowIndexCache.getOrDefault(key, -1);
    }
    
    



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
    
    // =============================
    
    


    
    
    





    private int getStableColorIndex(String flowKey) {
        Integer cached = flowColorAssignmentMap.get(flowKey);
        if (cached != null) {
            return cached;
        }
        int index = Math.abs(flowKey.hashCode()) % 24;
        flowColorAssignmentMap.put(flowKey, index);
        return index;
    }
    
    



    public Color getColorForFlow(Flow flow) {
        if (flow == null) {
            return Color.GRAY;
        }
        String key = generateFlowKey(flow);
        int colorIndex = getStableColorIndex(key);
        return getFlowColor(colorIndex);
    }

    






    private Color getEmphasizedColorForFlow(Flow flow) {
        Color base = getColorForFlow(flow);
        if (flow == null) {
            return base;
        }

        double rate = flow.estimatedFlowSendingRateBpsInTheLastSec;

        
        
        
        
        final double HIGH_RATE_THRESHOLD_BPS = 1_000_000_000.0;   // 1 Gbps
        final double LOW_RATE_THRESHOLD_BPS  =   100_000_000.0;   // 100 Mbps

        if (rate >= HIGH_RATE_THRESHOLD_BPS) {
            
            return base.brighter().brighter();
        } else if (rate < LOW_RATE_THRESHOLD_BPS) {
            
            return base.darker();
        } else {
            
            return base;
        }
    }

    public Color getFlowColor(int flowIndex) {
        
        Color color = flowColorMap.computeIfAbsent(flowIndex, k -> getDistinctColor(flowIndex, 24)); 
        
        
        if (DEBUG) System.out.println("[DEBUG] Flow " + flowIndex + " assigned color: " + 
                          "Hue=" + color.getHue() + 
                          ", Saturation=" + color.getSaturation() + 
                          ", Brightness=" + color.getBrightness() + 
                          ", RGB=(" + (int)(color.getRed()*255) + "," + 
                          (int)(color.getGreen()*255) + "," + 
                          (int)(color.getBlue()*255) + ")");
        
        return color;
    }


    
    private String nodePositionFile = "node_positions.json";
    
    
    public void setNodePositionFile(String filename) {
        this.nodePositionFile = filename;
    }
    
    
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


    
    public static class FlowWithDirection {
        public final Flow flow;
        public final String direction; 
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
        // Return a defensive copy to prevent external modification and race conditions
        return new ArrayList<>(links);
    }
    
    public List<Node> getNodes() {
        // Return a defensive copy to prevent external modification and race conditions
        return new ArrayList<>(nodes);
    }

    public void setDarkMode(boolean dark) {
        this.darkMode = dark;
        setStyle(!dark ? "-fx-background-color: white;" : "-fx-background-color: #23272e;");
        // Notify callback to update parent container background color
        if (darkModeCallback != null) {
            darkModeCallback.accept(dark);
        }
        draw();
    }
    
    // Callback to update parent container background color
    private java.util.function.Consumer<Boolean> darkModeCallback = null;
    
    public void setDarkModeCallback(java.util.function.Consumer<Boolean> callback) {
        this.darkModeCallback = callback;
    }

    public void resetZoom() {
        scale = 1.0;
        centerContentAtScale(scale);
        draw();
    }
    
    public void fitToWindow() {
        
        if (nodes.isEmpty()) return;
        
        
        double minSize = 10;
        double maxSize = 16;
        double nodeSize = Math.max(minSize, maxSize - nodes.size() * 0.2);

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;
        
        
        for (Node node : nodes) {
            double nodeRadius = nodeSize;

            minX = Math.min(minX, node.x - nodeRadius);
            minY = Math.min(minY, node.y - nodeRadius);
            maxX = Math.max(maxX, node.x + nodeRadius);
            maxY = Math.max(maxY, node.y + nodeRadius);
        }
        
        
        
        if (showLinks || showFlows) {
            for (Link link : links) {
                Node src = getNodeByIp(link.source);
                Node tgt = getNodeByIp(link.target);
                if (src != null && tgt != null) {
                    
                    
                }
            }
        }
        
        
        double contentWidth = maxX - minX;
        double contentHeight = maxY - minY;
        
        
        if (contentWidth <= 0) contentWidth = 1;
        if (contentHeight <= 0) contentHeight = 1;
        
        
        double padding = 8.0;
        double availableWidth = Math.max(1, getWidth() - padding * 2);
        double availableHeight = Math.max(1, getHeight() - padding * 2);
        
        double scaleX = availableWidth / contentWidth;
        double scaleY = availableHeight / contentHeight;

        
        scale = Math.min(scaleX, scaleY);
        
        
        double contentPixelWidth = contentWidth * scale;
        double contentPixelHeight = contentHeight * scale;
        double leftLimit = padding;
        double topLimit = padding;
        double availableWidthPx = Math.max(1, getWidth() - padding * 2);
        double availableHeightPx = Math.max(1, getHeight() - padding * 2);
        
        offsetX = leftLimit - minX * scale;
        offsetY = topLimit - minY * scale;
        
        if (contentPixelWidth < availableWidthPx) {
            offsetX += (availableWidthPx - contentPixelWidth) / 2.0;
        }
        if (contentPixelHeight < availableHeightPx) {
            offsetY += (availableHeightPx - contentPixelHeight) / 2.0;
        }
        
        
        System.out.println("[FIT_TO_WINDOW] Canvas size: " + getWidth() + "x" + getHeight());
        System.out.println("[FIT_TO_WINDOW] Content bounds: " + minX + "," + minY + " to " + maxX + "," + maxY);
        System.out.println("[FIT_TO_WINDOW] Content size: " + contentWidth + "x" + contentHeight);
        System.out.println("[FIT_TO_WINDOW] Calculated scale: " + scale);
        System.out.println("[FIT_TO_WINDOW] Offset: " + offsetX + "," + offsetY);
        System.out.println("[FIT_TO_WINDOW] ScaleX: " + scaleX + ", ScaleY: " + scaleY + ", padding: " + padding);
        
        draw();
    }

    private void centerContentAtScale(double targetScale) {
        if (nodes.isEmpty()) {
            offsetX = 0;
            offsetY = 0;
            return;
        }
        
        double minSize = 10;
        double maxSize = 16;
        double nodeSize = Math.max(minSize, maxSize - nodes.size() * 0.2);

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;
        for (Node node : nodes) {
            double nodeRadius = nodeSize;
            minX = Math.min(minX, node.x - nodeRadius);
            minY = Math.min(minY, node.y - nodeRadius);
            maxX = Math.max(maxX, node.x + nodeRadius);
            maxY = Math.max(maxY, node.y + nodeRadius);
        }
        double contentWidth = Math.max(1, maxX - minX);
        double contentHeight = Math.max(1, maxY - minY);
        double contentPixelWidth = contentWidth * targetScale;
        double contentPixelHeight = contentHeight * targetScale;
        offsetX = (getWidth() - contentPixelWidth) / 2 - minX * targetScale;
        offsetY = (getHeight() - contentPixelHeight) / 2 - minY * targetScale;
    }

    
    public void flickerLinksForFlow(Flow flow) {
        if (!flickerEnabled || flow == null || flow.pathNodes == null || flow.pathNodes.size() < 2) {
            return;
        }
        
        
        boolean isAlreadyFlickering = isFlowPathCurrentlyFlickering(flow);
        
        if (isAlreadyFlickering) {
            
            stopFlickering();
        } else {
            
            startFlickering(flow);
        }
        
        draw();
    }
    
    
    private boolean isFlowPathCurrentlyFlickering(Flow flow) {
        if (flickeredFlow == null || !flickeredFlow.equals(flow)) {
            return false;
        }
        
        
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
    
    
    public void startFlickering(Flow flow) {
        if (flow == null) {
            System.out.println("[DEBUG] Cannot start flickering: flow is null");
            return;
        }
        
        flickerLinks.clear();
        flickeredFlow = flow;
        
        
        String flowKey = generateFlowKey(flow);
        int colorIndex;
        
        
        flickeredFlowColor = getColorForFlow(flow);
        
        colorIndex = getStableColorIndex(flowKey);
        System.out.println("[DEBUG] Starting flicker for flow with stable color index " + colorIndex);
        
        System.out.println("[DEBUG] Starting flicker for flow: " + flow.srcIp + " -> " + flow.dstIp + " with color: " + flickeredFlowColor);
        
        
        if (flow.pathNodes != null && flow.pathNodes.size() >= 2) {
            System.out.println("[DEBUG] Using flow.pathNodes to determine flicker path (length: " + flow.pathNodes.size() + ")");
            
            
            for (int i = 0; i < flow.pathNodes.size() - 1; i++) {
                String nodeA = flow.pathNodes.get(i);
                String nodeB = flow.pathNodes.get(i + 1);
                
                
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
        
        
        draw();
    }
    
    
    public void stopFlickering() {
        flickerLinks.clear();
        flickerOn = false;
        flickeredFlow = null;
        flickeredFlowColor = null;
        flickerAlpha = 1.0;
        flickerPulse = 0.0;
        
        
        draw();
    }
    
    
    private String generateLinkKey(String a, String b) {
        return a.compareTo(b) < 0 ? a + "," + b : b + "," + a;
    }


    
    public Flow getFlickeredFlow() {
        return flickeredFlow;
    }
    
    
    public void testPathFlicker() {
        System.out.println("[DEBUG] === Testing Path Flicker ===");
        System.out.println("[DEBUG] Available flows: " + flows.size());
        System.out.println("[DEBUG] Current flicker state - flickerEnabled: " + flickerEnabled + ", flickerOn: " + flickerOn + ", flickerLinks size: " + flickerLinks.size());
        
        if (flows.isEmpty()) {
            System.out.println("[DEBUG] ERROR: No flows available for testing!");
            return;
        }
        
        
        Flow targetFlow = null;
        for (int i = 0; i < flows.size(); i++) {
            Flow flow = flows.get(i);
            System.out.println("[DEBUG] Flow " + i + ": " + flow.srcIp + " -> " + flow.dstIp);
            System.out.println("[DEBUG]   Path nodes: " + flow.pathNodes);
            
            
            if (flow.pathNodes != null && flow.pathNodes.size() >= 2) {
                
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

    
    private boolean isFlowVisible(int flowIndex) {
        boolean visible = visibleFlowIndices.isEmpty() || visibleFlowIndices.contains(flowIndex);
        
        if (!visibleFlowIndices.isEmpty() && DEBUG) {
            System.out.println("[TOP-K] isFlowVisible(" + flowIndex + ") = " + visible + 
                             " (visibleFlowIndices.size=" + visibleFlowIndices.size() + ")");
        }
        return visible;
    }
    
    
    public void setVisibleFlowIndices(Set<Integer> indices) {
        String mode = isPlaybackMode ? "Playback" : "Real-time";
        boolean topKEnabled = getTopKEnabled();
        
        
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
            visibleFlowIndices.clear(); 
        } else {
            System.out.println("[FLOW-INDICES] [" + mode + "] Setting visibleFlowIndices to " + indices.size() + " flows");
            visibleFlowIndices.clear();
            visibleFlowIndices.addAll(indices);
        }
    }
    
    




    public void setTopKFlows(List<Flow> topKFlows, int k) {
        String mode = isPlaybackMode ? "Playback" : "Real-time";
        
        if (topKFlows == null || topKFlows.isEmpty()) {
            
            setTopKEnabled(false);
            setTopKValue(0);
            visibleFlowIndices.clear();
            System.out.println("[TOP-K] [" + mode + "] Cleared flow filter - showing all flows");
        } else {
            
            setTopKEnabled(true);
            setTopKValue(k);
            
            System.out.println("[TOP-K] [" + mode + "] Setting Top-K: enabled=true, K=" + k);
            
            
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
                        System.out.println("[TOP-K] Flow " + i + " matched: " + 
                                         flow.srcIp + ":" + flow.srcPort + " -> " + 
                                         flow.dstIp + ":" + flow.dstPort + 
                                         " (Rate: " + flow.estimatedFlowSendingRateBpsInTheLastSec + " bps)");
                        break; 
                    }
                }
            }
            
            
            setVisibleFlowIndices(topKIndices);
            System.out.println("[TOP-K] [" + mode + "] Applied filter (K=" + k + ") - showing " + topKIndices.size() + " flows out of " + flows.size() + " total");
        }
        
        
        draw();
    }
    
    



    public void setTopKFlows(List<Flow> topKFlows) {
        setTopKFlows(topKFlows, topKFlows != null ? topKFlows.size() : 0);
    }
    
    


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
            return; 
        }
        
        System.out.println("[TOP-K] [" + mode + "] Reapplying Top-K filter (K=" + topKValue + ") after topology update");
        
        if (flows.isEmpty()) {
            System.out.println("[TOP-K] [" + mode + "] No flows available, but keeping Top-K state enabled");
            
            
            visibleFlowIndices.clear(); 
            notifySideBarToUpdateButton();
            System.out.println("[TOP-K] ========== reapplyTopKFilter END (no flows, state preserved) ==========\n");
            return;
        }
        
        
        List<Flow> sortedFlows = new ArrayList<>(flows);
        sortedFlows.sort((f1, f2) -> Double.compare(
            f2.estimatedFlowSendingRateBpsInTheLastSec, 
            f1.estimatedFlowSendingRateBpsInTheLastSec
        ));
        
        
        int actualK = Math.min(topKValue, sortedFlows.size());
        List<Flow> topKFlows = sortedFlows.subList(0, actualK);
        
        System.out.println("[TOP-K] [" + mode + "] Sorting complete, top " + actualK + " flows selected");
        System.out.println("[TOP-K] [" + mode + "] Top-3 flows by sending rate:");
        for (int i = 0; i < Math.min(3, topKFlows.size()); i++) {
            Flow f = topKFlows.get(i);
            System.out.println("[TOP-K] [" + mode + "]   #" + (i+1) + ": " + f.srcIp + ":" + f.srcPort + " -> " + 
                             f.dstIp + ":" + f.dstPort + " | Rate: " + f.estimatedFlowSendingRateBpsInTheLastSec + " bps");
        }
        
        
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
        
        
        setVisibleFlowIndices(topKIndices);
        System.out.println("[TOP-K] [" + mode + "] visibleFlowIndices.size (after)=" + visibleFlowIndices.size());
        System.out.println("[TOP-K] [" + mode + "] Reapplied filter - showing " + topKIndices.size() + " flows out of " + flows.size() + " total");
        
        
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
    
    



    public int getVisibleFlowCount() {
        boolean topKEnabled = getTopKEnabled();
        int topKValue = getTopKValue();
        String mode = isPlaybackMode ? "Playback" : "Real-time";
        
        int count;
        
        if (topKEnabled && topKValue > 0) {
            
            count = Math.min(topKValue, Math.min(visibleFlowIndices.size(), flows.size()));
            
            if (flows.isEmpty() || visibleFlowIndices.isEmpty()) {
                count = 0;
            }
        } else if (visibleFlowIndices.isEmpty()) {
            
            count = flows.size();
        } else {
            
            count = visibleFlowIndices.size();
        }
        System.out.println("[TOP-K] [" + mode + "] getVisibleFlowCount() called - topKEnabled=" + topKEnabled + 
                         ", returning " + count + 
                         " (visibleFlowIndices.size=" + visibleFlowIndices.size() + 
                         ", flows.size=" + flows.size() + 
                         ", topKValue=" + topKValue + ")");
        return count;
    }
    
    



    public boolean isTopKEnabled() {
        return getTopKEnabled();
    }
    
    



    public void setSideBar(SideBar sideBar) {
        this.sideBar = sideBar;
    }
    
    


    private void notifySideBarToUpdateButton() {
        if (sideBar != null) {
            
            sideBar.updateTopKButtonText();
            System.out.println("[TOP-K] Notified SideBar to update button");
        }
    }
    
    
    private boolean isLinkVisible(String linkKey) {
        return visibleLinkKeys.isEmpty() || visibleLinkKeys.contains(linkKey);
    }
    
    





     private Color getUtilizationColor(double utilizationPercent) {
        
        utilizationPercent = Math.max(0.0, Math.min(100.0, utilizationPercent));
        
        
        if (utilizationPercent <= 5.0) {
            
            return Color.rgb(0, 100, 200);
        } else if (utilizationPercent <= 10.0) {
            
            double t = (utilizationPercent - 5.0) / 5.0;
            return Color.rgb(
                (int) (0 + t * 50),     // R: 0 -> 50
                (int) (100 + t * 77),   // G: 100 -> 177
                (int) (200 + t * 27)    // B: 200 -> 227
            );
        } else if (utilizationPercent <= 15.0) {
            
            double t = (utilizationPercent - 10.0) / 5.0;
            return Color.rgb(
                (int) (50 + t * 25),    // R: 50 -> 75
                (int) (177 + t * 39),   // G: 177 -> 216
                (int) (227 + t * 14)    // B: 227 -> 241
            );
        } else if (utilizationPercent <= 20.0) {
            
            double t = (utilizationPercent - 15.0) / 5.0;
            return Color.rgb(
                (int) (75 + t * 25),    // R: 75 -> 100
                (int) (216 + t * 39),   // G: 216 -> 255
                (int) (241 - t * 86)    // B: 241 -> 155
            );
        } else if (utilizationPercent <= 25.0) {
            
            double t = (utilizationPercent - 20.0) / 5.0;
            return Color.rgb(
                (int) (100 + t * 0),    // R: 100 -> 100
                (int) (255 + t * 0),    // G: 255 -> 255
                (int) (155 - t * 31)    // B: 155 -> 124
            );
        } else if (utilizationPercent <= 30.0) {
            
            double t = (utilizationPercent - 25.0) / 5.0;
            return Color.rgb(
                (int) (100 + t * 31),   // R: 100 -> 131
                (int) (255 + t * 0),    // G: 255 -> 255
                (int) (124 - t * 62)    // B: 124 -> 62
            );
        } else if (utilizationPercent <= 35.0) {
            
            double t = (utilizationPercent - 30.0) / 5.0;
            return Color.rgb(
                (int) (131 + t * 62),   // R: 131 -> 193
                (int) (255 + t * 0),    // G: 255 -> 255
                (int) (62 - t * 31)     // B: 62 -> 31
            );
        } else if (utilizationPercent <= 40.0) {
            
            double t = (utilizationPercent - 35.0) / 5.0;
            return Color.rgb(
                (int) (193 + t * 62),   // R: 193 -> 255
                (int) (255 + t * 0),    // G: 255 -> 255
                (int) (31 - t * 31)     // B: 31 -> 0
            );
        } else if (utilizationPercent <= 45.0) {
            
            double t = (utilizationPercent - 40.0) / 5.0;
            return Color.rgb(
                (int) (255 + t * 0),    // R: 255 -> 255
                (int) (255 - t * 20),   // G: 255 -> 235
                (int) (0 + t * 0)       // B: 0 -> 0
            );
        } else if (utilizationPercent <= 50.0) {
            
            double t = (utilizationPercent - 45.0) / 5.0;
            return Color.rgb(
                (int) (255 + t * 0),    // R: 255 -> 255
                (int) (235 - t * 20),   // G: 235 -> 215
                (int) (0 + t * 0)       // B: 0 -> 0
            );
        } else if (utilizationPercent <= 55.0) {
            
            double t = (utilizationPercent - 50.0) / 5.0;
            return Color.rgb(
                (int) (255 + t * 0),    // R: 255 -> 255
                (int) (215 - t * 20),   // G: 215 -> 195
                (int) (0 + t * 0)       // B: 0 -> 0
            );
        } else if (utilizationPercent <= 60.0) {
            
            double t = (utilizationPercent - 55.0) / 5.0;
            return Color.rgb(
                (int) (255 + t * 0),    // R: 255 -> 255
                (int) (195 - t * 20),   // G: 195 -> 175
                (int) (0 + t * 0)       // B: 0 -> 0
            );
        } else if (utilizationPercent <= 65.0) {
            
            double t = (utilizationPercent - 60.0) / 5.0;
            return Color.rgb(
                (int) (255 + t * 0),    // R: 255 -> 255
                (int) (175 - t * 35),   // G: 175 -> 140
                (int) (0 + t * 0)       // B: 0 -> 0
            );
        } else if (utilizationPercent <= 70.0) {
            
            double t = (utilizationPercent - 65.0) / 5.0;
            return Color.rgb(
                (int) (255 + t * 0),    // R: 255 -> 255
                (int) (140 - t * 35),   // G: 140 -> 105
                (int) (0 + t * 0)       // B: 0 -> 0
            );
        } else if (utilizationPercent <= 75.0) {
            
            double t = (utilizationPercent - 70.0) / 5.0;
            return Color.rgb(
                (int) (255 + t * 0),    // R: 255 -> 255
                (int) (105 - t * 26),   // G: 105 -> 79
                (int) (0 + t * 0)       // B: 0 -> 0
            );
        } else if (utilizationPercent <= 80.0) {
            
            double t = (utilizationPercent - 75.0) / 5.0;
            return Color.rgb(
                (int) (255 + t * 0),    // R: 255 -> 255
                (int) (79 - t * 26),    // G: 79 -> 53
                (int) (0 + t * 0)       // B: 0 -> 0
            );
        } else if (utilizationPercent <= 85.0) {
            
            double t = (utilizationPercent - 80.0) / 5.0;
            return Color.rgb(
                (int) (255 + t * 0),    // R: 255 -> 255
                (int) (53 - t * 18),    // G: 53 -> 35
                (int) (0 + t * 0)       // B: 0 -> 0
            );
        } else if (utilizationPercent <= 90.0) {
            
            double t = (utilizationPercent - 85.0) / 5.0;
            return Color.rgb(
                (int) (255 + t * 0),    // R: 255 -> 255
                (int) (35 - t * 12),    // G: 35 -> 23
                (int) (0 + t * 0)       // B: 0 -> 0
            );
        } else if (utilizationPercent <= 95.0) {
            
            double t = (utilizationPercent - 90.0) / 5.0;
            return Color.rgb(
                (int) (255 + t * 0),    // R: 255 -> 255
                (int) (23 - t * 8),     // G: 23 -> 15
                (int) (0 + t * 0)       // B: 0 -> 0
            );
        } else {
            
            double t = (utilizationPercent - 95.0) / 5.0;
            return Color.rgb(
                (int) (255 + t * 0),    // R: 255 -> 255
                (int) (15 - t * 15),    // G: 15 -> 0
                (int) (0 + t * 0)       // B: 0 -> 0
            );
        }
    }

    
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
            
        }
        return posMap;
    }

    public void updateTopology(List<Node> newNodes, List<Link> newLinks, List<Flow> newFlows) {
        System.out.println("[DEBUG] TopologyCanvas.updateTopology called, nodes=" + (newNodes == null ? "null" : newNodes.size()) + ", links=" + (newLinks == null ? "null" : newLinks.size()) + ", flows=" + (newFlows == null ? "null" : newFlows.size()));
        
        
        if (newNodes != null && !newNodes.isEmpty()) {
            System.out.println("[DEBUG] First node details: " + newNodes.get(0).name + " (" + newNodes.get(0).ip + ")");
        }
        if (newLinks != null && !newLinks.isEmpty()) {
            System.out.println("[DEBUG] First link details: " + newLinks.get(0).source + " -> " + newLinks.get(0).target);
        }
        if (newFlows != null && !newFlows.isEmpty()) {
            System.out.println("[DEBUG] First flow details: " + newFlows.get(0).srcIp + " -> " + newFlows.get(0).dstIp);
        }
        
        if (isDragging && draggedNode != null) {
            this.links.clear();
            this.links.addAll(newLinks);
            this.flows.clear();
            this.flows.addAll(newFlows);
            
            // Rebuild flow index cache for performance optimization
            rebuildFlowIndexCache();
            
            
            if (newFlows.isEmpty()) {
                for (Link link : this.links) {
                    if (link.flow_set != null) {
                        link.flow_set.clear();
                    }
                }
            
            flowColorAssignmentMap.clear();
            System.out.println("[DEBUG] (dragging) No flows - cleared all link flow_sets and color assignments");
            }
            
            
            reapplyTopKFilter();
            
            draw();
            return;
        }
        if (newNodes == null || newLinks == null || newFlows == null) {
            this.nodes.clear();
            this.links.clear();
            this.flows.clear();
            selectedNodes.clear(); 
            
            if (this.flows.isEmpty()) {
                flowColorAssignmentMap.clear();
            }
            draw();
            return;
        }
        
        
        if (newNodes.isEmpty()) {
            this.nodes.clear();
            this.links.clear();
            this.flows.clear();
            selectedNodes.clear();
            
            flowColorAssignmentMap.clear();
            draw();
            return;
        }
        
        
        Set<String> selectedNodeIps = new HashSet<>();
        for (Node selectedNode : selectedNodes) {
            selectedNodeIps.add(selectedNode.ip);
        }
        
        Map<String, int[]> localPos = loadLocalNodePositions();
        this.nodes.clear();
        selectedNodes.clear(); 
        
        // Unified approach: Do not split nodes, keep one node per device
        for (Node n : newNodes) {
            int x = n.x, y = n.y;
            
            // Check if we have saved position for this node (by primary IP)
            if (localPos.containsKey(n.ip)) {
                x = localPos.get(n.ip)[0];
                y = localPos.get(n.ip)[1];
            }
            
            
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
        
        
        if (newFlows.isEmpty()) {
            for (Link link : this.links) {
                if (link.flow_set != null) {
                    link.flow_set.clear();
                }
            }
            
            flowColorAssignmentMap.clear();
            System.out.println("[DEBUG] No flows - cleared all link flow_sets and color assignments");
        }
        
        if (this.flowPos.length != this.flows.size()) {
            double[] newFlowPos = new double[this.flows.size()];
            System.arraycopy(this.flowPos, 0, newFlowPos, 0, Math.min(this.flowPos.length, newFlowPos.length));
            this.flowPos = newFlowPos;
        }
        
        
        reapplyTopKFilter();
        
        if (initialResetZoomPending) {
            initialResetZoomPending = false;
            resetZoom();
            return;
        }
        
        
        
        draw();
    }

    
    private void drawArrow(GraphicsContext gc, double x1, double y1, double x2, double y2, Color color) {
        
        double arrowLength = 10;  
        double arrowWidth = 6;    
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length == 0) return;
        double unitX = dx / length;
        double unitY = dy / length;
        
        double arrowX = x1 + dx * 0.5;  
        double arrowY = y1 + dy * 0.5;
        
        double leftX = arrowX - unitX * arrowLength + unitY * arrowWidth / 2;
        double leftY = arrowY - unitY * arrowLength - unitX * arrowWidth / 2;
        double rightX = arrowX - unitX * arrowLength - unitY * arrowWidth / 2;
        double rightY = arrowY - unitY * arrowLength + unitX * arrowWidth / 2;
        gc.setFill(color);
        gc.setStroke(color.darker());
        gc.setLineWidth(1.0);  
        gc.fillPolygon(new double[]{arrowX, leftX, rightX}, new double[]{arrowY, leftY, rightY}, 3);
        gc.strokePolygon(new double[]{arrowX, leftX, rightX}, new double[]{arrowY, leftY, rightY}, 3);
    }
} 