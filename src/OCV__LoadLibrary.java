import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.Plot;
import ij.gui.ProfilePlot;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.awt.Frame;
import java.awt.Rectangle;
import java.util.ArrayList;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.videoio.VideoCapture;

/*
 * The MIT License
 *
 * Copyright 2016 Takehito Nishida.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

/**
 * Load OpenCV library.
 */
public class OCV__LoadLibrary implements ExtendedPlugInFilter {
    public static final String VERSION = "0.9.48.0";
    public static final String URL_HELP = "https://github.com/WAKU-TAKE-A/IJToolsUsingOpenCV";

    private static boolean disposed = true;
    private static Mat dummy = null;

    public static MyFeatureDetector MyQuery;
    public static MyAffineTransform MyAffine;
    public static MyPerspectiveTransform MyPerspective;
    public static MyCameraCalibration MyCameraCalib; // 追加
    
    // カメラキャッシュ
    private static VideoCapture cachedCamera = null;
    private static int cachedDevice = -1;
    private static int cachedWidth = -1;
    private static int cachedHeight = -1;
    private static int cachedApi = -1;
    private static boolean cameraHealthy = true;
    
    // ExtendedPlugInFilter
    @Override
    public void setNPasses(int arg0) {
        // do nothing
    }

    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner prf) {
        if(!disposed) {
            return DONE;
        }
        else {
            return NO_IMAGE_REQUIRED;
        }
    }

    @Override
    public void run(ImageProcessor arg0) {
        try {           
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            IJ.showStatus("Loading succeeded.(" + VERSION + ")");
            
            // Initialize only if null (preserve existing instances)
            if (MyQuery == null) {
                MyQuery = new MyFeatureDetector();
            }
            if (MyAffine == null) {
                MyAffine = new MyAffineTransform();
            }
            if (MyPerspective == null) {
                MyPerspective = new MyPerspectiveTransform();
            }
            if (MyCameraCalib == null) {
                MyCameraCalib = new MyCameraCalibration();
            }
            
            disposed = false;
        }
        catch(Throwable ex) {
            IJ.log("ERR : " + ex.toString());
            disposed = true;
        }
    }

    @Override
    public int setup(String arg0, ImagePlus imp) {
        disposed = !isLoadOpenCV();
        return NO_IMAGE_REQUIRED;
    }
    
    // for check
    private boolean isLoadOpenCV() {
        try {
            if(dummy != null) {
                dummy.release();
            }

            dummy = new Mat();
            return true;
        }
        catch(Throwable ex) {
            return false;
        }
    }

    public static boolean isLoad() {
        return !disposed;
    }

    // Camera management methods
    /**
     * カメラを取得（キャッシュから再利用または新規作成）
     */
    public static VideoCapture GetCamera(int device, int width, int height, int apiId, boolean forceNew) {
        boolean needRecreate = forceNew || !cameraHealthy || cachedCamera == null || 
                               !cachedCamera.isOpened() ||
                               cachedDevice != device || cachedApi != apiId;
        
        if (needRecreate) {
            ReleaseCamera();
            
            try {
                cachedCamera = new VideoCapture();
                if (!cachedCamera.open(device, apiId)) {
                    cachedCamera = null;
                    cameraHealthy = false;
                    throw new RuntimeException("Camera initialization failed for device " + device);
                }
                
                cachedCamera.set(3, width);   // CV_CAP_PROP_FRAME_WIDTH
                cachedCamera.set(4, height);  // CV_CAP_PROP_FRAME_HEIGHT
                cachedCamera.set(38, 1);      // CAP_PROP_BUFFERSIZE
                
                cachedDevice = device;
                cachedWidth = (int)cachedCamera.get(3);
                cachedHeight = (int)cachedCamera.get(4);
                cachedApi = apiId;
                cameraHealthy = true;
                
            } catch (RuntimeException e) {
                cameraHealthy = false;
                ReleaseCamera();
                throw e;
            }
        } else {
            if (cachedWidth != width || cachedHeight != height) {
                cachedCamera.set(3, width);
                cachedCamera.set(4, height);
                cachedCamera.set(38, 1);
                cachedWidth = (int)cachedCamera.get(3);
                cachedHeight = (int)cachedCamera.get(4);
            }
        }
        
        return cachedCamera;
    }
    
    public static int GetCachedCameraWidth() { return cachedWidth; }
    public static int GetCachedCameraHeight() { return cachedHeight; }
    public static boolean IsCachedCameraOpened() { return cachedCamera != null && cachedCamera.isOpened(); }
    
    public static void ReleaseCamera() {
        if (cachedCamera != null) {
            try {
                if (cachedCamera.isOpened()) {
                    cachedCamera.release();
                }
            } catch (Exception e) {}
        }
        cachedCamera = null;
        cachedDevice = -1;
        cachedWidth = -1;
        cachedHeight = -1;
        cachedApi = -1;
        cameraHealthy = true;
    }
    
    public static void MarkCameraUnhealthy() { cameraHealthy = false; }

    // Conversion methods
    /**
     * a CV_8UC3 data of OpenCV -> a color data of ImageJ.
     */
    public static void mat2intarray(Mat src_cv_8uc3, int[] dst_ar, int imw, int imh) {
        if((src_cv_8uc3.width() != imw) || (src_cv_8uc3.height() != imh) || dst_ar.length != imw * imh) {
            IJ.error("Wrong image size");
            return;
        }

        int totalPixels = imw * imh;
        byte[] buffer = new byte[totalPixels * 3];
        src_cv_8uc3.get(0, 0, buffer);

        for (int i = 0; i < totalPixels; i++) {
            int b = buffer[i * 3] & 0xFF;
            int g = buffer[i * 3 + 1] & 0xFF;
            int r = buffer[i * 3 + 2] & 0xFF;
            dst_ar[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
    }

    /**
     * a color data of ImageJ -> a CV_8UC3 data of OpenCV
     */
    public static void intarray2mat(int[] src_ar, Mat dst_cv_8uc3, int imw, int imh) {
        if((dst_cv_8uc3.width() != imw) || (dst_cv_8uc3.height() != imh) || src_ar.length != imw * imh) {
            IJ.error("Wrong image size");
            return;
        }

        int totalPixels = imw * imh;
        byte[] buffer = new byte[totalPixels * 3];

        for (int i = 0; i < totalPixels; i++) {
            int pixel = src_ar[i];
            buffer[i * 3] = (byte)(pixel & 0xFF);           // b
            buffer[i * 3 + 1] = (byte)((pixel >> 8) & 0xFF);  // g
            buffer[i * 3 + 2] = (byte)((pixel >> 16) & 0xFF); // r
        }

        dst_cv_8uc3.put(0, 0, buffer);
    }

    /**
     * ImageProcessor -> OpenCV Mat
     */
    public static Mat ip2mat(ImageProcessor ip) {
        int w = ip.getWidth();
        int h = ip.getHeight();
        Mat mat;

        if (ip instanceof ColorProcessor) {
            mat = new Mat(h, w, CvType.CV_8UC3);
            intarray2mat((int[]) ip.getPixels(), mat, w, h);
        } else if (ip instanceof ByteProcessor) {
            mat = new Mat(h, w, CvType.CV_8UC1);
            mat.put(0, 0, (byte[]) ip.getPixels());
        } else if (ip instanceof ShortProcessor) {
            mat = new Mat(h, w, CvType.CV_16UC1);
            mat.put(0, 0, (short[]) ip.getPixels());
        } else if (ip instanceof FloatProcessor) {
            mat = new Mat(h, w, CvType.CV_32FC1);
            mat.put(0, 0, (float[]) ip.getPixels());
        } else {
            throw new IllegalArgumentException("Unsupported ImageProcessor type");
        }
        return mat;
    }

    /**
     * OpenCV Mat -> ImageProcessor
     */
    public static ImageProcessor mat2ip(Mat mat) {
        int w = mat.cols();
        int h = mat.rows();
        int type = mat.type();

        if (type == CvType.CV_8UC3) {
            ColorProcessor cp = new ColorProcessor(w, h);
            mat2intarray(mat, (int[]) cp.getPixels(), w, h);
            return cp;
        } else if (type == CvType.CV_8UC1) {
            ByteProcessor bp = new ByteProcessor(w, h);
            mat.get(0, 0, (byte[]) bp.getPixels());
            return bp;
        } else if (type == CvType.CV_16UC1) {
            ShortProcessor sp = new ShortProcessor(w, h);
            mat.get(0, 0, (short[]) sp.getPixels());
            return sp;
        } else if (type == CvType.CV_32FC1) {
            FloatProcessor fp = new FloatProcessor(w, h);
            mat.get(0, 0, (float[]) fp.getPixels());
            return fp;
        } else {
            throw new IllegalArgumentException("Unsupported Mat type: " + type);
        }
    }

    // Utility methods
    public static void GetCoordinates(Roi roi, ArrayList<Point> lstPt) {
        ImageProcessor mask = roi.getMask();
        Rectangle r = roi.getBounds();
        for(int y = 0; y < r.height; y++) {
            for(int x = 0; x < r.width; x++) {
                if(mask == null || mask.getPixel(x, y) != 0) {
                    lstPt.add(new Point(r.x + x, r.y + y));
                }
            }
        }
    }

    public static ResultsTable GetResultsTable(boolean enReset) {
        ResultsTable rt = ResultsTable.getResultsTable();
        if(rt == null || rt.getCounter() == 0) rt = new ResultsTable();
        if(enReset) rt.reset();
        rt.show("Results");
        return rt;
    }

    public static RoiManager GetRoiManager(boolean enReset, boolean enShowNone) {
        Frame frame = WindowManager.getFrame("ROI Manager");
        RoiManager rm = (frame == null) ? new RoiManager() : (RoiManager)frame;
        rm.setVisible(true);
        if(enReset) rm.reset();
        if(enShowNone) rm.runCommand("Show None");
        return rm;
    }

    public static void Wait(int wt) {
        try { if(wt > 0) Thread.sleep(wt); } catch(InterruptedException e) {}
    }
    
    public static Plot GetProfilePlot(ImagePlus imp) {
        ProfilePlot profPlot = new ProfilePlot(imp, Prefs.verticalProfile);
        double[] prof = profPlot.getProfile();
        if(prof == null || prof.length < 2) return null;
        Plot output_plot = new Plot("Profile", "Distance (pixels)", "Value");
        output_plot.add("line", prof);
        return output_plot;
    }
    
    public static void ArrayCopy(ImageProcessor src, ImageProcessor dst) {
        int len = src.getWidth() * src.getHeight();
        System.arraycopy(src.getPixels(), 0, dst.getPixels(), 0, len);
    }

    public static boolean isNullOrEmpty(String src) {
        return src == null || src.isEmpty() || src.isBlank();  
    }

    public static String DescribeMat(Mat m) {
        String depthName = switch (m.depth()) {
            case CvType.CV_8U -> "CV_8U";
            case CvType.CV_8S -> "CV_8S";
            case CvType.CV_16U -> "CV_16U";
            case CvType.CV_16S -> "CV_16S";
            case CvType.CV_32S -> "CV_32S";
            case CvType.CV_32F -> "CV_32F";
            case CvType.CV_64F -> "CV_64F";
            default -> "Unknown";
        };
        return "rows=" + m.rows() + ", cols=" + m.cols() + ", type=" + m.type()
                + " (depth=" + depthName + ", channels=" + m.channels() + ")";
    }
}