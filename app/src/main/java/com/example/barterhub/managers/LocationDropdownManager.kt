package com.example.barterhub.managers

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import okhttp3.*
import org.json.JSONArray
import java.io.IOException

object LocationDropdownManager {

    private const val BASE_URL = "https://psgc.gitlab.io/api"
    private val client = OkHttpClient()

    data class LocationItem(
        val code: String,
        val name: String
    )

    fun setup(
        context: Context,
        provinceView: AutoCompleteTextView,
        cityView: AutoCompleteTextView
    ) {
        cityView.isEnabled = false
        cityView.setText("", false)

        loadProvinces(context) { provinces ->
            val provinceNames = provinces.map { it.name }

            provinceView.setAdapter(
                ArrayAdapter(
                    context,
                    android.R.layout.simple_dropdown_item_1line,
                    provinceNames
                )
            )

            provinceView.setOnClickListener {
                provinceView.showDropDown()
            }

            provinceView.setOnItemClickListener { _, _, position, _ ->
                val selectedProvince = provinces[position]

                provinceView.setText(selectedProvince.name, false)
                cityView.setText("", false)
                cityView.isEnabled = false

                loadCitiesAndMunicipalities(context, selectedProvince.code) { cities ->
                    val cityNames = cities.map { it.name }

                    cityView.setAdapter(
                        ArrayAdapter(
                            context,
                            android.R.layout.simple_dropdown_item_1line,
                            cityNames
                        )
                    )

                    cityView.isEnabled = true
                    cityView.showDropDown()
                }
            }
        }

        cityView.setOnClickListener {
            if (cityView.isEnabled) {
                cityView.showDropDown()
            } else {
                Toast.makeText(context, "Select province first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadProvinces(
        context: Context,
        onLoaded: (List<LocationItem>) -> Unit
    ) {
        val request = Request.Builder()
            .url("$BASE_URL/provinces.json")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnMain(context) {
                    Toast.makeText(context, "Failed to load provinces", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        runOnMain(context) {
                            Toast.makeText(context, "Province server error", Toast.LENGTH_SHORT).show()
                        }
                        return
                    }

                    val body = response.body?.string().orEmpty()
                    val provinces = parseLocations(body)

                    runOnMain(context) {
                        onLoaded(provinces)
                    }
                }
            }
        })
    }

    private fun loadCitiesAndMunicipalities(
        context: Context,
        provinceCode: String,
        onLoaded: (List<LocationItem>) -> Unit
    ) {
        val request = Request.Builder()
            .url("$BASE_URL/provinces/$provinceCode/cities-municipalities.json")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnMain(context) {
                    Toast.makeText(context, "Failed to load cities", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        runOnMain(context) {
                            Toast.makeText(context, "City server error", Toast.LENGTH_SHORT).show()
                        }
                        return
                    }

                    val body = response.body?.string().orEmpty()
                    val cities = parseLocations(body)

                    runOnMain(context) {
                        onLoaded(cities)
                    }
                }
            }
        })
    }

    private fun parseLocations(json: String): List<LocationItem> {
        val array = JSONArray(json)
        val list = mutableListOf<LocationItem>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)

            val code = obj.optString("code")
            val name = obj.optString("name")

            if (code.isNotBlank() && name.isNotBlank()) {
                list.add(LocationItem(code, name))
            }
        }

        return list.sortedBy { it.name }
    }

    private fun runOnMain(context: Context, action: () -> Unit) {
        (context as? android.app.Activity)?.runOnUiThread {
            action()
        }
    }
}