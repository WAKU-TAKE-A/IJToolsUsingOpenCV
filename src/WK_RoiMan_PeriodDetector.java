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
import ij.process.FloatPolygon;
import ij.process.ImageProcessor;
import java.awt.Frame;

public class WK_RoiMan_PeriodDetector implements ExtendedPlugInFilter {
    private static final int FLAGS = DOES_ALL;

    // GUI option strings
    private final String[] DETREND_STR = new String[] { "MOVING_AVERAGE", "LINEAR_REGRESSION" };
    private final String[] PEAK_STR = new String[] { "HEIGHT", "PROMINENCE" };

    // Static variables to keep last used settings (also persisted via Prefs)
    private static int ind_detrend = 1;
    private static int ma_window_size = 31;
    private static int min_period = 2;
    private static int max_period = 0;
    private static int ind_peak = 1;
    private static double relative_threshold = 0.5;
    private static double min_prominence = 0.20;
    private static int max_divisor = PeriodDetector.DEFAULT_MAX_DIVISOR;
    private static double half_ratio = PeriodRefiner.DEFAULT_HALF_RATIO;
    private static boolean vertical_profile = false;
    private static boolean acf_smoothing = true;
    private static boolean enable_refresh_data = false;

    private static final String PREFS_PREFIX = "WK_RoiMan_PeriodDetector.";
    private static boolean prefsLoaded = false;

