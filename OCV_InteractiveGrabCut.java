import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.ImageRoi;
import ij.gui.Roi;
import ij.plugin.Macro_Runner;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JButton;
import javax.swing.JDialog;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.Rect;

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
 * grabCut (OpenCV4.5.3).
 */
public class OCV_InteractiveGrabCut implements ij.plugin.filter.ExtendedPlugInFilter {
    // constant var.
    private final int FLAGS = DOES_RGB;
    private final Macro_Runner MR = new Macro_Runner();

    // static var.
    private static int iter = 3;
    private static double opacity = 30;
    private static boolean enRepMskWithOut = false;

    // var.
    private String title_cmd = null;

    private ImagePlus imp_src = null;
    private ImagePlus imp_ov = null;
    private Mat mat_src_org  = null;
    private int imw_src = 0;
    private int imh_src = 0;
    private String title_src = "";

    private ImagePlus imp_msk = null;
    private Mat mat_msk = null;
    private String title_msk = "";

    private Roi roi = null;
    private Rect rect = null;

    public JDialog diag_free = null;
    boolean flag_fin_loop  = false;
    boolean flag_bgcol = false;
    boolean flag_fgcol = false;
    boolean flag_run = false;
    boolean flag_cancel = false;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        title_cmd = command.trim();
        GenericDialog gd = new GenericDialog(title_cmd + "...");

        gd.addNumericField("iterCount", iter, 0);
        gd.addNumericField("opacity", opacity, 1);
        gd.addCheckbox("enable_replace_mask_with_output", enRepMskWithOut);

        gd.showDialog();

        if(gd.wasCanceled()) {
            imp_src.setRoi(roi);
            return DONE;
        }
        else {
            iter = (int)gd.getNextNumber();
            opacity = (int)gd.getNextNumber();
            enRepMskWithOut = gd.getNextBoolean();
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

        if(imp.getRoi() == null) {
            IJ.error("Set a rectangular roi.");
            return DONE;
        }

        imp_src = imp;
        imw_src = imp_src.getWidth();
        imh_src = imp_src.getHeight();
        title_src = imp.getTitle();
        roi = imp.getRoi();
        imp.killRoi();
        imp_ov = imp.duplicate();
        Rectangle rect_java = roi.getBounds();
        rect = new Rect(rect_java.x , rect_java.y, rect_java.width, rect_java.height);

        return FLAGS;
    }

