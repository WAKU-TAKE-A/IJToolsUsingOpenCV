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
 * distanceTransform (OpenCV4.5.3).
 */
public class OCV_DistanceTransform implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener {
    // constant var.
    private static final int FLAGS =  DOES_32 | KEEP_PREVIEW;

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
    private static final int[] INT_DISTANCETYPE = { Imgproc.CV_DIST_L1, Imgproc.CV_DIST_L2, Imgproc.CV_DIST_C, Imgproc.CV_DIST_L12, Imgproc.DIST_FAIR, Imgproc.DIST_WELSCH, Imgproc.DIST_HUBER };
    private static final String[] STR_DISTANCETYPE = { "CV_DIST_L1", "CV_DIST_L2", "CV_DIST_C" };

    private static final int[] INT_DISTANCETRANSFORMMASKS = { Imgproc.CV_DIST_MASK_3, Imgproc.CV_DIST_MASK_5, Imgproc.CV_DIST_MASK_PRECISE  };
    private static final String[] STR_DISTANCETRANSFORMMASKS = { "CV_DIST_MASK_3", "CV_DIST_MASK_5", "CV_DIST_MASK_PRECISE" };

    // staic var.
    private static int indDistType = 0;
    private static int indMskSize = 0;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        GenericDialog gd = new GenericDialog(command.trim() + " ...");

        gd.addChoice("distanceType", STR_DISTANCETYPE, STR_DISTANCETYPE[indDistType]);
        gd.addChoice("maskSize", STR_DISTANCETRANSFORMMASKS, STR_DISTANCETRANSFORMMASKS[indMskSize]);
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
        // srcdst
        int imw = ip.getWidth();
        int imh = ip.getHeight();
        float[] srcdst_floats = (float[])ip.getPixels();

        // mat
        Mat src_mat_32f = new Mat(imh, imw, CvType.CV_32FC1);
        Mat src_mat_8u = new Mat(imh, imw, CvType.CV_8UC1);
        Mat dst_mat_32f = new Mat(imh, imw, CvType.CV_32FC1);

        // run
        src_mat_32f.put(0, 0, srcdst_floats);
        src_mat_32f.convertTo(src_mat_8u, CvType.CV_8UC1);
        Imgproc.distanceTransform(src_mat_8u, dst_mat_32f, INT_DISTANCETYPE[indDistType], INT_DISTANCETRANSFORMMASKS[indMskSize]);
        dst_mat_32f.get(0, 0, srcdst_floats);
    }
}
