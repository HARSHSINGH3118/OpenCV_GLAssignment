// app/src/main/cpp/native-lib.cpp
#include <jni.h>
#include <vector>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>

static void yuv420ToBgr(
        const uint8_t* y, const uint8_t* u, const uint8_t* v,
        int yStride, int uStride, int vStride,
        int w, int h,
        cv::Mat& bgr) {

    cv::Mat yMat(h, w, CV_8UC1, const_cast<uint8_t*>(y), yStride);
    cv::Mat uMat(h/2, w/2, CV_8UC1, const_cast<uint8_t*>(u), uStride);
    cv::Mat vMat(h/2, w/2, CV_8UC1, const_cast<uint8_t*>(v), vStride);

    cv::Mat uUp, vUp;
    cv::resize(uMat, uUp, {w, h}, 0, 0, cv::INTER_NEAREST);
    cv::resize(vMat, vUp, {w, h}, 0, 0, cv::INTER_NEAREST);

    // NOTE: Many devices deliver Y, Cb, Cr or Y, Cr, Cb; if colors look wrong, swap uUp/vUp below
    std::vector<cv::Mat> yuv = { yMat, vUp, uUp }; // Y,Cr,Cb ordering works well in practice
    cv::Mat yuvImg; cv::merge(yuv, yuvImg);

    cv::cvtColor(yuvImg, bgr, cv::COLOR_YCrCb2BGR);
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_example_opencv_1gl_1assignment_MainActivity_processFrameYuv420(
        JNIEnv* env, jobject,
        jbyteArray jy, jbyteArray ju, jbyteArray jv,
        jint yStride, jint uStride, jint vStride,
        jint width, jint height, jint mode) {

    jbyte* Y = env->GetByteArrayElements(jy, nullptr);
    jbyte* U = env->GetByteArrayElements(ju, nullptr);
    jbyte* V = env->GetByteArrayElements(jv, nullptr);

    cv::Mat bgr;
    yuv420ToBgr((uint8_t*)Y, (uint8_t*)U, (uint8_t*)V,
                yStride, uStride, vStride, width, height, bgr);

    cv::Mat outRGBA;
    if (mode == 1) {
        cv::Mat gray, edges;
        cv::cvtColor(bgr, gray, cv::COLOR_BGR2GRAY);
        cv::Canny(gray, edges, 80, 160);
        cv::cvtColor(edges, outRGBA, cv::COLOR_GRAY2RGBA);
    } else {
        cv::cvtColor(bgr, outRGBA, cv::COLOR_BGR2RGBA);
    }

    const size_t sz = outRGBA.total() * outRGBA.elemSize();
    jbyteArray jrgba = env->NewByteArray((jsize)sz);
    env->SetByteArrayRegion(jrgba, 0, (jsize)sz, reinterpret_cast<jbyte*>(outRGBA.data));

    env->ReleaseByteArrayElements(jy, Y, 0);
    env->ReleaseByteArrayElements(ju, U, 0);
    env->ReleaseByteArrayElements(jv, V, 0);
    return jrgba;
}
