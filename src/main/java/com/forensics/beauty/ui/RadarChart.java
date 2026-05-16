package com.forensics.beauty.ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 自定义雷达图 Canvas 组件
 * 显示多维度分析得分（0-100）
 */
public class RadarChart extends Canvas {

    private Map<String, Double> data = new LinkedHashMap<>();
    private String title = "";

    // 颜色方案
    private static final Color BG_COLOR     = Color.rgb(25, 28, 36);
    private static final Color GRID_COLOR   = Color.rgb(60, 65, 80);
    private static final Color FILL_COLOR   = Color.rgb(99, 155, 255, 0.35);
    private static final Color STROKE_COLOR = Color.rgb(99, 155, 255, 0.95);
    private static final Color LABEL_COLOR  = Color.rgb(200, 210, 230);
    private static final Color TITLE_COLOR  = Color.WHITE;

    public RadarChart(double width, double height) {
        super(width, height);
    }

    public void setData(Map<String, Double> data, String title) {
        this.data  = new LinkedHashMap<>(data);
        this.title = title;
        draw();
    }

    private void draw() {
        GraphicsContext gc = getGraphicsContext2D();
        double w = getWidth(), h = getHeight();
        double cx = w / 2, cy = h / 2 + 10;
        double radius = Math.min(w, h) * 0.35;
        int n = data.size();
        if (n < 3) return;

        // 背景
        gc.setFill(BG_COLOR);
        gc.fillRect(0, 0, w, h);

        String[] labels = data.keySet().toArray(new String[0]);
        double[] values = data.values().stream().mapToDouble(Double::doubleValue).toArray();

        // 绘制网格（5层）
        gc.setStroke(GRID_COLOR);
        gc.setLineWidth(0.5);
        for (int layer = 1; layer <= 5; layer++) {
            double r = radius * layer / 5.0;
            double[] xPts = new double[n];
            double[] yPts = new double[n];
            for (int i = 0; i < n; i++) {
                double angle = 2 * Math.PI * i / n - Math.PI / 2;
                xPts[i] = cx + r * Math.cos(angle);
                yPts[i] = cy + r * Math.sin(angle);
            }
            gc.strokePolygon(xPts, yPts, n);
            // 刻度标签
            gc.setFill(Color.rgb(100, 110, 130));
            gc.setFont(Font.font("Arial", 9));
            gc.fillText(String.valueOf(layer * 20), cx + 3, cy - r + 4);
        }

        // 轴线
        gc.setStroke(GRID_COLOR);
        gc.setLineWidth(0.8);
        for (int i = 0; i < n; i++) {
            double angle = 2 * Math.PI * i / n - Math.PI / 2;
            gc.strokeLine(cx, cy,
                cx + radius * Math.cos(angle),
                cy + radius * Math.sin(angle));
        }

        // 数据多边形
        double[] dataX = new double[n];
        double[] dataY = new double[n];
        for (int i = 0; i < n; i++) {
            double angle = 2 * Math.PI * i / n - Math.PI / 2;
            double r = radius * Math.min(values[i], 100) / 100.0;
            dataX[i] = cx + r * Math.cos(angle);
            dataY[i] = cy + r * Math.sin(angle);
        }
        gc.setFill(FILL_COLOR);
        gc.fillPolygon(dataX, dataY, n);
        gc.setStroke(STROKE_COLOR);
        gc.setLineWidth(2);
        gc.strokePolygon(dataX, dataY, n);

        // 数据点
        gc.setFill(STROKE_COLOR);
        for (int i = 0; i < n; i++) {
            gc.fillOval(dataX[i] - 4, dataY[i] - 4, 8, 8);
        }

        // 轴标签
        gc.setFill(LABEL_COLOR);
        gc.setFont(Font.font("Arial", 11));
        gc.setTextAlign(TextAlignment.CENTER);
        for (int i = 0; i < n; i++) {
            double angle = 2 * Math.PI * i / n - Math.PI / 2;
            double labelR = radius + 24;
            double lx = cx + labelR * Math.cos(angle);
            double ly = cy + labelR * Math.sin(angle) + 4;
            gc.fillText(labels[i], lx, ly);
            // 数值
            gc.setFill(getValueColor(values[i]));
            gc.setFont(Font.font("Arial", 10));
            gc.fillText(String.format("%.0f", values[i]), lx, ly + 13);
            gc.setFill(LABEL_COLOR);
            gc.setFont(Font.font("Arial", 11));
        }

        // 标题
        gc.setFill(TITLE_COLOR);
        gc.setFont(Font.font("Arial", 13));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(title, w / 2, 20);
    }

    private Color getValueColor(double value) {
        if (value < 30) return Color.rgb(80, 200, 120);
        if (value < 55) return Color.rgb(255, 200, 60);
        if (value < 75) return Color.rgb(255, 130, 60);
        return Color.rgb(240, 60, 80);
    }
}
