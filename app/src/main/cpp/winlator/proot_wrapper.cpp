#include <jni.h>
#include <string>
#include <vector>
#include <unistd.h>
#include <android/log.h>
#include <sys/wait.h>

#define TAG "PRoot_Wrapper"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" JNIEXPORT jint JNICALL
Java_com_winlator_orion_core_PRoot_execPRoot(
    JNIEnv* env,
    jobject /* this */,
    jobjectArray args,
    jobjectArray envVars,
    jstring workDir) {
    
    // Convert Java strings to C++ strings
    int argc = env->GetArrayLength(args);
    std::vector<char*> argv(argc + 1);
    std::vector<std::string> argStrings(argc);
    
    for (int i = 0; i < argc; i++) {
        jstring jstr = (jstring)env->GetObjectArrayElement(args, i);
        const char* str = env->GetStringUTFChars(jstr, nullptr);
        argStrings[i] = str;
        argv[i] = const_cast<char*>(argStrings[i].c_str());
        env->ReleaseStringUTFChars(jstr, str);
    }
    argv[argc] = nullptr;
    
    LOGD("Executing PRoot with %d arguments", argc);
    for (int i = 0; i < argc; i++) {
        LOGD("  argv[%d] = %s", i, argv[i]);
    }
    
    // Set environment variables
    if (envVars != nullptr) {
        int envCount = env->GetArrayLength(envVars);
        for (int i = 0; i < envCount; i++) {
            jstring jstr = (jstring)env->GetObjectArrayElement(envVars, i);
            const char* envPair = env->GetStringUTFChars(jstr, nullptr);
            
            // Parse "KEY=VALUE"
            std::string envStr(envPair);
            size_t pos = envStr.find('=');
            if (pos != std::string::npos) {
                std::string key = envStr.substr(0, pos);
                std::string value = envStr.substr(pos + 1);
                setenv(key.c_str(), value.c_str(), 1);
                LOGD("Set env: %s=%s", key.c_str(), value.c_str());
            }
            
            env->ReleaseStringUTFChars(jstr, envPair);
        }
    }
    
    // Change working directory
    if (workDir != nullptr) {
        const char* workDirStr = env->GetStringUTFChars(workDir, nullptr);
        if (chdir(workDirStr) != 0) {
            LOGE("Failed to change directory to: %s", workDirStr);
        } else {
            LOGD("Changed directory to: %s", workDirStr);
        }
        env->ReleaseStringUTFChars(workDir, workDirStr);
    }
    
    // Fork and exec
    pid_t pid = fork();
    
    if (pid == 0) {
        // Child process
        execvp(argv[0], argv.data());
        
        // If execvp returns, it failed
        LOGE("execvp failed: %s", strerror(errno));
        _exit(127);
    } else if (pid > 0) {
        // Parent process
        LOGD("Forked process with PID: %d", pid);
        return pid;
    } else {
        // Fork failed
        LOGE("fork failed: %s", strerror(errno));
        return -1;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_winlator_orion_core_PRoot_waitForProcess(
    JNIEnv* env,
    jobject /* this */,
    jint pid) {
    
    int status;
    pid_t result = waitpid(pid, &status, 0);
    
    if (result == -1) {
        LOGE("waitpid failed: %s", strerror(errno));
        return -1;
    }
    
    if (WIFEXITED(status)) {
        int exitCode = WEXITSTATUS(status);
        LOGD("Process %d exited with code: %d", pid, exitCode);
        return exitCode;
    } else if (WIFSIGNALED(status)) {
        int signal = WTERMSIG(status);
        LOGE("Process %d killed by signal: %d", pid, signal);
        return -signal;
    }
    
    return -1;
}