    @Override
    public void run(ImageProcessor ip) {
        // ----- Dialog -----
        diag_free = new JDialog(diag_free, title_cmd, false);
        JButton but_bg_cont = new JButton("Background color");
        JButton but_fg_cont = new JButton("Foreground color");
        JButton but_run_cont = new JButton("Run");
        JButton but_cancel_cont = new JButton("Cancel");
        JButton but_fin_cont = new JButton("Finish");

        but_fg_cont.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                flag_fgcol = true;
            }
        });

        but_bg_cont.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                flag_bgcol = true;
            }
        });

        but_run_cont.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                flag_run = true;
            }
        });

        but_cancel_cont.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                flag_cancel = true;
                diag_free.dispose();
            }
        });

        diag_free.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                flag_fin_loop = true;
            }
        });

        but_fin_cont.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                flag_fin_loop = true;
                diag_free.dispose();
            }
        });

        diag_free.setLayout(new GridLayout(5, 1));
        diag_free.add(but_fg_cont);
        diag_free.add(but_bg_cont);
        diag_free.add(but_run_cont);
        diag_free.add(but_cancel_cont);
        diag_free.add(but_fin_cont);
        diag_free.pack();
        diag_free.setSize(200, 240);
        // ----- End of dialog -----

        //  Create a new mask
        IJ.showStatus("Create new mask. (GC_INIT_WITH_RECT)");

        mat_src_org = convertRgbImage(imp_src);
        createNewMask();

        // Show dialog
        diag_free.setVisible(true);

        // Edit mask
        IJ.showStatus("Start editing mask.");

        for(;;) {
            if(flag_fin_loop) {
                copyMat2Imp_Gray(mat_msk, imp_msk);
                imp_msk.repaintWindow();
                break;
            }

            if(flag_fgcol) {
                MR.runMacro("setForegroundColor(253, 253, 253);", "");
                IJ.showStatus("Set foreground color(253).");
                flag_fgcol = false;
            }

            if(flag_bgcol) {
                MR.runMacro("setForegroundColor(60, 60, 60);", "");
                IJ.showStatus("Set background color(60).");
                flag_bgcol = false;
            }

            if(flag_run && imp_src.isVisible() && imp_msk.isVisible()) {
                IJ.showStatus("Do GrabCut with mask.");
                doGrabCut_WithMask();
                flag_run = false;
            }

            if(flag_cancel) {
                int[] arr_src = (int[])imp_src.getProcessor().getPixels();
                OCV__LoadLibrary.mat2intarray(mat_src_org, arr_src, imw_src, imh_src);
                imp_src.setRoi(roi);
                imp_src.repaintWindow();
                break;
            }

            if(!imp_src.isVisible()) {
                IJ.showStatus("Restore " + title_src + ".");
                restoreSrc();
            }

            if(!imp_msk.isVisible()) {
                IJ.showStatus("Restore " + title_msk + ".");
                createNewMask();
            }

            OCV__LoadLibrary.Wait(100);
        }
    }

    private void createNewMask() {
        imp_msk = null;
        title_msk = WindowManager.getUniqueName("GrabCut_Mask");
        imp_msk = IJ.createImage(title_msk, imw_src, imh_src, 1, 8);
        byte[] arr_msk = (byte[])imp_msk.getProcessor().getPixels();
        mat_msk = new Mat(imh_src, imw_src, CvType.CV_8UC1);

        Mat bgdModel = new Mat();
        Mat fgdModel = new Mat();
        Imgproc.grabCut(mat_src_org, mat_msk, rect, bgdModel, fgdModel, iter, Imgproc.GC_INIT_WITH_RECT);
        mat_msk.get(0, 0, arr_msk);

        ImageRoi imroi = new ImageRoi(0, 0, imp_ov.getProcessor());
        ((ImageRoi)imroi).setOpacity(opacity / 100.0);
        imp_msk.setRoi(imroi);

        imp_msk.show();
        AND(imp_src, mat_msk);
    }

    private void doGrabCut_WithMask() {
        mat_msk = convertMask(imp_msk);

        Mat bgdModel = new Mat();
        Mat fgdModel = new Mat();
        Imgproc.grabCut(mat_src_org, mat_msk, rect, bgdModel, fgdModel, iter, Imgproc.GC_INIT_WITH_MASK);

        copyMat2Imp_RGB(mat_src_org, imp_src);
        AND(imp_src, mat_msk);

        imp_src.repaintWindow();
        imp_msk.repaintWindow();
    }

    private void restoreSrc() {
        imp_src = null;
        imp_src = IJ.createImage(title_src, imw_src, imh_src, 1, 24);
        imp_src.show();

        doGrabCut_WithMask();
    }

    //  "0000 0001(1)", "0000 00011(3)" and  "1111 1101(253)" are 0xffffffff.
    // Mask 0000 0000(0x1) and multiply 0xffffffff.
    private void AND(ImagePlus srcColor, Mat msk) {
        int w = srcColor.getWidth();
        int h = srcColor.getHeight();
        int numpix = w * h;
        int[] arrSrcColor = (int[])srcColor.getProcessor().getPixels();

        byte[] arrMsk = new byte[numpix];
        msk.get(0, 0, arrMsk);

        for(int i = 0; i < numpix; i++) {
            int intMsk = ((int)arrMsk[i] & 0x1) * 0xffffffff;
            arrSrcColor[i] = intMsk & arrSrcColor[i];
        }

        srcColor.repaintWindow();
    }

    private Mat convertRgbImage(ImagePlus src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[]  arrSrc = (int[])src.getProcessor().getPixels();
        Mat mat_dst = new Mat(h, w, CvType.CV_8UC3);
        OCV__LoadLibrary.intarray2mat(arrSrc, mat_dst, w, h);
        return mat_dst;
    }

    // "0000 0000(0)" and  "0011 1100(60)" are 0.
    // "0000 0001(1)" and  "1111 1101(253)" are 1.
    // "0000 0010(2)"  is 2.
    // "0000 0011(3)"  is 3.
    // Mask 0000 0011(x3).
    private Mat convertMask(ImagePlus src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int numpix = w * h;
        byte[] arrSrc = (byte[])src.getProcessor().getPixels();
        byte[] arrDst = new byte[numpix];

        for(int i = 0; i < numpix; i++) {
            arrDst[i] = (byte)(arrSrc[i] & 0x3);
        }

        Mat matDst = new Mat(h, w, CvType.CV_8UC1);
        matDst.put(0, 0, arrDst);
        return matDst;
    }

    private void copyMat2Imp_RGB(Mat src, ImagePlus dst) {
        int w = dst.getWidth();
        int h = dst.getHeight();
        int[] arrDst = (int[])dst.getProcessor().getPixels();
        OCV__LoadLibrary.mat2intarray(src, arrDst, w, h);
    }

    private void copyMat2Imp_Gray(Mat src, ImagePlus dst) {
        byte[] arrDst = (byte[])dst.getProcessor().getPixels();
        src.get(0, 0, arrDst);
    }
}
