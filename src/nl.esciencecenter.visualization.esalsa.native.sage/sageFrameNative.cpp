#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include "../../sage-code/sage3.0/include/libsage.h"

#include "imau_visualization_SageFrame.h"

sail *sageInf; // sail object
void *buffer;

int resX, resY;
double frames;

int started = 0;

JNIEXPORT jint JNICALL Java_imau_visualization_SageFrame_setup(JNIEnv *env, jobject obj, jint width, jint height, jint fps) {
	resX = (int)width;
	resY = (int)height;
	frames = (double)fps;

	sageInf = createSAIL("javaBridge", resX, resY, PIXFMT_8888_INV, NULL, TOP_TO_BOTTOM, frames);
	
	buffer = nextBuffer(sageInf);

	return 0;
}

JNIEXPORT jint JNICALL Java_imau_visualization_SageFrame_display(JNIEnv *env, jobject obj, jintArray arr) {
	jint *body = env->GetIntArrayElements(arr, 0);

	memcpy(buffer, body, resX*resY*4);

	buffer = swapAndNextBuffer(sageInf);

	processMessages(sageInf);

	return 0;
}

