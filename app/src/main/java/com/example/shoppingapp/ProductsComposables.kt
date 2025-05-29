package com.example.shoppingapp

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.shoppingapp.data.Product
import com.example.shoppingapp.data.ShoppingDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    db: ShoppingDatabase,
    navController: NavHostController,
    searchResetSignal: Int
) {
    val dao = db.shoppingDao()
    var allProducts by remember { mutableStateOf(listOf<Product>()) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }

    val filteredProducts by remember(searchQuery, allProducts) {
        derivedStateOf {
            val sortedAll = allProducts.sortedBy { it.name }
            if (searchQuery.isBlank()) {
                sortedAll
            } else {
                sortedAll.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                            (it.brand?.contains(searchQuery, ignoreCase = true) == true) ||
                            (it.category?.contains(searchQuery, ignoreCase = true) == true) ||
                            (it.allergens?.contains(searchQuery, ignoreCase = true) == true)
                }
            }
        }
    }

    fun refreshProducts() {
        scope.launch {
            isLoading = true
            allProducts = withContext(Dispatchers.IO) { dao.getAllProducts() }
            isLoading = false
        }
    }

    LaunchedEffect(searchResetSignal) {
        if (searchResetSignal > 0) {
            searchQuery = ""
        }
    }

    LaunchedEffect(Unit) {
        refreshProducts()
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search Products") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search Icon") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            singleLine = true,
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                    }
                }
            }
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (allProducts.isEmpty() && searchQuery.isBlank()) {
                Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    Text("No products in the database.")
                }
            } else if (filteredProducts.isEmpty() && searchQuery.isNotBlank()) {
                Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    Text("No products found matching \"$searchQuery\".")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredProducts, key = { "product-item-${it.id}" }) { product ->
                        ProductListItemCard(product = product) {
                            navController.navigate("productDetails/${product.id}")
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun ProductListItemCard(product: Product, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(product.name)
                    }
                    append(" (${product.unit})")
                    product.allergens?.takeIf { it.isNotBlank() }?.let { allergensValue ->
                        append(" • Allergens: $allergensValue")
                    }
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailsScreen(
    productId: Int,
    db: ShoppingDatabase,
    navController: NavHostController
) {
    val dao = db.shoppingDao()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var product by remember { mutableStateOf<Product?>(null) }

    LaunchedEffect(productId) {
        if (productId == -1) {
            Toast.makeText(context, "Invalid Product ID.", Toast.LENGTH_SHORT).show()
            navController.popBackStack()
            return@LaunchedEffect
        }
        scope.launch {
            product = withContext(Dispatchers.IO) {
                dao.getProductById(productId)
            }
            if (product == null && productId != -1) {
                Toast.makeText(context, "Product not found.", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(product?.name ?: "Product Details", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        if (product == null) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                if (productId != -1) CircularProgressIndicator()
            }
        } else {
            val p = product!!
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { DetailItem("Product Name", p.name) }
                item { DetailItem("Standard Unit", p.unit) }
                p.brand?.takeIf { it.isNotBlank() }?.let { item { DetailItem("Brand", it) } }
                p.category?.takeIf { it.isNotBlank() }?.let { item { DetailItem("Category", it) } }
                p.calories?.let {
                    val unitContext = if (p.unit.equals("g", ignoreCase = true) || p.unit.equals("ml", ignoreCase = true)) " per 100${p.unit}" else ""
                    item { DetailItem("Calories (approx.)", "$it kcal$unitContext") }
                }
                p.allergens?.takeIf { it.isNotBlank() }?.let { item { DetailItem("Potential Allergens", it) } }
                p.description?.takeIf { it.isNotBlank() }?.let { item { DetailItem("Description", it, isBlock = true) } }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
fun DetailItem(label: String, value: String, isBlock: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = if (isBlock) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
            modifier = if (isBlock) Modifier.padding(start = 8.dp) else Modifier
        )
    }
}