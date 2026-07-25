/**
 * PeriodResult.java
 *
 * 周期検出の戻り値を格納するクラス。
 */
public class PeriodResult {
    
    /** ピクセル単位での検出周期 */
    public final int integerPeriod;
    
    /** サブピクセル精度の周期（放物線近似による） */
    public final double subpixelPeriod;
    
    /** 確信度（正規化自己相関値） */
    public final double confidence;

    public PeriodResult(int integerPeriod, double subpixelPeriod, double confidence) {
        this.integerPeriod = integerPeriod;
        this.subpixelPeriod = subpixelPeriod;
        this.confidence = confidence;
    }
}
