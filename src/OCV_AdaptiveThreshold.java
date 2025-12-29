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
 * adaptiveThreshold.
 */
public class OCV_AdaptiveThreshold implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener {
    // constant var.
    private static final int FLAGS = DOES_8G | KEEP_PREVIEW; // 8-bit single-channel image.
    private static final int[] INT_ADAPTIVEMETHOD = { Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C };
    private static final String[] STR_ADAPTIVEMETHOD = { "ADAPTIVE_THRESH_MEAN_C", "ADAPTIVE_THRESH_GAUSSIAN_C" };
    private static final int[] INT_THRESHOLDTYPE = { Imgproc.THRESH_BINARY, Imgproc.THRESH_BINARY_INV };
    private static final String[] STR_THRESHOLDTYPE = { "THRESH_BINARY", "THRESH_BINARY_INV" };

    // static var.
    private static double maxValue = 255.0;
    private static int indMethod = 0;
    private static int indType = 0;
    private static int blockSize = 5;
    private static double constantOffset = 10.0;
    
    // var.
    private String className;
    private Mat src_mat = null;
    private Mat dst_mat = null;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        className = command.trim();
        GenericDialog gd = new GenericDialog(className + " ...");

        gd.addSlider("maxValue", 1, 255, maxValue);
        gd.addChoice("adaptiveMethod", STR_ADAPTIVEMETHOD, STR_ADAPTIVEMETHOD[indMethod]);
        gd.addChoice("thresholdType", STR_THRESHOLDTYPE, STR_THRESHOLDTYPE[indType]);
        gd.addNumericField("blockSize", blockSize, 0);
        gd.addNumericField("constSubtractedFromMean", constantOffset, 4);
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
        maxValue = (double)gd.getNextNumber();
        indMethod = (int)gd.getNextChoiceIndex();
        indType = (int)gd.getNextChoiceIndex();
        blockSize = (int)gd.getNextNumber();
        constantOffset = (double)gd.getNextNumber();

        if(Double.isNaN(maxValue) || Double.isNaN(constantOffset)) {
            IJ.showStatus("ERR : NaN");
            return false;
        }

        if(blockSize <= 1) {
            IJ.showStatus("'1 < blockSize' is necessary.");
            return false;
        }

        if(blockSize % 2 == 0) {
            IJ.showStatus("blockSize should be odd.");
            return false;
        }

        IJ.showStatus("OCV_AdaptiveThreshold");
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

        // srcdst
        byte[] srcdst_ar = (byte[])ip.getPixels();

        // mat - allocate or reuse
        if(src_mat == null || src_mat.width() != imw || src_mat.height() != imh) {
            releaseResources();
            src_mat = new Mat(imh, imw, CvType.CV_8UC1);
            dst_mat = new Mat(imh, imw, CvType.CV_8UC1);
        }

        // run
        try {
            src_mat.put(0, 0, srcdst_ar);
            Imgproc.adaptiveThreshold(src_mat, dst_mat, maxValue, INT_ADAPTIVEMETHOD[indMethod], INT_THRESHOLDTYPE[indType], blockSize, constantOffset);
            dst_mat.get(0, 0, srcdst_ar);
        } catch(Exception e) {
            IJ.log("Adaptive threshold failed: " + e.getMessage());
            releaseResources();
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
    }
}