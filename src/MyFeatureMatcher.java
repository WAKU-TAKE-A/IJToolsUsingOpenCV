import java.util.ArrayList;
import java.util.List;
import org.opencv.core.Core;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.calib.Calib;
import org.opencv.geometry.Geometry;
import org.opencv.features.DescriptorMatcher;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;

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
 * Object detection using feature matching
 * @author nishida
 */
public class MyFeatureMatcher {
    
    // Parameters
    private double ratioThreshold = 0.75;
    private double ransacThreshold = 3.0;
    private int minMatchCount = 10;
    private double minInlierRatio = 0.3;
    private int maxDetections = 10;
    private double overlapThreshold = 0.5;
    
    // Store all good matches for later use
    private List<GoodMatch> lastAllGoodMatches = null;
    
    /**
     * Detection result
     */
    public static class DetectionResult {
        public Point[] corners;
        public int matchCount;
        public int inlierCount;
        public double inlierRatio;
        public List<Integer> usedIndices;
        public Mat homography;
        public double confidence;
        public List<MatchPair> inlierMatches;
        public List<MatchPair> outlierMatches;
        
        public DetectionResult(Point[] corners, int matchCount, int inlierCount, 
                              List<Integer> usedIndices, Mat homography, 
                              List<MatchPair> inlierMatches, List<MatchPair> outlierMatches) {
            this.corners = corners;
            this.matchCount = matchCount;
            this.inlierCount = inlierCount;
            // Division by zero protection
            this.inlierRatio = (matchCount > 0) ? (double)inlierCount / matchCount : 0.0;
            this.usedIndices = usedIndices;
            this.homography = homography;
            this.confidence = this.inlierRatio;
            this.inlierMatches = inlierMatches;
            this.outlierMatches = outlierMatches;
        }
    }
    
    /**
     * Match pair (query and train points)
     */
    public static class MatchPair {
        public Point queryPoint;
        public Point trainPoint;
        public float distance;
        
        public MatchPair(Point qp, Point tp, float dist) {
            this.queryPoint = qp;
            this.trainPoint = tp;
            this.distance = dist;
        }
    }
    
    /**
     * Good match information
     */
    private static class GoodMatch {
        int queryIdx;
        int trainIdx;
        Point queryPoint;
        Point trainPoint;
        float distance;
        
        GoodMatch(int qIdx, int tIdx, Point qp, Point tp, float dist) {
            this.queryIdx = qIdx;
            this.trainIdx = tIdx;
            this.queryPoint = qp;
            this.trainPoint = tp;
            this.distance = dist;
        }
    }
    
    // Constructors
    public MyFeatureMatcher() {
    }
    
    public MyFeatureMatcher(double ratioThreshold, double ransacThreshold, int minMatchCount, 
                           double minInlierRatio, int maxDetections, double overlapThreshold) {
        this.ratioThreshold = ratioThreshold;
        this.ransacThreshold = ransacThreshold;
        this.minMatchCount = minMatchCount;
        this.minInlierRatio = minInlierRatio;
        this.maxDetections = maxDetections;
        this.overlapThreshold = overlapThreshold;
    }
    
    // Getters and Setters
    public void setRatioThreshold(double ratioThreshold) {
        this.ratioThreshold = ratioThreshold;
    }
    
    public void setRansacThreshold(double ransacThreshold) {
        this.ransacThreshold = ransacThreshold;
    }
    
    public void setMinMatchCount(int minMatchCount) {
        this.minMatchCount = minMatchCount;
    }
    
    public void setMinInlierRatio(double minInlierRatio) {
        this.minInlierRatio = minInlierRatio;
    }
    
    public void setMaxDetections(int maxDetections) {
        this.maxDetections = maxDetections;
    }
    
    public void setOverlapThreshold(double overlapThreshold) {
        this.overlapThreshold = overlapThreshold;
    }
    
    public double getRatioThreshold() {
        return ratioThreshold;
    }
    
    public double getRansacThreshold() {
        return ransacThreshold;
    }
    
    public int getMinMatchCount() {
        return minMatchCount;
    }
    
    public double getMinInlierRatio() {
        return minInlierRatio;
    }
    
