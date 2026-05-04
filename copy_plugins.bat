@echo off
set "PROJECT_DIR=C:\Users\admin\Documents\NetBeansProjects\IJToolsUsingOpenCV"
set "IJ_OPENCV_DIR=C:\tools\ImageJ\plugins\OpenCV"
set "IJ_WAKU_DIR=C:\tools\ImageJ\plugins\Waku"

echo Copying IJTools_UsingOpenCV.jar to %IJ_OPENCV_DIR%...
copy /Y "%PROJECT_DIR%\IJTools_UsingOpenCV.jar" "%IJ_OPENCV_DIR%\"

echo Copying IJTools_Waku.jar to %IJ_WAKU_DIR%...
copy /Y "%PROJECT_DIR%\IJTools_Waku.jar" "%IJ_WAKU_DIR%\"

echo.
echo Copy complete!
pause
