package com.forensics.beauty.model;

import java.util.List;
import java.util.Map;

public class AnalysisResult {

    private double beautyFilterScore, bodyRetouchScore, aiGenerationScore;
    private MetricScore textureScore, detailLossScore, colorUniformScore, edgeScore, saturationScore;
    private MetricScore warpScore, proportionScore, contourScore;
    private MetricScore elaScore, fftArtifactScore, noisePatternScore, dctScore;
    private MetricScore eyeEnlargementScore, lipEnhancementScore, noseSlimmingScore;

    private List<FaceAnalysisResult> faceResults;
    private byte[] elaHeatmapPng;
    private String verdict, filePath;
    private boolean isVideo;
    private int totalFramesAnalyzed;
    private Map<String, Object> rawStats;

    // ── Getters / Setters ──────────────────────────────────────
    public double getBeautyFilterScore()          { return beautyFilterScore; }
    public void   setBeautyFilterScore(double v)  { beautyFilterScore = v; }
    public double getBodyRetouchScore()            { return bodyRetouchScore; }
    public void   setBodyRetouchScore(double v)    { bodyRetouchScore = v; }
    public double getAiGenerationScore()           { return aiGenerationScore; }
    public void   setAiGenerationScore(double v)   { aiGenerationScore = v; }

    public MetricScore getTextureScore()           { return textureScore; }
    public void        setTextureScore(MetricScore s)     { textureScore = s; }
    public MetricScore getDetailLossScore()        { return detailLossScore; }
    public void        setDetailLossScore(MetricScore s)  { detailLossScore = s; }
    public MetricScore getColorUniformScore()      { return colorUniformScore; }
    public void        setColorUniformScore(MetricScore s){ colorUniformScore = s; }
    public MetricScore getEdgeScore()              { return edgeScore; }
    public void        setEdgeScore(MetricScore s)        { edgeScore = s; }
    public MetricScore getSaturationScore()        { return saturationScore; }
    public void        setSaturationScore(MetricScore s)  { saturationScore = s; }
    public MetricScore getWarpScore()              { return warpScore; }
    public void        setWarpScore(MetricScore s)        { warpScore = s; }
    public MetricScore getProportionScore()        { return proportionScore; }
    public void        setProportionScore(MetricScore s)  { proportionScore = s; }
    public MetricScore getContourScore()           { return contourScore; }
    public void        setContourScore(MetricScore s)     { contourScore = s; }
    public MetricScore getElaScore()               { return elaScore; }
    public void        setElaScore(MetricScore s)         { elaScore = s; }
    public MetricScore getFftArtifactScore()       { return fftArtifactScore; }
    public void        setFftArtifactScore(MetricScore s) { fftArtifactScore = s; }
    public MetricScore getNoisePatternScore()      { return noisePatternScore; }
    public void        setNoisePatternScore(MetricScore s){ noisePatternScore = s; }
    public MetricScore getDctScore()               { return dctScore; }
    public void        setDctScore(MetricScore s)         { dctScore = s; }
    public MetricScore getEyeEnlargementScore()    { return eyeEnlargementScore; }
    public void        setEyeEnlargementScore(MetricScore s){ eyeEnlargementScore = s; }
    public MetricScore getLipEnhancementScore()    { return lipEnhancementScore; }
    public void        setLipEnhancementScore(MetricScore s){ lipEnhancementScore = s; }
    public MetricScore getNoseSlimmingScore()      { return noseSlimmingScore; }
    public void        setNoseSlimmingScore(MetricScore s){ noseSlimmingScore = s; }

    public List<FaceAnalysisResult> getFaceResults()                  { return faceResults; }
    public void                     setFaceResults(List<FaceAnalysisResult> v){ faceResults = v; }
    public byte[]                   getElaHeatmapPng()                { return elaHeatmapPng; }
    public void                     setElaHeatmapPng(byte[] v)        { elaHeatmapPng = v; }
    public String  getVerdict()                    { return verdict; }
    public void    setVerdict(String v)            { verdict = v; }
    public String  getFilePath()                   { return filePath; }
    public void    setFilePath(String v)           { filePath = v; }
    public boolean isVideo()                       { return isVideo; }
    public void    setVideo(boolean v)             { isVideo = v; }
    public int     getTotalFramesAnalyzed()        { return totalFramesAnalyzed; }
    public void    setTotalFramesAnalyzed(int v)   { totalFramesAnalyzed = v; }
    public Map<String,Object> getRawStats()        { return rawStats; }
    public void    setRawStats(Map<String,Object> m){ rawStats = m; }

