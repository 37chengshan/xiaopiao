# sherpa-onnx reads its Kotlin config classes' fields from native code (JNI GetFieldID), so names must survive R8.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# ML Kit finds its component registrars by name and calls the no-arg constructor (ComponentDiscovery).
-keep class com.google.mlkit.**.*Registrar { <init>(); }
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
