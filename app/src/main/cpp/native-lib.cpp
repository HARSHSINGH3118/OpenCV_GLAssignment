#include <jni.h>
#include <android/log.h>
#include <vector>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/imgcodecs.hpp>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "native", __VA_ARGS__)

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_example_opencv_1gl_1assignment_MainActivity_cannyProcessImage(
        JNIEnv* env, jobject, jstring jInputPath) {

    const char* inputPath = env->GetStringUTFChars(jInputPath, nullptr);
    std::string inPath(inputPath);
    env->ReleaseStringUTFChars(jInputPath, inputPath);

    // Load the input image
    cv::Mat img = cv::imread(inPath, cv::IMREAD_COLOR);
    if (img.empty()) {
        LOGI("Failed to read image: %s", inPath.c_str());
        return nullptr;
    }

    // Convert to grayscale
    cv::Mat gray;
    cv::cvtColor(img, gray, cv::COLOR_BGR2GRAY);

    // Run Canny edge detection
    cv::Mat edges;
    cv::Canny(gray, edges, 80, 160);

    // Convert edges to 3-channel (for viewing/saving)
    cv::Mat colorEdges;
    cv::cvtColor(edges, colorEdges, cv::COLOR_GRAY2BGR);

    // Encode as JPEG to memory
    std::vector<uchar> buf;
    cv::imencode(".jpg", colorEdges, buf);

    // Return as byte array
    jbyteArray jbytes = env->NewByteArray(buf.size());
    env->SetByteArrayRegion(jbytes, 0, buf.size(), reinterpret_cast<jbyte*>(buf.data()));
    return jbytes;
}
