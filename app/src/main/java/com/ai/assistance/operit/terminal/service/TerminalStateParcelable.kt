package com.ai.assistance.operit.terminal.service

import android.os.Parcel
import android.os.Parcelable
import com.ai.assistance.operit.ui.features.toolbox.screens.terminal.service.TerminalSessionDataParcelable

/**
 * 终端状态的Parcelable实现
 * 表示整个终端的完整状态，包括所有会话列表和当前会话ID
 */
data class TerminalStateParcelable(
    val sessions: List<TerminalSessionDataParcelable>,
    val currentSessionId: String?,
    val lastUpdated: Long
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.createTypedArrayList(TerminalSessionDataParcelable) ?: emptyList(),
        parcel.readString(),
        parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeTypedList(sessions)
        parcel.writeString(currentSessionId)
        parcel.writeLong(lastUpdated)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<TerminalStateParcelable> {
        override fun createFromParcel(parcel: Parcel): TerminalStateParcelable {
            return TerminalStateParcelable(parcel)
        }

        override fun newArray(size: Int): Array<TerminalStateParcelable?> {
            return arrayOfNulls(size)
        }
    }
}