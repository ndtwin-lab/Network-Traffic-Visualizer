package org.example.demo2;

import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

public class FilterButton extends HBox {
    private final TopologyCanvas topologyCanvas;
    private List<Flow> flows;
    private FlowFilterDialog flowFilterDialog;
    private final Button flowFilterButton;
    private javafx.stage.Stage primaryStage; // 主窗口引用

    public FilterButton(TopologyCanvas topologyCanvas, List<Flow> flows) {
        this.topologyCanvas = topologyCanvas;
        this.flows = flows;
        
        // Create Flow Filter dialog（稍後設置 owner）
        this.flowFilterDialog = new FlowFilterDialog(topologyCanvas, flows);
        
        setSpacing(10);
        setAlignment(Pos.CENTER_LEFT);
        
        // Flow Filter Button
        flowFilterButton = createFilterButton("Flow Filter", "Open flow filter dialog");
        flowFilterButton.setOnAction(e -> toggleFlowDialog());
        
        getChildren().add(flowFilterButton);
    }

    // Update data
    public void updateData(List<Flow> newFlows) {
        this.flows = newFlows;
        
        // Update dialog data
        flowFilterDialog.updateData(newFlows);
    }
    
    // Getter for FlowFilterDialog
    public FlowFilterDialog getFlowFilterDialog() {
        return flowFilterDialog;
    }
    
    // 設置主窗口引用（用於設置子窗口 owner）
    public void setPrimaryStage(javafx.stage.Stage primaryStage) {
        this.primaryStage = primaryStage;
        // 更新 FlowFilterDialog 的 owner
        if (flowFilterDialog != null) {
            flowFilterDialog.setOwner(primaryStage);
        }
    }
    
    private Button createFilterButton(String buttonText, String tooltipText) {
        Button button = new Button(buttonText);
        button.setPrefSize(160, 30);
        button.setMinSize(160, 30);
        button.setMaxSize(160, 30);
        button.setStyle(""); // Use default style, consistent with other buttons
        
        // Add tooltip
        button.setTooltip(new Tooltip(tooltipText));
        
        return button;
    }
    
    private void toggleFlowDialog() {
        if (flowFilterDialog.isShowing()) {
            flowFilterDialog.hide();
        } else {
            // Show dialog, will automatically center
            flowFilterDialog.show();
        }
    }
}