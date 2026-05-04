import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Rect2d;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgproc.Imgproc;

/**
 * Wrapper for YOLO/YOLOX ONNX models.
 * Supports three model formats selected by user:
 *   YOLO_NORMALIZED, YOLO_PIXEL, YOLOX_UNDECODED
 */
public class MyNetFromONNX {

    // -------------------------------------------------------------------------
    // Enums
    // -------------------------------------------------------------------------

    public enum ModelType {
        YOLO,
        YOLOX,
        CLASSIFICATION,
        POSE
    }

    public enum CoordFormat {
        YOLO_NORMALIZED,   // YOLO with normalized coordinates (0~1)
        YOLO_PIXEL,        // YOLO with pixel coordinates (0~inputSize)
        YOLOX_UNDECODED    // YOLOX undecoded format (tx, ty, tw, th)
    }

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private static final double SCALE_FACTOR  = 1.0 / 255.0;
    private static final Scalar MEAN_VAL      = new Scalar(0, 0, 0);
    private static final Scalar PADDING_COLOR = new Scalar(114, 114, 114);
    private static final int[]  STRIDES       = {8, 16, 32};

    // -------------------------------------------------------------------------
    // Members
    // -------------------------------------------------------------------------

    private Net     net;
    private boolean isLoaded  = false;
    private String  modelName = "";

    private Size        inputSize   = new Size(640, 640);
    private ModelType   modelType   = ModelType.YOLO;
    private CoordFormat coordFormat = CoordFormat.YOLO_PIXEL;

    private int     numClasses    = 0;
    private boolean hasObjectness = false;
    private int     objChannel    = 4;
    private int[]   outputShape   = null;

    private List<String> classNames = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Inner class: preprocessing result
    // -------------------------------------------------------------------------

    private static class PreprocessResult {
        Mat    blob;
        double ratioX;   // scaleX: inputWidth  / origWidth  (letterbox: ratioX == ratioY)
        double ratioY;   // scaleY: inputHeight / origHeight (letterbox: ratioX == ratioY)
        int    padLeft;
        int    padTop;

        void release() {
            if (blob != null) blob.release();
        }
    }

    // -------------------------------------------------------------------------
    // Setters / Getters
    // -------------------------------------------------------------------------

    public void setModelType(ModelType type)        { this.modelType   = type;   }
    public void setCoordFormat(CoordFormat format)  { this.coordFormat = format; }
    public void setInputSize(int width, int height) { this.inputSize   = new Size(width, height); }

    public boolean     isLoaded()               { return isLoaded;     }
    public String      getModelName()           { return modelName;    }
    public ModelType   getModelType()           { return modelType;    }
    public CoordFormat getCoordFormat()         { return coordFormat;  }
    public int         getNumClasses()          { return numClasses;   }
    public boolean     hasObjectness()          { return hasObjectness; }
    public int[]       getOutputShape()         { return outputShape;  }
    public int         getNumClassNamesLoaded() { return classNames.size(); }
    public Size        getInputSize()           { return inputSize;    }

    public String getModelSummary() {
        return "Model: " + modelType
             + ", Coords: " + coordFormat
             + ", Classes: " + numClasses
             + ", Objectness: " + hasObjectness;
    }

    // -------------------------------------------------------------------------
    // read() – load ONNX model and resolve numClasses via dummy inference
    // -------------------------------------------------------------------------

    public void read(String onnxPath) throws Exception {
        File f = new File(onnxPath);
        if (!f.exists()) throw new Exception("File not found: " + onnxPath);

        net = Dnn.readNetFromONNX(onnxPath);
        if (net.empty()) throw new Exception("Network is empty.");

        modelName = f.getName();
        isLoaded  = true;

        // Load class name list (.txt with same base name)
        classNames.clear();
        int dotIdx = onnxPath.lastIndexOf('.');
        String txtPath = (dotIdx > 0) ? onnxPath.substring(0, dotIdx) + ".txt"
                                      : onnxPath + ".txt";
        if (new File(txtPath).exists()) {
            try {
                for (String line : Files.readAllLines(Paths.get(txtPath))) {
                    if (!line.trim().isEmpty()) classNames.add(line.trim());
                }
            } catch (Exception e) {
                // Silent fail – class names are optional
            }
        }

        // Determine numClasses and outputShape via a dummy forward pass
        resolveOutputShape();
    }

