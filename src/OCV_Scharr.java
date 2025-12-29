import ij.*;
import ij.IJ;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilter;
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
 * Scharr.
 */
public class OCV_Scharr implements ExtendedPlugInFilter, DialogListener {
    // constant var.
    private static final int FLAGS = PlugInFilter.DOES_8G | PlugInFilter.DOES_16 | PlugInFilter.DOES_32 | ExtendedPlugInFilter.KEEP_PREVIEW;

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
    private static int dx = 1; // order of the derivative x.
    private static int dy = 0; // order of the derivative y.
    private static double scale = 1; // optional scale factor for the computed derivative values.
    private static double delta = 0; // optional delta value that is added to the results prior to storing them in dst.
    private static int indBorderType = 2; // border type
    
    // var.
    private String className;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        className = command.trim();
        GenericDialog gd = new GenericDialog(className + " ...");

        gd.addNumericField("dx", dx, 0);
        gd.addNumericField("dy", dy, 0);
        gd.addNumericField("scale", scale, 4);
        gd.addNumericField("delta", delta, 4);
        gd.addChoice("borderType", STR_BORDERTYPE, STR_BORDERTYPE[indBorderType]);
        gd.addPreviewCheckbox(pfr);
        gd.addDialogListener(this);

        gd.showDialog();

        if(gd.wasCanceled()) {
            return PlugInFilter.DONE;
        }
        else {
            return IJ.setupDialog(imp, FLAGS);
        }
    }

    @Override
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent awte) {
        dx = (int)gd.getNextNumber();
        dy = (int)gd.getNextNumber();
        scale = gd.getNextNumber();
        delta = gd.getNextNumber();
        indBorderType = gd.getNextChoiceIndex();

        if(!(dx >= 0 && dy >= 0 && dx + dy == 1)) {
            IJ.showStatus("dx >= 0, dy >= 0, and dx + dy must equal 1");
            return false;
        }

        if(Double.isNaN(scale) || Double.isNaN(delta)) {
            IJ.showStatus("Error: NaN value detected");
            return false;
        }

        IJ.showStatus("OCV_Scharr");
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
            return PlugInFilter.DONE;
        }

        if(imp == null) {
            IJ.noImage();
            return PlugInFilter.DONE;
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
                Mat srcMat = null;
                Mat dstMat = null;

                try {
                    byte[] srcdstBytes = (byte[])ip.getPixels();
                    srcMat = new Mat(imh, imw, CvType.CV_8UC1);
                    dstMat = new Mat(imh, imw, CvType.CV_8UC1);

                    srcMat.put(0, 0, srcdstBytes);
                    Imgproc.Scharr(srcMat, dstMat, CvType.CV_8U, dx, dy, scale, delta, INT_BORDERTYPE[indBorderType]);
                    dstMat.get(0, 0, srcdstBytes);
                }
                finally {
                    if(srcMat != null) srcMat.release();
                    if(dstMat != null) dstMat.release();
                }
            }
            else if(bitDepth == 16) {
                Mat srcMat = null;
                Mat dstMat = null;

                try {
                    short[] srcdstShorts = (short[])ip.getPixels();
                    srcMat = new Mat(imh, imw, CvType.CV_16U);
                    dstMat = new Mat(imh, imw, CvType.CV_16U);

                    srcMat.put(0, 0, srcdstShorts);
                    Imgproc.Scharr(srcMat, dstMat, CvType.CV_16U, dx, dy, scale, delta, INT_BORDERTYPE[indBorderType]);
                    dstMat.get(0, 0, srcdstShorts);
                }
                finally {
                    if(srcMat != null) srcMat.release();
                    if(dstMat != null) dstMat.release();
                }
            }
            else if(bitDepth == 32) {
                Mat srcMat = null;
                Mat dstMat = null;

                try {
                    float[] srcdstFloats = (float[])ip.getPixels();
                    srcMat = new Mat(imh, imw, CvType.CV_32F);
                    dstMat = new Mat(imh, imw, CvType.CV_32F);

                    srcMat.put(0, 0, srcdstFloats);
                    Imgproc.Scharr(srcMat, dstMat, CvType.CV_32F, dx, dy, scale, delta, INT_BORDERTYPE[indBorderType]);
                    dstMat.get(0, 0, srcdstFloats);
                }
                finally {
                    if(srcMat != null) srcMat.release();
                    if(dstMat != null) dstMat.release();
                }
            }
        }
        catch(Exception e) {
            IJ.log(className + " error: " + e.getMessage());
        }
    }
}