package com.example.shoppingapp

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.example.shoppingapp.data.Product
import com.example.shoppingapp.ui.theme.ShoppingAppTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.gson.Gson
import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.shoppingapp.data.ShoppingDatabase
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.pm.PackageManager
import androidx.camera.core.ExperimentalGetImage
import androidx.compose.ui.viewinterop.AndroidView
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import android.widget.Toast
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class QRCodeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = ShoppingDatabase.getDatabase(applicationContext)

        setContent {
            ShoppingAppTheme {
                QRCodeScreenWithDB(db)
            }
        }
    }
}

@Composable
fun QRCodeScreenWithDB(db: ShoppingDatabase) {
    var productList by remember { mutableStateOf<List<Product>>(emptyList()) }

    // Pobieranie danych z bazy
    LaunchedEffect(Unit) {
        productList = withContext(Dispatchers.IO) {
            db.shoppingDao().getAllProducts()
        }
    }

    QRCodeScreen(products = productList)
}

@Composable
fun QRCodeScreen(products: List<Product>) {
    val gson = remember { Gson() }
    val json = remember(products) { gson.toJson(products) }
    val qrBitmap = remember(json) { generateQRCode(json) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Zeskanuj ten kod, aby dodać produkty", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))
        qrBitmap?.let {
            Image(bitmap = it.asImageBitmap(), contentDescription = "QR Code")
        } ?: Text("Nie udało się wygenerować kodu QR")
    }
}

fun generateQRCode(text: String, size: Int = 512): Bitmap? {
    return try {
        val bitMatrix: BitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        Log.e("QR", "Błąd generowania kodu QR", e)
        null
    }
}
class QRCodeScannerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ShoppingAppTheme {
                QRScannerScreen()
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun QRScannerScreen() {
    val context = LocalContext.current
    val db = remember { ShoppingDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {

        Text("Zeskanuj kod QR z produktami", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))

        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = androidx.camera.core.Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val options = BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                            .build()

                        val barcodeScanner = BarcodeScanning.getClient(options)

                        val imageAnalyzer = ImageAnalysis.Builder()
                            .build()
                            .also {
                                it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                    processImageProxy(imageProxy, barcodeScanner, context) { result ->
                                        try {
                                            val products = Gson().fromJson(result, Array<Product>::class.java)
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    products.forEach { product ->
                                                        try {
                                                            val id = db.shoppingDao().insertProduct(product)
                                                            Log.d("QR_DB", "Dodano produkt: ${product.name}, ID: $id")
                                                        } catch (e: Exception) {
                                                            Log.e("QR_DB", "Błąd zapisu produktu: ${product.name}", e)
                                                        }
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e("QR SCAN", "Niepoprawny format danych", e)
                                        }
                                    }
                                }
                            }

                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            context as QRCodeScannerActivity,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalyzer
                        )
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize().weight(1f)
            )
        } else {
            Text("Brak uprawnień do kamery")
        }
    }
}
@androidx.camera.core.ExperimentalGetImage
fun processImageProxy(
    imageProxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    context: Context,
    onResult: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    barcode.rawValue?.let { result ->
                        Log.d("QR SCAN", "Zeskanowano: $result")
                        Toast.makeText(context, "Zeskanowano:\n$result", Toast.LENGTH_LONG).show()
                        onResult(result)
                    }
                }
            }
            .addOnFailureListener {
                Log.e("QR", "Błąd skanowania", it)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}
