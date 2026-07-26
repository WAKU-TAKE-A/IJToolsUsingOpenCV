import ij.*;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.Line;
import ij.measure.ResultsTable;
import ij.plugin.filter.*;
import ij.plugin.frame.RoiManager;
import ij.process.*;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import java.awt.AWTEvent;

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
 * HoughLinesP.
 */
public class OCV_HoughLinesP implements ExtendedPlugInFilter, DialogListener {
    // constant var.
    private static final int FLAGS = DOES_8G;

    // static var.
    private static double resDist = 1;
    private static double resAngFact = 180;
    private static int minVotes = 1;
    private static double minLen = 1;
    private static double maxGap = 1;
    private String className = null;
    private static boolean enAddRoi = true;

    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner pfr) {
        className = cmd.trim();
        GenericDialog gd = new GenericDialog(className + "...");

        gd.addNumericField("distance_resolution", resDist, 4);
        gd.addMessage("angle_resolution = CV_PI / angle_resolution_factor");
        gd.addNumericField("angle_resolution_factor", resAngFact, 4);
        gd.addNumericField("min_votes", minVotes, 0);
        gd.addNumericField("min_length", minLen, 4);
        gd.addNumericField("max_allowed_gap", maxGap, 4);
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
        resDist = (double)gd.getNextNumber();
        resAngFact = (double)gd.getNextNumber();
        minVotes = (int)gd.getNextNumber();
        minLen = (double)gd.getNextNumber();
        maxGap = (double)gd.getNextNumber();
        enAddRoi = gd.getNextBoolean();

        if(Double.isNaN(resDist) || Double.isNaN(resAngFact) || Double.isNaN(minLen) || Double.isNaN(maxGap)) {
            IJ.showStatus("ERR : NaN");
            return false;
        }

        if(resDist < 0) {
            IJ.showStatus("'0 <= distance_resolution' is necessary.");
            return false;
        }

        if(resAngFact < 0) {
            IJ.showStatus("'0 <= angle_resolution_factor' is necessary.");
            return false;
        }

        if(minVotes < 0) {
            IJ.showStatus("'0 <= min_votes' is necessary.");
            return false;
        }

        if(minLen < 0) {
            IJ.showStatus("'0 <= min_length' is necessary.");
            return false;
        }

        if(maxGap < 0) {
            IJ.showStatus("'0 <= max_allowed_gap' is necessary.");
            return false;
        }

        IJ.showStatus("OCV_HoughLinesP");
        return true;
    }

    @Override
    public void setNPasses(int arg0) {
        //do nothing
    }

    @Override
    public int setup(String arg0, ImagePlus imp) {
        if(!OCV__LoadLibrary.isLoad()) {
            OCV__LoadLibrary.logError("OCV_HoughLinesP", "Library is not loaded.");
            return DONE;
        }

        if(imp == null) {
            IJ.noImage();
            return DONE;
        }
        else {
            return DOES_8G;
        }
    }

    @Override
    public void run(ImageProcessor ip) {
        Mat srcMat = null;
        Mat dstLines = null;

        try {
            // src
            int imw = ip.getWidth();
            int imh = ip.getHeight();
            byte[] srcAr = (byte[])ip.getPixels();

            // mat
            srcMat = new Mat(imh, imw, CvType.CV_8UC1);
            dstLines = new Mat();

            // run
            srcMat.put(0, 0, srcAr);
            Imgproc.HoughLinesP(srcMat, dstLines, resDist, Math.PI / resAngFact, minVotes, minLen, maxGap);

            // fin
            showData(dstLines);
        }
        catch(Exception e) {
            OCV__LoadLibrary.logError(className, "Hough lines P failed (" + e.getMessage() + ")");
        }
        finally {
            if(srcMat != null) {
                srcMat.release();
            }
            if(dstLines != null) {
                dstLines.release();
            }
        }
    }

    // private
    private void showData(Mat lines) {
        try {
            int numLines = (lines.rows() == 1) ? lines.cols() : lines.rows();

            if(numLines == 0) {
                OCV__LoadLibrary.logError(className, "No lines detected.");
                return;
            }

            // prepare the ResultsTable
            ResultsTable rt = OCV__LoadLibrary.GetResultsTable(true);

            // prepare the ROI Manager
            RoiManager roiMan = null;

            if(enAddRoi) {
                roiMan = OCV__LoadLibrary.GetRoiManager(true, true);
                if(roiMan == null) {
                    OCV__LoadLibrary.logError(className, "Failed to get ROI Manager.");
                }
            }

            // show
            int[] line = new int[4];

            for(int i = 0; i < numLines; i++) {
                int r = (lines.rows() == 1) ? 0 : i;
                int c = (lines.rows() == 1) ? i : 0;
                lines.get(r, c, line);

                int x1 = line[0];
                int y1 = line[1];
                int x2 = line[2];
                int y2 = line[3];

                rt.incrementCounter();
                rt.addValue("No", i + 1);
                rt.addValue("x1", x1);
                rt.addValue("y1", y1);
                rt.addValue("x2", x2);
                rt.addValue("y2", y2);

                if(enAddRoi && roiMan != null) {
                    Line roi = new Line(x1, y1, x2, y2);
                    roiMan.addRoi(roi);
                    roiMan.rename(i, "no" + String.valueOf(i + 1));
                }
            }

            rt.show("Results");
        }
        catch(Exception e) {
            OCV__LoadLibrary.logError(className, "Show data failed (" + e.getMessage() + ")");
        }
    }
}