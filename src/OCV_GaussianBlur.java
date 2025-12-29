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
 * GaussianBlur.
 */
public class OCV_GaussianBlur implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener {
    // constant var.
    private static final int FLAGS = DOES_8G | DOES_16 | DOES_32 | KEEP_PREVIEW;

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
    private static int ksize_x = 3; // kernel size of x
    private static int ksize_y = 3; // kernel size of y
    private static double sigma_x = 0; // Gaussian kernel standard deviation in x direction
    private static double sigma_y = 0; // Gaussian kernel standard deviation in y direction
    private static int indBorderType = 2; // border type

    // instance var.
    private Size ksize = null;
    private Mat srcMat = null;
    private Mat dstMat = null;
    private int cachedWidth = -1;
    private int cachedHeight = -1;
    private int cachedBitDepth = -1;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        GenericDialog gd = new GenericDialog(command.trim() + " ...");

        gd.addNumericField("ksize_x", ksize_x, 0);
        gd.addNumericField("ksize_y", ksize_y, 0);
        gd.addNumericField("sigma_x", sigma_x, 4);
        gd.addNumericField("sigma_y", sigma_y, 4);
        gd.addChoice("borderType", STR_BORDERTYPE, STR_BORDERTYPE[indBorderType]);
        gd.addMessage("Note: ksize can be 0 (computed from sigma) or positive odd number.");
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
        ksize_x = (int)gd.getNextNumber();
        ksize_y = (int)gd.getNextNumber();
        sigma_x = (double)gd.getNextNumber();
        sigma_y = (double)gd.getNextNumber();
        indBorderType = (int)gd.getNextChoiceIndex();

        if(ksize_x < 0 || ksize_y < 0) {
            IJ.showStatus("'0 <= ksize_*' is necessary.");
            return false;
        }

        if(ksize_x > 0 && ksize_x % 2 == 0) {
            IJ.showStatus("ksize_x must be 0 or odd.");
            return false;
        }

        if(ksize_y > 0 && ksize_y % 2 == 0) {
            IJ.showStatus("ksize_y must be 0 or odd.");
            return false;
        }

        if(Double.isNaN(sigma_x) || Double.isNaN(sigma_y)) {
            IJ.showStatus("ERR : NaN");
            return false;
        }

        ksize = new Size(ksize_x, ksize_y);

        IJ.showStatus("OCV_GaussianBlur");
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
            else if(bitDepth == 32) {
                runForFloat32(ip, imw, imh);
            }
            else {
                IJ.log("Wrong image format");
            }
        }
        catch(Exception e) {
            IJ.log("Gaussian blur failed: " + e.getMessage());
            releaseResources();
        }
    }

    private void runForGrayscale8(ImageProcessor ip, int imw, int imh) {
        byte[] srcdstBytes = (byte[])ip.getPixels();
        
        allocateMatIfNeeded(imw, imh, 8, CvType.CV_8UC1);
        
        srcMat.put(0, 0, srcdstBytes);
        Imgproc.GaussianBlur(srcMat, dstMat, ksize, sigma_x, sigma_y, INT_BORDERTYPE[indBorderType]);
        dstMat.get(0, 0, srcdstBytes);
    }

    private void runForGrayscale16(ImageProcessor ip, int imw, int imh) {
        short[] srcdstShorts = (short[])ip.getPixels();
        
        allocateMatIfNeeded(imw, imh, 16, CvType.CV_16U);
        
        srcMat.put(0, 0, srcdstShorts);
        Imgproc.GaussianBlur(srcMat, dstMat, ksize, sigma_x, sigma_y, INT_BORDERTYPE[indBorderType]);
        dstMat.get(0, 0, srcdstShorts);
    }

    private void runForFloat32(ImageProcessor ip, int imw, int imh) {
        float[] srcdstFloats = (float[])ip.getPixels();
        
        allocateMatIfNeeded(imw, imh, 32, CvType.CV_32F);
        
        srcMat.put(0, 0, srcdstFloats);
        Imgproc.GaussianBlur(srcMat, dstMat, ksize, sigma_x, sigma_y, INT_BORDERTYPE[indBorderType]);
        dstMat.get(0, 0, srcdstFloats);
    }

    private void allocateMatIfNeeded(int imw, int imh, int bitDepth, int cvType) {
        if(srcMat == null || cachedWidth != imw || cachedHeight != imh || cachedBitDepth != bitDepth) {
            releaseResources();
            srcMat = new Mat(imh, imw, cvType);
            dstMat = new Mat(imh, imw, cvType);
            cachedWidth = imw;
            cachedHeight = imh;
            cachedBitDepth = bitDepth;
        }
    }

    private void releaseResources() {
        if(srcMat != null) {
            srcMat.release();
            srcMat = null;
        }
        if(dstMat != null) {
            dstMat.release();
            dstMat = null;
        }
        cachedWidth = -1;
        cachedHeight = -1;
        cachedBitDepth = -1;
    }
}