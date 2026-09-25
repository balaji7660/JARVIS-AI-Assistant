package com.jarvis.assistant.memory

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MemoryEntity::class,
        NoteEntity::class,
        ReminderEntity::class,
        TimerEntity::class,
        ConversationEntity::class,
        MessageEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class JarvisDatabase : RoomDatabase() {

    abstract fun memoryDao(): MemoryDao
    abstract fun noteDao(): NoteDao
    abstract fun reminderDao(): ReminderDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao

    companion object {
        private const val DATABASE_NAME = "jarvis_memory.db"

        @Volatile
        private var instance: JarvisDatabase? = null

        fun getInstance(context: Context): JarvisDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    JarvisDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
