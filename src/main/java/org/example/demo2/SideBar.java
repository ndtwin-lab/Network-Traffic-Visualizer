package org.example.demo2;

import java.util.List;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

public class SideBar extends VBox {
    private final TopologyCanvas topologyCanvas;
    private List<Flow> flows;

    private FilterButton filterButton;
    private PlaybackPanel playbackPanel;
    private NetworkTopologyApp mainApp;
    private javafx.stage.Stage primaryStage; 
    
    
    private Button currentDisplayModeButton = null;
    private Button currentDisplayOptionButton = null;
    
    
    private Button topKFlowsButton = null;

    // Legend components
    private Separator legendSeparator;
    private Label legendTitleLabel;
    private VBox legendBox;
    
    // Flow Legend components for Flow Only mode
    private Separator flowLegendSeparator;
    private Label flowLegendTitleLabel;
    private VBox flowLegendBox;
    
    
    private boolean isDarkMode = false;
    
    
    private java.util.List<Label> titleLabels = new java.util.ArrayList<>();


    public SideBar(TopologyCanvas topologyCanvas, List<Flow> flows, NetworkTopologyApp mainApp) {
        this.topologyCanvas = topologyCanvas;
        this.flows = flows;
        this.mainApp = mainApp;
        
        // Initialize FilterButton
        this.filterButton = new FilterButton(topologyCanvas, flows);

        setPrefWidth(200);
        setPadding(new Insets(20, 15, 20, 15));
        setSpacing(15);
        
        
        if (topologyCanvas != null) {
            isDarkMode = topologyCanvas.darkMode;
        }
        updateDarkModeStyle();
        
        // Add NDTwin text logo (NDT in blue, win in orange)
        HBox logoBox = new HBox(5); // Add spacing between NDT and win
        logoBox.setAlignment(Pos.CENTER);
        logoBox.setPadding(new Insets(5, 0, 5, 0)); 
        
        Label ndtLabel = new Label("NDT");
        ndtLabel.setFont(Font.font("Arial", FontWeight.BOLD, 36)); // Increased from 28 to 36
        ndtLabel.setTextFill(javafx.scene.paint.Color.web("#3498db")); // Blue
        
        Label winLabel = new Label("win");
        winLabel.setFont(Font.font("Arial", FontWeight.BOLD, 36)); // Increased from 28 to 36
        winLabel.setTextFill(javafx.scene.paint.Color.web("#f39c12")); // Orange
        
        logoBox.getChildren().addAll(ndtLabel, winLabel);
        
        
        VBox displayModeSection = createDisplayModeSection();
        
        
        VBox displayOptionsSection = createDisplayOptionsSection();
        
        
        VBox functionsSection = createFunctionsSection();
        
        
        VBox zoomControlsSection = createZoomControlsSection();
        
        
        VBox displaySettingSection = createDisplaySettingSection();
        
        
        getChildren().addAll(
            logoBox,
            displayModeSection,
            displayOptionsSection,
            functionsSection,
            zoomControlsSection,
            displaySettingSection
        );
        
        
        addLegend();
        addFlowLegend();
        
        
        if (flowLegendBox != null) {
            
            int legendBoxIndex = getChildren().indexOf(legendBox);
            if (legendBoxIndex >= 0) {
                getChildren().set(legendBoxIndex, flowLegendBox);
            }
        }
        
        hideLegend();
        showFlowLegend();
    }
    
    
    private VBox createDisplayModeSection() {
        VBox section = new VBox(8);
        section.setAlignment(Pos.TOP_LEFT);
        
        
        Label titleLabel = new Label("Display mode");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        titleLabel.setAlignment(Pos.CENTER_LEFT);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabels.add(titleLabel); 
        
        
        Button realTimeButton = createSmallButton("Real time");
        realTimeButton.setOnAction(e -> {
            switchToRealTimeMode();
            setDisplayModeButton(realTimeButton);
        });
        
        Button playbackModeButton = createSmallButton("Playback");
        playbackModeButton.setOnAction(e -> {
            togglePlaybackPanel();
            setDisplayModeButton(playbackModeButton);
            
            
            if (mainApp != null && playbackPanel != null) {
                showFlowOnly();
                System.out.println("[SIDEBAR] Switched to playback mode with Flow Only display");
            }
        });
        
        
        setDisplayModeButton(realTimeButton);
        
        section.getChildren().addAll(titleLabel, realTimeButton, playbackModeButton);
        return section;
    }
    
    private VBox createDisplayOptionsSection() {
        VBox section = new VBox(8);
        section.setAlignment(Pos.TOP_LEFT);
        
        
        Label titleLabel = new Label("Display option");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        titleLabel.setAlignment(Pos.CENTER_LEFT);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabels.add(titleLabel); 
        
        
        Button flowPackageButton = createSmallButton("Flow packet traveling");
        flowPackageButton.setOnAction(e -> {
            showFlowOnly();
            setDisplayOptionButton(flowPackageButton);
        });
        
        Button linkUsageButton = createSmallButton("Link utilization");
        linkUsageButton.setOnAction(e -> {
            showLinkOnly();
            setDisplayOptionButton(linkUsageButton);
        });
        
        
        setDisplayOptionButton(flowPackageButton);
        
        section.getChildren().addAll(titleLabel, flowPackageButton, linkUsageButton);
        return section;
    }
    
