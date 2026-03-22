package com.example.barterhub.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.barterhub.R
import com.example.barterhub.data.models.FeaturedItem
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.database.FirebaseDatabase

class EditItemFragment : Fragment(R.layout.fragment_edit_item) {
    private lateinit var categoryDropdown: AutoCompleteTextView
    private lateinit var categoryLayout: TextInputLayout
    private lateinit var conditionDropdown: AutoCompleteTextView
    private lateinit var conditionLayout: TextInputLayout
    private var itemId: String = ""
    private lateinit var database: com.google.firebase.database.DatabaseReference
    private lateinit var etTitle: TextInputEditText
    private lateinit var etDescription: TextInputEditText
    private lateinit var etPrice: TextInputEditText
    private lateinit var etLocation: TextInputEditText
    private lateinit var btnCancel: MaterialButton
    private lateinit var btnUpdate: MaterialButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupDropdowns()

        val isPreview = context == null || activity == null

        if (isPreview) {
            etTitle.setText("Sample Item Title")
            etDescription.setText("Sample description")
            etPrice.setText("100.0")
            categoryDropdown.setText("Electronics", false)
            conditionDropdown.setText("Brand New", false)
            etLocation.setText("Sample Location")
            return
        }

        itemId = arguments?.getString("itemId") ?: ""
        Log.d("EditItemFragment", "Editing item ID: $itemId")

        database = FirebaseDatabase.getInstance().getReference("items")
        loadItemData()

        btnCancel.setOnClickListener {
            findNavController().navigateUp()
        }

        btnUpdate.setOnClickListener {
            updateItem()
        }
    }

    private fun initializeViews(view: View) {
        etTitle = view.findViewById(R.id.etTitle)
        etDescription = view.findViewById(R.id.etDescription)
        etPrice = view.findViewById(R.id.etPrice)
        etLocation = view.findViewById(R.id.etLocation)
        btnCancel = view.findViewById(R.id.btnCancel)
        btnUpdate = view.findViewById(R.id.btnUpdate)

        // Category dropdown
        categoryDropdown = view.findViewById(R.id.categoryDropdown)
        categoryLayout = view.findViewById(R.id.categoryLayout)

        // Condition dropdown
        conditionDropdown = view.findViewById(R.id.conditionDropdown)
        conditionLayout = view.findViewById(R.id.conditionLayout)
    }

    private fun setupDropdowns() {
        val categories = listOf(
            "Electronics",
            "Kitchen",
            "Clothing",
            "Books",
            "Sports & Outdoor",
            "Food & Beverages",
            "Vehicles",
            "Baby & Kids",
            "Pet Supplies",
            "Rice",
            "Fish & Seafood",
            "Meat & Poultry",
            "Fruits & Vegetables",
            "Groceries",
            "Home Appliances",
            "Handmade & Crafts",
            "Livestock",
            "Services",
            "Others"
        )

        val categoryAdapter = ArrayAdapter(requireContext(), R.layout.dropdown_item, categories)
        categoryDropdown.setAdapter(categoryAdapter)

        val conditions = listOf(
            "Brand New",
            "Like New",
            "Good",
            "Fair",
            "Needs Repair",
            "Refurbished"

        )
        val conditionAdapter = ArrayAdapter(requireContext(), R.layout.dropdown_item, conditions)
        conditionDropdown.setAdapter(conditionAdapter)
    }

    private fun loadItemData() {
        if (itemId.isEmpty()) return

        database.child(itemId).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val item = snapshot.getValue(FeaturedItem::class.java)
                    item?.let {
                        etTitle.setText(it.title)
                        etDescription.setText(it.description)
                        etPrice.setText(it.price.toString())
                        categoryDropdown.setText(it.category, false)
                        conditionDropdown.setText(it.condition, false)
                        etLocation.setText(it.location)

                        Log.d("EditItemFragment", "Loaded item: ${it.title}")
                    }
                } else {
                    Toast.makeText(requireContext(), "Item not found", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load item", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
    }

    private fun updateItem() {
        val title = etTitle.text.toString().trim()
        val description = etDescription.text.toString().trim()
        val priceText = etPrice.text.toString().trim()
        val category = categoryDropdown.text.toString().trim()
        val condition = conditionDropdown.text.toString().trim()
        val location = etLocation.text.toString().trim()

        // Validation
        if (title.isEmpty()) {
            etTitle.error = "Title is required"
            return
        }
        if (description.isEmpty()) {
            etDescription.error = "Description is required"
            return
        }
        if (category.isEmpty()) {
            categoryLayout.error = "Select a category"
            return
        } else categoryLayout.error = null

        if (condition.isEmpty()) {
            conditionLayout.error = "Select a condition"
            return
        } else conditionLayout.error = null

        val price = priceText.toDoubleOrNull() ?: 0.0

        val updates = hashMapOf<String, Any>(
            "title" to title,
            "description" to description,
            "price" to price,
            "category" to category,
            "condition" to condition,
            "location" to location
        )

        database.child(itemId).updateChildren(updates)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Item updated successfully!", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to update item: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("EditItemFragment", "Update failed", e)
            }
    }
}
