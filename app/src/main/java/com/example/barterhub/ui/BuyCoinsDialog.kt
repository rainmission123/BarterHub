package com.example.barterhub.ui

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.barterhub.R
import com.example.barterhub.billing.CoinProduct
import com.example.barterhub.billing.PlayBillingManager
import com.example.barterhub.billing.PlayPurchaseVerifier
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

@SuppressLint("SetTextI18n")
class BuyCoinsDialog(private val onCoinsUpdated: (() -> Unit)? = null) : DialogFragment() {

    private lateinit var packageOptions: RadioGroup
    private lateinit var btnConfirmBuy: MaterialButton
    private lateinit var btnCancel: MaterialButton
    private lateinit var billingManager: PlayBillingManager

    private val productRadioButtons = mutableMapOf<String, RadioButton>()
    private val productDetailsById =
        mutableMapOf<String, PlayBillingManager.CoinProductDetails>()

    private var selectedCoins = 0
    private var selectedProductId = CoinProduct.BARTER_COINS_100.productId
    private var purchaseInProgress = false
    private var coinUpdatesRef: DatabaseReference? = null
    private var coinUpdatesListener: ValueEventListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        val title = TextView(context).apply {
            text = getString(R.string.play_billing_buy_barter_coins)
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        rootLayout.addView(title)

        packageOptions = RadioGroup(context)
        CoinProduct.all.forEach { product ->
            packageOptions.addView(createProductRadioButton(product))
        }
        rootLayout.addView(packageOptions)

        val buttonLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val paramsCancel = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = 16 }

        btnCancel = MaterialButton(context).apply {
            text = getString(R.string.play_billing_cancel)
        }
        btnConfirmBuy = MaterialButton(context).apply {
            text = getString(R.string.play_billing_loading_products)
            isEnabled = false
        }

        buttonLayout.addView(btnCancel, paramsCancel)
        buttonLayout.addView(btnConfirmBuy)
        rootLayout.addView(buttonLayout)

        billingManager = PlayBillingManager(
            context = context,
            listener = createBillingListener()
        )

        setupClickListeners()
        setupInitialSelection()
        listenForCoinUpdates()

