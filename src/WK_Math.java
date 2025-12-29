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
public class WK_Math implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener {
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
    private static final int DECIMAL_PLACES = 4;
    private static final float EPSILON = 1e-6f;

    // static var.
    private static int selectedMathIndex = 0;
    private static int selectedConditionIndex = 0;
    private static float value = 0;
    
    // var.
    private String className;

    /**
     * Functional interface for pixel operations
     */
    @FunctionalInterface
    private interface PixelOperation {
        float apply(float pixel, float value);
    }

    /**
     * Functional interface for condition checking
     */
    @FunctionalInterface
    private interface PixelCondition {
        boolean test(float pixel);
    }

    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner pfr) {
        className = cmd.trim();
        GenericDialog gd = new GenericDialog(className + " ...");

        gd.addChoice("math", TYPE_MATH, TYPE_MATH[selectedMathIndex]);
        gd.addNumericField("value", value, DECIMAL_PLACES);
        gd.addChoice("conditions", TYPE_COND, TYPE_COND[selectedConditionIndex]);
        gd.addPreviewCheckbox(pfr);
        gd.addDialogListener(this);

        gd.showDialog();

        if(gd.wasCanceled()) {
            return DONE;
        }
        else {
            return IJ.setupDialog(imp, FLAGS);
        }
    }

    @Override
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent awte) {
        selectedMathIndex = gd.getNextChoiceIndex();
        value = (float)gd.getNextNumber();
        selectedConditionIndex = gd.getNextChoiceIndex();

        IJ.showStatus("WK_Math");
        return true;
    }

    @Override
    public void setNPasses(int nPasses) {
        // do nothing
    }

    @Override
    public int setup(String arg, ImagePlus imp) {
        if(imp == null) {
            IJ.noImage();
            return DONE;
        }
        else {
            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip) {
        int imageWidth = ip.getWidth();
        int imageHeight = ip.getHeight();
        float[] pixels = (float[])ip.getPixels();

        String conditionType = TYPE_COND[selectedConditionIndex];
        String mathType = TYPE_MATH[selectedMathIndex];

        PixelOperation operation = getPixelOperation(mathType);
        PixelCondition condition = getPixelCondition(conditionType);

        applyOperation(pixels, imageWidth, imageHeight, value, operation, condition);
    }

    /**
     * Get pixel operation based on math type
     */
    private PixelOperation getPixelOperation(String mathType) {
        switch(mathType) {
            case ADD:
                return (pixel, val) -> pixel + val;
            case SUB:
                return (pixel, val) -> pixel - val;
            case MUL:
                return (pixel, val) -> pixel * val;
            case SET:
                return (pixel, val) -> val;
            default:
                return (pixel, val) -> pixel;
        }
    }

    /**
     * Get pixel condition based on condition type
     */
    private PixelCondition getPixelCondition(String conditionType) {
        switch(conditionType) {
            case ALL:
                return pixel -> true;
            case EQUAL_ZERO:
                return pixel -> Math.abs(pixel) < EPSILON;
            case NOT_ZERO:
                return pixel -> Math.abs(pixel) >= EPSILON;
            default:
                return pixel -> true;
        }
    }

    /**
     * Apply operation to pixels that match the condition
     */
    private void applyOperation(
        float[] pixels, 
        int imageWidth, 
        int imageHeight, 
        float value,
        PixelOperation operation,
        PixelCondition condition) {
        
        int numPixels = imageWidth * imageHeight;

        for(int i = 0; i < numPixels; i++) {
            if(condition.test(pixels[i])) {
                pixels[i] = operation.apply(pixels[i], value);
            }
        }
    }
}