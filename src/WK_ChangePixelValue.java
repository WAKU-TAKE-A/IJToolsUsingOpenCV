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
public class WK_ChangePixelValue implements ExtendedPlugInFilter, DialogListener {
    // constant var.
    private static final int FLAGS = DOES_8G | DOES_16 | DOES_32 | CONVERT_TO_FLOAT | DOES_STACKS | PARALLELIZE_STACKS | KEEP_PREVIEW;
    private static final float USHORT_MAX = 65535;
    private static final float UBYTE_MAX = 255;
    private static final String INNER = "inner";
    private static final String OUTER = "outer";
    private static final String[] BINARY_TYPE = { INNER, OUTER };
    private static final int DECIMAL_PLACES = 4;

    // static var.
    private static float lower = 0;
    private static float upper = 0;
    private static String type = INNER;
    private static float valueTrue = 255;
    private static float valueFalse = 0;

    // var.
    private String className;
    private float valueMax = 0;
    private int bitDepth = 0;

    @Override
    public int showDialog(ImagePlus ip, String command, PlugInFilterRunner pifr) {
        lower = 0 < ip.getProcessor().getMinThreshold() ? (float)ip.getProcessor().getMinThreshold() : lower;
        upper = 0 < ip.getProcessor().getMaxThreshold() ? (float)ip.getProcessor().getMaxThreshold() : upper;

        double minValue = 0;
        double maxValue = 0;

        if(bitDepth == 8) {
            minValue = 0;
            maxValue = UBYTE_MAX;
        }
        else if(bitDepth == 16) {
            minValue = 0;
            maxValue = USHORT_MAX;
        }
        else {
            ImageStatistics stat = ip.getStatistics();
            minValue = stat.min - 1;
            maxValue = stat.max + 1;
        }

        className = command.trim();
        GenericDialog gd = new GenericDialog(className + " ...");

        gd.addSlider("lower", minValue, maxValue, lower);
        gd.addSlider("upper", minValue, maxValue, upper);
        gd.addChoice("range_of_true", BINARY_TYPE, type);
        gd.addNumericField("value_of_true", valueTrue, DECIMAL_PLACES);
        gd.addNumericField("value_of_false", valueFalse, DECIMAL_PLACES);
        gd.addPreviewCheckbox(pifr);
        gd.addDialogListener(this);

        gd.showDialog();

        if(gd.wasCanceled()) {
            return DONE;
        }
        else {
            return IJ.setupDialog(ip, FLAGS);
        }
    }

