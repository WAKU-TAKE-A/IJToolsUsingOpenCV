import ij.*;
import ij.IJ;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
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
 * equalizeHist (OpenCV3.1)
 * @version 0.9.3.0
 */
public class OCV_EqualizeHist implements ij.plugin.filter.ExtendedPlugInFilter
{
    // constant var.
    private static final int FLAGS = DOES_8G;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr)
    {
        // do nothing
        return IJ.setupDialog(imp, FLAGS);
    }

    @Override
    public void setNPasses(int nPasses)
    {
        // do nothing
    }

    @Override
    public int setup(String arg, ImagePlus imp)
    {
        if(!OCV__LoadLibrary.isLoad)
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
            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip)
    {
        // srcdst
        int imw = ip.getWidth();
        int imh = ip.getHeight();
        byte[] srcdst_bytes = (byte[])ip.getPixels();
        
        // mat
        Mat src_mat = new Mat(imh, imw, CvType.CV_8UC1);
        Mat dst_mat = new Mat(imh, imw, CvType.CV_8UC1);      

        // run
        src_mat.put(0, 0, srcdst_bytes);
        Imgproc.equalizeHist(src_mat, dst_mat);
        dst_mat.get(0, 0, srcdst_bytes);        
    }
}
