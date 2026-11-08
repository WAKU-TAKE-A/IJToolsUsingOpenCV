import ij.ImagePlus;
import ij.WindowManager;
import ij.measure.ResultsTable;
import ij.process.ColorProcessor;
import java.io.*;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.opencv.core.CvType;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;

import org.opencv.features.Feature2D;
import org.opencv.features.Features;
import org.opencv.features.ORB;
import org.opencv.features.SIFT;
import org.opencv.imgcodecs.Imgcodecs;
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
 * Feature2D wrapper for multiple detectors
 * @author nishida
 */
public class MyFeatureDetector {
    // const var.
    private static final String STR_ORB = "ORB";
    private static final String STR_SIFT = "SIFT";
    private static final String STR_BASE_FOLDER = "features";

    /**
     * Validate the query name to prevent path traversal and invalid characters.
     * @param query_name
     */
    public static void validateQueryName(String query_name) {
        if (query_name == null || query_name.isEmpty()) {
            throw new IllegalArgumentException("QueryName is empty.");
        }
        if (query_name.contains("/") || query_name.contains("\\") || query_name.contains("..")) {
            throw new IllegalArgumentException("Invalid query name: " + query_name);
        }
        Path basePath = Paths.get(STR_BASE_FOLDER).toAbsolutePath().normalize();
        Path queryPath = Paths.get(STR_BASE_FOLDER, query_name).toAbsolutePath().normalize();
        if (!queryPath.getParent().equals(basePath)) {
            throw new IllegalArgumentException("Invalid query path: " + queryPath);
        }
    }

    // var.
    private Feature2D detector = null;
    public String DetectorType = "";
    public String QueryName = "";
    public Path FileParam = null;
    public Path FileQueryImage = null;
    public Path FileQueryImage_Key = null;
    public Path FileQueryKeyPoints = null;
    public Path FileQueryDescriptors = null;    
    public MatOfKeyPoint QueryKeyPoints = null;
    public Mat QueryDescriptors = null;
    public boolean IsInit = false;

    /**
     * constructor
     */  
    public  MyFeatureDetector() { }
    
    /**
     * Initilize
     * @param type
     * @param name
     * @throws IOException 
     */
    public void initialize(String type, String name) throws IOException {
        // check
        if (OCV__LoadLibrary.isNullOrEmpty(type)) {
            throw new IllegalArgumentException("Type is empty.");
        }
        validateQueryName(name);
        
        if (QueryKeyPoints == null) {
            QueryKeyPoints = new MatOfKeyPoint();
        }
        
        if (QueryDescriptors == null) {
            QueryDescriptors = new Mat();
        }
        
        // set
        IsInit = false;        
        reset_QueryKeyPoints_QueryDescriptors();        
        DetectorType = type;
        QueryName = name;
        
        if (detector != null && !detector.empty()) {
            detector.clear();
        }        
        
        if (DetectorType.equals(STR_ORB)) {
            detector = ORB.create();
        } else if (DetectorType.equals(STR_SIFT)) {
            detector = SIFT.create();
        } else {
            throw new IllegalArgumentException("Unknown detector type: " + DetectorType);
        }
       
        Path dir = Paths.get(STR_BASE_FOLDER, QueryName);
        Files.createDirectories(dir);
        FileParam = dir.resolve(DetectorType + ".yaml");
        FileQueryImage = dir.resolve("query.bmp");
        FileQueryImage_Key = dir.resolve("query_key.bmp");
        FileQueryKeyPoints = dir.resolve("query_key.mkp");
        FileQueryDescriptors = dir.resolve("query_desc.mat");       
        IsInit = true;
    }
    
