# Sourced by pixi on environment activation. Sets up the JDK from conda-forge
# and points Gradle at a usable Android SDK.
if [ -x "$CONDA_PREFIX/lib/jvm/bin/java" ]; then
  export JAVA_HOME="$CONDA_PREFIX/lib/jvm"
else
  export JAVA_HOME="$CONDA_PREFIX"
fi

# Prefer, in order: a local.properties sdk.dir, an ANDROID_HOME that actually has
# platform 37, then the default ~/Android/Sdk. Debian's /usr/lib/android-sdk has no
# platforms and is skipped.
_sdk_from_local="$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null | head -n1)"
if [ -n "$_sdk_from_local" ] && [ -n "$(ls -d "$_sdk_from_local"/platforms/android-37* 2>/dev/null)" ]; then
  export ANDROID_HOME="$_sdk_from_local"
elif [ -n "${ANDROID_HOME:-}" ] && [ -n "$(ls -d "$ANDROID_HOME"/platforms/android-37* 2>/dev/null)" ]; then
  :
else
  export ANDROID_HOME="$HOME/Android/Sdk"
fi
unset _sdk_from_local
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