    /**
     * Run a dummy forward pass to determine numClasses and outputShape.
     * Model type is already fixed by the user; only shape arithmetic is done here.
     */
    private void resolveOutputShape() throws Exception {
        Mat dummyImg = new Mat(
            (int) inputSize.height, (int) inputSize.width,
            org.opencv.core.CvType.CV_8UC3,
            new Scalar(128, 128, 128)
        );

        PreprocessResult preproc;
        if (modelType == ModelType.YOLOX) {
            preproc = preprocess(dummyImg, 1.0, false, true);  // letterbox enabled for YOLOX
        } else {
            preproc = preprocess(dummyImg, SCALE_FACTOR, true, true);
        }

        net.setInput(preproc.blob);
        Mat out = net.forward();

        int[] shape;
        if (out.dims() == 2) {
            shape = new int[]{out.size(0), out.size(1)};
            hasObjectness = false;
            numClasses    = out.size(1);
        } else {
            int d0 = out.size(0);
            int d1 = out.size(1);
            int d2 = out.size(2);
            shape = new int[]{d0, d1, d2};

            if (modelType == ModelType.YOLOX) {
                // Output shape: [1, 8400, numClasses+5]
                hasObjectness = true;
                numClasses    = d2 - 5;
            } else if (modelType == ModelType.POSE) {
                // Output shape: [1, 56, 8400]
                hasObjectness = false;
                numClasses    = 1;
            } else {
                // Output shape: [1, numClasses+4, 8400]
                hasObjectness = false;
                numClasses    = d1 - 4;
            }
        }
        outputShape = shape;

        preproc.release();
        out.release();
        dummyImg.release();
    }

    // -------------------------------------------------------------------------
    // preprocess() – shared by YOLO and YOLOX, behavior controlled by arguments
    //
    //   scaleFactor : 1.0/255.0 for YOLO, 1.0 for YOLOX
    //   swapRB      : true=BGR→RGB (YOLO), false=keep BGR (YOLOX)
    //   useLetterbox: true=letterbox+padding (YOLO), false=force resize (YOLOX)
    // -------------------------------------------------------------------------

    private PreprocessResult preprocess(Mat img,
                                        double scaleFactor,
                                        boolean swapRB,
                                        boolean useLetterbox) {
        int h0 = img.rows();
        int w0 = img.cols();

        Mat    prepared;
        double ratioX, ratioY;
        int    padLeft = 0;
        int    padTop  = 0;

        if (useLetterbox) {
            // Keep aspect ratio with letterbox padding
            double ratio = Math.min(inputSize.height / h0, inputSize.width / w0);
            ratioX = ratio;
            ratioY = ratio;

            int newH = (int) Math.round(h0 * ratio);
            int newW = (int) Math.round(w0 * ratio);

            Mat resized = new Mat();
            Imgproc.resize(img, resized, new Size(newW, newH), 0, 0, Imgproc.INTER_LINEAR);

            int dh  = (int) inputSize.height - newH;
            int dw  = (int) inputSize.width  - newW;
            padTop  = dh / 2;
            padLeft = dw / 2;

            prepared = new Mat();
            Core.copyMakeBorder(resized, prepared,
                                padTop, dh - padTop, padLeft, dw - padLeft,
                                Core.BORDER_CONSTANT, PADDING_COLOR);
            resized.release();

        } else {
            // Force resize (YOLOX): separate X/Y ratios, no padding
            ratioX = inputSize.width  / w0;
            ratioY = inputSize.height / h0;

            prepared = new Mat();
            Imgproc.resize(img, prepared,
                           new Size((int) inputSize.width, (int) inputSize.height),
                           0, 0, Imgproc.INTER_LINEAR);
        }

        // scaleFactor normalizes pixel values; swapRB handles BGR/RGB
        Mat blob = Dnn.blobFromImage(prepared, scaleFactor, inputSize, MEAN_VAL, swapRB, false);

        PreprocessResult result = new PreprocessResult();
        result.blob    = blob;
        result.ratioX  = ratioX;
        result.ratioY  = ratioY;
        result.padLeft = padLeft;
        result.padTop  = padTop;

        prepared.release();

        return result;
    }

    // -------------------------------------------------------------------------
    // inference() – main entry point
    // -------------------------------------------------------------------------

