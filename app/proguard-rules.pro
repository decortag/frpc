# app/proguard-rules.pro
# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in project's build type.

# You can specify additional options for R8.
# For example, if you use GSON for JSON parsing, you might need:
# -keep class com.google.gson.** { *; }
# -keep class sun.misc.Unsafe { *; }

# Keep all classes in the com.promedia.frcclient package
-keep class com.promedia.frcclient.** { *; }

# Keep all methods and fields of the BootReceiver and FRPService
-keep class com.promedia.frcclient.BootReceiver { *; }
-keep class com.promedia.frcclient.FRPService { *; }

# Keep members of Parcelable implementations
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# Preserve annotations
-keepattributes Signature, InnerClasses, EnclosingMethod, Exceptions, SourceFile, LineNumberTable, Deprecated, Synthetic, Bridge, RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations, RuntimeVisibleParameterAnnotations, RuntimeInvisibleParameterAnnotations, AnnotationDefault, ClassSignature, MemberClasses, StackMapTable, LocalVariableTable, LocalVariableTypeTable, RuntimeVisibleTypeAnnotations, RuntimeInvisibleTypeAnnotations
