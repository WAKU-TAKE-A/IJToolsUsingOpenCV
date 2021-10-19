import ij.*;
import ij.IJ;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import java.awt.AWTEvent;
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
 * medianBlur (OpenCV4.5.3).
 */
public class OCV_MedianBlur implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener
{
    // constant var.
    private static final int FLAGS = DOES_8G | DOES_RGB | DOES_16 | DOES_32 | KEEP_PREVIEW;
    
    // staic var.
    private static int ksize = 3; // Blurring kernel size of x
    
    // var.
    private int bitDepth;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr)
    {
        GenericDialog gd = new GenericDialog(command.trim() + " ...");
        bitDepth = imp.getBitDepth();
        
        gd.addNumericField("ksize", ksize, 0);
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
        ksize = (int)gd.getNextNumber();

        if(ksize < 3) { IJ.showStatus("'3 <= ksize' is necessary."); return false; }
        if(ksize % 2 == 0) { IJ.showStatus("ksize must be odd."); return false; }
        if((bitDepth == 16 || bitDepth == 32) && 5 < ksize) { IJ.showStatus("When an image of type is 16bit or 32bit, 'ksize <= 5' is necessary."); return false; }
        
        IJ.showStatus("OCV_MedianBlur");
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
        if(bitDepth == 8)
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
            Imgproc.medianBlur(src_mat, dst_mat, (int)ksize);
            dst_mat.get(0, 0, srcdst_bytes);
        }
        else if(bitDepth == 16)
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
            Imgproc.medianBlur(src_mat, dst_mat, (int)ksize);
            dst_mat.get(0, 0, srcdst_shorts);        
        }
        else if(bitDepth == 24)
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
            Imgproc.medianBlur(src_mat, dst_mat, (int)ksize);
            OCV__LoadLibrary.mat2intarray(dst_mat, srcdst_ints, imw, imh);
        }
        else if(bitDepth == 32)
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
            Imgproc.medianBlur(src_mat, dst_mat, (int)ksize);
            dst_mat.get(0, 0, srcdst_floats);        
        }
        else
        {
            IJ.error("Wrong image format");
        }
    }
}
