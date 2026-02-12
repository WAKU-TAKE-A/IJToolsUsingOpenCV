import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.measure.ResultsTable; // 追加
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfRect2d;
import org.opencv.core.Rect2d;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgproc.Imgproc;

/*
 * The MIT License
 *
 * Copyright 2026.
 */

/**
 * Object Detection using YOLOv8 ONNX model.
 */
public class OCV_ReadNetFromONNX implements ExtendedPlugInFilter {
    // --- Constants ---
    private static final int FLAGS = DOES_RGB | DOES_8G;
    private static final double SCALE_FACTOR = 1.0 / 255.0;
    private static final Size INPUT_SIZE = new Size(640, 640);
    private static final Scalar MEAN_VAL = new Scalar(0, 0, 0);
    
    private static final String[] CLASS_NAMES = {
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
        "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
        "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack",
        "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
        "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
        "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
        "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake",
        "chair", "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop",
        "mouse", "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
        "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
    };

    private static String modelPath = "";
    private static double scoreThreshold = 0.25;
    private static double nmsThreshold = 0.45;

    private ImagePlus imp;
    private Net net;

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        GenericDialog gd = new GenericDialog("YOLOv8 ONNX Detection");
        gd.addStringField("Model Path (.onnx)", modelPath, 40);
        gd.addNumericField("Score Threshold", scoreThreshold, 2);
        gd.addNumericField("NMS Threshold", nmsThreshold, 2);
        
        gd.showDialog();
        if (gd.wasCanceled()) return DONE;

        modelPath = gd.getNextString();
        scoreThreshold = gd.getNextNumber();
        nmsThreshold = gd.getNextNumber();
        
        if (modelPath.isEmpty() || !new File(modelPath).exists()) {
            IJ.error("Model file not found: " + modelPath);
            return DONE;
        }
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
        try {
            net = Dnn.readNetFromONNX(modelPath);
            IJ.showStatus("YOLO Inference...");
        } catch (Exception e) {
            IJ.error("Failed to load model: " + e.getMessage());
            return;
        }

        Mat image = OCV__LoadLibrary.ip2mat(ip);
        if (image.channels() == 3) {
            Imgproc.cvtColor(image, image, Imgproc.COLOR_BGR2RGB); 
        } else if (image.channels() == 1) {
            Imgproc.cvtColor(image, image, Imgproc.COLOR_GRAY2RGB);
        }

        int imgW = image.cols();
        int imgH = image.rows();

        Mat blob = Dnn.blobFromImage(image, SCALE_FACTOR, INPUT_SIZE, MEAN_VAL, false, false);
        net.setInput(blob);
        Mat outputs = net.forward();
        
        Mat output2D = outputs.reshape(1, 84); 
        Mat predictions = output2D.t();
        
        int rows = predictions.rows();
        List<Rect2d> boxesList = new ArrayList<>();
        List<Float> confidencesList = new ArrayList<>();
        List<Integer> classIdsList = new ArrayList<>();

        for (int i = 0; i < rows; i++) {
            Mat scores = predictions.row(i).colRange(4, 84);
            Core.MinMaxLocResult result = Core.minMaxLoc(scores);
            double confidence = result.maxVal;
            int classId = (int) result.maxLoc.x;

            if (confidence > scoreThreshold) {
                float[] coords = new float[4];
                predictions.row(i).colRange(0, 4).get(0, 0, coords);
                
                double rawCx = coords[0];
                double rawCy = coords[1];
                double rawW = coords[2];
                double rawH = coords[3];

                double cx, cy, w, h;
                if (rawW <= 1.0 && rawH <= 1.0) {
                    cx = rawCx * imgW;
                    cy = rawCy * imgH;
                    w  = rawW * imgW;
                    h  = rawH * imgH;
                } else {
                    double xFactor = (double) imgW / INPUT_SIZE.width;
                    double yFactor = (double) imgH / INPUT_SIZE.height;
                    cx = rawCx * xFactor;
                    cy = rawCy * yFactor;
                    w  = rawW * xFactor;
                    h  = rawH * yFactor;
                }

                double left = cx - 0.5 * w;
                double top  = cy - 0.5 * h;

                boxesList.add(new Rect2d(left, top, w, h));
                confidencesList.add((float) confidence);
                classIdsList.add(classId);
            }
        }

        if (boxesList.isEmpty()) {
            IJ.showStatus("No objects detected.");
            return;
        }

        MatOfRect2d boxesMat = new MatOfRect2d();
        boxesMat.fromList(boxesList);
        MatOfFloat confidencesMat = new MatOfFloat();
        confidencesMat.fromList(confidencesList);
        MatOfInt indicesMat = new MatOfInt();
        
        Dnn.NMSBoxes(boxesMat, confidencesMat, (float)scoreThreshold, (float)nmsThreshold, indicesMat);
        
        // --- 修正箇所: ResultsTable と RoiManager の初期化 ---
        // 指示通り、既存プラグインの作法に合わせる
        ResultsTable rt = OCV__LoadLibrary.GetResultsTable(true); // リセット
        RoiManager roiMan = OCV__LoadLibrary.GetRoiManager(true, true); // リセット

        if (indicesMat.rows() > 0) {
            int[] indices = indicesMat.toArray();
            for (int idx : indices) {
                Rect2d box = boxesList.get(idx);
                int classId = classIdsList.get(idx);
                float conf = confidencesList.get(idx);
                String className = (classId >= 0 && classId < CLASS_NAMES.length) ? CLASS_NAMES[classId] : "Unknown";
                
                // RoiManager への追加
                Roi roi = new Roi(box.x, box.y, box.width, box.height);
                String label = String.format("%s: %.2f", className, conf);
                roi.setName(label);
                roi.setStrokeColor(getColorForClass(classId));
                roiMan.addRoi(roi);
                
                // --- 修正箇所: ResultsTable への出力 ---
                rt.incrementCounter();
                rt.addValue("Label", className);
                rt.addValue("Confidence", conf);
                rt.addValue("X", box.x);
                rt.addValue("Y", box.y);
                rt.addValue("Width", box.width);
                rt.addValue("Height", box.height);
            }
            rt.show("Results");
            roiMan.runCommand(imp, "Show All with labels");
            IJ.showStatus("Detection Complete: " + indices.length + " objects.");
        } else {
            IJ.showStatus("No objects left after NMS.");
        }
    }
    
    private Color getColorForClass(int classId) {
        float hue = (classId * 0.618033988749895f) % 1.0f; 
        return Color.getHSBColor(hue, 1.0f, 1.0f);
    }
}