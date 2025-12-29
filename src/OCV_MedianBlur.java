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
 * medianBlur.
 */
public class OCV_MedianBlur implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener {
    // constant var.
    private static final int FLAGS = DOES_8G | DOES_RGB | DOES_16 | DOES_32 | KEEP_PREVIEW;
    private static final int MIN_KSIZE = 3;
    private static final int MAX_KSIZE_16_32BIT = 5;

    // static var.
    private static int ksize = 3; // Blurring kernel size

    // var.
    private String className;
    private int bitDepth;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        bitDepth = imp.getBitDepth();
        className = command.trim();
        GenericDialog gd = new GenericDialog(className + " ...");

        gd.addNumericField("ksize", ksize, 0);
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

        if(ksize < MIN_KSIZE) {
            IJ.showStatus("ksize must be >= " + MIN_KSIZE);
            return false;
        }

        if(ksize % 2 == 0) {
            IJ.showStatus("ksize must be odd");
            return false;
        }

        if((bitDepth == 16 || bitDepth == 32) && ksize > MAX_KSIZE_16_32BIT) {
            IJ.showStatus("For 16-bit or 32-bit images, ksize must be <= " + MAX_KSIZE_16_32BIT);
            return false;
        }

        IJ.showStatus("OCV_MedianBlur");
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

        if(bitDepth == 8) {
            Mat srcMat = null;
            Mat dstMat = null;

            try {
                byte[] srcdstBytes = (byte[])ip.getPixels();
                srcMat = new Mat(imh, imw, CvType.CV_8UC1);
                dstMat = new Mat(imh, imw, CvType.CV_8UC1);

                srcMat.put(0, 0, srcdstBytes);
                Imgproc.medianBlur(srcMat, dstMat, ksize);
                dstMat.get(0, 0, srcdstBytes);
            }
            catch(Exception e) {
                IJ.log(className + " error: " + e.getMessage());
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
                Imgproc.medianBlur(srcMat, dstMat, ksize);
                dstMat.get(0, 0, srcdstShorts);
            }
            catch(Exception e) {
                IJ.log(className + " error: " + e.getMessage());
            }
            finally {
                if(srcMat != null) srcMat.release();
                if(dstMat != null) dstMat.release();
            }
        }
        else if(bitDepth == 24) {
            Mat srcMat = null;
            Mat dstMat = null;

            try {
                int[] srcdstInts = (int[])ip.getPixels();
                srcMat = new Mat(imh, imw, CvType.CV_8UC3);
                dstMat = new Mat(imh, imw, CvType.CV_8UC3);

                OCV__LoadLibrary.intarray2mat(srcdstInts, srcMat, imw, imh);
                Imgproc.medianBlur(srcMat, dstMat, ksize);
                OCV__LoadLibrary.mat2intarray(dstMat, srcdstInts, imw, imh);
            }
            catch(Exception e) {
                IJ.log(className + " error: " + e.getMessage());
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
                Imgproc.medianBlur(srcMat, dstMat, ksize);
                dstMat.get(0, 0, srcdstFloats);
            }
            catch(Exception e) {
                IJ.log(className + " error: " + e.getMessage());
            }
            finally {
                if(srcMat != null) srcMat.release();
                if(dstMat != null) dstMat.release();
            }
        }
    }
}