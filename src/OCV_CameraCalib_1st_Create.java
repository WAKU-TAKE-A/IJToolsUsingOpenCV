import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import java.awt.AWTEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Size;
import org.opencv.core.TermCriteria;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

/*
 * The MIT License (Copyright 2025 Takehito Nishida)
 * Code follows the project's standard ASCII/try-finally pattern.
 */

public class OCV_CameraCalib_1st_Create implements ExtendedPlugInFilter, DialogListener {
    // Constants
    private final int FLAGS = NO_IMAGE_REQUIRED;
    private final String[] STR_MODES = { "new_calib", "read_calib", "remake_calib" };
    private final String[] STR_PATTERNS = { "Chessboard", "Symmetric Circles", "Asymmetric Circles" };
    private final String[] STR_SOURCES = { "file", "camera" };
    private final String[] STR_CAP_APIS = { "Auto", "DirectShow", "MicrosoftMediaFoundation" };
    private final int[] INT_CAP_APIS = { 0, 700, 1400 };

    // Static variables for persistence
    private static int indMode = 0;
    private static String calibName = "";
    private static int indPattern = 0;
    private static int patternCols = 9;
    private static int patternRows = 6;
    private static double squareSize = 20.0;
    private static int winSize = 11;
    private static int indSource = 0;
    private static int device = 0;
    private static int width = 640;
    private static int height = 480;
    private static int indCapApi = 1;
    
    private static volatile boolean isRunning = false;

