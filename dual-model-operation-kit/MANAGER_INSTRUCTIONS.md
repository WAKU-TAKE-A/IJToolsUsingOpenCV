# Manager Instructions

Last updated: 2026-11-03

## Instruction ID

`I-0008`

## Current Phase

`Group G6 Bug Fixes`: `Step 8 - Fix MyFeatureDetector row Mat leaks, WK_HoughCircles ROI offset bug, OCV_FeatDet_2nd_Match empty QueryDescriptors check, and add NO_CHANGES flags across G6`

## Current Status

Coder report for `I-0007` was accepted and released in `master` (`v0.9.62.8`).
Now starting `I-0008` based on findings from Group G6 code inspection (Jules inspection + Manager local verification).

- Completed: `I-0001` .. `I-0007`
- Remaining: Execute `I-0008` across the 7 G6 Java classes, then report in `dual-model-operation-kit/CODER_STATUS.md`.
- Verification: Use `git diff` and `git status` (do NOT run `ant` in the Linux container because `ij.jar` and `opencv-500.jar` are git-ignored).
- Notes:
  - Do not leave any temporary helper scripts in the working tree.
  - **Line endings**: all files under `src/` use CRLF (`\r\n`). Preserve CRLF on every line you touch and do NOT rewrite whole files; the `git diff --stat` must show only the lines you actually changed.

## Objective Alignment

Reference: `DUAL_MODEL_OPERATION.md` / `Project Objective`

- Eliminates native `Mat` memory leaks in `MyFeatureDetector`, fixes detected circle coordinates when running with an ROI in `WK_HoughCircles`, adds safe guard for empty `QueryDescriptors` in `OCV_FeatDet_2nd_Match`, and adds `NO_CHANGES` to all read-only G6 plugins.

## Principle Application

Reference: `DUAL_MODEL_OPERATION.md` / `Project Principle`

- Modifies only the 7 G6 Java files, preserving existing macro parameter names, ResultsTable column names, and method signatures.

## Persistent Decisions

- Preserve OpenCV imports and calls.
- Do not run `ant` or native OpenCV tests inside the Linux container.
- Do not modify files outside the allowed scope.

## Manager Decision

Status: `Not Yet Reviewed`

- Next phase: `Step 8`
- Non-goals: Modifying `OCV_FeatDet_1st_SetQuery.java` (already clean) or any files outside Group G6.

## Instruction For Coder

Implement the following changes:

1. `src/MyFeatureDetector.java`:
   - In `writeDescriptors(Mat descriptors, String filePath)`:
     For all 3 loops (`CV_32F`, `CV_64F`, `CV_8U`), release the temporary `Mat` created by `descriptors.row(r)`:
     ```java
     Mat row = descriptors.row(r);
     try {
         row.get(0, 0, buffer);
     } finally {
         row.release();
     }
     ```
   - In `readDescriptors(String filePath)`:
     For all 3 loops (`CV_32F`, `CV_64F`, `CV_8U`), release the temporary `Mat` created by `descriptors.row(r)`:
     ```java
     Mat row = descriptors.row(r);
     try {
         row.put(0, 0, buffer);
     } finally {
         row.release();
     }
     ```
     Also, ensure `descriptors.release()` is called if an exception occurs after `Mat descriptors = new Mat(rows, cols, type);` is created before returning.

2. `src/WK_HoughCircles.java`:
   - In `showData(short[] arr_hough_img)`:
     The plugin computes Hough space using coordinates local to the ROI (`0` to `w-1`, `0` to `h-1`), but forgets to add the ROI offset `rect.x` and `rect.y` when recording results.
     When calculating average center coordinates `xave` and `yave` or adding to ResultsTable / ROI Manager:
     Add `rect.x` and `rect.y` to the recorded center coordinates:
     ```java
     double xave = res_ar[IXSUM] / res_ar[IN] + rect.x;
     double yave = res_ar[IYSUM] / res_ar[IN] + rect.y;
     ```
   - In `FLAGS` and `setup`:
     Change `FLAGS` to `DOES_8G | NO_CHANGES`.
     In `setup()`, return `DOES_8G | NO_CHANGES;` (or `FLAGS`).

