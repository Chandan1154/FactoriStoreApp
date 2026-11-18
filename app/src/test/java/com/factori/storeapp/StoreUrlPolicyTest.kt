package com.factori.storeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoreUrlPolicyTest {
    @Test
    fun normalizesHttpsStoreUrls() {
        assertEquals(
            "https://shop.example.com/products?q=shirt",
            StoreUrlPolicy.normalizeStorefrontUrl(" Shop.Example.com/products?q=shirt ")
        )
    }

    @Test
    fun rejectsNonHttpsAndCredentials() {
        assertNull(StoreUrlPolicy.normalizeStorefrontUrl("http://shop.example.com"))
        assertNull(StoreUrlPolicy.normalizeStorefrontUrl("https://user:pass@shop.example.com"))
    }

    @Test
    fun rejectsLocalAndIpBasedHosts() {
        assertNull(StoreUrlPolicy.normalizeStorefrontUrl("https://localhost"))
        assertNull(StoreUrlPolicy.normalizeStorefrontUrl("https://192.168.1.1"))
        assertNull(StoreUrlPolicy.normalizeStorefrontUrl("https://2130706433"))
    }

    @Test
    fun rejectsUnexpectedPortsAndMalformedUrls() {
        assertNull(StoreUrlPolicy.normalizeStorefrontUrl("https://shop.example.com:8443"))
        assertNull(StoreUrlPolicy.normalizeStorefrontUrl("not a url"))
    }

    @Test
    fun matchesOnlyTheStoreDomainAndExplicitPaymentProviders() {
        assertEquals(true, StoreUrlPolicy.isStorefrontHost("www.shop.example.com", "shop.example.com"))
        assertEquals(false, StoreUrlPolicy.isStorefrontHost("shop.example.com.attacker.net", "shop.example.com"))
        assertEquals(true, StoreUrlPolicy.isSupportedPaymentHost("checkout.razorpay.com"))
        assertEquals(false, StoreUrlPolicy.isSupportedPaymentHost("razorpay.com.attacker.net"))
    }
}
