package com.aetherion.nfcwallet

import android.app.Application
import androidx.room.Room
import com.aetherion.nfcwallet.data.CardRepository
import com.aetherion.nfcwallet.data.WalletDatabase

class WalletApp : Application() {
    private val database: WalletDatabase by lazy {
        Room.databaseBuilder(this, WalletDatabase::class.java, "wallet.db").build()
    }

    val repository: CardRepository by lazy { CardRepository(database.cardDao()) }
}
