import ij.IJ;
import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import java.awt.AWTEvent;

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
 * getPerspectiveTransform.
 */
public class OCV_GetPerspectiveTransform implements ExtendedPlugInFilter, DialogListener  {
    // constant var.
    private static final int FLAGS = NO_IMAGE_REQUIRED;
    private static final String[] TYPE_STR_CMD = new String[] { "compute", "compute_and_write", "compute_dst", "read"};

    // static var.
    private static MyPerspectiveTransform myPerspective = new MyPerspectiveTransform();
    private static int indCmd = 0;
    private static String targetName = "";
    private static boolean enShowMat = false;

    // var.
    private String className = "";
    private RoiManager roiMan = null;
    
    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner prf) {
        className = cmd.trim();
        GenericDialog gd = new GenericDialog(className + "...");
        gd.addChoice("command", TYPE_STR_CMD, TYPE_STR_CMD[indCmd]);
        gd.addStringField("target_name", targetName, 8);
        gd.addCheckbox("enable_show_matrix", enShowMat);
        gd.addDialogListener(this);

        gd.showDialog();
        return FLAGS;
    }

    @Override
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent awte) {
        indCmd = (int)gd.getNextChoiceIndex();
        targetName  = (String)gd.getNextString();
        enShowMat = (boolean)gd.getNextBoolean();

        if ((indCmd == 1 || indCmd == 3) && OCV__LoadLibrary.isNullOrEmpty(targetName)) {
            IJ.showStatus("target_name is empty.");
            return false;
        }

        IJ.showStatus(className);
        return true;
    }

    @Override
    public void setNPasses(int arg0) {
        // do nothing
    }

    @Override
    public int setup(String arg0, ImagePlus imp) {
        if(!OCV__LoadLibrary.isLoad()) {
            IJ.error("Library is not loaded.");
            return DONE;
        }

        return FLAGS;
    }

    @Override
    public void run(ImageProcessor ip) {
        try {
            if (indCmd == 0 || indCmd == 1) {
                // compute or compute_and_write: need 2 ROIs
                roiMan = OCV__LoadLibrary.GetRoiManager(false, true);
                int roiNum = roiMan.getCount();
                if (roiNum < 2) {
                    IJ.log(className + " error: At least two ROIs are required.");
                    return;
                }
                Roi roiSrc = roiMan.getRoi(0);
                Roi roiDst = roiMan.getRoi(1);
                myPerspective.setRoi(roiSrc, roiDst);
            } else if (indCmd == 2) {
                // compute_dst: need existing src and 1 new ROI
                if (!myPerspective.finSetRoi) {
                    IJ.log(className + " error: Source ROI is not set. Use 'compute' first.");
                    return;
                }
                
                roiMan = OCV__LoadLibrary.GetRoiManager(false, true);
                int roiNum = roiMan.getCount();
                if (roiNum < 1) {
                    IJ.log(className + " error: At least one ROI is required for new destination.");
                    return;
                }
                
                Roi roiSrc = myPerspective.PerspectiveSrc;
                Roi roiDst = roiMan.getRoi(0);
                myPerspective.setRoi(roiSrc, roiDst);
            }

            // Execute command
            if (indCmd == 0 || indCmd == 2) {
                // compute or compute_dst
                myPerspective.compute();
            } else if (indCmd == 1) {
                // compute_and_write
                myPerspective.setFileName(targetName);
                myPerspective.computeAndWrite();
            } else if (indCmd == 3) {
                // read
                myPerspective.setFileName(targetName);
                myPerspective.read();
            } else {
                IJ.log(className + " error: Invalid command index.");
                return;
            }

            // Show matrix if requested
            if (enShowMat) {
                myPerspective.ShowData();
            }
            
            // Copy to global instance
            myPerspective.copyTo(OCV__LoadLibrary.MyPerspective);
            
        } catch(java.io.IOException e) {
            IJ.log(className + " IO error: " + e.getMessage());
        } catch(IllegalStateException | IllegalArgumentException e) {
            IJ.log(className + " error: " + e.getMessage());
        } catch(Exception e) {
            IJ.log(className + " unexpected error: " + e.getMessage());
        }
    }
}