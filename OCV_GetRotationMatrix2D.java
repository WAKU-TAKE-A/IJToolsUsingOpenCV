import ij.IJ;
import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.measure.ResultsTable;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import java.awt.AWTEvent;
import org.opencv.core.Mat;
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
 * getRotationMatrix2D (OpenCV3.1).
 */
public class OCV_GetRotationMatrix2D implements ExtendedPlugInFilter, DialogListener
{
    // constant var.
    private static final int FLAGS = NO_IMAGE_REQUIRED;

    // staic var.
    private static double center_x = 0; // Center of the rotation in the source image (x)
    private static double center_y = 0; // Center of the rotation in the source image (y)
    private static double angle = 0; // Rotation angle in degrees
    private static double scale = 1; // Isotropic scale factor

    @Override
    public void setNPasses(int arg0)
    {
        // do nothing
    }

    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner prf)
    {
        GenericDialog gd = new GenericDialog(cmd.trim() + " ...");

        gd.addNumericField("center_x", center_x, 4);
        gd.addNumericField("center_y", center_y, 4);
        gd.addNumericField("angle", angle, 4);
        gd.addNumericField("scale", scale, 4);
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
        center_x = (double)gd.getNextNumber();
        center_y = (double)gd.getNextNumber();
        angle = (double)gd.getNextNumber();
        scale = (double)gd.getNextNumber();

        if(Double.isNaN(center_x) || Double.isNaN(center_y) || Double.isNaN(angle) || Double.isNaN(scale)) { IJ.showStatus("ERR : NaN"); return false; }
        if(scale <= 0) { IJ.showStatus("'0 < scale' is necessary."); return false; }

        IJ.showStatus("OCV_GetRotationMatrix2D");
        return true;
    }

    @Override
    public void run(ImageProcessor ip)
    {
        Mat mat = Imgproc.getRotationMatrix2D(new Point(center_x, center_y), angle, scale);

        if(mat == null || mat.rows() <= 0 || mat.cols() <= 0)
        {
            IJ.showMessage("Output is null or error");
            return;
        }

        // set the ResultsTable
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

        return FLAGS;
    }
}
