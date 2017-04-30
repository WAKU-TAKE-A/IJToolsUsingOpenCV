import ij.*;
import ij.IJ;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import java.awt.AWTEvent;

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
 * Calculate for pixels that match the condition.
 */
public class WK_Math implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener
{
    // const var.
    private static final int FLAGS = DOES_8G | DOES_16 | DOES_32 | CONVERT_TO_FLOAT | DOES_STACKS | PARALLELIZE_STACKS | KEEP_PREVIEW;
    private static final String ADD = "add";
    private static final String SUB = "sub"; 
    private static final String MUL = "multiply"; 
    private static final String SET = "set";
    private static final String[] TYPE_MATH = { ADD, SUB, MUL, SET };
    private static final String ALL = "all";
    private static final String EQUAL_ZERO = "equal_zero";
    private static final String NOT_ZERO = "not_zero";    
    private static final String[] TYPE_COND = { ALL, EQUAL_ZERO, NOT_ZERO };

    // static var.
    private static int ind_math = 0;
    private static int ind_cond = 0;
    private static float value = 0;

    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner pfr)
    {
        GenericDialog gd = new GenericDialog(cmd + "...");
        
        gd.addChoice("math", TYPE_MATH, TYPE_MATH[ind_math]);
        gd.addNumericField("value", value, 4);
        gd.addChoice("conditions", TYPE_COND, TYPE_COND[ind_cond]);
        gd.addPreviewCheckbox(pfr);
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
        ind_math = (int)gd.getNextChoiceIndex();
        value = (float)gd.getNextNumber();
        ind_cond = (int)gd.getNextChoiceIndex();
        
        IJ.showStatus("WK_Math");
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
        if (imp == null)
        {
            IJ.noImage();
            return DONE;
        }
        else
        {
            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip)
    {
        // srcdst
        int imw = ip.getWidth();
        int imh = ip.getHeight();
        float[] srcdst_floats = (float[])ip.getPixels();

        // run
        String type_cond = TYPE_COND[ind_cond];
        String type_math = TYPE_MATH[ind_math];
        
        if (type_math.equals(ADD)) 
        {
            math_add(srcdst_floats, imw, imh, value, type_cond);
        }
        else if (type_math.equals(SUB))
        {
            math_sub(srcdst_floats, imw, imh, value, type_cond);
        }
        else if (type_math.equals(MUL))
        {
            math_mul(srcdst_floats, imw, imh, value, type_cond);
        }
        else if (type_math.equals(SET))
        {
            math_set(srcdst_floats, imw, imh, value, type_cond);
        }
    }
    
    private void math_add(float[] srcdst, int imw, int imh, float value, String type)
    {
        int k = 0;
        
        if (type.equals(ALL))
        {
             for (int y = 0; y < imh; y++)
            {
                for (int x = 0; x < imw; x++)
                {
                    k = x + imw * y;
                    srcdst[k] = srcdst[k] + value;
                }
            }
        }
        else if (type.equals(EQUAL_ZERO))
        {
            for (int y = 0; y < imh; y++)
            {
                for (int x = 0; x < imw; x++)
                {
                    k = x + imw * y;

                    if(srcdst[k] == 0)
                    {
                        srcdst[k] = srcdst[k] + value;
                    }
                }
            }
        }
        else if (type.equals(NOT_ZERO))
        {
               for (int y = 0; y < imh; y++)  
            {
                for (int x = 0; x < imw; x++)
                {
                    k = x + imw * y;

                    if(srcdst[k] != 0)
                    {
                        srcdst[k] = srcdst[k] + value;
                    }
                }
            }         
        }
    }
    
    private void math_sub(float[] srcdst, int imw, int imh, float value, String type)
    {
        int k = 0;
        
         if (type.equals(ALL))
        {
            for (int y = 0; y < imh; y++)
            {
                for (int x = 0; x < imw; x++)
                {
                    k = x + imw * y;
                    srcdst[k] = srcdst[k] - value;
                }
            }
        }
        else if (type.equals(EQUAL_ZERO))
        {
            for (int y = 0; y < imh; y++)
            {
                for (int x = 0; x < imw; x++)
                {
                    k = x + imw * y;

                    if(srcdst[k] == 0)
                    {
                        srcdst[k] = srcdst[k] - value;
                    }
                }
            }
        }
        else if (type.equals(NOT_ZERO))
        {
            for (int y = 0; y < imh; y++)
            {
                for (int x = 0; x < imw; x++)
                {
                    k = x + imw * y;

                    if(srcdst[k] != 0)
                    {
                        srcdst[k] = srcdst[k] - value;
                    }
                }
            }
        }
    }
    
    private void math_mul(float[] srcdst, int imw, int imh, float value, String type)
    {
        int k = 0;        
        
        if (type.equals(ALL))
        {
                for (int y = 0; y < imh; y++)
                {
                    for (int x = 0; x < imw; x++)
                    {
                        k = x + imw * y;
                        srcdst[k] = srcdst[k] * value;
                    }
                }
        }
        else if (type.equals(EQUAL_ZERO))
        {
                 for (int y = 0; y < imh; y++)
                {
                    for (int x = 0; x < imw; x++)
                    {
                        k = x + imw * y;
                        
                        if(srcdst[k] == 0)
                        {
                            srcdst[k] = srcdst[k] * value;
                        }
                    }
                } 
        }
        else if (type.equals(NOT_ZERO))
        {
                for (int y = 0; y < imh; y++)
                {
                    for (int x = 0; x < imw; x++)
                    {
                        k = x + imw * y;
                        
                        if(srcdst[k] != 0)
                        {
                            srcdst[k] = srcdst[k] * value;
                        }
                    }
                }
        }
    }
    
    private void math_set(float[] srcdst, int imw, int imh, float value, String type)
    {
        int k = 0;
        
        if (type.equals(ALL))
        {
            for (int y = 0; y < imh; y++)
            {
                for (int x = 0; x < imw; x++)
                {
                    k = x + imw * y;
                    srcdst[k] = value;
                }
            }
        }
        else if (type.equals(EQUAL_ZERO))
        {
            for (int y = 0; y < imh; y++)
            {
                for (int x = 0; x < imw; x++)
                {
                    k = x + imw * y;

                    if(srcdst[k] == 0)
                    {
                        srcdst[k] = value;
                    }
                }
            } 
        }
        else if (type.equals(NOT_ZERO))
        {
                for (int y = 0; y < imh; y++)
                {
                    for (int x = 0; x < imw; x++)
                    {
                        k = x + imw * y;
                        
                        if(srcdst[k] != 0)
                        {
                            srcdst[k] = value;
                        }
                    }
                } 
        }
    }
}
