// web/server.js
const express = require("express");
const multer = require("multer");
const fs = require("fs");
const path = require("path");

const app = express();
const PORT = 5000;

// Folder to save uploaded frames
const upload = multer({ dest: "uploads/" });

// Handle image uploads from Android
app.post("/upload", upload.single("frame"), (req, res) => {
  const filePath = path.join("uploads", "latest_frame.jpg");
  fs.renameSync(req.file.path, filePath);
  console.log(" Received new frame:", filePath);
  res.json({ success: true });
});

// Serve static files (uploads + built web)
app.use(express.static("uploads"));
app.use(express.static("dist"));

app.listen(PORT, () => {
  console.log(` Bridge server running on http://localhost:${PORT}`);
});
