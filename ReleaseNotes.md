# Release v0.9.41.0

Latest update: August 17th 2023

Confirmed with ImageJ 1.54h9 + JDK17 + OpenCV4.8.0.

# Release v0.9.101.0

Latest update: July 23th 2023

Confirmed with ImageJ 1.53t + JDK11 + OpenCV4.8.0.

## Changes

* Compatible with OpenCV4.8.0.
* Requires a custom ImageJ to load opencv_java000.dll.

# Release v0.9.40.0

Latest update: July 23th 2023

Confirmed with ImageJ 1.53t + JDK8 + OpenCV4.6.0.

## Changes

* Manage the project itself, not just the source.

# Release v0.9.37.0

Latest update: October 19th 2021

Confirmed with ImageJ 1.53m + OpenCV4.5.3.

## Changes

* OCV_MedianBlur is supported in RGB 24-bit, 8-bit gray, 16-bit gray, and 32-bit gray. When 16-bit gray, and 32-bit gray, ksize is 3 or 5.

# Release v0.9.36.1

Latest update: October 8th 2021

Confirmed with ImageJ 1.53m + OpenCV4.5.3.

## Changes

* Accelerate OCV_CntrlUvcCamera.

# Release v0.9.36.0

Latest update: August 22th 2021

Confirmed with ImageJ 1.53K + OpenCV4.5.3.

## Changes

* The dialog is no longer displayed when OCV__LoadLibrary is executed.(display in the status bar)
* Fix the bug of OCV_MatchTemplate.
* Fix the bug of OCV_MorphologyEx.
* Added functionality to OCV_VideoCapture. (view statistics, view profile)
* Changed the maxValue setting of OCV_AdaptiveThreshold to a slider.

etc.

# Release v0.9.35.0

Latest update: May 10th 2020

Confirmed with OpenCV4.5.3.

## Changes

Since the argument of the constructor of VideoCapture was changed, fix it.

# Release v0.9.33.2

Latest update: October 22th 2018

# Release v0.9.33.3

Latest update: Janualy 1st 2019

Confirmed with OpenCV4.2.0.

# Release v0.9.33.2

Latest update: October 22th 2018

## Bug Fixes

* Fixed the wrong name of the binary threshold in "OCV_Threshold" dialog (lower-> thresh). Since the contents of the macro change, attention is necessary.
* Fixed the omission of "gd.addDialogListener (this);" in "OCV_Resize" and "OCV_WarpPolar".

# Release v0.9.32.0

Latest update: September 7th 2018

## New Features

* Add plugins of OCV_CornerHarris, OCV_Resize and OCV_Scharr.

## Bug Fixes

* Fixed a bug that a interpolation did not switch properly in OCV_WarpPolar plugin.

# Release v0.9.31.2

Latest update: August 22th 2018

## New Features

* Add OCV_WarpPolar plugin.
* OCV_LinearPolar.java and OCV_LogPolar.java are deleted.
* FeatureDetector and DescriptorExtractor are not used.
* The binary for Java 1.8 (64bit) only is released.

# Release v0.9.29.0

Latest update: July 28th 2018

## New Features

* Create and debug plugins in OpenCV 4.1.0.
* Add OCV_MorphologyEx plugin.
* In OCV_BilateralFilter / OCV_Blur / OCV_GaussianBlur / OCV_Laplacian / OCV_Sobel, confirm border types again.
* WK_DilateWhite and WK_ErodeWhite is abolished.

# Release v0.9.28.0

Latest update: December 18th 2017

## New Features

* In OCV_ConnectedComponentsWithStats, it is now possible to select ROI selection by the Wand tool or rectangular selection by Bx / By / Width / Height.

# Release v0.9.26.0

Latest update: December 13th 2017

## Bug Fixes

* In OCV_FeatDet_2nd_Match, "Unknown error" prevents new window from being displayed, only to be displayed in status.

# Release v0.9.25.0

Latest update: December 10th 2017

## New Features

* Create and debug plugins in OpenCV 3.3.1.
* Significantly changed plugin for feature detection.
* Add interactive GrabCut plugin.
* OCV_LinearPolar.java : It corresponds to 8bit, 16bit, 32bit, RGB.
* OCV_LogPolar.java : It corresponds to 8bit, 16bit, 32bit, RGB.
* Added Wait function.

## Bug Fixes

Several serious bugs were found and fixed. A bug was found in a function that converts the OpenCV Mat class to an int array. I'm sorry.

* OCV__LoadLibrary.java
  * In mat2intarray(), Fixed forgetting mask processing.
* OCV_ConnectedComponentsWithStats.java, 
  * Fixed wrong order when selecting blob in doWand.
* OCV_CntrlUvcCamera.java
  * Fixed to open again when the window is closed.
* OCV_BoundingRect.java, OCV_FitEllipse.java, OCV_MinAreaRect.java, OCV_MinEnclosingCircle.java
  * RoiManager and ResultsTable will not open when no processing is in progress.
  * ROI will be selected appropriately after processing.
* OCV_ConvexHull.java
  * ROI will be selected appropriately after processing.
* OCV_Sobel.java
  * Change the dialog.
* WK_RoiMan_DisplayedInTheCenter.java
  * When it is not selected, the center of the image is displayed.
