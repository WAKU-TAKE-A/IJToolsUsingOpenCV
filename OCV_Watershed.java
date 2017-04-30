import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
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
 * watershed (OpenCV3.1).
 */
public class OCV_Watershed implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener
{
    // constant var.
    private final int FLAGS = DOES_32 | DOES_RGB | KEEP_PREVIEW; // Input 8-bit 3-channel image, and Input/output 32-bit single-channel map.
    
    // static var.
    private static int ind_src = 0;
    private static int ind_msk = 1;
    
    // var.
    private ImagePlus imp_src = null;
    private ImagePlus imp_map = null;
    private int[] lst_wnd;
    private String[] titles_wnd;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr)
    {
        GenericDialog gd = new GenericDialog(command.trim() + "...");

        gd.addChoice("src", titles_wnd, titles_wnd[ind_src]);
        gd.addChoice("mask", titles_wnd, titles_wnd[ind_msk]);
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
        
        if(ind_src == ind_msk) { IJ.showStatus("The same image can not be selected."); return false; }

        imp_src = WindowManager.getImage(lst_wnd[ind_src]);
        imp_map = WindowManager.getImage(lst_wnd[ind_msk]);

        if(imp_src.getBitDepth() != 24 || imp_map.getBitDepth() != 32) { IJ.showStatus("The image should be RGB, and the mask should be 32bit."); return false; }
        if(imp_src.getWidth() != imp_map.getWidth() || imp_src.getHeight() != imp_map.getHeight()) { IJ.showStatus("The size of src should be same as the size of mask."); return false; }
        
        IJ.showStatus("OCV_Watershed");
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

            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip)
    {
        // src (RGB)
        int[] arr_src_rgb = (int[])imp_src.getChannelProcessor().getPixels();
        int imw_src = imp_src.getWidth();
        int imh_src = imp_src.getHeight();
        Mat mat_src_rgb = new Mat(imh_src, imw_src, CvType.CV_8UC3);

        // map (32bit)
        float[] arr_map_32f = (float[])imp_map.getChannelProcessor().getPixels();
        int imw_map = imp_map.getWidth();
        int imh_map = imp_map.getHeight();
        Mat mat_map_32f = new Mat(imh_map, imw_map, CvType.CV_32FC1);
        Mat mat_map_32s = new Mat(imh_map, imw_map, CvType.CV_32SC1);
        
        // run
        OCV__LoadLibrary.intarray2mat(arr_src_rgb, mat_src_rgb, imw_src, imh_src);
        mat_map_32f.put(0, 0, arr_map_32f);
        mat_map_32f.convertTo(mat_map_32s, CvType.CV_32SC1);
        
        Imgproc.watershed(mat_src_rgb, mat_map_32s);
        
        mat_map_32s.convertTo(mat_map_32f, CvType.CV_32FC1);
        mat_map_32f.get(0, 0, arr_map_32f);         
    }
}
