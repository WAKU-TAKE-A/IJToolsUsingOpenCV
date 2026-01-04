# OCUtil_MeasureWidth（Canny法による幅計測）
## 1. 概要：画像処理の仕組み
画像上のラインROIに沿ってエッジを検出し、その2点間の距離（幅）を自動計測します。
内部でCanny法によるエッジ検出を行っているため、ノイズに強く、サブピクセル精度に近い安定した幅計測が可能です。

## 2. GUIの使い方
- **Threshold1 / 2**: Canny法の感度を調整する2つの閾値です。
- **LeftSideScan / RightSideScan**: エッジを探す方向を指定します（左から右へ、中央から外へ、など）。
- **ThresholdLeft / Right**: 検出されたエッジ強度のうち、どこを境界とみなすかの閾値です。
- **TypeOfRoi**: 結果を「線(line)」として残すか「点(point)」として残すかを選択します。

## 3. 注意点
- 入力画像は **8-bit Grayscale** である必要があります。
- 実行には **Line, Rectangle, または Rotated Rectangle ROI** が設定されている必要があります。
