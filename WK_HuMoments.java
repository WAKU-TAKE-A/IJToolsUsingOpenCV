import ij.IJ;
import ij.ImagePlus;
import ij.measure.ResultsTable;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import java.awt.Rectangle;

/*
 * The MIT License
 *
 * Copyright 2016 WAKU_TAKE_A.
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
 * calculate HuMoments within the ROI.
 * 
 * I referred to the followings.
 * https://en.wikipedia.org/wiki/Image_moment#Rotation_invariant_moments
 * \opencv-3.1.0\sources\modules\imgproc\src\moments.cpp
 * @author WAKU_TAKE_A
 * @version 0.9.0.0
 */
public class WK_HuMoments implements ExtendedPlugInFilter 
{   
    // const var.
    private final int FLAGS = DOES_8G | CONVERT_TO_FLOAT;
    
    // static var.
    private static double[] res_ini = new double[7];
    
    // var.
    private Rectangle rect = null;

    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner prf)
    {
        // do nothing
        return FLAGS;
    }    
    
    @Override
    public void setNPasses(int arg0)
    {
        // do nothing
    }

    @Override
    public int setup(String arg0, ImagePlus imp)
    {
        if (imp == null)
        {
            IJ.noImage();
            return DONE;
        }
        else
        {            
            if(imp.getRoi() != null)
            {
                rect = imp.getRoi().getBounds();
            }
            else
            {
                rect = new Rectangle(0, 0, imp.getWidth(), imp.getHeight());
            }
            
            return FLAGS;
        }
    }
    
    @Override
    public void run(ImageProcessor ip)
    {
        float[] floatArray = (float[])ip.getPixels();
        int img_width = ip.getWidth();
        int roi_w = rect.width;
        int roi_h = rect.height;
        
        float[] fltArr_roi = new float[roi_w * roi_h];
        int ind = 0;
        
        for(int y = rect.y ; y < rect.y + roi_h; y++)
        {
            for(int x = rect.x; x < rect.x + roi_w; x++)
            {
                fltArr_roi[ind] = floatArray[x + y * img_width];
                ind++;
            }
        }
        
        moments mom = new moments();
        double[] hu = new double[9];

        calc_moments(fltArr_roi, mom, roi_w, roi_h);
        calc_humoments(mom, hu);
        
        // hu_dbl[7] is calculated in showData().
        hu[8] = 0.5 * Math.atan(2.0 * mom.nu11/ (mom.nu20 - mom.nu02)) * 180 / Math.PI;
        
        showData(hu);
    }

    // private
    private class moments
    {
        // Raw moments
        public double M00;
        public double M10;
        public double M01;
        public double M20;
        public double M11;
        public double M02;
        public double M30;
        public double M21;
        public double M12;
        public double M03;

        // Central moments
        public double mu00;
        public double mu20;
        public double mu11;
        public double mu02;
        public double mu30;
        public double mu21;
        public double mu12;
        public double mu03;

        // Scale invariants
        public double nu20;
        public double nu11;
        public double nu02;
        public double nu30;
        public double nu21;
        public double nu12;
        public double nu03;
    }    
   
    private void calc_moments(float[] src, moments dst, int w, int h)
    {
        double M00 = 0,  M10 = 0, M01 = 0, M20 = 0, M11 = 0, M02 = 0, M30 = 0, M21 = 0, M12 = 0, M03 = 0;
        double mu00 = 0, mu20 = 0, mu11 = 0, mu02 = 0, mu30 = 0, mu21 = 0, mu12 = 0, mu03 = 0;
        double val = 0;
        double xx = 0;
        double yy = 0;
        double xxx = 0;
        double yyy = 0;
        
        for(int y = 0; y < h; y++)
        {
            for(int x = 0; x < w; x++)
            {
                val = (double)src[x + y * w];
                xx = x * x;
                yy = y * y;
                xxx = xx * x;
                yyy = yy * y;
                M00 += val * 1 * 1;
                M10 += val * x * 1;
                M01 += val * 1 * y;
                M20 += val * xx * 1;
                M11 += val * x * y;
                M02 += val * 1 * yy;
                M30 += val * xxx * 1;
                M21 += val * xx * y;
                M12 += val * x * yy;
                M03 += val * 1 * yyy;
            }
        }
        
        dst.M00 = M00;
        dst.M10 = M10;
        dst.M01 = M01;
        dst.M20 = M20;
        dst.M11 = M11;
        dst.M02 = M02;
        dst.M30 = M30;
        dst.M21 = M21;
        dst.M12 = M12;
        dst.M03 = M03;
        
        double av_x = M10 / M00;
        double av_y = M01 / M00;        
       
        mu00 = M00;
        mu20 = M20 - av_x * M10;
        mu11 = M11 - av_x * M01; // M11 - av_y * M10
        mu02 = M02 - av_y * M01;
        mu30 = M30 - 3 * av_x * M20 + 2 * av_x * av_x * M10;
        mu21 = M21 - 2 * av_x * M11 - av_y * M20 + 2 * av_x * av_x * M01;
        mu12 = M12 - 2 * av_y * M11 - av_x * M02 + 2 * av_y * av_y * M10;
        mu03 = M03 - 3 * av_y * M02 + 2 * av_y * av_y * M01;        
        
        dst.mu00 = mu00;
        dst.mu20 = mu20;
        dst.mu11 = mu11;
        dst.mu02 = mu02;
        dst.mu30 = mu30;
        dst.mu21 = mu21;
        dst.mu12 = mu12;
        dst.mu03 = mu03;       
        
        dst.nu20 = mu20 / Math.pow(mu00, ((2 + 0) / 2 + 1));
        dst.nu11 = mu11 / Math.pow(mu00, ((1 + 1) / 2 + 1));
        dst.nu02 = mu02 / Math.pow(mu00, ((0 + 2) / 2 + 1));
        dst.nu30 = mu30 / Math.pow(mu00, ((3 + 0) / 2 + 1));
        dst.nu21 = mu21 / Math.pow(mu00, ((2 + 1) / 2 + 1));
        dst.nu12 = mu12 / Math.pow(mu00, ((1 + 2) / 2 + 1));
        dst.nu03 = mu03 / Math.pow(mu00, ((0 + 3) / 2 + 1));
    }
    
    private void calc_humoments(moments mom, double[] hu)
    {
        if(hu.length < 7)
        {
            return;
        }
        
        double t0 = mom.nu30 + mom.nu12;
        double t1 = mom.nu21 + mom.nu03;

        double q0 = t0 * t0, q1 = t1 * t1;

        double n4 = 4 * mom.nu11;
        double s = mom.nu20 + mom.nu02;
        double d = mom.nu20 - mom.nu02;

        hu[0] = s;
        hu[1] = d * d + n4 * mom.nu11;
        hu[3] = q0 + q1;
        hu[5] = d * (q0 - q1) + n4 * t0 * t1;

        t0 *= q0 - 3 * q1;
        t1 *= 3 * q0 - q1;

        q0 = mom.nu30 - 3 * mom.nu12;
        q1 = 3 * mom.nu21 - mom.nu03;

        hu[2] = q0 * q0 + q1 * q1;
        hu[4] = q0 * t0 + q1 * t1;
        hu[6] = q1 * t0 - q0 * t1;
    }
    
    private void showData(double[] results)
    {
        ResultsTable rt = ResultsTable.getResultsTable();
        
        if (rt == null || rt.getCounter() == 0)
        {
            rt = new ResultsTable();
            res_ini[0] = results[0];
            res_ini[1] = results[1];
            res_ini[2] = results[2];
            res_ini[3] = results[3];
            res_ini[4] = results[4];
            res_ini[5] = results[5];
            res_ini[6] = results[6];
            results[7] = 0;
        }
        else
        {
            for(int i = 0; i < 7; i++)
            {
                double res = Math.copySign(Math.log(Math.abs(results[i])), results[i]);
                double ini = Math.copySign(Math.log(Math.abs(res_ini[i])), res_ini[i]);               
                
                results[7] += Math.abs(1 / res - 1 / ini);                
            }            
        }
        
        rt.incrementCounter();
        rt.addValue("Hu0", String.format("%8e", results[0]));
        rt.addValue("Hu1", String.format("%8e", results[1]));
        rt.addValue("Hu2", String.format("%8e", results[2]));
        rt.addValue("Hu3", String.format("%8e", results[3]));
        rt.addValue("Hu4", String.format("%8e", results[4]));
        rt.addValue("Hu5", String.format("%8e", results[5]));
        rt.addValue("Hu6", String.format("%8e", results[6]));
        rt.addValue("Match", String.format("%8e", results[7]));
        rt.addValue("Rotation", String.format("%8f", results[8]));
        rt.addValue("Width", rect.width);
        rt.addValue("Height", rect.height);
        
        rt.show("Results");
    }
}
