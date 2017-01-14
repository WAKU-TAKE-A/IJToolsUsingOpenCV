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
 * bilateralFilter (OpenCV3.1).
 */
public class OCV_BilateralFilter implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener
{
    // constant var.
    private static final int FLAGS = DOES_8G | DOES_RGB | DOES_32 | KEEP_PREVIEW; // 8-bit or floating-point, 1-channel or 3-channel image.
    /*
     Various border types, image boundaries are denoted with '|'

     * BORDER_ISOLATED:      can not use
     * BORDER_REFLECT:       fedcba|abcdefgh|hgfedcb
     * BORDER_REFLECT_101:   gfedcb|abcdefgh|gfedcba
     * BORDER_REPLICATE:     aaaaaa|abcdefgh|hhhhhhh
     * BORDER_WRAP:          can not use
     * BORDER_TRANSPARENT    can not use
     */
    private static final int[] INT_BORDERTYPE = { Core.BORDER_REFLECT, Core.BORDER_REFLECT101, Core.BORDER_REPLICATE };
    private static final String[] STR_BORDERTYPE = { "BORDER_REFLECT", "BORDER_REFLECT101", "BORDER_REPLICATE" };

    // staic var.
    private static int diameter = 5; // Diameter of each pixel neighborhood that is used during filtering.
    private static double sigmaColor  = 15; // Filter sigma in the color space.
    private static double sigmaSpace  = 8; // Filter sigma in the coordinate space.
    private static int indBorderType = 1; // Border types.

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr)
    {
        GenericDialog gd = new GenericDialog(command.trim() + " ...");
        
        gd.addMessage("If diameter is negative, it is computed from sigmaSpace.");
        gd.addNumericField("diameter", diameter, 0);
        gd.addNumericField("sigmaColor", sigmaColor, 4);
        gd.addNumericField("sigmaSpace", sigmaSpace, 4);
        gd.addChoice("borderType", STR_BORDERTYPE, STR_BORDERTYPE[indBorderType]);
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
        diameter = (int)gd.getNextNumber();
        sigmaColor = (double)gd.getNextNumber();
        sigmaSpace = (double)gd.getNextNumber();
        indBorderType = (int)gd.getNextChoiceIndex();

        if(Double.isNaN(sigmaColor) || Double.isNaN(sigmaSpace)) { IJ.showStatus("ERR : NaN"); return false; }
        if(sigmaColor <= 0) { IJ.showStatus("'0 < sigmaColor' is necessary."); return false; }
        if(sigmaSpace <= 0) { IJ.showStatus("'0 < sigmaSpace' is necessary."); return false; }
        
        IJ.showStatus("OCV_BilateralFilter");
        return true;
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
        else if(ip.getBitDepth() == 32)
        {
             // srcdst
            int imw = ip.getWidth();
            int imh = ip.getHeight();
            float[] srcdst_bytes = (float[])ip.getPixels();
            
            // mat
            Mat src_mat = new Mat(imh, imw, CvType.CV_32FC1);            
            Mat dst_mat = new Mat(imh, imw, CvType.CV_32FC1);
            
            // run
            src_mat.put(0, 0, srcdst_bytes);
            Imgproc.bilateralFilter(src_mat, dst_mat, diameter, sigmaColor, sigmaSpace, INT_BORDERTYPE[indBorderType]);
            dst_mat.get(0, 0, srcdst_bytes);           
        }
        else
        {
            IJ.error("Wrong image format");
        }
    }
}
