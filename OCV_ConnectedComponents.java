import ij.*;
import ij.gui.GenericDialog;
import ij.plugin.Macro_Runner;
import ij.plugin.filter.*;
import ij.plugin.frame.RoiManager;
import ij.process.*;
import java.awt.Frame;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

/*
 * The MIT License
 *
 * Copyright 2016 WAKU_TAKE_A.
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
 * connectedComponents (OpenCV)
 * @author WAKU_TAKE_A
 * @version 0.9.0.0
 */
public class OCV_ConnectedComponents implements ExtendedPlugInFilter
{
    // const var.
    private static final int FLAGS = DOES_8G;    
    private static final int CONN_4 = 4;
    private static final int CONN_8 = 8;
    private static final int[] TYPE_INT = { CONN_4, CONN_8 };
    private static final String[] TYPE_STR = { "4-connected", "8-connected" };
    
    // static var.
	private static int type_ind = 1;
    private static boolean enOutImg;

    // var.
    private ImagePlus impSrc = null;

    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner pifr)
    {       
        GenericDialog gd = new GenericDialog(cmd.trim() + "...");

        gd.addChoice("connectivity", TYPE_STR, TYPE_STR[type_ind]);
        gd.addCheckbox("enable_output_labeled_image", enOutImg);

        gd.showDialog();

        if (gd.wasCanceled())
        {
            return DONE;
        }
        else
        {
            type_ind = (int)gd.getNextChoiceIndex();
            enOutImg = (boolean)gd.getNextBoolean();

            return FLAGS;
        }
    }

    @Override
    public void setNPasses(int i)
    {
        // do nothing
    }

    @Override
    public int setup(String string, ImagePlus imp)
    {
        if(!OCV__LoadLibrary.isLoad)
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
            impSrc = imp;
            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip)
    {
        // src
        byte[] src_arr = (byte[])ip.getPixels();
        int imw = ip.getWidth();
        int imh = ip.getHeight();
        Mat src_mat = new Mat(imh, imw, CvType.CV_8UC1);
        
        // dst
        String titleDst = WindowManager.getUniqueName(impSrc.getTitle() + "_Connect" + String.valueOf(TYPE_INT[type_ind]));
        ImagePlus impDst = new ImagePlus (titleDst, new ShortProcessor(imw, imh));
        short[] dst_arr = (short[]) impDst.getChannelProcessor().getPixels();
        Mat dst_mat = new Mat(imh, imw, CvType.CV_16U);
        
        // run
        src_mat.put(0, 0, src_arr);
        
        int output_con = Imgproc.connectedComponents(src_mat, dst_mat, TYPE_INT[type_ind], CvType.CV_16U);
        
        dst_mat.get(0, 0, dst_arr);
        
        // show data
        if(0 < output_con - 1)
        {
            showData(dst_arr, imw, imh, output_con - 1);
        }    
        
        // finish
        if(1 < output_con && enOutImg)
        {
            impDst.show();
        }
        else
        {
            impDst.close();
        }
    }
    
    private void showData(short[] dst_arr, int imw, int imh, int num_lab)
    {
        Frame frame = WindowManager.getFrame("ROI Manager");
        RoiManager roiManager;
        Macro_Runner mr = new Macro_Runner();
        
        if (frame==null)
        {
            IJ.run("ROI Manager...");
        }

        frame = WindowManager.getFrame("ROI Manager");
        roiManager = (RoiManager)frame;
        
        roiManager.reset();
        roiManager.runCommand("show none");
        
        mr.runMacro("setBatchMode(true);", "");    
        
        int[] tbl = new int[num_lab + 1];
        int val = 0;
        String type = TYPE_STR[type_ind];
        
        for(int y = 0; y < imh; y++)
        {
            for(int x = 0; x < imw; x++)
            {
                val = (int)dst_arr[x + y * imw];
                
                if(val != 0 && tbl[val] == 0)
                {
                    mr.runMacro("doWand(" + String.valueOf(x) + ", " + String.valueOf(y) + ", 0.0, \"" + type + "\");", "");
                    roiManager.runCommand("add");
                    tbl[val] = 1;
                }
            }
        }
    
        mr.runMacro("setBatchMode(false);", "");
        roiManager.runCommand("show all");
    }

}
