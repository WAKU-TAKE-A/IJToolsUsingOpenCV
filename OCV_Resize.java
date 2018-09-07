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
 * resize (OpenCV3.4.2).
 */
public class OCV_Resize implements ExtendedPlugInFilter, DialogListener
{
    // constant var.
    private final int FLAGS = DOES_8G | DOES_RGB | DOES_16 | DOES_32;
  
    /*
    interpolation algorithm

    * INTER_NEAREST   :	nearest neighbor interpolation
    * INTER_LINEAR    :	bilinear interpolation
    * INTER_CUBIC     :	bicubic interpolation
    * INTER_AREA      :	resampling using pixel area relation
    * INTER_LANCZOS4  :	Lanczos interpolation over 8x8 neighborhood
    * INTER_LINEAR_EXACT:	Bit exact bilinear interpolation
    * INTER_MAX       :	mask for interpolation codes(Error occurred)
    * WARP_FILL_OUTLIERS:	flag, fills all of the destination image pixels(Error occurred)
    */
    private static final int[] INT_INTERPOLATION = { Imgproc.INTER_NEAREST, Imgproc.INTER_LINEAR, Imgproc.INTER_CUBIC, Imgproc.INTER_AREA, Imgproc.INTER_LANCZOS4, Imgproc.INTER_LINEAR_EXACT/*,  Imgproc.INTER_MAX,  Imgproc.WARP_FILL_OUTLIERS*/ };
    private static final String[] STR_INTERPOLATION = { "INTER_NEAREST", "INTER_LINEAR", "INTER_CUBIC", "INTER_AREA", "INTER_LANCZOS4", "INTER_LINEAR_EXACT"/*, "INTER_MAX", "WARP_FILL_OUTLIERS"*/ };
    
    // static var.
    private static double dsize_w = 0;
    private static double dsize_h = 0;
    private static double scale_w = 0;
    private static double scale_h = 0;
    private static int indInterpolation = 0;
    
