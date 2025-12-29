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
 * morphologyEx.
 */
public class OCV_MorphologyEx implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener {
    // constant var.
    private static final int FLAGS = DOES_8G | DOES_16 | DOES_32 | DOES_RGB | KEEP_PREVIEW;
    private static final Point ANCHOR = new Point(-1, -1);

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

    // static var.
    private static int ksizeX = 3; // kernel size of x
    private static int ksizeY = 3; // kernel size of y
    private static int iterations = 1; // Number of times erosion and dilation are applied.
    private static int indOperation = 0; // operation
    private static int indShapeType = 0; // shape type

    // var.
    private String className;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        className = command.trim();
        GenericDialog gd = new GenericDialog(className + " ...");

        gd.addChoice("operation", STR_OPERATION, STR_OPERATION[indOperation]);
        gd.addChoice("shape", STR_SHAPERTYPE, STR_SHAPERTYPE[indShapeType]);
        gd.addNumericField("ksize_x", ksizeX, 0);
        gd.addNumericField("ksize_y", ksizeY, 0);
        gd.addNumericField("iterations", iterations, 0);
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
        indOperation = gd.getNextChoiceIndex();
        indShapeType = gd.getNextChoiceIndex();
        ksizeX = (int)gd.getNextNumber();
        ksizeY = (int)gd.getNextNumber();
        iterations = (int)gd.getNextNumber();

        if(ksizeX < 0 || ksizeY < 0) {
            IJ.showStatus("ksize_x and ksize_y must be >= 0");
            return false;
        }

        if(ksizeX % 2 == 0 || ksizeY % 2 == 0) {
            IJ.showStatus("ksize_x and ksize_y must be odd");
            return false;
        }

        if(iterations < 1) {
            IJ.showStatus("iterations must be >= 1");
            return false;
        }

        IJ.showStatus("OCV_MorphologyEx");
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
        
        Mat kernel = null;

        try {
            Size ksize = new Size(ksizeX, ksizeY);
            kernel = Imgproc.getStructuringElement(INT_SHAPERTYPE[indShapeType], ksize);

            if(bitDepth == 8) {
                Mat srcMat = null;
                Mat dstMat = null;

                try {
                    byte[] srcdstBytes = (byte[])ip.getPixels();
                    srcMat = new Mat(imh, imw, CvType.CV_8UC1);
                    dstMat = new Mat(imh, imw, CvType.CV_8UC1);

                    srcMat.put(0, 0, srcdstBytes);
                    Imgproc.morphologyEx(srcMat, dstMat, INT_OPERATION[indOperation], kernel, ANCHOR, iterations);
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
                    Imgproc.morphologyEx(srcMat, dstMat, INT_OPERATION[indOperation], kernel, ANCHOR, iterations);
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
                    Imgproc.morphologyEx(srcMat, dstMat, INT_OPERATION[indOperation], kernel, ANCHOR, iterations);
                    dstMat.get(0, 0, srcdstFloats);
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
                    Imgproc.morphologyEx(srcMat, dstMat, INT_OPERATION[indOperation], kernel, ANCHOR, iterations);
                    OCV__LoadLibrary.mat2intarray(dstMat, srcdstInts, imw, imh);
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
        finally {
            if(kernel != null) kernel.release();
        }
    }
}