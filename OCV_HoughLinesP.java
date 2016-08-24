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
 * houghLinesP (OpenCV3.1)
 * @version 0.9.4.0
 */
public class OCV_HoughLinesP implements ExtendedPlugInFilter, DialogListener
{
    // constant var.
    private static final int FLAGS = DOES_8G;
    private static final double CV_PI = 3.1415926535897932384626433832795;

    // static var.
    private static double resDist = 1;
    private static double resAngFact = 180;
    private static int minVotes = 1;
    private static double minLen = 1;
    private static double maxGap = 1;
    private static boolean enAddRoi = true;
    private static boolean enDispTbl = true;

    // var.
    private ImagePlus impSrc = null;

    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner pfr)
    {
        GenericDialog gd = new GenericDialog(cmd.trim() + "...");

        gd.addNumericField("distance_resolution", resDist, 4);
        gd.addMessage("angle_resolution = CV_PI / angle_resolution_factor");
        gd.addNumericField("angle_resolution_factor", resAngFact, 4);
        gd.addNumericField("min_votes", minVotes, 0);
        gd.addNumericField("min_length", minLen, 4);
        gd.addNumericField("max_allowed_gap", maxGap, 4);
        gd.addCheckbox("enable_add_roi", enAddRoi);
        gd.addCheckbox("enable_display_table", enDispTbl);
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
    public void setNPasses(int arg0)
    {
        //do nothing
    }

    @Override
    public int setup(String arg0, ImagePlus imp)
    {
        if(!OCV__LoadLibrary.isLoad)
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
            impSrc = imp;
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
        Imgproc.HoughLinesP(src_mat, dst_lines, resDist, CV_PI / resAngFact, minVotes, minLen, maxGap);

        // fin
        showData(dst_lines);
    }

    @Override
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent awte)
    {
        resDist = (double)gd.getNextNumber();
        resAngFact = (double)gd.getNextNumber();
        minVotes = (int)gd.getNextNumber();
        minLen = (double)gd.getNextNumber();
        maxGap = (double)gd.getNextNumber();
        enAddRoi = gd.getNextBoolean();
        enDispTbl = gd.getNextBoolean();

        if (resDist < 0 || resAngFact < 0 || minVotes < 0 || minLen < 0 || maxGap < 0)
        {
            return false;
        }
        else
        {
            return true;
        }
    }

    // private
    private void showData(Mat lines)
    {
        RoiManager roiManager = null;
        ResultsTable rt = null;

        // get ROI Manager
        if(enAddRoi)
        {
            Frame frame = WindowManager.getFrame("ROI Manager");

            if (frame==null)
            {
                IJ.run("ROI Manager...");
            }

            frame = WindowManager.getFrame("ROI Manager");
            roiManager = (RoiManager)frame;

            roiManager.reset();
            roiManager.runCommand("show none");
        }

        // get ResultsTable
        if(enDispTbl)
        {
            rt = ResultsTable.getResultsTable();

            if(rt == null || rt.getCounter() == 0)
            {
                rt = new ResultsTable();
            }

            rt.reset();
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
            
            if(enDispTbl && (rt != null))
            {          
                rt.incrementCounter();
                rt.addValue("x1", x1);
                rt.addValue("y1", y1);
                rt.addValue("x2", x2);
                rt.addValue("y2", y2);
            }

            if(enAddRoi && (roiManager != null))
            {
                Line roi = new Line(x1, y1, x2, y2);
                roiManager.addRoi(roi);
            }
        }

        if(enDispTbl && (rt != null))
        {
            rt.show("Results");
        }
    }
}
