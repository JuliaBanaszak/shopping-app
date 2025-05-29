package com.example.shoppingapp

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.shoppingapp.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ShoppingListScreenFABs(
    onAddListClick: () -> Unit,
    onStartShoppingClick: () -> Unit,
    onShareListClick: () -> Unit,
    onScanListClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.End
    ) {
        FloatingActionButton(
            onClick = onStartShoppingClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Default.ShoppingCartCheckout, contentDescription = "Start Shopping Session")
        }

        FloatingActionButton(onClick = onShareListClick) {
            Icon(Icons.Default.Share, contentDescription = "Generate Shopping List QR Code")
        }

        FloatingActionButton(onClick = onScanListClick) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR to Add Shopping List")
        }

        FloatingActionButton(
            onClick = onAddListClick
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add New Shopping List")
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    db: ShoppingDatabase,
    navController: NavHostController,
    showAddListDialogInitially: Boolean,
    onDismissAddListDialog: () -> Unit
) {
    val dao = db.shoppingDao()
    var lists by remember { mutableStateOf(listOf<ShoppingList>()) }
    val scope = rememberCoroutineScope()
    val currentDate = Calendar.getInstance().time
    val sdf = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
    val dateString = sdf.format(currentDate)

    val lifecycleOwner = LocalLifecycleOwner.current

    fun refreshLists() {
        scope.launch {
            val updatedLists = withContext(Dispatchers.IO) {
                dao.getAllShoppingLists()
            }
            lists = updatedLists
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshLists()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        refreshLists()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (lists.isEmpty()){
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center){
                Text("No shopping lists yet. Click '+' to add one!", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(lists, key = { "list-card-${it.id}" }) { list ->
                    ShoppingListCard(list = list) {
                        navController.navigate("details/${list.id}")
                    }
                }
            }
        }

        if (showAddListDialogInitially) {
            ListDetailsDialog(
                dialogTitleText = "New Shopping List",
                confirmButtonText = "Add",
                onDismiss = onDismissAddListDialog,
                onConfirm = { title, description, notes, imageUriString ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            dao.insertShoppingList(
                                ShoppingList(
                                    title = title,
                                    description = description,
                                    notes = notes,
                                    timesUsed = 0,
                                    dateCreated = dateString,
                                    imageUri = imageUriString
                                )
                            )
                        }
                        refreshLists()
                    }
                    onDismissAddListDialog()
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
                    modifier = Modifier
                        .fillMaxSize(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (list.imageUri != null) {
                            AsyncImage(
                                model = list.imageUri.toUri(),
                                contentDescription = list.title + " thumbnail",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.FillHeight,
                                placeholder = rememberVectorPainter(Icons.Filled.Image),
                                error = rememberVectorPainter(Icons.Filled.BrokenImage)
                            )
                        } else {
                            Icon(
                                Icons.Filled.ShoppingCart,
                                contentDescription = "No image for ${list.title}",
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
                    text = list.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}


@Composable
fun ListDetailsDialog(
    initialTitle: String = "",
    initialDescription: String = "",
    initialNotes: String = "",
    initialImageUri: String? = null,
    dialogTitleText: String,
    confirmButtonText: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String, notes: String, imageUri: String?) -> Unit
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var description by remember(initialDescription) { mutableStateOf(initialDescription) }
    var notes by remember(initialNotes) { mutableStateOf(initialNotes) }
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
                Log.e("ListDetailsDialog", "Failed to take persistable URI permission for $it", e)
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
                    minLines = 2
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text("List Thumbnail (Optional)", style = MaterialTheme.typography.labelLarge)
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
                            placeholder = rememberVectorPainter(Icons.Filled.Image),
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
                    if (title.isNotBlank()) {
                        onConfirm(title, description, notes, selectedImageUriState?.toString())
                    }
                },
                enabled = title.isNotBlank()
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
fun ShoppingListDetails(
    listId: Int,
    db: ShoppingDatabase,
    navController: NavHostController
) {
    val dao = db.shoppingDao()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var shoppingListWithItems by remember { mutableStateOf<ShoppingListWithItems?>(null) }
    var allProducts by remember { mutableStateOf<List<Product>>(emptyList()) }
    val productMap: Map<Int, Product> by remember(allProducts) {
        derivedStateOf { allProducts.associateBy { it.id } }
    }

    var showItemEntryDialogForAdd by remember { mutableStateOf(false) }
    var showItemEntryDialogForEdit by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<ShoppingListItem?>(null) }
    var showEditListDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }


    fun refreshListDetails() {
        if (listId == -1) {
            return
        }
        scope.launch {
            val fetchedListWithItems = withContext(Dispatchers.IO) {
                dao.getShoppingListWithItems(listId)
            }
            if (allProducts.isEmpty()) {
                val fetchedProducts = withContext(Dispatchers.IO) {
                    dao.getAllProducts()
                }
                allProducts = fetchedProducts
            }
            shoppingListWithItems = fetchedListWithItems
            if (fetchedListWithItems == null && listId != -1) {
                Toast.makeText(context, "List not found.", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
        }
    }

    LaunchedEffect(listId) {
        if (listId != -1) {
            refreshListDetails()
        } else {
            Toast.makeText(context, "Invalid List ID.", Toast.LENGTH_SHORT).show()
            navController.popBackStack()
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
                },
                actions = {
                    if (shoppingListWithItems != null) {
                        OverflowMenu(menuItems = listOf(
                            "Delete List" to { showDeleteConfirmDialog = true }
                        ))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        },
        floatingActionButton = {
            if (shoppingListWithItems != null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    FloatingActionButton(onClick = { showEditListDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit List Details")
                    }
                    FloatingActionButton(onClick = { showItemEntryDialogForAdd = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Item to List")
                    }
                }
            }
        }
    ) { padding ->
        if (listId == -1) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Invalid List ID. Please go back.")
            }
            return@Scaffold
        }
        if (shoppingListWithItems == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val list = shoppingListWithItems!!.shoppingList
            val items = shoppingListWithItems!!.items

            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .fillMaxSize(),
            ) {
                if (list.imageUri != null) {
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
                                    model = list.imageUri.toUri(),
                                    contentDescription = "${list.title} thumbnail",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.FillHeight,
                                    placeholder = rememberVectorPainter(Icons.Filled.Image),
                                    error = rememberVectorPainter(Icons.Filled.BrokenImage)
                                )
                            }
                        }
                    }
                }

                Column(modifier = Modifier.padding(top = if (list.imageUri == null) 16.dp else 0.dp).verticalScroll(rememberScrollState())) {
                    if (list.description.isNotBlank()) {
                        Text("Description", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(list.description, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (list.notes.isNotBlank()) {
                        Text("Notes", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(list.notes, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Text("Created on: ${list.dateCreated}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Times Used: ${list.timesUsed}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(24.dp))


                    Text("Items in this list:", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (items.isEmpty()) {
                        Text("No items added yet. Click the '+' button to add items.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Column {
                            items.forEach { item ->
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = {
                                        if (it == SwipeToDismissBoxValue.StartToEnd) {
                                            itemToEdit = item
                                            showItemEntryDialogForEdit = true
                                            return@rememberSwipeToDismissBoxState false
                                        }
                                        true
                                    }
                                )

                                LaunchedEffect(dismissState.currentValue) {
                                    if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart &&
                                        dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
                                    ) {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                dao.deleteShoppingListItem(item)
                                            }
                                            refreshListDetails()
                                        }
                                    }
                                }

                                SwipeToDismissBox(
                                    state = dismissState,
                                    enableDismissFromStartToEnd = true,
                                    enableDismissFromEndToStart = true,
                                    backgroundContent = {
                                        val direction = dismissState.targetValue
                                        val backgroundColor = when (direction) {
                                            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                            SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.secondaryContainer
                                            else -> Color.Transparent
                                        }
                                        val iconColor = when (direction) {
                                            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.onErrorContainer
                                            SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.onSecondaryContainer
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                        val alignment = when (direction) {
                                            SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                            SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                            else -> Alignment.Center
                                        }
                                        val icon = when (direction) {
                                            SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
                                            SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Edit
                                            else -> null
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(backgroundColor)
                                                .padding(horizontal = 16.dp),
                                            contentAlignment = alignment
                                        ) {
                                            icon?.let {
                                                Icon(it, contentDescription = null, tint = iconColor)
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val product = productMap[item.productId]
                                    ListItem(
                                        headlineContent = { Text(product?.name ?: "Unknown Product") },
                                        supportingContent = {
                                            Text("Quantity: ${item.quantity} ${product?.unit ?: ""}")
                                        },
                                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }

    if (showDeleteConfirmDialog && shoppingListWithItems != null) {
        val listToDelete = shoppingListWithItems!!.shoppingList
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete List") },
            text = { Text("Are you sure you want to delete the list \"${listToDelete.title}\"? This action cannot be undone and will remove all items in this list.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                dao.deleteShoppingList(listToDelete)
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "\"${listToDelete.title}\" deleted", Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            }
                        }
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }


    if (showItemEntryDialogForAdd) {
        ItemEntryDialog(
            products = allProducts,
            dialogTitle = "Add Item to List",
            confirmButtonText = "Add",
            onDismiss = { showItemEntryDialogForAdd = false },
            onConfirm = { productId, quantity ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val currentItems = shoppingListWithItems?.items ?: emptyList()
                        val existingItem = currentItems.find { it.productId == productId }

                        if (existingItem != null) {
                            val updatedItem = existingItem.copy(quantity = existingItem.quantity + quantity)
                            dao.updateShoppingListItem(updatedItem)
                        } else {
                            val newItem = ShoppingListItem(
                                listId = listId,
                                productId = productId,
                                quantity = quantity
                            )
                            dao.insertShoppingListItem(newItem)
                        }
                    }
                    refreshListDetails()
                }
                showItemEntryDialogForAdd = false
            }
        )
    }

    if (showItemEntryDialogForEdit && itemToEdit != null) {
        val productForEdit = productMap[itemToEdit!!.productId]
        ItemEntryDialog(
            products = allProducts,
            dialogTitle = "Edit Item",
            confirmButtonText = "Save",
            initialSelectedProduct = productForEdit,
            initialQuantity = itemToEdit!!.quantity,
            onDismiss = {
                showItemEntryDialogForEdit = false
                itemToEdit = null
            },
            onConfirm = { editedProductId, editedQuantity ->
                val originalItem = itemToEdit!!
                scope.launch {
                    withContext(Dispatchers.IO) {
                        if (originalItem.productId == editedProductId) {
                            val updatedItem = originalItem.copy(quantity = editedQuantity)
                            dao.updateShoppingListItem(updatedItem)
                        } else {
                            val existingTargetItem = shoppingListWithItems?.items?.find {
                                it.id != originalItem.id && it.productId == editedProductId
                            }
                            if (existingTargetItem != null) {
                                dao.deleteShoppingListItem(originalItem)
                                val mergedItem = existingTargetItem.copy(quantity = existingTargetItem.quantity + editedQuantity)
                                dao.updateShoppingListItem(mergedItem)
                            } else {
                                val updatedItem = originalItem.copy(productId = editedProductId, quantity = editedQuantity)
                                dao.updateShoppingListItem(updatedItem)
                            }
                        }
                    }
                    refreshListDetails()
                }
                showItemEntryDialogForEdit = false
                itemToEdit = null
            }
        )
    }

    if (showEditListDialog && shoppingListWithItems != null) {
        val currentList = shoppingListWithItems!!.shoppingList
        ListDetailsDialog(
            initialTitle = currentList.title,
            initialDescription = currentList.description,
            initialNotes = currentList.notes,
            initialImageUri = currentList.imageUri,
            dialogTitleText = "Edit Shopping List",
            confirmButtonText = "Save",
            onDismiss = { showEditListDialog = false },
            onConfirm = { newTitle, newDescription, newNotes, newImageUriString ->
                scope.launch {
                    val updatedList = currentList.copy(
                        title = newTitle,
                        description = newDescription,
                        notes = newNotes,
                        imageUri = newImageUriString
                    )
                    withContext(Dispatchers.IO) {
                        dao.updateShoppingList(updatedList)
                    }
                    refreshListDetails()
                }
                showEditListDialog = false
            }
        )
    }
}

@Composable
fun ItemEntryDialog(
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

enum class ItemSourceType { LIST, RECIPE }

data class MergedShoppingItemSource(
    val type: ItemSourceType,
    val name: String,
    val icon: ImageVector
)

data class MergedShoppingItem(
    val productId: Int,
    val productName: String,
    val productUnit: String,
    var totalQuantity: Float,
    var isBought: MutableState<Boolean>,
    val sources: List<MergedShoppingItemSource>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveShoppingScreen(
    listIdsString: String?,
    recipeIdsString: String?,
    db: ShoppingDatabase,
    navController: NavHostController
) {
    val dao = db.shoppingDao()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var mergedItems by remember { mutableStateOf<List<MergedShoppingItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val originalListIds = remember(listIdsString) {
        listIdsString?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
    }
    val originalRecipeIds = remember(recipeIdsString) {
        recipeIdsString?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
    }

    LaunchedEffect(originalListIds, originalRecipeIds) {
        if (originalListIds.isEmpty() && originalRecipeIds.isEmpty()) {
            Toast.makeText(context, "Error: No lists or recipes specified for shopping.", Toast.LENGTH_LONG).show()
            navController.popBackStack()
            return@LaunchedEffect
        }

        isLoading = true
        scope.launch {
            val allProductsMap = withContext(Dispatchers.IO) { dao.getAllProducts().associateBy { it.id } }
            val allIndividualSourceItems = mutableListOf<Triple<Int, Float, MergedShoppingItemSource>>()

            originalListIds.forEach { listId ->
                val listWithItems = withContext(Dispatchers.IO) { dao.getShoppingListWithItems(listId) }
                listWithItems?.let { listData ->
                    val source = MergedShoppingItemSource(ItemSourceType.LIST, listData.shoppingList.title,
                        Icons.AutoMirrored.Filled.ListAlt
                    )
                    listData.items.forEach { item ->
                        allIndividualSourceItems.add(Triple(item.productId, item.quantity, source))
                    }
                }
            }

            originalRecipeIds.forEach { recipeId ->
                val recipeWithIngredients = withContext(Dispatchers.IO) { dao.getRecipeWithIngredients(recipeId) }
                recipeWithIngredients?.let { recipeData ->
                    val source = MergedShoppingItemSource(ItemSourceType.RECIPE, recipeData.recipe.name, Icons.Default.RestaurantMenu)
                    recipeData.ingredients.forEach { ingredient ->
                        allIndividualSourceItems.add(Triple(ingredient.productId, ingredient.quantity, source))
                    }
                }
            }

            val groupedAndMergedItems = allIndividualSourceItems
                .groupBy { it.first }
                .map { (productId, itemsData) ->
                    val product = allProductsMap[productId] ?: Product(id = productId, name = "Unknown Product", unit = "")
                    val totalQuantity = itemsData.sumOf { it.second.toDouble() }.toFloat()
                    val distinctSources = itemsData.map { it.third }.distinctBy { it.type.toString() + it.name }

                    MergedShoppingItem(
                        productId = productId,
                        productName = product.name,
                        productUnit = product.unit,
                        totalQuantity = totalQuantity,
                        isBought = mutableStateOf(false),
                        sources = distinctSources
                    )
                }
                .sortedBy { it.productName }

            mergedItems = groupedAndMergedItems
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Active Shopping Session") },
                navigationIcon = {
                    IconButton(onClick = {
                        Toast.makeText(context, "Shopping Cancelled", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel Shopping and Go Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        Toast.makeText(context, "Shopping Cancelled", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.Cancel, contentDescription = "Cancel Icon", modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                originalListIds.forEach { listId ->
                                    val list = dao.getShoppingListWithItems(listId)?.shoppingList
                                    list?.let {
                                        val updatedList = it.copy(timesUsed = it.timesUsed + 1)
                                        dao.updateShoppingList(updatedList)
                                    }
                                }
                                originalRecipeIds.forEach { recipeId ->
                                    val recipe = dao.getRecipeWithIngredients(recipeId)?.recipe
                                    recipe?.let {
                                        val updatedRecipe = it.copy(timesUsed = it.timesUsed + 1)
                                        dao.updateRecipe(updatedRecipe)
                                    }
                                }
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Shopping Finished!", Toast.LENGTH_LONG).show()
                                navController.popBackStack()
                            }
                        }
                    },
                    enabled = !isLoading && mergedItems.isNotEmpty()
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Finish Icon", modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Finish")
                }
            }
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (mergedItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("No items to shop for from the selected lists/recipes, or they were empty.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            ) {
                items(mergedItems, key = { "merged-item-${it.productId}" }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { item.isBought.value = !item.isBought.value }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = item.isBought.value,
                            onCheckedChange = { item.isBought.value = it }
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.productName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textDecoration = if (item.isBought.value) TextDecoration.LineThrough else null,
                                    color = if (item.isBought.value) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.width(8.dp))
                                item.sources.forEach { source ->
                                    Icon(
                                        imageVector = source.icon,
                                        contentDescription = source.type.name,
                                        modifier = Modifier.size(18.dp).padding(end = 2.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            Text(
                                text = "Quantity: ${item.totalQuantity} ${item.productUnit}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textDecoration = if (item.isBought.value) TextDecoration.LineThrough else null
                            )
                            if (item.sources.size > 1 || (item.sources.isNotEmpty() && (item.sources.first().name.length > 20 || item.sources.size ==1 ))) {
                                item.sources.forEach { source ->
                                    Text(
                                        text = "${source.type.name.lowercase().replaceFirstChar { it.uppercase() }}: ${source.name}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp, end = 16.dp))
                }
            }
        }
    }
}