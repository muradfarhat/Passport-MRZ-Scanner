package com.example.passportreader.utils

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.exp

object Utils {
    fun dateFormat(oldDate: String): String? {
        try {
            val oldFormat = SimpleDateFormat("yyMMdd", Locale.getDefault())
            val newFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault())

            val newDate: Date? = oldFormat.parse(oldDate)

            return newDate?.let { newFormat.format(it) }
        } catch (ex: Exception) {
            ex.printStackTrace()
            return ""
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun isPassExpired(expDate: String): Boolean {
        val currentDate = Calendar.getInstance()

        val day = currentDate.get(Calendar.DAY_OF_MONTH)
        val month = currentDate.get(Calendar.MONTH) + 1
        val year = currentDate.get(Calendar.YEAR) - 2000

        val passDate: MutableList<String> = expDate.split("/") as MutableList

        if (passDate[2].toInt() > (year + 10)) {
            passDate[2] = (passDate[2].toInt() + 1900).toString()
        } else passDate[2] = (passDate[2].toInt() + 2000).toString()

        val firstDate = LocalDate.of(year + 2000, month, day)
        val secondDate = LocalDate.of(passDate[2].toInt(), passDate[1].toInt(), passDate[0].toInt())

        return firstDate.isBefore(secondDate)
    }
}