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
 * The selected roi is displayed in the center.
 * If there is not a selected roi, the center of image is displayed in the center.
 */
public class WK_RoiMan_DisplayedInTheCenter implements ExtendedPlugInFilter {
    // const var.
    private static final int FLAGS = DOES_ALL;

    // var.
    private ImagePlus impSrc = null;
    private RoiManager roiMan = null;

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
            // get the ImagePlus
            impSrc = imp;

            // get the ROI Manager
            roiMan = getRoiManager(false, true);

            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip) {
        int num_roi = roiMan.getCount();
        int[] selectedIndexes = roiMan.getSelectedIndexes();
        int num_slctd = selectedIndexes == null ? 0 : selectedIndexes.length;
        Macro_Runner mr = new Macro_Runner();
        ImageCanvas ic = impSrc.getCanvas();
        int cx;
        int cy;
        int zm;

        if(num_roi == 0 || num_slctd == 0) {
            Rectangle roi = ip.getRoi();

            if(roi == null) {
                cx = (int)((double)ip.getWidth() / 2 + 0.5);
                cy = (int)((double)ip.getHeight() / 2 + 0.5);
                zm = (int)(ic.getMagnification() * 100);

                mr.runMacro("run(\"Set... \", \"zoom=" + Double.toString(zm) + " x=" + Integer.toString(cx) + " y=" + Integer.toString(cy) + "\");", "");
            }
            else {
                cx = (int)((double)roi.getX() + (double)roi.getWidth() / 2 + 0.5);
                cy = (int)((double)roi.getY() + (double)roi.getHeight() / 2 + 0.5);
                zm = (int)(ic.getMagnification() * 100);

                mr.runMacro("run(\"Set... \", \"zoom=" + Double.toString(zm) + " x=" + Integer.toString(cx) + " y=" + Integer.toString(cy) + "\");", "");
            }
        }
        else {
            int num_all = 0;
            double sx = 0;
            double sy = 0;
            ArrayList<Point> lstPt = new ArrayList<Point>();

            for(int i = 0; i < num_slctd; i++) {
                Roi roi = roiMan.getRoi(selectedIndexes[i]);
                getCoordinates(roi, lstPt);
            }

            num_all = lstPt.size();

            for(int i = 0; i < num_all; i++) {
                double x = lstPt.get(i).x;
                double y = lstPt.get(i).y;

                sx += x;
                sy += y;
            }

            cx = (int)(sx / (double)num_all + 0.5);
            cy = (int)(sy / (double)num_all + 0.5);
            zm = (int)(ic.getMagnification() * 100);

            mr.runMacro("run(\"Set... \", \"zoom=" + Double.toString(zm) + " x=" + Integer.toString(cx) + " y=" + Integer.toString(cy) + "\");", "");
        }
    }

    /**
     * get the RoiManager or create a new RoiManager
     * @param enReset reset or not
     * @param enShowNone show none or not
     * @return RoiManager
     */
    private RoiManager getRoiManager(boolean enReset, boolean enShowNone) {
        Frame frame = WindowManager.getFrame("ROI Manager");
        RoiManager rm = null;

        if(frame == null) {
            rm = new RoiManager();
            rm.setVisible(true);
        }
        else {
            rm = (RoiManager)frame;
        }

        if(enReset) {
            rm.reset();
        }

        if(enShowNone) {
            rm.runCommand("Show None");
        }

        return rm;
    }

    /**
     * get the coordinates of the roi(ref:XYCoordinates.saveSelectionCoordinates())
     * @param roi
     * @param lstPt
     */
    private void getCoordinates(Roi roi, ArrayList<Point> lstPt) {
        ImageProcessor mask = roi.getMask();
        Rectangle r = roi.getBounds();
        int pos_x = 0;
        int pos_y = 0;

        for(int y = 0; y < r.height; y++) {
            for(int x = 0; x < r.width; x++) {
                if(mask == null || mask.getPixel(x, y) != 0) {
                    pos_x = r.x + x;
                    pos_y = r.y + y;
                    lstPt.add(new Point(pos_x, pos_y));
                }
            }
        }
    }
}
