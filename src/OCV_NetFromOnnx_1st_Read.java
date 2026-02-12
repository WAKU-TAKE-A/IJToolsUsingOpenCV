
import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import java.io.File;

/**
 * 1st step: Load ONNX model into memory (OCV__LoadLibrary.MyNet).
 */
public class OCV_NetFromOnnx_1st_Read implements ExtendedPlugInFilter {
    private static String modelPath = "";

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        GenericDialog gd = new GenericDialog("Load ONNX Model");
        gd.addStringField("Model Path (.onnx)", modelPath, 40);
        gd.showDialog();

        if (gd.wasCanceled()) return DONE;

        modelPath = gd.getNextString();
        
        if (modelPath.isEmpty() || !new File(modelPath).exists()) {
            IJ.error("Model file not found: " + modelPath);
            return DONE;
        }
        
        return NO_IMAGE_REQUIRED; // No image needed for loading model
    }

    @Override
    public void setNPasses(int nPasses) {}

    @Override
    public int setup(String arg, ImagePlus imp) {
        if (!OCV__LoadLibrary.isLoad()) {
            IJ.error("OpenCV Library is not loaded.");
            return DONE;
        }
        return NO_IMAGE_REQUIRED;
    }

    @Override
    public void run(ImageProcessor ip) {
        try {
            if (OCV__LoadLibrary.MyNet == null) {
                OCV__LoadLibrary.MyNet = new MyNetFromONNX();
            }
            
            OCV__LoadLibrary.MyNet.read(modelPath);
            IJ.showStatus("Model Loaded: " + OCV__LoadLibrary.MyNet.getModelName());
        } catch (Exception e) {
            IJ.error("Failed to load model: " + e.getMessage());
        }
    }
}
