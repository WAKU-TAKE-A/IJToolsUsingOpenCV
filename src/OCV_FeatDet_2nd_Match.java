import ij.IJ;
import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import java.awt.AWTEvent;
import java.util.List;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.imgcodecs.Imgcodecs;

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
 * Matching in FeatureDetection.
 *
 * * Feature detection using FeatureDetector, DescriptorExtractor, DescriptorMatcher
 * * AKAZE, BRISK, ORB
 */
public class OCV_FeatDet_2nd_Match implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener {
    // constant var.
    private static final int FLAGS = DOES_RGB;
    private static final String[] TYPE_STR_DET = new String[] { "AKAZE", "BRISK", "ORB", "SIFT"};
    
    // static var.
    private static String[] type_str_match;
    private static int[] type_val_match;
    private static int ind_det = -1;
    private static int ind_match = 0;
    private static double ratioThreshold = 0.75;
    private static double ransacThreshold = 3.0;
    private static int minMatchCount = 10;
    private static double minInlierRatio = 0.3;
    private static boolean bestMatchOnly = true;
    private static int maxDetections = 10;
    private static double overlapThreshold = 0.5;    
    private static boolean enShowTable = true;
    private static boolean enAddRoi = true;
    private static boolean resetResult = true;

    // var.
    private String className;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        MyFeatureDetector myQuery = OCV__LoadLibrary.MyQuery;
        
        if ("SIFT".equals(myQuery.DetectorType)) {
            type_str_match = new String[] { "BRUTEFORCE", "FLANNBASED" };
            type_val_match = new int[] { DescriptorMatcher.BRUTEFORCE, DescriptorMatcher.FLANNBASED  };            
        } else {
            type_str_match = new String[] { "BRUTEFORCE_HAMMING", "BRUTEFORCE_HAMMINGLUT" };
            type_val_match = new int[] { DescriptorMatcher.BRUTEFORCE_HAMMING, DescriptorMatcher.BRUTEFORCE_HAMMINGLUT  };
        }
        
        className = command.trim();
        GenericDialog gd = new GenericDialog(className + " ...");

        gd.addMessage("query name: " + myQuery.QueryName);
        gd.addMessage("detector type: " + myQuery.DetectorType);
        gd.addChoice("descriptor_matcher", type_str_match, type_str_match[ind_match]);
        gd.addNumericField("ratio_threshold", ratioThreshold, 2);
        gd.addNumericField("ransac_threshold", ransacThreshold, 2);
        gd.addNumericField("min_match_count", minMatchCount, 0);
        gd.addNumericField("min_inlier_ratio", minInlierRatio, 2);  
        gd.addCheckbox("best_match_only", bestMatchOnly);
        gd.addNumericField("max_detections", maxDetections, 0);
        gd.addNumericField("overlap_threshold", overlapThreshold, 2);
        gd.addCheckbox("enabled_show_table", enShowTable);
        gd.addCheckbox("enabled_add_roi", enAddRoi);
        gd.addCheckbox("reset_result", resetResult);
        gd.addDialogListener(this);

        gd.showDialog();

