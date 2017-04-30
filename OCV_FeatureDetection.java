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
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
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
 * Feature Detection (OpenCV3.1).
 * 
 * * Feature detection using FeatureDetector, DescriptorExtractor, DescriptorMatcher
 * * AKAZE, BRISK, ORB
 */
public class OCV_FeatureDetection implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener
{
    // constant var.
    private final int FLAGS = DOES_RGB;
    private final String[] TYPE_STR_DET = new String[] { "AKAZE", "BRISK", "ORB"};
    private final int[] TYPE_VAL_DET = new int[] { FeatureDetector.AKAZE, FeatureDetector.BRISK, FeatureDetector.ORB };    
    private final int[] TYPE_VAL_EXT = new int[] { DescriptorExtractor.AKAZE, DescriptorExtractor.BRISK, DescriptorExtractor.ORB };
    private final String[] TYPE_STR_MATCH = new String[] { "BRUTEFORCE", "BRUTEFORCE_HAMMING", "BRUTEFORCE_HAMMINGLUT", "BRUTEFORCE_L1", "BRUTEFORCE_SL2" };
    private final int[] TYPE_VAL_MATCH = new int[] { DescriptorMatcher.BRUTEFORCE, DescriptorMatcher.BRUTEFORCE_HAMMING, DescriptorMatcher.BRUTEFORCE_HAMMINGLUT, DescriptorMatcher.BRUTEFORCE_L1, DescriptorMatcher.BRUTEFORCE_SL2 };
    
    // static var.
    private static int ind_query = 0;
    private static int ind_train = 1;
    private static int ind_det = 0;
    private static int ind_match = 0;
    private static int type_det = FeatureDetector.AKAZE;
    private static int type_ext = DescriptorExtractor.AKAZE;
    private static double max_distance = 0.1;
    private static boolean enDrawMatches = true;    

