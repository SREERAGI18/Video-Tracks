# Video Exif

An Android application that records video while simultaneously tracking and embedding high-frequency GPS metadata. It allows users to playback recordings with a synchronized map view, showing exactly where they were at every second of the video.

## 🚀 Features

- **Video Recording**: High-quality video recording using Android's Camera2 API.
- **GPS Tracking**: Real-time location tracking during recording (1Hz updates).
- **Synchronized Playback**: Integrated video player and Google Maps view that stays in sync during playback.
- **GPX Support**: Metadata is saved in the standard GPX format with custom extensions for video time-offsets.
- **External Integration**: Open recorded tracks in Google Maps or share GPX files with other mapping applications.
- **Clean Architecture**: Built with modern Android development practices, following Clean Architecture and MVI (Model-View-Intent).

## 🛠 Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: Clean Architecture + MVI
- **Camera**: Camera2 API
- **Location**: Google Play Services Location
- **Maps**: Google Maps SDK for Android (Compose Library)
- **Video Playback**: Media3 ExoPlayer
- **Serialization**: Kotlinx Serialization (JSON & XML Parsing)
- **Dependency Injection**: Manual DI (Refactored for simplicity)

## 🏗 Architecture

The project follows the principles of Clean Architecture, separated into three main layers:

### 1. Domain Layer
Contains the core business logic and entities.
- **Models**: `LocationPoint`, `VideoData`, `VideoMetadata`.
- **Repository Interface**: `VideoRepository`.

### 2. Data Layer
Handles data persistence and hardware interactions.
- **Repository Implementation**: `VideoRepositoryImpl`.
- **DataSources**: `VideoRecorder` (Camera2), `LocationTracker` (FusedLocationProvider).
- **Storage**: MediaStore for videos and public Documents for GPX metadata.

### 3. Presentation Layer
Implements the MVI pattern using Compose and ViewModels.
- **Record**: Camera preview and recording controls.
- **Gallery**: List of recorded videos with associated metadata.
- **Playback**: Synchronized ExoPlayer and Google Map view.

## 📸 Screenshots

*(Add screenshots here after running the app)*

## 🚦 Getting Started

### Prerequisites
- Android Studio Ladybug or newer.
- Android Device/Emulator running API 24 (Nougat) or higher.
- A Google Maps API Key.

### Installation
1. Clone the repository.
2. Add your Google Maps API Key to `local.properties` or `AndroidManifest.xml`.
3. Sync the project with Gradle.
4. Run the app on your device.

## 🛡 Permissions

The app requires the following permissions:
- `CAMERA`: For video recording.
- `RECORD_AUDIO`: For audio in videos.
- `ACCESS_FINE_LOCATION`: For high-accuracy GPS tracking.
- `READ_EXTERNAL_STORAGE` / `READ_MEDIA_VIDEO`: To access the gallery.

## 📄 License

This project is licensed under the MIT License.
