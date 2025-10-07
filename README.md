# edgeX — Real-Time Mobile Edge Detection

![Android](https://img.shields.io/badge/Platform-Android-brightgreen?style=flat-square&logo=android)
![OpenCV](https://img.shields.io/badge/OpenCV-C%2B%2B-blue?style=flat-square&logo=opencv)
![OpenGL%20ES](https://img.shields.io/badge/OpenGL-ES%203.0-orange?style=flat-square&logo=opengl)
![Node.js](https://img.shields.io/badge/Backend-Node.js-4FC08D?style=flat-square&logo=node.js)
![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)

## Overview
**edgeX** bridges on-device edge detection (Android + OpenCV C++ via JNI) with a lightweight LAN viewer (Node/Express + SPA).  
Camera frames are captured with **Camera2**, processed natively in **C++/OpenCV** (e.g., Canny), rendered with **OpenGL ES** in real time, optionally saved to Gallery, and uploaded to a **local server** that serves the most recent processed frame to any browser on the same network.

---

## System Architecture

          ┌──────────────────────────────────────────────────────────────────┐
          │                           Android                                │
          │   Kotlin UI / Lifecycle                                         │
          │   ─────────────────────────                                      │
          │   Camera2 (YUV_420_888)                                         │
          │        │                                                         │
          │        ▼                                                         │
          │   JNI Bridge (AAR / .so)  ─────▶  C++ OpenCV (YUV→RGBA→Edges)   │
          │        │                                    │                   │
          │        ▼                                    ▼                   │
          │   OpenGL ES Renderer (real-time preview)   Frame Buffer         │
          │        │                                    │                   │
          │        ├────────── Save to Gallery (MediaStore)                 │
          │        └────────── HTTP POST multipart/form-data                │
          │                               to http://<laptop-ip>:5000/upload │
          └──────────────────────────────────────────────────────────────────┘
                                            │
                                            ▼
```
┌────────────────────────────────────────────────────────────────────────────────┐
│ Node.js + Express + Multer │
│ /upload ── receives image → stores as web/uploads/latest_frame.jpg │
│ / ── serves SPA (web/dist/index.html + index.js) │
│ /latest_frame.jpg ── served directly for embedding/viewing │
└────────────────────────────────────────────────────────────────────────────────┘
```

**Data flow summary**
1. Camera2 delivers YUV frames.
2. JNI passes planes to native C++; OpenCV converts & detects edges.
3. OpenGL ES renders preview; current frame can be saved locally and/or uploaded.
4. Server persists the last frame and serves it to the SPA (auto-refresh).

---

## Folder Structure
```
edgeX/
├─ app/ # Android application
│ ├─ src/main/java/com/example/opencv_gl_assignment/
│ │ ├─ MainActivity.kt
│ │ └─ SplashActivity.kt
│ ├─ src/main/cpp/ # JNI + OpenCV C++
│ ├─ src/main/res/layout/
│ │ ├─ activity_main.xml
│ │ └─ activity_splash.xml
│ ├─ src/main/res/xml/
│ │ └─ network_security_config.xml # cleartext LAN allowance
│ ├─ CMakeLists.txt
│ └─ AndroidManifest.xml
│
├─ web/
│ ├─ server.js # Express + Multer
│ ├─ uploads/
│ │ └─ latest_frame.jpg # written by /upload
│ └─ dist/
│ ├─ index.html # SPA (no framework)
│ └─ index.js # fetch/refresh viewer logic
│
└─ README.md
```

---

## Tech Stack (Concise)
- **Android:** Kotlin, Camera2, OpenGL ES, MediaStore
- **Native:** C++17, OpenCV, JNI
- **Server:** Node.js, Express, Multer
- **Web SPA:** HTML/CSS + vanilla JS (auto-refresh viewer)

---

## Features
- Real-time on-device preview (OpenGL ES) with **Edge/Raw** toggle
- Native **OpenCV** pipeline via **JNI** for performance
- Optional **Save to Gallery** (MediaStore)
- **LAN upload** of current processed frame to local server
- Minimal **browser dashboard** showing the latest frame, metadata, and direct file endpoint

---

## ScreenShots
<img width="1621" height="1006" alt="image" src="https://github.com/user-attachments/assets/a0fac4ed-002c-4778-8ce6-67a3200d5a79" />
<img width="1588" height="631" alt="image" src="https://github.com/user-attachments/assets/39822d57-816f-4873-8031-2cb796b54c66" />
<img width="1644" height="551" alt="image" src="https://github.com/user-attachments/assets/df8b60d2-1c88-41f2-b0ff-51ce4ee37e50" />




## Installation & Setup

### Android
1. Open `app/` in Android Studio.
2. In `AndroidManifest.xml`:
   ```xml
   <uses-permission android:name="android.permission.CAMERA"/>
   <uses-permission android:name="android.permission.INTERNET"/>
### And inside <application ...>:
```
android:usesCleartextTraffic="true"
android:networkSecurityConfig="@xml/network_security_config"
```
### Create res/xml/network_security_config.xml:
```
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
  <base-config cleartextTrafficPermitted="true" />
</network-security-config>
```
### Set your server URL (example):
// e.g., MainActivity.kt
val uploadUrl = URL("http://192.168.1.50:5000/upload")
### Build & run on a device connected to the same Wi-Fi as your laptop.

## Web Server
```
cd web
npm init -y
npm i express multer
node server.js
# Open: http://<laptop-ip>:5000/
```
## Security & Privacy
- Intended for local LAN use only; /latest_frame.jpg always exposes the latest frame.
- Do not deploy publicly without authentication, HTTPS, and rate limiting.
- Ensure legal/ethical compliance for any surveillance-adjacent use.
## License
All right reserved with Harsh Singj

## Acknowledgements
OpenCV, Android Camera2, OpenGL ES, Express.js.
