package com.example.shoppingapp // Or your actual package for RecipeScreens

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.shoppingapp.data.* // Assuming this is where Recipe, Product, etc. are
// Import QRCodeActivity and QRCodeScannerActivity if they are in the same package
// If they are in com.example.shoppingapp, these imports are fine.
// import com.example.shoppingapp.QRCodeActivity
// import com.example.shoppingapp.QRCodeScannerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RecipeScreenFABs(
    onPrimaryRecipeActionClick: () -> Unit,
    onAddRecipeClick: () -> Unit
) {
    val context = LocalContext.current
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.End
    ) {
        FloatingActionButton(
            onClick = onPrimaryRecipeActionClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Default.ShoppingCartCheckout, contentDescription = "Start Shopping with Recipes")
        }

        FloatingActionButton(onClick = {
            val intent = Intent(context, QRCodeActivity::class.java).apply {
                putExtra(EXTRA_PRESELECTED_QR_TYPE, QrContentType.RECIPE.name)
            }
            context.startActivity(intent)
        }) {
            Icon(Icons.Default.Share, contentDescription = "Generate Recipe QR Code")
        }

        FloatingActionButton(onClick = {
            val intent = Intent(context, QRCodeScannerActivity::class.java)
            context.startActivity(intent)
        }) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR to Add Recipe")
        }

        FloatingActionButton(
            onClick = onAddRecipeClick
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add New Recipe")
        }
    }
}

