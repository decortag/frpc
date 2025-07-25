@rem
@rem Copyright 2010 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@rem Determine the Java command to use to start the JVM.
@set JAVACMD="%JAVA_HOME%\bin\java.exe"
@if not exist %JAVACMD% set JAVACMD=java.exe

@rem Determine the script directory.
@for %%i in (%0) do @set APP_HOME=%%~dpi
@set APP_HOME=%APP_HOME:~0,-1%

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options.
@set DEFAULT_JVM_OPTS=-Xmx2048m -Dfile.encoding=UTF-8

@rem The project's build file name
@set GRADLE_BUILD_FILE=build.gradle

@rem The Gradle executable name
@set GRADLE_EXECUTABLE=gradle

@rem --- Run the Gradle Wrapper ---
@rem This script will download and run the appropriate Gradle distribution.
"%JAVACMD%" %DEFAULT_JVM_OPTS% -classpath "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
