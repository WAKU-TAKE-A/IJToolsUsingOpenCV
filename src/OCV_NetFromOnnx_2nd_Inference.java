import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.measure.ResultsTable;
import ij.plugin.filter.ExtendedPlugInFilter;
import static ij.plugin.filter.PlugInFilter.DOES_8G;
import static ij.plugin.filter.PlugInFilter.DOES_RGB;
import static ij.plugin.filter.PlugInFilter.DONE;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import java.awt.Color;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.List;
import org.opencv.core.Mat;

/**
 * 2nd step: Perform inference using the loaded model.
 */
public class OCV_NetFromOnnx_2nd_Inference implements ExtendedPlugInFilter {

    private static final int FLAGS = DOES_RGB;

    private static double  scoreThreshold   = 0.25;
    private static double  nmsThreshold     = 0.45;
    private static double  kptThreshold     = 0.50;
    private static boolean showKeypoints    = true;
    private static boolean showSkeleton     = true;
    private static boolean showResultsTable = true;
    private static boolean enableRefreshData = true;
    private static boolean showLog          = true;

    private ImagePlus imp;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        if (OCV__LoadLibrary.MyNet == null || !OCV__LoadLibrary.MyNet.isLoaded()) {
            OCV__LoadLibrary.logError("OCV_NetFromOnnx_2nd_Inference", "Model is not loaded. Please run '1st_Read' first.");
            return DONE;
        }

        GenericDialog gd = new GenericDialog("Inference");
        gd.addNumericField("score_threshold",    scoreThreshold,   2);
        
        boolean isClassification = (OCV__LoadLibrary.MyNet.getModelType() == MyNetFromONNX.ModelType.CLASSIFICATION);
        boolean isPose = (OCV__LoadLibrary.MyNet.getModelType() == MyNetFromONNX.ModelType.POSE);
        if (!isClassification) {
            gd.addNumericField("nms_threshold",      nmsThreshold,     2);
        }
        if (isPose) {
            gd.addNumericField("kpt_threshold",      kptThreshold,     2);
            gd.addCheckbox("show_keypoints",         showKeypoints);
            gd.addCheckbox("show_skeleton",          showSkeleton);
        }
        gd.addCheckbox("enable_results_table",   showResultsTable);
        gd.addCheckbox("enable_refresh_data",    enableRefreshData);
        gd.addCheckbox("enable_log",             showLog);
        gd.showDialog();

        if (gd.wasCanceled()) return DONE;

        scoreThreshold    = gd.getNextNumber();
        if (!isClassification) {
            nmsThreshold      = gd.getNextNumber();
        }
        if (isPose) {
            kptThreshold      = gd.getNextNumber();
            showKeypoints     = gd.getNextBoolean();
            showSkeleton      = gd.getNextBoolean();
        }
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
            OCV__LoadLibrary.logError("OCV_NetFromOnnx_2nd_Inference", "OpenCV Library is not loaded.");
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

        // Check for ROI cropping
        java.awt.Rectangle roiRect = null;
        Roi roi = imp.getRoi();
        ImageProcessor targetIp = ip;
        boolean isClassification = (OCV__LoadLibrary.MyNet.getModelType() == MyNetFromONNX.ModelType.CLASSIFICATION);
        
        if (isClassification && roi != null && roi.getType() == Roi.RECTANGLE) {
            roiRect = roi.getBounds();
            targetIp = ip.crop();
            if (showLog) {
                IJ.log("Cropped ROI: " + roiRect.x + ", " + roiRect.y + ", " + roiRect.width + "x" + roiRect.height);
            }
        }

        // Run inference
        Mat image = OCV__LoadLibrary.ip2mat(targetIp);
        List<MyNetFromONNX.DetectionResult> results =
            OCV__LoadLibrary.MyNet.inference(image, scoreThreshold, nmsThreshold);
            
        // release the cropped image Mat!
        image.release();
        
        // offset the results if cropped
        if (roiRect != null && (roiRect.x != 0 || roiRect.y != 0)) {
            for (MyNetFromONNX.DetectionResult res : results) {
                res.box.x += roiRect.x;
                res.box.y += roiRect.y;
            }
        }

        if (showLog) {
            IJ.log("Inference complete. Detected: " + results.size() + " objects.");
            IJ.log("=".repeat(60));
        }

        // Prepare output
        ResultsTable rt = null;
        if (showResultsTable) {
            rt = OCV__LoadLibrary.GetResultsTable(enableRefreshData);
        }

        RoiManager roiMan = OCV__LoadLibrary.GetRoiManager(enableRefreshData, true);

