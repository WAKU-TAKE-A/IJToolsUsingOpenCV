import ij.*;
import ij.IJ;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import java.awt.AWTEvent;
import org.opencv.core.Core;
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
 * blur - Improved version with OpenCV-based processing.
 */
public class OCUtil_BluredImageDiff implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener {
    // constant var.
    private static final int FLAGS = DOES_8G | DOES_RGB | DOES_16 | DOES_32 | KEEP_PREVIEW;

    /*
     Various border types, image boundaries are denoted with '|'

     * BORDER_CONSTANT:      iiiiii|abcdefgh|iiiiiii with some specified i
     * BORDER_REPLICATE:     aaaaaa|abcdefgh|hhhhhhh
     * BORDER_REFLECT:       fedcba|abcdefgh|hgfedcb
     * BORDER_REFLECT_101:   gfedcb|abcdefgh|gfedcba
     * BORDER_WRAP:          cdefgh|abcdefgh|abcdefg (Error occurred)
     * BORDER_TRANSPARENT:   uvwxyz|abcdefgh|ijklmno (Error occurred)
     * BORDER_ISOLATED:      do not look outside of ROI
     */
    private static final int[] INT_BORDERTYPE = { Core.BORDER_CONSTANT, Core.BORDER_REPLICATE, Core.BORDER_REFLECT, Core.BORDER_REFLECT101, /*Core.BORDER_WRAP, Core.BORDER_TRANSPARENT,*/ Core.BORDER_ISOLATED };
    private static final String[] STR_BORDERTYPE = { "BORDER_CONSTANT", "BORDER_REPLICATE", "BORDER_REFLECT", "BORDER_REFLECT101", /*"BORDER_WRAP", "BORDER_TRANSPARENT",*/ "BORDER_ISOLATED" };

    // static var. - kernel sizes as integers
    private static int small_ksize_x = 3;     // small blurring kernel size of x
    private static int small_ksize_y = 3;     // small blurring kernel size of y
    private static int large_ksize_x = 31;    // large blurring kernel size of x
    private static int large_ksize_y = 31;    // large blurring kernel size of y
    private static double offset = 128;
    private static int indBorderType = 2;     // Border type

    // var.
    private String className;
    private int bitDepth = 0;
    private Size small_ksize = null;
    private Size large_ksize = null;
    
    // Variables for Mat reuse
    private int lastWidth = 0;
    private int lastHeight = 0;
    private int lastBitDepth = 0;
    private Mat src_mat = null;
    private Mat blur_small = null;
    private Mat blur_large = null;
    private Mat blur_small_32f = null;
    private Mat blur_large_32f = null;
    private Mat diff_mat = null;
    private Mat result_mat = null;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        className = command.trim();
        GenericDialog gd = new GenericDialog(className + " ...");

        gd.addNumericField("small_ksize_x", small_ksize_x, 0);
        gd.addNumericField("small_ksize_y", small_ksize_y, 0);
        gd.addNumericField("large_ksize_x", large_ksize_x, 0);
        gd.addNumericField("large_ksize_y", large_ksize_y, 0);
        gd.addNumericField("offset", offset, 4);
        gd.addChoice("borderType", STR_BORDERTYPE, STR_BORDERTYPE[indBorderType]);
        gd.addPreviewCheckbox(pfr);
        gd.addDialogListener(this);

        gd.showDialog();