    // var.
    private String fname = "";
    private ImagePlus imp_query = null;
    private ImagePlus imp_train = null;
    private int[] lst_wid = null;
    private String[] titles = null;
    private FeatureDetector detector = FeatureDetector.create(FeatureDetector.AKAZE);

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr)
    {
        GenericDialog gd = new GenericDialog(command.trim() + "...");

        gd.addChoice("QueryImage", titles, titles[ind_query]);
        gd.addChoice("TrainImage", titles, titles[ind_train]);
        gd.addChoice("FeatureDetector", TYPE_STR_DET, TYPE_STR_DET[ind_det]);
        gd.addChoice("DescriptorMatcher", TYPE_STR_MATCH, TYPE_STR_MATCH[ind_match]);
        gd.addNumericField("MaxDistance", max_distance, 4);
        gd.addCheckbox("DrawMatches", enDrawMatches);
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
        ind_query = (int)gd.getNextChoiceIndex();
        ind_train = (int)gd.getNextChoiceIndex();
        ind_det = (int)gd.getNextChoiceIndex();
        ind_match = (int)gd.getNextChoiceIndex();
        max_distance = (double)gd.getNextNumber();
        enDrawMatches = (boolean)gd.getNextBoolean();
        
        if(Double.isNaN(max_distance)) { IJ.showStatus("ERR : NaN"); return false; }
        if(max_distance < 0) { IJ.showStatus("'0 <= MaxDistance' is necessary."); return false; }
        if(ind_train == ind_query) { IJ.showStatus("The same image can not be selected."); return false; }

        imp_query = WindowManager.getImage(lst_wid[ind_query]);
        imp_train = WindowManager.getImage(lst_wid[ind_train]);

        if(imp_query.getBitDepth() != 24 || imp_train.getBitDepth() != 24) { IJ.showStatus("The both images should be RGB."); return false; }

        type_det = TYPE_VAL_DET[ind_det];
        type_ext = TYPE_VAL_EXT[ind_det];
        detector = FeatureDetector.create(type_det);

        fname = TYPE_STR_DET[ind_det] + ".yaml";
        File file = new File(fname);

        if(file.exists())
        {
            detector.read(fname);
        }
        else
        {
            detector.write(fname);
        }
        
        IJ.showStatus("OCV_FeatureDetection");
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
            lst_wid = WindowManager.getIDList();

            if (lst_wid==null || lst_wid.length < 2)
            {
                IJ.error("At least more than 2 images are needed.");
                return DONE;
            }

            titles = new String[lst_wid.length];

            for (int i=0; i < lst_wid.length; i++)
            {
                ImagePlus imp2 = WindowManager.getImage(lst_wid[i]);
                titles[i] = imp2 != null ? imp2.getTitle() : "";
            }

            return FLAGS;
        }
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

        // TrainImage
        int[] arr_train = (int[])imp_train.getChannelProcessor().getPixels();
        int imw_train = imp_train.getWidth();
        int imh_train = imp_train.getHeight();
        Mat mat_train = new Mat(imh_train, imw_train, CvType.CV_8UC3);
        OCV__LoadLibrary.intarray2mat(arr_train, mat_train, imw_train, imh_train);
        
        // KeyPoint
        MatOfKeyPoint key_query = new MatOfKeyPoint();
        MatOfKeyPoint key_train = new MatOfKeyPoint();
        detector.detect(mat_query, key_query);
        detector.detect(mat_train, key_train);
        
        // Descriptor
        DescriptorExtractor extractor = DescriptorExtractor.create(type_ext);
        Mat desc_query = new Mat();
        Mat desc_train = new Mat();
        extractor.compute(mat_query, key_query, desc_query);
        extractor.compute(mat_train, key_train, desc_train);
        
        // Matcher
        DescriptorMatcher matcher = DescriptorMatcher.create(TYPE_VAL_MATCH[ind_match]);
        MatOfDMatch dmatch = new MatOfDMatch();
        matcher.match(desc_query, desc_train, dmatch);
        
        dmatch = showData(key_query, key_train, dmatch);        
        
        // Output
        if(enDrawMatches)
        {
            Mat mat_dst = new Mat();
            Features2d.drawMatches(mat_query, key_query, mat_train, key_train, dmatch, mat_dst);

            String title_dst = WindowManager.getUniqueName("FeatureDetection");
            int imw_dst = mat_dst.cols();
            int imh_dst = mat_dst.rows();
            ImagePlus imp_dst = new ImagePlus (title_dst, new ColorProcessor(imw_dst, imh_dst));
            int[] arr_dst = (int[]) imp_dst.getChannelProcessor().getPixels();
            OCV__LoadLibrary.mat2intarray(mat_dst, arr_dst, imw_dst, imh_dst);
            imp_dst.show();
        }
    }
   
    private MatOfDMatch showData(MatOfKeyPoint key_query, MatOfKeyPoint key_train, MatOfDMatch dmatch)
    {
        MatOfDMatch output = new MatOfDMatch();
        int num = dmatch.rows();
        float[] ele_dmatch = new float[4];
        
        ResultsTable rt = OCV__LoadLibrary.GetResultsTable(true);
        
        for(int i = 0; i < num; i++)
        {
            dmatch.get(i, 0, ele_dmatch);
            
            if(ele_dmatch[3] <= max_distance)
            {
                output.push_back(dmatch.row(i));

                int queryidx = (int)ele_dmatch[0];
                int trainidx = (int)ele_dmatch[1];
                float distance = ele_dmatch[3];
                
                double x_query = key_query.get(queryidx, 0)[0];
                double y_query = key_query.get(queryidx, 0)[1];
                double x_train = key_train.get(trainidx, 0)[0];
                double y_train = key_train.get(trainidx, 0)[1];
                
                rt.incrementCounter();
                rt.addValue("x_query", x_query);
                rt.addValue("y_query", y_query);
                rt.addValue("x_train", x_train);
                rt.addValue("y_train", y_train);
                rt.addValue("distance", distance);
                rt.show("Results");
            }
        }
        
        return output;
    }
}