    // Instance variables
    private String className;
    private MyCameraCalibration mCalib;
    private boolean flag_fin_loop = false;
    private boolean flag_capture = false;
    private ImagePlus impStack = null;
    private ImagePlus impActive = null;

    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner pr) {
        className = cmd.trim();
        if (isRunning) {
            IJ.showStatus("Already running.");
            return DONE;
        }

        GenericDialog gd = new GenericDialog(className);
        gd.addChoice("mode", STR_MODES, STR_MODES[indMode]);
        gd.addStringField("calibration_name", calibName, 8);
        gd.addChoice("pattern_type", STR_PATTERNS, STR_PATTERNS[indPattern]);
        gd.addNumericField("pattern_cols", patternCols, 0);
        gd.addNumericField("pattern_rows", patternRows, 0);
        gd.addNumericField("square_size_mm", squareSize, 2);
        gd.addNumericField("subpix_winSize", winSize, 0);
        gd.addChoice("input_source", STR_SOURCES, STR_SOURCES[indSource]);
        gd.addNumericField("device", device, 0);
        gd.addNumericField("width", width, 0);
        gd.addNumericField("height", height, 0);
        gd.addChoice("capture_api", STR_CAP_APIS, STR_CAP_APIS[indCapApi]);

        gd.addDialogListener(this);
        gd.showDialog();

        if (gd.wasCanceled()) return DONE;
        
        // Final validation before running
        if (indMode == 0 && (impActive == null && indSource == 0)) {
            IJ.noImage();
            return DONE;
        }
        if (OCV__LoadLibrary.isNullOrEmpty(calibName)) {
            IJ.showStatus("calibration_name is empty.");
            return DONE;
        }

        return FLAGS;
    }

    @Override
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent awte) {
        indMode = gd.getNextChoiceIndex();
        calibName = gd.getNextString();
        indPattern = gd.getNextChoiceIndex();
        patternCols = (int) gd.getNextNumber();
        patternRows = (int) gd.getNextNumber();
        squareSize = gd.getNextNumber();
        winSize = (int) gd.getNextNumber();
        indSource = gd.getNextChoiceIndex();
        device = (int) gd.getNextNumber();
        width = (int) gd.getNextNumber();
        height = (int) gd.getNextNumber();
        indCapApi = gd.getNextChoiceIndex();

        // Parameter checks
        if (OCV__LoadLibrary.isNullOrEmpty(calibName)) {
            IJ.showStatus("calibration_name is empty.");
            return false;
        }

        if (patternCols <= 0 || patternRows <= 0 || squareSize <= 0) {
            IJ.showStatus("pattern size and square size must be positive.");
            return false;
        }

        // Mode-specific existence checks
        if (indMode == 0) { // new_calib
            if (MyCameraCalibration.exists(calibName)) {
                IJ.showStatus("calibration_name already exists.");
                return false;
            }
        } else { // read or remake
            if (!MyCameraCalibration.exists(calibName)) {
                IJ.showStatus("calibration_name does not exist.");
                return false;
            }
        }

        IJ.showStatus(className);
        return true;
    }

    @Override
    public void run(ImageProcessor ip) {
        isRunning = true;
        mCalib = OCV__LoadLibrary.MyCameraCalib;
        
        try {
            mCalib.setCalibName(calibName);
            
            if (indMode != 0) { // read or remake
                mCalib.read();
                // Apply loaded settings for remake
                patternCols = mCalib.patternCols;
                patternRows = mCalib.patternRows;
                squareSize = mCalib.squareSize;
                indPattern = mCalib.patternType;
                winSize = mCalib.winSize;
            }

            if (indMode == 1) { // read_calib
                IJ.showStatus(className + ": Loaded calibration \"" + calibName + "\"");
                return;
            }

            if (indSource == 0) { // file
                processStack(impActive);
            } else { // camera
                processLiveCamera();
            }

        } catch (Exception e) {
            OCV__LoadLibrary.logError(className, e.getMessage());
        } finally {
            isRunning = false;
        }
    }

    private void processStack(ImagePlus imp) {
        if (imp == null || imp.getStackSize() < 1) {
            OCV__LoadLibrary.logError(className, "Requires an image stack.");
            return;
        }

        Size boardSize = new Size(patternCols, patternRows);
        List<Mat> imagePoints = new ArrayList<>();
        List<Mat> objectPoints = new ArrayList<>();
        Mat obj = createObjectPoints(boardSize, squareSize, indPattern);

        try {
            ImageStack stack = imp.getStack();
            for (int i = 1; i <= stack.getSize(); i++) {
                ImageProcessor sliceIp = stack.getProcessor(i);
                Mat mat = OCV__LoadLibrary.ip2mat(sliceIp);
                Mat gray = new Mat();
                
                if (mat.channels() == 3) Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY);
                else mat.copyTo(gray);

                MatOfPoint2f corners = new MatOfPoint2f();
                boolean found = findPattern(gray, boardSize, corners);

                if (found) {
                    Imgproc.cornerSubPix(gray, corners, new Size(winSize, winSize), new Size(-1, -1),
                            new TermCriteria(TermCriteria.EPS + TermCriteria.COUNT, 30, 0.1));
                    imagePoints.add(corners);
                    objectPoints.add(obj.clone());
                } else {
                    corners.release();
                }

                mat.release();
                gray.release();
            }

            if (imagePoints.size() > 0) {
                mCalib.calibrate(objectPoints, imagePoints, new Size(imp.getWidth(), imp.getHeight()));
                saveResults();
            } else {
                OCV__LoadLibrary.logError(className, "No patterns detected in stack.");
            }
        } finally {
            obj.release();
            for (Mat m : imagePoints) m.release();
            for (Mat m : objectPoints) m.release();
        }
    }

    private void processLiveCamera() {
        VideoCapture cap = null;
        Mat frame = new Mat();
        Mat gray = new Mat();
        ImagePlus impLive = null;
        JDialog diag = createCameraControl();

        try {
            cap = OCV__LoadLibrary.GetCamera(device, width, height, INT_CAP_APIS[indCapApi], true);
            int w = OCV__LoadLibrary.GetCachedCameraWidth();
            int h = OCV__LoadLibrary.GetCachedCameraHeight();
            
            impLive = IJ.createImage("Live - " + calibName, w, h, 1, 24);
            int[] pixels = (int[]) impLive.getChannelProcessor().getPixels();
            impLive.show();
            diag.setVisible(true);

            Size boardSize = new Size(patternCols, patternRows);

            while (!flag_fin_loop) {
                if (!cap.read(frame)) break;

                Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
                MatOfPoint2f corners = new MatOfPoint2f();
                boolean found = findPattern(gray, boardSize, corners);

                if (flag_capture) {
                    if (impStack == null) {
                        impStack = new ImagePlus("Captured_Frames", OCV__LoadLibrary.mat2ip(frame));
                        impStack.show();
                    } else {
                        impStack.getStack().addSlice(OCV__LoadLibrary.mat2ip(frame));
                        impStack.setStack(impStack.getStack());
                        impStack.setSlice(impStack.getStackSize());
                    }
                    flag_capture = false;
                }

                if (found) {
                    Calib3d.drawChessboardCorners(frame, boardSize, corners, found);
                }

                OCV__LoadLibrary.mat2intarray(frame, pixels, w, h);
                impLive.draw();
                
                corners.release();
                if (IJ.escapePressed() || (impLive != null && !impLive.isVisible())) break;
            }
            
            if (impStack != null && impStack.getStackSize() > 0) {
                processStack(impStack);
            }

        } finally {
            if (cap != null) OCV__LoadLibrary.ReleaseCamera();
            frame.release();
            gray.release();
            diag.dispose();
            if (impLive != null) impLive.close();
        }
    }

    private boolean findPattern(Mat gray, Size size, MatOfPoint2f corners) {
        if (indPattern == 0) return Calib3d.findChessboardCorners(gray, size, corners);
        if (indPattern == 1) return Calib3d.findCirclesGrid(gray, size, corners, Calib3d.CALIB_CB_SYMMETRIC_GRID);
        if (indPattern == 2) return Calib3d.findCirclesGrid(gray, size, corners, Calib3d.CALIB_CB_ASYMMETRIC_GRID);
        return false;
    }

    private Mat createObjectPoints(Size size, double sqSize, int type) {
        Mat obj = new Mat((int) (size.width * size.height), 1, CvType.CV_32FC3);
        for (int i = 0; i < size.height; i++) {
            for (int j = 0; j < size.width; j++) {
                float x = (float)(j * sqSize);
                float y = (float)(i * sqSize);
                if (type == 2) x = (float)((2 * j + i % 2) * sqSize);
                obj.put((int) (i * size.width + j), 0, new float[]{x, y, 0.0f});
            }
        }
        return obj;
    }

    private JDialog createCameraControl() {
        JDialog diag = new JDialog((JDialog)null, "Control", false);
        JPanel panel = new JPanel();
        JButton btnCap = new JButton("Capture Frame");
        JButton btnDone = new JButton("Start Calibrate");

        btnCap.addActionListener(e -> flag_capture = true);
        btnDone.addActionListener(e -> { flag_fin_loop = true; diag.dispose(); });
        
        panel.add(btnCap);
        panel.add(btnDone);
        diag.add(panel);
        diag.setSize(250, 120);
        
        diag.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                flag_fin_loop = true;
            }
        });
        
        return diag;
    }

    private void saveResults() {
        try {
            mCalib.patternCols = patternCols;
            mCalib.patternRows = patternRows;
            mCalib.squareSize = squareSize;
            mCalib.patternType = indPattern;
            mCalib.winSize = winSize;
            mCalib.write();
            IJ.showStatus(className + ": Calibration saved \"" + calibName + "\" (Error: " + String.format("%.4f", mCalib.reprojectionError) + ")");
        } catch (IOException e) {
            OCV__LoadLibrary.logError(className, "Failed to save calibration files (" + e.getMessage() + ")");
        }
    }

    @Override public void setNPasses(int n) {}
    @Override public int setup(String arg, ImagePlus imp) { 
        if (!OCV__LoadLibrary.isLoad()) {
            OCV__LoadLibrary.logError("OCV_CameraCalib_1st_Create", "Library not loaded.");
            return DONE;
        }
        impActive = imp;
        return FLAGS; 
    }
}