3. `src/OCV_FeatDet_2nd_Match.java`:
   - In `FLAGS`:
     Change `FLAGS` to `DOES_RGB | NO_CHANGES`.
   - In `run(ImageProcessor ip)`:
     After calculating `train_key` and `train_desc`, check both `myQuery.QueryDescriptors` and `train_desc`:
     ```java
     if (myQuery.QueryDescriptors == null || myQuery.QueryDescriptors.rows() == 0 || train_key.rows() == 0 || train_desc.rows() == 0) {
         OCV__LoadLibrary.logError(className, "Result or query descriptor is empty.");
         return;
     }
     ```

4. `src/OCV_MatchTemplate.java`:
   - Change `FLAGS` to `DOES_8G | NO_CHANGES`.

5. `src/OCV_HoughLines.java`:
   - Change `FLAGS` to `DOES_8G | NO_CHANGES`.
   - In `showDialog()`, return `IJ.setupDialog(imp, DOES_8G | NO_CHANGES);`.
   - In `setup()`, return `DOES_8G | NO_CHANGES;`.

6. `src/OCV_HoughLinesP.java`:
   - Change `FLAGS` to `DOES_8G | NO_CHANGES`.
   - In `showDialog()`, return `IJ.setupDialog(imp, DOES_8G | NO_CHANGES);`.
   - In `setup()`, return `DOES_8G | NO_CHANGES;`.

7. `src/OCV_HoughCircles.java`:
   - Change `FLAGS` to `DOES_8G | NO_CHANGES`.
   - In `showDialog()`, return `IJ.setupDialog(imp, DOES_8G | NO_CHANGES);`.
   - In `setup()`, return `DOES_8G | NO_CHANGES;`.

8. `dual-model-operation-kit/CODER_STATUS.md`:
   - Update with your execution report for `I-0008`.

## Scope And Boundaries

Allowed changes:

- `src/MyFeatureDetector.java`
- `src/WK_HoughCircles.java`
- `src/OCV_FeatDet_2nd_Match.java`
- `src/OCV_MatchTemplate.java`
- `src/OCV_HoughLines.java`
- `src/OCV_HoughLinesP.java`
- `src/OCV_HoughCircles.java`
- `dual-model-operation-kit/CODER_STATUS.md`

Must not change:

- `dual-model-operation-kit/DUAL_MODEL_OPERATION.md`
- `dual-model-operation-kit/MANAGER_INSTRUCTIONS.md`
- `dual-model-operation-kit/STEPS.md`
- `dual-model-operation-kit/README.md`
- Version strings, `ReleaseNotes.md`, `README*.md`
- Any other files in `src/` or `manual_jp/`

## Acceptance Criteria

1. `MyFeatureDetector` releases row `Mat` objects in all 6 loops of `writeDescriptors` and `readDescriptors`, and cleans up on exception.
2. `WK_HoughCircles.showData` includes `rect.x` and `rect.y` in detected circle center positions, and uses `NO_CHANGES`.
3. `OCV_FeatDet_2nd_Match` guards against `null` or empty `myQuery.QueryDescriptors` and has `NO_CHANGES`.
4. `OCV_MatchTemplate`, `OCV_HoughLines`, `OCV_HoughLinesP`, and `OCV_HoughCircles` include `NO_CHANGES` in `FLAGS`, `showDialog`, and `setup`.
5. `CODER_STATUS.md` is updated for `I-0008`, CRLF line endings are preserved, and no files outside the allowed scope are modified.

## Verification

Minimum verification in container:

```bash
git status --short
git diff --stat
```

Expected repository changes:

- Only the 7 source files and `CODER_STATUS.md` are modified.

## Stop Conditions

Stop immediately and report if:

- Work requires touching files outside scope.
- Any acceptance criterion cannot be met.
