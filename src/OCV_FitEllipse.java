import ij.IJ;
import ij.ImagePlus;
import ij.gui.EllipseRoi;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import java.awt.Rectangle;
import java.util.ArrayList;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.imgproc.Imgproc;

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
 * fitEllipse.
 */
public class OCV_FitEllipse implements ExtendedPlugInFilter {
    // constant var.
    private static final int BACKGROUND_VALUE = 0;
    private static final int MIN_POINTS_FOR_ELLIPSE = 5;

    // static var.
    private static boolean enRefData = false;

    // var.
    private ImagePlus impSrc = null;
    private ResultsTable rt = null;
    private RoiManager roiMan = null;
    private int countNPass = 0;
    private Roi roiSrc = null;
    private String className = null;

    @Override
    public void setNPasses(int nPasses) {
        if(nPasses > 0 && enRefData) {
            countNPass = 0;
        }
    }

    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner prf) {
        className = cmd.trim();
        GenericDialog gd = new GenericDialog(className + "...");
        gd.addCheckbox("enable_refresh_data", enRefData);
        gd.showDialog();

        if(gd.wasCanceled()) {
            return DONE;
        }
        else {
            enRefData = gd.getNextBoolean();
            return IJ.setupDialog(imp, DOES_8G);
        }
    }

    @Override
    public void run(ImageProcessor ip) {
        MatOfPoint2f pts = null;
        ImageProcessor ipWork = null;

        try {
            int numSlice = ip.getSliceNumber();
            
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

            if(lstPt.size() < MIN_POINTS_FOR_ELLIPSE) {
                OCV__LoadLibrary.logError(className, "Fit ellipse requires at least " + MIN_POINTS_FOR_ELLIPSE + " points (found " + lstPt.size() + ")");
                return;
            }

            pts = new MatOfPoint2f();
            pts.fromList(lstPt);
            RotatedRect rect = Imgproc.fitEllipse(pts);

            rt = OCV__LoadLibrary.GetResultsTable(false);
            roiMan = OCV__LoadLibrary.GetRoiManager(false, true);

            if(enRefData && countNPass == 0) {
                rt.reset();
                roiMan.reset();
            }

            showData(rect, numSlice);
        }
        catch(Exception e) {
            OCV__LoadLibrary.logError(className, "Fit ellipse failed (" + e.getMessage() + ")");
        }
        finally {
            if(pts != null) {
                pts.release();
            }
            ipWork = null;
        }
    }

    @Override
    public int setup(String arg0, ImagePlus imp) {
        if(!OCV__LoadLibrary.isLoad()) {
            OCV__LoadLibrary.logError("OCV_FitEllipse", "Library is not loaded.");
            return DONE;
        }

        if(imp == null) {
            IJ.noImage();
            return DONE;
        }
        else {
            impSrc = imp;
            roiSrc = imp.getRoi();
            countNPass = 0;
            
            return DOES_8G;
        }
    }

    private void showData(RotatedRect rect, int numSlice) {
        try {
            // set the ResultsTable
            rt.incrementCounter();
            rt.addValue("CenterX", rect.center.x);
            rt.addValue("CenterY", rect.center.y);
            rt.addValue("Width", rect.size.width);
            rt.addValue("Height", rect.size.height);
            rt.addValue("Angle", rect.angle);
            rt.show("Results");

            // set the ROI Manager
            double[] xPoints = new double[2];
            double[] yPoints = new double[2];
            double cx = rect.center.x;
            double cy = rect.center.y;
            double w = rect.size.width;
            double h = rect.size.height;
            double rad = rect.angle * Math.PI / 180;
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            double ratio = w / h;

            xPoints[0] = (float)((0) * cos - (h / 2.0) * sin + cx);
            yPoints[0] = (float)((0) * sin + (h / 2.0) * cos + cy);
            xPoints[1] = (float)((0) * cos - (-h / 2.0) * sin + cx);
            yPoints[1] = (float)((0) * sin + (-h / 2.0) * cos + cy);

            impSrc.setSlice(numSlice);
            EllipseRoi eroi = new EllipseRoi(xPoints[0], yPoints[0], xPoints[1], yPoints[1], ratio);
            eroi.setPosition(countNPass + 1);
            countNPass++;

            roiMan.addRoi(eroi);
            int numRoiMan = roiMan.getCount();
            roiMan.select(numRoiMan - 1);
        }
        catch(Exception e) {
            OCV__LoadLibrary.logError(className, "Show data failed (" + e.getMessage() + ")");
        }
    }
}