package com.example.shoppingapp.data

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.concurrent.Executors

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val unit: String
)

@Entity(tableName = "shopping_lists")
data class ShoppingList(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val dateCreated: String,
    val description: String,
    val notes: String,
    val timesUsed: Int
)

data class ShoppingListWithItems(
    @Embedded val shoppingList: ShoppingList,
    @Relation(
        parentColumn = "id",
        entityColumn = "listId"
    )
    val items: List<ShoppingListItem>
)

@Entity(
    tableName = "shopping_list_items",
    foreignKeys = [
        ForeignKey(entity = ShoppingList::class, parentColumns = ["id"], childColumns = ["listId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Product::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("listId"), Index("productId")]
)

data class ShoppingListItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val listId: Int,
    val productId: Int,
    val quantity: Float
)

@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String
)

@Entity(
    tableName = "recipe_ingredients",
    foreignKeys = [
        ForeignKey(entity = Recipe::class, parentColumns = ["id"], childColumns = ["recipeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Product::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("recipeId"), Index("productId")]
)
data class RecipeIngredient(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val recipeId: Int,
    val productId: Int,
    val quantity: Float
)

@Dao
interface ShoppingDao {
    @Insert fun insertProduct(product: Product): Long
    @Insert fun insertShoppingList(list: ShoppingList): Long
    @Insert fun insertShoppingListItem(item: ShoppingListItem): Long
    @Insert fun insertRecipe(recipe: Recipe): Long
    @Insert fun insertRecipeIngredient(ingredient: RecipeIngredient): Long

    @Query("SELECT * FROM products")
    fun getAllProducts(): List<Product>

    @Query("SELECT * FROM shopping_lists")
    fun getAllShoppingLists(): List<ShoppingList>

    @Transaction
    @Query("SELECT * FROM shopping_list_items WHERE listId = :listId")
    fun getItemsForList(listId: Int): List<ShoppingListItem>

    @Transaction
    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :recipeId")
    fun getIngredientsForRecipe(recipeId: Int): List<RecipeIngredient>

    @Transaction
    @Query("SELECT * FROM shopping_lists")
    fun getShoppingListsWithItems(): List<ShoppingListWithItems>

    @Transaction
    @Query("SELECT * FROM shopping_lists WHERE id = :listId")
    fun getShoppingListWithItems(listId: Int): ShoppingListWithItems? // Changed to nullable
}

@Database(
    entities = [Product::class, ShoppingList::class, ShoppingListItem::class, Recipe::class, RecipeIngredient::class],
    version = 2 // Keep version or increment if schema changed and migrations aren't handled (not changed here)
)

abstract class ShoppingDatabase : RoomDatabase() {
    abstract fun shoppingDao(): ShoppingDao

    companion object {
        @Volatile
        private var INSTANCE: ShoppingDatabase? = null

        fun getDatabase(context: Context): ShoppingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ShoppingDatabase::class.java,
                    "shopping_database"
                ).fallbackToDestructiveMigration()
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            Executors.newSingleThreadExecutor().execute {
                                val dao = getDatabase(context).shoppingDao() // Careful with re-entrant getDatabase
                                // Pre-populate data
                                dao.insertProduct(Product(name = "Mleko", unit = "ml"))
                                dao.insertProduct(Product(name = "Chleb", unit = "szt"))
                                dao.insertProduct(Product(name = "Masło", unit = "g"))
                                dao.insertProduct(Product(name = "Jajka", unit = "szt"))
                                dao.insertProduct(Product(name = "Mąka", unit = "g"))
                                dao.insertProduct(Product(name = "Cukier", unit = "kg"))
                                dao.insertProduct(Product(name = "Sól", unit = "g"))
                                dao.insertProduct(Product(name = "Ryż", unit = "kg"))
                                dao.insertProduct(Product(name = "Makaron", unit = "g"))
                            }
                        }
                    })
                    // .fallbackToDestructiveMigration() // Add if schema changes and you don't want to write migrations
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}