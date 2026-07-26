import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import java.awt.Rectangle;
import java.util.ArrayList;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.geometry.Geometry;

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
 * convexHull.
 */
public class OCV_ConvexHull implements ExtendedPlugInFilter {
    // constant var.
    private static final int BACKGROUND_VALUE = 0;

    // static var.
    private static boolean enCW = true;
    private static boolean enRefData = false;

    // var.
    private String className;
    private int countNPass = 0;
    private Roi roiSrc = null;
    private ResultsTable rt = null;
    private RoiManager roiMan = null;

    @Override
    public void setNPasses(int nPasses) {
        if(nPasses > 0 && enRefData) {
            countNPass = 0;
        }
    }

    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner prf) {
        className = cmd.trim();
        GenericDialog gd = new GenericDialog(className + " ...");
        
        gd.addCheckbox("enable_clockwise", enCW);
        gd.addCheckbox("enable_refresh_data", enRefData);
        
        gd.showDialog();

        if(gd.wasCanceled()) {
            return DONE;
        }
        else {
            enCW = gd.getNextBoolean();
            enRefData = gd.getNextBoolean();
            return DOES_8G;
        }
    }

    @Override
    public void run(ImageProcessor ip) {
        MatOfPoint pts = null;
        MatOfInt hull = null;
        ImageProcessor ipWork = null;

        try {
            byte[] byteArray;
            int w;
            int h;
            int offsetX;
            int offsetY;
            
            if(roiSrc == null) {
                // Process entire image
                byteArray = (byte[])ip.getPixels();
                w = ip.getWidth();
                h = ip.getHeight();
                offsetX = 0;
                offsetY = 0;
            }
            else {
                // Process ROI region
                ipWork = ip.duplicate();
                ipWork.setColor(BACKGROUND_VALUE);
                ipWork.setRoi(roiSrc);
                ipWork.fillOutside(roiSrc);
                
                ImageProcessor ipCrop = ipWork.crop();
                byteArray = (byte[])ipCrop.getPixels();
                w = ipCrop.getWidth();
                h = ipCrop.getHeight();
                Rectangle bounds = roiSrc.getBounds();
                offsetX = bounds.x;
                offsetY = bounds.y;
                
                ipCrop = null;
            }

            ArrayList<Point> lstPt = new ArrayList<Point>();

            for(int y = 0; y < h; y++) {
                for(int x = 0; x < w; x++) {
                    if(byteArray[x + w * y] != BACKGROUND_VALUE) {
                        lstPt.add(new Point(x + offsetX, y + offsetY));
                    }
                }
            }

            if(lstPt.isEmpty()) {
                return;
            }

            pts = new MatOfPoint();
            pts.fromList(lstPt);
            hull = new MatOfInt();
            Geometry.convexHull(pts, hull, enCW);
            
            if(hull.empty()) {
                OCV__LoadLibrary.logError(className, "Convex hull is empty.");
                return;
            }

            rt = OCV__LoadLibrary.GetResultsTable(false);
            roiMan = OCV__LoadLibrary.GetRoiManager(false, true);

            rt.reset();
            
            if(enRefData) {
                roiMan.reset();
            }

            showData(pts, hull);
        }
        catch(Exception e) {
            OCV__LoadLibrary.logError(className, "Convex hull failed (" + e.getMessage() + ")");
        }
        finally {
            if(pts != null) {
                pts.release();
            }
            if(hull != null) {
                hull.release();
            }
            ipWork = null;
        }
    }

    @Override
    public int setup(String arg0, ImagePlus imp) {
        if(!OCV__LoadLibrary.isLoad()) {
            OCV__LoadLibrary.logError("OCV_ConvexHull", "Library is not loaded.");
            return DONE;
        }

        if(imp == null) {
            IJ.noImage();
            return DONE;
        }
        else {
            roiSrc = imp.getRoi();
            countNPass = 0;
            
            return DOES_8G;
        }
    }

    private void showData(MatOfPoint pts, MatOfInt hull) {
        try {
            // set the ResultsTable
            int numHull = (int)hull.size().height;
            float[] xPoints = new float[numHull];
            float[] yPoints = new float[numHull];

            for(int i = 0; i < numHull; i++) {
                int index = (int)hull.get(i, 0)[0];
                xPoints[i] = (float)pts.get(index, 0)[0];
                yPoints[i] = (float)pts.get(index, 0)[1];

                rt.incrementCounter();
                rt.addValue("X", xPoints[i]);
                rt.addValue("Y", yPoints[i]);
            }

            rt.show("Results");

            // set the ROI
            PolygonRoi proi = new PolygonRoi(xPoints, yPoints, Roi.POLYGON);
            proi.setPosition(countNPass + 1);
            countNPass++;

            roiMan.addRoi(proi);
            int numRoiMan = roiMan.getCount();
            roiMan.select(numRoiMan - 1);
        }
        catch(Exception e) {
            OCV__LoadLibrary.logError(className, "Show data failed (" + e.getMessage() + ")");
        }
    }
}