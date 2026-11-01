# Manager Instructions

Last updated: 2026-11-01

## Instruction ID

`I-0003`

## Current Phase

`PeriodDetector Bug Fixes & Subpixel Unification`: `Step 3 - Fix PeriodRefiner/Detrend bugs and unify PeriodDetector output to subpixel precision`

## Current Status

Coder report for `I-0002` was accepted and merged into `master`.
Now starting `I-0003` to fix algorithmic bugs in the `PeriodDetector` classes and unify the output to subpixel precision (Option A).

- Completed: `I-0001` (Feature Matching cleanup), `I-0002` (G2 Edge/Corner fixes)
- Remaining: Execute `I-0003` across `src/` and `sample/PeriodDetector/` and `manual_jp/WK_RoiMan_PeriodDetector.md`, then report in `dual-model-operation-kit/CODER_STATUS.md`.
- Verification: Run `ant jar` (pure Java compilation works in Linux container; do NOT run `ant test`).
- Notes: Do not leave any temporary helper scripts (`.sh`, `.py`, `.diff`) in the working tree.

## Objective Alignment

Reference: `DUAL_MODEL_OPERATION.md` / `Project Objective`

- Fixes harmonic refinement inaccuracies, non-peak false positives, half-pixel sampling attenuation, and numerical precision issues in `PeriodDetector`, and unifies the output to subpixel accuracy.

## Principle Application

Reference: `DUAL_MODEL_OPERATION.md` / `Project Principle`

- Modifies only the PeriodDetector-related files in `src/`, `sample/PeriodDetector/`, and `manual_jp/WK_RoiMan_PeriodDetector.md`.

## Persistent Decisions

- Preserve existing GUI parameters and workflow of `WK_RoiMan_PeriodDetector`.
- Unify period output to subpixel precision (`double period` in `PeriodResult`, and `"Period"` column in `ResultsTable`).
- Synchronize `sample/PeriodDetector/` with `src/` so sample code does not diverge.
- Do not run `ant test` (native OpenCV DLLs are Windows-only), but DO run `ant jar` to verify Java compilation.

## Manager Decision

Status: `Not Yet Reviewed`

- Next phase: `Step 3 - Fix PeriodRefiner/Detrend bugs and unify PeriodDetector output to subpixel precision`
- Why now: User requested fixing the 5 PeriodDetector files and unifying the output to subpixel precision (Option A) via Jules.
- Non-goals: Modifying any `OCV_*` or `OCUtil_*` plugins, or adding OpenCV dependencies to `PeriodDetector`.

## Instruction For Coder

Implement the following changes:

1. `src/PeakDetector.java`:
   - Change `isPeak(double[] acf, int index)` to `public static boolean isPeak(double[] acf, int index)` with bounds safety:
     ```java
     public static boolean isPeak(double[] acf, int index) {
         if (acf == null || index < 1 || index >= acf.length - 1) {
             return false;
         }
         return acf[index] > acf[index - 1]
                 && acf[index] >= acf[index + 1];
     }
     ```
   - Add 3-point parabolic interpolation helper methods to `PeakDetector`:
     ```java
     /**
      * 3点放物線近似によるサブピクセルピーク位置を算出する。
      */
     public static double interpolatePeakPosition(double[] acf, int peakIndex) {
         if (acf == null || peakIndex <= 0 || peakIndex >= acf.length - 1) {
             return peakIndex;
         }
         double y1 = acf[peakIndex - 1];
         double y2 = acf[peakIndex];
         double y3 = acf[peakIndex + 1];
         double denom = 2.0 * (y1 - 2.0 * y2 + y3);
         if (denom < 0) {
             double delta = (y1 - y3) / denom;
             if (Double.isFinite(delta) && Math.abs(delta) <= 0.5) {
                 return peakIndex + delta;
             }
         }
         return peakIndex;
     }

     /**
      * 3点放物線近似による補間後のピーク頂点値を算出する。
      */
     public static double interpolatePeakValue(double[] acf, int peakIndex) {
         if (acf == null || peakIndex < 0 || peakIndex >= acf.length) {
             return 0.0;
         }
         if (peakIndex == 0 || peakIndex == acf.length - 1) {
             return acf[peakIndex];
         }
         double y1 = acf[peakIndex - 1];
         double y2 = acf[peakIndex];
         double y3 = acf[peakIndex + 1];
         double denom = 2.0 * (y1 - 2.0 * y2 + y3);
         if (denom < 0) {
             double delta = (y1 - y3) / denom;
             if (Double.isFinite(delta) && Math.abs(delta) <= 0.5) {
                 return y2 - 0.25 * (y1 - y3) * delta;
             }
         }
         return y2;
     }
     ```