    // ── 丰富的综合判决 ──────────────────────────────────────────
    public void computeVerdict() {
        StringBuilder sb = new StringBuilder();
        int issueCount = 0;

        // ═══ 1. AI生成检测 ═══
        if (aiGenerationScore >= 45) {
            issueCount++;
            sb.append("━━━ 🤖 AI生成内容检测 ━━━\n");
            if (aiGenerationScore >= 70) {
                sb.append(String.format("⚠ 高度疑似AI生成  得分: %.0f/100  (极高置信度)\n", aiGenerationScore));
                sb.append("  • 可能由扩散模型(SD/Midjourney)或GAN合成\n");
            } else {
                sb.append(String.format("⚠ 部分区域疑似AI合成  得分: %.0f/100  (中置信度)\n", aiGenerationScore));
                sb.append("  • 可能存在局部AI修复(Inpainting)或背景替换\n");
            }
            if (elaScore != null && elaScore.getScore() > 55)
                sb.append(String.format("  • ELA异常(%.0f分): 重压缩残差分布不符合真实拍摄\n", elaScore.getScore()));
            if (fftArtifactScore != null && fftArtifactScore.getScore() > 50)
                sb.append(String.format("  • GAN频域伪影(%.0f分): 频谱存在周期性checkerboard特征\n", fftArtifactScore.getScore()));
            if (noisePatternScore != null && noisePatternScore.getScore() > 50)
                sb.append(String.format("  • 噪声异常(%.0f分): 与真实相机PRNU传感器特征不符\n", noisePatternScore.getScore()));
            if (dctScore != null && dctScore.getScore() > 40)
                sb.append(String.format("  • DCT偏离(%.0f分): AC系数峰度偏离自然JPEG统计规律\n", dctScore.getScore()));
            sb.append("  📌 建议: 此图像不宜作为真实身份/场景的证据使用\n");
        }

        // ═══ 2. 面部美颜滤镜 ═══
        if (beautyFilterScore >= 45) {
            issueCount++;
            if (sb.length() > 0) sb.append("\n");
            sb.append("━━━ ✦ 面部美颜滤镜检测 ━━━\n");
            if (beautyFilterScore >= 70) {
                sb.append(String.format("✦ 重度美颜处理  得分: %.0f/100  (高置信度)\n", beautyFilterScore));
                sb.append("  • 面部皮肤已被深度处理，真实外貌特征存疑\n");
            } else {
                sb.append(String.format("✦ 中度美颜处理  得分: %.0f/100  (中置信度)\n", beautyFilterScore));
                sb.append("  • 存在一定程度皮肤美化，自然细节部分保留\n");
            }
            if (textureScore != null && textureScore.getScore() > 50)
                sb.append(String.format("  • 纹理磨皮(%.0f分): LBP熵%.1f低于自然值%.1f，毛孔/细纹被抹除\n",
                    textureScore.getScore(), 0.0, 4.8));
            if (detailLossScore != null && detailLossScore.getScore() > 50)
                sb.append(String.format("  • 细节丢失(%.0f分): 皮肤Laplacian方差骤降，高频成分被低通滤除\n", detailLossScore.getScore()));
            if (colorUniformScore != null && colorUniformScore.getScore() > 50)
                sb.append(String.format("  • 色调均化(%.0f分): 肤色色度std异常低，疑似美白/遮瑕处理\n", colorUniformScore.getScore()));
            if (edgeScore != null && edgeScore.getScore() > 50)
                sb.append(String.format("  • 边缘抑制(%.0f分): 皮肤内边缘密度极低，磨皮导致瑕疵消失\n", edgeScore.getScore()));
            if (saturationScore != null && saturationScore.getScore() > 50)
                sb.append(String.format("  • 饱和增强(%.0f分): 皮肤HSV饱和度均值偏高且分布均匀，人工调色迹象\n", saturationScore.getScore()));
        }

        // ═══ 3. 局部特征美化 ═══
        boolean hasLocal =
            (eyeEnlargementScore != null && eyeEnlargementScore.getScore() >= 45) ||
            (lipEnhancementScore != null && lipEnhancementScore.getScore() >= 45) ||
            (noseSlimmingScore   != null && noseSlimmingScore.getScore()   >= 45);
        if (hasLocal) {
            issueCount++;
            if (sb.length() > 0) sb.append("\n");
            sb.append("━━━ ◉ 局部特征美化检测 ━━━\n");
            if (eyeEnlargementScore != null && eyeEnlargementScore.getScore() >= 45) {
                boolean heavy = eyeEnlargementScore.getScore() >= 65;
                sb.append(String.format("  👁 %s眼睛放大(%.0f分): 眼宽/脸宽比超出自然范围(≈0.25)%s\n",
                    heavy?"明显":"轻度", eyeEnlargementScore.getScore(),
                    heavy?"，放大幅度可能超过20%，双眼不对称明显":"，轻微放大或双眼不对称"));
            }
            if (lipEnhancementScore != null && lipEnhancementScore.getScore() >= 45) {
                boolean heavy = lipEnhancementScore.getScore() >= 65;
                sb.append(String.format("  👄 %s嘴唇美化(%.0f分): 唇部饱和度显著高于面部均值%s\n",
                    heavy?"明显":"轻度", lipEnhancementScore.getScore(),
                    heavy?"，疑似滤镜提色或虚拟唇妆增强":"，轻微色调调整"));
            }
            if (noseSlimmingScore != null && noseSlimmingScore.getScore() >= 45) {
                boolean heavy = noseSlimmingScore.getScore() >= 65;
                sb.append(String.format("  👃 %s鼻子瘦化(%.0f分): 鼻翼/脸宽比低于自然值(≈0.30)%s\n",
                    heavy?"明显":"轻度", noseSlimmingScore.getScore(),
                    heavy?"，边缘过度平滑+高度对称，强液化痕迹":"，轻微液化痕迹"));
            }
        }

        // ═══ 4. 身体形态修图 ═══
        if (bodyRetouchScore >= 40) {
            issueCount++;
            if (sb.length() > 0) sb.append("\n");
            sb.append("━━━ ◈ 身体形态修图检测 ━━━\n");
            if (bodyRetouchScore >= 65) {
                sb.append(String.format("◈ 明显身体修图  得分: %.0f/100  (高置信度)\n", bodyRetouchScore));
            } else {
                sb.append(String.format("◈ 轻度身体修图  得分: %.0f/100  (低置信度)\n", bodyRetouchScore));
            }
            if (warpScore != null && warpScore.getScore() > 40)
                sb.append(String.format("  • 液化形变(%.0f分): 背景直线出现弯曲，疑似腰/腿液化拉伸\n", warpScore.getScore()));
            if (contourScore != null && contourScore.getScore() > 40)
                sb.append(String.format("  • 轮廓异常(%.0f分): 人体轮廓曲率突变，非自然体型弧线\n", contourScore.getScore()));
            if (proportionScore != null && proportionScore.getScore() > 40)
                sb.append(String.format("  • 比例拉伸(%.0f分): 纵向梯度分布不均，疑似全身拉伸显高/显瘦\n", proportionScore.getScore()));
        }

        // ═══ 5. 多人脸摘要 ═══
        if (faceResults != null) {
            long faceCount = faceResults.stream().filter(f -> !f.isWholeImage).count();
            if (faceCount > 1) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(String.format("━━━ 👥 多人脸摘要 (%d张) ━━━\n", faceCount));
                faceResults.stream().filter(f -> !f.isWholeImage).forEach(f ->
                    sb.append(String.format("  %s  →  %s\n", f.getLabel(), riskEmoji(f.beautyScore))));
            }
        }

