import ij.*;
import ij.IJ;
import ij.gui.GenericDialog;
import ij.plugin.Macro_Runner;
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
 * Select all ROI.
 */
public class WK_RoiMan_SelectAll implements ij.plugin.filter.ExtendedPlugInFilter
{
    // const var.
    private static final int FLAGS = DOES_ALL;
    private static final String STR_NONE = "none";
    private static final String[] TYPE_STR = { STR_NONE, "and", "or", "xor" };

    // static var.
    private static int type_ind = 0;

    // var.
    private RoiManager roiMan = null;

    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner pfr)
    {
        GenericDialog gd = new GenericDialog(cmd + "...");
        gd.addChoice("action_after_selecting", TYPE_STR, TYPE_STR[type_ind]);
        gd.addHelp(OCV__LoadLibrary.URL_HELP);
        gd.showDialog();

        if (gd.wasCanceled())
        {
            return DONE;
        }
        else
        {
            type_ind = (int)gd.getNextChoiceIndex();
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
        if (imp == null)
        {
            IJ.noImage();
            return DONE;
        }
        else
        {
            // get the ROI Manager
            roiMan = getRoiManager(false, true);
            int num_roi = roiMan.getCount();

            if(num_roi == 0)
            {
                IJ.error("ROI is vacant.");
                return DONE;
            }

            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip)
    {
        Macro_Runner mr = new Macro_Runner();
        mr.runMacro("setBatchMode(true);", "");
        
        int num_roi = roiMan.getCount();

        if(num_roi == 0)
        {
            // do nothing
        }
        else if(num_roi == 1)
        {
            roiMan.select(0);
            // do nothing after selecting
        }
        else
        {
            roiMan.deselect();

            int[] indx_all = roiMan.getIndexes();
            roiMan.setSelectedIndexes(indx_all);

            if(!TYPE_STR[type_ind].equals(STR_NONE))
            {
                roiMan.runCommand(TYPE_STR[type_ind]);
            }
        }
        
        mr.runMacro("setBatchMode(false);", "");
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
