package com.example.passportreader

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.example.passportreader.databinding.ActivityMainBinding
import com.example.passportreader.models.PassportInfo
import com.example.passportreader.utils.Utils
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.Date

@RequiresApi(Build.VERSION_CODES.O)
class MainActivity : AppCompatActivity() {

    private lateinit var imageCapture: ImageCapture

    private lateinit var binding: ActivityMainBinding

    private var didGetPassInfo = false

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var runnable: Runnable

    companion object {
        private const val REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        setContentView(binding.root)

        // Check for camera permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA), REQUEST_CODE
            )
        }

        // Setup capture button
        val captureButton: Button = findViewById(R.id.captureButton)
        captureButton.setOnClickListener {
            didGetPassInfo = false
            binding.firstInfoLine.visibility = View.INVISIBLE
            binding.secondInfoLine.visibility = View.INVISIBLE
            binding.passValidation.setTextColor(Color.TRANSPARENT)
        }


        imageCapture = ImageCapture.Builder().build()
        startCamera()

        runnable = Runnable {
            if (!didGetPassInfo) {
                binding.length.setBackgroundColor(Color.TRANSPARENT)
                binding.length.setTextColor(Color.TRANSPARENT)
                takePicture()
            }
            handler.postDelayed(runnable, 1000)
        }

        // Start the first execution
        handler.post(runnable)
    }

    override fun onDestroy() {
        super.onDestroy()

        // Stop the runnable when activity is destroyed
        handler.removeCallbacks(runnable)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePicture() {
        val file = File(externalMediaDirs.first(), "${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val inputImage = InputImage.fromFilePath(this@MainActivity, Uri.fromFile(file))
                    recognizeText(inputImage)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraX", "Image capture failed", exception)
                }
            })
    }

    private fun recognizeText(image: InputImage) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val mrz = extractMRZ(visionText.text) ?: ""
                parseMRZ(mrz)
            }
            .addOnFailureListener { e ->
                Log.e("OCR", "Text recognition failed", e)
            }
    }

    private fun extractMRZ(text: String): String? {
        val lines = text.lines().map { it.trim() }.filter { it.length == 44 }
        return if (lines.size >= 2) {
            lines[lines.size - 2] + lines[lines.size - 1]
        } else {
            null
        }
    }

    private fun parseMRZ(mrz: String) {

        if (didGetPassInfo) return

        if (mrz.length < 88) { // Ensure it's 88 or more characters
            binding.length.setBackgroundColor(Color.RED)
            binding.length.setTextColor(Color.WHITE)
            binding.length.text = "Try Again!"
            return
        } else {
            didGetPassInfo = true
            binding.length.setBackgroundColor(Color.GREEN)
            binding.length.setTextColor(Color.WHITE)
            binding.length.text = "Got it!"
        }

        val line1 = mrz.substring(0, 43)
        val line2 = mrz.substring(44, 87)

        val documentType = line1[0].toString()
        val countryCode = line1.substring(2, 5)
        val surnameAndGivenNames = line1.substring(5).split("<<")
        val surname = surnameAndGivenNames[0].replace("<", " ")
        val givenNames = surnameAndGivenNames.getOrNull(1)?.replace("<", " ") ?: ""

        val passportNumber = line2.substring(0, 9).replace("<", "")
        val nationality = line2.substring(10, 13)
        val dateOfBirth = line2.substring(13, 19)
        val sex = line2[20].toString()
        val expirationDate = line2.substring(21, 27)
        val personalNumber = line2.substring(28, 42).replace("<", "")

        val passportInfo = PassportInfo(
            documentType,
            countryCode,
            surname,
            givenNames,
            passportNumber,
            nationality,
            dateOfBirth,
            sex,
            expirationDate,
            personalNumber
        )

        setViewData(passportInfo)


    }

    private fun setViewData(passportInfo: PassportInfo) {
        val date = Utils.dateFormat(passportInfo.expirationDate ?: "") ?: ""
        if (Utils.isPassExpired(date)) {
            binding.passValidation.setTextColor(Color.GREEN)
            binding.passValidation.text = "Valid Passport"
        } else {
            binding.passValidation.setTextColor(Color.RED)
            binding.passValidation.text = "Expired Passport"
        }

        binding.apply {
            passType.text = "Document Type: ${passportInfo.documentType}"
            country.text = "Country Code: ${passportInfo.countryCode}"
            passNum.text = "Passport Number: ${passportInfo.passportNumber}"
            personSurname.text = "Surname: ${passportInfo.surname}"
            fullName.text = "Full Name: ${passportInfo.givenNames} ${passportInfo.surname}"

            personNationality.text = "Nationality: ${passportInfo.nationality}"
            personDateOfBirth.text =
                "Date Of Birth: ${Utils.dateFormat(passportInfo.dateOfBirth ?: "")}"
            peronSex.text = "Sex: ${passportInfo.sex}"
            personExpirationDate.text = "Expiration Date: ${date}"

            binding.firstInfoLine.visibility = View.VISIBLE
            binding.secondInfoLine.visibility = View.VISIBLE
        }
    }
}

