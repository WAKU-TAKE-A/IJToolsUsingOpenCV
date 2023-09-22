import ij.*;
import ij.IJ;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.Line;
import ij.gui.Plot;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.RoiManager;
import ij.process.FloatPolygon;
import ij.process.ImageProcessor;
import java.awt.AWTEvent;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
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
 * Cany.
 */
public class OCUtil_MeasureWidth implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener {
    // constant var.
    private static final int FLAGS = DOES_8G | KEEP_PREVIEW; // 8-bit input image.
    private final String[] SIZE_STR = new String[] { "3", "5", "7" };
    private final int[] SIZE_VAL = new int[] { 3, 5, 7 };
    private final String[] LEFTSIDESCAN_STR = new String[] { "left->right", "center->left" };
    private final String[] RIGHTSIDESCAN_STR = new String[] { "right->left", "center->right" };
    private final String[] ROI_STR = new String[] { "line", "point" };
    // staic var.
    private static double thr1  = 30; // first threshold for the hysteresis procedure.
    private static double thr2  = 40; // second threshold for the hysteresis procedure.
    private static int ind_size = 0; // aperture size for the Sobel operator.
    private static boolean l2grad = false; // L2gradient;
    private static int ind_leftside = 0;
    private static double thr_le = 100;
    private static int ind_rightside = 0;
    private static double thr_ri = 100;
    private static boolean dispCanny = true;
    private static boolean dispProf = true;
    private static boolean dispTable = true;
    private static boolean dispRoi = true;
    private static int ind_roi = 0;
    private static boolean enVertical = false;
    private static ImagePlus impPlot_canny = null;
    private static ImagePlus img_canny = null;
    // var.
    private String className;
    private ImagePlus img;
    private Roi roiImg;
    private String lescan;
    private String riscan;
    private final double[] roi_vec = new double[2];
    private double[] roi_cen = new double[2];
    private double roi_len;
    private RoiManager roiMan = null;
    private String typeRoi;
    private boolean ini_verticalProfile = false;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        className = command.trim();
        GenericDialog gd = new GenericDialog(className + " ...");

        gd.addNumericField("Threshold1", thr1, 4);
        gd.addNumericField("Threshold2", thr2, 4);
        gd.addChoice("ApertureSize", SIZE_STR, SIZE_STR[ind_size]);
        gd.addCheckbox("L2gradient", l2grad);
        gd.addChoice("LeftSideScan", LEFTSIDESCAN_STR, LEFTSIDESCAN_STR[ind_leftside]);
        gd.addNumericField("ThresholdLeft", thr_le, 4);
        gd.addChoice("RightSideScan", RIGHTSIDESCAN_STR, RIGHTSIDESCAN_STR[ind_rightside]);
        gd.addNumericField("ThresholdRight", thr_ri, 4);
        gd.addCheckbox("DisplayCanny", dispCanny);
        gd.addCheckbox("DisplayProfile", dispProf);
        gd.addCheckbox("DisplayTable", dispTable);
        gd.addCheckbox("DisplayRoi", dispRoi);
        gd.addChoice("TypeOfRoi", ROI_STR, ROI_STR[ind_roi]);
        gd.addCheckbox("VerticalProfileWhenRectangle", enVertical);
        gd.addDialogListener(this);

        gd.showDialog();

