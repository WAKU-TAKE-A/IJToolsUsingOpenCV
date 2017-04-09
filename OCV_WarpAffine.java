import ij.*;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.measure.ResultsTable;
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
 * warpAffine (OpenCV3.1).
 */
public class OCV_WarpAffine implements ExtendedPlugInFilter, DialogListener
{
    // constant var.
    private static final int FLAGS = DOES_8G | DOES_RGB | DOES_16 | DOES_32 | KEEP_PREVIEW;
    private static final int[] FLAGS_INT = new int[] { Imgproc.INTER_NEAREST, Imgproc.INTER_LINEAR, Imgproc.INTER_CUBIC, Imgproc.INTER_AREA, Imgproc.INTER_LANCZOS4, Imgproc.WARP_FILL_OUTLIERS, Imgproc.WARP_INVERSE_MAP };
    private static final String[] FLAGS_STR = new String [] { "INTER_NEAREST", "INTER_LINEAR", "INTER_CUBIC", "INTER_AREA", "INTER_LANCZOS4", "WARP_FILL_OUTLIERS", "INVERSE_TRANSFORMATION" };

    // static var.
    private static int flags_ind = 1;
    private ResultsTable rt  = null;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr)
    {
        GenericDialog gd = new GenericDialog(command.trim() + "...");

        gd.addChoice("interpolation_method", FLAGS_STR, FLAGS_STR[flags_ind]);
        gd.addHelp(OCV__LoadLibrary.URL_HELP);
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
        flags_ind = (int)gd.getNextChoiceIndex();       
        IJ.showStatus("OCV_WarpAffine");
        return true;
    }
    
    @Override
    public void setNPasses(int arg0)
    {
        // do nothing
    }

    @Override
    public int setup(String arg0, ImagePlus imp)
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
        
        rt = OCV__LoadLibrary.GetResultsTable(false);
        
        if(rt == null || rt.size() != 2)
        {
            IJ.error("It is necessary that ResultsTable.size() is two.");
            return DONE;
        }
        
        return FLAGS;
    }

    @Override
    public void run(ImageProcessor ip)
    {
        int imw = ip.getWidth();
        int imh = ip.getHeight();
        Size size =  new Size((double)imw, (double)imh);        
        Mat mat = new Mat(2, 3, CvType.CV_64FC1);
        
        for(int i = 0; i < 2; i++)
        {
            mat.put(i, 0, new double[] { Double.valueOf(rt.getStringValue(0, i).replaceAll("\"|'", "")) });
            mat.put(i, 1, new double[] { Double.valueOf(rt.getStringValue(1, i).replaceAll("\"|'", "")) });
            mat.put(i, 2, new double[] { Double.valueOf(rt.getStringValue(2, i).replaceAll("\"|'", "")) });
        }
        
        if(ip.getBitDepth() == 8)
        {
            byte[] srcdst_ar = (byte[])ip.getPixels();
            Mat src_mat = new Mat(imh, imw, CvType.CV_8UC1);
            Mat dst_mat = new Mat(imh, imw, CvType.CV_8UC1);
            
            src_mat.put(0, 0, srcdst_ar);
            Imgproc.warpAffine(src_mat, dst_mat, mat, size, FLAGS_INT[flags_ind]);
            dst_mat.get(0, 0, srcdst_ar);            
        }
        else if(ip.getBitDepth() == 16)
        {
           short[] srcdst_ar = (short[])ip.getPixels();
            Mat src_mat = new Mat(imh, imw, CvType.CV_16UC1);
            Mat dst_mat = new Mat(imh, imw, CvType.CV_16UC1);
            
            src_mat.put(0, 0, srcdst_ar);
            Imgproc.warpAffine(src_mat, dst_mat, mat, size, FLAGS_INT[flags_ind]);
            dst_mat.get(0, 0, srcdst_ar);   
        }
        else if(ip.getBitDepth() == 24)
        {
            int[] srcdst_ar = (int[])ip.getPixels();
            Mat src_mat = new Mat(imh, imw, CvType.CV_8UC3);
            Mat dst_mat = new Mat(imh, imw, CvType.CV_8UC3);
            
            OCV__LoadLibrary.intarray2mat(srcdst_ar, src_mat, imw, imh);
            Imgproc.warpAffine(src_mat, dst_mat, mat, size, FLAGS_INT[flags_ind]);
            OCV__LoadLibrary.mat2intarray(dst_mat, srcdst_ar, imw, imh);
        }
        else if(ip.getBitDepth() == 32)
        {
          float[] srcdst_ar = (float[])ip.getPixels();
            Mat src_mat = new Mat(imh, imw, CvType.CV_32FC1);
            Mat dst_mat = new Mat(imh, imw, CvType.CV_32FC1);
            
            src_mat.put(0, 0, srcdst_ar);
            Imgproc.warpAffine(src_mat, dst_mat, mat, size, FLAGS_INT[flags_ind]);
            dst_mat.get(0, 0, srcdst_ar);   
        }
        else
        {
            IJ.error("Wrong image format");
        }
    }
}