    private VBox createFunctionsSection() {
        VBox section = new VBox(8);
        section.setAlignment(Pos.TOP_LEFT);
        
        
        Label titleLabel = new Label("Function");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        titleLabel.setAlignment(Pos.CENTER_LEFT);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabels.add(titleLabel); 
        
        
        Button pathFlickerButton = createSmallButton("Flow path flicker");
        pathFlickerButton.setOnAction(e -> showPathFlickerDialog());
        
        Button flowFilterButton = createSmallButton("Flow filter");
        flowFilterButton.setOnAction(e -> {
            
            if (filterButton != null) {
                FlowFilterDialog dialog = filterButton.getFlowFilterDialog();
                if (dialog != null) {
                    if (dialog.isShowing()) {
                        dialog.hide();
                    } else {
                        dialog.show();
                    }
                }
            }
        });
        
        topKFlowsButton = createSmallButton("Top-K flows");
        topKFlowsButton.setOnAction(e -> showTopKFlowsDialog());
        
        
        topKFlowsButton.setOnMouseEntered(e -> {
            
            if (topKFlowsButton.getStyle().contains("#27ae60")) {
                topKFlowsButton.setStyle("-fx-background-color: #229954; -fx-text-fill: white; -fx-border-color: #1e8449; -fx-border-width: 1; -fx-font-weight: bold;");
            } else {
                
                topKFlowsButton.setStyle("-fx-background-color: #d0d0d0; -fx-text-fill: #333333; -fx-border-color: #3498db; -fx-border-width: 1;");
            }
        });
        
        topKFlowsButton.setOnMouseExited(e -> {
            
            updateTopKButtonText();
        });
        
        section.getChildren().addAll(titleLabel, pathFlickerButton, flowFilterButton, topKFlowsButton);
        return section;
    }
    
    private VBox createZoomControlsSection() {
        VBox section = new VBox(8);
        section.setAlignment(Pos.TOP_LEFT);
        
        
        Label titleLabel = new Label("Zoom control");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        titleLabel.setAlignment(Pos.CENTER_LEFT);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabels.add(titleLabel); 
        
        
        Button resetZoomButton = createSmallButton("Reset zoom");
        resetZoomButton.setOnAction(e -> topologyCanvas.resetZoom());
        
        Button fitToWindowButton = createSmallButton("Fit to window");
        fitToWindowButton.setOnAction(e -> topologyCanvas.fitToWindow());
        
        section.getChildren().addAll(titleLabel, resetZoomButton, fitToWindowButton);
        return section;
    }
    
    private VBox createDisplaySettingSection() {
        VBox section = new VBox(8);
        section.setAlignment(Pos.TOP_LEFT);
        
        
        Label titleLabel = new Label("Display setting");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        titleLabel.setAlignment(Pos.CENTER_LEFT);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabels.add(titleLabel); 
        
        
        Button settingButton = createSmallButton("Setting");
        settingButton.setOnAction(e -> showSettings());
        
        section.getChildren().addAll(titleLabel, settingButton);
        return section;
    }
    
    
    private Button createSmallButton(String text) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setPrefHeight(30);
        button.setFont(Font.font("Arial", 12));
        
        
        final String normalStyle, hoverStyle, selectedHoverStyle;
        
        if (isDarkMode) {
            
            normalStyle = "-fx-background-color: #34495e; -fx-text-fill: #ecf0f1; -fx-border-color: #3498db; -fx-border-width: 1;";
            hoverStyle = "-fx-background-color: #3d566e; -fx-text-fill: #ecf0f1; -fx-border-color: #3498db; -fx-border-width: 1;";
            selectedHoverStyle = "-fx-background-color: #2980b9; -fx-text-fill: white; -fx-border-color: #2471a3; -fx-border-width: 1;";
        } else {
            
            normalStyle = "-fx-background-color: #e0e0e0; -fx-text-fill: #333333; -fx-border-color: #cccccc; -fx-border-width: 1;";
            hoverStyle = "-fx-background-color: #d0d0d0; -fx-text-fill: #333333; -fx-border-color: #3498db; -fx-border-width: 1;";
            selectedHoverStyle = "-fx-background-color: #2980b9; -fx-text-fill: white; -fx-border-color: #2471a3; -fx-border-width: 1;";
        }
        
        button.setStyle(normalStyle);
        
        
        button.setUserData(normalStyle);
        
        
        button.setOnMouseEntered(e -> {
            String currentStyle = button.getStyle();
            if (currentStyle.contains("#3498db") && currentStyle.contains("white")) {
                
                button.setStyle(selectedHoverStyle);
            } else {
                
                button.setStyle(hoverStyle);
            }
        });
        
        button.setOnMouseExited(e -> {
            
            
            String currentNormalStyle, currentSelectedStyle;
            if (isDarkMode) {
                currentNormalStyle = "-fx-background-color: #34495e; -fx-text-fill: #ecf0f1; -fx-border-color: #3498db; -fx-border-width: 1;";
                currentSelectedStyle = "-fx-background-color: #3498db; -fx-text-fill: white; -fx-border-color: #2980b9; -fx-border-width: 1;";
            } else {
                currentNormalStyle = "-fx-background-color: #e0e0e0; -fx-text-fill: #333333; -fx-border-color: #cccccc; -fx-border-width: 1;";
                currentSelectedStyle = "-fx-background-color: #3498db; -fx-text-fill: white; -fx-border-color: #2980b9; -fx-border-width: 1;";
            }
            
            
            if (button == currentDisplayModeButton || button == currentDisplayOptionButton) {
                
                button.setStyle(currentSelectedStyle);
            } else {
                
                button.setStyle(currentNormalStyle);
            }
        });
        
