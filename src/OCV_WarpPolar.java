import ij.*;
import ij.IJ;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.*;
import ij.process.*;
import java.awt.AWTEvent;
import java.awt.Rectangle;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
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
 * warpPolar.
 */
public class OCV_WarpPolar implements ExtendedPlugInFilter, DialogListener {
    // constant var.
    private static final int FLAGS = PlugInFilter.DOES_8G | PlugInFilter.DOES_RGB | PlugInFilter.DOES_16 | PlugInFilter.DOES_32;

    /*
    Specify the polar mapping mode.

    * WARP_POLAR_LINEAR :   Remaps an image to/from polar space.
    * WARP_POLAR_LOG   :    Remaps an image to/from polar space.
    */
    private static final int[] INT_MODE = { Imgproc.WARP_POLAR_LINEAR, Imgproc.WARP_POLAR_LOG };
    private static final String[] STR_MODE = { "WARP_POLAR_LINEAR", "WARP_POLAR_LOG" };

    /*
    interpolation algorithm

    * INTER_NEAREST   : nearest neighbor interpolation
    * INTER_LINEAR    : bilinear interpolation
    * INTER_CUBIC     : bicubic interpolation
    * INTER_AREA      : resampling using pixel area relation
    * INTER_LANCZOS4  : Lanczos interpolation over 8x8 neighborhood
    * INTER_LINEAR_EXACT:   Bit exact bilinear interpolation(Error occurred)
    * INTER_MAX       : mask for interpolation codes(Error occurred)
    * WARP_FILL_OUTLIERS:   flag, fills all of the destination image pixels
    */
    private static final int[] INT_INTERPOLATION = { Imgproc.INTER_NEAREST, Imgproc.INTER_LINEAR, Imgproc.INTER_CUBIC, Imgproc.INTER_AREA, Imgproc.INTER_LANCZOS4/*, Imgproc.INTER_LINEAR_EXACT,  Imgproc.INTER_MAX*/,  Imgproc.WARP_FILL_OUTLIERS };
    private static final String[] STR_INTERPOLATION = { "INTER_NEAREST", "INTER_LINEAR", "INTER_CUBIC", "INTER_AREA", "INTER_LANCZOS4"/*, "INTER_LINEAR_EXACT", "INTER_MAX"*/, "WARP_FILL_OUTLIERS" };

    // static var.
    private static Rectangle rect = new Rectangle(0, 0, 0, 0);
    private static int cx = 0;
    private static int cy = 0;
    private static int destW = 0;
    private static int destH = 0;
    private static int rmax = 0;
    private static int indMode = 0;
    private static int indInterpolation = 0;
    private static boolean enInverse = false;

    // var
    private String className;
    private String titleSrc = "";

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        className = command.trim();
        rect = imp.getRoi().getBounds();

        if(rmax == 0) {
            rmax = (int)Math.min(rect.getWidth() / 2, rect.getHeight() / 2);
        }

        if(destW == 0 || destH == 0) {
            destW = imp.getWidth();
            destH = imp.getHeight();
        }

        GenericDialog gd = new GenericDialog(className + "...");

        gd.addNumericField("center_x", rect.getX() + rect.getWidth() / 2, 0);
        gd.addNumericField("center_y", rect.getY() + rect.getHeight() / 2, 0);
        gd.addNumericField("destination_width", destW, 0);
        gd.addNumericField("destination_height", destH, 0);
        gd.addNumericField("max_radius", rmax, 0);
        gd.addChoice("mode", STR_MODE, STR_MODE[indMode]);
        gd.addChoice("interpolation", STR_INTERPOLATION, STR_INTERPOLATION[indInterpolation]);
        gd.addCheckbox("enable_inverse", enInverse);
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
        cx = (int)gd.getNextNumber();
        cy = (int)gd.getNextNumber();
        destW = (int)gd.getNextNumber();
        destH = (int)gd.getNextNumber();
        rmax = (int)gd.getNextNumber();
        indMode = gd.getNextChoiceIndex();
        indInterpolation = gd.getNextChoiceIndex();
        enInverse = gd.getNextBoolean();

        if(cx < 0) {
            IJ.showStatus("center_x must be >= 0");
            return false;
        }

        if(cy < 0) {
            IJ.showStatus("center_y must be >= 0");
            return false;
        }

        if(destW <= 0) {
            IJ.showStatus("destination_width must be > 0");
            return false;
        }

        if(destH <= 0) {
            IJ.showStatus("destination_height must be > 0");
            return false;
        }

