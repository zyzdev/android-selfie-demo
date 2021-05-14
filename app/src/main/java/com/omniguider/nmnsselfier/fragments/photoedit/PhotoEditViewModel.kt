package com.omniguider.nmnsselfier.fragments.photoedit

import android.app.Application
import android.os.Parcel
import android.os.Parcelable
import androidx.lifecycle.AndroidViewModel

class PhotoEditViewModel(application: Application) : AndroidViewModel(application) {

}

data class FrameSelectionStatus(var drawableRes: Int, var selected: Boolean? = false) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readValue(Boolean::class.java.classLoader) as? Boolean
    )

    override fun toString(): String {
        return "FrameSelectionStatus(drawableRes=$drawableRes, selected=$selected)"
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(drawableRes)
        parcel.writeValue(selected)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<FrameSelectionStatus> {
        override fun createFromParcel(parcel: Parcel): FrameSelectionStatus {
            return FrameSelectionStatus(parcel)
        }

        override fun newArray(size: Int): Array<FrameSelectionStatus?> {
            return arrayOfNulls(size)
        }
    }
}

data class ColorSelectionStatus(var color: Int, var selected: Boolean? = false) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readValue(Boolean::class.java.classLoader) as? Boolean
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(color)
        parcel.writeValue(selected)
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun toString(): String {
        return "ColorSelectionStatus(color=$color, selected=$selected)"
    }

    companion object CREATOR : Parcelable.Creator<ColorSelectionStatus> {
        override fun createFromParcel(parcel: Parcel): ColorSelectionStatus {
            return ColorSelectionStatus(parcel)
        }

        override fun newArray(size: Int): Array<ColorSelectionStatus?> {
            return arrayOfNulls(size)
        }
    }

}