import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.measure.ResultsTable;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import java.awt.AWTEvent;
import java.io.File;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.Features2d;

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
 * Setting a query in FeatureDetection (OpenCV3.3.1).
 * 
 * * Feature detection using FeatureDetector, DescriptorExtractor, DescriptorMatcher
 * * AKAZE, BRISK, ORB
 */
public class OCV_FeatDet_1st_SetQuery implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener
{
    // constant var.
    private final int FLAGS = DOES_RGB;
    private final String[] TYPE_STR_DET = new String[] { "AKAZE", "BRISK", "ORB"};
    private final int[] TYPE_VAL_DET = new int[] { FeatureDetector.AKAZE, FeatureDetector.BRISK, FeatureDetector.ORB };    
    private final int[] TYPE_VAL_EXT = new int[] { DescriptorExtractor.AKAZE, DescriptorExtractor.BRISK, DescriptorExtractor.ORB };
    
    // static var.
    private static int ind_det = 0;
    private static int type_det = FeatureDetector.AKAZE;
    private static int type_ext = DescriptorExtractor.AKAZE;
    private static boolean enDrawKeys = false;

    // var.
    private String fname = "";
    private ImagePlus imp_query = null;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr)
    {
        GenericDialog gd = new GenericDialog(command.trim() + "...");

        gd.addChoice("feature_detector", TYPE_STR_DET, TYPE_STR_DET[ind_det]);
        gd.addCheckbox("enable_draw_keypoints", enDrawKeys);
        gd.addDialogListener(this);

        gd.showDialog();

        if (gd.wasCanceled())
        {
            return DONE;
        }
        else
        {
            return FLAGS;
        }
    }

    @Override
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent awte)
    {
        ind_det = (int)gd.getNextChoiceIndex();
        enDrawKeys = (boolean)gd.getNextBoolean();

        type_det = TYPE_VAL_DET[ind_det];
        type_ext = TYPE_VAL_EXT[ind_det];
        fname = TYPE_STR_DET[ind_det] + ".yaml";
        
        IJ.showStatus("OCV_FeatDet_1st_SetQuery");
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
        
        if (imp == null)
        {
            IJ.noImage();
            return DONE;
        }
        else
        {
            imp_query = imp;
        }
        
        return FLAGS;
    }

    @Override
    public void run(ImageProcessor ip)
    {
        // QueryImage
        int[] arr_query = (int[])imp_query.getChannelProcessor().getPixels();
        int imw_query = imp_query.getWidth();
        int imh_query = imp_query.getHeight();
        Mat mat_query = new Mat(imh_query, imw_query, CvType.CV_8UC3);
        OCV__LoadLibrary.intarray2mat(arr_query, mat_query, imw_query, imh_query);
       
        // KeyPoint of QueryImage
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
        
        MatOfKeyPoint key_query = new MatOfKeyPoint();
        detector.detect(mat_query, key_query);
        
        // Descriptor of QueryImage
        DescriptorExtractor extractor = DescriptorExtractor.create(type_ext);
        Mat desc_query = new Mat();
        extractor.compute(mat_query, key_query, desc_query);
        
        // Set data
        if(OCV__LoadLibrary.QueryMat != null) { OCV__LoadLibrary.QueryMat.release(); }
        if(OCV__LoadLibrary.QueryKeys != null) { OCV__LoadLibrary.QueryKeys.release(); }
        if(OCV__LoadLibrary.QueryDesc != null) { OCV__LoadLibrary.QueryDesc.release(); }
        
        OCV__LoadLibrary.QueryMat = mat_query;
        OCV__LoadLibrary.QueryKeys = key_query;
        OCV__LoadLibrary.QueryDesc = desc_query;
        OCV__LoadLibrary.FeatDetType = TYPE_STR_DET[ind_det];
        
        // Draw key points
        if(enDrawKeys)
        {
            showData(key_query);
            drawKeyPoints(mat_query, key_query);
        }
    }
   
    private void showData(MatOfKeyPoint key_query)
    {
        ResultsTable rt = OCV__LoadLibrary.GetResultsTable(true);
        int num = key_query.rows();
        
        for(int i = 0; i < num; i++)
        {
            double query_x = key_query.get(i, 0)[0];
            double query_y = key_query.get(i, 0)[1];
            double query_size = key_query.get(i, 0)[2];
            double query_angle = key_query.get(i, 0)[3];
            double query_response = key_query.get(i, 0)[4];
            double query_octave = key_query.get(i, 0)[5];
            double query_class_id = key_query.get(i, 0)[6];
            
            rt.incrementCounter();
            rt.addValue("query_x", query_x);
            rt.addValue("query_y", query_y);
            rt.addValue("query_size", query_size);
            rt.addValue("query_angle", query_angle);
            rt.addValue("query_response", query_response);
            rt.addValue("query_octave", query_octave);
            rt.addValue("query_class_id", query_class_id);
        }
        
        rt.show("Results");
    }
    
    private void drawKeyPoints(Mat mat_query, MatOfKeyPoint key_query)
    {
        Mat mat_dst = new Mat();
        Features2d.drawKeypoints(mat_query, key_query, mat_dst);        

        String title_dst = WindowManager.getUniqueName("FeatureDetection_Extract");
        int imw_dst = mat_dst.cols();
        int imh_dst = mat_dst.rows();
        ImagePlus imp_dst = new ImagePlus (title_dst, new ColorProcessor(imw_dst, imh_dst));
        int[] arr_dst = (int[]) imp_dst.getChannelProcessor().getPixels();
        OCV__LoadLibrary.mat2intarray(mat_dst, arr_dst, imw_dst, imh_dst);
        imp_dst.show();
    }
}