    public List<DetectionResult> inference(Mat image, double scoreThresh, double nmsThresh) {
        if (!isLoaded) return new ArrayList<>();

        int imgH = image.rows();
        int imgW = image.cols();

        // 1. Preprocess (parameters differ by model type)
        PreprocessResult preproc;
        if (modelType == ModelType.CLASSIFICATION) {
            preproc = preprocess(image, SCALE_FACTOR, true, false);  // simple resize for classification (no letterbox)
        } else if (modelType == ModelType.YOLOX) {
            preproc = preprocess(image, 1.0, false, true);  // letterbox enabled for YOLOX
        } else {
            preproc = preprocess(image, SCALE_FACTOR, true, true);  // letterbox for YOLO detection
        }

        // 2. Forward
        net.setInput(preproc.blob);
        Mat outputs = net.forward();

        if (modelType == ModelType.CLASSIFICATION) {
            List<DetectionResult> results = new ArrayList<>();
            Mat scores = outputs.row(0);
            Core.MinMaxLocResult mmr = Core.minMaxLoc(scores);
            double confidence = mmr.maxVal;
            int classId = (int) mmr.maxLoc.x;

            if (confidence >= scoreThresh) {
                String label = (classId < classNames.size())
                                ? classNames.get(classId)
                                : String.valueOf(classId);
                results.add(new DetectionResult(new Rect2d(0, 0, imgW, imgH), (float) confidence, classId, label));
            }
            scores.release();
            preproc.release();
            outputs.release();
            return results;
        }

        // 3. Reshape output to [N_boxes, C_channels]
        Mat predictions = parseOutput(outputs);

        // 4. Post-process (model-specific)
        List<Rect2d>  boxes    = new ArrayList<>();
        List<Float>   confs    = new ArrayList<>();
        List<Integer> classIds = new ArrayList<>();
        List<float[]> kptsList = new ArrayList<>();

        if (modelType == ModelType.YOLOX) {
            processYOLOX(predictions, predictions.rows(), predictions.cols(),
                         imgW, imgH, scoreThresh,
                         preproc.ratioX, preproc.ratioY, preproc.padLeft, preproc.padTop,
                         boxes, confs, classIds);
        } else if (modelType == ModelType.POSE) {
            processYOLOPose(predictions, predictions.rows(), predictions.cols(),
                            imgW, imgH, scoreThresh, coordFormat,
                            preproc.ratioX, preproc.padLeft, preproc.padTop,
                            boxes, confs, classIds, kptsList);
        } else {
            processYOLO(predictions, predictions.rows(), predictions.cols(),
                        imgW, imgH, scoreThresh, coordFormat,
                        preproc.ratioX, preproc.padLeft, preproc.padTop,
                        boxes, confs, classIds);
        }

        // 5. Per-class NMS
        Set<Integer>  uniqueClasses = new LinkedHashSet<>();
        for (int id : classIds) uniqueClasses.add(id);

        List<Integer> keepIndices = new ArrayList<>();
        for (int c : uniqueClasses) {
            List<Integer> classOrigIndices = new ArrayList<>();
            List<Rect2d>  classBoxes       = new ArrayList<>();
            List<Float>   classConfs       = new ArrayList<>();

            for (int i = 0; i < classIds.size(); i++) {
                if (classIds.get(i) == c) {
                    classOrigIndices.add(i);
                    classBoxes.add(boxes.get(i));
                    classConfs.add(confs.get(i));
                }
            }

            List<Integer> keep = nmsXYXY(classBoxes, classConfs, nmsThresh);
            for (int k : keep) {
                keepIndices.add(classOrigIndices.get(k));
            }
        }

        // 6. Build results
        List<DetectionResult> results = new ArrayList<>();
        for (int idx : keepIndices) {
            Rect2d box        = boxes.get(idx);
            float  confidence = confs.get(idx);
            int    classId    = classIds.get(idx);
            String label      = (classId < classNames.size())
                                ? classNames.get(classId)
                                : String.valueOf(classId);
            DetectionResult res = new DetectionResult(box, confidence, classId, label);
            if (modelType == ModelType.POSE && idx < kptsList.size()) {
                res.kpts = kptsList.get(idx);
            }
            results.add(res);
        }

        preproc.release();
        predictions.release();
        outputs.release();

        return results;
    }

    // -------------------------------------------------------------------------
    // parseOutput() – reshape net output to [N_boxes, C_channels]
    // -------------------------------------------------------------------------