2. `src/PeriodRefiner.java`:
   - In `refine(double[] acf, int period, double halfRatio, int maxDivisor)`:
     - Check `if (acf == null || period <= 1 || period >= acf.length)` -> `return period;`.
     - Compute the interpolated peak value and subpixel position of `period`:
       ```java
       double peakValue = PeakDetector.interpolatePeakValue(acf, period);
       if (peakValue <= 0) {
           return period;
       }
       double subpxPeriod = PeakDetector.interpolatePeakPosition(acf, period);
       ```
     - For `int divisor = 2; divisor <= maxDivisor; divisor++`:
       - Compute `int center = (int) Math.round(subpxPeriod / divisor);`
       - Search in `int start = Math.max(1, center - 1);` to `int end = Math.min(acf.length - 2, center + 1);` for a candidate index `cand` where `PeakDetector.isPeak(acf, cand)` is true and `PeakDetector.interpolatePeakValue(acf, cand)` is maximized.
       - If such a local peak `bestCand` is found and `bestCandValue >= peakValue * halfRatio`:
         `best = bestCand; break;`
     - Return `best;`.

3. `src/Detrend.java`:
   - In `removeLinearTrend(double[] input)`:
     - Center the x-coordinate around `meanX = (n - 1) * 0.5` to prevent floating-point cancellation on long profiles:
       ```java
       double meanX = (n - 1) * 0.5;
       double meanY = 0.0;
       for (int i = 0; i < n; i++) {
           meanY += input[i];
       }
       meanY /= n;

       double sumXY = 0.0;
       double sumX2 = 0.0;
       for (int i = 0; i < n; i++) {
           double dx = i - meanX;
           double dy = input[i] - meanY;
           sumXY += dx * dy;
           sumX2 += dx * dx;
       }

       if (sumX2 < EPSILON) {
           return input.clone();
       }

       double a = sumXY / sumX2;
       double[] output = new double[n];
       for (int i = 0; i < n; i++) {
           double trend = a * (i - meanX) + meanY;
           output[i] = input[i] - trend;
       }
       return output;
       ```

4. `src/PeriodResult.java`:
   - Unify to subpixel period output (Option A):
     ```java
     /**
      * PeriodResult.java
      *
      * 周期検出の戻り値を格納するクラス。
      */
     public class PeriodResult {

         /** サブピクセル精度の検出周期（ピクセル単位） */
         public final double period;

         /** 確信度（正規化自己相関値） */
         public final double confidence;

         public PeriodResult(double period, double confidence) {
             this.period = period;
             this.confidence = confidence;
         }
     }
     ```

5. `src/PeriodDetector.java`:
   - After `PeakDetector.findPeak` and `PeriodRefiner.refine`:
     - Compute `double subpixelPeriod = PeakDetector.interpolatePeakPosition(acf, period);`
     - Clamp: `subpixelPeriod = Math.max(minPeriod, Math.min(maxPeriod, subpixelPeriod));`
     - Compute `double confidence = 0.0;`:
       ```java
       if (acf.length > 0 && acf[0] != 0.0) {
           confidence = PeakDetector.interpolatePeakValue(acf, period) / acf[0];
       }
       return new PeriodResult(subpixelPeriod, confidence);
       ```

