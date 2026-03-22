package com.example.barterhub.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.example.barterhub.R
import com.example.barterhub.adapters.FullscreenImagePagerAdapter
import com.example.barterhub.databinding.FragmentFullscreenImagesBinding

class FullscreenImageViewerFragment : Fragment(R.layout.fragment_fullscreen_images) {

    private var _binding: FragmentFullscreenImagesBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentFullscreenImagesBinding.bind(view)

        val urls = arguments?.getStringArray("urls")?.toList() ?: emptyList()
        val startIndex = arguments?.getInt("index") ?: 0

        if (urls.isEmpty()) {
            findNavController().popBackStack()
            return
        }

        binding.fullscreenViewPager.adapter = FullscreenImagePagerAdapter(urls)
        binding.fullscreenViewPager.setCurrentItem(
            startIndex.coerceIn(0, urls.size - 1),
            false
        )

        // counter
        binding.tvCounter.text = "${binding.fullscreenViewPager.currentItem + 1}/${urls.size}"

        binding.fullscreenViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.tvCounter.text = "${position + 1}/${urls.size}"
            }
        })

        binding.btnClose.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
