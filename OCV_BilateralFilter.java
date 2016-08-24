import ij.*;
import ij.IJ;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import java.awt.AWTEvent;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
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
 * bilateralFilter (OpenCV3.1)
 * @version 0.9.4.0
 */
public class OCV_BilateralFilter implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener
{
    // constant var.
    private static final int FLAGS = DOES_8G | DOES_RGB | KEEP_PREVIEW;
    /*
     Various border types, image boundaries are denoted with '|'

     * BORDER_REPLICATE:     aaaaaa|abcdefgh|hhhhhhh
     * BORDER_REFLECT:       fedcba|abcdefgh|hgfedcb
     * BORDER_REFLECT_101:   gfedcb|abcdefgh|gfedcba
     * BORDER_WRAP:          cdefgh|abcdefgh|abcdefg
     * BORDER_CONSTANT:      iiiiii|abcdefgh|iiiiiii  with some specified 'i'
     */
    private static final int[] INT_BORDERTYPE = { Core.BORDER_REPLICATE, Core.BORDER_REFLECT, Core.BORDER_REFLECT_101, Core.BORDER_WRAP, Core.BORDER_CONSTANT };
    private static final String[] STR_BORDERTYPE = { "BORDER_REPLICATE", "BORDER_REFLECT", "BORDER_REFLECT_101", "BORDER_WRAP", "BORDER_CONSTANT" };

    // staic var.
    private static int diameter = 5; // Diameter of each pixel neighborhood that is used during filtering.
    private static double sigmaColor  = 15; // Filter sigma in the color space.
    private static double sigmaSpace  = 8; // Filter sigma in the coordinate space.
    private static int indBorderType = 2; // Border types.

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr)
    {
        GenericDialog gd = new GenericDialog(command.trim() + " ...");
        
        gd.addMessage("If diameter is negative, it is computed from sigmaSpace.");
        gd.addNumericField("diameter", diameter, 0);
        gd.addNumericField("sigma_color", sigmaColor, 3);
        gd.addNumericField("sigma_space", sigmaSpace, 3);
        gd.addChoice("border_type", STR_BORDERTYPE, STR_BORDERTYPE[indBorderType]);
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
    public void setNPasses(int nPasses)
    {
        // do nothing
    }

    @Override
    public int setup(String arg, ImagePlus imp)
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
            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip)
    {
        if(ip.getBitDepth() == 8)
        {
            // srcdst
            int imw = ip.getWidth();
            int imh = ip.getHeight();
            byte[] srcdst_bytes = (byte[])ip.getPixels();
            
            // mat
            Mat src_mat = new Mat(imh, imw, CvType.CV_8UC1);            
            Mat dst_mat = new Mat(imh, imw, CvType.CV_8UC1);
            
            // run
            src_mat.put(0, 0, srcdst_bytes);
            Imgproc.bilateralFilter(src_mat, dst_mat, diameter, sigmaColor, sigmaSpace, INT_BORDERTYPE[indBorderType]);
            dst_mat.get(0, 0, srcdst_bytes);
        }
        else if(ip.getBitDepth() == 24)
        {
            // dst
            int imw = ip.getWidth();
            int imh = ip.getHeight();
            int[] srcdst_ints = (int[])ip.getPixels();
            
            // mat
            Mat src_mat = new Mat(imh, imw, CvType.CV_8UC3);            
            Mat dst_mat = new Mat(imh, imw, CvType.CV_8UC3);
         
            // run
            OCV__LoadLibrary.intarray2mat(srcdst_ints, src_mat, imw, imh);
            Imgproc.bilateralFilter(src_mat, dst_mat, diameter, sigmaColor, sigmaSpace, INT_BORDERTYPE[indBorderType]);
            OCV__LoadLibrary.mat2intarray(dst_mat, srcdst_ints, imw, imh);
        }
        else
        {
            IJ.error("Wrong image format");
        }
    }

    @Override
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent awte)
    {
        diameter = (int)gd.getNextNumber();
        sigmaColor = (double)gd.getNextNumber();
        sigmaSpace = (double)gd.getNextNumber();
        indBorderType = (int)gd.getNextChoiceIndex();

        if(0 < sigmaColor && 0 < sigmaSpace)
        {
            return true;
        }
        else
        {
            return false;
        }
    }
}
