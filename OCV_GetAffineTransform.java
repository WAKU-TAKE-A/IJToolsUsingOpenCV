import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import java.util.ArrayList;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
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
 * getAffineTransform (OpenCV3.1).
 */
public class OCV_GetAffineTransform implements ExtendedPlugInFilter
{
    // constant var.
    private static final int FLAGS = NO_IMAGE_REQUIRED;
    
    // var.
    private RoiManager roiMan = null;
    private ArrayList<Point> lstPt_src = null;
    private ArrayList<Point> lstPt_dst = null;

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
        
        Mat mat = Imgproc.getAffineTransform(matPt_src, matPt_dst);

        if(mat == null || mat.rows() <= 0 || mat.cols() <= 0)
        {
            IJ.showMessage("Output is null or error");
            return;
        }

        ResultsTable rt = OCV__LoadLibrary.GetResultsTable(true);
        rt.incrementCounter();
        rt.addValue("Column01", String.valueOf(mat.get(0, 0)[0]));
        rt.addValue("Column02", String.valueOf(mat.get(0, 1)[0]));
        rt.addValue("Column03", String.valueOf(mat.get(0, 2)[0]));
         rt.incrementCounter();
        rt.addValue("Column01", String.valueOf(mat.get(1, 0)[0]));
        rt.addValue("Column02", String.valueOf(mat.get(1, 1)[0]));
        rt.addValue("Column03", String.valueOf(mat.get(1, 2)[0]));
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
        
        Roi roi_src = roiMan.getRoi(0);
        Roi roi_dst = roiMan.getRoi(1); 
        lstPt_src = new ArrayList<Point>();
        lstPt_dst = new ArrayList<Point>();
        OCV__LoadLibrary.GetCoordinates(roi_src, lstPt_src);
        OCV__LoadLibrary.GetCoordinates(roi_dst, lstPt_dst);
        
        if(lstPt_src.size() != 3 || lstPt_dst.size() != 3)
        {
            IJ.error("It is necessary that the number of point is 3.");
            return DONE;
        }

        return FLAGS;
    }
}
