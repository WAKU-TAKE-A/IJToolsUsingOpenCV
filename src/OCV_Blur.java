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
public class OCV_Blur implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener {
    // constant var.
    private static final int FLAGS = DOES_8G | DOES_RGB | DOES_16 | DOES_32 | KEEP_PREVIEW;
    private static final Point ANCHOR_POINT = new Point(-1, -1);

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

    // static var.
    private static double ksize_x = 3; // Blurring kernel size of x
    private static double ksize_y = 3; // Blurring kernel size of y
    private static int indBorderType = 2; // Border type

    // instance var.
    private Size ksize = null;
    private Mat src_mat = null;
    private Mat dst_mat = null;
    private int cachedWidth = -1;
    private int cachedHeight = -1;
    private int cachedBitDepth = -1;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        GenericDialog gd = new GenericDialog(command.trim() + " ...");

        gd.addNumericField("ksize_x", ksize_x, 4);
        gd.addNumericField("ksize_y", ksize_y, 4);
        gd.addChoice("borderType", STR_BORDERTYPE, STR_BORDERTYPE[indBorderType]);
        gd.addPreviewCheckbox(pfr);
        gd.addDialogListener(this);

        gd.showDialog();

        if(gd.wasCanceled()) {
            releaseResources();
            return DONE;
        }
        else {
            return IJ.setupDialog(imp, FLAGS);
        }
    }

    @Override
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent awte) {
        ksize_x = (double)gd.getNextNumber();
        ksize_y = (double)gd.getNextNumber();
        indBorderType = (int)gd.getNextChoiceIndex();

        if(Double.isNaN(ksize_x) || Double.isNaN(ksize_y)) {
            IJ.showStatus("ERR : NaN");
            return false;
        }

        if(ksize_x <= 0) {
            IJ.showStatus("'0 < ksize_x' is necessary.");
            return false;
        }

        if(ksize_y <= 0) {
            IJ.showStatus("'0 < ksize_y' is necessary.");
            return false;
        }

        ksize = new Size(ksize_x, ksize_y);
        IJ.showStatus("OCV_Blur");
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
            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip) {
        int imw = ip.getWidth();
        int imh = ip.getHeight();
        int bitDepth = ip.getBitDepth();

        try {
            if(bitDepth == 8) {
                runForGrayscale8(ip, imw, imh);
            }
            else if(bitDepth == 16) {
                runForGrayscale16(ip, imw, imh);
            }
            else if(bitDepth == 24) {
                runForRGB(ip, imw, imh);
            }
            else if(bitDepth == 32) {
                runForFloat32(ip, imw, imh);
            }
            else {
                IJ.error("Wrong image format");
            }
        }
        catch(Exception e) {
            IJ.log("Blur filter failed: " + e.getMessage());
            releaseResources();
        }
    }

    private void runForGrayscale8(ImageProcessor ip, int imw, int imh) {
        byte[] srcdst_bytes = (byte[])ip.getPixels();
        
        allocateMatIfNeeded(imw, imh, 8, CvType.CV_8UC1);
        
        src_mat.put(0, 0, srcdst_bytes);
        Imgproc.blur(src_mat, dst_mat, ksize, ANCHOR_POINT, INT_BORDERTYPE[indBorderType]);
        dst_mat.get(0, 0, srcdst_bytes);
    }

    private void runForGrayscale16(ImageProcessor ip, int imw, int imh) {
        short[] srcdst_shorts = (short[])ip.getPixels();
        
        allocateMatIfNeeded(imw, imh, 16, CvType.CV_16U);
        
        src_mat.put(0, 0, srcdst_shorts);
        Imgproc.blur(src_mat, dst_mat, ksize, ANCHOR_POINT, INT_BORDERTYPE[indBorderType]);
        dst_mat.get(0, 0, srcdst_shorts);
    }

    private void runForRGB(ImageProcessor ip, int imw, int imh) {
        int[] srcdst_ints = (int[])ip.getPixels();
        
        allocateMatIfNeeded(imw, imh, 24, CvType.CV_8UC3);
        
        OCV__LoadLibrary.intarray2mat(srcdst_ints, src_mat, imw, imh);
        Imgproc.blur(src_mat, dst_mat, ksize, ANCHOR_POINT, INT_BORDERTYPE[indBorderType]);
        OCV__LoadLibrary.mat2intarray(dst_mat, srcdst_ints, imw, imh);
    }

    private void runForFloat32(ImageProcessor ip, int imw, int imh) {
        float[] srcdst_floats = (float[])ip.getPixels();
        
        allocateMatIfNeeded(imw, imh, 32, CvType.CV_32F);
        
        src_mat.put(0, 0, srcdst_floats);
        Imgproc.blur(src_mat, dst_mat, ksize, ANCHOR_POINT, INT_BORDERTYPE[indBorderType]);
        dst_mat.get(0, 0, srcdst_floats);
    }

    private void allocateMatIfNeeded(int imw, int imh, int bitDepth, int cvType) {
        if(src_mat == null || cachedWidth != imw || cachedHeight != imh || cachedBitDepth != bitDepth) {
            releaseResources();
            src_mat = new Mat(imh, imw, cvType);
            dst_mat = new Mat(imh, imw, cvType);
            cachedWidth = imw;
            cachedHeight = imh;
            cachedBitDepth = bitDepth;
        }
    }

    private void releaseResources() {
        if(src_mat != null) {
            src_mat.release();
            src_mat = null;
        }
        if(dst_mat != null) {
            dst_mat.release();
            dst_mat = null;
        }
        cachedWidth = -1;
        cachedHeight = -1;
        cachedBitDepth = -1;
    }
}