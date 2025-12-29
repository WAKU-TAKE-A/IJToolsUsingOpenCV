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
import ij.process.ImageProcessor;
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
    public static final String VERSION = "0.9.46.0";
    public static final String URL_HELP = "https://github.com/WAKU-TAKE-A/IJToolsUsingOpenCV";

    private static boolean disposed = true;
    private static Mat dummy = null;

    public static MyFeatureDetector MyQuery;
    public static MyAffineTransform MyAffine;
    public static MyPerspectiveTransform MyPerspective;
    
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
     * @param device デバイス番号
     * @param width 幅
     * @param height 高さ
     * @param apiId API ID
     * @param forceNew 強制的に新規作成
     * @return VideoCapture
     */
    public static VideoCapture GetCamera(int device, int width, int height, int apiId, boolean forceNew) {
        // キャッシュが存在するが開いていない場合は再作成が必要
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
            // キャッシュ再利用の場合もwidth/heightが変更されていれば再設定
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
    
    /**
     * キャッシュされたカメラの幅を取得
     * @return 幅
     */
    public static int GetCachedCameraWidth() {
        return cachedWidth;
    }
    
    /**
     * キャッシュされたカメラの高さを取得
     * @return 高さ
     */
    public static int GetCachedCameraHeight() {
        return cachedHeight;
    }
    
    /**
     * キャッシュされたカメラが開いているか確認
     * @return 開いている場合true
     */
    public static boolean IsCachedCameraOpened() {
        return cachedCamera != null && cachedCamera.isOpened();
    }
    
    /**
     * カメラリソースを解放
     */
    public static void ReleaseCamera() {
        if (cachedCamera != null) {
            try {
                if (cachedCamera.isOpened()) {
                    cachedCamera.release();
                }
            } catch (Exception e) {
                // 解放失敗は無視
            }
        }
        cachedCamera = null;
        cachedDevice = -1;
        cachedWidth = -1;
        cachedHeight = -1;
        cachedApi = -1;
        cameraHealthy = true;
    }
    
    /**
     * カメラエラーをマーク（次回GetCameraで強制再作成）
     */
    public static void MarkCameraUnhealthy() {
        cameraHealthy = false;
    }

    // static method
    /**
     * a CV_8UC3 data of OpenCV -> a color data of ImageJ.
     * @param src_cv_8uc3 a CV_8UC3 data of OpenCV
     * @param dst_ar a color data of ImageJ (int[])
     * @param imw width of image
     * @param imh height of image
     */
    public static void mat2intarray(Mat src_cv_8uc3, int[] dst_ar, int imw, int imh) {
        if((src_cv_8uc3.width() != imw) || (src_cv_8uc3.height() != imh) || dst_ar.length != imw * imh) {
            IJ.error("Wrong image size");
            return;
        }

        // 一括取得
        int totalPixels = imw * imh;
        byte[] buffer = new byte[totalPixels * 3];
        src_cv_8uc3.get(0, 0, buffer);

        // 高速変換
        for (int i = 0; i < totalPixels; i++) {
            int b = buffer[i * 3] & 0xFF;
            int g = buffer[i * 3 + 1] & 0xFF;
            int r = buffer[i * 3 + 2] & 0xFF;
            dst_ar[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
    }

    /**
     * a color data of ImageJ -> a CV_8UC3 data of OpenCV
     * @param src_ar a color data of ImageJ (int[])
     * @param dst_cv_8uc3 CV_8UC3 data of OpenCV
     * @param imw width of image
     * @param imh height of image
     */
    public static void intarray2mat(int[] src_ar, Mat dst_cv_8uc3, int imw, int imh) {
        if((dst_cv_8uc3.width() != imw) || (dst_cv_8uc3.height() != imh) || src_ar.length != imw * imh) {
            IJ.error("Wrong image size");
            return;
        }

        // 高速変換
        int totalPixels = imw * imh;
        byte[] buffer = new byte[totalPixels * 3];

        for (int i = 0; i < totalPixels; i++) {
            int pixel = src_ar[i];
            buffer[i * 3] = (byte)(pixel & 0xFF);           // b
            buffer[i * 3 + 1] = (byte)((pixel >> 8) & 0xFF);  // g
            buffer[i * 3 + 2] = (byte)((pixel >> 16) & 0xFF); // r
        }

        // 一括書き込み
        dst_cv_8uc3.put(0, 0, buffer);
    }

    /**
     * get the coordinates of the roi(ref:XYCoordinates.saveSelectionCoordinates())
     * @param roi
     * @param lstPt
     */
    public static void GetCoordinates(Roi roi, ArrayList<Point> lstPt) {
        ImageProcessor mask = roi.getMask();
        Rectangle r = roi.getBounds();
        int pos_x;
        int pos_y;

        for(int y = 0; y < r.height; y++) {
            for(int x = 0; x < r.width; x++) {
                if(mask == null || mask.getPixel(x, y) != 0) {
                    pos_x = r.x + x;
                    pos_y = r.y + y;
                    lstPt.add(new Point(pos_x, pos_y));
                }
            }
        }
    }

    /**
     * get the ResultsTable or create a new ResultsTable
     * @param enReset reset or not
     * @return ResultsTable
     */
    public static ResultsTable GetResultsTable(boolean enReset) {
        ResultsTable rt = ResultsTable.getResultsTable();

        if(rt == null || rt.getCounter() == 0) {
            rt = new ResultsTable();
        }

        if(enReset) {
            rt.reset();
        }

        rt.show("Results");
        return rt;
    }

    /**
     * get the RoiManager or create a new RoiManager
     * @param enReset reset or not
     * @param enShowNone show none or not
     * @return RoiManager
     */
    public static RoiManager GetRoiManager(boolean enReset, boolean enShowNone) {
        Frame frame = WindowManager.getFrame("ROI Manager");
        RoiManager rm;

        if(frame == null) {
            rm = new RoiManager();
            rm.setVisible(true);
        }
        else {
            rm = (RoiManager)frame;
        }

        if(enReset) {
            rm.reset();
        }

        if(enShowNone) {
            rm.runCommand("Show None");
        }

        return rm;
    }

    /**
     * Wait.
     * @param wt wait time (ms).
     */
    public static void Wait(int wt) {
        try {
            if(wt == 0) {
                // do nothing
            }
            else {
                Thread.sleep(wt);
            }
        }
        catch(InterruptedException e) {
            // do nothing
        }
    }
    
    /**
     * plot profile
     * @param imp
     * @return
     */
    public static Plot GetProfilePlot(ImagePlus imp) {
        ProfilePlot profPlot = new ProfilePlot(imp, Prefs.verticalProfile);
        double[] prof = profPlot.getProfile();

        if(prof == null || prof.length < 2) {
            return null;
        }

        String xLabel = "Distance (pixels)";
        String yLabel = "Value";

        Plot output_plot = new Plot("Profile", xLabel, yLabel);
        output_plot.add("line", prof);

        return output_plot;
    }
    
    /**
     * copy array
     * @param src
     * @param dst
     */    
    public static void ArrayCopy(ImageProcessor src, ImageProcessor dst) {
        if(src.getBitDepth() == 8) {
            int imw = src.getWidth();
            int imh = src.getHeight();
            byte[] src_bytes = (byte[])src.getPixels();
            byte[] dst_bytes = (byte[])dst.getPixels();
            System.arraycopy(src_bytes, 0, dst_bytes, 0, imw*imh);
        }
        else if(src.getBitDepth() == 16) {
            int imw = src.getWidth();
            int imh = src.getHeight();
            short[] src_shorts = (short[])src.getPixels();
            short[] dst_shorts = (short[])dst.getPixels();            
            System.arraycopy(src_shorts, 0, dst_shorts, 0, imw*imh);
        }
        else if(src.getBitDepth() == 24) {
            int imw = src.getWidth();
            int imh = src.getHeight();
            int[] src_ints = (int[])src.getPixels();
            int[] dst_ints = (int[])dst.getPixels();
            System.arraycopy(src_ints, 0, dst_ints, 0, imw*imh);
        }
        else if(src.getBitDepth() == 32) {
            // srcdst
            int imw = src.getWidth();
            int imh = src.getHeight();
            float[] src_floats = (float[])src.getPixels();
            float[] dst_floats = (float[])dst.getPixels();
            System.arraycopy(src_floats, 0, dst_floats, 0, imw*imh);
        }
        else {
            IJ.log("OCV_LoadLibrary error: Wrong image format.");
        }
    }

    /** 
     * null check for a string
     * @param src
     * @return
     */
    public static boolean isNullOrEmpty(String src)
    {
        return src == null || src.isEmpty() || src.isBlank();  
    }

    /**
     * describe the type of mat
     * @param m
     * @return 
     */
    public static String DescribeMat(Mat m) {
        int type = m.type();
        int depth = m.depth();
        int channels = m.channels();

        String depthName;
        depthName = switch (depth) {
                case CvType.CV_8U -> "CV_8U";
                case CvType.CV_8S -> "CV_8S";
                case CvType.CV_16U -> "CV_16U";
                case CvType.CV_16S -> "CV_16S";
                case CvType.CV_32S -> "CV_32S";
                case CvType.CV_32F -> "CV_32F";
                case CvType.CV_64F -> "CV_64F";
                default -> "Unknown";
            };

        return "rows=" + m.rows() + ", cols=" + m.cols() + ", type=" + type
                + " (depth=" + depthName + ", channels=" + channels + ")";
    }
}