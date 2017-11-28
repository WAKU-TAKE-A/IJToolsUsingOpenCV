import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.measure.ResultsTable;
import ij.plugin.Macro_Runner;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.RoiManager;
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
 * Limit ROI.
 */
public class WK_RoiMan_Limited implements ExtendedPlugInFilter
{
    // const var.
    private static final int FLAGS = DOES_ALL;

    // static var.
    private static String type = "Area";
    private static boolean enMin = false;
    private static double min = 0.0;
    private static boolean enMax = false;
    private static double max = 0.0;

    // var.
    private RoiManager roiMan = null;
    private int num_roi = 0;
    private ResultsTable rsTbl = null;
    private final Macro_Runner mr = new Macro_Runner();
    private boolean useExistRes;

    @Override
    public int showDialog(ImagePlus ip, String cmd, PlugInFilterRunner pifr)
    {
        String[] feats = rsTbl.getHeadings();

        GenericDialog gd = new GenericDialog(cmd + "...");

        gd.addChoice("type", feats, type);
        gd.addCheckbox("enable_min_limit", enMin);
        gd.addNumericField("min_limit", min, 4);
        gd.addCheckbox("enable_max_limit", enMax);
        gd.addNumericField("max_limit", max, 4);

        if(useExistRes)
        {
            gd.addMessage("The existing ResultsTable is used");
        }
        else
        {
            gd.addMessage("The new ResultsTable is used");
        }

        gd.showDialog();

        if (gd.wasCanceled())
        {
            return DONE;
        }
        else
        {
            type = (String)feats[(int)gd.getNextChoiceIndex()];
            enMin = (boolean)gd.getNextBoolean();
            min = (double)gd.getNextNumber();
            enMax = (boolean)gd.getNextBoolean();
            max = (double)gd.getNextNumber();

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
            // get the ROI Manager
            roiMan = getRoiManager(false, true);
            num_roi = roiMan.getCount();

            if(num_roi == 0)
            {
                IJ.error("ROI is vacant.");
                return DONE;
            }

            // get the ResultsTable
            rsTbl = getResultsTable(false);

            if(rsTbl.getCounter() != roiMan.getCount())
            {
                rsTbl.reset();
            }

            // Mesure
            roiMan.deselect();

            if(rsTbl.getCounter() == 0)
            {
                mr.runMacro("roiManager(\"Measure\");", "");
                useExistRes = false;
            }
            else
            {
                useExistRes = true;
            }

            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip)
    {
        mr.runMacro("setBatchMode(true);", "");

        int col = rsTbl.getColumnIndex(type);
        double val;
        boolean chk_min;
        boolean chk_max;

        for(int i = num_roi - 1; 0 <= i; i--)
        {
            val = Double.valueOf(rsTbl.getStringValue(col, i));
            chk_min = enMin ? min <= val : true;
            chk_max = enMax ? val <= max : true;

            if(!chk_min || !chk_max)
            {
                roiMan.select(i);
                roiMan.runCommand("delete");
                rsTbl.deleteRow(i);
            }
        }

        mr.runMacro("setBatchMode(false);", "");
        rsTbl.show("Results");
        roiMan.runCommand("show all");
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
