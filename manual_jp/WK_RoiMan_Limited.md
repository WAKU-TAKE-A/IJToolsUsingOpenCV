# WK_RoiMan_Limited（ROIの数値フィルタリング）
## 1. 概要：画像処理の仕組み
面積(Area)や周長(Perimeter)などの計測値を元に、条件に合わないROIをROI Managerから一括削除します。
例えば、二値化後に「小さすぎるノイズの塊」や「細長すぎる形状」を数値指定で取り除きたい場合に非常に便利です。

## 2. GUIの使い方
- **type**: フィルタリングの基準にする項目（Area, Mean, Widthなど）を選択します。※Results Tableの見出し列から選べます。
- **enable_min_limit / min_limit**: 下限値を設定します。
- **enable_max_limit / max_limit**: 上限値を設定します。

## 3. 注意点
- 事前に `Results Table` に計測結果が出ている必要があります。もし結果がない場合は、自動的に `Measure` が実行されます。
- 条件に合わないROIはManagerから **完全に消去される** ため、必要に応じて事前にROIセットを保存しておいてください。
