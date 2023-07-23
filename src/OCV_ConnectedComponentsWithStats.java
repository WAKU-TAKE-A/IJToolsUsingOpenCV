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
    private static int type_ind = 1;
    private static boolean enOutImg = false;
    private static boolean enWand = false;

    // var.
    private ImagePlus impSrc = null;

    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner pifr) {
        GenericDialog gd = new GenericDialog(cmd.trim() + "...");

        gd.addChoice("connectivity", TYPE_STR, TYPE_STR[type_ind]);
        gd.addCheckbox("enable_output_labeled_image", enOutImg);
        gd.addCheckbox("enable_select_roi_by_dowand", enWand);

        gd.showDialog();

        if(gd.wasCanceled()) {
            return DONE;
        }
        else {
            type_ind = (int)gd.getNextChoiceIndex();
            enOutImg = (boolean)gd.getNextBoolean();
            enWand = (boolean)gd.getNextBoolean();

            return FLAGS;
        }
    }

    @Override
    public void setNPasses(int i) {
        // do nothing
    }

    @Override
    public int setup(String string, ImagePlus imp) {
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
        // src
        int imw = ip.getWidth();
        int imh = ip.getHeight();
        byte[] src_arr = (byte[])ip.getPixels();
        Mat src_mat = new Mat(imh, imw, CvType.CV_8UC1);

        // dst
        String titleDst = WindowManager.getUniqueName(impSrc.getTitle() + "_Connect" + String.valueOf(TYPE_INT[type_ind]));
        ImagePlus impDst = new ImagePlus(titleDst, new FloatProcessor(imw, imh));
        float[] dst_arr = (float[]) impDst.getChannelProcessor().getPixels();
        Mat dst_mat_32s = new Mat(imh, imw, CvType.CV_32S);
        Mat dst_mat_32f = new Mat(imh, imw, CvType.CV_32F);
        Mat stats_mat = new Mat();
        Mat cens_mat = new Mat();

        // run
        src_mat.put(0, 0, src_arr);
        int output_con = Imgproc.connectedComponentsWithStats(src_mat, dst_mat_32s, stats_mat, cens_mat, TYPE_INT[type_ind], CvType.CV_32S);
        dst_mat_32s.convertTo(dst_mat_32f, CvType.CV_32F);
        dst_mat_32f.get(0, 0, dst_arr);

        // show data
        if(1 < output_con) {
            showData(dst_arr, imw, imh, output_con, stats_mat, cens_mat);
        }

        // finish
        if(1 < output_con && enOutImg) {
            impDst.show();
        }
        else {
            impDst.close();
        }
    }

    private void showData(float[] dst_arr, int imw, int imh, int output_con, Mat stats_mat, Mat cens_mat) {
        int num_lab = output_con - 1;
        Rectangle[] rects = new Rectangle[output_con];
        int[] areas = new int[output_con];
        double[] cens = new double[output_con * 2];

        cens_mat.get(0, 0, cens);

        for(int i = 0; i < output_con; i++) {
            rects[i] = new Rectangle((int)(stats_mat.get(i, 0)[0]), (int)(stats_mat.get(i, 1)[0]), (int)(stats_mat.get(i, 2)[0]), (int)(stats_mat.get(i, 3)[0]));
            areas[i] = (int)(stats_mat.get(i, 4)[0]);
        }

        ResultsTable rt = OCV__LoadLibrary.GetResultsTable(true);
        RoiManager roiManager = OCV__LoadLibrary.GetRoiManager(true, true);
        Macro_Runner mr = new Macro_Runner();

        mr.runMacro("setBatchMode(true);", "");

        for(int i = 1; i < output_con; i++) {
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
            int[] chk = new int[num_lab + 1];
            int[] x_doWand = new int[num_lab + 1];
            int[] y_doWand = new int[num_lab + 1];
            String type = TYPE_STR[type_ind];

            for(int y = 0; y < imh; y++) {
                for(int x = 0; x < imw; x++) {
                    int val = (int)dst_arr[x + y * imw];

                    if(val != 0 && chk[val] == 0) {
                        chk[val] = 1;
                        x_doWand[val] = x;
                        y_doWand[val] = y;
                    }
                }
            }

            for(int i = 1; i < num_lab + 1; i++) {
                mr.runMacro("doWand(" + String.valueOf(x_doWand[i]) + ", " + String.valueOf(y_doWand[i]) + ", 0.0, \"" + type + "\");", "");
                roiManager.runCommand("add");
                roiManager.rename(i - 1, "no" + String.valueOf(i) + "-" + String.valueOf(areas[i]));
            }
        }

        mr.runMacro("setBatchMode(false);", "");

        rt.show("Results");
        roiManager.runCommand("show all");
    }
}