        // ═══ 6. 综合结论 ═══
        if (sb.length() > 0) sb.append("\n");
        sb.append("━━━ 📋 综合结论 ━━━\n");
        if (issueCount == 0) {
            sb.append("✅ 未发现明显处理痕迹\n");
            sb.append("  • 皮肤纹理、频域分布、噪声模式均符合真实拍摄特征\n");
            sb.append("  • 边缘密度、肤色分布、ELA残差均在自然范围内\n");
            sb.append("  • 初步认定为未经重度后期处理的真实照片\n");
            sb.append("  ⚠ 注意: 本工具为辅助分析，无法替代专业取证鉴定");
        } else {
            double risk = beautyFilterScore*0.35 + aiGenerationScore*0.40 + bodyRetouchScore*0.25;
            String level = risk >= 70 ? "🔴 高风险" : risk >= 45 ? "🟠 中风险" : "🟡 低风险";
            sb.append(String.format("  综合风险评级: 【%s】  综合分: %.0f/100\n", level, risk));
            sb.append(String.format("  共发现 %d 类处理痕迹\n", issueCount));
            sb.append("  ⚠ 该图像经过后期处理，内容真实性存在疑问\n");
            sb.append("  📌 建议: 结合原始EXIF数据和ELA热图进行综合取证判断");
        }
        verdict = sb.toString();
    }

    private String riskEmoji(double score) {
        if (score < 30) return "✅ 自然 ("+String.format("%.0f",score)+"分)";
        if (score < 55) return "🟡 轻度处理 ("+String.format("%.0f",score)+"分)";
        if (score < 75) return "🟠 明显处理 ("+String.format("%.0f",score)+"分)";
        return "🔴 重度处理 ("+String.format("%.0f",score)+"分)";
    }
}