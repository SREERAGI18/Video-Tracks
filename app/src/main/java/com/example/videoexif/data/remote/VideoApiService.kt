package com.example.videoexif.data.remote

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface VideoApiService {
    @Multipart
    @POST("upload/gpx")
    suspend fun uploadGpx(
        @Part file: MultipartBody.Part
    ): Response<Unit>

    @Multipart
    @POST("upload/srt")
    suspend fun uploadSrt(
        @Part file: MultipartBody.Part
    ): Response<Unit>

    @Multipart
    @POST("upload/mp4")
    suspend fun uploadMp4(
        @Part file: MultipartBody.Part
    ): Response<Unit>

    @Multipart
    @POST("upload/all")
    suspend fun uploadAll(
        @Part gpxFile: MultipartBody.Part,
        @Part srtFile: MultipartBody.Part,
        @Part mp4File: MultipartBody.Part
    ): Response<Unit>
}