        if(gd.wasCanceled()) {
            return DONE;
        }
        else {
            return IJ.setupDialog(imp, FLAGS);
        }
    }

    @Override
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent awte) {
        thr1 = (double)gd.getNextNumber();
        thr2 = (double)gd.getNextNumber();
        ind_size = (int)gd.getNextChoiceIndex();
        l2grad = (boolean)gd.getNextBoolean();
        ind_leftside = (int)gd.getNextChoiceIndex();
        thr_le = (double)gd.getNextNumber();
        ind_rightside = (int)gd.getNextChoiceIndex();
        thr_ri = (double)gd.getNextNumber();
        dispCanny =  (boolean)gd.getNextBoolean();
        dispProf =  (boolean)gd.getNextBoolean();
        dispTable =  (boolean)gd.getNextBoolean();
        dispRoi =  (boolean)gd.getNextBoolean();
        ind_roi = (int)gd.getNextChoiceIndex();
        enVertical = (boolean)gd.getNextBoolean();        

        lescan = LEFTSIDESCAN_STR[ind_leftside];
        riscan = RIGHTSIDESCAN_STR[ind_rightside];
        typeRoi = ROI_STR[ind_roi];
        Prefs.verticalProfile = enVertical;
        
        if(Double.isNaN(thr1) || Double.isNaN(thr2)) {
            IJ.showStatus("ERR : NaN");
            return false;
        }

        if(thr1 < 0) {
            IJ.showStatus("'0 <= threshold1' is necessary.");
            return false;
        }

        if(thr2 < 0) {
            IJ.showStatus("'0 <= threshold2' is necessary.");
            return false;
        }

        FloatPolygon fp = roiImg.getFloatPolygon();
        roi_cen = roiImg.getContourCentroid();

        if (roiImg.getType() == Roi.LINE || roiImg.getType() == Roi.FREEROI || (roiImg.getType() == Roi.RECTANGLE && !enVertical)) {
            roi_len = CalculateDistance(
                    fp.xpoints[0], fp.ypoints[0],
                    fp.xpoints[1], fp.ypoints[1]);
            roi_vec[0] = (fp.xpoints[1] - fp.xpoints[0]) / roi_len;
            roi_vec[1] = (fp.ypoints[1] - fp.ypoints[0]) / roi_len;
        } else {
            roi_len = CalculateDistance(
                    fp.xpoints[1], fp.ypoints[1],
                    fp.xpoints[2], fp.ypoints[2]);
            roi_vec[0] = (fp.xpoints[2] - fp.xpoints[1]) / roi_len;
            roi_vec[1] = (fp.ypoints[2] - fp.ypoints[1]) / roi_len;                
        }
        
        IJ.showStatus(className);
        return true;
    }

    @Override
    public void setNPasses(int nPasses) {
        // do nothing
    }

    @Override
    public int setup(String arg, ImagePlus imp) {
        if(!OCV__LoadLibrary.isLoad()) {
            IJ.error("Library is not loaded.");
            return DONE;
        }

        if(imp == null) {
            IJ.noImage();
            return DONE;
        }
        else {
            img = imp;
            roiImg = imp.getRoi();

            if (roiImg == null || (roiImg.getType() != Roi.LINE && roiImg.getType() != Roi.RECTANGLE && roiImg.getType() != Roi.FREEROI)) {
                IJ.error("A line, rectangle, or rotated rectangle ROI is necessary.");
                return DONE;
            }
            
            ini_verticalProfile = Prefs.verticalProfile;

            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip) {
        if (dispRoi) {
            roiMan = OCV__LoadLibrary.GetRoiManager(false, true);

            if (0 < roiMan.getCount()) {
                roiMan.select(roiMan.getCount() - 1);
                roiMan.runCommand(img, "Delete");
            }
            
            roiMan.deselect();
        }
        
        IJ.run(img, "Select All", "");

        if (img_canny == null) {
            img_canny = img.duplicate();
        } else {
            if (ip.getWidth() != img_canny.getWidth() || ip.getHeight() != img_canny.getHeight() || !img_canny.isVisible()) {
                img_canny.close();
                img_canny = img.duplicate();
            } else {
                OCV__LoadLibrary.ArrayCopy(ip, img_canny.getProcessor());
            }
        }
        
        Canny(img_canny, thr1, thr2, SIZE_VAL[ind_size], l2grad);

        img_canny.setRoi(roiImg);
        Plot plot_canny = OCV__LoadLibrary.GetProfilePlot(img_canny);
        float[] xpoints = plot_canny.getXValues();
        float[] ypoints = plot_canny.getYValues();
        int prf_len = xpoints.length;
        int le;
        int ri;

        if(lescan == "left->right"){
            for (le = 0; le < prf_len; le++) {
                if (thr_le <= ypoints[le]) {
                    break;
                }
            }
        }else{
            for (le = prf_len/2; 0 <= le; le--) {
                if (thr_le <= ypoints[le]) {
                    break;
                }
            }
        }

        if(riscan == "right->left"){
            for (ri = prf_len-1; 0 <= ri; ri--) {
                if (thr_ri <= ypoints[ri]) {
                    break;
                }
            }
        }else{
            for (ri = prf_len/2; ri < prf_len; ri++) {
                 if (thr_ri <= ypoints[ri]) {
                    break;
                }
            }
        }

        double center = (double)prf_len / 2;
        double le_from_center = ((double)le - center) * roi_len / (double)prf_len;
        double ri_from_center = ((double)ri - center) * roi_len / (double)prf_len;
        double[] pnt_le = new double[] { (roi_cen[0] + roi_vec[0] * le_from_center), (roi_cen[1] + roi_vec[1] * le_from_center) };
        double[] pnt_ri = new double[] { (roi_cen[0] + roi_vec[0] * ri_from_center), (roi_cen[1] + roi_vec[1] * ri_from_center) };
        double len = CalculateDistance(pnt_le[0], pnt_le[1], pnt_ri[0], pnt_ri[1]);
        
        // Display Canny.
        img_canny.resetRoi();
        
        if (dispCanny) {
            img_canny.show();
        }
        else
        {
            img_canny.close();
        }

        // Display profile.
        if(plot_canny != null && dispProf) {
            if(impPlot_canny == null) {
                impPlot_canny = new ImagePlus(className + " Profile", plot_canny.getProcessor());
            }
            else {
                impPlot_canny.setProcessor(null, plot_canny.getProcessor());
            }

            impPlot_canny.show();
        }
        
        // Display table.
        if (dispTable) {
            ResultsTable tblResults = OCV__LoadLibrary.GetResultsTable(false);

            if(tblResults == null) {
                tblResults = new ResultsTable();
            }

            int nrow = tblResults.size();
            tblResults.setValue("LeftX", nrow, pnt_le[0]);
            tblResults.setValue("LeftY", nrow, pnt_le[1]);
            tblResults.setValue("RightX", nrow, pnt_ri[0]);
            tblResults.setValue("RightY", nrow, pnt_ri[1]);
            tblResults.setValue("Width", nrow, len);

            tblResults.show("Results");
        }

        img.show();
        
        // Add roi.
        if (dispRoi) {
            if (typeRoi == "line")
            {
                Line line = new Line(pnt_le[0], pnt_le[1],pnt_ri[0], pnt_ri[1]);
                roiMan.addRoi(line);
            }
            else
            {
                PointRoi pnts = new PointRoi();
                pnts.addPoint(pnt_le[0], pnt_le[1]);
                pnts.addPoint(pnt_ri[0], pnt_ri[1]);
                roiMan.addRoi(pnts);
            }
            
            roiMan.addRoi(roiImg);
            
            IJ.selectWindow(img.getID());
            roiMan.select(roiMan.getCount() - 1);
        } else {
            IJ.selectWindow(img.getID());
            img.setRoi(roiImg);
        }
        
        Prefs.verticalProfile = ini_verticalProfile;
    }

    public void Canny(ImagePlus imp, double threshold1, double threshold2, int apertureSize, boolean L2gradient) {
        ImageProcessor ip = imp.getProcessor();

        // srcdst
        int imw = ip.getWidth();
        int imh = ip.getHeight();
        byte[] srcdst_bytes = (byte[])ip.getPixels();

        // mat
        Mat src_mat = new Mat(imh, imw, CvType.CV_8UC1);
        Mat dst_mat = new Mat(imh, imw, CvType.CV_8UC1);

        // run
        src_mat.put(0, 0, srcdst_bytes);
        Imgproc.Canny(src_mat, dst_mat, threshold1, threshold2, apertureSize, L2gradient);
        dst_mat.get(0, 0, srcdst_bytes);
    }

    public double CalculateDistance(double x1, double y1, double x2, double y2) {
        double dlt_len_x = x2 - x1;
        double dlt_len_y = y2 - y1;
        double len2 = Math.pow(dlt_len_x, 2) + Math.pow(dlt_len_y, 2);
        return Math.sqrt(len2);
    }
}