    // var
    private String titleSrc = "";
    private ImagePlus impSrc = null;
    
    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr)
    {      
        if(scale_w == 0 || scale_h == 0)
        {
            dsize_w = imp.getWidth();
            dsize_h = imp.getHeight();
        }
        
        GenericDialog gd = new GenericDialog(command.trim() + "...");

        gd.addNumericField("dsize_w", dsize_w, 0);
        gd.addNumericField("dsize_h", dsize_h, 0);
        gd.addNumericField("scale_factor_x", scale_w, 4);
        gd.addNumericField("scale_factor_y", scale_h, 4);
        gd.addChoice("interpolation", STR_INTERPOLATION, STR_INTERPOLATION[indInterpolation]);
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
        dsize_w = (double)gd.getNextNumber();
        dsize_h = (double)gd.getNextNumber();
        scale_w = (double)gd.getNextNumber();
        scale_h = (double)gd.getNextNumber();
        indInterpolation = (int)gd.getNextChoiceIndex();

        if(dsize_w < 0) { IJ.showStatus("'0 <= dsize_w' is necessary."); return false; }
        if(dsize_h < 0) { IJ.showStatus("'0 <= dsize_h' is necessary."); return false; }
        if(scale_w < 0) { IJ.showStatus("'0 <= scale_w' is necessary."); return false; }
        if(scale_h < 0) { IJ.showStatus("'0 <= scale_h' is necessary."); return false; }
        if(Double.isNaN(dsize_w) || Double.isNaN(dsize_h) || Double.isNaN(scale_w) || Double.isNaN(scale_h)) {IJ.showStatus("ERR : NaN"); return false;}

        if(0 < scale_w && 0 < scale_h)
        {
            dsize_w = ((double)impSrc.getWidth()) * scale_w;
            dsize_h = ((double)impSrc.getHeight()) * scale_h;
        }

        if(dsize_w == 0 || dsize_h == 0)
        {
            IJ.showStatus("'The output height and width values should not be 0."); return true;
        }
        
        IJ.showStatus("OCV_Resize");
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
        else
        {
            titleSrc = imp.getTitle();
            impSrc = imp;
            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip)
    {        
        Size dsize = new Size(dsize_w, dsize_h);
        
        if(ip.getBitDepth() == 8)
        {
            // src
            int imw = ip.getWidth();
            int imh = ip.getHeight();
            byte[] src_byte = (byte[])ip.getPixels();
            
            // dst
            String titleDst = WindowManager.getUniqueName(titleSrc+ "_Resize");
            ImagePlus impDst = new ImagePlus (titleDst, new ByteProcessor((int)dsize.width, (int)dsize.height));
            byte[] dst_byte = (byte[]) impDst.getChannelProcessor().getPixels();
            
            // mat
            Mat src_mat = new Mat(imh, imw, CvType.CV_8UC1);            
            Mat dst_mat = new Mat((int)dsize.width, (int)dsize.height, CvType.CV_8UC1);
            
            // flag
            int flags = INT_INTERPOLATION[indInterpolation];
            
            // run
            src_mat.put(0, 0, src_byte);
            Imgproc.resize(src_mat, dst_mat, dsize, scale_w, scale_h, flags);
            dst_mat.get(0, 0, dst_byte);
            
            // show
            impDst.show();
        }
        else if(ip.getBitDepth() == 16)
        {
            // src
            int imw = ip.getWidth();
            int imh = ip.getHeight();
            short[] src_short = (short[])ip.getPixels();
            
            // dst
            String titleDst = WindowManager.getUniqueName(titleSrc+ "_Resize");
            ImagePlus impDst = new ImagePlus (titleDst, new ShortProcessor((int)dsize.width, (int)dsize.height));
            short[] dst_short = (short[]) impDst.getChannelProcessor().getPixels();            
            
            // mat
            Mat src_mat = new Mat(imh, imw, CvType.CV_16S);            
            Mat dst_mat = new Mat((int)dsize.width, (int)dsize.height, CvType.CV_16S);
            
            // flag
             int flags = INT_INTERPOLATION[indInterpolation];
            
            // run
            src_mat.put(0, 0, src_short);
            Imgproc.resize(src_mat, dst_mat, dsize, scale_w, scale_h, flags);
            dst_mat.get(0, 0, dst_short);
            
            // show
            impDst.show();
        }
        else if(ip.getBitDepth() == 24)
        {
            // src
            int imw = ip.getWidth();
            int imh = ip.getHeight();
            int[] src_int = (int[])ip.getPixels();
            
            // dst
            String titleDst = WindowManager.getUniqueName(titleSrc+ "_Resize");
            ImagePlus  impDst = IJ.createImage(titleDst, (int)dsize.width, (int)dsize.height, 1, 24);
            int[] dst_int = (int[])impDst.getChannelProcessor().getPixels();
            
            // mat
            Mat src_mat = new Mat(imh, imw, CvType.CV_8UC3);            
            Mat dst_mat = new Mat((int)dsize.width, (int)dsize.height, CvType.CV_8UC3);
         
             // flag
             int flags = INT_INTERPOLATION[indInterpolation];
 
            // run
            OCV__LoadLibrary.intarray2mat(src_int, src_mat, imw, imh);
            Imgproc.resize(src_mat, dst_mat, dsize, scale_w, scale_h, flags);
            OCV__LoadLibrary.mat2intarray(dst_mat, dst_int, (int)dsize.width, (int)dsize.height);
            
            // show
            impDst.show();
        }
        else if(ip.getBitDepth() == 32)
        {
            // src
            int imw = ip.getWidth();
            int imh = ip.getHeight();
            float[] src_float = (float[])ip.getPixels();
            
             // dst
            String titleDst = WindowManager.getUniqueName(titleSrc+ "_Resize");
            ImagePlus impDst = new ImagePlus (titleDst, new FloatProcessor((int)dsize.width, (int)dsize.height));
            float[] dst_float = (float[]) impDst.getChannelProcessor().getPixels();  
            
             // flag
             int flags = INT_INTERPOLATION[indInterpolation];
            
            // mat
            Mat src_mat = new Mat(imh, imw, CvType.CV_32F);            
            Mat dst_mat = new Mat((int)dsize.width, (int)dsize.height, CvType.CV_32F);
            
            // run
            src_mat.put(0, 0, src_float);
            Imgproc.resize(src_mat, dst_mat, dsize, scale_w, scale_h, flags);
            dst_mat.get(0, 0, dst_float);
            
            // show
            impDst.show();
        }
        else
        {
            IJ.error("Wrong image format");
        }
    }
}
