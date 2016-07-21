
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.Macro_Runner;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.process.FloatProcessor;
import java.awt.Frame;
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
 * matchTemplate (OpenCV3.1)
 * @version 0.9.2.0
 */
public class OCV_MatchTemplate implements ij.plugin.filter.ExtendedPlugInFilter
{
    // constant var.
    private final int FLAGS = DOES_8G;
    private String[] TYPE_STR = new String[] { "TM_SQDIFF", "TM_SQDIFF_NORMED", "TM_CCORR", "TM_CCORR_NORMED", "TM_CCOEFF", "TM_CCOEFF_NORMED"};
    private int[] TYPE_VAL = new int[] { Imgproc.TM_SQDIFF, Imgproc.TM_SQDIFF_NORMED, Imgproc.TM_CCORR, Imgproc.TM_CCORR_NORMED, Imgproc.TM_CCOEFF, Imgproc.TM_CCOEFF_NORMED };

    // static var.
    private static int ind_src;
    private static int ind_tmp;
    private static int ind_type = 3;
    private static float thr_res = (float)0.5;
    private static boolean enResult = true;
    private static boolean enSetRoi = true;

    // var.
    private String title_src = null;
    private ImagePlus imp_src = null;
    private ImagePlus imp_tmp = null;
    private int[] lst_wid;
    private String[] titles;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr)
    {
        GenericDialog gd = new GenericDialog(command.trim() + "...");

        gd.addChoice("src", titles, titles[0]);
        gd.addChoice("template", titles, titles[1]);
        gd.addChoice("method", TYPE_STR, TYPE_STR[ind_type]);
        gd.addNumericField("threshold_of_results", thr_res, 3);
        gd.addCheckbox("enable_results_table", enResult);
        gd.addCheckbox("__enable_set_roi", enSetRoi);

        gd.showDialog();

        if (gd.wasCanceled())
        {
            return DONE;
        }
        else
        {
            ind_src = (int)gd.getNextChoiceIndex();
            ind_tmp = (int)gd.getNextChoiceIndex();
            ind_type = (int)gd.getNextChoiceIndex();
            thr_res = (float)gd.getNextNumber();
            enResult = (boolean)gd.getNextBoolean();
            enSetRoi = (boolean)gd.getNextBoolean();

            if(ind_src == ind_tmp)
            {
                IJ.error("ERR : cannot be the same as.");
                return DONE;
            }

            imp_src = WindowManager.getImage(lst_wid[ind_src]);
            imp_tmp = WindowManager.getImage(lst_wid[ind_tmp]);
            title_src = imp_src.getShortTitle();

            if(imp_src.getBitDepth() != 8 || imp_tmp.getBitDepth() != 8)
            {
                IJ.error("ERR : only 8bit.");
                return DONE;
            }

            return FLAGS;
        }
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
            lst_wid = WindowManager.getIDList();

            if (lst_wid==null || lst_wid.length < 2)
            {
                IJ.error("ERR : require at least two open images.");
                return DONE;
            }

            titles = new String[lst_wid.length];

            for (int i=0; i < lst_wid.length; i++)
            {
                ImagePlus imp2 = WindowManager.getImage(lst_wid[i]);
                titles[i] = imp2 != null ? imp2.getTitle() : "";
            }

            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip)
    {
        // src
        byte[] src_arr = (byte[])imp_src.getChannelProcessor().getPixels();
        int imw_src = imp_src.getWidth();
        int imh_src = imp_src.getHeight();
        Mat mat_src = new Mat(imh_src, imw_src, CvType.CV_8UC1);
        mat_src.put(0, 0, src_arr);

        // tmp
        byte[] tmp_arr = (byte[])imp_tmp.getChannelProcessor().getPixels();
        int imw_tmp = imp_tmp.getWidth();
        int imh_tmp = imp_tmp.getHeight();
        Mat mat_tmp = new Mat(imh_tmp, imw_tmp, CvType.CV_8UC1);
        mat_tmp.put(0, 0, tmp_arr);

        // dst
        String title_dst = WindowManager.getUniqueName(title_src + "_MatchTemplate");
        int imw_dst = imw_src - imw_tmp + 1;
        int imh_dst = imh_src - imh_tmp + 1;
        ImagePlus imp_dst = new ImagePlus (title_dst, new FloatProcessor(imw_dst, imh_dst));
        float[] dst_arr = (float[]) imp_dst.getChannelProcessor().getPixels();
        Mat mat_dst = new Mat();

        // run
        Imgproc.matchTemplate(mat_src, mat_tmp, mat_dst, TYPE_VAL[ind_type]);
        mat_dst.get(0, 0, dst_arr);
        imp_dst.show();

        // show data
        if(enResult)
        {
            showData(dst_arr, imw_dst, imh_dst, imw_tmp, imh_tmp);
        }
    }

    private void showData(float[] dst_arr, int imw_dst, int imh_dst, int imw_tmp, int imh_tmp)
    {
        // table
        int numpix = imw_dst * imh_dst;
        int bx;
        int by;
        int w;
        int h;
        float match;
        int num = 0;
        int idx_last = 0;

        ResultsTable rt = ResultsTable.getResultsTable();
        rt.reset();

        if(rt == null || rt.getCounter() == 0)
        {
            rt = new ResultsTable();
        }

        for(int y = 0; y < imh_dst; y++)
        {
            for(int x = 0; x < imw_dst; x++)
            {
                if(thr_res <= dst_arr[x + y * imw_dst])
                {
                    bx = x;
                    by = y;
                    w = imw_tmp;
                    h = imh_tmp;
                    match = dst_arr[x + y * imw_dst];
                    num++;

                    if(enSetRoi)
                    {
                        Roi roi = new Roi(bx, by, w, h);
                        imp_src.setRoi(roi);
                    }

                    rt.incrementCounter();
                    rt.addValue("BX", bx);
                    rt.addValue("BY", by);
                    rt.addValue("Width", w);
                    rt.addValue("Height", h);
                    rt.addValue("Match", match);
                    rt.show("Results");
                }
            }
        }

        // ROI Manager
        Frame frame = WindowManager.getFrame("ROI Manager");
        RoiManager roiManager;
        Roi roi;
        Macro_Runner mr = new Macro_Runner();

        if(enSetRoi)
        {
            if (frame==null)
            {
                IJ.run("ROI Manager...");
            }

            frame = WindowManager.getFrame("ROI Manager");
            roiManager = (RoiManager)frame;

            roiManager.reset();
            roiManager.runCommand("Show None");
            mr.runMacro("setBatchMode(true);", "");

            int col_x = rt.getColumnIndex("BX");
            int col_y = rt.getColumnIndex("BY");
            int col_w = rt.getColumnIndex("Width");
            int col_h = rt.getColumnIndex("Height");
            int col_match = rt.getColumnIndex("Match");

            for(int i = 0; i < num; i++)
            {
                bx = (int)(rt.getValueAsDouble(col_x, i));
                by = (int)(rt.getValueAsDouble(col_y, i));
                w = (int)(rt.getValueAsDouble(col_w, i));
                h = (int)(rt.getValueAsDouble(col_h, i));
                match = (float)(rt.getValueAsDouble(col_match, i));

                roi = new Roi(bx, by, w, h);
                roiManager.addRoi(roi);
                idx_last = roiManager.getCount() - 1;
                roiManager.select(idx_last);
                roiManager.runCommand("Rename", String.valueOf(i + 1) + "_" + "Match" + "_" + String.valueOf(match));
            }

            mr.runMacro("setBatchMode(false);", "");
            roiManager.runCommand("Show All");
        }
    }
}
