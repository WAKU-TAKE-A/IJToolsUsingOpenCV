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
 * grabCut.
 */
public class OCV_InteractiveGrabCut implements ij.plugin.filter.ExtendedPlugInFilter {
    // constant var.
    private static final int FLAGS = DOES_RGB;

    // static var.
    private static int iter = 3;
    private static double opacity = 30;
    private static boolean enRepMskWithOut = false;

    // var.
    private String titleCmd = null;
    private ImagePlus impSrc = null;
    private ImagePlus impOv = null;
    private Mat matSrcOrg = null;
    private int imwSrc = 0;
    private int imhSrc = 0;
    private String titleSrc = "";
    private Macro_Runner MR = new Macro_Runner();

    private ImagePlus impMsk = null;
    private Mat matMsk = null;
    private String titleMsk = "";

    private Roi roi = null;
    private Rect rect = null;

    public JDialog diagFree = null;
    boolean flagFinLoop = false;
    boolean flagBgcol = false;
    boolean flagFgcol = false;
    boolean flagRun = false;
    boolean flagCancel = false;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        titleCmd = command.trim();
        GenericDialog gd = new GenericDialog(titleCmd + "...");

        gd.addNumericField("iterCount", iter, 0);
        gd.addNumericField("opacity", opacity, 1);
        gd.addCheckbox("enable_replace_mask_with_output", enRepMskWithOut);

        gd.showDialog();

        if(gd.wasCanceled()) {
            impSrc.setRoi(roi);
            releaseResources();
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
            OCV__LoadLibrary.logError("OCV_InteractiveGrabCut", "Library is not loaded.");
            return DONE;
        }

        if(imp == null) {
            IJ.noImage();
            return DONE;
        }

        if(imp.getRoi() == null) {
            OCV__LoadLibrary.logError(titleCmd, "Set a rectangular roi.");
            return DONE;
        }

        impSrc = imp;
        imwSrc = impSrc.getWidth();
        imhSrc = impSrc.getHeight();
        titleSrc = imp.getTitle();
        roi = imp.getRoi();
        imp.killRoi();
        impOv = imp.duplicate();
        Rectangle rectJava = roi.getBounds();
        rect = new Rect(rectJava.x, rectJava.y, rectJava.width, rectJava.height);

