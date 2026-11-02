# Manager Instructions

Last updated: 2026-11-03

## Instruction ID

`I-0007`

## Current Phase

`Group G5 Bug Fixes`: `Step 7 - Fix FitEllipse ROI axis ordering, ConvexHull/ConnectedComponentsWithStats setupDialog, NO_CHANGES flags, and WK_HuMoments ROI validation`

## Current Status

Coder report for `I-0006` was accepted and merged into `master` (`v0.9.62.7`).
Now starting `I-0007` based on findings from Group G5 code inspection (Jules inspection + Manager local verification).

- Completed: `I-0001` .. `I-0006`
- Remaining: Execute `I-0007` across the 7 G5 Java classes, then report in `dual-model-operation-kit/CODER_STATUS.md`.
- Verification: Use `git diff` and `git status` (do NOT run `ant` in the Linux container because `ij.jar` and `opencv-500.jar` are git-ignored).
- Notes:
  - Do not leave any temporary helper scripts in the working tree.
  - Do NOT change `org.opencv.geometry.Geometry` imports/calls (OpenCV 5.0.0 in this repository places `minAreaRect`, `fitEllipse`, `convexHull`, etc. there).
  - **Line endings**: all files under `src/` use CRLF (`\r\n`). Preserve CRLF on every line you touch and do NOT rewrite whole files; the `git diff --stat` must show only the lines you actually changed (a few lines per file, not hundreds).
  - Do NOT apply the following rejected inspection findings: `className` NPE (showDialog is always called), `< 3 points` checks in `OCV_MinAreaRect`/`OCV_ConvexHull` (OpenCV handles them), replacing the corner computation in `OCV_MinAreaRect` with `rect.points()`, and de-`static`-ing `WK_HuMoments.RES_INI` (first result as reference is intended).

## Objective Alignment

Reference: `DUAL_MODEL_OPERATION.md` / `Project Objective`

- Fixes the distorted ellipse ROI in `OCV_FitEllipse`, aligns dialog/stack handling in `OCV_ConvexHull` and `OCV_ConnectedComponentsWithStats`, adds `NO_CHANGES` to read-only plugins, and prevents `NaN`/exceptions in `WK_HuMoments` on degenerate ROIs.

## Principle Application

Reference: `DUAL_MODEL_OPERATION.md` / `Project Principle`

- Modifies only the 7 G5 Java files (and manuals only if a description mismatches), preserving existing macro parameter names and ResultsTable columns/values.

## Persistent Decisions

- Preserve `org.opencv.geometry.Geometry` imports and calls.
- Do not run `ant` or native OpenCV tests inside the Linux container.
- Do not change ResultsTable output values (`CenterX`, `CenterY`, `Width`, `Height`, `Angle`, etc.).

## Manager Decision

Status: `Not Yet Reviewed`

- Next phase: `Step 7`
- Non-goals: Modifying `OCV_DistanceTransform.java` (done in G3) or any files outside Group G5.

## Instruction For Coder

Implement the following changes:

1. `src/OCV_FitEllipse.java` (`showData`):
   - `fitEllipse` may return a `RotatedRect` with `size.width > size.height`. The ROI code assumes `h` is the major axis and `ratio = w / h <= 1`.
   - Keep the ResultsTable values (`rect.size.width`, `rect.size.height`, `rect.angle`) exactly as is. For the `EllipseRoi` computation only, use local variables:
     ```java
     double w = rect.size.width;
     double h = rect.size.height;
     double angleDeg = rect.angle;
     if (w > h) {
         double tmp = w;
         w = h;
         h = tmp;
         angleDeg += 90.0;
     }
     double rad = angleDeg * Math.PI / 180;
     ```
     and keep the existing endpoint formulas and `double ratio = w / h;`. Guard `h <= 0` (skip ROI creation, no exception).

2. `src/OCV_ConvexHull.java` (`showDialog`): replace `return DOES_8G;` after reading dialog values with `return IJ.setupDialog(imp, DOES_8G | NO_CHANGES);`.

3. `src/OCV_ConnectedComponentsWithStats.java`:
   - Change `FLAGS` to `DOES_8G | NO_CHANGES`.
   - In `showDialog`, replace `return FLAGS;` (after dialog values are read) with `return IJ.setupDialog(imp, FLAGS);`.

