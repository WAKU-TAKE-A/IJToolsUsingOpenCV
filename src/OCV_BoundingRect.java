import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import java.awt.Rectangle;
import java.util.ArrayList;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
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
 * boundingRect.
 */
public class OCV_BoundingRect implements ExtendedPlugInFilter {
    // constant var.
    private static final int BACKGROUND_VALUE = 0;

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
        MatOfPoint pts = null;
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

            pts = new MatOfPoint();
            pts.fromList(lstPt);
            Rect rect = Geometry.boundingRect(pts);

            rt = OCV__LoadLibrary.GetResultsTable(false);
            roiMan = OCV__LoadLibrary.GetRoiManager(false, true);

            if(enRefData && countNPass == 0) {
                rt.reset();
                roiMan.reset();
            }

            showData(rect, numSlice);
        }
        catch(Exception e) {
            OCV__LoadLibrary.logError(className, "Bounding rect failed (" + e.getMessage() + ")");
        }
        finally {
            if(pts != null) {
                pts.release();
            }
            // Set to null to encourage garbage collection
            ipWork = null;
        }
    }

    @Override
    public int setup(String arg0, ImagePlus imp) {
        if(!OCV__LoadLibrary.isLoad()) {
            OCV__LoadLibrary.logError("OCV_BoundingRect", "Library is not loaded.");
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

    private void showData(Rect rect, int numSlice) {
        // set the ResultsTable
        rt.incrementCounter();
        rt.addValue("BX", rect.x);
        rt.addValue("BY", rect.y);
        rt.addValue("Width", rect.width);
        rt.addValue("Height", rect.height);
        rt.show("Results");

        // set the ROI Manager
        impSrc.setSlice(numSlice);
        Roi roi = new Roi(rect.x, rect.y, rect.width, rect.height);
        roi.setPosition(countNPass + 1);
        countNPass++;

        roiMan.addRoi(roi);
        int numRoiMan = roiMan.getCount();
        roiMan.select(numRoiMan - 1);
    }
}