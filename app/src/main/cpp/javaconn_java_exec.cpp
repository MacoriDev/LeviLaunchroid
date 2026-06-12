#include <jni.h>
#include <dlfcn.h>
#include <dirent.h>
#include <sys/stat.h>
#include <unistd.h>

#include <algorithm>
#include <cstring>
#include <iostream>
#include <optional>
#include <sstream>
#include <string>
#include <vector>

#ifndef JNI_VERSION_1_8
#define JNI_VERSION_1_8 0x00010008
#endif

static bool fileExists(const std::string& p) {
    struct stat st{};
    return stat(p.c_str(), &st) == 0 && S_ISREG(st.st_mode);
}

static bool dirExists(const std::string& p) {
    struct stat st{};
    return stat(p.c_str(), &st) == 0 && S_ISDIR(st.st_mode);
}

static std::optional<std::string> findFileRecursive(const std::string& dir, const std::string& name, int depth) {
    if (depth < 0) return std::nullopt;
    DIR* d = opendir(dir.c_str());
    if (!d) return std::nullopt;
    std::vector<std::string> children;
    while (dirent* e = readdir(d)) {
        if (std::strcmp(e->d_name, ".") == 0 || std::strcmp(e->d_name, "..") == 0) continue;
        std::string p = dir + "/" + e->d_name;
        if (std::strcmp(e->d_name, name.c_str()) == 0 && fileExists(p)) {
            closedir(d);
            return p;
        }
        if (dirExists(p)) children.push_back(p);
    }
    closedir(d);
    std::sort(children.begin(), children.end());
    for (const auto& child : children) {
        if (auto r = findFileRecursive(child, name, depth - 1)) return r;
    }
    return std::nullopt;
}

static std::string joinPathList(const std::vector<std::string>& v) {
    std::string out;
    for (const auto& s : v) {
        if (!dirExists(s)) continue;
        if (!out.empty()) out += ':';
        out += s;
    }
    return out;
}

static bool startsWith(const std::string& s, const char* p) {
    return s.rfind(p, 0) == 0;
}

static bool printJniException(JNIEnv* env, const char* where) {
    if (!env || !env->ExceptionCheck()) return false;
    std::cerr << "JavaConnect wrapper JNI exception at " << where << std::endl;
    env->ExceptionDescribe();
    env->ExceptionClear();
    return true;
}

static jobjectArray makeStringArray(JNIEnv* env, const std::vector<std::string>& args) {
    jclass stringClass = env->FindClass("java/lang/String");
    if (!stringClass || printJniException(env, "FindClass java/lang/String")) return nullptr;
    jobjectArray arr = env->NewObjectArray(static_cast<jsize>(args.size()), stringClass, nullptr);
    if (!arr || printJniException(env, "NewObjectArray")) return nullptr;
    for (jsize i = 0; i < static_cast<jsize>(args.size()); ++i) {
        jstring s = env->NewStringUTF(args[static_cast<size_t>(i)].c_str());
        if (!s || printJniException(env, "NewStringUTF")) return nullptr;
        env->SetObjectArrayElement(arr, i, s);
        env->DeleteLocalRef(s);
        if (printJniException(env, "SetObjectArrayElement")) return nullptr;
    }
    return arr;
}