@Composable
fun RecipeCard(recipe: Recipe, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.7f)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (recipe.imageUri != null) {
                            AsyncImage(
                                model = recipe.imageUri.toUri(),
                                contentDescription = recipe.name + " thumbnail",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.FillHeight,
                                placeholder = rememberVectorPainter(Icons.Filled.RestaurantMenu),
                                error = rememberVectorPainter(Icons.Filled.BrokenImage)
                            )
                        } else {
                            Icon(
                                Icons.Filled.RestaurantMenu,
                                contentDescription = "No image for ${recipe.name}",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.3f)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = recipe.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun RecipeDetailsDialog(
    initialName: String = "",
    initialDescription: String = "",
    initialInstructions: String? = null,
    initialImageUri: String? = null,
    dialogTitleText: String,
    confirmButtonText: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, instructions: String?, imageUri: String?) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var description by remember(initialDescription) { mutableStateOf(initialDescription) }
    var instructions by remember(initialInstructions) { mutableStateOf(initialInstructions ?: "") }
    var selectedImageUriState by remember(initialImageUri) {
        mutableStateOf(initialImageUri?.toUri())
    }
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
                selectedImageUriState = it
            } catch (e: SecurityException) {
                Log.e("RecipeDetailsDialog", "Failed to take persistable URI permission for $it", e)
                Toast.makeText(context, "Could not select this image. Please try another.", Toast.LENGTH_LONG).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitleText) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Recipe Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("Instructions (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text("Recipe Thumbnail (Optional)", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .align(Alignment.CenterHorizontally)
                        .clickable { imagePickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedImageUriState != null) {
                        AsyncImage(
                            model = selectedImageUriState,
                            contentDescription = "Selected thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillHeight,
                            placeholder = rememberVectorPainter(Icons.Filled.RestaurantMenu),
                            error = rememberVectorPainter(Icons.Filled.BrokenImage)
                        )
                    } else {
                        Icon(
                            Icons.Filled.AddAPhoto,
                            contentDescription = "No image selected, click to add",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (selectedImageUriState != null) Arrangement.SpaceBetween else Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { imagePickerLauncher.launch("image/*") }) {
                        Text(if (selectedImageUriState == null) "Select Image" else "Change Image")
                    }
                    if (selectedImageUriState != null) {
                        Button(
                            onClick = { selectedImageUriState = null },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Remove")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name, description, instructions.ifBlank { null }, selectedImageUriState?.toString())
                    }
                },
                enabled = name.isNotBlank()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    db: ShoppingDatabase,
    navController: NavHostController,
    showAddRecipeDialogInitially: Boolean,
    onDismissAddRecipeDialog: () -> Unit
) {
    val dao = db.shoppingDao()
    var recipes by remember { mutableStateOf(listOf<Recipe>()) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val sdf = SimpleDateFormat("dd/MM/yy", Locale.getDefault())


    fun refreshRecipes() {
        scope.launch {
            val updatedRecipes = withContext(Dispatchers.IO) {
                dao.getAllRecipes()
            }
            recipes = updatedRecipes
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshRecipes()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        refreshRecipes()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (recipes.isEmpty()){
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center){
                Text("No recipes yet. Click '+' to add one!", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(recipes, key = { "recipe-card-${it.id}" }) { recipe ->
                    RecipeCard(recipe = recipe) {
                        navController.navigate("recipeDetails/${recipe.id}")
                    }
                }
            }
        }

        if (showAddRecipeDialogInitially) {
            RecipeDetailsDialog(
                dialogTitleText = "New Recipe",
                confirmButtonText = "Add",
                onDismiss = onDismissAddRecipeDialog,
                onConfirm = { name, description, instructions, imageUri ->
                    val currentDate = Calendar.getInstance().time
                    val dateString = sdf.format(currentDate)
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            dao.insertRecipe(
                                Recipe(
                                    name = name,
                                    description = description,
                                    instructions = instructions,
                                    imageUri = imageUri,
                                    dateCreated = dateString,
                                    timesUsed = 0
                                )
                            )
                        }
                        refreshRecipes()
                    }
                    onDismissAddRecipeDialog()
                }
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailsScreen(
    recipeId: Int,
    db: ShoppingDatabase,
    navController: NavHostController
) {
    val dao = db.shoppingDao()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var recipeWithIngredients by remember { mutableStateOf<RecipeWithIngredients?>(null) }
    var allProducts by remember { mutableStateOf<List<Product>>(emptyList()) }
    val productMap: Map<Int, Product> by remember(allProducts) {
        derivedStateOf { allProducts.associateBy { it.id } }
    }

    var showIngredientEntryDialogForAdd by remember { mutableStateOf(false) }
    var showIngredientEntryDialogForEdit by remember { mutableStateOf(false) }
    var ingredientToEdit by remember { mutableStateOf<RecipeIngredient?>(null) }
    var showEditRecipeDialog by remember { mutableStateOf(false) }
    var showDeleteRecipeConfirmDialog by remember { mutableStateOf(false) }

    fun refreshRecipeDetails() {
        if (recipeId == -1) return
        scope.launch {
            val fetchedRecipeWithIngredients = withContext(Dispatchers.IO) {
                dao.getRecipeWithIngredients(recipeId)
            }
            if (allProducts.isEmpty()) {
                val fetchedProducts = withContext(Dispatchers.IO) { dao.getAllProducts() }
                allProducts = fetchedProducts
            }
            recipeWithIngredients = fetchedRecipeWithIngredients
            if (fetchedRecipeWithIngredients == null && recipeId != -1) {
                Toast.makeText(context, "Recipe not found.", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
        }
    }

    LaunchedEffect(recipeId) {
        if (recipeId != -1) {
            refreshRecipeDetails()
        } else {
            Toast.makeText(context, "Invalid Recipe ID.", Toast.LENGTH_SHORT).show()
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recipeWithIngredients?.recipe?.name ?: "Recipe Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (recipeWithIngredients != null) {
                        IconButton(onClick = {
                            scope.launch {
                                val currentRecipe = recipeWithIngredients!!.recipe
                                val updatedRecipe = currentRecipe.copy(timesUsed = currentRecipe.timesUsed + 1)
                                withContext(Dispatchers.IO) {
                                    dao.updateRecipe(updatedRecipe)
                                }
                                refreshRecipeDetails()
                                Toast.makeText(context, "'${currentRecipe.name}' marked as cooked!", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Filled.OutdoorGrill, contentDescription = "Cook this Recipe")
                        }
                        OverflowMenu(menuItems = listOf(
                            "Delete Recipe" to { showDeleteRecipeConfirmDialog = true }
                        ))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            )
        },
        floatingActionButton = {
            if (recipeWithIngredients != null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    FloatingActionButton(
                        onClick = { showEditRecipeDialog = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Recipe Details")
                    }
                    FloatingActionButton(
                        onClick = { showIngredientEntryDialogForAdd = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Ingredient to Recipe")
                    }
                }
            }
        }
    ) { padding ->
        if (recipeId == -1) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Invalid Recipe ID. Please go back.")
            }
            return@Scaffold
        }
        if (recipeWithIngredients == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val recipe = recipeWithIngredients!!.recipe
            val ingredients = recipeWithIngredients!!.ingredients

            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                if (recipe.imageUri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(232.dp)
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = recipe.imageUri.toUri(),
                                    contentDescription = "${recipe.name} image",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.FillHeight,
                                    placeholder = rememberVectorPainter(Icons.Filled.RestaurantMenu),
                                    error = rememberVectorPainter(Icons.Filled.BrokenImage)
                                )
                            }
                        }
                    }
                }

                Column(modifier = Modifier.padding(top = if (recipe.imageUri == null) 16.dp else 0.dp)) {
                    if (recipe.description.isNotBlank()) {
                        Text("Description", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(recipe.description, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (!recipe.instructions.isNullOrBlank()) {
                        Text("Instructions", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(recipe.instructions, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Text("Created on: ${recipe.dateCreated.ifEmpty { "N/A" }}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Times Cooked: ${recipe.timesUsed}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(24.dp))
                }

                Text("Ingredients:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                if (ingredients.isEmpty()) {
                    Text("No ingredients added yet. Click the '+' button to add ingredients.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Column {
                        ingredients.forEach { ingredient ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = {
                                    if (it == SwipeToDismissBoxValue.StartToEnd) {
                                        ingredientToEdit = ingredient
                                        showIngredientEntryDialogForEdit = true
                                        return@rememberSwipeToDismissBoxState false
                                    }
                                    true
                                }
                            )
                            LaunchedEffect(dismissState.currentValue) {
                                if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart &&
                                    dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { dao.deleteRecipeIngredient(ingredient) }
                                        refreshRecipeDetails()
                                    }
                                }
                            }

                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = true,
                                enableDismissFromEndToStart = true,
                                backgroundContent = {
                                    val direction = dismissState.targetValue
                                    val backgroundColor: Color
                                    val iconColor: Color
                                    val alignment: Alignment
                                    val icon: androidx.compose.ui.graphics.vector.ImageVector?

                                    when (direction) {
                                        SwipeToDismissBoxValue.EndToStart -> {
                                            backgroundColor = MaterialTheme.colorScheme.errorContainer
                                            iconColor = MaterialTheme.colorScheme.onErrorContainer
                                            alignment = Alignment.CenterEnd
                                            icon = Icons.Default.Delete
                                        }
                                        SwipeToDismissBoxValue.StartToEnd -> {
                                            backgroundColor = MaterialTheme.colorScheme.secondaryContainer
                                            iconColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            alignment = Alignment.CenterStart
                                            icon = Icons.Default.Edit
                                        }
                                        else -> {
                                            backgroundColor = Color.Transparent
                                            iconColor = MaterialTheme.colorScheme.onSurface
                                            alignment = Alignment.Center
                                            icon = null
                                        }
                                    }
                                    Box(
                                        Modifier.fillMaxSize().background(backgroundColor).padding(horizontal = 16.dp),
                                        contentAlignment = alignment
                                    ) { icon?.let { Icon(imageVector = it, contentDescription = null, tint = iconColor) } }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val product = productMap[ingredient.productId]
                                ListItem(
                                    headlineContent = { Text(product?.name ?: "Unknown Product") },
                                    supportingContent = { Text("Quantity: ${ingredient.quantity} ${product?.unit ?: ""}") },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    if (showDeleteRecipeConfirmDialog && recipeWithIngredients != null) {
        val recipeToDelete = recipeWithIngredients!!.recipe
        AlertDialog(
            onDismissRequest = { showDeleteRecipeConfirmDialog = false },
            title = { Text("Delete Recipe") },
            text = { Text("Are you sure you want to delete the recipe \"${recipeToDelete.name}\"? This action cannot be undone and will remove all its ingredients.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { dao.deleteRecipe(recipeToDelete) }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "\"${recipeToDelete.name}\" deleted", Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            }
                        }
                        showDeleteRecipeConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteRecipeConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showIngredientEntryDialogForAdd) {
        IngredientEntryDialog(
            products = allProducts,
            dialogTitle = "Add Ingredient to Recipe",
            confirmButtonText = "Add",
            onDismiss = { showIngredientEntryDialogForAdd = false },
            onConfirm = { productId, quantity ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val currentIngredients = recipeWithIngredients?.ingredients ?: emptyList()
                        val existingIngredient = currentIngredients.find { it.productId == productId }
                        if (existingIngredient != null) {
                            val updatedIngredient = existingIngredient.copy(quantity = existingIngredient.quantity + quantity)
                            dao.updateRecipeIngredient(updatedIngredient)
                        } else {
                            dao.insertRecipeIngredient(RecipeIngredient(recipeId = recipeId, productId = productId, quantity = quantity))
                        }
                    }
                    refreshRecipeDetails()
                }
                showIngredientEntryDialogForAdd = false
            }
        )
    }

    if (showIngredientEntryDialogForEdit && ingredientToEdit != null) {
        val productForEdit = productMap[ingredientToEdit!!.productId]
        IngredientEntryDialog(
            products = allProducts,
            dialogTitle = "Edit Ingredient",
            confirmButtonText = "Save",
            initialSelectedProduct = productForEdit,
            initialQuantity = ingredientToEdit!!.quantity,
            onDismiss = {
                showIngredientEntryDialogForEdit = false
                ingredientToEdit = null
            },
            onConfirm = { editedProductId, editedQuantity ->
                val originalIngredient = ingredientToEdit!!
                scope.launch {
                    withContext(Dispatchers.IO) {

                        if (originalIngredient.productId == editedProductId) {

                            dao.updateRecipeIngredient(originalIngredient.copy(quantity = editedQuantity))
                        } else {

                            val existingTargetIngredient = recipeWithIngredients?.ingredients?.find {
                                it.id != originalIngredient.id && it.productId == editedProductId
                            }

                            if (existingTargetIngredient != null) {

                                dao.deleteRecipeIngredient(originalIngredient)
                                dao.updateRecipeIngredient(existingTargetIngredient.copy(quantity = existingTargetIngredient.quantity + editedQuantity))
                            } else {

                                dao.updateRecipeIngredient(originalIngredient.copy(productId = editedProductId, quantity = editedQuantity))
                            }
                        }
                    }
                    refreshRecipeDetails()
                }
                showIngredientEntryDialogForEdit = false
                ingredientToEdit = null
            }
        )
    }


    if (showEditRecipeDialog && recipeWithIngredients != null) {
        val currentRecipe = recipeWithIngredients!!.recipe
        RecipeDetailsDialog(
            initialName = currentRecipe.name,
            initialDescription = currentRecipe.description,
            initialInstructions = currentRecipe.instructions,
            initialImageUri = currentRecipe.imageUri,
            dialogTitleText = "Edit Recipe",
            confirmButtonText = "Save",
            onDismiss = { showEditRecipeDialog = false },
            onConfirm = { newName, newDescription, newInstructions, newImageUri ->
                scope.launch {
                    val updatedRecipe = currentRecipe.copy(
                        name = newName,
                        description = newDescription,
                        instructions = newInstructions,
                        imageUri = newImageUri
                    )
                    withContext(Dispatchers.IO) { dao.updateRecipe(updatedRecipe) }
                    refreshRecipeDetails()
                }
                showEditRecipeDialog = false
            }
        )
    }
}

@Composable
fun IngredientEntryDialog(
    products: List<Product>,
    onDismiss: () -> Unit,
    onConfirm: (productId: Int, quantity: Float) -> Unit,
    dialogTitle: String,
    confirmButtonText: String,
    initialSelectedProduct: Product? = null,
    initialQuantity: Float? = null
) {
    ProductSelectionEntryDialog(
        products = products,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        dialogTitle = dialogTitle,
        confirmButtonText = confirmButtonText,
        initialSelectedProduct = initialSelectedProduct,
        initialQuantity = initialQuantity
    )
}