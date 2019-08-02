package com.github.medavox.kubjson

class Printa(private val tag:String) {
    fun rintln(msg: String) {
        println("$tag : $msg")
    }

    fun rintln(owt:Any?) {
        println("$tag : "+(owt?.toString() ?: "<null>"))
    }
}