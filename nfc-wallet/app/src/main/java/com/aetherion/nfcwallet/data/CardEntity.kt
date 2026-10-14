package com.aetherion.nfcwallet.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * The database holds nothing readable: a random id plus two AES-GCM blobs.
 * No names, UIDs or timestamps are stored in the clear.
 */
@Entity(tableName = "cards")
class CardEntity(
    @PrimaryKey val id: String,
    val metaBlob: ByteArray,
    val dumpBlob: ByteArray,
)

@Dao
interface CardDao {
    @Query("SELECT * FROM cards")
    fun observeAll(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun get(id: String): CardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CardEntity)

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM cards")
    suspend fun deleteAll()
}

@Database(entities = [CardEntity::class], version = 1, exportSchema = false)
abstract class WalletDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
}
