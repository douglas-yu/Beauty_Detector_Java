package com.forensics.beauty.ui;

import com.forensics.beauty.model.AnalysisResult;
import com.forensics.beauty.model.FaceAnalysisResult;
import com.forensics.beauty.model.MetricScore;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ResultsPanel {

    private final VBox root;
    private final HBox gaugeRow;
    private final RadarChart radarBeauty, radarAI;
    private final VBox detailsBox;
    private final Label verdictLabel;
    private final ComboBox<Object> faceSelector; // FaceAnalysisResult or "综合"
    private AnalysisResult currentResult;

    private static final String OVERALL = "综合评分（最严重人脸）";

    public ResultsPanel() {
        root = new VBox(10);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color:#1a1d26;");
        root.setPrefWidth(420);

        Label title = new Label("分析结果");
        title.setFont(Font.font("Arial",FontWeight.BOLD,16));
        title.setTextFill(Color.WHITE);

        gaugeRow = new HBox(8);
        gaugeRow.setAlignment(Pos.CENTER);
        gaugeRow.getChildren().addAll(
            createGauge("美颜滤镜",0,"#639BFF"),
            createGauge("身体修图",0,"#FF8C42"),
            createGauge("AI生成",  0,"#FF4F79"));

        radarBeauty = new RadarChart(390,230);
        radarAI     = new RadarChart(390,200);

        verdictLabel = new Label("请加载图像/视频开始分析");
        verdictLabel.setWrapText(true);
        //verdictLabel.setFont(Font.font("Arial",13));
        verdictLabel.setFont(Font.font("Consolas", 13));
        verdictLabel.setTextFill(Color.rgb(200,210,230));
        verdictLabel.setStyle("-fx-background-color:#252836;-fx-padding:10;-fx-background-radius:6;");
        verdictLabel.setMaxWidth(Double.MAX_VALUE);

        // Face selector
        faceSelector = new ComboBox<>();
        faceSelector.setMaxWidth(Double.MAX_VALUE);
        faceSelector.setStyle("-fx-background-color:#252836;-fx-text-fill:#c8d0e0;-fx-background-radius:4;");
        faceSelector.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Object o) {
                if (o==null) return "";
                if (o instanceof FaceAnalysisResult f) return f.getLabel();
                return o.toString();
            }
            @Override public Object fromString(String s) { return s; }
        });
        faceSelector.setOnAction(e -> refreshDetailsForSelection());
        faceSelector.setVisible(false);

        detailsBox = new VBox(4);
        detailsBox.setPadding(new Insets(4,0,0,0));

        ScrollPane scroll = new ScrollPane(new VBox(12, gaugeRow,
            sec("▶ 美颜/局部滤镜分析"), radarBeauty,
            sec("▶ AI生成检测"),         radarAI,
            sec("▶ 综合判决"),           verdictLabel,
            sec("▶ 人脸选择器"),         faceSelector,
            sec("▶ 详细指标"),           detailsBox));
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background:#1a1d26;-fx-background-color:#1a1d26;");
        VBox.setVgrow(scroll,Priority.ALWAYS);

        root.getChildren().addAll(title, scroll);
    }

    public VBox getRoot() { return root; }

    public void updateResults(AnalysisResult result) {
        currentResult = result;

        // Gauges (overall)
        gaugeRow.getChildren().setAll(
            createGauge("美颜滤镜",result.getBeautyFilterScore(),"#639BFF"),
            createGauge("身体修图",result.getBodyRetouchScore(), "#FF8C42"),
            createGauge("AI生成",  result.getAiGenerationScore(),"#FF4F79"));

        // Overall radars
        refreshRadars(result.getTextureScore(), result.getDetailLossScore(),
            result.getColorUniformScore(), result.getEdgeScore(),
            result.getSaturationScore(), result.getEyeEnlargementScore(),
            result.getLipEnhancementScore(), result.getNoseSlimmingScore(),
            result.getElaScore(), result.getFftArtifactScore(),
            result.getNoisePatternScore(), result.getDctScore(), result.getWarpScore());

        verdictLabel.setText(result.getVerdict());

        // Face selector
        List<FaceAnalysisResult> faces = result.getFaceResults();
        faceSelector.getItems().clear();
        if (faces!=null && faces.stream().filter(f->!f.isWholeImage).count()>1) {
            faceSelector.getItems().add(OVERALL);
            faces.stream().filter(f->!f.isWholeImage).forEach(f->faceSelector.getItems().add(f));
            faceSelector.getSelectionModel().selectFirst();
            faceSelector.setVisible(true);
        } else {
            faceSelector.setVisible(false);
        }

        // Default: show overall detail metrics
        showOverallDetails(result);
    }

    private void refreshDetailsForSelection() {
        if (currentResult==null) return;
        Object sel = faceSelector.getValue();
        if (sel instanceof FaceAnalysisResult face) {
            showFaceDetails(face);
            // Update radar for this face
            refreshRadars(
                ms(face,"texture"),ms(face,"detail"),ms(face,"color"),
                ms(face,"edge"),ms(face,"saturation"),ms(face,"eye"),
                ms(face,"lip"),ms(face,"nose"),
                currentResult.getElaScore(), currentResult.getFftArtifactScore(),
                currentResult.getNoisePatternScore(), currentResult.getDctScore(),
                currentResult.getWarpScore());
        } else {
            showOverallDetails(currentResult);
            refreshRadars(currentResult.getTextureScore(), currentResult.getDetailLossScore(),
                currentResult.getColorUniformScore(), currentResult.getEdgeScore(),
                currentResult.getSaturationScore(), currentResult.getEyeEnlargementScore(),
                currentResult.getLipEnhancementScore(), currentResult.getNoseSlimmingScore(),
                currentResult.getElaScore(), currentResult.getFftArtifactScore(),
                currentResult.getNoisePatternScore(), currentResult.getDctScore(), currentResult.getWarpScore());
        }
    }

    private MetricScore ms(FaceAnalysisResult f, String key) {
        return f.metrics.getOrDefault(key, new MetricScore(key,0,"N/A"));
    }

    private void refreshRadars(MetricScore tex, MetricScore det, MetricScore col, MetricScore edg,
                                MetricScore sat, MetricScore eye, MetricScore lip, MetricScore nos,
                                MetricScore ela, MetricScore fftAI, MetricScore noise, MetricScore dct,
                                MetricScore warp) {
        Map<String,Double> bd=new LinkedHashMap<>();
        addR(bd,"纹理",tex); addR(bd,"细节",det); addR(bd,"色均",col);
        addR(bd,"边缘",edg); addR(bd,"饱和",sat); addR(bd,"眼睛",eye);
        addR(bd,"嘴唇",lip); addR(bd,"鼻子",nos);
        if (bd.size()>=3) radarBeauty.setData(bd,"美颜/局部滤镜维度分析");

        Map<String,Double> ad=new LinkedHashMap<>();
        addR(ad,"ELA",ela); addR(ad,"FFT",fftAI); addR(ad,"噪声",noise);
        addR(ad,"DCT",dct); addR(ad,"形变",warp);
        if (ad.size()>=3) radarAI.setData(ad,"AI生成/修图维度分析");
    }

    private void addR(Map<String,Double> m, String k, MetricScore s) {
        if(s!=null) m.put(k,s.getScore());
    }

    private void showOverallDetails(AnalysisResult r) {
        detailsBox.getChildren().clear();
        row("皮肤纹理平滑度",r.getTextureScore());
        row("高频细节丢失",  r.getDetailLossScore());
        row("肤色均匀度",    r.getColorUniformScore());
        row("边缘/残差抑制", r.getEdgeScore());
        row("饱和度增强",    r.getSaturationScore());
        row("眼睛放大检测",  r.getEyeEnlargementScore());
        row("嘴唇美化检测",  r.getLipEnhancementScore());
        row("鼻子瘦化检测",  r.getNoseSlimmingScore());
        row("局部形变检测",  r.getWarpScore());
        row("轮廓异常",      r.getContourScore());
        row("比例拉伸",      r.getProportionScore());
        row("ELA误差分析",   r.getElaScore());
        row("GAN频域伪影",   r.getFftArtifactScore());
        row("噪声模式",      r.getNoisePatternScore());
        row("DCT分布",       r.getDctScore());
    }

    private void showFaceDetails(FaceAnalysisResult face) {
        detailsBox.getChildren().clear();
        face.metrics.forEach((k,v) -> row(v.getName(), v));
    }

    private void row(String name, MetricScore score) {
        if (score==null) return;
        HBox r=new HBox(8); r.setAlignment(Pos.CENTER_LEFT);
        r.setPadding(new Insets(3,6,3,6));
        r.setStyle("-fx-background-color:#252836;-fx-background-radius:4;");
        Label nl=new Label(name);
        nl.setFont(Font.font("Arial",12)); nl.setTextFill(Color.rgb(190,200,220)); nl.setPrefWidth(140);
        ProgressBar bar=new ProgressBar(score.getScore()/100.0);
        bar.setPrefWidth(110); bar.setPrefHeight(10);
        bar.setStyle("-fx-accent:"+rc(score)+";");
        Label sl=new Label(String.format("%.0f  %s",score.getScore(),score.getRiskLabel()));
        sl.setFont(Font.font("Arial",12)); sl.setTextFill(Color.web(rc(score)));
        Tooltip.install(r, new Tooltip(score.getDescription()));
        r.getChildren().addAll(nl,bar,sl);
        detailsBox.getChildren().add(r);
    }

    private String rc(MetricScore s) {
        return switch(s.getRisk()){
            case LOW->"#4EC994"; case MEDIUM->"#F0C040";
            case HIGH->"#FF8040"; case CRITICAL->"#FF3C5A";
        };
    }

    private StackPane createGauge(String label, double value, String hex) {
        StackPane p=new StackPane(); p.setPrefSize(108,110);
        Arc bg=new Arc(54,60,42,42,210,-240); bg.setType(ArcType.OPEN);
        bg.setStroke(Color.rgb(50,55,70)); bg.setStrokeWidth(10); bg.setFill(Color.TRANSPARENT);
        Arc vl=new Arc(54,60,42,42,210,-(value/100.0)*240); vl.setType(ArcType.OPEN);
        vl.setStroke(Color.web(hex)); vl.setStrokeWidth(10); vl.setFill(Color.TRANSPARENT);
        Text vt=new Text(String.format("%.0f",value));
        vt.setFont(Font.font("Arial",FontWeight.BOLD,20)); vt.setFill(Color.web(hex)); vt.setTranslateY(4);
        Text lt=new Text(label);
        lt.setFont(Font.font("Arial",12)); lt.setFill(Color.rgb(180,190,210)); lt.setTranslateY(26);
        p.getChildren().addAll(bg,vl,vt,lt); return p;
    }

    private Label sec(String t) {
        Label l=new Label(t);
        l.setFont(Font.font("Arial",FontWeight.BOLD,13));
        l.setTextFill(Color.rgb(140,160,200));
        l.setPadding(new Insets(6,0,2,0)); return l;
    }
}