#!/usr/bin/env sh

##############################################################################
##
##  Gradle wrapper script for UNIX
##
##############################################################################

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME environment variable to the root directory of your Java installation."
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command can be found in your PATH.

Please set the JAVA_HOME environment variable to the root directory of your Java installation, or add 'java' to your PATH."
fi

# Determine the script directory.
SCRIPT_DIR=$(dirname "$0")

# Determine the Gradle distribution URL from gradle-wrapper.properties
GRADLE_WRAPPER_PROPERTIES="$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.properties"
if [ -f "$GRADLE_WRAPPER_PROPERTIES" ]; then
    DISTRIBUTION_URL=$(grep 'distributionUrl' "$GRADLE_WRAPPER_PROPERTIES" | cut -d'=' -f2-)
    if [ -n "$DISTRIBUTION_URL" ]; then
        # Extract version from URL (e.g., gradle-8.1.1-bin.zip -> 8.1.1)
        GRADLE_VERSION=$(echo "$DISTRIBUTION_URL" | sed -n 's/.*gradle-\([0-9.]*\)-bin\.zip/\1/p')
    fi
fi

# Fallback if version not found or properties file missing
if [ -z "$GRADLE_VERSION" ]; then
    GRADLE_VERSION="8.1.1" # Default to 8.1.1 if not specified
fi

# Set the GRADLE_HOME environment variable.
APP_HOME=$(cd "$SCRIPT_DIR" && pwd)

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options.
DEFAULT_JVM_OPTS="-Xmx2048m -Dfile.encoding=UTF-8"

# The project's build file name
GRADLE_BUILD_FILE="build.gradle"

# The Gradle executable name
GRADLE_EXECUTABLE="gradle"

# --- Run the Gradle Wrapper ---
# This script will download and run the appropriate Gradle distribution.
exec "$JAVACMD" $DEFAULT_JVM_OPTS -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
