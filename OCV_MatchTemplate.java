import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.Macro_Runner;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.process.FloatProcessor;
import java.awt.AWTEvent;
import java.util.ArrayList;
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
 * matchTemplate (OpenCV3.1).
 */
public class OCV_MatchTemplate implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener
{
    // constant var.
    private final int FLAGS = DOES_8G;
    private final String[] TYPE_STR = new String[] { "1 - TM_SQDIFF_NORMED", "TM_CCORR_NORMED", "TM_CCOEFF_NORMED"};
    private final int[] TYPE_VAL = new int[] { Imgproc.TM_SQDIFF_NORMED, Imgproc.TM_CCORR_NORMED, Imgproc.TM_CCOEFF_NORMED };

    // static var.
    private static int ind_src = 0;
    private static int ind_tmp = 1;
    private static int ind_type = 1;
    private static float thr_res = (float)0.5;
    private static boolean enResult = true;
    private static boolean enSearchMax = false;

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

        gd.addChoice("src", titles, titles[ind_src]);
        gd.addChoice("template", titles, titles[ind_tmp]);
        gd.addChoice("method", TYPE_STR, TYPE_STR[ind_type]);
        gd.addNumericField("threshold_of_results", thr_res, 4);
        gd.addCheckbox("enable_results_table", enResult);
        gd.addCheckbox("enable_search_max_point", enSearchMax);        
        gd.addDialogListener(this);

        gd.showDialog();

