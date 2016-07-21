import ij.IJ;
import ij.ImagePlus;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import org.opencv.core.Core;
import org.opencv.core.Mat;

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
 * load library
 * @version 0.9.2.0
 */
public class OCV__LoadLibrary implements ExtendedPlugInFilter
{
    public static boolean isLoad = false;

    /*
     * @see ij.plugin.filter.ExtendedPlugInFilter#setNPasses(int)
     */
    @Override
    public void setNPasses(int arg0)
    {
        // do nothing
    }

    /*
     * @see ij.plugin.filter.ExtendedPlugInFilter#showDialog(ij.ImagePlus, java.lang.String, ij.plugin.filter.PlugInFilterRunner)
     */
    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner prf)
    {
        return IJ.setupDialog(imp, NO_IMAGE_REQUIRED);
    }

    /*
     * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
     */
    @Override
    public void run(ImageProcessor arg0)
    {
        if(isLoad)
        {
            IJ.error("Load already !");
            return;
        }

        try
        {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            IJ.showMessage("Load " + Core.NATIVE_LIBRARY_NAME + ".dll");

            isLoad = true;
        }
        catch(Exception ex)
        {
            IJ.error(ex.getMessage());
        }
    }

    /*
     * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
     */
    @Override
    public int setup(String arg0, ImagePlus imp)
    {
        return NO_IMAGE_REQUIRED;
    }

    /**
     * a CV_8UC3 data of OpenCV -> a color data of ImageJ.
     * @param src_cv_8uc3 a CV_8UC3 data of OpenCV
     * @param dst_ar a color data of ImageJ (int[])
     * @param imw width of image
     * @param imh height of image
     */
    public static void mat2intarray(Mat src_cv_8uc3, int[] dst_ar, int imw, int imh)
    {
        if((src_cv_8uc3.width() != imw) || (src_cv_8uc3.height() != imh) || dst_ar.length != imw * imh)
        {
            IJ.error("Wrong image size");
        }

        for(int y = 0; y < imh; y++)
        {
            for(int x = 0; x < imw; x++)
            {
                byte[] dst_cv_8uc3_ele = new byte[3];
                src_cv_8uc3.get(y, x, dst_cv_8uc3_ele);
                int b = (int)dst_cv_8uc3_ele[0];
                int g = (int)dst_cv_8uc3_ele[1] << 8;
                int r = (int)dst_cv_8uc3_ele[2] << 16;
                int a = 0xff000000;
                dst_ar[x + imw * y] = b + g + r + a;
            }
        }
    }

    /**
     * a color data of ImageJ -> a CV_8UC3 data of OpenCV
     * @param src_ar a color data of ImageJ (int[])
     * @param dst_cv_8uc3 CV_8UC3 data of OpenCV
     * @param imw width of image
     * @param imh height of image
     */
    public static void intarray2mat(int[] src_ar, Mat dst_cv_8uc3, int imw, int imh)
    {
        if((dst_cv_8uc3.width() != imw) || (dst_cv_8uc3.height() != imh) || src_ar.length != imw * imh)
        {
            IJ.error("Wrong image size");
        }

        for(int y = 0; y < imh; y++)
        {
            for(int x = 0; x < imw; x++)
            {
                int ind = x + imw * y;
                byte b = (byte)(src_ar[ind] & 0x0000ff);
                byte g = (byte)((src_ar[ind] >> 8) & 0x0000ff);
                byte r = (byte)((src_ar[ind] >> 16) & 0x0000ff);
                dst_cv_8uc3.put(y, x, new byte[] { b, g, r });
            }
        }
    }
}
