package com.example.ph232

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Helper class for Cloudinary image upload/download.
 * Uses direct HTTP API with unsigned uploads.
 * Cloud name: dflbohx2f
 * Upload preset: ph232_profile
 */
object CloudinaryHelper {

    private const val TAG = "CloudinaryHelper"
    private const val CLOUD_NAME = "dflbohx2f"
    private const val UPLOAD_PRESET = "ph232_profile"
    private val mainHandler = Handler(Looper.getMainLooper())

    fun init(context: Context) {
        // No SDK initialization needed - using direct HTTP API
        Log.d(TAG, "CloudinaryHelper ready (HTTP mode)")
    }

    /**
     * Upload a bitmap to Cloudinary via direct HTTP API.
     */
    fun uploadProfileImage(
        context: Context,
        bitmap: Bitmap,
        userId: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        // Save bitmap to temp file
        val tempFile = File(context.cacheDir, "temp_profile_upload.jpg")
        try {
            val fos = FileOutputStream(tempFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
            fos.flush()
            fos.close()
        } catch (e: Exception) {
            onError("Failed to prepare image: ${e.message}")
            return
        }

        Thread {
            try {
                val uploadUrl = URL("https://api.cloudinary.com/v1_1/$CLOUD_NAME/image/upload")
                val boundary = "----FormBoundary${System.currentTimeMillis()}"
                val connection = uploadUrl.openConnection() as HttpURLConnection
                connection.doOutput = true
                connection.doInput = true
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                connection.connectTimeout = 30000
                connection.readTimeout = 30000

                val outputStream = connection.outputStream

                // upload_preset field (only parameter allowed with unsigned uploads)
                writeFormField(outputStream, boundary, "upload_preset", UPLOAD_PRESET)


                // file field
                outputStream.write("--$boundary\r\n".toByteArray())
                outputStream.write("Content-Disposition: form-data; name=\"file\"; filename=\"profile.jpg\"\r\n".toByteArray())
                outputStream.write("Content-Type: image/jpeg\r\n\r\n".toByteArray())
                outputStream.write(tempFile.readBytes())
                outputStream.write("\r\n".toByteArray())

                // End boundary
                outputStream.write("--$boundary--\r\n".toByteArray())
                outputStream.flush()
                outputStream.close()

                val responseCode = connection.responseCode
                Log.d(TAG, "Cloudinary response code: $responseCode")

                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    Log.d(TAG, "Cloudinary response: $response")

                    // Parse secure_url from JSON
                    val secureUrl = Regex("\"secure_url\"\\s*:\\s*\"([^\"]+)\"")
                        .find(response)?.groupValues?.get(1)
                        ?.replace("\\/", "/") ?: ""

                    if (secureUrl.isNotEmpty()) {
                        // Save URL locally
                        val prefs = context.getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
                        prefs.edit().putString("PROFILE_IMAGE_URL", secureUrl).apply()

                        mainHandler.post { onSuccess(secureUrl) }
                    } else {
                        mainHandler.post { onError("Could not parse upload URL from response") }
                    }
                } else {
                    val errorBody = try {
                        connection.errorStream?.bufferedReader()?.readText() ?: "No error body"
                    } catch (e: Exception) { "Could not read error" }
                    Log.e(TAG, "Upload failed ($responseCode): $errorBody")
                    mainHandler.post { onError("Upload failed ($responseCode): $errorBody") }
                }

                connection.disconnect()
                tempFile.delete()

            } catch (e: Exception) {
                Log.e(TAG, "Upload exception: ${e.message}", e)
                tempFile.delete()
                mainHandler.post { onError("Upload error: ${e.message}") }
            }
        }.start()
    }

    private fun writeFormField(outputStream: java.io.OutputStream, boundary: String, name: String, value: String) {
        outputStream.write("--$boundary\r\n".toByteArray())
        outputStream.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
        outputStream.write("$value\r\n".toByteArray())
    }

    /**
     * Download a profile image from a URL and cache locally.
     */
    fun downloadProfileImage(
        context: Context,
        imageUrl: String,
        onSuccess: (Bitmap) -> Unit,
        onError: (String) -> Unit
    ) {
        Thread {
            try {
                val url = URL(imageUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.doInput = true
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.connect()

                val inputStream: InputStream = connection.inputStream
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                connection.disconnect()

                if (bitmap != null) {
                    // Cache locally
                    val localFile = ProfileDialog.getProfileImageFile(context)
                    val fos = FileOutputStream(localFile)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                    fos.flush()
                    fos.close()

                    mainHandler.post { onSuccess(bitmap) }
                } else {
                    mainHandler.post { onError("Failed to decode image") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error: ${e.message}", e)
                mainHandler.post { onError("Download error: ${e.message}") }
            }
        }.start()
    }

    /**
     * Get the saved Cloudinary profile image URL.
     */
    fun getSavedProfileUrl(context: Context): String? {
        val prefs = context.getSharedPreferences("PH232_PREFS", Context.MODE_PRIVATE)
        return prefs.getString("PROFILE_IMAGE_URL", null)
    }
}
