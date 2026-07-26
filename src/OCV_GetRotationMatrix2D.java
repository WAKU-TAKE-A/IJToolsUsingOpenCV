import ij.IJ;
import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.measure.ResultsTable;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import java.awt.AWTEvent;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.geometry.Geometry;
import org.opencv.imgproc.Imgproc;

/*
 * The MIT License
 *
 * Copyright 2016 Takehito Nishida.
 */

/**
 * getRotationMatrix2D.
 * 算出した回転行列を OCV__LoadLibrary.MyAffine にセットし、OCV_WarpAffine と連携可能にします。
 */
public class OCV_GetRotationMatrix2D implements ExtendedPlugInFilter, DialogListener {
    // constant var.
    private static final int FLAGS = NO_IMAGE_REQUIRED;

    // static var.
    private static double centerX = 0; // Center of the rotation in the source image (x)
    private static double centerY = 0; // Center of the rotation in the source image (y)
    private static double angle = 0; // Rotation angle in degrees
    private static double scale = 1; // Isotropic scale factor
    private static boolean enShowMat = false; // 行列を表示するかどうかのフラグ

    // instance var.
    private Point center = null;
    private String className = "";

    @Override
    public void setNPasses(int arg0) {
        // do nothing
    }

    @Override
    public int showDialog(ImagePlus imp, String cmd, PlugInFilterRunner prf) {
        className = cmd.trim();
        GenericDialog gd = new GenericDialog(className + " ...");

        gd.addNumericField("center_x", centerX, 4);
        gd.addNumericField("center_y", centerY, 4);
        gd.addNumericField("angle", angle, 4);
        gd.addNumericField("scale", scale, 4);
        gd.addCheckbox("enable_show_matrix", enShowMat); // チェックボックスの追加
        gd.addDialogListener(this);

        gd.showDialog();

        if(gd.wasCanceled()) {
            return DONE;
        }
        else {
            return FLAGS;
        }
    }

    @Override
    public boolean dialogItemChanged(GenericDialog gd, AWTEvent awte) {
        centerX = (double)gd.getNextNumber();
        centerY = (double)gd.getNextNumber();
        angle = (double)gd.getNextNumber();
        scale = (double)gd.getNextNumber();
        enShowMat = (boolean)gd.getNextBoolean(); // フラグの更新

        if(Double.isNaN(centerX) || Double.isNaN(centerY) || Double.isNaN(angle) || Double.isNaN(scale)) {
            IJ.showStatus("ERR : NaN");
            return false;
        }

        if(scale <= 0) {
            IJ.showStatus("'0 < scale' is necessary.");
            return false;
        }

        center = new Point(centerX, centerY);

        IJ.showStatus(className);
        return true;
    }

    @Override
    public void run(ImageProcessor ip) {
        Mat mat = null;
        Mat inv = null;

        try {
            // 回転行列(2x3)を計算
            mat = Geometry.getRotationMatrix2D(center, angle, scale);

            if(mat == null || mat.rows() <= 0 || mat.cols() <= 0) {
                OCV__LoadLibrary.logError(className, "Output is null or error.");
                return;
            }
            
            // 逆行列の計算 (OCV_WarpAffine の inverse 指定用)
            inv = new Mat();
            Geometry.invertAffineTransform(mat, inv);

            // 一時的な MyAffineTransform にデータを格納
            MyAffineTransform tmpAffine = new MyAffineTransform();
            tmpAffine.AffineMatrix = mat.clone();
            tmpAffine.AffineInverse = inv.clone();
            tmpAffine.hasMatrix = true; // フラグを立てる

            // 結果を表示する場合のみ ShowData() を実行
            if (enShowMat) {
                tmpAffine.ShowData();
            }
            
            // 全局の共通領域（LoadLibrary 内のインスタンス）にコピー
            // これにより OCV_WarpAffine が行列を認識できるようになります
            tmpAffine.copyTo(OCV__LoadLibrary.MyAffine);
            
            IJ.showStatus(className + ": Matrix updated.");
        }
        catch(Exception e) {
            OCV__LoadLibrary.logError(className, "Processing failed (" + e.getMessage() + ")");
        }
        finally {
            if(mat != null) mat.release();
            if(inv != null) inv.release();
        }
    }

    @Override
    public int setup(String arg0, ImagePlus imp) {
        if(!OCV__LoadLibrary.isLoad()) {
            OCV__LoadLibrary.logError("OCV_GetRotationMatrix2D", "Library is not loaded.");
            return DONE;
        }
        return FLAGS;
    }
}