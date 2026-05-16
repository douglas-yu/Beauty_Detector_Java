package com.forensics.beauty.analysis;

import com.forensics.beauty.model.AnalysisResult;
import com.forensics.beauty.model.MetricScore;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class VideoProcessor {

    // Fix4: 减少采样帧数，视频分析从3~5分钟降至20~30秒
    private static final int MAX_FRAMES    = 8;   // 最多采样帧数
    private static final int SAMPLE_STEP   = 15;  // 每隔N帧采样1帧

    @FunctionalInterface
    interface AggregateFunction {
        void aggregate(AnalysisResult result,
                       Map<String, MetricScore> face,
                       Map<String, MetricScore> body,
                       Map<String, MetricScore> ai);
    }

    public AnalysisResult analyzeVideo(String path, AnalysisResult result,
                                        BiConsumer<Integer, String> progress,
                                        FaceSkinAnalyzer faceSkinAnalyzer,
                                        BodyShapeAnalyzer bodyShapeAnalyzer,
                                        AIDetector aiDetector,
                                        AggregateFunction aggregator) throws Exception {
        VideoCapture cap = new VideoCapture(path);
        if (!cap.isOpened()) throw new Exception("无法打开视频: " + path);

        int totalFrames = (int) cap.get(Videoio.CAP_PROP_FRAME_COUNT);
        int fps = (int) cap.get(Videoio.CAP_PROP_FPS);
        progress.accept(10, String.format("视频: %d帧, %.1ffps", totalFrames, (double)fps));

        // 收集各帧结果
        List<Map<String, MetricScore>> faceResults = new ArrayList<>();
        List<Map<String, MetricScore>> bodyResults = new ArrayList<>();
        List<Map<String, MetricScore>> aiResults   = new ArrayList<>();

        int frameIdx = 0, sampledCount = 0;
        Mat frame = new Mat();

        while (cap.read(frame) && sampledCount < MAX_FRAMES) {
            if (frameIdx % SAMPLE_STEP == 0 && !frame.empty()) {
                faceResults.add(faceSkinAnalyzer.analyze(frame));
                bodyResults.add(bodyShapeAnalyzer.analyze(frame));
                if (sampledCount % 5 == 0) { // AI检测计算量大，降频
                    aiResults.add(aiDetector.analyze(frame, path));
                }
                sampledCount++;
                int prog = 10 + (int)((double) sampledCount / MAX_FRAMES * 75);
                progress.accept(prog,
                    String.format("分析帧 %d/%d...", sampledCount, MAX_FRAMES));
            }
            frameIdx++;
        }

        cap.release();
        frame.release();
        result.setTotalFramesAnalyzed(sampledCount);

        // 对采样帧结果取平均
        aggregator.aggregate(result,
            averageMetrics(faceResults),
            averageMetrics(bodyResults),
            aiResults.isEmpty() ? new HashMap<>() : averageMetrics(aiResults));

        progress.accept(100, "视频分析完成（共采样" + sampledCount + "帧）");
        return result;
    }

    /** 对多帧的MetricScore取均值 */
    private Map<String, MetricScore> averageMetrics(List<Map<String, MetricScore>> frames) {
        if (frames.isEmpty()) return new HashMap<>();
        Map<String, List<Double>> scores = new LinkedHashMap<>();
        Map<String, String[]> meta = new LinkedHashMap<>();

        for (Map<String, MetricScore> frame : frames) {
            for (Map.Entry<String, MetricScore> e : frame.entrySet()) {
                scores.computeIfAbsent(e.getKey(), k -> new ArrayList<>())
                      .add(e.getValue().getScore());
                meta.put(e.getKey(), new String[]{
                    e.getValue().getName(),
                    e.getValue().getDescription()
                });
            }
        }

        Map<String, MetricScore> avg = new LinkedHashMap<>();
        for (Map.Entry<String, List<Double>> e : scores.entrySet()) {
            double meanScore = e.getValue().stream()
                .mapToDouble(Double::doubleValue).average().orElse(0);
            String[] m = meta.get(e.getKey());
            avg.put(e.getKey(), new MetricScore(m[0], meanScore, "[视频均值] " + m[1]));
        }
        return avg;
    }
}
