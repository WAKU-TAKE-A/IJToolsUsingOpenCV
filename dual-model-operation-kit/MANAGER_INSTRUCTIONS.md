# Manager Instructions

Last updated: 2026-10-25

## Instruction ID

`I-0001`

## Current Phase

`Feature Matching Cleanup`: `Step 1 - Remove deprecated AKAZE and BRISK remnants from Feature Matching code and docs`

## Current Status

Initial instruction (no prior Coder report).

- Completed: None
- Remaining: Clean up unused `STR_AKAZE` / `STR_BRISK` constants, outdated Javadoc comments, and Japanese manual descriptions in the 4 feature matching files, then report in `dual-model-operation-kit/CODER_STATUS.md`.
- Verification: Not yet run
- Notes: `AKAZE` and `BRISK` were removed from the core `features` module in OpenCV 5.0.0. Runtime logic already supports only `ORB` and `SIFT`.

## Objective Alignment

Reference: `DUAL_MODEL_OPERATION.md` / `Project Objective`

- Synchronizes `MyFeatureDetector`, `OCV_FeatDet_1st_SetQuery`, `OCV_FeatDet_2nd_Match`, and `manual_jp/OCV_FeatDet_1st_SetQuery.md` with the actual OpenCV 5.0.0 implementation (`ORB` and `SIFT`).

## Principle Application

Reference: `DUAL_MODEL_OPERATION.md` / `Project Principle`

- Removes only dead constants and outdated documentation/Javadoc references to `AKAZE` and `BRISK` without changing any runtime feature detection or matching behavior.

## Persistent Decisions

- Preserve all runtime behavior in `MyFeatureDetector.java`, `OCV_FeatDet_1st_SetQuery.java`, and `OCV_FeatDet_2nd_Match.java`.
- Keep historical mentions of `AKAZE` / `BRISK` in `ReleaseNotes.md` and `README_AI.md` untouched.

## Manager Decision

Status: `Not Yet Reviewed`

- Next phase: `Step 1 - Remove deprecated AKAZE and BRISK remnants from Feature Matching code and docs`
- Why now: Ensure code and documentation accurately reflect the supported `ORB` and `SIFT` detectors in OpenCV 5.0.0.
- Non-goals: Adding new feature detectors, modifying runtime detection/matching logic, or editing `ReleaseNotes.md` / `README_AI.md`.

## Instruction For Coder

Inspect and update the following 4 files to remove outdated `AKAZE` and `BRISK` references, and record your execution status in `dual-model-operation-kit/CODER_STATUS.md`:

1. `src/MyFeatureDetector.java`:
   - Remove the unused constants `private static final String STR_AKAZE = "AKAZE";` and `private static final String STR_BRISK = "BRISK";`.
   - Do not modify any methods or active fields.
2. `src/OCV_FeatDet_1st_SetQuery.java`:
   - Update the class-level Javadoc comment from `* * AKAZE, BRISK, ORB` to `* * ORB, SIFT`.
3. `src/OCV_FeatDet_2nd_Match.java`:
   - Update the class-level Javadoc comment from `* * AKAZE, BRISK, ORB` to `* * ORB, SIFT`.
4. `manual_jp/OCV_FeatDet_1st_SetQuery.md`:
   - Under `## 1. 概要：画像処理の仕組み`, replace `AKAZEやORBといったアルゴリズムを用い` with `ORBやSIFTといったアルゴリズムを用い`.
   - Under `## 2. GUIの使い方` -> `feature_detector`, replace `- \`AKAZE / BRISK / ORB\`: 高速でライセンスフリーな手法。` with `- \`ORB\`: 高速でライセンスフリーな手法。` (keeping `- \`SIFT\`: 高精度だが計算負荷が高い手法。`).
5. `dual-model-operation-kit/CODER_STATUS.md`:
   - Update with your report for `I-0001`. Note: Because `dual-model-operation-kit` is listed in `.gitignore`, use `git add -f dual-model-operation-kit/CODER_STATUS.md` if staging manually.

## Scope And Boundaries

Allowed changes:

- `src/MyFeatureDetector.java`
- `src/OCV_FeatDet_1st_SetQuery.java`
- `src/OCV_FeatDet_2nd_Match.java`
- `manual_jp/OCV_FeatDet_1st_SetQuery.md`
- `dual-model-operation-kit/CODER_STATUS.md`

Must not change:

- `dual-model-operation-kit/DUAL_MODEL_OPERATION.md`
- `dual-model-operation-kit/MANAGER_INSTRUCTIONS.md`
- `dual-model-operation-kit/STEPS.md`
- `dual-model-operation-kit/README.md`
- Any other files in `src/`, `manual_jp/`, `ReleaseNotes.md`, or `README_AI.md`
- Runtime logic or method signatures in `MyFeatureDetector.java`, `OCV_FeatDet_1st_SetQuery.java`, or `OCV_FeatDet_2nd_Match.java`

New files allowed:

- `None`

## Required Shape

```text
Not specified
```

## Acceptance Criteria

1. `src/MyFeatureDetector.java` no longer defines `STR_AKAZE` or `STR_BRISK`, and its runtime logic remains unchanged.
2. `src/OCV_FeatDet_1st_SetQuery.java` and `src/OCV_FeatDet_2nd_Match.java` list `ORB, SIFT` instead of `AKAZE, BRISK, ORB` in their class Javadoc comments.
3. `manual_jp/OCV_FeatDet_1st_SetQuery.md` describes `ORB` and `SIFT` without referencing `AKAZE` or `BRISK`, and `dual-model-operation-kit/CODER_STATUS.md` is updated for `I-0001` with no out-of-scope file modifications.

## Verification

Minimum verification:

```bash
git diff --stat
git status --short
git grep -n -E "AKAZE|BRISK" src/ manual_jp/
```

Expected repository changes:

- Only `src/MyFeatureDetector.java`, `src/OCV_FeatDet_1st_SetQuery.java`, `src/OCV_FeatDet_2nd_Match.java`, `manual_jp/OCV_FeatDet_1st_SetQuery.md`, and `dual-model-operation-kit/CODER_STATUS.md` modified; `git grep -n -E "AKAZE|BRISK" src/ manual_jp/` returns no matches.

## Stop Conditions

Stop immediately and report if:

- Work requires touching files outside scope.
- A protected behavior must change.
- The current work causes a new build or test failure.

Wait for Manager approval before continuing if:

- The goal, scope, or acceptance criteria are ambiguous.
- Judgment logic must change without explicit approval.
- A temporary workaround or materially different implementation becomes necessary.
- The work grows beyond the current phase.

Continue within scope and report afterward if:

- A pre-existing build or test failure remains unchanged.
- An unrelated issue is discovered.
- Optional verification cannot be run.

## Coder Status Update Required

After work, update `dual-model-operation-kit/CODER_STATUS.md`.

Include:

- Based On Instruction ID (`I-0001`)
- Current Phase
- Files changed
- Acceptance criteria results (`Pass`, `Fail`, or `Not Verified` for each of the 3 criteria)
- Build/test/verification results
- Whether work is in progress, blocked, or ready for Manager review (`Ready for Manager Review`)
- Recommended next step
- Questions for Manager
