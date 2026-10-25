# Manager Instructions

Last updated: 2026-10-25

## Instruction ID

`I-0002`

## Current Phase

`Group G2 Bug Fixes`: `Step 2 - Fix CornerHarris unsupported border exception, Canny memory leak, and Laplacian status typo`

## Current Status

Coder report for `I-0001` was accepted and merged.
Now starting `I-0002` based on findings from G2 code inspection.

- Completed: `I-0001` (Feature Matching AKAZE/BRISK cleanup)
- Remaining: Fix 3 identified defects in `OCV_CornerHarris`, `OCV_Canny`, and `OCV_Laplacian`, then report in `dual-model-operation-kit/CODER_STATUS.md`.
- Verification: Not yet run
- Notes: Do not attempt to run `ant test` or native OpenCV execution in the container. Use static analysis and targeted git checks.

## Objective Alignment

Reference: `DUAL_MODEL_OPERATION.md` / `Project Objective`

- Eliminates runtime crash risks, native memory leaks, and UI text errors in the G2 edge detection and corner plugins.

## Principle Application

Reference: `DUAL_MODEL_OPERATION.md` / `Project Principle`

- Fixes only the 3 concrete defects identified in inspection without changing mathematical gradient algorithms or modifying files outside scope.

## Persistent Decisions

- Preserve existing behavior and signatures of all working plugins.
- Do not run `ant test` or native OpenCV compilation inside the Linux container.
- General refactoring of gradient calculation precision across Sobel/Scharr/Laplacian is deferred as a backlog item (Non-goal for this instruction).

## Manager Decision

Status: `Not Yet Reviewed`

- Next phase: `Step 2 - Fix CornerHarris unsupported border exception, Canny memory leak, and Laplacian status typo`
- Why now: Inspection completed with 3 clear, low-risk, high-value fixes ready for implementation.
- Non-goals: Changing internal calculation types (e.g. CV_32F refactoring) for Sobel/Scharr/Laplacian, modifying any manuals, or modifying files outside the 3 target Java classes.

## Instruction For Coder

Implement the following 3 targeted fixes in `src/`:

1. `src/OCV_CornerHarris.java`:
   - Remove or comment out `Core.BORDER_WRAP` and `Core.BORDER_TRANSPARENT` from `INT_BORDERTYPE` and `STR_BORDERTYPE` (similar to how `OCV_Sobel.java` comments them out), because OpenCV's CornerHarris does not support these border types and throws runtime exceptions when selected.
   - Ensure the comment block above `INT_BORDERTYPE` reflects that these types are excluded/unsupported.

2. `src/OCV_Canny.java`:
   - Refactor `src_mat` and `dst_mat` from instance fields to local variables inside `run(ImageProcessor ip)`.
   - In `run()`, allocate `src_mat` and `dst_mat` locally with `CvType.CV_8UC1`, and use a `try ... finally` block to guarantee `src_mat.release()` and `dst_mat.release()` are always executed, preventing native memory leaks after processing finishes.
   - Remove the unused instance fields `src_mat`, `dst_mat`, `cachedWidth`, `cachedHeight`, and the unused helper methods `allocateMatIfNeeded` and `releaseResources` (and simplify `showDialog` where `releaseResources()` was called on cancel).

3. `src/OCV_Laplacian.java`:
   - In `dialogItemChanged()`, at line ~102 where `ksize % 2 == 0` is checked, change `IJ.showStatus("'ksize is odd.");` to `IJ.showStatus("'ksize must be odd.'");` to correct the grammatical typo and close the quote properly.

4. `dual-model-operation-kit/CODER_STATUS.md`:
   - Update with your report for `I-0002`. Note: If staging manually, use `git add -f dual-model-operation-kit/CODER_STATUS.md`.

## Scope And Boundaries

Allowed changes:

- `src/OCV_CornerHarris.java`
- `src/OCV_Canny.java`
- `src/OCV_Laplacian.java`
- `dual-model-operation-kit/CODER_STATUS.md`

Must not change:

- `dual-model-operation-kit/DUAL_MODEL_OPERATION.md`
- `dual-model-operation-kit/MANAGER_INSTRUCTIONS.md`
- `dual-model-operation-kit/STEPS.md`
- `dual-model-operation-kit/README.md`
- `src/OCV_Sobel.java`, `src/OCV_Scharr.java`, or any other Java files
- Any markdown files under `manual_jp/`

New files allowed:

- `None`

## Required Shape

```text
Not specified
```

## Acceptance Criteria

1. `src/OCV_CornerHarris.java`: `BORDER_WRAP` and `BORDER_TRANSPARENT` are excluded from `INT_BORDERTYPE` and `STR_BORDERTYPE`.
2. `src/OCV_Canny.java`: `src_mat` and `dst_mat` are local variables released in a `finally` block in `run()`, with no lingering instance Mat references.
3. `src/OCV_Laplacian.java`: The status message for even kernel size is corrected to `'ksize must be odd.'`.
4. `dual-model-operation-kit/CODER_STATUS.md` is updated for `I-0002`, and no files outside the 3 target Java classes are modified.

## Verification

Minimum verification:

```bash
git diff --stat
git status --short
```

Expected repository changes:

- Only `src/OCV_CornerHarris.java`, `src/OCV_Canny.java`, `src/OCV_Laplacian.java`, and `dual-model-operation-kit/CODER_STATUS.md` modified.

## Stop Conditions

Stop immediately and report if:

- Work requires touching files outside scope.
- A protected behavior must change.
- The current work causes a new build or test failure.

Wait for Manager approval before continuing if:

- The goal, scope, or acceptance criteria are ambiguous.
- Judgment logic must change without explicit approval.
- The work grows beyond the current phase.

Continue within scope and report afterward if:

- Optional verification cannot be run.

## Coder Status Update Required

After work, update `dual-model-operation-kit/CODER_STATUS.md`.

Include:

- Based On Instruction ID (`I-0002`)
- Current Phase
- Files changed
- Acceptance criteria results (`Pass`, `Fail`, or `Not Verified` for each of the 4 criteria)
- Verification results
- Coder Result (`Ready for Manager Review`)
- Recommended next step
- Questions for Manager
