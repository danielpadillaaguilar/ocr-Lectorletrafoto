
package com.example.ocrapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.ocrapp.databinding.ActivityMainBinding

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.graphics.Color

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var scannedText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        inicializarListeners()
    }

    private fun inicializarListeners() {
        with(binding) {
            icScan.setOnClickListener {
                if (obtenerPermisosLecturaYEscritura()) {
                    contentLauncher.launch("image/*")
                }
            }

            btnCopy.setOnClickListener {
                if (scannedText.isNullOrEmpty()) {
                    mostrarToast("Por favor escanea una imagen")
                } else {
                    copiarAlPortapapeles(scannedText!!)
                    mostrarToast("Texto copiado al portapapeles")
                }
            }

            btnSend.setOnClickListener {
                if (scannedText.isNullOrEmpty()) {
                    mostrarToast("Por favor escanea una imagen")
                } else {
                    compartirTexto(scannedText!!)
                }
            }

            btnSearch.setOnClickListener {
                val palabra = searchEditText.text.toString().trim()
                buscarPalabra(palabra)
            }
        }
    }

    private val contentLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                binding.icScan.setImageURI(uri)
                val photoBitmap = uri.toBitmap(this) ?: return@let
                procesarImagen(photoBitmap)
            }
        }

    private fun procesarImagen(photoBitmap: Bitmap) {
        val image = InputImage.fromBitmap(photoBitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                scannedText = visionText.text
                binding.resultTextView.text = visionText.text
            }
            .addOnFailureListener { e ->
                Log.e("OCR", "Fallo el reconocimiento de texto", e)
                mostrarToast("No se pudo reconocer el texto")
            }
    }

    private fun copiarAlPortapapeles(texto: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Texto OCR", texto)
        clipboard.setPrimaryClip(clip)
    }

    private fun compartirTexto(texto: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, texto)
        }
        startActivity(Intent.createChooser(intent, "Compartir con"))
    }

    private fun mostrarToast(mensaje: String) {
        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
    }

    private fun obtenerPermisosLecturaYEscritura(): Boolean {
        val permisos = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permisos.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            permisos.add(android.Manifest.permission.CAMERA)
        } else {
            permisos.add(android.Manifest.permission.CAMERA)
            permisos.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            permisos.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val permisosFaltantes = permisos.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        return if (permisosFaltantes.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permisosFaltantes.toTypedArray(), 101)
            false
        } else {
            true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                mostrarToast("Permisos concedidos")
            } else {
                mostrarToast("Permisos denegados")
            }
        }
    }

    // 🔍 Método agregado: Búsqueda de palabras
    private fun buscarPalabra(palabra: String) {
        if (scannedText.isNullOrEmpty()) {
            mostrarToast("No hay texto escaneado")
            return
        }

        if (palabra.isEmpty()) {
            mostrarToast("Escriba una palabra para buscar")
            return
        }

        val texto = scannedText!!
        val spannable = SpannableString(texto)

        var index = texto.indexOf(palabra, ignoreCase = true)
        if (index == -1) {
            mostrarToast("Palabra no encontrada")
        }

        while (index >= 0) {
            spannable.setSpan(
                BackgroundColorSpan(Color.YELLOW),
                index,
                index + palabra.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            index = texto.indexOf(palabra, startIndex = index + palabra.length, ignoreCase = true)
        }

        binding.resultTextView.text = spannable
    }
}

// Función de extensión Uri.toBitmap()
fun Uri.toBitmap(context: Context): Bitmap? {
    return try {
        context.contentResolver.openInputStream(this)?.use { inputStream ->
            android.graphics.BitmapFactory.decodeStream(inputStream)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun View.visible() {
    visibility = View.VISIBLE
}

fun View.gone() {
    visibility = View.GONE
}
