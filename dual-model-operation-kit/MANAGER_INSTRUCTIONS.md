# Manager Instructions

Last updated: 2026-11-01

## Instruction ID

`I-0005`

## Current Phase

`Group G3 Bug Fixes`: `Step 5 - Fix Mat memory leaks in AdaptiveThreshold/DistanceTransform, preview corruption/crash in Watershed/GrabCut, and FloodFill/InteractiveGrabCut issues`

## Current Status

Coder report for `I-0004` was accepted and merged into `master`.
Now starting `I-0005` based on findings from Group G3 code inspection.

- Completed: `I-0001` (Feature Matching cleanup), `I-0002` (G2 Edge/Corner fixes), `I-0003` (PeriodDetector bug fixes & subpixel unification), `I-0004` (G1 Filter/Blur/EqualizeHist fixes)
- Remaining: Execute `I-0005` across the 6 G3 Java classes, then report in `dual-model-operation-kit/CODER_STATUS.md`.
- Verification: Use `git diff` and `git status` (do NOT run `ant` in the Linux container because `ij.jar` and `opencv-500.jar` are git-ignored).
- Notes: Do not leave any temporary helper scripts in the working tree.

## Objective Alignment

Reference: `DUAL_MODEL_OPERATION.md` / `Project Objective`

- Eliminates native `Mat` memory leaks in `OCV_AdaptiveThreshold` and `OCV_DistanceTransform`, prevents preview-induced mask corruption and OpenCV crashes in `OCV_Watershed` and `OCV_GrabCut`, ensures mask windows redraw properly, and fixes initialization/resource cleanup in `OCV_FloodFill` and `OCV_InteractiveGrabCut`.

## Principle Application

Reference: `DUAL_MODEL_OPERATION.md` / `Project Principle`

- Modifies only the 6 G3 Java files identified during the G3 inspection while preserving macro parameter names and existing workflows (such as `Watershed_Macro.txt`).

## Persistent Decisions

- Preserve existing working plugin behavior and macro parameter names (`STR_DISTANCETYPE`, `STR_DISTANCETRANSFORMMASKS`, etc.).
- Allocate OpenCV `Mat` instances locally inside `run()` and always release them in `finally` blocks rather than caching them in instance fields across runs.
- Do not run `ant` or native OpenCV tests inside the Linux container (`ij.jar` and `opencv-500.jar` are not tracked in git).

## Manager Decision

Status: `Not Yet Reviewed`

- Next phase: `Step 5 - Fix Mat memory leaks in AdaptiveThreshold/DistanceTransform, preview corruption/crash in Watershed/GrabCut, and FloodFill/InteractiveGrabCut issues`
- Why now: Group G3 inspection identified clear memory leaks, preview crash/data-corruption bugs on secondary mask images, and minor initialization issues.
- Non-goals: Modifying `OCV_Threshold.java` or any files outside Group G3.

## Instruction For Coder

Implement the following changes:

1. `src/OCV_AdaptiveThreshold.java`:
   - Remove instance fields `src_mat` and `dst_mat`, remove `releaseResources()`, and remove `releaseResources();` from `if(gd.wasCanceled())`.
   - In `run(ImageProcessor ip)`:
     Allocate `Mat src_mat = null; Mat dst_mat = null;` locally and release both in a `finally` block:
     ```java
     @Override
     public void run(ImageProcessor ip) {
         int imw = ip.getWidth();
         int imh = ip.getHeight();
         byte[] srcdst_ar = (byte[])ip.getPixels();

         Mat src_mat = null;
         Mat dst_mat = null;

         try {
             src_mat = new Mat(imh, imw, CvType.CV_8UC1);
             dst_mat = new Mat(imh, imw, CvType.CV_8UC1);
             src_mat.put(0, 0, srcdst_ar);
             Imgproc.adaptiveThreshold(src_mat, dst_mat, maxValue, INT_ADAPTIVEMETHOD[indMethod], INT_THRESHOLDTYPE[indType], blockSize, constantOffset);
             dst_mat.get(0, 0, srcdst_ar);
         } catch(Exception e) {
             OCV__LoadLibrary.logError(className, "Adaptive threshold failed (" + e.getMessage() + ")");
         } finally {
             if (src_mat != null) src_mat.release();
             if (dst_mat != null) dst_mat.release();
         }
     }
     ```

