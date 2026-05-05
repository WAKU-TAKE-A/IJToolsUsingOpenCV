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
 *  cornerHarris.
 */
public class OCV_CornerHarris implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener {
    // constant var.
    private static final int FLAGS = DOES_8G | DOES_32;

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

    // static var.
    private static int blockSize = 2; // Neighborhood size.
    private static int ksize = 3; // Aperture parameter for the Sobel operator.
    private static double k = 0.04; // Harris detector free parameter.
    private static int indBorderType = 2; // Border type

    // var
    private String className;
    private String titleSrc = "";

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        className = command.trim();
        GenericDialog gd = new GenericDialog(className + " ...");

        gd.addNumericField("blockSize", blockSize, 0);
        gd.addNumericField("ksize", ksize, 0);
        gd.addNumericField("free_parame", k, 4);
        gd.addChoice("borderType", STR_BORDERTYPE, STR_BORDERTYPE[indBorderType]);
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
        blockSize = (int)gd.getNextNumber();
        ksize = (int)gd.getNextNumber();
        k = (double)gd.getNextNumber();
        indBorderType = (int)gd.getNextChoiceIndex();

        if(blockSize <= 0) {
            IJ.showStatus("'0 < blockSize' is necessary.");
            return false;
        }

        if(ksize <= 0) {
            IJ.showStatus("'0 < ksize' is necessary.");
            return false;
        }

        if(ksize % 2 == 0) {
            IJ.showStatus("ksize must be odd.");
            return false;
        }

        if(Double.isNaN(k)) {
            IJ.showStatus("ERR : NaN");
            return false;
        }

        IJ.showStatus("OCV_CornerHarris");
        return true;
    }

    @Override
    public void setNPasses(int nPasses) {
        // do nothing
    }

    @Override
    public int setup(String arg, ImagePlus imp) {
        if(!OCV__LoadLibrary.isLoad()) {
            OCV__LoadLibrary.logError("OCV_CornerHarris", "Library is not loaded.");
            return DONE;
        }

        if(imp == null) {
            IJ.noImage();
            return DONE;
        }
        else {
            titleSrc = imp.getTitle();
            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip) {
        Mat srcMat = null;
        Mat dstMat = null;

        try {
            if(ip.getBitDepth() == 8) {
                // src
                int imw = ip.getWidth();
                int imh = ip.getHeight();
                byte[] srcBytes = (byte[])ip.getPixels();

                // dst
                String titleDst = WindowManager.getUniqueName(titleSrc + "_CornerHarris");
                ImagePlus impDst = new ImagePlus(titleDst, new FloatProcessor(imw, imh));
                float[] dstFloats = (float[])impDst.getChannelProcessor().getPixels();

                // mat
                srcMat = new Mat(imh, imw, CvType.CV_8UC1);
                dstMat = new Mat(imh, imw, CvType.CV_32F);

                // run
                srcMat.put(0, 0, srcBytes);
                Imgproc.cornerHarris(srcMat, dstMat, blockSize, ksize, k, INT_BORDERTYPE[indBorderType]);
                dstMat.get(0, 0, dstFloats);

                // show
                impDst.show();
            }
            else if(ip.getBitDepth() == 32) {
                // src
                int imw = ip.getWidth();
                int imh = ip.getHeight();
                float[] srcFloats = (float[])ip.getPixels();

                // dst
                String titleDst = WindowManager.getUniqueName(titleSrc + "_CornerHarris");
                ImagePlus impDst = new ImagePlus(titleDst, new FloatProcessor(imw, imh));
                float[] dstFloats = (float[])impDst.getChannelProcessor().getPixels();

                // mat
                srcMat = new Mat(imh, imw, CvType.CV_32F);
                dstMat = new Mat(imh, imw, CvType.CV_32F);

                // run
                srcMat.put(0, 0, srcFloats);
                Imgproc.cornerHarris(srcMat, dstMat, blockSize, ksize, k, INT_BORDERTYPE[indBorderType]);
                dstMat.get(0, 0, dstFloats);

                // show
                impDst.show();
            }
            else {
                OCV__LoadLibrary.logError(className, "Wrong image format.");
            }
        }
        catch(Exception e) {
            OCV__LoadLibrary.logError(className, "Corner Harris failed (" + e.getMessage() + ")");
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