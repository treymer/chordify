package com.example.myapplication

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "BillingManager"
const val PRODUCT_ID_PRO = "cadence_pro"

/**
 * Manages Google Play Billing for the Cadence Pro one-time purchase.
 *
 * Lifecycle:
 *  - Call [connect] once (e.g. from Activity.onCreate) to open the billing connection.
 *  - Call [endConnection] in Activity.onDestroy to release the connection.
 *  - Observe [isPro] to gate Pro features.
 *  - Call [launchPurchaseFlow] when the user taps the upgrade button.
 *  - Call [restorePurchases] when the user taps "Restore Purchase".
 */
class BillingManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    // Cached product details so we can launch the purchase flow without a second query.
    private var proProductDetails: ProductDetails? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase -> handlePurchase(purchase) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "User cancelled the purchase flow.")
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // User already owns this — treat as Pro and re-query to get the Purchase object.
                Log.d(TAG, "Item already owned; restoring.")
                restorePurchases()
            }
            else -> {
                Log.w(TAG, "Purchase update failed: ${billingResult.responseCode} — ${billingResult.debugMessage}")
            }
        }
    }

    val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    // ── Connection ────────────────────────────────────────────────────────────

    fun connect() {
        if (billingClient.isReady) {
            // Already connected — just restore purchases to ensure state is current.
            restorePurchases()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client connected.")
                    // Restore prior purchases immediately on connect so returning users
                    // regain Pro access without having to tap "Restore Purchase".
                    restorePurchases()
                    // Pre-fetch product details for faster purchase flow launch.
                    scope.launch { fetchProductDetails() }
                } else {
                    Log.w(TAG, "Billing setup failed: ${billingResult.responseCode} — ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.d(TAG, "Billing service disconnected.")
                // billingClient will retry automatically on next operation.
            }
        })
    }

    fun endConnection() {
        billingClient.endConnection()
        Log.d(TAG, "Billing connection ended.")
    }

    // ── Product details ───────────────────────────────────────────────────────

    private suspend fun fetchProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID_PRO)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        val result = billingClient.queryProductDetails(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            proProductDetails = result.productDetailsList?.firstOrNull()
            Log.d(TAG, "Product details fetched: ${proProductDetails?.title}")
        } else {
            Log.w(TAG, "Failed to fetch product details: ${result.billingResult.debugMessage}")
        }
    }

    // ── Purchase flow ─────────────────────────────────────────────────────────

    /**
     * Launches the Google Play purchase bottom sheet for cadence_pro.
     * Must be called from the UI thread with a live [Activity] reference.
     */
    fun launchPurchaseFlow(activity: Activity) {
        scope.launch {
            // Ensure product details are available; fetch if not yet cached.
            if (proProductDetails == null) fetchProductDetails()

            val details = proProductDetails
            if (details == null) {
                Log.e(TAG, "Cannot launch purchase flow: product details unavailable.")
                return@launch
            }

            // One-time INAPP products do not use offer tokens — omit setOfferToken().
            val productDetailsParamsList = listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .build()
            )
            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .build()

            // launchBillingFlow must run on the main thread.
            activity.runOnUiThread {
                val result = billingClient.launchBillingFlow(activity, billingFlowParams)
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "launchBillingFlow failed: ${result.responseCode} — ${result.debugMessage}")
                }
            }
        }
    }

    // ── Restore purchases ─────────────────────────────────────────────────────

    /**
     * Queries Google Play for existing INAPP purchases and re-grants Pro if found.
     * Safe to call any time — no-ops if billing client is not ready.
     */
    fun restorePurchases() {
        if (!billingClient.isReady) {
            Log.d(TAG, "restorePurchases called before client ready — connecting first.")
            connect()
            return
        }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        scope.launch {
            val result = billingClient.queryPurchasesAsync(params)
            if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val proPurchase = result.purchasesList.firstOrNull { purchase ->
                    purchase.products.contains(PRODUCT_ID_PRO)
                }
                if (proPurchase != null) {
                    Log.d(TAG, "Restored Pro purchase.")
                    handlePurchase(proPurchase)
                } else {
                    Log.d(TAG, "No prior Pro purchase found.")
                    _isPro.value = false
                }
            } else {
                Log.w(TAG, "queryPurchasesAsync failed: ${result.billingResult.debugMessage}")
            }
        }
    }

    // ── Purchase handling / acknowledgement ───────────────────────────────────

    private fun handlePurchase(purchase: Purchase) {
        if (!purchase.products.contains(PRODUCT_ID_PRO)) return

        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                Log.d(TAG, "Purchase PURCHASED state confirmed.")
                _isPro.value = true
                // Google Play requires acknowledgement within 3 days or the purchase is refunded.
                if (!purchase.isAcknowledged) acknowledgePurchase(purchase)
            }
            Purchase.PurchaseState.PENDING -> {
                // User chose a deferred payment method (e.g. cash at kiosk).
                // Don't grant access yet; wait for a PURCHASED state update.
                Log.d(TAG, "Purchase PENDING — awaiting payment completion.")
            }
            else -> {
                Log.d(TAG, "Unhandled purchase state: ${purchase.purchaseState}")
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        scope.launch {
            val result = billingClient.acknowledgePurchase(params)
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Purchase acknowledged successfully.")
            } else {
                Log.w(TAG, "Acknowledgement failed: ${result.responseCode} — ${result.debugMessage}")
            }
        }
    }
}