        return AlertDialog.Builder(context)
            .setView(rootLayout)
            .setCancelable(false)
            .create()
            .apply {
                setOnShowListener {
                    setPackageOptionsEnabled(false)
                    billingManager.start()
                }
                setOnDismissListener {
                    stopBillingAndListeners()
                }
            }
    }

    override fun onDestroy() {
        stopBillingAndListeners()
        super.onDestroy()
    }

    private fun setupInitialSelection() {
        val firstButton = packageOptions.getChildAt(0) as RadioButton
        packageOptions.check(firstButton.id)
        updateSelectedPackage(firstButton)
    }

    private fun setupClickListeners() {
        packageOptions.setOnCheckedChangeListener { group, checkedId ->
            val rb = group.findViewById<RadioButton>(checkedId)
            updateSelectedPackage(rb)
        }

        btnCancel.setOnClickListener { dismiss() }

        btnConfirmBuy.setOnClickListener {
            if (purchaseInProgress) return@setOnClickListener

            if (selectedCoins <= 0) {
                showToast(getString(R.string.play_billing_select_package))
                return@setOnClickListener
            }

            if (FirebaseAuth.getInstance().currentUser == null) {
                showToast(getString(R.string.play_billing_login_required))
                return@setOnClickListener
            }

            if (productDetailsById[selectedProductId] == null) {
                showToast(getString(R.string.play_billing_products_not_ready))
                return@setOnClickListener
            }

            purchaseInProgress = true
            btnConfirmBuy.isEnabled = false
            btnConfirmBuy.text = getString(R.string.play_billing_opening_purchase)
            billingManager.launchPurchase(requireActivity(), selectedProductId)
        }
    }

    private fun updateSelectedPackage(rb: RadioButton) {
        val selectedProduct = productRadioButtons.entries
            .firstOrNull { it.value.id == rb.id }
            ?.key
            ?.let(CoinProduct::fromProductId)

        if (selectedProduct == null) {
            selectedCoins = 0
            selectedProductId = ""
            btnConfirmBuy.text = getString(R.string.play_billing_select_package)
            return
        }

        selectedCoins = selectedProduct.coins
        selectedProductId = selectedProduct.productId
        updateConfirmButton()
    }

    private fun createProductRadioButton(product: CoinProduct): RadioButton {
        return RadioButton(requireContext()).apply {
            id = View.generateViewId()
            text = getString(R.string.play_billing_product_loading, product.coins)
            setPadding(24, 24, 24, 24)
            isEnabled = false
            productRadioButtons[product.productId] = this
        }
    }

    private fun createBillingListener(): PlayBillingManager.Listener {
        return object : PlayBillingManager.Listener {
            override fun onBillingReady(
                products: List<PlayBillingManager.CoinProductDetails>
            ) {
                if (!isDialogActive()) return

                productDetailsById.clear()
                products.forEach { details ->
                    productDetailsById[details.product.productId] = details
                    productRadioButtons[details.product.productId]?.text =
                        getString(
                            R.string.play_billing_product_label,
                            details.product.coins,
                            details.formattedPrice
                        )
                }

                purchaseInProgress = false
                setPackageOptionsEnabled(productDetailsById.isNotEmpty())
                updateConfirmButton()
            }

            override fun onBillingUnavailable() {
                if (!isDialogActive()) return

                purchaseInProgress = false
                setPackageOptionsEnabled(false)
                btnConfirmBuy.isEnabled = false
                btnConfirmBuy.text = getString(R.string.play_billing_unavailable)
            }

            override fun onPurchasePending(productId: String) {
                if (!isDialogActive()) return

                purchaseInProgress = false
                showToast(getString(R.string.play_billing_purchase_pending))
                updateConfirmButton()
            }

            override fun onPurchaseProcessing(productId: String) {
                if (!isDialogActive()) return

                btnConfirmBuy.text = getString(R.string.play_billing_verifying_purchase)
            }

            override fun onPurchaseVerified(result: PlayPurchaseVerifier.Result.Success) {
                if (!isDialogActive()) return

                purchaseInProgress = false
                onCoinsUpdated?.invoke()
                showToast(getString(R.string.play_billing_purchase_success))
                dismissAllowingStateLoss()
            }

            override fun onPurchaseNeedsReconciliation(productId: String) {
                if (!isDialogActive()) return

                purchaseInProgress = false
                onCoinsUpdated?.invoke()
                showToast(getString(R.string.play_billing_purchase_success_reconciling))
                updateConfirmButton()
            }

            override fun onPurchaseCanceled() {
                if (!isDialogActive()) return

                purchaseInProgress = false
                updateConfirmButton()
            }

            override fun onPurchaseError() {
                if (!isDialogActive()) return

                purchaseInProgress = false
                showToast(getString(R.string.play_billing_purchase_error))
                updateConfirmButton()
            }
        }
    }

    private fun setPackageOptionsEnabled(enabled: Boolean) {
        productRadioButtons.forEach { (productId, radioButton) ->
            radioButton.isEnabled = enabled && productDetailsById.containsKey(productId)
        }
    }

    private fun updateConfirmButton() {
        if (purchaseInProgress) return

        val details = productDetailsById[selectedProductId]
        btnConfirmBuy.isEnabled = details != null
        btnConfirmBuy.text = if (details == null) {
            getString(R.string.play_billing_loading_products)
        } else {
            getString(R.string.play_billing_buy_now_price, details.formattedPrice)
        }
    }

    private fun listenForCoinUpdates() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseDatabase.getInstance()
            .getReference("users")
            .child(uid)
            .child("wallet")
            .child("coins")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onCoinsUpdated?.invoke()
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }

        coinUpdatesRef = db
        coinUpdatesListener = listener
        db.addValueEventListener(listener)
    }

    private fun stopBillingAndListeners() {
        billingManager.stop()
        coinUpdatesListener?.let { listener ->
            coinUpdatesRef?.removeEventListener(listener)
        }
        coinUpdatesListener = null
        coinUpdatesRef = null
    }

    private fun isDialogActive(): Boolean {
        return isAdded && dialog?.isShowing == true
    }

    private fun showToast(message: String) {
        if (isAdded) Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
