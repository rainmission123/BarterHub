package com.example.barterhub.ui.profile

import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import com.example.barterhub.R

class PremiumOptionSelector(
    private val rootView: View,
    private val rgPremiumOptions: RadioGroup,
    private val rb50: RadioButton,
    private val rb100: RadioButton,
    private val rb200: RadioButton
) {

    private val card50: View = rootView.findViewById(R.id.cardPlan50)
    private val card100: View = rootView.findViewById(R.id.cardPlan100)
    private val card200: View = rootView.findViewById(R.id.cardPlan200)

    fun setup() {
        card50.setOnClickListener { select50() }
        card100.setOnClickListener { select100() }
        card200.setOnClickListener { select200() }

        rb50.setOnClickListener { select50() }
        rb100.setOnClickListener { select100() }
        rb200.setOnClickListener { select200() }
    }

    private fun reset() {
        card50.isSelected = false
        card100.isSelected = false
        card200.isSelected = false

        rb50.isChecked = false
        rb100.isChecked = false
        rb200.isChecked = false
    }

    fun select50() {
        reset()
        card50.isSelected = true
        rb50.isChecked = true
        rgPremiumOptions.check(rb50.id)
    }

    fun select100() {
        reset()
        card100.isSelected = true
        rb100.isChecked = true
        rgPremiumOptions.check(rb100.id)
    }

    fun select200() {
        reset()
        card200.isSelected = true
        rb200.isChecked = true
        rgPremiumOptions.check(rb200.id)
    }
}