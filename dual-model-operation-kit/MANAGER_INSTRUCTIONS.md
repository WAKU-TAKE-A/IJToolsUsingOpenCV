# Manager Instructions

Last updated: 2026-11-01

## Instruction ID

`I-0004`

## Current Phase

`Group G1 Bug Fixes`: `Step 4 - Fix native Mat memory leaks in Blur/GaussianBlur/BilateralFilter, GaussianBlur ksize=0 crash, EqualizeHist Undo bug, and manual updates`

## Current Status

Coder report for `I-0003` was accepted and merged into `master`.
Now starting `I-0004` based on findings from Group G1 code inspection.

- Completed: `I-0001` (Feature Matching cleanup), `I-0002` (G2 Edge/Corner fixes), `I-0003` (PeriodDetector bug fixes & subpixel unification)
- Remaining: Execute `I-0004` across the 4 G1 Java classes and 2 Japanese manuals, then report in `dual-model-operation-kit/CODER_STATUS.md`.
- Verification: Use `git diff` and `git status` (do NOT run `ant` in the Linux container because `ij.jar` and `opencv-500.jar` are git-ignored).
- Notes: Do not leave any temporary helper scripts in the working tree.

## Objective Alignment

Reference: `DUAL_MODEL_OPERATION.md` / `Project Objective`

- Eliminates native `Mat` memory leaks in `OCV_Blur`, `OCV_GaussianBlur`, and `OCV_BilateralFilter`, prevents OpenCV assertion crashes in `OCV_GaussianBlur` when `ksize=0` and `sigma=0`, restores ImageJ Undo support in `OCV_EqualizeHist`, and aligns UI parameter types and Japanese manuals.

## Principle Application

Reference: `DUAL_MODEL_OPERATION.md` / `Project Principle`

- Modifies only the 4 G1 Java files and 2 Japanese manuals identified during the G1 inspection without touching unrelated files.

## Persistent Decisions

- Preserve existing working plugin behavior and macro parameter names.
- Allocate OpenCV `Mat` instances locally inside `run()` (or helper methods called from `run()`) and always release them in `finally` blocks rather than caching them in instance fields across runs.
- Do not run `ant` or native OpenCV tests inside the Linux container (`ij.jar` and `opencv-500.jar` are not tracked in git).

## Manager Decision

Status: `Not Yet Reviewed`

- Next phase: `Step 4 - Fix native Mat memory leaks in Blur/GaussianBlur/BilateralFilter, GaussianBlur ksize=0 crash, EqualizeHist Undo bug, and manual updates`
- Why now: Group G1 inspection identified clear, high-priority memory leaks, an OpenCV assertion crash path, and an ImageJ Undo bug.
- Non-goals: Modifying `OCV_MedianBlur`, `OCUtil_BluredImageDiff`, `OCV_MorphologyEx`, or any files outside Group G1.

## Instruction For Coder

Implement the following changes:

1. `src/OCV_Blur.java`:
   - Change `ksize_x` and `ksize_y` from `double` to `int`:
     ```java
     private static int ksize_x = 3; // Blurring kernel size of x
     private static int ksize_y = 3; // Blurring kernel size of y
     ```
   - In `showDialog`:
     - Change decimal places of `ksize_x` and `ksize_y` from `4` to `0`:
       ```java
       gd.addNumericField("ksize_x", ksize_x, 0);
       gd.addNumericField("ksize_y", ksize_y, 0);
       ```
     - Remove `releaseResources();` from `if(gd.wasCanceled())`.
   - In `dialogItemChanged`:
     - Read `ksize_x = (int)gd.getNextNumber();` and `ksize_y = (int)gd.getNextNumber();`.
     - Remove the `Double.isNaN(ksize_x) || Double.isNaN(ksize_y)` check (since they are now `int` like `OCUtil_BluredImageDiff` and `OCV_GaussianBlur`, and `<= 0` check handles invalid/0 values).
   - Remove instance fields `src_mat`, `dst_mat`, `cachedWidth`, `cachedHeight`, `cachedBitDepth` and remove methods `allocateMatIfNeeded` and `releaseResources`.
   - In `run(ImageProcessor ip)` and `runForGrayscale8`, `runForGrayscale16`, `runForRGB`, `runForFloat32`:
     Allocate `Mat src_mat = null; Mat dst_mat = null;` locally and release both in a `finally` block (following the pattern in `OCV_MedianBlur.java`), e.g.:
     ```java
     private void runForGrayscale8(ImageProcessor ip, int imw, int imh) {
         Mat src_mat = null;
         Mat dst_mat = null;
         try {
             byte[] srcdst_bytes = (byte[])ip.getPixels();
             src_mat = new Mat(imh, imw, CvType.CV_8UC1);
             dst_mat = new Mat(imh, imw, CvType.CV_8UC1);
             src_mat.put(0, 0, srcdst_bytes);
             Imgproc.blur(src_mat, dst_mat, ksize, ANCHOR_POINT, INT_BORDERTYPE[indBorderType]);
             dst_mat.get(0, 0, srcdst_bytes);
         } finally {
             if (src_mat != null) src_mat.release();
             if (dst_mat != null) dst_mat.release();
         }
     }
     ```
     (Apply the same `try ... finally` local `Mat` release pattern to `runForGrayscale16`, `runForRGB`, and `runForFloat32`, and remove `releaseResources()` from `catch(Exception e)` in `run()`.)

