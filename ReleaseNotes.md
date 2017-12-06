# Release v0.9.23.0

Latest update: December 6th 2017

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
