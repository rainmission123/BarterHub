package com.example.barterhub.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.barterhub.R

class FullscreenImageActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var imageUrls: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fullscreen_image)

        viewPager = findViewById(R.id.viewPagerImages)

        // Kunin images at initial position mula sa intent
        imageUrls = intent.getStringArrayListExtra("images") ?: listOf()
        val position = intent.getIntExtra("position", 0)

        // I-set ang adapter
        viewPager.adapter = FullscreenImageAdapter(imageUrls)

        // I-set ang initial image na makikita
        viewPager.setCurrentItem(position, false)
    }
}
