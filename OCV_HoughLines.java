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
 * houghLines (OpenCV3.3.1).
 */
public class OCV_HoughLines implements ExtendedPlugInFilter, DialogListener
{
    // constant var.
    private static final int FLAGS = DOES_8G;
    private static final double CV_PI = 3.1415926535897932384626433832795;

    // static var.
    private static double resDist = 1;
    private static double resAngFact = 180;
    private static int minVotes = 1;
    private static double srn = 0;
    private static double stn = 0;
    private static double minDeg = 0;
    private static double maxDeg = 360;
    private static boolean enAddRoi = true;

    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner pfr)
    {
        GenericDialog gd = new GenericDialog(cmd.trim() + "...");

        gd.addNumericField("distance_resolution", resDist, 4);
        gd.addMessage("angle_resolution = CV_PI / angle_resolution_factor");
        gd.addNumericField("angle_resolution_factor", resAngFact, 4);
        gd.addNumericField("min_votes", minVotes, 0);
        gd.addNumericField("min_angle", minDeg, 4);
        gd.addNumericField("max_angle", maxDeg, 4);
        gd.addMessage("If both srn=0 and stn=0 , the classical Hough transform is used.\nOtherwise, the multi-scale Hough transform is used.");
        gd.addNumericField("srn", srn, 4);
        gd.addNumericField("stn", stn, 4);
        gd.addCheckbox("enable_add_roi", enAddRoi);
        gd.addDialogListener(this);

        gd.showDialog();

        if (gd.wasCanceled())
        {
            return DONE;
        }
        else
        {
            return FLAGS;
        }
    }

    @Override
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent awte)
    {
        resDist = (double)gd.getNextNumber();
        resAngFact = (double)gd.getNextNumber();
        minVotes = (int)gd.getNextNumber();
        minDeg = (double)gd.getNextNumber();
        maxDeg = (double)gd.getNextNumber();
        srn = (double)gd.getNextNumber();
        stn = (double)gd.getNextNumber();
        enAddRoi = gd.getNextBoolean();
        
        if(Double.isNaN(resDist) || Double.isNaN(resAngFact) || Double.isNaN(srn) || Double.isNaN(stn) || Double.isNaN(minDeg) || Double.isNaN(maxDeg)) { IJ.showStatus("ERR : NaN"); return false; }
        if(resDist < 0) { IJ.showStatus("'0 <= distance_resolution' is necessary."); return false; }
        if(resAngFact < 0) { IJ.showStatus("'0 <= angle_resolution_factor' is necessary."); return false; }
        if(minVotes < 0) { IJ.showStatus("'0 <= min_votes' is necessary."); return false; }
        if(srn < 0) { IJ.showStatus("'0 <= divisor_distance' is necessary."); return false; }
        if(stn < 0) { IJ.showStatus("'0 <= devisor_angle' is necessary."); return false; }
        if(minDeg < 0) { IJ.showStatus("'0 <= min_angle' is necessary."); return false; }
        if(maxDeg < 0) { IJ.showStatus("'0 <= max_angle' is necessary."); return false; }
        if(360 < minDeg) { IJ.showStatus("'min_angle <= 360' is necessary."); return false; }
        if(360 < maxDeg) { IJ.showStatus("'max_angle <= 360' is necessary."); return false; }
        if(maxDeg < minDeg) { IJ.showStatus("'min_angle <= max_angle' is necessary."); return false; }
        
        IJ.showStatus("OCV_HoughLines");
        return true;
    }
    
    @Override
    public void setNPasses(int arg0)
    {
        //do nothing
    }

    @Override
    public int setup(String arg0, ImagePlus imp)
    {
        if(!OCV__LoadLibrary.isLoad())
        {
            IJ.error("Library is not loaded.");
            return DONE;
        }
        
        if (imp == null)
        {
            IJ.noImage();
            return DONE;
        }
        else
        {
            return DOES_8G;
        }
    }

    @Override
    public void run(ImageProcessor ip)
    {
        // src
        int imw = ip.getWidth();
        int imh = ip.getHeight();
        byte[] src_ar = (byte[]) ip.getPixels();

        // mat
        Mat src_mat = new Mat(imh, imw, CvType.CV_8UC1);
        Mat dst_lines = new Mat();          

        // run
        src_mat.put(0, 0, src_ar);
        
        double resAng = CV_PI / resAngFact;
        double minTheta = CV_PI / 360.0 * minDeg;
        double maxTheta = CV_PI / 360.0 * maxDeg;
        Imgproc.HoughLines(src_mat, dst_lines, resDist, resAng, minVotes, srn, stn, minTheta, maxTheta);

        // fin
        showData(dst_lines, imw, imh);
    }

    // private
    private void showData(Mat lines, int imw, int imh)
    {
        // prepare the ResultsTable
        ResultsTable rt = OCV__LoadLibrary.GetResultsTable(true);
        
        // prepare the ROI Manager
        RoiManager roiMan = null;
        
        if(enAddRoi)
        {
            roiMan = OCV__LoadLibrary.GetRoiManager(true, true);
        }
       
        // show
        int num_lines = lines.rows();
        float[] res = new float[2];
        
        for(int i = 0; i < num_lines; i++)
        {
            lines.get(i, 0, res);
            
            float rho = res[0];
            float theta = res[1];
            double a = Math.cos(theta);
            double b = Math.sin(theta);
            double x0 = a * rho;
            double y0 = b * rho;
            double z = imw < imh ? imh : imw;
            double x1 = x0 + z * (-b);
            double y1 = y0 + z * a;
            double x2 = x0 - z * (-b);
            double y2 = y0 - z * (a);
            
            rt.incrementCounter();
            rt.addValue("No", i + 1);
            rt.addValue("theta", (double)theta / CV_PI * 180);
            rt.addValue("x1", x1);
            rt.addValue("y1", y1);
            rt.addValue("x2", x2);
            rt.addValue("y2", y2);

            if(enAddRoi && (roiMan != null))
            {
                Line roi = new Line(x1, y1, x2, y2);
                roiMan.addRoi(roi);
                roiMan.rename(i, "no" + String.valueOf(i + 1));
            }
        }

        rt.show("Results");
    }
}
