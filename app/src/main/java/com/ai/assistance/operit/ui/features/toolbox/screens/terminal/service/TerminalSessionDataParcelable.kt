package com.ai.assistance.operit.ui.features.toolbox.screens.terminal.service

import android.os.Parcel
import android.os.Parcelable

/**
 * 终端会话数据的Parcelable实现
 * 表示单个终端会话的状态，包括其 ID、命令历史、当前目录等
 */
data class TerminalSessionDataParcelable(
    val sessionId: String,
    val sessionName: String,
    val currentDirectory: String,
    val currentUser: String?,
    val commandHistory: List<CommandHistoryItemParcelable>,
    val isActive: Boolean,
    val createdTime: Long
) : Parcelable {
    
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString(),
        parcel.createTypedArrayList(CommandHistoryItemParcelable.CREATOR) ?: emptyList(),
        parcel.readByte() != 0.toByte(),
        parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(sessionId)
        parcel.writeString(sessionName)
        parcel.writeString(currentDirectory)
        parcel.writeString(currentUser)
        parcel.writeTypedList(commandHistory)
        parcel.writeByte(if (isActive) 1 else 0)
        parcel.writeLong(createdTime)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<TerminalSessionDataParcelable> {
        override fun createFromParcel(parcel: Parcel): TerminalSessionDataParcelable {
            return TerminalSessionDataParcelable(parcel)
        }

        override fun newArray(size: Int): Array<TerminalSessionDataParcelable?> {
            return arrayOfNulls(size)
        }
    }
} 