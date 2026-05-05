import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
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
 * watershed.
 */
public class OCV_Watershed implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener {
    // constant var.
    private static final int FLAGS = DOES_32 | DOES_RGB | KEEP_PREVIEW; // Input 8-bit 3-channel image, and Input/output 32-bit single-channel map.

    // static var.
    private static int indSrc = 0;
    private static int indMsk = 1;

    // var.
    private String className;
    private ImagePlus impSrc = null;
    private ImagePlus impMap = null;
    private int[] lstWnd;
    private String[] titlesWnd;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        className = command.trim();
        GenericDialog gd = new GenericDialog(className + " ...");

        gd.addChoice("src", titlesWnd, titlesWnd[indSrc]);
        gd.addChoice("mask", titlesWnd, titlesWnd[indMsk]);
        gd.addPreviewCheckbox(pfr);
        gd.addDialogListener(this);

        gd.showDialog();

        if(gd.wasCanceled()) {
            return DONE;
        }
        else {
            return FLAGS;
        }
    }

    @Override
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent awte) {
        indSrc = (int)gd.getNextChoiceIndex();
        indMsk = (int)gd.getNextChoiceIndex();

        if(indSrc == indMsk) {
            IJ.showStatus("The same image can not be selected.");
            return false;
        }

        impSrc = WindowManager.getImage(lstWnd[indSrc]);
        impMap = WindowManager.getImage(lstWnd[indMsk]);

        if(impSrc.getBitDepth() != 24 || impMap.getBitDepth() != 32) {
            IJ.showStatus("The image should be RGB, and the mask should be 32bit.");
            return false;
        }

        if(impSrc.getWidth() != impMap.getWidth() || impSrc.getHeight() != impMap.getHeight()) {
            IJ.showStatus("The size of src should be same as the size of mask.");
            return false;
        }

        IJ.showStatus("OCV_Watershed");
        return true;
    }

    @Override
    public void setNPasses(int nPasses) {
        // do nothing
    }

    @Override
    public int setup(String arg, ImagePlus imp) {
        if(!OCV__LoadLibrary.isLoad()) {
            OCV__LoadLibrary.logError("OCV_Watershed", "Library is not loaded.");
            return DONE;
        }

        if(imp == null) {
            IJ.noImage();
            return DONE;
        }
        else {
            // get the windows
            lstWnd = WindowManager.getIDList();

            if(lstWnd == null || lstWnd.length < 2) {
                OCV__LoadLibrary.logError("OCV_Watershed", "At least more than 2 images are needed.");
                return DONE;
            }

            titlesWnd = new String[lstWnd.length];

            for(int i = 0; i < lstWnd.length; i++) {
                ImagePlus imp2 = WindowManager.getImage(lstWnd[i]);
                titlesWnd[i] = imp2 != null ? imp2.getTitle() : "";
            }

            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip) {
        Mat matSrcRgb = null;
        Mat matMap32f = null;
        Mat matMap32s = null;

        try {
            // src (RGB)
            int[] arrSrcRgb = (int[])impSrc.getChannelProcessor().getPixels();
            int imwSrc = impSrc.getWidth();
            int imhSrc = impSrc.getHeight();
            matSrcRgb = new Mat(imhSrc, imwSrc, CvType.CV_8UC3);

            // map (32bit)
            float[] arrMap32f = (float[])impMap.getChannelProcessor().getPixels();
            int imwMap = impMap.getWidth();
            int imhMap = impMap.getHeight();
            matMap32f = new Mat(imhMap, imwMap, CvType.CV_32FC1);
            matMap32s = new Mat(imhMap, imwMap, CvType.CV_32SC1);

            // run
            OCV__LoadLibrary.intarray2mat(arrSrcRgb, matSrcRgb, imwSrc, imhSrc);
            matMap32f.put(0, 0, arrMap32f);
            matMap32f.convertTo(matMap32s, CvType.CV_32SC1);

            Imgproc.watershed(matSrcRgb, matMap32s);

            matMap32s.convertTo(matMap32f, CvType.CV_32FC1);
            matMap32f.get(0, 0, arrMap32f);
        }
        catch(Exception e) {
            OCV__LoadLibrary.logError(className, "Watershed failed. (" + e.getMessage() + ")");
        }
        finally {
            if(matSrcRgb != null) {
                matSrcRgb.release();
            }
            if(matMap32f != null) {
                matMap32f.release();
            }
            if(matMap32s != null) {
                matMap32s.release();
            }
        }
    }
}