import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.gui.Plot;
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
 * Control UVC camera using VideoCapture function.
 */
public class OCUtil_CntrlUvcCamera implements ExtendedPlugInFilter {
    // const var.
    private final int FLAGS = NO_IMAGE_REQUIRED;
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
    private static int indCapApi = 1;
    private static boolean enCalcStat = true;
    private static int max_results = 100;
    private static boolean enProfile = true;
    private static int wait_time = 100;
    private static boolean enOneShot = false;
    private static volatile boolean isRunning = false;

    // var.
    private String className = null;
    public JDialog diag_free = null;
    private ResultsTable tblResults = null;
    private Plot plot = null;
    private boolean flag_fin_loop = false;
    private boolean ini_verticalProfile = false;
    private ImagePlus impPlot = null;
    private final Mat dummy = new Mat();

    // Instance variables
    private VideoCapture src_cap = null;
    
    // Static variables for image display
    private static ImagePlus imp_dsp = null;
    private static int[] impdsp_intarray = null;

    @Override
    public int showDialog(ImagePlus arg0, String cmd, PlugInFilterRunner arg2) {
        className = cmd.trim();
        
        // Warn if already running
        if (isRunning) {
            GenericDialog gd = new GenericDialog(className + "...");
            gd.addMessage("Camera is currently running.");
            gd.addMessage("Stop the current session before changing settings.");
            gd.showDialog();
            return DONE;
        }
        
        GenericDialog gd = new GenericDialog(className + "...");

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

            device = (int)gd.getNextNumber();
            width = (int)gd.getNextNumber();
            height = (int)gd.getNextNumber();
            indCapApi = (int)gd.getNextChoiceIndex();
            enCalcStat = gd.getNextBoolean();
            max_results = (int)gd.getNextNumber();
            enProfile = (boolean)gd.getNextBoolean();
            Prefs.verticalProfile = (boolean)gd.getNextBoolean();
            wait_time = (int)gd.getNextNumber();
            enOneShot = (boolean)gd.getNextBoolean();

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
        // Check for duplicate execution
        if (isRunning) {
            IJ.error(className, "Already running. Please stop the current session first.");
            return;
        }
               
        isRunning = true;
        boolean bret;
        Mat src_mat = new Mat();

        try {
            // ----- stop dialog during continuous grabbing -----
            diag_free = new JDialog(diag_free, className, false);
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

            // Initialize camera (OneShot mode reuses cache, normal mode creates new instance)
            src_cap = OCV__LoadLibrary.GetCamera(
                device, width, height, 
                INT_CAP_APIS[indCapApi], 
                !enOneShot  // false for OneShot (cache), true for normal mode (new instance)
            );

            // Get actual camera width and height
            width = OCV__LoadLibrary.GetCachedCameraWidth();
            height = OCV__LoadLibrary.GetCachedCameraHeight();

            imp_dsp = IJ.createImage(className, width, height, 1, 24);
            impdsp_intarray = (int[])imp_dsp.getChannelProcessor().getPixels();
            imp_dsp.show();

            // show stop dialog
            if(!enOneShot) {
                diag_free.setVisible(true);
            }

            // run
            for(;;) {
                if(flag_fin_loop) {
                    break;
                }

                // grab
                imp_dsp.startTiming();
                
                if (enOneShot) {
                    // Drop old frames
                    int dropFrames = 1;
                    for (int i = 0; i < dropFrames; i++) {                        
                        src_cap.read(dummy);                        
                    }                    
                }
                
                bret = src_cap.read(src_mat);
                IJ.showTime(imp_dsp, imp_dsp.getStartTime(), className + " : ");

                if(!bret) {
                    OCV__LoadLibrary.MarkCameraUnhealthy();
                    throw new RuntimeException("Error occurred in grabbing.");
                }

                if(src_mat.empty()) {
                    OCV__LoadLibrary.MarkCameraUnhealthy();
                    throw new RuntimeException("Mat is empty.");
                }

                // display
                if(!imp_dsp.isVisible()) {
                    imp_dsp.close();
                    imp_dsp = IJ.createImage(className, width, height, 1, 24);
                    impdsp_intarray = (int[])imp_dsp.getChannelProcessor().getPixels();
                    imp_dsp.show();
                }

                if(src_mat.type() == CvType.CV_8UC3) {
                    OCV__LoadLibrary.mat2intarray(src_mat, impdsp_intarray, width, height);
                }
                else {
                    OCV__LoadLibrary.MarkCameraUnhealthy();
                    throw new RuntimeException("Color camera is supported only.");
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

                    if(roi != null && (roi.getType() == Roi.LINE || roi.getType() == Roi.RECTANGLE || roi.getType() == Roi.FREEROI)) {
                        plot = OCV__LoadLibrary.GetProfilePlot(imp_dsp);
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
            
        } catch (RuntimeException e) {
            IJ.log(className + " exception: " + e.getMessage());
            // Mark camera as unhealthy on exception
            OCV__LoadLibrary.MarkCameraUnhealthy();
        } finally {
            // Resource cleanup
            if (src_mat != null) {
                src_mat.release();
            }
            
            if (dummy != null){
                dummy.release();
            }
            
            // Release camera only in normal mode
            if (!enOneShot) {
                OCV__LoadLibrary.ReleaseCamera();
            }
            
            // Set local reference to null
            src_cap = null;
            
            Prefs.verticalProfile = ini_verticalProfile;
            
            if (diag_free != null) {
                diag_free.dispose();
            }
            
            isRunning = false;
        }
    }
}