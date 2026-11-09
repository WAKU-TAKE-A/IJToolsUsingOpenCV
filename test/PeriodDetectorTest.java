import java.util.Random;

/**
 * 周期検出チェーンの回帰テスト（JUnit不要、mainで実行）。
 *
 * 実行方法:
 *   javac -cp ij.jar -d out src/*.java test/PeriodDetectorTest.java
 *   java  -cp out:ij.jar PeriodDetectorTest
 * 終了コード 0 = 全件パス。
 */
public class PeriodDetectorTest {

    private static int failures = 0;
    private static int checks = 0;

    private static void check(String name, boolean cond) {
        checks++;
        if (cond) {
            System.out.println("PASS: " + name);
        } else {
            failures++;
            System.out.println("FAIL: " + name);
        }
    }

    private static void checkClose(String name, double expected, double actual, double tol) {
        boolean ok = Math.abs(expected - actual) <= tol;
        if (!ok) {
            System.out.println("  expected=" + expected + " actual=" + actual + " tol=" + tol);
        }
        check(name, ok);
    }

    private static double[] sine(int n, double period, double trendSlope, long noiseSeed, double noiseAmp) {
        double[] x = new double[n];
        Random rnd = new Random(noiseSeed);
        for (int i = 0; i < n; i++) {
            x[i] = Math.sin(2.0 * Math.PI * i / period)
                    + trendSlope * i
                    + noiseAmp * rnd.nextGaussian();
        }
        return x;
    }

