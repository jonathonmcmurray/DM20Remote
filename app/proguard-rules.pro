# Keep protobuf generated classes.
-keep class com.dm20remote.proto.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