    public int getMaxDetections() {
        return maxDetections;
    }
    
    public double getOverlapThreshold() {
        return overlapThreshold;
    }
    
    /**
     * Detect objects in train image
     * 
     * @param queryKeyPoints query keypoints
     * @param queryDescriptors query descriptors
     * @param queryWidth query image width
     * @param queryHeight query image height
     * @param trainKeyPoints train keypoints
     * @param trainDescriptors train descriptors
     * @param matcher descriptor matcher
     * @param bestMatchOnly true: detect best match only, false: detect multiple objects
     * @return list of detection results
     * @throws IllegalArgumentException if parameters are invalid
     */
    public List<DetectionResult> detect(
            MatOfKeyPoint queryKeyPoints,
            Mat queryDescriptors,
            int queryWidth,
            int queryHeight,
            MatOfKeyPoint trainKeyPoints,
            Mat trainDescriptors,
            DescriptorMatcher matcher,
            boolean bestMatchOnly) {
        
        // Exception handling: null checks
        if (queryKeyPoints == null || queryDescriptors == null || 
            trainKeyPoints == null || trainDescriptors == null || matcher == null) {
            throw new IllegalArgumentException("Input parameters cannot be null");
        }
        
        // Exception handling: empty checks
        if (queryKeyPoints.rows() == 0 || queryDescriptors.rows() == 0 ||
            trainKeyPoints.rows() == 0 || trainDescriptors.rows() == 0) {
            return new ArrayList<>();
        }
        
        // Exception handling: size validation
        if (queryWidth <= 0 || queryHeight <= 0) {
            throw new IllegalArgumentException("Query image dimensions must be positive");
        }
        
        List<DetectionResult> results = new ArrayList<>();
        
        // Convert KeyPoints to array for coordinate access
        KeyPoint[] queryKPs = queryKeyPoints.toArray();
        KeyPoint[] trainKPs = trainKeyPoints.toArray();
        
        // 1. knnMatch
        List<MatOfDMatch> knnMatches = new ArrayList<>();
        try {
            matcher.knnMatch(queryDescriptors, trainDescriptors, knnMatches, 2);
        } catch (Exception e) {
            throw new RuntimeException("knnMatch failed: " + e.getMessage(), e);
        }
        
        // 2. Lowe's ratio test (improved with toArray() and order guarantee)
        List<GoodMatch> allGoodMatches = new ArrayList<>();
        
        for (MatOfDMatch matOfDMatch : knnMatches) {
            if (matOfDMatch.rows() >= 2) {
                DMatch[] matches = matOfDMatch.toArray();
                
                // Ensure order: matches[0] should have smaller distance
                if (matches[0].distance > matches[1].distance) {
                    DMatch temp = matches[0];
                    matches[0] = matches[1];
                    matches[1] = temp;
                }
                
                DMatch best = matches[0];
                DMatch second = matches[1];
                
                if (best.distance < ratioThreshold * second.distance) {
                    int queryIdx = best.queryIdx;
                    int trainIdx = best.trainIdx;
                    
                    // Exception handling: index validation
                    if (queryIdx < 0 || queryIdx >= queryKPs.length ||
                        trainIdx < 0 || trainIdx >= trainKPs.length) {
                        continue;
                    }
                    
                    // Correct way to get KeyPoint coordinates
                    Point queryPoint = queryKPs[queryIdx].pt;
                    Point trainPoint = trainKPs[trainIdx].pt;
                    
                    if (queryPoint != null && trainPoint != null) {
                        allGoodMatches.add(new GoodMatch(
                            queryIdx,
                            trainIdx,
                            queryPoint,
                            trainPoint,
                            best.distance
                        ));
                    }
                }
            }
        }
        
        // Store for later use
        lastAllGoodMatches = allGoodMatches;
        
        if (allGoodMatches.size() < minMatchCount) {
            return results;
        }
        
        // 3. Detection
        if (bestMatchOnly) {
            DetectionResult result = detectSingle(allGoodMatches, queryWidth, queryHeight);
            if (result != null) {
                results.add(result);
            }
        } else {
            results = detectMultiple(allGoodMatches, queryWidth, queryHeight);
        }
        
        return results;
    }
    