    // Instance variables
    private String className = "WK_RoiMan_PeriodDetector";
    private ImagePlus impSrc = null;
    private RoiManager roiMan = null;
    private int[] selectedIndexes = null;
    private ResultsTable rsTbl = null;
    private boolean ini_verticalProfile = false;
    private Roi originalRoi = null;

    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner pifr) {
        className = "WK_RoiMan_PeriodDetector";
        loadPrefs();

        GenericDialog gd = new GenericDialog(className + " ...");

        gd.addChoice("detrend_method", DETREND_STR, DETREND_STR[ind_detrend]);
        gd.addNumericField("ma_window_size", ma_window_size, 0);
        gd.addNumericField("min_period", min_period, 0);
        gd.addNumericField("max_period", max_period, 0);
        gd.addChoice("peak_method", PEAK_STR, PEAK_STR[ind_peak]);
        gd.addNumericField("relative_threshold", relative_threshold, 2);
        gd.addNumericField("min_prominence", min_prominence, 2);
        gd.addNumericField("max_divisor", max_divisor, 0);
        gd.addNumericField("half_ratio", half_ratio, 2);
        gd.addCheckbox("vertical_profile", vertical_profile);
        gd.addCheckbox("acf_smoothing", acf_smoothing);
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
            min_prominence = (double)gd.getNextNumber();
            max_divisor = (int)gd.getNextNumber();
            half_ratio = (double)gd.getNextNumber();
            vertical_profile = (boolean)gd.getNextBoolean();
            acf_smoothing = (boolean)gd.getNextBoolean();
            enable_refresh_data = (boolean)gd.getNextBoolean();

            String error = validateSettings();
            if (error != null) {
                IJ.error(className, error);
                return DONE;
            }

            if (ind_detrend == 0 && max_period > 0 && ma_window_size <= max_period) {
                IJ.log(className + " warning: ma_window_size (" + ma_window_size
                        + ") <= max_period (" + max_period
                        + "). The moving average may follow and cancel the periodic signal."
                        + " A window larger than the period is recommended.");
            }

            savePrefs();

            if (enable_refresh_data) {
                if (rsTbl != null) {
                    rsTbl.reset();
                }
            }

            return FLAGS;
        }
    }

    /**
     * 設定値の検証。不正ならエラーメッセージを返す。
     * ma_window_size の偶数は奇数へ丸める（Detrend の推奨仕様に合わせる）。
     */
    private String validateSettings() {
        if (ind_detrend == 0) {
            if (ma_window_size < 3) {
                return "ma_window_size must be >= 3 for MOVING_AVERAGE.";
            }
            if (ma_window_size % 2 == 0) {
                ma_window_size += 1;
            }
        }
        if (!Double.isFinite(relative_threshold) || relative_threshold < 0 || relative_threshold > 1) {
            return "relative_threshold must be between 0 and 1.";
        }
        if (!Double.isFinite(min_prominence) || min_prominence < 0 || min_prominence > 2) {
            return "min_prominence must be between 0 and 2 (0 = disabled).";
        }
        if (min_period < 1) {
            return "min_period must be >= 1.";
        }
        if (max_period < 0 || (max_period > 0 && max_period <= min_period)) {
            return "max_period must be 0 (auto) or greater than min_period.";
        }
        if (max_divisor < 2) {
            max_divisor = 2;
        }
        if (max_divisor > 10) {
            max_divisor = 10;
        }
        if (!Double.isFinite(half_ratio) || half_ratio < 0 || half_ratio > 1) {
            return "half_ratio must be between 0 and 1.";
        }
        return null;
    }

    private void loadPrefs() {
        if (prefsLoaded) {
            return;
        }
        prefsLoaded = true;
        ind_detrend = (int)Prefs.get(PREFS_PREFIX + "ind_detrend", ind_detrend);
        ma_window_size = (int)Prefs.get(PREFS_PREFIX + "ma_window_size", ma_window_size);
        min_period = (int)Prefs.get(PREFS_PREFIX + "min_period", min_period);
        max_period = (int)Prefs.get(PREFS_PREFIX + "max_period", max_period);
        ind_peak = (int)Prefs.get(PREFS_PREFIX + "ind_peak", ind_peak);
        relative_threshold = Prefs.get(PREFS_PREFIX + "relative_threshold", relative_threshold);
        min_prominence = Prefs.get(PREFS_PREFIX + "min_prominence", min_prominence);
        max_divisor = (int)Prefs.get(PREFS_PREFIX + "max_divisor", max_divisor);
        half_ratio = Prefs.get(PREFS_PREFIX + "half_ratio", half_ratio);
        vertical_profile = Prefs.get(PREFS_PREFIX + "vertical_profile", vertical_profile);
        acf_smoothing = Prefs.get(PREFS_PREFIX + "acf_smoothing", acf_smoothing);
        enable_refresh_data = Prefs.get(PREFS_PREFIX + "enable_refresh_data", enable_refresh_data);
        // Guard against out-of-range indices from a hand-edited prefs file.
        if (ind_detrend < 0 || ind_detrend >= DETREND_STR.length) ind_detrend = 1;
        if (ind_peak < 0 || ind_peak >= PEAK_STR.length) ind_peak = 1;
    }

    private void savePrefs() {
        Prefs.set(PREFS_PREFIX + "ind_detrend", ind_detrend);
        Prefs.set(PREFS_PREFIX + "ma_window_size", ma_window_size);
        Prefs.set(PREFS_PREFIX + "min_period", min_period);
        Prefs.set(PREFS_PREFIX + "max_period", max_period);
        Prefs.set(PREFS_PREFIX + "ind_peak", ind_peak);
        Prefs.set(PREFS_PREFIX + "relative_threshold", relative_threshold);
        Prefs.set(PREFS_PREFIX + "min_prominence", min_prominence);
        Prefs.set(PREFS_PREFIX + "max_divisor", max_divisor);
        Prefs.set(PREFS_PREFIX + "half_ratio", half_ratio);
        Prefs.set(PREFS_PREFIX + "vertical_profile", vertical_profile);
        Prefs.set(PREFS_PREFIX + "acf_smoothing", acf_smoothing);
        Prefs.set(PREFS_PREFIX + "enable_refresh_data", enable_refresh_data);
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

            Roi roi = imp.getRoi();
            originalRoi = (roi != null) ? (Roi)roi.clone() : null;

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
                String roiName = roiMan.getName(index);
                if (roiName == null || roiName.isEmpty()) {
                    roiName = "ROI_" + index;
                }

                // Per-ROI error handling: one failing ROI must not abort the rest.
                double centerX = Double.NaN;
                double centerY = Double.NaN;
                try {
                    Roi roi = roiMan.getRoi(index);
                    double[] center = centerOf(roi);
                    centerX = center[0];
                    centerY = center[1];
                    impSrc.setRoi(roi);

                    ProfilePlot profPlot = new ProfilePlot(impSrc, Prefs.verticalProfile);
                    double[] profile = profPlot.getProfile();

                    if (profile == null || profile.length < 2) {
                        IJ.log(className + " error: Cannot get valid profile from " + roiName);
                        writeRow(rsTbl, roiName, centerX, centerY, null, "Failed: invalid profile");
                        continue;
                    }

                    int effectiveWindow = ma_window_size;
                    if (detrendMethod == PeriodDetector.DetrendMethod.MOVING_AVERAGE) {
                        effectiveWindow = clampWindowToProfile(ma_window_size, profile.length);
                    }

                    PeriodResult result = PeriodDetector.detectPeriod(
                            profile,
                            detrendMethod,
                            effectiveWindow,
                            min_period,
                            max_period,
                            peakMethod,
                            relative_threshold,
                            max_divisor,
                            half_ratio,
                            acf_smoothing,
                            min_prominence
                    );

                    if (result != null) {
                        writeRow(rsTbl, roiName, centerX, centerY, result, "OK");
                    } else {
                        IJ.log(className + " warning: Period detection failed for " + roiName);
                        writeRow(rsTbl, roiName, centerX, centerY, null, "Failed: no period detected");
                    }
                } catch (Exception e) {
                    IJ.log(className + " exception for " + roiName + ": " + e.getMessage());
                    writeRow(rsTbl, roiName, centerX, centerY, null, "Failed: " + e.getMessage());
                }
            }

            if (rsTbl.getCounter() > 0) {
                rsTbl.show("Results");
            }

        } catch (Exception e) {
            IJ.log(className + " exception: " + e.getMessage());
        } finally {
            Prefs.verticalProfile = ini_verticalProfile;
            // Restore the ROI the image had before this plugin ran.
            if (impSrc != null) {
                if (originalRoi != null) {
                    impSrc.setRoi((Roi)originalRoi.clone());
                } else {
                    impSrc.killRoi();
                }
            }
        }
    }

    /** プロファイル長に合わせて移動平均の窓を丸める（奇数・3以上を維持できる範囲で）。 */
    private int clampWindowToProfile(int window, int profileLength) {
        int w = window;
        if (w > profileLength) {
            w = profileLength;
            if (w % 2 == 0) {
                w -= 1;
            }
        }
        if (w < 1) {
            w = 1;
        }
        return w;
    }

    /**
     * ROI の中心座標（画像座標、ピクセル）。ROI の輪郭点の平均位置を用いるため、
     * 矩形・回転矩形では中心、線 ROI では線分の中点と一致する。
     * （外接矩形の中心は、線 ROI では線幅の分だけ中点からずれるため用いない。）
     * ROI が取得できない場合は {NaN, NaN} を返す。
     */
    static double[] centerOf(Roi roi) {
        if (roi == null) {
            return new double[] { Double.NaN, Double.NaN };
        }
        FloatPolygon fp = roi.getFloatPolygon();
        if (fp == null || fp.npoints == 0) {
            return new double[] { Double.NaN, Double.NaN };
        }
        double sx = 0;
        double sy = 0;
        for (int i = 0; i < fp.npoints; i++) {
            sx += fp.xpoints[i];
            sy += fp.ypoints[i];
        }
        return new double[] { sx / fp.npoints, sy / fp.npoints };
    }

    /**
     * 結果表に1行書く。列構成は成功・失敗によらず常に同じ
     * （ROI_Name, Center_X, Center_Y, Period, Confidence, Status）。
     * CSV やスクリプトから安定して読めるよう、列を条件付きで出し分けない。
     * Center_X / Center_Y は ROI の中心座標（画像座標、ピクセル）で、取得できない場合は NaN。
     * Period はピクセル単位。実寸への換算は校正値を使って読み手側で行う。
     */
    static void writeRow(ResultsTable tbl, String roiName, double centerX, double centerY,
                         PeriodResult result, String status) {
        tbl.incrementCounter();
        tbl.addValue("ROI_Name", roiName);
        tbl.addValue("Center_X", centerX);
        tbl.addValue("Center_Y", centerY);
        tbl.addValue("Period", result != null ? result.period : Double.NaN);
        tbl.addValue("Confidence", result != null ? result.confidence : Double.NaN);
        tbl.addValue("Status", status);
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
