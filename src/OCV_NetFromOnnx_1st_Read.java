import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import static ij.plugin.filter.PlugInFilter.DONE;
import static ij.plugin.filter.PlugInFilter.NO_IMAGE_REQUIRED;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import java.io.File;

/**
 * 1st step: Load ONNX model.
 * Select one of three model formats:
 *   YOLO Normalized / YOLO Pixel / YOLOX Undecoded
 */
public class OCV_NetFromOnnx_1st_Read implements ExtendedPlugInFilter {

    private static String  modelPath       = "";
    private static int     inputWidth      = 640;
    private static int     inputHeight     = 640;
    private static int     formatChoice    = 0;    // 0=YOLO_Pixel, 1=YOLO_Normalized, 2=YOLOX_Undecoded
    private static boolean showLog         = true;

    // Format choices (no AUTO)
    private static final String[] FORMAT_LABELS = {
        "YOLO_Pixel",
        "YOLO_Normalized",
        "YOLOX_Undecoded"
    };

    @Override
    public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
        GenericDialog gd = new GenericDialog("Load ONNX Model");
        gd.addStringField("model_path", modelPath, 40);
        gd.addNumericField("input_width",  inputWidth,  0);
        gd.addNumericField("input_height", inputHeight, 0);
        gd.addChoice("model_format", FORMAT_LABELS, FORMAT_LABELS[formatChoice]);
        gd.addCheckbox("enable_log", showLog);
        gd.addMessage("Note: Paths with quotes will be automatically trimmed.");

        gd.showDialog();
        if (gd.wasCanceled()) return DONE;

        modelPath    = trimQuotes(gd.getNextString());
        inputWidth   = (int) gd.getNextNumber();
        inputHeight  = (int) gd.getNextNumber();
        formatChoice = gd.getNextChoiceIndex();
        showLog      = gd.getNextBoolean();

        if (modelPath.isEmpty() || !new File(modelPath).exists()) {
            IJ.error("Model file not found: " + modelPath);
            return DONE;
        }
        if (inputWidth <= 0 || inputHeight <= 0) {
            IJ.error("Input dimensions must be positive.");
            return DONE;
        }

        return NO_IMAGE_REQUIRED;
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

            if (showLog) {
                IJ.log("=".repeat(60));
                IJ.log("Loading ONNX model...");
            }

            // Set input size
            OCV__LoadLibrary.MyNet.setInputSize(inputWidth, inputHeight);

            // Set model type and coordinate format from user choice
            switch (formatChoice) {
                case 0: // YOLO_Pixel
                    OCV__LoadLibrary.MyNet.setModelType(MyNetFromONNX.ModelType.YOLO);
                    OCV__LoadLibrary.MyNet.setCoordFormat(MyNetFromONNX.CoordFormat.YOLO_PIXEL);
                    break;
                case 1: // YOLO_Normalized
                    OCV__LoadLibrary.MyNet.setModelType(MyNetFromONNX.ModelType.YOLO);
                    OCV__LoadLibrary.MyNet.setCoordFormat(MyNetFromONNX.CoordFormat.YOLO_NORMALIZED);
                    break;
                case 2: // YOLOX_Undecoded
                    OCV__LoadLibrary.MyNet.setModelType(MyNetFromONNX.ModelType.YOLOX);
                    OCV__LoadLibrary.MyNet.setCoordFormat(MyNetFromONNX.CoordFormat.YOLOX_UNDECODED);
                    break;
            }

            // Load model (runs dummy inference internally to get numClasses)
            OCV__LoadLibrary.MyNet.read(modelPath);

            if (showLog) {
                IJ.log("Model file loaded: " + OCV__LoadLibrary.MyNet.getModelName());

                if (OCV__LoadLibrary.MyNet.getNumClassNamesLoaded() > 0) {
                    IJ.log("Loaded " + OCV__LoadLibrary.MyNet.getNumClassNamesLoaded() + " class names");
                }

                IJ.log("Input blob shape: [1, 3, " + inputWidth + ", " + inputHeight + "]");

                int[] outShape = OCV__LoadLibrary.MyNet.getOutputShape();
                if (outShape != null) {
                    IJ.log("Output shape: ["
                        + outShape[0] + ", "
                        + outShape[1] + ", "
                        + outShape[2] + "]");
                }

                IJ.log("Number of classes: " + OCV__LoadLibrary.MyNet.getNumClasses());
                IJ.log("Has objectness: "    + OCV__LoadLibrary.MyNet.hasObjectness());

                IJ.log("=".repeat(60));
                IJ.log("Model Load Complete:");
                IJ.log("  File: "       + OCV__LoadLibrary.MyNet.getModelName());
                IJ.log("  Input Size: " + inputWidth + " x " + inputHeight);
                IJ.log("  Format: "     + FORMAT_LABELS[formatChoice]);
                IJ.log("  "             + OCV__LoadLibrary.MyNet.getModelSummary());
                IJ.log("  Letterbox Preprocessing: ENABLED");
                IJ.log("  Custom NMS: ENABLED");
                IJ.log("=".repeat(60));
            }

            IJ.showStatus("Model loaded: " + OCV__LoadLibrary.MyNet.getModelName());

        } catch (Exception e) {
            IJ.error("Failed to load model: " + e.getMessage());
        }
    }

    private String trimQuotes(String str) {
        if (str == null || str.isEmpty()) return str;
        String s = str.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2)
            s = s.substring(1, s.length() - 1);
        if (s.startsWith("'") && s.endsWith("'") && s.length() >= 2)
            s = s.substring(1, s.length() - 1);
        return s;
    }
}
