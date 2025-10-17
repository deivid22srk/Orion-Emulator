# Orion Emulator

Android application for running Windows applications with Wine and Box86/Box64, built with Kotlin and Jetpack Compose Material 3.

## Features

- 🎮 Run Windows applications and games on Android
- 🍷 Wine integration for Windows compatibility
- 📦 Box86/Box64 support for x86/x86_64 emulation
- 🎨 Modern Material 3 UI with Jetpack Compose
- 🎯 Virtual and physical controller support
- 🖼️ OpenGL rendering with VirGL
- 🔊 Audio support via PulseAudio/ALSA
- 📱 Android 8.0+ support (API 26+)

## Project Structure

This project is a port of [Winlator-Ludashi](https://github.com/StevenMXZ/Winlator-Ludashi) with a modern Kotlin and Compose-based UI.

### Technology Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Design System**: Material 3 (Material You)
- **Native Code**: C/C++ (CMake)
- **Build System**: Gradle 8.8.0
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)

## Building

### Prerequisites

- Android Studio Hedgehog or newer
- JDK 17
- NDK 27.0.12077973
- CMake 3.22.1+

### Build Instructions

1. Clone the repository:
```bash
git clone https://github.com/deivid22srk/Orion-Emulator.git
cd Orion-Emulator
```

2. Build the project:
```bash
./gradlew assembleDebug
```

The APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`

### CI/CD

This project uses GitHub Actions for continuous integration. On every push to `main` or pull request, the project is automatically built and the APK is uploaded as an artifact.

## Dependencies

### Kotlin & Compose
- Jetpack Compose BOM 2024.09.00
- Material 3 1.2.0
- Navigation Compose
- Activity Compose
- Lifecycle ViewModel Compose

### Core Libraries
- Wine (bundled)
- Box64/Box86 (bundled)
- VirGL Renderer
- PulseAudio
- Adrenotools

### Networking
- Retrofit 2.11.0
- OkHttp 4.12.0
- Gson 2.11.0

### Compression
- Zstd JNI
- XZ Utils
- Apache Commons Compress

### Image Loading
- Coil Compose
- Glide

## Architecture

The app follows modern Android architecture principles:

- **UI Layer**: Jetpack Compose with Material 3
- **Navigation**: Navigation Compose for screen navigation
- **State Management**: ViewModel and State hoisting
- **Native Layer**: JNI bindings to C/C++ code

### Main Screens

1. **Containers**: Manage Windows containers
2. **Contents**: Download Wine, Box64, and components
3. **Shortcuts**: Quick access to Windows applications
4. **Controls**: Configure virtual and physical controllers
5. **Settings**: App configuration and preferences

## Native Components

The project includes several native components:

- **XServer**: X11 server implementation for Android
- **VirGL Renderer**: OpenGL renderer
- **Wine Handler**: Wine process management
- **Input Controls**: Touch and controller input handling
- **Audio Plugin**: ALSA/PulseAudio integration
- **SysV Shared Memory**: Shared memory support

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Credits

- Based on [Winlator-Ludashi](https://github.com/StevenMXZ/Winlator-Ludashi)
- Original Winlator by [Pipetto-crypto](https://github.com/Pipetto-crypto/winlator)
- Wine Project
- Box86/Box64 Projects
- VirGL Project

## Disclaimer

This is an experimental project. Compatibility and performance may vary depending on your device and the Windows application you're trying to run.
