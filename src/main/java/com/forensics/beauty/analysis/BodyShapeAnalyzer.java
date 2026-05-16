package com.forensics.beauty.analysis;

import com.forensics.beauty.model.MetricScore;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.*;

/**
 * 身体形态修图检测
 *
 * 检测维度:
 *  1. 边缘直线度偏差 — 液化/瘦身导致背景线条弯曲
 *  2. 局部梯度异常 — 形变区域梯度方向突变
 *  3. 轮廓凹凸分析 — 腰臀轮廓不自然
 *  4. JPEG块对齐 — 修图工具留下的块边界
 */
public class BodyShapeAnalyzer {

    public Map<String, MetricScore> analyze(Mat bgrImage) {
        Map<String, MetricScore> results = new LinkedHashMap<>();
        results.put("warp",       detectLocalWarp(bgrImage));
        results.put("contour",    analyzeBodyContour(bgrImage));
        results.put("proportion", checkProportionAnomalies(bgrImage));
        return results;
    }

    // ─────────────────────────────────────────────
    // 液化/形变检测：背景线条弯曲度分析
    // 原理: 对图像做直线检测（HoughLines），
    //       统计"近直线"的弯曲偏差
    //       修图导致直线变弯，弯曲度增大
    // ─────────────────────────────────────────────
    private MetricScore detectLocalWarp(Mat bgrImage) {
        Mat gray = new Mat();
        Imgproc.cvtColor(bgrImage, gray, Imgproc.COLOR_BGR2GRAY);
        Mat edges = new Mat();
        Imgproc.Canny(gray, edges, 50, 150);

        // 概率Hough直线检测
        Mat lines = new Mat();
        Imgproc.HoughLinesP(edges, lines, 1, Math.PI / 180, 80, 100, 20);

        // 分析长线段的偏差（理论应为直线，修图后会弯曲）
        // 简化: 用相邻线段角度一致性衡量
        double warpScore = analyzeLineConsistency(lines, bgrImage);

        gray.release(); edges.release(); lines.release();
        return new MetricScore("局部形变检测", warpScore,
            String.format("背景线条弯曲度分析，得分=%.1f，%s",
                warpScore, warpScore > 50 ? "检测到液化形变痕迹" : "形态自然"));
    }

    private double analyzeLineConsistency(Mat lines, Mat image) {
        if (lines.rows() == 0) return 10;

        List<Double> angles = new ArrayList<>();
        for (int i = 0; i < lines.rows(); i++) {
            double[] pts = lines.get(i, 0);
            if (pts == null) continue;
            double dx = pts[2] - pts[0];
            double dy = pts[3] - pts[1];
            angles.add(Math.atan2(dy, dx) * 180 / Math.PI);
        }

        if (angles.size() < 2) return 5;

        // 计算角度分布的双峰性（正常图像线条多为水平/垂直）
        // 修图后原本直线的线条会呈现角度散乱
        Collections.sort(angles);
        double mean = angles.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double var  = angles.stream().mapToDouble(a -> (a-mean)*(a-mean)).average().orElse(0);

        // 高方差 + 多条短线（非长线）= 形变迹象
        double normalizedVar = Math.min(1.0, var / 2000.0);
        return normalizedVar * 60;
    }

