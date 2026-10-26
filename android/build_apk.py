#!/usr/bin/env python3
"""
Fast, zero-Gradle Android APK builder using the installed Android SDK 34 + JDK 17 tools.
Compiles, packages, zipaligns, and signs `android/build/T60.apk`.
"""

import glob
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
BUILD_DIR = os.path.join(ROOT, "build")
CLASSES_DIR = os.path.join(BUILD_DIR, "classes")
DEX_DIR = os.path.join(BUILD_DIR, "dex")

SDK_DIR = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk")
BUILD_TOOLS = os.path.join(SDK_DIR, "build-tools", "34.0.0")
ANDROID_JAR = os.path.join(SDK_DIR, "platforms", "android-34", "android.jar")
KEYSTORE = os.path.expandvars(r"%USERPROFILE%\.android\debug.keystore")

JAVAC = r"C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot\bin\javac.exe"
AAPT2 = os.path.join(BUILD_TOOLS, "aapt2.exe")
D8 = os.path.join(BUILD_TOOLS, "d8.bat")
ZIPALIGN = os.path.join(BUILD_TOOLS, "zipalign.exe")
APKSIGNER = os.path.join(BUILD_TOOLS, "apksigner.bat")


def run(cmd):
    print("->", " ".join(cmd))
    subprocess.check_call(cmd)


def main() -> int:
    if os.path.exists(BUILD_DIR):
        shutil.rmtree(BUILD_DIR)
    os.makedirs(CLASSES_DIR, exist_ok=True)
    os.makedirs(DEX_DIR, exist_ok=True)

    manifest = os.path.join(ROOT, "src", "main", "AndroidManifest.xml")
    java_files = glob.glob(os.path.join(ROOT, "src", "main", "java", "**", "*.java"), recursive=True)

    # 1. Compile Java sources against android-34
    run([
        JAVAC,
        "-encoding", "UTF-8",
        "--release", "11",
        "-classpath", ANDROID_JAR,
        "-d", CLASSES_DIR,
        *java_files,
    ])

    # 2. Convert .class files to classes.dex using d8
    class_files = glob.glob(os.path.join(CLASSES_DIR, "**", "*.class"), recursive=True)
    run([
        D8,
        "--lib", ANDROID_JAR,
        "--min-api", "26",
        "--output", DEX_DIR,
        *class_files,
    ])

    # 3. Compile resources (launcher icon) and package them with AndroidManifest.xml
    res_zip = os.path.join(BUILD_DIR, "res.zip")
    run([AAPT2, "compile", "--dir", os.path.join(ROOT, "src", "main", "res"), "-o", res_zip])
    unsigned_apk = os.path.join(BUILD_DIR, "T60-unaligned.apk")
    run([
        AAPT2, "link",
        "-I", ANDROID_JAR,
        "--manifest", manifest,
        "-o", unsigned_apk,
        res_zip,
    ])

    # 4. Add classes.dex into the APK zip
    import zipfile
    with zipfile.ZipFile(unsigned_apk, "a", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.write(os.path.join(DEX_DIR, "classes.dex"), "classes.dex")

    # 5. Zipalign
    aligned_apk = os.path.join(BUILD_DIR, "T60-aligned.apk")
    run([ZIPALIGN, "-f", "4", unsigned_apk, aligned_apk])

    # 6. Sign with debug keystore
    final_apk = os.path.join(BUILD_DIR, "T60.apk")
    run([
        APKSIGNER, "sign",
        "--ks", KEYSTORE,
        "--ks-pass", "pass:android",
        "--key-pass", "pass:android",
        "--ks-key-alias", "androiddebugkey",
        "--out", final_apk,
        aligned_apk,
    ])

    print(f"\nSUCCESS: Built signed APK at {final_apk} ({os.path.getsize(final_apk):,} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
