package com.example.shoppingapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.shoppingapp.data.ShoppingDatabase
import com.example.shoppingapp.data.ShoppingList
import com.example.shoppingapp.ui.theme.ShoppingAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import com.example.shoppingapp.data.Product
import com.example.shoppingapp.data.ShoppingListItem
import com.example.shoppingapp.data.ShoppingListWithItems
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Search
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = ShoppingDatabase.getDatabase(applicationContext)

        setContent {
            ShoppingAppTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "listScreen") {
                    composable("listScreen") {
                        ShoppingListScreen(db = db, navController = navController)
                    }
                    composable(
                        route = "details/{listId}", // Changed route to use listId
                        arguments = listOf(
                            navArgument("listId") { type = NavType.IntType }
                        )
                    ) { backStackEntry ->
                        ShoppingListDetails(
                            listId = backStackEntry.arguments?.getInt("listId") ?: -1, // Get listId
                            db = db,
                            navController = navController

                        )
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(db: ShoppingDatabase, navController: NavHostController) {
    val dao = db.shoppingDao()
    var lists by remember { mutableStateOf(listOf<ShoppingList>()) }
    var showDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentDate = Calendar.getInstance().time
    val sdf = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
    val dateString = sdf.format(currentDate)

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    val updatedLists = withContext(Dispatchers.IO) {
                        dao.getAllShoppingLists()
                    }
                    lists = updatedLists
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        scope.launch {
            val fetchedLists = withContext(Dispatchers.IO) {
                dao.getAllShoppingLists()
            }
            lists = fetchedLists
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("My Shopping Lists") })
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FloatingActionButton(onClick = {
                    // Twój przycisk QR
                    val intent = Intent(context, QRCodeActivity::class.java)
                    context.startActivity(intent)
                }) {
                    Icon(Icons.Default.Share, contentDescription = "QR Code")
                }
                FloatingActionButton(onClick = {
                    val intent = Intent(context, QRCodeScannerActivity::class.java)
                    context.startActivity(intent)
                }) {
                    Icon(Icons.Default.Search, contentDescription = "Scan QR")
                }
                FloatingActionButton(onClick = { showDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add List")
                }

            }}
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(lists) { list ->
                ShoppingListCard(list = list) {
                    navController.navigate("details/${list.id}")
                }
            }
        }

        if (showDialog) {
            AddListDialog(
                onDismiss = { showDialog = false },
                onConfirm = { title, description, notes ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            dao.insertShoppingList(
                                ShoppingList(
                                    title = title,
                                    description = description,
                                    notes = notes,
                                    timesUsed = 0,
                                    dateCreated = dateString,
                                )
                            )
                            val updatedLists = dao.getAllShoppingLists()
                            lists = updatedLists
                        }
                    }
                    showDialog = false
                }
            )
        }
    }
}

@Composable
fun ShoppingListCard(list: ShoppingList, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = list.title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Created: ${list.dateCreated}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun AddListDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Shopping List") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title, description, notes)
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListDetails(
    listId: Int,
    db: ShoppingDatabase,
    navController: NavHostController
) {
    val dao = db.shoppingDao()
    val scope = rememberCoroutineScope()

    var shoppingListWithItems by remember { mutableStateOf<ShoppingListWithItems?>(null) }
    var allProducts by remember { mutableStateOf<List<Product>>(emptyList()) }
    val productMap: Map<Int, Product> by remember(allProducts) {
        derivedStateOf { allProducts.associateBy { it.id } }
    }

    var showAddItemDialog by remember { mutableStateOf(false) }

    LaunchedEffect(listId) {
        if (listId == -1) {
            navController.popBackStack()
            return@LaunchedEffect
        }
        scope.launch {
            val fetchedListWithItems = withContext(Dispatchers.IO) {
                dao.getShoppingListWithItems(listId)
            }
            val fetchedProducts = withContext(Dispatchers.IO) {
                dao.getAllProducts()
            }
            withContext(Dispatchers.Main) {
                shoppingListWithItems = fetchedListWithItems
                allProducts = fetchedProducts
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(shoppingListWithItems?.shoppingList?.title ?: "List Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (shoppingListWithItems != null) {
                FloatingActionButton(onClick = { showAddItemDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Item to List")
                }

            }
        }

    ) { padding ->
        if (shoppingListWithItems == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (listId != -1) CircularProgressIndicator() else Text("Invalid List ID.")
            }
        } else {
            val list = shoppingListWithItems!!.shoppingList
            val items = shoppingListWithItems!!.items

            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxSize(),
            ) {
                Text("Title: ${list.title}", style = MaterialTheme.typography.titleLarge)
                Text("Description: ${list.description.ifEmpty { "No description" }}", style = MaterialTheme.typography.bodyMedium)
                Text("Notes: ${list.notes.ifEmpty { "No notes" }}", style = MaterialTheme.typography.bodyMedium)
                Text("Created on: ${list.dateCreated}", style = MaterialTheme.typography.bodySmall)
                Text("Times Used: ${list.timesUsed}", style = MaterialTheme.typography.bodySmall)

                Spacer(modifier = Modifier.height(16.dp))
                Text("Items in this list:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                if (items.isEmpty()) {
                    Text("No items added yet. Click the '+' button to add items.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(items) { item ->
                            val product = productMap[item.productId]
                            ListItem(
                                headlineContent = { Text(product?.name ?: "Unknown Product") },
                                supportingContent = {
                                    Text("Quantity: ${item.quantity} ${product?.unit ?: ""}")
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (showAddItemDialog && shoppingListWithItems != null) {
        AddItemDialog(
            products = allProducts,
            onDismiss = { showAddItemDialog = false },
            onConfirm = { productId, quantity ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val newItem = ShoppingListItem(
                            listId = listId,
                            productId = productId,
                            quantity = quantity
                        )
                        dao.insertShoppingListItem(newItem)
                        val updatedListWithItems = dao.getShoppingListWithItems(listId)
                        withContext(Dispatchers.Main) {
                            shoppingListWithItems = updatedListWithItems
                        }
                    }
                }
                showAddItemDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemDialog(
    products: List<Product>,
    onDismiss: () -> Unit,
    onConfirm: (productId: Int, quantity: Float) -> Unit
) {
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var quantityStr by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Item to List") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        //próbowałem pozbyć się tutaj warninga ale gdy dodaje potrzebne rzeczy to program dostaje fikołka
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        readOnly = true,
                        value = selectedProduct?.name ?: "Select Product",
                        onValueChange = {},
                        label = { Text("Product") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        if (products.isEmpty()){
                            DropdownMenuItem(
                                text = { Text("No products available") },
                                onClick = { expanded = false },
                                enabled = false
                            )
                        }
                        products.forEach { product ->
                            DropdownMenuItem(
                                text = { Text("${product.name} (${product.unit})") },
                                onClick = {
                                    selectedProduct = product
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = quantityStr,
                    onValueChange = { quantityStr = it.filter { char -> char.isDigit() || char == '.' || char == ',' } // Allow comma as well for decimal
                        .replace(',', '.')
                    },
                    label = { Text("Quantity${selectedProduct?.unit?.let { " ($it)" } ?: ""}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = selectedProduct != null
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val quantity = quantityStr.toFloatOrNull()
                    if (selectedProduct != null && quantity != null && quantity > 0) {
                        onConfirm(selectedProduct!!.id, quantity)
                    }
                },
                enabled = selectedProduct != null && (quantityStr.toFloatOrNull() ?: 0f) > 0f
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}