package com.github.medavox.kubjson

import com.github.medavox.kubjson.Markers.*
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.UnsupportedEncodingException
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.ParseException
import javax.validation.constraints.Size
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier

object Reader {

    //All integer types (int8, uint8, int16, int32 and int64) are written in most-significant-bit order
    // (high byte written first, aka “big endian“).

    //The char type is synonymous with 1-byte, UTF8 encoded value (decimal values 0-127).
    // A char value must not have a decimal value larger than 127.

    //The Universal Binary JSON specification dictates UTF-8 as the required string encoding
    // (this includes the high-precision number type as it is a string-encoded value).

    //using a listener is no good, because it can't handle container types:
    // how would you tell if onBoolean() was being called for a value in the current type,
    // or a type inside that type?
    fun readAny(inputStream: InputStream, typeChar: Char):Any? {
        return when(typeChar) {
            NULL_TYPE.marker -> null
            TRUE_TYPE.marker -> true
            FALSE_TYPE.marker -> false
            NO_OP_TYPE.marker -> Unit //fixme: returning Unit implicitly casts to any
            INT8_TYPE.marker -> {
                val next1Byte = ByteArray(1)
                inputStream.read(next1Byte)
                readInt8(next1Byte[0])
            }
            UINT8_TYPE.marker -> {
                val next1Byte = ByteArray(1)
                inputStream.read(next1Byte)
                readUint8(next1Byte[0])
            }
            INT16_TYPE.marker -> {
                val next2Bytes = ByteArray(2)
                inputStream.read(next2Bytes)
                readInt16(next2Bytes)
            }
            INT32_TYPE.marker -> {
                val next4Bytes = ByteArray(4)
                inputStream.read(next4Bytes)
                readInt32(next4Bytes)
            }
            INT64_TYPE.marker -> {
                val next8Bytes = ByteArray(8)
                inputStream.read(next8Bytes)
                readInt64(next8Bytes)
            }
            FLOAT32_TYPE.marker -> {
                val nextBytes = ByteArray(4)
                inputStream.read(nextBytes)
                readFloat32(nextBytes)
            }
            FLOAT64_TYPE.marker -> {
                val nextBytes = ByteArray(8)
                inputStream.read(nextBytes)
                readFloat64(nextBytes)
            }
            CHAR_TYPE.marker -> {
                val next1Byte = ByteArray(1)
                inputStream.read(next1Byte)
                readChar(next1Byte[0])
            }
            STRING_TYPE.marker -> {
                val strLength = readLength(inputStream)
                if (strLength < 0) {
                    throw ParseException("String specified a negative length value", 0)
                }
                if(strLength > Int.MAX_VALUE) {
                    throw UnsupportedEncodingException("String length is longer than maximum supported by JVM:" +
                            strLength)
                }
                val nextBytes = ByteArray(strLength.toInt())
                inputStream.read(nextBytes)
                readString(nextBytes)
            }
            HIGH_PRECISION_NUMBER_TYPE.marker -> {
                val strLength = readLength(inputStream)
                if (strLength < 0) {
                    throw ParseException("High-precision number specified a negative length value", 0)
                }
                if(strLength > Int.MAX_VALUE) {
                    throw UnsupportedEncodingException("High-precision number length is longer than maximum" +
                            " supported by JVM: $strLength")
                }
                val nextBytes = ByteArray(strLength.toInt())
                inputStream.read(nextBytes)
                readHighPrecisionNumber(nextBytes)
            }
            OBJECT_START.marker -> {
                readObjectWithoutType(inputStream)
            }
            ARRAY_START.marker -> {
                readArray(inputStream)//oh look recursion. yay. -_-
            }
            else -> throw IllegalArgumentException("unexpected char in type marker: $typeChar")
        }
    }

