package org.example.demo2;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class InfoPanel extends VBox {
    private final TextArea textArea;
    public final Button toggleButton;
    private final Label flowCountLabel;
    public boolean isShowingAllFlows = false;
    private Link lastEdge = null;
    private Node lastSrc = null, lastTgt = null;
    private final TopologyCanvas topoPanel;
    private final VBox flowBox;
    private VBox contentBox;

    public InfoPanel(TopologyCanvas topoPanel) {
        this.topoPanel = topoPanel;
        setPrefWidth(250);
        setMaxWidth(320);
        setMinWidth(180);
        setStyle("-fx-background-color: #f9f9f9; -fx-border-color: #cccccc;");
        setPadding(new javafx.geometry.Insets(10));
        setSpacing(10);
        // Main content wrapped in ScrollPane
        contentBox = new javafx.scene.layout.VBox();
        contentBox.setSpacing(8);
        javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane(contentBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
        getChildren().add(scrollPane);

        // All content will be added to contentBox later
        // Top title bar
        Label headerLabel = new Label("information");
        headerLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        headerLabel.setMaxWidth(Double.MAX_VALUE);
        headerLabel.setAlignment(Pos.CENTER);

        // Flow/Link button
        toggleButton = new Button("Flow");
        toggleButton.setVisible(false);
        toggleButton.setOnAction(e -> toggleFlowDisplay());

        HBox titleBar = new HBox(headerLabel, toggleButton);
        HBox.setHgrow(headerLabel, Priority.ALWAYS);
        titleBar.setAlignment(Pos.CENTER);
        titleBar.setPadding(new Insets(5, 10, 5, 10));
        titleBar.setSpacing(10);

        // Flow statistics label
        flowCountLabel = new Label("Active flows: 0 / 0");
        flowCountLabel.setFont(Font.font("Arial", 12));
        flowCountLabel.setPadding(new Insets(0, 0, 5, 10));

        // Information content
        textArea = new TextArea();
        textArea.setEditable(false);
        textArea.setFont(Font.font("Monospaced", 14));
        textArea.setWrapText(true);
        textArea.setPrefRowCount(20);
        textArea.setStyle("-fx-focus-color: transparent; -fx-faint-focus-color: transparent;");

        flowBox = new VBox();
        flowBox.setSpacing(8);
        flowBox.setPadding(new Insets(10, 10, 10, 10));
        flowBox.setVisible(false);
        VBox.setVgrow(flowBox, Priority.ALWAYS);

        contentBox.getChildren().addAll(titleBar, flowCountLabel, textArea, flowBox);
        VBox.setVgrow(textArea, Priority.ALWAYS);
    }


    public void showNode(Node n) {
        textArea.setVisible(true);
        flowBox.setVisible(false);
        textArea.setText("");
        textArea.appendText("node\n----\n");
        textArea.appendText("IP: " + n.ip + "\n");
        textArea.appendText("Device_name: " + n.name + "\n");
        textArea.appendText("type: " + n.type + "\n");
        
        // New: Display CPU and memory utilization information
        if (n.hasUtilizationData()) {
            textArea.appendText("\nResource Utilization\n");
            textArea.appendText("-------------------\n");
            if (n.getCpuUtilization() != null) {
                textArea.appendText("CPU Usage: " + n.getCpuUtilization() + "%\n");
            }
            if (n.getMemoryUtilization() != null) {
                textArea.appendText("Memory Usage: " + n.getMemoryUtilization() + "%\n");
            }
        } else {
            textArea.appendText("\nResource Utilization\n");
            textArea.appendText("-------------------\n");
            textArea.appendText("No utilization data available\n");
        }
        
        // Hide Flow button because nodes don't have Flow buttons
        toggleButton.setVisible(false);
        isShowingAllFlows = false;
    }

    public void showLink(Link l, Node src, Node tgt) {
        // Save Link information for switching back to Link view
        lastEdge = l;
        lastSrc = src;
        lastTgt = tgt;
        
        textArea.setVisible(true);
        flowBox.setVisible(false);
        textArea.setText("");
        textArea.appendText("link\n----\n");
        textArea.appendText("SRCIP: " + (src != null ? src.ip : l.source) + "\n");
        textArea.appendText("DSTIP: " + (tgt != null ? tgt.ip : l.target) + "\n");
        textArea.appendText("Is_up: " + (l.is_up ? "up" : "down") + "\n");
        textArea.appendText("bandwidth: " + l.bandwidth + "\n");
        
        // Add flow numbers
        int flowNumbers = (l.flow_set != null) ? l.flow_set.size() : 0;
        textArea.appendText("flow numbers: " + flowNumbers + "\n");
        
        // Show Flow button
        toggleButton.setText("Flow");
        toggleButton.setVisible(true);
        isShowingAllFlows = false;
    }


    public void clear() {
        textArea.setVisible(true);
        flowBox.setVisible(false);
        textArea.setText("");
        toggleButton.setVisible(false);
        isShowingAllFlows = false;
        lastEdge = null;
    }

    private void toggleFlowDisplay() {
        if (lastEdge != null) {
            showLink(lastEdge, lastSrc, lastTgt);
            toggleButton.setText("Flow");
            isShowingAllFlows = false;
            if (topoPanel != null) {
                topoPanel.setShowFlows(false);
            }
        }
    }
} 