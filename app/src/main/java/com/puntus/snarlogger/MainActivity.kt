package com.puntus.snarlogger

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.utils.ColorTemplate
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var xAccelTextView: TextView
    private lateinit var yAccelTextView: TextView
    private lateinit var zAccelTextView: TextView
    private lateinit var lineChart: LineChart
    private lateinit var accelerometerLogger: logger
    private lateinit var sensorSpinner: Spinner
    private lateinit var titleTextView: TextView

    private val xEntries = ArrayList<Entry>()
    private val yEntries = ArrayList<Entry>()
    private val zEntries = ArrayList<Entry>()
    private var startTime = 0L // Время начала измерений
    private var elapsedTime = 0f // Общее прошедшее время

    private val createFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            if (uri != null) {
                try {
                    contentResolver.openOutputStream(uri)?.let { outputStream ->
                        accelerometerLogger.writeDataToCSV(outputStream)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Ошибка при сохранении файла", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        xAccelTextView = findViewById(R.id.xAccelTextView)
        yAccelTextView = findViewById(R.id.yAccelTextView)
        zAccelTextView = findViewById(R.id.zAccelTextView)
        lineChart = findViewById(R.id.lineChart)
        val recordButton = findViewById<Button>(R.id.recordButton)
        val clearButton = findViewById<Button>(R.id.clearButton)
        val aboutButton = findViewById<Button>(R.id.aboutButton)
        sensorSpinner = findViewById(R.id.sensorSpinner)
        titleTextView = findViewById(R.id.titleTextView)

        startTime = System.currentTimeMillis()

        setupChart()

        accelerometerLogger = logger(this)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // Настройка Spinner
        val sensorTypes = arrayOf("Акселерометр", "Линейное ускорение", "Гироскоп")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sensorTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sensorSpinner.adapter = adapter

        sensorSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Очистка графика и данных
                clearChart()
                // Обновление заголовка и описания
                updateTitle(position)
                // Регистрация нового датчика
                sensorManager.unregisterListener(this@MainActivity)
                when (position) {
                    0 -> registerSensor(Sensor.TYPE_ACCELEROMETER)
                    1 -> registerSensor(Sensor.TYPE_LINEAR_ACCELERATION)
                    2 -> registerSensor(Sensor.TYPE_GYROSCOPE)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Ничего не делаем
            }
        }

        recordButton.setOnClickListener {
            openFilePicker()
        }

        clearButton.setOnClickListener {
            accelerometerLogger.clearLog()
            clearChart()
            elapsedTime = 0f
            startTime = System.currentTimeMillis()
        }

        aboutButton.setOnClickListener {
            showAboutDialog()
        }
    }

    private fun clearChart() {
        xEntries.clear()
        yEntries.clear()
        zEntries.clear()
        lineChart.clear()
        lineChart.invalidate() // Обновление графика
    }

    private fun updateTitle(position: Int) {
        val title = when (position) {
            0 -> "Акселерометр"
            1 -> "Линейное ускорение"
            2 -> "Гироскоп"
            else -> "Датчик"
        }
        titleTextView.text = title

        // Обновление описания графика
        val description = when (position) {
            0 -> "Ускорение от времени (акселерометр)"
            1 -> "Ускорение от времени (линейное ускорение)"
            2 -> "Угловая скорость от времени (гироскоп)"
            else -> "Данные от времени"
        }
        lineChart.description.text = description
    }

    private fun registerSensor(sensorType: Int) {
        val sensor = sensorManager.getDefaultSensor(sensorType)
        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            Toast.makeText(this, "Датчик не доступен", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupChart() {
        lineChart.description.text = "Ускорение от времени"
        lineChart.description.textSize = 12f
        lineChart.description.textColor = ContextCompat.getColor(this, R.color.chart_description_color)
        lineChart.legend.textColor = ContextCompat.getColor(this, R.color.chart_description_color)
        lineChart.xAxis.textColor = ContextCompat.getColor(this, R.color.chart_description_color)
        lineChart.axisLeft.textColor = ContextCompat.getColor(this, R.color.chart_description_color)
        lineChart.isDragEnabled = true
        lineChart.setScaleEnabled(true)
        lineChart.setPinchZoom(true)
        lineChart.setBorderWidth(1f)
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv" // Тип файла
            putExtra(Intent.EXTRA_TITLE, "accel_log_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv")
        }
        createFileLauncher.launch(intent)
    }

    @SuppressLint("SetTextI18n")
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val xValue = event.values[0]
            val yValue = event.values[1]
            val zValue = event.values[2]

            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    xAccelTextView.text = "Акселерометр x: $xValue м/с²"
                    yAccelTextView.text = "Акселерометр y: $yValue м/с²"
                    zAccelTextView.text = "Акселерометр z: $zValue м/с²"
                }
                Sensor.TYPE_LINEAR_ACCELERATION -> {
                    xAccelTextView.text = "Линейное ускорение x: $xValue м/с²"
                    yAccelTextView.text = "Линейное ускорение y: $yValue м/с²"
                    zAccelTextView.text = "Линейное ускорение z: $zValue м/с²"
                }
                Sensor.TYPE_GYROSCOPE -> {
                    xAccelTextView.text = "Гироскоп x: $xValue рад/с"
                    yAccelTextView.text = "Гироскоп y: $yValue рад/с"
                    zAccelTextView.text = "Гироскоп z: $zValue рад/с"
                }
            }

            val currentTime = System.currentTimeMillis()
            elapsedTime = (currentTime - startTime) / 1000f

            xEntries.add(Entry(elapsedTime, xValue))
            yEntries.add(Entry(elapsedTime, yValue))
            zEntries.add(Entry(elapsedTime, zValue))

            val timeStamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            accelerometerLogger.logData(timeStamp.format(Date()), xValue, yValue, zValue)

            updateChart()
        }
    }

    private fun updateChart() {
        val xDataSet = LineDataSet(xEntries, "X")
        xDataSet.setColor(ColorTemplate.COLORFUL_COLORS[0])
        xDataSet.setCircleColor(ColorTemplate.COLORFUL_COLORS[0])
        xDataSet.setDrawCircleHole(false)
        xDataSet.lineWidth = 1.3f
        xDataSet.setDrawValues(false)

        val yDataSet = LineDataSet(yEntries, "Y")
        yDataSet.setColor(ColorTemplate.COLORFUL_COLORS[2])
        yDataSet.setCircleColor(ColorTemplate.COLORFUL_COLORS[2])
        yDataSet.setDrawCircleHole(false)
        yDataSet.lineWidth = 1.3f
        yDataSet.setDrawValues(false)

        val zDataSet = LineDataSet(zEntries, "Z")
        zDataSet.setColor(ColorTemplate.COLORFUL_COLORS[3])
        zDataSet.setCircleColor(ColorTemplate.COLORFUL_COLORS[3])
        zDataSet.setDrawCircleHole(false)
        zDataSet.lineWidth = 1.3f
        zDataSet.setDrawValues(false)

        val lineData = LineData(xDataSet, yDataSet, zDataSet)
        lineChart.data = lineData
        lineChart.invalidate() // Refresh the chart
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle accuracy changes if needed
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onResume() {
        super.onResume()
        val selectedSensor = when (sensorSpinner.selectedItemPosition) {
            0 -> Sensor.TYPE_ACCELEROMETER
            1 -> Sensor.TYPE_LINEAR_ACCELERATION
            2 -> Sensor.TYPE_GYROSCOPE
            else -> Sensor.TYPE_ACCELEROMETER
        }
        registerSensor(selectedSensor)
    }

    private fun showAboutDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_about, null)



        AlertDialog.Builder(this)
            .setTitle("Об авторах")
            .setView(dialogView)
            .setPositiveButton("Закрыть") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }


}