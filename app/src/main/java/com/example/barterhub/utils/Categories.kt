package com.example.barterhub.utils

import com.example.barterhub.R

object Categories {

    val ALL_CATEGORIES = listOf(
        "Electronics",
        "Kitchen",
        "Clothing",
        "Books",
        "Sports & Outdoors",
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

    val CATEGORIES_WITH_ICONS: Map<String, Int> = mapOf(
        "Electronics" to R.drawable.ic_electronics,
        "Kitchen" to R.drawable.ic_kitchen,
        "Clothing" to R.drawable.ic_clothings,
        "Books" to R.drawable.ic_books,
        "Sports & Outdoors" to R.drawable.ic_sports,
        "Food & Beverages" to R.drawable.food,
        "Vehicles" to R.drawable.car,
        "Baby & Kids" to R.drawable.baby,
        "Pet Supplies" to R.drawable.pet,
        "Rice" to R.drawable.rice,
        "Fish & Seafood" to R.drawable.fish,
        "Meat & Poultry" to R.drawable.meat,
        "Fruits & Vegetables" to R.drawable.vegetable,
        "Groceries" to R.drawable.grocery,
        "Home Appliances" to R.drawable.furniture,
        "Handmade & Crafts" to R.drawable.craft,
        "Livestock" to R.drawable.livestock,
        "Services" to R.drawable.service,
        "Others" to R.drawable.ic_others
    )
}