@file:Suppress("OPT_IN_ARGUMENT_IS_NOT_MARKER")

package com.example.shoppingapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.example.shoppingapp.data.*
import com.example.shoppingapp.ui.theme.ShoppingAppTheme
import com.google.gson.Gson
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

enum class QrContentType {
    SHOPPING_LIST, RECIPE
}

const val EXTRA_PRESELECTED_QR_TYPE = "PRESELECTED_QR_TYPE"

class QRCodeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = ShoppingDatabase.getDatabase(applicationContext)

        val preselectedTypeString = intent.getStringExtra(EXTRA_PRESELECTED_QR_TYPE)
        val preselectedType = QrContentType.entries.find { it.name == preselectedTypeString }

        setContent {
            ShoppingAppTheme {
                QRCodeGeneratorScreen(db, initialSelectedQrType = preselectedType)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRCodeGeneratorScreen(db: ShoppingDatabase, initialSelectedQrType: QrContentType? = null) {
    var selectedQrType by remember(initialSelectedQrType) { mutableStateOf(initialSelectedQrType) }
    var allShoppingLists by remember { mutableStateOf<List<ShoppingList>>(emptyList()) }
    var allRecipes by remember { mutableStateOf<List<Recipe>>(emptyList()) }

    var selectedShoppingList by remember { mutableStateOf<ShoppingList?>(null) }
    var selectedRecipe by remember { mutableStateOf<Recipe?>(null) }

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var showShoppingListDialog by remember { mutableStateOf(false) }
    var showRecipeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        allShoppingLists = withContext(Dispatchers.IO) {
            db.shoppingDao().getAllShoppingLists()
        }
        allRecipes = withContext(Dispatchers.IO) {
            db.shoppingDao().getAllRecipes()
        }
    }

    LaunchedEffect(selectedQrType) {
        if (selectedQrType == QrContentType.RECIPE) {
            selectedShoppingList = null
        } else if (selectedQrType == QrContentType.SHOPPING_LIST) {
            selectedRecipe = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (qrBitmap != null) {
            Text(
                "Scan the QR Code",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Image(
                bitmap = qrBitmap!!.asImageBitmap(),
                contentDescription = "QR Code",
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f)
                    .padding(vertical = 24.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    qrBitmap = null
                    selectedShoppingList = null
                    selectedRecipe = null
                },
                modifier = Modifier.fillMaxWidth(0.7f)
            ) {
                Text("Clear QR & Select New")
            }

        } else {
            Box(modifier = Modifier.fillMaxSize()) {

                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Select item to generate QR code for")

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                selectedQrType = QrContentType.SHOPPING_LIST
                                qrBitmap = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedQrType == QrContentType.SHOPPING_LIST) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) { Text("Shopping List") }
                        Button(
                            onClick = {
                                selectedQrType = QrContentType.RECIPE
                                qrBitmap = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedQrType == QrContentType.RECIPE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) { Text("Recipe") }
                    }

                    when (selectedQrType) {
                        QrContentType.SHOPPING_LIST -> {
                            Text("Selected: Shopping List")
                            Button(onClick = { showShoppingListDialog = true }) {
                                Text(selectedShoppingList?.title ?: "Select a list")
                            }
                            if (showShoppingListDialog) {
                                AlertDialog(
                                    onDismissRequest = { showShoppingListDialog = false },
                                    title = { Text("Select Shopping List") },
                                    text = {
                                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                            if (allShoppingLists.isEmpty()) {
                                                item { Text("No shopping lists available.", modifier = Modifier.padding(16.dp)) }
                                            } else {
                                                items(allShoppingLists) { list ->
                                                    TextButton(
                                                        onClick = {
                                                            selectedShoppingList = list
                                                            showShoppingListDialog = false
                                                            qrBitmap = null
                                                        },
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) { Text(list.title, style = MaterialTheme.typography.bodyLarge) }
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = { TextButton(onClick = { showShoppingListDialog = false }) { Text("Cancel") } }
                                )
                            }
                        }
                        QrContentType.RECIPE -> {
                            Text("Selected: Recipe")
                            Button(onClick = { showRecipeDialog = true }) {
                                Text(selectedRecipe?.name ?: "Select a recipe")
                            }
                            if (showRecipeDialog) {
                                AlertDialog(
                                    onDismissRequest = { showRecipeDialog = false },
                                    title = { Text("Select Recipe") },
                                    text = {
                                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                            if (allRecipes.isEmpty()) {
                                                item { Text("No recipes available.", modifier = Modifier.padding(16.dp)) }
                                            } else {
                                                items(allRecipes) { recipe ->
                                                    TextButton(
                                                        onClick = {
                                                            selectedRecipe = recipe
                                                            showRecipeDialog = false
                                                            qrBitmap = null
                                                        },
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) { Text(recipe.name, style = MaterialTheme.typography.bodyLarge) }
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = { TextButton(onClick = { showRecipeDialog = false }) { Text("Cancel") } }
                                )
                            }
                        }
                        null -> {
                            Text("Please select an item type above.")
                        }
                    }
                }

                Button(
                    onClick = {
                        val gson = Gson()
                        CoroutineScope(Dispatchers.Main).launch {
                            val jsonToEncode: String? = when (selectedQrType) {
                                QrContentType.SHOPPING_LIST -> selectedShoppingList?.let { list ->
                                    val fullList = withContext(Dispatchers.IO) { db.shoppingDao().getShoppingListWithItems(list.id) }
                                    if (fullList == null) { Log.e("QRGen", "List not found"); return@launch }
                                    val products = withContext(Dispatchers.IO) { db.shoppingDao().getAllProducts() }
                                    val itemDetails = fullList.items.mapNotNull { item ->
                                        products.find { it.id == item.productId }?.let { product ->
                                            mapOf("name" to product.name, "unit" to product.unit, "quantity" to item.quantity)
                                        }
                                    }
                                    gson.toJson(mapOf("type" to "shopping_list", "title" to list.title, "description" to list.description, "notes" to list.notes, "items" to itemDetails))
                                }
                                QrContentType.RECIPE -> selectedRecipe?.let { recipe ->
                                    val fullRecipe = withContext(Dispatchers.IO) { db.shoppingDao().getRecipeWithIngredients(recipe.id) }
                                    if (fullRecipe == null) { Log.e("QRGen", "Recipe not found"); return@launch }
                                    val products = withContext(Dispatchers.IO) { db.shoppingDao().getAllProducts() }
                                    val ingredientDetails = fullRecipe.ingredients.mapNotNull { ingredient ->
                                        products.find { it.id == ingredient.productId }?.let { product ->
                                            mapOf("name" to product.name, "unit" to product.unit, "quantity" to ingredient.quantity)
                                        }
                                    }
                                    gson.toJson(mapOf("type" to "recipe", "name" to recipe.name, "description" to recipe.description, "instructions" to (recipe.instructions ?: ""), "ingredients" to ingredientDetails))
                                }
                                null -> null
                            }
                            jsonToEncode?.let { qrBitmap = generateQRCode(it) }
                        }
                    },
                    enabled = (selectedQrType == QrContentType.SHOPPING_LIST && selectedShoppingList != null) ||
                            (selectedQrType == QrContentType.RECIPE && selectedRecipe != null),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(0.7f)
                ) {
                    Text("Generate QR")
                }
            }
        }
    }
}

fun generateQRCode(text: String, size: Int = 512): Bitmap? {
    return try {
        val bitMatrix: BitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap[x, y] = if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
        }
        bitmap
    } catch (e: Exception) {
        Log.e("QR", "Error generating QR code", e)
        null
    }
}

class QRCodeScannerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShoppingAppTheme {
                QRScannerScreen(onScanComplete = {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    finish()
                })
            }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun QRScannerScreen(onScanComplete: () -> Unit) {
    val context = LocalContext.current
    val db = remember { ShoppingDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    var scanned by remember { mutableStateOf(false) }

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
        if (!granted) {
            Toast.makeText(context, "Camera permission is required to scan QR codes.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        AndroidView(factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val barcodeScanner = BarcodeScanning.getClient(
                    BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build()
                )

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().also {
                        it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                            if (scanned) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                barcodeScanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        if (scanned) return@addOnSuccessListener
                                        for (barcode in barcodes) {
                                            barcode.rawValue?.let { result ->
                                                if (!scanned) {
                                                    scanned = true
                                                    Log.d("QR SCAN", "Scanned: $result")
                                                    scope.launch {
                                                        @Suppress("UNCHECKED_CAST")
                                                        try {
                                                            val gson = Gson()
                                                            val genericMap = gson.fromJson(result, Map::class.java)
                                                            val type = genericMap["type"] as? String

                                                            when (type) {
                                                                "shopping_list" -> {
                                                                    val title = genericMap["title"] as? String ?: "Imported List"
                                                                    val description = genericMap["description"] as? String ?: ""
                                                                    val notes = genericMap["notes"] as? String ?: ""
                                                                    val itemsData = genericMap["items"] as? List<Map<String, Any>> ?: emptyList()

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

                                                                    val allDbProducts = withContext(Dispatchers.IO) { db.shoppingDao().getAllProducts() }
                                                                    withContext(Dispatchers.IO) {
                                                                        itemsData.forEach { itemMap ->
                                                                            val name = itemMap["name"] as? String ?: return@forEach
                                                                            val unit = itemMap["unit"] as? String ?: ""
                                                                            val quantity = (itemMap["quantity"] as? Double)?.toFloat() ?: 1.0f

                                                                            var productId = allDbProducts.find { p -> p.name.equals(name, ignoreCase = true) && p.unit.equals(unit, ignoreCase = true) }?.id
                                                                            if (productId == null) {
                                                                                productId = db.shoppingDao().insertProduct(Product(name = name, unit = unit)).toInt()
                                                                            }

                                                                            db.shoppingDao().insertShoppingListItem(
                                                                                ShoppingListItem(listId = newListId, productId = productId, quantity = quantity)
                                                                            )
                                                                        }
                                                                    }
                                                                    Log.d("QR SCAN", "Shopping list '$title' processed and saved.")
                                                                    withContext(Dispatchers.Main) {
                                                                        Toast.makeText(context, "Shopping list '$title' added!", Toast.LENGTH_LONG).show()
                                                                        onScanComplete()
                                                                    }
                                                                }
                                                                "recipe" -> {
                                                                    val name = genericMap["name"] as? String ?: "Imported Recipe"
                                                                    val description = genericMap["description"] as? String ?: ""
                                                                    val instructions = genericMap["instructions"] as? String ?: ""
                                                                    val ingredientsData = genericMap["ingredients"] as? List<Map<String, Any>> ?: emptyList()

                                                                    val newRecipeId = withContext(Dispatchers.IO) {
                                                                        db.shoppingDao().insertRecipe(
                                                                            Recipe(
                                                                                name = name,
                                                                                description = description,
                                                                                instructions = instructions,
                                                                                dateCreated = SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date()),
                                                                                timesUsed = 0
                                                                            )
                                                                        ).toInt()
                                                                    }
                                                                    val allDbProducts = withContext(Dispatchers.IO) { db.shoppingDao().getAllProducts() }
                                                                    withContext(Dispatchers.IO) {
                                                                        ingredientsData.forEach { ingredientMap ->
                                                                            val productName = ingredientMap["name"] as? String ?: return@forEach
                                                                            val productUnit = ingredientMap["unit"] as? String ?: ""
                                                                            val quantity = (ingredientMap["quantity"] as? Double)?.toFloat() ?: 1.0f

                                                                            var productId = allDbProducts.find { p -> p.name.equals(productName, ignoreCase = true) && p.unit.equals(productUnit, ignoreCase = true) }?.id
                                                                            if (productId == null) {
                                                                                productId = db.shoppingDao().insertProduct(Product(name = productName, unit = productUnit)).toInt()
                                                                            }
                                                                            db.shoppingDao().insertRecipeIngredient(
                                                                                RecipeIngredient(recipeId = newRecipeId, productId = productId, quantity = quantity)
                                                                            )
                                                                        }
                                                                    }
                                                                    Log.d("QR SCAN", "Recipe '$name' processed and saved.")
                                                                    withContext(Dispatchers.Main) {
                                                                        Toast.makeText(context, "Recipe '$name' added!", Toast.LENGTH_LONG).show()
                                                                        onScanComplete()
                                                                    }
                                                                }
                                                                else -> {
                                                                    Log.e("QR SCAN", "Unknown data type in QR code: $type")
                                                                    withContext(Dispatchers.Main) {
                                                                        Toast.makeText(context, "Unknown QR code format.", Toast.LENGTH_LONG).show()
                                                                        scanned = false
                                                                    }
                                                                }
                                                            }
                                                        } catch (e: Exception) {
                                                            Log.e("QR SCAN", "Error processing QR data: ${e.message}", e)
                                                            withContext(Dispatchers.Main) {
                                                                Toast.makeText(context, "Error reading QR data.", Toast.LENGTH_LONG).show()
                                                                scanned = false
                                                            }
                                                        }
                                                    }
                                                    return@addOnSuccessListener
                                                }
                                            }
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        if (!scanned) Log.e("QR SCAN", "Barcode scanning failed", e)
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            } else {
                                imageProxy.close()
                            }
                        }
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        ctx as ComponentActivity,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalyzer
                    )
                } catch (exc: Exception) {
                    Log.e("QRScanner", "Use case binding failed", exc)
                }

            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }, modifier = Modifier.fillMaxSize())
    } else if (!LocalInspectionMode.current) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Camera permission denied.", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                Text("Grant Permission")
            }
        }
    } else if (scanned) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Processing complete...", style = MaterialTheme.typography.titleMedium)
        }
    }
    if (!hasCameraPermission && LocalInspectionMode.current) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Camera permission will be requested.", style = MaterialTheme.typography.titleMedium)
        }
    }
}