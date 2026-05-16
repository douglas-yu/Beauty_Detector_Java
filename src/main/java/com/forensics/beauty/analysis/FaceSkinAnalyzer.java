package com.forensics.beauty.analysis;

import com.forensics.beauty.model.FaceAnalysisResult;
import com.forensics.beauty.model.MetricScore;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class FaceSkinAnalyzer {

    private CascadeClassifier frontalDetector, profileDetector, eyeDetector, smileDetector;

    private static final double NATURAL_LBP_ENTROPY    = 4.8;
    private static final double NATURAL_HF_RATIO        = 0.18;
    private static final double NATURAL_SKIN_COLOR_STD  = 12.0;
    private static final double NATURAL_EYE_FACE_RATIO  = 0.25;
    private static final double NATURAL_NOSE_FACE_RATIO = 0.30;
    private static final double NATURAL_LIP_SAT_DIFF    = 20.0;

    private static class DetectedFace {
        final Rect rect; final boolean isProfile;
        DetectedFace(Rect r, boolean p) { rect = r; isProfile = p; }
    }

    public FaceSkinAnalyzer() {
        frontalDetector = loadCascade("/haarcascade_frontalface_alt2.xml");
        profileDetector = loadCascade("/haarcascade_profileface.xml");
        eyeDetector     = loadCascade("/haarcascade_eye.xml");
        smileDetector   = loadCascade("/haarcascade_smile.xml");
    }

    private CascadeClassifier loadCascade(String path) {
        try {
            InputStream is = getClass().getResourceAsStream(path);
            if (is == null) { System.err.println("未找到: " + path); return new CascadeClassifier(); }
            File tmp = File.createTempFile("cc_", ".xml"); tmp.deleteOnExit();
            Files.copy(is, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
            CascadeClassifier cc = new CascadeClassifier(tmp.getAbsolutePath());
            if (cc.empty()) System.err.println("加载失败: " + path);
            return cc;
        } catch (Exception e) { return new CascadeClassifier(); }
    }

    // ── Public: multi-face analysis ──
    public List<FaceAnalysisResult> analyzeAllFaces(Mat bgrImage) {
        List<DetectedFace> detected = detectFacesCombined(bgrImage);
        List<FaceAnalysisResult> results = new ArrayList<>();

        for (int i = 0; i < detected.size(); i++) {
            DetectedFace df = detected.get(i);
            Mat roi  = new Mat(bgrImage, df.rect);
            Mat skin = extractSkinMask(roi);
            Map<String, MetricScore> m = analyzeROI(roi, skin, df.rect);
            results.add(new FaceAnalysisResult(i, df.rect.x, df.rect.y,
                df.rect.width, df.rect.height, df.isProfile, false, m, beautyScore(m)));
            roi.release(); skin.release();
        }
        if (results.isEmpty()) {
            Mat mask = new Mat(bgrImage.size(), CvType.CV_8U, Scalar.all(255));
            Rect full = new Rect(0, 0, bgrImage.cols(), bgrImage.rows());
            Map<String, MetricScore> m = analyzeROI(bgrImage, mask, full);
            mask.release();
            results.add(new FaceAnalysisResult(0, 0, 0,
                bgrImage.cols(), bgrImage.rows(), false, true, m, beautyScore(m)));
        }
        return results;
    }

    // ── Backward-compat for VideoProcessor ──
    public Map<String, MetricScore> analyze(Mat bgrImage) {
        return analyzeAllFaces(bgrImage).stream()
            .max(Comparator.comparingDouble(f -> f.beautyScore))
            .map(f -> f.metrics).orElse(new HashMap<>());
    }

    private Map<String, MetricScore> analyzeROI(Mat roi, Mat skin, Rect faceRect) {
        Map<String, MetricScore> r = new LinkedHashMap<>();
        r.put("texture",    lbpTexture(roi, skin));
        r.put("detail",     laplacianDetail(roi, skin));
        r.put("fft",        fftHighFreq(roi, skin));
        r.put("color",      colorUniformity(roi, skin));
        r.put("edge",       edgeDensity(roi, skin));
        r.put("saturation", saturationEnhancement(roi, skin));
        r.put("eye",        eyeEnlargement(roi, faceRect));
        r.put("lip",        lipEnhancement(roi));
        r.put("nose",       noseSlimming(roi, faceRect));
        return r;
    }

    private double beautyScore(Map<String, MetricScore> m) {
        return Math.min(100,
            sc(m,"texture")*0.18 + sc(m,"detail")*0.18 + sc(m,"color")*0.14 +
            sc(m,"edge")*0.12 + sc(m,"saturation")*0.08 + sc(m,"fft")*0.05 +
            sc(m,"eye")*0.13 + sc(m,"lip")*0.07 + sc(m,"nose")*0.05);
    }
    private double sc(Map<String, MetricScore> m, String k) {
        MetricScore v = m.get(k); return v != null ? v.getScore() : 0;
    }

    // ── Three-path face detection (frontal + profile + flipped) ──
    private List<DetectedFace> detectFacesCombined(Mat image) {
        Mat gray = new Mat();
        Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.equalizeHist(gray, gray);
        List<DetectedFace> all = new ArrayList<>();

        if (frontalDetector != null && !frontalDetector.empty()) {
            MatOfRect f = new MatOfRect();
            frontalDetector.detectMultiScale(gray, f, 1.1, 3, 0, new Size(60,60), new Size());
            for (Rect r : f.toArray()) all.add(new DetectedFace(r, false));
        }
        if (profileDetector != null && !profileDetector.empty()) {
            MatOfRect p = new MatOfRect();
            profileDetector.detectMultiScale(gray, p, 1.1, 3, 0, new Size(60,60), new Size());
            for (Rect r : p.toArray()) all.add(new DetectedFace(r, true));
            Mat flipped = new Mat(); Core.flip(gray, flipped, 1);
            MatOfRect pf = new MatOfRect();
            profileDetector.detectMultiScale(flipped, pf, 1.1, 3, 0, new Size(60,60), new Size());
            for (Rect r : pf.toArray())
                all.add(new DetectedFace(new Rect(image.cols()-r.x-r.width, r.y, r.width, r.height), true));
            flipped.release();
        }
        gray.release();
        return mergeDetectedFaces(all);
    }

    private List<DetectedFace> mergeDetectedFaces(List<DetectedFace> faces) {
        List<DetectedFace> out = new ArrayList<>();
        boolean[] used = new boolean[faces.size()];
        for (int i = 0; i < faces.size(); i++) {
            if (used[i]) continue;
            Rect ra = faces.get(i).rect;
            for (int j = i+1; j < faces.size(); j++) {
                if (used[j]) continue;
                Rect rb = faces.get(j).rect;
                int ix1=Math.max(ra.x,rb.x), iy1=Math.max(ra.y,rb.y);
                int ix2=Math.min(ra.x+ra.width,rb.x+rb.width);
                int iy2=Math.min(ra.y+ra.height,rb.y+rb.height);
                int iw=ix2-ix1, ih=iy2-iy1;
                if (iw>0&&ih>0) {
                    double iou=(double)(iw*ih)/(ra.width*ra.height+rb.width*rb.height-iw*ih);
                    if (iou>0.3) used[j]=true;
                }
            }
            out.add(faces.get(i));
        }
        return out;
    }

    private Mat extractSkinMask(Mat bgr) {
        Mat ycc=new Mat(); Imgproc.cvtColor(bgr,ycc,Imgproc.COLOR_BGR2YCrCb);
        Mat mask=new Mat(); Core.inRange(ycc,new Scalar(0,133,77),new Scalar(255,173,127),mask);
        Mat k=Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(7,7));
        Imgproc.morphologyEx(mask,mask,Imgproc.MORPH_OPEN,k);
        Imgproc.morphologyEx(mask,mask,Imgproc.MORPH_CLOSE,k);
        ycc.release(); k.release(); return mask;
    }

    private MetricScore lbpTexture(Mat face, Mat mask) {
        Mat sm=new Mat(), smk=new Mat();
        double sc=Math.min(1.0, 320.0/Math.max(face.cols(),1));
        Imgproc.resize(face,sm,new Size(face.cols()*sc,face.rows()*sc));
        Imgproc.resize(mask,smk,new Size(face.cols()*sc,face.rows()*sc));
        Mat gray=new Mat(); Imgproc.cvtColor(sm,gray,Imgproc.COLOR_BGR2GRAY);
        int rows=gray.rows(), cols=gray.cols();
        byte[] gd=new byte[rows*cols], md=new byte[rows*cols];
        gray.get(0,0,gd); smk.get(0,0,md);
        int[] hist=new int[256];
        int[][] off={{-1,-1},{-1,0},{-1,1},{0,1},{1,1},{1,0},{1,-1},{0,-1}};
        for(int y=1;y<rows-1;y++) for(int x=1;x<cols-1;x++) {
            if((md[y*cols+x]&0xFF)==0) continue;
            int c=gd[y*cols+x]&0xFF, code=0;
            for(int i=0;i<8;i++){int ny=y+off[i][0],nx=x+off[i][1]; if((gd[ny*cols+nx]&0xFF)>=c) code|=(1<<i);}
            hist[code]++;
        }
        double total=Arrays.stream(hist).sum(), entropy=0;
        for(int ct:hist) if(ct>0){double p=ct/total; entropy-=p*Math.log(p)/Math.log(2);}
        double score=Math.max(0,Math.min(100,(1.0-entropy/NATURAL_LBP_ENTROPY)*120));
        gray.release(); sm.release(); smk.release();
        return new MetricScore("皮肤纹理平滑度",score,String.format("LBP熵=%.2f",entropy));
    }

    private MetricScore laplacianDetail(Mat face, Mat mask) {
        Mat gray=new Mat(); Imgproc.cvtColor(face,gray,Imgproc.COLOR_BGR2GRAY);
        Mat lap=new Mat(); Imgproc.Laplacian(gray,lap,CvType.CV_64F,3);
        double sv=maskedVar(lap,mask);
        Mat inv=new Mat(); Core.bitwise_not(mask,inv);
        double bv=maskedVar(lap,inv);
        double ratio=bv>1?sv/bv:1.0;
        gray.release(); lap.release(); inv.release();
        return new MetricScore("高频细节丢失",Math.max(0,Math.min(100,(1.0-ratio)*130)),
            String.format("皮肤/背景方差比=%.3f",ratio));
    }

    private double maskedVar(Mat src, Mat mask) {
        MatOfDouble m=new MatOfDouble(),s=new MatOfDouble();
        Core.meanStdDev(src,m,s,mask); double v=s.get(0,0)[0]; return v*v;
    }

    private MetricScore fftHighFreq(Mat face, Mat mask) {
        Mat sm=new Mat(),smk=new Mat();
        Imgproc.resize(face,sm,new Size(256,256)); Imgproc.resize(mask,smk,new Size(256,256));
        Mat gray=new Mat(); Imgproc.cvtColor(sm,gray,Imgproc.COLOR_BGR2GRAY);
        Mat masked=new Mat(gray.size(),CvType.CV_8U,Scalar.all(0)); gray.copyTo(masked,smk);
        Mat flt=new Mat(); masked.convertTo(flt,CvType.CV_32F);
        Mat dft=new Mat(); Core.dft(flt,dft,Core.DFT_COMPLEX_OUTPUT);
        List<Mat> pl=new ArrayList<>(); Core.split(dft,pl);
        Mat mag=new Mat(); Core.magnitude(pl.get(0),pl.get(1),mag);
        Core.add(mag,Scalar.all(1),mag); Core.log(mag,mag);
        int cx=mag.cols()/2,cy=mag.rows()/2,r=Math.min(cx,cy)/4;
        double total=0,hf=0;
        for(int y=0;y<mag.rows();y++) for(int x=0;x<mag.cols();x++){
            double v=mag.get(y,x)[0]; total+=v;
            if(Math.sqrt((x-cx)*(x-cx)+(y-cy)*(y-cy))>r) hf+=v;
        }
        double ratio=total>0?hf/total:NATURAL_HF_RATIO;
        double score=Math.max(0,Math.min(100,(1.0-ratio/NATURAL_HF_RATIO)*110));
        gray.release(); sm.release(); smk.release(); masked.release();
        flt.release(); dft.release(); mag.release(); pl.forEach(Mat::release);
        return new MetricScore("频域低通滤波",score,String.format("高频能量比=%.4f",ratio));
    }

    private MetricScore colorUniformity(Mat face, Mat mask) {
        Mat ycc=new Mat(); Imgproc.cvtColor(face,ycc,Imgproc.COLOR_BGR2YCrCb);
        List<Mat> ch=new ArrayList<>(); Core.split(ycc,ch);
        MatOfDouble m1=new MatOfDouble(),s1=new MatOfDouble(),m2=new MatOfDouble(),s2=new MatOfDouble();
        Core.meanStdDev(ch.get(2),m1,s1,mask); Core.meanStdDev(ch.get(1),m2,s2,mask);
        double std=(s1.get(0,0)[0]+s2.get(0,0)[0])/2.0;
        ycc.release(); ch.forEach(Mat::release);
        return new MetricScore("肤色均匀度",Math.max(0,Math.min(100,(1.0-std/NATURAL_SKIN_COLOR_STD)*110)),
            String.format("色度std=%.2f",std));
    }

    private MetricScore edgeDensity(Mat face, Mat mask) {
        Mat gray=new Mat(); Imgproc.cvtColor(face,gray,Imgproc.COLOR_BGR2GRAY);
        Mat edges=new Mat(); Imgproc.Canny(gray,edges,40,120);
        Mat se=new Mat(); Core.bitwise_and(edges,mask,se);
        int sp=Core.countNonZero(mask);
        double density=sp>0?(double)Core.countNonZero(se)/sp:0.1;
        gray.release(); edges.release(); se.release();
        return new MetricScore("边缘/残差抑制",Math.max(0,Math.min(100,(1.0-density/0.06)*110)),
            String.format("边缘密度=%.4f",density));
    }

    private MetricScore saturationEnhancement(Mat face, Mat mask) {
        Mat hsv=new Mat(); Imgproc.cvtColor(face,hsv,Imgproc.COLOR_BGR2HSV);
        List<Mat> ch=new ArrayList<>(); Core.split(hsv,ch);
        MatOfDouble m=new MatOfDouble(),s=new MatOfDouble();
        Core.meanStdDev(ch.get(1),m,s,mask);
        double mean=m.get(0,0)[0], std=s.get(0,0)[0];
        double score=Math.min(100,Math.max(0,(mean-80)/80.0*60)+Math.max(0,(1.0-std/25.0)*40));
        hsv.release(); ch.forEach(Mat::release);
        return new MetricScore("饱和度增强",score,String.format("S均值=%.1f std=%.2f",mean,std));
    }

    private MetricScore eyeEnlargement(Mat faceROI, Rect faceRect) {
        if (eyeDetector==null||eyeDetector.empty())
            return new MetricScore("眼睛放大检测",0,"检测器未加载");
        Mat gray=new Mat(); Imgproc.cvtColor(faceROI,gray,Imgproc.COLOR_BGR2GRAY);
        Mat eyeReg=new Mat(gray,new Rect(0,0,gray.cols(),gray.rows()/2));
        MatOfRect eyes=new MatOfRect();
        eyeDetector.detectMultiScale(eyeReg,eyes,1.1,4,0,new Size(20,20),new Size(gray.cols()/3,gray.rows()/4));
        gray.release();
        Rect[] arr=eyes.toArray();
        if(arr.length==0) return new MetricScore("眼睛放大检测",0,"未检测到眼睛");
        double avgW=Arrays.stream(arr).mapToDouble(r->r.width).average().orElse(0);
        double ratio=avgW/Math.max(faceRect.width,1);
        double asymScore=0;
        if(arr.length>=2){
            Arrays.sort(arr,Comparator.comparingInt(r->r.x));
            double a=Math.abs(arr[0].width-arr[arr.length-1].width)/(double)Math.max(arr[0].width,arr[arr.length-1].width);
            asymScore=Math.min(40,a*200);
        }
        double score=Math.min(100,Math.max(0,(ratio-NATURAL_EYE_FACE_RATIO)/0.10*60)+asymScore);
        return new MetricScore("眼睛放大检测",score,
            String.format("眼宽/脸宽=%.3f (自然≈%.2f)，%s",ratio,NATURAL_EYE_FACE_RATIO,score>45?"疑似放大":"自然"));
    }

    private MetricScore lipEnhancement(Mat faceROI) {
        if (smileDetector==null||smileDetector.empty())
            return new MetricScore("嘴唇美化检测",0,"检测器未加载");
        Mat gray=new Mat(); Imgproc.cvtColor(faceROI,gray,Imgproc.COLOR_BGR2GRAY);
        Imgproc.equalizeHist(gray,gray);
        int mY=gray.rows()*3/5, mH=gray.rows()-mY;
        if(mH<=0){gray.release();return new MetricScore("嘴唇美化检测",0,"N/A");}
        Mat mReg=new Mat(gray,new Rect(0,mY,gray.cols(),mH));
        MatOfRect smiles=new MatOfRect();
        smileDetector.detectMultiScale(mReg,smiles,1.1,15,0,new Size(25,15),new Size(gray.cols(),mH));
        gray.release();
        Rect[] arr=smiles.toArray();
        Rect lipR=arr.length>0 ? new Rect(arr[0].x,arr[0].y+mY,arr[0].width,arr[0].height)
                               : new Rect(faceROI.cols()/4,mY,faceROI.cols()/2,mH);
        lipR.x=Math.max(0,Math.min(lipR.x,faceROI.cols()-1));
        lipR.y=Math.max(0,Math.min(lipR.y,faceROI.rows()-1));
        lipR.width=Math.min(lipR.width,faceROI.cols()-lipR.x);
        lipR.height=Math.min(lipR.height,faceROI.rows()-lipR.y);
        if(lipR.width<=0||lipR.height<=0) return new MetricScore("嘴唇美化检测",10,"区域无效");
        Mat hsv=new Mat(); Imgproc.cvtColor(faceROI,hsv,Imgproc.COLOR_BGR2HSV);
        List<Mat> ch=new ArrayList<>(); Core.split(hsv,ch);
        MatOfDouble lm=new MatOfDouble(),ls=new MatOfDouble(),fm=new MatOfDouble(),fs=new MatOfDouble();
        Mat lipROI=new Mat(ch.get(1),lipR);
        Core.meanStdDev(lipROI,lm,ls); Core.meanStdDev(ch.get(1),fm,fs);
        double lipSat=lm.get(0,0)[0],faceSat=fm.get(0,0)[0],diff=lipSat-faceSat,lipStd=ls.get(0,0)[0];
        double score=Math.min(100,Math.max(0,(diff-NATURAL_LIP_SAT_DIFF)/30.0*60)+Math.max(0,(1.0-lipStd/20.0)*40));
        hsv.release(); lipROI.release(); ch.forEach(Mat::release);
        return new MetricScore("嘴唇美化检测",score,
            String.format("唇S=%.1f 脸S=%.1f 差=%.1f，%s",lipSat,faceSat,diff,score>45?"疑似提色":"自然"));
    }

    private MetricScore noseSlimming(Mat faceROI, Rect faceRect) {
        int nX=faceROI.cols()/3, nW=faceROI.cols()/3;
        int nY=(int)(faceROI.rows()*0.25), nH=(int)(faceROI.rows()*0.40);
        if(nW<=0||nH<=0) return new MetricScore("鼻子瘦化检测",0,"N/A");
        Mat nROI=new Mat(faceROI,new Rect(nX,nY,nW,nH));
        Mat ng=new Mat(); Imgproc.cvtColor(nROI,ng,Imgproc.COLOR_BGR2GRAY);
        Mat gradX=new Mat(); Imgproc.Sobel(ng,gradX,CvType.CV_32F,1,0);
        List<Double> ww=new ArrayList<>();
        for(int y=ng.rows()/4;y<ng.rows()*3/4;y++){
            float[] gr=new float[ng.cols()]; gradX.get(y,0,gr);
            int le=0,re=ng.cols()-1; float ml=0,mr=0;
            for(int x=0;x<ng.cols()/2;x++) if(gr[x]>ml){ml=gr[x];le=x;}
            for(int x=ng.cols()-1;x>ng.cols()/2;x--) if(-gr[x]>mr){mr=-gr[x];re=x;}
            if(ml>20&&mr>20) ww.add((double)(re-le));
        }
        double avgW=ww.stream().mapToDouble(Double::doubleValue).average().orElse(nW*0.7);
        double noseRatio=avgW/Math.max(faceRect.width,1);
        Mat ne=new Mat(); Imgproc.Canny(ng,ne,30,90);
        double edgeDensity=(double)Core.countNonZero(ne)/(nROI.rows()*nROI.cols());
        Mat nL=new Mat(ng,new Rect(0,0,ng.cols()/2,ng.rows()));
        Mat nR=new Mat(ng,new Rect(ng.cols()/2,0,ng.cols()/2,ng.rows()));
        MatOfDouble mL=new MatOfDouble(),sL=new MatOfDouble(),mR=new MatOfDouble(),sR=new MatOfDouble();
        Core.meanStdDev(nL,mL,sL); Core.meanStdDev(nR,mR,sR);
        double asym=Math.abs(mL.get(0,0)[0]-mR.get(0,0)[0])/128.0;
        nROI.release(); ng.release(); gradX.release(); ne.release(); nL.release(); nR.release();
        double score=Math.min(100,
            Math.max(0,Math.min(50,(NATURAL_NOSE_FACE_RATIO-noseRatio)/0.12*50)) +
            (edgeDensity<0.02?30:edgeDensity>0.12?0:15) + (asym<0.03?20:0));
        return new MetricScore("鼻子瘦化检测",score,
            String.format("鼻宽/脸宽=%.3f (自然≈%.2f)，%s",noseRatio,NATURAL_NOSE_FACE_RATIO,
                score>45?"疑似液化瘦化":"形态自然"));
    }
}