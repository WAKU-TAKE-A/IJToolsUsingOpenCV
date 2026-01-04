# IJToolsUsingOpenCV ヘルプドキュメント・インデックス

このフォルダには、ImageJプラグイン「IJToolsUsingOpenCV」の各機能に関するヘルプファイルが格納されています。

## プロジェクト概要
OpenCVの強力な画像処理ライブラリをImageJ上でシームレスに利用するためのプラグイン群です。
全てのOpenCV系プラグインは、実行前に `OCV__LoadLibrary` が呼び出されている必要があります。

## プラグイン・カテゴリ別リスト

### 1. フィルタ・平滑化
- [OCV_Blur](OCV_Blur.md) / [OCV_GaussianBlur](OCV_GaussianBlur.md) / [OCV_MedianBlur](OCV_MedianBlur.md) / [OCV_BilateralFilter](OCV_BilateralFilter.md)
- [OCUtil_BluredImageDiff](OCUtil_BluredImageDiff.md)

### 2. エッジ検出・勾配
- [OCV_Canny](OCV_Canny.md) / [OCV_Sobel](OCV_Sobel.md) / [OCV_Scharr](OCV_Scharr.md) / [OCV_Laplacian](OCV_Laplacian.md)
- [OCV_CornerHarris](OCV_CornerHarris.md)

### 3. 二値化・領域分割
- [OCV_Threshold](OCV_Threshold.md) / [OCV_AdaptiveThreshold](OCV_AdaptiveThreshold.md)
- [OCV_Watershed](OCV_Watershed.md) / [OCV_GrabCut](OCV_GrabCut.md) / [OCV_InteractiveGrabCut](OCV_InteractiveGrabCut.md)
- [OCV_FloodFill](OCV_FloodFill.md)

### 4. 幾何変換・リサイズ
- [OCV_Resize](OCV_Resize.md) / [OCV_WarpAffine](OCV_WarpAffine.md) / [OCV_WarpPerspective](OCV_WarpPerspective.md) / [OCV_WarpPolar](OCV_WarpPolar.md)
- [OCV_GetRotationMatrix2D](OCV_GetRotationMatrix2D.md) / [OCV_GetAffineTransform](OCV_GetAffineTransform.md) / [OCV_GetPerspectiveTransform](OCV_GetPerspectiveTransform.md)

### 5. 形状解析・統計
- [OCV_BoundingRect](OCV_BoundingRect.md) / [OCV_MinAreaRect](OCV_MinAreaRect.md) / [OCV_MinEnclosingCircle](OCV_MinEnclosingCircle.md) / [OCV_FitEllipse](OCV_FitEllipse.md)
- [OCV_ConvexHull](OCV_ConvexHull.md) / [OCV_ConnectedComponentsWithStats](OCV_ConnectedComponentsWithStats.md)
- [WK_HuMoments](WK_HuMoments.md) / [OCV_DistanceTransform](OCV_DistanceTransform.md)

### 6. 特徴抽出・マッチング
- [OCV_MatchTemplate](OCV_MatchTemplate.md)
- [OCV_FeatDet_1st_SetQuery](OCV_FeatDet_1st_SetQuery.md) / [OCV_FeatDet_2nd_Match](OCV_FeatDet_2nd_Match.md)
- [OCV_HoughLines](OCV_HoughLines.md) / [OCV_HoughLinesP](OCV_HoughLinesP.md) / [WK_HoughCircles](WK_HoughCircles.md)

### 7. カメラ・計測・ユーティリティ
- [OCUtil_CntrlUvcCamera](OCUtil_CntrlUvcCamera.md) / [OCUtil_MeasureWidth](OCUtil_MeasureWidth.md)
- [OCV_CameraCalib_1st_Create](OCV_CameraCalib_1st_Create.md) / [OCV_CameraCalib_2nd_Undistort](OCV_CameraCalib_2nd_Undistort.md)
- [WK_ChangePixelValue](WK_ChangePixelValue.md) / [WK_Math](WK_Math.md)
- [WK_RoiMan_SelectAll](WK_RoiMan_SelectAll.md) / [WK_RoiMan_Limited](WK_RoiMan_Limited.md) / [WK_RoiMan_LinearFitting](WK_RoiMan_LinearFitting.md) / [WK_RoiMan_DisplayedInTheCenter](WK_RoiMan_DisplayedInTheCenter.md)
- [WK_Wait](WK_Wait.md) / [WK_GetProperty](WK_GetProperty.md)

---
## 開発者向け引き継ぎ事項
- **ライブラリ依存**: すべてのプラグインは `OCV__LoadLibrary` を介してネイティブライブラリをロードします。
- **インスタンス管理**: `UnsatisfiedLinkError` を避けるため、フィールドでの `new Mat()` 宣言は禁止されています。必ずメソッド内、または `run` メソッドの `try-finally` ブロックでリソースを管理（`release()`）してください。
