package com.example.smartfridge.domain

enum class Freshness {
    FRESH,
    EXPIRING_SOON,
    EXPIRED
}

fun getFreshness(
    expiresAt: Long
): Freshness {

    val now =
        System.currentTimeMillis()

    val difference =
        expiresAt - now

    val days =
        difference /
                (1000L * 60L * 60L * 24L)

    return when {

        difference < 0 ->
            Freshness.EXPIRED

        days <= 2 ->
            Freshness.EXPIRING_SOON

        else ->
            Freshness.FRESH
    }
}