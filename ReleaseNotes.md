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

* Create and debug plugins in OpenCV 3.4.2.
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