    /**
     * generate the query's file set
     * calc QueryKeyPoints and QueryDescriptors -> write（FileParam, QueryImage, QueryImage_Key, QueryKeyPoints, QueryDescriptors）
     * @param image
     * @throws IOException 
     */
    public void generateQuery(Mat image) throws IOException {
        if (detector == null || !IsInit) {
            throw new IllegalStateException("Not initialize.");
        }
        
        Mat image_key = null;
        Path tempDir = Paths.get(STR_BASE_FOLDER, QueryName + "_tmp_" + System.currentTimeMillis());
        Path finalDir = Paths.get(STR_BASE_FOLDER, QueryName);
        Path backupDir = Paths.get(STR_BASE_FOLDER, QueryName + "_bak_" + System.currentTimeMillis());
        
        try {
            Files.createDirectories(tempDir);
            
            reset_QueryKeyPoints_QueryDescriptors();
            calc_KeyPoints_Descriptors(image, QueryKeyPoints, QueryDescriptors);
            
            image_key = new Mat();
            Features.drawKeypoints(image, QueryKeyPoints, image_key);
            
            Path tempFileParam = tempDir.resolve(FileParam.getFileName());
            Path tempFileQueryImage = tempDir.resolve(FileQueryImage.getFileName());
            Path tempFileQueryImage_Key = tempDir.resolve(FileQueryImage_Key.getFileName());
            Path tempFileQueryKeyPoints = tempDir.resolve(FileQueryKeyPoints.getFileName());
            Path tempFileQueryDescriptors = tempDir.resolve(FileQueryDescriptors.getFileName());
            
            detector.write(tempFileParam.toString());
            boolean bret1 = Imgcodecs.imwrite(tempFileQueryImage.toString(), image);
            boolean bret2 = Imgcodecs.imwrite(tempFileQueryImage_Key.toString(), image_key);
            writeMatOfKeyPoint(QueryKeyPoints, tempFileQueryKeyPoints.toString());
            writeDescriptors(QueryDescriptors, tempFileQueryDescriptors.toString());
            
            if (!bret1 || !bret2) {
                throw new IOException("Can not write the image.");
            }
            
            if (Files.exists(finalDir)) {
                Files.move(finalDir, backupDir, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            }
            try {
                Files.move(tempDir, finalDir, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                if (Files.exists(backupDir)) {
                    Files.move(backupDir, finalDir, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                }
                throw e;
            }
            if (Files.exists(backupDir)) {
                deleteDirectory(backupDir.toFile());
            }
        } catch (Exception e) {
            deleteDirectory(tempDir.toFile());
            if (e instanceof IOException) {
                throw (IOException) e;
            } else {
                throw new IOException(e);
            }
        } finally {
            if (image_key != null) {
                image_key.release();
            }
        }
    }
    
    private void deleteDirectory(File dir) {
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            dir.delete();
        }
    }
    
    /**
     * read FileParam and FileQueryImage, generate the query's file set.
     * read (FileParam, QueryImage) -> calc QueryKeyPoints and QueryDescriptors -> write (FileQueryImage_Key, QueryKeyPoints, QueryDescriptors)
     * @throws IOException 
     */
    public void remakeQuery() throws IOException {
        if (detector == null || !IsInit) {
            throw new IllegalStateException("Not initialize.");
        }
        
        Mat image = null;
        Mat image_key = null;
        Path tempDir = Paths.get(STR_BASE_FOLDER, QueryName + "_tmp_" + System.currentTimeMillis());
        Path finalDir = Paths.get(STR_BASE_FOLDER, QueryName);
        Path backupDir = Paths.get(STR_BASE_FOLDER, QueryName + "_bak_" + System.currentTimeMillis());
        
        try {
            // read Param -> read QueryImage -> detect QueryKeyPoints -> write (FileQueryImage_Key, QueryKeyPoints)
            detector.read(FileParam.toString());       
            image = Imgcodecs.imread(FileQueryImage.toString());
            
            Files.createDirectories(tempDir);
            
            reset_QueryKeyPoints_QueryDescriptors();
            calc_KeyPoints_Descriptors(image, QueryKeyPoints, QueryDescriptors);
            
            image_key = new Mat();
            Features.drawKeypoints(image, QueryKeyPoints, image_key);
            
            Path tempFileParam = tempDir.resolve(FileParam.getFileName());
            Path tempFileQueryImage = tempDir.resolve(FileQueryImage.getFileName());
            Path tempFileQueryImage_Key = tempDir.resolve(FileQueryImage_Key.getFileName());
            Path tempFileQueryKeyPoints = tempDir.resolve(FileQueryKeyPoints.getFileName());
            Path tempFileQueryDescriptors = tempDir.resolve(FileQueryDescriptors.getFileName());
            
            detector.write(tempFileParam.toString());
            boolean bret1 = Imgcodecs.imwrite(tempFileQueryImage.toString(), image);
            boolean bret2 = Imgcodecs.imwrite(tempFileQueryImage_Key.toString(), image_key);
            writeMatOfKeyPoint(QueryKeyPoints, tempFileQueryKeyPoints.toString());
            writeDescriptors(QueryDescriptors, tempFileQueryDescriptors.toString());
            
            if (!bret1 || !bret2) {
                throw new IOException("Can not write the image.");
            }
            
            if (Files.exists(finalDir)) {
                Files.move(finalDir, backupDir, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            }
            try {
                Files.move(tempDir, finalDir, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                if (Files.exists(backupDir)) {
                    Files.move(backupDir, finalDir, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                }
                throw e;
            }
            if (Files.exists(backupDir)) {
                deleteDirectory(backupDir.toFile());
            }
        } catch (Exception e) {
            deleteDirectory(tempDir.toFile());
            if (e instanceof IOException) {
                throw (IOException) e;
            } else {
                throw new IOException(e);
            }
        } finally {
            if (image != null) {
                image.release();
            }
            if (image_key != null) {
                image_key.release();
            }
        }
    }
    
    /**
     * read only.
     * read only (FileParam, QueryKeyPoints, QueryDescriptors)
     * @throws IOException 
     */
    public void readQuery() throws IOException {
        if (detector == null || !IsInit) {
            throw new IllegalStateException("Not initialize.");
        }

        if (!Files.exists(FileParam)){
            throw new IOException("Can not find " + FileParam.toString() + " .");
        }
        
        try {
            detector.read(FileParam.toString());
        } catch (Exception e) {
            throw new IOException("Failed to read parameters: " + e.getMessage(), e);
        }
        
        reset_QueryKeyPoints_QueryDescriptors();
        QueryKeyPoints = readMatOfKeyPoint(FileQueryKeyPoints.toString());     
        QueryDescriptors = readDescriptors(FileQueryDescriptors.toString()); 
    }
    
    /**
     * calclate KeyPoints and  Descriptors
     * @param image
     * @param key
     * @param desc 
     */
    public void calc_KeyPoints_Descriptors(Mat image, MatOfKeyPoint key, Mat desc) {
        if (detector == null || !IsInit) {
            throw new IllegalStateException("Detector not created");
        }
        
        detector.detect(image, key);
        detector.compute(image, key, desc);
    }
    
    /**
     * copy
     * @param dst 
     */
    public void CopyTo(MyFeatureDetector dst) throws IOException {
        dst.initialize(DetectorType, QueryName);
        QueryKeyPoints.copyTo(dst.QueryKeyPoints);
        QueryDescriptors.copyTo(dst.QueryDescriptors);
    }

    /**
     * reset QueryKeyPoints
     */
    private void reset_QueryKeyPoints_QueryDescriptors() {
        if (QueryKeyPoints != null && !QueryKeyPoints.empty()) {
            QueryKeyPoints.release();
        }
        
        if (QueryDescriptors != null && !QueryDescriptors.empty()) {
            QueryDescriptors.release();
        }
    }
    
    /**
     * write MatOfKeyPoint
     * @param mkp
     * @param filePath
     * @throws IOException 
     */
    private void writeMatOfKeyPoint(MatOfKeyPoint mkp, String filePath) throws IOException {
        KeyPoint[] kps = mkp.toArray();
        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(filePath)))) {
            dos.writeInt(kps.length);
            for (KeyPoint kp : kps) {
                dos.writeFloat((float) kp.pt.x);
                dos.writeFloat((float) kp.pt.y);
                dos.writeFloat((float) kp.size);
                dos.writeFloat((float) kp.angle);
                dos.writeFloat((float) kp.response);
                dos.writeInt(kp.octave);
                dos.writeInt(kp.class_id);
            }
        }
    }

    /**
     * read MatOfKeyPoint
     * @param filePath
     * @return
     * @throws IOException 
     */
    private MatOfKeyPoint readMatOfKeyPoint(String filePath) throws IOException {
        File file = new File(filePath);
        long fileSize = file.length();
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            int n = dis.readInt();
            if (n < 0) {
                throw new IOException("Negative keypoint count: " + n);
            }
            long expectedSize = 4L + (long) n * 28L;
            if (fileSize != expectedSize) {
                throw new IOException("File size mismatch. Expected " + expectedSize + " but got " + fileSize);
            }
            List<KeyPoint> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                float x = dis.readFloat();
                float y = dis.readFloat();
                float size = dis.readFloat();
                float angle = dis.readFloat();
                float response = dis.readFloat();
                int octave = dis.readInt();
                int classId = dis.readInt();

                // KeyPoint の 7 引数コンストラクタを使って生成
                KeyPoint kp = new KeyPoint(x, y, size, angle, response, octave, classId);
                list.add(kp);
            }
            MatOfKeyPoint mkp = new MatOfKeyPoint();
            mkp.fromList(list);           
            return mkp;
        }
    }

