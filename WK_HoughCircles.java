import ij.*;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.OvalRoi;
import ij.measure.ResultsTable;
import ij.plugin.Macro_Runner;
import ij.plugin.filter.*;
import ij.plugin.frame.RoiManager;
import ij.process.*;
import java.awt.AWTEvent;
import java.awt.Frame;
import java.awt.Rectangle;
import java.util.ArrayList;

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
 * Circle hough transform.
 */
public class WK_HoughCircles implements ExtendedPlugInFilter, DialogListener
{
    // constant var.
    private static final int FLAGS = DOES_8G;
    private static final int ERR_OK = 0;
    private static final int ERR_ARG = -2;
    private static final int ERR_MEM_ALLOC = 3;
    private static final int[] INT_MODE = { 90, 120, 180, 240, 360, 720, 1440, -1 };
    private static final String[] STR_MODE = { "90", "120", "180", "240", "360", "720", "1440", "RMAX*8" };
    private static final int IR = 0;
    private static final int IVOTE = 1;
    private static final int IXMIN = 2;
    private static final int IXMAX = 3;
    private static final int IYMIN = 4;
    private static final int IYMAX = 5;
    private static final int IXSUM = 6;
    private static final int IYSUM = 7;
    private static final int IN = 8;

    // static var.
    private static Rectangle rect = null;
    private static int rmin = 0;
    private static int rmax = 0;
    private static int indMode = 4;
    private static int minVotes = 1;
    private static double rngSame = 1;
    private static boolean enAddRoi = true;
    private static boolean enOutputImg = true;