    /**
     * Detect single object
     */
    private DetectionResult detectSingle(List<GoodMatch> matches, int queryWidth, int queryHeight) {
        if (matches.size() < minMatchCount) {
            return null;
        }
        
        MatOfPoint2f queryPointsMat = null;
        MatOfPoint2f trainPointsMat = null;
        Mat mask = null;
        Mat homography = null;
        MatOfPoint2f queryCornersMat = null;
        MatOfPoint2f detectedCornersMat = null;
        
        try {
            List<Point> queryPoints = new ArrayList<>();
            List<Point> trainPoints = new ArrayList<>();
            List<Integer> indices = new ArrayList<>();
            
            for (int i = 0; i < matches.size(); i++) {
                GoodMatch m = matches.get(i);
                queryPoints.add(m.queryPoint);
                trainPoints.add(m.trainPoint);
                indices.add(i);
            }
            
            queryPointsMat = new MatOfPoint2f();
            trainPointsMat = new MatOfPoint2f();
            queryPointsMat.fromList(queryPoints);
            trainPointsMat.fromList(trainPoints);
            
            mask = new Mat();
            
            // Correct order: src=query (template), dst=train (detection target)
            // This computes the transformation from query to train
            homography = Geometry.findHomography(
                queryPointsMat,  // src: query points (template)
                trainPointsMat,  // dst: train points (where to find)
                Geometry.RANSAC,
                ransacThreshold,
                mask,
                2000,
                0.995
            );
            
            // Improved homography null/empty check
            if (homography == null || homography.empty() || 
                homography.rows() != 3 || homography.cols() != 3) {
                return null;
            }
            
            // Homography validity check
            if (!isValidHomography(homography)) {
                return null;
            }
            
            int inlierCount = Core.countNonZero(mask);
            
            // inlierRatio: ratio of inliers among matches used in RANSAC
            // This represents "what percentage of Ratio-Test-passed matches are actually inliers"
            // Division by zero protection (though matches.size() should always be > 0 here)
            double inlierRatio = (matches.size() > 0) ? (double)inlierCount / matches.size() : 0.0;
            
            if (inlierCount < minMatchCount || inlierRatio < minInlierRatio) {
                return null;
            }
            
            Point[] queryCorners = new Point[4];
            queryCorners[0] = new Point(0, 0);
            queryCorners[1] = new Point(queryWidth, 0);
            queryCorners[2] = new Point(queryWidth, queryHeight);
            queryCorners[3] = new Point(0, queryHeight);
            
            queryCornersMat = new MatOfPoint2f(queryCorners);
            detectedCornersMat = new MatOfPoint2f();
            
            Core.perspectiveTransform(queryCornersMat, detectedCornersMat, homography);
            
            Point[] detectedCorners = detectedCornersMat.toArray();
            
            // Check if detected corners form a valid convex quadrilateral
            if (!isValidQuadrilateral(detectedCorners, queryWidth, queryHeight)) {
                return null;
            }
            
            List<Integer> usedIndices = new ArrayList<>();
            List<MatchPair> inlierMatches = new ArrayList<>();
            List<MatchPair> outlierMatches = new ArrayList<>();
            
            for (int i = 0; i < mask.rows(); i++) {
                GoodMatch m = matches.get(indices.get(i));
                MatchPair pair = new MatchPair(m.queryPoint, m.trainPoint, m.distance);
                
                if (mask.get(i, 0)[0] == 1.0) {
                    usedIndices.add(indices.get(i));
                    inlierMatches.add(pair);
                } else {
                    outlierMatches.add(pair);
                }
            }
            
            return new DetectionResult(detectedCorners, matches.size(), inlierCount, 
                                      usedIndices, homography.clone(), inlierMatches, outlierMatches);
        } catch (Exception e) {
            return null;
        } finally {
            // Release temporary resources
            if (queryPointsMat != null) {
                queryPointsMat.release();
            }
            if (trainPointsMat != null) {
                trainPointsMat.release();
            }
            if (mask != null) {
                mask.release();
            }
            if (homography != null) {
                homography.release();
            }
            if (queryCornersMat != null) {
                queryCornersMat.release();
            }
            if (detectedCornersMat != null) {
                detectedCornersMat.release();
            }
        }
    }
    
