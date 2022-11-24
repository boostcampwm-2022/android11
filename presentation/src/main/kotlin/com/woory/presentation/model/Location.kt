package com.woory.presentation.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Location(val geoPoint: GeoPoint, val address: String) : Parcelable

@Parcelize
data class GeoPoint(val latitude: Double, val longitude: Double) : Parcelable