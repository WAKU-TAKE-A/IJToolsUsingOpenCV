import ij.*;
import ij.IJ;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.PlugInFilterRunner;
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
    // staic var.
    private static double thr1  = 0; // first threshold for the hysteresis procedure.
    private static double thr2  = 0; // second threshold for the hysteresis procedure.
    private static int ind_size = 0; // aperture size for the Sobel operator.
    private static boolean l2grad = false; // L2gradient;
    private static int ind_leftside = 0;
    private static int ind_rightside = 0;
    private static double plot_threshold = 100;
    private static boolean onlyresult = false;
    private static ImagePlus impPlot_canny = null;
    
    // var.
    private String className;
    private ImagePlus img;
    private Roi roiImg;
    private String lescan;
    private String riscan; 

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        className = command.trim();
        GenericDialog gd = new GenericDialog(className + " ...");

        gd.addNumericField("threshold1", thr1, 4);
        gd.addNumericField("threshold2", thr2, 4);
        gd.addChoice("apertureSize", SIZE_STR, SIZE_STR[ind_size]);
        gd.addCheckbox("L2gradient", l2grad);
        gd.addChoice("LeftSideScan", LEFTSIDESCAN_STR, LEFTSIDESCAN_STR[ind_leftside]);
        gd.addChoice("RightSideScan", RIGHTSIDESCAN_STR, RIGHTSIDESCAN_STR[ind_rightside]);
        gd.addNumericField("PlotThreshold", plot_threshold, 4);
        gd.addCheckbox("DisplayOnlyResult", onlyresult);
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
        ind_rightside = (int)gd.getNextChoiceIndex();
        plot_threshold = (double)gd.getNextNumber(); 
        onlyresult =  (boolean)gd.getNextBoolean();
        
        lescan = LEFTSIDESCAN_STR[ind_leftside];
        riscan = RIGHTSIDESCAN_STR[ind_rightside];                
                
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
            
            if (roiImg == null || (roiImg.getType() != Roi.LINE && roiImg.getType() != Roi.RECTANGLE) && roiImg.getType() != Roi.FREEROI) {
                return DONE;
            }
            
            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip) {
        IJ.run(img, "Select All", "");
        
        ImagePlus img_canny = img.duplicate();        
        Canny(img_canny, thr1, thr2, SIZE_VAL[ind_size], l2grad);
        
        img_canny.setRoi(roiImg);
        Plot plot_canny = OCV__LoadLibrary.GetProfilePlot(img_canny);
        float[] xpoints = plot_canny.getXValues();
        float[] ypoints = plot_canny.getYValues();
        int length = xpoints.length;
        int le;
        int ri;        
        
        if(lescan == "left->right"){
            le = 0;
            while(ypoints[le] <= plot_threshold && le < length - 1){
                le++;
            }
        }else{
            le = length / 2;
            while(ypoints[le] <= plot_threshold && 0 < le){
                le--;
            }
        }
        
        if(riscan == "right->left"){
            ri = length-1;
            while(ypoints[ri] <= plot_threshold && 0 < ri){
                ri--;
            }
        }else{
            ri = length/2;
            while(ypoints[ri] <= plot_threshold && ri  <length - 1){
                ri++;
            }
        }
        
        ResultsTable tblResults = OCV__LoadLibrary.GetResultsTable(false);
        
        if(tblResults == null) {
            tblResults = new ResultsTable();
        }
        
        int nrow = tblResults.size();
        tblResults.setValue("Left", nrow, le);
        tblResults.setValue("Right", nrow, ri);
        tblResults.setValue("Width", nrow, ri-le);
        
        img.setRoi(roiImg);
        img_canny.show();
        tblResults.show("Results");
        
        if(plot_canny != null) {
            if(impPlot_canny == null) {
                impPlot_canny = new ImagePlus(className + " Profile", plot_canny.getProcessor());
            }
            else {
                impPlot_canny.setProcessor(null, plot_canny.getProcessor());
            }

            impPlot_canny.show();
        }
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
}
