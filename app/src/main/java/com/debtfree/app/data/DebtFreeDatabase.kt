package com.debtfree.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [LoanEntity::class, GoldItemEntity::class, PaymentEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class DebtFreeDatabase : RoomDatabase() {
    abstract fun loanDao(): LoanDao

    companion object {
        fun create(context: Context): DebtFreeDatabase =
            Room.databaseBuilder(context, DebtFreeDatabase::class.java, "debt-free.db").build()

        fun inMemory(context: Context): DebtFreeDatabase =
            Room.inMemoryDatabaseBuilder(context, DebtFreeDatabase::class.java).build()
    }
}
