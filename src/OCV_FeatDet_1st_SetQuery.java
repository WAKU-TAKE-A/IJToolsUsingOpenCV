import ij.IJ;
import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import java.awt.AWTEvent;
import java.io.IOException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
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
 * Setting a query in FeatureDetection.
 *
 * * Feature detection using FeatureDetector, DescriptorExtractor, DescriptorMatcher
 * * AKAZE, BRISK, ORB
 */
public class OCV_FeatDet_1st_SetQuery implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener {
    // constant var.
    private final int FLAGS = NO_IMAGE_REQUIRED;
    private final String[] TYPE_STR_CMD = new String[] { "new_query", "read_query", "remake_query"};
    private final String[] TYPE_STR_DET = new String[] { "AKAZE", "BRISK", "ORB", "SIFT"};
    
    // Command indices
    private final int CMD_NEW = 0;
    private final int CMD_READ = 1;
    private final int CMD_REMAKE = 2;
    
    // UI constants
    private final int STRING_FIELD_WIDTH = 8;
    private final int COLOR_BLACK = 0;
    private final int EMPTY_ROWS = 0;

    // static var.
    private static int ind_cmd = 0;
    private static int ind_det = 0;
    private static String query_name = "";
    private static boolean enDrawKeys = false;
    private static final MyFeatureDetector detector = new MyFeatureDetector();

    // var.
    private String className = "";
    private ImagePlus imp_query = null;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        className = command.trim();
        GenericDialog gd = new GenericDialog(className + "...");

        gd.addChoice("command", TYPE_STR_CMD, TYPE_STR_CMD[ind_cmd]);
        gd.addChoice("feature_detector", TYPE_STR_DET, TYPE_STR_DET[ind_det]);
        gd.addStringField("query_name", query_name, STRING_FIELD_WIDTH);
        gd.addCheckbox("enable_draw_keypoints", enDrawKeys);
        gd.addDialogListener(this);

        gd.showDialog();
        
        if (ind_cmd == CMD_NEW && imp_query == null) {
            IJ.noImage();
            return DONE;
        }
        
        if (ind_cmd == CMD_NEW && OCV__LoadLibrary.isNullOrEmpty(query_name)) {
            IJ.error("query_name is empty.");
            return DONE;
        }

        if (gd.wasCanceled()) {
            return DONE;
        }
        else {
            return FLAGS;
        }
    }

    @Override
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent awte) {
        ind_cmd = (int)gd.getNextChoiceIndex();
        ind_det = (int)gd.getNextChoiceIndex();
        query_name  = (String)gd.getNextString();
        enDrawKeys = (boolean)gd.getNextBoolean();
        
        if (OCV__LoadLibrary.isNullOrEmpty(query_name)) {
            IJ.showStatus("query_name is empty.");
            return false;
        }
        
        try {
            if (ind_cmd == CMD_NEW) {
                if (MyFeatureDetector.exitQueryName(query_name)) {
                    IJ.showStatus("query_name already exists.");
                    return false;                
                }
            } else {
                if (!MyFeatureDetector.exitQueryName(query_name)) {
                    IJ.showStatus("query_name does not exist.");
                    return false;                     
                }
                
                if (!MyFeatureDetector.exitParam(TYPE_STR_DET[ind_det], query_name)) {
                    IJ.showStatus("the feature_detector is incorrect.");
                    return false;                     
                }               
            }
        }
        catch(IOException ex) {
            IJ.showStatus("IOException.");
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

        if (imp != null) {
            imp_query = imp;
        }

        return FLAGS;
    }

    @Override
    public void run(ImageProcessor ip) {
        Mat mat_query = null;
        
        try {
            // 0:new
            // 1:read
            // 2:remake  
            if (ind_cmd == CMD_NEW){
                if(imp_query == null) {
                    IJ.log(className + " error: No image.");
                    return;
                }
                
                // 元の画像を保護するため、作業用にプロセッサを複製する
                ImageProcessor ip_query_working = imp_query.getChannelProcessor().duplicate();
                Roi roi = imp_query.getRoi();
                
                // ROI processing (作業用プロセッサに対して行う)
                if (roi != null) {
                    // Fill outside and crop
                    ip_query_working.setColor(COLOR_BLACK);
                    ip_query_working.fillOutside(roi);
                    ip_query_working.setRoi(roi);
                    ip_query_working = ip_query_working.crop();
                }
                
                int[] arr_query = (int[])ip_query_working.getPixels();
                int imw_query = ip_query_working.getWidth();
                int imh_query = ip_query_working.getHeight();
                mat_query = new Mat(imh_query, imw_query, CvType.CV_8UC3);
                OCV__LoadLibrary.intarray2mat(arr_query, mat_query, imw_query, imh_query);

                try {
                    detector.initialize(TYPE_STR_DET[ind_det], query_name);
                    detector.generateQuery(mat_query);
                } catch(IOException ex) {
                    IJ.log(className + " error: " + ex.getMessage());
                    return;
                }            
            } else if (ind_cmd == CMD_READ){
                try {
                    detector.initialize(TYPE_STR_DET[ind_det], query_name);
                    detector.readQuery();
                } catch(IOException ex) {
                    IJ.log(className + " error: " + ex.getMessage());
                    return;
                }
            } else if (ind_cmd == CMD_REMAKE){
                try {
                    detector.initialize(TYPE_STR_DET[ind_det], query_name);
                    detector.remakeQuery();
                } catch(IOException ex) {
                    IJ.log(className + " error: " + ex.getMessage());
                    return;
                }        
            } else {
                IJ.log(className + " error: Wrong command index.");
                return;
            }

            // post-processing
            if (detector == null) {
                IJ.log(className + " error: Can not create detector.");
                return;
            }

            if (detector.QueryKeyPoints == null || detector.QueryKeyPoints.rows() == EMPTY_ROWS) {
                IJ.log(className + " error: KeyPoint is empty.");
                return;
            }

            try{
                detector.CopyTo(OCV__LoadLibrary.MyQuery);
            } catch(IOException ex) {
                IJ.log(className + " error: " + ex.getMessage());
                return;
            }

            if(enDrawKeys && detector.QueryKeyPoints != null) {
                Mat mat_query_for_draw = null;
                try {
                    detector.showData(detector.QueryKeyPoints);
                    mat_query_for_draw = Imgcodecs.imread(detector.FileQueryImage.toString());
                    MyFeatureDetector.drawKeyPoints(mat_query_for_draw, detector.QueryKeyPoints);
                } finally {
                    if (mat_query_for_draw != null) {
                        mat_query_for_draw.release();
                    }
                }
            }
        } finally {
            // Release resources
            if (mat_query != null) {
                mat_query.release();
            }
        }
    }
}
