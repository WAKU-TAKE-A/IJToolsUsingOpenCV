import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import java.awt.AWTEvent;
import java.awt.Rectangle;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.Rect;

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
 * grabCut (OpenCV3.1).
 */
public class OCV_GrabCut implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener
{
    // constant var.
    private final int FLAGS = DOES_8G | DOES_RGB | KEEP_PREVIEW; // Input 8-bit 3-channel image, and Input/output 8-bit single-channel mask.
    private final String[] TYPE_STR = new String[] { "GC_INIT_WITH_RECT", "GC_INIT_WITH_MASK" };
    private final int[] TYPE_VAL = new int[] { Imgproc.GC_INIT_WITH_RECT, Imgproc.GC_INIT_WITH_MASK };

    // static var.
    private static int ind_src = 0;
    private static int ind_msk = 1;
    private static int ind_type = 0;
    private static int iter = 3;
    private static boolean enFgd = true;   
        
    // var.
    private ImagePlus imp_src = null;
    private ImagePlus imp_msk = null;
    private Rect rect = null;
    private int[] lst_wnd;
    private String[] titles_wnd;    

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr)
    {
        GenericDialog gd = new GenericDialog(command.trim() + "...");

        gd.addChoice("src", titles_wnd, titles_wnd[ind_src]);
        gd.addChoice("mask", titles_wnd, titles_wnd[ind_msk]);
        gd.addNumericField("iterCount", iter, 0);
        gd.addChoice("mode", TYPE_STR, TYPE_STR[ind_type]);
        gd.addCheckbox("enable_foreground_is_255", enFgd);
        gd.addHelp(OCV__LoadLibrary.URL_HELP);
        gd.addPreviewCheckbox(pfr);
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
        ind_src = (int)gd.getNextChoiceIndex();
        ind_msk = (int)gd.getNextChoiceIndex();
        iter = (int)gd.getNextNumber();
        ind_type = (int)gd.getNextChoiceIndex();
        enFgd = (boolean)gd.getNextBoolean();   
        
        if(ind_src == ind_msk) { IJ.showStatus("ERR : The same image can not be selected."); return false; }

        imp_src = WindowManager.getImage(lst_wnd[ind_src]);
        imp_msk = WindowManager.getImage(lst_wnd[ind_msk]);

        if(imp_src.getBitDepth() != 24 || imp_msk.getBitDepth() != 8) { IJ.showStatus("The image should be RGB, and the mask should be 8bit gray."); return false; }
        if(imp_src.getWidth() != imp_msk.getWidth() || imp_src.getHeight() != imp_msk.getHeight()) { IJ.showStatus("The size of src should be same as the size of mask."); return false; }
        
        IJ.showStatus("OCV_GrabCut");
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
            // get the windows
            lst_wnd = WindowManager.getIDList();

            if (lst_wnd==null || lst_wnd.length < 2)
            {
                IJ.error("At least more than 2 images are needed.");
                return DONE;
            }

            titles_wnd = new String[lst_wnd.length];

            for (int i=0; i < lst_wnd.length; i++)
            {
                ImagePlus imp2 = WindowManager.getImage(lst_wnd[i]);
                titles_wnd[i] = imp2 != null ? imp2.getTitle() : "";
            }
            
            // get the ROI
            Rectangle rect_java;

            if(imp.getRoi() != null)
            {
                rect_java = imp.getRoi().getBounds();
            }
            else
            {
                rect_java = new Rectangle(1, 1, imp.getWidth() - 2, imp.getHeight() - 2);
            }
            
            rect = new Rect(rect_java.x , rect_java.y, rect_java.width, rect_java.height);

            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip)
    {
        // src (RGB)
        int[] src_arr = (int[])imp_src.getChannelProcessor().getPixels();
        int imw_src = imp_src.getWidth();
        int imh_src = imp_src.getHeight();
        Mat mat_src = new Mat(imh_src, imw_src, CvType.CV_8UC3);
        OCV__LoadLibrary.intarray2mat(src_arr, mat_src, imw_src, imh_src);

        // tmp (Gray)
        byte[] msk_arr = (byte[])imp_msk.getChannelProcessor().getPixels();
        int imw_msk = imp_msk.getWidth();
        int imh_msk = imp_msk.getHeight();
        int numpix_msk = imw_msk * imh_msk;

        // output
        Mat mat_msk = new Mat(imh_msk, imw_msk, CvType.CV_8UC1);
        Mat bgdModel = new Mat();
        Mat fgdModel = new Mat();
        
        // run      
        mat_msk.put(0, 0, msk_arr);
        Imgproc.grabCut(mat_src, mat_msk, rect, bgdModel, fgdModel, iter, TYPE_VAL[ind_type]);        
        mat_msk.get(0, 0, msk_arr);        
        
        if(enFgd)
        {
            for(int i = 0; i < numpix_msk; i++)
            {
                if(msk_arr[i] == Imgproc.GC_FGD || msk_arr[i] == Imgproc.GC_PR_FGD)
                {
                    msk_arr[i] = (byte)255;
                }
                else
                {
                    msk_arr[i] = (byte)0;
                }
            }
        }  
    }
}
