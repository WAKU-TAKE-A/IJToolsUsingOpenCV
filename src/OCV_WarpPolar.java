import ij.*;
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
    private final int FLAGS = DOES_8G | DOES_RGB | DOES_16 | DOES_32;

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
    private static int dest_w = 0;
    private static int dest_h = 0;
    private static int rmax = 0;
    private static int indMode = 0;
    private static int indInterpolation = 0;
    private static boolean enInverse = false;

    // var
    private String titleSrc = "";

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        rect = imp.getRoi().getBounds();

        if(rmax == 0) {
            rmax = (int)(rect.getWidth() / 2 < rect.getHeight() / 2 ? rect.getWidth() / 2 : rect.getHeight() / 2);
        }

        if(dest_w == 0 || dest_h == 0) {
            dest_w = imp.getWidth();
            dest_h = imp.getHeight();
        }

        GenericDialog gd = new GenericDialog(command.trim() + "...");

        gd.addNumericField("center_x", rect.getX() + rect.getWidth() / 2, 0);
        gd.addNumericField("center_y", rect.getY() + rect.getHeight() / 2, 0);
        gd.addNumericField("destination_width", dest_w, 0);
        gd.addNumericField("destination_height", dest_h, 0);
        gd.addNumericField("max_radius", rmax, 0);
        gd.addChoice("mode", STR_MODE, STR_MODE[indMode]);
        gd.addChoice("interpolation", STR_INTERPOLATION, STR_INTERPOLATION[indInterpolation]);
        gd.addCheckbox("enable_inverse", enInverse);
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
        cx = (int)gd.getNextNumber();
        cy = (int)gd.getNextNumber();
        dest_w = (int)gd.getNextNumber();
        dest_h = (int)gd.getNextNumber();
        rmax = (int)gd.getNextNumber();
        indMode = (int)gd.getNextChoiceIndex();
        indInterpolation = (int)gd.getNextChoiceIndex();
        enInverse = (boolean)gd.getNextBoolean();

        if(cx < 0) {
            IJ.showStatus("'0 <= center_x' is necessary.");
            return false;
        }

        if(cy < 0) {
            IJ.showStatus("'0 <= center_y' is necessary.");
            return false;
        }

        if(dest_w <= 0) {
            IJ.showStatus("'0 < destination_width' is necessary.");
            return false;
        }

        if(dest_h <= 0) {
            IJ.showStatus("'0 < destination_height' is necessary.");
            return false;
        }

        if(rmax <= 0) {
            IJ.showStatus("'0 < max_radius' is necessary.");
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
            IJ.error("Library is not loaded.");
            return DONE;
        }

        if(imp == null) {
            IJ.noImage();
            return DONE;
        }
        else {
            titleSrc = imp.getTitle();

            if(imp.getRoi() != null) {
                rect = imp.getRoi().getBounds();
            }
            else {
                rect = new Rectangle(0, 0, imp.getWidth(), imp.getHeight());
            }

            imp.setRoi(0, 0, imp.getWidth(), imp.getHeight());
            imp.setRoi(rect);

            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip) {
        if(ip.getBitDepth() == 8) {
            // src
            int imw = ip.getWidth();
            int imh = ip.getHeight();
            byte[] src_byte = (byte[])ip.getPixels();

            // dst
            String titleDst = WindowManager.getUniqueName(titleSrc + "_WarpPolar");
            ImagePlus impDst = new ImagePlus(titleDst, new ByteProcessor(dest_w, dest_h));
            byte[] dst_byte = (byte[]) impDst.getChannelProcessor().getPixels();

            // mat
            Mat src_mat = new Mat(imh, imw, CvType.CV_8UC1);
            Mat dst_mat = new Mat(dest_w, dest_h, CvType.CV_8UC1);

            // flag
            int flags = INT_MODE[indMode] + INT_INTERPOLATION[indInterpolation] + (enInverse ? Imgproc.WARP_INVERSE_MAP : 0);

            // run
            src_mat.put(0, 0, src_byte);
            Imgproc.warpPolar(src_mat, dst_mat, new Size(dest_w, dest_h), new Point(cx, cy), (double)rmax, flags);
            dst_mat.get(0, 0, dst_byte);

            // show
            impDst.show();
        }
        else if(ip.getBitDepth() == 16) {
            // src
            int imw = ip.getWidth();
            int imh = ip.getHeight();
            short[] src_short = (short[])ip.getPixels();

            // dst
            String titleDst = WindowManager.getUniqueName(titleSrc + "_WarpPolar");
            ImagePlus impDst = new ImagePlus(titleDst, new ShortProcessor(dest_w, dest_h));
            short[] dst_short = (short[]) impDst.getChannelProcessor().getPixels();

            // mat
            Mat src_mat = new Mat(imh, imw, CvType.CV_16S);
            Mat dst_mat = new Mat(dest_w, dest_h, CvType.CV_16S);

            // flag
            int flags = INT_MODE[indMode] + INT_INTERPOLATION[indInterpolation] + (enInverse ? Imgproc.WARP_INVERSE_MAP : 0);

            // run
            src_mat.put(0, 0, src_short);
            Imgproc.warpPolar(src_mat, dst_mat, new Size(dest_w, dest_h), new Point(cx, cy), (double)rmax, flags);
            dst_mat.get(0, 0, dst_short);

            // show
            impDst.show();
        }
        else if(ip.getBitDepth() == 24) {
            // src
            int imw = ip.getWidth();
            int imh = ip.getHeight();
            int[] src_int = (int[])ip.getPixels();

            // dst
            String titleDst = WindowManager.getUniqueName(titleSrc + "_WarpPolar");
            ImagePlus  impDst = IJ.createImage(titleDst, dest_w, dest_h, 1, 24);
            int[] dst_int = (int[])impDst.getChannelProcessor().getPixels();

            // mat
            Mat src_mat = new Mat(imh, imw, CvType.CV_8UC3);
            Mat dst_mat = new Mat(dest_w, dest_h, CvType.CV_8UC3);

            // flag
            int flags = INT_MODE[indMode] + INT_INTERPOLATION[indInterpolation] + (enInverse ? Imgproc.WARP_INVERSE_MAP : 0);

            // run
            OCV__LoadLibrary.intarray2mat(src_int, src_mat, imw, imh);
            Imgproc.warpPolar(src_mat, dst_mat, new Size(dest_w, dest_h), new Point(cx, cy), (double)rmax, flags);
            OCV__LoadLibrary.mat2intarray(dst_mat, dst_int, dest_w, dest_h);

            // show
            impDst.show();
        }
        else if(ip.getBitDepth() == 32) {
            // src
            int imw = ip.getWidth();
            int imh = ip.getHeight();
            float[] src_float = (float[])ip.getPixels();

            // dst
            String titleDst = WindowManager.getUniqueName(titleSrc + "_WarpPolar");
            ImagePlus impDst = new ImagePlus(titleDst, new FloatProcessor(dest_w, dest_h));
            float[] dst_float = (float[]) impDst.getChannelProcessor().getPixels();

            // flag
            int flags = INT_MODE[indMode] + INT_INTERPOLATION[indInterpolation] + (enInverse ? Imgproc.WARP_INVERSE_MAP : 0);

            // mat
            Mat src_mat = new Mat(imh, imw, CvType.CV_32F);
            Mat dst_mat = new Mat(dest_w, dest_h, CvType.CV_32F);

            // run
            src_mat.put(0, 0, src_float);
            Imgproc.warpPolar(src_mat, dst_mat, new Size(dest_w, dest_h), new Point(cx, cy), (double)rmax, flags);
            dst_mat.get(0, 0, dst_float);

            // show
            impDst.show();
        }
        else {
            IJ.error("Wrong image format");
        }
    }
}
