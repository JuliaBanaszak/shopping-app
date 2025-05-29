package com.example.shoppingapp.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.concurrent.Executors

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val unit: String,
    val calories: Int? = null,
    val allergens: String? = null,
    val description: String? = null,
    val brand: String? = null,
    val category: String? = null
)

@Entity(tableName = "shopping_lists")
data class ShoppingList(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val dateCreated: String,
    val description: String,
    val notes: String,
    val timesUsed: Int,
    val imageUri: String? = null
)

@Entity(
    tableName = "shopping_list_items",
    foreignKeys = [
        ForeignKey(
            entity = ShoppingList::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
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
    val description: String,
    val instructions: String? = null,
    val imageUri: String? = null,
    val dateCreated: String,
    val timesUsed: Int
)

@Entity(
    tableName = "recipe_ingredients",
    foreignKeys = [
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("recipeId"), Index("productId")]
)
data class RecipeIngredient(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val recipeId: Int,
    val productId: Int,
    val quantity: Float
)

data class ShoppingListWithItems(
    @Embedded val shoppingList: ShoppingList,
    @Relation(
        parentColumn = "id",
        entityColumn = "listId"
    )
    val items: List<ShoppingListItem>
)

data class RecipeWithIngredients(
    @Embedded val recipe: Recipe,
    @Relation(
        parentColumn = "id",
        entityColumn = "recipeId"
    )
    val ingredients: List<RecipeIngredient>
)

@Dao
interface ShoppingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertProduct(product: Product): Long

    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): List<Product>

    @Query("SELECT * FROM products WHERE id = :productId")
    fun getProductById(productId: Int): Product?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertShoppingList(list: ShoppingList): Long

    @Query("SELECT * FROM shopping_lists ORDER BY dateCreated DESC")
    fun getAllShoppingLists(): List<ShoppingList>

    @Transaction
    @Query("SELECT * FROM shopping_lists ORDER BY dateCreated DESC")
    fun getShoppingListsWithItems(): List<ShoppingListWithItems>

    @Transaction
    @Query("SELECT * FROM shopping_lists WHERE id = :listId")
    fun getShoppingListWithItems(listId: Int): ShoppingListWithItems?

    @Update
    fun updateShoppingList(list: ShoppingList)

    @Delete
    fun deleteShoppingList(list: ShoppingList)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertShoppingListItem(item: ShoppingListItem): Long

    @Transaction
    @Query("SELECT * FROM shopping_list_items WHERE listId = :listId")
    fun getItemsForList(listId: Int): List<ShoppingListItem>

    @Update
    fun updateShoppingListItem(item: ShoppingListItem)

    @Delete
    fun deleteShoppingListItem(item: ShoppingListItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRecipe(recipe: Recipe): Long

    @Query("SELECT * FROM recipes ORDER BY name ASC")
    fun getAllRecipes(): List<Recipe>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :recipeId")
    fun getRecipeWithIngredients(recipeId: Int): RecipeWithIngredients?

    @Update
    fun updateRecipe(recipe: Recipe)

    @Delete
    fun deleteRecipe(recipe: Recipe)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRecipeIngredient(ingredient: RecipeIngredient): Long

    @Transaction
    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :recipeId")
    fun getIngredientsForRecipe(recipeId: Int): List<RecipeIngredient>

    @Update
    fun updateRecipeIngredient(ingredient: RecipeIngredient)

    @Delete
    fun deleteRecipeIngredient(ingredient: RecipeIngredient)
}

@Database(
    entities = [
        Product::class,
        ShoppingList::class,
        ShoppingListItem::class,
        Recipe::class,
        RecipeIngredient::class
    ],
    version = 1,
    exportSchema = true
)
abstract class ShoppingDatabase : RoomDatabase() {

    abstract fun shoppingDao(): ShoppingDao

    companion object {
        @Volatile
        private var INSTANCE: ShoppingDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shopping_lists ADD COLUMN imageUri TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN instructions TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN imageUri TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE recipes ADD COLUMN dateCreated TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE recipes ADD COLUMN timesUsed INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN calories INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE products ADD COLUMN allergens TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE products ADD COLUMN description TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE products ADD COLUMN brand TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE products ADD COLUMN category TEXT DEFAULT NULL")
            }
        }
        fun getDatabase(context: Context): ShoppingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ShoppingDatabase::class.java,
                    "shopping_database"
                )

                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            Executors.newSingleThreadExecutor().execute {
                                val dao = getDatabase(context.applicationContext).shoppingDao()
                                dao.insertProduct(Product(name = "Milk", unit = "l", brand = "Mlekovita", category = "Dairy", calories = 60, allergens = "Lactose"))
                                dao.insertProduct(Product(name = "Bread", unit = "pcs", brand = "Golden Ear Bakery", category = "Bakery", calories = 250, allergens = "Gluten", description = "Traditional wheat-rye bread."))
                                dao.insertProduct(Product(name = "Butter", unit = "g", brand = "Łaciate", category = "Dairy", calories = 700, allergens = "Lactose"))
                                dao.insertProduct(Product(name = "Eggs", unit = "pcs", category = "Dairy", calories = 78, description = "Fresh free-range eggs."))
                                dao.insertProduct(Product(name = "Flour", unit = "kg", brand = "Basia", category = "Dry Goods", calories = 360, allergens = "Gluten"))
                                dao.insertProduct(Product(name = "Sugar", unit = "kg", category = "Dry Goods", calories = 400))
                                dao.insertProduct(Product(name = "Apples", unit = "kg", category = "Fruits", calories = 52, description = "Juicy Gala apples."))
                                dao.insertProduct(Product(name = "Chicken Breast", unit = "kg", category = "Meat", calories = 165, description = "Fresh chicken breast, perfect for baking or frying."))
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}