4. `src/OCV_BoundingRect.java`, `src/OCV_MinAreaRect.java`, `src/OCV_MinEnclosingCircle.java`, `src/OCV_FitEllipse.java`:
   - Add `| NO_CHANGES` to the flags returned from `showDialog` (`IJ.setupDialog(imp, DOES_8G | NO_CHANGES)`) and from `setup()` (`return DOES_8G | NO_CHANGES;`). These plugins only read pixels.

5. `src/WK_HuMoments.java`:
   - Add `NO_CHANGES` to `FLAGS`: `DOES_8G | CONVERT_TO_FLOAT | NO_CHANGES`.
   - In `setup()`, clamp `rect` to the image bounds: `rect = rect.intersection(new Rectangle(0, 0, imp.getWidth(), imp.getHeight()));`.
   - At the start of `run()`: if `rect.width <= 0 || rect.height <= 0`, call `IJ.showStatus("ROI width and height must be > 0")` / `IJ.error` is NOT needed; just `return;`.
   - After `calc_moments(...)`: if `mom.M00 == 0`, `IJ.showStatus("Sum of pixel values is 0")` and `return;` before computing Hu moments / `showData`.
   - Do NOT change `RES_INI` or the calculation formulas.

6. Manuals: only if the behavior changes make a statement in `manual_jp/OCV_FitEllipse.md`, `manual_jp/OCV_ConvexHull.md`, `manual_jp/OCV_ConnectedComponentsWithStats.md`, or `manual_jp/WK_HuMoments.md` incorrect, minimally correct it. Otherwise leave manuals untouched. (Manual files are UTF-8 with CRLF; preserve.)

7. `dual-model-operation-kit/CODER_STATUS.md`: update with your execution report for `I-0007`.

## Scope And Boundaries

Allowed changes:

- `src/OCV_FitEllipse.java`
- `src/OCV_ConvexHull.java`
- `src/OCV_ConnectedComponentsWithStats.java`
- `src/OCV_BoundingRect.java`
- `src/OCV_MinAreaRect.java`
- `src/OCV_MinEnclosingCircle.java`
- `src/WK_HuMoments.java`
- `manual_jp/OCV_FitEllipse.md`, `manual_jp/OCV_ConvexHull.md`, `manual_jp/OCV_ConnectedComponentsWithStats.md`, `manual_jp/WK_HuMoments.md` (only if needed, see item 6)
- `dual-model-operation-kit/CODER_STATUS.md`

Must not change:

- `dual-model-operation-kit/DUAL_MODEL_OPERATION.md`
- `dual-model-operation-kit/MANAGER_INSTRUCTIONS.md`
- `dual-model-operation-kit/STEPS.md`
- `dual-model-operation-kit/README.md`
- Version strings, `ReleaseNotes.md`, `README*.md` (version bump is done by the Manager afterwards)
- Any other files in `src/` or `manual_jp/`

## Acceptance Criteria

1. `OCV_FitEllipse.showData` swaps `w`/`h` and adds 90° to the angle for ROI computation when `w > h`, keeps ResultsTable values unchanged, and `ratio <= 1`.
2. `OCV_ConvexHull` and `OCV_ConnectedComponentsWithStats` `showDialog` return `IJ.setupDialog(...)` results.
3. All 7 G5 plugins include `NO_CHANGES` in their flags (including the `setup()` return values of the four ROI-producing plugins).
4. `WK_HuMoments` clamps `rect` to the image, returns early on empty ROI and `M00 == 0`, and keeps `RES_INI`/formulas unchanged.
5. `CODER_STATUS.md` is updated for `I-0007`, CRLF is preserved (`git diff --stat` shows only a few changed lines per file), and no files outside the allowed scope are modified.

## Verification

Minimum verification in container:

```bash
git status --short
git diff --stat
```

Expected repository changes:

- Only the 7 source files, `CODER_STATUS.md`, and (optionally) manuals listed in Scope And Boundaries are modified.

## Stop Conditions

Stop immediately and report if:

- Work requires touching files outside scope.
- Any acceptance criterion cannot be met.