    @Override
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent awte) {
        lower = (float)gd.getNextNumber();
        upper = (float)gd.getNextNumber();

        if(upper < lower) {
            IJ.showStatus("'lower <= upper' is necessary.");
            return false;
        }

        if(bitDepth == 8 || bitDepth == 16) {
            lower = clampValue(lower, 0, valueMax);
            upper = clampValue(upper, 0, valueMax);
        }

        type = BINARY_TYPE[gd.getNextChoiceIndex()];

        valueTrue = (float)gd.getNextNumber();
        valueFalse = (float)gd.getNextNumber();

        if(bitDepth == 8 || bitDepth == 16) {
            valueTrue = clampValue(valueTrue, 0, valueMax);
            valueFalse = clampValue(valueFalse, 0, valueMax);
        }

        IJ.showStatus("WK_ChangePixelValue");
        return true;
    }

    @Override
    public void setNPasses(int i) {
        // do nothing
    }

    @Override
    public int setup(String string, ImagePlus ip) {
        if(ip == null) {
            IJ.noImage();
            return DONE;
        }
        else {
            bitDepth = ip.getBitDepth();

            if(bitDepth == 8) {
                valueMax = UBYTE_MAX;
            }
            else if(bitDepth == 16) {
                valueMax = USHORT_MAX;
            }

            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip) {
        float[] pixels = (float[])((FloatProcessor)ip).getPixels();
        int imageWidth = ip.getWidth();
        int imageHeight = ip.getHeight();
        int numPixels = imageWidth * imageHeight;
        Rectangle rect = ip.getRoi();

        if(rect == null || (rect.width == imageWidth && rect.height == imageHeight)) {
            rect = null;
        }
        else {
            rect = ip.getRoi().getBounds();
        }

        if(bitDepth == 8 || bitDepth == 16) {
            float[] table = createLookupTable(lower, upper, type, valueTrue, valueFalse);

            if(rect == null) {
                applyLookupTable(pixels, numPixels, table);
            }
            else {
                applyLookupTableWithROI(pixels, imageWidth, rect.x, rect.y, rect.width, rect.height, table);
            }
        }
        else {
            if(rect == null) {
                rect = new Rectangle(0, 0, imageWidth, imageHeight);
            }

            changePixelValueForFloat(pixels, lower, upper, type, valueTrue, valueFalse, imageWidth, rect.x, rect.y, rect.width, rect.height);
        }
    }

    /**
     * Clamp value to specified range
     */
    private float clampValue(float value, float min, float max) {
        if(value < min) {
            return min;
        }
        else if(value > max) {
            return max;
        }
        else {
            return value;
        }
    }

    /**
     * Create lookup table for pixel value transformation
     */
    private float[] createLookupTable(float lowerThreshold, float upperThreshold, String rangeType, float trueValue, float falseValue) {
        int tableSize = (int)valueMax + 1;
        float[] table = new float[tableSize];

        int lowerInt = (int)lowerThreshold;
        int upperInt = (int)upperThreshold;
        boolean isOuter = rangeType.equals(OUTER);

        // Below lower threshold
        float valueBelowLower = isOuter ? trueValue : falseValue;
        for(int i = 0; i < lowerInt; i++) {
            table[i] = valueBelowLower;
        }

        // Between lower and upper threshold (inclusive)
        float valueInRange = isOuter ? falseValue : trueValue;
        for(int i = lowerInt; i <= upperInt && i < tableSize; i++) {
            table[i] = valueInRange;
        }

        // Above upper threshold
        float valueAboveUpper = isOuter ? trueValue : falseValue;
        for(int i = upperInt + 1; i < tableSize; i++) {
            table[i] = valueAboveUpper;
        }

        return table;
    }

    /**
     * Apply lookup table to entire image
     */
    private void applyLookupTable(float[] pixels, int numPixels, float[] table) {
        for(int i = 0; i < numPixels; i++) {
            int index = (int)pixels[i];
            if(index >= 0 && index < table.length) {
                pixels[i] = table[index];
            }
        }
    }

    /**
     * Apply lookup table to ROI
     */
    private void applyLookupTableWithROI(float[] pixels, int stride, int roiX, int roiY, int roiWidth, int roiHeight, float[] table) {
        for(int y = 0; y < roiHeight; y++) {
            for(int x = 0; x < roiWidth; x++) {
                int k = x + roiX + (stride * (y + roiY));
                int index = (int)pixels[k];
                if(index >= 0 && index < table.length) {
                    pixels[k] = table[index];
                }
            }
        }
    }

    /**
     * Change pixel values for 32-bit float images
     */
    private void changePixelValueForFloat(
        float[] pixels,
        float lowerThreshold, 
        float upperThreshold,
        String rangeType,
        float trueValue, 
        float falseValue,
        int stride, 
        int roiX, 
        int roiY, 
        int roiWidth, 
        int roiHeight) {
        
        boolean isOuter = rangeType.equals(OUTER);

        for(int y = 0; y < roiHeight; y++) {
            for(int x = 0; x < roiWidth; x++) {
                int k = x + roiX + (stride * (y + roiY));
                float pixelValue = pixels[k];

                boolean inRange = (pixelValue >= lowerThreshold && pixelValue <= upperThreshold);
                
                if(isOuter) {
                    pixels[k] = inRange ? falseValue : trueValue;
                }
                else {
                    pixels[k] = inRange ? trueValue : falseValue;
                }
            }
        }
    }
}