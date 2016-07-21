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
 * Change the pixel values
 * @version 0.9.2.0
 */
public class WK_ChangePixelValue implements ExtendedPlugInFilter, DialogListener
{
    // constant var.
    private final int FLAGS = DOES_8G | DOES_16 | CONVERT_TO_FLOAT | KEEP_PREVIEW;
    private static final int USHORT_MAX = 65535;
    private static final int UBYTE_MAX = 255;
    private static final String INNER = "inner";
    private static final String OUTER = "outer";
    private static final String[] BYNARY_TYPE = { INNER, OUTER };

    // static var.
    private static int lower = 0;
    private static int upper = 0;
    private static String type = INNER;
    private static int valTrue = 255;
    private static int valFalse = 0;

    // var.
    private int valMax = 0;

    @Override
    public int showDialog(ImagePlus ip, String command, PlugInFilterRunner pifr)
    {
        lower = 0 < ip.getProcessor().getMinThreshold() ? (int)(ip.getProcessor().getMinThreshold()) : lower;
        upper = 0 < ip.getProcessor().getMaxThreshold() ? (int)(ip.getProcessor().getMaxThreshold()) : upper;
        type = type == null ? INNER : type;

        GenericDialog gd = new GenericDialog(command.trim() + "...");

        gd.addNumericField("lower", lower, 0);
        gd.addNumericField("upper", upper, 0);
        gd.addChoice("range_of_true", BYNARY_TYPE, type);
        gd.addNumericField("value_of_true", valTrue, 0);
        gd.addNumericField("value_of_false", valFalse, 0);
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
            int bd = ip.getBitDepth();

            if(bd == 8)
            {
                valMax = UBYTE_MAX;
            }
            else if(bd == 16)
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
        Rectangle rect;

        if(ip.getRoi() != null)
        {
            rect = ip.getRoi().getBounds();
        }
        else
        {
            rect = null;
        }

        float[] table = makeTable(lower, upper, type, valTrue, valFalse);

        if(rect == null)
        {
            changePixelValue(srcdst, numpix, table);
        }
        else
        {
            changePixelValue(srcdst, imw, rect.x, rect.y, rect.width, rect.height, table);
        }
    }

    @Override
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent awte)
    {
        lower = (int)gd.getNextNumber();
        upper = (int)gd.getNextNumber();

        if(upper < lower)
        {
            IJ.error("upper < lower");
            return false;
        }

        lower = checkValue(lower, 0, valMax);
        upper = checkValue(upper, 0, valMax);

        type = (String)BYNARY_TYPE[(int)gd.getNextChoiceIndex()];

        valTrue = (int)gd.getNextNumber();
        valFalse = (int)gd.getNextNumber();
        valTrue = checkValue(valTrue, 0, valMax);
        valFalse = checkValue(valFalse, 0, valMax);
        
        return true;
    }
    
    private int checkValue(int src, int min, int max)
    {
        int dst = 0;

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

    private float[] makeTable(int lower, int upper, String type, int val_tr, int val_fls)
    {
        float[] table_new = new float[valMax  + 1];

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

        for (int i = lower; i < upper + 1; i++)
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

        for (int i = upper + 1; i < valMax + 1; i++)
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

    private void changePixelValue(float[] src, int num, float[] tbl)
    {
        for (int i = 0; i < num; i++)
        {
            src[i] = tbl[(int)src[i]];
        }
    }

    private void changePixelValue(float[] src, int str, int roix, int roiy, int roiw, int roih, float[] tbl)
    {
        int k = 0;

        for (int y = 0; y < roih; y++)
        {
            for (int x = 0; x < roiw; x++)
            {
                k = x + roix + (str * (y + roiy));
                src[k] = tbl[(int)src[k]];
            }
        }
    }
}
