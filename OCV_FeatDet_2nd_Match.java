import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.RoiManager;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import java.awt.AWTEvent;
import java.io.File;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.Size;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.Features2d;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.MatOfPoint2f;
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
 * Matching in FeatureDetection (OpenCV3.3.1).
 *
 * * Feature detection using FeatureDetector, DescriptorExtractor, DescriptorMatcher
 * * AKAZE, BRISK, ORB
 */
public class OCV_FeatDet_2nd_Match implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener
{
    // constant var.
    private final int FLAGS = DOES_RGB;
    private final String[] TYPE_STR_DET = new String[] { "AKAZE", "BRISK", "ORB"};
    private final int[] TYPE_VAL_DET = new int[] { FeatureDetector.AKAZE, FeatureDetector.BRISK, FeatureDetector.ORB };
    private final int[] TYPE_VAL_EXT = new int[] { DescriptorExtractor.AKAZE, DescriptorExtractor.BRISK, DescriptorExtractor.ORB };
    private final String[] TYPE_STR_MATCH = new String[] { "BRUTEFORCE_HAMMING", "BRUTEFORCE_HAMMINGLUT" };
    private final int[] TYPE_VAL_MATCH = new int[] { DescriptorMatcher.BRUTEFORCE_HAMMING, DescriptorMatcher.BRUTEFORCE_HAMMINGLUT  };

    // static var.
    private static int ind_det = -1;
    private static int ind_match = 0;
    private static int type_det = -1;
    private static int type_ext = -1;
    private static double max_dist = 100;
    private static boolean enDrawMatches = true;
    private static boolean enDetectQuery = true;
    private static double ransacReprojThreshold = 3;

