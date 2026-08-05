package com.Marbleng

data class Config(
    val remark: String,
    val address: String,
    val port: Int,
    val type: String,
    val uuid: String? = null
)
