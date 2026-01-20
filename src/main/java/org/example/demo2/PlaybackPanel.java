package org.example.demo2;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class PlaybackPanel extends VBox {
    private final NetworkTopologyApp mainApp;
    
    // 智能對齊策略的數據結構
    private static class AlignedData {
        PlaybackDataReader.TopologySnapshot topoSnapshot;
        List<PlaybackDataReader.FlowSnapshot> flowSnapshots;
        int topoIndex;
        int flowIndex;
        
        AlignedData(PlaybackDataReader.TopologySnapshot topoSnapshot, 
                   List<PlaybackDataReader.FlowSnapshot> flowSnapshots,
                   int topoIndex, int flowIndex) {
            this.topoSnapshot = topoSnapshot;
            this.flowSnapshots = flowSnapshots;
            this.topoIndex = topoIndex;
            this.flowIndex = flowIndex;
        }
    }
    
    private PlaybackData playbackData;
    private int currentFrameIndex = 0;
    private boolean isPlaying = false;
    private double playbackSpeed = 1.0; // 播放速度倍數
    
    // File upload components
    private VBox flowDataPanel;
    private VBox graphDataPanel;
    private Label flowDataStatus;
    private Label graphDataStatus;
    private Label flowStatus;  // For compact version
    private Label graphStatus; // For compact version
    private File flowDataFile;
    private File graphDataFile;
    // Built indexes
    private NdjsonIndexUtil.BuiltIndex flowIndex;
    private NdjsonIndexUtil.BuiltIndex topoIndex;
    
    // Playback data reader for efficient time-based queries
    private PlaybackDataReader dataReader;
    private PlaybackDataReader.TimeRange timeRange;
    
    // Time range components
    private TextField startTimeField;
    private TextField endTimeField;
    private Label selectedRangeLabel;
    private Label durationLabel;
    
    // Timeline components
    private Slider timelineSlider;
    private Label currentTimeLabel;
    private Button prevButton;
    private Button playPauseButton;
    private Button nextButton;
    private Button stopButton;
    private ComboBox<String> speedComboBox;
    
    // Jump to time components
    private ComboBox<Integer> jumpYearCombo;
    private ComboBox<Integer> jumpMonthCombo;
    private ComboBox<Integer> jumpDayCombo;
    private ComboBox<Integer> jumpHourCombo;
    private ComboBox<Integer> jumpMinuteCombo;
    private ComboBox<Integer> jumpSecondCombo;
    private Button jumpButton;
    
    // Flag to prevent slider listener from interfering with programmatic slider updates
    private volatile boolean isUpdatingSliderProgrammatically = false;
    
    // File choosers
    private FileChooser flowDataChooser;
    private FileChooser graphDataChooser;
    
    // Loading UI components removed - now handled by main app
    
    public PlaybackPanel(TopologyCanvas topologyCanvas, NetworkTopologyApp mainApp) {
        this.mainApp = mainApp;
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        
        // Initialize with empty state
        enablePlaybackControls(false);
    }
    
    private void initializeComponents() {
        // Initialize file choosers
        flowDataChooser = new FileChooser();
        flowDataChooser.setTitle("Select Flow Data File");
        flowDataChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("JSON Files", "*.json")
        );
        
        graphDataChooser = new FileChooser();
        graphDataChooser.setTitle("Select Topology Data File");
        graphDataChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("JSON Files", "*.json")
        );
        
        // Initialize loading UI
        // Loading UI initialization removed - now handled by main app
        
        // Initialize file upload panels
        initializeFileUploadPanels();
        
        // Initialize time range components
        initializeTimeRangeComponents();
        
        // Initialize timeline components
        initializeTimelineComponents();
        
        // Initialize jump to time components
        initializeJumpToTimeComponents();
        
        // Initialize with default time range
        updateTimeRange();
    }
    
    // Loading UI initialization removed - now handled by main app
    
    // Loading UI methods removed - now handled by main app
    
    private void initializeFileUploadPanels() {
        // Flow Data Panel
        flowDataPanel = new VBox(8);
        flowDataPanel.setPadding(new Insets(10));
        flowDataPanel.setStyle("-fx-border-color: #27ae60; -fx-border-style: dashed; -fx-border-width: 2; -fx-background-color: #f8fff8;");
        flowDataPanel.setPrefHeight(80);
        
        HBox flowDataHeader = new HBox(10);
        flowDataHeader.setAlignment(Pos.CENTER_LEFT);
        
        Circle flowDataIcon = new Circle(8, Color.GREEN);
        Label flowDataTitle = new Label("Flow Data");
        flowDataTitle.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        
        flowDataStatus = new Label("No file uploaded");
        flowDataStatus.setFont(Font.font("Arial", 12));
        flowDataStatus.setStyle("-fx-text-fill: #7f8c8d;");
        
        Button flowDataRemoveButton = new Button("Remove");
        flowDataRemoveButton.setStyle("-fx-text-fill: #e74c3c; -fx-background-color: transparent; -fx-underline: true;");
        flowDataRemoveButton.setVisible(false);
        
        flowDataHeader.getChildren().addAll(flowDataIcon, flowDataTitle);
        flowDataPanel.getChildren().addAll(flowDataHeader, flowDataStatus, flowDataRemoveButton);
        
        // Graph Data Panel
        graphDataPanel = new VBox(8);
        graphDataPanel.setPadding(new Insets(10));
        graphDataPanel.setStyle("-fx-border-color: #bdc3c7; -fx-border-style: dashed; -fx-border-width: 2; -fx-background-color: #f8f9fa;");
        graphDataPanel.setPrefHeight(80);
        
        HBox graphDataHeader = new HBox(10);
        graphDataHeader.setAlignment(Pos.CENTER_LEFT);
        
        Circle graphDataIcon = new Circle(8, Color.GRAY);
        Label graphDataTitle = new Label("Topology Data");
        graphDataTitle.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        
        graphDataStatus = new Label("Drag and drop a file here, or browse");
        graphDataStatus.setFont(Font.font("Arial", 12));
        graphDataStatus.setStyle("-fx-text-fill: #7f8c8d;");
        
        Button graphDataBrowseButton = new Button("browse");
        graphDataBrowseButton.setStyle("-fx-text-fill: #3498db; -fx-background-color: transparent; -fx-underline: true;");
        
        graphDataHeader.getChildren().addAll(graphDataIcon, graphDataTitle);
        graphDataPanel.getChildren().addAll(graphDataHeader, graphDataStatus, graphDataBrowseButton);
    }
    
    private void initializeTimeRangeComponents() {
        // Start Time
        startTimeField = new TextField();
        startTimeField.setPromptText("yyyy-MM-dd HH:mm:ss");
        startTimeField.setPrefWidth(180);
        
        // End Time
        endTimeField = new TextField();
        endTimeField.setPromptText("yyyy-MM-dd HH:mm:ss");
        endTimeField.setPrefWidth(180);
        
        // Now buttons removed - time range will be set from file data
        
        // Selected Range Display
        selectedRangeLabel = new Label("Selected Range:");
        selectedRangeLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        
        durationLabel = new Label("Duration: 0 seconds");
        durationLabel.setFont(Font.font("Arial", 12));
        durationLabel.setStyle("-fx-text-fill: #27ae60;");
    }
    
    private void initializeTimelineComponents() {
        // Timeline Slider (seconds from start)
        timelineSlider = new Slider();
        timelineSlider.setMin(0);
        timelineSlider.setMax(0); // set after loading data
        timelineSlider.setValue(0);
        timelineSlider.setDisable(true);
        timelineSlider.setPrefWidth(300);
        
        // Current Time Label
        currentTimeLabel = new Label("No data");
        currentTimeLabel.setFont(Font.font("Arial", 12));
        currentTimeLabel.setStyle("-fx-text-fill: #7f8c8d;");
        
        // Progress Label
        
        // Playback Controls
        prevButton = new Button("⏪ -5s");
        prevButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 14;");
        prevButton.setDisable(true);
        prevButton.setTooltip(new Tooltip("Jump back 5 seconds"));
        
        playPauseButton = new Button("▶");
        playPauseButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 16;");
        playPauseButton.setDisable(true);
        
        nextButton = new Button("⏩ +5s");
        nextButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 14;");
        nextButton.setDisable(true);
        nextButton.setTooltip(new Tooltip("Jump forward 5 seconds"));
        
        stopButton = new Button("⏹");
        stopButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 16;");
        stopButton.setDisable(true);
        
        // Speed Control
        speedComboBox = new ComboBox<>();
        speedComboBox.getItems().addAll("0.5x", "1x", "2x", "4x", "8x");
        speedComboBox.setValue("1x");
        speedComboBox.setPrefWidth(60);
    }
    
    private void initializeJumpToTimeComponents() {
        // Year combo (范围：2000-2050)
        jumpYearCombo = new ComboBox<>();
        jumpYearCombo.setEditable(false); // 不可编辑，只能选择
        for (int year = 2000; year <= 2050; year++) {
            jumpYearCombo.getItems().add(year);
        }
        jumpYearCombo.setPrefWidth(80);
        jumpYearCombo.setPrefHeight(25);
        jumpYearCombo.setPromptText("Year");
        jumpYearCombo.setStyle("-fx-font-size: 12;");
        
        // Month combo (1-12)
        jumpMonthCombo = new ComboBox<>();
        jumpMonthCombo.setEditable(false);
        for (int month = 1; month <= 12; month++) {
            jumpMonthCombo.getItems().add(month);
        }
        jumpMonthCombo.setPrefWidth(65);
        jumpMonthCombo.setPrefHeight(25);
        jumpMonthCombo.setPromptText("Mon");
        jumpMonthCombo.setStyle("-fx-font-size: 12;");
        
        // Day combo (1-31)
        jumpDayCombo = new ComboBox<>();
        jumpDayCombo.setEditable(false);
        for (int day = 1; day <= 31; day++) {
            jumpDayCombo.getItems().add(day);
        }
        jumpDayCombo.setPrefWidth(65);
        jumpDayCombo.setPrefHeight(25);
        jumpDayCombo.setPromptText("Day");
        jumpDayCombo.setStyle("-fx-font-size: 12;");
        
        // Hour combo (0-23)
        jumpHourCombo = new ComboBox<>();
        jumpHourCombo.setEditable(false);
        for (int hour = 0; hour <= 23; hour++) {
            jumpHourCombo.getItems().add(hour);
        }
        jumpHourCombo.setPrefWidth(65);
        jumpHourCombo.setPrefHeight(25);
        jumpHourCombo.setPromptText("Hr");
        jumpHourCombo.setStyle("-fx-font-size: 12;");
        
        // Minute combo (0-59)
        jumpMinuteCombo = new ComboBox<>();
        jumpMinuteCombo.setEditable(false);
        for (int minute = 0; minute <= 59; minute++) {
            jumpMinuteCombo.getItems().add(minute);
        }
        jumpMinuteCombo.setPrefWidth(65);
        jumpMinuteCombo.setPrefHeight(25);
        jumpMinuteCombo.setPromptText("Min");
        jumpMinuteCombo.setStyle("-fx-font-size: 12;");
        
        // Second combo (0-59)
        jumpSecondCombo = new ComboBox<>();
        jumpSecondCombo.setEditable(false);
        for (int second = 0; second <= 59; second++) {
            jumpSecondCombo.getItems().add(second);
        }
        jumpSecondCombo.setPrefWidth(65);
        jumpSecondCombo.setPrefHeight(25);
        jumpSecondCombo.setPromptText("Sec");
        jumpSecondCombo.setStyle("-fx-font-size: 12;");
        
        // Jump button
        jumpButton = new Button("Jump");
        jumpButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 11; -fx-font-weight: bold;");
        jumpButton.setPrefWidth(60);
        jumpButton.setPrefHeight(25);
        jumpButton.setDisable(true);
        jumpButton.setTooltip(new Tooltip("Jump to specified time"));
        
        // Set all combos to initially disabled
        jumpYearCombo.setDisable(true);
        jumpMonthCombo.setDisable(true);
        jumpDayCombo.setDisable(true);
        jumpHourCombo.setDisable(true);
        jumpMinuteCombo.setDisable(true);
        jumpSecondCombo.setDisable(true);
    }
    
    private void setupLayout() {
        setSpacing(0);
        setPadding(new Insets(10, 10, 10, 10)); // 上下左右统一padding
        setStyle("-fx-background-color: #f9f9f9; -fx-border-color: #cccccc;");
        
        // Set preferred height - reduced for more compact layout
        setPrefHeight(130);
        setMaxHeight(130);
        setMinHeight(130);
        
        // Clear existing children to avoid duplicates
        getChildren().clear();
        
        // Create horizontal layout for all controls
        HBox mainContent = new HBox(20);
        mainContent.setAlignment(Pos.CENTER_LEFT);
        
        // Left side - File upload (compact)
        VBox leftSection = createCompactDataUploadSection();
        
        // Middle - Time range (compact)
        VBox middleSection = createCompactTimeRangeSection();
        
        // Right side - Jump to time and Timeline controls (stacked vertically)
        VBox rightSection = createJumpAndTimelineSection();
        
        mainContent.getChildren().addAll(leftSection, middleSection, rightSection);
        
        // Loading overlay removed - now handled by main app
        getChildren().addAll(mainContent);
    }

    private VBox createCompactDataUploadSection() {
        VBox section = new VBox(5);
        
        // Title - 放大字体
        Label title = new Label("Open trace files");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        title.setStyle("-fx-text-fill: #2c3e50;");
        
        // Compact file panels
        HBox filePanels = new HBox(12);
        filePanels.setAlignment(Pos.CENTER_LEFT);
        
        // Flow Data Panel (compact) - 优化样式
        VBox flowDataCompact = new VBox(5);
        flowDataCompact.setPadding(new Insets(8));
        // Set style based on whether file is loaded
        if (flowDataFile != null) {
            flowDataCompact.setStyle("-fx-border-color: #27ae60; -fx-border-style: solid; -fx-border-width: 2; -fx-border-radius: 5; -fx-background-color: #f0f9f4; -fx-background-radius: 5; -fx-effect: dropshadow(gaussian, rgba(39, 174, 96, 0.3), 4, 0, 0, 1);");
        } else {
            flowDataCompact.setStyle("-fx-border-color: #bdc3c7; -fx-border-style: solid; -fx-border-width: 2; -fx-border-radius: 5; -fx-background-color: #ffffff; -fx-background-radius: 5; -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.1), 3, 0, 0, 1);");
        }
        flowDataCompact.setPrefWidth(150);
        flowDataCompact.setPrefHeight(85);
        
        HBox flowHeader = new HBox(6);
        flowHeader.setAlignment(Pos.CENTER_LEFT);
        Circle flowIcon = new Circle(5, flowDataFile != null ? Color.web("#27ae60") : Color.web("#95a5a6"));
        Label flowTitle = new Label("Flow data file");
        flowTitle.setFont(Font.font("Arial", FontWeight.BOLD, 11));
        flowTitle.setStyle(flowDataFile != null ? "-fx-text-fill: #27ae60;" : "-fx-text-fill: #34495e;");
        flowHeader.getChildren().addAll(flowIcon, flowTitle);
        
        flowStatus = new Label(flowDataFile != null ? flowDataFile.getName() : "No file selected");
        flowStatus.setFont(Font.font("Arial", 9));
        flowStatus.setStyle(flowDataFile != null ? "-fx-text-fill: #27ae60;" : "-fx-text-fill: #7f8c8d;");
        flowStatus.setWrapText(true);
        flowStatus.setMaxWidth(130);
        
        Button flowBrowse = new Button("Open");
        flowBrowse.setStyle(flowDataFile != null ? 
            "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 10; -fx-font-weight: bold; -fx-border-radius: 3; -fx-background-radius: 3; -fx-cursor: hand;" : 
            "-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 10; -fx-font-weight: bold; -fx-border-radius: 3; -fx-background-radius: 3; -fx-cursor: hand;");
        flowBrowse.setPrefWidth(65);
        flowBrowse.setPrefHeight(24);
        flowBrowse.setOnAction(e -> loadFlowDataFile());
        
        flowDataCompact.getChildren().addAll(flowHeader, flowStatus, flowBrowse);
        
        // Graph Data Panel (compact) - 优化样式
        VBox graphDataCompact = new VBox(5);
        graphDataCompact.setPadding(new Insets(8));
        graphDataCompact.setStyle("-fx-border-color: #bdc3c7; -fx-border-style: solid; -fx-border-width: 2; -fx-border-radius: 5; -fx-background-color: #ffffff; -fx-background-radius: 5; -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.1), 3, 0, 0, 1);");
        graphDataCompact.setPrefWidth(150);
        graphDataCompact.setPrefHeight(85);
        
        HBox graphHeader = new HBox(6);
        graphHeader.setAlignment(Pos.CENTER_LEFT);
        Circle graphIcon = new Circle(5, Color.web("#95a5a6"));
        Label graphTitle = new Label("Topology data file");
        graphTitle.setFont(Font.font("Arial", FontWeight.BOLD, 11));
        graphTitle.setStyle("-fx-text-fill: #34495e;");
        graphHeader.getChildren().addAll(graphIcon, graphTitle);
        
        graphStatus = new Label("No file selected");
        graphStatus.setFont(Font.font("Arial", 9));
        graphStatus.setStyle("-fx-text-fill: #7f8c8d;");
        graphStatus.setWrapText(true);
        graphStatus.setMaxWidth(130);
        
        Button graphBrowse = new Button("Open");
        graphBrowse.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 10; -fx-font-weight: bold; -fx-border-radius: 3; -fx-background-radius: 3; -fx-cursor: hand;");
        graphBrowse.setPrefWidth(65);
        graphBrowse.setPrefHeight(24);
        graphBrowse.setOnAction(e -> loadGraphDataFile());
        
        graphDataCompact.getChildren().addAll(graphHeader, graphStatus, graphBrowse);
        
        filePanels.getChildren().addAll(flowDataCompact, graphDataCompact);
        section.getChildren().addAll(title, filePanels);
        
        return section;
    }
    
    private VBox createCompactTimeRangeSection() {
        VBox section = new VBox(8);
        
        // Title - 放大字体
        Label title = new Label("Time range");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        title.setStyle("-fx-text-fill: #2c3e50;");
        
        // Time inputs (compact)
        VBox timeInputs = new VBox(8);
        
        HBox startTimeBox = new HBox(8);
        startTimeBox.setAlignment(Pos.CENTER_LEFT);
        Label startLabel = new Label("Start:");
        startLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        startTimeField.setPrefWidth(180);
        startTimeField.setPrefHeight(32);
        startTimeField.setStyle("-fx-font-size: 13;");
        startTimeBox.getChildren().addAll(startLabel, startTimeField);
        
        HBox endTimeBox = new HBox(8);
        endTimeBox.setAlignment(Pos.CENTER_LEFT);
        Label endLabel = new Label("End:");
        endLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        endTimeField.setPrefWidth(180);
        endTimeField.setPrefHeight(32);
        endTimeField.setStyle("-fx-font-size: 13;");
        endTimeBox.getChildren().addAll(endLabel, endTimeField);
        
        timeInputs.getChildren().addAll(startTimeBox, endTimeBox);
        section.getChildren().addAll(title, timeInputs);
        
        return section;
    }
    
    private VBox createJumpAndTimelineSection() {
        VBox mainSection = new VBox(10);
        mainSection.setAlignment(Pos.TOP_LEFT);
        
        // Jump to time section (top)
        VBox jumpSection = createJumpToTimeSection();
        
        // Timeline section (bottom)
        VBox timelineSection = createCompactTimelineSection();
        
        mainSection.getChildren().addAll(jumpSection, timelineSection);
        
        return mainSection;
    }
    
    private VBox createJumpToTimeSection() {
        VBox section = new VBox(5);
        
        // Single horizontal row with all controls
        HBox singleRow = new HBox(5);
        singleRow.setAlignment(Pos.CENTER_LEFT);
        
        // Title
        Label title = new Label("Jump to:");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        title.setStyle("-fx-text-fill: #2c3e50;");
        
        // Date and time selectors labels and combos
        Label yearLabel = new Label("Y:");
        yearLabel.setFont(Font.font("Arial", 9));
        
        Label monthLabel = new Label("M:");
        monthLabel.setFont(Font.font("Arial", 9));
        
        Label dayLabel = new Label("D:");
        dayLabel.setFont(Font.font("Arial", 9));
        
        Label hourLabel = new Label("H:");
        hourLabel.setFont(Font.font("Arial", 9));
        
        Label minuteLabel = new Label("M:");
        minuteLabel.setFont(Font.font("Arial", 9));
        
        Label secondLabel = new Label("S:");
        secondLabel.setFont(Font.font("Arial", 9));
        
        // Add all elements in a single row
        singleRow.getChildren().addAll(
            title,
            yearLabel, jumpYearCombo,
            monthLabel, jumpMonthCombo,
            dayLabel, jumpDayCombo,
            hourLabel, jumpHourCombo,
            minuteLabel, jumpMinuteCombo,
            secondLabel, jumpSecondCombo,
            jumpButton
        );
        
        section.getChildren().add(singleRow);
        
        return section;
    }
    
    private VBox createCompactTimelineSection() {
        VBox section = new VBox(5);
        
        // Title
        Label title = new Label("Timeline");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        title.setStyle("-fx-text-fill: #2c3e50;");
        
        // Timeline slider (compact)
        timelineSlider.setPrefWidth(350);
        timelineSlider.setPrefHeight(20);
        
        // Playback controls (compact)
        HBox controls = new HBox(5);
        controls.setAlignment(Pos.CENTER);
        
        prevButton.setPrefSize(75, 30);
        playPauseButton.setPrefSize(35, 30);
        nextButton.setPrefSize(75, 30);
        stopButton.setPrefSize(35, 30);
        
        speedComboBox.setPrefWidth(60);
        speedComboBox.setPrefHeight(25);
        
        controls.getChildren().addAll(prevButton, playPauseButton, nextButton, stopButton, speedComboBox);
        
        // Progress info
        // Progress info - display current time below timeline
        HBox progressInfo = new HBox(10);
        progressInfo.setAlignment(Pos.CENTER_LEFT);
        currentTimeLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        progressInfo.getChildren().addAll(currentTimeLabel);
        
        section.getChildren().addAll(timelineSlider, controls, progressInfo);
        
        return section;
    }
    
    private void setupEventHandlers() {
        // File upload handlers
        setupFileUploadHandlers();
        
        // Time range handlers
        setupTimeRangeHandlers();
        
        // Timeline handlers
        setupTimelineHandlers();
        
        // Jump to time handlers
        setupJumpToTimeHandlers();
    }
    
    private void setupFileUploadHandlers() {
        // Flow data panel click handler
        flowDataPanel.setOnMouseClicked(e -> {
            if (flowDataFile == null) {
                loadFlowDataFile();
            }
        });
        
        // Graph data panel click handler
        graphDataPanel.setOnMouseClicked(e -> {
            if (graphDataFile == null) {
                loadGraphDataFile();
            }
        });
        
        // Browse button handlers
        Button graphDataBrowseButton = (Button) graphDataPanel.getChildren().get(2);
        graphDataBrowseButton.setOnAction(e -> loadGraphDataFile());
    }
    
    private void setupTimeRangeHandlers() {
        // Start time field change
        startTimeField.textProperty().addListener((obs, oldVal, newVal) -> {
            updateTimeRange();
        });
        
        // End time field change
        endTimeField.textProperty().addListener((obs, oldVal, newVal) -> {
            updateTimeRange();
        });
        
        // Now buttons removed - time range set from file data
    }
    
    private void setupTimelineHandlers() {
        // Timeline slider
        timelineSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            // Skip if this is a programmatic update (e.g., from jump or playback)
            if (isUpdatingSliderProgrammatically) {
                System.out.println("[PLAYBACK] Timeline slider value changed programmatically: " + newVal + " (skipping listener)");
                return;
            }
            
            System.out.println("[PLAYBACK] Timeline slider value changed by user: " + newVal);
            if (dataReader != null || playbackData != null) {
                updateFrameFromSlider();
            }
        });
        
        // Playback controls
        prevButton.setOnAction(e -> previousFrame());
        playPauseButton.setOnAction(e -> togglePlayback());
        nextButton.setOnAction(e -> nextFrame());
        stopButton.setOnAction(e -> stopPlayback());
        
        // Speed control
        speedComboBox.setOnAction(e -> {
            String speed = speedComboBox.getValue();
            System.out.println("[PLAYBACK] Speed changed to: " + speed);
            
            // Parse speed multiplier
            double speedMultiplier = 1.0;
            if (speed != null) {
                try {
                    speedMultiplier = Double.parseDouble(speed.replace("x", ""));
                } catch (NumberFormatException ex) {
                    System.err.println("[PLAYBACK] Invalid speed format: " + speed);
                    speedMultiplier = 1.0;
                }
            }
            
            // Update playback speed
            updatePlaybackSpeed(speedMultiplier);
        });
    }
    
    private void setupJumpToTimeHandlers() {
        // Jump button handler
        jumpButton.setOnAction(e -> jumpToSelectedTime());
    }
    
    // Helper method: Smoothly update progress from one value to another
    // 简化版本：直接跳转到目标进度，不使用动画
    private void smoothUpdateProgress(int fromPercent, int toPercent, long totalDurationMs) {
        // 直接更新到目标进度，避免阻塞线程
        javafx.application.Platform.runLater(() -> mainApp.updateProgress(toPercent));
    }
    
    private void loadFlowDataFile() {
        System.out.println("[PLAYBACK] Opening flow data file chooser...");
        File file = flowDataChooser.showOpenDialog(null);
        System.out.println("[PLAYBACK] File chooser returned: " + (file != null ? file.getAbsolutePath() : "null"));
        
        if (file != null) {
            mainApp.showLoading("Loading Flow Data...");
            
            // Use async loading to ensure loading screen is visible
            javafx.concurrent.Task<Void> loadTask = new javafx.concurrent.Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    // Smooth progress from 0 to 10
                    smoothUpdateProgress(0, 10, 500);
                    
                    flowDataFile = file;
                    updateFlowDataPanel();
                    
                    // Smooth progress from 10 to 20
                    smoothUpdateProgress(10, 20, 500);
            
                    // Check if file needs preprocessing (large single JSON)
                    if (file.length() > 100 * 1024 * 1024) { // > 100MB
                        try {
                            // Smooth progress from 20 to 30
                            smoothUpdateProgress(20, 30, 800);
                            
                            System.out.println("[PLAYBACK] Large file detected, preprocessing flow data...");
                            File outputDir = new File(file.getParentFile(), "preprocessed");
                            outputDir.mkdirs();
                            
                            // Smooth progress from 30 to 50 (preprocessing phase)
                            smoothUpdateProgress(30, 50, 1500);
                            
                            JsonPreprocessor.PreprocessResult result = JsonPreprocessor.preprocessFlowHistory(file, outputDir);
                            System.out.println("[PLAYBACK] Flow preprocessing complete: " + result.totalEntries + " entries, " + 
                                             (result.timeRangeMs / 1000) + "s duration");
                            
                            // Update file reference to preprocessed version
                            flowDataFile = result.ndjsonFile;
                            
                            // Smooth progress from 50 to 70 (building index)
                            smoothUpdateProgress(50, 70, 1500);
                            
                            // Build index for preprocessed file
                            File idx = new File(result.ndjsonFile.getParentFile(), result.ndjsonFile.getName() + ".idx");
                            flowIndex = NdjsonIndexUtil.buildIndex(result.ndjsonFile, idx);
                            System.out.println("[PLAYBACK] Flow index built: " + idx.getAbsolutePath() + ", entries=" + 
                                             (flowIndex == null ? 0 : flowIndex.entries.size()));
                            
                        } catch (Exception ex) {
                            System.err.println("[PLAYBACK] Failed to preprocess flow data: " + ex.getMessage());
                            ex.printStackTrace();
                            // Fallback to direct indexing (may fail for very large files)
                            try {
                                smoothUpdateProgress(30, 50, 1000);
                                File idx = new File(file.getParentFile(), file.getName() + ".flow.idx");
                                flowIndex = NdjsonIndexUtil.buildIndex(file, idx);
                                System.out.println("[PLAYBACK] Flow index built (fallback): " + idx.getAbsolutePath());
                            } catch (Exception ex2) {
                                System.err.println("[PLAYBACK] Fallback indexing also failed: " + ex2.getMessage());
                            }
                        }
                    } else {
                        // Small file, try single JSON indexing first, then fallback to NDJSON
                        try {
                            // Smooth progress from 20 to 40
                            smoothUpdateProgress(20, 40, 1000);
                            
                            File idx = new File(file.getParentFile(), file.getName() + ".flow.idx");
                            
                            // Try single JSON indexing first
                            try {
                                flowIndex = NdjsonIndexUtil.buildIndexForSingleJson(file, idx);
                                System.out.println("[PLAYBACK] Flow index built (single JSON): " + idx.getAbsolutePath() + ", entries=" + 
                                                 (flowIndex == null ? 0 : flowIndex.entries.size()));
                            } catch (Exception ex) {
                                System.out.println("[PLAYBACK] Single JSON indexing failed, trying NDJSON: " + ex.getMessage());
                                // Fallback to NDJSON indexing
                                flowIndex = NdjsonIndexUtil.buildIndex(file, idx);
                                System.out.println("[PLAYBACK] Flow index built (NDJSON): " + idx.getAbsolutePath() + ", entries=" + 
                                                 (flowIndex == null ? 0 : flowIndex.entries.size()));
                            }
                            
                            // Smooth progress from 40 to 70
                            smoothUpdateProgress(40, 70, 1500);
                        } catch (Exception ex) {
                            System.err.println("[PLAYBACK] Failed to build flow index: " + ex.getMessage());
                            ex.printStackTrace();
                        }
                    }
                    
                    // Smooth progress from 70 to 95
                    smoothUpdateProgress(70, 95, 1200);
                    
                    return null;
                }
                
                @Override
                protected void succeeded() {
                    // Smooth progress from 95 to 100
                    try {
                        smoothUpdateProgress(95, 100, 300);
                        Thread.sleep(200); // Show 100% for a moment
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    
                    // Update compact version status and style
                    if (flowStatus != null) {
                        flowStatus.setText(flowDataFile.getName());
                        flowStatus.setStyle("-fx-text-fill: #27ae60;");
                        // Update panel style to green
                        updateFlowDataCompactStyle(true);
                    }
                    System.out.println("[PLAYBACK] Flow data file loaded: " + flowDataFile.getName());
                    
                    // Initialize data reader if both files are loaded
                    initializeDataReader();
                    
                    mainApp.hideLoading();
                }
                
                @Override
                protected void failed() {
                    mainApp.hideLoading();
                    System.err.println("[PLAYBACK] Failed to load flow data file");
                }
            };
            
            new Thread(loadTask).start();
        }
    }
    
    private void loadGraphDataFile() {
        System.out.println("[PLAYBACK] Opening topology data file chooser...");
        File file = graphDataChooser.showOpenDialog(null);
        System.out.println("[PLAYBACK] File chooser returned: " + (file != null ? file.getAbsolutePath() : "null"));
        if (file != null) {
            mainApp.showLoading("Loading Topology Data...");
            
            // Use async loading to ensure loading screen is visible
            javafx.concurrent.Task<Void> loadTask = new javafx.concurrent.Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    // Smooth progress from 0 to 10
                    smoothUpdateProgress(0, 10, 500);
                    
                    graphDataFile = file;
                    updateGraphDataPanel();
                    
                    // Smooth progress from 10 to 20
                    smoothUpdateProgress(10, 20, 500);
            
                    // Check if file needs preprocessing (large single JSON)
                    if (file.length() > 100 * 1024 * 1024) { // > 100MB
                        try {
                            // Smooth progress from 20 to 30
                            smoothUpdateProgress(20, 30, 800);
                            
                            System.out.println("[PLAYBACK] Large file detected, preprocessing topology data...");
                            File outputDir = new File(file.getParentFile(), "preprocessed");
                            outputDir.mkdirs();
                            
                            // Smooth progress from 30 to 50 (preprocessing phase)
                            smoothUpdateProgress(30, 50, 1500);
                            
                            JsonPreprocessor.PreprocessResult result = JsonPreprocessor.preprocessTopologyHistory(file, outputDir);
                            System.out.println("[PLAYBACK] Topology preprocessing complete: " + result.totalEntries + " entries, " + 
                                             (result.timeRangeMs / 1000) + "s duration");
                            
                            // Update file reference to preprocessed version
                            graphDataFile = result.ndjsonFile;
                            
                            // Smooth progress from 50 to 70 (building index)
                            smoothUpdateProgress(50, 70, 1500);
                            
                            // Build index for preprocessed file
                            File idx = new File(result.ndjsonFile.getParentFile(), result.ndjsonFile.getName() + ".idx");
                            topoIndex = NdjsonIndexUtil.buildIndex(result.ndjsonFile, idx);
                            System.out.println("[PLAYBACK] Topology index built: " + idx.getAbsolutePath() + ", entries=" + 
                                             (topoIndex == null ? 0 : topoIndex.entries.size()));
                            // Update time range immediately from topology index
                            updateTimeRangeFromTopoIndex();
                            
                            // Initialize data reader immediately after topoIndex is set
                            initializeDataReader();
                            
                        } catch (Exception ex) {
                            System.err.println("[PLAYBACK] Failed to preprocess topology data: " + ex.getMessage());
                            ex.printStackTrace();
                            // Fallback to direct indexing (may fail for very large files)
                            try {
                                smoothUpdateProgress(30, 50, 1000);
                                File idx = new File(file.getParentFile(), file.getName() + ".topo.idx");
                                topoIndex = NdjsonIndexUtil.buildIndex(file, idx);
                                System.out.println("[PLAYBACK] Topology index built (fallback): " + idx.getAbsolutePath());
                            } catch (Exception ex2) {
                                System.err.println("[PLAYBACK] Fallback indexing also failed: " + ex2.getMessage());
                            }
                        }
                    } else {
                        // Small file, try single JSON indexing first, then fallback to NDJSON
                        try {
                            // Smooth progress from 20 to 40
                            smoothUpdateProgress(20, 40, 1000);
                            
                            File idx = new File(file.getParentFile(), file.getName() + ".topo.idx");
                            
                            // Try single JSON indexing first
                            try {
                                topoIndex = NdjsonIndexUtil.buildIndexForSingleJson(file, idx);
                                System.out.println("[PLAYBACK] Topology index built (single JSON): " + idx.getAbsolutePath() + ", entries=" + 
                                                 (topoIndex == null ? 0 : topoIndex.entries.size()));
                            } catch (Exception ex) {
                                System.out.println("[PLAYBACK] Single JSON indexing failed, trying NDJSON: " + ex.getMessage());
                                // Fallback to NDJSON indexing
                                topoIndex = NdjsonIndexUtil.buildIndex(file, idx);
                                System.out.println("[PLAYBACK] Topology index built (NDJSON): " + idx.getAbsolutePath() + ", entries=" + 
                                                 (topoIndex == null ? 0 : topoIndex.entries.size()));
                            }
                            
                            // Update time range immediately from topology index
                            updateTimeRangeFromTopoIndex();
                            
                            // Initialize data reader immediately after topoIndex is set
                            initializeDataReader();
                            
                            // Smooth progress from 40 to 70
                            smoothUpdateProgress(40, 70, 1500);
                        } catch (Exception ex) {
                            System.err.println("[PLAYBACK] Failed to build topology index: " + ex.getMessage());
                            ex.printStackTrace();
                        }
                    }
                    
                    // Smooth progress from 70 to 95
                    smoothUpdateProgress(70, 95, 1200);
                    
                    return null;
                }
                
                @Override
                protected void succeeded() {
                    // Smooth progress from 95 to 100
                    try {
                        smoothUpdateProgress(95, 100, 300);
                        Thread.sleep(200); // Show 100% for a moment
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    
                    // Update compact version status
                    if (graphStatus != null) {
                        graphStatus.setText(graphDataFile.getName());
                        graphStatus.setStyle("-fx-text-fill: #27ae60;");
                    }
                    System.out.println("[PLAYBACK] Topology data file loaded: " + graphDataFile.getName());
                    
                    // Initialize data reader if both files are loaded
                    initializeDataReader();
                    
                    mainApp.hideLoading();
                }
                
                @Override
                protected void failed() {
                    mainApp.hideLoading();
                    System.err.println("[PLAYBACK] Failed to load topology data file");
                }
            };
            
            new Thread(loadTask).start();
        }
    }
    
    private void initializeDataReader() {
        System.out.println("[PLAYBACK] initializeDataReader called");
        System.out.println("[PLAYBACK] flowIndex: " + (flowIndex != null ? "present" : "null"));
        System.out.println("[PLAYBACK] topoIndex: " + (topoIndex != null ? "present" : "null"));
        System.out.println("[PLAYBACK] flowDataFile: " + (flowDataFile != null ? flowDataFile.getName() : "null"));
        System.out.println("[PLAYBACK] graphDataFile: " + (graphDataFile != null ? graphDataFile.getName() : "null"));
        
        if (flowIndex != null && topoIndex != null && flowDataFile != null && graphDataFile != null) {
            try {
                System.out.println("[PLAYBACK] Creating PlaybackDataReader...");
                dataReader = new PlaybackDataReader(flowIndex, topoIndex, flowDataFile, graphDataFile);
                timeRange = dataReader.getTimeRange();
                
                System.out.println("[PLAYBACK] Data reader initialized:");
                System.out.println("  Time range: " + timeRange.startTime + " to " + timeRange.endTime);
                System.out.println("  Duration: " + (timeRange.duration / 1000) + " seconds");
                
                // Update time range fields
                updateTimeRangeFromData();
                
                // Enable playback controls
                enablePlaybackControls(true);
                
                System.out.println("[PLAYBACK] Playback controls enabled");
                
            } catch (Exception ex) {
                System.err.println("[PLAYBACK] Failed to initialize data reader: " + ex.getMessage());
                ex.printStackTrace();
            }
        } else {
            System.out.println("[PLAYBACK] Cannot initialize data reader - missing required data");
        }
    }

    private void updateTimeRangeFromTopoIndex() {
        try {
            if (topoIndex != null && topoIndex.entries != null && !topoIndex.entries.isEmpty()) {
                long start = topoIndex.entries.get(0).timestamp;
                long end = topoIndex.entries.get(topoIndex.entries.size() - 1).timestamp;
                if (startTimeField != null) {
                    startTimeField.setText(formatTimestamp(start));
                    startTimeField.setEditable(false);
                    startTimeField.setStyle("-fx-background-color: #f0f0f0; -fx-text-fill: #000;");
                }
                if (endTimeField != null) {
                    endTimeField.setText(formatTimestamp(end));
                    endTimeField.setEditable(false);
                    endTimeField.setStyle("-fx-background-color: #f0f0f0; -fx-text-fill: #000;");
                }
                if (timelineSlider != null) {
                    long durationSeconds = Math.max(0, (end - start) / 1000);
                    timelineSlider.setMin(0);
                    timelineSlider.setMax(durationSeconds);
                    isUpdatingSliderProgrammatically = true;
                    try {
                        timelineSlider.setValue(0);
                    } finally {
                        isUpdatingSliderProgrammatically = false;
                    }
                }
                System.out.println("[PLAYBACK] Time range (topology index): start=" + start + ", end=" + end);
            }
        } catch (Exception ignore) {}
    }
    
    private void updateTimeRangeFromData() {
        if (timeRange != null) {
            // Set time fields to read-only with file data
            if (startTimeField != null) {
                startTimeField.setText(formatTimestamp(timeRange.startTime));
                startTimeField.setEditable(false); // Make read-only
                startTimeField.setStyle("-fx-background-color: #f0f0f0; -fx-text-fill: #000;");
            }
            if (endTimeField != null) {
                endTimeField.setText(formatTimestamp(timeRange.endTime));
                endTimeField.setEditable(false); // Make read-only
                endTimeField.setStyle("-fx-background-color: #f0f0f0; -fx-text-fill: #000;");
            }
            
            // Update timeline slider range to seconds based on topology-only time range
            if (timelineSlider != null) {
                long durationSeconds = Math.max(0, timeRange.duration / 1000);
                timelineSlider.setMin(0);
                timelineSlider.setMax(durationSeconds);
                isUpdatingSliderProgrammatically = true;
                try {
                    timelineSlider.setValue(0);
                } finally {
                    isUpdatingSliderProgrammatically = false;
                }
                
                // Update progress label to show time range info
            }
            
            // Initialize jump to time components with start time
            initializeJumpToTimeValues(timeRange.startTime);
            
            System.out.println("[PLAYBACK] Time range set from file data: " + timeRange.startTime + " to " + timeRange.endTime);
            System.out.println("[PLAYBACK] Duration: " + (timeRange.duration / 1000) + " seconds");
        } else {
            // No data loaded, show placeholder
            if (startTimeField != null) {
                startTimeField.setText("No data loaded");
                startTimeField.setEditable(false);
                startTimeField.setStyle("-fx-background-color: #f0f0f0; -fx-text-fill: #000;");
            }
            if (endTimeField != null) {
                endTimeField.setText("No data loaded");
                endTimeField.setEditable(false);
                endTimeField.setStyle("-fx-background-color: #f0f0f0; -fx-text-fill: #000;");
            }
        }
    }
    
    private void enablePlaybackControls(boolean enabled) {
        if (playPauseButton != null) {
            playPauseButton.setDisable(!enabled);
        }
        if (prevButton != null) {
            prevButton.setDisable(!enabled);
        }
        if (nextButton != null) {
            nextButton.setDisable(!enabled);
        }
        if (stopButton != null) {
            stopButton.setDisable(!enabled);
        }
        if (timelineSlider != null) {
            timelineSlider.setDisable(!enabled);
        }
        
        // Enable/disable jump to time controls
        if (jumpYearCombo != null) {
            jumpYearCombo.setDisable(!enabled);
        }
        if (jumpMonthCombo != null) {
            jumpMonthCombo.setDisable(!enabled);
        }
        if (jumpDayCombo != null) {
            jumpDayCombo.setDisable(!enabled);
        }
        if (jumpHourCombo != null) {
            jumpHourCombo.setDisable(!enabled);
        }
        if (jumpMinuteCombo != null) {
            jumpMinuteCombo.setDisable(!enabled);
        }
        if (jumpSecondCombo != null) {
            jumpSecondCombo.setDisable(!enabled);
        }
        if (jumpButton != null) {
            jumpButton.setDisable(!enabled);
        }
    }
    
    private void updatePlaybackSpeed(double speedMultiplier) {
        this.playbackSpeed = speedMultiplier;
        System.out.println("[PLAYBACK] Playback speed updated to: " + speedMultiplier + "x");
    }

    private void updateFlowDataCompactStyle(boolean hasFile) {
        // Update the compact Flow Data panel style without recreating the entire layout
        if (flowStatus != null) {
            if (hasFile) {
                flowStatus.setStyle("-fx-text-fill: #27ae60;");
            } else {
                flowStatus.setStyle("-fx-text-fill: #000;");
            }
        }
    }
    
    private void updateFlowDataPanel() {
        if (flowDataFile != null) {
            flowDataPanel.setStyle("-fx-border-color: #27ae60; -fx-border-style: solid; -fx-border-width: 2; -fx-background-color: #f8fff8;");
            
            // Update status
            flowDataStatus.setText(flowDataFile.getName() + " (" + getFileSize(flowDataFile) + ")");
            flowDataStatus.setStyle("-fx-text-fill: #27ae60;");
            
            // Show remove button
            Button removeButton = (Button) flowDataPanel.getChildren().get(2);
            removeButton.setVisible(true);
            removeButton.setOnAction(e -> {
                flowDataFile = null;
                flowDataPanel.setStyle("-fx-border-color: #27ae60; -fx-border-style: dashed; -fx-border-width: 2; -fx-background-color: #f8fff8;");
                flowDataStatus.setText("No file uploaded");
                flowDataStatus.setStyle("-fx-text-fill: #7f8c8d;");
                removeButton.setVisible(false);
            });
        }
    }
    
    private void updateGraphDataPanel() {
        if (graphDataFile != null) {
            graphDataPanel.setStyle("-fx-border-color: #27ae60; -fx-border-style: solid; -fx-border-width: 2; -fx-background-color: #f8fff8;");
            
            // Update status
            graphDataStatus.setText(graphDataFile.getName() + " (" + getFileSize(graphDataFile) + ")");
            graphDataStatus.setStyle("-fx-text-fill: #27ae60;");
            
            // Hide browse button and add remove button
            graphDataPanel.getChildren().clear();
            
            HBox header = new HBox(10);
            header.setAlignment(Pos.CENTER_LEFT);
            Circle icon = new Circle(8, Color.GREEN);
            Label title = new Label("Topology Data");
            title.setFont(Font.font("Arial", FontWeight.BOLD, 14));
            header.getChildren().addAll(icon, title);
            
            Button removeButton = new Button("Remove");
            removeButton.setStyle("-fx-text-fill: #e74c3c; -fx-background-color: transparent; -fx-underline: true;");
            removeButton.setOnAction(e -> {
                graphDataFile = null;
                initializeFileUploadPanels();
                setupFileUploadHandlers();
            });
            
            graphDataPanel.getChildren().addAll(header, graphDataStatus, removeButton);
        }
    }
    
    private String getFileSize(File file) {
        long size = file.length();
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }
    
    private void updateTimeRange() {
        String startTime = startTimeField.getText();
        String endTime = endTimeField.getText();
        
        if (!startTime.isEmpty() && !endTime.isEmpty()) {
            selectedRangeLabel.setText("Selected Range:\nStart: " + startTime + "\n End: " + endTime);
            durationLabel.setText("Duration: 4 seconds");
            durationLabel.setStyle("-fx-text-fill: #27ae60;");
            
            // Enable timeline controls
            timelineSlider.setDisable(false);
            prevButton.setDisable(false);
            playPauseButton.setDisable(false);
            nextButton.setDisable(false);
            stopButton.setDisable(false);
        }
    }
    
    private void previousFrame() {
        if (timelineSlider != null) {
            // 如果正在播放，先暂停
            boolean wasPlaying = isPlaying;
            if (isPlaying) {
                isPlaying = false;
                playPauseButton.setText("▶");
                playPauseButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 14;");
                System.out.println("[PLAYBACK] Auto-paused for backward jump");
            }
            
            // 往前跳5秒
            double currentValue = timelineSlider.getValue();
            double newValue = Math.max(timelineSlider.getMin(), currentValue - 5);
            
            // 设置标志，表示这是程序化更新
            isUpdatingSliderProgrammatically = true;
            try {
                timelineSlider.setValue(newValue);
            } finally {
                isUpdatingSliderProgrammatically = false;
            }
            
            System.out.println("[PLAYBACK] Previous: jumped from " + currentValue + "s to " + newValue + "s (backward 5 seconds)");
            
            // 更新数据
            if (dataReader != null && timeRange != null) {
                long targetTime = timeRange.startTime + (long)(newValue * 1000L);
                loadDataAtTime(targetTime);
            } else if (playbackData != null) {
                int frameIndex = (int) newValue;
                if (frameIndex >= 0 && frameIndex < playbackData.playback.size()) {
                    currentFrameIndex = frameIndex;
                    loadFrame(frameIndex);
                }
            }
            
            // 通知状态变化（如果暂停了）
            if (wasPlaying) {
                notifyPlaybackStateChanged();
            }
        }
    }
    
    private void nextFrame() {
        if (timelineSlider != null) {
            // 如果正在播放，先暂停
            boolean wasPlaying = isPlaying;
            if (isPlaying) {
                isPlaying = false;
                playPauseButton.setText("▶");
                playPauseButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 14;");
                System.out.println("[PLAYBACK] Auto-paused for forward jump");
            }
            
            // 往後跳5秒
            double currentValue = timelineSlider.getValue();
            double newValue = Math.min(timelineSlider.getMax(), currentValue + 5);
            
            // 设置标志，表示这是程序化更新
            isUpdatingSliderProgrammatically = true;
            try {
                timelineSlider.setValue(newValue);
            } finally {
                isUpdatingSliderProgrammatically = false;
            }
            
            System.out.println("[PLAYBACK] Next: jumped from " + currentValue + "s to " + newValue + "s (forward 5 seconds)");
            
            // 更新数据
            if (dataReader != null && timeRange != null) {
                long targetTime = timeRange.startTime + (long)(newValue * 1000L);
                loadDataAtTime(targetTime);
            } else if (playbackData != null) {
                int frameIndex = (int) newValue;
                if (frameIndex >= 0 && frameIndex < playbackData.playback.size()) {
                    currentFrameIndex = frameIndex;
                    loadFrame(frameIndex);
                }
            }
            
            // 通知状态变化（如果暂停了）
            if (wasPlaying) {
                notifyPlaybackStateChanged();
            }
        }
    }
    
    private void togglePlayback() {
        System.out.println("[PLAYBACK] togglePlayback called");
        System.out.println("[PLAYBACK] dataReader: " + (dataReader != null ? "present" : "null"));
        System.out.println("[PLAYBACK] playbackData: " + (playbackData != null ? "present" : "null"));
        
        if (dataReader == null && playbackData == null) {
            System.out.println("[PLAYBACK] No data available for playback");
            return;
        }
        
        isPlaying = !isPlaying;
        
        if (isPlaying) {
            System.out.println("[PLAYBACK] Starting playback");
            playPauseButton.setText("⏸");
            playPauseButton.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-font-size: 14;");
            // 播放时，更新时间标签移除重播提示
            updateCurrentTimeLabel();
            startPlayback();
        } else {
            System.out.println("[PLAYBACK] Stopping playback");
            playPauseButton.setText("▶");
            playPauseButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 14;");
            
            // 暂停时，更新时间标签显示重播提示
            updateCurrentTimeLabel();
        }
        
        // 通知 SideBar 更新按鈕樣式
        notifyPlaybackStateChanged();
    }
    
    private void stopPlayback() {
        isPlaying = false;
        playPauseButton.setText("▶");
        playPauseButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 14;");
        
        // Reset to first frame
        currentFrameIndex = 0;
        isUpdatingSliderProgrammatically = true;
        try {
            timelineSlider.setValue(0);
        } finally {
            isUpdatingSliderProgrammatically = false;
        }
        loadFrame(0);
        
        // 停止后更新时间标签显示重播提示
        updateCurrentTimeLabel();
        
        // 通知 SideBar 更新按鈕樣式
        notifyPlaybackStateChanged();
    }

    // Public API for external callers (e.g., switching back to real-time)
    public void stopPlaybackExternal() {
        // Ensure playback loops exit quickly
        isPlaying = false;
        // Update UI state if controls are initialized
        if (playPauseButton != null) {
            playPauseButton.setText("▶");
            playPauseButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 14;");
        }
        // Inform sidebar to refresh button style
        notifyPlaybackStateChanged();
    }
    
    private void startPlayback() {
        System.out.println("[PLAYBACK] startPlayback called");
        
        if (dataReader != null && timeRange != null) {
            System.out.println("[PLAYBACK] Starting dataReader-based playback");
            startDataReaderPlayback();
        } else if (playbackData != null && playbackData.playback != null) {
            System.out.println("[PLAYBACK] Starting playbackData-based playback");
            startPlaybackDataPlayback();
        } else {
            System.out.println("[PLAYBACK] No valid data for playback");
        }
    }
    
    private void startDataReaderPlayback() {
        new Thread(() -> {
            if (dataReader == null || timeRange == null) {
                System.out.println("[PLAYBACK] Cannot start playback - missing dataReader or timeRange");
                return;
            }
            
            // Calculate actual time step based on topology data intervals
            long timeStep = calculateTimeStep();
            System.out.println("[PLAYBACK] Starting playback with time step: " + timeStep + "ms");
            
            // 从当前 slider 位置开始播放，而不是总是从开始位置
            double currentSliderValue = timelineSlider.getValue();
            long currentTime = timeRange.startTime + (long)(currentSliderValue * 1000L);
            long endTime = timeRange.endTime;
            
            System.out.println("[PLAYBACK] Starting from current position: " + currentTime + "ms (slider: " + currentSliderValue + "s)");
            
            while (isPlaying && currentTime < endTime) {
                try {
                    // 根據播放速度調整延遲時間
                    long delayMs = (long)(1000 / playbackSpeed);
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                
                if (isPlaying) {
                    currentTime += timeStep;
                    if (currentTime > endTime) {
                        currentTime = endTime;
                    }
                    
                    final long finalTime = currentTime;
                    javafx.application.Platform.runLater(() -> {
                        loadDataAtTime(finalTime);
                    });
                }
            }
            
            // Auto-stop when reaching the end
            if (currentTime >= endTime) {
                javafx.application.Platform.runLater(() -> {
                    isPlaying = false;
                    playPauseButton.setText("▶");
                    playPauseButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 14;");
                });
            }
        }).start();
    }
    
    private long calculateTimeStep() {
        if (dataReader == null || dataReader.getTopoIndex() == null || dataReader.getTopoIndex().entries.size() < 2) {
            return 1000; // Default 1 second if not enough data
        }
        
        // Calculate average time step from topology data
        long totalInterval = 0;
        int intervalCount = 0;
        
        for (int i = 1; i < dataReader.getTopoIndex().entries.size(); i++) {
            long interval = dataReader.getTopoIndex().entries.get(i).timestamp - dataReader.getTopoIndex().entries.get(i-1).timestamp;
            if (interval > 0) {
                totalInterval += interval;
                intervalCount++;
            }
        }
        
        if (intervalCount > 0) {
            long avgInterval = totalInterval / intervalCount;
            System.out.println("[PLAYBACK] Calculated average time step: " + avgInterval + "ms from " + intervalCount + " intervals");
            return avgInterval;
        }
        
        return 1000; // Default fallback
    }
    
    private void startPlaybackDataPlayback() {
        new Thread(() -> {
            // 从当前 slider 位置开始播放
            currentFrameIndex = (int) timelineSlider.getValue();
            System.out.println("[PLAYBACK] Starting playback from frame: " + currentFrameIndex);
            
            while (isPlaying && currentFrameIndex < playbackData.playback.size() - 1) {
                try {
                    // 根據播放速度調整延遲時間
                    long delayMs = (long)(100 / playbackSpeed);
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                
                if (isPlaying) {
                    currentFrameIndex++;
                    javafx.application.Platform.runLater(() -> {
                        isUpdatingSliderProgrammatically = true;
                        try {
                            timelineSlider.setValue(currentFrameIndex);
                        } finally {
                            isUpdatingSliderProgrammatically = false;
                        }
                        loadFrame(currentFrameIndex);
                    });
                }
            }
            
            // Auto-stop when reaching the end
            if (currentFrameIndex >= playbackData.playback.size() - 1) {
                javafx.application.Platform.runLater(() -> {
                    isPlaying = false;
                    playPauseButton.setText("▶");
                    playPauseButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 14;");
                });
            }
        }).start();
    }
    
    private void updateFrameFromSlider() {
        System.out.println("[PLAYBACK] updateFrameFromSlider called");
        System.out.println("[PLAYBACK] dataReader: " + (dataReader != null ? "present" : "null"));
        System.out.println("[PLAYBACK] timeRange: " + (timeRange != null ? "present" : "null"));
        System.out.println("[PLAYBACK] playbackData: " + (playbackData != null ? "present" : "null"));
        
        if (dataReader != null && timeRange != null) {
            double sliderSeconds = timelineSlider.getValue();
            long targetTime = timeRange.startTime + (long)(sliderSeconds * 1000L);
            
            System.out.println("[PLAYBACK] Slider seconds: " + sliderSeconds + ", targetTime(ms): " + targetTime +
                " (start: " + timeRange.startTime + ", durationMs: " + timeRange.duration + ")");
            
            // 先更新时间标签显示 slider 的时间（用户拖拽的时间）
            updateTimeLabels(targetTime);
            
            // 然后加载数据（数据可能来自稍微不同的时间戳，但标签保持显示 slider 时间）
            loadDataAtTime(targetTime, false); // false 表示不再更新时间标签
        } else if (playbackData != null) {
            System.out.println("[PLAYBACK] Using fallback logic with playbackData");
            // Fallback to old logic for compatibility
            int frameIndex = (int) timelineSlider.getValue();
            if (frameIndex != currentFrameIndex) {
                currentFrameIndex = frameIndex;
                loadFrame(frameIndex);
            }
        } else {
            System.out.println("[PLAYBACK] No data reader or playback data available");
        }
    }
    
    private void loadDataAtTime(long timestamp) {
        loadDataAtTime(timestamp, true); // 默认更新时间标签
    }
    
    private void loadDataAtTime(long timestamp, boolean updateTimeLabel) {
        System.out.println("[PLAYBACK] loadDataAtTime called with timestamp: " + timestamp + ", updateTimeLabel: " + updateTimeLabel);
        
        if (dataReader == null) {
            System.out.println("[PLAYBACK] No data reader available, showing empty topology");
            // No data reader available, show empty topology
            updateTopologyDirectly(null, null);
            return;
        }
        
        try {
            // 智能對齊策略：當topo timestamp第一次超過flow第一個timestamp時開始對齊
            AlignedData alignedData = getAlignedDataAtTime(timestamp);
            
            System.out.println("[PLAYBACK] Using smart alignment strategy");
            System.out.println("[PLAYBACK] Aligned topo index: " + alignedData.topoIndex + 
                             ", flow index: " + alignedData.flowIndex);

            // Get aligned topology and flow data
            PlaybackDataReader.TopologySnapshot topoSnapshot = alignedData.topoSnapshot;
            List<PlaybackDataReader.FlowSnapshot> flowSnapshots = alignedData.flowSnapshots;
            
            System.out.println("[PLAYBACK] Updating topology with aligned data");
            // Update topology directly with our data (null values will show empty)
            updateTopologyDirectly(topoSnapshot, flowSnapshots);
            
            // Update UI labels only if requested
            if (updateTimeLabel) {
                updateTimeLabels(timestamp);
            }
            
            int nodeCount = topoSnapshot != null && topoSnapshot.nodes != null ? topoSnapshot.nodes.size() : 0;
            int linkCount = topoSnapshot != null && topoSnapshot.links != null ? topoSnapshot.links.size() : 0;
            int flowCount = 0;
            if (flowSnapshots != null) {
                for (PlaybackDataReader.FlowSnapshot snapshot : flowSnapshots) {
                    if (snapshot.flows != null) {
                        flowCount += snapshot.flows.size();
                    }
                }
            }
            
            System.out.println("[PLAYBACK] Loaded aligned data at " + timestamp + "ms: " + 
                             nodeCount + " nodes, " + linkCount + " links, " + flowCount + " flows");
            
        } catch (Exception ex) {
            System.err.println("[PLAYBACK] Failed to load data at time " + timestamp + ": " + ex.getMessage());
            ex.printStackTrace();
            // Show empty topology on error
            updateTopologyDirectly(null, null);
        }
    }
    
    // 智能對齊策略：當topo timestamp第一次超過flow第一個timestamp時開始對齊
    private AlignedData getAlignedDataAtTime(long timestamp) {
        System.out.println("[PLAYBACK] getAlignedDataAtTime called with timestamp: " + timestamp);
        
        // 獲取索引信息（不載入實際數據）
        List<NdjsonIndexUtil.IndexEntry> topoIndexEntries = dataReader.getAllTopologyIndexEntries();
        List<NdjsonIndexUtil.IndexEntry> flowIndexEntries = dataReader.getAllFlowIndexEntries();
        
        System.out.println("[PLAYBACK] Index entries - topo: " + topoIndexEntries.size() + ", flow: " + flowIndexEntries.size());
        
        if (topoIndexEntries.isEmpty()) {
            System.out.println("[PLAYBACK] No topology data available");
            return new AlignedData(null, null, -1, -1);
        }
        
        if (flowIndexEntries.isEmpty()) {
            System.out.println("[PLAYBACK] No flow data available");
            return new AlignedData(null, null, -1, -1);
        }
        
        // 找到flow數據的第一個timestamp
        long firstFlowTimestamp = Long.MAX_VALUE;
        for (NdjsonIndexUtil.IndexEntry entry : flowIndexEntries) {
            if (entry.timestamp < firstFlowTimestamp) {
                firstFlowTimestamp = entry.timestamp;
            }
        }
        
        System.out.println("[PLAYBACK] First flow timestamp: " + firstFlowTimestamp);
        
        // 找到topo timestamp第一次超過flow第一個timestamp的索引
        int alignmentStartIndex = -1;
        for (int i = 0; i < topoIndexEntries.size(); i++) {
            if (topoIndexEntries.get(i).timestamp >= firstFlowTimestamp) {
                alignmentStartIndex = i;
                break;
            }
        }
        
        if (alignmentStartIndex == -1) {
            System.out.println("[PLAYBACK] No topology data after first flow timestamp, using last topology");
            alignmentStartIndex = topoIndexEntries.size() - 1;
        }
        
        System.out.println("[PLAYBACK] Alignment starts at topology index: " + alignmentStartIndex);
        
        // 計算目標時間對應的索引
        int targetTopoIndex = alignmentStartIndex;
        int targetFlowIndex = 0;
        
        // 找到最接近目標時間的topology索引
        for (int i = alignmentStartIndex; i < topoIndexEntries.size(); i++) {
            if (topoIndexEntries.get(i).timestamp <= timestamp) {
                targetTopoIndex = i;
            } else {
                break;
            }
        }
        
        // 計算對應的flow索引（從對齊點開始的相對位置）
        int relativePosition = targetTopoIndex - alignmentStartIndex;
        targetFlowIndex = Math.min(relativePosition, flowIndexEntries.size() - 1);
        
        System.out.println("[PLAYBACK] Target indices - topo: " + targetTopoIndex + ", flow: " + targetFlowIndex);
        
        // 現在才載入實際的數據
        PlaybackDataReader.TopologySnapshot topoSnapshot = null;
        List<PlaybackDataReader.FlowSnapshot> flowSnapshots = new ArrayList<>();
        
        try {
            // 載入對應的topology數據
            if (targetTopoIndex < topoIndexEntries.size()) {
                long topoTimestamp = topoIndexEntries.get(targetTopoIndex).timestamp;
                topoSnapshot = dataReader.getTopologyAt(topoTimestamp);
            }
            
            // 載入對應的flow數據
            if (targetFlowIndex < flowIndexEntries.size()) {
                long flowTimestamp = flowIndexEntries.get(targetFlowIndex).timestamp;
                System.out.println("[PLAYBACK] Loading flow data at timestamp: " + flowTimestamp);
                List<PlaybackDataReader.FlowSnapshot> flows = dataReader.getFlowsAt(flowTimestamp, 1000);
                if (flows != null) {
                    System.out.println("[PLAYBACK] Loaded " + flows.size() + " flow snapshots");
                    for (PlaybackDataReader.FlowSnapshot snapshot : flows) {
                        if (snapshot.flows != null) {
                            System.out.println("[PLAYBACK] Flow snapshot has " + snapshot.flows.size() + " flows");
                        } else {
                            System.out.println("[PLAYBACK] Flow snapshot has null flows");
                        }
                    }
                    flowSnapshots.addAll(flows);
                } else {
                    System.out.println("[PLAYBACK] No flow data returned for timestamp: " + flowTimestamp);
                }
            } else {
                System.out.println("[PLAYBACK] targetFlowIndex " + targetFlowIndex + " >= flowIndexEntries.size() " + flowIndexEntries.size());
            }
        } catch (Exception e) {
            System.err.println("[PLAYBACK] Error loading aligned data: " + e.getMessage());
        }
        
        System.out.println("[PLAYBACK] Aligned data - topo timestamp: " + 
                         (topoSnapshot != null ? topoSnapshot.timestamp : "null") + 
                         ", flow timestamp: " + 
                         (flowSnapshots.isEmpty() ? "null" : flowSnapshots.get(0).timestamp));
        
        return new AlignedData(topoSnapshot, flowSnapshots, targetTopoIndex, targetFlowIndex);
    }
    
    private PlaybackData.PlaybackFrame convertToPlaybackFrame(long timestamp, 
                                                             PlaybackDataReader.TopologySnapshot topoSnapshot,
                                                             List<PlaybackDataReader.FlowSnapshot> flowSnapshots) {
        PlaybackData.PlaybackFrame frame = new PlaybackData.PlaybackFrame();
        frame.time = timestamp;
        
        // Convert topology nodes
        if (topoSnapshot != null && topoSnapshot.nodes != null) {
            frame.nodes = new ArrayList<>();
            for (Node node : topoSnapshot.nodes) {
                PlaybackData.NodeData nodeData = new PlaybackData.NodeData();
                nodeData.brand_name = node.name != null ? node.name : node.ip;
                nodeData.number = topoSnapshot.nodes.indexOf(node);
                frame.nodes.add(nodeData);
            }
        }
        
        // Convert topology links
        if (topoSnapshot != null && topoSnapshot.links != null) {
            frame.edges = new ArrayList<>();
            for (Link link : topoSnapshot.links) {
                PlaybackData.EdgeData edgeData = new PlaybackData.EdgeData();
                edgeData.number = topoSnapshot.links.indexOf(link);
                frame.edges.add(edgeData);
            }
        }
        
        // Convert flows
        if (flowSnapshots != null && !flowSnapshots.isEmpty()) {
            frame.flow = new ArrayList<>();
            for (PlaybackDataReader.FlowSnapshot flowSnapshot : flowSnapshots) {
                if (flowSnapshot.flows != null) {
                    for (Flow flow : flowSnapshot.flows) {
                        PlaybackData.FlowData flowData = new PlaybackData.FlowData();
                        flowData.number = frame.flow.size();
                        frame.flow.add(flowData);
                    }
                }
            }
        }
        
        return frame;
    }
    
    private void updateTopologyDirectly(PlaybackDataReader.TopologySnapshot topoSnapshot, 
                                       List<PlaybackDataReader.FlowSnapshot> flowSnapshots) {
        System.out.println("[PLAYBACK] updateTopologyDirectly called");
        System.out.println("[PLAYBACK] topoSnapshot: " + (topoSnapshot != null ? "present" : "null"));
        System.out.println("[PLAYBACK] flowSnapshots: " + (flowSnapshots != null ? flowSnapshots.size() + " snapshots" : "null"));
        
        if (mainApp == null) {
            System.out.println("[PLAYBACK] mainApp is null, cannot update topology");
            return;
        }
        
        // Convert topology data
        List<Node> nodes = new ArrayList<>();
        List<Link> links = new ArrayList<>();
        List<Flow> flows = new ArrayList<>();
        
        if (topoSnapshot != null) {
            System.out.println("[PLAYBACK] Processing topology snapshot");
            if (topoSnapshot.nodes != null) {
                nodes.addAll(topoSnapshot.nodes);
                System.out.println("[PLAYBACK] Added " + topoSnapshot.nodes.size() + " nodes");
            }
            if (topoSnapshot.links != null) {
                links.addAll(topoSnapshot.links);
                System.out.println("[PLAYBACK] Added " + topoSnapshot.links.size() + " links");
            }
        }
        
        // Convert flow data
        if (flowSnapshots != null) {
            System.out.println("[PLAYBACK] Processing " + flowSnapshots.size() + " flow snapshots");
            for (PlaybackDataReader.FlowSnapshot flowSnapshot : flowSnapshots) {
                if (flowSnapshot.flows != null) {
                    flows.addAll(flowSnapshot.flows);
                    System.out.println("[PLAYBACK] Added " + flowSnapshot.flows.size() + " flows from snapshot");
                } else {
                    System.out.println("[PLAYBACK] Flow snapshot has null flows");
                }
            }
        } else {
            System.out.println("[PLAYBACK] flowSnapshots is null");
        }
        
        // Build DPID to IP mapping from nodes
        dpidToIpMap.clear();
        for (Node node : nodes) {
            if (node.ip != null && node.ip.length() > 0) {
                // dpid is int (primitive), so we convert it to String
                dpidToIpMap.put(String.valueOf(node.dpid), node.ip);
            }
        }
        System.out.println("[PLAYBACK] Built DPID to IP mapping with " + dpidToIpMap.size() + " entries");
        // Debug: Show first few entries
        dpidToIpMap.entrySet().stream().limit(5).forEach(entry -> 
            System.out.println("[PLAYBACK]   DPID " + entry.getKey() + " -> " + entry.getValue()));
        
        // CRITICAL: Assign flows to their corresponding links for animation
        System.out.println("[PLAYBACK] ===== BEFORE assignFlowsToLinks =====");
        System.out.println("[PLAYBACK] flows.size() = " + flows.size());
        System.out.println("[PLAYBACK] links.size() = " + links.size());
        assignFlowsToLinks(flows, links);
        
        // 验证分配结果
        int linksWithFlowsCount = 0;
        for (Link link : links) {
            if (link.flow_set != null && !link.flow_set.isEmpty()) {
                linksWithFlowsCount++;
            }
        }
        System.out.println("[PLAYBACK] ===== AFTER assignFlowsToLinks =====");
        System.out.println("[PLAYBACK] Links with flows: " + linksWithFlowsCount + "/" + links.size());
        
        System.out.println("[PLAYBACK] Final data: " + nodes.size() + " nodes, " + links.size() + " links, " + flows.size() + " flows");
        
        // Update topology canvas through main app
        javafx.application.Platform.runLater(() -> {
            if (mainApp != null) {
                System.out.println("[PLAYBACK] Calling mainApp.updateTopologyWithPlaybackData");
                mainApp.updateTopologyWithPlaybackData(nodes, links, flows);
                
                // Debug: Check canvas animation state
                if (mainApp.getTopologyCanvas() != null) {
                    System.out.println("[PLAYBACK] Canvas showFlows: " + mainApp.getTopologyCanvas().isShowFlows());
                    System.out.println("[PLAYBACK] Canvas showLinks: " + mainApp.getTopologyCanvas().isShowLinks());
                    System.out.println("[PLAYBACK] Canvas animationTime: " + mainApp.getTopologyCanvas().getAnimationTime());
                }
                
                // Update progress label to show current state
                
                System.out.println("[PLAYBACK] Updated topology: " + nodes.size() + " nodes, " + 
                                 links.size() + " links, " + flows.size() + " flows");
            } else {
                System.out.println("[PLAYBACK] mainApp is null in Platform.runLater");
            }
        });
    }
    
    /**
     * Convert timestamp (milliseconds since 1970-01-01) to human-readable format
     */
    private String formatTimestamp(long timestamp) {
        Date date = new Date(timestamp);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return formatter.format(date);
    }
    
    private void updateTimeLabels(long timestamp) {
        if (currentTimeLabel != null) {
            String timeText = formatTimestamp(timestamp);
            // 如果暂停中，添加重播提示
            if (!isPlaying) {
                timeText += " (Replay the flow packet traveling in this second)";
                currentTimeLabel.setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold;");
            } else {
                currentTimeLabel.setStyle("-fx-text-fill: #2c3e50; -fx-font-weight: bold;");
            }
            currentTimeLabel.setText(timeText);
        }
        
        // Progress calculation removed - no longer using progressLabel
    }
    
    /**
     * 更新当前时间标签（根据滑动条位置计算时间）
     */
    private void updateCurrentTimeLabel() {
        if (timeRange != null && timelineSlider != null && currentTimeLabel != null) {
            double sliderSeconds = timelineSlider.getValue();
            long currentTime = timeRange.startTime + (long)(sliderSeconds * 1000L);
            updateTimeLabels(currentTime);
        }
    }
    
    /**
     * 初始化跳转时间下拉选单的值（设置为指定的时间戳）
     */
    private void initializeJumpToTimeValues(long timestamp) {
        try {
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            calendar.setTimeInMillis(timestamp);
            
            int year = calendar.get(java.util.Calendar.YEAR);
            int month = calendar.get(java.util.Calendar.MONTH) + 1; // Calendar month is 0-based
            int day = calendar.get(java.util.Calendar.DAY_OF_MONTH);
            int hour = calendar.get(java.util.Calendar.HOUR_OF_DAY);
            int minute = calendar.get(java.util.Calendar.MINUTE);
            int second = calendar.get(java.util.Calendar.SECOND);
            
            if (jumpYearCombo != null) {
                jumpYearCombo.setValue(year);
            }
            if (jumpMonthCombo != null) {
                jumpMonthCombo.setValue(month);
            }
            if (jumpDayCombo != null) {
                jumpDayCombo.setValue(day);
            }
            if (jumpHourCombo != null) {
                jumpHourCombo.setValue(hour);
            }
            if (jumpMinuteCombo != null) {
                jumpMinuteCombo.setValue(minute);
            }
            if (jumpSecondCombo != null) {
                jumpSecondCombo.setValue(second);
            }
            
            System.out.println("[PLAYBACK] Initialized jump to time values: " + 
                             year + "-" + month + "-" + day + " " + 
                             hour + ":" + minute + ":" + second);
        } catch (Exception ex) {
            System.err.println("[PLAYBACK] Failed to initialize jump to time values: " + ex.getMessage());
        }
    }
    
    /**
     * 跳转到用户选择的时间
     */
    private void jumpToSelectedTime() {
        try {
            // 获取用户选择的时间
            Integer year = jumpYearCombo.getValue();
            Integer month = jumpMonthCombo.getValue();
            Integer day = jumpDayCombo.getValue();
            Integer hour = jumpHourCombo.getValue();
            Integer minute = jumpMinuteCombo.getValue();
            Integer second = jumpSecondCombo.getValue();
            
            // 检查所有值是否都已选择
            if (year == null || month == null || day == null || 
                hour == null || minute == null || second == null) {
                System.err.println("[PLAYBACK] Jump to time: Not all values are selected");
                return;
            }
            
            // 构造时间戳
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            calendar.set(year, month - 1, day, hour, minute, second); // month is 0-based in Calendar
            calendar.set(java.util.Calendar.MILLISECOND, 0);
            long targetTimestamp = calendar.getTimeInMillis();
            
            System.out.println("[PLAYBACK] Jump to time: " + year + "-" + month + "-" + day + " " + 
                             hour + ":" + minute + ":" + second + " (" + targetTimestamp + "ms)");
            
            // 检查目标时间是否在有效范围内
            if (timeRange != null) {
                if (targetTimestamp < timeRange.startTime) {
                    System.err.println("[PLAYBACK] Jump to time: Target time is before start time");
                    targetTimestamp = timeRange.startTime;
                } else if (targetTimestamp > timeRange.endTime) {
                    System.err.println("[PLAYBACK] Jump to time: Target time is after end time");
                    targetTimestamp = timeRange.endTime;
                }
            }
            
            // 如果正在播放，先暂停
            if (isPlaying) {
                isPlaying = false;
                playPauseButton.setText("▶");
                playPauseButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 14;");
                System.out.println("[PLAYBACK] Auto-paused for jump to time");
                notifyPlaybackStateChanged();
            }
            
            // 计算对应的 slider 值（秒数）
            if (timeRange != null && timelineSlider != null) {
                double secondsFromStart = (targetTimestamp - timeRange.startTime) / 1000.0;
                
                // 设置标志，表示这是程序化更新
                isUpdatingSliderProgrammatically = true;
                try {
                    timelineSlider.setValue(secondsFromStart);
                } finally {
                    isUpdatingSliderProgrammatically = false;
                }
                
                // 加载对应时间的数据
                loadDataAtTime(targetTimestamp);
                
                System.out.println("[PLAYBACK] Jumped to time: " + formatTimestamp(targetTimestamp) + 
                                 " (slider: " + secondsFromStart + "s)");
            }
            
        } catch (Exception ex) {
            System.err.println("[PLAYBACK] Failed to jump to selected time: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
    
    /**
     * Convert IP string to display format (reverse the order)
     */
    private String convertIpForDisplay(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[3] + "." + parts[2] + "." + parts[1] + "." + parts[0];
        }
        return ip;
    }
    
    /**
     * Convert node ID (string number) to IP address format
     */
    // Cache for DPID to IP mapping
    private Map<String, String> dpidToIpMap = new HashMap<>();
    
    private String convertNodeIdToIp(String nodeId) {
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
                System.out.println("[PLAYBACK] WARNING: DPID " + nodeId + " not found in dpidToIpMap");
            }
            
            // This is an IP address in integer format - convert directly
            return convertIpToString(nodeIdLong);
        } catch (NumberFormatException e) {
            // If it's not a number, return as is (might be an IP already)
            return nodeId;
        }
    }
    
    /**
     * Convert IP integer to dotted decimal string (same as PlaybackDataReader)
     */
    private String convertIpToString(long ip) {
        return String.format("%d.%d.%d.%d",
            (ip >> 24) & 0xFF,
            (ip >> 16) & 0xFF,
            (ip >> 8) & 0xFF,
            ip & 0xFF);
    }
    
    /**
     * Assign flows to their corresponding links for animation display.
     * This is critical for flow animation to work in playback mode.
     * Only assigns top 10 flows by sending rate for better performance.
     */
    private void assignFlowsToLinks(List<Flow> flows, List<Link> links) {
        System.out.println("[PLAYBACK] ========== Flow Assignment Start ==========");
        System.out.println("[PLAYBACK] Total flows to assign: " + flows.size());
        System.out.println("[PLAYBACK] Total links: " + links.size());
        
        // Clear existing flow_set from all links first
        for (Link link : links) {
            if (link.flow_set == null) {
                link.flow_set = new ArrayList<>();
            } else {
                link.flow_set.clear();
            }
        }
        
        // 调试：显示前 3 个 flows 的信息
        System.out.println("[PLAYBACK] Sample flows (first 3):");
        for (int i = 0; i < Math.min(3, flows.size()); i++) {
            Flow flow = flows.get(i);
            System.out.println("[PLAYBACK]   Flow " + i + ": " + 
                             convertIpForDisplay(flow.srcIp) + ":" + flow.srcPort + " -> " +
                             convertIpForDisplay(flow.dstIp) + ":" + flow.dstPort);
            System.out.println("[PLAYBACK]     pathNodes: " + (flow.pathNodes != null ? flow.pathNodes : "NULL"));
            System.out.println("[PLAYBACK]     rate: " + flow.estimatedFlowSendingRateBpsInTheLastSec);
        }
        
        // 调试：显示前 3 个 links 的信息
        System.out.println("[PLAYBACK] Sample links (first 3):");
        for (int i = 0; i < Math.min(3, links.size()); i++) {
            Link link = links.get(i);
            System.out.println("[PLAYBACK]   Link " + i + ": " + 
                             convertIpForDisplay(link.source) + " -> " + convertIpForDisplay(link.target));
        }
        
        // Filter flows with valid paths and sort by sending rate (descending)
        List<Flow> validFlows = new ArrayList<>();
        int noPathCount = 0;
        int shortPathCount = 0;
        
        for (Flow flow : flows) {
            if (flow.pathNodes == null) {
                noPathCount++;
            } else if (flow.pathNodes.size() < 2) {
                shortPathCount++;
            } else {
                validFlows.add(flow);
            }
        }
        
        System.out.println("[PLAYBACK] Flow filtering results:");
        System.out.println("[PLAYBACK]   Valid flows (with path >= 2 nodes): " + validFlows.size());
        System.out.println("[PLAYBACK]   No path: " + noPathCount);
        System.out.println("[PLAYBACK]   Path too short (< 2 nodes): " + shortPathCount);
        
        // Sort by sending rate (highest first) and take top 20
        validFlows.sort((a, b) -> Double.compare(b.estimatedFlowSendingRateBpsInTheLastSec, a.estimatedFlowSendingRateBpsInTheLastSec));
        List<Flow> topFlows = validFlows.subList(0, Math.min(20, validFlows.size()));
        
        System.out.println("[PLAYBACK] Selected top " + topFlows.size() + " flows by sending rate:");
        for (int i = 0; i < topFlows.size(); i++) {
            Flow flow = topFlows.get(i);
            System.out.println("[PLAYBACK] Flow " + i + ": " + convertIpForDisplay(flow.srcIp) + ":" + flow.srcPort + 
                             " -> " + convertIpForDisplay(flow.dstIp) + ":" + flow.dstPort + 
                             " (rate: " + flow.estimatedFlowSendingRateBpsInTheLastSec + ")");
        }
        
        int assignedFlows = 0;
        
        // For each top flow, find the links it should be assigned to
        for (Flow flow : topFlows) {
            List<String> displayPathNodes = new ArrayList<>();
            for (String pathNode : flow.pathNodes) {
                displayPathNodes.add(convertIpForDisplay(convertNodeIdToIp(pathNode)));
            }
            System.out.println("[PLAYBACK] Processing flow path: " + displayPathNodes);
            
            // Find all links that this flow passes through
            for (int i = 0; i < flow.pathNodes.size() - 1; i++) {
                String srcNodeId = flow.pathNodes.get(i);
                String dstNodeId = flow.pathNodes.get(i + 1);
                
                // Convert node IDs to IP addresses
                String srcIp = convertNodeIdToIp(srcNodeId);
                String dstIp = convertNodeIdToIp(dstNodeId);
                
                // Find the link between these two nodes
                boolean foundLink = false;
                for (Link link : links) {
                    if ((link.source.equals(srcIp) && link.target.equals(dstIp)) ||
                        (link.source.equals(dstIp) && link.target.equals(srcIp))) {
                        
                        // Add this flow to the link's flow_set
                        link.flow_set.add(flow);
                        assignedFlows++;
                        foundLink = true;
                        System.out.println("[PLAYBACK] ✓ Assigned flow " + convertIpForDisplay(flow.srcIp) + ":" + flow.srcPort +
                                         " -> " + convertIpForDisplay(flow.dstIp) + ":" + flow.dstPort +
                                         " to link " + convertIpForDisplay(link.source) + " -> " + convertIpForDisplay(link.target) +
                                         " (utilization: " + link.link_bandwidth_utilization_percent + "%)");
                        break; // Found the link, move to next path segment
                    }
                }
                if (!foundLink) {
                    System.out.println("[PLAYBACK] ✗ No link found between: " + convertIpForDisplay(srcIp) + " -> " + convertIpForDisplay(dstIp));
                }
            }
        }
        
        System.out.println("[PLAYBACK] Successfully assigned " + assignedFlows + " flow instances to links");
        
        // Debug: Show flow_set counts for each link
        for (Link link : links) {
            if (link.flow_set != null && !link.flow_set.isEmpty()) {
                System.out.println("[PLAYBACK] Link " + convertIpForDisplay(link.source) + " -> " + convertIpForDisplay(link.target) + 
                                 " has " + link.flow_set.size() + " flows (utilization: " + link.link_bandwidth_utilization_percent + "%)");
            }
        }
        
        int linksWithFlows = (int) links.stream().filter(l -> l.flow_set != null && !l.flow_set.isEmpty()).count();
        System.out.println("[PLAYBACK] Flow assignment complete. Links with flows: " + linksWithFlows + "/" + links.size());
        
        // 备用方案：如果没有任何 flow 被分配（可能是因为缺少 pathNodes），
        // 尝试直接将 flows 分配到它们的源-目标链路
        if (linksWithFlows == 0 && !flows.isEmpty()) {
            System.out.println("[PLAYBACK] WARNING: No flows assigned via pathNodes, trying direct assignment...");
            
            int directlyAssigned = 0;
            List<Flow> sortedFlows = new ArrayList<>(flows);
            sortedFlows.sort((a, b) -> Double.compare(b.estimatedFlowSendingRateBpsInTheLastSec, a.estimatedFlowSendingRateBpsInTheLastSec));
            List<Flow> topDirectFlows = sortedFlows.subList(0, Math.min(20, sortedFlows.size()));
            
            for (Flow flow : topDirectFlows) {
                boolean assigned = false;
                
                // 尝试找到从 srcIp 到 dstIp 的直接链路
                for (Link link : links) {
                    if ((link.source.equals(flow.srcIp) && link.target.equals(flow.dstIp)) ||
                        (link.source.equals(flow.dstIp) && link.target.equals(flow.srcIp))) {
                        
                        link.flow_set.add(flow);
                        directlyAssigned++;
                        assigned = true;
                        System.out.println("[PLAYBACK] ✓ Direct assigned: " + 
                                         convertIpForDisplay(flow.srcIp) + ":" + flow.srcPort + " -> " +
                                         convertIpForDisplay(flow.dstIp) + ":" + flow.dstPort +
                                         " to link " + convertIpForDisplay(link.source) + " -> " + convertIpForDisplay(link.target));
                        break;
                    }
                }
                
                if (!assigned) {
                    System.out.println("[PLAYBACK] ✗ No direct link found for: " + 
                                     convertIpForDisplay(flow.srcIp) + " -> " + convertIpForDisplay(flow.dstIp));
                }
            }
            
            System.out.println("[PLAYBACK] Direct assignment complete: " + directlyAssigned + " flows assigned");
            linksWithFlows = (int) links.stream().filter(l -> l.flow_set != null && !l.flow_set.isEmpty()).count();
            System.out.println("[PLAYBACK] Final links with flows: " + linksWithFlows + "/" + links.size());
        }
        
        System.out.println("[PLAYBACK] ========== Flow Assignment End ==========");
    }
    
    private void loadFrame(int frameIndex) {
        if (playbackData == null || playbackData.playback == null || 
            frameIndex < 0 || frameIndex >= playbackData.playback.size()) {
            return;
        }
        
        PlaybackData.PlaybackFrame frame = playbackData.playback.get(frameIndex);
        
        // Update time labels
        if (currentTimeLabel != null) {
            currentTimeLabel.setText(formatTimestamp(frame.time));
        }
        
        // Update progress
        double progress = (double) frameIndex / (playbackData.playback.size() - 1) * 100;
        
        // Update topology with frame data
        updateTopologyWithFrameData(frame);
        
        System.out.println("[PLAYBACK] Loaded frame " + frameIndex + " at time " + frame.time + "ms");
    }
    
    private void updateTopologyWithFrameData(PlaybackData.PlaybackFrame frame) {
        // Call the main app's method to update topology with playback data
        if (mainApp != null) {
            mainApp.updateTopologyWithPlaybackData(frame);
        }
    }

    public void hide() {
        setVisible(false);
    }
    
    public void show() {
        setVisible(true);
    }
    
    // 獲取播放狀態
    public boolean isPlaying() {
        return isPlaying;
    }
    
    // 通知播放狀態改變
    private void notifyPlaybackStateChanged() {
        System.out.println("[DEBUG] notifyPlaybackStateChanged called, isPlaying: " + isPlaying);
        if (mainApp != null) {
            System.out.println("[DEBUG] Calling mainApp.notifyPlaybackStateChanged()");
            mainApp.notifyPlaybackStateChanged();
        } else {
            System.out.println("[DEBUG] mainApp is null");
        }
    }
}
