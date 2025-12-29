import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.gui.Roi;
import ij.plugin.Macro_Runner;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import java.awt.Frame;
import java.awt.Rectangle;

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
 * The selected roi is displayed in the center.
 * If there is not a selected roi, the center of image is displayed in the center.
 */
public class WK_RoiMan_DisplayedInTheCenter implements ExtendedPlugInFilter {
    // const var.
    private static final int FLAGS = DOES_ALL;
    private static final int ZOOM_SCALE = 100;

    // var.
    private ImagePlus impSrc = null;
    private RoiManager roiMan = null;

    /**
     * Helper class to store coordinate sum and count
     */
    private static class CoordinateSum {
        double sumX;
        double sumY;
        int count;

        CoordinateSum(double sumX, double sumY, int count) {
            this.sumX = sumX;
            this.sumY = sumY;
            this.count = count;
        }
    }

    @Override
    public int showDialog(ImagePlus ip, String cmd, PlugInFilterRunner pifr) {
        return FLAGS;
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
            impSrc = imp;
            roiMan = getRoiManager(false, true);

            if(roiMan == null) {
                IJ.error("Failed to get ROI Manager");
                return DONE;
            }

            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip) {
        try {
            int roiCount = roiMan.getCount();
            int[] selectedIndexes = roiMan.getSelectedIndexes();
            boolean hasSelection = (selectedIndexes != null && selectedIndexes.length > 0);

            ImageCanvas canvas = impSrc.getCanvas();
            if(canvas == null) {
                IJ.log("Canvas is not available");
                return;
            }

            int centerX;
            int centerY;

            if(roiCount == 0 || !hasSelection) {
                // No ROI or no selection: center on image or current ROI
                Rectangle roi = ip.getRoi();

                if(roi == null) {
                    centerX = Math.round(ip.getWidth() / 2.0f);
                    centerY = Math.round(ip.getHeight() / 2.0f);
                }
                else {
                    centerX = Math.round(roi.x + roi.width / 2.0f);
                    centerY = Math.round(roi.y + roi.height / 2.0f);
                }
            }
            else {
                // Calculate center of selected ROIs
                double sumX = 0;
                double sumY = 0;
                int totalPoints = 0;

                for(int i = 0; i < selectedIndexes.length; i++) {
                    Roi roi = roiMan.getRoi(selectedIndexes[i]);
                    if(roi != null) {
                        CoordinateSum sum = calculateCoordinateSum(roi);
                        sumX += sum.sumX;
                        sumY += sum.sumY;
                        totalPoints += sum.count;
                    }
                }

                if(totalPoints > 0) {
                    centerX = (int)Math.round(sumX / totalPoints);
                    centerY = (int)Math.round(sumY / totalPoints);
                }
                else {
                    IJ.log("No valid coordinates found in selected ROIs");
                    return;
                }
            }

            int zoomPercent = (int)(canvas.getMagnification() * ZOOM_SCALE);
            setViewCenter(centerX, centerY, zoomPercent);
        }
        catch(Exception e) {
            IJ.log("Error in WK_RoiMan_DisplayedInTheCenter: " + e.getMessage());
        }
    }

    /**
     * Set the view center using macro command
     */
    private void setViewCenter(int centerX, int centerY, int zoomPercent) {
        try {
            String macro = String.format(
                "run(\"Set... \", \"zoom=%d x=%d y=%d\");",
                zoomPercent,
                centerX,
                centerY
            );

            Macro_Runner macroRunner = new Macro_Runner();
            macroRunner.runMacro(macro, "");
        }
        catch(Exception e) {
            IJ.log("Failed to set view center: " + e.getMessage());
        }
    }

    /**
     * Calculate the sum of coordinates and count of points in ROI
     * This avoids storing all points in memory
     */
    private CoordinateSum calculateCoordinateSum(Roi roi) {
        ImageProcessor mask = roi.getMask();
        Rectangle bounds = roi.getBounds();
        double sumX = 0;
        double sumY = 0;
        int count = 0;

        for(int y = 0; y < bounds.height; y++) {
            for(int x = 0; x < bounds.width; x++) {
                if(mask == null || mask.getPixel(x, y) != 0) {
                    sumX += bounds.x + x;
                    sumY += bounds.y + y;
                    count++;
                }
            }
        }

        return new CoordinateSum(sumX, sumY, count);
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
            IJ.log("Failed to get ROI Manager: " + e.getMessage());
            return null;
        }
    }
}