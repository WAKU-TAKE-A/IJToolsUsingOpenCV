import ij.*;
import ij.IJ;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.FloatProcessor;
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
 *  cornerHarris (OpenCV4.2.0).
 */
public class OCV_CornerHarris implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener
{
    // constant var.
    private static final int FLAGS = DOES_8G | DOES_32 | KEEP_PREVIEW;
    
    /*
     Various border types, image boundaries are denoted with '|'

     * BORDER_CONSTANT:      iiiiii|abcdefgh|iiiiiii with some specified i
     * BORDER_REPLICATE:     aaaaaa|abcdefgh|hhhhhhh
     * BORDER_REFLECT:       fedcba|abcdefgh|hgfedcb
     * BORDER_REFLECT_101:   gfedcb|abcdefgh|gfedcba
     * BORDER_WRAP:          cdefgh|abcdefgh|abcdefg
     * BORDER_TRANSPARENT:   uvwxyz|abcdefgh|ijklmno
     * BORDER_ISOLATED:      do not look outside of ROI
     */
    private static final int[] INT_BORDERTYPE = { Core.BORDER_CONSTANT, Core.BORDER_REPLICATE, Core.BORDER_REFLECT, Core.BORDER_REFLECT101, Core.BORDER_WRAP, Core.BORDER_TRANSPARENT, Core.BORDER_ISOLATED };
    private static final String[] STR_BORDERTYPE = { "BORDER_CONSTANT", "BORDER_REPLICATE", "BORDER_REFLECT", "BORDER_REFLECT101", "BORDER_WRAP", "BORDER_TRANSPARENT", "BORDER_ISOLATED" };

    // staic var.
    private static int blockSize = 2; // Neighborhood size.
    private static int ksize = 3; // Aperture parameter for the Sobel operator.
    private static double k = 0.04; // Harris detector free parameter.
    private static int indBorderType = 2; // Border type

    // var
    private final String titleSrc = "";
    
    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr)
    {
        GenericDialog gd = new GenericDialog(command.trim() + " ...");
        
        gd.addNumericField("blockSize", blockSize, 0);
        gd.addNumericField("ksize", ksize, 0);
        gd.addNumericField("free_parame", k, 4);
        gd.addChoice("borderType", STR_BORDERTYPE, STR_BORDERTYPE[indBorderType]);
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
        blockSize = (int)gd.getNextNumber();
        ksize = (int)gd.getNextNumber();
        k = (double)gd.getNextNumber();
        indBorderType = (int)gd.getNextChoiceIndex();

        if(blockSize <= 0) { IJ.showStatus("'0 < ksize_x' is necessary."); return false; }
        if(ksize <= 0) { IJ.showStatus("'0 < ksize_y' is necessary."); return false; }
        if(Double.isNaN(k)) { IJ.showStatus("ERR : NaN"); return false; }
        
        IJ.showStatus("OCV_ CornerHarris");
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
            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip)
    {        
        if(ip.getBitDepth() == 8)
        {
            // src
            int imw = ip.getWidth();
            int imh = ip.getHeight();
            byte[] src_bytes = (byte[])ip.getPixels();
            
             // dst
            String titleDst = WindowManager.getUniqueName(titleSrc+ "_CornerHarris");
            ImagePlus impDst = new ImagePlus (titleDst, new FloatProcessor(imw, imh));
            float[] dst_floats = (float[]) impDst.getChannelProcessor().getPixels(); 
            
            // mat
            Mat src_mat = new Mat(imh, imw, CvType.CV_8UC1);            
            Mat dst_mat = new Mat(imh, imw, CvType.CV_32F);
            
            // run
            src_mat.put(0, 0, src_bytes);
            Imgproc.cornerHarris(src_mat, dst_mat, blockSize, ksize, k, INT_BORDERTYPE[indBorderType]);
            dst_mat.get(0, 0, dst_floats);
            
            // show
            impDst.show();
        }
        else if(ip.getBitDepth() == 32)
        {
            // src
            int imw = ip.getWidth();
            int imh = ip.getHeight();
            float[] src_floats = (float[])ip.getPixels();
            
             // dst
            String titleDst = WindowManager.getUniqueName(titleSrc+ "_CornerHarris");
            ImagePlus impDst = new ImagePlus (titleDst, new FloatProcessor(imw, imh));
            float[] dst_floats = (float[]) impDst.getChannelProcessor().getPixels();  
            
            // mat
            Mat src_mat = new Mat(imh, imw, CvType.CV_32F);            
            Mat dst_mat = new Mat(imh, imw, CvType.CV_32F);
            
            // run
            src_mat.put(0, 0, src_floats);
            Imgproc.cornerHarris(src_mat, dst_mat, blockSize, ksize, k, INT_BORDERTYPE[indBorderType]);
            dst_mat.get(0, 0, dst_floats);
            
            // show
            impDst.show();
        }
        else
        {
            IJ.error("Wrong image format");
        }
    }
}