int main(int argc, char** argv) {
    const char* homeEnv = std::getenv("JAVA_HOME");
    if (!homeEnv || !*homeEnv) homeEnv = std::getenv("JAVACONNECT_JAVA_HOME");
    if (!homeEnv || !*homeEnv) {
        std::cerr << "JavaConnect wrapper: JAVA_HOME is not set" << std::endl;
        return 127;
    }
    std::string javaHome = homeEnv;
    std::cerr << "JavaConnect wrapper starting" << std::endl;
    std::cerr << "JAVA_HOME=" << javaHome << std::endl;

    std::vector<std::string> libDirs = {
        javaHome + "/lib",
        javaHome + "/lib/server",
        javaHome + "/lib/jli",
        javaHome + "/lib/aarch64",
        javaHome + "/lib/aarch64/server",
        javaHome + "/lib/aarch64/jli",
        "/sdcard/games/JavaConnect/lib"
    };
    std::string ld = joinPathList(libDirs);
    const char* oldLd = std::getenv("LD_LIBRARY_PATH");
    if (oldLd && *oldLd) ld += std::string(":") + oldLd;
    setenv("LD_LIBRARY_PATH", ld.c_str(), 1);
    setenv("JAVA_HOME", javaHome.c_str(), 1);

    std::string libjvm = javaHome + "/lib/server/libjvm.so";
    if (!fileExists(libjvm)) libjvm = javaHome + "/lib/aarch64/server/libjvm.so";
    if (!fileExists(libjvm)) {
        if (auto found = findFileRecursive(javaHome, "libjvm.so", 6)) libjvm = *found;
    }
    if (!fileExists(libjvm)) {
        std::cerr << "JavaConnect wrapper: libjvm.so not found under " << javaHome << std::endl;
        return 127;
    }
    std::cerr << "libjvm=" << libjvm << std::endl;

    void* h = dlopen(libjvm.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (!h) {
        std::cerr << "JavaConnect wrapper: dlopen libjvm failed: " << dlerror() << std::endl;
        return 127;
    }
    using CreateJavaVMFn = jint (*)(JavaVM**, void**, void*);
    auto createJvm = reinterpret_cast<CreateJavaVMFn>(dlsym(h, "JNI_CreateJavaVM"));
    if (!createJvm) {
        std::cerr << "JavaConnect wrapper: dlsym JNI_CreateJavaVM failed: " << dlerror() << std::endl;
        return 127;
    }

    std::vector<std::string> vmOptStrings;
    std::vector<std::string> appArgs;
    std::string jar;

    for (int i = 1; i < argc; ++i) {
        std::string a = argv[i] ? argv[i] : "";
        if (a == "-jar" && i + 1 < argc) {
            jar = argv[++i];
            for (++i; i < argc; ++i) appArgs.emplace_back(argv[i] ? argv[i] : "");
            break;
        }
        if (startsWith(a, "-X") || startsWith(a, "-D") || startsWith(a, "--")) {
            vmOptStrings.push_back(a);
        } else {
            // A class-name launch is not used by JavaConnect, but keep it readable.
            if (jar.empty()) jar = a;
            for (++i; i < argc; ++i) appArgs.emplace_back(argv[i] ? argv[i] : "");
            break;
        }
    }

    if (jar.empty()) {
        const char* envJar = std::getenv("JAVACONNECT_JAR");
        if (envJar && *envJar) jar = envJar;
    }
    if (jar.empty() || !fileExists(jar)) {
        std::cerr << "JavaConnect wrapper: ViaProxy jar missing: " << jar << std::endl;
        return 127;
    }

    auto addOpt = [&](const std::string& opt) {
        if (std::find(vmOptStrings.begin(), vmOptStrings.end(), opt) == vmOptStrings.end()) vmOptStrings.push_back(opt);
    };
    addOpt(std::string("-Djava.home=") + javaHome);
    addOpt(std::string("-Djava.class.path=") + jar);
    addOpt(std::string("-Djava.library.path=") + ld);
    addOpt("-Djava.awt.headless=true");
    addOpt("-Duser.home=/sdcard/games/JavaConnect");
    addOpt("-Djava.io.tmpdir=/sdcard/games/JavaConnect/tmp");
    addOpt(std::string("-Dsun.java.command=") + jar);
    addOpt("--add-opens=java.base/sun.launcher=ALL-UNNAMED");

    std::vector<JavaVMOption> options;
    options.reserve(vmOptStrings.size());
    for (auto& s : vmOptStrings) {
        JavaVMOption o{};
        o.optionString = const_cast<char*>(s.c_str());
        options.push_back(o);
    }

    JavaVMInitArgs vmArgs{};
    vmArgs.version = JNI_VERSION_1_8;
    vmArgs.nOptions = static_cast<jint>(options.size());
    vmArgs.options = options.data();
    vmArgs.ignoreUnrecognized = JNI_TRUE;

    JavaVM* vm = nullptr;
    JNIEnv* env = nullptr;
    std::cerr << "calling JNI_CreateJavaVM" << std::endl;
    jint rc = createJvm(&vm, reinterpret_cast<void**>(&env), &vmArgs);
    if (rc != JNI_OK || !vm || !env) {
        std::cerr << "JavaConnect wrapper: JNI_CreateJavaVM failed rc=" << static_cast<int>(rc) << std::endl;
        return 127;
    }
    std::cerr << "JNI_CreateJavaVM OK" << std::endl;

    jclass helper = env->FindClass("sun/launcher/LauncherHelper");
    if (!helper || printJniException(env, "FindClass sun/launcher/LauncherHelper")) return 127;
    jmethodID checkAndLoadMain = env->GetStaticMethodID(helper, "checkAndLoadMain", "(ZILjava/lang/String;)Ljava/lang/Class;");
    if (!checkAndLoadMain || printJniException(env, "GetStaticMethodID checkAndLoadMain")) return 127;

    jstring jarString = env->NewStringUTF(jar.c_str());
    constexpr jint LM_JAR = 2;
    jobject mainClassObj = env->CallStaticObjectMethod(helper, checkAndLoadMain, JNI_TRUE, LM_JAR, jarString);
    if (!mainClassObj || printJniException(env, "LauncherHelper.checkAndLoadMain")) return 127;

    auto mainClass = reinterpret_cast<jclass>(mainClassObj);
    jmethodID mainMethod = env->GetStaticMethodID(mainClass, "main", "([Ljava/lang/String;)V");
    if (!mainMethod || printJniException(env, "GetStaticMethodID main")) return 127;

    jobjectArray mainArgs = makeStringArray(env, appArgs);
    if (!mainArgs) return 127;

    std::cerr << "calling ViaProxy main from wrapper" << std::endl;
    env->CallStaticVoidMethod(mainClass, mainMethod, mainArgs);
    if (printJniException(env, "CallStaticVoidMethod main")) return 1;

    std::cerr << "ViaProxy main returned" << std::endl;
    if (vm) vm->DestroyJavaVM();
    return 0;
}
