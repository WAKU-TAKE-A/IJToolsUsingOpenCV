import ij.*;
import ij.IJ;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.*;
import ij.process.*;
import java.awt.AWTEvent;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
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
 * warpPerspective.
 */
public class OCV_WarpPerspective implements ExtendedPlugInFilter, DialogListener {
    // constant var.
    private static final int FLAGS = PlugInFilter.DOES_8G | PlugInFilter.DOES_RGB | PlugInFilter.DOES_16 | PlugInFilter.DOES_32 | ExtendedPlugInFilter.KEEP_PREVIEW;
    private static final int[] INT_INTERPOLATION = { Imgproc.INTER_NEAREST, Imgproc.INTER_LINEAR, Imgproc.INTER_CUBIC, Imgproc.INTER_AREA, Imgproc.INTER_LANCZOS4, Imgproc.WARP_FILL_OUTLIERS, Imgproc.WARP_INVERSE_MAP };
    private static final String[] STR_INTERPOLATION = { "INTER_NEAREST", "INTER_LINEAR", "INTER_CUBIC", "INTER_AREA", "INTER_LANCZOS4", "WARP_FILL_OUTLIERS", "INVERSE_TRANSFORMATION" };

    // static var.
    private static boolean useInverse = false;
    private static int flagsInd = 1;
    
    // var.
    private String className;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        className = command.trim();
        GenericDialog gd = new GenericDialog(className + " ...");
        
        gd.addCheckbox("use_inverse", useInverse);
        gd.addChoice("interpolation_method", STR_INTERPOLATION, STR_INTERPOLATION[flagsInd]);
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
        useInverse = gd.getNextBoolean();
        flagsInd = gd.getNextChoiceIndex();
        IJ.showStatus("OCV_WarpPerspective");
        return true;
    }

    @Override
    public void setNPasses(int arg0) {
        // do nothing
    }

    @Override
    public int setup(String arg0, ImagePlus imp) {
        if (!OCV__LoadLibrary.isLoad()) {
            OCV__LoadLibrary.logError("OCV_WarpPerspective", "Library is not loaded.");
            return PlugInFilter.DONE;
        }

        if (imp == null) {
            IJ.noImage();
            return PlugInFilter.DONE;
        }

        if (!OCV__LoadLibrary.MyPerspective.hasMatrix) {
            OCV__LoadLibrary.logError("OCV_WarpPerspective", "Matrix has not been generated yet.");
            return PlugInFilter.DONE;
        }

        return FLAGS;
    }

    @Override
    public void run(ImageProcessor ip) {
        int imw = ip.getWidth();
        int imh = ip.getHeight();
        int bitDepth = ip.getBitDepth();
        
        Size size = new Size(imw, imh);
        Mat mat;
        
        if (useInverse) {
            mat = OCV__LoadLibrary.MyPerspective.PerspectiveInverse;
        } else {
            mat = OCV__LoadLibrary.MyPerspective.PerspectiveMatrix;
        }

        if (mat == null || mat.empty()) {
            OCV__LoadLibrary.logError(className, "Invalid transformation matrix");
            return;
        }

        Mat srcMat = null;
        Mat dstMat = null;
        
        try {
            if(bitDepth == 8) {
                byte[] srcdstArray = (byte[])ip.getPixels();
                srcMat = new Mat(imh, imw, CvType.CV_8UC1);
                dstMat = new Mat(imh, imw, CvType.CV_8UC1);

                srcMat.put(0, 0, srcdstArray);
                Imgproc.warpPerspective(srcMat, dstMat, mat, size, INT_INTERPOLATION[flagsInd]);
                dstMat.get(0, 0, srcdstArray);
            }
            else if(bitDepth == 16) {
                short[] srcdstArray = (short[])ip.getPixels();
                srcMat = new Mat(imh, imw, CvType.CV_16U);
                dstMat = new Mat(imh, imw, CvType.CV_16U);

                srcMat.put(0, 0, srcdstArray);
                Imgproc.warpPerspective(srcMat, dstMat, mat, size, INT_INTERPOLATION[flagsInd]);
                dstMat.get(0, 0, srcdstArray);
            }
            else if(bitDepth == 24) {
                int[] srcdstArray = (int[])ip.getPixels();
                srcMat = new Mat(imh, imw, CvType.CV_8UC3);
                dstMat = new Mat(imh, imw, CvType.CV_8UC3);

                OCV__LoadLibrary.intarray2mat(srcdstArray, srcMat, imw, imh);
                Imgproc.warpPerspective(srcMat, dstMat, mat, size, INT_INTERPOLATION[flagsInd]);
                OCV__LoadLibrary.mat2intarray(dstMat, srcdstArray, imw, imh);
            }
            else if(bitDepth == 32) {
                float[] srcdstArray = (float[])ip.getPixels();
                srcMat = new Mat(imh, imw, CvType.CV_32F);
                dstMat = new Mat(imh, imw, CvType.CV_32F);

                srcMat.put(0, 0, srcdstArray);
                Imgproc.warpPerspective(srcMat, dstMat, mat, size, INT_INTERPOLATION[flagsInd]);
                dstMat.get(0, 0, srcdstArray);
            }
        }
        catch(Exception e) {
            OCV__LoadLibrary.logError(className, e.getMessage());
        }
        finally {
            if (srcMat != null) srcMat.release();
            if (dstMat != null) dstMat.release();
        }
    }
}