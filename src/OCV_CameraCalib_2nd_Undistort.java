import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

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
 * Undistort an image using camera calibration parameters from the shared area.
 */
public class OCV_CameraCalib_2nd_Undistort implements ExtendedPlugInFilter {
    // constant var.
    private final int FLAGS = DOES_ALL + KEEP_PREVIEW;

    // var.
    private String className = "";

    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner pr) {
        className = cmd.trim();
        MyCameraCalibration mCalib = OCV__LoadLibrary.MyCameraCalib;

        // Check if calibration data is loaded in the shared area
        if (mCalib == null || !mCalib.hasResult || OCV__LoadLibrary.isNullOrEmpty(mCalib.calibName)) {
            OCV__LoadLibrary.logError(className, "No calibration data loaded. Please run 'OCV_CameraCalib_1st_Create' first.");
            return DONE;
        }

        GenericDialog gd = new GenericDialog(className + "...");
        
        // Only show current name as a message, no input field or selection allowed
        gd.addMessage("calibration_name: " + mCalib.calibName);

        gd.showDialog();

        if (gd.wasCanceled()) {
            return DONE;
        } else {
            return FLAGS;
        }
    }

    @Override
    public void run(ImageProcessor ip) {
        MyCameraCalibration mCalib = OCV__LoadLibrary.MyCameraCalib;
        Mat matSrc = null;
        Mat matDst = null;

        try {
            // Double check if matrices exist
            if (mCalib.cameraMatrix.empty() || mCalib.distCoeffs.empty()) {
                OCV__LoadLibrary.logError(className, "Calibration matrices are empty.");
                return;
            }

            // Convert ImageProcessor to OpenCV Mat
            matSrc = OCV__LoadLibrary.ip2mat(ip);
            matDst = new Mat();

            // Perform undistortion using OpenCV Imgproc.undistort
            Imgproc.undistort(matSrc, matDst, mCalib.cameraMatrix, mCalib.distCoeffs);

            // Convert result back to ImageProcessor and copy pixels
            ImageProcessor ipRes = OCV__LoadLibrary.mat2ip(matDst);
            OCV__LoadLibrary.ArrayCopy(ipRes, ip);

            // Success: show status message
            IJ.showStatus(className + ": Applied calibration \"" + mCalib.calibName + "\"");

        } catch (Exception e) {
            // Failure: use log for errors during run
            OCV__LoadLibrary.logError(className, e.getMessage());
        } finally {
            // Release OpenCV resources
            if (matSrc != null) {
                matSrc.release();
            }
            if (matDst != null) {
                matDst.release();
            }
        }
    }

    @Override
    public void setNPasses(int n) {
        // do nothing
    }

    @Override
    public int setup(String arg, ImagePlus imp) {
        if (!OCV__LoadLibrary.isLoad()) {
            OCV__LoadLibrary.logError("OCV_CameraCalib_2nd_Undistort", "Library is not loaded.");
            return DONE;
        }
        return FLAGS;
    }
}