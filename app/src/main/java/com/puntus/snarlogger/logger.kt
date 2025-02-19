package com.puntus.snarlogger

import android.content.Context
import android.os.Environment
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class logger(private val context: Context) {
    private val dataLog = mutableListOf<String>() // Список для хранения данных

    // Добавление данных в лог
    fun logData(timeStamp: String, x: Float, y: Float, z: Float) {
        dataLog.add("$timeStamp,$x,$y,$z")
    }

    // Запись данных в CSV-файл
    fun writeDataToCSV(outputStream: OutputStream) {
        try {
            outputStream.use { stream ->
                stream.write("Time,X,Y,Z\n".toByteArray()) // Заголовок CSV
                dataLog.forEach { line ->
                    stream.write("$line\n".toByteArray())
                }
            }
            Toast.makeText(context, "Файл успешно сохранен", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Ошибка при записи файла", Toast.LENGTH_SHORT).show()
        }
    }

    // Очистка лога
    fun clearLog() {
        dataLog.clear()
    }
}