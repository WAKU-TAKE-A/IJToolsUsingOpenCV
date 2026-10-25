
## 日本語

OpenCVの関数の一部を利用するImageJ用プラグインを作りました。

ONNX推論プラグイン（物体検出・姿勢推定・画像分類）は削除しました。これらの機能には [IJToolsUsingOnnxruntime](https://github.com/WAKU-TAKE-A/IJToolsUsingOnnxruntime) を使用してください。旧プラグインを呼び出す既存のマクロは、移行先のコマンドと引数に書き換える必要があります。

v0.9.62.0以前からの更新時は、古い `IJTools_UsingOpenCV.jar` を置き換え、ImageJのpluginsフォルダに個別配置した `OCV_NetFromOnnx_*.class` と `MyNetFromONNX*.class` があれば削除してから、ImageJを再起動してください。ONNX推論を続ける場合は `IJTools_UsingOnnxruntime.jar` を導入してください。具体的な移行先メニューは[日本語マニュアル](manual_jp/README.md#onnx推論機能の移行先)に記載しています。特徴点マッチング（`OCV_FeatDet_*`）は引き続き利用できます。

## 目的

* 手軽にOpenCVの関数を利用したい
* OpenCVの関数の処理の結果を簡単に確認したい
* ImageJにない機能を追加したい

## ビルド方法

本リポジトリを「NetBeansProjects」のフォルダにコピーします。

本リポジトリのルートに「ij.jar」「opencv-500.jar」「opencv_java500.dll」をコピーします。

NetBeansを起動します。

ファイル⇒プロジェクトを開く

先ほどコピーしたフォルダを選択します。

プロパティ－ライブラリのコンパイル時ライブラリの「Classpath」に「ij.jar」「opencv-500.jar」を追加します。

ビルドを実行してください。

## バイナリー

作成したプラグインのバイナリー(jarファイル)を[こちら](https://github.com/WAKU-TAKE-A/IJToolsUsingOpenCV/releases)に置いておきます。<br>是非使ってみてください。

-----

# IJToolsUsingOpenCV

Version: **0.9.62.3** (2026-10-25) — [Release notes](ReleaseNotes.md)

[The english discriptions - >](#English)  
[日本語の説明 - >](#日本語)

## English

I have made plugins for ImageJ that uses a part of OpenCV's functions.

ONNX inference plugins (object detection, pose estimation, and image classification) have been removed. Use [IJToolsUsingOnnxruntime](https://github.com/WAKU-TAKE-A/IJToolsUsingOnnxruntime) for these tasks. Existing macros calling the removed plugins must be updated to use its commands and arguments.

When upgrading from v0.9.62.0 or earlier, replace the old `IJTools_UsingOpenCV.jar` and remove any separately installed `OCV_NetFromOnnx_*.class` and `MyNetFromONNX*.class` files from the ImageJ plugins folder, then restart ImageJ. Install `IJTools_UsingOnnxruntime.jar` to continue using ONNX inference. Feature matching (`OCV_FeatDet_*`) remains available in this project.

## Purpose

* For using OpenCV functions easily.
* For checking the result of the processing eassily.
* For adding functions not included in ImageJ.

## How to build

Copy this repository to the "NetBeansProjects" folder.

Copy "ij.jar", "opencv-500.jar" and "opencv_java500.dll" to the root of this repository.

Start NetBeans.

File ⇒ Open Project

Select the folder you copied earlier.

Add "ij.jar" and "opencv-500.jar" to the "Classpath" in Properties - Libraries.

Please run the build.

## Binaries

There are the binaries (jar files) of the created plugins in [here](https://github.com/WAKU-TAKE-A/IJToolsUsingOpenCV/releases). Please try using it.
