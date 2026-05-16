package com.forensics.beauty.model;
import java.util.Map;

public class FaceAnalysisResult {
    public final int index;
    public final int x, y, w, h;          // bounding box in original image coords
    public final boolean isProfile;
    public final boolean isWholeImage;     // true = no face detected, used whole image
    public final Map<String, MetricScore> metrics;
    public final double beautyScore;

    public FaceAnalysisResult(int index, int x, int y, int w, int h,
                               boolean isProfile, boolean isWholeImage,
                               Map<String, MetricScore> metrics, double beautyScore) {
        this.index = index; this.x = x; this.y = y; this.w = w; this.h = h;
        this.isProfile = isProfile; this.isWholeImage = isWholeImage;
        this.metrics = metrics; this.beautyScore = beautyScore;
    }

    public String getLabel() {
        if (isWholeImage) return "全图分析";
        return String.format("人脸 %d%s  (%.0f分)", index + 1, isProfile ? " 侧" : "", beautyScore);
    }

    @Override public String toString() { return getLabel(); }
}