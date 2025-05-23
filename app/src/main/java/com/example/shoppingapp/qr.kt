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
import com.example.shoppingapp.data.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.camera.core.Preview

class QRCodeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = ShoppingDatabase.getDatabase(applicationContext)
        setContent {
            ShoppingAppTheme {
                QRCodeListSelectorScreen(db)
            }
        }
    }
}

@Composable
fun QRCodeListSelectorScreen(db: ShoppingDatabase) {
    var allLists by remember { mutableStateOf<List<ShoppingList>>(emptyList()) }
    var selectedList by remember { mutableStateOf<ShoppingList?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        allLists = withContext(Dispatchers.IO) {
            db.shoppingDao().getAllShoppingLists()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Wybierz listę zakupów do zakodowania")

        Box {
            Button(onClick = { expanded = true }) {
                Text(selectedList?.title ?: "Wybierz listę")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                allLists.forEach { list ->
                    DropdownMenuItem(
                        text = { Text(list.title) },
                        onClick = {
                            selectedList = list
                            expanded = false
                        }
                    )
                }
            }
        }

        Button(
            onClick = {
                selectedList?.let { list ->
                    val gson = Gson()
                    CoroutineScope(Dispatchers.Main).launch {
                        val fullList = withContext(Dispatchers.IO) {
                            db.shoppingDao().getShoppingListWithItems(list.id)
                        }
                        if (fullList == null) return@launch
                        val products = withContext(Dispatchers.IO) {
                            db.shoppingDao().getAllProducts()
                        }
                        val itemDetails = fullList.items.map { item ->
                            val product = products.find { it.id == item.productId }
                            mapOf(
                                "name" to (product?.name ?: ""),
                                "unit" to (product?.unit ?: ""),
                                "quantity" to item.quantity
                            )
                        }
                        val qrData = mapOf(
                            "title" to list.title,
                            "description" to list.description,
                            "notes" to list.notes,
                            "items" to itemDetails
                        )
                        val json = gson.toJson(qrData)
                        qrBitmap = generateQRCode(json)
                    }
                }
            },
            enabled = selectedList != null
        ) {
            Text("Generuj QR")
        }

        qrBitmap?.let {
            Image(bitmap = it.asImageBitmap(), contentDescription = "Kod QR")
        }
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

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@OptIn(ExperimentalGetImage::class)
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Zeskanuj kod QR z listą zakupów", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))

        if (hasCameraPermission) {
            AndroidView(factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val barcodeScanner = BarcodeScanning.getClient(
                        BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
                            .build()
                    )

                    val imageAnalyzer = ImageAnalysis.Builder().build().also {
                        it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                barcodeScanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        for (barcode in barcodes) {
                                            barcode.rawValue?.let { result ->
                                                Log.d("QR SCAN", "Zeskanowano: $result")
                                                Toast.makeText(context, "Zeskanowano dane!", Toast.LENGTH_SHORT).show()
                                                scope.launch {
                                                    try {
                                                        val map = Gson().fromJson(result, Map::class.java)
                                                        val title = map["title"] as? String ?: return@launch
                                                        val description = map["description"] as? String ?: ""
                                                        val notes = map["notes"] as? String ?: ""
                                                        val items = map["items"] as? List<Map<String, Any>> ?: return@launch

                                                        val newListId = withContext(Dispatchers.IO) {
                                                            db.shoppingDao().insertShoppingList(
                                                                ShoppingList(
                                                                    title = title,
                                                                    description = description,
                                                                    notes = notes,
                                                                    dateCreated = SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date()),
                                                                    timesUsed = 0
                                                                )
                                                            ).toInt()
                                                        }

                                                        withContext(Dispatchers.IO) {
                                                            items.forEach { itemMap ->
                                                                val name = itemMap["name"] as? String ?: return@forEach
                                                                val unit = itemMap["unit"] as? String ?: ""
                                                                val quantity = (itemMap["quantity"] as? Double)?.toFloat() ?: 1f

                                                                val productId = db.shoppingDao().getAllProducts()
                                                                    .find { it.name == name && it.unit == unit }?.id
                                                                    ?: db.shoppingDao().insertProduct(Product(name = name, unit = unit)).toInt()

                                                                db.shoppingDao().insertShoppingListItem(
                                                                    ShoppingListItem(
                                                                        listId = newListId,
                                                                        productId = productId,
                                                                        quantity = quantity
                                                                    )
                                                                )
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e("QR SCAN", "Błąd przetwarzania danych z QR", e)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    .addOnCompleteListener { imageProxy.close() }
                            } else {
                                imageProxy.close()
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
            }, modifier = Modifier.fillMaxSize().weight(1f))
        } else {
            Text("Brak uprawnień do kamery")
        }
    }
}

