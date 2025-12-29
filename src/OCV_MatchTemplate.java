import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.Macro_Runner;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.process.FloatProcessor;
import java.awt.AWTEvent;
import java.util.ArrayList;
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
 * matchTemplate.
 */
public class OCV_MatchTemplate implements ij.plugin.filter.ExtendedPlugInFilter, DialogListener {
    // constant var.
    private static final int FLAGS = DOES_8G;
    private static final String[] TYPE_STR = new String[] { "1 - TM_SQDIFF_NORMED", "TM_CCORR_NORMED", "TM_CCOEFF_NORMED" };
    private static final int[] TYPE_VAL = new int[] { Imgproc.TM_SQDIFF_NORMED, Imgproc.TM_CCORR_NORMED, Imgproc.TM_CCOEFF_NORMED };

    // static var.
    private static int indSrc = 0;
    private static int indTmp = 1;
    private static int indType = 1;
    private static float thrRes = (float)0.5;
    private static boolean enResult = true;
    private static boolean enSearchMax = false;

    // var.
    private String titleSrc = null;
    private ImagePlus impSrc = null;
    private ImagePlus impTmp = null;
    private int[] lstWid;
    private String[] titles;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        GenericDialog gd = new GenericDialog(command.trim() + "...");

        gd.addChoice("src", titles, titles[indSrc]);
        gd.addChoice("template", titles, titles[indTmp]);
        gd.addChoice("method", TYPE_STR, TYPE_STR[indType]);
        gd.addNumericField("threshold_of_results", thrRes, 4);
        gd.addCheckbox("enable_results_table", enResult);
        gd.addCheckbox("enable_search_max_point_in_blob", enSearchMax);
        gd.addDialogListener(this);

        gd.showDialog();

