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
    private static final String STR_AKAZE = "AKAZE";
    private static final String STR_BRISK = "BRISK";
    private static final String STR_ORB = "ORB";
    private static final String STR_SIFT = "SIFT";
    private static final String STR_BASE_FOLDER = "features";

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
        if (OCV__LoadLibrary.isNullOrEmpty(type) || OCV__LoadLibrary.isNullOrEmpty(name)) {
            throw new IllegalArgumentException("Type or QueryName is empty.");
        }
        
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
        
        try {
            reset_QueryKeyPoints_QueryDescriptors();
            calc_KeyPoints_Descriptors(image, QueryKeyPoints, QueryDescriptors);
            
            image_key = new Mat();
            Features.drawKeypoints(image, QueryKeyPoints, image_key);
            
            detector.write(FileParam.toString());
            boolean bret1 = Imgcodecs.imwrite(FileQueryImage.toString(), image);
            boolean bret2 = Imgcodecs.imwrite(FileQueryImage_Key.toString(), image_key);
            writeMatOfKeyPoint(QueryKeyPoints, FileQueryKeyPoints.toString());
            writeDescriptors(QueryDescriptors, FileQueryDescriptors.toString());
            
            if (!bret1 || !bret2) {
                throw new IOException("Can not write the image.");
            }
        } finally {
            if (image_key != null) {
                image_key.release();
            }
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
        
        try {
            // read Param -> read QueryImage -> detect QueryKeyPoints -> write (FileQueryImage_Key, QueryKeyPoints)
            detector.read(FileParam.toString());       
            image = Imgcodecs.imread(FileQueryImage.toString());
            
            reset_QueryKeyPoints_QueryDescriptors();
            calc_KeyPoints_Descriptors(image, QueryKeyPoints, QueryDescriptors);
            
            image_key = new Mat();
            Features.drawKeypoints(image, QueryKeyPoints, image_key);
            
            boolean bret2 = Imgcodecs.imwrite(FileQueryImage_Key.toString(), image_key);
            writeMatOfKeyPoint(QueryKeyPoints, FileQueryKeyPoints.toString());
            writeDescriptors(QueryDescriptors, FileQueryDescriptors.toString());
            
            if (!bret2) {
                throw new IOException("Can not write the image.");
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
        
        detector.read(FileParam.toString());
        
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
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(filePath)))) {
            int n = dis.readInt();
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
            return;
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
                    descriptors.row(r).get(0, 0, buffer);
                    for (int c = 0; c < cols; c++) {
                        dos.writeFloat(buffer[c]);
                    }
                }
            } else if (type == CvType.CV_64F) {
                double[] buffer = new double[cols];
                for (int r = 0; r < rows; r++) {
                    descriptors.row(r).get(0, 0, buffer);
                    for (int c = 0; c < cols; c++) {
                        dos.writeDouble(buffer[c]);
                    }
                }
            } else if (type == CvType.CV_8U) {
                byte[] buffer = new byte[cols];
                for (int r = 0; r < rows; r++) {
                    descriptors.row(r).get(0, 0, buffer);
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
        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(new FileInputStream(filePath)))) {

            int rows = dis.readInt();
            int cols = dis.readInt();
            int type = dis.readInt();

            if (rows <= 0 || cols <= 0) {
                return new Mat();
            }

            Mat descriptors = new Mat(rows, cols, type);

            if (type == CvType.CV_32F) {
                float[] buffer = new float[cols];
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        buffer[c] = dis.readFloat();
                    }
                    descriptors.row(r).put(0, 0, buffer);
                }
            } else if (type == CvType.CV_64F) {
                double[] buffer = new double[cols];
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        buffer[c] = dis.readDouble();
                    }
                    descriptors.row(r).put(0, 0, buffer);
                }
            } else if (type == CvType.CV_8U) {
                byte[] buffer = new byte[cols];
                for (int r = 0; r < rows; r++) {
                    dis.readFully(buffer);
                    descriptors.row(r).put(0, 0, buffer);
                }
            } else {
                throw new IllegalArgumentException("Unsupported descriptor type: " + type);
            }

            return descriptors;
        }
    }
    
    /**
     * Check the existence of a folder named [query_name].
     * @param query_name
     * @return
     * @throws IOException 
     */
    public static boolean exitQueryName(String query_name) throws IOException {
        Path queryPath = Paths.get(STR_BASE_FOLDER, query_name);     
        return Files.isDirectory(queryPath);
    }
    
    /**
     * Check the existence of the parameter.
     * @param type
     * @param query_name
     * @return 
     */
    public static boolean exitParam(String type, String query_name) {
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