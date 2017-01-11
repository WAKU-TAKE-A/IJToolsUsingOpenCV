import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.Macro_Runner;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.RoiManager;
import ij.process.FloatPolygon;
import ij.process.ImageProcessor;
import java.util.ArrayList;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.imgproc.Imgproc;

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
 * getPerspectiveTransform (OpenCV3.1).
 */
public class OCV_GetPerspectiveTransform implements ExtendedPlugInFilter
{
    // constant var.
    private static final int FLAGS = NO_IMAGE_REQUIRED;
    
    // var.
    private RoiManager roiMan = null;
    private ArrayList<org.opencv.core.Point> lstPt_src = null;
    private ArrayList<org.opencv.core.Point> lstPt_dst = null;

    @Override
    public void setNPasses(int arg0)
    {
        // do nothing
    }

    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner prf)
    {
        // do nothing
        return FLAGS;
    }

    @Override
    public void run(ImageProcessor ip)
    {       
        MatOfPoint2f matPt_src = new MatOfPoint2f();
        MatOfPoint2f matPt_dst = new MatOfPoint2f();
        matPt_src.fromList(lstPt_src);
        matPt_dst.fromList(lstPt_dst);
        
        Mat mat = Imgproc.getPerspectiveTransform(matPt_src, matPt_dst);

        if(mat == null || mat.rows() <= 0 || mat.cols() <= 0)
        {
            IJ.showMessage("Output is null or error");
            return;
        }

        ResultsTable rt = OCV__LoadLibrary.GetResultsTable(true);
        
        for(int i = 0; i < 3; i++)
        {
            rt.incrementCounter();
            rt.addValue("Column01", String.valueOf(mat.get(i, 0)[0]));
            rt.addValue("Column02", String.valueOf(mat.get(i, 1)[0]));
            rt.addValue("Column03", String.valueOf(mat.get(i, 2)[0]));       
        }

        rt.show("Results");
    }

    @Override
    public int setup(String arg0, ImagePlus imp)
    {
        if(!OCV__LoadLibrary.isLoad)
        {
            IJ.error("Library is not loaded.");
            return DONE;
        }
        
        roiMan = OCV__LoadLibrary.GetRoiManager(false, true);
        
        if(roiMan == null || roiMan.getCount() < 2)
        {
            IJ.error("'2 <= RoiManager.getCount()' is necessary.");
            return DONE;
        }
        
        if(imp != null)
        {
            Macro_Runner mr = new Macro_Runner();
            mr.runMacro("run(\"Select None\");", "");
        }
        
       Roi roi_src = roiMan.getRoi(0);
       Roi roi_dst = roiMan.getRoi(1);
        //java.awt.Point[] pts_src = roi_src.getContainedPoints();
        java.awt.Point[] pts_src = getContainedPoints(roi_src);
       // java.awt.Point[] pts_dst = roi_dst.getContainedPoints();
        java.awt.Point[] pts_dst = getContainedPoints(roi_dst);
        
        if(pts_src.length != 4 || pts_dst.length != 4)
        {
            IJ.error("It is necessary that the number of point is four.");
            return DONE;
        }

        lstPt_src = new ArrayList<org.opencv.core.Point>();
        lstPt_dst = new ArrayList<org.opencv.core.Point>();
        
        for(int i = 0; i < 4; i++)
        {
            lstPt_src.add(new org.opencv.core.Point(pts_src[i].getX(), pts_src[i].getY()));
            lstPt_dst.add(new org.opencv.core.Point(pts_dst[i].getX(), pts_dst[i].getY()));
        }
        
        return FLAGS;
    }
    
    private java.awt.Point[] getContainedPoints(Roi roi)
    {
        FloatPolygon p = roi.getFloatPolygon();
        java.awt.Point[] points = new java.awt.Point[p.npoints];

        for (int i=0; i<p.npoints; i++)
        {
            points[i] = new java.awt.Point((int)Math.round(p.xpoints[i]),(int)Math.round(p.ypoints[i]));
        }

        return points;
    }
}
