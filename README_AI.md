# README for AI Assistants

This document contains important guidelines for AI coding assistants working on the `IJToolsUsingOpenCV` project. Please read these instructions carefully before proposing or implementing changes.

## 1. Project Structure and Build System
- **Build System**: This is an Apache Ant-based NetBeans project.
- **Compilation**: Do not rely solely on the IDE. To compile and package the project, use the Ant binary provided by NetBeans.
  - Run: `& "C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat" clean jar`
- **Plugin Naming**: ImageJ plugins must have an underscore `_` in their class name (e.g., `WK_RoiMan_PeriodDetector`, `OCV_Blur`).

## 2. Packaging and Dependency Rules (CRITICAL)
When you create a new class, you must pay attention to how it gets packaged into the JAR files. ImageJ requires plugins to be in a specific format, and `build.xml` handles this distribution.

### Plugins vs. Utilities
- **Plugin Classes (`WK_*.java`, `OCV_*.java`)**: The `build.xml` is configured to move compiled plugin classes into the `plugins/` directory.
- **Utility / API Classes (Non-Plugins)**: If you create new helper classes, utility functions, or data classes (e.g., `PeriodDetector.java`, `PeriodResult.java`), **DO NOT** let them be copied loosely into the `plugins/` directory. They must be packaged inside the main JAR (e.g., `IJTools_Waku.jar` or `IJTools_UsingOpenCV.jar`).
- **How to include Utilities in the JAR**:
  - Open `build.xml`.
  - Locate the `<jar>` task inside the `-post-compile` target.
  - You **MUST** add a `<fileset>` line to explicitly include your new utility classes.
  - Example: `<fileset dir="${build.classes.dir}" includes="YourNewUtility*.class"/>`
  - **Failure to do this will result in a `java.lang.NoClassDefFoundError` when the user tries to run the plugin in ImageJ.**

## 3. The "My" Prefix Classes (e.g., MyNetFromONNX, MyFeatureMatcher)
This project utilizes a design pattern where complex OpenCV operations are encapsulated into helper classes prefixed with `My` (e.g., `MyCameraCalibration`, `MyFeatureDetector`).
- **Encapsulation**: These classes abstract away the heavy C++-like OpenCV APIs (like DNN module handling, descriptor matching, and calibration) into clean, object-oriented Java interfaces.
- **Not Plugins**: Because their class names do not contain an underscore (`_`), ImageJ's plugin loader automatically ignores them and does not attempt to add them to the Plugins menu. This is intentional.
- **Resource Management**: These classes often manage the lifecycle of complex OpenCV `Mat` objects internally. When using them or creating new ones, ensure they provide proper cleanup methods or handle memory efficiently.

## 4. OpenCV Integration Best Practices
This project heavily utilizes the Java wrapper for OpenCV. When creating or modifying `OCV_*.java` classes, adhere to the following rules:
- **Library Check**: Always check if the OpenCV library is loaded at the beginning of the `setup` method using `if(!OCV__LoadLibrary.isLoad()) { return DONE; }`.
- **Memory Management (CRITICAL)**: OpenCV `Mat` objects created in Java are backed by native C++ memory. You **MUST** manually call `.release()` on every `Mat` (or `MatOfPoint`, etc.) object when you are done with it. Use `try-catch-finally` blocks or explicit resource management to prevent severe memory leaks.
- **Image Conversion**: Use the helper methods in `OCV__LoadLibrary` to convert between ImageJ's `ImageProcessor` and OpenCV's `Mat` (e.g., `OCV__LoadLibrary.intarray2mat`, `OCV__LoadLibrary.ip2mat`).
- **Bit Depth Handling**: `ImageProcessor` can be 8-bit, 16-bit, 24-bit (RGB), or 32-bit (Float). Ensure your OpenCV code explicitly handles the bit depth by mapping it to the correct `CvType` (e.g., `CV_8UC1`, `CV_16U`, `CV_8UC3`, `CV_32F`). See `OCV_Blur.java` for a good example of type branching.
- **Mat.get() 1xN Bug (CRITICAL)**: In the OpenCV 5 Java API, calling `mat.get(0, col, array)` on a `1 x N` Mat does not advance the memory pointer correctly and will repeatedly return the elements of the 0-th column. To safely read a `1 x N` Mat (such as the output from `HoughLines`, `HoughLinesP`, or `HoughCircles`), you **MUST** allocate a single large array and fetch all data at once using `mat.get(0, 0, largeArray)`, then manually calculate the offset `(i * channels)` inside your Java loop.

