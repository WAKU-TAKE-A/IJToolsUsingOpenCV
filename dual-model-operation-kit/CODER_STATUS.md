# Coder Status

Last updated: 2026-11-03

## Based On Instruction ID

`I-0007`

## Coder Result

Status: `Ready for Review`

- Acceptance Criteria:
  - [x] 1. `OCV_FitEllipse.showData` swaps `w`/`h` and adds 90 degrees to the angle for ROI computation when `w > h`, keeps ResultsTable values unchanged, and `ratio <= 1`.
  - [x] 2. `OCV_ConvexHull` and `OCV_ConnectedComponentsWithStats` `showDialog` return `IJ.setupDialog(...)` results.
  - [x] 3. All 7 G5 plugins include `NO_CHANGES` in their flags (including the `setup()` return values of the four ROI-producing plugins).
  - [x] 4. `WK_HuMoments` clamps `rect` to the image, returns early on empty ROI and `M00 == 0`, and keeps `RES_INI`/formulas unchanged.
  - [x] 5. `CODER_STATUS.md` is updated for `I-0007`, CRLF is preserved (`git diff --stat` shows only a few changed lines per file), and no files outside the allowed scope are modified.

- Notes: Successfully completed the requested Group G5 fixes. All scope restrictions were honored, CRLF endings preserved, and `ant` was not used. No manuals needed correction.
