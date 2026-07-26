import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.core.TermCriteria;
import org.opencv.imgproc.Imgproc;
import org.opencv.calib.Calib;

/*
 * The MIT License
 *
 * Copyright 2025 Takehito Nishida.
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
 * Camera Calibration data and logic manager
 */
public class MyCameraCalibration {
    // Constants
    private static final String STR_BASE_FOLDER = "CameraCalibration";
    private static final String FILE_CAMERA_MATRIX = "CameraMatrix.mat";
    private static final String FILE_DIST_COEFFS = "DistCoeffs.mat";
    private static final String FILE_SETTINGS = "Settings.bin";
    
    // Calibration Settings
    public int patternCols;
    public int patternRows;
    public double squareSize;
    public int patternType; 
    public int winSize; 
    
    // Calibration Results
    public Mat cameraMatrix;
    public Mat distCoeffs;
    public double reprojectionError;
    
    // Undistort Cache (for performance optimization)
    private Mat map1;
    private Mat map2;
    private Size lastImageSize;
    
    // State variables
    public String calibName;
    public boolean hasResult;
    public boolean finSetSettings;

    public MyCameraCalibration() {
        cameraMatrix = new Mat();
        distCoeffs = new Mat();
        map1 = new Mat();
        map2 = new Mat();
        lastImageSize = new Size(0, 0);
        hasResult = false;
        finSetSettings = false;
        calibName = "";
        
        patternCols = 9;
        patternRows = 6;
        squareSize = 20.0;
        patternType = 0;
        winSize = 11;
    }

    public static boolean exists(String name) {
        if (name == null || name.isEmpty()) return false;
        Path dir = Paths.get(STR_BASE_FOLDER, name);
        return Files.isDirectory(dir);
    }

    public void setCalibName(String name) throws IOException {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Calibration name cannot be null or empty");
        }
        Path dir = Paths.get(STR_BASE_FOLDER, name);
        Files.createDirectories(dir);
        calibName = name;
        
        // Reset cache when folder changes
        resetCache();
    }

    private void resetCache() {
        if (map1 != null) map1.release();
        if (map2 != null) map2.release();
        map1 = new Mat();
        map2 = new Mat();
        lastImageSize = new Size(0, 0);
    }

    public void calibrate(List<Mat> objectPoints, List<Mat> imagePoints, Size imageSize) {
        if (objectPoints.isEmpty() || imagePoints.isEmpty()) {
            throw new IllegalArgumentException("Points lists cannot be empty");
        }

        List<Mat> rvecs = new ArrayList<>();
        List<Mat> tvecs = new ArrayList<>();
        
        if (!cameraMatrix.empty()) cameraMatrix.release();
        if (!distCoeffs.empty()) distCoeffs.release();
        
        cameraMatrix = Mat.eye(3, 3, CvType.CV_64F);
        distCoeffs = Mat.zeros(5, 1, CvType.CV_64F);

        reprojectionError = Calib.calibrateCamera(
                objectPoints, 
                imagePoints, 
                imageSize, 
                cameraMatrix, 
                distCoeffs, 
                rvecs, 
                tvecs
        );

        for (Mat m : rvecs) m.release();
        for (Mat m : tvecs) m.release();

        hasResult = !cameraMatrix.empty();
        resetCache();
    }

    /**
     * Get or create undistort maps for high performance
     */
    public Mat[] getUndistortMaps(Size size) {
        if (!hasResult) return null;
        
        if (size.width != lastImageSize.width || size.height != lastImageSize.height || map1.empty()) {
            resetCache();
            Imgproc.initUndistortRectifyMap(
                cameraMatrix, distCoeffs, new Mat(), 
                cameraMatrix, size, CvType.CV_16SC2, map1, map2
            );
            lastImageSize = size.clone();
        }
        return new Mat[]{map1, map2};
    }

    public void write() throws IOException {
        if (calibName.isEmpty()) throw new IllegalStateException("Calibration name not set");
        if (!hasResult) throw new IllegalStateException("No calibration result to save");

        Path dir = Paths.get(STR_BASE_FOLDER, calibName);
        writeMat(cameraMatrix, dir.resolve(FILE_CAMERA_MATRIX).toString());
        writeMat(distCoeffs, dir.resolve(FILE_DIST_COEFFS).toString());
        
        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(dir.resolve(FILE_SETTINGS).toString())))) {
            dos.writeInt(patternCols);
            dos.writeInt(patternRows);
            dos.writeDouble(squareSize);
            dos.writeInt(patternType);
            dos.writeInt(winSize);
        }
    }

    public void read() throws IOException {
        if (calibName.isEmpty()) throw new IllegalStateException("Calibration name not set");
        Path dir = Paths.get(STR_BASE_FOLDER, calibName);
        if (!Files.isDirectory(dir)) throw new IOException("Directory not found");

        if (!cameraMatrix.empty()) cameraMatrix.release();
        if (!distCoeffs.empty()) distCoeffs.release();
        
        cameraMatrix = readMat(dir.resolve(FILE_CAMERA_MATRIX).toString());
        distCoeffs = readMat(dir.resolve(FILE_DIST_COEFFS).toString());
        
        Path settingsPath = dir.resolve(FILE_SETTINGS);
        if (Files.exists(settingsPath)) {
            try (DataInputStream dis = new DataInputStream(
                    new BufferedInputStream(new FileInputStream(settingsPath.toString())))) {
                patternCols = dis.readInt();
                patternRows = dis.readInt();
                squareSize = dis.readDouble();
                patternType = dis.readInt();
                winSize = dis.readInt();
            }
        }
        
        hasResult = true;
        finSetSettings = true;
        resetCache();
    }

    private void writeMat(Mat mat, String filePath) throws IOException {
        int rows = mat.rows();
        int cols = mat.cols();
        int type = mat.type();

        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(filePath)))) {
            dos.writeInt(rows);
            dos.writeInt(cols);
            dos.writeInt(type);

            if (type == CvType.CV_64F) {
                double[] buffer = new double[cols];
                for (int r = 0; r < rows; r++) {
                    mat.row(r).get(0, 0, buffer);
                    for (int c = 0; c < cols; c++) dos.writeDouble(buffer[c]);
                }
            } else if (type == CvType.CV_32F) {
                float[] buffer = new float[cols];
                for (int r = 0; r < rows; r++) {
                    mat.row(r).get(0, 0, buffer);
                    for (int c = 0; c < cols; c++) dos.writeFloat(buffer[c]);
                }
            }
        }
    }

    private Mat readMat(String filePath) throws IOException {
        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(new FileInputStream(filePath)))) {
            int rows = dis.readInt();
            int cols = dis.readInt();
            int type = dis.readInt();
            Mat mat = new Mat(rows, cols, type);

            if (type == CvType.CV_64F) {
                double[] buffer = new double[cols];
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) buffer[c] = dis.readDouble();
                    mat.row(r).put(0, 0, buffer);
                }
            } else if (type == CvType.CV_32F) {
                float[] buffer = new float[cols];
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) buffer[c] = dis.readFloat();
                    mat.row(r).put(0, 0, buffer);
                }
            }
            return mat;
        }
    }
}