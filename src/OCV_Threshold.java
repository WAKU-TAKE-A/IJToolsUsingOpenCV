import ij.*;
import ij.IJ;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
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
 * threshold.
 */
public class OCV_Threshold implements ExtendedPlugInFilter, DialogListener {
    // constant var.
    private static final int FLAGS = PlugInFilter.DOES_8G | PlugInFilter.DOES_16 | PlugInFilter.DOES_32 | ExtendedPlugInFilter.KEEP_PREVIEW;
    private static final int[] INT_TYPE = { Imgproc.THRESH_BINARY, Imgproc.THRESH_BINARY_INV, Imgproc.THRESH_TRUNC, Imgproc.THRESH_TOZERO, Imgproc.THRESH_TOZERO_INV, Imgproc.THRESH_OTSU, Imgproc.THRESH_OTSU + Imgproc.THRESH_BINARY_INV, Imgproc.THRESH_TRIANGLE };
    private static final String[] STR_TYPE = { "THRESH_BINARY", "THRESH_BINARY_INV", "THRESH_TRUNC", "THRESH_TOZERO", "THRESH_TOZERO_INV" , "THRESH_OTSU", "THRESH_OTSU_INV", "THRESH_TRIANGLE"};
    private static final float UBYTE_MAX = 255;

    // static var.
    private static double thresh = 125;
    private static double maxVal = 255.0;
    private static int idxType = 0;

    // var.
    private String className;
    private int bitDepth = 0;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        double minVal = 0;
        double maxVal = 0;

        if(bitDepth == 8) {
            minVal = 0;
            maxVal = UBYTE_MAX;
        }
        else {
            ImageStatistics stat = imp.getStatistics();
            minVal = stat.min - 1;
            maxVal = stat.max + 1;
        }

        className = command.trim();
        GenericDialog gd = new GenericDialog(className + " ...");

        gd.addSlider("thresh", minVal, maxVal, thresh);
        gd.addNumericField("maxval", OCV_Threshold.maxVal, 4);
        gd.addChoice("type", STR_TYPE, STR_TYPE[idxType]);
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
        thresh = gd.getNextNumber();
        maxVal = gd.getNextNumber();
        idxType = gd.getNextChoiceIndex();

        if(Double.isNaN(thresh) || Double.isNaN(maxVal)) {
            IJ.showStatus("Error: NaN value detected");
            return false;
        }

        if(bitDepth == 8 && (thresh < 0 || thresh > UBYTE_MAX)) {
            IJ.showStatus("For 8-bit images, thresh must be between 0 and " + (int)UBYTE_MAX);
            return false;
        }

        if(bitDepth == 8 && maxVal < 0) {
            IJ.showStatus("maxVal must be >= 0");
            return false;
        }

        // THRESH_OTSU and THRESH_TRIANGLE only support 8-bit images
        if(bitDepth != 8 && (idxType == 5 || idxType == 6 || idxType == 7)) {
            IJ.showStatus("OTSU and TRIANGLE methods are only supported for 8-bit images");
            return false;
        }

        IJ.showStatus("OCV_Threshold");
        return true;
    }

    @Override
    public void setNPasses(int nPasses) {
        // do nothing
    }

    @Override
    public int setup(String arg, ImagePlus imp) {
        if(!OCV__LoadLibrary.isLoad()) {
            OCV__LoadLibrary.logError("OCV_Threshold", "Library is not loaded.");
            return PlugInFilter.DONE;
        }

        if(imp == null) {
            IJ.noImage();
            return PlugInFilter.DONE;
        }
        else {
            bitDepth = imp.getBitDepth();
            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip) {
        int imw = ip.getWidth();
        int imh = ip.getHeight();

        try {
            if(bitDepth == 8) {
                Mat srcMat = null;
                Mat dstMat = null;

                try {
                    byte[] srcdstBytes = (byte[])ip.getPixels();
                    srcMat = new Mat(imh, imw, CvType.CV_8UC1);
                    dstMat = new Mat(imh, imw, CvType.CV_8UC1);

                    srcMat.put(0, 0, srcdstBytes);
                    Imgproc.threshold(srcMat, dstMat, thresh, maxVal, INT_TYPE[idxType]);
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
                    Imgproc.threshold(srcMat, dstMat, thresh, maxVal, INT_TYPE[idxType]);
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
                    Imgproc.threshold(srcMat, dstMat, thresh, maxVal, INT_TYPE[idxType]);
                    dstMat.get(0, 0, srcdstFloats);
                }
                finally {
                    if(srcMat != null) srcMat.release();
                    if(dstMat != null) dstMat.release();
                }
            }
        }
        catch(Exception e) {
            OCV__LoadLibrary.logError(className, e.getMessage());
        }
    }
}