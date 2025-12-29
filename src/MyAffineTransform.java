import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

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
 * Affine transformation matrix manager
 * @author nishida
 */
public class MyAffineTransform {
    // Constants
    private static final String STR_BASE_FOLDER = "AffineTransform";
    private static final String FILE_ROI = "Roi.zip";
    private static final String FILE_MATRIX = "Matrix.mat";
    private static final String FILE_INVERSE = "Inverse.mat";
    
    // Variables
    public Roi AffineSrc;
    public Roi AffineDst;
    public boolean finSetRoi;
    public Mat AffineMatrix;
    public Mat AffineInverse;
    public String FileName;
    public boolean finSetFilename;
    public boolean hasMatrix;
    
    /**
     * Constructor
     */
    public MyAffineTransform() {
        AffineSrc = null;
        AffineDst = null;
        finSetRoi = false;
        AffineMatrix = new Mat();
        AffineInverse = new Mat();
        FileName = "";
        finSetFilename = false;
        hasMatrix = false;
    }
    
    /**
     * Set ROI src and dst
     * @param src source ROI
     * @param dst destination ROI
     * @throws IllegalArgumentException if ROI has less than 3 points
     */
    public void setRoi(Roi src, Roi dst) {
        if (src == null || dst == null) {
            throw new IllegalArgumentException("ROI cannot be null");
        }
        
        // Get points from ROI
        java.awt.Point[] pts_src = getContainedPoints(src);
        java.awt.Point[] pts_dst = getContainedPoints(dst);
        
        // Check point count (must be 3 or more)
        if (pts_src.length < 3 || pts_dst.length < 3) {
            throw new IllegalArgumentException("ROI must have at least 3 points");
        }
        
        // Invalidate old matrices
        hasMatrix = false;
        
        // Set variables
        AffineSrc = src;
        AffineDst = dst;
        finSetRoi = true;
    }
    
    /**
     * Set file name and create directory
     * @param folder_name folder name under AffineTransform/
     * @throws IOException if directory creation fails
     */
    public void setFileName(String folder_name) throws IOException {
        if (folder_name == null || folder_name.isEmpty()) {
            throw new IllegalArgumentException("Folder name cannot be null or empty");
        }
        
        // Create directory: AffineTransform/folder_name
        Path dir = Paths.get(STR_BASE_FOLDER, folder_name);
        Files.createDirectories(dir);
        
        FileName = folder_name;
        finSetFilename = true;
    }
    
    /**
     * Compute affine transformation matrix and inverse matrix
     * @throws IllegalStateException if ROI is not set
     * @throws RuntimeException if computation fails
     */
    public void compute() {
        if (!finSetRoi) {
            throw new IllegalStateException("ROI is not set. Call setRoi() first.");
        }

        MatOfPoint2f matPt_src = null;
        MatOfPoint2f matPt_dst = null;
        Mat matrix = null;
        Mat inverse = null;

        try {
            // Get points from ROI
            java.awt.Point[] pts_src = getContainedPoints(AffineSrc);
            java.awt.Point[] pts_dst = getContainedPoints(AffineDst);

            // Convert to OpenCV Point and use first 3 points
            ArrayList<Point> lstPt_src = new ArrayList<Point>();
            ArrayList<Point> lstPt_dst = new ArrayList<Point>();

            for (int i = 0; i < 3; i++) {
                lstPt_src.add(new Point(pts_src[i].getX(), pts_src[i].getY()));
                lstPt_dst.add(new Point(pts_dst[i].getX(), pts_dst[i].getY()));
            }

            // Create MatOfPoint2f
            matPt_src = new MatOfPoint2f();
            matPt_dst = new MatOfPoint2f();
            matPt_src.fromList(lstPt_src);
            matPt_dst.fromList(lstPt_dst);

            // Compute affine transformation matrix
            matrix = Imgproc.getAffineTransform(matPt_src, matPt_dst);

            if (matrix == null || matrix.empty() || matrix.rows() != 2 || matrix.cols() != 3) {
                throw new RuntimeException("Failed to compute affine transformation matrix");
            }

            // Compute inverse matrix using OpenCV's invertAffineTransform
            inverse = new Mat();
            Imgproc.invertAffineTransform(matrix, inverse);

            if (inverse == null || inverse.empty()) {
                throw new RuntimeException("Failed to compute inverse matrix");
            }

            // Release old matrices if exist
            if (!AffineMatrix.empty()) {
                AffineMatrix.release();
            }
            if (!AffineInverse.empty()) {
                AffineInverse.release();
            }

            // Store results
            AffineMatrix = matrix.clone();
            AffineInverse = inverse.clone();

            // Set flag
            hasMatrix = true;
        } finally {
            // Release temporary resources
            if (matPt_src != null) {
                matPt_src.release();
            }
            if (matPt_dst != null) {
                matPt_dst.release();
            }
            if (matrix != null) {
                matrix.release();
            }
            if (inverse != null) {
                inverse.release();
            }
        }
    }
    
