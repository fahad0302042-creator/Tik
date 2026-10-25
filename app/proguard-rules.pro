# The app is not minified, but keep rules here so the release build stays predictable
# if minification is ever enabled.
-keepclassmembers class com.tiktv.app.MainActivity$Bridge {
    public *;
}
-keepattributes JavascriptInterface
