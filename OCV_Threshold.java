import ij.*;
import ij.IJ;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
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
 * threshold (OpenCV4.5.3).
 */
public class OCV_Threshold implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener
{
    // constant var.
    private static final int FLAGS = DOES_8G | DOES_32 | KEEP_PREVIEW;
    private static final int[] INT_TYPE = { Imgproc.THRESH_BINARY, Imgproc.THRESH_BINARY_INV, Imgproc.THRESH_TRUNC, Imgproc.THRESH_TOZERO, Imgproc.THRESH_TOZERO_INV, Imgproc.THRESH_OTSU, Imgproc.THRESH_OTSU + Imgproc.THRESH_BINARY_INV, Imgproc.THRESH_TRIANGLE };
    private static final String[] STR_TYPE = { "THRESH_BINARY", "THRESH_BINARY_INV", "THRESH_TRUNC", "THRESH_TOZERO", "THRESH_TOZERO_INV" , "THRESH_OTSU", "THRESH_OTSU_INV", "THRESH_TRIANGLE"};
    private static final float UBYTE_MAX = 255;
    
    // staic var.
    private static double thresh = 125;
    private static double maxVal  = 255.0;
    private static int idxType = 0;
    
    // var.
    private int bitDepth = 0;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr)
    {
        double min_val = 0;
        double max_val = 0;
        
        if(bitDepth == 8)
        {
            min_val = 0;
            max_val = UBYTE_MAX;
        }
        else
        {
            ImageStatistics stat =  imp.getStatistics();
            min_val = stat.min - 1;
            max_val = stat.max + 1;
        }
        
        GenericDialog gd = new GenericDialog(command.trim() + " ...");
        
        gd.addSlider("thresh", min_val, max_val, thresh);
        gd.addNumericField("maxval", maxVal, 4);
        gd.addChoice("adaptiveMethod", STR_TYPE, STR_TYPE[idxType]);
        gd.addPreviewCheckbox(pfr);
        gd.addDialogListener(this);

        gd.showDialog();

        if (gd.wasCanceled())
        {
            return DONE;
        }
        else
        {
            return IJ.setupDialog(imp, FLAGS);
        }
    }
    
    @Override
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent awte)
    {
        thresh = (double)gd.getNextNumber();
        maxVal = (double)gd.getNextNumber();
        idxType = (int)gd.getNextChoiceIndex();

        if(Double.isNaN(thresh) || Double.isNaN(maxVal)) { IJ.showStatus("ERR : NaN"); return false; }
        if(bitDepth == 8 && (thresh < 0 || 255 < thresh)) { IJ.showStatus("'0 <= thresh & thresh <= 255' is necessary."); return false; }
        if(bitDepth == 8 && maxVal < 0) { IJ.showStatus("'0 <= maxValue' is necessary."); return false; }
        
        IJ.showStatus("OCV_Threshold");
        return true;
    }

    @Override
    public void setNPasses(int nPasses)
    {
        // do nothing
    }

    @Override
    public int setup(String arg, ImagePlus imp)
    {
        if(!OCV__LoadLibrary.isLoad())
        {
            IJ.error("Library is not loaded.");
            return DONE;
        }

        if (imp == null)
        {
            IJ.noImage();
            return DONE;
        }
        else
        {
            bitDepth = imp.getBitDepth();
            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip)
    {
        int imw = ip.getWidth();
        int imh = ip.getHeight();

        if(ip.getBitDepth() == 8)
        {
         // srcdst
            byte[] srcdst_bytes = (byte[])ip.getPixels();        

            // mat
            Mat src_mat = new Mat(imh, imw, CvType.CV_8UC1);
            Mat dst_mat = new Mat(imh, imw, CvType.CV_8UC1);
            
            // run
            src_mat.put(0, 0, srcdst_bytes);
            Imgproc.threshold(src_mat, dst_mat, thresh, maxVal, INT_TYPE[idxType]);
            dst_mat.get(0, 0, srcdst_bytes);
        }
        else if(ip.getBitDepth() == 32)
        {
            // srcdst
            float[] srcdst_floats = (float[])ip.getPixels();

            // mat
            Mat src_mat = new Mat(imh, imw, CvType.CV_32F);            
            Mat dst_mat = new Mat(imh, imw, CvType.CV_32F);
            
            // run
            src_mat.put(0, 0, srcdst_floats);
            Imgproc.threshold(src_mat, dst_mat, thresh, maxVal, INT_TYPE[idxType]);
            dst_mat.get(0, 0, srcdst_floats);
        }
        else
        {
            IJ.error("Wrong image format");
        }
    }
}
