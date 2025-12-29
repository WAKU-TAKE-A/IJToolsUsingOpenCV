import ij.*;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.Macro_Runner;
import ij.plugin.filter.*;
import ij.plugin.frame.RoiManager;
import ij.process.*;
import java.awt.Rectangle;
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
 * connectedComponentsWithStats.
 */
public class OCV_ConnectedComponentsWithStats implements ExtendedPlugInFilter {
    // const var.
    private static final int FLAGS = DOES_8G;
    private static final int CONN_4 = 4;
    private static final int CONN_8 = 8;
    private static final int[] TYPE_INT = { CONN_4, CONN_8 };
    private static final String[] TYPE_STR = { "4-connected", "8-connected" };

    // static var.
    private static int typeInd = 1;
    private static boolean enOutImg = false;
    private static boolean enWand = false;

    // var.
    private String className;
    private ImagePlus impSrc = null;

    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner pifr) {
        className = cmd.trim();
        GenericDialog gd = new GenericDialog(className + " ...");

        gd.addChoice("connectivity", TYPE_STR, TYPE_STR[typeInd]);
        gd.addCheckbox("enable_output_labeled_image", enOutImg);
        gd.addCheckbox("enable_select_roi_by_dowand", enWand);

        gd.showDialog();

        if(gd.wasCanceled()) {
            return DONE;
        }
        else {
            typeInd = (int)gd.getNextChoiceIndex();
            enOutImg = (boolean)gd.getNextBoolean();
            enWand = (boolean)gd.getNextBoolean();

            return FLAGS;
        }
    }

    @Override
    public void setNPasses(int nPasses) {
        // do nothing
    }

    @Override
    public int setup(String arg, ImagePlus imp) {
        if(!OCV__LoadLibrary.isLoad()) {
            IJ.error("Library is not loaded.");
            return DONE;
        }

        if(imp == null) {
            IJ.noImage();
            return DONE;
        }
        else {
            impSrc = imp;
            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip) {
        Mat srcMat = null;
        Mat dstMat32s = null;
        Mat dstMat32f = null;
        Mat statsMat = null;
        Mat censMat = null;
        ImagePlus impDst = null;

        try {
            // src
            int imw = ip.getWidth();
            int imh = ip.getHeight();
            byte[] srcArr = (byte[])ip.getPixels();
            srcMat = new Mat(imh, imw, CvType.CV_8UC1);

            // dst
            String titleDst = WindowManager.getUniqueName(impSrc.getTitle() + "_Connect" + String.valueOf(TYPE_INT[typeInd]));
            impDst = new ImagePlus(titleDst, new FloatProcessor(imw, imh));
            float[] dstArr = (float[])impDst.getChannelProcessor().getPixels();
            dstMat32s = new Mat(imh, imw, CvType.CV_32S);
            dstMat32f = new Mat(imh, imw, CvType.CV_32F);
            statsMat = new Mat();
            censMat = new Mat();

            // run
            srcMat.put(0, 0, srcArr);
            int outputCon = Imgproc.connectedComponentsWithStats(srcMat, dstMat32s, statsMat, censMat, TYPE_INT[typeInd], CvType.CV_32S);
            dstMat32s.convertTo(dstMat32f, CvType.CV_32F);
            dstMat32f.get(0, 0, dstArr);

            // show data
            if(1 < outputCon) {
                showData(dstArr, imw, imh, outputCon, statsMat, censMat);
            }

            // finish
            if(1 < outputCon && enOutImg) {
                impDst.show();
                impDst = null; // Prevent closing in finally
            }
        }
        catch(Exception e) {
            IJ.log("Connected components analysis failed: " + e.getMessage());
        }
        finally {
            // Release OpenCV resources
            if(srcMat != null) {
                srcMat.release();
            }
            if(dstMat32s != null) {
                dstMat32s.release();
            }
            if(dstMat32f != null) {
                dstMat32f.release();
            }
            if(statsMat != null) {
                statsMat.release();
            }
            if(censMat != null) {
                censMat.release();
            }
            // Close ImagePlus if not shown
            if(impDst != null) {
                impDst.close();
            }
        }
    }

    private void showData(float[] dstArr, int imw, int imh, int outputCon, Mat statsMat, Mat censMat) {
        try {
            int numLab = outputCon - 1;
            Rectangle[] rects = new Rectangle[outputCon];
            int[] areas = new int[outputCon];

            // Batch get stats data for efficiency
            int statsRows = statsMat.rows();
            int statsCols = statsMat.cols();
            
            if(statsRows != outputCon || statsCols < 5) {
                IJ.log("Warning: Unexpected stats matrix size: " + statsRows + "x" + statsCols);
                return;
            }

            int[] statsData = new int[statsRows * statsCols];
            statsMat.get(0, 0, statsData);

            for(int i = 0; i < outputCon; i++) {
                int idx = i * statsCols;
                int x = statsData[idx];
                int y = statsData[idx + 1];
                int width = statsData[idx + 2];
                int height = statsData[idx + 3];
                int area = statsData[idx + 4];
                
                rects[i] = new Rectangle(x, y, width, height);
                areas[i] = area;
            }

            ResultsTable rt = OCV__LoadLibrary.GetResultsTable(true);
            RoiManager roiManager = OCV__LoadLibrary.GetRoiManager(true, true);
            Macro_Runner mr = new Macro_Runner();

            mr.runMacro("setBatchMode(true);", "");

            for(int i = 1; i < outputCon; i++) {
                rt.incrementCounter();
                rt.addValue("No", i);
                rt.addValue("Area", areas[i]);
                rt.addValue("BX", rects[i].x);
                rt.addValue("BY", rects[i].y);
                rt.addValue("Width", rects[i].width);
                rt.addValue("Height", rects[i].height);

                if(!enWand) {
                    Roi roi = new Roi(rects[i].x, rects[i].y, rects[i].width, rects[i].height);
                    roiManager.addRoi(roi);
                    roiManager.rename(i - 1, "no" + String.valueOf(i) + "-" + String.valueOf(areas[i]));
                }
            }

            if(enWand) {
                processDoWand(dstArr, imw, imh, numLab, areas, mr, roiManager);
            }

            mr.runMacro("setBatchMode(false);", "");

            rt.show("Results");
            roiManager.runCommand("show all");
        }
        catch(Exception e) {
            IJ.log("Show data failed: " + e.getMessage());
        }
    }

    private void processDoWand(float[] dstArr, int imw, int imh, int numLab, int[] areas, Macro_Runner mr, RoiManager roiManager) {
        try {
            int[] chk = new int[numLab + 1];
            int[] xDoWand = new int[numLab + 1];
            int[] yDoWand = new int[numLab + 1];
            String type = TYPE_STR[typeInd];

            for(int y = 0; y < imh; y++) {
                for(int x = 0; x < imw; x++) {
                    int val = (int)dstArr[x + y * imw];

                    if(val != 0 && val <= numLab && chk[val] == 0) {
                        chk[val] = 1;
                        xDoWand[val] = x;
                        yDoWand[val] = y;
                    }
                }
            }

            for(int i = 1; i <= numLab; i++) {
                if(chk[i] == 1) {
                    String macroCmd = String.format("doWand(%d, %d, 0.0, \"%s\");", xDoWand[i], yDoWand[i], type);
                    mr.runMacro(macroCmd, "");
                    roiManager.runCommand("add");
                    roiManager.rename(roiManager.getCount() - 1, "no" + String.valueOf(i) + "-" + String.valueOf(areas[i]));
                }
            }
        }
        catch(Exception e) {
            IJ.log("DoWand processing failed: " + e.getMessage());
        }
    }
}