    /**
     * Compute and write to files
     * @throws IllegalStateException if ROI or filename is not set
     * @throws IOException if write fails
     */
    public void computeAndWrite() throws IOException {
        if (!finSetRoi) {
            throw new IllegalStateException("ROI is not set. Call setRoi() first.");
        }
        if (!finSetFilename) {
            throw new IllegalStateException("Filename is not set. Call setFileName() first.");
        }
        
        // Compute matrices
        compute();
        
        // Write to files
        write();
    }
    
    /**
     * Write ROI and matrices to files
     * @throws IllegalStateException if filename or matrices are not set
     * @throws IOException if write fails
     */
    public void write() throws IOException {
        if (!finSetFilename) {
            throw new IllegalStateException("Filename is not set. Call setFileName() first.");
        }
        if (!hasMatrix) {
            throw new IllegalStateException("Matrices are not computed. Call compute() first.");
        }
        
        Path dir = Paths.get(STR_BASE_FOLDER, FileName);
        
        // Write ROI.zip
        RoiManager roiMan = new RoiManager(false);
        roiMan.addRoi(AffineSrc);
        roiMan.addRoi(AffineDst);
        roiMan.save(dir.resolve(FILE_ROI).toString());
        roiMan.close();
        
        // Write Matrix.mat
        writeMat(AffineMatrix, dir.resolve(FILE_MATRIX).toString());
        
        // Write Inverse.mat
        writeMat(AffineInverse, dir.resolve(FILE_INVERSE).toString());
    }
    
    /**
     * Read ROI and matrices from files
     * @throws IllegalStateException if filename is not set
     * @throws IOException if read fails
     */
    public void read() throws IOException {
        if (!finSetFilename) {
            throw new IllegalStateException("Filename is not set. Call setFileName() first.");
        }
        
        Path dir = Paths.get(STR_BASE_FOLDER, FileName);
        
        // Check if directory exists
        if (!Files.isDirectory(dir)) {
            throw new IOException("Directory does not exist: " + dir.toString());
        }
        
        // Read Roi.zip
        Path roiPath = dir.resolve(FILE_ROI);
        if (!Files.exists(roiPath)) {
            throw new IOException("ROI file does not exist: " + roiPath.toString());
        }
        
        RoiManager roiMan = new RoiManager(false);
        roiMan.open(roiPath.toString());
        
        if (roiMan.getCount() < 2) {
            roiMan.close();
            throw new IOException("ROI file must contain at least 2 ROIs");
        }
        
        AffineSrc = roiMan.getRoi(0);
        AffineDst = roiMan.getRoi(1);
        roiMan.close();
        
        finSetRoi = true;
        
        // Read Matrix.mat
        Path matrixPath = dir.resolve(FILE_MATRIX);
        if (!Files.exists(matrixPath)) {
            throw new IOException("Matrix file does not exist: " + matrixPath.toString());
        }
        
        if (!AffineMatrix.empty()) {
            AffineMatrix.release();
        }
        AffineMatrix = readMat(matrixPath.toString());
        
        // Read Inverse.mat
        Path inversePath = dir.resolve(FILE_INVERSE);
        if (!Files.exists(inversePath)) {
            throw new IOException("Inverse matrix file does not exist: " + inversePath.toString());
        }
        
        if (!AffineInverse.empty()) {
            AffineInverse.release();
        }
        AffineInverse = readMat(inversePath.toString());
        
        // Set flag
        hasMatrix = true;
    }
    
