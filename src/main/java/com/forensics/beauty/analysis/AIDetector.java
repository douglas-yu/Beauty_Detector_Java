package com.forensics.beauty.analysis;

import com.forensics.beauty.model.MetricScore;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

public class AIDetector {

    private static final int    ELA_QUALITY  = 75;
    private static final double GAN_FREQ_THR = 0.15;

    public Map<String, MetricScore> analyze(Mat bgrImage, String originalFilePath) {
        Map<String, MetricScore> r = new LinkedHashMap<>();
        r.put("ela",   performELA(bgrImage, originalFilePath));
        r.put("fft",   detectFFTArtifacts(bgrImage));
        r.put("noise", analyzeNoisePattern(bgrImage));
        r.put("dct",   analyzeDCTDistribution(bgrImage));
        return r;
    }

    // ── NEW: ELA colorized heatmap as PNG bytes ──
    public byte[] computeELAHeatmapPng(Mat bgrImage) {
        try {
            // Re-encode at quality 75
            MatOfInt params = new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, ELA_QUALITY);
            MatOfByte jpegBuf = new MatOfByte();
            Imgcodecs.imencode(".jpg", bgrImage, jpegBuf, params);
            Mat recomp = Imgcodecs.imdecode(jpegBuf, Imgcodecs.IMREAD_COLOR);
            if (recomp.empty()) return null;

            // Absolute difference
            Mat diff = new Mat();
            Core.absdiff(bgrImage, recomp, diff);

            // Amplify x15 for visibility
            Core.multiply(diff, new Scalar(15, 15, 15), diff);

            // Grayscale → COLORMAP_JET
            Mat gray = new Mat();
            Imgproc.cvtColor(diff, gray, Imgproc.COLOR_BGR2GRAY);
            Mat heatmap = new Mat();
            Imgproc.applyColorMap(gray, heatmap, Imgproc.COLORMAP_JET);

            // Blend: heatmap 65% + original 35%
            Mat blended = new Mat();
            Core.addWeighted(heatmap, 0.65, bgrImage, 0.35, 0, blended);

            // Encode to PNG bytes
            MatOfByte out = new MatOfByte();
            Imgcodecs.imencode(".png", blended, out);
            byte[] result = out.toArray();

            recomp.release(); diff.release(); gray.release();
            heatmap.release(); blended.release();
            return result;
        } catch (Exception e) {
            System.err.println("ELA heatmap error: " + e.getMessage());
            return null;
        }
    }

    private MetricScore performELA(Mat bgrImage, String filePath) {
        try {
            BufferedImage original = matToBufferedImage(bgrImage);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            javax.imageio.ImageWriteParam param =
                ImageIO.getImageWritersByFormatName("jpeg").next().getDefaultWriteParam();
            param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(ELA_QUALITY / 100.0f);
            javax.imageio.stream.ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
            javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(original, null, null), param);
            ios.flush(); writer.dispose(); ios.close();
            BufferedImage recompressed = ImageIO.read(new ByteArrayInputStream(baos.toByteArray()));
            double elaVar = computeELAVariance(original, recompressed);
            double score;
            if      (elaVar < 500)  score = Math.min(100,(1.0-elaVar/500.0)*85+15);
            else if (elaVar > 8000) score = Math.min(100,(elaVar-8000)/5000.0*60+40);
            else                    score = Math.max(0,20-(elaVar-500)/375.0*5);
            return new MetricScore("ELA误差分析",score,String.format("残差方差=%.1f",elaVar));
        } catch (Exception e) {
            return new MetricScore("ELA误差分析",0,"ELA分析失败: "+e.getMessage());
        }
    }

    private double computeELAVariance(BufferedImage o, BufferedImage r) {
        DescStats s = new DescStats();
        int w=Math.min(o.getWidth(),r.getWidth()), h=Math.min(o.getHeight(),r.getHeight());
        for(int y=0;y<h;y+=2) for(int x=0;x<w;x+=2) { // sample every other pixel for speed
            int c1=o.getRGB(x,y), c2=r.getRGB(x,y);
            int dr=((c1>>16)&0xFF)-((c2>>16)&0xFF);
            int dg=((c1>>8)&0xFF)-((c2>>8)&0xFF);
            int db=(c1&0xFF)-(c2&0xFF);
            s.add(Math.sqrt(dr*dr+dg*dg+db*db));
        }
        return s.variance();
    }

    private MetricScore detectFFTArtifacts(Mat bgrImage) {
        Mat gray=new Mat(); Imgproc.cvtColor(bgrImage,gray,Imgproc.COLOR_BGR2GRAY);
        Mat flt=new Mat(); gray.convertTo(flt,CvType.CV_32F);
        Mat dft=new Mat(); Core.dft(flt,dft,Core.DFT_COMPLEX_OUTPUT);
        List<Mat> pl=new ArrayList<>(); Core.split(dft,pl);
        Mat mag=new Mat(); Core.magnitude(pl.get(0),pl.get(1),mag);
        Core.add(mag,Scalar.all(1),mag); Core.log(mag,mag);
        double periodicity=detectPeriodicPeaks(mag);
        double score=Math.min(100,periodicity*100);
        gray.release(); flt.release(); dft.release(); mag.release(); pl.forEach(Mat::release);
        return new MetricScore("频域GAN伪影",score,String.format("周期性峰值=%.4f",periodicity));
    }

    private double detectPeriodicPeaks(Mat mag) {
        DescStats s=new DescStats();
        int cx=mag.cols()/2,cy=mag.rows()/2,skip=Math.min(cx,cy)/6;
        for(int y=0;y<mag.rows();y++) for(int x=0;x<mag.cols();x++)
            if(Math.sqrt((x-cx)*(x-cx)+(y-cy)*(y-cy))>skip) s.add(mag.get(y,x)[0]);
        return Math.max(0,Math.min(1,(s.kurtosis()-2.0)/8.0));
    }

    private MetricScore analyzeNoisePattern(Mat bgrImage) {
        Mat gray=new Mat(); Imgproc.cvtColor(bgrImage,gray,Imgproc.COLOR_BGR2GRAY);
        gray.convertTo(gray,CvType.CV_32F);
        Mat denoised=new Mat();
        Imgproc.GaussianBlur(bgrImage,denoised,new Size(5,5),1.5); // fast vs fastNlMeans
        Mat dg=new Mat(); Imgproc.cvtColor(denoised,dg,Imgproc.COLOR_BGR2GRAY);
        dg.convertTo(dg,CvType.CV_32F);
        Mat noise=new Mat(); Core.subtract(gray,dg,noise);
        MatOfDouble m=new MatOfDouble(),s=new MatOfDouble();
        Core.meanStdDev(noise,m,s);
        double noiseStd=s.get(0,0)[0];
        double autocorr=computeAutocorr(noise);
        double score=Math.min(100,Math.max(0,(2.0-noiseStd)/2.0*50)+Math.max(0,(autocorr-0.05)/0.45*50));
        gray.release(); denoised.release(); dg.release(); noise.release();
        return new MetricScore("噪声模式分析",score,String.format("噪声std=%.3f 自相关=%.4f",noiseStd,autocorr));
    }

    private double computeAutocorr(Mat noise) {
        int rows=noise.rows(),cols=noise.cols(); if(rows<3||cols<3) return 0;
        double sum=0; int cnt=0;
        for(int y=0;y<rows-1;y++) for(int x=0;x<cols-1;x++){
            double v1=noise.get(y,x)[0],v2=noise.get(y,x+1)[0],v3=noise.get(y+1,x)[0];
            if(v1!=0&&v2!=0&&v3!=0){sum+=(v1*v2+v1*v3)/2.0;cnt++;}
        }
        return cnt>0?Math.abs(sum/cnt)/100.0:0;
    }

    private MetricScore analyzeDCTDistribution(Mat bgrImage) {
        Mat resized=new Mat(); Imgproc.resize(bgrImage,resized,new Size(512,512));
        Mat gray=new Mat(); Imgproc.cvtColor(resized,gray,Imgproc.COLOR_BGR2GRAY);
        resized.release();
        gray.convertTo(gray,CvType.CV_32F);
        DescStats s=new DescStats();
        int bs=8;
        for(int y=0;y+bs<=gray.rows();y+=bs) for(int x=0;x+bs<=gray.cols();x+=bs){
            Mat block=new Mat(gray,new Rect(x,y,bs,bs));
            Mat dct=new Mat(); Core.dct(block,dct);
            for(int by=0;by<bs;by++) for(int bx=0;bx<bs;bx++)
                if(by!=0||bx!=0) s.add(dct.get(by,bx)[0]);
            block.release(); dct.release();
        }
        gray.release();
        double k=s.kurtosis();
        double score = k<1.2 ? Math.min(100,(1.2-k)/1.2*70+30)
                     : k>6.0 ? Math.min(100,(k-6.0)/4.0*50+20)
                     : Math.max(0,15-(k-2.5)*5);
        return new MetricScore("DCT系数分布",score,String.format("AC峰度=%.3f",k));
    }

    private BufferedImage matToBufferedImage(Mat mat) throws Exception {
        MatOfByte mob=new MatOfByte();
        Imgcodecs.imencode(".png",mat,mob);
        return ImageIO.read(new ByteArrayInputStream(mob.toArray()));
    }

    private static class DescStats {
        private final List<Double> d=new ArrayList<>();
        void add(double v){d.add(v);}
        double variance(){
            if(d.isEmpty()) return 0;
            double m=d.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            return d.stream().mapToDouble(v->(v-m)*(v-m)).average().orElse(0);
        }
        double kurtosis(){
            if(d.size()<4) return 3;
            double m=d.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double var=variance(); if(var<1e-10) return 0;
            double m4=d.stream().mapToDouble(v->Math.pow(v-m,4)).average().orElse(0);
            return m4/(var*var);
        }
    }
}