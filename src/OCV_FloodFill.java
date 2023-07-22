import ij.*;
import ij.IJ;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import java.awt.AWTEvent;
import java.util.ArrayList;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
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
 * floodFill (OpenCV4.5.3).
 */
public class OCV_FloodFill implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener {
    // constant var.
    private static final int FLAGS = DOES_8G | DOES_RGB | DOES_32 | KEEP_PREVIEW; // 1- or 3-channel, 8-bit, or floating-point image.
    private static final int[] INT_FLAGS = { 4, 8, Imgproc.FLOODFILL_FIXED_RANGE };
    private static final String[] STR_ADAPTIVEMETHOD = { "4-connected", "8-connected", "FLOODFILL_FIXED_RANGE" };

    // staic var.
    private static double newVal_0 = 255.0;
    private static double newVal_1 = 255.0;
    private static double newVal_2 = 255.0;
    private static double loDiff_0 = 5.0;
    private static double loDiff_1 = 5.0;
    private static double loDiff_2 = 5.0;
    private static double upDiff_0 = 5.0;
    private static double upDiff_1 = 5.0;
    private static double upDiff_2 = 5.0;
    private static int indFlags = 1;

    // var.
    private RoiManager roiMan = null;
    private int[] selectedIndexes = null;
    private Scalar newVal = null;
    private Scalar loDiff = null;
    private Scalar upDiff = null;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        GenericDialog gd = new GenericDialog(command.trim() + " ...");

        gd.addNumericField("newVal_B", newVal_0, 3);
        gd.addNumericField("newVal_G", newVal_1, 3);
        gd.addNumericField("newVal_R", newVal_2, 3);
        gd.addNumericField("loDiff_B", loDiff_0, 3);
        gd.addNumericField("loDiff_G", loDiff_1, 3);
        gd.addNumericField("loDiff_R", loDiff_2, 3);
        gd.addNumericField("upDiff_B", upDiff_0, 3);
        gd.addNumericField("upDiff_G", upDiff_1, 3);
        gd.addNumericField("upDiff_R", upDiff_2, 3);
        gd.addChoice("adaptiveMethod", STR_ADAPTIVEMETHOD, STR_ADAPTIVEMETHOD[indFlags]);
        gd.addMessage("If the image is 8-bit or 32-bit,\nonly use the value written as *_B.");

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
        newVal_0 = (double)gd.getNextNumber();
        newVal_1 = (double)gd.getNextNumber();
        newVal_2 = (double)gd.getNextNumber();
        loDiff_0 = (double)gd.getNextNumber();
        loDiff_1 = (double)gd.getNextNumber();
        loDiff_2 = (double)gd.getNextNumber();
        upDiff_0 = (double)gd.getNextNumber();
        upDiff_1 = (double)gd.getNextNumber();
        upDiff_2 = (double)gd.getNextNumber();
        indFlags = (int)gd.getNextChoiceIndex();

        if(
            Double.isNaN(newVal_0) ||
            Double.isNaN(newVal_1) ||
            Double.isNaN(newVal_2) ||
            Double.isNaN(loDiff_0) ||
            Double.isNaN(loDiff_1) ||
            Double.isNaN(loDiff_2) ||
            Double.isNaN(upDiff_0) ||
            Double.isNaN(upDiff_1) ||
            Double.isNaN(upDiff_2)) {
            IJ.showStatus("ERR : NaN");
            return false;
        }

        newVal = new Scalar(newVal_0, newVal_1, newVal_2);
        loDiff = new Scalar(loDiff_0, loDiff_1, loDiff_2);
        upDiff = new Scalar(upDiff_0, upDiff_1, upDiff_2);

        IJ.showStatus("OCV_FloodFill");
        return true;
    }

    @Override
    public void setNPasses(int nPasses) {
        // do nothing
    }

    @Override
    public int setup(String string, ImagePlus imp) {
        if(!OCV__LoadLibrary.isLoad()) {
            IJ.error("Library is not loaded.");
            return DONE;
        }

        if(imp == null) {
            IJ.noImage();
            return DONE;
        }
        else {
            // get the ROI Manager
            roiMan = OCV__LoadLibrary.GetRoiManager(false, true);
            int num_roi = roiMan.getCount();

            if(num_roi == 0) {
                IJ.error("ROI is vacant. Select points.");
                return DONE;
            }

            // get the selected rois
            selectedIndexes = roiMan.getSelectedIndexes();

            if(selectedIndexes == null || selectedIndexes.length == 0) {
                selectedIndexes = new int[] { 0 };
            }

            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip) {
        // set varr.
        int imw = ip.getWidth();
        int imh = ip.getHeight();
        Rect rect = new Rect(0, 0, imw, imh);

        // get seed points
        int num_slctd = selectedIndexes.length;
        ArrayList<Point> lstPt = new ArrayList<Point>();

        for(int i = 0; i < num_slctd; i++) {
            Roi roi = roiMan.getRoi(selectedIndexes[i]);
            OCV__LoadLibrary.GetCoordinates(roi, lstPt);
        }


        if(ip.getBitDepth() == 8) {
            // srcdst
            byte[] srcdst_bytes = (byte[])ip.getPixels();

            // mat
            Mat srcdst_mat = new Mat(imh, imw, CvType.CV_8UC1);
            srcdst_mat.put(0, 0, srcdst_bytes);

            // run
            int num = lstPt.size();

            for(int i = 0; i < num; i++) {
                Mat msk_mat = Mat.zeros(imh + 2, imw + 2, CvType.CV_8UC1);
                Point pt = new Point(lstPt.get(i).x, lstPt.get(i).y);

                Imgproc.floodFill(srcdst_mat, msk_mat, pt, newVal, rect, loDiff, upDiff, INT_FLAGS[indFlags]);
            }

            srcdst_mat.get(0, 0, srcdst_bytes);
        }
        else if(ip.getBitDepth() == 24) {
            // srcdst
            int[] srcdst_ints = (int[])ip.getPixels();

            // mat
            Mat srcdst_mat = new Mat(imh, imw, CvType.CV_8UC3);
            OCV__LoadLibrary.intarray2mat(srcdst_ints, srcdst_mat, imw, imh);

            // run
            int num = lstPt.size();

            for(int i = 0; i < num; i++) {
                Mat msk_mat = Mat.zeros(imh + 2, imw + 2, CvType.CV_8UC1);
                Point pt = new Point(lstPt.get(i).x, lstPt.get(i).y);

                Imgproc.floodFill(srcdst_mat, msk_mat, pt, newVal, rect, loDiff, upDiff, INT_FLAGS[indFlags]);
            }

            OCV__LoadLibrary.mat2intarray(srcdst_mat, srcdst_ints, imw, imh);
        }
        else if(ip.getBitDepth() == 32) {
            // srcdst
            float[] srcdst_floats = (float[])ip.getPixels();

            // mat
            Mat srcdst_mat = new Mat(imh, imw, CvType.CV_32F);
            srcdst_mat.put(0, 0, srcdst_floats);

            // run
            int num = lstPt.size();

            for(int i = 0; i < num; i++) {
                Mat msk_mat = Mat.zeros(imh + 2, imw + 2, CvType.CV_8UC1);
                Point pt = new Point(lstPt.get(i).x, lstPt.get(i).y);

                Imgproc.floodFill(srcdst_mat, msk_mat, pt, newVal, rect, loDiff, upDiff, INT_FLAGS[indFlags]);
            }

            srcdst_mat.get(0, 0, srcdst_floats);
        }
    }
}
