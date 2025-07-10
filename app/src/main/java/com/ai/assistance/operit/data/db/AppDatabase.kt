package com.ai.assistance.operit.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ai.assistance.operit.data.dao.ChatDao
import com.ai.assistance.operit.data.dao.MessageDao
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.MessageEntity

/** 应用数据库，包含问题记录表、聊天表和消息表 */
@Database(
        entities = [ProblemEntity::class, ChatEntity::class, MessageEntity::class],
        version = 5,
        exportSchema = false
)
@TypeConverters(StringListConverter::class)
abstract class AppDatabase : RoomDatabase() {

    /** 获取问题记录DAO */
    abstract fun problemDao(): ProblemDao

    /** 获取聊天DAO */
    abstract fun chatDao(): ChatDao

    /** 获取消息DAO */
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // 定义从版本2到3的迁移
        private val MIGRATION_2_3 =
                object : Migration(2, 3) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        // 向chats表添加group列
                        db.execSQL("ALTER TABLE chats ADD COLUMN `group` TEXT")
                    }
                }

        // 定义从版本3到4的迁移
        private val MIGRATION_3_4 =
                object : Migration(3, 4) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        // 向chats表添加displayOrder列，并用updatedAt填充现有数据
                        db.execSQL(
                                "ALTER TABLE chats ADD COLUMN `displayOrder` INTEGER NOT NULL DEFAULT 0"
                        )
                        db.execSQL("UPDATE chats SET displayOrder = updatedAt")
                    }
                }
        
        // 定义从版本4到5的迁移
        private val MIGRATION_4_5 =
                object : Migration(4, 5) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        // 向chats表添加workspace列
                        db.execSQL("ALTER TABLE chats ADD COLUMN `workspace` TEXT")
                    }
                }

        /** 获取数据库实例，单例模式 */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE
                    ?: synchronized(this) {
                        val instance =
                                Room.databaseBuilder(
                                                context.applicationContext,
                                                AppDatabase::class.java,
                                                "app_database"
                                        )
                                        .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5) // 添加迁移
                                        .build()
                        INSTANCE = instance
                        instance
                    }
        }
    }
}