## 4. ImageJ GUI (GenericDialog) Best Practices
- **Variables**: GUI options should be stored as `private static` variables in the plugin class. This ensures that ImageJ remembers the user's last used settings between plugin runs.
- **Preview Support**: If your plugin modifies the image visually, implement `ij.gui.DialogListener` and add `gd.addPreviewCheckbox(pfr);` to support live previews (see `OCV_Blur.java`).
- **Labeling**: Avoid using spaces or special characters in the labels if they might be called from ImageJ macros. Use lowercase labels with underscores (e.g., `ma_window_size`).
- **Defaults**: Choose algorithmic defaults carefully. For signal processing or generic algorithms, prefer the most robust defaults (e.g., Linear Regression over Moving Average) when dealing with unknown data.

## 5. ROI (Region of Interest) Handling
- When processing an image with an active ROI, calculate the coordinate offsets. OpenCV processes the cropped `Mat`, so output coordinates (like bounding boxes or point coordinates) must be shifted back by `roiRect.x` and `roiRect.y` to align with the original image (see `OCV_NetFromOnnx_2nd_Inference.java` and `OCV_BoundingRect.java`).
- For complex ROI shapes, duplicate the `ImageProcessor`, use `.fillOutside(roiSrc)` with a background color, and then `.crop()` to ensure only the masked pixels are sent to OpenCV.

## 6. Outputting Results (ResultsTable & RoiManager)
- Use `OCV__LoadLibrary.GetResultsTable(reset)` and `OCV__LoadLibrary.GetRoiManager(reset, true)` to fetch references safely.
- Do not call `ResultsTable.show("Results")` blindly at the start of a plugin. Only call `show()` after processing is complete and you have confirmed that valid results were actually added to the table.

## 7. Documentation
- When creating a new plugin, always create a corresponding manual in the `manual_jp/` directory.
- Follow the existing Markdown format (e.g., `manual_jp/WK_RoiMan_LinearFitting.md`).

## 8. Analyzing and Migrating OpenCV Versions
If you encounter compilation errors due to OpenCV version upgrades (e.g., migrating to OpenCV 5 where many classes and methods are restructured):
- **Do not rely on web searches for Java API documentation**, as OpenCV Java wrappers often have poor or outdated documentation online.
- **Analyze the JAR directly**: Use the `jar` and `javap` tools to dynamically inspect the contents of the local OpenCV JAR file (e.g., `opencv-500.jar`).
  - To find new modules/packages: `jar tf opencv-500.jar > jar_contents.txt` and search for `.class` files.
  - To locate missing methods (e.g., `convexHull` moved from `Imgproc` to `Geometry`): Run `javap -cp opencv-500.jar org.opencv.geometry.Geometry` to list available methods and their precise signatures.
  - To find constants (e.g., `CV_DIST_L1` or `CALIB_CB_SYMMETRIC_GRID`): Run `javap -cp opencv-500.jar -constants org.opencv.calib.Calib` to dump the constant values.
- **Handling Removed Constants**: Sometimes constants (like `CV_DIST_L1 = 1`) are completely removed from the Java wrapper (or lose their `CV_` prefix). Use `javap -constants` to verify. If they are truly gone, redefine them locally in your Java file as `private static final int` to maintain backward compatibility with the C++ backend.
- **Removed Algorithms**: If an algorithm (like `AKAZE` or `BRISK`) is removed from the core modules, gracefully remove it from the UI arrays (`String[]`) and fallback/branching logic.
