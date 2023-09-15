import ij.*;
import ij.IJ;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.ImageCalculator;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import java.awt.AWTEvent;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Size;
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
 * blur.
 */
public class OcvUtil_BluredImageDiff implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener {
    // constant var.
    private static final int FLAGS = DOES_8G | DOES_RGB | DOES_16 | DOES_32 | KEEP_PREVIEW;

    /*
     Various border types, image boundaries are denoted with '|'

     * BORDER_CONSTANT:      iiiiii|abcdefgh|iiiiiii with some specified i
     * BORDER_REPLICATE:     aaaaaa|abcdefgh|hhhhhhh
     * BORDER_REFLECT:       fedcba|abcdefgh|hgfedcb
     * BORDER_REFLECT_101:   gfedcb|abcdefgh|gfedcba
     * BORDER_WRAP:          cdefgh|abcdefgh|abcdefg (Error occurred)
     * BORDER_TRANSPARENT:   uvwxyz|abcdefgh|ijklmno (Error occurred)
     * BORDER_ISOLATED:      do not look outside of ROI
     */
    private static final int[] INT_BORDERTYPE = { Core.BORDER_CONSTANT, Core.BORDER_REPLICATE, Core.BORDER_REFLECT, Core.BORDER_REFLECT101, /*Core.BORDER_WRAP, Core.BORDER_TRANSPARENT,*/ Core.BORDER_ISOLATED };
    private static final String[] STR_BORDERTYPE = { "BORDER_CONSTANT", "BORDER_REPLICATE", "BORDER_REFLECT", "BORDER_REFLECT101", /*"BORDER_WRAP", "BORDER_TRANSPARENT",*/ "BORDER_ISOLATED" };

    // staic var.
    private static double small_ksize_x = 3;  // small blurring kernel size of x
    private static double small_ksize_y = 3;  // small blurring kernel size of y
    private static double large_ksize_x = 31; // large blurring kernel size of x
    private static double large_ksize_y = 31; // large blurring kernel size of y
    private static double offset = 128;
    private static int indBorderType = 2;     // Border type

    // var.
    private int bitDepth = 0;
    private Size small_ksize = null;
    private Size large_ksize = null;
    private final ImageCalculator ic = new ImageCalculator();

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        GenericDialog gd = new GenericDialog(command.trim() + " ...");

        gd.addNumericField("small_ksize_x", small_ksize_x, 4);
        gd.addNumericField("small_ksize_y", small_ksize_y, 4);
        gd.addNumericField("large_ksize_x", large_ksize_x, 4);
        gd.addNumericField("large_ksize_y", large_ksize_y, 4);
        gd.addNumericField("offset", offset, 4);
        gd.addChoice("borderType", STR_BORDERTYPE, STR_BORDERTYPE[indBorderType]);
        gd.addPreviewCheckbox(pfr);
        gd.addDialogListener(this);

        gd.showDialog();

