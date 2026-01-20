package org.example.demo2;

import java.util.List;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.Parent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class SideBar extends VBox {
    private final TopologyCanvas topologyCanvas;
    private List<Flow> flows;

    private FilterButton filterButton;
    private PlaybackPanel playbackPanel;
    private NetworkTopologyApp mainApp;
    private javafx.stage.Stage primaryStage; // 主窗口引用，用於設置子窗口 owner
    
    // 按鈕狀態管理
    private Button currentDisplayModeButton = null;
    private Button currentDisplayOptionButton = null;
    
    // Top-K flows 按鈕引用
    private Button topKFlowsButton = null;

    // Legend components
    private Separator legendSeparator;
    private Label legendTitleLabel;
    private VBox legendBox;
    
    // Flow Legend components for Flow Only mode
    private Separator flowLegendSeparator;
    private Label flowLegendTitleLabel;
    private VBox flowLegendBox;
    
    // Dark mode 狀態
    private boolean isDarkMode = false;
    
    // 存儲所有標題 Label 以便更新顏色
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
        
        // 初始化 dark mode 狀態
        if (topologyCanvas != null) {
            isDarkMode = topologyCanvas.darkMode;
        }
        updateDarkModeStyle();
        
        // Add NDTwin.jpg image
        ImageView logoImageView = null;
        try {
            // Use getResource to load resources from JAR
            Image logoImage = new Image(getClass().getResourceAsStream("/images/NDTwin.jpg"));
            logoImageView = new ImageView(logoImage);
            logoImageView.setFitWidth(180); // Set image width
            logoImageView.setFitHeight(120); // Set image height
            logoImageView.setPreserveRatio(true); // Preserve ratio
        } catch (Exception e) {
            System.err.println("Failed to load NDTwin image: " + e.getMessage());
        }
        
        // ===== Display mode 區域 =====
        VBox displayModeSection = createDisplayModeSection();
        
        // ===== Display options 區域 =====
        VBox displayOptionsSection = createDisplayOptionsSection();
        
        // ===== Functions 區域 =====
        VBox functionsSection = createFunctionsSection();
        
        // ===== Zoom controls 區域 =====
        VBox zoomControlsSection = createZoomControlsSection();
        
        // ===== Display setting 區域 =====
        VBox displaySettingSection = createDisplaySettingSection();
        
        // ===== 添加所有組件 =====
        if (logoImageView != null) {
            getChildren().add(logoImageView);
        }
        getChildren().addAll(
            displayModeSection,
            displayOptionsSection,
            functionsSection,
            zoomControlsSection,
            displaySettingSection
        );
        
        // ===== 添加图例區域（Link Legend 和 Flow Legend 共用同一個位置） =====
        addLegend();
        addFlowLegend();
        
        // 將 Flow Legend 容器加到主容器（替換 Link Legend 的位置）
        if (flowLegendBox != null) {
            // 找到 legendBox 的位置並替換它
            int legendBoxIndex = getChildren().indexOf(legendBox);
            if (legendBoxIndex >= 0) {
                getChildren().set(legendBoxIndex, flowLegendBox);
            }
        }
        
        hideLegend();
        showFlowLegend();
    }
    
    // ===== 創建各個區域的方法 =====
    private VBox createDisplayModeSection() {
        VBox section = new VBox(8);
        section.setAlignment(Pos.TOP_LEFT);
        
        // 大字標題
        Label titleLabel = new Label("Display mode");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        titleLabel.setAlignment(Pos.CENTER_LEFT);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabels.add(titleLabel); // 添加到列表以便更新顏色
        
        // 小字按鈕
        Button realTimeButton = createSmallButton("Real time");
        realTimeButton.setOnAction(e -> {
            switchToRealTimeMode();
            setDisplayModeButton(realTimeButton);
        });
        
        Button playbackModeButton = createSmallButton("Playback");
        playbackModeButton.setOnAction(e -> {
            togglePlaybackPanel();
            setDisplayModeButton(playbackModeButton);
            
            // 當切換到 playback 模式時，自動設置為 Flow Only 模式
            if (mainApp != null && playbackPanel != null) {
                showFlowOnly();
                System.out.println("[SIDEBAR] Switched to playback mode with Flow Only display");
            }
        });
        
        // 設置初始狀態 - Real time為預設選中
        setDisplayModeButton(realTimeButton);
        
        section.getChildren().addAll(titleLabel, realTimeButton, playbackModeButton);
        return section;
    }
    
    private VBox createDisplayOptionsSection() {
        VBox section = new VBox(8);
        section.setAlignment(Pos.TOP_LEFT);
        
        // 大字標題
        Label titleLabel = new Label("Display option");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        titleLabel.setAlignment(Pos.CENTER_LEFT);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabels.add(titleLabel); // 添加到列表以便更新顏色
        
        // 小字按鈕
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
        
        // 設置初始狀態 - Flow package traveling為預設選中
        setDisplayOptionButton(flowPackageButton);
        
        section.getChildren().addAll(titleLabel, flowPackageButton, linkUsageButton);
        return section;
    }
    
    private VBox createFunctionsSection() {
        VBox section = new VBox(8);
        section.setAlignment(Pos.TOP_LEFT);
        
        // 大字標題
        Label titleLabel = new Label("Function");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        titleLabel.setAlignment(Pos.CENTER_LEFT);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabels.add(titleLabel); // 添加到列表以便更新顏色
        
        // 小字按鈕
        Button pathFlickerButton = createSmallButton("Flow path flicker");
        pathFlickerButton.setOnAction(e -> showPathFlickerDialog());
        
        Button flowFilterButton = createSmallButton("Flow filter");
        flowFilterButton.setOnAction(e -> {
            // 打开 flow filter 对话框
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
        
        // 為 Top-K 按鈕添加特殊的 hover 效果
        topKFlowsButton.setOnMouseEntered(e -> {
            // 如果是啟用狀態（綠色），hover 時變深綠色
            if (topKFlowsButton.getStyle().contains("#27ae60")) {
                topKFlowsButton.setStyle("-fx-background-color: #229954; -fx-text-fill: white; -fx-border-color: #1e8449; -fx-border-width: 1; -fx-font-weight: bold;");
            } else {
                // 如果是未啟用狀態（灰色），hover 時變淺灰色
                topKFlowsButton.setStyle("-fx-background-color: #d0d0d0; -fx-text-fill: #333333; -fx-border-color: #3498db; -fx-border-width: 1;");
            }
        });
        
        topKFlowsButton.setOnMouseExited(e -> {
            // 根據當前狀態恢復原本的樣式
            updateTopKButtonText();
        });
        
        section.getChildren().addAll(titleLabel, pathFlickerButton, flowFilterButton, topKFlowsButton);
        return section;
    }
    
    private VBox createZoomControlsSection() {
        VBox section = new VBox(8);
        section.setAlignment(Pos.TOP_LEFT);
        
        // 大字標題
        Label titleLabel = new Label("Zoom control");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        titleLabel.setAlignment(Pos.CENTER_LEFT);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabels.add(titleLabel); // 添加到列表以便更新顏色
        
        // 小字按鈕
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
        
        // 大字標題
        Label titleLabel = new Label("Display setting");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        titleLabel.setAlignment(Pos.CENTER_LEFT);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabels.add(titleLabel); // 添加到列表以便更新顏色
        
        // 小字按鈕
        Button settingButton = createSmallButton("Setting");
        settingButton.setOnAction(e -> showSettings());
        
        section.getChildren().addAll(titleLabel, settingButton);
        return section;
    }
    
    // 創建小字按鈕的輔助方法
    private Button createSmallButton(String text) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setPrefHeight(30);
        button.setFont(Font.font("Arial", 12));
        
        // 根據 dark mode 狀態設置樣式
        final String normalStyle, hoverStyle, selectedStyle, selectedHoverStyle;
        
        if (isDarkMode) {
            // Dark mode 樣式
            normalStyle = "-fx-background-color: #34495e; -fx-text-fill: #ecf0f1; -fx-border-color: #7f8c8d; -fx-border-width: 1;";
            hoverStyle = "-fx-background-color: #3d566e; -fx-text-fill: #ecf0f1; -fx-border-color: #3498db; -fx-border-width: 1;";
            selectedStyle = "-fx-background-color: #3498db; -fx-text-fill: white; -fx-border-color: #2980b9; -fx-border-width: 1;";
            selectedHoverStyle = "-fx-background-color: #2980b9; -fx-text-fill: white; -fx-border-color: #2471a3; -fx-border-width: 1;";
        } else {
            // Light mode 樣式
            normalStyle = "-fx-background-color: #e0e0e0; -fx-text-fill: #333333; -fx-border-color: #cccccc; -fx-border-width: 1;";
            hoverStyle = "-fx-background-color: #d0d0d0; -fx-text-fill: #333333; -fx-border-color: #3498db; -fx-border-width: 1;";
            selectedStyle = "-fx-background-color: #3498db; -fx-text-fill: white; -fx-border-color: #2980b9; -fx-border-width: 1;";
            selectedHoverStyle = "-fx-background-color: #2980b9; -fx-text-fill: white; -fx-border-color: #2471a3; -fx-border-width: 1;";
        }
        
        button.setStyle(normalStyle);
        
        // 保存原始樣式的引用
        button.setUserData(normalStyle);
        
        // 添加hover效果 - 根據當前狀態決定hover樣式
        button.setOnMouseEntered(e -> {
            String currentStyle = button.getStyle();
            if (currentStyle.contains("#3498db") && currentStyle.contains("white")) {
                // 如果是選中狀態（藍色背景 + 白色文字），hover時變深藍色
                button.setStyle(selectedHoverStyle);
            } else {
                // 如果是普通狀態，hover時變淺灰色 + 藍色邊框
                button.setStyle(hoverStyle);
            }
        });
        
        button.setOnMouseExited(e -> {
            // 檢查是否是被選中的按鈕（Display mode 或 Display option）
            if (button == currentDisplayModeButton || button == currentDisplayOptionButton) {
                // 恢復選中狀態的樣式
                button.setStyle(selectedStyle);
            } else {
                // 恢復普通狀態的樣式
                button.setStyle(normalStyle);
            }
        });
        
        return button;
    }
    
    // 切換到Real time模式
    private void switchToRealTimeMode() {
        if (mainApp != null) {
            // 如果當前在playback模式，則切換回real-time模式
            if (playbackPanel != null && playbackPanel.isVisible()) {
                mainApp.togglePlaybackPanel();
            }
        }
    }
    
    // 設置Display mode按鈕狀態
    private void setDisplayModeButton(Button selectedButton) {
        // 重置之前的按鈕
        if (currentDisplayModeButton != null) {
            if (isDarkMode) {
                currentDisplayModeButton.setStyle("-fx-background-color: #34495e; -fx-text-fill: #ecf0f1; -fx-border-color: #7f8c8d; -fx-border-width: 1;");
            } else {
                currentDisplayModeButton.setStyle("-fx-background-color: #e0e0e0; -fx-text-fill: #333333; -fx-border-color: #cccccc; -fx-border-width: 1;");
            }
        }
        
        // 設置新選中的按鈕（選中狀態在 dark mode 和 light mode 下都是藍色）
        currentDisplayModeButton = selectedButton;
        currentDisplayModeButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-border-color: #2980b9; -fx-border-width: 1;");
    }
    
    // 設置Display options按鈕狀態
    private void setDisplayOptionButton(Button selectedButton) {
        // 重置之前的按鈕
        if (currentDisplayOptionButton != null) {
            if (isDarkMode) {
                currentDisplayOptionButton.setStyle("-fx-background-color: #34495e; -fx-text-fill: #ecf0f1; -fx-border-color: #7f8c8d; -fx-border-width: 1;");
            } else {
                currentDisplayOptionButton.setStyle("-fx-background-color: #e0e0e0; -fx-text-fill: #333333; -fx-border-color: #cccccc; -fx-border-width: 1;");
            }
        }
        
        // 設置新選中的按鈕（選中狀態在 dark mode 和 light mode 下都是藍色）
        currentDisplayOptionButton = selectedButton;
        currentDisplayOptionButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-border-color: #2980b9; -fx-border-width: 1;");
    }
    
    private void showFlowOnly() {
        // 只显示flow动画，隐藏link
        topologyCanvas.setShowFlows(true);
        topologyCanvas.setShowLinks(false);
        
        // 隐藏图例，显示 flow legend
        hideLegend();
        showFlowLegend();
        
        // 按鈕樣式已在setDisplayOptionButton中處理
    }
    
    private void showLinkOnly() {
        // 只显示link，隐藏flow动画
        topologyCanvas.setShowFlows(false);
        topologyCanvas.setShowLinks(true);
        
        // 显示图例，隐藏 flow legend
        showLegend();
        hideFlowLegend();
        
        // 按鈕樣式已在setDisplayOptionButton中處理
    }
    
    

    
    private void showSettings() {
        // 显示设置对话框
        showSettingsDialog();
    }
    
    
    private void showSettingsDialog() {
        javafx.scene.control.Dialog<Double> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Flow Animation Settings");
        dialog.setHeaderText("Adjust how long a flow takes to traverse a single link.");
        
        // 設置 owner 為主窗口（方案一）
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
            // 更新 SideBar 的 dark mode 樣式
            setDarkMode(newVal);
        });

        // Flow 在單條 link 上的移動秒數滑塊
        assert topologyCanvas != null;
        javafx.scene.control.Slider speedSlider = new javafx.scene.control.Slider(0.5, 10.0, topologyCanvas.getFlowMoveSpeed());
        speedSlider.setShowTickLabels(true);
        speedSlider.setShowTickMarks(true);
        speedSlider.setMajorTickUnit(1.0);
        speedSlider.setMinorTickCount(4);

        javafx.scene.control.Label speedLabel = new javafx.scene.control.Label("Flow traversal time per link: " + String.format("%.1f", speedSlider.getValue()) + " seconds");
        speedSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            speedLabel.setText("Flow traversal time per link: " + String.format("%.1f", newVal.doubleValue()) + " seconds");
            // 即時更新速度
            topologyCanvas.setFlowMoveSpeed(newVal.doubleValue());
            // 即時保存設置
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
        content.getChildren().add(darkModeCheck); // 先加dark mode
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
    
    // Path Flicker 窗口單例（避免重複創建）
    private javafx.stage.Stage pathFlickerStage = null;
    
    private void showPathFlickerDialog() {
        // 如果窗口已存在且正在顯示，則關閉它（toggle 功能）
        if (pathFlickerStage != null && pathFlickerStage.isShowing()) {
            pathFlickerStage.close();
            return;
        }
        
        // 創建 Path Flicker 對話框
        if (pathFlickerStage == null) {
            pathFlickerStage = new javafx.stage.Stage();
            pathFlickerStage.setTitle("Path Flicker");
            pathFlickerStage.setWidth(350); // 增加寬度從 200 到 350，讓文字可以完整顯示
            pathFlickerStage.setHeight(500); // 稍微增加高度
            pathFlickerStage.initModality(javafx.stage.Modality.NONE);
            pathFlickerStage.setResizable(true); // 改為可調整大小，讓使用者可以根據需要調整
            
            // 設置 owner 為主窗口（方案一）
            if (primaryStage != null) {
                pathFlickerStage.initOwner(primaryStage);
            }
        }
        
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #e9ecef; -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8;");
        
        // 標題
        Label titleLabel = new Label("Highlight Flow Path");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        titleLabel.setAlignment(Pos.CENTER);
        titleLabel.setMaxWidth(Double.MAX_VALUE);

        
        // Flow 顏色圖標垂直排列
        VBox flowContainer = new VBox(10);
        flowContainer.setAlignment(Pos.TOP_LEFT);
        flowContainer.setPadding(new Insets(10));
        
        // Limit display to top 50 flows for performance
        int maxFlowsToShow = Math.min(50, flows.size());
        System.out.println("[PATH-FLICKER] Showing " + maxFlowsToShow + " out of " + flows.size() + " flows");
        
        // 創建 Flow 顏色圖標
        for (int i = 0; i < maxFlowsToShow; i++) {
            Flow flow = flows.get(i);
            // 使用新格式：h1(10.0.0.1) -> h100(10.0.0.100)
            String srcDeviceNameWithIp = getDeviceNameWithIp(flow.srcIp);
            String dstDeviceNameWithIp = getDeviceNameWithIp(flow.dstIp);
            
            // 生成 flow 顏色：使用 TopologyCanvas 的統一顏色邏輯（hash five-tuple + Color specification）
            javafx.scene.paint.Color flowColor = topologyCanvas.getColorForFlow(flow);
            
            // 創建顏色圖標
            javafx.scene.shape.Rectangle colorIcon = new javafx.scene.shape.Rectangle(40, 30);
            colorIcon.setFill(flowColor);
            colorIcon.setStroke(javafx.scene.paint.Color.BLACK);
            colorIcon.setStrokeWidth(1);
            colorIcon.setArcWidth(5);
            colorIcon.setArcHeight(5);
            
            // 創建標籤
            Label flowLabel = new Label(srcDeviceNameWithIp + " → " + dstDeviceNameWithIp);
            flowLabel.setFont(Font.font("Arial", 10));
            flowLabel.setMaxWidth(280); // 增加最大寬度從 120 到 280，配合視窗寬度增加
            flowLabel.setWrapText(true); // 如果文字還是太長，允許換行
            
            // 創建容器
            VBox flowItemContainer = new VBox(5);
            flowItemContainer.setAlignment(Pos.CENTER);
            flowItemContainer.getChildren().addAll(colorIcon, flowLabel);
            flowItemContainer.setPadding(new Insets(5));
            flowItemContainer.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-radius: 5; -fx-background-radius: 5;");
            
            // 添加點擊事件
            final Flow currentFlow = flow;
            flowItemContainer.setOnMouseClicked(e -> {
                // 檢查當前是否已經在閃爍這個 flow
                Flow currentFlickeredFlow = topologyCanvas.getFlickeredFlow();
                if (currentFlickeredFlow != null && 
                    currentFlickeredFlow.srcIp.equals(currentFlow.srcIp) &&
                    currentFlickeredFlow.dstIp.equals(currentFlow.dstIp) &&
                    currentFlickeredFlow.srcPort == currentFlow.srcPort &&
                    currentFlickeredFlow.dstPort == currentFlow.dstPort) {
                    // 如果已經在閃爍這個 flow，則停止閃爍
                    topologyCanvas.stopFlickering();
                    // 恢復所有圖標的正常大小
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
                    // 開始閃爍選中的 flow
                    topologyCanvas.startFlickering(currentFlow);
                    
                    // 恢復所有圖標的正常大小
                    for (int j = 0; j < flowContainer.getChildren().size(); j++) {
                        javafx.scene.Node node = flowContainer.getChildren().get(j);
                        if (node instanceof VBox) {
                            VBox container = (VBox) node;
                            container.setScaleX(1.0);
                            container.setScaleY(1.0);
                            container.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-radius: 5; -fx-background-radius: 5;");
                        }
                    }
                    
                    // 放大選中的圖標
                    flowItemContainer.setScaleX(1.2);
                    flowItemContainer.setScaleY(1.2);
                    flowItemContainer.setStyle("-fx-background-color: #e3f2fd; -fx-border-color: #2196f3; -fx-border-width: 2; -fx-border-radius: 5; -fx-background-radius: 5;");
                }
            });
            
            // 添加到水平容器
            flowContainer.getChildren().add(flowItemContainer);
        }
        
        // 創建滾動面板
        javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane(flowContainer);
        scrollPane.setPrefHeight(300);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-radius: 5;");
        
        // 按鈕區域
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        
        Button stopButton = new Button("Stop Flickering");
        stopButton.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white; -fx-font-weight: bold;");
        stopButton.setOnAction(e -> {
            topologyCanvas.stopFlickering();
            // 恢復所有圖標的正常大小
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
        
        // 添加提示信息（如果flows数量超过限制）
        if (flows.size() > maxFlowsToShow) {
            Label infoLabel = new Label(
                "Showing top " + maxFlowsToShow + " flows out of " + flows.size() + " total.\n" +
                "Displaying all flows may cause performance issues."
            );
            infoLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #e74c3c; -fx-font-style: italic; " +
                             "-fx-padding: 5; -fx-background-color: #fff3cd; -fx-border-color: #ffc107; " +
                             "-fx-border-radius: 3; -fx-background-radius: 3;");
            infoLabel.setWrapText(true);
            infoLabel.setMaxWidth(310); // 增加最大寬度，配合視窗寬度增加
            root.getChildren().addAll(titleLabel, infoLabel, scrollPane, buttonBox);
        } else {
            root.getChildren().addAll(titleLabel, scrollPane, buttonBox);
        }
        
        javafx.scene.Scene scene = new javafx.scene.Scene(root);
        pathFlickerStage.setScene(scene);
        pathFlickerStage.show();
    }
    
    // 獲取 device name
    private String getDeviceName(String ip) {
        Node node = topologyCanvas.getNodeByIp(ip);
        return node != null ? node.name : ip;
    }
    
    // 獲取設備名稱和 IP 地址的格式化字符串，格式：h1 (10.0.0.1)
    private String getDeviceNameWithIp(String ip) {
        Node node = topologyCanvas.getNodeByIp(ip);
        if (node != null && node.name != null) {
            // 將 IP 地址轉換為反轉字節順序顯示格式
            String displayIp = convertIpStringForDisplay(ip);
            return node.name + " (" + displayIp + ")"; // 在 host 名稱和括號之間添加空格
        } else {
            // 如果找不到節點，直接返回反轉字節順序的 IP
            return convertIpStringForDisplay(ip);
        }
    }
    
    // 將 IP 字符串轉換為反轉字節順序顯示格式（10.0.0.1 而不是 1.0.0.10）
    private String convertIpStringForDisplay(String ipString) {
        if (ipString == null || ipString.isEmpty()) {
            return ipString;
        }
        
        // 首先嘗試解析為整數（little-endian 格式）
        try {
            int littleEndianIp = Integer.parseInt(ipString);
            // 反轉字節順序顯示：10.0.0.100 而不是 100.0.0.10
            return String.format("%d.%d.%d.%d",
                (littleEndianIp >> 24) & 0xFF,
                (littleEndianIp >> 16) & 0xFF,
                (littleEndianIp >> 8) & 0xFF,
                littleEndianIp & 0xFF
            );
        } catch (NumberFormatException e) {
            // 如果不是整數，檢查是否已經是點分十進制格式（如 "1.0.0.10"）
            // 如果是，反轉字節順序
            if (ipString.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                String[] parts = ipString.split("\\.");
                if (parts.length == 4) {
                    try {
                        // 反轉字節順序："1.0.0.10" -> "10.0.0.1"
                        return String.format("%s.%s.%s.%s", parts[3], parts[2], parts[1], parts[0]);
                    } catch (Exception ex) {
                        // 如果解析失敗，返回原始字符串
                        return ipString;
                    }
                }
            }
            // 如果不是預期格式，返回原始字符串
            return ipString;
        }
    }
    
    private void showTopKFlowsDialog() {
        // 創建 Top-K Flows 對話框
        javafx.scene.control.Dialog<Integer> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Top-K Flows");
        dialog.setHeaderText("Display Top-K Flows by Sending Rate");
        
        // 設置 owner 為主窗口（方案一）
        if (primaryStage != null) {
            dialog.initOwner(primaryStage);
        }
        
        // 設置按鈕類型
        javafx.scene.control.ButtonType applyButtonType = new javafx.scene.control.ButtonType("Apply", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        javafx.scene.control.ButtonType showAllButtonType = new javafx.scene.control.ButtonType("Show All", javafx.scene.control.ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(applyButtonType, showAllButtonType, javafx.scene.control.ButtonType.CANCEL);
        
        // 創建內容
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        
        // 說明文字
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
        
        // K 值輸入區域
        HBox inputBox = new HBox(10);
        inputBox.setAlignment(Pos.CENTER_LEFT);
        
        Label kLabel = new Label("K =");
        kLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        
        javafx.scene.control.TextField kTextField = new javafx.scene.control.TextField();
        kTextField.setPromptText("Enter K (e.g., 10)");
        kTextField.setPrefWidth(150);
        kTextField.setFont(Font.font("Arial", 13));
        
        // 獲取當前實際顯示的流數量
        int currentDisplayingCount = topologyCanvas.getVisibleFlowCount();
        int totalFlowCount = flows.size();
        boolean isTopKEnabled = topologyCanvas.isTopKEnabled();
        
        // 如果Top-K已啟用，設置默認值為當前K值；否則設為總數
        if (isTopKEnabled) {
            kTextField.setText(String.valueOf(currentDisplayingCount));
        } else {
            kTextField.setText(String.valueOf(totalFlowCount));
        }
        
        inputBox.getChildren().addAll(kLabel, kTextField);
        
        // 當前狀態顯示 - 根據是否啟用Top-K顯示不同信息
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
        
        // 處理結果
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
                return flows.size(); // 返回全部數量，表示顯示所有 flows
            }
            return null;
        });
        
        // 顯示對話框並處理結果
        dialog.showAndWait().ifPresent(k -> {
            if (k >= flows.size()) {
                // 如果 K >= 總 flow 數，表示要顯示全部
                topologyCanvas.setTopKFlows(null, 0); // 清除過濾器
                updateTopKButtonText(); // 更新按鈕文字
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
        
        // 根據 sending rate 排序（降序）
        List<Flow> sortedFlows = new java.util.ArrayList<>(flows);
        sortedFlows.sort((f1, f2) -> Double.compare(
            f2.estimatedFlowSendingRateBpsInTheLastSec, 
            f1.estimatedFlowSendingRateBpsInTheLastSec
        ));
        
        // 取前 K 個 flows
        int actualK = Math.min(k, sortedFlows.size());
        List<Flow> topKFlows = sortedFlows.subList(0, actualK);
        
        // 顯示 debug 信息
        System.out.println("[SIDEBAR] Top-K Flows (K=" + k + ", actual=" + actualK + "):");
        for (int i = 0; i < Math.min(5, topKFlows.size()); i++) {
            Flow flow = topKFlows.get(i);
            System.out.println("  [" + (i+1) + "] " + getDeviceName(flow.srcIp) + " → " + 
                             getDeviceName(flow.dstIp) + " | Rate: " + 
                             flow.estimatedFlowSendingRateBpsInTheLastSec + " bps");
        }
        
        // 應用到 TopologyCanvas（傳入用戶設定的 K 值和實際的 top-K flows）
        topologyCanvas.setTopKFlows(topKFlows, k);
        
        // 更新按鈕文字
        updateTopKButtonText();
    }
    
    /**
     * 更新 Top-K flows 按鈕的文字顯示
     */
    public void updateTopKButtonText() {
        if (topKFlowsButton == null) {
            System.out.println("[SIDEBAR] updateTopKButtonText: button is null");
            return;
        }
        
        // 從 TopologyCanvas 獲取當前的 Top-K 狀態
        int displayingCount = topologyCanvas.getVisibleFlowCount();
        int totalCount = flows != null ? flows.size() : 0;
        boolean isTopKEnabled = topologyCanvas.isTopKEnabled();
        
        System.out.println("[SIDEBAR] updateTopKButtonText: enabled=" + isTopKEnabled + 
                         ", displaying=" + displayingCount + ", total=" + totalCount);
        
        // 修改判斷邏輯：只要 Top-K 啟用了，就顯示綠色按鈕
        if (isTopKEnabled) {
            // Top-K 啟用中，顯示 "Top-K flows (顯示數/總數)"
            String buttonText = "Top-K flows (" + displayingCount + "/" + totalCount + ")";
            topKFlowsButton.setText(buttonText);
            // 改變按鈕顏色以顯示過濾狀態
            topKFlowsButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-border-color: #229954; -fx-border-width: 1; -fx-font-weight: bold;");
            System.out.println("[SIDEBAR] Set button text to: " + buttonText + " (green)");
        } else {
            // Top-K 未啟用，顯示預設文字
            topKFlowsButton.setText("Top-K flows");
            // 恢復預設樣式（根據 dark mode 狀態）
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
        // 分隔线
        legendSeparator = new Separator();
        legendSeparator.setPadding(new Insets(20, 0, 10, 0));
        
        // 图例标题
        legendTitleLabel = new Label("Link utilization");
        legendTitleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        legendTitleLabel.setAlignment(Pos.CENTER);
        legendTitleLabel.setMaxWidth(Double.MAX_VALUE);
        
        // 直向圖例容器
        legendBox = new VBox(8);
        legendBox.setAlignment(Pos.CENTER);
        legendBox.setPadding(new Insets(15, 5, 15, 5));
        
        // Congested 標籤 (100%)
        Label congestedLabel = new Label("Congested 100%");
        congestedLabel.setFont(Font.font("Arial", 12));
        congestedLabel.setAlignment(Pos.CENTER);
        // 根據 dark mode 設置顏色（將在 updateDarkModeStyle 中更新）
        
        // 75% 標籤
        Label label75 = new Label("75%");
        label75.setFont(Font.font("Arial", 10));
        label75.setAlignment(Pos.CENTER);
        // 根據 dark mode 設置顏色（將在 updateDarkModeStyle 中更新）
        
        // 50% 標籤
        Label label50 = new Label("50%");
        label50.setFont(Font.font("Arial", 10));
        label50.setAlignment(Pos.CENTER);
        // 根據 dark mode 設置顏色（將在 updateDarkModeStyle 中更新）
        
        // 25% 標籤
        Label label25 = new Label("25%");
        label25.setFont(Font.font("Arial", 10));
        label25.setAlignment(Pos.CENTER);
        // 根據 dark mode 設置顏色（將在 updateDarkModeStyle 中更新）
        
        // Empty 標籤 (0%)
        Label emptyLabel = new Label("Idle 0%");
        emptyLabel.setFont(Font.font("Arial", 12));
        emptyLabel.setAlignment(Pos.CENTER);
        // 根據 dark mode 設置顏色（將在 updateDarkModeStyle 中更新）
        
        // 存儲圖例標籤以便更新顏色
        java.util.List<Label> legendLabels = new java.util.ArrayList<>();
        legendLabels.add(congestedLabel);
        legendLabels.add(label75);
        legendLabels.add(label50);
        legendLabels.add(label25);
        legendLabels.add(emptyLabel);
        
        // 初始化顏色
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
        
        // 添加直向漸變圖片
        ImageView gradientImageView = null;
        try {
            // 使用getResource加载JAR中的资源
            Image gradientImage = new Image(getClass().getResourceAsStream("/images/gradieantcolor-2.png"));
            gradientImageView = new ImageView(gradientImage);
            gradientImageView.setFitWidth(30); // 直向窄一點
            gradientImageView.setFitHeight(120); // 高一點
            gradientImageView.setPreserveRatio(false); // 填滿
            // 不旋轉
        } catch (Exception e) {
            System.err.println("無法載入漸變圖片: " + e.getMessage());
        }
        
        if (gradientImageView != null) {
            // 創建一個包含漸變圖片和百分比標籤的容器
            javafx.scene.layout.StackPane gradientContainer = new javafx.scene.layout.StackPane();
            gradientContainer.setPrefWidth(80); // 給標籤留出空間
            gradientContainer.setPrefHeight(120);
            
            // 將漸變圖片放在容器中
            gradientContainer.getChildren().add(gradientImageView);
            
            // 創建一個 VBox 來放置百分比標籤
            VBox labelsContainer = new VBox();
            labelsContainer.setAlignment(Pos.CENTER_LEFT);
            labelsContainer.setPadding(new Insets(0, 0, 0, 35)); // 右邊留出空間給標籤
            
            // 計算標籤的位置（從上到下：100%, 75%, 50%, 25%, 0%）
            labelsContainer.getChildren().addAll(
                new javafx.scene.layout.Region(), // 100% 位置（頂部）
                label75,
                label50,
                label25,
                new javafx.scene.layout.Region()  // 0% 位置（底部）
            );
            
            // 設置標籤的間距
            labelsContainer.setSpacing(30); // 120/4 = 30 像素間距
            
            gradientContainer.getChildren().add(labelsContainer);
            
            legendBox.getChildren().addAll(congestedLabel, gradientContainer, emptyLabel);
        } else {
            legendBox.getChildren().addAll(congestedLabel, emptyLabel);
        }
        
        // 將圖例加到主容器
        getChildren().addAll(legendSeparator, legendTitleLabel, legendBox);
    }
    
    private void showLegend() {
        // 顯示分隔線
        if (legendSeparator != null) legendSeparator.setVisible(true);
        
        // 更新標題為 "Link bandwidth utilization"
        if (legendTitleLabel != null) {
            legendTitleLabel.setText("Link utilization");
            legendTitleLabel.setVisible(true);
        }
        
        // 隱藏 Flow Legend
        if (flowLegendBox != null) flowLegendBox.setVisible(false);
        
        // 顯示 Link Legend 的內容
        if (legendBox != null) {
            legendBox.setVisible(true);
            // 將 legendBox 重新添加到正確的位置
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
        // 重用 Link Legend 的分隔線和標題位置
        flowLegendSeparator = legendSeparator;
        flowLegendTitleLabel = legendTitleLabel;
        
        // Flow 內容容器（垂直列表，但允許內容寬度依文字自動變寬，以支援水平捲動）
        VBox flowContentBox = new VBox(2);
        flowContentBox.setAlignment(Pos.TOP_CENTER);
        flowContentBox.setPadding(new Insets(5));
        flowContentBox.setFillWidth(false); // 不強制子節點縮到 ScrollPane 寬度，讓內容可以變寬
        
        // 創建滾動面板
        ScrollPane scrollPane = new ScrollPane(flowContentBox);
        scrollPane.setPrefViewportHeight(150);
        scrollPane.setMaxHeight(150);
        // 垂直方向固定顯示全部（不需要垂直捲動），水平方向如有需要可以捲動
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        
        // Flow 图例容器（包含滾動面板）
        flowLegendBox = new VBox(2);
        flowLegendBox.setAlignment(Pos.TOP_CENTER);
        flowLegendBox.setPadding(new Insets(0, 0, 0, 0));
        flowLegendBox.getChildren().add(scrollPane);
        
        // 注意：不將 Flow Legend 加到主容器，因為它會重用 Link Legend 的位置
    }
    
    private void showFlowLegend() {
        // 顯示分隔線
        if (flowLegendSeparator != null) flowLegendSeparator.setVisible(true);
        
        // 更新標題為 "Active Flows"
        if (flowLegendTitleLabel != null) {
            flowLegendTitleLabel.setText("Active flows");
            flowLegendTitleLabel.setVisible(true);
        }
        
        // 隱藏 Link Legend
        if (legendBox != null) legendBox.setVisible(false);
        
        // 顯示 Flow Legend 的內容
        if (flowLegendBox != null) {
            flowLegendBox.setVisible(true);
            updateFlowLegend();
            // 將 flowLegendBox 重新添加到正確的位置
            if (!getChildren().contains(flowLegendBox)) {
                int legendBoxIndex = getChildren().indexOf(legendBox);
                if (legendBoxIndex >= 0) {
                    getChildren().set(legendBoxIndex, flowLegendBox);
                }
            }
        }
    }
    
    private void hideFlowLegend() {
        // 隱藏 Flow Legend 的內容
        if (flowLegendBox != null) flowLegendBox.setVisible(false);
    }
    
    private void updateFlowLegend() {
        if (flowLegendBox == null) return;
        
        // 獲取滾動面板和內容容器
        ScrollPane scrollPane = null;
        VBox flowContentBox = null;
        
        if (flowLegendBox.getChildren().size() > 0 && flowLegendBox.getChildren().get(0) instanceof ScrollPane) {
            scrollPane = (ScrollPane) flowLegendBox.getChildren().get(0);
            if (scrollPane.getContent() instanceof VBox) {
                flowContentBox = (VBox) scrollPane.getContent();
            }
        }
        
        if (flowContentBox == null) return;
        
        // 清空現有內容
        flowContentBox.getChildren().clear();
        
        // 只顯示前10個 Flow
        int maxFlowsToShow = Math.min(10, flows.size());
        
        // 為每個 flow 創建圖例項目
        for (int i = 0; i < maxFlowsToShow; i++) {
            Flow flow = flows.get(i);
            
            // 生成 flow 顏色：使用 TopologyCanvas 的統一顏色邏輯（hash five-tuple + Color specification）
            javafx.scene.paint.Color flowColor = topologyCanvas.getColorForFlow(flow);
            
            // 創建顏色長方形
            javafx.scene.shape.Rectangle colorRectangle = new javafx.scene.shape.Rectangle(12, 12);
            colorRectangle.setFill(flowColor);
            colorRectangle.setStroke(javafx.scene.paint.Color.BLACK);
            colorRectangle.setStrokeWidth(0.5);
            
            // 獲取設備名稱和 IP（格式：h1(10.0.0.1)）
            String srcDeviceNameWithIp = getDeviceNameWithIp(flow.srcIp);
            String dstDeviceNameWithIp = getDeviceNameWithIp(flow.dstIp);
            
            // 創建標籤
            Label label = new Label(srcDeviceNameWithIp + " → " + dstDeviceNameWithIp);
            label.setFont(Font.font("Arial", 11));
            // 根據 dark mode 設置文字顏色
            if (isDarkMode) {
                label.setStyle("-fx-text-fill: #ecf0f1;");
            } else {
                label.setStyle("-fx-text-fill: #333333;");
            }
            
            // 使用 HBox 來對齊，確保所有 flow item 的顏色方塊和文字都對齊
            HBox container = new HBox(8);
            container.setAlignment(Pos.CENTER_LEFT);
            container.setPadding(new Insets(2, 5, 2, 5));
            container.setPrefHeight(20);
            // 不固定寬度，讓文字可以撐開，配合 ScrollPane 水平捲動
            
            // 將顏色方塊和標籤加入 HBox，自然對齊
            container.getChildren().addAll(colorRectangle, label);
            flowContentBox.getChildren().add(container);
        }
        
        // 如果 Flow 數量超過10個，添加提示信息
        if (flows.size() > 10) {
            Label infoLabel = new Label("only show 10 Flows，total " + flows.size() + " Flows");
            // 根據 dark mode 設置文字顏色
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

    // 新增：更新數據
    public void updateData(List<Flow> newFlows, List<Link> newLinks, List<Node> newNodes) {
        this.flows = newFlows;

        // 更新 filterButton 的數據
        if (filterButton != null) {
            filterButton.updateData(newFlows);
        }
        
        // 更新 flow legend
        updateFlowLegend();
        
        // 注意：不在這裡更新 Top-K 按鈕，因為 TopologyCanvas 會在 reapplyTopKFilter() 後主動通知
        // updateTopKButtonText(); // 移除，避免與 TopologyCanvas 的通知產生競態條件
    }
    
    // 設置 PlaybackPanel 引用
    public void setPlaybackPanel(PlaybackPanel playbackPanel) {
        this.playbackPanel = playbackPanel;
    }
    
    // 設置主窗口引用（用於設置子窗口 owner）
    public void setPrimaryStage(javafx.stage.Stage primaryStage) {
        this.primaryStage = primaryStage;
        // 同時設置 FilterButton 的 primaryStage
        if (filterButton != null) {
            filterButton.setPrimaryStage(primaryStage);
        }
    }
    
    // 切換 Playback Panel 顯示/隱藏
    private void togglePlaybackPanel() {
        if (mainApp != null) {
            mainApp.togglePlaybackPanel();
        }
    }
    
    // 更新播放按鈕樣式
    public void updatePlaybackButtonStyle(boolean isPlaying) {
        // 由於新的設計，這個方法可能需要重新實現
        System.out.println("Playback button style update: " + (isPlaying ? "playing" : "paused"));
    }
    
    /**
     * 關閉所有對話框（在主窗口關閉時調用）
     */
    public void closeAllDialogs() {
        // 關閉 Path Flicker 窗口
        if (pathFlickerStage != null && pathFlickerStage.isShowing()) {
            pathFlickerStage.close();
        }
        
        // 關閉 Flow Filter 對話框
        if (filterButton != null && filterButton.getFlowFilterDialog() != null) {
            filterButton.getFlowFilterDialog().hide();
        }
    }
    
    /**
     * 設置 dark mode 狀態並更新樣式
     */
    public void setDarkMode(boolean dark) {
        this.isDarkMode = dark;
        updateDarkModeStyle();
    }
    
    /**
     * 更新 SideBar 的 dark mode 樣式
     */
    private void updateDarkModeStyle() {
        if (isDarkMode) {
            // Dark mode 樣式：深色背景，淺色文字
            setStyle("-fx-background-color: #2c3e50; -fx-border-color: #34495e;");
            
            // 更新所有標題 Label 的文字顏色為白色
            for (Label label : titleLabels) {
                label.setTextFill(javafx.scene.paint.Color.WHITE);
            }
            
            // 更新圖例標題顏色
            if (legendTitleLabel != null) {
                legendTitleLabel.setTextFill(javafx.scene.paint.Color.WHITE);
            }
            if (flowLegendTitleLabel != null) {
                flowLegendTitleLabel.setTextFill(javafx.scene.paint.Color.WHITE);
            }
            
            // 更新分隔線顏色（在 dark mode 下使用較亮的顏色）
            if (legendSeparator != null) {
                legendSeparator.setStyle("-fx-background-color: #7f8c8d;");
            }
            if (flowLegendSeparator != null) {
                flowLegendSeparator.setStyle("-fx-background-color: #7f8c8d;");
            }
            
        } else {
            // Light mode 樣式：淺色背景，深色文字
            setStyle("-fx-background-color: #f9f9f9; -fx-border-color: #cccccc;");
            
            // 更新所有標題 Label 的文字顏色為黑色
            for (Label label : titleLabels) {
                label.setTextFill(javafx.scene.paint.Color.BLACK);
            }
            
            // 更新圖例標題顏色
            if (legendTitleLabel != null) {
                legendTitleLabel.setTextFill(javafx.scene.paint.Color.BLACK);
            }
            if (flowLegendTitleLabel != null) {
                flowLegendTitleLabel.setTextFill(javafx.scene.paint.Color.BLACK);
            }
            
            // 恢復分隔線默認樣式
            if (legendSeparator != null) {
                legendSeparator.setStyle("");
            }
            if (flowLegendSeparator != null) {
                flowLegendSeparator.setStyle("");
            }
        }
        
        // 更新按鈕樣式（如果按鈕已創建）
        updateButtonsDarkMode();
    }
    
    /**
     * 更新所有按鈕的 dark mode 樣式
     */
    private void updateButtonsDarkMode() {
        // 遍歷所有子節點，更新按鈕樣式
        updateButtonsInNode(this);
    }
    
    /**
     * 遞歸更新節點及其子節點中的按鈕樣式
     */
    private void updateButtonsInNode(javafx.scene.Node node) {
        if (node instanceof Button) {
            Button button = (Button) node;
            String currentStyle = button.getStyle();
            
            // 跳過已經選中的按鈕（藍色背景）和 Top-K 按鈕（綠色背景）
            if (currentStyle.contains("#3498db") || currentStyle.contains("#27ae60")) {
                return;
            }
            
            if (isDarkMode) {
                // Dark mode 按鈕樣式：深色背景，淺色文字
                if (currentStyle.contains("white") || currentStyle.contains("#f9f9f9")) {
                    // 已經是淺色，不需要更新
                    return;
                }
                // 更新普通按鈕樣式
                String darkNormalStyle = "-fx-background-color: #34495e; -fx-text-fill: #ecf0f1; -fx-border-color: #7f8c8d; -fx-border-width: 1;";
                String darkHoverStyle = "-fx-background-color: #3d566e; -fx-text-fill: #ecf0f1; -fx-border-color: #3498db; -fx-border-width: 1;";
                
                // 檢查是否是 hover 狀態
                if (currentStyle.contains("#d0d0d0") || currentStyle.contains("#e0e0e0")) {
                    button.setStyle(darkHoverStyle);
                } else {
                    button.setStyle(darkNormalStyle);
                }
            } else {
                // Light mode 按鈕樣式：淺色背景，深色文字
                if (currentStyle.contains("#34495e") || currentStyle.contains("#3d566e")) {
                    // 從 dark mode 恢復到 light mode
                    String lightNormalStyle = "-fx-background-color: #e0e0e0; -fx-text-fill: #333333; -fx-border-color: #cccccc; -fx-border-width: 1;";
                    String lightHoverStyle = "-fx-background-color: #d0d0d0; -fx-text-fill: #333333; -fx-border-color: #3498db; -fx-border-width: 1;";
                    
                    if (currentStyle.contains("#3d566e")) {
                        button.setStyle(lightHoverStyle);
                    } else {
                        button.setStyle(lightNormalStyle);
                    }
                }
            }
        } else if (node instanceof Parent) {
            // 遞歸處理子節點
            for (javafx.scene.Node child : ((Parent) node).getChildrenUnmodifiable()) {
                updateButtonsInNode(child);
            }
        }
    }


} 