    /**it's a bad  idea to pass all the bytes at the start, because we don't know how many bytes we'll need,
     and we might try and pass more bytes than are left in the array, even if those extras bytes are not needed*/
    internal fun readLength(input:InputStream):Long {
        val oneByte = ByteArray(1)
        input.read(oneByte)
        return when(readChar(oneByte[0])) {
            INT8_TYPE.marker -> {
                val ba = ByteArray(1)
                input.read(ba)
                readInt8(ba[0]).toLong()
            }
            INT16_TYPE.marker -> {
                val ba = ByteArray(2)
                input.read(ba)
                readInt16(ba).toLong()
            }
            INT32_TYPE.marker -> {
                val ba = ByteArray(4)
                input.read(ba)
                readInt32(ba).toLong()
            }
            INT64_TYPE.marker -> {
                val ba = ByteArray(8)
                input.read(ba)
                readInt64(ba)
            }
            else -> throw IllegalArgumentException("was expecting a numeric type marker, " +
                    "but got ${oneByte[0]} = '${oneByte[0].toChar()}'")
        }
    }

    /**Reads an arbitrary number of bytes from the passed [InputStream], until an object is parsed or an error occurs.
     *
     * Unlike the `read` methods for value types, we can't pass a ByteArray to this function,
     * because we don't know in advance how many bytes long the object is.
     * In those cases finding out its length in bytes would amount to parsing it.
     *
     * (Technically, we do know the byte-length of a homogeneous object with a length marker,
     * whose elements are of a fixed-length type.
     * But that is too specific a case to bother handling separately.) */
    internal fun <T:Any> readObject(input:InputStream, toType: KClass<T>):T {
        val map:Map<String, Any?> = readObjectWithoutType(input)
        val p = Printa("readObject")
        p.rintln("map:")
        map.forEach { p.rintln(it) }
        //option a: manually set each of the instance's properties with elements from the map,
        //whose name and type match
        p.rintln("constructors:")
        toType.constructors.forEach { p.rintln(it) }

        //option B: find a constructor whose name/type pairs all match entries in the map
        construc@ for(constructor in toType.constructors) {
            for(param in constructor.parameters) {
                if(map.containsKey(param.name)) {
                    //check it's the correct type,
                    //or can be cast to the correct type
                    val paramClass:KClassifier? = param.type.classifier
                    val candidate = map.get(param.name)
                    val mapClass:KClassifier? = if(candidate != null){candidate::class}else{null}
                    val areEqual:Boolean = paramClass == mapClass
                    p.rintln("parameter classifier: $paramClass")
                    p.rintln("map entry classifier: $mapClass")
                    p.rintln("types are equal: $areEqual")
                }else {
                    p.rintln("map doesn't contain a param named \"${param.name}\" for this constructor, moving on...")
                    //if the map contains no entry with this parameter's name,
                    //then that disqualifies this constructor,
                    //so move on to the next constructor, if any
                    continue@construc
                }
            }
        }

        //throw an exception if no constructor could be satisfied (given the names & types of the variables we got)
        //which creates an instance of this class
        //Don't check static methods, companion object's functions,
        //because they might not return an instance containing this data, but rather some compile-time constant,
        //like BigDecimal.ONE
        TODO()
    }

    /** an arbitrary number of bytes from the passed [InputStream], until an object is parsed or an error occurs.
     * This method writes the values to a Map<String, Any?>, without defining its type more explicitly.*/
    internal fun readObjectWithoutType(inputStream:InputStream):Map<String, Any?> {
        val values:MutableMap<String, Any?> = mutableMapOf()
        val p = Printa("readObjectWithoutType")
        val (homogeneousType, lengthIfSpecified, firstByte) = checkForContainerTypeAndOrLength(inputStream)
        var nextByte:Byte = firstByte
        lengthIfSpecified?.let { p.rintln("length: $it") }
        var index = 0
        val einByt = byteArrayOf()
        //define end conditions
        val unfinished:() -> Boolean = if(lengthIfSpecified != null) {{
            index < lengthIfSpecified
        }}else {{
            readChar(nextByte) == OBJECT_END.marker
        }}

        //define loop step/increment
        val step:() -> Unit = if(lengthIfSpecified != null) {{
            index++
        }} else {{
            inputStream.read(einByt)
            nextByte = einByt[0]
        }}
        //loop through the array
        while(unfinished()) {
            val name:String = readAny(inputStream, STRING_TYPE.marker) as String
            val data = readAny(inputStream, homogeneousType ?: readChar(nextByte))
            p.rintln("adding: {$name | $data}")
            values.put(name, data)
            step()
        }
        return values
    }


