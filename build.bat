@echo off

dir /s /b src\*.java > sources.txt


javac -d out @sources.txt
if %errorlevel% neq 0 (
	echo %errorlevel%
	exit /b %errorlevel%
)

xcopy /s /y /i src\com\dreamcodex\ti\images out\com\dreamcodex\ti\images
xcopy /s /y /i src\com\dreamcodex\ti\component\images out\com\dreamcodex\ti\images

pushd out
jar cfe Magellan.jar com.dreamcodex.ti.Magellan -C . com
popd

java -cp out com.dreamcodex.ti.Magellan