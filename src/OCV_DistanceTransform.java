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
 * distanceTransform.
 */
public class OCV_DistanceTransform implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener {
    // constant var.
    private static final int FLAGS = DOES_32 | KEEP_PREVIEW;

    /*
    Distance types for Distance Transform and M-estimators

    DIST_USER : User defined distance.
    DIST_L1 : distance = |x1-x2| + |y1-y2|
    DIST_L2 : the simple euclidean distance
    DIST_C : distance = max(|x1-x2|,|y1-y2|)
    DIST_L12 : L1-L2 metric: distance = 2(sqrt(1+x*x/2) - 1))
    DIST_FAIR : distance = c^2(|x|/c-log(1+|x|/c)), c = 1.3998
    DIST_WELSCH : distance = c^2/2(1-exp(-(x/c)^2)), c = 2.9846
    DIST_HUBER : distance = |x|<c ? x^2/2 : c(|x|-c/2), c=1.345
    */
    private static final int CV_DIST_L1 = 1;
    private static final int CV_DIST_L2 = 2;
    private static final int CV_DIST_C = 3;
    private static final int[] INT_DISTANCETYPE = { CV_DIST_L1, CV_DIST_L2, CV_DIST_C };
    private static final String[] STR_DISTANCETYPE = { "CV_DIST_L1", "CV_DIST_L2", "CV_DIST_C" };

    private static final int[] INT_DISTANCETRANSFORMMASKS = { Imgproc.DIST_MASK_3, Imgproc.DIST_MASK_5, Imgproc.DIST_MASK_PRECISE };
    private static final String[] STR_DISTANCETRANSFORMMASKS = { "CV_DIST_MASK_3", "CV_DIST_MASK_5", "CV_DIST_MASK_PRECISE" };

    // static var.
    private static int indDistType = 0;
    private static int indMskSize = 0;

    // instance var.
    private Mat srcMat32f = null;
    private Mat srcMat8u = null;
    private Mat dstMat32f = null;
    private int cachedWidth = -1;
    private int cachedHeight = -1;
    private String className = null;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        className = command.trim();
        GenericDialog gd = new GenericDialog(className + " ...");

        gd.addChoice("distanceType", STR_DISTANCETYPE, STR_DISTANCETYPE[indDistType]);
        gd.addChoice("maskSize", STR_DISTANCETRANSFORMMASKS, STR_DISTANCETRANSFORMMASKS[indMskSize]);
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
        indDistType = (int)gd.getNextChoiceIndex();
        indMskSize = (int)gd.getNextChoiceIndex();

        IJ.showStatus("OCV_DistanceTransform");
        return true;
    }

    @Override
    public void setNPasses(int nPasses) {
        // do nothing
    }

    @Override
    public int setup(String arg, ImagePlus imp) {
        if(!OCV__LoadLibrary.isLoad()) {
            OCV__LoadLibrary.logError("OCV_DistanceTransform", "Library is not loaded.");
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
        try {
            int imw = ip.getWidth();
            int imh = ip.getHeight();
            float[] srcdstFloats = (float[])ip.getPixels();

            allocateMatIfNeeded(imw, imh);

            srcMat32f.put(0, 0, srcdstFloats);
            srcMat32f.convertTo(srcMat8u, CvType.CV_8UC1);
            Imgproc.distanceTransform(srcMat8u, dstMat32f, INT_DISTANCETYPE[indDistType], INT_DISTANCETRANSFORMMASKS[indMskSize]);
            dstMat32f.get(0, 0, srcdstFloats);
        }
        catch(Exception e) {
            OCV__LoadLibrary.logError(className, "Distance transform failed (" + e.getMessage() + ")");
            releaseResources();
        }
    }

    private void allocateMatIfNeeded(int imw, int imh) {
        if(srcMat32f == null || cachedWidth != imw || cachedHeight != imh) {
            releaseResources();
            srcMat32f = new Mat(imh, imw, CvType.CV_32FC1);
            srcMat8u = new Mat(imh, imw, CvType.CV_8UC1);
            dstMat32f = new Mat(imh, imw, CvType.CV_32FC1);
            cachedWidth = imw;
            cachedHeight = imh;
        }
    }

    private void releaseResources() {
        if(srcMat32f != null) {
            srcMat32f.release();
            srcMat32f = null;
        }
        if(srcMat8u != null) {
            srcMat8u.release();
            srcMat8u = null;
        }
        if(dstMat32f != null) {
            dstMat32f.release();
            dstMat32f = null;
        }
        cachedWidth = -1;
        cachedHeight = -1;
    }
}