    /**
     * write Descriptors
     * @param descriptors
     * @param filePath
     * @throws IOException 
     */
    public static void writeDescriptors(Mat descriptors, String filePath) throws IOException {
        if (descriptors == null || descriptors.empty()) {
            throw new IOException("Descriptors mat is null or empty");
        }

        int rows = descriptors.rows();
        int cols = descriptors.cols();
        int type = descriptors.type();

        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(filePath)))) {

            dos.writeInt(rows);
            dos.writeInt(cols);
            dos.writeInt(type);

            if (type == CvType.CV_32F) {
                float[] buffer = new float[cols];
                for (int r = 0; r < rows; r++) {
                    // 1行を float[] に取得
                    Mat rowMat = descriptors.row(r);
                    try {
                        rowMat.get(0, 0, buffer);
                    } finally {
                        rowMat.release();
                    }
                    for (int c = 0; c < cols; c++) {
                        dos.writeFloat(buffer[c]);
                    }
                }
            } else if (type == CvType.CV_64F) {
                double[] buffer = new double[cols];
                for (int r = 0; r < rows; r++) {
                    Mat rowMat = descriptors.row(r);
                    try {
                        rowMat.get(0, 0, buffer);
                    } finally {
                        rowMat.release();
                    }
                    for (int c = 0; c < cols; c++) {
                        dos.writeDouble(buffer[c]);
                    }
                }
            } else if (type == CvType.CV_8U) {
                byte[] buffer = new byte[cols];
                for (int r = 0; r < rows; r++) {
                    Mat rowMat = descriptors.row(r);
                    try {
                        rowMat.get(0, 0, buffer);
                    } finally {
                        rowMat.release();
                    }
                    dos.write(buffer);
                }
            } else {
                throw new IllegalArgumentException("Unsupported descriptor type: " + type);
            }
        }
    }
    
    /**
     * read Descriptors
     * @param filePath
     * @return
     * @throws IOException 
     */
    public static Mat readDescriptors(String filePath) throws IOException {
        File file = new File(filePath);
        long fileSize = file.length();
        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {

            int rows = dis.readInt();
            int cols = dis.readInt();
            int type = dis.readInt();

            if (rows <= 0 || cols <= 0) {
                throw new IOException("Invalid dimensions: rows=" + rows + ", cols=" + cols);
            }

            int elemSize = 0;
            if (type == CvType.CV_32F) {
                elemSize = 4;
            } else if (type == CvType.CV_64F) {
                elemSize = 8;
            } else if (type == CvType.CV_8U) {
                elemSize = 1;
            } else {
                throw new IOException("Unsupported descriptor type: " + type);
            }

            long expectedSize = 12L + (long) rows * (long) cols * (long) elemSize;
            if (fileSize != expectedSize) {
                throw new IOException("File size mismatch. Expected " + expectedSize + " but got " + fileSize);
            }

            Mat descriptors = new Mat(rows, cols, type);

            try {
                if (type == CvType.CV_32F) {
                    float[] buffer = new float[cols];
                    for (int r = 0; r < rows; r++) {
                        for (int c = 0; c < cols; c++) {
                            buffer[c] = dis.readFloat();
                        }
                        Mat rowMat = descriptors.row(r);
                        try {
                            rowMat.put(0, 0, buffer);
                        } finally {
                            rowMat.release();
                        }
                    }
                } else if (type == CvType.CV_64F) {
                    double[] buffer = new double[cols];
                    for (int r = 0; r < rows; r++) {
                        for (int c = 0; c < cols; c++) {
                            buffer[c] = dis.readDouble();
                        }
                        Mat rowMat = descriptors.row(r);
                        try {
                            rowMat.put(0, 0, buffer);
                        } finally {
                            rowMat.release();
                        }
                    }
                } else if (type == CvType.CV_8U) {
                    byte[] buffer = new byte[cols];
                    for (int r = 0; r < rows; r++) {
                        dis.readFully(buffer);
                        Mat rowMat = descriptors.row(r);
                        try {
                            rowMat.put(0, 0, buffer);
                        } finally {
                            rowMat.release();
                        }
                    }
                } else {
                    throw new IllegalArgumentException("Unsupported descriptor type: " + type);
                }

                return descriptors;
            } catch (Exception e) {
                descriptors.release();
                throw e;
            }
        }
    }
    
    /**
     * Check the existence of a folder named [query_name].
     * @param query_name
     * @return
     * @throws IOException 
     */
    public static boolean exitQueryName(String query_name) throws IOException {
        validateQueryName(query_name);
        Path queryPath = Paths.get(STR_BASE_FOLDER, query_name);     
        if (!Files.isDirectory(queryPath)) {
            return false;
        }
        
        boolean hasORB = Files.exists(queryPath.resolve(STR_ORB + ".yaml"));
        boolean hasSIFT = Files.exists(queryPath.resolve(STR_SIFT + ".yaml"));
        
        return (hasORB || hasSIFT) &&
               Files.exists(queryPath.resolve("query.bmp")) &&
               Files.exists(queryPath.resolve("query_key.bmp")) &&
               Files.exists(queryPath.resolve("query_key.mkp")) &&
               Files.exists(queryPath.resolve("query_desc.mat"));
    }
    
    /**
     * Check the existence of the parameter.
     * @param type
     * @param query_name
     * @return 
     */
    public static boolean exitParam(String type, String query_name) {
        validateQueryName(query_name);
        Path queryPath = Paths.get(STR_BASE_FOLDER, query_name);
        Path paramPath = queryPath.resolve(type + ".yaml");
        return Files.exists(paramPath);
    }
    
    /**
     * Show detection results in ResultsTable 
     * @param key_query 
     */
    public void showData(MatOfKeyPoint key_query) {
        ResultsTable rt = OCV__LoadLibrary.GetResultsTable(true);
        KeyPoint[] kpArr = key_query.toArray();
        int num = kpArr.length;

        for(int i = 0; i < num; i++) {
            KeyPoint kp = kpArr[i];
            double query_x = kp.pt.x;
            double query_y = kp.pt.y;
            double query_size = kp.size;
            double query_angle = kp.angle;
            double query_response = kp.response;
            double query_octave = kp.octave;
            double query_class_id = kp.class_id;

            rt.incrementCounter();
            rt.addValue("query_x", query_x);
            rt.addValue("query_y", query_y);
            rt.addValue("query_size", query_size);
            rt.addValue("query_angle", query_angle);
            rt.addValue("query_response", query_response);
            rt.addValue("query_octave", query_octave);
            rt.addValue("query_class_id", query_class_id);
        }

        rt.show("Results");
    }

    /**
     * draw KeyPoints
     * @param mat_query
     * @param key_query 
     */
    public static void drawKeyPoints(Mat mat_query, MatOfKeyPoint key_query) {
        Mat mat_dst = null;
        
        try {
            mat_dst = new Mat();
            Features.drawKeypoints(mat_query, key_query, mat_dst);

            String title_dst = WindowManager.getUniqueName("FeatureDetection_Extract");
            int imw_dst = mat_dst.cols();
            int imh_dst = mat_dst.rows();
            ImagePlus imp_dst = new ImagePlus(title_dst, new ColorProcessor(imw_dst, imh_dst));
            int[] arr_dst = (int[]) imp_dst.getChannelProcessor().getPixels();
            OCV__LoadLibrary.mat2intarray(mat_dst, arr_dst, imw_dst, imh_dst);
            imp_dst.show();
        } finally {
            if (mat_dst != null) {
                mat_dst.release();
            }
        }
    }
}