2. `src/OCV_GaussianBlur.java` & `manual_jp/OCV_GaussianBlur.md`:
   - In `src/OCV_GaussianBlur.java`:
     - Remove instance fields `srcMat`, `dstMat`, `cachedWidth`, `cachedHeight`, `cachedBitDepth` and remove methods `allocateMatIfNeeded` and `releaseResources` (and remove `releaseResources()` calls in `showDialog` and `run`).
     - In `runForGrayscale8`, `runForGrayscale16`, `runForFloat32`, allocate `Mat srcMat = null; Mat dstMat = null;` locally and release both in a `finally` block.
     - In `dialogItemChanged`, after checking `Double.isNaN(sigma_x) || Double.isNaN(sigma_y)`, add validation to prevent negative sigmas and prevent `ksize = 0` when `sigma <= 0`:
       ```java
       if(sigma_x < 0 || sigma_y < 0) {
           IJ.showStatus("'0 <= sigma_*' is necessary.");
           return false;
       }

       if(ksize_x == 0 && sigma_x <= 0) {
           IJ.showStatus("When ksize_x is 0, sigma_x must be > 0.");
           return false;
       }

       if(ksize_y == 0 && sigma_y <= 0 && sigma_x <= 0) {
           IJ.showStatus("When ksize_y is 0, sigma_x or sigma_y must be > 0.");
           return false;
       }
       ```
   - In `manual_jp/OCV_GaussianBlur.md`:
     - Update line 14 (`- **ksize** を指定する場合は、必ず **正の奇数**（3, 5, 7...）である必要があります。`) to clarify that `0` is allowed only when `sigma > 0` (e.g. `- **ksize** は **正の奇数**（3, 5, 7...）を指定する必要があります（`0` を指定して自動計算させる場合は、`sigma_x` に `0` より大きい値を指定してください）。`).

3. `src/OCV_BilateralFilter.java`:
   - Remove instance fields `src_mat`, `dst_mat`, `cachedWidth`, `cachedHeight`, `cachedBitDepth` and remove methods `allocateMatIfNeeded` and `releaseResources` (and remove `releaseResources()` calls in `showDialog` and `run`).
   - In `runForGrayscale8`, `runForRGB`, `runForFloat32`, allocate `Mat src_mat = null; Mat dst_mat = null;` locally and release both in a `finally` block.
   - In `dialogItemChanged`:
     Change `if(diameter >= 0 && diameter < 1)` to `if(diameter == 0)`.

4. `src/OCV_EqualizeHist.java` & `manual_jp/OCV_EqualizeHist.md`:
   - In `src/OCV_EqualizeHist.java`:
     - Change `private static final int FLAGS = DOES_8G | NO_CHANGES;` to `private static final int FLAGS = DOES_8G;`.
     - Remove the unused `ImagePlus impSrc;` field, remove `impSrc = imp;` in `setup()`, and remove `impSrc.draw();` in `run()` so ImageJ's `PlugInFilterRunner` properly saves the Undo snapshot and redraws the image.
   - In `manual_jp/OCV_EqualizeHist.md`:
     - Update line 11 (`- 元の画像の輝度値が書き換えられるため、必要に応じて事前に複製（Duplicate）しておいてください。`) to `- 元の画像の輝度値が直接書き換えられます（実行直後であれば ImageJ の `Edit > Undo` で元に戻すことができます）。`.

5. `dual-model-operation-kit/CODER_STATUS.md`:
   - Update with your execution report for `I-0004`.

## Scope And Boundaries

Allowed changes:

- `src/OCV_Blur.java`
- `src/OCV_GaussianBlur.java`
- `src/OCV_BilateralFilter.java`
- `src/OCV_EqualizeHist.java`
- `manual_jp/OCV_GaussianBlur.md`
- `manual_jp/OCV_EqualizeHist.md`
- `dual-model-operation-kit/CODER_STATUS.md`

Must not change:

- `dual-model-operation-kit/DUAL_MODEL_OPERATION.md`
- `dual-model-operation-kit/MANAGER_INSTRUCTIONS.md`
- `dual-model-operation-kit/STEPS.md`
- `dual-model-operation-kit/README.md`
- Any other files in `src/` or `manual_jp/`

## Acceptance Criteria

1. `OCV_Blur`, `OCV_GaussianBlur`, and `OCV_BilateralFilter` allocate `Mat` objects locally and release them in `finally` blocks, with all instance-cached `Mat` fields and `allocateMatIfNeeded` / `releaseResources` methods removed.
2. `OCV_Blur` uses `int` for `ksize_x` and `ksize_y` with `0` decimal places in `GenericDialog`, and `OCV_BilateralFilter` checks `if(diameter == 0)`.
3. `OCV_GaussianBlur` validates `sigma_x >= 0 && sigma_y >= 0`, `ksize_x == 0 -> sigma_x > 0`, and `ksize_y == 0 -> (sigma_y > 0 || sigma_x > 0)`, and `manual_jp/OCV_GaussianBlur.md` is updated accordingly.
4. `OCV_EqualizeHist` uses `FLAGS = DOES_8G` without `NO_CHANGES` or `impSrc.draw()`, and `manual_jp/OCV_EqualizeHist.md` is updated accordingly.
5. `dual-model-operation-kit/CODER_STATUS.md` is updated for `I-0004`, and no files outside the allowed scope are modified.

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
