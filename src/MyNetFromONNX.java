
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
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

/**
 * Wrapper class for YOLOv8 ONNX model.
 * Handles model loading, class names, and inference logic.
 */
public class MyNetFromONNX {
    // Constants
    private static final Size INPUT_SIZE = new Size(640, 640);
    private static final Scalar MEAN_VAL = new Scalar(0, 0, 0);
    private static final double SCALE_FACTOR = 1.0 / 255.0;

    // Members
    private Net net;
    private List<String> classNames;
    private boolean isLoaded = false;
    private String modelName = "";

    public MyNetFromONNX() {
        classNames = new ArrayList<>();
    }

    /**
     * Load ONNX model and corresponding class name file (.txt).
     * @param onnxPath Full path to the .onnx file
     * @throws Exception if file not found or load failed
     */
    public void read(String onnxPath) throws Exception {
        File f = new File(onnxPath);
        if (!f.exists()) {
            throw new Exception("File not found: " + onnxPath);
        }

        // 1. Load Model
        // Using readNetFromONNX to support YOLOv8 structure
        this.net = Dnn.readNetFromONNX(onnxPath);
        if (this.net.empty()) {
             throw new Exception("Network is empty.");
        }
        
        this.modelName = f.getName();
        this.isLoaded = true;

        // 2. Load Class Names (.txt)
        // Expecting a file with the same name but .txt extension
        String txtPath = "";
        int dotIndex = onnxPath.lastIndexOf('.');
        if (dotIndex > 0) {
            txtPath = onnxPath.substring(0, dotIndex) + ".txt";
        } else {
            txtPath = onnxPath + ".txt";
        }

        File txtFile = new File(txtPath);
        classNames.clear();
        
        if (txtFile.exists()) {
            try {
                List<String> lines = Files.readAllLines(Paths.get(txtPath));
                for (String line : lines) {
                    if (!line.trim().isEmpty()) {
                        classNames.add(line.trim());
                    }
                }
            } catch (Exception e) {
                // If loading text fails, we proceed without names (will use IDs)
            }
        }
    }

    /**
     * Run inference on the given image.
     * @param image Input image (OpenCV Mat)
     * @param scoreThresh Confidence threshold
     * @param nmsThresh NMS threshold
     * @return List of detection results
     */
    public List<DetectionResult> inference(Mat image, double scoreThresh, double nmsThresh) {
        if (!isLoaded || net == null) {
            return new ArrayList<>();
        }

        // Preprocessing: Convert BGR/Gray to RGB
        Mat blobInput = image.clone();
        if (image.channels() == 3) {
            Imgproc.cvtColor(image, blobInput, Imgproc.COLOR_BGR2RGB);
        } else if (image.channels() == 1) {
            Imgproc.cvtColor(image, blobInput, Imgproc.COLOR_GRAY2RGB);
        }

        int imgW = image.cols();
        int imgH = image.rows();

        // Create Blob
        Mat blob = Dnn.blobFromImage(blobInput, SCALE_FACTOR, INPUT_SIZE, MEAN_VAL, false, false);
        net.setInput(blob);

        // Forward Pass
        Mat outputs = net.forward(); // Output shape: [1, 84, 8400]

        // Reshape and Transpose: [1, 84, 8400] -> [84, 8400] -> [8400, 84]
        Mat output2D = outputs.reshape(1, 84);
        Mat predictions = output2D.t();
        
        int rows = predictions.rows();
        List<Rect2d> boxesList = new ArrayList<>();
        List<Float> confidencesList = new ArrayList<>();
        List<Integer> classIdsList = new ArrayList<>();

        // Parse predictions
        for (int i = 0; i < rows; i++) {
            // Scores are from index 4 to 84
            Mat scores = predictions.row(i).colRange(4, 84);
            Core.MinMaxLocResult result = Core.minMaxLoc(scores);
            double confidence = result.maxVal;
            int classId = (int) result.maxLoc.x;

            if (confidence > scoreThresh) {
                // Get coordinates (cx, cy, w, h) from index 0 to 4
                // MUST use float array to match CV_32F data type
                float[] coords = new float[4];
                predictions.row(i).colRange(0, 4).get(0, 0, coords);
                
                double rawCx = coords[0];
                double rawCy = coords[1];
                double rawW = coords[2];
                double rawH = coords[3];

                double cx, cy, w, h;
                
                // Logic: Normalized Coordinates (0-1) vs Pixel Coordinates
                if (rawW <= 1.0 && rawH <= 1.0) {
                    // Normalized -> Scale to image size
                    cx = rawCx * imgW;
                    cy = rawCy * imgH;
                    w  = rawW * imgW;
                    h  = rawH * imgH;
                } else {
                    // Pixel coordinates (640 base) -> Scale by resize factor
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

        // NMS (Non-Maximum Suppression)
        List<DetectionResult> results = new ArrayList<>();
        if (boxesList.isEmpty()) {
            return results;
        }

        MatOfRect2d boxesMat = new MatOfRect2d();
        boxesMat.fromList(boxesList);
        MatOfFloat confidencesMat = new MatOfFloat();
        confidencesMat.fromList(confidencesList);
        MatOfInt indicesMat = new MatOfInt();

        Dnn.NMSBoxes(boxesMat, confidencesMat, (float)scoreThresh, (float)nmsThresh, indicesMat);

        if (indicesMat.rows() > 0) {
            int[] indices = indicesMat.toArray();
            for (int idx : indices) {
                Rect2d box = boxesList.get(idx);
                int classId = classIdsList.get(idx);
                float conf = confidencesList.get(idx);
                
                String label;
                if (!classNames.isEmpty() && classId >= 0 && classId < classNames.size()) {
                    label = classNames.get(classId);
                } else {
                    // Fallback: 1-based index if names are missing
                    label = String.valueOf(classId + 1);
                }
                
                results.add(new DetectionResult(box, conf, classId, label));
            }
        }
        
        return results;
    }

    public boolean isLoaded() {
        return isLoaded;
    }

    public String getModelName() {
        return modelName;
    }

    // Inner class for result data
    public static class DetectionResult {
        public Rect2d box;
        public float confidence;
        public int classId;
        public String label;

        public DetectionResult(Rect2d box, float confidence, int classId, String label) {
            this.box = box;
            this.confidence = confidence;
            this.classId = classId;
            this.label = label;
        }
    }
}
