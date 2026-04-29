import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.ExtendedPlugInFilter;
import static ij.plugin.filter.PlugInFilter.DOES_8G;
import static ij.plugin.filter.PlugInFilter.DOES_RGB;
import static ij.plugin.filter.PlugInFilter.DONE;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import java.awt.Color;
import java.util.List;
import org.opencv.core.Mat;

/**
 * 2nd step: Perform inference using the loaded model.
 */
public class OCV_NetFromOnnx_2nd_Inference implements ExtendedPlugInFilter {

    private static final int FLAGS = DOES_RGB | DOES_8G;

    private static double  scoreThreshold   = 0.25;
    private static double  nmsThreshold     = 0.45;
    private static boolean showResultsTable = true;
    private static boolean enableRefreshData = true;
    private static boolean showLog          = true;

    private ImagePlus imp;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        if (OCV__LoadLibrary.MyNet == null || !OCV__LoadLibrary.MyNet.isLoaded()) {
            IJ.error("Model is not loaded. Please run '1st_Read' first.");
            return DONE;
        }

        GenericDialog gd = new GenericDialog("Inference");
        gd.addNumericField("score_threshold",    scoreThreshold,   2);
        gd.addNumericField("nms_threshold",      nmsThreshold,     2);
        gd.addCheckbox("enable_results_table",   showResultsTable);
        gd.addCheckbox("enable_refresh_data",    enableRefreshData);
        gd.addCheckbox("enable_log",             showLog);
        gd.showDialog();

        if (gd.wasCanceled()) return DONE;

        scoreThreshold    = gd.getNextNumber();
        nmsThreshold      = gd.getNextNumber();
        showResultsTable  = gd.getNextBoolean();
        enableRefreshData = gd.getNextBoolean();
        showLog           = gd.getNextBoolean();

        return FLAGS;
    }

    @Override
    public void setNPasses(int nPasses) {}

    @Override
    public int setup(String arg, ImagePlus imp) {
        if (!OCV__LoadLibrary.isLoad()) {
            IJ.error("OpenCV Library is not loaded.");
            return DONE;
        }
        this.imp = imp;
        return FLAGS;
    }

    @Override
    public void run(ImageProcessor ip) {
        if (showLog) {
            IJ.log("=".repeat(60));
            IJ.log("Starting inference...");
            IJ.log("Format: " + OCV__LoadLibrary.MyNet.getModelSummary());
        }

        String imageTitle = imp.getTitle();

        // Run inference
        Mat image = OCV__LoadLibrary.ip2mat(ip);
        List<MyNetFromONNX.DetectionResult> results =
            OCV__LoadLibrary.MyNet.inference(image, scoreThreshold, nmsThreshold);

        if (showLog) {
            IJ.log("Inference complete. Detected: " + results.size() + " objects.");
            IJ.log("=".repeat(60));
        }

        // Prepare output
        ResultsTable rt = null;
        if (showResultsTable) {
            rt = OCV__LoadLibrary.GetResultsTable(enableRefreshData);
        }

        RoiManager roiMan = OCV__LoadLibrary.GetRoiManager(true, true);

        if (!results.isEmpty()) {
            for (MyNetFromONNX.DetectionResult res : results) {
                // Add to RoiManager
                Roi roi = new Roi(res.box.x, res.box.y, res.box.width, res.box.height);
                roi.setName(String.format("%s: %.2f", res.label, res.confidence));
                roi.setStrokeColor(getColorForClass(res.classId));
                roiMan.addRoi(roi);

                // Add to ResultsTable
                if (showResultsTable && rt != null) {
                    rt.incrementCounter();
                    rt.addValue("Image",      imageTitle);
                    rt.addValue("Label",      res.label);
                    rt.addValue("Confidence", res.confidence);
                    rt.addValue("X",          res.box.x);
                    rt.addValue("Y",          res.box.y);
                    rt.addValue("Width",      res.box.width);
                    rt.addValue("Height",     res.box.height);
                }
            }

            if (showResultsTable && rt != null) {
                rt.show("Results");
            }

            roiMan.runCommand(imp, "Show All with labels");
            IJ.showStatus("Detected: " + results.size() + " objects.");

        } else {
            IJ.showStatus("No objects detected.");
        }
    }

    private Color getColorForClass(int classId) {
        float hue = (classId * 0.618033988749895f) % 1.0f;
        return Color.getHSBColor(hue, 1.0f, 1.0f);
    }
}
