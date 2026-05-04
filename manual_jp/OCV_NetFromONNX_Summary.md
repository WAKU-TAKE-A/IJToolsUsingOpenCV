# YOLO/YOLOX ONNX Java実装 総まとめ

## 概要

ImageJプラグインとして、YOLOv8・YOLOXのONNX物体検出モデル、**YOLO姿勢推定（Pose）モデル**、および**YOLOの画像分類モデル**をサポートする推論パイプラインを実装した。
Pythonスクリプト（onnxruntime）で動作する推論をJava（OpenCV DNN）に移植する作業であり、多くのトラブルシューティングを経て完成した。

---

## 1. ONNXモデルのInput/Output構成

### Input

全モデル共通で以下の形状：

```
[batch, channel, height, width] = [1, 3, 640, 640]
```

- batch: 常に1
- channel: RGB（またはBGR）の3チャンネル
- height, width: モデルの入力サイズ（通常640x640）

### Output

モデルによって形状が異なる：

| モデル | Output形状 | 意味 |
|--------|-----------|------|
| YOLOv8（YOLO_Object_Pixel / YOLO_Object_Normalized） | `[1, 4+numClasses, 8400]` | 4=bbox(cx,cy,w,h) + クラス数 |
| YOLO姿勢推定（YOLO_Pose） | `[1, 56, 8400]` | 4=bbox + 1=score + 17kpts(x,y,conf) |
| YOLOX（YOLOX_Object_Undecoded） | `[1, 8400, 5+numClasses]` | 5=bbox+objectness + クラス数 |
| YOLO分類（YOLO_Class） | `[1, numClasses]` | 各クラスの確率スコア（softmax済み） |

### 構成の調べ方

**Pythonでの確認：**
```python
sess = ort.InferenceSession("model.onnx")
inp = sess.get_inputs()[0]
out = sess.get_outputs()[0]
print("Input :", inp.name, inp.shape, inp.type)
print("Output:", out.name, out.shape, out.type)
```
---

## 2. Output後の整形（reshape）

### YOLOの場合

Output: `[1, 84, 8400]` → 各列が1つのボックス → 転置して使いやすくする

```java
// [1, 84, 8400] → [84, 8400]
Mat temp = outputs.reshape(1, outputs.size(1));
// [84, 8400] → [8400, 84]  ← 各行が1ボックス
Mat predictions = temp.t();
```

### YOLOXの場合

Output: `[1, 8400, 85]` → すでに各行が1ボックス → reshapeのみ

```java
// [1, 8400, 85] → [8400, 85]
Mat predictions = outputs.reshape(1, outputs.size(1));
```

### 整形後の各行の意味

**YOLO:**
```
[cx, cy, w, h, cls_0, cls_1, ..., cls_79]  // 84列
```

**YOLOX:**
```
[tx, ty, tw, th, objectness, cls_0, cls_1, ..., cls_N]  // 85列以上
```

---

## 3. 正規化

### YOLOの場合

学習時に0-1正規化されているため、入力も0-1に変換が必要：

```java
private static final double SCALE_FACTOR = 1.0 / 255.0;
Mat blob = Dnn.blobFromImage(rgb, SCALE_FACTOR, inputSize, MEAN_VAL, swapRB, false);
//                                ↑ 0-255 を 0-1 に正規化
```

### YOLOXの場合

学習時に正規化なし（0-255のまま）のため、変換不要：

```java
Mat blob = Dnn.blobFromImage(bgr, 1.0, inputSize, MEAN_VAL, false, false);
//                               ↑ 1.0 = 変換なし
```

### チャンネル順（BGR/RGB）

| モデル | チャンネル順 | blobFromImageのswapRB |
|--------|------------|----------------------|
| YOLO   | RGB        | true                 |
| YOLOX  | BGRのまま   | false                |

---

## 4. Letterbox・リサイズ・パディング

### なぜLetterboxが必要か

YOLOv8は学習時にLetterboxを使用している（Ultralytics内部で自動適用）。
推論時も同じ前処理をしないと精度が落ちる。

### Letterboxの仕組み

アスペクト比を保ちながらリサイズし、余白をグレー(114,114,114)で埋める：

```
元画像: 480x640 (縦長)
↓ ratio = min(640/640, 640/480) = 1.0
リサイズ後: 480x640 (変化なし)
↓ パディング
左右に80pxずつグレーパディング → 640x640
```