    private data class ContainerTypeAndOrLength(val homogeneousType:Char?, val lengthIfSpecified:Long?, val nextByte:Byte)
    private fun checkForContainerTypeAndOrLength(inputStream:InputStream):ContainerTypeAndOrLength {
        val input:BufferedInputStream = if(inputStream is BufferedInputStream) inputStream
                else BufferedInputStream(inputStream)//only wrap inputSTream in a BIS if it's not one already;
        val oneByte = ByteArray(1)
        input.read(oneByte)
        val homogeneousType:Char? = if(readChar(oneByte[0]) == HOMOGENEOUS_CONTAINER_TYPE.marker) {
            //index + 2//increment index past type marker here, so the last expression can be the return type
            val einBeit = ByteArray(1)
            input.read(einBeit)
            val homogeneousType = readChar(einBeit[0])
            //read the next byte into possibleTypeOrLength, so it's ready to be checked by the next if
            input.read(oneByte)
            homogeneousType
        } else { null }

        val lengthIfSpecified:Long? = when {
            readChar(oneByte[0]) == CONTAINER_LENGTH.marker -> {
                val len = readLength(input)
                //read next byte, so the first byte of the first contained value is always pre-consumed.
                //without doing this, sometime it'd be pre-consumed, and sometimes not, with no way to tell
                //it wouldn't be naturally preconsumed in this if-branch, but it would in every other
                input.read(oneByte)
                len
            }
            homogeneousType == null -> null
            else -> throw IllegalArgumentException("Container type marker must not be specified without a length marker")
        }

        //validate length
        if(lengthIfSpecified != null) {
            //the UBJSON spec explicitly requires checking for incorrectly negative length values:
            //http://ubjson.org/developer-resources/#library_req
            if (lengthIfSpecified < 0) {
                throw ParseException("array specified a negative length value", 0)
            }
            if (lengthIfSpecified > Int.MAX_VALUE) {
                throw UnsupportedEncodingException("array length is longer than maximum supported by JVM: $lengthIfSpecified")
            }
        }

        return ContainerTypeAndOrLength(homogeneousType, lengthIfSpecified, oneByte[0])
    }

    data class ArrayWithType(val array:Array<Any?>, val type:KClass<out Any>)

