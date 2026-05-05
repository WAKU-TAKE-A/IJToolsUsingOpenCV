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
public class WK_RoiMan_SelectAll implements ij.plugin.filter.ExtendedPlugInFilter {
    // const var.
    private static final int FLAGS = DOES_ALL;
    private static final String STR_NONE = "none";
    private static final String[] TYPE_STR = { STR_NONE, "and", "or", "xor" };

    // static var.
    private static int selectedActionIndex = 0;

    // var.
    private String className;
    private RoiManager roiManager = null;
    private int roiCount = 0;
    private Macro_Runner macroRunner = new Macro_Runner();

    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner pfr) {
        className = cmd.trim();
        GenericDialog gd = new GenericDialog(className + " ...");
        gd.addChoice("action_after_selecting", TYPE_STR, TYPE_STR[selectedActionIndex]);
        gd.showDialog();

        if(gd.wasCanceled()) {
            return DONE;
        }
        else {
            selectedActionIndex = gd.getNextChoiceIndex();
            return FLAGS;
        }
    }

    @Override
    public void setNPasses(int nPasses) {
        // do nothing
    }

    @Override
    public int setup(String arg, ImagePlus imp) {
        if(imp == null) {
            IJ.noImage();
            return DONE;
        }
        else {
            roiManager = getRoiManager(false, true);

            if(roiManager == null) {
                OCV__LoadLibrary.logError("WK_RoiMan_SelectAll", "Failed to get ROI Manager");
                return DONE;
            }

            roiCount = roiManager.getCount();

            if(roiCount == 0) {
                OCV__LoadLibrary.logError("WK_RoiMan_SelectAll", "ROI is vacant.");
                return DONE;
            }

            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip) {
        try {
            macroRunner.runMacro("setBatchMode(true);", "");

            if(roiCount == 1) {
                roiManager.select(0);
            }
            else if(roiCount > 1) {
                roiManager.deselect();

                int[] allIndexes = roiManager.getIndexes();

                if(allIndexes == null || allIndexes.length == 0) {
                    OCV__LoadLibrary.logError("WK_RoiMan_SelectAll", "Failed to get ROI indexes");
                    return;
                }

                roiManager.setSelectedIndexes(allIndexes);

                if(!TYPE_STR[selectedActionIndex].equals(STR_NONE)) {
                    roiManager.runCommand(TYPE_STR[selectedActionIndex]);
                }
            }

            macroRunner.runMacro("setBatchMode(false);", "");
        }
        catch(Exception e) {
            OCV__LoadLibrary.logError("WK_RoiMan_SelectAll", "Error in processing (" + e.getMessage() + ")");
            try {
                macroRunner.runMacro("setBatchMode(false);", "");
            }
            catch(Exception ex) {
                // Ignore if batch mode exit fails
            }
        }
    }

    /**
     * Get the RoiManager or create a new RoiManager
     * @param shouldReset reset or not
     * @param shouldShowNone show none or not
     * @return RoiManager or null if failed
     */
    private RoiManager getRoiManager(boolean shouldReset, boolean shouldShowNone) {
        try {
            Frame frame = WindowManager.getFrame("ROI Manager");
            RoiManager roiManager = null;

            if(frame == null) {
                roiManager = new RoiManager();
                roiManager.setVisible(true);
            }
            else {
                roiManager = (RoiManager)frame;
            }

            if(shouldReset) {
                roiManager.reset();
            }

            if(shouldShowNone) {
                roiManager.runCommand("Show None");
            }

            return roiManager;
        }
        catch(Exception e) {
            OCV__LoadLibrary.logError("WK_RoiMan_SelectAll", "Failed to get ROI Manager (" + e.getMessage() + ")");
            return null;
        }
    }
}