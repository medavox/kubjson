package com.github.medavox.kubjson

import java.math.BigDecimal

interface ReaderListener {
    fun onBoolean(boolean: Boolean)
    fun onNull()
    fun onNoOp()
    fun onInt8(int8:Byte)
    fun onUint8(uint8:UByte)
    fun onInt16(int16:Short)
    fun onInt32(int32:Int)
    fun onInt64(int64:Long)
    fun onFloat32(float32:Float)
    fun onFloat64(float64:Double)
    fun onChar(char:Char)
    fun onString(string:String)
    fun onHighPrecisionNumber(highPrecisionNumber:BigDecimal)
}