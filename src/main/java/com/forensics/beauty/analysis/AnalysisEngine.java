package com.forensics.beauty.analysis;

import com.forensics.beauty.model.AnalysisResult;
import com.forensics.beauty.model.FaceAnalysisResult;
import com.forensics.beauty.model.MetricScore;
import javafx.concurrent.Task;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import java.util.*;

public class AnalysisEngine {

    private final FaceSkinAnalyzer faceSkinAnalyzer = new FaceSkinAnalyzer();
    private final BodyShapeAnalyzer bodyShapeAnalyzer = new BodyShapeAnalyzer();
    private final AIDetector aiDetector = new AIDetector();
    private final VideoProcessor videoProcessor = new VideoProcessor();

    public Task<AnalysisResult> createAnalysisTask(String filePath,
                                                    java.util.function.Consumer<String> cb) {
        return new Task<>() {
            @Override protected AnalysisResult call() throws Exception {
                updateProgress(0,100);
                AnalysisResult result = new AnalysisResult();
                result.setFilePath(filePath);
                result.setVideo(isVideoFile(filePath));
                return result.isVideo() ? analyzeVideo(filePath, result)
                                        : analyzeImage(filePath, result);
            }

            private AnalysisResult analyzeImage(String path, AnalysisResult result) throws Exception {
                updateMessage("加载图像..."); updateProgress(5,100);
                Mat image = Imgcodecs.imread(path);
                if (image.empty()) throw new Exception("无法加载: " + path);

                updateMessage("多人脸检测与分析..."); updateProgress(15,100);
                List<FaceAnalysisResult> faceResults = faceSkinAnalyzer.analyzeAllFaces(image);
                result.setFaceResults(faceResults);

                // Aggregate from worst (highest scored) face
                Map<String,MetricScore> bestFace = faceResults.stream()
                    .max(Comparator.comparingDouble(f -> f.beautyScore))
                    .map(f -> f.metrics).orElse(new HashMap<>());

                updateMessage("身体形态检测..."); updateProgress(55,100);
                Map<String,MetricScore> bodyMetrics = bodyShapeAnalyzer.analyze(image);

                updateMessage("AI生成检测..."); updateProgress(68,100);
                Map<String,MetricScore> aiMetrics = aiDetector.analyze(image, path);

                updateMessage("生成ELA热图..."); updateProgress(80,100);
                result.setElaHeatmapPng(aiDetector.computeELAHeatmapPng(image));

                updateMessage("计算综合评分..."); updateProgress(90,100);
                aggregateResults(result, bestFace, bodyMetrics, aiMetrics);
                result.setFaceResults(faceResults); // restore after aggregate
                result.setTotalFramesAnalyzed(1);

                updateMessage("分析完成 — 检测到" + faceResults.stream()
                    .filter(f->!f.isWholeImage).count() + "张人脸");
                updateProgress(100,100);
                image.release();
                return result;
            }

            private AnalysisResult analyzeVideo(String path, AnalysisResult result) throws Exception {
                updateMessage("打开视频..."); updateProgress(5,100);
                return videoProcessor.analyzeVideo(path, result,
                    (prog,msg)->{updateProgress(prog,100); updateMessage(msg);},
                    faceSkinAnalyzer, bodyShapeAnalyzer, aiDetector,
                    AnalysisEngine.this::aggregateResults);
            }
        };
    }

    void aggregateResults(AnalysisResult r,
                          Map<String,MetricScore> face,
                          Map<String,MetricScore> body,
                          Map<String,MetricScore> ai) {
        MetricScore tex=g(face,"texture","皮肤纹理平滑度"), det=g(face,"detail","高频细节丢失");
        MetricScore fft=g(face,"fft","频域低通滤波"),      col=g(face,"color","肤色均匀度");
        MetricScore edg=g(face,"edge","边缘/残差抑制"),     sat=g(face,"saturation","饱和度增强");
        MetricScore eye=g(face,"eye","眼睛放大检测"),       lip=g(face,"lip","嘴唇美化检测");
        MetricScore nos=g(face,"nose","鼻子瘦化检测");

        r.setTextureScore(tex); r.setDetailLossScore(det); r.setColorUniformScore(col);
        r.setEdgeScore(edg);    r.setSaturationScore(sat);
        r.setEyeEnlargementScore(eye); r.setLipEnhancementScore(lip); r.setNoseSlimmingScore(nos);

        r.setBeautyFilterScore(Math.min(100,
            tex.getScore()*0.18+det.getScore()*0.18+col.getScore()*0.14+
            edg.getScore()*0.12+sat.getScore()*0.08+fft.getScore()*0.05+
            eye.getScore()*0.13+lip.getScore()*0.07+nos.getScore()*0.05));

        MetricScore warp=g(body,"warp","局部形变"), cont=g(body,"contour","轮廓异常"), prop=g(body,"proportion","比例拉伸");
        r.setWarpScore(warp); r.setContourScore(cont); r.setProportionScore(prop);
        r.setBodyRetouchScore(Math.min(100,warp.getScore()*0.4+cont.getScore()*0.35+prop.getScore()*0.25));

        MetricScore ela=g(ai,"ela","ELA误差"), fftAI=g(ai,"fft","GAN伪影"), noise=g(ai,"noise","噪声模式"), dct=g(ai,"dct","DCT分布");
        r.setElaScore(ela); r.setFftArtifactScore(fftAI); r.setNoisePatternScore(noise); r.setDctScore(dct);
        r.setAiGenerationScore(Math.min(100,ela.getScore()*0.30+fftAI.getScore()*0.30+noise.getScore()*0.25+dct.getScore()*0.15));

        r.computeVerdict();
    }

    private MetricScore g(Map<String,MetricScore> m, String k, String name) {
        return m.getOrDefault(k, new MetricScore(name,0,"N/A"));
    }

    private boolean isVideoFile(String p) {
        String l=p.toLowerCase();
        return l.endsWith(".mp4")||l.endsWith(".avi")||l.endsWith(".mov")||
               l.endsWith(".mkv")||l.endsWith(".flv")||l.endsWith(".webm");
    }
}