6. `src/WK_RoiMan_PeriodDetector.java` & `manual_jp/WK_RoiMan_PeriodDetector.md`:
   - In `src/WK_RoiMan_PeriodDetector.java`:
     Update ResultsTable output in `run()` so `"Period"` outputs `result.period` directly (removing the separate `"Period_Subpx"` column):
     ```java
     if (result != null) {
         rsTbl.incrementCounter();
         rsTbl.addValue("ROI_Name", roiName);
         rsTbl.addValue("Period", result.period);
         rsTbl.addValue("Confidence", result.confidence);
         rsTbl.addValue("Profile_Len", profile.length);
     }
     ```
   - In `manual_jp/WK_RoiMan_PeriodDetector.md`:
     Update the description in section 1 and section 3 to reflect that `Period` outputs the subpixel-accurate period directly (and remove the separate `Period_Subpx` bullet).

7. `sample/PeriodDetector/`:
   - Copy the updated `src/Autocorrelation.java`, `src/Detrend.java`, `src/PeakDetector.java`, `src/PeriodDetector.java`, `src/PeriodRefiner.java`, and `src/PeriodResult.java` into `sample/PeriodDetector/` so `sample/PeriodDetector/` stays completely synchronized with `src/`.

8. `dual-model-operation-kit/CODER_STATUS.md`:
   - Update with your execution report for `I-0003`.

## Scope And Boundaries

Allowed changes:

- `src/Autocorrelation.java`
- `src/Detrend.java`
- `src/PeakDetector.java`
- `src/PeriodDetector.java`
- `src/PeriodRefiner.java`
- `src/PeriodResult.java`
- `src/WK_RoiMan_PeriodDetector.java`
- `manual_jp/WK_RoiMan_PeriodDetector.md`
- `sample/PeriodDetector/Autocorrelation.java`
- `sample/PeriodDetector/Detrend.java`
- `sample/PeriodDetector/PeakDetector.java`
- `sample/PeriodDetector/PeriodDetector.java`
- `sample/PeriodDetector/PeriodRefiner.java`
- `sample/PeriodDetector/PeriodResult.java`
- `dual-model-operation-kit/CODER_STATUS.md`

Must not change:

- `dual-model-operation-kit/DUAL_MODEL_OPERATION.md`
- `dual-model-operation-kit/MANAGER_INSTRUCTIONS.md`
- `dual-model-operation-kit/STEPS.md`
- `dual-model-operation-kit/README.md`
- Any other files in `src/` or `manual_jp/`

New files allowed:

- `sample/PeriodDetector/PeriodResult.java`

## Required Shape

```text
PeriodResult(double period, double confidence)
ResultsTable columns: ROI_Name, Period, Confidence, Profile_Len
```

## Acceptance Criteria

1. `PeakDetector` exposes `isPeak`, `interpolatePeakPosition`, and `interpolatePeakValue` using 3-point parabolic interpolation.
2. `PeriodRefiner.refine` searches `[center - 1, center + 1]` around `Math.round(subpxPeriod / divisor)`, requires `PeakDetector.isPeak(acf, cand)`, and compares `PeakDetector.interpolatePeakValue`.
3. `Detrend.removeLinearTrend` centers the x-coordinates around `(n - 1) * 0.5`.
4. `PeriodResult` contains `public final double period` and `public final double confidence`; `WK_RoiMan_PeriodDetector` writes `result.period` to the `"Period"` column in `ResultsTable`, and `manual_jp/WK_RoiMan_PeriodDetector.md` is updated accordingly.
5. `sample/PeriodDetector/` contains the synchronized Java files (`Autocorrelation.java`, `Detrend.java`, `PeakDetector.java`, `PeriodDetector.java`, `PeriodRefiner.java`, `PeriodResult.java`).
6. `ant jar` compiles cleanly without errors, `dual-model-operation-kit/CODER_STATUS.md` is updated for `I-0003`, and no temporary files are left behind.

## Verification

Minimum verification:

```bash
ant jar
git status --short
git diff --stat
```

Expected repository changes:

- Only the allowed files listed in Scope And Boundaries are modified/added.
- `ant jar` succeeds with `BUILD SUCCESSFUL`.

## Stop Conditions

Stop immediately and report if:

- Work requires touching files outside scope.
- Any acceptance criterion cannot be verified.