        return button;
    }
    
    
    private void switchToRealTimeMode() {
        if (mainApp != null) {
            
            if (playbackPanel != null && playbackPanel.isVisible()) {
                mainApp.togglePlaybackPanel();
            }
        }
    }
    
    
    private void setDisplayModeButton(Button selectedButton) {
        
        if (currentDisplayModeButton != null) {
            if (isDarkMode) {
                currentDisplayModeButton.setStyle("-fx-background-color: #34495e; -fx-text-fill: #ecf0f1; -fx-border-color: #7f8c8d; -fx-border-width: 1;");
            } else {
                currentDisplayModeButton.setStyle("-fx-background-color: #e0e0e0; -fx-text-fill: #333333; -fx-border-color: #cccccc; -fx-border-width: 1;");
            }
        }
        
        
        currentDisplayModeButton = selectedButton;
        currentDisplayModeButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-border-color: #2980b9; -fx-border-width: 1;");
    }
    
    
    private void setDisplayOptionButton(Button selectedButton) {
        
        if (currentDisplayOptionButton != null) {
            if (isDarkMode) {
                currentDisplayOptionButton.setStyle("-fx-background-color: #34495e; -fx-text-fill: #ecf0f1; -fx-border-color: #7f8c8d; -fx-border-width: 1;");
            } else {
                currentDisplayOptionButton.setStyle("-fx-background-color: #e0e0e0; -fx-text-fill: #333333; -fx-border-color: #cccccc; -fx-border-width: 1;");
            }
        }
        
        
        currentDisplayOptionButton = selectedButton;
        currentDisplayOptionButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-border-color: #2980b9; -fx-border-width: 1;");
    }
    
    private void showFlowOnly() {
        
        topologyCanvas.setShowFlows(true);
        topologyCanvas.setShowLinks(false);
        
        
        hideLegend();
        showFlowLegend();
        
        
    }
    
    private void showLinkOnly() {
        
        topologyCanvas.setShowFlows(false);
        topologyCanvas.setShowLinks(true);
        
        
        showLegend();
        hideFlowLegend();
        
        
    }
    
    

    
    private void showSettings() {
        
        showSettingsDialog();
    }
    
    
    private void showSettingsDialog() {
        javafx.scene.control.Dialog<Double> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Flow Animation Settings");
        dialog.setHeaderText("Adjust how long a flow takes to traverse a single link.");
        
        
        if (primaryStage != null) {
            dialog.initOwner(primaryStage);
        }

        javafx.scene.control.ButtonType applyButtonType = new javafx.scene.control.ButtonType("Apply", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(applyButtonType, javafx.scene.control.ButtonType.CANCEL);

        // Dark mode checkbox
        javafx.scene.control.CheckBox darkModeCheck = new javafx.scene.control.CheckBox("Dark Mode");
        darkModeCheck.setSelected(topologyCanvas != null && topologyCanvas.darkMode);
        darkModeCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (topologyCanvas != null) {
                topologyCanvas.setDarkMode(newVal);
            }
            
            setDarkMode(newVal);
        });

        
        assert topologyCanvas != null;
        javafx.scene.control.Slider speedSlider = new javafx.scene.control.Slider(0.5, 10.0, topologyCanvas.getFlowMoveSpeed());
        speedSlider.setShowTickLabels(true);
        speedSlider.setShowTickMarks(true);
        speedSlider.setMajorTickUnit(1.0);
        speedSlider.setMinorTickCount(4);

        javafx.scene.control.Label speedLabel = new javafx.scene.control.Label("Flow traversal time per link: " + String.format("%.1f", speedSlider.getValue()) + " seconds");
        speedSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            speedLabel.setText("Flow traversal time per link: " + String.format("%.1f", newVal.doubleValue()) + " seconds");
            
            topologyCanvas.setFlowMoveSpeed(newVal.doubleValue());
            
            saveSettings(newVal.doubleValue());
        });



        javafx.scene.control.Label infoLabel = new javafx.scene.control.Label(
                """
                        This setting controls how long a flow takes to move
                        from one node to the next (traverse a single link).
                        
                        • Lower values = faster movement
                        • Higher values = slower movement
                        """
        );
        infoLabel.setWrapText(true);

        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(10);
        content.getChildren().add(darkModeCheck); 
        content.getChildren().addAll(infoLabel, speedLabel, speedSlider);
        dialog.getDialogPane().setContent(content);

      
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == applyButtonType) {
                return speedSlider.getValue();
            }
            return null;
        });


        dialog.showAndWait();
    }
    
    private void saveSettings(double flowMoveSpeed) {
        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter("settings.json"))) {
            writer.println("{");
            writer.println("  \"flow_move_speed\": " + flowMoveSpeed);
            writer.println("}");
        } catch (java.io.IOException e) {
            System.err.println("Error saving settings: " + e.getMessage());
        }
    }
    
    
    private javafx.stage.Stage pathFlickerStage = null;
    
    private void showPathFlickerDialog() {
        
        if (pathFlickerStage != null && pathFlickerStage.isShowing()) {
            pathFlickerStage.close();
            return;
        }
        
        
        if (pathFlickerStage == null) {
            pathFlickerStage = new javafx.stage.Stage();
            pathFlickerStage.setTitle("Path Flicker");
            pathFlickerStage.setWidth(350); 
            pathFlickerStage.setHeight(500); 
            pathFlickerStage.initModality(javafx.stage.Modality.NONE);
            pathFlickerStage.setResizable(true); 
            
            
            if (primaryStage != null) {
                pathFlickerStage.initOwner(primaryStage);
            }
        }
        
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #e9ecef; -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8;");
        
        
        Label titleLabel = new Label("Highlight Flow Path");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        titleLabel.setAlignment(Pos.CENTER);
        titleLabel.setMaxWidth(Double.MAX_VALUE);

        
        
        VBox flowContainer = new VBox(10);
        flowContainer.setAlignment(Pos.TOP_LEFT);
        flowContainer.setPadding(new Insets(10));
        
        // Limit display to top 50 flows for performance
        int maxFlowsToShow = Math.min(50, flows.size());
        System.out.println("[PATH-FLICKER] Showing " + maxFlowsToShow + " out of " + flows.size() + " flows");
        
        
        for (int i = 0; i < maxFlowsToShow; i++) {
            Flow flow = flows.get(i);
            
            String srcDeviceNameWithIp = getDeviceNameWithIp(flow.srcIp);
            String dstDeviceNameWithIp = getDeviceNameWithIp(flow.dstIp);
            
            
            javafx.scene.paint.Color flowColor = topologyCanvas.getColorForFlow(flow);
            
            
            javafx.scene.shape.Rectangle colorIcon = new javafx.scene.shape.Rectangle(40, 30);
            colorIcon.setFill(flowColor);
            colorIcon.setStroke(javafx.scene.paint.Color.BLACK);
            colorIcon.setStrokeWidth(1);
            colorIcon.setArcWidth(5);
            colorIcon.setArcHeight(5);
            
            
            Label flowLabel = new Label(srcDeviceNameWithIp + " → " + dstDeviceNameWithIp);
            flowLabel.setFont(Font.font("Arial", 10));
            flowLabel.setMaxWidth(280); 
            flowLabel.setWrapText(true); 
            
            
            VBox flowItemContainer = new VBox(5);
            flowItemContainer.setAlignment(Pos.CENTER);
            flowItemContainer.getChildren().addAll(colorIcon, flowLabel);
            flowItemContainer.setPadding(new Insets(5));
            flowItemContainer.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-radius: 5; -fx-background-radius: 5;");
            
            
            final Flow currentFlow = flow;
            flowItemContainer.setOnMouseClicked(e -> {
                
                Flow currentFlickeredFlow = topologyCanvas.getFlickeredFlow();
                if (currentFlickeredFlow != null && 
                    currentFlickeredFlow.srcIp.equals(currentFlow.srcIp) &&
                    currentFlickeredFlow.dstIp.equals(currentFlow.dstIp) &&
                    currentFlickeredFlow.srcPort == currentFlow.srcPort &&
                    currentFlickeredFlow.dstPort == currentFlow.dstPort) {
                    
                    topologyCanvas.stopFlickering();
                    
                    for (int j = 0; j < flowContainer.getChildren().size(); j++) {
                        javafx.scene.Node node = flowContainer.getChildren().get(j);
                        if (node instanceof VBox) {
                            VBox container = (VBox) node;
                            container.setScaleX(1.0);
                            container.setScaleY(1.0);
                            container.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-radius: 5; -fx-background-radius: 5;");
                        }
                    }
                } else {
                    
                    topologyCanvas.startFlickering(currentFlow);
                    
                    
                    for (int j = 0; j < flowContainer.getChildren().size(); j++) {
                        javafx.scene.Node node = flowContainer.getChildren().get(j);
                        if (node instanceof VBox) {
                            VBox container = (VBox) node;
                            container.setScaleX(1.0);
                            container.setScaleY(1.0);
                            container.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-radius: 5; -fx-background-radius: 5;");
                        }
                    }
                    
                    
                    flowItemContainer.setScaleX(1.2);
                    flowItemContainer.setScaleY(1.2);
                    flowItemContainer.setStyle("-fx-background-color: #e3f2fd; -fx-border-color: #2196f3; -fx-border-width: 2; -fx-border-radius: 5; -fx-background-radius: 5;");
                }
            });
            
            
            flowContainer.getChildren().add(flowItemContainer);
        }
        
        
        javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane(flowContainer);
        scrollPane.setPrefHeight(300);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-radius: 5;");
        
        
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        
        Button stopButton = new Button("Stop Flickering");
        stopButton.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white; -fx-font-weight: bold;");
        stopButton.setOnAction(e -> {
            topologyCanvas.stopFlickering();
            
            for (int j = 0; j < flowContainer.getChildren().size(); j++) {
                javafx.scene.Node node = flowContainer.getChildren().get(j);
                if (node instanceof VBox) {
                    VBox container = (VBox) node;
                    container.setScaleX(1.0);
                    container.setScaleY(1.0);
                    container.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-radius: 5; -fx-background-radius: 5;");
                }
            }
        });
        
        Button closeButton = new Button("Close");
        closeButton.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white; -fx-font-weight: bold;");
        closeButton.setOnAction(e -> {
            topologyCanvas.stopFlickering();
            pathFlickerStage.close();
        });
        
        buttonBox.getChildren().addAll(stopButton, closeButton);
        
        
        if (flows.size() > maxFlowsToShow) {
            Label infoLabel = new Label(
                "Showing top " + maxFlowsToShow + " flows out of " + flows.size() + " total.\n" +
                "Displaying all flows may cause performance issues."
            );
            infoLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #e74c3c; -fx-font-style: italic; " +
                             "-fx-padding: 5; -fx-background-color: #fff3cd; -fx-border-color: #ffc107; " +
                             "-fx-border-radius: 3; -fx-background-radius: 3;");
            infoLabel.setWrapText(true);
            infoLabel.setMaxWidth(310); 
            root.getChildren().addAll(titleLabel, infoLabel, scrollPane, buttonBox);
        } else {
            root.getChildren().addAll(titleLabel, scrollPane, buttonBox);
        }
        
        javafx.scene.Scene scene = new javafx.scene.Scene(root);
        pathFlickerStage.setScene(scene);
        pathFlickerStage.show();
    }
    
    
    private String getDeviceName(String ip) {
        Node node = topologyCanvas.getNodeByIp(ip);
        return node != null ? node.name : ip;
    }
    
    
    private String getDeviceNameWithIp(String ip) {
        Node node = topologyCanvas.getNodeByIp(ip);
        if (node != null && node.name != null) {
            
            String displayIp = convertIpStringForDisplay(ip);
            return node.name + " (" + displayIp + ")"; 
        } else {
            
            return convertIpStringForDisplay(ip);
        }
    }
    
    
    private String convertIpStringForDisplay(String ipString) {
        if (ipString == null || ipString.isEmpty()) {
            return ipString;
        }
        
        
        try {
            int littleEndianIp = Integer.parseInt(ipString);
            
            return String.format("%d.%d.%d.%d",
                (littleEndianIp >> 24) & 0xFF,
                (littleEndianIp >> 16) & 0xFF,
                (littleEndianIp >> 8) & 0xFF,
                littleEndianIp & 0xFF
            );
        } catch (NumberFormatException e) {
            
            
            if (ipString.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                String[] parts = ipString.split("\\.");
                if (parts.length == 4) {
                    try {
                        
                        return String.format("%s.%s.%s.%s", parts[3], parts[2], parts[1], parts[0]);
                    } catch (Exception ex) {
                        
                        return ipString;
                    }
                }
            }
            
            return ipString;
        }
    }
    
    private void showTopKFlowsDialog() {
        
        javafx.scene.control.Dialog<Integer> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Top-K Flows");
        dialog.setHeaderText("Display Top-K Flows by Sending Rate");
        
        
        if (primaryStage != null) {
            dialog.initOwner(primaryStage);
        }
        
        
        javafx.scene.control.ButtonType applyButtonType = new javafx.scene.control.ButtonType("Apply", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        javafx.scene.control.ButtonType showAllButtonType = new javafx.scene.control.ButtonType("Show All", javafx.scene.control.ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(applyButtonType, showAllButtonType, javafx.scene.control.ButtonType.CANCEL);
        
        
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        
        
        Label infoLabel = new Label(
            "This function allows you to display only the top-K flows\n" +
            "with the highest sending rates on the topology.\n\n" +
            "• Flows are ranked by: estimated_flow_sending_rate_bps\n" +
            "• Enter a number K to show only the top-K flows\n" +
            "• Current total flow number: " + flows.size()
        );
        infoLabel.setWrapText(true);
        infoLabel.setFont(Font.font("Arial", 12));
        infoLabel.setStyle("-fx-text-fill: #2c3e50; -fx-background-color: #ecf0f1; -fx-padding: 10; -fx-background-radius: 5;");
        
        
        HBox inputBox = new HBox(10);
        inputBox.setAlignment(Pos.CENTER_LEFT);
        
        Label kLabel = new Label("K =");
        kLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        
        javafx.scene.control.TextField kTextField = new javafx.scene.control.TextField();
        kTextField.setPromptText("Enter K (e.g., 10)");
        kTextField.setPrefWidth(150);
        kTextField.setFont(Font.font("Arial", 13));
        
        
        int currentDisplayingCount = topologyCanvas.getVisibleFlowCount();
        int totalFlowCount = flows.size();
        boolean isTopKEnabled = topologyCanvas.isTopKEnabled();
        
        
        if (isTopKEnabled) {
            kTextField.setText(String.valueOf(currentDisplayingCount));
        } else {
            kTextField.setText(String.valueOf(totalFlowCount));
        }
        
        inputBox.getChildren().addAll(kLabel, kTextField);
        
        
        Label statusLabel;
        if (isTopKEnabled && currentDisplayingCount < totalFlowCount) {
            statusLabel = new Label("Current: Showing " + currentDisplayingCount + " out of " + totalFlowCount + " flows");
        } else {
            statusLabel = new Label("Current: Showing all " + totalFlowCount + " flows");
        }
        statusLabel.setFont(Font.font("Arial", 11));
        statusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-style: italic;");
        
        content.getChildren().addAll(infoLabel, inputBox, statusLabel);
        dialog.getDialogPane().setContent(content);
        
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == applyButtonType) {
                try {
                    int k = Integer.parseInt(kTextField.getText().trim());
                    if (k <= 0) {
                        showAlert("Invalid Input", "K must be a positive number!");
                        return null;
                    }
                    return k;
                } catch (NumberFormatException e) {
                    showAlert("Invalid Input", "Please enter a valid number!");
                    return null;
                }
            } else if (dialogButton == showAllButtonType) {
                return flows.size(); 
            }
            return null;
        });
        
        
        dialog.showAndWait().ifPresent(k -> {
            if (k >= flows.size()) {
                
                topologyCanvas.setTopKFlows(null, 0); 
                updateTopKButtonText(); 
                System.out.println("[SIDEBAR] Showing all flows (K=" + k + " >= total=" + flows.size() + ")");
            } else {
                applyTopKFlows(k);
                System.out.println("[SIDEBAR] Applied Top-K flows filter: K=" + k);
            }
        });
    }
    
    private void applyTopKFlows(int k) {
        if (flows == null || flows.isEmpty()) {
            showAlert("No Flows", "There are no flows to display!");
            return;
        }
        
        
        List<Flow> sortedFlows = new java.util.ArrayList<>(flows);
        sortedFlows.sort((f1, f2) -> Double.compare(
            f2.estimatedFlowSendingRateBpsInTheLastSec, 
            f1.estimatedFlowSendingRateBpsInTheLastSec
        ));
        
        
        int actualK = Math.min(k, sortedFlows.size());
        List<Flow> topKFlows = sortedFlows.subList(0, actualK);
        
        
        System.out.println("[SIDEBAR] Top-K Flows (K=" + k + ", actual=" + actualK + "):");
        for (int i = 0; i < Math.min(5, topKFlows.size()); i++) {
            Flow flow = topKFlows.get(i);
            System.out.println("  [" + (i+1) + "] " + getDeviceName(flow.srcIp) + " → " + 
                             getDeviceName(flow.dstIp) + " | Rate: " + 
                             flow.estimatedFlowSendingRateBpsInTheLastSec + " bps");
        }
        
        
        topologyCanvas.setTopKFlows(topKFlows, k);
        
        
        updateTopKButtonText();
    }
    
    


    public void updateTopKButtonText() {
        if (topKFlowsButton == null) {
            System.out.println("[SIDEBAR] updateTopKButtonText: button is null");
            return;
        }
        
        
        int displayingCount = topologyCanvas.getVisibleFlowCount();
        int totalCount = flows != null ? flows.size() : 0;
        boolean isTopKEnabled = topologyCanvas.isTopKEnabled();
        
        System.out.println("[SIDEBAR] updateTopKButtonText: enabled=" + isTopKEnabled + 
                         ", displaying=" + displayingCount + ", total=" + totalCount);
        
        
        if (isTopKEnabled) {
            
            String buttonText = "Top-K flows (" + displayingCount + "/" + totalCount + ")";
            topKFlowsButton.setText(buttonText);
            
            topKFlowsButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-border-color: #229954; -fx-border-width: 1; -fx-font-weight: bold;");
            System.out.println("[SIDEBAR] Set button text to: " + buttonText + " (green)");
        } else {
            
            topKFlowsButton.setText("Top-K flows");
            
            if (isDarkMode) {
                topKFlowsButton.setStyle("-fx-background-color: #34495e; -fx-text-fill: #ecf0f1; -fx-border-color: #7f8c8d; -fx-border-width: 1;");
            } else {
                topKFlowsButton.setStyle("-fx-background-color: #e0e0e0; -fx-text-fill: #333333; -fx-border-color: #cccccc; -fx-border-width: 1;");
            }
            System.out.println("[SIDEBAR] Set button text to: Top-K flows (gray)");
        }
    }
    
    private void showAlert(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void addLegend() {
        
        legendSeparator = new Separator();
        legendSeparator.setPadding(new Insets(5, 0, 5, 0)); 
        
        legendSeparator.setStyle("-fx-background-color: transparent;");
        
        
        legendTitleLabel = new Label("Link utilization");
        legendTitleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        legendTitleLabel.setAlignment(Pos.CENTER);
        legendTitleLabel.setMaxWidth(Double.MAX_VALUE);
        
        
        legendBox = new VBox(8);
        legendBox.setAlignment(Pos.CENTER);
        legendBox.setPadding(new Insets(15, 5, 15, 5));
        
        
        legendBox.setMinHeight(200);
        
        
        Label congestedLabel = new Label("Congested 100%");
        congestedLabel.setFont(Font.font("Arial", 12));
        congestedLabel.setAlignment(Pos.CENTER);
        
        
        
        Label label75 = new Label("75%");
        label75.setFont(Font.font("Arial", 10));
        label75.setAlignment(Pos.CENTER);
        
        
        
        Label label50 = new Label("50%");
        label50.setFont(Font.font("Arial", 10));
        label50.setAlignment(Pos.CENTER);
        
        
        
        Label label25 = new Label("25%");
        label25.setFont(Font.font("Arial", 10));
        label25.setAlignment(Pos.CENTER);
        
        
        
        Label emptyLabel = new Label("Idle 0%");
        emptyLabel.setFont(Font.font("Arial", 12));
        emptyLabel.setAlignment(Pos.CENTER);
        
        
        
        java.util.List<Label> legendLabels = new java.util.ArrayList<>();
        legendLabels.add(congestedLabel);
        legendLabels.add(label75);
        legendLabels.add(label50);
        legendLabels.add(label25);
        legendLabels.add(emptyLabel);
        
        
        for (Label label : legendLabels) {
            if (isDarkMode) {
                if (label == congestedLabel || label == emptyLabel) {
                    label.setTextFill(javafx.scene.paint.Color.WHITE);
                } else {
                    label.setTextFill(javafx.scene.paint.Color.web("#95a5a6"));
                }
            } else {
                if (label == congestedLabel || label == emptyLabel) {
                    label.setTextFill(javafx.scene.paint.Color.BLACK);
                } else {
                    label.setStyle("-fx-text-fill: #666666;");
                }
            }
        }
        
        
        ImageView gradientImageView = null;
        try {
            
            Image gradientImage = new Image(getClass().getResourceAsStream("/images/gradieantcolor-2.png"));
            gradientImageView = new ImageView(gradientImage);
            gradientImageView.setFitWidth(30); 
            gradientImageView.setFitHeight(120); 
            gradientImageView.setPreserveRatio(false); 
            
        } catch (Exception e) {
            System.err.println("Failed to load gradient image: " + e.getMessage());
        }
        
        if (gradientImageView != null) {
            
            javafx.scene.layout.StackPane gradientContainer = new javafx.scene.layout.StackPane();
            gradientContainer.setPrefWidth(80); 
            gradientContainer.setPrefHeight(120);
            
            
            gradientContainer.getChildren().add(gradientImageView);
            
            
            VBox labelsContainer = new VBox();
            labelsContainer.setAlignment(Pos.CENTER_LEFT);
            labelsContainer.setPadding(new Insets(0, 0, 0, 35)); 
            
            
            labelsContainer.getChildren().addAll(
                new javafx.scene.layout.Region(), 
                label75,
                label50,
                label25,
                new javafx.scene.layout.Region()  
            );
            
            
            labelsContainer.setSpacing(30); 
            
            gradientContainer.getChildren().add(labelsContainer);
            
            legendBox.getChildren().addAll(congestedLabel, gradientContainer, emptyLabel);
        } else {
            legendBox.getChildren().addAll(congestedLabel, emptyLabel);
        }
        
        
        getChildren().addAll(legendSeparator, legendTitleLabel, legendBox);
    }
    
    private void showLegend() {
        
        
        if (flowLegendSeparator != null) flowLegendSeparator.setVisible(true);
        
        
        if (legendTitleLabel != null) {
            legendTitleLabel.setText("Link utilization");
            legendTitleLabel.setVisible(true);
        }
        
        
        if (flowLegendBox != null) flowLegendBox.setVisible(false);
        
        
        if (legendBox != null) {
            legendBox.setVisible(true);
            
            if (!getChildren().contains(legendBox)) {
                int flowLegendIndex = getChildren().indexOf(flowLegendBox);
                if (flowLegendIndex >= 0) {
                    getChildren().set(flowLegendIndex, legendBox);
                }
            }
        }
    }
    
    private void hideLegend() {
        if (legendSeparator != null) legendSeparator.setVisible(false);
        if (legendTitleLabel != null) legendTitleLabel.setVisible(false);
        if (legendBox != null) legendBox.setVisible(false);
    }
    
    private void addFlowLegend() {
        
        flowLegendSeparator = legendSeparator;
        flowLegendTitleLabel = legendTitleLabel;
        
        
        VBox flowContentBox = new VBox(2);
        flowContentBox.setAlignment(Pos.TOP_CENTER);
        flowContentBox.setPadding(new Insets(5));
        flowContentBox.setFillWidth(false); 
        
        
        ScrollPane scrollPane = new ScrollPane(flowContentBox);
        scrollPane.setPrefViewportHeight(200); 
        scrollPane.setMaxHeight(200); 
        
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        
        
        flowLegendBox = new VBox(2);
        flowLegendBox.setAlignment(Pos.TOP_CENTER);
        flowLegendBox.setPadding(new Insets(0, 0, 0, 0));
        
        flowLegendBox.setMinHeight(200);
        flowLegendBox.getChildren().add(scrollPane);
        
        
    }
    
    private void showFlowLegend() {
        
        if (flowLegendSeparator != null) flowLegendSeparator.setVisible(true);
        
        
        if (flowLegendTitleLabel != null) {
            flowLegendTitleLabel.setText("Active flows");
            flowLegendTitleLabel.setVisible(true);
        }
        
        
        if (legendBox != null) legendBox.setVisible(false);
        
        
        if (flowLegendBox != null) {
            flowLegendBox.setVisible(true);
            updateFlowLegend();
            
            if (!getChildren().contains(flowLegendBox)) {
                int legendBoxIndex = getChildren().indexOf(legendBox);
                if (legendBoxIndex >= 0) {
                    getChildren().set(legendBoxIndex, flowLegendBox);
                }
            }
        }
    }
    
    private void hideFlowLegend() {
        
        if (flowLegendBox != null) flowLegendBox.setVisible(false);
    }
    
    private void updateFlowLegend() {
        if (flowLegendBox == null) return;
        
        
        ScrollPane scrollPane = null;
        VBox flowContentBox = null;
        
        if (flowLegendBox.getChildren().size() > 0 && flowLegendBox.getChildren().get(0) instanceof ScrollPane) {
            scrollPane = (ScrollPane) flowLegendBox.getChildren().get(0);
            if (scrollPane.getContent() instanceof VBox) {
                flowContentBox = (VBox) scrollPane.getContent();
            }
        }
        
        if (flowContentBox == null) return;
        
        
        flowContentBox.getChildren().clear();
        
        
        int maxFlowsToShow = Math.min(10, flows.size());
        
        
        double maxLabelWidth = 0;
        Font labelFont = Font.font("Arial", 11);
        for (int i = 0; i < maxFlowsToShow; i++) {
            Flow flow = flows.get(i);
            String srcDeviceNameWithIp = getDeviceNameWithIp(flow.srcIp);
            String dstDeviceNameWithIp = getDeviceNameWithIp(flow.dstIp);
            String labelText = srcDeviceNameWithIp + " → " + dstDeviceNameWithIp;
            Text measure = new Text(labelText);
            measure.setFont(labelFont);
            maxLabelWidth = Math.max(maxLabelWidth, measure.getLayoutBounds().getWidth());
        }
        
        
        for (int i = 0; i < maxFlowsToShow; i++) {
            Flow flow = flows.get(i);
            
            
            javafx.scene.paint.Color flowColor = topologyCanvas.getColorForFlow(flow);
            
            
            javafx.scene.shape.Rectangle colorRectangle = new javafx.scene.shape.Rectangle(12, 12);
            colorRectangle.setFill(flowColor);
            colorRectangle.setStroke(javafx.scene.paint.Color.BLACK);
            colorRectangle.setStrokeWidth(0.5);
            javafx.scene.layout.StackPane colorBox = new javafx.scene.layout.StackPane(colorRectangle);
            colorBox.setMinWidth(18);
            colorBox.setPrefWidth(18);
            colorBox.setMaxWidth(18);
            colorBox.setAlignment(Pos.CENTER);
            
            
            String srcDeviceNameWithIp = getDeviceNameWithIp(flow.srcIp);
            String dstDeviceNameWithIp = getDeviceNameWithIp(flow.dstIp);
            
            
            Label label = new Label(srcDeviceNameWithIp + " → " + dstDeviceNameWithIp);
            label.setFont(labelFont);
            
            if (isDarkMode) {
                label.setStyle("-fx-text-fill: #ecf0f1;");
            } else {
                label.setStyle("-fx-text-fill: #333333;");
            }
            
            
            HBox container = new HBox(8);
            container.setAlignment(Pos.CENTER_LEFT);
            container.setPadding(new Insets(2, 5, 2, 5));
            container.setPrefHeight(20);
            
            label.setMinWidth(0);
            label.setPrefWidth(Math.ceil(maxLabelWidth));
            HBox.setHgrow(label, Priority.NEVER);
            container.setMaxWidth(Double.MAX_VALUE);
            
            
            container.getChildren().addAll(colorBox, label);
            flowContentBox.getChildren().add(container);
        }
        
        
        if (flows.size() > 10) {
            Label infoLabel = new Label("only show 10 Flows，total " + flows.size() + " Flows");
            
            if (isDarkMode) {
                infoLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #95a5a6; -fx-font-style: italic;");
            } else {
                infoLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666666; -fx-font-style: italic;");
            }
            infoLabel.setPadding(new Insets(5, 0, 0, 0));
            infoLabel.setAlignment(Pos.CENTER);
            flowContentBox.getChildren().add(infoLabel);
        }
    }

    
    public void updateData(List<Flow> newFlows, List<Link> newLinks, List<Node> newNodes) {
        this.flows = newFlows;

        
        if (filterButton != null) {
            filterButton.updateData(newFlows);
        }
        
        
        updateFlowLegend();
        
        
        
    }
    
    
    public void setPlaybackPanel(PlaybackPanel playbackPanel) {
        this.playbackPanel = playbackPanel;
    }
    
    
    public void setPrimaryStage(javafx.stage.Stage primaryStage) {
        this.primaryStage = primaryStage;
        
        if (filterButton != null) {
            filterButton.setPrimaryStage(primaryStage);
        }
    }
    
    
    private void togglePlaybackPanel() {
        if (mainApp != null) {
            mainApp.togglePlaybackPanel();
        }
    }
    
    
    public void updatePlaybackButtonStyle(boolean isPlaying) {
        
        System.out.println("Playback button style update: " + (isPlaying ? "playing" : "paused"));
    }
    
    


    public void closeAllDialogs() {
        
        if (pathFlickerStage != null && pathFlickerStage.isShowing()) {
            pathFlickerStage.close();
        }
        
        
        if (filterButton != null && filterButton.getFlowFilterDialog() != null) {
            filterButton.getFlowFilterDialog().hide();
        }
    }
    
    


    public void setDarkMode(boolean dark) {
        this.isDarkMode = dark;
        updateDarkModeStyle();
    }
    
    


    private void updateDarkModeStyle() {
        if (isDarkMode) {
            
            setStyle("-fx-background-color: #2c3e50; -fx-border-color: #34495e;");
            
            
            for (Label label : titleLabels) {
                label.setTextFill(javafx.scene.paint.Color.WHITE);
            }
            
            
            if (legendTitleLabel != null) {
                legendTitleLabel.setTextFill(javafx.scene.paint.Color.WHITE);
            }
            if (flowLegendTitleLabel != null) {
                flowLegendTitleLabel.setTextFill(javafx.scene.paint.Color.WHITE);
            }
            
            
            
            
            if (legendSeparator != null) {
                javafx.application.Platform.runLater(() -> {
                    legendSeparator.setStyle("-fx-background-color: transparent;");
                    javafx.scene.Node line = legendSeparator.lookup(".line");
                    if (line != null && line instanceof javafx.scene.layout.Region) {
                        ((javafx.scene.layout.Region) line).setStyle("-fx-background-color: white;");
                    }
                });
            }
            if (flowLegendSeparator != null) {
                javafx.application.Platform.runLater(() -> {
                    flowLegendSeparator.setStyle("-fx-background-color: transparent;");
                    javafx.scene.Node line = flowLegendSeparator.lookup(".line");
                    if (line != null && line instanceof javafx.scene.layout.Region) {
                        ((javafx.scene.layout.Region) line).setStyle("-fx-background-color: white;");
                    }
                });
            }
            
        } else {
            
            setStyle("-fx-background-color: #f9f9f9; -fx-border-color: #cccccc;");
            
            
            for (Label label : titleLabels) {
                label.setTextFill(javafx.scene.paint.Color.BLACK);
            }
            
            
            if (legendTitleLabel != null) {
                legendTitleLabel.setTextFill(javafx.scene.paint.Color.BLACK);
            }
            if (flowLegendTitleLabel != null) {
                flowLegendTitleLabel.setTextFill(javafx.scene.paint.Color.BLACK);
            }
            
            
            if (legendSeparator != null) {
                legendSeparator.setStyle("");
            }
            if (flowLegendSeparator != null) {
                flowLegendSeparator.setStyle("");
            }
        }
        
        
        updateButtonsDarkMode();
    }
    
    


    private void updateButtonsDarkMode() {
        
        javafx.application.Platform.runLater(() -> {
            
            updateButtonsInNode(this);
        });
    }
    
    


    private void updateButtonsInNode(javafx.scene.Node node) {
        if (node instanceof Button) {
            Button button = (Button) node;
            String currentStyle = button.getStyle();
            
            
            
            
            boolean isSelectedBlue =
                currentStyle.contains("-fx-background-color: #3498db") ||
                currentStyle.contains("-fx-background-color: #2980b9");
            boolean isTopKGreen =
                currentStyle.contains("-fx-background-color: #27ae60") ||
                currentStyle.contains("-fx-background-color: #229954");
            if (isSelectedBlue || isTopKGreen) {
                return;
            }
            
            
            
            final boolean currentDarkMode = this.isDarkMode;
            if (currentDarkMode) {
                
                
                if (currentStyle.contains("#3d566e")) {
                    
                    button.setStyle("-fx-background-color: #3d566e; -fx-text-fill: #ecf0f1; -fx-border-color: #3498db; -fx-border-width: 1;");
                } else {
                    
                    button.setStyle("-fx-background-color: #34495e; -fx-text-fill: #ecf0f1; -fx-border-color: #3498db; -fx-border-width: 1;");
                }
            } else {
                
                
                
                button.setStyle("-fx-background-color: #e0e0e0; -fx-text-fill: #333333; -fx-border-color: #cccccc; -fx-border-width: 1;");
            }
        } else if (node instanceof Parent) {
            
            for (javafx.scene.Node child : ((Parent) node).getChildrenUnmodifiable()) {
                updateButtonsInNode(child);
            }
        }
    }


} 