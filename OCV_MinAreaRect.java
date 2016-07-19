import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import java.util.ArrayList;
import java.util.List;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.imgproc.Imgproc;

/*
 * The MIT License
 *
 * Copyright 2016 WAKU_TAKE_A.
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
 * minAreaRect (OpenCV3.1)
 * @author WAKU_TAKE_A
 * @version 0.9.1.0
 */
public class OCV_MinAreaRect implements ExtendedPlugInFilter
{
    // static var.
    private static boolean enSetRoi;
    private static boolean enRefTbl = true;

    // var.
    private ImagePlus impSrc = null;
    private String name_cmd = null;
    private int nPass;

    /*
     * @see ij.plugin.filter.ExtendedPlugInFilter#setNPasses(int)
     */
    @Override
    public void setNPasses(int arg0)
    {
        nPass = arg0;
    }

    /*
     * @see ij.plugin.filter.ExtendedPlugInFilter#showDialog(ij.ImagePlus, java.lang.String, ij.plugin.filter.PlugInFilterRunner)
     */
    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner prf)
    {
        name_cmd = cmd;

        GenericDialog gd = new GenericDialog(name_cmd + "...");
        gd.addCheckbox("enable_set_roi", enSetRoi);
        gd.addCheckbox("enable_refresh_table", enRefTbl);
        gd.showDialog();

        if (gd.wasCanceled())
        {
            return DONE;
        }
        else
        {
            enSetRoi = (boolean)gd.getNextBoolean();
            enRefTbl = (boolean)gd.getNextBoolean();

            return IJ.setupDialog(imp, DOES_8G); // Displays a "Process all images?" dialog
        }
    }

    /*
     * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
     */
    @Override
    public void run(ImageProcessor ip)
    {
        byte[] byteArray = (byte[])ip.getPixels();
        int w = ip.getWidth();
        int h = ip.getHeight();

        List<Point> lstPt = new ArrayList<Point>();
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
        RotatedRect rect = Imgproc.minAreaRect(pts);
        showData(rect);
    }

    /*
     * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
     */
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
            this.impSrc = imp;
            return DOES_8G;
        }
    }

    private void showData(RotatedRect rect)
    {
        ResultsTable rt = ResultsTable.getResultsTable();

        if(rt == null || rt.getCounter() == 0)
        {
            rt = new ResultsTable();
        }

        if(enSetRoi)
        {
            float[] xPoints = new float[4];
            float[] yPoints = new float[4];
            double cx = rect.center.x;
            double cy = rect.center.y;
            double w = rect.size.width;
            double h = rect.size.height;
            double rad =  rect.angle * Math.PI / 180;
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);

            xPoints[0] = (float)((w / 2.0) * cos - (h / 2.0) * sin + cx);
            yPoints[0] = (float)((w / 2.0) * sin + (h / 2.0) * cos + cy);
            xPoints[1] = (float)(((-1) * w / 2.0) * cos - (h / 2.0) * sin + cx);
            yPoints[1] = (float)(((-1) * w / 2.0) * sin + (h / 2.0) * cos + cy);
            xPoints[2] = (float)(((-1) * w / 2.0) * cos - ((-1) * h / 2.0) * sin + cx);
            yPoints[2] = (float)(((-1) * w / 2.0) * sin + ((-1) * h / 2.0) * cos + cy);
            xPoints[3] = (float)((w / 2.0) * cos - ((-1) * h / 2.0) * sin + cx);
            yPoints[3] = (float)((w / 2.0) * sin + ((-1) * h / 2.0) * cos + cy);

            PolygonRoi proi = new PolygonRoi(xPoints, yPoints, Roi.POLYGON);
            impSrc.setRoi(proi);
        }

        if(enRefTbl && 1 == nPass)
        {
            rt.reset();
        }

        rt.incrementCounter();
        rt.addValue("CenterX", rect.center.x);
        rt.addValue("CenterY", rect.center.y);
        rt.addValue("Width", rect.size.width);
        rt.addValue("Height", rect.size.height);
        rt.addValue("Angle", rect.angle);
        rt.show("Results");
    }
}
