import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Line;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.RoiManager;
import ij.process.FloatPolygon;
import ij.process.ImageProcessor;
import java.awt.Frame;

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
 * linear fitting
 */
public class WK_RoiMan_LinearFitting implements ExtendedPlugInFilter
{
    // const var.
    private static final int FLAGS = DOES_ALL;

    // static var.
    private static boolean enRefData = false;
    private static boolean enAddRoi = true;

    // var.
    private ImagePlus impSrc = null;
    private FloatPolygon fPoly = null;
    private RoiManager roiMan = null;
    private int selectedIndex = -1;
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
                IJ.error("ERR : ROI is vacant.");
                return DONE;
            }
            
            // selectr roi
            selectedIndex = roiMan.getSelectedIndex();
            
            if(selectedIndex < 0)
            {
                selectedIndex = 0;
            }

            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip)
    {       
        Roi roi = roiMan.getRoi(selectedIndex);
        fPoly = getCoordinates(roi);
        
        int num = fPoly.npoints;
        boolean all_eq_x = true;
        boolean all_eq_y = true;
        float sxy = 0;
        float sx = 0;
        float sy = 0;
        float sxx = 0;
        
        for(int i = 0; i < num; i++)
        {
            if(fPoly.xpoints[0] != fPoly.xpoints[i])
            {
                all_eq_x = false;
            }
            
            if(fPoly.ypoints[0] != fPoly.ypoints[i])
            {
                all_eq_y = false;
            }
            
            sxy += fPoly.xpoints[i] * fPoly.ypoints[i];
            sx += fPoly.xpoints[i];
            sy += fPoly.ypoints[i];
            sxx += fPoly.xpoints[i] * fPoly.xpoints[i];
        }
        
        double a = (double)num * sxx - sx * sx;        
        double slope = 0;
        double intercept = 0;
        
        if(all_eq_x && all_eq_y)
        {
            slope = 0.0 / 0.0;
            intercept = 0.0 / 0.0;
        }
        else if(all_eq_x && !all_eq_y)
        {
            slope = 0.0 / 0.0;
            intercept = 0.0 / 0.0;
        }
        else if(!all_eq_x && all_eq_y)
        {
            slope = 0;
            intercept = fPoly.ypoints[0];
        }
        else
        {
            slope = ((double)num * sxy - sx * sy) / a;
            intercept = (sxx * sy - sxy * sx) / a;
        }
        
        rsTbl.incrementCounter();
        rsTbl.addValue("Slope", slope);
        rsTbl.addValue("Intercept", intercept);

        rsTbl.show("Results");
        
        if(enAddRoi)
        {
            if(all_eq_x && all_eq_y)
            {
                PointRoi pt = new PointRoi(fPoly.xpoints[0], fPoly.ypoints[0]);
                roiMan.addRoi(pt);
            }
            else if(all_eq_x && !all_eq_y)
            {
                double h = (double)(impSrc.getHeight());
                Line ln = new Line(fPoly.xpoints[0], 0.0, fPoly.xpoints[0], h);
                roiMan.addRoi(ln);
            }
            else if(!all_eq_x && all_eq_y)
            {
                double w = (double)(impSrc.getWidth());
                Line ln = new Line(0.0, fPoly.ypoints[0], w, fPoly.ypoints[0]);
                roiMan.addRoi(ln); 
            }
            else
            {
                if(-1 <= slope && slope <= 1)
                {
                    double w = (double)(impSrc.getWidth());
                    Line ln = new Line(0.0, intercept, w, slope * w + intercept);
                    roiMan.addRoi(ln);               
                }
                else
                {
                    double h = (double)(impSrc.getHeight());
                    Line ln = new Line((0 - intercept) / slope, 0.0, (h - intercept) / slope, h);
                    roiMan.addRoi(ln);                
                }               
            }
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
        RoiManager rm;        
        
        if (frame==null)
        {
            IJ.run("ROI Manager...");
        }

        frame = WindowManager.getFrame("ROI Manager");
        rm = (RoiManager)frame;
        
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
     * get the coordinates of the roi
     * @param roi ROI
     * @return Points
     */
    private FloatPolygon getCoordinates(Roi roi)
    {       
        FloatPolygon output = new FloatPolygon();
        
        if (roi.getType() == Roi.LINE)
        {
            Line line = (Line)roi;
            output.addPoint(line.x1d, line.y1d);
            output.addPoint(line.x2d, line.y2d);
        }
        else 
        {
            output = roi.getFloatPolygon();
        }
        
        return output;
    }
}
