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
 * HoughCircles (OpenCV4.5.3).
 */
public class OCV_HoughCircles implements ExtendedPlugInFilter, DialogListener
{
    // constant var.
    private static final int FLAGS = DOES_8G;
    private static final double CV_PI = 3.1415926535897932384626433832795;
    private static final int[] INT_HOUGHMETHOD = { Imgproc.HOUGH_GRADIENT, Imgproc.HOUGH_GRADIENT_ALT };
    private static final String[] STR_HOUGHMETHOD = { "HOUGH_GRADIENT", "HOUGH_GRADIENT_ALT" };

    // static var.    
    private static int indMethod  = 1;
    private static double dp = 1;
    private static double minDist = 1;  
    private static double param1 = 100;
    private static double param2 = 100;
    private static int minRadius = 0;
    private static int maxRadius = 0;
    private static boolean enAddRoi = true;

    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner pfr)
    {
        GenericDialog gd = new GenericDialog(cmd.trim() + "...");
     
        gd.addChoice("method", STR_HOUGHMETHOD, STR_HOUGHMETHOD[indMethod]);
        gd.addMessage("'dp' is the inverse ratio of the resolution.");
        gd.addNumericField("dp", dp, 4);
        gd.addNumericField("minDist", minDist, 4);
        gd.addMessage("'param1' is the higher threshold of the two passed to the Canny edge detector.");
        gd.addNumericField("param1", param1, 4);
        gd.addMessage("HOUGH_GRADIENT: 'param2' is the accumulator threshold for the circle centers.");
        gd.addMessage("HOUGH_GRADIENT_ALT: 'param2' is the circle 'perfectness'.");
        gd.addNumericField("param2", param2, 4);
        gd.addNumericField("minRadius", minRadius, 0);
        gd.addNumericField("maxRadius", maxRadius, 0);
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
        indMethod = (int)gd.getNextChoiceIndex();
        dp = (double)gd.getNextNumber();
        minDist = (double)gd.getNextNumber();
        param1 = (double)gd.getNextNumber();
        param2 = (double)gd.getNextNumber();
        minRadius = (int)gd.getNextNumber();
        maxRadius = (int)gd.getNextNumber();
        enAddRoi = gd.getNextBoolean();
        
        if(Double.isNaN(dp) || Double.isNaN(minDist) || Double.isNaN(param1) || Double.isNaN(param2)) { IJ.showStatus("ERR : NaN"); return false; }
        if(dp <= 0) { IJ.showStatus("'0 < dp' is necessary."); return false; }
        if(minDist < 0) { IJ.showStatus("'0 <= minDist' is necessary."); return false; }
        if(param1 < 0) { IJ.showStatus("'0 <= param1' is necessary."); return false; }
        if(param2 < 0) { IJ.showStatus("'0 <= param2' is necessary."); return false; }
        if(minRadius < 0) { IJ.showStatus("'0 <= minRadius' is necessary."); return false; }
        if(maxRadius < 0) { IJ.showStatus("'0 <= maxRadius' is necessary."); return false; }
        
        IJ.showStatus("OCV_HoughCircles");
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
        Mat dst_circles = new Mat();          

        // run
        src_mat.put(0, 0, src_ar);
        
        if(param1 == 0)
        {
            Imgproc.HoughCircles(src_mat, dst_circles, INT_HOUGHMETHOD[indMethod], dp, minDist);
        }
        else if(param1 != 0 && param2 == 0)
        {
            Imgproc.HoughCircles(src_mat, dst_circles, INT_HOUGHMETHOD[indMethod], dp, minDist, param1);
        }
        else if(param1 != 0 && param2 != 0 && minRadius == 0)
        {
            Imgproc.HoughCircles(src_mat, dst_circles, INT_HOUGHMETHOD[indMethod], dp, minDist, param1, param2);
        }
        else if(param1 != 0 && param2 != 0 && minRadius != 0 && maxRadius == 0)
        {
            Imgproc.HoughCircles(src_mat, dst_circles, INT_HOUGHMETHOD[indMethod], dp, minDist, param1, param2, minRadius);
        }
        else if(param1 != 0 && param2 != 0 && minRadius != 0 && maxRadius != 0)
        {
            Imgproc.HoughCircles(src_mat, dst_circles, INT_HOUGHMETHOD[indMethod], dp, minDist, param1, param2, minRadius, maxRadius);
        }
        
        
        // fin
        showData(dst_circles);
    }

    // private
    private void showData(Mat lines)
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
        int[] line = new int[4];
        
        for(int i = 0; i < num_lines; i++)
        {
            lines.get(i, 0, line);
            
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
