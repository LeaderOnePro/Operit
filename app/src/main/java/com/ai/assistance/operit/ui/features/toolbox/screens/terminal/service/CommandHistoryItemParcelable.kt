package com.ai.assistance.operit.ui.features.toolbox.screens.terminal.service

import android.os.Parcel
import android.os.Parcelable

/**
 * 命令历史项的Parcelable实现
 * 表示终端中的单个命令历史条目，包括命令文本、时间戳、输出等
 */
data class CommandHistoryItemParcelable(
    val command: String,
    val timestamp: Long,
    val output: String,
    val exitCode: Int,
    val workingDirectory: String
) : Parcelable {
    
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readLong(),
        parcel.readString() ?: "",
        parcel.readInt(),
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(command)
        parcel.writeLong(timestamp)
        parcel.writeString(output)
        parcel.writeInt(exitCode)
        parcel.writeString(workingDirectory)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<CommandHistoryItemParcelable> {
        override fun createFromParcel(parcel: Parcel): CommandHistoryItemParcelable {
            return CommandHistoryItemParcelable(parcel)
        }

        override fun newArray(size: Int): Array<CommandHistoryItemParcelable?> {
            return arrayOfNulls(size)
        }
    }
} 