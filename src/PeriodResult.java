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