    // var.
    private String fname = "";
    private int countNPass = 0;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr)
    {
        GenericDialog gd = new GenericDialog(command.trim() + "...");

        gd.addMessage("Type of feature detector is " + OCV__LoadLibrary.FeatDetType + ".");
        gd.addChoice("descriptor_matcher", TYPE_STR_MATCH, TYPE_STR_MATCH[ind_match]);
        gd.addNumericField("max_distance", max_dist, 2);
        gd.addCheckbox("enable_draw_matches", enDrawMatches);
        gd.addCheckbox("enable_detect_query", enDetectQuery);
        gd.addNumericField("RANSAC_Reproj_Threshold", ransacReprojThreshold, 2);
        gd.addDialogListener(this);

        gd.showDialog();

        if (gd.wasCanceled())
        {
            return DONE;
        }
        else
        {
            return IJ.setupDialog(imp, FLAGS);
        }
    }

    @Override
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent awte)
    {
        ind_match = (int)gd.getNextChoiceIndex();
        max_dist = (double)gd.getNextNumber();
        enDrawMatches = (boolean)gd.getNextBoolean();
        enDetectQuery = (boolean)gd.getNextBoolean();
        ransacReprojThreshold = (double)gd.getNextNumber();

        type_det = TYPE_VAL_DET[ind_det];
        type_ext = TYPE_VAL_EXT[ind_det];
        fname = TYPE_STR_DET[ind_det] + ".yaml";
        
        if(Double.isNaN(max_dist)  || Double.isNaN(ransacReprojThreshold)) { IJ.showStatus("ERR : NaN"); return false; }        
        if(max_dist <= 0) { IJ.showStatus("'0 < max_dist' is necessary."); return false; }
        if(ransacReprojThreshold <= 0) { IJ.showStatus("'0 < max_dist' is necessary."); return false; }

        IJ.showStatus("OCV_FeatDet_2nd_Match");
        return true;
    }

    @Override
    public void setNPasses(int nPasses)
    {
        // do nothing
    }

    @Override
    public int setup(String arg, ImagePlus imp)
    {
        if(!OCV__LoadLibrary.isLoad())
        {
            IJ.error("Library is not loaded.");
            return DONE;
        }

        if(OCV__LoadLibrary.QueryMat == null || OCV__LoadLibrary.QueryKeys == null || OCV__LoadLibrary.QueryDesc == null || OCV__LoadLibrary.FeatDetType == null)
        {
            IJ.error("No query.");
            return DONE;
        }

        if (imp == null)
        {
            IJ.noImage();
            return DONE;
        }

        for(int i = 0; i < TYPE_STR_DET.length; i++)
        {
            if(OCV__LoadLibrary.FeatDetType.equals(TYPE_STR_DET[i]))
            {
                ind_det = i;
                break;
            }
        }

        if(ind_det == -1)
        {
             IJ.error("Unknown error.");
            return DONE;
        }

        return FLAGS;
    }

    @Override
    public void run(ImageProcessor ip)
    {
        // TrainImage
        int[] arr_train = (int[])ip.getPixels();
        int imw_train = ip.getWidth();
        int imh_train = ip.getHeight();
        Mat mat_train = new Mat(imh_train, imw_train, CvType.CV_8UC3);
        OCV__LoadLibrary.intarray2mat(arr_train, mat_train, imw_train, imh_train);

        // KeyPoint of TrainImage
        FeatureDetector detector = FeatureDetector.create(type_det);
        File file = new File(fname);

        if(file.exists())
        {
            detector.read(fname);
        }
        else
        {
            detector.write(fname);
        }
        
        MatOfKeyPoint key_train = new MatOfKeyPoint();
        detector.detect(mat_train, key_train);

        // Descriptor of TrainImage
        DescriptorExtractor extractor = DescriptorExtractor.create(type_ext);
        Mat desc_train = new Mat();
        extractor.compute(mat_train, key_train, desc_train);

        // Match
        DescriptorMatcher matcher = DescriptorMatcher.create(TYPE_VAL_MATCH[ind_match]);
        MatOfDMatch matchQt = new MatOfDMatch();
        MatOfDMatch matchTq = new MatOfDMatch();
        matcher.match(OCV__LoadLibrary.QueryDesc, desc_train, matchQt);
        matcher.match(desc_train, OCV__LoadLibrary.QueryDesc, matchTq);

        // Cross check
        // [0]:queryIdx, [1]trainIdx, [2]imgIdx, [3]distance
        int num_matchQt = matchQt.rows();
        int num_matchTq = matchTq.rows();
        int num_max = num_matchQt < num_matchTq ? num_matchTq : num_matchQt;
        MatOfDMatch cross_match = new MatOfDMatch();
        MatOfPoint2f pnts_query = new MatOfPoint2f();
        MatOfPoint2f pnts_train = new MatOfPoint2f();
        boolean[] chk = new boolean[num_max];

        for(int i = 0; i < num_matchTq; i++)
        {
            int tq_trainIdx = (int)getElementOfDMatch(matchTq, i)[1];

            for(int di = 0; di < num_matchQt; di++)
            {
                int qt_queryIdx = (int)getElementOfDMatch(matchQt, di)[0];
                int qt_trainIdx = (int)getElementOfDMatch(matchQt, di)[1];
                int qt_distance = (int)getElementOfDMatch(matchQt, di)[3];

                if(!chk[qt_queryIdx] && qt_distance <= max_dist && qt_queryIdx == tq_trainIdx)
                {
                    chk[qt_queryIdx] = true;
                    cross_match.push_back(matchQt.row(di));
                    pnts_query.push_back(getPointOfKeyPoint(OCV__LoadLibrary.QueryKeys, qt_queryIdx));
                    pnts_train.push_back(getPointOfKeyPoint(key_train, qt_trainIdx));
                    break;
                }
            }
        }

        // Output result
        Mat mskOfRansac = new Mat();
        
         if(enDetectQuery)
        {
            drawDetectedCorner(OCV__LoadLibrary.QueryMat, pnts_query, pnts_train, mskOfRansac);
        }

        if(enDrawMatches)
        {
            showData(OCV__LoadLibrary.QueryKeys, key_train, cross_match, mskOfRansac);
            drawMatches(OCV__LoadLibrary.QueryMat, OCV__LoadLibrary.QueryKeys, mat_train, key_train, cross_match);
        }
    }

    private float[] getElementOfDMatch(MatOfDMatch src, int row)
    {
        float[] dst = new float[4];
        src.get(row, 0, dst);
        return dst;
    }
    
    private MatOfPoint2f getPointOfKeyPoint(MatOfKeyPoint src, int row)
    {
        double[] buf = src.get(row, 0);
        return new MatOfPoint2f(new Point(buf[0], buf[1]));
    }

    private void showData(
            MatOfKeyPoint key_query,
            MatOfKeyPoint key_train,
            MatOfDMatch dmatch,
            Mat mskOfRansac)
    {
        int num = dmatch.rows();
        boolean existMsk = 0 < mskOfRansac.rows();
        
        if(num == 0)
        {
            return;
        }
        
        ResultsTable dst_rt = OCV__LoadLibrary.GetResultsTable(true);
        
        for(int i = 0; i < num; i++)
        {
            float[] ele_match = getElementOfDMatch(dmatch, i);
            int queryidx = (int)ele_match[0];
            int trainidx = (int)ele_match[1];
            float distance = ele_match[3];

            double query_x = key_query.get(queryidx, 0)[0];
            double query_y = key_query.get(queryidx, 0)[1];
            double query_size = key_query.get(queryidx, 0)[2];
            double query_angle = key_query.get(queryidx, 0)[3];
            double query_response = key_query.get(queryidx, 0)[4];
            double query_octave = key_query.get(queryidx, 0)[5];
            double query_class_id = key_query.get(queryidx, 0)[6];
            double train_x = key_train.get(trainidx, 0)[0];
            double train_y = key_train.get(trainidx, 0)[1];
            double train_size = key_train.get(trainidx, 0)[2];
            double train_angle = key_train.get(trainidx, 0)[3];
            double train_response = key_train.get(trainidx, 0)[4];
            double train_octave = key_train.get(trainidx, 0)[5];
            double train_class_id = key_train.get(trainidx, 0)[6];
            double ransac = existMsk ? mskOfRansac.get(i, 0)[0] : 1;

            dst_rt.incrementCounter();
            dst_rt.addValue("index_query", queryidx);
            dst_rt.addValue("query_x", query_x);
            dst_rt.addValue("query_y", query_y);
            dst_rt.addValue("query_size", query_size);
            dst_rt.addValue("query_angle", query_angle);
            dst_rt.addValue("query_response", query_response);
            dst_rt.addValue("query_octave", query_octave);
            dst_rt.addValue("query_class_id", query_class_id);
            dst_rt.addValue("index_train", trainidx);
            dst_rt.addValue("train_x", train_x);
            dst_rt.addValue("train_y", train_y);
            dst_rt.addValue("train_size", train_size);
            dst_rt.addValue("train_angle", train_angle);
            dst_rt.addValue("train_response", train_response);
            dst_rt.addValue("train_octave", train_octave);
            dst_rt.addValue("train_class_id", train_class_id);
            dst_rt.addValue("distance", distance);
            dst_rt.addValue("RANSAC", ransac);
        }
        
        dst_rt.show("Results");
    }
    private void drawMatches(
            Mat mat_query,
            MatOfKeyPoint key_query,
            Mat mat_train,
            MatOfKeyPoint key_train,
            MatOfDMatch dmatch)
    {
        Mat mat_dst = new Mat();
        Features2d.drawMatches(mat_query, key_query, mat_train, key_train, dmatch, mat_dst);

        String title_dst = WindowManager.getUniqueName("FeatureDetection_Match");
        int imw_dst = mat_dst.cols();
        int imh_dst = mat_dst.rows();
        ImagePlus imp_dst = new ImagePlus (title_dst, new ColorProcessor(imw_dst, imh_dst));
        int[] arr_dst = (int[]) imp_dst.getChannelProcessor().getPixels();
        OCV__LoadLibrary.mat2intarray(mat_dst, arr_dst, imw_dst, imh_dst);
        imp_dst.show();
    }

    private void drawDetectedCorner(
            Mat mat_query,
            MatOfPoint2f pnts_query,
            MatOfPoint2f pnts_train,
            Mat mskOfRansac)
    {
        Size size_query = mat_query.size();
        int num = pnts_query.rows();

        if(num < 4)
        {
            return;
        }

        Point[] corner_query = new Point[4];
        corner_query[0] = new Point(0, 0);
        corner_query[1] = new Point(size_query.width, 0);
        corner_query[2] = new Point(size_query.width, size_query.height);
        corner_query[3] = new Point(0, size_query.height);
        MatOfPoint2f corner_query_mat = new MatOfPoint2f(corner_query);
        MatOfPoint2f corner_detected_mat = new MatOfPoint2f();
        
        Mat hg = Calib3d.findHomography(pnts_query, pnts_train, Calib3d.RANSAC, ransacReprojThreshold, mskOfRansac, 2000, 0.995); // "maxIters = 2000" and "confidence = 0.995" is default value.
        Core.perspectiveTransform(corner_query_mat, corner_detected_mat, hg);
        
        RoiManager roiMan = OCV__LoadLibrary.GetRoiManager(false, true);

        float[] pnts_x = {
            (float)corner_detected_mat.get(0, 0)[0],
            (float)corner_detected_mat.get(1, 0)[0],
            (float)corner_detected_mat.get(2, 0)[0],
            (float)corner_detected_mat.get(3, 0)[0],
            (float)corner_detected_mat.get(0, 0)[0],
        };
        
        float[] pnts_y = {
            (float)corner_detected_mat.get(0, 0)[1],
            (float)corner_detected_mat.get(1, 0)[1],
            (float)corner_detected_mat.get(2, 0)[1],
            (float)corner_detected_mat.get(3, 0)[1],
            (float)corner_detected_mat.get(0, 0)[1],
        };
        
        PolygonRoi roi = new PolygonRoi(pnts_x, pnts_y, Roi.POLYLINE);
        roi.setPosition(countNPass + 1); // Start from one.
        countNPass++;
        
        roiMan.addRoi(roi);
        int num_roiMan = roiMan.getCount();
        roiMan.select(num_roiMan - 1);
    }
}
