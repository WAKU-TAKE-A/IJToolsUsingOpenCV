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
import org.opencv.core.Point;
import org.opencv.core.Size;

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
 * blur (OpenCV3.1)
 */
public class OCV_Blur implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener
{
    // constant var.
    private static final int FLAGS = DOES_8G | DOES_RGB | DOES_32 | DOES_16 | KEEP_PREVIEW;
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
    private static double ksize_x = 3; // Blurring kernel size of x
    private static double ksize_y = 3; // Blurring kernel size of y
    private static int indBorderType = 1; // Border types.
    
    // var.
    private Size ksize = null;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr)
    {
        GenericDialog gd = new GenericDialog(command.trim() + " ...");
        
        gd.addNumericField("ksize_x", ksize_x, 4);
        gd.addNumericField("ksize_y", ksize_y, 4);
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
            Imgproc.blur(src_mat, dst_mat, ksize, new Point(-1, -1), INT_BORDERTYPE[indBorderType]);
            dst_mat.get(0, 0, srcdst_bytes);
        }
        else if(ip.getBitDepth() == 16)
        {
            // srcdst
            int imw = ip.getWidth();
            int imh = ip.getHeight();
            short[] srcdst_shorts = (short[])ip.getPixels();
            
            // mat
            Mat src_mat = new Mat(imh, imw, CvType.CV_16S);            
            Mat dst_mat = new Mat(imh, imw, CvType.CV_16S);
            
            // run
            src_mat.put(0, 0, srcdst_shorts);
            Imgproc.blur(src_mat, dst_mat, ksize, new Point(-1, -1), INT_BORDERTYPE[indBorderType]);
            dst_mat.get(0, 0, srcdst_shorts);        
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
            Imgproc.blur(src_mat, dst_mat, ksize, new Point(-1, -1), INT_BORDERTYPE[indBorderType]);
            OCV__LoadLibrary.mat2intarray(dst_mat, srcdst_ints, imw, imh);
        }
        else if(ip.getBitDepth() == 32)
        {
            // srcdst
            int imw = ip.getWidth();
            int imh = ip.getHeight();
            float[] srcdst_floats = (float[])ip.getPixels();
            
            // mat
            Mat src_mat = new Mat(imh, imw, CvType.CV_32F);            
            Mat dst_mat = new Mat(imh, imw, CvType.CV_32F);
            
            // run
            src_mat.put(0, 0, srcdst_floats);
            Imgproc.blur(src_mat, dst_mat, ksize, new Point(-1, -1), INT_BORDERTYPE[indBorderType]);
            dst_mat.get(0, 0, srcdst_floats);        
        }
        else
        {
            IJ.error("Wrong image format");
        }
    }

    @Override
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent awte)
    {
        ksize_x = (double)gd.getNextNumber();
        ksize_y = (double)gd.getNextNumber();
        indBorderType = (int)gd.getNextChoiceIndex();

        if((0 < ksize_x) && (0 < ksize_y))
        {
            ksize = new Size(ksize_x, ksize_y);
            return true;
        }
        else
        {
            return false;
        }
    }
}
