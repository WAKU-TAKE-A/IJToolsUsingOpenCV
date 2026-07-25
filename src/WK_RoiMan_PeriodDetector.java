import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.ProfilePlot;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import java.awt.Frame;

public class WK_RoiMan_PeriodDetector implements ExtendedPlugInFilter {
    private static final int FLAGS = DOES_ALL;

    // GUI option strings
    private final String[] DETREND_STR = new String[] { "MOVING_AVERAGE", "LINEAR_REGRESSION" };
    private final String[] PEAK_STR = new String[] { "HEIGHT", "PROMINENCE" };

    // Static variables to keep last used settings
    private static int ind_detrend = 1;
    private static int ma_window_size = 31;
    private static int min_period = 2;
    private static int max_period = 0;
    private static int ind_peak = 1;
    private static double relative_threshold = 0.5;
    private static boolean vertical_profile = false;
    private static boolean enable_refresh_data = false;

    // Instance variables
    private String className;
    private ImagePlus impSrc = null;
    private RoiManager roiMan = null;
    private int[] selectedIndexes = null;
    private ResultsTable rsTbl = null;
    private boolean ini_verticalProfile = false;

    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner pifr) {
        className = "WK_RoiMan_PeriodDetector";
        GenericDialog gd = new GenericDialog(className + " ...");

        gd.addChoice("detrend_method", DETREND_STR, DETREND_STR[ind_detrend]);
        gd.addNumericField("ma_window_size", ma_window_size, 0);
        gd.addNumericField("min_period", min_period, 0);
        gd.addNumericField("max_period", max_period, 0);
        gd.addChoice("peak_method", PEAK_STR, PEAK_STR[ind_peak]);
        gd.addNumericField("relative_threshold", relative_threshold, 2);
        gd.addCheckbox("vertical_profile", vertical_profile);
        gd.addCheckbox("enable_refresh_data", enable_refresh_data);

        gd.showDialog();

        if (gd.wasCanceled()) {
            return DONE;
        } else {
            ind_detrend = (int)gd.getNextChoiceIndex();
            ma_window_size = (int)gd.getNextNumber();
            min_period = (int)gd.getNextNumber();
            max_period = (int)gd.getNextNumber();
            ind_peak = (int)gd.getNextChoiceIndex();
            relative_threshold = (double)gd.getNextNumber();
            vertical_profile = (boolean)gd.getNextBoolean();
            enable_refresh_data = (boolean)gd.getNextBoolean();

            if (enable_refresh_data) {
                if (rsTbl != null) {
                    rsTbl.reset();
                }
            }

            return FLAGS;
        }
    }

    @Override
    public void setNPasses(int arg0) {
        // do nothing
    }

    @Override
    public int setup(String arg0, ImagePlus imp) {
        if (imp == null) {
            IJ.noImage();
            return DONE;
        } else {
            impSrc = imp;
            rsTbl = getResultsTable(false);
            roiMan = getRoiManager(false, true);

            if (roiMan.getCount() == 0) {
                IJ.log((className != null ? className : "WK_RoiMan_PeriodDetector") + " error: ROI is vacant.");
                return DONE;
            }

            selectedIndexes = roiMan.getSelectedIndexes();
            if (selectedIndexes == null || selectedIndexes.length == 0) {
                // If nothing is selected, process all ROIs instead of just index 0.
                // This makes it easier to run the macro that adds 3 ROIs and processes them all at once.
                selectedIndexes = new int[roiMan.getCount()];
                for (int i = 0; i < roiMan.getCount(); i++) {
                    selectedIndexes[i] = i;
                }
            }

            ini_verticalProfile = Prefs.verticalProfile;

            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip) {
        try {
            Prefs.verticalProfile = vertical_profile;

            PeriodDetector.DetrendMethod detrendMethod = (ind_detrend == 0) 
                    ? PeriodDetector.DetrendMethod.MOVING_AVERAGE 
                    : PeriodDetector.DetrendMethod.LINEAR_REGRESSION;
                    
            PeakDetector.PeakMethod peakMethod = (ind_peak == 0) 
                    ? PeakDetector.PeakMethod.HEIGHT 
                    : PeakDetector.PeakMethod.PROMINENCE;

            for (int i = 0; i < selectedIndexes.length; i++) {
                int index = selectedIndexes[i];
                Roi roi = roiMan.getRoi(index);
                String roiName = roiMan.getName(index);
                if (roiName == null || roiName.isEmpty()) {
                    roiName = "ROI_" + index;
                }

                impSrc.setRoi(roi);

                ProfilePlot profPlot = new ProfilePlot(impSrc, Prefs.verticalProfile);
                double[] profile = profPlot.getProfile();

                if (profile == null || profile.length < 2) {
                    IJ.log(className + " error: Cannot get valid profile from " + roiName);
                    continue;
                }

                PeriodResult result = PeriodDetector.detectPeriod(
                        profile,
                        detrendMethod,
                        ma_window_size,
                        min_period,
                        max_period,
                        peakMethod,
                        relative_threshold
                );

                if (result != null) {
                    rsTbl.incrementCounter();
                    rsTbl.addValue("ROI_Name", roiName);
                    rsTbl.addValue("Period(px)", result.integerPeriod);
                    rsTbl.addValue("Period_Subpx", result.subpixelPeriod);
                    rsTbl.addValue("Confidence", result.confidence);
                    rsTbl.addValue("Profile_Len", profile.length);
                } else {
                    IJ.log(className + " warning: Period detection failed for " + roiName);
                }
            }

            if (rsTbl.getCounter() > 0) {
                rsTbl.show("Results");
            }

            // Clean up ROI from image
            impSrc.killRoi();

        } catch (Exception e) {
            IJ.log(className + " exception: " + e.getMessage());
        } finally {
            Prefs.verticalProfile = ini_verticalProfile;
        }
    }

    private ResultsTable getResultsTable(boolean enReset) {
        ResultsTable rt = ResultsTable.getResultsTable();
        if (rt == null || rt.getCounter() == 0) {
            rt = new ResultsTable();
        }
        if (enReset) {
            rt.reset();
        }
        return rt;
    }

    private RoiManager getRoiManager(boolean enReset, boolean enShowNone) {
        RoiManager rm = RoiManager.getInstance();
        if (rm == null) rm = new RoiManager();
        if (rm.isVisible() == false) {
            rm.setVisible(true);
        }
        if (enReset) {
            rm.reset();
        }
        if (enShowNone) {
            rm.runCommand("Show None");
        }
        return rm;
    }
}
