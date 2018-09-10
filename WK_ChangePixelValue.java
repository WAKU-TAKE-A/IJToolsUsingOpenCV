import ij.*;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.process.*;
import ij.plugin.filter.*;
import java.awt.AWTEvent;
import java.awt.Rectangle;

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
 * Change the pixel values.
 */
public class WK_ChangePixelValue implements ExtendedPlugInFilter, DialogListener
{
    // constant var.
    private final int FLAGS = DOES_8G | DOES_16 | DOES_32 | CONVERT_TO_FLOAT | DOES_STACKS | PARALLELIZE_STACKS | KEEP_PREVIEW;
    private static final float USHORT_MAX = 65535;
    private static final float UBYTE_MAX = 255;
    private static final String INNER = "inner";
    private static final String OUTER = "outer";
    private static final String[] BYNARY_TYPE = { INNER, OUTER };

    // static var.
    private static float lower = 0;
    private static float upper = 0;
    private static String type = INNER;
    private static float valTrue = 255;
    private static float valFalse = 0;

    // var.
    private float valMax = 0;
    private int bitDepth = 0;

    @Override
    public int showDialog(ImagePlus ip, String command, PlugInFilterRunner pifr)
    {
        lower = 0 < ip.getProcessor().getMinThreshold() ? (int)(ip.getProcessor().getMinThreshold()) : lower;
        upper = 0 < ip.getProcessor().getMaxThreshold() ? (int)(ip.getProcessor().getMaxThreshold()) : upper;
        type = type == null ? INNER : type;

        double min_val = 0;
        double max_val = 0;
        
        if(bitDepth == 8)
        {
            min_val = 0;
            max_val = UBYTE_MAX;
        }
        else if(bitDepth == 16)
        {
            min_val = 0;
            max_val = USHORT_MAX;   
        }
        else
        {
            ImageStatistics stat =  ip.getStatistics();
            min_val = stat.min - 1;
            max_val = stat.max + 1;
        }
        
        GenericDialog gd = new GenericDialog(command.trim() + "...");

        gd.addSlider("lower", min_val, max_val, lower);
        gd.addSlider("upper", min_val, max_val, upper);
        gd.addChoice("range_of_true", BYNARY_TYPE, type);
        gd.addNumericField("value_of_true", valTrue, 4);
        gd.addNumericField("value_of_false", valFalse, 4);
        gd.addPreviewCheckbox(pifr);
        gd.addDialogListener(this);

        gd.showDialog();

        if (gd.wasCanceled())
        {
            return DONE;
        }
        else
        {
            return IJ.setupDialog(ip, FLAGS);
        }
    }
    