        if(gd.wasCanceled()) {
            releaseAllMats();
            return DONE;
        }
        else {
            return IJ.setupDialog(imp, FLAGS);
        }
    }

    @Override
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent awte) {
        small_ksize_x = (int)gd.getNextNumber();
        small_ksize_y = (int)gd.getNextNumber();
        large_ksize_x = (int)gd.getNextNumber();
        large_ksize_y = (int)gd.getNextNumber();
        offset = (double)gd.getNextNumber();
        indBorderType = (int)gd.getNextChoiceIndex();

        if(Double.isNaN(offset)) {
            IJ.showStatus("ERR : NaN");
            return false;
        }

        if(small_ksize_x <= 0) {
            IJ.showStatus("'0 < small_ksize_x' is necessary.");
            return false;
        }

        if(small_ksize_y <= 0) {
            IJ.showStatus("'0 < small_ksize_y' is necessary.");
            return false;
        }
        
        if(large_ksize_x <= 0) {
            IJ.showStatus("'0 < large_ksize_x' is necessary.");
            return false;
        }

        if(large_ksize_y <= 0) {
            IJ.showStatus("'0 < large_ksize_y' is necessary.");
            return false;
        }

        // Check offset range based on bit depth
        switch(bitDepth) {
            case 8:
            case 24:
                if(offset < 0 || offset > 255) {
                    IJ.showStatus("Offset for 8bit/RGB should be 0-255 (current: " + offset + ")");
                    return false;
                }
                break;
            case 16:
                if(offset < 0 || offset > 65535) {
                    IJ.showStatus("Offset for 16bit should be 0-65535 (current: " + offset + ")");
                    return false;
                }
                break;
            case 32:
                // No restriction for 32bit
                break;
        }

        small_ksize = new Size(small_ksize_x, small_ksize_y);
        large_ksize = new Size(large_ksize_x, large_ksize_y);
        IJ.showStatus(className);
        return true;
    }

    @Override
    public void setNPasses(int nPasses) {
        // do nothing
    }

    @Override
    public int setup(String arg, ImagePlus imp) {
        if(!OCV__LoadLibrary.isLoad()) {
            OCV__LoadLibrary.logError("OCUtil_BluredImageDiff", "Library is not loaded.");
            return DONE;
        }

        if(imp == null) {
            IJ.noImage();
            return DONE;
        }
        else {
            bitDepth = imp.getBitDepth();
            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip) {
        int imw = 0;
        int imh = 0;
        
        try {
            imw = ip.getWidth();
            imh = ip.getHeight();
            int borderType = INT_BORDERTYPE[indBorderType];
            
            // Reallocate Mats if image size or bit depth changed
            if (imw != lastWidth || imh != lastHeight || bitDepth != lastBitDepth) {
                releaseAllMats();
                allocateMats(imw, imh, bitDepth);
                lastWidth = imw;
                lastHeight = imh;
                lastBitDepth = bitDepth;
            }
            
            if (bitDepth == 24) {
                // RGB processing fully implemented with OpenCV
                processRGBWithOpenCV(ip, imw, imh, borderType);
            } else {
                // Grayscale processing
                processGrayscaleWithOpenCV(ip, borderType);
            }
        } catch (Exception e) {
            OCV__LoadLibrary.logError(className, e.getMessage());
        }
        finally {
            allocateMats(imw, imh, bitDepth);
        }
    }
    
    /**
     * Allocate Mats (for reuse)
     */
    private void allocateMats(int imw, int imh, int bitDepth) {
        if (bitDepth == 24) {
            src_mat = new Mat(imh, imw, CvType.CV_8UC3);
            blur_small = new Mat(imh, imw, CvType.CV_8UC3);
            blur_large = new Mat(imh, imw, CvType.CV_8UC3);
            blur_small_32f = new Mat(imh, imw, CvType.CV_32FC3);
            blur_large_32f = new Mat(imh, imw, CvType.CV_32FC3);
            diff_mat = new Mat(imh, imw, CvType.CV_32FC3);
            result_mat = new Mat(imh, imw, CvType.CV_8UC3);
        } else {
            int cvType = getCvType(bitDepth);
            if (cvType != -1) {
                src_mat = new Mat(imh, imw, cvType);
                blur_small = new Mat(imh, imw, cvType);
                blur_large = new Mat(imh, imw, cvType);
                blur_small_32f = new Mat(imh, imw, CvType.CV_32F);
                blur_large_32f = new Mat(imh, imw, CvType.CV_32F);
                diff_mat = new Mat(imh, imw, CvType.CV_32F);
                result_mat = new Mat(imh, imw, cvType);
            }
        }
    }
    
    /**
     * Release all Mats
     */
    private void releaseAllMats() {
        if (src_mat != null) { src_mat.release(); src_mat = null; }
        if (blur_small != null) { blur_small.release(); blur_small = null; }
        if (blur_large != null) { blur_large.release(); blur_large = null; }
        if (blur_small_32f != null) { blur_small_32f.release(); blur_small_32f = null; }
        if (blur_large_32f != null) { blur_large_32f.release(); blur_large_32f = null; }
        if (diff_mat != null) { diff_mat.release(); diff_mat = null; }
        if (result_mat != null) { result_mat.release(); result_mat = null; }
    }
    
    /**
     * RGB image processing (fully OpenCV-based, no ImageJ commands)
     */
    private void processRGBWithOpenCV(ImageProcessor ip, int imw, int imh, int borderType) {
        int[] pixels = (int[])ip.getPixels();
        
        // Convert to OpenCV Mat
        OCV__LoadLibrary.intarray2mat(pixels, src_mat, imw, imh);
        
        // Blur processing
        Imgproc.blur(src_mat, blur_small, small_ksize, new Point(-1, -1), borderType);
        Imgproc.blur(src_mat, blur_large, large_ksize, new Point(-1, -1), borderType);
        
        // Convert to 32F and calculate difference
        blur_small.convertTo(blur_small_32f, CvType.CV_32FC3);
        blur_large.convertTo(blur_large_32f, CvType.CV_32FC3);
        
        Core.subtract(blur_small_32f, blur_large_32f, diff_mat);
        Core.add(diff_mat, new org.opencv.core.Scalar(offset, offset, offset), diff_mat);
        
        // Convert back to 8UC (explicit arguments)
        diff_mat.convertTo(result_mat, CvType.CV_8UC3, 1.0, 0.0);
        
        // Convert back to ImageJ
        OCV__LoadLibrary.mat2intarray(result_mat, pixels, imw, imh);
    }
    
    /**
     * Grayscale image processing (fully OpenCV-based)
     */
    private void processGrayscaleWithOpenCV(ImageProcessor ip, int borderType) {
        int cvType = getCvType(bitDepth);
        if (cvType == -1) {
            return;
        }
        
        // Transfer data to Mat
        putPixelsToMat(ip, src_mat);
        
        // Blur processing
        Imgproc.blur(src_mat, blur_small, small_ksize, new Point(-1, -1), borderType);
        Imgproc.blur(src_mat, blur_large, large_ksize, new Point(-1, -1), borderType);
        
        // Calculate difference in 32F (to maintain tonal consistency)
        blur_small.convertTo(blur_small_32f, CvType.CV_32F);
        blur_large.convertTo(blur_large_32f, CvType.CV_32F);
        
        Core.subtract(blur_small_32f, blur_large_32f, diff_mat);
        Core.add(diff_mat, new org.opencv.core.Scalar(offset), diff_mat);
        
        // Convert back to original type (explicit arguments)
        diff_mat.convertTo(result_mat, cvType, 1.0, 0.0);
        
        // Transfer back to ImageJ
        getPixelsFromMat(result_mat, ip);
    }
    
    /**
     * Get OpenCV CvType from ImageJ bit depth
     */
    private int getCvType(int bitDepth) {
        switch(bitDepth) {
            case 8:
                return CvType.CV_8UC1;
            case 16:
                return CvType.CV_16UC1;  // Use unsigned, not CV_16S
            case 32:
                return CvType.CV_32F;
            default:
                OCV__LoadLibrary.logError(className, "Unsupported bit depth (" + bitDepth + ")");
                return -1;
        }
    }
    
    /**
     * Transfer pixel data from ImageProcessor to Mat
     */
    private void putPixelsToMat(ImageProcessor ip, Mat mat) {
        if (ip.getBitDepth() == 8) {
            byte[] pixels = (byte[])ip.getPixels();
            mat.put(0, 0, pixels);
        } else if (ip.getBitDepth() == 16) {
            short[] pixels = (short[])ip.getPixels();
            mat.put(0, 0, pixels);
        } else if (ip.getBitDepth() == 32) {
            float[] pixels = (float[])ip.getPixels();
            mat.put(0, 0, pixels);
        }
    }
    
    /**
     * Transfer data from Mat to ImageProcessor
     */
    private void getPixelsFromMat(Mat mat, ImageProcessor ip) {
        if (ip.getBitDepth() == 8) {
            byte[] pixels = (byte[])ip.getPixels();
            mat.get(0, 0, pixels);
        } else if (ip.getBitDepth() == 16) {
            short[] pixels = (short[])ip.getPixels();
            mat.get(0, 0, pixels);
        } else if (ip.getBitDepth() == 32) {
            float[] pixels = (float[])ip.getPixels();
            mat.get(0, 0, pixels);
        }
    }
}