        if(gd.wasCanceled()) {
            return DONE;
        }
        else {
            return IJ.setupDialog(imp, FLAGS);
        }
    }

    @Override
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent awte) {
        small_ksize_x = (double)gd.getNextNumber();
        small_ksize_y = (double)gd.getNextNumber();
        large_ksize_x = (double)gd.getNextNumber();
        large_ksize_y = (double)gd.getNextNumber();
        offset = (double)gd.getNextNumber();
        indBorderType = (int)gd.getNextChoiceIndex();

        if(Double.isNaN(small_ksize_x) || Double.isNaN(small_ksize_y) || Double.isNaN(large_ksize_x) || Double.isNaN(large_ksize_y) || Double.isNaN(offset)) {
            IJ.showStatus("ERR : NaN");
            return false;
        }

        if(small_ksize_x <= 0) {
            IJ.showStatus("'0 < small_ksize_x' is necessary.");
            return false;
        }

        if(small_ksize_y <= 0) {
            IJ.showStatus("'0 < small_ksize_y' is necessary.");
            return false;
        }
        
        if(large_ksize_x <= 0) {
            IJ.showStatus("'0 < large_ksize_x' is necessary.");
            return false;
        }

        if(large_ksize_y <= 0) {
            IJ.showStatus("'0 < large_ksize_y' is necessary.");
            return false;
        }

        small_ksize = new Size(small_ksize_x, small_ksize_y);
        large_ksize = new Size(large_ksize_x, large_ksize_y);
        IJ.showStatus("OcvUtil_BluredImageDiff");
        return true;
    }

    @Override
    public void setNPasses(int nPasses) {
        // do nothing
    }

    @Override
    public int setup(String arg, ImagePlus imp) {
        if(!OCV__LoadLibrary.isLoad()) {
            IJ.error("Library is not loaded.");
            return DONE;
        }

        if(imp == null) {
            IJ.noImage();
            return DONE;
        }
        else {
            bitDepth = imp.getBitDepth();
            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip) {
        Boolean curDoScal = ImageConverter.getDoScaling();
        ImageConverter.setDoScaling(false);
        
        ImageProcessor ip_small = ip.duplicate();
        ImageProcessor ip_large = ip.duplicate();
        blur(ip_small, small_ksize, INT_BORDERTYPE[indBorderType]);
        blur(ip_large, large_ksize, INT_BORDERTYPE[indBorderType]);
        ImagePlus imp_small = new ImagePlus("small_blur", ip_small);
        ImagePlus imp_large = new ImagePlus("largel_blur", ip_large);

        if (bitDepth == 24) {
            IJ.run(imp_small, "RGB Stack", "");
            IJ.run(imp_large, "RGB Stack", "");
            IJ.run(imp_small, "32-bit", "");
            IJ.run(imp_large, "32-bit", "");
            ImagePlus imp_dst = ic.run("Subtract create 32-bit stack", imp_small, imp_large);
            IJ.run(imp_dst, "Add...", "value=" + String.valueOf(offset) + " stack");
            IJ.run(imp_dst, "8-bit", "");
            IJ.run(imp_dst, "RGB Color", "");
            
            ArrayCopy(imp_dst.getProcessor(), ip);
            
            imp_small.close();
            imp_large.close();
            imp_dst.close();
        }
        else {
            new ImageConverter(imp_small).convertToGray32();
            new ImageConverter(imp_large).convertToGray32();
            ImagePlus imp_dst = ic.run("Subtract create 32-bit", imp_small, imp_large);
            IJ.run(imp_dst, "Add...", "value=" + String.valueOf(offset));
            
            if (ip.getBitDepth() == 8) {
                new ImageConverter(imp_dst).convertToGray8();
            }
            else if (ip.getBitDepth() == 16) {
                new ImageConverter(imp_dst).convertToGray16();
            }
            else if (ip.getBitDepth() == 32) {
                // do nothing
            }
           
            ArrayCopy(imp_dst.getProcessor(), ip);
            
            imp_small.close();
            imp_large.close();
            imp_dst.close();
        }
        
        ImageConverter.setDoScaling(curDoScal);
    }
   
    public void blur(ImageProcessor ip, Size ksize, int borderType) {
        if(ip.getBitDepth() == 8) {
            // srcdst
            int imw = ip.getWidth();
            int imh = ip.getHeight();
            byte[] srcdst_bytes = (byte[])ip.getPixels();

            // mat
            Mat src_mat = new Mat(imh, imw, CvType.CV_8UC1);
            Mat dst_mat = new Mat(imh, imw, CvType.CV_8UC1);

            // run
            src_mat.put(0, 0, srcdst_bytes);
            Imgproc.blur(src_mat, dst_mat, ksize, new Point(-1, -1), borderType);
            dst_mat.get(0, 0, srcdst_bytes);
        }
        else if(ip.getBitDepth() == 16) {
            // srcdst
            int imw = ip.getWidth();
            int imh = ip.getHeight();
            short[] srcdst_shorts = (short[])ip.getPixels();

            // mat
            Mat src_mat = new Mat(imh, imw, CvType.CV_16S);
            Mat dst_mat = new Mat(imh, imw, CvType.CV_16S);

            // run
            src_mat.put(0, 0, srcdst_shorts);
            Imgproc.blur(src_mat, dst_mat, ksize, new Point(-1, -1), borderType);
            dst_mat.get(0, 0, srcdst_shorts);
        }
        else if(ip.getBitDepth() == 24) {
            // srcdst
            int imw = ip.getWidth();
            int imh = ip.getHeight();
            int[] srcdst_ints = (int[])ip.getPixels();

            // mat
            Mat src_mat = new Mat(imh, imw, CvType.CV_8UC3);
            Mat dst_mat = new Mat(imh, imw, CvType.CV_8UC3);

            // run
            OCV__LoadLibrary.intarray2mat(srcdst_ints, src_mat, imw, imh);
            Imgproc.blur(src_mat, dst_mat, ksize, new Point(-1, -1), borderType);
            OCV__LoadLibrary.mat2intarray(dst_mat, srcdst_ints, imw, imh);
        }
        else if(ip.getBitDepth() == 32) {
            // srcdst
            int imw = ip.getWidth();
            int imh = ip.getHeight();
            float[] srcdst_floats = (float[])ip.getPixels();

            // mat
            Mat src_mat = new Mat(imh, imw, CvType.CV_32F);
            Mat dst_mat = new Mat(imh, imw, CvType.CV_32F);

            // run
            src_mat.put(0, 0, srcdst_floats);
            Imgproc.blur(src_mat, dst_mat, ksize, new Point(-1, -1), borderType);
            dst_mat.get(0, 0, srcdst_floats);
        }
        else {
            IJ.error("Wrong image format");
        }
    }
    
    public void ArrayCopy(ImageProcessor src, ImageProcessor dst) {
        if(src.getBitDepth() == 8) {
            int imw = src.getWidth();
            int imh = src.getHeight();
            byte[] src_bytes = (byte[])src.getPixels();
            byte[] dst_bytes = (byte[])dst.getPixels();
            System.arraycopy(src_bytes, 0, dst_bytes, 0, imw*imh);
        }
        else if(src.getBitDepth() == 16) {
            int imw = src.getWidth();
            int imh = src.getHeight();
            short[] src_shorts = (short[])src.getPixels();
            short[] dst_shorts = (short[])dst.getPixels();            
            System.arraycopy(src_shorts, 0, dst_shorts, 0, imw*imh);
        }
        else if(src.getBitDepth() == 24) {
            int imw = src.getWidth();
            int imh = src.getHeight();
            int[] src_ints = (int[])src.getPixels();
            int[] dst_ints = (int[])dst.getPixels();
            System.arraycopy(src_ints, 0, dst_ints, 0, imw*imh);
        }
        else if(src.getBitDepth() == 32) {
            // srcdst
            int imw = src.getWidth();
            int imh = src.getHeight();
            float[] src_floats = (float[])src.getPixels();
            float[] dst_floats = (float[])dst.getPixels();
            System.arraycopy(src_floats, 0, dst_floats, 0, imw*imh);
        }
        else {
            IJ.error("Wrong image format");
        }
    }
}