    /**
     * Set new destination ROI and recompute
     * @param dst new destination ROI
     * @throws IllegalStateException if source ROI is not set
     * @throws IllegalArgumentException if dst has less than 3 points
     */
    public void setDstAndCompute(Roi dst) {
        if (!finSetRoi) {
            throw new IllegalStateException("Source ROI is not set. Call setRoi() first.");
        }
        
        if (dst == null) {
            throw new IllegalArgumentException("Destination ROI cannot be null");
        }
        
        // Get points from dst ROI
        java.awt.Point[] pts_dst = getContainedPoints(dst);
        
        // Check point count (must be 3 or more)
        if (pts_dst.length < 3) {
            throw new IllegalArgumentException("Destination ROI must have at least 3 points");
        }
        
        // Invalidate old matrices
        hasMatrix = false;
        
        // Update destination ROI
        AffineDst = dst;
        
        // Recompute matrices
        compute();
    }

    /**
     * Copy all data to destination instance (deep copy)
     * @param dst destination instance
     * @throws IllegalArgumentException if dst is null
     */
    public void copyTo(MyAffineTransform dst) {
        if (dst == null) {
            throw new IllegalArgumentException("Destination cannot be null");
        }

        // Copy ROI (clone)
        if (AffineSrc != null) {
            dst.AffineSrc = (Roi) AffineSrc.clone();
        } else {
            dst.AffineSrc = null;
        }

        if (AffineDst != null) {
            dst.AffineDst = (Roi) AffineDst.clone();
        } else {
            dst.AffineDst = null;
        }

        // Copy boolean flags
        dst.finSetRoi = this.finSetRoi;
        dst.finSetFilename = this.finSetFilename;
        dst.hasMatrix = this.hasMatrix;

        // Copy Mat (deep copy with clone)
        if (!AffineMatrix.empty()) {
            if (!dst.AffineMatrix.empty()) {
                dst.AffineMatrix.release();
            }
            dst.AffineMatrix = AffineMatrix.clone();
        } else {
            if (!dst.AffineMatrix.empty()) {
                dst.AffineMatrix.release();
            }
            dst.AffineMatrix = new Mat();
        }

        if (!AffineInverse.empty()) {
            if (!dst.AffineInverse.empty()) {
                dst.AffineInverse.release();
            }
            dst.AffineInverse = AffineInverse.clone();
        } else {
            if (!dst.AffineInverse.empty()) {
                dst.AffineInverse.release();
            }
            dst.AffineInverse = new Mat();
        }

        // Copy String
        dst.FileName = this.FileName;
    }

    /**
     * Show matrix data in ResultsTable
     * @throws IllegalStateException if matrices are not computed
     */
    public void ShowData() {
        if (!hasMatrix) {
            throw new IllegalStateException("Matrices are not computed. Call compute() or read() first.");
        }
        
        ResultsTable rt = OCV__LoadLibrary.GetResultsTable(true);
        
        // Show AffineMatrix
        if (!AffineMatrix.empty()) {
            rt.incrementCounter();
            rt.addValue("Matrix", "Affine");
            rt.addValue("Row", 0);
            rt.addValue("Column01", AffineMatrix.get(0, 0)[0]);
            rt.addValue("Column02", AffineMatrix.get(0, 1)[0]);
            rt.addValue("Column03", AffineMatrix.get(0, 2)[0]);
            
            rt.incrementCounter();
            rt.addValue("Matrix", "Affine");
            rt.addValue("Row", 1);
            rt.addValue("Column01", AffineMatrix.get(1, 0)[0]);
            rt.addValue("Column02", AffineMatrix.get(1, 1)[0]);
            rt.addValue("Column03", AffineMatrix.get(1, 2)[0]);
        }
        
        // Show AffineInverse
        if (!AffineInverse.empty()) {
            rt.incrementCounter();
            rt.addValue("Matrix", "Inverse");
            rt.addValue("Row", 0);
            rt.addValue("Column01", AffineInverse.get(0, 0)[0]);
            rt.addValue("Column02", AffineInverse.get(0, 1)[0]);
            rt.addValue("Column03", AffineInverse.get(0, 2)[0]);
            
            rt.incrementCounter();
            rt.addValue("Matrix", "Inverse");
            rt.addValue("Row", 1);
            rt.addValue("Column01", AffineInverse.get(1, 0)[0]);
            rt.addValue("Column02", AffineInverse.get(1, 1)[0]);
            rt.addValue("Column03", AffineInverse.get(1, 2)[0]);
        }
        
        rt.show("Results");
    }
    
