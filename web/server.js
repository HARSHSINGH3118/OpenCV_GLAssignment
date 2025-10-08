// web/server.js
const express = require("express");
const multer = require("multer");
const fs = require("fs");
const path = require("path");

const app = express();
const PORT = 5000;

// Ensure folders
const UPLOAD_DIR = path.join(__dirname, "uploads");
const DIST_DIR = path.join(__dirname, "dist");
if (!fs.existsSync(UPLOAD_DIR)) fs.mkdirSync(UPLOAD_DIR, { recursive: true });
if (!fs.existsSync(DIST_DIR)) fs.mkdirSync(DIST_DIR, { recursive: true });

const upload = multer({ dest: path.join(__dirname, "tmp") });

// Disable caching for dynamic things
app.use((req, res, next) => {
  res.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("Expires", "0");
  next();
});

// Handle image uploads from Android (field name "frame")
app.post("/upload", upload.single("frame"), (req, res) => {
  try {
    if (!req.file) return res.status(400).json({ ok: false, error: "No file 'frame'" });
    const finalPath = path.join(UPLOAD_DIR, "latest_frame.jpg");

    // Move/rename to latest_frame.jpg
    fs.renameSync(req.file.path, finalPath);
    console.log("✅ Received new frame:", finalPath);

    // Touch file mtime to now (optional)
    const now = new Date();
    fs.utimesSync(finalPath, now, now);

    return res.json({ ok: true });
  } catch (e) {
    console.error(e);
    return res.status(500).json({ ok: false, error: String(e) });
  }
});

// Metadata endpoint used by the web page
app.get("/latest_meta", (req, res) => {
  const p = path.join(UPLOAD_DIR, "latest_frame.jpg");
  if (!fs.existsSync(p)) return res.json({ exists: false });

  const stat = fs.statSync(p);
  res.json({
    exists: true,
    bytes: stat.size,
    modified: stat.mtimeMs, // epoch ms
  });
});

// Serve the latest frame at /latest_frame.jpg
app.get("/latest_frame.jpg", (req, res) => {
  const p = path.join(UPLOAD_DIR, "latest_frame.jpg");
  if (!fs.existsSync(p)) return res.status(404).send("No frame yet");
  res.sendFile(p);
});

// Serve built site
app.use(express.static(DIST_DIR));
// Also serve uploads publicly so /latest_frame.jpg works
app.use(express.static(UPLOAD_DIR));

// Default to index.html
app.get("*", (req, res) => {
  res.sendFile(path.join(DIST_DIR, "index.html"));
});

app.listen(PORT, () => {
  console.log(`🌐 Ajax server running: http://0.0.0.0:${PORT}`);
  console.log(`   Upload endpoint:   POST /upload (field "frame")`);
  console.log(`   Latest frame URL:  /latest_frame.jpg`);
});
