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
 * bilateralFilter.
 */
public class OCV_BilateralFilter implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener {
    // constant var.
    private static final int FLAGS = DOES_8G | DOES_RGB | DOES_32 | KEEP_PREVIEW; // 8-bit or floating-point, 1-channel or 3-channel image.

    /*
     Various border types, image boundaries are denoted with '|'

     * BORDER_CONSTANT:      iiiiii|abcdefgh|iiiiiii with some specified i
     * BORDER_REPLICATE:     aaaaaa|abcdefgh|hhhhhhh
     * BORDER_REFLECT:       fedcba|abcdefgh|hgfedcb
     * BORDER_REFLECT_101:   gfedcb|abcdefgh|gfedcba
     * BORDER_WRAP:          cdefgh|abcdefgh|abcdefg
     * BORDER_TRANSPARENT:   uvwxyz|abcdefgh|ijklmno (Error occurred)
     * BORDER_ISOLATED:      do not look outside of ROI
     */
    private static final int[] INT_BORDERTYPE = { Core.BORDER_CONSTANT, Core.BORDER_REPLICATE, Core.BORDER_REFLECT, Core.BORDER_REFLECT101, Core.BORDER_WRAP, /*Core.BORDER_TRANSPARENT,*/ Core.BORDER_ISOLATED };
    private static final String[] STR_BORDERTYPE = { "BORDER_CONSTANT", "BORDER_REPLICATE", "BORDER_REFLECT", "BORDER_REFLECT101", "BORDER_WRAP", /*"BORDER_TRANSPARENT",*/ "BORDER_ISOLATED" };

    // static var.
    private static int diameter = 5; // Diameter of each pixel neighborhood that is used during filtering.
    private static double sigmaColor = 15; // Filter sigma in the color space.
    private static double sigmaSpace = 8; // Filter sigma in the coordinate space.
    private static int indBorderType = 2; // Border type.

    // instance var.
    private String className = null;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        className = command.trim();
        GenericDialog gd = new GenericDialog(className + " ...");

        gd.addMessage("If diameter is negative, it is computed from sigmaSpace.");
        gd.addNumericField("diameter", diameter, 0);
        gd.addNumericField("sigmaColor", sigmaColor, 4);
        gd.addNumericField("sigmaSpace", sigmaSpace, 4);
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
        diameter = (int)gd.getNextNumber();
        sigmaColor = (double)gd.getNextNumber();
        sigmaSpace = (double)gd.getNextNumber();
        indBorderType = (int)gd.getNextChoiceIndex();

        if(Double.isNaN(sigmaColor) || Double.isNaN(sigmaSpace)) {
            IJ.showStatus("ERR : NaN");
            return false;
        }

        if(diameter == 0) {
            IJ.showStatus("diameter must be negative or >= 1.");
            return false;
        }

        if(sigmaColor <= 0) {
            IJ.showStatus("'0 < sigmaColor' is necessary.");
            return false;
        }

        if(sigmaSpace <= 0) {
            IJ.showStatus("'0 < sigmaSpace' is necessary.");
            return false;
        }

        IJ.showStatus("OCV_BilateralFilter");
        return true;
    }

    @Override
    public void setNPasses(int nPasses) {
        // do nothing
    }

    @Override
    public int setup(String arg, ImagePlus imp) {
        if(!OCV__LoadLibrary.isLoad()) {
            OCV__LoadLibrary.logError("OCV_BilateralFilter", "Library is not loaded.");
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
            else if(bitDepth == 24) {
                runForRGB(ip, imw, imh);
            }
            else if(bitDepth == 32) {
                runForFloat32(ip, imw, imh);
            }
            else {
                OCV__LoadLibrary.logError(className, "Wrong image format.");
            }
        }
        catch(Exception e) {
            OCV__LoadLibrary.logError(className, "Bilateral filter failed (" + e.getMessage() + ")");
        }
    }

    private void runForGrayscale8(ImageProcessor ip, int imw, int imh) {
        Mat src_mat = null;
        Mat dst_mat = null;
        try {
            byte[] srcdst_bytes = (byte[])ip.getPixels();
            src_mat = new Mat(imh, imw, CvType.CV_8UC1);
            dst_mat = new Mat(imh, imw, CvType.CV_8UC1);
            src_mat.put(0, 0, srcdst_bytes);
            Imgproc.bilateralFilter(src_mat, dst_mat, diameter, sigmaColor, sigmaSpace, INT_BORDERTYPE[indBorderType]);
            dst_mat.get(0, 0, srcdst_bytes);
        } finally {
            if (src_mat != null) src_mat.release();
            if (dst_mat != null) dst_mat.release();
        }
    }

    private void runForRGB(ImageProcessor ip, int imw, int imh) {
        Mat src_mat = null;
        Mat dst_mat = null;
        try {
            int[] srcdst_ints = (int[])ip.getPixels();
            src_mat = new Mat(imh, imw, CvType.CV_8UC3);
            dst_mat = new Mat(imh, imw, CvType.CV_8UC3);
            OCV__LoadLibrary.intarray2mat(srcdst_ints, src_mat, imw, imh);
            Imgproc.bilateralFilter(src_mat, dst_mat, diameter, sigmaColor, sigmaSpace, INT_BORDERTYPE[indBorderType]);
            OCV__LoadLibrary.mat2intarray(dst_mat, srcdst_ints, imw, imh);
        } finally {
            if (src_mat != null) src_mat.release();
            if (dst_mat != null) dst_mat.release();
        }
    }

    private void runForFloat32(ImageProcessor ip, int imw, int imh) {
        Mat src_mat = null;
        Mat dst_mat = null;
        try {
            float[] srcdst_floats = (float[])ip.getPixels();
            src_mat = new Mat(imh, imw, CvType.CV_32FC1);
            dst_mat = new Mat(imh, imw, CvType.CV_32FC1);
            src_mat.put(0, 0, srcdst_floats);
            Imgproc.bilateralFilter(src_mat, dst_mat, diameter, sigmaColor, sigmaSpace, INT_BORDERTYPE[indBorderType]);
            dst_mat.get(0, 0, srcdst_floats);
        } finally {
            if (src_mat != null) src_mat.release();
            if (dst_mat != null) dst_mat.release();
        }
    }
}