    /**
     * Detect multiple objects
     */
    private List<DetectionResult> detectMultiple(List<GoodMatch> allMatches, int queryWidth, int queryHeight) {
        List<DetectionResult> results = new ArrayList<>();
        List<GoodMatch> remainingMatches = new ArrayList<>(allMatches);
        
        for (int detection = 0; detection < maxDetections; detection++) {
            if (remainingMatches.size() < minMatchCount) {
                break;
            }
            
            DetectionResult result = detectSingle(remainingMatches, queryWidth, queryHeight);
            
            if (result == null) {
                break;
            }
            
            boolean shouldAdd = true;
            for (DetectionResult existing : results) {
                double overlap = calculateOverlap(result.corners, existing.corners);
                if (overlap > overlapThreshold) {
                    shouldAdd = false;
                    break;
                }
            }
            
            if (shouldAdd) {
                results.add(result);
            }
            
            List<GoodMatch> newRemaining = new ArrayList<>();
            for (int i = 0; i < remainingMatches.size(); i++) {
                if (!result.usedIndices.contains(allMatches.indexOf(remainingMatches.get(i)))) {
                    newRemaining.add(remainingMatches.get(i));
                }
            }
            remainingMatches = newRemaining;
        }
        
        return results;
    }
    
    /**
     * Check if homography is valid
     * - Determinant should be positive (not degenerate or mirrored)
     * - Determinant should not be too large (reasonable transformation)
     */
    private boolean isValidHomography(Mat H) {
        if (H == null || H.rows() != 3 || H.cols() != 3) {
            return false;
        }
        
        try {
            double det = Core.determinant(H);
            
            // Check for degenerate or mirrored transformations
            if (det <= 0) {
                return false;
            }
            
            // Check for extreme scaling (too small or too large)
            if (det < 1e-6 || det > 1e6) {
                return false;
            }
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check if quadrilateral is valid
     * - Must be convex
     * - Area should not be too small or too large
     * - All points should be reasonably positioned
     */
    private boolean isValidQuadrilateral(Point[] corners, int queryWidth, int queryHeight) {
        if (corners == null || corners.length != 4) {
            return false;
        }
        
        // Check if any corner is at infinity or NaN
        for (Point p : corners) {
            if (Double.isNaN(p.x) || Double.isNaN(p.y) ||
                Double.isInfinite(p.x) || Double.isInfinite(p.y)) {
                return false;
            }
        }
        
        // Check if quadrilateral is convex using cross product
        if (!isConvex(corners)) {
            return false;
        }
        
        // Check area
        double area = calculateArea(corners);
        double queryArea = queryWidth * queryHeight;
        
        // Area should not be too small (< 1% of query) or too large (> 100x query)
        if (area < queryArea * 0.01 || area > queryArea * 100) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Check if quadrilateral is convex
     */
    private boolean isConvex(Point[] corners) {
        int n = corners.length;
        boolean hasPositive = false;
        boolean hasNegative = false;
        
        for (int i = 0; i < n; i++) {
            Point p1 = corners[i];
            Point p2 = corners[(i + 1) % n];
            Point p3 = corners[(i + 2) % n];
            
            double dx1 = p2.x - p1.x;
            double dy1 = p2.y - p1.y;
            double dx2 = p3.x - p2.x;
            double dy2 = p3.y - p2.y;
            
            double crossProduct = dx1 * dy2 - dy1 * dx2;
            
            if (crossProduct > 0) {
                hasPositive = true;
            } else if (crossProduct < 0) {
                hasNegative = true;
            }
            
            if (hasPositive && hasNegative) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Calculate area of quadrilateral using shoelace formula
     */
    private double calculateArea(Point[] corners) {
        double area = 0;
        int n = corners.length;
        
        for (int i = 0; i < n; i++) {
            Point p1 = corners[i];
            Point p2 = corners[(i + 1) % n];
            area += p1.x * p2.y - p2.x * p1.y;
        }
        
        return Math.abs(area) / 2.0;
    }
    
    /**
     * Calculate overlap ratio between two rectangles (bounding box approximation)
     */
    private double calculateOverlap(Point[] corners1, Point[] corners2) {
        Rect rect1 = getBoundingRect(corners1);
        Rect rect2 = getBoundingRect(corners2);
        
        int x1 = Math.max(rect1.x, rect2.x);
        int y1 = Math.max(rect1.y, rect2.y);
        int x2 = Math.min(rect1.x + rect1.width, rect2.x + rect2.width);
        int y2 = Math.min(rect1.y + rect1.height, rect2.y + rect2.height);
        
        if (x2 <= x1 || y2 <= y1) {
            return 0.0;
        }
        
        int intersectionArea = (x2 - x1) * (y2 - y1);
        int area1 = rect1.width * rect1.height;
        int area2 = rect2.width * rect2.height;
        int minArea = Math.min(area1, area2);
        
        return (double)intersectionArea / minArea;
    }
    
    /**
     * Get bounding rectangle from 4 corners
     */
    private Rect getBoundingRect(Point[] corners) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;
        
        for (Point p : corners) {
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
        }
        
        return new Rect(
            (int)minX,
            (int)minY,
            (int)(maxX - minX),
            (int)(maxY - minY)
        );
    }
    
    /**
     * Show detection results in ResultsTable
     * 
     * @param results detection results
     * @param resetTable true: reset table, false: append to existing table
     */
    public void showData(List<DetectionResult> results, boolean resetTable) {
        if (results == null || results.isEmpty()) {
            return;
        }
        
        ResultsTable dst_rt = OCV__LoadLibrary.GetResultsTable(resetTable);
        
        for (DetectionResult result : results) {
            // Calculate centroid of polygon
            double centerX = 0;
            double centerY = 0;
            
            for (int i = 0; i < 4; i++) {
                centerX += result.corners[i].x;
                centerY += result.corners[i].y;
            }
            centerX /= 4.0;
            centerY /= 4.0;
            
            // Calculate average distance
            double avgDistance = calculateAverageDistance(result);
            
            dst_rt.incrementCounter();
            dst_rt.addValue("CenterX", centerX);
            dst_rt.addValue("CenterY", centerY);
            dst_rt.addValue("Match", result.matchCount);
            dst_rt.addValue("Inlier", result.inlierCount);
            dst_rt.addValue("Ratio", result.inlierRatio);
            dst_rt.addValue("Confidence", result.confidence);
            dst_rt.addValue("AvgDistance", avgDistance);
        }
        
        dst_rt.show("Results");
    }
    
    /**
     * Add detection results to RoiManager
     * 
     * @param results detection results
     * @param resetRoiManager true: reset RoiManager, false: append to existing
     * @param showNone true: hide all ROIs, false: show all
     */
    public void addRoiManager(List<DetectionResult> results, boolean resetRoiManager, boolean showNone) {
        if (results == null || results.isEmpty()) {
            return;
        }
        
        RoiManager roiMan = OCV__LoadLibrary.GetRoiManager(resetRoiManager, showNone);
        
        for (DetectionResult result : results) {
            int num_roiMan = roiMan.getCount();
            
            // Create polygon points
            float[] pnts_x = new float[5];
            float[] pnts_y = new float[5];
            
            for (int i = 0; i < 4; i++) {
                pnts_x[i] = (float)result.corners[i].x;
                pnts_y[i] = (float)result.corners[i].y;
            }
            pnts_x[4] = pnts_x[0];  // Close polygon
            pnts_y[4] = pnts_y[0];
            
            // Create and add ROI
            PolygonRoi roi = new PolygonRoi(pnts_x, pnts_y, Roi.POLYLINE);
            roi.setPosition(num_roiMan + 1);  // Start from one
            roiMan.addRoi(roi);
            roiMan.select(num_roiMan);  // Start from zero
        }
    }
    
    /**
     * Calculate average match distance for a detection result
     * 
     * @param result detection result
     * @return average distance
     */
    private double calculateAverageDistance(DetectionResult result) {
        if (result.inlierMatches == null || result.inlierMatches.isEmpty()) {
            return 0;
        }
        
        double sumDistance = 0;
        for (MatchPair pair : result.inlierMatches) {
            sumDistance += pair.distance;
        }
        
        return sumDistance / result.inlierMatches.size();
    }
}