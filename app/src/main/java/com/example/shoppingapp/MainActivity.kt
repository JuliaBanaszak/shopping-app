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
import android.net.Uri

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
                        route = "details/{title}/{description}/{notes}/{dateCreated}/{timesUsed}",
                        arguments = listOf(
                            navArgument("title") { type = NavType.StringType },
                            navArgument("description") { type = NavType.StringType },
                            navArgument("notes") { type = NavType.StringType },
                            navArgument("dateCreated") { type = NavType.StringType },
                            navArgument("timesUsed") { type = NavType.IntType }
                        )
                    ) { backStackEntry ->
                        ShoppingListDetails(
                            title = backStackEntry.arguments?.getString("title") ?: "",
                            description = backStackEntry.arguments?.getString("description") ?: "",
                            notes = backStackEntry.arguments?.getString("notes") ?: "",
                            dateCreated = backStackEntry.arguments?.getString("dateCreated") ?: "",
                            timesUsed = backStackEntry.arguments?.getInt("timesUsed") ?: 0
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

    val currentDate = Calendar.getInstance().time
    val sdf = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
    val dateString = sdf.format(currentDate)

    LaunchedEffect(Unit) {
        scope.launch {
            lists = withContext(Dispatchers.IO) {
                dao.getAllShoppingLists()
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add List")
            }
        }
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
                    navController.navigate(
                        "details/${Uri.encode(list.title)}/${Uri.encode(list.description)}/${Uri.encode(list.notes)}/${Uri.encode(list.dateCreated)}/${list.timesUsed}"
                    )
                }
            }
        }

        if (showDialog) {
            AddListDialog(
                onDismiss = { showDialog = false },
                onConfirm = { title, description, notes ->
                    scope.launch(Dispatchers.IO) {
                        dao.insertShoppingList(
                            ShoppingList(
                                title = title,
                                description = description,
                                notes = notes,
                                timesUsed = 0,
                                dateCreated = dateString,
                            )
                        )
                        lists = dao.getAllShoppingLists()
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
                .padding(16.dp),
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
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(title, description, notes)
            }) {
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
    title: String,
    description: String,
    notes: String,
    dateCreated: String,
    timesUsed: Int
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("List Details") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Title: $title", style = MaterialTheme.typography.titleLarge)
            Text("Description: $description", style = MaterialTheme.typography.bodyMedium)
            Text("Notes: $notes", style = MaterialTheme.typography.bodyMedium)
            Text("Created on: $dateCreated", style = MaterialTheme.typography.bodyMedium)
            Text("Times Used: $timesUsed", style = MaterialTheme.typography.bodyMedium)
        }
    }
}