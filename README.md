# IJToolsUsingOpenCV

Version: **0.9.62.11** (2026-11-08) — [Release notes](ReleaseNotes.md)

[The english discriptions - >](#English)  
[日本語の説明 - >](#日本語)

-----

## 日本語

OpenCVの関数の一部を利用するImageJ用プラグインを作りました。

ONNX推論プラグイン（物体検出・姿勢推定・画像分類）は削除しました。これらの機能には [IJToolsUsingOnnxruntime](https://github.com/WAKU-TAKE-A/IJToolsUsingOnnxruntime) を使用してください。

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

## English

I have made plugins for ImageJ that uses a part of OpenCV's functions.

ONNX inference plugins (object detection, pose estimation, and image classification) have been removed. Use [IJToolsUsingOnnxruntime](https://github.com/WAKU-TAKE-A/IJToolsUsingOnnxruntime) for these tasks.

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