        if(gd.wasCanceled()) {
            return DONE;
        }
        else {
            return FLAGS;
        }
    }

    @Override
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent awte) {
        indSrc = (int)gd.getNextChoiceIndex();
        indTmp = (int)gd.getNextChoiceIndex();
        indType = (int)gd.getNextChoiceIndex();
        thrRes = (float)gd.getNextNumber();
        enResult = (boolean)gd.getNextBoolean();
        enSearchMax = (boolean)gd.getNextBoolean();

        if(Float.isNaN(thrRes)) {
            IJ.showStatus("ERR : NaN");
            return false;
        }

        if(indSrc == indTmp) {
            IJ.showStatus("The same image can not be selected.");
            return false;
        }

        impSrc = WindowManager.getImage(lstWid[indSrc]);
        impTmp = WindowManager.getImage(lstWid[indTmp]);
        titleSrc = impSrc.getShortTitle();

        if(impSrc.getBitDepth() != 8 || impTmp.getBitDepth() != 8) {
            IJ.showStatus("The both images should be 8bit gray");
            return false;
        }

        if(impSrc.getWidth() < impTmp.getWidth() || impSrc.getHeight() < impTmp.getHeight()) {
            IJ.showStatus("The size of src should be larger than the size of template.");
            return false;
        }

        if(enSearchMax) {
            enResult = true;
        }

        IJ.showStatus("OCV_MatchTemplate");
        return true;
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
            lstWid = WindowManager.getIDList();

            if(lstWid == null || lstWid.length < 2) {
                IJ.error("At least more than 2 images are needed.");
                return DONE;
            }

            titles = new String[lstWid.length];

            for(int i = 0; i < lstWid.length; i++) {
                ImagePlus imp2 = WindowManager.getImage(lstWid[i]);
                titles[i] = imp2 != null ? imp2.getTitle() : "";
            }

            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip) {
        Mat matSrc = null;
        Mat matTmp = null;
        Mat matDst = null;

        try {
            // src
            byte[] arrSrc = (byte[])impSrc.getChannelProcessor().getPixels();
            int imwSrc = impSrc.getWidth();
            int imhSrc = impSrc.getHeight();
            matSrc = new Mat(imhSrc, imwSrc, CvType.CV_8UC1);
            matSrc.put(0, 0, arrSrc);

            // tmp
            byte[] arrTmp = (byte[])impTmp.getChannelProcessor().getPixels();
            int imwTmp = impTmp.getWidth();
            int imhTmp = impTmp.getHeight();
            matTmp = new Mat(imhTmp, imwTmp, CvType.CV_8UC1);
            matTmp.put(0, 0, arrTmp);

            // dst
            String titleDst = WindowManager.getUniqueName(titleSrc + "_MatchTemplate");
            int imwDst = imwSrc - imwTmp + 1;
            int imhDst = imhSrc - imhTmp + 1;
            ImagePlus impDst = new ImagePlus(titleDst, new FloatProcessor(imwDst, imhDst));
            float[] arrDst = (float[])impDst.getChannelProcessor().getPixels();
            matDst = new Mat();

            // run
            Imgproc.matchTemplate(matSrc, matTmp, matDst, TYPE_VAL[indType]);
            matDst.get(0, 0, arrDst);
            impDst.show();

            if(TYPE_VAL[indType] == Imgproc.TM_SQDIFF_NORMED) {
                substractedFromOne(arrDst);
            }

            //IJ.run(impDst, "Enhance Contrast", "saturated=0.35");

            // show data
            if(enResult) {
                if(enSearchMax) {
                    showDataEnSearchMaxPoint(impDst, arrDst, thrRes, imwTmp, imhTmp);
                }
                else {
                    showData(arrDst, imwDst, imhDst, imwTmp, imhTmp);
                }
            }
        }
        catch(Exception e) {
            IJ.log("Match template failed: " + e.getMessage());
        }
        finally {
            if(matSrc != null) {
                matSrc.release();
            }
            if(matTmp != null) {
                matTmp.release();
            }
            if(matDst != null) {
                matDst.release();
            }
        }
    }

    private void showData(float[] arrDst, int imwDst, int imhDst, int imwTmp, int imhTmp) {
        try {
            // prepare the ResultsTable
            ResultsTable rt = OCV__LoadLibrary.GetResultsTable(true);

            // prepare the ROI Manager
            RoiManager roiMan = OCV__LoadLibrary.GetRoiManager(true, true);

            // show
            Macro_Runner mr = new Macro_Runner();
            mr.runMacro("setBatchMode(true);", "");
            ArrayList<float[]> res = new ArrayList<float[]>();

            for(int y = 0; y < imhDst; y++) {
                for(int x = 0; x < imwDst; x++) {
                    if(thrRes <= arrDst[x + y * imwDst]) {
                        res.add(new float[] { (float)x, (float)y, arrDst[x + y * imwDst] });
                    }
                }
            }

            int numMatch = res.size();

            for(int i = 0; i < numMatch; i++) {
                int bx = (int)res.get(i)[0];
                int by = (int)res.get(i)[1];
                float match = res.get(i)[2];

                Roi roi = new Roi(bx, by, imwTmp, imhTmp);
                impSrc.setRoi(roi);

                roiMan.addRoi(roi);
                int idxLast = roiMan.getCount() - 1;
                roiMan.select(idxLast);
                roiMan.runCommand("Rename", "no" + String.valueOf(i + 1) + "-" + String.valueOf(match));

                rt.incrementCounter();
                rt.addValue("No", i + 1);
                rt.addValue("BX", bx);
                rt.addValue("BY", by);
                rt.addValue("Width", imwTmp);
                rt.addValue("Height", imhTmp);
                rt.addValue("Match", String.valueOf(match));
                rt.show("Results");
            }

            mr.runMacro("setBatchMode(false);", "");
            roiMan.runCommand("Show All");
        }
        catch(Exception e) {
            IJ.log("Show data failed: " + e.getMessage());
        }
    }

    private void showDataEnSearchMaxPoint(ImagePlus impDst, float[] arrDst, float thr, int imwTmp, int imhTmp) {
        ImagePlus impBin = null;
        ImagePlus impLab = null;

        try {
            int imw = impDst.getWidth();

            impBin = impDst.duplicate();
            impBin.setTitle("__bin");
            float[] arrBin = (float[])impBin.getChannelProcessor().getPixels();
            binaryFloat(arrBin, thr);

            IJ.run(impBin, "8-bit", "");
            IJ.run(impBin, "OCV ConnectedComponentsWithStats", "connectivity=8-connected enable_output_labeled_image");
            impLab = WindowManager.getImage("__bin_Connect8-1");

            ResultsTable rt = ResultsTable.getResultsTable();
            int colX = rt.getColumnIndex("BX");
            int colY = rt.getColumnIndex("BY");
            int colW = rt.getColumnIndex("Width");
            int colH = rt.getColumnIndex("Height");
            ArrayList<float[]> arrPointMax = new ArrayList<float[]>();

            for(int i = 0; i < rt.size(); i++) {
                int bx = (int)(rt.getValueAsDouble(colX, i));
                int by = (int)(rt.getValueAsDouble(colY, i));
                int w = (int)(rt.getValueAsDouble(colW, i));
                int h = (int)(rt.getValueAsDouble(colH, i));
                float[] pointMax = new float[3];

                searchMaxPoint(arrDst, bx, by, w, h, imw, pointMax);
                arrPointMax.add(pointMax);
            }

            // prepare the ResultsTable
            rt.reset();

            // prepare the ROI Manager
            RoiManager roiMan = OCV__LoadLibrary.GetRoiManager(true, true);

            // show
            Macro_Runner mr = new Macro_Runner();
            mr.runMacro("setBatchMode(true);", "");
            int numMatch = arrPointMax.size();

            for(int i = 0; i < numMatch; i++) {
                int bx = (int)arrPointMax.get(i)[0];
                int by = (int)arrPointMax.get(i)[1];
                float match = arrPointMax.get(i)[2];

                Roi roi = new Roi(bx, by, imwTmp, imhTmp);
                impSrc.setRoi(roi);

                roiMan.addRoi(roi);
                int idxLast = roiMan.getCount() - 1;
                roiMan.select(idxLast);
                roiMan.runCommand("Rename", "no" + String.valueOf(i + 1) + "-" + String.valueOf(match));

                rt.incrementCounter();
                rt.addValue("No", i + 1);
                rt.addValue("BX", bx);
                rt.addValue("BY", by);
                rt.addValue("Width", imwTmp);
                rt.addValue("Height", imhTmp);
                rt.addValue("Match", String.valueOf(match));
                rt.show("Results");
            }

            mr.runMacro("setBatchMode(false);", "");
            roiMan.runCommand("Show All");
        }
        catch(Exception e) {
            IJ.log("Show data (search max point) failed: " + e.getMessage());
        }
        finally {
            if(impBin != null) {
                impBin.close();
            }
            if(impLab != null) {
                impLab.close();
            }
        }
    }

    private void substractedFromOne(float[] srcdst) {
        int num = srcdst.length;

        for(int i = 0; i < num; i++) {
            srcdst[i] = 1 - srcdst[i];
        }
    }

    private void binaryFloat(float[] srcdst, float thr) {
        int num = srcdst.length;

        for(int i = 0; i < num; i++) {
            if(thr <= srcdst[i]) {
                srcdst[i] = 255;
            }
            else {
                srcdst[i] = 0;
            }
        }
    }

    private void searchMaxPoint(float[] src, int bx, int by, int w, int h, int imw, float[] pointMax) {
        pointMax[0] = (float)bx;
        pointMax[1] = (float)by;
        pointMax[2] = (float)src[bx + by * imw];

        for(int y = by; y < by + h; y++) {
            for(int x = bx; x < bx + w; x++) {
                float s = src[x + y * imw];

                if(pointMax[2] < s) {
                    pointMax[0] = (float)x;
                    pointMax[1] = (float)y;
                    pointMax[2] = s;
                }
            }
        }
    }
}