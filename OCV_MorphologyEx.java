import ij.*;
import ij.IJ;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import java.awt.AWTEvent;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;
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
 * morphologyEx (OpenCV4.3.0).
 */
public class OCV_MorphologyEx implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener
{
    // constant var.
    private static final int FLAGS = DOES_8G | DOES_16 | DOES_32 | DOES_RGB | KEEP_PREVIEW;
    
    /*
     type of morphological operation

     * MORPH_ERODE:      erode
     * MORPH_DILATE:     dilate
     * MORPH_OPEN:       dst=dilate(erode(src,element))
     * MORPH_CLOSE:      dst=erode(dilate(src,element))
     * MORPH_GRADIENT:   dst=dilate(src,element)?erode(src,element)
     * MORPH_TOPHAT:     dst=src?open(src,element)
     * MORPH_BLACKHAT:   dst=close(src,element)?src
     * MORPH_HITMISS:    Only supported for CV_8UC1 binary images. A tutorial can be found in the documentation (I did not implement it because I could not understand it well.)
     */
    private static final int[] INT_OPERATION = { Imgproc.MORPH_ERODE, Imgproc.MORPH_DILATE, Imgproc.MORPH_OPEN, Imgproc.MORPH_CLOSE, Imgproc.MORPH_GRADIENT, Imgproc.MORPH_TOPHAT, Imgproc.MORPH_BLACKHAT };
    private static final String[] STR_OPERATION = { "MORPH_ERODE", "MORPH_DILATE", "MORPH_OPEN", "MORPH_CLOSE", "MORPH_GRADIENT", "MORPH_TOPHAT", "MORPH_BLACKHAT" };

    /*
     shape of the structuring element

     * MORPH_RECT:      a rectangular structuring element
     * MORPH_CROSS:     a cross-shaped structuring element:
     * MORPH_ELLIPSE:   an elliptic structuring element, that is, a filled ellipse inscribed into the rectangle Rect(0, 0, esize.width, 0.esize.height)
     */
    private static final int[] INT_SHAPERTYPE = { Imgproc.MORPH_RECT, Imgproc.MORPH_CROSS, Imgproc.MORPH_ELLIPSE };
    private static final String[] STR_SHAPERTYPE = { "MORPH_RECT", "MORPH_CROSS", "MORPH_ELLIPSE" };
    

    // staic var.
    private static int ksize_x = 3; // kernel size of x
    private static int ksize_y = 3; // kernel size of y
    private static int iterations  = 1; // Number of times erosion and dilation are applied.
    private static int indOperation = 0; // operation
    private static int indShapeType = 0; // shape type
    
    // var.
    private Mat kernel = null;
    private Point anchor = null;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr)
    {
        GenericDialog gd = new GenericDialog(command.trim() + " ...");
        
        gd.addChoice("", STR_OPERATION, STR_OPERATION[indOperation]);
        gd.addNumericField("ksize_x", ksize_x, 0);
        gd.addNumericField("ksize_y", ksize_y, 0);
        gd.addChoice("", STR_SHAPERTYPE, STR_SHAPERTYPE[indShapeType]);
        gd.addNumericField("iterations", iterations, 0);
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
        indOperation = (int)gd.getNextChoiceIndex();
        ksize_x = (int)gd.getNextNumber();
        ksize_y = (int)gd.getNextNumber();
        indShapeType = (int)gd.getNextChoiceIndex();
        iterations = (int)gd.getNextNumber();

        if(ksize_x < 0 || ksize_y < 0) { IJ.showStatus("'0 <= ksize_*' is necessary."); return false; }
        if(ksize_x % 2 == 0 || ksize_y % 2 == 0) { IJ.showStatus("'ksize_* is odd."); return false; }
        if(iterations < 1) { IJ.showStatus("'1 <= iterations' is necessary."); return false; }
        
        Size ksize = new Size(ksize_x, ksize_y);
        kernel = Imgproc.getStructuringElement(INT_SHAPERTYPE[indShapeType], ksize);
        
        anchor = new Point(-1,-1);
        
        IJ.showStatus("OCV_MorphologyEx");
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
            // srcdst
            int imw = ip.getWidth();
            int imh = ip.getHeight();
            byte[] srcdst_bytes = (byte[])ip.getPixels();
            
            // mat
            Mat src_mat = new Mat(imh, imw, CvType.CV_8UC1);            
            Mat dst_mat = new Mat(imh, imw, CvType.CV_8UC1);
            
            // run
            src_mat.put(0, 0, srcdst_bytes);
            Imgproc.morphologyEx(src_mat, dst_mat, INT_OPERATION[indOperation], kernel, anchor, iterations);
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
            Imgproc.morphologyEx(src_mat, dst_mat, INT_OPERATION[indOperation], kernel, anchor, iterations);
            dst_mat.get(0, 0, srcdst_shorts);        
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
            Imgproc.morphologyEx(src_mat, dst_mat, INT_OPERATION[indOperation], kernel, anchor, iterations);
            dst_mat.get(0, 0, srcdst_floats);        
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
            Imgproc.morphologyEx(src_mat, dst_mat, INT_OPERATION[indOperation], kernel, anchor, iterations);
            OCV__LoadLibrary.mat2intarray(dst_mat, srcdst_ints, imw, imh);
        }
        else
        {
            IJ.error("Wrong image format");
        }
    }
}