        if (gd.wasCanceled())
        {
            return DONE;
        }
        else
        {
            return FLAGS;
        }
    }

    @Override
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent awte)
    {
        ind_src = (int)gd.getNextChoiceIndex();
        ind_tmp = (int)gd.getNextChoiceIndex();
        ind_type = (int)gd.getNextChoiceIndex();
        thr_res = (float)gd.getNextNumber();
        enResult = (boolean)gd.getNextBoolean();   
        enSearchMax = (boolean)gd.getNextBoolean(); 
       
        if(Float.isNaN(thr_res)) { IJ.showStatus("ERR : NaN"); return false; }
        if(ind_src == ind_tmp) { IJ.showStatus("ERR : The same image can not be selected."); return false; }

        imp_src = WindowManager.getImage(lst_wid[ind_src]);
        imp_tmp = WindowManager.getImage(lst_wid[ind_tmp]);
        title_src = imp_src.getShortTitle();

        if(imp_src.getBitDepth() != 8 || imp_tmp.getBitDepth() != 8) { IJ.showStatus("The both images should be 8bit gray"); return false; }
        if(imp_src.getWidth() < imp_tmp.getWidth() || imp_src.getHeight() < imp_tmp.getHeight()) { IJ.showStatus("The size of src should be larger than the size of template."); return false; }
                
        if(enSearchMax)
        {
            enResult = true;
        }
        
        IJ.showStatus("OCV_MatchTemplate");
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
                IJ.error("At least more than 2 images are needed.");
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
        byte[] arr_src = (byte[])imp_src.getChannelProcessor().getPixels();
        int imw_src = imp_src.getWidth();
        int imh_src = imp_src.getHeight();
        Mat mat_src = new Mat(imh_src, imw_src, CvType.CV_8UC1);
        mat_src.put(0, 0, arr_src);

        // tmp
        byte[] arr_tmp = (byte[])imp_tmp.getChannelProcessor().getPixels();
        int imw_tmp = imp_tmp.getWidth();
        int imh_tmp = imp_tmp.getHeight();
        Mat mat_tmp = new Mat(imh_tmp, imw_tmp, CvType.CV_8UC1);
        mat_tmp.put(0, 0, arr_tmp);

        // dst
        String title_dst = WindowManager.getUniqueName(title_src + "_MatchTemplate");
        int imw_dst = imw_src - imw_tmp + 1;
        int imh_dst = imh_src - imh_tmp + 1;
        ImagePlus imp_dst = new ImagePlus (title_dst, new FloatProcessor(imw_dst, imh_dst));
        float[] arr_dst = (float[]) imp_dst.getChannelProcessor().getPixels();
        Mat mat_dst = new Mat();

        // run
        Imgproc.matchTemplate(mat_src, mat_tmp, mat_dst, TYPE_VAL[ind_type]);
        mat_dst.get(0, 0, arr_dst);
        imp_dst.show();
        
        if(TYPE_VAL[ind_type] == Imgproc.TM_SQDIFF_NORMED)
        {
            substracted_from_one(arr_dst);
        }
        
        IJ.run(imp_dst, "Enhance Contrast", "saturated=0.35");

        // show data
        if(enResult)
        {
            if(enSearchMax)
            {
                showData_enSearchMaxPoint(imp_dst, thr_res, imw_tmp, imh_tmp);
            }
            else
            {
                showData(arr_dst, imw_dst, imh_dst, imw_tmp, imh_tmp);
            }
        }
    }
    
    private void showData(float[] arr_dst, int imw_dst, int imh_dst, int imw_tmp, int imh_tmp)
    {
        // prepare the ResultsTable
        ResultsTable rt = OCV__LoadLibrary.GetResultsTable(true);
        
        // prepare the ROI Manager
        RoiManager roiMan = OCV__LoadLibrary.GetRoiManager(true, true);
        
        // show
        Macro_Runner mr = new Macro_Runner();
        mr.runMacro("setBatchMode(true);", "");
        ArrayList<float[]> res = new ArrayList<float[]>();
        
        for(int y = 0; y < imh_dst; y++)
        {
            for(int x = 0; x < imw_dst; x++)
            {
                if(thr_res <= arr_dst[x + y * imw_dst])
                {
                    res.add(new float[] { (float)x, (float)y, arr_dst[x + y * imw_dst]});
                }
            }
        }
        
        int num_match = res.size();
        
        for(int i = 0; i < num_match; i++)
        {
            int bx = (int)res.get(i)[0];
            int by = (int)res.get(i)[1];
            float match = res.get(i)[2];
            
            Roi roi = new Roi(bx, by, imw_tmp, imh_tmp);
            imp_src.setRoi(roi);
            
            roiMan.addRoi(roi);
            int idx_last = roiMan.getCount() - 1;
            roiMan.select(idx_last);
            roiMan.runCommand("Rename", String.valueOf(i + 1) + "_" + "Match" + "_" + String.valueOf(match));
            
            rt.incrementCounter();
            rt.addValue("BX", bx);
            rt.addValue("BY", by);
            rt.addValue("Width", imw_tmp);
            rt.addValue("Height", imh_tmp);
            rt.addValue("Match", match);
            rt.show("Results");            
        }
        
        mr.runMacro("setBatchMode(false);", "");
        roiMan.runCommand("Show All");
    }
    
    private void showData_enSearchMaxPoint(ImagePlus imp_dst, float thr, int imw_tmp, int imh_tmp)
    {
        ImagePlus imp_bin = imp_dst.duplicate();
        imp_bin.setTitle("__bin");
        float[] arr_bin = (float[]) imp_bin.getChannelProcessor().getPixels();        
        binary_float(arr_bin, thr);

        IJ.run(imp_bin, "8-bit", "");
        IJ.run(imp_bin, "OCV ConnectedComponentsWithStats", "connectivity=8-connected enable_output_labeled_image");
        ImagePlus imp_lab = WindowManager.getImage("__bin_Connect8-1");        
        
        ResultsTable rt = ResultsTable.getResultsTable();
        int col_x = rt.getColumnIndex("BX");
        int col_y = rt.getColumnIndex("BY");
        int col_w = rt.getColumnIndex("Width");
        int col_h = rt.getColumnIndex("Height");
        ArrayList<float[]> arr_point_max = new ArrayList<float[]>();
        
        for(int i = 0; i < rt.size(); i++)
        {
            int bx = (int)(rt.getValueAsDouble(col_x, i));
            int by = (int)(rt.getValueAsDouble(col_y, i));
            int w = (int)(rt.getValueAsDouble(col_w, i));
            int h = (int)(rt.getValueAsDouble(col_h, i));  
            float[] point_max = new float[3];
            
            Roi roi_blob = new Roi(bx, by, w, h);

            imp_dst.setRoi(roi_blob);
            ImagePlus imp_dst_roi = imp_dst.duplicate();
            float[] arr__dst_roi = (float[]) imp_dst_roi.getChannelProcessor().getPixels();
            
            imp_lab.setRoi(roi_blob);
            ImagePlus imp_lab_roi = imp_lab.duplicate();
            IJ.run(imp_lab_roi, "32-bit", "");
            float[] arr_lab_roi = (float[]) imp_lab_roi.getChannelProcessor().getPixels();
            
            search_max_point(arr__dst_roi, arr_lab_roi, w, i + 1, point_max);
            point_max[0] = point_max[0] + (float)bx;
            point_max[1] = point_max[1] + (float)by; 
            arr_point_max.add(point_max);
            
            imp_dst_roi.close();
            imp_lab_roi.close();
        }
        
        imp_bin.close();
        imp_lab.close();
        
        // prepare the ResultsTable
        rt.reset();
        
        // prepare the ROI Manager
        RoiManager roiMan = OCV__LoadLibrary.GetRoiManager(true, true);
        
        // show
        Macro_Runner mr = new Macro_Runner();
        mr.runMacro("setBatchMode(true);", "");
        int num_match = arr_point_max.size();
        
        for(int i = 0; i < num_match; i++)
        {
            int bx = (int)arr_point_max.get(i)[0];
            int by = (int)arr_point_max.get(i)[1];
            float match = arr_point_max.get(i)[2];
            
            Roi roi = new Roi(bx, by, imw_tmp, imh_tmp);
            imp_src.setRoi(roi);
            
            roiMan.addRoi(roi);
            int idx_last = roiMan.getCount() - 1;
            roiMan.select(idx_last);
            roiMan.runCommand("Rename", String.valueOf(i + 1) + "_" + "Match" + "_" + String.valueOf(match));
            
            rt.incrementCounter();
            rt.addValue("BX", bx);
            rt.addValue("BY", by);
            rt.addValue("Width", imw_tmp);
            rt.addValue("Height", imh_tmp);
            rt.addValue("Match", match);
            rt.show("Results");            
        }
        
        mr.runMacro("setBatchMode(false);", "");
        roiMan.runCommand("Show All");    
    }
    
    private void substracted_from_one(float[] srcdst)
    {
         int num = srcdst.length;
        
        for(int i = 0; i < num; i++)
        {
            srcdst[i] = 1 - srcdst[i];
        }
    }
    
    private void binary_float(float[] srcdst, float thr)
    {
        int num = srcdst.length;
        
        for(int i = 0; i < num; i++)
        {
            if(thr <= srcdst[i])
            {
                srcdst[i] = 255;
            }
            else
            {
                srcdst[i] = 0;
            }
        }
    }
    
    private void search_max_point(float[] src, float[] lab, int w, int ind, float[] point_max)
    {
        int num = src.length;
        float max = src[0];
        int ind_max = 0;
        
        for(int i = 0; i < num; i++)
        {
            if(lab[i] == (float)ind && max < src[i])
            {
                max = src[i];
                ind_max = i;
            }
        }
        
        point_max[0] = (float)(ind_max % w);
        point_max[1] = (float)(ind_max / w);
        point_max[2] = max;
    }
}