2. `src/OCV_DistanceTransform.java`:
   - Replace the hardcoded `CV_DIST_L1`, `CV_DIST_L2`, `CV_DIST_C` constants with `Imgproc.DIST_L1`, `Imgproc.DIST_L2`, `Imgproc.DIST_C` in `INT_DISTANCETYPE`:
     ```java
     private static final int[] INT_DISTANCETYPE = { Imgproc.DIST_L1, Imgproc.DIST_L2, Imgproc.DIST_C };
     private static final String[] STR_DISTANCETYPE = { "CV_DIST_L1", "CV_DIST_L2", "CV_DIST_C" };
     ```
     (Keep `STR_DISTANCETYPE` and `STR_DISTANCETRANSFORMMASKS` unchanged for macro compatibility.)
   - Remove instance fields `srcMat32f`, `srcMat8u`, `dstMat32f`, `cachedWidth`, `cachedHeight` and remove methods `allocateMatIfNeeded` and `releaseResources` (and remove `releaseResources();` from `if(gd.wasCanceled())`).
   - In `run(ImageProcessor ip)`:
     Allocate `Mat srcMat32f = null; Mat srcMat8u = null; Mat dstMat32f = null;` locally and release all three in a `finally` block:
     ```java
     @Override
     public void run(ImageProcessor ip) {
         Mat srcMat32f = null;
         Mat srcMat8u = null;
         Mat dstMat32f = null;

         try {
             int imw = ip.getWidth();
             int imh = ip.getHeight();
             float[] srcdstFloats = (float[])ip.getPixels();

             srcMat32f = new Mat(imh, imw, CvType.CV_32FC1);
             srcMat8u = new Mat(imh, imw, CvType.CV_8UC1);
             dstMat32f = new Mat(imh, imw, CvType.CV_32FC1);

             srcMat32f.put(0, 0, srcdstFloats);
             srcMat32f.convertTo(srcMat8u, CvType.CV_8UC1);
             Imgproc.distanceTransform(srcMat8u, dstMat32f, INT_DISTANCETYPE[indDistType], INT_DISTANCETRANSFORMMASKS[indMskSize]);
             dstMat32f.get(0, 0, srcdstFloats);
         }
         catch(Exception e) {
             OCV__LoadLibrary.logError(className, "Distance transform failed (" + e.getMessage() + ")");
         }
         finally {
             if(srcMat32f != null) srcMat32f.release();
             if(srcMat8u != null) srcMat8u.release();
             if(dstMat32f != null) dstMat32f.release();
         }
     }
     ```

3. `src/OCV_Watershed.java`:
   - Remove `KEEP_PREVIEW` from `FLAGS`:
     ```java
     private static final int FLAGS = DOES_32 | DOES_RGB; // Input 8-bit 3-channel image, and Input/output 32-bit single-channel map.
     ```
   - In `showDialog`: remove `gd.addPreviewCheckbox(pfr);` (keep `gd.addDialogListener(this);` so `dialogItemChanged` still runs when the dialog opens and when OK is clicked).
   - In `run`: after `matMap32f.get(0, 0, arrMap32f);`, call `impMap.updateAndDraw();`.