        if (gd.wasCanceled()) {
            return DONE;
        }
        else {
            return IJ.setupDialog(imp, FLAGS);
        }
    }

    @Override
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent awte) {
        ind_match = (int)gd.getNextChoiceIndex();
        ratioThreshold = (double)gd.getNextNumber();
        ransacThreshold = (double)gd.getNextNumber();
        minMatchCount = (int)gd.getNextNumber();
        minInlierRatio = (double)gd.getNextNumber();
        bestMatchOnly = (boolean)gd.getNextBoolean();
        maxDetections = (int)gd.getNextNumber();
        overlapThreshold = (double)gd.getNextNumber();
        enShowTable = (boolean)gd.getNextBoolean();
        enAddRoi = (boolean)gd.getNextBoolean();
        resetResult = (boolean)gd.getNextBoolean(); 

        if (Double.isNaN(ratioThreshold) ||
            Double.isNaN(ransacThreshold) ||
            Double.isNaN(minInlierRatio) ||
            Double.isNaN(overlapThreshold)) {
            
            IJ.showStatus("ERR : NaN");
            return false;
        }

        if (minMatchCount <= 0) {
            IJ.showStatus("'0 < minMatchCount' is necessary.");
            return false;
        }

        if (maxDetections <= 0) {
            IJ.showStatus("'0 < maxDetections' is necessary.");
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
        if (!OCV__LoadLibrary.isLoad()) {
            IJ.error("Library is not loaded.");
            return DONE;
        }

        if (OCV__LoadLibrary.isNullOrEmpty(OCV__LoadLibrary.MyQuery.QueryName) || 
            OCV__LoadLibrary.isNullOrEmpty(OCV__LoadLibrary.MyQuery.DetectorType) || 
            OCV__LoadLibrary.MyQuery.QueryKeyPoints.rows() == 0) {
            
            IJ.error("Query is empty.");
            return DONE;
        }

        if(imp == null) {
            IJ.noImage();
            return DONE;
        }

        for(int i = 0; i < TYPE_STR_DET.length; i++) {
            if(OCV__LoadLibrary.MyQuery.DetectorType.equals(TYPE_STR_DET[i])) {
                ind_det = i;
                break;
            }
        }

        if(ind_det == -1) {
            IJ.error("Unknown error.");
            return DONE;
        }

        return FLAGS;
    }

    @Override
    public void run(ImageProcessor ip) {
        Mat mat_train = null;
        MatOfKeyPoint train_key = null;
        Mat train_desc = null;
        Mat mat_query = null;
        DescriptorMatcher dm = null;
        
        try {
            // train image
            int[] arr_train = (int[])ip.getPixels();
            int imw_train = ip.getWidth();
            int imh_train = ip.getHeight();
            mat_train = new Mat(imh_train, imw_train, CvType.CV_8UC3);
            OCV__LoadLibrary.intarray2mat(arr_train, mat_train, imw_train, imh_train);

            // calc KeyPoints and Descriptors of the train 
            MyFeatureDetector myQuery = OCV__LoadLibrary.MyQuery;
            train_key = new MatOfKeyPoint();
            train_desc = new Mat();
            myQuery.calc_KeyPoints_Descriptors(mat_train, train_key, train_desc);
            
            if(train_key.rows() == 0 || train_desc.rows() == 0) {
                IJ.log(className + " error: Result is empty.");
                return;
            }
            
            // query image
            mat_query = Imgcodecs.imread(myQuery.FileQueryImage.toString());          
            int imw_query = mat_query.cols();
            int imh_query = mat_query.rows();
            
            // Match
            dm = DescriptorMatcher.create(type_val_match[ind_match]);
            MyFeatureMatcher matcher = new MyFeatureMatcher();
            matcher.setMaxDetections(maxDetections);
            matcher.setMinInlierRatio(minInlierRatio);
            matcher.setMinMatchCount(minMatchCount);
            matcher.setOverlapThreshold(overlapThreshold);
            matcher.setRansacThreshold(ransacThreshold);
            matcher.setRatioThreshold(ratioThreshold);
            
            List<MyFeatureMatcher.DetectionResult> results = matcher.detect(
                    myQuery.QueryKeyPoints,
                    myQuery.QueryDescriptors,
                    imw_query,
                    imh_query,
                    train_key,
                    train_desc,
                    dm,
                    bestMatchOnly);
            
            // post-processing 
            if(enShowTable) {
                matcher.showData(results, resetResult);
            }

            if(enAddRoi) {                
                matcher.addRoiManager(results, resetResult, false);
            }
        }
        catch(Exception ex) {
            IJ.log("Can not calculation. ( " +  ex.getMessage() + " )"); // Not suitable, but to prevent "Unknown error".
        }
        finally {
            if(mat_train != null) mat_train.release();
            if(train_key != null) train_key.release();
            if(train_desc != null) train_desc.release();
            if(mat_query != null) mat_query.release();
        }
    }
}