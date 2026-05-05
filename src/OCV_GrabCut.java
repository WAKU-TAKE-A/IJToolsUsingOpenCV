import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import java.awt.AWTEvent;
import java.awt.Rectangle;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.Rect;

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
 * grabCut.
 */
public class OCV_GrabCut implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener {
    // constant var.
    private static final int FLAGS = DOES_8G | DOES_RGB | KEEP_PREVIEW; // Input 8-bit 3-channel image, and Input/output 8-bit single-channel mask.
    private static final String[] TYPE_STR = new String[] { "GC_INIT_WITH_RECT", "GC_INIT_WITH_MASK" };
    private static final int[] TYPE_VAL = new int[] { Imgproc.GC_INIT_WITH_RECT, Imgproc.GC_INIT_WITH_MASK };

    // static var.
    private static int indSrc = 0;
    private static int indMsk = 1;
    private static int indType = 0;
    private static int iter = 3;
    private static boolean enFgd = true;

    // var.
    private ImagePlus impSrc = null;
    private ImagePlus impMsk = null;
    private Rect rect = null;
    private String className = null;
    private int[] lstWnd;
    private String[] titlesWnd;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        className = command.trim();
        GenericDialog gd = new GenericDialog(className + "...");

        gd.addChoice("src", titlesWnd, titlesWnd[indSrc]);
        gd.addChoice("mask", titlesWnd, titlesWnd[indMsk]);
        gd.addNumericField("iterCount", iter, 0);
        gd.addChoice("mode", TYPE_STR, TYPE_STR[indType]);
        gd.addCheckbox("enable_foreground_is_255", enFgd);
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
        iter = (int)gd.getNextNumber();
        indType = (int)gd.getNextChoiceIndex();
        enFgd = (boolean)gd.getNextBoolean();

        if(indSrc == indMsk) {
            IJ.showStatus("The same image can not be selected.");
            return false;
        }

        impSrc = WindowManager.getImage(lstWnd[indSrc]);
        impMsk = WindowManager.getImage(lstWnd[indMsk]);

        if(impSrc.getBitDepth() != 24 || impMsk.getBitDepth() != 8) {
            IJ.showStatus("The image should be RGB, and the mask should be 8bit gray.");
            return false;
        }

        if(impSrc.getWidth() != impMsk.getWidth() || impSrc.getHeight() != impMsk.getHeight()) {
            IJ.showStatus("The size of src should be same as the size of mask.");
            return false;
        }

        IJ.showStatus("OCV_GrabCut");
        return true;
    }

    @Override
    public void setNPasses(int nPasses) {
        // do nothing
    }

    @Override
    public int setup(String arg, ImagePlus imp) {
        if(!OCV__LoadLibrary.isLoad()) {
            OCV__LoadLibrary.logError("OCV_GrabCut", "Library is not loaded.");
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
                OCV__LoadLibrary.logError("OCV_GrabCut", "At least more than 2 images are needed.");
                return DONE;
            }

            titlesWnd = new String[lstWnd.length];

            for(int i = 0; i < lstWnd.length; i++) {
                ImagePlus imp2 = WindowManager.getImage(lstWnd[i]);
                titlesWnd[i] = imp2 != null ? imp2.getTitle() : "";
            }

            // get the ROI
            Rectangle rectJava;

            if(imp.getRoi() != null) {
                rectJava = imp.getRoi().getBounds();
            }
            else {
                rectJava = new Rectangle(1, 1, imp.getWidth() - 2, imp.getHeight() - 2);
            }

            rect = new Rect(rectJava.x, rectJava.y, rectJava.width, rectJava.height);

            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip) {
        Mat matSrc = null;
        Mat matMsk = null;
        Mat bgdModel = null;
        Mat fgdModel = null;

        try {
            // src (RGB)
            int[] srcArr = (int[])impSrc.getChannelProcessor().getPixels();
            int imwSrc = impSrc.getWidth();
            int imhSrc = impSrc.getHeight();
            matSrc = new Mat(imhSrc, imwSrc, CvType.CV_8UC3);
            OCV__LoadLibrary.intarray2mat(srcArr, matSrc, imwSrc, imhSrc);

            // tmp (Gray)
            byte[] mskArr = (byte[])impMsk.getChannelProcessor().getPixels();
            int imwMsk = impMsk.getWidth();
            int imhMsk = impMsk.getHeight();
            int numpixMsk = imwMsk * imhMsk;

            // output
            matMsk = new Mat(imhMsk, imwMsk, CvType.CV_8UC1);
            bgdModel = new Mat();
            fgdModel = new Mat();

            // run
            matMsk.put(0, 0, mskArr);
            Imgproc.grabCut(matSrc, matMsk, rect, bgdModel, fgdModel, iter, TYPE_VAL[indType]);
            matMsk.get(0, 0, mskArr);

            if(enFgd) {
                for(int i = 0; i < numpixMsk; i++) {
                    if(mskArr[i] == Imgproc.GC_FGD || mskArr[i] == Imgproc.GC_PR_FGD) {
                        mskArr[i] = (byte)255;
                    }
                    else {
                        mskArr[i] = (byte)0;
                    }
                }
            }
        }
        catch(Exception e) {
            OCV__LoadLibrary.logError(className, "GrabCut failed (" + e.getMessage() + ")");
        }
        finally {
            if(matSrc != null) {
                matSrc.release();
            }
            if(matMsk != null) {
                matMsk.release();
            }
            if(bgdModel != null) {
                bgdModel.release();
            }
            if(fgdModel != null) {
                fgdModel.release();
            }
        }
    }
}