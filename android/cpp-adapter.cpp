#include <jni.h>
#include <sys/types.h>
#include "gizwits_c_sdk.h"
#include "pthread.h"
#include <jsi/jsi.h>
#include <android/log.h>

using namespace facebook::jsi;
using namespace std;

JavaVM *java_vm;
jclass java_class;
jobject java_object;


/**
 * A simple callback function that allows us to detach current JNI Environment
 * when the thread
 * See https://stackoverflow.com/a/30026231 for detailed explanation
 */


static jstring string2jstring(JNIEnv *env, const string &str) {
    return (*env).NewStringUTF(str.c_str());
}
void DeferThreadDetach(JNIEnv *env) {
    static pthread_key_t thread_key;

    // Set up a Thread Specific Data key, and a callback that
    // will be executed when a thread is destroyed.
    // This is only done once, across all threads, and the value
    // associated with the key for any given thread will initially
    // be NULL.
    static auto run_once = [] {
        const auto err = pthread_key_create(&thread_key, [](void *ts_env) {
            if (ts_env) {
                java_vm->DetachCurrentThread();
            }
        });
        if (err) {
            // Failed to create TSD key. Throw an exception if you want to.
        }
        return 0;
    }();

    // For the callback to actually be executed when a thread exits
    // we need to associate a non-NULL value with the key on that thread.
    // We can use the JNIEnv* as that value.
    const auto ts_env = pthread_getspecific(thread_key);
    if (!ts_env) {
        if (pthread_setspecific(thread_key, env)) {
            // Failed to set thread-specific value for key. Throw an exception if you want to.
        }
    }
}

/**
 * Get a JNIEnv* valid for this thread, regardless of whether
 * we're on a native thread or a Java thread.
 * If the calling thread is not currently attached to the JVM
 * it will be attached, and then automatically detached when the
 * thread is destroyed.
 *
 * See https://stackoverflow.com/a/30026231 for detailed explanation
 */
JNIEnv *GetJniEnv() {
    JNIEnv *env = nullptr;
    // We still call GetEnv first to detect if the thread already
    // is attached. This is done to avoid setting up a DetachCurrentThread
    // call on a Java thread.

    // g_vm is a global.
    auto get_env_result = java_vm->GetEnv((void **) &env, JNI_VERSION_1_6);
    if (get_env_result == JNI_EDETACHED) {
        if (java_vm->AttachCurrentThread(&env, NULL) == JNI_OK) {
            DeferThreadDetach(env);
        } else {
            // Failed to attach thread. Throw an exception if you want to.
        }
    } else if (get_env_result == JNI_EVERSION) {
        // Unsupported JNI version. Throw an exception if you want to.
    }
    return env;
}


void install(facebook::jsi::Runtime &jsiRuntime) {

    auto sendData = Function::createFromHostFunction(jsiRuntime,
                                                          PropNameID::forAscii(jsiRuntime,
                                                                               "sendData"),
                                                          0,
                                                          [](Runtime &runtime,
                                                             const Value &thisValue,
                                                             const Value *arguments,
                                                             size_t count) -> Value {

        // 检查参数个数是否正确
          if (count != 1) {
            return Value(false);
        }

        // 检查参数类型是否正确
        if (!arguments[0].isString()) {
            return Value(false);
        }
        JNIEnv *jniEnv = GetJniEnv();

        string data = arguments[0].getString(
                runtime).utf8(runtime);

        jstring jsData = string2jstring(jniEnv,data);

        jvalue params[1];
        params[0].l = jsData;

        java_class = jniEnv->GetObjectClass(
                java_object);
        jmethodID sendData_c = jniEnv->GetMethodID(
                java_class, "sendData_c",
                "(Ljava/lang/String;)Z");
        jniEnv->CallBooleanMethodA(
                java_object, sendData_c, params);
        return Value(JNI_TRUE);

    });

    jsiRuntime.global().setProperty(jsiRuntime, "sendData", move(sendData));
}


void emitJsi(JNIEnv *env, jobject thiz, jlong jsi, jstring name, jstring data) {
    if (jsi == 0) {
        __android_log_print(ANDROID_LOG_ERROR, "Java_com_gizwitspadsdk_GizwitsPadSdkModule_emitJSI", "Invalid JSI pointer");
        return;
    }

    auto runtime = reinterpret_cast<facebook::jsi::Runtime *>(jsi);

    if (name == nullptr || data == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, "Java_com_gizwitspadsdk_GizwitsPadSdkModule_emitJSI", "Invalid name or data string");
        return;
    }
    const char *nameChars = env->GetStringUTFChars(name, nullptr);
    const char *dataChars = env->GetStringUTFChars(data, nullptr);

    if (nameChars == nullptr || dataChars == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, "Java_com_gizwitspadsdk_GizwitsPadSdkModule_emitJSI", "Failed to get name or data string");
        return;
    }

    std::string functionName(nameChars);
    std::string jsonString(dataChars);

    env->ReleaseStringUTFChars(name, nameChars);
    env->ReleaseStringUTFChars(data, dataChars);

   facebook::jsi::Object globalObject = runtime->global();
   facebook::jsi::String functionNameString = facebook::jsi::String::createFromUtf8(*runtime, functionName);
   facebook::jsi::String jsonStringJsi = facebook::jsi::String::createFromUtf8(*runtime, jsonString);

   if (globalObject.hasProperty(*runtime, functionNameString)) {
       facebook::jsi::Value nameFunction = globalObject.getProperty(*runtime, functionNameString);
       if (nameFunction.isObject() && nameFunction.asObject(*runtime).isFunction(*runtime)) {
           facebook::jsi::Function function = nameFunction.asObject(*runtime).asFunction(*runtime);
           function.call(*runtime, jsonStringJsi, 1); // 传递需要的参数
       }
   }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_gizwitspadsdk_GizwitsPadSdkModule_nativeInstall(JNIEnv *env, jobject thiz, jlong jsi) {

    auto runtime = reinterpret_cast<facebook::jsi::Runtime *>(jsi);

    if (runtime) {
        gizwits_c_sdk::install(*runtime);
        install(*runtime);
    }

    env->GetJavaVM(&java_vm);
    java_object = env->NewGlobalRef(thiz);
}


extern "C"
JNIEXPORT void JNICALL
Java_com_gizwitspadsdk_GizwitsPadSdkModule_emitJSI(JNIEnv *env, jobject thiz, jlong jsi, jstring name, jstring data) {

    emitJsi(env, thiz, jsi, name, data);

}
