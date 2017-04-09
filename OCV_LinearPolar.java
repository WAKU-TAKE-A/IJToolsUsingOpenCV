import ij.*;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.*;
import ij.process.*;
import java.awt.AWTEvent;
import java.awt.Rectangle;
import org.opencv.core.CvType;
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
 * linearPolar (OpenCV3.1).
 */
public class OCV_LinearPolar implements ExtendedPlugInFilter, DialogListener
{
    // const var.
    private final int FLAGS = DOES_8G | KEEP_PREVIEW;
    private static final int[] TYPE_INT = { Imgproc.INTER_NEAREST, Imgproc.INTER_LINEAR, Imgproc.INTER_CUBIC, Imgproc.INTER_AREA, Imgproc.INTER_LANCZOS4, Imgproc.WARP_FILL_OUTLIERS, Imgproc.WARP_INVERSE_MAP };
    private static final String[] TYPE_STR = { "INTER_NEAREST", "INTER_LINEAR", "INTER_CUBIC", "INTER_AREA", "INTER_LANCZOS4", "WARP_FILL_OUTLIERS", "WARP_INVERSE_MAP" };

    // static var.
    private static Rectangle rect = new Rectangle(0, 0, 0, 0);
    private static int cx = 0;
    private static int cy = 0;
    private static int rmax = 1;
    private static int type_ind = 0;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr)
    {
        rect = imp.getRoi().getBounds();

        GenericDialog gd = new GenericDialog(command.trim() + "...");

        gd.addNumericField("centerx", rect.getX() + rect.getWidth() / 2, 0);
        gd.addNumericField("centery", rect.getY() + rect.getHeight() /2, 0);
        gd.addNumericField("max_radius", rmax, 0);
        gd.addChoice("color", TYPE_STR, TYPE_STR[type_ind]);
        gd.addHelp(OCV__LoadLibrary.URL_HELP);
        gd.addPreviewCheckbox(pfr);
        gd.addDialogListener(this);

        gd.showDialog();

        if (gd.wasCanceled())
        {
            return DONE;
        }
        else
        {
            return IJ.setupDialog(imp, FLAGS);
        }
    }

    @Override
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent awte)
    {
        cx = (int)gd.getNextNumber();
        cy = (int)gd.getNextNumber();
        rmax = (int)gd.getNextNumber();
        type_ind = (int)gd.getNextChoiceIndex();

        if(cx < 0) { IJ.showStatus("'0 <= centerx' is necessary."); return false; }
        if(cy < 0) { IJ.showStatus("'0 <= centery' is necessary."); return false; }
        if(rmax <= 0) { IJ.showStatus("'0 <= max_radius' is necessary."); return false; }
        
        IJ.showStatus("OCV_LinearPolar");
        return true;
    }
    
    @Override
    public void setNPasses(int arg0)
    {
        // do nothing
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
            if(imp.getRoi() != null)
            {
                rect = imp.getRoi().getBounds();
            }
            else
            {
                rect = new Rectangle(0, 0, imp.getWidth(), imp.getHeight());
            }

            imp.setRoi(0, 0, imp.getWidth(), imp.getHeight());
            imp.setRoi(rect);

            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip)
    {
        // srcdst
        int imw = ip.getWidth();
        int imh = ip.getHeight();
        byte[] srcdst_ar = (byte[])ip.getPixels();
        
        // mat
        Mat src_mat = new Mat(imh, imw, CvType.CV_8UC1);
        Mat dst_mat = new Mat(imh, imw, CvType.CV_8UC1);      

        // run
        src_mat.put(0, 0, srcdst_ar);
        Imgproc.linearPolar(src_mat, dst_mat, new Point(cx, cy), (double)rmax, TYPE_INT[type_ind]);
        dst_mat.get(0, 0, srcdst_ar);
    }
}
