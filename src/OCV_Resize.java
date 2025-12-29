import ij.*;
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
 * resize.
 */
public class OCV_Resize implements ExtendedPlugInFilter, DialogListener {
    // constant var.
    private static final int FLAGS = DOES_8G | DOES_RGB | DOES_16 | DOES_32;

    /*
    interpolation algorithm

    * INTER_NEAREST   : nearest neighbor interpolation
    * INTER_LINEAR    : bilinear interpolation
    * INTER_CUBIC     : bicubic interpolation
    * INTER_AREA      : resampling using pixel area relation
    * INTER_LANCZOS4  : Lanczos interpolation over 8x8 neighborhood
    * INTER_LINEAR_EXACT:   Bit exact bilinear interpolation
    * INTER_MAX       : mask for interpolation codes(Error occurred)
    * WARP_FILL_OUTLIERS:   flag, fills all of the destination image pixels(Error occurred)
    */
    private static final int[] INT_INTERPOLATION = { Imgproc.INTER_NEAREST, Imgproc.INTER_LINEAR, Imgproc.INTER_CUBIC, Imgproc.INTER_AREA, Imgproc.INTER_LANCZOS4, Imgproc.INTER_LINEAR_EXACT/*,  Imgproc.INTER_MAX,  Imgproc.WARP_FILL_OUTLIERS*/ };
    private static final String[] STR_INTERPOLATION = { "INTER_NEAREST", "INTER_LINEAR", "INTER_CUBIC", "INTER_AREA", "INTER_LANCZOS4", "INTER_LINEAR_EXACT"/*, "INTER_MAX", "WARP_FILL_OUTLIERS"*/ };

    // static var.
    private static double dsizeW = 0;
    private static double dsizeH = 0;
    private static double scaleW = 0;
    private static double scaleH = 0;
    private static int indInterpolation = 0;

    // var
    private String className;
    private String titleSrc = "";
    private ImagePlus impSrc = null;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        if(scaleW == 0 || scaleH == 0) {
            dsizeW = imp.getWidth();
            dsizeH = imp.getHeight();
        }
        
        className = command.trim();
        GenericDialog gd = new GenericDialog(className + "...");

        gd.addNumericField("dsize_w", dsizeW, 0);
        gd.addNumericField("dsize_h", dsizeH, 0);
        gd.addNumericField("scale_factor_x", scaleW, 4);
        gd.addNumericField("scale_factor_y", scaleH, 4);
        gd.addChoice("interpolation", STR_INTERPOLATION, STR_INTERPOLATION[indInterpolation]);
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
        dsizeW = gd.getNextNumber();
        dsizeH = gd.getNextNumber();
        scaleW = gd.getNextNumber();
        scaleH = gd.getNextNumber();
        indInterpolation = gd.getNextChoiceIndex();

        if(dsizeW < 0) {
            IJ.showStatus("dsize_w must be >= 0");
            return false;
        }

        if(dsizeH < 0) {
            IJ.showStatus("dsize_h must be >= 0");
            return false;
        }

        if(scaleW < 0) {
            IJ.showStatus("scale_factor_x must be >= 0");
            return false;
        }

        if(scaleH < 0) {
            IJ.showStatus("scale_factor_y must be >= 0");
            return false;
        }

        if(Double.isNaN(dsizeW) || Double.isNaN(dsizeH) || Double.isNaN(scaleW) || Double.isNaN(scaleH)) {
            IJ.showStatus("Error: NaN value detected");
            return false;
        }

        if(scaleW > 0 && scaleH > 0) {
            dsizeW = impSrc.getWidth() * scaleW;
            dsizeH = impSrc.getHeight() * scaleH;
        }

        if(dsizeW == 0 || dsizeH == 0) {
            IJ.showStatus("Output width and height must not be 0");
            return false;
        }

        IJ.showStatus("OCV_Resize");
        return true;
    }

    @Override
    public void setNPasses(int arg0) {
        // do nothing
    }

    @Override
    public int setup(String arg0, ImagePlus imp) {
        if(!OCV__LoadLibrary.isLoad()) {
            IJ.error("Library is not loaded.");
            return DONE;
        }

        if(imp == null) {
            IJ.noImage();
            return DONE;
        }
        else {
            titleSrc = imp.getTitle();
            impSrc = imp;
            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip) {
        int imw = ip.getWidth();
        int imh = ip.getHeight();
        int bitDepth = ip.getBitDepth();
        
        Size dsize = new Size(dsizeW, dsizeH);
        int dsizeWidth = (int)dsizeW;
        int dsizeHeight = (int)dsizeH;
        int flags = INT_INTERPOLATION[indInterpolation];

        try {
            if(bitDepth == 8) {
                Mat srcMat = null;
                Mat dstMat = null;

                try {
                    byte[] srcByte = (byte[])ip.getPixels();
                    String titleDst = WindowManager.getUniqueName(titleSrc + "_Resize");
                    ImagePlus impDst = new ImagePlus(titleDst, new ByteProcessor(dsizeWidth, dsizeHeight));
                    byte[] dstByte = (byte[])impDst.getChannelProcessor().getPixels();

                    srcMat = new Mat(imh, imw, CvType.CV_8UC1);
                    dstMat = new Mat(dsizeHeight, dsizeWidth, CvType.CV_8UC1);

                    srcMat.put(0, 0, srcByte);
                    Imgproc.resize(srcMat, dstMat, dsize, scaleW, scaleH, flags);
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
                    String titleDst = WindowManager.getUniqueName(titleSrc + "_Resize");
                    ImagePlus impDst = new ImagePlus(titleDst, new ShortProcessor(dsizeWidth, dsizeHeight));
                    short[] dstShort = (short[])impDst.getChannelProcessor().getPixels();

                    srcMat = new Mat(imh, imw, CvType.CV_16U);
                    dstMat = new Mat(dsizeHeight, dsizeWidth, CvType.CV_16U);

                    srcMat.put(0, 0, srcShort);
                    Imgproc.resize(srcMat, dstMat, dsize, scaleW, scaleH, flags);
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
                    String titleDst = WindowManager.getUniqueName(titleSrc + "_Resize");
                    ImagePlus impDst = IJ.createImage(titleDst, dsizeWidth, dsizeHeight, 1, 24);
                    int[] dstInt = (int[])impDst.getChannelProcessor().getPixels();

                    srcMat = new Mat(imh, imw, CvType.CV_8UC3);
                    dstMat = new Mat(dsizeHeight, dsizeWidth, CvType.CV_8UC3);

                    OCV__LoadLibrary.intarray2mat(srcInt, srcMat, imw, imh);
                    Imgproc.resize(srcMat, dstMat, dsize, scaleW, scaleH, flags);
                    OCV__LoadLibrary.mat2intarray(dstMat, dstInt, dsizeWidth, dsizeHeight);

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
                    String titleDst = WindowManager.getUniqueName(titleSrc + "_Resize");
                    ImagePlus impDst = new ImagePlus(titleDst, new FloatProcessor(dsizeWidth, dsizeHeight));
                    float[] dstFloat = (float[])impDst.getChannelProcessor().getPixels();

                    srcMat = new Mat(imh, imw, CvType.CV_32F);
                    dstMat = new Mat(dsizeHeight, dsizeWidth, CvType.CV_32F);

                    srcMat.put(0, 0, srcFloat);
                    Imgproc.resize(srcMat, dstMat, dsize, scaleW, scaleH, flags);
                    dstMat.get(0, 0, dstFloat);

                    impDst.show();
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
    }
}