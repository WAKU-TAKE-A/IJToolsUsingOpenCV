import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Line;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import java.awt.Frame;
import java.awt.Rectangle;
import java.util.ArrayList;
import org.opencv.core.Point;

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
 * Linear fitting.
 */
public class WK_RoiMan_LinearFitting implements ExtendedPlugInFilter
{
    // const var.
    private static final int FLAGS = DOES_8G | DOES_16 | DOES_32;

    // static var.
    private static int num_run = 0;
    private static boolean enRefData = false;
    private static boolean enAddRoi = true;

    // var.
    private ImagePlus impSrc = null;
    private RoiManager roiMan = null;
    private int[] selectedIndexes = null;
    private ResultsTable rsTbl = null;

    @Override
    public int showDialog(ImagePlus ip, String cmd, PlugInFilterRunner pifr)
    {
        GenericDialog gd = new GenericDialog(cmd + "...");

        gd.addCheckbox("enable_refresh_data", enRefData);
        gd.addCheckbox("enable_add_roi", enAddRoi);

        gd.showDialog();

        if (gd.wasCanceled())
        {
            return DONE;
        }
        else
        {
            enRefData = (boolean)gd.getNextBoolean();
            enAddRoi = (boolean)gd.getNextBoolean();
            
            if(enRefData)
            {
                rsTbl.reset();
            }

            return FLAGS;
        }
    }

    @Override
    public void setNPasses(int i)
    {
        // do nothing
    }

    @Override
    public int setup(String string, ImagePlus imp)
    {
        if (imp == null)
        {
            IJ.noImage();
            return DONE;
        }
        else
        {
            // get the ImagePlus
            impSrc = imp;
            
            // get the ResultsTable
            rsTbl = getResultsTable(false);

            // get the ROI Manager
            roiMan = getRoiManager(false, true);
            int num_roi = roiMan.getCount();

            if(num_roi == 0)
            {
                IJ.error("ROI is vacant.");
                return DONE;
            }
            
            // get the selected rois
            selectedIndexes = roiMan.getSelectedIndexes();
            
            if(selectedIndexes == null || selectedIndexes.length == 0)
            {
                selectedIndexes = new int[] { 0 };
            }

            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip)
    {       
        int num_slctd = selectedIndexes.length;
        
        boolean all_eq_x = true;
        boolean all_eq_y = true;
        int num_all = 0;
        double ini_x = 0;
        double ini_y = 0;
        double sxy = 0;
        double sx = 0;
        double sy = 0;
        double sxx = 0;
        ArrayList<Point> lstPt = new ArrayList<Point>();

        for(int i = 0; i < num_slctd; i++)
        {
            Roi roi = roiMan.getRoi(selectedIndexes[i]);
            getCoordinates(roi, lstPt);           
        }

        ini_x = lstPt.get(0).x;
        ini_y = lstPt.get(0).y;        
        num_all = lstPt.size();
        
        for(int i = 0; i < num_all; i++)
        {
            double x = lstPt.get(i).x;
            double y = lstPt.get(i).y;
            
            if(ini_x != x)
            {
               all_eq_x = false;
            }

            if(ini_y != y)
            {
               all_eq_y = false;
            }
            
            sx += x;
            sy += y;
            sxx += x * x;
            sxy += x * y;
        }
        
        double a = (double)num_all * (double)sxx - (double)sx * (double)sx;        
        double slope = 0;
        double intercept = 0;
        
        if(all_eq_x && all_eq_y)
        {
            IJ.error("only one point");
            return;
        }
        else if(all_eq_x && !all_eq_y)
        {
            slope = 0.0 / 0.0;
            intercept = 0.0 / 0.0;
        }
        else if(!all_eq_x && all_eq_y)
        {
            slope = 0;
            intercept = ini_y;
        }
        else
        {
            slope = ((double)num_all * (double)sxy - (double)sx * (double)sy) / a;
            intercept = ((double)sxx * (double)sy - (double)sxy * (double)sx) / a;
        }
        
        rsTbl.incrementCounter();
        rsTbl.addValue("Slope", String.valueOf(slope));
        rsTbl.addValue("Intercept", String.valueOf(intercept));
        rsTbl.show("Results");
        
        if(enAddRoi)
        {
            double x1 = 0.0;
            double y1 = 0.0;
            double x2 = 0.0;
            double y2 = 0.0;
            
            if(all_eq_x && !all_eq_y)
            {
                double h = (double)(impSrc.getHeight());
                x1 = (double)ini_x;
                y1 = 0.0;
                x2 = x1;
                y2 = h + (double)num_run;
            }
            else if(!all_eq_x && all_eq_y)
            {
                double w = (double)(impSrc.getWidth());
                x1 = 0.0;
                y1 = (double)ini_y;
                x2 = w + (double)num_run;
                y2 = y1;               
            }
            else
            {
                if(-1 <= slope && slope <= 1)
                {
                    double w = (double)(impSrc.getWidth());
                    x1 = 0.0;
                    y1 = slope * x1 + intercept;
                    x2 = w + (double)num_run;
                    y2 = slope * x2 + intercept;  
                }
                else
                {
                    double h = (double)(impSrc.getHeight());
                    y1 = 0.0;
                    x1 = (y1 - intercept) / slope;
                    y2 = h + (double)num_run; 
                    x2 = (y2 - intercept) / slope;               
                }               
            }
            
            Line ln = new Line(x1, y1, x2, y2);
            roiMan.addRoi(ln);
            num_run++;
        }
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
    
    /**
     * get the coordinates in the roi.
     * @param roi
     * @param lstPt 
     */
    private void getCoordinates(Roi roi, ArrayList<Point> lstPt)
    {
        int pos_x = 0;
        int pos_y = 0;
        Rectangle rect = roi.getBounds();
        int le = rect.x;
        int to = rect.y;
        int ri = rect.x + rect.width - 1;
        int bo = rect.y + rect.height - 1;
        int imw = impSrc.getWidth();
        
        if(impSrc.getBitDepth() == 8)
        {
            byte[] data = (byte[])impSrc.getProcessor().getPixels();
            
            for(int y = to; y <= bo; y++)
            {
                for(int x = le; x <= ri; x++)
                {
                    if (data[x + y * imw] != 0)
                    {
                        lstPt.add(new Point(x, y));
                    }
                }
            }
        }
        else if(impSrc.getBitDepth() == 16)
        {
            short[] data = (short[])impSrc.getProcessor().getPixels();
            
            for(int y = to; y <= bo; y++)
            {
                for(int x = le; x <= ri; x++)
                {
                    if (data[x + y * imw] != 0)
                    {
                        lstPt.add(new Point(x, y));
                    }
                }
            }
        }
        else if(impSrc.getBitDepth() == 32)
        {
            float[] data = (float[])impSrc.getProcessor().getPixels();
            
            for(int y = to; y <= bo; y++)
            {
                for(int x = le; x <= ri; x++)
                {
                    if (data[x + y * imw] != 0)
                    {
                        lstPt.add(new Point(x, y));
                    }
                }
            }
        }
    }
}
