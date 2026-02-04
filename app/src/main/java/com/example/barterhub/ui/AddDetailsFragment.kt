package com.example.barterhub.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.barterhub.R
import com.example.barterhub.databinding.FragmentAddDetailsBinding
import com.example.barterhub.ui.viewmodel.ListingViewModel
import com.example.barterhub.utils.Categories
import com.google.android.material.snackbar.Snackbar

class AddDetailsFragment : Fragment() {

    private var _binding: FragmentAddDetailsBinding? = null
    private val binding get() = _binding!!
    private val listingViewModel: ListingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDropdowns()
        setupNextButton()
    }

    private fun setupDropdowns() {
        // Categories Dropdown
        val categories = Categories.ALL_CATEGORIES
        binding.categoryDropdown.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories)
        )

        // Conditions Dropdown
        val conditions = listOf(
            "Brand New",
            "Like New",
            "Good",
            "Fair",
            "Needs Repair",
            "Refurbished"
        )
        binding.conditionDropdown.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, conditions)
        )
    }

    private fun setupNextButton() {
        binding.nextButton.setOnClickListener {
            if (validateForm()) {
                saveDataToViewModel()
                navigateToNext()
            }
        }
    }

    private fun validateForm(): Boolean {
        var isValid = true

        // Title validation
        if (binding.titleInput.text.isNullOrBlank()) {
            binding.titleLayout.error = "Title is required"
            isValid = false
        } else {
            binding.titleLayout.error = null
        }

        // Description validation
        if (binding.descriptionInput.text.isNullOrBlank()) {
            binding.descriptionLayout.error = "Description is required"
            isValid = false
        } else if (binding.descriptionInput.text!!.length < 10) {
            binding.descriptionLayout.error = "Description should be at least 10 characters"
            isValid = false
        } else {
            binding.descriptionLayout.error = null
        }

        // Category validation
        if (binding.categoryDropdown.text.isNullOrBlank()) {
            binding.categoryLayout.error = "Category is required"
            isValid = false
        } else {
            binding.categoryLayout.error = null
        }

        // Condition validation
        if (binding.conditionDropdown.text.isNullOrBlank()) {
            binding.conditionLayout.error = "Condition is required"
            isValid = false
        } else {
            binding.conditionLayout.error = null
        }

        return isValid
    }

    private fun saveDataToViewModel() {
        listingViewModel.title = binding.titleInput.text.toString().trim()
        listingViewModel.description = binding.descriptionInput.text.toString().trim()
        listingViewModel.category = binding.categoryDropdown.text.toString().trim()
        listingViewModel.condition = binding.conditionDropdown.text.toString().trim()
        listingViewModel.price = binding.priceInput.text.toString().trim()
    }

    private fun navigateToNext() {
        try {
            findNavController().navigate(R.id.action_addDetails_to_addLocation)
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Navigation error: ${e.message}", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