        return FLAGS;
    }

    @Override
    public void run(ImageProcessor ip) {
        try {
            // ----- Dialog -----
            diagFree = new JDialog(diagFree, titleCmd, false);
            JButton butBgCont = new JButton("Background color");
            JButton butFgCont = new JButton("Foreground color");
            JButton butRunCont = new JButton("Run");
            JButton butCancelCont = new JButton("Cancel");
            JButton butFinCont = new JButton("Finish");

            butFgCont.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    flagFgcol = true;
                }
            });

            butBgCont.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    flagBgcol = true;
                }
            });

            butRunCont.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    flagRun = true;
                }
            });

            butCancelCont.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    flagCancel = true;
                    diagFree.dispose();
                }
            });

            diagFree.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    flagFinLoop = true;
                }
            });

            butFinCont.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    flagFinLoop = true;
                    diagFree.dispose();
                }
            });

            diagFree.setLayout(new GridLayout(5, 1));
            diagFree.add(butFgCont);
            diagFree.add(butBgCont);
            diagFree.add(butRunCont);
            diagFree.add(butCancelCont);
            diagFree.add(butFinCont);
            diagFree.pack();
            diagFree.setSize(200, 240);
            // ----- End of dialog -----

            //  Create a new mask
            IJ.showStatus("Create new mask. (GC_INIT_WITH_RECT)");

            matSrcOrg = convertRgbImage(impSrc);
            createNewMask();

            // Show dialog
            diagFree.setVisible(true);

            // Edit mask
            IJ.showStatus("Start editing mask.");

            for(;;) {
                if(flagFinLoop) {
                    copyMat2ImpGray(matMsk, impMsk);
                    impMsk.repaintWindow();
                    break;
                }

                if(flagFgcol) {
                    MR.runMacro("setForegroundColor(253, 253, 253);", "");
                    IJ.showStatus("Set foreground color(253).");
                    flagFgcol = false;
                }

                if(flagBgcol) {
                    MR.runMacro("setForegroundColor(60, 60, 60);", "");
                    IJ.showStatus("Set background color(60).");
                    flagBgcol = false;
                }

                if(flagRun && impSrc.isVisible() && impMsk.isVisible()) {
                    IJ.showStatus("Do GrabCut with mask.");
                    doGrabCutWithMask();
                    flagRun = false;
                }

                if(flagCancel) {
                    int[] arrSrc = (int[])impSrc.getProcessor().getPixels();
                    OCV__LoadLibrary.mat2intarray(matSrcOrg, arrSrc, imwSrc, imhSrc);
                    impSrc.setRoi(roi);
                    impSrc.repaintWindow();
                    break;
                }

                if(!impSrc.isVisible()) {
                    IJ.showStatus("Restore " + titleSrc + ".");
                    restoreSrc();
                }

                if(!impMsk.isVisible()) {
                    IJ.showStatus("Restore " + titleMsk + ".");
                    createNewMask();
                }

                OCV__LoadLibrary.Wait(100);
            }
        }
        catch(Exception e) {
            OCV__LoadLibrary.logError(titleCmd, "Interactive GrabCut failed (" + e.getMessage() + ")");
        }
        finally {
            releaseResources();
        }
    }

    private void createNewMask() {
        Mat bgdModel = null;
        Mat fgdModel = null;

        try {
            impMsk = null;
            titleMsk = WindowManager.getUniqueName("GrabCut_Mask");
            impMsk = IJ.createImage(titleMsk, imwSrc, imhSrc, 1, 8);
            byte[] arrMsk = (byte[])impMsk.getProcessor().getPixels();
            
            if(matMsk != null) {
                matMsk.release();
            }
            matMsk = new Mat(imhSrc, imwSrc, CvType.CV_8UC1);

            bgdModel = new Mat();
            fgdModel = new Mat();
            Imgproc.grabCut(matSrcOrg, matMsk, rect, bgdModel, fgdModel, iter, Imgproc.GC_INIT_WITH_RECT);
            matMsk.get(0, 0, arrMsk);

            ImageRoi imroi = new ImageRoi(0, 0, impOv.getProcessor());
            ((ImageRoi)imroi).setOpacity(opacity / 100.0);
            impMsk.setRoi(imroi);

            impMsk.show();
            AND(impSrc, matMsk);
        }
        catch(Exception e) {
            OCV__LoadLibrary.logError(titleCmd, "Create new mask failed (" + e.getMessage() + ")");
        }
        finally {
            if(bgdModel != null) {
                bgdModel.release();
            }
            if(fgdModel != null) {
                fgdModel.release();
            }
        }
    }

    private void doGrabCutWithMask() {
        Mat bgdModel = null;
        Mat fgdModel = null;
        Mat tempMask = null;

        try {
            tempMask = convertMask(impMsk);
            
            if(matMsk != null) {
                matMsk.release();
            }
            matMsk = tempMask;
            tempMask = null;

            bgdModel = new Mat();
            fgdModel = new Mat();
            Imgproc.grabCut(matSrcOrg, matMsk, rect, bgdModel, fgdModel, iter, Imgproc.GC_INIT_WITH_MASK);

            copyMat2ImpRGB(matSrcOrg, impSrc);
            AND(impSrc, matMsk);

            impSrc.repaintWindow();
            impMsk.repaintWindow();
        }
        catch(Exception e) {
            OCV__LoadLibrary.logError(titleCmd, "GrabCut with mask failed (" + e.getMessage() + ")");
        }
        finally {
            if(bgdModel != null) {
                bgdModel.release();
            }
            if(fgdModel != null) {
                fgdModel.release();
            }
            if(tempMask != null) {
                tempMask.release();
            }
        }
    }

    private void restoreSrc() {
        try {
            impSrc = null;
            impSrc = IJ.createImage(titleSrc, imwSrc, imhSrc, 1, 24);
            impSrc.show();

            doGrabCutWithMask();
        }
        catch(Exception e) {
            OCV__LoadLibrary.logError(titleCmd, "Restore source failed (" + e.getMessage() + ")");
        }
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
        int[] arrSrc = (int[])src.getProcessor().getPixels();
        Mat matDst = new Mat(h, w, CvType.CV_8UC3);
        OCV__LoadLibrary.intarray2mat(arrSrc, matDst, w, h);
        return matDst;
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

    private void copyMat2ImpRGB(Mat src, ImagePlus dst) {
        int w = dst.getWidth();
        int h = dst.getHeight();
        int[] arrDst = (int[])dst.getProcessor().getPixels();
        OCV__LoadLibrary.mat2intarray(src, arrDst, w, h);
    }

    private void copyMat2ImpGray(Mat src, ImagePlus dst) {
        byte[] arrDst = (byte[])dst.getProcessor().getPixels();
        src.get(0, 0, arrDst);
    }

    private void releaseResources() {
        if(matSrcOrg != null) {
            matSrcOrg.release();
            matSrcOrg = null;
        }
        if(matMsk != null) {
            matMsk.release();
            matMsk = null;
        }
    }
}