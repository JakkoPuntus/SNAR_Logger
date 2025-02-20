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
import com.github.mikephil.charting.formatter.ValueFormatter
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
    private var startTime = 0L
    private var elapsedTime = 0f

    private val createFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        accelerometerLogger.writeDataToCSV(outputStream)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    showToast("Ошибка при сохранении файла")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupChart()
        setupSensorSpinner()
        setupButtons()

        accelerometerLogger = logger(this)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        startTime = System.currentTimeMillis()
    }

    private fun initializeViews() {
        xAccelTextView = findViewById(R.id.xAccelTextView)
        yAccelTextView = findViewById(R.id.yAccelTextView)
        zAccelTextView = findViewById(R.id.zAccelTextView)
        lineChart = findViewById(R.id.lineChart)
        sensorSpinner = findViewById<Spinner>(R.id.sensorSpinner)
        titleTextView = findViewById(R.id.titleTextView)
    }

    private fun setupChart() {
        lineChart.apply {
            description.text = "Ускорение от времени"
            description.textSize = 12f
            description.textColor = ContextCompat.getColor(this@MainActivity, R.color.chart_description_color)
            legend.textColor = ContextCompat.getColor(this@MainActivity, R.color.chart_description_color)

            // Настройка оси X
            xAxis.apply {
                textColor = ContextCompat.getColor(this@MainActivity, R.color.chart_description_color)
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "${value.toInt()} с" // Ось X: время в секундах
                    }
                }
                axisLineWidth = 1f
                axisLineColor = ContextCompat.getColor(this@MainActivity, R.color.chart_description_color)
                setLabelCount(5, true) // Количество меток на оси X
                granularity = 1f // Минимальный шаг оси X
                isGranularityEnabled = true
                setDrawLabels(true)
                setDrawAxisLine(true)
                setDrawGridLines(true)
                xAxis.labelRotationAngle = 0f
                xAxis.setCenterAxisLabels(false)
                xAxis.setAvoidFirstLastClipping(true)
            }

            axisLeft.apply {
                textColor = ContextCompat.getColor(this@MainActivity, R.color.chart_description_color)
                axisLineWidth = 1f
                axisLineColor = ContextCompat.getColor(this@MainActivity, R.color.chart_description_color)
                setLabelCount(6, true)
                setDrawLabels(true)
                setDrawAxisLine(true)
                setDrawGridLines(true)
                granularity = 0.1f
                isGranularityEnabled = true
                setCenterAxisLabels(false)
                setDrawZeroLine(true)
                zeroLineWidth = 1f
            }

            // Отключение правой оси Y
            axisRight.isEnabled = false

            // Дополнительные настройки графика
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            setBorderWidth(1f)
        }
    }

    private fun setupSensorSpinner() {
        val sensorTypes = arrayOf("Акселерометр", "Линейное ускорение", "Гироскоп")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sensorTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sensorSpinner.adapter = adapter

        sensorSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                clearChart()
                updateTitle(position)
                registerSelectedSensor(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.recordButton).setOnClickListener { openFilePicker() }
        findViewById<Button>(R.id.clearButton).setOnClickListener { clearData() }
        findViewById<Button>(R.id.aboutButton).setOnClickListener { showAboutDialog() }
    }

    private fun clearChart() {
        xEntries.clear()
        yEntries.clear()
        zEntries.clear()
        lineChart.clear()
        lineChart.invalidate()
    }

    private fun updateTitle(position: Int) {
        val (title, description, yAxisLabel) = when (position) {
            0 -> Triple("Акселерометр", "Ускорение от времени (акселерометр)", "м/с²")
            1 -> Triple("Линейное ускорение", "Ускорение от времени (линейное ускорение)", "/с²")
            2 -> Triple("Гироскоп", "Угловая скорость от времени (гироскоп)", "рад/с")
            else -> Triple("Датчик", "Данные от времени", "Значение")
        }
        titleTextView.text = title
        lineChart.description.text = description
        lineChart.axisLeft.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "$value $yAxisLabel" // Подпись оси Y с единицами измерения
            }
        }
        lineChart.invalidate() // Обновление графика
    }

    private fun registerSelectedSensor(position: Int) {
        sensorManager.unregisterListener(this)
        when (position) {
            0 -> registerSensor(Sensor.TYPE_ACCELEROMETER)
            1 -> registerSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            2 -> registerSensor(Sensor.TYPE_GYROSCOPE)
        }
    }

    private fun registerSensor(sensorType: Int) {
        sensorManager.getDefaultSensor(sensorType)?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        } ?: showToast("Датчик не доступен")
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, "accel_log_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv")
        }
        createFileLauncher.launch(intent)
    }

    private fun clearData() {
        accelerometerLogger.clearLog()
        clearChart()
        elapsedTime = 0f
        startTime = System.currentTimeMillis()
    }

    @SuppressLint("SetTextI18n")
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val (xValue, yValue, zValue) = event.values
            updateSensorTextViews(event.sensor.type, xValue, yValue, zValue)

            elapsedTime = (System.currentTimeMillis() - startTime) / 1000f

            xEntries.add(Entry(elapsedTime, xValue))
            yEntries.add(Entry(elapsedTime, yValue))
            zEntries.add(Entry(elapsedTime, zValue))

            accelerometerLogger.logData(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()), xValue, yValue, zValue)
            updateChart()
        }
    }

    private fun updateSensorTextViews(sensorType: Int, xValue: Float, yValue: Float, zValue: Float) {
        val (xLabel, yLabel, zLabel) = when (sensorType) {
            Sensor.TYPE_ACCELEROMETER -> Triple("Акселерометр x: $xValue м/с²", "Акселерометр y: $yValue м/с²", "Акселерометр z: $zValue м/с²")
            Sensor.TYPE_LINEAR_ACCELERATION -> Triple("Линейное ускорение x: $xValue м/с²", "Линейное ускорение y: $yValue м/с²", "Линейное ускорение z: $zValue м/с²")
            Sensor.TYPE_GYROSCOPE -> Triple("Гироскоп x: $xValue рад/с", "Гироскоп y: $yValue рад/с", "Гироскоп z: $zValue рад/с")
            else -> Triple("", "", "")
        }
        xAccelTextView.text = xLabel
        yAccelTextView.text = yLabel
        zAccelTextView.text = zLabel
    }

    private fun updateChart() {
        val xDataSet = createDataSet(xEntries, "X", ColorTemplate.COLORFUL_COLORS[0])
        val yDataSet = createDataSet(yEntries, "Y", ColorTemplate.COLORFUL_COLORS[2])
        val zDataSet = createDataSet(zEntries, "Z", ColorTemplate.COLORFUL_COLORS[3])

        lineChart.data = LineData(xDataSet, yDataSet, zDataSet)
        lineChart.invalidate()
    }

    private fun createDataSet(entries: ArrayList<Entry>, label: String, color: Int): LineDataSet {
        return LineDataSet(entries, label).apply {
            setColor(color)
            setCircleColor(color)
            setDrawCircleHole(false)
            setDrawCircles(false)
            lineWidth = 1.3f
            setDrawValues(false)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onResume() {
        super.onResume()
        registerSelectedSensor(sensorSpinner.selectedItemPosition)
    }

    private fun showAboutDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_about, null)
        AlertDialog.Builder(this)
            .setTitle("Об авторах")
            .setView(dialogView)
            .setPositiveButton("Закрыть") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}