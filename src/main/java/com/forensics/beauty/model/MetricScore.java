package com.forensics.beauty.model;

/**
 * 单项分析指标：含名称、得分(0-100)、说明
 */
public class MetricScore {
    private final String name;
    private final double score;          // 0–100，越高越"疑似处理"
    private final String description;
    private final RiskLevel risk;

    public enum RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }

    public MetricScore(String name, double score, String description) {
        this.name = name;
        this.score = Math.min(100, Math.max(0, score));
        this.description = description;
        if      (score < 30) risk = RiskLevel.LOW;
        else if (score < 55) risk = RiskLevel.MEDIUM;
        else if (score < 75) risk = RiskLevel.HIGH;
        else                 risk = RiskLevel.CRITICAL;
    }

    public String getName()        { return name; }
    public double getScore()       { return score; }
    public String getDescription() { return description; }
    public RiskLevel getRisk()     { return risk; }

    public String getRiskLabel() {
        return switch (risk) {
            case LOW      -> "自然";
            case MEDIUM   -> "轻微处理";
            case HIGH     -> "明显处理";
            case CRITICAL -> "重度处理";
        };
    }
}