    /**
     * Get points from ROI (similar to OCV_GetAffineTransform)
     * @param roi ROI
     * @return array of points
     */
    private java.awt.Point[] getContainedPoints(Roi roi) {
        ij.process.FloatPolygon p = roi.getFloatPolygon();
        java.awt.Point[] points = new java.awt.Point[p.npoints];
        
        for (int i = 0; i < p.npoints; i++) {
            points[i] = new java.awt.Point((int)Math.round(p.xpoints[i]), (int)Math.round(p.ypoints[i]));
        }
        
        return points;
    }
    
    /**
     * Write Mat to binary file (similar to MyFeatureDetector.writeDescriptors)
     * @param mat matrix to write
     * @param filePath file path
     * @throws IOException if write fails
     */
    private void writeMat(Mat mat, String filePath) throws IOException {
        if (mat == null || mat.empty()) {
            throw new IOException("Matrix is null or empty");
        }
        
        int rows = mat.rows();
        int cols = mat.cols();
        int type = mat.type();
        
        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(filePath)))) {
            
            dos.writeInt(rows);
            dos.writeInt(cols);
            dos.writeInt(type);
            
            if (type == CvType.CV_32F) {
                float[] buffer = new float[cols];
                for (int r = 0; r < rows; r++) {
                    mat.row(r).get(0, 0, buffer);
                    for (int c = 0; c < cols; c++) {
                        dos.writeFloat(buffer[c]);
                    }
                }
            } else if (type == CvType.CV_64F) {
                double[] buffer = new double[cols];
                for (int r = 0; r < rows; r++) {
                    mat.row(r).get(0, 0, buffer);
                    for (int c = 0; c < cols; c++) {
                        dos.writeDouble(buffer[c]);
                    }
                }
            } else if (type == CvType.CV_8U) {
                byte[] buffer = new byte[cols];
                for (int r = 0; r < rows; r++) {
                    mat.row(r).get(0, 0, buffer);
                    dos.write(buffer);
                }
            } else {
                throw new IllegalArgumentException("Unsupported matrix type: " + type);
            }
        }
    }
    
    /**
     * Read Mat from binary file (similar to MyFeatureDetector.readDescriptors)
     * @param filePath file path
     * @return matrix
     * @throws IOException if read fails
     */
    private Mat readMat(String filePath) throws IOException {
        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(new FileInputStream(filePath)))) {
            
            int rows = dis.readInt();
            int cols = dis.readInt();
            int type = dis.readInt();
            
            if (rows <= 0 || cols <= 0) {
                throw new IOException("Invalid matrix dimensions");
            }
            
            Mat mat = new Mat(rows, cols, type);
            
            if (type == CvType.CV_32F) {
                float[] buffer = new float[cols];
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        buffer[c] = dis.readFloat();
                    }
                    mat.row(r).put(0, 0, buffer);
                }
            } else if (type == CvType.CV_64F) {
                double[] buffer = new double[cols];
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        buffer[c] = dis.readDouble();
                    }
                    mat.row(r).put(0, 0, buffer);
                }
            } else if (type == CvType.CV_8U) {
                byte[] buffer = new byte[cols];
                for (int r = 0; r < rows; r++) {
                    dis.readFully(buffer);
                    mat.row(r).put(0, 0, buffer);
                }
            } else {
                throw new IllegalArgumentException("Unsupported matrix type: " + type);
            }
            
            return mat;
        }
    }
}