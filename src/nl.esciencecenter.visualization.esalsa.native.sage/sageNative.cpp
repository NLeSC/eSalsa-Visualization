#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include "../../sage-code/sage/include/libsage.h"

#include "nl_esciencecenter_visualization_esalsa_SageInterface.h"

sail *sageInf1;//, *sageInf2; // sail object
void *buffer1;//, *buffer2;

int resX, resY;
double frames;

int started = 0;

//Attach the current thread to the JVM if it hasn't been done yet
	//JavaVM *jvm;
	//env->GetJavaVM(&jvm);
	//int res = jvm->AttachCurrentThread((void **)&env, NULL);
	//if (res < 0) {
	//	fprintf(stderr, "Attach failed\n");
	//	return false;
	//}

JNIEXPORT jint JNICALL Java_nl_esciencecenter_visualization_esalsa_jni_SageInterface_setup(JNIEnv *env, jobject obj, jint width, jint height, jint fps) {
	resX = (int)width;
	resY = (int)height;
	frames = (double)fps;

	sageInf1 = createSAIL("javaBridge", resX, resY, PIXFMT_8888_INV, NULL, BOTTOM_TO_TOP, frames);
	//sageInf2 = createSAIL("javaBridge2", resX, resY, PIXFMT_8888_INV, NULL, TOP_TO_BOTTOM, frames);
	
	buffer1 = nextBuffer(sageInf1);
	//buffer2 = nextBuffer(sageInf2);

	return 0;
}

JNIEXPORT jint JNICALL Java_nl_esciencecenter_visualization_esalsa_jni_SageInterface_start(JNIEnv *env, jobject obj, jobject byteBuffer) {
	//long *int1;
	//int1 = (long *)malloc(sizeof(long)*size);
	//env->GetIntArrayRegion(arr,0,size,int1);
	//free(int1);

	void* bbuf_in = env->GetDirectBufferAddress(byteBuffer);

	memcpy(buffer1, bbuf_in, resX*resY*4);
	//memcpy(buffer2, body, resX*resY*4);

	//env->GetIntArrayRegion(arr, 0, size, (int*)buffer);
	//memset(buffer, 100, resX*resY*4);
	buffer1 = swapAndNextBuffer(sageInf1);
	//buffer2 = swapAndNextBuffer(sageInf2);

	processMessages(sageInf1);
	//processMessages(sageInf2);

	return 0;
}

