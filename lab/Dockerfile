FROM eclipse-temurin:21-jdk-jammy

ENV DEBIAN_FRONTEND=noninteractive \
    ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_USER_HOME=/gradle

RUN apt-get update && apt-get install -y --no-install-recommends \
      curl unzip git ca-certificates \
      libpulse0 libgl1 libnss3 libxcursor1 libxi6 libxtst6 libasound2 \
      cpu-checker qemu-kvm \
      python3 file zip \
    && rm -rf /var/lib/apt/lists/*

# Android command line tools
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && cd /tmp \
    && curl -fsSL -o cl.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
    && unzip -q cl.zip -d ${ANDROID_HOME}/cmdline-tools \
    && mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
    && rm cl.zip

ENV PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/emulator

RUN yes | sdkmanager --licenses > /dev/null 2>&1 || true

# SDK base. Las platforms/build-tools concretas se afinan tras inspeccionar el proyecto.
RUN sdkmanager --install \
      "platform-tools" \
      "platforms;android-36" \
      "platforms;android-35" \
      "build-tools;36.0.0" \
      "emulator" \
    && yes | sdkmanager --licenses > /dev/null 2>&1 || true

WORKDIR /work
CMD ["/bin/bash"]

# --- capa 2: SDK que exige el proyecto (compileSdk 37, AGP 9.2.1) + emuladores Wear ---
RUN sdkmanager --install \
      "platforms;android-37.0" \
      "build-tools;37.0.0" \
      "system-images;android-34;android-wear;x86_64" \
      "system-images;android-35-ext15;android-wear;x86_64" \
    && yes | sdkmanager --licenses > /dev/null 2>&1 || true

RUN echo no | avdmanager create avd -n wear34 \
      -k "system-images;android-34;android-wear;x86_64" -d "wearos_small_round" \
    && echo no | avdmanager create avd -n wear35 \
      -k "system-images;android-35-ext15;android-wear;x86_64" -d "wearos_small_round"
