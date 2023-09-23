import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.OvalRoi;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import java.awt.Polygon;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;
import java.util.ArrayList;

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
 * minEnclosingCircle.
 */
public class OCV_MinEnclosingCircle implements ExtendedPlugInFilter {
    // static var.
    private static boolean enRefData = false;

    // var.
    private ImagePlus impSrc = null;
    private ResultsTable rt = null;
    private RoiManager roiMan = null;
    private int countNPass = 0;
    private Roi roiSrc = null;

    /*
     * @see ij.plugin.filter.ExtendedPlugInFilter#setNPasses(int)
     */
    @Override
    public void setNPasses(int arg0) {
        // do nothing
    }

    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner prf) {
        GenericDialog gd = new GenericDialog(cmd.trim() + "...");
        gd.addCheckbox("enable_refresh_data", enRefData);
        gd.showDialog();

        if(gd.wasCanceled()) {
            return DONE;
        }
        else {
            enRefData = (boolean)gd.getNextBoolean();
            return IJ.setupDialog(imp, DOES_8G); // Displays a "Process all images?" dialog
        }
    }

    @Override
    public void run(ImageProcessor ip) {
        int num_slice = ip.getSliceNumber();
        
        byte[] byteArray;
        int w;
        int h;
        int offsetx;
        int offsety;
        
        if (roiSrc == null) {
            byteArray = (byte[])ip.getPixels();
            w = ip.getWidth();
            h = ip.getHeight();
            offsetx = 0;
            offsety = 0;
        }
        else
        {
            ImageProcessor ip_crop = ip.crop();
            byteArray = (byte[])ip_crop.getPixels();
            w = ip_crop.getWidth();
            h = ip_crop.getHeight();
            Polygon pol = roiSrc.getPolygon();
            offsetx = pol.xpoints[0];
            offsety = pol.ypoints[0];
        }


        ArrayList<Point> lstPt = new ArrayList<Point>();
        MatOfPoint2f pts = new MatOfPoint2f();

        for(int y = 0; y < h; y++) {
            for(int x = 0; x < w; x++) {
                if(byteArray[x + w * y] != 0) {
                    lstPt.add(new Point((double)x+(double)offsetx, (double)y+(double)offsety));
                }
            }
        }

        if(lstPt.isEmpty()) {
            return;
        }

        pts.fromList(lstPt);
        float[] radius = new float[1];
        Point center = new Point();
        Imgproc.minEnclosingCircle(pts, center, radius);

        rt = OCV__LoadLibrary.GetResultsTable(false);
        roiMan = OCV__LoadLibrary.GetRoiManager(false, true);

        if(enRefData) {
            rt.reset();
            roiMan.reset();
        }

        showData(center.x, center.y, (double)radius[0], num_slice);
    }

    @Override
    public int setup(String arg0, ImagePlus imp) {
        if(!OCV__LoadLibrary.isLoad()) {
            IJ.error("Library is not loaded.");
            return DONE;
        }

        if(imp == null) {
            IJ.noImage();
            return DONE;
        }
        else {
            impSrc = imp;
            roiSrc = imp.getRoi();
            
            if (roiSrc == null || roiSrc.getType() != Roi.RECTANGLE) {
                roiSrc = null;
            } 
            
            return DOES_8G;
        }
    }

    private void showData(double center_x, double center_y, double radius, int num_slice) {
        // set the ResultsTable
        rt.incrementCounter();
        rt.addValue("CenterX", center_x);
        rt.addValue("CenterY", center_y);
        rt.addValue("R", radius);
        rt.show("Results");

        // set the ROI
        double diameter = (double)(radius * 2);
        impSrc.setSlice(num_slice);
        OvalRoi roi = new OvalRoi((center_x - radius), (center_y - radius), diameter, diameter);
        roi.setPosition(countNPass + 1); // Start from one.
        countNPass++;

        roiMan.addRoi(roi);
        int num_roiMan = roiMan.getCount();
        roiMan.select(num_roiMan - 1);
    }
}

