# sherpa-onnx JNI surface.
# Native code resolves these classes, their fields and their constructors by name;
# R8 strips them silently and the failure only shows up in a release build.
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclassmembers class com.k2fsa.sherpa.onnx.** { native <methods>; }
