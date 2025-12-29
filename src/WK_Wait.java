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
public class WK_Wait implements ExtendedPlugInFilter {
    // constant var.
    private static final int FLAGS = NO_IMAGE_REQUIRED;
    private static final int DECIMAL_PLACES_WAIT = 0;
    private static final int MIN_WAIT_TIME = 0;
    private static final int COLUMNS = 8;

    // static var.
    private static int waitTime = 1000;
    private static int maxWaitTime = 60000;
    private static String textWhenFinished = "";
    
    // var.
    private String className;

    @Override
    public int showDialog(ImagePlus ip, String command, PlugInFilterRunner pifr) {
        className = command.trim();
        GenericDialog gd = new GenericDialog(className + " ...");
        gd.addNumericField("waittime", waitTime, DECIMAL_PLACES_WAIT);
        gd.addNumericField("maxtime", maxWaitTime, DECIMAL_PLACES_WAIT);
        gd.addStringField("text_when_finish", textWhenFinished, COLUMNS);
        gd.addMessage("The unit is ms.");
        gd.showDialog();

        if(gd.wasCanceled()) {
            return DONE;
        }
        else {
            int inputWaitTime = (int)gd.getNextNumber();
            int inputMaxWaitTime = (int)gd.getNextNumber();
            textWhenFinished = gd.getNextString();
            
            if(inputMaxWaitTime <= MIN_WAIT_TIME) {
                IJ.error("maxtime must be positive.");
                return DONE;               
            }
            
            maxWaitTime = inputMaxWaitTime;

            if(inputWaitTime < MIN_WAIT_TIME) {
                IJ.error("Wait time must be non-negative.");
                return DONE;
            }

            if(inputWaitTime > maxWaitTime) {
                IJ.error("Wait time must be less than or equal to " + maxWaitTime + " ms.");
                return DONE;
            }

            waitTime = inputWaitTime;
            return FLAGS;
        }
    }

    @Override
    public void setNPasses(int i) {
        // do nothing
    }

    @Override
    public int setup(String string, ImagePlus ip) {
        return FLAGS;
    }

    @Override
    public void run(ImageProcessor ip) {
        try {
            OCV__LoadLibrary.Wait(waitTime);
            
            if(!isNullOrEmpty(textWhenFinished)) {
                IJ.log(textWhenFinished);
            }
        }
        catch(Exception e) {
            IJ.log("Error during wait: " + e.getMessage());
        }
    }
    
    private static boolean isNullOrEmpty(String src)
    {
        return src == null || src.isEmpty() || src.isBlank();  
    }
}