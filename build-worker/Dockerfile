FROM gradle:8.6.0-jdk17

ARG ANDROID_CMDLINE_TOOLS_REV=16111833
ARG ANDROID_CMDLINE_TOOLS_SHA1=e025545c62a8e64c7559119566a569fb1dec5f60

ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV ANDROID_HOME=/opt/android-sdk
ENV GRADLE_USER_HOME=/home/gradle/.gradle

USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip python3 python3-pil \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools" \
    && curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_REV}_latest.zip" \
        -o /tmp/android-commandline-tools.zip \
    && echo "${ANDROID_CMDLINE_TOOLS_SHA1}  /tmp/android-commandline-tools.zip" | sha1sum -c - \
    && mkdir -p /tmp/android-commandline-tools \
    && unzip -q /tmp/android-commandline-tools.zip -d /tmp/android-commandline-tools \
    && mv /tmp/android-commandline-tools/cmdline-tools "${ANDROID_SDK_ROOT}/cmdline-tools/latest" \
    && rm -rf /tmp/android-commandline-tools /tmp/android-commandline-tools.zip \
    && yes | "${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager" --sdk_root="${ANDROID_SDK_ROOT}" --licenses \
    && "${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager" --sdk_root="${ANDROID_SDK_ROOT}" \
        "platform-tools" "platforms;android-34" "build-tools;34.0.0" \
    && chown -R gradle:gradle "${ANDROID_SDK_ROOT}" /home/gradle

COPY --chown=gradle:gradle . /opt/factori-template
USER gradle
WORKDIR /opt/factori-template
RUN ./gradlew --no-daemon :app:assembleDebug
RUN rm -rf /opt/factori-template
WORKDIR /workspace
