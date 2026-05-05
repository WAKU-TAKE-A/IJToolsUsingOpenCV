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
import java.awt.Polygon;
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
public class WK_RoiMan_LinearFitting implements ExtendedPlugInFilter {
    // const var.
    private static final int FLAGS = DOES_ALL;

    // static var.
    private static int num_run = 0;
    private static boolean enRefData = false;
    private static boolean enAddRoi = true;

    // var.
    private String className;
    private ImagePlus impSrc = null;
    private RoiManager roiMan = null;
    private int[] selectedIndexes = null;
    private ResultsTable rsTbl = null;

    @Override
    public int showDialog(ImagePlus ip, String cmd, PlugInFilterRunner pifr) {
        className = cmd.trim();
        GenericDialog gd = new GenericDialog(className + " ...");

        gd.addCheckbox("enable_refresh_data", enRefData);
        gd.addCheckbox("enable_add_roi", enAddRoi);

        gd.showDialog();

        if(gd.wasCanceled()) {
            return DONE;
        }
        else {
            enRefData = (boolean)gd.getNextBoolean();
            enAddRoi = (boolean)gd.getNextBoolean();

            if(enRefData) {
                rsTbl.reset();
                num_run = 0;
            }

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
            impSrc = imp;
            rsTbl = getResultsTable(false);
            roiMan = getRoiManager(false, true);
            
            if(roiMan.getCount() == 0) {
                OCV__LoadLibrary.logError("WK_RoiMan_LinearFitting", "ROI is vacant.");
                return DONE;
            }

            selectedIndexes = roiMan.getSelectedIndexes();

            if(selectedIndexes == null || selectedIndexes.length == 0) {
                selectedIndexes = new int[] { 0 };
            }

            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip) {
        ArrayList<Point> lstPt = new ArrayList<Point>();

        for(int i = 0; i < selectedIndexes.length; i++) {
            Roi roi = roiMan.getRoi(selectedIndexes[i]);
            getCoordinates(roi, lstPt);
        }

        int num_all = lstPt.size();

        if (num_all < 2) {
            OCV__LoadLibrary.logError(className, "At least 2 points are required.");
            return;
        }

        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        double ini_x = lstPt.get(0).x;
        boolean all_eq_x = true;

        for(int i = 0; i < num_all; i++) {
            double x = lstPt.get(i).x;
            double y = lstPt.get(i).y;

            if(x != ini_x) all_eq_x = false;

            sx += x;
            sy += y;
            sxx += x * x;
            sxy += x * y;
        }

        double a = (double)num_all * sxx - sx * sx;
        double slope = 0;
        double intercept = 0;

        if (Math.abs(a) < 1e-12 || all_eq_x) {
            slope = Double.POSITIVE_INFINITY;
            intercept = Double.NaN;
        } else {
            slope = ((double)num_all * sxy - sx * sy) / a;
            intercept = (sxx * sy - sxy * sx) / a;
        }

        rsTbl.incrementCounter();
        rsTbl.addValue("Slope", slope);
        rsTbl.addValue("Intercept", intercept);
        rsTbl.show("Results");

        if(enAddRoi) {
            double x1 = 0, y1 = 0, x2 = 0, y2 = 0;
            double w = (double)impSrc.getWidth();
            double h = (double)impSrc.getHeight();

            if (Double.isInfinite(slope)) {
                x1 = ini_x; y1 = 0;
                x2 = ini_x; y2 = h;
            } else {
                x1 = 0;
                y1 = intercept;
                x2 = w;
                y2 = slope * w + intercept;
            }

            Line ln = new Line(x1, y1, x2, y2);
            int currentCount = roiMan.getCount();
            roiMan.addRoi(ln);
            roiMan.rename(currentCount, "Fit_Line_" + num_run);
            num_run++;
        }
    }

    private ResultsTable getResultsTable(boolean enReset) {
        ResultsTable rt = ResultsTable.getResultsTable();
        if(rt == null || rt.getCounter() == 0) {
            rt = new ResultsTable();
        }
        if(enReset) {
            rt.reset();
        }
        rt.show("Results");
        return rt;
    }

    private RoiManager getRoiManager(boolean enReset, boolean enShowNone) {
        Frame frame = WindowManager.getFrame("ROI Manager");
        RoiManager rm = (frame == null) ? new RoiManager() : (RoiManager)frame;
        if(frame == null) rm.setVisible(true);
        if(enReset) rm.reset();
        if(enShowNone) rm.runCommand("Show None");
        return rm;
    }

    private void getCoordinates(Roi roi, ArrayList<Point> lstPt) {
        if (roi.getType() >= Roi.POLYLINE && roi.getType() <= Roi.FREELINE || roi.getType() == Roi.LINE) {
            // Use Polygon for better compatibility with older ImageJ versions
            Polygon p = roi.getPolygon();
            for (int i = 0; i < p.npoints; i++) {
                lstPt.add(new Point(p.xpoints[i], p.ypoints[i]));
            }
        } else {
            ImageProcessor mask = roi.getMask();
            Rectangle r = roi.getBounds();
            for(int y = 0; y < r.height; y++) {
                for(int x = 0; x < r.width; x++) {
                    if(mask == null || mask.getPixel(x, y) != 0) {
                        lstPt.add(new Point(r.x + x, r.y + y));
                    }
                }
            }
        }
    }
}