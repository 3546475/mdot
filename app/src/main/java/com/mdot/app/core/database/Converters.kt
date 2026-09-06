package com.mdot.app.core.database

import androidx.room.TypeConverter
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun dateToString(date: LocalDate): String = date.toString()

    @TypeConverter
    fun stringToDate(value: String): LocalDate = LocalDate.parse(value)
}
