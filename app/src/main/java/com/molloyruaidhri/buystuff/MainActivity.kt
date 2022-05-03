package com.molloyruaidhri.buystuff

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import com.android.billingclient.api.*
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.molloyruaidhri.buystuff.databinding.ActivityMainBinding
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var billingClient: BillingClient

    private lateinit var sharedPref: SharedPreferences

    companion object {
        const val TAG = "Debug"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        billingClient = BillingClient.newBuilder(this)
            .enablePendingPurchases()
            .setListener { billingResult, list ->
                purchasesUpdated(billingResult, list)
            }
            .build()

        sharedPref = getPreferences(Context.MODE_PRIVATE)

        setUpAds()

        connectToGooglePlayBilling()
    }

    // Called when leaving the activity
    override fun onPause() {
        binding.adView.pause()
        super.onPause()
    }

    // Called before the activity is destroyed
    override fun onDestroy() {
        binding.adView.destroy()
        super.onDestroy()
    }

    // Called when returning to the activity
    override fun onResume() {
        super.onResume()
        queryPurchases()
    }

    private fun queryPurchases() {
        billingClient.queryPurchasesAsync(BillingClient.SkuType.INAPP) { billingResult, list ->
            when {
                list.isNullOrEmpty() -> {
                    initNonConsumableProduct()
                }
                else -> {
                    purchasesUpdated(billingResult, list)
                }
            }
        }
        billingClient.queryPurchasesAsync(BillingClient.SkuType.SUBS) { billingResult, list ->
            when {
                list.isNullOrEmpty() -> {
                    initSubscriptionProduct()
                }
                else -> {
                    purchasesUpdated(billingResult, list)
                }
            }
        }
    }

    private fun initNonConsumableProduct() {
        updateProductState(getString(R.string.product_non_consumable), false)
    }

    private fun initSubscriptionProduct() {
        updateProductState(getString(R.string.product_subscription), false)
    }

    private fun setUpAds() {
        // Initialize the Mobile Ads SDK with an AdMob App ID
        MobileAds.initialize(this) {}

        val testDeviceIds = listOf("968AF1CA639E2B630DEA0EEF21970C3F", AdRequest.DEVICE_ID_EMULATOR)
        val configuration = RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build()
        MobileAds.setRequestConfiguration(configuration)

        //Create an ad request and start loading the ad in the background.
        val adRequest = AdRequest.Builder().build()
        binding.adView.loadAd(adRequest)
    }

    private fun purchasesUpdated(billingResult: BillingResult, list: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            if(!list.isNullOrEmpty()){
                for (purchase in list) {
                    when {
                        isNonConsumableProduct(getProductId(purchase)) -> {
                            val productId = getProductId(purchase)
                            updateProductState(productId, purchase.isAcknowledged)
                        }
                    }
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED  &&
                            !purchase.isAcknowledged) {
                        verifyPurchase(purchase)
                    }
                }
            }
        }
    }

    private fun connectToGooglePlayBilling() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // The BillingClient is ready. You can query purchases here.
                    getInAppProductDetails()
                    getSubsProductDetails()
                    queryPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.
                connectToGooglePlayBilling()
            }
        })
    }

    private fun getInAppProductDetails() {
        val listOfProductIds =
            listOf(getString(R.string.product_consumable),
                getString(R.string.product_non_consumable))
        val productDetailsQuery = SkuDetailsParams.newBuilder()
            .setSkusList(listOfProductIds)
            .setType(BillingClient.SkuType.INAPP)
            .build()
        val activity = this

        billingClient.querySkuDetailsAsync(
            productDetailsQuery
        ) { billingResult, list ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && list != null) {
                val productOne: SkuDetails = list[0]
                activity.runOnUiThread {
                    binding.tvConsumableProduct.text = productOne.title
                    binding.btnConsumableProduct.text = productOne.price
                }
                binding.btnConsumableProduct.setOnClickListener {
                    billingClient.launchBillingFlow(
                        activity,
                        BillingFlowParams.newBuilder().setSkuDetails(productOne).build()
                    )
                }

                val productTwo: SkuDetails = list[1]
                activity.runOnUiThread {
                    binding.tvNonConsumableProduct.text = productTwo.title
                    binding.btnNonConsumableProduct.text = productTwo.price
                }
                binding.btnNonConsumableProduct.setOnClickListener {
                    billingClient.launchBillingFlow(
                        activity,
                        BillingFlowParams.newBuilder().setSkuDetails(productTwo).build()
                    )
                }
            }
        }
    }

    private fun getSubsProductDetails() {
        val listOfProductIds =
            listOf(getString(R.string.product_subscription))
        val productDetailsQuery = SkuDetailsParams.newBuilder()
            .setSkusList(listOfProductIds)
            .setType(BillingClient.SkuType.SUBS)
            .build()
        val activity = this

        billingClient.querySkuDetailsAsync(
            productDetailsQuery
        ) { billingResult, list ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && list != null) {
                val productThree: SkuDetails = list[0]
                activity.runOnUiThread {
                    binding.tvSubscriptionProduct.text = productThree.title
                    binding.btnSubscriptionProduct.text = productThree.price
                }
                binding.btnSubscriptionProduct.setOnClickListener {
                    billingClient.launchBillingFlow(
                        activity,
                        BillingFlowParams.newBuilder().setSkuDetails(productThree).build()
                    )
                }
            }
        }
    }

    private fun verifyPurchase(purchase: Purchase) {
        Log.d(TAG, purchase.originalJson)
        val requestUrl = "https://europe-west2-buy-stuff-46336.cloudfunctions.net/verifyPurchases?" +
                "purchaseToken=" + purchase.purchaseToken + "&" +
                "purchaseTime=" + purchase.purchaseTime + "&" +
                "orderId=" + purchase.orderId

        val req = StringRequest(
            Request.Method.POST,
            requestUrl,
            { response ->
                try {
                    Log.d(TAG, response)
                    val isValidResponseFromServer = JSONObject(response).getBoolean("isValid")
                    Log.d(TAG, isValidResponseFromServer.toString())
                    if(isValidResponseFromServer) {
                        if(isNonConsumableProduct(getProductId(purchase))) ackNonConsumableProduct(purchase)
                        else ackConsumableProduct(purchase)
                    }
                } catch (err: Exception) {
                    Log.e(TAG, err.stackTraceToString())
                }
            },
            { error ->
                Log.e(TAG, error.stackTraceToString())
            })

        Volley.newRequestQueue(this).add(req)
    }

    private fun getProductId(purchase: Purchase): String {
        val purchaseInfoFromServer = JSONObject(purchase.originalJson)
        return purchaseInfoFromServer.getString("productId")
    }

    private fun setNonConsumableProductEnabledState(productId: String) {
        runOnUiThread {
            val isPurchased = getProductStateInSharedPref(productId)
            when (productId) {
                getString(R.string.product_non_consumable) -> {
                    binding.btnNonConsumableProduct.isEnabled = !isPurchased
                }
                getString(R.string.product_subscription) -> {
                    if(isPurchased) {
                        binding.btnSubscriptionProduct.isEnabled = false
                        binding.ivBasicContent.visibility = View.GONE
                        binding.ivPremiumContent.visibility = View.VISIBLE
                    }
                    else{
                        binding.btnSubscriptionProduct.isEnabled = true
                        binding.ivBasicContent.visibility = View.VISIBLE
                        binding.ivPremiumContent.visibility = View.GONE
                    }
                }
                else -> {
                    Log.d(TAG, "Product ID $productId not found!")
                }
            }
        }
    }

    private fun isNonConsumableProduct(productId: String): Boolean {
        return productId == getString(R.string.product_non_consumable)
                || productId == getString(R.string.product_subscription)
    }

    private fun ackNonConsumableProduct(purchase: Purchase) {
        val ack = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        billingClient.acknowledgePurchase(ack
        ) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val msg = "Non-consumable product purchase acknowledged!"
                Log.d(TAG, msg)
                runOnUiThread {
                    val productId = getProductId(purchase)
                    updateProductState(productId, true)
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun ackConsumableProduct(purchase: Purchase) {
        val consumeParams = ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        billingClient.consumeAsync(consumeParams
        ) { billingResult, _ ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val msg = "Non-consumable product purchase consumed!"
                Log.d(TAG, msg)
                runOnUiThread {
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateProductState(productId: String, isAcknowledged: Boolean) {
        setProductStateInSharedPref(productId, isAcknowledged)
        setNonConsumableProductEnabledState(productId)
    }

    private fun setProductStateInSharedPref(productId: String, isAcknowledged: Boolean) {
        with (sharedPref.edit()) {
            putBoolean(productId, isAcknowledged)
            apply()
        }
    }

    private fun getProductStateInSharedPref(productName: String): Boolean {
        return sharedPref.getBoolean(productName, false)
    }
}