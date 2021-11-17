import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.gui.ProfilePlot;
import ij.gui.Roi;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JButton;
import javax.swing.JDialog;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;

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
 * Control UVC camera using VideoCapture function (OpenCV4.5.3).
 */
public class OCV_CntrlUvcCamera implements ExtendedPlugInFilter {
    // const var.
    private final int FLAGS = NO_IMAGE_REQUIRED;
    // from /sources/modules/videoio/include/opencv2/videoio/videoio_c.h
    private final int CV_CAP_PROP_FRAME_WIDTH = 3;
    private final int CV_CAP_PROP_FRAME_HEIGHT = 4;
    /*
    VideoCapture API backends identifier.

    * CAP_ANY : Auto detect.
    * CAP_DSHOW  : DirectShow (via videoInput).
    * CAP_MSMF : Microsoft Media Foundation (via videoInput).
    */
    private final int CV_CAP_ANY = 0;
    private final int CV_CAP_DSHOW = 700;
    private final int  CV_CAP_MSMF = 1400;
    private final int[] INT_CAP_APIS = { CV_CAP_ANY, CV_CAP_DSHOW, CV_CAP_MSMF};
    private final String[] STR_CAP_APIS = { "Auto", "DirectShow", "MicrosoftMediaFoundation" };

    // static var.
    private static int device = 0;
    private static int width = 640;
    private static int height = 480;
    private static int indCapApi = 0;
    private static boolean enCalcStat = true;
    private static int max_results = 100;
    private static boolean enProfile = true;
    private static int wait_time = 100;
    private static boolean enOneShot = false;

    // var.
    private String title = null;
    public JDialog diag_free = null;
    private ResultsTable tblResults = null;
    private Plot plot = null;
    private boolean flag_fin_loop = false;
    private boolean ini_verticalProfile = false;
    private ImagePlus impPlot = null;

    // For speeding up.
    private static VideoCapture src_cap = null;
    private static ImagePlus imp_dsp = null;
    private static int[] impdsp_intarray = null;
    private boolean isChanged = true;

    @Override
    public int showDialog(ImagePlus arg0, String cmd, PlugInFilterRunner arg2) {
        title = cmd.trim();
        GenericDialog gd = new GenericDialog(title + "...");

        gd.addNumericField("device", device, 0);
        gd.addNumericField("width", width, 0);
        gd.addNumericField("height", height, 0);
        gd.addChoice("capture_api", STR_CAP_APIS, STR_CAP_APIS[indCapApi]);
        gd.addCheckbox("enabled_calculate_statistics", enCalcStat);
        gd.addNumericField("lines_maximum", max_results, 0);
        gd.addCheckbox("enabled_draw_profile", enProfile);
        gd.addCheckbox("vertical_profile", Prefs.verticalProfile);
        gd.addNumericField("wait_time", wait_time, 0);
        gd.addCheckbox("one_shot", enOneShot);

        gd.showDialog();

        if(gd.wasCanceled()) {
            return DONE;
        }
        else {
            ini_verticalProfile = Prefs.verticalProfile;

            int dev_bef = device;
            int w_bef = width;
            int h_bef = height;

            device = (int)gd.getNextNumber();
            width = (int)gd.getNextNumber();
            height = (int)gd.getNextNumber();
            indCapApi = (int)gd.getNextChoiceIndex();
            enCalcStat = gd.getNextBoolean();
            max_results = (int)gd.getNextNumber();
            enProfile = gd.getNextBoolean();
            Prefs.verticalProfile = gd.getNextBoolean();
            wait_time = (int)gd.getNextNumber();
            enOneShot = (boolean)gd.getNextBoolean();

            isChanged = src_cap == null || dev_bef != device || w_bef != width || h_bef != height;

            return FLAGS;
        }
    }

    @Override
    public void setNPasses(int arg0) {
        // do nothing
    }

    @Override
    public int setup(String arg0, ImagePlus arg1) {
        if(!OCV__LoadLibrary.isLoad()) {
            IJ.error("Library is not loaded.");
            return DONE;
        }

        return FLAGS;
    }

