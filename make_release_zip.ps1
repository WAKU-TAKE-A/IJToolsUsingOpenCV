# 1. バージョンの指定
$version = "0.9.51.0"
$zipName = "IJToolsUsingOpenCV_$version.zip"

# 2. 一時的なフォルダ構成の作成
$tempDir = "temp_release_build"
if (Test-Path $tempDir) { Remove-Item -Path $tempDir -Recurse -Force }
New-Item -ItemType Directory -Path "$tempDir\OpenCV" -Force | Out-Null
New-Item -ItemType Directory -Path "$tempDir\Waku" -Force | Out-Null

# 3. JARファイルのコピー (プロジェクトルートにあるものをコピー)
Copy-Item -Path ".\IJTools_UsingOpenCV.jar" -Destination "$tempDir\OpenCV\"
Copy-Item -Path ".\IJTools_Waku.jar" -Destination "$tempDir\Waku\"

# 4. ZIP圧縮の実行
if (Test-Path $zipName) { Remove-Item -Path $zipName -Force }
Compress-Archive -Path "$tempDir\*" -DestinationPath ".\$zipName" -Force

# 5. 一時フォルダの削除
Remove-Item -Path $tempDir -Recurse -Force

Write-Host "Done! Created: $zipName" -ForegroundColor Green
