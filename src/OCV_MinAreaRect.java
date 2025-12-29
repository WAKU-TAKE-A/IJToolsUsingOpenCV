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
 * minAreaRect.
 */
public class OCV_MinAreaRect implements ExtendedPlugInFilter {
    // constant var.
    private static final int BACKGROUND_VALUE = 0;

    // static var.
    private static boolean enRefData = false;

    // var.
    private String className;
    private ImagePlus impSrc = null;
    private ResultsTable rt = null;
    private RoiManager roiMan = null;
    private int countNPass = 0;
    private Roi roiSrc = null;

    @Override
    public void setNPasses(int arg0) {
        // do nothing
    }

    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner prf) {
        className = cmd.trim();
        GenericDialog gd = new GenericDialog(className + " ...");
        gd.addCheckbox("enable_refresh_data", enRefData);
        gd.showDialog();

        if(gd.wasCanceled()) {
            return DONE;
        }
        else {
            enRefData = (boolean)gd.getNextBoolean();
            countNPass = 0; // リセット
            return IJ.setupDialog(imp, DOES_8G);
        }
    }

    @Override
    public void run(ImageProcessor ip) {
        int numSlice = ip.getSliceNumber();
        ImageProcessor ipWork = null;
        MatOfPoint2f pts = null;

        try {
            byte[] byteArray;
            int w;
            int h;
            int offsetX;
            int offsetY;

            if (roiSrc == null) {
                // 画像全体を処理
                byteArray = (byte[])ip.getPixels();
                w = ip.getWidth();
                h = ip.getHeight();
                offsetX = 0;
                offsetY = 0;
            }
            else {
                // ROI領域を処理
                ipWork = ip.duplicate();
                ipWork.setColor(BACKGROUND_VALUE);
                ipWork.setRoi(roiSrc);
                ipWork.fillOutside(roiSrc);

                ImageProcessor ipCrop = ipWork.crop();
                byteArray = (byte[])ipCrop.getPixels();
                w = ipCrop.getWidth();
                h = ipCrop.getHeight();

                Rectangle rect = roiSrc.getBounds();
                offsetX = rect.x;
                offsetY = rect.y;

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

            pts = new MatOfPoint2f();
            pts.fromList(lstPt);
            RotatedRect rotatedRect = Imgproc.minAreaRect(pts);

            rt = OCV__LoadLibrary.GetResultsTable(false);
            roiMan = OCV__LoadLibrary.GetRoiManager(false, true);

            if(enRefData && countNPass == 0) {
                rt.reset();
                roiMan.reset();
            }

            showData(rotatedRect, numSlice);
        }
        catch(Exception e) {
            IJ.log(className + " error: " + e.getMessage());
        }
        finally {
            if(pts != null) pts.release();
            ipWork = null;
        }
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
            return DOES_8G;
        }
    }

    private void showData(RotatedRect rect, int numSlice) {
        // set the ResultsTable
        rt.incrementCounter();
        rt.addValue("CenterX", rect.center.x);
        rt.addValue("CenterY", rect.center.y);
        rt.addValue("Width", rect.size.width);
        rt.addValue("Height", rect.size.height);
        rt.addValue("Angle", rect.angle);
        rt.show("Results");

        // set the ROI
        float[] xPoints = new float[4];
        float[] yPoints = new float[4];
        double cx = rect.center.x;
        double cy = rect.center.y;
        double w = rect.size.width;
        double h = rect.size.height;
        double rad = rect.angle * Math.PI / 180;
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        xPoints[0] = (float)((w / 2.0) * cos - (h / 2.0) * sin + cx);
        yPoints[0] = (float)((w / 2.0) * sin + (h / 2.0) * cos + cy);
        xPoints[1] = (float)((-w / 2.0) * cos - (h / 2.0) * sin + cx);
        yPoints[1] = (float)((-w / 2.0) * sin + (h / 2.0) * cos + cy);
        xPoints[2] = (float)((-w / 2.0) * cos - (-h / 2.0) * sin + cx);
        yPoints[2] = (float)((-w / 2.0) * sin + (-h / 2.0) * cos + cy);
        xPoints[3] = (float)((w / 2.0) * cos - (-h / 2.0) * sin + cx);
        yPoints[3] = (float)((w / 2.0) * sin + (-h / 2.0) * cos + cy);

        impSrc.setSlice(numSlice);
        PolygonRoi proi = new PolygonRoi(xPoints, yPoints, Roi.POLYGON);
        proi.setPosition(countNPass + 1);
        countNPass++;

        roiMan.addRoi(proi);
        int numRoiMan = roiMan.getCount();
        roiMan.select(numRoiMan - 1);
    }
}