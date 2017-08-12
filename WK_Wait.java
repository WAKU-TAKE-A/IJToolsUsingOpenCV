import ij.*;
import ij.gui.GenericDialog;
import ij.process.*;
import ij.plugin.filter.*;

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
 * Wait time.
 */
public class WK_Wait implements ExtendedPlugInFilter
{
    // constant var.
    private final int FLAGS = NO_IMAGE_REQUIRED;

    // static var.
    private static int wtime = 0;

    @Override
    public int showDialog(ImagePlus ip, String command, PlugInFilterRunner pifr)
    {
        GenericDialog gd = new GenericDialog(command.trim() + "...");
        gd.addNumericField("waittime", wtime, 0);
        gd.addMessage("The unit is ms.");
        gd.showDialog();

        if (gd.wasCanceled())
        {
            return DONE;
        }
        else
        {
            wtime = (int)gd.getNextNumber();
            return FLAGS;
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
        // do nothing
        return FLAGS;
    }

    @Override
    public void run(ImageProcessor ip)
    {
        wait(wtime);
    }
    
    private void wait(int wt){

        try
        {
            if(wt == 0)
            {
                // do nothing
            }
            else
            {
                Thread.sleep(wt);
            }
        } catch (InterruptedException e)
        {
            // do nothing
        }
    }
}