    private Mat parseOutput(Mat outputs) {
        if (modelType == ModelType.YOLOX) {
            // Input: [1, 8400, C] → [8400, C]
            return outputs.reshape(1, outputs.size(1));
        } else {
            // Input: [1, C, 8400] → [C, 8400] → [8400, C]
            Mat temp       = outputs.reshape(1, outputs.size(1));
            Mat transposed = temp.t();
            temp.release();
            return transposed;
        }
    }

    // -------------------------------------------------------------------------
    // processYOLO() – post-process for YOLO (normalized or pixel)
    //   ratio : letterbox ratio (ratioX == ratioY for letterbox)
    // -------------------------------------------------------------------------

    private void processYOLO(Mat predictions, int rows, int cols,
                             int imgW, int imgH,
                             double scoreThresh, CoordFormat fmt,
                             double ratio, int padLeft, int padTop,
                             List<Rect2d> boxes, List<Float> confs, List<Integer> classIds) {

        for (int i = 0; i < rows; i++) {
            // Class scores
            Mat scores = predictions.row(i).colRange(4, cols);
            Core.MinMaxLocResult mmr = Core.minMaxLoc(scores);
            double confidence = mmr.maxVal;
            int    classId    = (int) mmr.maxLoc.x;

            if (confidence < scoreThresh) {
                scores.release();
                continue;
            }

            // Raw bbox (cx, cy, w, h)
            float[] coords = new float[4];
            predictions.row(i).colRange(0, 4).get(0, 0, coords);
            double cx = coords[0];
            double cy = coords[1];
            double w  = coords[2];
            double h  = coords[3];

            double x1, y1, x2, y2;

            if (fmt == CoordFormat.YOLO_NORMALIZED) {
                // Step 1: pixel coordinates on padded image
                double cx_px = cx * inputSize.width;
                double cy_px = cy * inputSize.height;
                double w_px  = w  * inputSize.width;
                double h_px  = h  * inputSize.height;
                // Step 2: remove padding offset
                cx_px -= padLeft;
                cy_px -= padTop;
                // Step 3: scale to original image
                double cx_orig = cx_px / ratio;
                double cy_orig = cy_px / ratio;
                double w_orig  = w_px  / ratio;
                double h_orig  = h_px  / ratio;
                // Step 4: convert to xyxy
                x1 = cx_orig - w_orig / 2;
                y1 = cy_orig - h_orig / 2;
                x2 = cx_orig + w_orig / 2;
                y2 = cy_orig + h_orig / 2;

            } else { // YOLO_PIXEL
                // Step 1: remove padding offset
                double cx_px = cx - padLeft;
                double cy_px = cy - padTop;
                // Step 2: scale to original image
                double cx_orig = cx_px / ratio;
                double cy_orig = cy_px / ratio;
                double w_orig  = w    / ratio;
                double h_orig  = h    / ratio;
                // Step 3: convert to xyxy
                x1 = cx_orig - w_orig / 2;
                y1 = cy_orig - h_orig / 2;
                x2 = cx_orig + w_orig / 2;
                y2 = cy_orig + h_orig / 2;
            }

            // Clip to image boundaries
            x1 = Math.max(0, Math.min(x1, imgW));
            y1 = Math.max(0, Math.min(y1, imgH));
            x2 = Math.max(0, Math.min(x2, imgW));
            y2 = Math.max(0, Math.min(y2, imgH));

            // Skip invalid boxes
            if (x2 <= x1 || y2 <= y1) {
                scores.release();
                continue;
            }

            boxes.add(new Rect2d(x1, y1, x2 - x1, y2 - y1));
            confs.add((float) confidence);
            classIds.add(classId);

            scores.release();
        }
    }

    // -------------------------------------------------------------------------
    // processYOLOPose() – post-process for YOLO Pose
    // -------------------------------------------------------------------------

