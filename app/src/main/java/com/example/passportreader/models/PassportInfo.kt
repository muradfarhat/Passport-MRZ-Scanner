package com.example.passportreader.models

data class PassportInfo(
    val documentType: String? = "",
    val countryCode: String? = "",
    val surname: String? = "",
    val givenNames: String? = "",
    val passportNumber: String? = "",
    val nationality: String? = "",
    val dateOfBirth: String? = "",
    val sex: String? = "",
    val expirationDate: String? = "",
    val personalNumber: String? = ""
)

