import ij.IJ;
import ij.ImagePlus;
import ij.gui.EllipseRoi;
import ij.gui.GenericDialog;
import ij.measure.ResultsTable;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import java.util.ArrayList;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
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
 * fitEllipse (OpenCV3.1)
 * @version 0.9.6.1
 */
public class OCV_FitEllipse implements ExtendedPlugInFilter
{
    // static var.
    private static boolean enRefData = false;

    // var.
    private ImagePlus impSrc = null;
    private ResultsTable rt = null;
    private RoiManager roiMan = null;

    @Override
    public void setNPasses(int arg0)
    {
        // do nothing
    }

    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner prf)
    {
        GenericDialog gd = new GenericDialog(cmd.trim() + "...");
        gd.addCheckbox("enable_refresh_data", enRefData);
        gd.showDialog();

        if (gd.wasCanceled())
        {
            return DONE;
        }
        else
        {
            enRefData = (boolean)gd.getNextBoolean();
            
            if(enRefData)
            {
                rt.reset();
                roiMan.reset();
            }

            return IJ.setupDialog(imp, DOES_8G); // Displays a "Process all images?" dialog
        }
    }

    @Override
    public void run(ImageProcessor ip)
    {
        byte[] byteArray = (byte[])ip.getPixels();
        int w = ip.getWidth();
        int h = ip.getHeight();
        int num_slice = ip.getSliceNumber();
        
        ArrayList<Point> lstPt = new ArrayList<Point>();
        MatOfPoint2f pts = new MatOfPoint2f();

        for(int y = 0; y < h; y++)
        {
            for(int x = 0; x < w; x++)
            {
                if(byteArray[x + w * y] != 0)
                {
                    lstPt.add(new Point((double)x, (double)y));
                }
            }
        }

        if(lstPt.isEmpty())
        {
            return;
        }

        pts.fromList(lstPt);
        RotatedRect rect =  Imgproc.fitEllipse(pts);
        showData(rect, num_slice);
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
            rt = OCV__LoadLibrary.GetResultsTable(false);
            roiMan = OCV__LoadLibrary.GetRoiManager(false, true);
            return DOES_8G;
        }
    }

    private void showData(RotatedRect rect, int num_slice)
    {
        // set the ResultsTable
        rt.incrementCounter();
        rt.addValue("CenterX", rect.center.x);
        rt.addValue("CenterY", rect.center.y);
        rt.addValue("Width", rect.size.width);
        rt.addValue("Height", rect.size.height);
        rt.addValue("Angle", rect.angle);
        rt.show("Results");
        
        // ser the ROI Manager
       double[] xPoints = new double[2];
        double[] yPoints = new double[2];
        double cx = rect.center.x;
        double cy = rect.center.y;
        double w = rect.size.width;
        double h = rect.size.height;
        double rad =  rect.angle * Math.PI / 180;
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double ratio = w / h;

        xPoints[0] = (float)((0) * cos - (h / 2.0) * sin + cx);
        yPoints[0] = (float)((0) * sin + (h / 2.0) * cos + cy);
        xPoints[1] = (float)((0) * cos - ((-1) * h / 2.0) * sin + cx);
        yPoints[1] = (float)((0) * sin + ((-1) * h / 2.0) * cos + cy);

        impSrc.setSlice(num_slice);
        EllipseRoi eroi = new EllipseRoi(xPoints[0], yPoints[0], xPoints[1], yPoints[1], ratio);
        roiMan.addRoi(eroi);
    }
}