    private void processYOLOPose(Mat predictions, int rows, int cols,
                                 int imgW, int imgH,
                                 double scoreThresh, CoordFormat fmt,
                                 double ratio, int padLeft, int padTop,
                                 List<Rect2d> boxes, List<Float> confs, List<Integer> classIds, List<float[]> kptsList) {

        for (int i = 0; i < rows; i++) {
            float[] rowData = new float[cols];
            predictions.row(i).get(0, 0, rowData);
            
            double confidence = rowData[4]; // Person score is at index 4

            if (confidence < scoreThresh) {
                continue;
            }

            double cx = rowData[0];
            double cy = rowData[1];
            double w  = rowData[2];
            double h  = rowData[3];

            double x1, y1, x2, y2;
            boolean isNormalized = (fmt == CoordFormat.YOLO_NORMALIZED);

            if (isNormalized) {
                double cx_px = cx * inputSize.width;
                double cy_px = cy * inputSize.height;
                double w_px  = w  * inputSize.width;
                double h_px  = h  * inputSize.height;
                cx_px -= padLeft; cy_px -= padTop;
                double cx_orig = cx_px / ratio;
                double cy_orig = cy_px / ratio;
                double w_orig  = w_px  / ratio;
                double h_orig  = h_px  / ratio;
                x1 = cx_orig - w_orig / 2;
                y1 = cy_orig - h_orig / 2;
                x2 = cx_orig + w_orig / 2;
                y2 = cy_orig + h_orig / 2;
            } else { // YOLO_PIXEL
                double cx_px = cx - padLeft;
                double cy_px = cy - padTop;
                double cx_orig = cx_px / ratio;
                double cy_orig = cy_px / ratio;
                double w_orig  = w    / ratio;
                double h_orig  = h    / ratio;
                x1 = cx_orig - w_orig / 2;
                y1 = cy_orig - h_orig / 2;
                x2 = cx_orig + w_orig / 2;
                y2 = cy_orig + h_orig / 2;
            }

            // Clip to image boundaries
            x1 = Math.max(0, Math.min(x1, imgW));
            y1 = Math.max(0, Math.min(y1, imgH));
            x2 = Math.max(0, Math.min(x2, imgW));
            y2 = Math.max(0, Math.min(y2, imgH));

            if (x2 <= x1 || y2 <= y1) {
                continue;
            }

            // Keypoints (5 to end)
            int numKpts = (cols - 5) / 3;
            float[] processedKpts = new float[numKpts * 3];
            for (int k = 0; k < numKpts; k++) {
                double kx = rowData[5 + k * 3];
                double ky = rowData[5 + k * 3 + 1];
                double kconf = rowData[5 + k * 3 + 2];
                
                if (isNormalized) {
                    kx = kx * inputSize.width;
                    ky = ky * inputSize.height;
                }
                
                kx = (kx - padLeft) / ratio;
                ky = (ky - padTop) / ratio;
                
                processedKpts[k * 3] = (float) kx;
                processedKpts[k * 3 + 1] = (float) ky;
                processedKpts[k * 3 + 2] = (float) kconf;
            }

            boxes.add(new Rect2d(x1, y1, x2 - x1, y2 - y1));
            confs.add((float) confidence);
            classIds.add(0); // class 0 for person
            kptsList.add(processedKpts);
        }
    }

    // -------------------------------------------------------------------------
    // processYOLOX() – post-process for YOLOX (undecoded grid format)
    //   ratioX / ratioY : X/Y scale factors
    //                     (letterbox: ratioX == ratioY, force resize: ratioX != ratioY)
    // -------------------------------------------------------------------------

    private void processYOLOX(Mat predictions, int rows, int cols,
                              int imgW, int imgH,
                              double scoreThresh,
                              double ratioX, double ratioY, int padLeft, int padTop,
                              List<Rect2d> boxes, List<Float> confs, List<Integer> classIds) {

        int[][] gridStride = makeGridStride((int) inputSize.width, STRIDES);
        int[]   grid       = gridStride[0];
        int[]   stride     = gridStride[1];

        for (int i = 0; i < rows; i++) {
            float[] data = new float[cols];
            predictions.row(i).get(0, 0, data);

            float tx  = data[0];
            float ty  = data[1];
            float tw  = data[2];
            float th  = data[3];
            float obj = data[objChannel];

            // Find best class
            float maxClsScore = -Float.MAX_VALUE;
            int   maxClsId    = 0;
            for (int c = 0; c < numClasses; c++) {
                float s = data[5 + c];
                if (s > maxClsScore) { maxClsScore = s; maxClsId = c; }
            }

            float confidence = (float)(Math.sqrt(Math.max(0, obj)) * maxClsScore);
            if (confidence < scoreThresh) continue;

            // Decode grid coordinates
            int gx = grid[i * 2];
            int gy = grid[i * 2 + 1];
            int s  = stride[i];

            double cx = (tx + gx) * s;
            double cy = (ty + gy) * s;
            double w  = Math.exp(tw) * s;
            double h  = Math.exp(th) * s;

            // Remove padding and scale to original image
            // (letterbox: ratioX == ratioY, force resize: ratioX != ratioY)
            cx = (cx - padLeft) / ratioX;
            cy = (cy - padTop)  / ratioY;
            w  =  w             / ratioX;
            h  =  h             / ratioY;

            double x1 = cx - w / 2;
            double y1 = cy - h / 2;
            double x2 = cx + w / 2;
            double y2 = cy + h / 2;

            // Clip
            x1 = Math.max(0, Math.min(x1, imgW));
            y1 = Math.max(0, Math.min(y1, imgH));
            x2 = Math.max(0, Math.min(x2, imgW));
            y2 = Math.max(0, Math.min(y2, imgH));

            if (x2 <= x1 || y2 <= y1) continue;

            boxes.add(new Rect2d(x1, y1, x2 - x1, y2 - y1));
            confs.add(confidence);
            classIds.add(maxClsId);
        }
    }

