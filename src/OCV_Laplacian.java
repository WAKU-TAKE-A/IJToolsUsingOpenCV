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
 * Laplacian.
 */
public class OCV_Laplacian implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener {
    // constant var.
    private static final int FLAGS = DOES_8G | DOES_16 | DOES_RGB | DOES_32 | KEEP_PREVIEW;

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
    private static int ksize = 3; // kernel size
    private static double scale = 1; // optional scale factor for the computed Laplacian values
    private static double delta = 0; // optional delta value that is added to the results prior to storing them in dst
    private static int indBorderType = 2; // border type

    // var.
    private String className;
    
    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        className = command.trim();
        GenericDialog gd = new GenericDialog(className + " ...");

        gd.addNumericField("ksize", ksize, 0);
        gd.addNumericField("scale", scale, 4);
        gd.addNumericField("delta", delta, 4);
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
        ksize = (int)gd.getNextNumber();
        scale = (double)gd.getNextNumber();
        delta = (double)gd.getNextNumber();
        indBorderType = (int)gd.getNextChoiceIndex();

        if(ksize <= 0) {
            IJ.showStatus("'0 < ksize' is necessary.");
            return false;
        }

        if(ksize % 2 == 0) {
            IJ.showStatus("'ksize is odd.");
            return false;
        }

        if(Double.isNaN(scale) || Double.isNaN(delta)) {
            IJ.showStatus("ERR : NaN");
            return false;
        }

        IJ.showStatus("OCV_Laplacian");
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
        Mat srcMat = null;
        Mat dstMat = null;

        try {
            int imw = ip.getWidth();
            int imh = ip.getHeight();

            if(ip.getBitDepth() == 8) {
                byte[] srcdstBytes = (byte[])ip.getPixels();
                srcMat = new Mat(imh, imw, CvType.CV_8UC1);
                dstMat = new Mat(imh, imw, CvType.CV_8UC1);
                srcMat.put(0, 0, srcdstBytes);
                Imgproc.Laplacian(srcMat, dstMat, dstMat.depth(), ksize, scale, delta, INT_BORDERTYPE[indBorderType]);
                dstMat.get(0, 0, srcdstBytes);
            }
            else if(ip.getBitDepth() == 16) {
                short[] srcdstShorts = (short[])ip.getPixels();
                srcMat = new Mat(imh, imw, CvType.CV_16S);
                dstMat = new Mat(imh, imw, CvType.CV_16S);
                srcMat.put(0, 0, srcdstShorts);
                Imgproc.Laplacian(srcMat, dstMat, dstMat.depth(), ksize, scale, delta, INT_BORDERTYPE[indBorderType]);
                dstMat.get(0, 0, srcdstShorts);
            }
            else if(ip.getBitDepth() == 24) {
                int[] srcdstInts = (int[])ip.getPixels();
                srcMat = new Mat(imh, imw, CvType.CV_8UC3);
                dstMat = new Mat(imh, imw, CvType.CV_8UC3);
                OCV__LoadLibrary.intarray2mat(srcdstInts, srcMat, imw, imh);
                Imgproc.Laplacian(srcMat, dstMat, dstMat.depth(), ksize, scale, delta, INT_BORDERTYPE[indBorderType]);
                OCV__LoadLibrary.mat2intarray(dstMat, srcdstInts, imw, imh);
            }
            else if(ip.getBitDepth() == 32) {
                float[] srcdstFloats = (float[])ip.getPixels();
                srcMat = new Mat(imh, imw, CvType.CV_32F);
                dstMat = new Mat(imh, imw, CvType.CV_32F);
                srcMat.put(0, 0, srcdstFloats);
                Imgproc.Laplacian(srcMat, dstMat, dstMat.depth(), ksize, scale, delta, INT_BORDERTYPE[indBorderType]);
                dstMat.get(0, 0, srcdstFloats);
            }
            else {
                IJ.log(className + " error: Wrong image format.");
            }
        }
        catch(Exception e) {
            IJ.log(className + " error: Laplacian failed. (" + e.getMessage() + ")");
        }
        finally {
            if(srcMat != null) {
                srcMat.release();
            }
            if(dstMat != null) {
                dstMat.release();
            }
        }
    }
}