    @Override
    public void run(ImageProcessor arg0) {
        boolean bret = true;

        // ----- stop dialog during continuous grabbing -----
        diag_free = new JDialog(diag_free, title, false);
        JButton but_stop_cont = new JButton("Stop");

        but_stop_cont.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                flag_fin_loop = true;
                diag_free.dispose();
            }
        });

        diag_free.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                flag_fin_loop = true;
            }
        });

        diag_free.add(but_stop_cont);
        diag_free.setSize(100, 75);
        // ----- end of stop dialog -----

        // initialize camera
        if(isChanged) {
            src_cap = new VideoCapture();
            bret = src_cap.open(device, INT_CAP_APIS[indCapApi]);

            if(!bret) {
                IJ.error("Camera initialization is failed.");
                diag_free.dispose();
                return;
            }

            src_cap.set(CV_CAP_PROP_FRAME_WIDTH, width);
            src_cap.set(CV_CAP_PROP_FRAME_HEIGHT, height);

            // Setting the image display window
            width = (int) src_cap.get(CV_CAP_PROP_FRAME_WIDTH);
            height = (int) src_cap.get(CV_CAP_PROP_FRAME_HEIGHT);

            imp_dsp = IJ.createImage(title, width, height, 1, 24);
            impdsp_intarray = (int[])imp_dsp.getChannelProcessor().getPixels();
            imp_dsp.show();
        }

        // show stop dialog
        if(!enOneShot) {
            diag_free.setVisible(true);
        }

        Mat src_mat = new Mat();

        // run
        for(;;) {
            if(flag_fin_loop) {
                break;
            }

            // grab
            imp_dsp.startTiming();
            bret = src_cap.read(src_mat);
            IJ.showTime(imp_dsp, imp_dsp.getStartTime(), title + " : ");

            if(!bret) {
                IJ.error("Error occurred in grabbing.");
                diag_free.dispose();
                break;
            }

            if(src_mat.empty()) {
                IJ.error("Mat is empty.");
                diag_free.dispose();
                break;
            }

            // display
            if(!imp_dsp.isVisible()) {
                imp_dsp.close();
                imp_dsp = IJ.createImage(title, width, height, 1, 24);
                impdsp_intarray = (int[])imp_dsp.getChannelProcessor().getPixels();
                imp_dsp.show();
            }

            if(src_mat.type() == CvType.CV_8UC3) {
                OCV__LoadLibrary.mat2intarray(src_mat, impdsp_intarray, width, height);
            }
            else {
                IJ.error("Color camera is supported only.");
                diag_free.dispose();
                break;
            }

            imp_dsp.draw();

            // Statistics.
            if(enCalcStat) {
                ImagePlus impBuf;
                ImageStatistics st;
                Roi ro;

                int meas = Measurements.MIN_MAX;
                meas += Measurements.MEAN;
                meas += Measurements.MODE;
                meas += Measurements.STD_DEV;
                meas += Measurements.RECT;
                meas += Measurements.AREA;

                ro = imp_dsp.getRoi();

                if(ro != null) {
                    impBuf = imp_dsp.getRoi().getImage();
                    st = impBuf.getStatistics(meas);
                    tblResults = ResultsTable.getResultsTable();

                    if(tblResults == null || tblResults.getCounter() == 0) {
                        tblResults = new ResultsTable();
                    }

                    if(max_results < tblResults.getCounter()) {
                        tblResults.reset();
                    }

                    tblResults.incrementCounter();
                    tblResults.addValue("Min", st.min);
                    tblResults.addValue("Max", st.max);
                    tblResults.addValue("Mean", st.mean);
                    tblResults.addValue("Mode", st.mode);
                    tblResults.addValue("StdDev", st.stdDev);
                    tblResults.addValue("X", st.roiX);
                    tblResults.addValue("Y", st.roiY);
                    tblResults.addValue("W", st.roiWidth);
                    tblResults.addValue("H", st.roiHeight);
                    tblResults.addValue("Area", st.area);

                    tblResults.show("Results");
                }
            }

            // Profile.
            if(enProfile) {
                Roi roi = imp_dsp.getRoi();

                if(roi != null && (roi.getType() != Roi.LINE || roi.getType() != Roi.RECTANGLE)) {
                    plot = getProfilePlot(imp_dsp);
                }
                else {
                    if(plot != null) {
                        plot.dispose();
                        plot = null;
                    }
                }

                if(plot != null) {
                    if(impPlot == null) {
                        impPlot = new ImagePlus("Profile (line or rectangle)", plot.getProcessor());
                    }
                    else {
                        impPlot.setProcessor(null, plot.getProcessor());
                    }

                    impPlot.show();
                }
            }

            if(enOneShot) {
                break;
            }

            // wait
            OCV__LoadLibrary.Wait(wait_time);
        }

        Prefs.verticalProfile = ini_verticalProfile;
        diag_free.dispose();
    }

    private Plot getProfilePlot(ImagePlus imp) {
        ProfilePlot profPlot = new ProfilePlot(imp, Prefs.verticalProfile);
        double[] prof = profPlot.getProfile();

        if(prof == null || prof.length < 2) {
            return null;
        }

        String xLabel = "Distance (pixels)";
        String yLabel = "Value";

        Plot output_plot = new Plot("Profile", xLabel, yLabel);
        output_plot.add("line", prof);

        return output_plot;
    }
}