    // var.
    private ImagePlus impSrc = null;
    private final ArrayList<double[]> res = new ArrayList<double[]>();

    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner pfr)
    {
        GenericDialog gd = new GenericDialog(cmd.trim() + "...");

        gd.addNumericField("min_radius", rmin, 0);
        gd.addNumericField("max_radius", rmax, 0);
        gd.addChoice("mode", STR_MODE, STR_MODE[indMode]);
        gd.addNumericField("min_votes", minVotes, 0);
        gd.addNumericField("range_to_judge_same", rngSame, 4);
        gd.addCheckbox("enable_add_roi", enAddRoi);
        gd.addCheckbox("enable_output_hough_image", enOutputImg);
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
    public void setNPasses(int arg0)
    {
        //do nothing
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
            impSrc = imp;

            if(imp.getRoi() != null)
            {
                rect = imp.getRoi().getBounds();
            }
            else
            {
                rect = new Rectangle(0, 0, imp.getWidth(), imp.getHeight());
            }

            imp.setRoi(rect.x, rect.y, rect.width, rect.height);

            return DOES_8G;
        }
    }

    @Override
    public void run(ImageProcessor ip)
    {
        // src
        byte[] src = (byte[]) ip.getPixels();
        int imw = ip.getWidth();

        // dst
        ImagePlus impDst = new ImagePlus ("tmp", new ShortProcessor(rect.width, rect.height * (rmax - rmin + 1)));
        short[] dst = (short[]) impDst.getChannelProcessor().getPixels();

        // run
        int mode = indMode == (STR_MODE.length - 1) ? rmax * 8 : INT_MODE[indMode];
        int err = houghCircles(src, dst, imw, rect.x, rect.y, rect.width, rect.height, rmin, rmax, mode);

        // fin
        if (err != ERR_OK)
        {
            IJ.error("Err code of HoughCircle() is " + String.valueOf(err));
            return;
        }

        if(enOutputImg)
        {
            showHoughImg(impDst, rect, rmin, rmax, impSrc.getShortTitle() + "_HoughImage");
        }

        showData(dst);
    }

    @Override
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent awte)
    {
        rmin = (int)gd.getNextNumber();
        rmax = (int)gd.getNextNumber();
        indMode = gd.getNextChoiceIndex();
        minVotes = (int)gd.getNextNumber();
        rngSame = (double)gd.getNextNumber();
        enAddRoi = gd.getNextBoolean();
        enOutputImg = gd.getNextBoolean();

        if(rmin < 0) { IJ.showStatus("ERR : rmin < 0"); return false; }
        if(rmax < 0) { IJ.showStatus("ERR : rmax < 0"); return false; }
        if(rmax < rmin) { IJ.showStatus("ERR : rmax < rmin"); return false; }
        if(minVotes < 0) { IJ.showStatus("ERR : minVotes < 0"); return false; }
        if(rngSame < 0) { IJ.showStatus("ERR : rngSame < 0"); return false; }
        
        return true;
    }

    // private
    private int houghCircles(byte[] pSrc, short[] pHoughValues, int imw, int roix, int roiy, int roiw, int roih, int rmin, int rmax, int mode)
    {
        int err = ERR_OK;
        int incDen = 0;
        int depthR = (rmax - rmin) + 1;
        int lutSize = 0;

        // check
        if (err == ERR_OK && mode > 0)
        {
            incDen = mode;
        }
        else
        {
            err = ERR_ARG;
        }

        // lookup table
        int[] cosLut = new int[incDen * depthR];
        int[] sinLut = new int[incDen * depthR];

        if (err == ERR_OK)
        {
            if ((cosLut == null) || (sinLut == null))
            {
                err = ERR_MEM_ALLOC;
            }
        }

        int i = 0;

        if (err == ERR_OK)
        {
            for (int indR = 0; indR < depthR ; indR++)
            {
                i = 0;

                for (int incNun = 0; incNun < incDen / 4; incNun++)
                {
                    double angle =  2 * Math.PI  / (double)incDen * (double)incNun;
                    int tmp = (int)((rmin + indR) * Math.cos(angle));
                    int rcos = (int)(tmp + 0.5 - (tmp < 0 ? 1 : 0));
                    tmp = (int)((rmin + indR) * Math.sin(angle));
                    int rsin = (int)(tmp + 0.5 - (tmp < 0 ? 1 : 0));

                    if ((i == 0) | (rcos != cosLut[i * depthR + indR]) & (rsin != sinLut[i * depthR + indR]))
                    {
                        cosLut[i * depthR + indR] = rcos;
                        sinLut[i * depthR + indR] = rsin;
                        i++;
                    }
                }
            }

            lutSize = i;
        }

       // Hough transform
        if (err == ERR_OK)
        {
            for (int y = roih - 1; y >= 0; y--)
            {
                for (int x = roiw - 1; x >= 0; x--)
                {
                    for (int indR = depthR - 1; indR >= 0; indR--)
                    {
                        if (pSrc[(x + roix) + (y + roiy) * imw] != 0)
                        {
                            for (int k = lutSize - 1; k >= 0; k--)
                            {
                                int a1 = x + cosLut[k * depthR + indR];
                                int b1 = y + sinLut[k * depthR + indR];

                                if ((b1 >= 0) && (b1 < roih) && (a1 >= 0) && (a1 < roiw))
                                {
                                    int cond = indR * roih * roiw + b1 * roiw + a1;
                                    pHoughValues[cond] += 1;
                                }

                                int a2 = x + cosLut[k * depthR + indR];
                                int b2 = y - sinLut[k * depthR + indR];

                                if ((b2 >= 0) && (b2 < roih) && (a2 >= 0) && (a2 < roiw))
                                {
                                    int cond = indR * roih * roiw + b2 * roiw + a2;
                                    pHoughValues[cond] += 1;
                                }

                                int a3 = x - cosLut[k * depthR + indR];
                                int b3 = y + sinLut[k * depthR + indR];

                                if ((b3 >= 0) && (b3 < roih) && (a3 >= 0) && (a3 < roiw))
                                {
                                    int cond = indR * roih * roiw + b3 * roiw + a3;
                                    pHoughValues[cond] += 1;
                                }

                                int a4 = x - cosLut[k * depthR + indR];
                                int b4 = y - sinLut[k * depthR + indR];

                                if ((b4 >= 0) && (b4 < roih) && (a4 >= 0) && (a4 < roiw))
                                {
                                    int cond = indR * roih * roiw + b4 * roiw + a4;
                                    pHoughValues[cond] += 1;
                                }
                            }
                        }
                    }
                }
            }
        }

        return err;
    }

    private void showHoughImg(ImagePlus imp, Rectangle rect, int rmin, int rmax, String title)
    {
        imp.setDisplayRange(Short.MIN_VALUE, Short.MAX_VALUE);

        ImageStack ims = new ImageStack(rect.width, rect.height);

        for (int i = 0; i <= rmax - rmin; i++)
        {
            ImagePlus buf = new ImagePlus ("buf", new ShortProcessor(rect.width, rect.height));
            imp.setRoi(0, rect.height * i, rect.width, rect.height);
            buf = imp.duplicate();
            ims.addSlice("R = " + Integer.toString(i + rmin), buf.getProcessor());
        }

        ImagePlus stk_imp = new ImagePlus(title, ims);
        stk_imp.show();

        Macro_Runner mr = new Macro_Runner();
        mr.runMacro("run(\"Enhance Contrast\", \"saturated=0.35\");", "");
    }

    private void showData(short[] arr_hough_img)
    {
        // prepare the ResultsTable
        ResultsTable rt = getResultsTable(true);
        
        // prepare the ROI Manager
        RoiManager roiMan = null;
        
        if(enAddRoi)
        {
            roiMan = getRoiManager(true, true);
        }

        // judge to be the same
        int w = rect.width;
        int h = rect.height;
        int numAll = arr_hough_img.length;
        int num_res = 0;

        for(int i = 0; i < numAll; i++)
        {
            if(minVotes < arr_hough_img[i])
            {
                int x = i % w;
                int y = i / w % h;
                int r = i / w / h + rmin;
                int vt = (int)arr_hough_img[i];
                
                num_res = res.size();
                boolean chkMatch = false;

                if(num_res != 0)
                {
                    for(int i_res = 0; i_res < num_res; i_res++)
                    {
                        double[] res_ar = res.get(i_res);
                        
                        if(res_ar[IR] == r && res_ar[IXMIN] <= x && x <= res_ar[IXMAX] && res_ar[IYMIN] <= y && y <= res_ar[IYMAX])
                        {
                            res_ar[IXSUM] += (double)x;
                            res_ar[IYSUM] += (double)y;
                            res_ar[IN] += 1;
                            double xave = res_ar[IXSUM] / res_ar[IN];
                            double yave = res_ar[IYSUM] / res_ar[IN];
                            res_ar[IXMIN] = xave - rngSame;
                            res_ar[IXMAX] = xave + rngSame;
                            res_ar[IYMIN] = yave - rngSame;
                            res_ar[IYMAX] = yave + rngSame;
                            
                            if(res_ar[IVOTE] < vt)
                            {
                                res_ar[IVOTE] = vt;
                            }
                            
                            chkMatch = true;
                            break;
                        }                    
                    }
                }
                
                if(!chkMatch)
                {
                    res.add(new double[]{ (double)r, (double)vt, (double)(x - rngSame), (double)(x + rngSame), (double)(y - rngSame), (double)(y + rngSame), x, y, 1});
                }
            }
        }
        
        // show
        num_res = res.size();
        
        for(int i = 0; i < num_res; i++)
        {
            double[] res_ar = res.get(i);
            
            double xave = res_ar[IXSUM] / res_ar[IN];
            double yave = res_ar[IYSUM] / res_ar[IN];
            double r = res_ar[IR];
            double dia = 2 * r;
            int vt = (int)res_ar[IVOTE];
            int n = (int)res_ar[IN];
            
            rt.incrementCounter();
            rt.addValue("CenterX", xave);
            rt.addValue("CenterY", yave);
            rt.addValue("R", r);
            rt.addValue("MaxVotes", vt);
            rt.addValue("NumOfSame", n);

            if(enAddRoi && (null != roiMan))
            {
                OvalRoi roi = new OvalRoi((xave - r), (yave - r), dia, dia);
                roiMan.addRoi(roi);
            }
        }

        rt.show("Results");
    }

    /**
     * get the ResultsTable or create a new ResultsTable
     * @param enReset reset or not
     * @return ResultsTable
     */
    private ResultsTable getResultsTable(boolean enReset)
    {
        ResultsTable rt = ResultsTable.getResultsTable();

        if(rt == null || rt.getCounter() == 0)
        {
            rt = new ResultsTable();
        }

        if(enReset)
        {
            rt.reset();
        }

        rt.show("Results");

        return rt;
    }
    
    /**
     * get the RoiManager or create a new RoiManager
     * @param enReset reset or not
     * @param enShowNone show none or not
     * @return RoiManager
     */
    private RoiManager getRoiManager(boolean enReset, boolean enShowNone)
    {
        Frame frame = WindowManager.getFrame("ROI Manager");
        RoiManager rm = null;        
        
        if (frame == null)
        {
            rm = new RoiManager();
            rm.setVisible(true);
        }
        else
        {
            rm = (RoiManager)frame;       
        }
        
        if(enReset)
        {
            rm.reset();
        }
        
        if(enShowNone)
        {
            rm.runCommand("Show None");
        }
        
        return rm;
    }
}