4. `src/OCV_GrabCut.java`:
   - Remove `KEEP_PREVIEW` from `FLAGS`:
     ```java
     private static final int FLAGS = DOES_8G | DOES_RGB; // Input 8-bit 3-channel image, and Input/output 8-bit single-channel mask.
     ```
   - In `showDialog`: remove `gd.addPreviewCheckbox(pfr);` (keep `gd.addDialogListener(this);`).
   - In `dialogItemChanged`: add validation for `iter`:
     ```java
     if(iter <= 0) {
         IJ.showStatus("'0 < iterCount' is necessary.");
         return false;
     }
     ```
   - In `setup`: when obtaining `rectJava` from `imp.getRoi()`, if the ROI covers or exceeds the entire image (`rectJava.x <= 0 && rectJava.y <= 0 && rectJava.width >= imp.getWidth() && rectJava.height >= imp.getHeight()`), shrink it by 1px inside the image bounds:
     ```java
     if(imp.getRoi() != null) {
         rectJava = imp.getRoi().getBounds();
         if(rectJava.x <= 0 && rectJava.y <= 0 && rectJava.width >= imp.getWidth() && rectJava.height >= imp.getHeight()) {
             rectJava = new Rectangle(1, 1, Math.max(1, imp.getWidth() - 2), Math.max(1, imp.getHeight() - 2));
         }
     }
     else {
         rectJava = new Rectangle(1, 1, Math.max(1, imp.getWidth() - 2), Math.max(1, imp.getHeight() - 2));
     }
     ```
   - In `run`: after the `if(enFgd) { ... }` block, call `impMsk.updateAndDraw();`.

5. `src/OCV_FloodFill.java`:
   - Initialize `private String className = "OCV_FloodFill";` instead of `null` so error messages in `setup()` do not log `null`.

6. `src/OCV_InteractiveGrabCut.java`:
   - Initialize `private String titleCmd = "OCV_InteractiveGrabCut";` instead of `null` so error messages in `setup()` do not log `null`.
   - In `showDialog()`: change `opacity = (int)gd.getNextNumber();` to `opacity = gd.getNextNumber();`.
   - In `releaseResources()`: also release `impOv`:
     ```java
     if(impOv != null) {
         impOv.flush();
         impOv = null;
     }
     ```

7. `dual-model-operation-kit/CODER_STATUS.md`:
   - Update with your execution report for `I-0005`.

## Scope And Boundaries

Allowed changes:

- `src/OCV_AdaptiveThreshold.java`
- `src/OCV_DistanceTransform.java`
- `src/OCV_Watershed.java`
- `src/OCV_GrabCut.java`
- `src/OCV_FloodFill.java`
- `src/OCV_InteractiveGrabCut.java`
- `dual-model-operation-kit/CODER_STATUS.md`

Must not change:

- `dual-model-operation-kit/DUAL_MODEL_OPERATION.md`
- `dual-model-operation-kit/MANAGER_INSTRUCTIONS.md`
- `dual-model-operation-kit/STEPS.md`
- `dual-model-operation-kit/README.md`
- `src/OCV_Threshold.java`
- Any other files in `src/` or `manual_jp/`

## Acceptance Criteria

1. `OCV_AdaptiveThreshold` and `OCV_DistanceTransform` allocate `Mat` objects locally in `run()` and release them in `finally` blocks, with all instance-cached `Mat` fields removed, and `OCV_DistanceTransform` uses `Imgproc.DIST_L1`, `Imgproc.DIST_L2`, `Imgproc.DIST_C`.
2. `OCV_Watershed` and `OCV_GrabCut` remove `KEEP_PREVIEW` and `gd.addPreviewCheckbox(pfr)` while keeping `gd.addDialogListener(this)`, and call `impMap.updateAndDraw()` / `impMsk.updateAndDraw()` in `run()`.
3. `OCV_GrabCut` validates `iter > 0` in `dialogItemChanged` and clamps full-image ROI bounds in `setup()`.
4. `OCV_FloodFill` initializes `className = "OCV_FloodFill"`, and `OCV_InteractiveGrabCut` initializes `titleCmd = "OCV_InteractiveGrabCut"`, reads `opacity = gd.getNextNumber()`, and flushes `impOv` in `releaseResources()`.
5. `dual-model-operation-kit/CODER_STATUS.md` is updated for `I-0005`, and no files outside the allowed scope are modified.

## Verification

Minimum verification in container:

```bash
git status --short
git diff --stat
```

Expected repository changes:

- Only the 7 allowed files listed in Scope And Boundaries are modified.

## Stop Conditions

Stop immediately and report if:

- Work requires touching files outside scope.
- Any acceptance criterion cannot be met.
