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
public class WK_RoiMan_Limited implements ExtendedPlugInFilter {
    // const var.
    private static final int FLAGS = DOES_ALL;
    private static final int DECIMAL_PLACES = 4;

    // static var.
    private static String type = "Area";
    private static boolean enableMinLimit = false;
    private static double minLimit = 0.0;
    private static boolean enableMaxLimit = false;
    private static double maxLimit = 0.0;

    // var.
    private String className;
    private RoiManager roiManager = null;
    private int roiCount = 0;
    private ResultsTable resultsTable = null;
    private Macro_Runner macroRunner = new Macro_Runner();
    private boolean useExistingResults;

    @Override
    public int showDialog(ImagePlus ip, String cmd, PlugInFilterRunner pifr) {
        String[] features = resultsTable.getHeadings();
        
        className = cmd.trim();
        GenericDialog gd = new GenericDialog(className + " ...");

        gd.addChoice("type", features, type);
        gd.addCheckbox("enable_min_limit", enableMinLimit);
        gd.addNumericField("min_limit", minLimit, DECIMAL_PLACES);
        gd.addCheckbox("enable_max_limit", enableMaxLimit);
        gd.addNumericField("max_limit", maxLimit, DECIMAL_PLACES);

        if(useExistingResults) {
            gd.addMessage("The existing ResultsTable is used");
        }
        else {
            gd.addMessage("The new ResultsTable is used");
        }

        gd.showDialog();

        if(gd.wasCanceled()) {
            return DONE;
        }
        else {
            type = features[gd.getNextChoiceIndex()];
            enableMinLimit = gd.getNextBoolean();
            minLimit = gd.getNextNumber();
            enableMaxLimit = gd.getNextBoolean();
            maxLimit = gd.getNextNumber();

            return FLAGS;
        }
    }

    @Override
    public void setNPasses(int i) {
        // do nothing
    }

    @Override
    public int setup(String string, ImagePlus imp) {
        if(imp == null) {
            IJ.noImage();
            return DONE;
        }
        else {
            roiManager = getRoiManager(false, true);
            
            if(roiManager == null) {
                OCV__LoadLibrary.logError("WK_RoiMan_Limited", "Failed to get ROI Manager");
                return DONE;
            }

            roiCount = roiManager.getCount();

            if(roiCount == 0) {
                OCV__LoadLibrary.logError("WK_RoiMan_Limited", "ROI is vacant.");
                return DONE;
            }

            resultsTable = getResultsTable(false);

            if(resultsTable.getCounter() != roiManager.getCount()) {
                resultsTable.reset();
            }

            roiManager.deselect();

            if(resultsTable.getCounter() == 0) {
                try {
                    macroRunner.runMacro("roiManager(\"Measure\");", "");
                    useExistingResults = false;
                }
                catch(Exception e) {
                    OCV__LoadLibrary.logError("WK_RoiMan_Limited", "Failed to measure ROIs (" + e.getMessage() + ")");
                    return DONE;
                }
            }
            else {
                useExistingResults = true;
            }

            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip) {
        try {
            macroRunner.runMacro("setBatchMode(true);", "");

            int columnIndex = resultsTable.getColumnIndex(type);
            
            if(columnIndex == ResultsTable.COLUMN_NOT_FOUND) {
                OCV__LoadLibrary.logError("WK_RoiMan_Limited", "Column '" + type + "' not found in Results Table");
                macroRunner.runMacro("setBatchMode(false);", "");
                return;
            }

            for(int i = roiCount - 1; i >= 0; i--) {
                try {
                    double value = Double.valueOf(resultsTable.getStringValue(columnIndex, i));
                    
                    boolean meetsMinLimit = !enableMinLimit || (minLimit <= value);
                    boolean meetsMaxLimit = !enableMaxLimit || (value <= maxLimit);

                    if(!meetsMinLimit || !meetsMaxLimit) {
                        roiManager.select(i);
                        roiManager.runCommand("delete");
                        resultsTable.deleteRow(i);
                    }
                }
                catch(Exception e) {
                    OCV__LoadLibrary.logError("WK_RoiMan_Limited", "Error processing ROI " + i + " (" + e.getMessage() + ")");
                }
            }

            macroRunner.runMacro("setBatchMode(false);", "");
            resultsTable.show("Results");
            roiManager.runCommand("show all");
        }
        catch(Exception e) {
            OCV__LoadLibrary.logError("WK_RoiMan_Limited", "Error in processing (" + e.getMessage() + ")");
            try {
                macroRunner.runMacro("setBatchMode(false);", "");
            }
            catch(Exception ex) {
                // Ignore if batch mode exit fails
            }
        }
    }

    /**
     * Get the ResultsTable or create a new ResultsTable
     * @param shouldReset reset or not
     * @return ResultsTable
     */
    private ResultsTable getResultsTable(boolean shouldReset) {
        ResultsTable resultsTable = ResultsTable.getResultsTable();

        if(resultsTable == null || resultsTable.getCounter() == 0) {
            resultsTable = new ResultsTable();
        }

        if(shouldReset) {
            resultsTable.reset();
        }

        resultsTable.show("Results");

        return resultsTable;
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
            OCV__LoadLibrary.logError("WK_RoiMan_Limited", "Failed to get ROI Manager (" + e.getMessage() + ")");
            return null;
        }
    }
}