    // -------------------------------------------------------------------------
    // nmsXYXY() – NMS (shared by YOLO & YOLOX, called per-class from inference())
    // -------------------------------------------------------------------------

    private List<Integer> nmsXYXY(List<Rect2d> boxes, List<Float> scores, double iouThresh) {
        // Convert to xyxy
        List<double[]> xyxy = new ArrayList<>();
        for (Rect2d b : boxes) {
            xyxy.add(new double[]{b.x, b.y, b.x + b.width, b.y + b.height});
        }

        // Sort by score descending
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < scores.size(); i++) idx.add(i);
        idx.sort((a, b) -> Float.compare(scores.get(b), scores.get(a)));

        List<Integer> keep = new ArrayList<>();
        while (!idx.isEmpty()) {
            int i = idx.get(0);
            keep.add(i);
            if (idx.size() == 1) break;

            List<Integer> rest   = new ArrayList<>(idx.subList(1, idx.size()));
            List<Integer> newIdx = new ArrayList<>();
            for (int j : rest) {
                if (iouXYXY(xyxy.get(i), xyxy.get(j)) <= iouThresh) newIdx.add(j);
            }
            idx = newIdx;
        }
        return keep;
    }

    // -------------------------------------------------------------------------
    // iouXYXY() – IoU between two xyxy boxes (shared)
    // -------------------------------------------------------------------------

    private double iouXYXY(double[] a, double[] b) {
        double ix1 = Math.max(a[0], b[0]);
        double iy1 = Math.max(a[1], b[1]);
        double ix2 = Math.min(a[2], b[2]);
        double iy2 = Math.min(a[3], b[3]);

        double iw    = Math.max(0.0, ix2 - ix1);
        double ih    = Math.max(0.0, iy2 - iy1);
        double inter = iw * ih;

        double areaA = Math.max(0.0, a[2] - a[0]) * Math.max(0.0, a[3] - a[1]);
        double areaB = Math.max(0.0, b[2] - b[0]) * Math.max(0.0, b[3] - b[1]);
        double union = areaA + areaB - inter + 1e-9;

        return inter / union;
    }

    // -------------------------------------------------------------------------
    // makeGridStride() – generate grid and stride arrays for YOLOX decoding
    // -------------------------------------------------------------------------

    private int[][] makeGridStride(int imgSize, int[] strides) {
        List<Integer> gridList   = new ArrayList<>();
        List<Integer> strideList = new ArrayList<>();

        for (int s : strides) {
            int gridH = imgSize / s;
            int gridW = imgSize / s;
            for (int y = 0; y < gridH; y++) {
                for (int x = 0; x < gridW; x++) {
                    gridList.add(x);
                    gridList.add(y);
                    strideList.add(s);
                }
            }
        }

        int[] grid   = new int[gridList.size()];
        int[] stride = new int[strideList.size()];
        for (int i = 0; i < gridList.size();   i++) grid[i]   = gridList.get(i);
        for (int i = 0; i < strideList.size(); i++) stride[i] = strideList.get(i);

        return new int[][]{grid, stride};
    }

    // -------------------------------------------------------------------------
    // DetectionResult – result data class
    // -------------------------------------------------------------------------

    public static class DetectionResult {
        public Rect2d box;
        public float  confidence;
        public int    classId;
        public String label;
        public float[] kpts;

        public DetectionResult(Rect2d box, float confidence, int classId, String label) {
            this.box        = box;
            this.confidence = confidence;
            this.classId    = classId;
            this.label      = label;
        }
    }
}