    // ─────────────────────────────────────────────
    // 身体轮廓分析
    // 原理: 提取人体轮廓，检测凹陷异常（腰部过度收缩等）
    // ─────────────────────────────────────────────
    private MetricScore analyzeBodyContour(Mat bgrImage) {
        Mat gray = new Mat();
        Imgproc.cvtColor(bgrImage, gray, Imgproc.COLOR_BGR2GRAY);

        // 自适应阈值提取人体轮廓
        Mat thresh = new Mat();
        Imgproc.adaptiveThreshold(gray, thresh, 255,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 21, 10);

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(thresh, contours, hierarchy,
            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // 找最大轮廓（假设为人体）
        double score = 0;
        if (!contours.isEmpty()) {
            MatOfPoint largest = contours.stream()
                .max(Comparator.comparingDouble(c -> Imgproc.contourArea(c)))
                .orElse(null);
            if (largest != null && Imgproc.contourArea(largest) > 5000) {
                score = computeContourSmoothness(largest, bgrImage);
            }
        }

        gray.release(); thresh.release(); hierarchy.release();
        contours.forEach(Mat::release);

        return new MetricScore("轮廓平滑异常", score,
            String.format("轮廓分析得分=%.1f，%s",
                score, score > 55 ? "轮廓存在异常凹凸变化" : "轮廓形态自然"));
    }

    private double computeContourSmoothness(MatOfPoint contour, Mat image) {
        Point[] pts = contour.toArray();
        if (pts.length < 20) return 0;

        // 计算轮廓局部曲率的方差
        List<Double> curvatures = new ArrayList<>();
        for (int i = 5; i < pts.length - 5; i++) {
            Point p1 = pts[i-5], p2 = pts[i], p3 = pts[i+5];
            double curvature = computeCurvature(p1, p2, p3);
            curvatures.add(curvature);
        }

        double mean = curvatures.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double var  = curvatures.stream().mapToDouble(c -> (c-mean)*(c-mean)).average().orElse(0);

        // 高曲率方差 + 极端曲率点 = 异常轮廓
        long extremePts = curvatures.stream().filter(c -> Math.abs(c) > mean + 2*Math.sqrt(var)).count();
        double extremeRatio = (double) extremePts / curvatures.size();

        return Math.min(100, extremeRatio * 200 + Math.sqrt(var) * 0.5);
    }

    private double computeCurvature(Point p1, Point p2, Point p3) {
        double ax = p2.x - p1.x, ay = p2.y - p1.y;
        double bx = p3.x - p2.x, by = p3.y - p2.y;
        double cross = ax*by - ay*bx;
        double magA  = Math.sqrt(ax*ax + ay*ay);
        double magB  = Math.sqrt(bx*bx + by*by);
        if (magA * magB < 1e-6) return 0;
        return cross / (magA * magB);
    }

    // ─────────────────────────────────────────────
    // 比例异常检测
    // 原理: 分析图像宽高比与已知美颜拉伸特征
    //       检测垂直拉伸（显腿长）、水平收缩（显瘦）
    // ─────────────────────────────────────────────
    private MetricScore checkProportionAnomalies(Mat bgrImage) {
        // 分析图像的局部形变：对比上下/左右区域的梯度方向
        Mat gray = new Mat();
        Imgproc.cvtColor(bgrImage, gray, Imgproc.COLOR_BGR2GRAY);

        Mat gradX = new Mat(), gradY = new Mat();
        Imgproc.Sobel(gray, gradX, CvType.CV_32F, 1, 0);
        Imgproc.Sobel(gray, gradY, CvType.CV_32F, 0, 1);

        // 在垂直方向分段，比较梯度比例（检测拉伸）
        int segments = 6;
        int segHeight = gray.rows() / segments;
        double[] yGradRatios = new double[segments - 1];

        for (int i = 0; i < segments && (i+1) * segHeight <= gray.rows(); i++) {
            Mat segX = new Mat(gradX, new Rect(0, i*segHeight, gray.cols(), segHeight));
            Mat segY = new Mat(gradY, new Rect(0, i*segHeight, gray.cols(), segHeight));
            MatOfDouble mx = new MatOfDouble(), sx = new MatOfDouble();
            MatOfDouble my = new MatOfDouble(), sy = new MatOfDouble();
            Core.meanStdDev(segX, mx, sx);
            Core.meanStdDev(segY, my, sy);
            if (i > 0 && sy.get(0,0)[0] > 0.01) {
                yGradRatios[i-1] = sx.get(0,0)[0] / sy.get(0,0)[0];
            }
            segX.release(); segY.release();
        }

        // 计算比例异常（理想情况下各段比例相近）
        OptionalDouble mean = Arrays.stream(yGradRatios).average();
        double var = Arrays.stream(yGradRatios)
            .map(r -> (r - mean.orElse(1)) * (r - mean.orElse(1)))
            .average().orElse(0);

        double score = Math.min(100, Math.sqrt(var) * 30);

        gray.release(); gradX.release(); gradY.release();
        return new MetricScore("比例拉伸检测", score,
            String.format("梯度比例方差=%.4f，%s",
                var, score > 45 ? "检测到垂直/水平拉伸痕迹" : "比例未见异常"));
    }
}