        if(rmax <= 0) {
            IJ.showStatus("max_radius must be > 0");
            return false;
        }

        IJ.showStatus("OCV_WarpPolar");
        return true;
    }

    @Override
    public void setNPasses(int arg0) {
        // do nothing
    }

    @Override
    public int setup(String arg0, ImagePlus imp) {
        if(!OCV__LoadLibrary.isLoad()) {
            OCV__LoadLibrary.logError("OCV_WarpPolar", "Library is not loaded.");
            return PlugInFilter.DONE;
        }

        if(imp == null) {
            IJ.noImage();
            return PlugInFilter.DONE;
        }
        else {
            titleSrc = imp.getTitle();
            
            // 常に全画像を選択
            imp.setRoi(0, 0, imp.getWidth(), imp.getHeight());
            rect = imp.getRoi().getBounds();

            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip) {
        int imw = ip.getWidth();
        int imh = ip.getHeight();
        int bitDepth = ip.getBitDepth();
        int flags = INT_MODE[indMode] + INT_INTERPOLATION[indInterpolation] + (enInverse ? Imgproc.WARP_INVERSE_MAP : 0);

        try {
            if(bitDepth == 8) {
                Mat srcMat = null;
                Mat dstMat = null;

                try {
                    byte[] srcByte = (byte[])ip.getPixels();
                    String titleDst = WindowManager.getUniqueName(titleSrc + "_WarpPolar");
                    ImagePlus impDst = new ImagePlus(titleDst, new ByteProcessor(destW, destH));
                    byte[] dstByte = (byte[])impDst.getChannelProcessor().getPixels();

                    srcMat = new Mat(imh, imw, CvType.CV_8UC1);
                    dstMat = new Mat(destH, destW, CvType.CV_8UC1);

                    srcMat.put(0, 0, srcByte);
                    Imgproc.warpPolar(srcMat, dstMat, new Size(destW, destH), new Point(cx, cy), rmax, flags);
                    dstMat.get(0, 0, dstByte);

                    impDst.show();
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
                    short[] srcShort = (short[])ip.getPixels();
                    String titleDst = WindowManager.getUniqueName(titleSrc + "_WarpPolar");
                    ImagePlus impDst = new ImagePlus(titleDst, new ShortProcessor(destW, destH));
                    short[] dstShort = (short[])impDst.getChannelProcessor().getPixels();

                    srcMat = new Mat(imh, imw, CvType.CV_16U);
                    dstMat = new Mat(destH, destW, CvType.CV_16U);

                    srcMat.put(0, 0, srcShort);
                    Imgproc.warpPolar(srcMat, dstMat, new Size(destW, destH), new Point(cx, cy), rmax, flags);
                    dstMat.get(0, 0, dstShort);

                    impDst.show();
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
                    int[] srcInt = (int[])ip.getPixels();
                    String titleDst = WindowManager.getUniqueName(titleSrc + "_WarpPolar");
                    ImagePlus impDst = IJ.createImage(titleDst, destW, destH, 1, 24);
                    int[] dstInt = (int[])impDst.getChannelProcessor().getPixels();

                    srcMat = new Mat(imh, imw, CvType.CV_8UC3);
                    dstMat = new Mat(destH, destW, CvType.CV_8UC3);

                    OCV__LoadLibrary.intarray2mat(srcInt, srcMat, imw, imh);
                    Imgproc.warpPolar(srcMat, dstMat, new Size(destW, destH), new Point(cx, cy), rmax, flags);
                    OCV__LoadLibrary.mat2intarray(dstMat, dstInt, destW, destH);

                    impDst.show();
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
                    float[] srcFloat = (float[])ip.getPixels();
                    String titleDst = WindowManager.getUniqueName(titleSrc + "_WarpPolar");
                    ImagePlus impDst = new ImagePlus(titleDst, new FloatProcessor(destW, destH));
                    float[] dstFloat = (float[])impDst.getChannelProcessor().getPixels();

                    srcMat = new Mat(imh, imw, CvType.CV_32F);
                    dstMat = new Mat(destH, destW, CvType.CV_32F);

                    srcMat.put(0, 0, srcFloat);
                    Imgproc.warpPolar(srcMat, dstMat, new Size(destW, destH), new Point(cx, cy), rmax, flags);
                    dstMat.get(0, 0, dstFloat);

                    impDst.show();
                }
                finally {
                    if(srcMat != null) srcMat.release();
                    if(dstMat != null) dstMat.release();
                }
            }
        } catch(Exception e) {
            OCV__LoadLibrary.logError(className, e.getMessage());
        }
    }
}