    /**Reads an arbitrary number of bytes from the passed [InputStream], until the array is parsed or an error occurs.
     *
     * Unlike the `read` methods for value types, we can't pass a ByteArray to this function,
     * because we don't know in advance how many bytes long the array is.
     * In those cases finding out its length in bytes would amount to parsing it.
     *
     * (Technically, we do know the byte-length of a homogeneous array with a length marker,
     * whose elements are of a fixed-length type.
     * But that is too specific a case to bother handling separately.)*/
    fun readArray(inputStream:InputStream):Array<out Any?> {
        val values:MutableList<Any?> = mutableListOf()

        val (homogeneousType, lengthIfSpecified, firstByte) = checkForContainerTypeAndOrLength(inputStream)
        var nextByte:Byte = firstByte

        var index = 0
        val einByt = byteArrayOf()
        //define end conditions
        val unfinished:() -> Boolean = if(lengthIfSpecified != null) {{
            index < lengthIfSpecified
        }}else {{
            readChar(nextByte) == ARRAY_END.marker
        }}

        //define loop step/increment
        val step:() -> Unit = if(lengthIfSpecified != null) {{
            index++
        }} else {{
            inputStream.read(einByt)
            nextByte = einByt[0]
        }}
        //loop through the array
        while(unfinished()) {
            val data = readAny(inputStream, homogeneousType ?: readChar(nextByte))
            values.add(data)
            step()
        }
        //cast array to a more specific type
        val returnArray:Array<out Any?> = when(homogeneousType) {
            //if we've already been told the type of all elements in this array, use that
            NULL_TYPE.marker -> values.toTypedArray()
            TRUE_TYPE.marker, FALSE_TYPE.marker -> values.toTypedArray() as Array<Boolean>
            NO_OP_TYPE.marker -> values.toTypedArray() as Array<Unit> //fixme: returning Unit implicitly casts to any
            INT8_TYPE.marker -> values.toTypedArray() as Array<Byte>
            UINT8_TYPE.marker -> values.toTypedArray()//fixme:choose a type
            INT16_TYPE.marker -> values.toTypedArray() as Array<Short>
            INT32_TYPE.marker -> values.toTypedArray() as Array<Int>
            INT64_TYPE.marker -> values.toTypedArray() as Array<Long>
            FLOAT32_TYPE.marker -> values.toTypedArray() as Array<Float>
            FLOAT64_TYPE.marker -> values.toTypedArray() as Array<Double>
            CHAR_TYPE.marker -> values.toTypedArray() as Array<Char>
            STRING_TYPE.marker -> values.toTypedArray() as Array<String>
            HIGH_PRECISION_NUMBER_TYPE.marker -> values.toTypedArray() as Array<BigDecimal>
            OBJECT_START.marker -> values.toTypedArray() as Array<Any>//non-null, because a red object is guaranteed to exist
            ARRAY_START.marker -> values.toTypedArray() as Array<Array<Any?>>//fixme
            null -> {
                //there is no homogeneous type marker, so
                //manually find most specific common ancestor of all the types found in the array, and return it as that
                val typesInArray:Set<KClass<*>?> = values.map { it?.javaClass?.kotlin}.toSet()
                //println("types in array: "+typesInArray)
                if(typesInArray.size > 1) {
                    //if there is > 1 type in the whole array, use heterogeneous array syntax
                }else {
                    // otherwise, make a homogeneous array of that one type
                }
                values.toTypedArray()
            }
            else -> throw IllegalArgumentException("unexpected char/byte in type marker: " +
                    "$nextByte/${readChar(nextByte)}")
        }
        return returnArray
    }


    /**Read the contents of the Int8 from the start of the passed [ByteArray], without a preceding type marker or length.*/
    internal fun readInt8(b:Byte):Byte {
        return ByteBuffer.wrap(byteArrayOf(b)).order(ByteOrder.BIG_ENDIAN).get()
    }

    /**Read the contents of the UInt8 in the start of the passed [ByteArray], without a preceding type marker or length.*/
    @UseExperimental(ExperimentalUnsignedTypes::class)
    internal fun readUint8(b:Byte):UByte {
        return readInt8(b).toUByte()
    }

    /**Read a UBJSON Int16 value into a JVM Short.
     * @param b a [ByteArray] of the contents (no type marker)*/
    internal fun readInt16(@Size(min=2, max=2)b:ByteArray):Short {
        return ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).getShort()
    }

    /**Read the contents of the Int32 contained at the start of the passed [ByteArray] into a JVM Int(eger),
     * without a preceding type marker or length.*/
    internal fun readInt32(@Size(min=4, max=4)b:ByteArray):Int {
        return ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).getInt()
    }

    /**Read the contents of the Int64 contained at the start of the passed [ByteArray] into a JVM Long,
     * without a preceding type marker or length.*/
    internal fun readInt64(@Size(min=8, max=8)b:ByteArray):Long {
        return ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).getLong()
    }

    internal fun readFloat32(@Size(min=4, max=4)b:ByteArray):Float {
        return ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).getFloat()
    }

    internal fun readFloat64(@Size(min=8, max=8)b:ByteArray):Double {
        return ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).getDouble()
    }

    internal fun readChar(b:Byte):Char {
        return readInt8(b).toChar()
    }

    /**Read the value of a UBJSON UTF-8 encoded string into a JVM UTF-16 String
     * @param b a [ByteArray] containing just the content of the string -- no type marker or length field*/
    internal fun readString(b:ByteArray):String {
        return b.toString(Charsets.UTF_8)
    }

    internal fun readHighPrecisionNumber(b:ByteArray):BigDecimal {
        return BigDecimal(readString(b))
    }
}