package com.example.videoexif

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.Surface
import java.io.File
import java.io.FileDescriptor

class VideoRecorder(private val context: Context) {
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var mediaRecorder: MediaRecorder? = null
    
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").apply { start() }
        backgroundHandler = backgroundThread?.let { Handler(it.looper) }
    }

    fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e("VideoRecorder", "Interrupted stopping background thread", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun openCamera(onOpened: () -> Unit) {
        val cameraId = getCameraId() ?: return
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                onOpened()
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                cameraDevice = null
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.e("VideoRecorder", "Camera error: $error")
                camera.close()
                cameraDevice = null
            }
        }, backgroundHandler)
    }

    fun closeCamera() {
        stopRecording()
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        mediaRecorder?.release()
        mediaRecorder = null
    }

    fun startPreview(surfaceTexture: SurfaceTexture) {
        val camera = cameraDevice ?: return
        surfaceTexture.setDefaultBufferSize(1920, 1080)
        val previewSurface = Surface(surfaceTexture)
        
        val previewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        previewRequestBuilder.addTarget(previewSurface)

        camera.createCaptureSession(listOf(previewSurface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                try {
                    session.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler)
                } catch (e: Exception) {
                    Log.e("VideoRecorder", "Failed to start preview", e)
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.e("VideoRecorder", "Preview session configuration failed")
            }
        }, backgroundHandler)
    }

    fun startRecording(pfd: FileDescriptor, surfaceTexture: SurfaceTexture) {
        try {
            val camera = cameraDevice ?: throw IllegalStateException("Camera device is null")
            captureSession?.close()
            
            if (!setupMediaRecorder(pfd, 1920, 1080)) {
                throw IllegalStateException("Failed to setup MediaRecorder")
            }
            
            val previewSurface = Surface(surfaceTexture)
            val recordSurface = mediaRecorder?.surface ?: throw IllegalStateException("MediaRecorder surface is null")

            val recordRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            recordRequestBuilder.addTarget(previewSurface)
            recordRequestBuilder.addTarget(recordSurface)

            camera.createCaptureSession(listOf(previewSurface, recordSurface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    recordRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                    try {
                        session.setRepeatingRequest(recordRequestBuilder.build(), null, backgroundHandler)
                        mediaRecorder?.start()
                    } catch (e: Exception) {
                        Log.e("VideoRecorder", "Failed to start recording session", e)
                        cleanupRecording()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e("VideoRecorder", "Recording session configuration failed")
                    cleanupRecording()
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e("VideoRecorder", "Error starting recording", e)
            cleanupRecording()
            throw e
        }
    }
    
    private fun cleanupRecording() {
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
        try {
            mediaRecorder?.reset()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    fun stopRecording() {
        try {
            mediaRecorder?.let { recorder ->
                // Check if media recorder is recording before stopping
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // On API 26+, we can check the state more reliably
                    recorder.stop()
                } else {
                    // On older APIs, just try to stop
                    recorder.stop()
                }
            }
        } catch (e: IllegalStateException) {
            Log.e("VideoRecorder", "MediaRecorder not in recording state", e)
        } catch (e: RuntimeException) {
            Log.e("VideoRecorder", "MediaRecorder stop failed - possibly no valid audio/video data", e)
        } catch (e: Exception) {
            Log.e("VideoRecorder", "Unexpected error stopping recording", e)
        } finally {
            try {
                mediaRecorder?.reset()
            } catch (e: Exception) {
                Log.e("VideoRecorder", "Error resetting MediaRecorder", e)
            }
        }
    }

    private fun setupMediaRecorder(pfd: FileDescriptor, width: Int, height: Int): Boolean {
        try {
            mediaRecorder?.release()
            mediaRecorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(pfd)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setVideoSize(width, height)
                setVideoFrameRate(30)
                setVideoEncodingBitRate(10000000)
                prepare()
            }
            return true
        } catch (e: Exception) {
            Log.e("VideoRecorder", "Setup MediaRecorder failed", e)
            return false
        }
    }
    
    private fun getCameraId(): String? {
        return cameraManager.cameraIdList.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    }
}