        if (!results.isEmpty()) {
            boolean isPose = (OCV__LoadLibrary.MyNet.getModelType() == MyNetFromONNX.ModelType.POSE);
            int poseCount = 1;
            
            for (MyNetFromONNX.DetectionResult res : results) {
                // Add to RoiManager
                Roi resultRoi = new Roi(res.box.x, res.box.y, res.box.width, res.box.height);
                String baseName = String.format("%s: %.2f", res.label, res.confidence);
                resultRoi.setName(isPose ? poseCount + "-Box" : baseName);
                resultRoi.setStrokeColor(getColorForClass(res.classId));
                roiMan.addRoi(resultRoi);

                double avgConf = 0, minConf = 1.0, maxConf = 0.0;
                int validKptCount = 0;

                if (isPose && res.kpts != null) {
                    List<Float> validX = new ArrayList<>();
                    List<Float> validY = new ArrayList<>();
                    boolean[] isValid = new boolean[17];
                    
                    for (int i = 0; i < 17; i++) {
                        if (i * 3 + 2 < res.kpts.length) {
                            float kconf = res.kpts[i * 3 + 2];
                            if (kconf >= kptThreshold) {
                                validX.add(res.kpts[i * 3]);
                                validY.add(res.kpts[i * 3 + 1]);
                                isValid[i] = true;
                                
                                avgConf += kconf;
                                minConf = Math.min(minConf, kconf);
                                maxConf = Math.max(maxConf, kconf);
                                validKptCount++;
                            }
                        }
                    }
                    
                    if (validKptCount > 0) {
                        avgConf /= validKptCount;
                        
                        if (showKeypoints) {
                            // Add PointRoi
                            float[] xArr = new float[validX.size()];
                            float[] yArr = new float[validY.size()];
                            for (int i = 0; i < validX.size(); i++) {
                                xArr[i] = validX.get(i);
                                yArr[i] = validY.get(i);
                            }
                            PointRoi ptRoi = new PointRoi(xArr, yArr);
                            ptRoi.setName(poseCount + "-Kpt");
                            ptRoi.setStrokeColor(Color.YELLOW);
                            roiMan.addRoi(ptRoi);
                        }
                        
                        if (showSkeleton) {
                            // Add ShapeRoi for skeleton
                            GeneralPath path = new GeneralPath();
                            int[][] skeleton = {
                                {3, 1}, {1, 2}, {2, 4}, {1, 0}, {0, 2},
                                {5, 6},
                                {5, 7}, {7, 9},
                                {6, 8}, {8, 10},
                                {5, 11}, {11, 12}, {12, 6},
                                {11, 13}, {13, 15},
                                {12, 14}, {14, 16}
                            };
                            
                            boolean pathAdded = false;
                            for (int[] bone : skeleton) {
                                int p1 = bone[0];
                                int p2 = bone[1];
                                if (isValid[p1] && isValid[p2]) {
                                    path.moveTo(res.kpts[p1 * 3], res.kpts[p1 * 3 + 1]);
                                    path.lineTo(res.kpts[p2 * 3], res.kpts[p2 * 3 + 1]);
                                    pathAdded = true;
                                }
                            }
                            
                            if (pathAdded) {
                                ShapeRoi skeletonRoi = new ShapeRoi(path);
                                skeletonRoi.setName(poseCount + "-Skel");
                                skeletonRoi.setStrokeColor(Color.MAGENTA);
                                roiMan.addRoi(skeletonRoi);
                            }
                        }
                    }
                    poseCount++;
                }

                // Add to ResultsTable
                if (showResultsTable && rt != null) {
                    rt.incrementCounter();
                    rt.addValue("Image",      imageTitle);
                    if (roiRect != null) {
                        rt.addValue("ROI",    "Yes");
                    }
                    rt.addValue("Label",      res.label);
                    rt.addValue("Confidence", res.confidence);
                    rt.addValue("X",          res.box.x);
                    rt.addValue("Y",          res.box.y);
                    rt.addValue("Width",      res.box.width);
                    rt.addValue("Height",     res.box.height);
                    
                    if (isPose) {
                        if (validKptCount > 0) {
                            rt.addValue("Kpt_Avg", avgConf);
                            rt.addValue("Kpt_Min", minConf);
                            rt.addValue("Kpt_Max", maxConf);
                        } else {
                            rt.addValue("Kpt_Avg", Double.NaN);
                            rt.addValue("Kpt_Min", Double.NaN);
                            rt.addValue("Kpt_Max", Double.NaN);
                        }
                    }
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