    public static void main(String[] args) {

        // 1) 既知周期の正弦波（トレンド＋ノイズ付き、線形回帰デトレンド）
        {
            double[] x = sine(400, 20.0, 0.01, 42L, 0.1);
            PeriodResult r = PeriodDetector.detectPeriod(
                    x, PeriodDetector.DetrendMethod.LINEAR_REGRESSION, 0,
                    2, 0, PeakDetector.PeakMethod.PROMINENCE, 0.5);
            check("sine20 detected", r != null);
            if (r != null) {
                checkClose("sine20 period", 20.0, r.period, 0.6);
                check("sine20 confidence high", r.confidence > 0.7);
                check("sine20 confidence <= 1", r.confidence <= 1.0);
            }
        }

        // 2) 移動平均デトレンド
        {
            double[] x = sine(400, 30.0, 0.0, 7L, 0.1);
            PeriodResult r = PeriodDetector.detectPeriod(
                    x, PeriodDetector.DetrendMethod.MOVING_AVERAGE, 51,
                    2, 0, PeakDetector.PeakMethod.PROMINENCE, 0.5);
            check("sine30 (MA) detected", r != null);
            if (r != null) {
                checkClose("sine30 (MA) period", 30.0, r.period, 0.8);
            }
        }

        // 3) 倍周期トラップ: 周期10の繰り返しが支配的 -> 20や30ではなく10を返す
        {
            int n = 300;
            double[] x = new double[n];
            Random rnd = new Random(3L);
            for (int i = 0; i < n; i++) {
                x[i] = Math.sin(2.0 * Math.PI * i / 10.0)
                        + 0.15 * Math.sin(2.0 * Math.PI * i / 30.0)
                        + 0.05 * rnd.nextGaussian();
            }
            PeriodResult r = PeriodDetector.detectPeriod(
                    x, PeriodDetector.DetrendMethod.LINEAR_REGRESSION, 0,
                    2, 0, PeakDetector.PeakMethod.PROMINENCE, 0.5);
            check("subharmonic detected", r != null);
            if (r != null) {
                checkClose("subharmonic period is 10", 10.0, r.period, 0.6);
            }
        }

        // 4) PeriodRefiner: /3 補正が効くこと（4引数版）、従来版では効かないこと
        {
            double[] acf = new double[64];
            acf[29] = 0.90; acf[30] = 1.00; acf[31] = 0.90;
            acf[9] = 0.87;  acf[10] = 0.97; acf[11] = 0.87;
            int withDiv3 = PeriodRefiner.refine(acf, 30, 0.95, 3);
            int withDiv2 = PeriodRefiner.refine(acf, 30, 0.95);
            check("refine /3 adopts 10", withDiv3 == 10);
            check("refine legacy keeps 30", withDiv2 == 30);
        }

        // 5) フラット信号・極短入力は null
        {
            double[] flat = new double[100];
            java.util.Arrays.fill(flat, 5.0);
            check("flat -> null", PeriodDetector.detectPeriod(
                    flat, 2, 0, PeakDetector.PeakMethod.HEIGHT, 0.5) == null);
            check("length1 -> null", PeriodDetector.detectPeriod(
                    new double[] { 1.0 }, 2, 0, PeakDetector.PeakMethod.HEIGHT, 0.5) == null);
            check("null input -> null", PeriodDetector.detectPeriod(
                    null, 2, 0, PeakDetector.PeakMethod.HEIGHT, 0.5) == null);
        }

        // 6) 不正パラメータで例外ではなく null が返ること
        {
            double[] x = sine(100, 10.0, 0.0, 1L, 0.0);
            boolean threw = false;
            PeriodResult r1 = null, r2 = null, r3 = null;
            try {
                r1 = PeriodDetector.detectPeriod(
                        x, PeriodDetector.DetrendMethod.MOVING_AVERAGE, 0,
                        2, 0, PeakDetector.PeakMethod.HEIGHT, 0.5);
                r2 = PeriodDetector.detectPeriod(
                        x, PeriodDetector.DetrendMethod.MOVING_AVERAGE, 1000,
                        2, 0, PeakDetector.PeakMethod.HEIGHT, 0.5);
                r3 = PeriodDetector.detectPeriod(
                        x, PeriodDetector.DetrendMethod.LINEAR_REGRESSION, 0,
                        2, 0, PeakDetector.PeakMethod.HEIGHT, 1.5);
            } catch (RuntimeException e) {
                threw = true;
            }
            check("invalid params do not throw", !threw);
            check("window 0 -> null", r1 == null);
            check("window > n -> null", r2 == null);
            check("threshold > 1 -> null", r3 == null);
        }

        // 7) ACF が常に [-1, 1] に収まり acf[0] == 1（小信号 = 直接計算パス）
        {
            Random rnd = new Random(99L);
            boolean ok = true;
            for (int t = 0; t < 300 && ok; t++) {
                int n = 10 + rnd.nextInt(200);
                double[] x = new double[n];
                for (int i = 0; i < n; i++) {
                    x[i] = rnd.nextGaussian() * (rnd.nextDouble() < 0.2 ? 5.0 : 1.0);
                }
                x = Detrend.removeMean(x);
                double[] acf = Autocorrelation.compute(x, 1, n - 1);
                if (acf.length > 0 && acf[0] != 1.0) ok = false;
                for (double v : acf) {
                    if (v < -1.0 - 1e-9 || v > 1.0 + 1e-9) ok = false;
                }
            }
            check("ACF within [-1,1] (direct path)", ok);
        }

        // 8) FFT パス: 直接計算（偏り推定の定義式）と一致し、範囲内にあること
        {
            Random rnd = new Random(123L);
            int n = 1000;
            double[] x = new double[n];
            for (int i = 0; i < n; i++) {
                x[i] = rnd.nextGaussian();
            }
            x = Detrend.removeMean(x);
            double[] acf = Autocorrelation.compute(x, 1, 200);

            double raw0 = 0.0;
            for (double v : x) raw0 += v * v;
            boolean ok = acf.length == 201;
            for (int lag = 1; lag <= 200 && ok; lag++) {
                double sum = 0.0;
                for (int i = 0; i + lag < n; i++) {
                    sum += x[i] * x[i + lag];
                }
                double expected = sum / raw0;
                if (Math.abs(expected - acf[lag]) > 1e-6) ok = false;
                if (acf[lag] < -1.0 || acf[lag] > 1.0) ok = false;
            }
            check("ACF FFT path matches direct formula", ok);
        }

        // 9) ランダム信号でも Confidence は [0, 1]
        {
            Random rnd = new Random(2024L);
            boolean ok = true;
            int detected = 0;
            for (int t = 0; t < 200; t++) {
                int n = 50 + rnd.nextInt(400);
                double[] x = new double[n];
                for (int i = 0; i < n; i++) {
                    x[i] = rnd.nextGaussian() * (rnd.nextDouble() < 0.2 ? 5.0 : 1.0);
                }
                PeriodResult r = PeriodDetector.detectPeriod(
                        x, PeriodDetector.DetrendMethod.LINEAR_REGRESSION, 0,
                        2, 0, PeakDetector.PeakMethod.PROMINENCE, 0.5);
                if (r != null) {
                    detected++;
                    if (r.confidence < 0.0 || r.confidence > 1.0) ok = false;
                }
            }
            check("confidence within [0,1] on random signals (detected=" + detected + ")", ok);
        }

        // 10) ACF平滑化オプション付きでも既知周期を検出できる
        {
            double[] x = sine(400, 25.0, 0.0, 55L, 0.3);
            PeriodResult r = PeriodDetector.detectPeriod(
                    x, PeriodDetector.DetrendMethod.LINEAR_REGRESSION, 0,
                    2, 0, PeakDetector.PeakMethod.PROMINENCE, 0.5,
                    3, 0.95, true);
            check("sine25 with smoothing detected", r != null);
            if (r != null) {
                checkClose("sine25 with smoothing period", 25.0, r.period, 0.8);
            }
        }

        // 11) 結果表の列構成が常に一定であること（成功/失敗で列が変わらない）
        //     Period_Scaled / Unit / Profile_Len は出力しない
        {
            ij.measure.ResultsTable tbl = new ij.measure.ResultsTable();
            WK_RoiMan_PeriodDetector.writeRow(tbl, "A", 25.0, 40.0,
                    new PeriodResult(10.0, 0.9), "OK");
            WK_RoiMan_PeriodDetector.writeRow(tbl, "B", 5.0, 10.0,
                    null, "Failed: test");
            WK_RoiMan_PeriodDetector.writeRow(tbl, "C", Double.NaN, Double.NaN,
                    new PeriodResult(4.0, 0.5), "OK");

            java.util.List<String> headings =
                    java.util.Arrays.asList(tbl.getHeadings());
            String[] expected = { "ROI_Name", "Center_X", "Center_Y", "Period", "Confidence",
                    "Status" };
            boolean allPresent = true;
            for (String h : expected) {
                if (!headings.contains(h)) allPresent = false;
            }
            check("all result columns always present", allPresent);
            check("removed columns absent",
                    !headings.contains("Period_Scaled")
                            && !headings.contains("Unit")
                            && !headings.contains("Profile_Len"));
            check("row count", tbl.getCounter() == 3);
            checkClose("period on success row", 10.0,
                    tbl.getValue("Period", 0), 1e-9);
            check("period is NaN on failure",
                    Double.isNaN(tbl.getValue("Period", 1)));
            check("status present on all rows",
                    "OK".equals(tbl.getStringValue("Status", 0))
                            && tbl.getStringValue("Status", 1).startsWith("Failed"));
            checkClose("center_x on success row", 25.0,
                    tbl.getValue("Center_X", 0), 1e-9);
            checkClose("center_y on failure row", 10.0,
                    tbl.getValue("Center_Y", 1), 1e-9);
            check("center is NaN when ROI is unavailable",
                    Double.isNaN(tbl.getValue("Center_X", 2)));
        }

        // 12) ROI の中心座標: 輪郭点の平均位置（線 ROI では線分の中点）
        {
            double[] c1 = WK_RoiMan_PeriodDetector.centerOf(new ij.gui.Roi(10, 20, 30, 40));
            checkClose("rect roi center x", 25.0, c1[0], 1e-9);
            checkClose("rect roi center y", 40.0, c1[1], 1e-9);
            double[] c2 = WK_RoiMan_PeriodDetector.centerOf(new ij.gui.Line(0, 0, 10, 20));
            checkClose("line roi center x (midpoint)", 5.0, c2[0], 1e-9);
            checkClose("line roi center y (midpoint)", 10.0, c2[1], 1e-9);
            double[] c3 = WK_RoiMan_PeriodDetector.centerOf(null);
            check("null roi center is NaN",
                    Double.isNaN(c3[0]) && Double.isNaN(c3[1]));
        }

        // 13) 最小 prominence: 絶対下限で候補ピークを棄却できること
        {
            // lag 2: 高さ 0.9 / prominence 0.2、lag 5: 高さ 0.6 / prominence 0.5
            double[] acf = { 1.0, 0.7, 0.9, 0.7, 0.1, 0.6, 0.1, 0.0 };
            check("no floor picks tallest peak",
                    PeakDetector.findPeak(acf, 1, 6,
                            PeakDetector.PeakMethod.HEIGHT, 0.5, 0.0) == 2);
            check("floor rejects low-prominence tallest peak",
                    PeakDetector.findPeak(acf, 1, 6,
                            PeakDetector.PeakMethod.HEIGHT, 0.5, 0.3) == 5);
            check("floor above all prominences -> no peak",
                    PeakDetector.findPeak(acf, 1, 6,
                            PeakDetector.PeakMethod.HEIGHT, 0.5, 0.6) == -1);
            check("legacy findPeak has no floor",
                    PeakDetector.findPeak(acf, 1, 6,
                            PeakDetector.PeakMethod.HEIGHT, 0.5) == 2);
        }

        // 14) 最小 prominence（detectPeriod 経由）: 正常な周期信号は通り、
        //     不正な下限や到達不能な下限では null になること
        {
            double[] x = sine(400, 25.0, 0.0, 55L, 0.3);
            PeriodResult r = PeriodDetector.detectPeriod(
                    x, PeriodDetector.DetrendMethod.LINEAR_REGRESSION, 0,
                    2, 0, PeakDetector.PeakMethod.PROMINENCE, 0.5,
                    3, 0.95, false, 0.10);
            check("sine25 passes min prominence 0.10", r != null);
            if (r != null) {
                checkClose("sine25 with min prominence period", 25.0, r.period, 0.8);
            }
            check("negative min prominence -> null", PeriodDetector.detectPeriod(
                    x, PeriodDetector.DetrendMethod.LINEAR_REGRESSION, 0,
                    2, 0, PeakDetector.PeakMethod.PROMINENCE, 0.5,
                    3, 0.95, false, -0.1) == null);
            check("NaN min prominence -> null", PeriodDetector.detectPeriod(
                    x, PeriodDetector.DetrendMethod.LINEAR_REGRESSION, 0,
                    2, 0, PeakDetector.PeakMethod.PROMINENCE, 0.5,
                    3, 0.95, false, Double.NaN) == null);
            check("unreachable min prominence -> null", PeriodDetector.detectPeriod(
                    x, PeriodDetector.DetrendMethod.LINEAR_REGRESSION, 0,
                    2, 0, PeakDetector.PeakMethod.PROMINENCE, 0.5,
                    3, 0.95, false, 5.0) == null);
        }

        System.out.println("----");
        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }
}
