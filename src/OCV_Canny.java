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
 * Canny.
 */
public class OCV_Canny implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener {
    // constant var.
    private static final int FLAGS = DOES_8G | KEEP_PREVIEW; // 8-bit input image.
    private static final String[] SIZE_STRINGS = new String[] { "3", "5", "7" };
    private static final int[] SIZE_VALUES = new int[] { 3, 5, 7 };

    // static var.
    private static double thr1 = 0; // first threshold for the hysteresis procedure.
    private static double thr2 = 0; // second threshold for the hysteresis procedure.
    private static int indSize = 0; // aperture size for the Sobel operator.
    private static boolean l2grad = false; // L2gradient;

    // instance var.
    private Mat src_mat = null;
    private Mat dst_mat = null;
    private int cachedWidth = -1;
    private int cachedHeight = -1;
    private String className = null;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        className = command.trim();
        GenericDialog gd = new GenericDialog(className + " ...");

        gd.addNumericField("threshold1", thr1, 4);
        gd.addNumericField("threshold2", thr2, 4);
        gd.addChoice("apertureSize", SIZE_STRINGS, SIZE_STRINGS[indSize]);
        gd.addCheckbox("L2gradient", l2grad);
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
        thr1 = (double)gd.getNextNumber();
        thr2 = (double)gd.getNextNumber();
        indSize = (int)gd.getNextChoiceIndex();
        l2grad = (boolean)gd.getNextBoolean();

        if(Double.isNaN(thr1) || Double.isNaN(thr2)) {
            IJ.showStatus("ERR : NaN");
            return false;
        }

        if(thr1 < 0) {
            IJ.showStatus("'0 <= threshold1' is necessary.");
            return false;
        }

        if(thr2 < 0) {
            IJ.showStatus("'0 <= threshold2' is necessary.");
            return false;
        }

        if(thr1 > thr2) {
            IJ.showStatus("Warning: threshold1 should be <= threshold2 for typical usage.");
        }

        IJ.showStatus("OCV_Canny");
        return true;
    }

    @Override
    public void setNPasses(int nPasses) {
        // do nothing
    }

    @Override
    public int setup(String arg, ImagePlus imp) {
        if(!OCV__LoadLibrary.isLoad()) {
            OCV__LoadLibrary.logError("OCV_Canny", "Library is not loaded.");
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
        byte[] srcdst_bytes = (byte[])ip.getPixels();

        try {
            allocateMatIfNeeded(imw, imh);

            src_mat.put(0, 0, srcdst_bytes);
            Imgproc.Canny(src_mat, dst_mat, thr1, thr2, SIZE_VALUES[indSize], l2grad);
            dst_mat.get(0, 0, srcdst_bytes);
        }
        catch(Exception e) {
            OCV__LoadLibrary.logError(className, "Canny edge detection failed (" + e.getMessage() + ")");
            releaseResources();
        }
    }

    private void allocateMatIfNeeded(int imw, int imh) {
        if(src_mat == null || cachedWidth != imw || cachedHeight != imh) {
            releaseResources();
            src_mat = new Mat(imh, imw, CvType.CV_8UC1);
            dst_mat = new Mat(imh, imw, CvType.CV_8UC1);
            cachedWidth = imw;
            cachedHeight = imh;
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
        cachedWidth = -1;
        cachedHeight = -1;
    }
}