import ij.IJ;
import ij.ImagePlus;
import ij.measure.ResultsTable;
import ij.plugin.filter.ExtendedPlugInFilter;
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
 * getRotationMatrix2D (OpenCV4.5.3).
 */
public class OCV_InvertAffineTransform implements ExtendedPlugInFilter
{
    // constant var.
    private static final int FLAGS = NO_IMAGE_REQUIRED;

    // var.
    private ResultsTable rt  = null;
    private Boolean isPerspective = false;

    @Override
    public void setNPasses(int arg0)
    {
        // do nothing
    }

    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner prf)
    {
        // do nothing
        return FLAGS;
    }

    @Override
    public void run(ImageProcessor ip)
    {
        if(isPerspective)
        {
            Mat mat_src = new Mat(3, 3, CvType.CV_64FC1);
            Mat mat_dst = new Mat(3, 3, CvType.CV_64FC1);

            mat_src.put(0, 0, new double[] { Double.valueOf(rt.getStringValue(0, 0).replaceAll("\"|'", ""))});
            mat_src.put(0, 1, new double[] { Double.valueOf(rt.getStringValue(1, 0).replaceAll("\"|'", ""))});
            mat_src.put(0, 2, new double[] { Double.valueOf(rt.getStringValue(2, 0).replaceAll("\"|'", ""))});
            mat_src.put(1, 0, new double[] { Double.valueOf(rt.getStringValue(0, 1).replaceAll("\"|'", ""))});
            mat_src.put(1, 1, new double[] { Double.valueOf(rt.getStringValue(1, 1).replaceAll("\"|'", ""))});
            mat_src.put(1, 2, new double[] { Double.valueOf(rt.getStringValue(2, 1).replaceAll("\"|'", ""))});
            mat_src.put(2, 0, new double[] { Double.valueOf(rt.getStringValue(0, 2).replaceAll("\"|'", ""))});
            mat_src.put(2, 1, new double[] { Double.valueOf(rt.getStringValue(1, 2).replaceAll("\"|'", ""))});
            mat_src.put(2, 2, new double[] { Double.valueOf(rt.getStringValue(2, 2).replaceAll("\"|'", ""))});

            Imgproc.invertAffineTransform(mat_src, mat_dst);

            rt.reset();
            rt.incrementCounter();
            rt.addValue("Column01", String.valueOf(mat_dst.get(0, 0)[0]));
            rt.addValue("Column02", String.valueOf(mat_dst.get(0, 1)[0]));
            rt.addValue("Column03", String.valueOf(mat_dst.get(0, 2)[0]));
            rt.incrementCounter();
            rt.addValue("Column01", String.valueOf(mat_dst.get(1, 0)[0]));
            rt.addValue("Column02", String.valueOf(mat_dst.get(1, 1)[0]));
            rt.addValue("Column03", String.valueOf(mat_dst.get(1, 2)[0]));
            rt.incrementCounter();
            rt.addValue("Column01", String.valueOf(mat_dst.get(2, 0)[0]));
            rt.addValue("Column02", String.valueOf(mat_dst.get(2, 1)[0]));
            rt.addValue("Column03", String.valueOf(mat_dst.get(2, 2)[0]));
            rt.show("Results");
        }
        else
        {
            Mat mat_src = new Mat(2, 3, CvType.CV_64FC1);
            Mat mat_dst = new Mat(2, 3, CvType.CV_64FC1);

            mat_src.put(0, 0, new double[] { Double.valueOf(rt.getStringValue(0, 0).replaceAll("\"|'", ""))});
            mat_src.put(0, 1, new double[] { Double.valueOf(rt.getStringValue(1, 0).replaceAll("\"|'", ""))});
            mat_src.put(0, 2, new double[] { Double.valueOf(rt.getStringValue(2, 0).replaceAll("\"|'", ""))});
            mat_src.put(1, 0, new double[] { Double.valueOf(rt.getStringValue(0, 1).replaceAll("\"|'", ""))});
            mat_src.put(1, 1, new double[] { Double.valueOf(rt.getStringValue(1, 1).replaceAll("\"|'", ""))});
            mat_src.put(1, 2, new double[] { Double.valueOf(rt.getStringValue(2, 1).replaceAll("\"|'", ""))});

            Imgproc.invertAffineTransform(mat_src, mat_dst);

            rt.reset();
            rt.incrementCounter();
            rt.addValue("Column01", String.valueOf(mat_dst.get(0, 0)[0]));
            rt.addValue("Column02", String.valueOf(mat_dst.get(0, 1)[0]));
            rt.addValue("Column03", String.valueOf(mat_dst.get(0, 2)[0]));
            rt.incrementCounter();
            rt.addValue("Column01", String.valueOf(mat_dst.get(1, 0)[0]));
            rt.addValue("Column02", String.valueOf(mat_dst.get(1, 1)[0]));
            rt.addValue("Column03", String.valueOf(mat_dst.get(1, 2)[0]));
            rt.show("Results");

        }
    }

    @Override
    public int setup(String arg0, ImagePlus imp)
    {
        if(!OCV__LoadLibrary.isLoad())
        {
            IJ.error("Library is not loaded.");
            return DONE;
        }

        rt = OCV__LoadLibrary.GetResultsTable(false);

        if(rt == null)
        {
            IJ.error("ResultsTable is none.");
            return DONE;
        }
        
        if(rt.size() == 2)
        {
            isPerspective = false;
        }
        else if(rt.size() == 3)
        {
            isPerspective = true;
        }
        else
        {
            IJ.error("It is necessary that ResultsTable.size() is either two or three.");
            return DONE;
        }

        return FLAGS;
    }
}
