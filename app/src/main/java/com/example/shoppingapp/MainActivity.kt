package com.example.shoppingapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.shoppingapp.data.Product
import com.example.shoppingapp.data.Recipe
import com.example.shoppingapp.data.ShoppingDatabase
import com.example.shoppingapp.data.ShoppingList
import com.example.shoppingapp.ui.theme.ShoppingAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = ShoppingDatabase.getDatabase(applicationContext)

        setContent {
            ShoppingAppTheme {
                val appNavController = rememberNavController()
                AppNavigationHost(appNavController = appNavController, db = db)
            }
        }
    }
}

@Composable
fun OverflowMenu(
    menuItems: List<Pair<String, () -> Unit>>
) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "More options"
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            menuItems.forEach { (text, action) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        action()
                        showMenu = false
                    }
                )
            }
        }
    }
}


@Composable
fun AppNavigationHost(appNavController: NavHostController, db: ShoppingDatabase) {
    NavHost(navController = appNavController, startDestination = "mainTabs") {
        composable("mainTabs") {
            MainScreenWithTabsComposable(db = db, appNavController = appNavController)
        }
        composable(
            route = "details/{listId}",
            arguments = listOf(navArgument("listId") { type = NavType.IntType })
        ) { backStackEntry ->
            ShoppingListDetails(
                listId = backStackEntry.arguments?.getInt("listId") ?: -1,
                db = db,
                navController = appNavController
            )
        }
        composable(
            route = "recipeDetails/{recipeId}",
            arguments = listOf(navArgument("recipeId") { type = NavType.IntType })
        ) { backStackEntry ->
            RecipeDetailsScreen(
                recipeId = backStackEntry.arguments?.getInt("recipeId") ?: -1,
                db = db,
                navController = appNavController
            )
        }
        composable(
            route = "activeShopping?listIds={listIds}&recipeIds={recipeIds}",
            arguments = listOf(
                navArgument("listIds") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("recipeIds") {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) { backStackEntry ->
            val listIdsString = backStackEntry.arguments?.getString("listIds")
            val recipeIdsString = backStackEntry.arguments?.getString("recipeIds")
            ActiveShoppingScreen(
                listIdsString = listIdsString,
                recipeIdsString = recipeIdsString,
                db = db,
                navController = appNavController
            )
        }
        composable(
            route = "productDetails/{productId}",
            arguments = listOf(navArgument("productId") { type = NavType.IntType })
        ) { backStackEntry ->
            ProductDetailsScreen(
                productId = backStackEntry.arguments?.getInt("productId") ?: -1,
                db = db,
                navController = appNavController
            )
        }
    }
}


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreenWithTabsComposable(db: ShoppingDatabase, appNavController: NavHostController) {
    val tabTitles = listOf("Shopping Lists", "Recipes", "Products")
    val pagerState = rememberPagerState { tabTitles.size }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val dao = db.shoppingDao()

    var showAddShoppingListDialog by remember { mutableStateOf(false) }
    var showAddRecipeDialog by remember { mutableStateOf(false) }

    var showShoppingSessionSelectionDialog by remember { mutableStateOf(false) }
    var allShoppingListsForSelection by remember { mutableStateOf<List<ShoppingList>>(emptyList()) }
    var allRecipesForSelection by remember { mutableStateOf<List<Recipe>>(emptyList()) }

    val focusManager = LocalFocusManager.current
    var productSearchResetSignal by remember { mutableIntStateOf(0) }

    LaunchedEffect(pagerState.settledPage) {
        focusManager.clearFocus()
        if (pagerState.settledPage == 2) {
            productSearchResetSignal++
        }
    }

    fun openShoppingSessionDialog() {
        scope.launch {
            allShoppingListsForSelection = withContext(Dispatchers.IO) { dao.getAllShoppingLists() }
            allRecipesForSelection = withContext(Dispatchers.IO) { dao.getAllRecipes() }
            if (allShoppingListsForSelection.isEmpty() && allRecipesForSelection.isEmpty()) {
                Toast.makeText(context, "No shopping lists or recipes to choose from.", Toast.LENGTH_SHORT).show()
            } else {
                showShoppingSessionSelectionDialog = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shopping App") },
                actions = {
                    OverflowMenu(
                        menuItems = listOf(
                            "Settings" to { Toast.makeText(context, "Settings clicked (Not implemented)", Toast.LENGTH_SHORT).show() },
                            "App Details" to { Toast.makeText(context, "App Details clicked (Not implemented)", Toast.LENGTH_SHORT).show() },
                            "Legal Information" to { Toast.makeText(context, "Legal Info clicked (Not implemented)", Toast.LENGTH_SHORT).show() }
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        },
        floatingActionButton = {
            when (pagerState.currentPage) {
                0 -> ShoppingListScreenFABs(
                    onAddListClick = { showAddShoppingListDialog = true },
                    onStartShoppingClick = { openShoppingSessionDialog() },
                    onShareListClick = {
                        val intent = Intent(context, QRCodeActivity::class.java).apply {
                            putExtra(EXTRA_PRESELECTED_QR_TYPE, QrContentType.SHOPPING_LIST.name)
                        }
                        context.startActivity(intent)
                    },
                    onScanListClick = {
                        val intent = Intent(context, QRCodeScannerActivity::class.java)
                        context.startActivity(intent)
                    }
                )
                1 -> RecipeScreenFABs(
                    onPrimaryRecipeActionClick = { openShoppingSessionDialog() },
                    onAddRecipeClick = { showAddRecipeDialog = true }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = { Text(title) }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                when (page) {
                    0 -> ShoppingListScreen(
                        db = db,
                        navController = appNavController,
                        showAddListDialogInitially = showAddShoppingListDialog,
                        onDismissAddListDialog = { showAddShoppingListDialog = false }
                    )
                    1 -> RecipeListScreen(
                        db = db,
                        navController = appNavController,
                        showAddRecipeDialogInitially = showAddRecipeDialog,
                        onDismissAddRecipeDialog = { showAddRecipeDialog = false }
                    )
                    2 -> ProductListScreen(
                        db = db,
                        navController = appNavController,
                        searchResetSignal = productSearchResetSignal
                    )
                }
            }
        }
    }

    if (showShoppingSessionSelectionDialog) {
        ShoppingSessionSelectionDialog(
            allLists = allShoppingListsForSelection,
            allRecipes = allRecipesForSelection,
            onDismiss = { showShoppingSessionSelectionDialog = false },
            onConfirm = { selectedListIds, selectedRecipeIds ->
                showShoppingSessionSelectionDialog = false
                val params = mutableListOf<String>()
                if (selectedListIds.isNotEmpty()) {
                    params.add("listIds=${selectedListIds.joinToString(",")}")
                }
                if (selectedRecipeIds.isNotEmpty()) {
                    params.add("recipeIds=${selectedRecipeIds.joinToString(",")}")
                }
                if (params.isNotEmpty()) {
                    appNavController.navigate("activeShopping?${params.joinToString("&")}")
                } else {
                    Toast.makeText(context, "No items selected for shopping.", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShoppingSessionSelectionDialog(
    allLists: List<ShoppingList>,
    allRecipes: List<Recipe>,
    onDismiss: () -> Unit,
    onConfirm: (selectedListIds: List<Int>, selectedRecipeIds: List<Int>) -> Unit
) {
    var selectedListIds by remember { mutableStateOf(setOf<Int>()) }
    var selectedRecipeIds by remember { mutableStateOf(setOf<Int>()) }

    val tabTitles = listOf("Shopping Lists", "Recipes")
    val pagerState = rememberPagerState { tabTitles.size }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.heightIn(max = 500.dp).widthIn(max = 350.dp),
        title = { Text("Select for Shopping Session") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = { Text(title) }
                        )
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { page ->
                    when (page) {
                        0 -> {
                            if (allLists.isEmpty()) {
                                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text("No shopping lists available.")
                                }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxHeight()) {
                                    items(allLists, key = { "list-sel-${it.id}" }) { list ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedListIds = if (selectedListIds.contains(list.id)) {
                                                        selectedListIds - list.id
                                                    } else {
                                                        selectedListIds + list.id
                                                    }
                                                }
                                                .padding(vertical = 8.dp, horizontal = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = selectedListIds.contains(list.id),
                                                onCheckedChange = { isChecked ->
                                                    selectedListIds = if (isChecked) {
                                                        selectedListIds + list.id
                                                    } else {
                                                        selectedListIds - list.id
                                                    }
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(list.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            if (allRecipes.isEmpty()) {
                                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text("No recipes available.")
                                }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxHeight()) {
                                    items(allRecipes, key = { "recipe-sel-${it.id}" }) { recipe ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedRecipeIds = if (selectedRecipeIds.contains(recipe.id)) {
                                                        selectedRecipeIds - recipe.id
                                                    } else {
                                                        selectedRecipeIds + recipe.id
                                                    }
                                                }
                                                .padding(vertical = 8.dp, horizontal = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = selectedRecipeIds.contains(recipe.id),
                                                onCheckedChange = { isChecked ->
                                                    selectedRecipeIds = if (isChecked) {
                                                        selectedRecipeIds + recipe.id
                                                    } else {
                                                        selectedRecipeIds - recipe.id
                                                    }
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(recipe.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(selectedListIds.toList(), selectedRecipeIds.toList())
                },
                enabled = selectedListIds.isNotEmpty() || selectedRecipeIds.isNotEmpty()
            ) {
                Text("Start Shopping")
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
fun ProductSelectionEntryDialog(
    products: List<Product>,
    onDismiss: () -> Unit,
    onConfirm: (productId: Int, quantity: Float) -> Unit,
    dialogTitle: String,
    confirmButtonText: String,
    initialSelectedProduct: Product? = null,
    initialQuantity: Float? = null
) {
    var selectedProduct by remember(initialSelectedProduct) { mutableStateOf(initialSelectedProduct) }
    var quantityStr by remember(initialQuantity) {
        mutableStateOf(initialQuantity?.toString()?.replace(',', '.') ?: "")
    }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(initialSelectedProduct, initialQuantity) {
        selectedProduct = initialSelectedProduct
        quantityStr = initialQuantity?.toString()?.replace(',', '.') ?: ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        readOnly = true,
                        value = selectedProduct?.name ?: "Select Product",
                        onValueChange = {},
                        label = { Text("Product") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
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
                    onValueChange = {
                        val newText = it.replace(',', '.')
                        if (newText.count { char -> char == '.' } <= 1) {
                            quantityStr = newText.filter { char -> char.isDigit() || char == '.' }
                        }
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
                Text(confirmButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}