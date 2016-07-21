import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import java.util.ArrayList;
import java.util.List;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
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
 * boundingRect (OpenCV3.1)
 * @version 0.9.2.0
 */
public class OCV_BoundingRect implements ExtendedPlugInFilter
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
        MatOfPoint pts = new MatOfPoint();

        for(int y = 0; y < h; y++)
        {
            for(int x = 0; x < w; x++)
            {
                if(byteArray[x + w * y] != 0)
                {
                    lstPt.add(new Point(x, y));
                }
            }
        }

        if(lstPt.isEmpty())
        {
                return;
        }

        pts.fromList(lstPt);
        Rect rect = Imgproc.boundingRect(pts);
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

    private void showData(Rect rect)
    {

        ResultsTable rt = ResultsTable.getResultsTable();

        if(rt == null || rt.getCounter() == 0)
        {
            rt = new ResultsTable();
        }

        if(enSetRoi)
        {
            Roi roi = new Roi(rect.x, rect.y, rect.width, rect.height);
            impSrc.setRoi(roi);
        }

        if(enRefTbl && 1 == nPass)
        {
            rt.reset();
        }

        rt.incrementCounter();
        rt.addValue("BX", rect.x);
        rt.addValue("BY", rect.y);
        rt.addValue("Width", rect.width);
        rt.addValue("Height", rect.height);
        rt.show("Results");
    }
}
