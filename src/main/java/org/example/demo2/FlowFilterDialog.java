package org.example.demo2;

import java.util.List;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class FlowFilterDialog {
    private final Stage dialog;
    private final FlowFilter flowFilter;
    private final TopologyCanvas topologyCanvas;
    private final List<Flow> flows;

    public FlowFilterDialog(TopologyCanvas topologyCanvas, List<Flow> flows) {
        this.topologyCanvas = topologyCanvas;
        this.flows = flows;
        
        // Create FlowFilter
        this.flowFilter = new FlowFilter(topologyCanvas, flows);
        
        // Create dialog
        this.dialog = new Stage();
        dialog.initModality(Modality.NONE); // Non-modal dialog
        dialog.setTitle("Flow Filter");
        dialog.setResizable(false);
        dialog.setWidth(250);
        dialog.setHeight(350);
        
        // Create UI
        createUI();
    }
    
    /**
     * 設置窗口 owner（方案一：設置 owner 為主窗口）
     */
    public void setOwner(javafx.stage.Stage owner) {
        if (dialog != null && owner != null) {
            dialog.initOwner(owner);
        }
    }
    
    private void createUI() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #e9ecef; -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8;");
        
        // Title
        Label titleLabel = new Label("Flow Filter & Information");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        titleLabel.setAlignment(Pos.CENTER);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setStyle("-fx-text-fill: #2c3e50;");
        
        // Content area
        VBox contentBox = new VBox(10);
        contentBox.setPadding(new Insets(10));
        contentBox.getChildren().add(flowFilter);
        
        root.getChildren().addAll(titleLabel, contentBox);
        
        Scene scene = new Scene(root);
        dialog.setScene(scene);
    }
    

    
    public void show() {
        dialog.show();
        // Place window in front of main window
        dialog.toFront();
        
        // Calculate center position
        centerOnScreen();
    }
    
    private void centerOnScreen() {
        // Get screen dimensions
        javafx.stage.Screen screen = javafx.stage.Screen.getPrimary();
        javafx.geometry.Rectangle2D bounds = screen.getVisualBounds();
        
        // Calculate center position
        double centerX = bounds.getMinX() + (bounds.getWidth() - dialog.getWidth()) / 2;
        double centerY = bounds.getMinY() + (bounds.getHeight() - dialog.getHeight()) / 2;
        
        // Set window position
        dialog.setX(centerX);
        dialog.setY(centerY);
    }
    
    public void hide() {
        dialog.hide();
    }
    
    public boolean isShowing() {
        return dialog.isShowing();
    }
    
    public void updateData(List<Flow> newFlows) {
        flowFilter.updateFlows(newFlows);
    }
    

    

} 