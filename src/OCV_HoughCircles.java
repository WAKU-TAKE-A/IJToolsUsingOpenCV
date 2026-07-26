import ij.*;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.OvalRoi;
import ij.measure.ResultsTable;
import ij.plugin.filter.*;
import ij.plugin.frame.RoiManager;
import ij.process.*;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import java.awt.AWTEvent;
import java.awt.Frame;

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
 * HoughCircles.
 */
public class OCV_HoughCircles implements ExtendedPlugInFilter, DialogListener {
    // constant var.
    private static final int FLAGS = DOES_8G;
    private static final String[] STR_METHOD = { "HOUGH_GRADIENT", "HOUGH_GRADIENT_ALT" };
    private static final int[] INT_METHOD = { Imgproc.HOUGH_GRADIENT, Imgproc.HOUGH_GRADIENT_ALT };

    // static var.
    private static int indMethod = 0;
    private static double dp = 1.0;
    private static double minDist = 20.0;
    private static double param1 = 100.0;
    private static double param2 = 100.0;
    private static int minRadius = 0;
    private static int maxRadius = 0;
    private static boolean enAddRoi = true;

    // var.
    private String className;

    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner pfr) {
        className = cmd.trim();
        GenericDialog gd = new GenericDialog(className + " ...");

        gd.addChoice("method", STR_METHOD, STR_METHOD[indMethod]);
        gd.addNumericField("dp", dp, 4);
        gd.addNumericField("minDist", minDist, 4);
        gd.addNumericField("param1", param1, 4);
        gd.addNumericField("param2", param2, 4);
        gd.addNumericField("minRadius", minRadius, 0);
        gd.addNumericField("maxRadius", maxRadius, 0);
        gd.addCheckbox("enable_add_roi", enAddRoi);
        gd.addDialogListener(this);

        gd.showDialog();

        if(gd.wasCanceled()) {
            return DONE;
        }
        else {
            return FLAGS;
        }
    }

    @Override
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent awte) {
        indMethod = gd.getNextChoiceIndex();
        dp = (double)gd.getNextNumber();
        minDist = (double)gd.getNextNumber();
        param1 = (double)gd.getNextNumber();
        param2 = (double)gd.getNextNumber();
        minRadius = (int)gd.getNextNumber();
        maxRadius = (int)gd.getNextNumber();
        enAddRoi = gd.getNextBoolean();

        if(dp <= 0) {
            IJ.showStatus("'0 < dp' is necessary.");
            return false;
        }

        if(minDist <= 0) {
            IJ.showStatus("'0 < minDist' is necessary.");
            return false;
        }

        if(param1 <= 0) {
            IJ.showStatus("'0 < param1' is necessary.");
            return false;
        }

        if(param2 <= 0) {
            IJ.showStatus("'0 < param2' is necessary.");
            return false;
        }

        if(minRadius < 0) {
            IJ.showStatus("'0 <= minRadius' is necessary.");
            return false;
        }

        if(maxRadius < 0) {
            IJ.showStatus("'0 <= maxRadius' is necessary.");
            return false;
        }

        IJ.showStatus("OCV_HoughCircles");
        return true;
    }

    @Override
    public void setNPasses(int arg0) {
        //do nothing
    }

    @Override
    public int setup(String arg0, ImagePlus imp) {
        if(!OCV__LoadLibrary.isLoad()) {
            IJ.error("Library is not loaded.");
            return DONE;
        }

        if(imp == null) {
            IJ.noImage();
            return DONE;
        }
        else {
            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip) {
        // src
        int imw = ip.getWidth();
        int imh = ip.getHeight();
        byte[] src_ar = (byte[])ip.getPixels();
        Mat srcMat = new Mat(imh, imw, CvType.CV_8UC1);

        // dst
        Mat dstCircles = new Mat();

        try {
            srcMat.put(0, 0, src_ar);
            int method = INT_METHOD[indMethod];
            Imgproc.HoughCircles(srcMat, dstCircles, method, dp, minDist, param1, param2, minRadius, maxRadius);
            showData(dstCircles);
        }
        catch(Exception e) {
            OCV__LoadLibrary.logError(className, "Hough circles failed (" + e.getMessage() + ")");
        }
    }

    // private
    private void showData(Mat circles) {
        try {
            int numCircles = (circles.rows() == 1) ? circles.cols() : circles.rows();

            if(numCircles == 0) {
                OCV__LoadLibrary.logError(className, "No circles detected.");
                return;
            }

            // prepare the ResultsTable
            ResultsTable rt = getResultsTable(true);

            // prepare the ROI Manager
            RoiManager roiMan = null;

            if(enAddRoi) {
                roiMan = getRoiManager(true, true);
            }

            // show
            int channels = circles.channels();
            float[] data = new float[numCircles * channels];
            circles.get(0, 0, data);

            for(int i = 0; i < numCircles; i++) {
                float x = data[i * channels + 0];
                float y = data[i * channels + 1];
                float radius = data[i * channels + 2];
                float dia = 2 * radius;

                rt.incrementCounter();
                rt.addValue("CenterX", x);
                rt.addValue("CenterY", y);
                rt.addValue("Radius", radius);

                if(enAddRoi && (null != roiMan)) {
                    OvalRoi roi = new OvalRoi((x - radius), (y - radius), dia, dia);
                    roiMan.addRoi(roi);
                }
            }

            rt.show("Results");
        }
        catch(Exception e) {
            OCV__LoadLibrary.logError(className, "showData failed (" + e.getMessage() + ")");
        }
    }

    /**
     * get the ResultsTable or create a new ResultsTable
     * @param enReset reset or not
     * @return ResultsTable
     */
    private ResultsTable getResultsTable(boolean enReset) {
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
    private RoiManager getRoiManager(boolean enReset, boolean enShowNone) {
        Frame frame = WindowManager.getFrame("ROI Manager");
        RoiManager rm = null;

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
}