```java
// Java実装
double ratio = Math.min(inputSize.height / h0, inputSize.width / w0);
int newH = (int) Math.round(h0 * ratio);
int newW = (int) Math.round(w0 * ratio);

Imgproc.resize(img, resized, new Size(newW, newH));

int padTop  = (640 - newH) / 2;
int padLeft = (640 - newW) / 2;
Core.copyMakeBorder(resized, padded,
    padTop, dh - padTop, padLeft, dw - padLeft,
    Core.BORDER_CONSTANT, new Scalar(114, 114, 114));
```

### YOLOXの場合（letterbox対応）

**学習時の前処理：**
YOLOX公式コードは学習時にletterboxを使用している（YOLOX/yolox/data/data_augment.py）。

```java
// Letterbox版（推奨）
preproc = preprocess(image, 1.0, false, true);  // useLetterbox=true
// → ratioX == ratioY, padLeft/padTopあり

// 強制リサイズ版（Pythonスクリプト互換）
preproc = preprocess(image, 1.0, false, false);  // useLetterbox=false
// → ratioX != ratioY, padLeft/padTop=0
```

---

## 5. 座標変換

### YOLO_PIXEL の場合

モデル出力の座標は640x640のパディング済み画像上のピクセル座標：

```java
// Step 1: パディングオフセットを除去
double cx_px = cx - padLeft;
double cy_px = cy - padTop;
// Step 2: 元画像サイズにスケール
double cx_orig = cx_px / ratio;
double cy_orig = cy_px / ratio;
// Step 3: xywh → xyxy変換
double x1 = cx_orig - w_orig / 2;
double y1 = cy_orig - h_orig / 2;
double x2 = cx_orig + w_orig / 2;
double y2 = cy_orig + h_orig / 2;
```

### YOLO_NORMALIZED の場合

座標が0-1の正規化済み → まずピクセル化してから同様の処理：

```java
// Step 1: ピクセル座標化
double cx_px = cx * inputSize.width;
double cy_px = cy * inputSize.height;
// Step 2以降はYOLO_PIXELと同じ
```

### YOLOX の場合

グリッドとストライドを使ったデコードが必要：

```java
// グリッド座標 → 640x640上のピクセル座標
double cx = (tx + gx) * stride;
double cy = (ty + gy) * stride;
double w  = Math.exp(tw) * stride;
double h  = Math.exp(th) * stride;
// 元画像サイズにスケール（X/Y別々）
cx = (cx - padLeft) / ratioX;
cy = (cy - padTop)  / ratioY;
```

---

## 6. NMS（Non-Maximum Suppression）

### 目的

同じ物体に対する重複した検出ボックスを除去する。

### アルゴリズム（Python/Java共通）

```
1. スコアの高い順にソート
2. 最高スコアのボックスをkeepに追加
3. 残りのボックスとIoUを計算
4. IoUがしきい値を超えるボックスを除去（重複とみなす）
5. 残ったボックスで2に戻る
```

### IoU（Intersection over Union）

```java
double inter = max(0, ix2-ix1) * max(0, iy2-iy1);  // 重複面積
double union = areaA + areaB - inter + 1e-9;          // 合計面積
double iou   = inter / union;
```

### agnostic NMS vs per-class NMS

**agnostic（クラスを無視）：** 全ボックスをまとめてNMS
→ クラスが違っても位置が重なれば削除される問題がある

**per-class（クラスごと）：** クラスごとに分けてNMS → 採用
→ バスの上に人がいるような場合でも両方検出できる

```java
// per-class NMS
for (int c : uniqueClasses) {
    // クラスcのボックスだけ抽出してNMS
    List<Integer> keep = nmsXYXY(classBoxes, classConfs, nmsThresh);
    // 結果をマージ
}
```

### OpenCV NMSBoxesではなく独自実装を採用した理由

`Dnn.NMSBoxes()`のシグネチャ：

```java
Dnn.NMSBoxes(MatOfRect2d bboxes, MatOfFloat scores,
             float score_threshold, float nms_threshold, MatOfInt indices)
```

**per-classの引数が存在しない。** 常にagnosticのみ。

per-classを実現するにはクラスごとにループして`NMSBoxes()`を呼ぶ必要があり、
結局独自実装と手間が変わらない。それならPythonと完全に同じアルゴリズムを
独自実装した方が：

