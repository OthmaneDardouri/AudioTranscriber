// Ponte JNI minimale tra Kotlin e whisper.cpp.
#include <jni.h>
#include <android/log.h>
#include <string>
#include "whisper.h"

#define TAG "VocaleAI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

struct ProgressCtx {
    JNIEnv *env = nullptr;
    jobject listener = nullptr;
    jmethodID method = nullptr;
    int last = -1;
};

// Chiamata da whisper_full sullo stesso thread della chiamata JNI, quindi env è valido.
void on_progress(struct whisper_context * /*ctx*/, struct whisper_state * /*state*/,
                 int progress, void *user_data) {
    auto *p = static_cast<ProgressCtx *>(user_data);
    if (p == nullptr || p->listener == nullptr || p->method == nullptr) return;
    if (progress == p->last) return;
    p->last = progress;
    p->env->CallVoidMethod(p->listener, p->method, static_cast<jint>(progress));
    if (p->env->ExceptionCheck()) p->env->ExceptionClear();
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_othmane_vocaleai_whisper_WhisperLib_initContext(JNIEnv *env, jobject /*thiz*/,
                                                         jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    if (ctx == nullptr) LOGE("Impossibile caricare il modello: %s", path);
    else LOGI("Modello caricato: %s", path);
    env->ReleaseStringUTFChars(model_path, path);
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_com_othmane_vocaleai_whisper_WhisperLib_freeContext(JNIEnv * /*env*/, jobject /*thiz*/,
                                                         jlong ctx_ptr) {
    auto *ctx = reinterpret_cast<whisper_context *>(ctx_ptr);
    if (ctx != nullptr) whisper_free(ctx);
}

// Restituisce il testo come byte UTF-8 (NewStringUTF non gestisce bene emoji/caratteri a 4 byte).
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_othmane_vocaleai_whisper_WhisperLib_transcribe(JNIEnv *env, jobject /*thiz*/,
                                                       jlong ctx_ptr, jfloatArray audio,
                                                       jint n_threads, jstring language,
                                                       jobject listener) {
    auto *ctx = reinterpret_cast<whisper_context *>(ctx_ptr);
    if (ctx == nullptr) return nullptr;

    const char *lang_c = env->GetStringUTFChars(language, nullptr);
    std::string lang(lang_c);
    env->ReleaseStringUTFChars(language, lang_c);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = lang.c_str();
    params.n_threads = n_threads;
    params.no_context = true;
    params.single_segment = false;
    params.suppress_blank = true;

    ProgressCtx pctx;
    if (listener != nullptr) {
        jclass cls = env->GetObjectClass(listener);
        pctx.env = env;
        pctx.listener = listener;
        pctx.method = env->GetMethodID(cls, "onProgress", "(I)V");
        env->DeleteLocalRef(cls);
        if (pctx.method != nullptr) {
            params.progress_callback = on_progress;
            params.progress_callback_user_data = &pctx;
        } else if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
    }

    const jsize n_samples = env->GetArrayLength(audio);
    jfloat *samples = env->GetFloatArrayElements(audio, nullptr);
    const int rc = whisper_full(ctx, params, samples, n_samples);
    env->ReleaseFloatArrayElements(audio, samples, JNI_ABORT);

    if (rc != 0) {
        LOGE("whisper_full ha restituito %d", rc);
        return nullptr;
    }

    std::string out;
    const int n_segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < n_segments; ++i) {
        const char *seg = whisper_full_get_segment_text(ctx, i);
        if (seg != nullptr) out += seg;
    }

    jbyteArray result = env->NewByteArray(static_cast<jsize>(out.size()));
    if (result == nullptr) return nullptr;
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(out.size()),
                            reinterpret_cast<const jbyte *>(out.data()));
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_othmane_vocaleai_whisper_WhisperLib_detectedLanguage(JNIEnv *env, jobject /*thiz*/,
                                                              jlong ctx_ptr) {
    auto *ctx = reinterpret_cast<whisper_context *>(ctx_ptr);
    if (ctx == nullptr) return env->NewStringUTF("");
    const int id = whisper_full_lang_id(ctx);
    const char *code = id >= 0 ? whisper_lang_str(id) : nullptr;
    return env->NewStringUTF(code != nullptr ? code : "");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_othmane_vocaleai_whisper_WhisperLib_systemInfo(JNIEnv *env, jobject /*thiz*/) {
    return env->NewStringUTF(whisper_print_system_info());
}