    @Override
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent awte)
    {
        lower = (float)gd.getNextNumber();
        upper = (float)gd.getNextNumber();
        
        if(upper < lower) { IJ.showStatus("'lower <= upper' is necessary."); return false; }

        if(bitDepth == 8 || bitDepth == 16)
        {
            lower = checkValue(lower, 0, valMax);
            upper = checkValue(upper, 0, valMax);      
        }
        
        type = (String)BYNARY_TYPE[(int)gd.getNextChoiceIndex()];

        valTrue = (float)gd.getNextNumber();
        valFalse = (float)gd.getNextNumber();
        
        if(bitDepth == 8 || bitDepth == 16)
        {        
            valTrue = checkValue(valTrue, 0, valMax);
            valFalse = checkValue(valFalse, 0, valMax);
        }
        
        IJ.showStatus("WK_ChangePixelValue");
        return true;
    }
    
    @Override
    public void setNPasses(int i)
    {
        // do nothing
    }

    @Override
    public int setup(String string, ImagePlus ip)
    {
        if (ip == null)
        {
            IJ.noImage();
            return DONE;
        }
        else
        {
            bitDepth = ip.getBitDepth();

            if(bitDepth == 8)
            {
                valMax = UBYTE_MAX;
            }
            else if(bitDepth == 16)
            {
                valMax = USHORT_MAX;
            }

            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip)
    {
        float[] srcdst = (float[])((FloatProcessor)ip).getPixels();
        int imw = ip.getWidth();
        int imh = ip.getHeight();
        int numpix = imw * imh;
        Rectangle rect = ip.getRoi();       
        
        if(rect == null || (rect.width == imw && rect.height == imh))
        {       
            rect = null;          
        }
        else
        {
            rect = ip.getRoi().getBounds();
        }

        if(bitDepth == 8 || bitDepth == 16)
        {
            float[] table = makeTable(lower, upper, type, valTrue, valFalse);

            if(rect == null)
            {
                changePixelValueWithTable(srcdst, numpix, table);
            }
            else
            {
                changePixelValueWithTable(srcdst, imw, rect.x, rect.y, rect.width, rect.height, table);
            }           
        }
        else
        {
            if(rect == null)
            {
                rect = new Rectangle(0, 0, imw, imh);
            }
            
            changePixelValueForFloat(srcdst, lower, upper, type, valTrue, valFalse, imw, rect.x, rect.y, rect.width, rect.height);
        }
    }
   
    private float checkValue(float src, float min, float max)
    {
        float dst = 0;

        if(src < min)
        {
            dst = min;
        }
        else if(max < src)
        {
            dst = max;
        }
        else
        {
            dst = src;
        }

        return dst;
    }

    private float[] makeTable(float lower, float upper, String type, float val_tr, float val_fls)
    {
        float[] table_new = new float[(int)valMax  + 1];

        for (int i = 0; i < lower; i++)
        {
            if(type.equals(OUTER)) // true
            {
                table_new[i] = (float)val_tr;
            }
            else if(type.equals(INNER)) // false
            {
                table_new[i] = (float)val_fls;
            }
        }

        int lower_int = (int)lower;
        int upper_int = (int)upper;
        
        for (int i = lower_int; i < upper_int + 1; i++)
        {
            if(type.equals(OUTER)) // false
            {
                table_new[i] = (float)val_fls;
            }
            else if(type.equals(INNER)) // true
            {
                table_new[i] = (float)val_tr;
            }
        }

        int valMax_int = (int)valMax;
        
        for (int i = upper_int + 1; i < valMax_int + 1; i++)
        {
            if(type.equals(OUTER)) // true
            {
                table_new[i] = (float)val_tr;
            }
            else if(type.equals(INNER)) // false
            {
                table_new[i] = (float)val_fls;
            }
        }

        return table_new;
    }

    private void changePixelValueWithTable(float[] srcdst, int num, float[] tbl)
    {
        for (int i = 0; i < num; i++)
        {
            srcdst[i] = tbl[(int)srcdst[i]];
        }
    }

    private void changePixelValueWithTable(float[] srcdst, int str, int roix, int roiy, int roiw, int roih, float[] tbl)
    {
        int k = 0;

        for (int y = 0; y < roih; y++)
        {
            for (int x = 0; x < roiw; x++)
            {
                k = x + roix + (str * (y + roiy));
                srcdst[k] = tbl[(int)srcdst[k]];
            }
        }
    }
  
    private void  changePixelValueForFloat(
            float[] srcdst, 
            float thr_low, float thr_high,
            String type,
            float val_tr, float val_fls,
            int str, int roix, int roiy, int roiw, int roih)
    {
        int k = 0;
        
        if(type.equals(OUTER))
        {
            for (int y = 0; y < roih; y++)
            {
                for (int x = 0; x < roiw; x++)
                {
                    k = x + roix + (str * (y + roiy));

                    if(srcdst[k] < thr_low)
                    {
                         srcdst[k] = val_tr;
                    }
                    else if (thr_low <= srcdst[k] && srcdst[k] <  thr_high)
                    {
                        srcdst[k] = val_fls;
                    }
                    else if(thr_high <= srcdst[k])
                    {
                        srcdst[k] = val_tr;
                    }
                }
            }
        }
        else if(type.equals(INNER))
        {
            for (int y = 0; y < roih; y++)
            {
                for (int x = 0; x < roiw; x++)
                {
                    k = x + roix + (str * (y + roiy));

                    if(srcdst[k] < thr_low)
                    {
                         srcdst[k] = val_fls;
                    }
                    else if (thr_low <= srcdst[k] && srcdst[k] <  thr_high)
                    {
                        srcdst[k] = val_tr;
                    }
                    else if(thr_high <= srcdst[k])
                    {
                        srcdst[k] = val_fls;
                    }
                }
            }
        }
    }
}
