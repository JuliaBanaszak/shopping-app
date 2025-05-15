package com.example.shoppingapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.shoppingapp.ui.theme.ShoppingAppTheme
import androidx.room.Room
import com.example.shoppingapp.data.Product
import com.example.shoppingapp.data.ShoppingDatabase
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LocalLifecycleOwner

class MainActivity : ComponentActivity() {

    private lateinit var db: ShoppingDatabase
    override fun onResume() {
        super.onResume()
        setContent {
            ShoppingAppTheme {
                AddProductScreen(
                    db = ShoppingDatabase.getDatabase(applicationContext)
                )
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Room.databaseBuilder(
            applicationContext,
            ShoppingDatabase::class.java,
            "shopping_database"
        ).build()

        setContent {
            ShoppingAppTheme {
                AddProductScreen(
                    db = ShoppingDatabase.getDatabase(applicationContext)
                )
            }
        }
    }
}




@Composable
fun AddProductScreen(db: ShoppingDatabase) {
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var productList by remember { mutableStateOf(listOf<Product>()) }
    var refresh by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current



    // Automatyczne załadowanie przy starcie i po dodaniu
    LaunchedEffect(refresh) {
        val products = withContext(Dispatchers.IO) {
            db.shoppingDao().getAllProducts()
        }
        productList = products
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refresh = !refresh // To wymusi ponowne pobranie danych z bazy
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Dodaj produkt", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nazwa produktu") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = unit,
            onValueChange = { unit = it },
            label = { Text("Jednostka (np. g, ml, szt)") },
            modifier = Modifier.fillMaxWidth()
        )



        Button(
            onClick = {
                if (name.isNotBlank() && unit.isNotBlank()) {
                    val product = Product(name = name.trim(), unit = unit.trim())
                    name = ""
                    unit = ""

                    scope.launch {
                        withContext(Dispatchers.IO) {
                            db.shoppingDao().insertProduct(product)
                        }
                        refresh = !refresh
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Dodaj")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val intent = Intent(context, QRCodeActivity::class.java)
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Pokaż QR z produktami")
        }
        Button(
            onClick = {
                val intent = Intent(context, QRCodeScannerActivity::class.java)
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Zeskanuj kod QR i dodaj produkty")
        }

        Divider()

        Text("Lista produktów:", style = MaterialTheme.typography.titleMedium)
        LazyColumn {
            items(productList) { product ->
                Text("• ${product.name} (${product.unit})")
            }
        }
    }
}