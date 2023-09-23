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
import java.awt.Polygon;
import java.util.ArrayList;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
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
 * convexHull.
 */
public class OCV_ConvexHull implements ExtendedPlugInFilter {
    // static var.
    private static boolean enCW = true;

    // var.
    private int countNPass = 0;
    private Roi roiSrc = null;

    @Override
    public void setNPasses(int arg0) {
        // do nothing
    }

    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner prf) {
        GenericDialog gd = new GenericDialog(cmd.trim() + "...");
        gd.addCheckbox("enable_clockwise", enCW);
        gd.showDialog();

        if(gd.wasCanceled()) {
            return DONE;
        }
        else {
            enCW = (boolean)gd.getNextBoolean();
            return DOES_8G;
        }
    }

    @Override
    public void run(ImageProcessor ip) {
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
        MatOfPoint pts = new MatOfPoint();

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
        MatOfInt hull = new MatOfInt();
        Imgproc.convexHull(pts, hull, enCW);
        showData(pts, hull);
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
            roiSrc = imp.getRoi();
            
            if (roiSrc == null || roiSrc.getType() != Roi.RECTANGLE) {
                roiSrc = null;
            }
            
            return DOES_8G;
        }
    }

    private void showData(MatOfPoint pts, MatOfInt hull) {
        // set the ResultsTable
        ResultsTable rt = OCV__LoadLibrary.GetResultsTable(true);

        int num_hull = (int)hull.size().height;
        float[] xPoints = new float[num_hull];
        float[] yPoints = new float[num_hull];

        for(int i = 0; i < num_hull ; i++) {
            int index = (int)hull.get(i, 0)[0];
            xPoints[i] = (float)pts.get(index, 0)[0];
            yPoints[i] = (float)pts.get(index, 0)[1];

            rt.incrementCounter();
            rt.addValue("X", xPoints[i]);
            rt.addValue("Y", yPoints[i]);
        }

        rt.show("Results");

        // set the ROI
        RoiManager roiMan = OCV__LoadLibrary.GetRoiManager(true, true);
        PolygonRoi proi = new PolygonRoi(xPoints, yPoints, Roi.POLYGON);
        proi.setPosition(countNPass + 1); // Start from one.
        countNPass++;

        roiMan.addRoi(proi);
        int num_roiMan = roiMan.getCount();
        roiMan.select(num_roiMan - 1);
    }
}