- per-classが自然に実現できる
- 動作が完全に把握できる
- デバッグしやすい

というメリットがある。速度面ではC++実装の`NMSBoxes()`が有利だが、
検出候補数（数千個程度）では実用上問題にならない。

---

## 7. 実装構成

### ファイル構成

| ファイル | 役割 |
|---------|------|
| `MyNetFromONNX.java` | モデルラッパー。前処理・推論・後処理・NMSを担当 |
| `OCV_NetFromOnnx_1st_Read.java` | ImageJプラグイン。モデルのロードとフォーマット選択 |
| `OCV_NetFromOnnx_2nd_Inference.java` | ImageJプラグイン。推論実行と結果表示 |

### フォーマット選択（4択）

| 選択肢 | 正規化 | チャンネル | リサイズ | 用途 |
|--------|--------|-----------|---------|---------|
| YOLO_Object_Pixel | 0-1 | RGB | Letterbox | 物体検出 |
| YOLO_Object_Normalized | 0-1 | RGB | Letterbox | 物体検出 |
| YOLO_Class | 0-1 | RGB | 単純リサイズ | 画像分類 |
| YOLO_Pose | 0-1 | RGB | Letterbox | 姿勢推定 |
| YOLOX_Object_Undecoded | なし(0-255) | BGR | Letterbox | 物体検出 |

---

## 8. 困難だった部分

### (1) NMSで削減されなかった

大量の候補が削減されず(1000個以上)、しかも全部confidence=1.0という異常な状態だった。
当初はNMSBoxesのJavaバインディングを疑ったが、後の調査で**真の原因は入力の正規化なし**だった。
正規化なし（0-255のまま）でモデルに入力すると推論結果が異常値（confidence=1.0が大量発生）になり、
NMSBoxesが正しく機能しているにもかかわらず削減できない状態になっていた。

正規化を修正（`1.0/255.0`）することで正常なconfidenceが得られ、NMSも正常に機能するようになった。

最終的にはper-class NMSを実現するために独自実装`nmsXYXY()`を採用した。
`Dnn.NMSBoxes()`はper-classの引数を持たないため、クラスごとにループして呼ぶ必要があり、
それなら独自実装の方がシンプルで動作把握もしやすいという判断から。

### (2) 入力の正規化なし（最重要バグ）

```java
// 間違い
Mat blob = Dnn.blobFromImage(rgb, 1.0, inputSize, ...);
// 正しい
Mat blob = Dnn.blobFromImage(rgb, 1.0/255.0, inputSize, ...);
```

`1.0`のままでは0-255がそのまま入力される。モデルは0-1を期待しているため
推論結果が全て異常値（confidence=1.0が大量発生）になっていた。
原因特定まで非常に時間がかかった。

### (3) Javaとpythonの計算順序の違い

座標変換の計算手順が微妙に異なっていた：

```python
# Pythonの正解
x1 = (cx - w/2 - pad_left) / ratio
```

```java
// 間違い（途中でratioで割っていた）
cx = (cx - padLeft) / ratio;
x1 = cx - w / ratio / 2;
```

Pythonのコードを1行1行忠実に移植することで解決。

### (4) YOLOとYOLOXの前処理の根本的な違い

当初、前処理は全モデル共通と思っていたが、YOLOXは：
- 正規化なし（0-255）
- BGRのまま
- Letterbox：参照したPythonスクリプトはなし（強制リサイズ）だが、公式学習コードはあり

という異なる前処理が必要だった。
`preprocess()`の引数に`scaleFactor`、`swapRB`、`useLetterbox`を追加することで
1つの関数で全パターンに対応できるよう設計した。

---

## 9. 教訓

1. **Pythonと完全に同じにするには1行1行確認が必要**
   - 計算順序が少し違うだけで結果が大きく変わる

2. **バグの原因は最もシンプルなところにある**
   - `1.0` を `1.0/255.0` にするだけで劇的改善

3. **フレームワーク標準APIより独自実装が適切なケースがある**
   - `Dnn.NMSBoxes()`はper-classの引数を持たない
   - per-classを実現するなら独自実装の方がシンプルで動作把握もしやすい

4. **前処理はモデルの学習条件に合わせる**
   - letterboxあり/なし、正規化あり/なし、BGR/RGBは学習時と揃える必要がある
