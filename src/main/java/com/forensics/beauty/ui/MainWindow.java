package com.forensics.beauty.ui;

import com.forensics.beauty.analysis.AnalysisEngine;
import com.forensics.beauty.model.AnalysisResult;
import com.forensics.beauty.model.FaceAnalysisResult;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainWindow {

    private final BorderPane root;
    private final Stage stage;
    private final AnalysisEngine engine;
    private final ResultsPanel resultsPanel;

    private final ImageView previewView;
    private final Canvas faceCanvas;
    private final Label statusLabel;
    private final ProgressBar progressBar;
    private final ExecutorService executor;

    // Overlay state
    private Image originalImage;
    private Image elaImage;
    private boolean showELA   = false;
    private boolean showFaces = true;
    private AnalysisResult lastResult;
    private String currentFilePath;

    // Toggle buttons (kept as fields for style update)
    private Button elaBtn, faceBtn;

    public MainWindow(Stage stage) {
        this.stage  = stage;
        this.engine = new AnalysisEngine();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "analysis-thread"); t.setDaemon(true); return t;
        });

        // ── Preview area ──
        previewView = new ImageView();
        previewView.setPreserveRatio(true);
        previewView.setFitWidth(700); previewView.setFitHeight(600);

        faceCanvas = new Canvas(700, 600);
        faceCanvas.setMouseTransparent(true);

        StackPane previewPane = new StackPane(previewView, faceCanvas);
        previewPane.setStyle("-fx-background-color: #12141c;");
        previewPane.setMinWidth(480);

        // Bind canvas to pane size
        faceCanvas.widthProperty().bind(previewPane.widthProperty());
        faceCanvas.heightProperty().bind(previewPane.heightProperty());
        previewView.fitWidthProperty().bind(previewPane.widthProperty());
        previewView.fitHeightProperty().bind(previewPane.heightProperty());

        // Redraw on resize
        previewPane.widthProperty().addListener((o,ov,nv)->  Platform.runLater(this::refreshFaceBoxes));
        previewPane.heightProperty().addListener((o,ov,nv)-> Platform.runLater(this::refreshFaceBoxes));

        Label hint = new Label("拖拽图片/视频至此，或点击「打开文件」");
        hint.setFont(Font.font("Arial",14)); hint.setTextFill(Color.rgb(90,100,130));
        previewPane.getChildren().add(hint);
        previewView.imageProperty().addListener((o,ov,nv)->hint.setVisible(nv==null));
        enableDragDrop(previewPane);

        // ── Results panel ──
        resultsPanel = new ResultsPanel();

        // ── Status bar ──
        statusLabel = new Label("就绪");
        statusLabel.setTextFill(Color.rgb(150,170,200));
        statusLabel.setFont(Font.font("Arial",12));
        progressBar = new ProgressBar(0); progressBar.setPrefWidth(200); progressBar.setVisible(false);
        HBox statusBar = new HBox(10, progressBar, statusLabel);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(6,12,6,12));
        statusBar.setStyle("-fx-background-color:#111318;-fx-border-color:#2a2d3a;-fx-border-width:1 0 0 0;");

        SplitPane split = new SplitPane(previewPane, resultsPanel.getRoot());
        split.setDividerPositions(0.65);
        VBox.setVgrow(split, Priority.ALWAYS);

        root = new BorderPane();
        root.setTop(buildToolbar());
        root.setCenter(split);
        root.setBottom(statusBar);
        root.setStyle("-fx-background-color:#1a1d26;");
    }

    public BorderPane getRoot() { return root; }

    private HBox buildToolbar() {
        Button openBtn = btn("📂  打开文件", "#639BFF");
        openBtn.setOnAction(e -> openFile());

        Button analyzeBtn = btn("🔍  开始分析", "#4EC994");
        analyzeBtn.setOnAction(e -> { if (currentFilePath!=null) runAnalysis(currentFilePath); });

        elaBtn = btn("🌡  ELA热图", "#888EA0");
        elaBtn.setOnAction(e -> toggleELA());

        faceBtn = btn("👥  人脸框  ✓", "#F0C040");
        faceBtn.setOnAction(e -> toggleFaceBoxes());

        Button clearBtn = btn("✕  清除", "#888EA0");
        clearBtn.setOnAction(e -> {
            previewView.setImage(null); originalImage=null; elaImage=null;
            currentFilePath=null; lastResult=null;
            clearCanvas(); statusLabel.setText("就绪");
        });

        Label title = new Label("BeautyLens Detector  —  美颜/AI图像取证分析");
        title.setFont(Font.font("Arial",FontWeight.BOLD,14));
        title.setTextFill(Color.WHITE);
        Region spacer = new Region(); HBox.setHgrow(spacer,Priority.ALWAYS);

        HBox tb = new HBox(8, openBtn, analyzeBtn, elaBtn, faceBtn, clearBtn, spacer, title);
        tb.setAlignment(Pos.CENTER_LEFT);
        tb.setPadding(new Insets(10,15,10,15));
        tb.setStyle("-fx-background-color:#13151f;-fx-border-color:#2a2d3a;-fx-border-width:0 0 1 0;");
        return tb;
    }

    private void toggleELA() {
        showELA = !showELA;
        if (showELA && elaImage!=null) {
            previewView.setImage(elaImage);
            elaBtn.setStyle(elaBtn.getStyle().replace("#888EA0","#FF6B6B"));
        } else {
            previewView.setImage(originalImage);
            elaBtn.setStyle(elaBtn.getStyle().replace("#FF6B6B","#888EA0"));
        }
        if (!showELA) elaBtn.setText("🌡  ELA热图");
        else          elaBtn.setText("🌡  原图恢复");
    }

    private void toggleFaceBoxes() {
        showFaces = !showFaces;
        faceBtn.setText(showFaces ? "👥  人脸框  ✓" : "👥  人脸框");
        refreshFaceBoxes();
    }

    private void refreshFaceBoxes() {
        if (lastResult==null||lastResult.getFaceResults()==null) { clearCanvas(); return; }
        if (!showFaces) { clearCanvas(); return; }
        drawFaceBoxes(lastResult.getFaceResults());
    }

    private void clearCanvas() {
        GraphicsContext gc = faceCanvas.getGraphicsContext2D();
        gc.clearRect(0,0,faceCanvas.getWidth(),faceCanvas.getHeight());
    }

    private void drawFaceBoxes(List<FaceAnalysisResult> faces) {
        GraphicsContext gc = faceCanvas.getGraphicsContext2D();
        gc.clearRect(0,0,faceCanvas.getWidth(),faceCanvas.getHeight());

        Image img = previewView.getImage();
        if (img==null||faces==null) return;

        // Compute image display bounds (centered, aspect-ratio preserved)
        double imgW=img.getWidth(), imgH=img.getHeight();
        double viewW=faceCanvas.getWidth(), viewH=faceCanvas.getHeight();
        double scale=Math.min(viewW/imgW, viewH/imgH);
        double dispW=imgW*scale, dispH=imgH*scale;
        double offX=(viewW-dispW)/2, offY=(viewH-dispH)/2;

        gc.setFont(Font.font("Arial",FontWeight.BOLD,13));

        for (FaceAnalysisResult face : faces) {
            if (face.isWholeImage) continue;

            double bx=offX+face.x*scale, by=offY+face.y*scale;
            double bw=face.w*scale,      bh=face.h*scale;
            Color color = scoreColor(face.beautyScore);

            // Box
            gc.setStroke(color); gc.setLineWidth(2.5);
            gc.strokeRect(bx,by,bw,bh);

            // Corner accents
            double corner = Math.min(bw,bh)*0.15;
            gc.setLineWidth(4);
            gc.strokeLine(bx,by,bx+corner,by);        gc.strokeLine(bx,by,bx,by+corner);
            gc.strokeLine(bx+bw,by,bx+bw-corner,by);  gc.strokeLine(bx+bw,by,bx+bw,by+corner);
            gc.strokeLine(bx,by+bh,bx+corner,by+bh);  gc.strokeLine(bx,by+bh,bx,by+bh-corner);
            gc.strokeLine(bx+bw,by+bh,bx+bw-corner,by+bh); gc.strokeLine(bx+bw,by+bh,bx+bw,by+bh-corner);

            // Label background + text
            String label = String.format("人脸%d %s  %.0f分",
                face.index+1, face.isProfile?"(侧)":"", face.beautyScore);
            double tw = label.length()*8.5;
            gc.setFill(Color.rgb(0,0,0,0.65));
            gc.fillRoundRect(bx, by-22, tw+10, 20, 6, 6);
            gc.setFill(color);
            gc.fillText(label, bx+5, by-6);
        }
    }

    private Color scoreColor(double score) {
        if (score < 30) return Color.rgb(78, 201, 148);
        if (score < 55) return Color.rgb(240, 192, 64);
        if (score < 75) return Color.rgb(255, 128, 64);
        return Color.rgb(240, 60, 80);
    }

    private void openFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("选择图像或视频文件");
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("图像","*.jpg","*.jpeg","*.png","*.bmp","*.webp"),
            new FileChooser.ExtensionFilter("视频","*.mp4","*.avi","*.mov","*.mkv","*.flv","*.webm"),
            new FileChooser.ExtensionFilter("全部支持","*.jpg","*.jpeg","*.png","*.bmp","*.webp","*.mp4","*.avi","*.mov","*.mkv"));
        File file = fc.showOpenDialog(stage);
        if (file!=null) loadFile(file.getAbsolutePath());
    }

    private void enableDragDrop(StackPane pane) {
        pane.setOnDragOver(e->{if(e.getDragboard().hasFiles())e.acceptTransferModes(TransferMode.COPY);e.consume();});
        pane.setOnDragDropped(e->{
            Dragboard db=e.getDragboard();
            if(db.hasFiles()&&!db.getFiles().isEmpty()) loadFile(db.getFiles().get(0).getAbsolutePath());
            e.setDropCompleted(true); e.consume();
        });
    }

    private void loadFile(String path) {
        currentFilePath=path; showELA=false;
        elaBtn.setText("🌡  ELA热图");
        statusLabel.setText("已加载: "+new File(path).getName());
        String lp=path.toLowerCase();
        boolean isVideo=lp.endsWith(".mp4")||lp.endsWith(".avi")||lp.endsWith(".mov")||lp.endsWith(".mkv");
        if (!isVideo) {
            originalImage = new Image("file:"+path);
            previewView.setImage(originalImage);
        }
        runAnalysis(path);
    }

    private void runAnalysis(String path) {
        progressBar.setVisible(true); progressBar.setProgress(-1);
        Task<AnalysisResult> task = engine.createAnalysisTask(path, msg->Platform.runLater(()->statusLabel.setText(msg)));

        task.progressProperty().addListener((o,ov,nv)->Platform.runLater(()->progressBar.setProgress(nv.doubleValue())));
        task.messageProperty().addListener((o,ov,nv)->Platform.runLater(()->statusLabel.setText(nv)));

        task.setOnSucceeded(e->Platform.runLater(()->{
            lastResult = task.getValue();
            // Load ELA image if available
            if (lastResult.getElaHeatmapPng()!=null) {
                elaImage = new Image(new ByteArrayInputStream(lastResult.getElaHeatmapPng()));
            }
            resultsPanel.updateResults(lastResult);
            refreshFaceBoxes();
            progressBar.setProgress(1.0); progressBar.setVisible(false);
            statusLabel.setText("✓ 分析完成  |  "+new File(path).getName());
        }));
        task.setOnFailed(e->Platform.runLater(()->{
            progressBar.setVisible(false);
            statusLabel.setText("✗ 分析失败: "+task.getException().getMessage());
        }));
        executor.submit(task);
    }

    private Button btn(String text, String color) {
        Button b=new Button(text);
        b.setFont(Font.font("Arial",FontWeight.BOLD,13));
        b.setStyle(String.format("-fx-background-color:#252836;-fx-text-fill:%s;-fx-background-radius:6;-fx-padding:7 14;",color));
        b.setOnMouseEntered(e->b.setStyle(b.getStyle().replace("#252836","#2e3245")));
        b.setOnMouseExited(e-> b.setStyle(b.getStyle().replace("#2e3245","#252836")));
        return b;
    }
}