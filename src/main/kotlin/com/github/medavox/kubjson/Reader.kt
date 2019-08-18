package com.github.medavox.kubjson

import com.github.medavox.kubjson.Markers.*
import java.io.InputStream
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import javax.validation.constraints.Size
import kotlin.reflect.*

class Reader(inputStream: InputStream) {
    private val shim = InputStreamShim(inputStream)

    fun readAnything():Any? {
        return readAnything(readChar(shim.readOneByte()))
    }

    //All integer types (int8, uint8, int16, int32 and int64) are written in most-significant-bit order
    // (high byte written first, aka “big endian“).

    //The char type is synonymous with 1-byte, UTF8 encoded value (decimal values 0-127).
    // A char value must not have a decimal value larger than 127.

    //The Universal Binary JSON specification dictates UTF-8 as the required string encoding
    // (this includes the high-precision number type as it is a string-encoded value).

    //using a listener is no good, because it can't handle container types:
    // how would you tell if onBoolean() was being called for a value in the current type,
    // or a type inside that type?
    fun readAnything(typeChar: Char):Any? {
        //Printa("readAnything").rintln("typeChar passed:$typeChar")
        return when(typeChar) {
            NULL_TYPE.marker -> null
            //since the string type is used more than the others (for object variable names),
            //check it early to avoid having to go through all the other types
            STRING_TYPE.marker -> {
                val strLength = readLength()
                if (strLength < 0) {
                    throw UbjsonParseException("String specified a negative length value", shim.bytesReadSoFar)
                }
                if(strLength > Int.MAX_VALUE) {
                    throw UbjsonParseException("String length is longer than maximum supported by JVM: $strLength",
                        shim.bytesReadSoFar)
                }
                val nextBytes = shim.readBytes(strLength.toInt())
                readString(nextBytes)
            }
            TRUE_TYPE.marker -> true
            FALSE_TYPE.marker -> false
            NO_OP_TYPE.marker -> Unit //fixme: returning Unit implicitly casts to any
            INT8_TYPE.marker -> readInt8(shim.readOneByte())
            UINT8_TYPE.marker -> readUint8(shim.readOneByte())
            INT16_TYPE.marker -> readInt16(shim.readBytes(2))
            INT32_TYPE.marker -> readInt32(shim.readBytes(4))
            INT64_TYPE.marker -> readInt64(shim.readBytes(8))
            FLOAT32_TYPE.marker -> readFloat32(shim.readBytes(4))
            FLOAT64_TYPE.marker -> readFloat64(shim.readBytes(8))
            CHAR_TYPE.marker -> readChar(shim.readOneByte())
            HIGH_PRECISION_NUMBER_TYPE.marker -> {
                val strLength = readLength()
                if (strLength < 0) {
                    throw UbjsonParseException("High-precision number specified a negative length value",
                        shim.bytesReadSoFar)
                }
                if(strLength > Int.MAX_VALUE) {
                    throw UbjsonParseException("High-precision number length is longer than maximum supported by JVM:" +
                            " $strLength", shim.bytesReadSoFar)
                }
                val nextBytes = shim.readBytes(strLength.toInt())
                readHighPrecisionNumber(nextBytes)
            }
            OBJECT_START.marker -> readObjectWithoutType()
            ARRAY_START.marker -> readArray()//oh look recursion. yay. -_-
            else -> throw UbjsonParseException("unexpected char in type marker: $typeChar", shim.bytesReadSoFar)
        }
    }

    /**it's a bad  idea to pass all the bytes at the start, because we don't know how many bytes we'll need,
     and we might try and pass more bytes than are left in the array, even if those extras bytes are not needed*/
    internal fun readLength():Long {
        val p = Printa("readLength")
        val oneByte = shim.readOneByte()
        //p.rintln("potential numeric type marker: $oneByte=${readChar(oneByte)}")
        return when(readChar(oneByte)) {
            INT8_TYPE.marker -> readInt8(shim.readOneByte()).toLong()
            INT16_TYPE.marker -> readInt16(shim.readBytes(2)).toLong()
            INT32_TYPE.marker -> readInt32(shim.readBytes(4)).toLong()
            INT64_TYPE.marker -> readInt64(shim.readBytes(8))
            else -> throw UbjsonParseException("was expecting a numeric type marker, but got " +
                    "${oneByte} = '${readChar(oneByte)}'", shim.bytesReadSoFar)
        }
    }

    /**Reads an arbitrary number of bytes from the passed [InputStream], until an object is parsed or an error occurs.
     *
     * Unlike the `read` methods for value types, we can't pass a ByteArray to this function,
     * because we don't know in advance how many bytes long the object is.
     * In those cases finding out its count in bytes would amount to parsing it.
     *
     * (Technically, we do know the byte-count of a homogeneous object with a count marker,
     * whose elements are of a fixed-count type.
     * But that is too specific a case to bother handling separately.) */
    internal fun <T:Any> readObject(toType: KClass<T>):T {
        val map:Map<String, Any?> = readObjectWithoutType()
        val p = Printa("readObject")
        p.rintln("map:")
        map.forEach { p.rintln(it) }
        //option a: manually set each of the instance's properties with elements from the map,
        //whose name and type match NAH

        fun <R> KFunction<R>.asString():String {
            val params:String = parameters.fold("") { acc:String, param:KParameter ->
                val typ = param.type.toString()
                val classString=if(typ.startsWith("kotlin.")) typ.substring(7) else typ
                acc+", "+if(param.isVararg){"vararg "}else{""}+"${param.name}:$classString"}
                .substring(2)
            return "fun <init>($params): $returnType"
        }

        //option B: find a constructor whose name/type pairs all match entries in the map
        val constructorToUse: KFunction<T> =
            toType.constructors.firstOrNull{ construc -> construc.parameters.all { param ->
                val mapClass = map[param.name]?.javaClass?.kotlin
                if(param.type.classifier != mapClass) {
                    p.rintln("with constructor  ${construc.asString()}:")
                    p.rintln("param \'${param.name}\' classifier:" + param.type.classifier+
                            "doesn't match value classifier: $mapClass\n")
                }
                param.isOptional || map.containsKey(param.name) &&
                        (param.type.classifier == mapClass || mapClass?.supertypes?.any { it == param.type } ?: false)
            }} ?: throw IllegalArgumentException("no constructor could be satisfied for class \'$toType\'. " +
            //throw an exception if no constructor could be satisfied (given the names & types of the variables we got)
            //which creates an instance of this class
                    map.asSequence().fold("UBJSON properties given:") {acc, entry ->
                        acc+"\n\t${entry.key} = "+if(entry.value is Array<*>) {Arrays.toString(entry.value as Array<*>)
                        } else entry.value}+ "\n\n"+
                    toType.constructors.fold("available constructors:") { acc, constr ->
                        acc+"\n\t${constr.asString()}"
                    }
            )

        val constructorParams:Map<KParameter, Any> = constructorToUse.parameters.filter{!it.isOptional}.
            associateWith { param:KParameter ->
            map.asSequence().firstOrNull { entry:Map.Entry<String, Any?> ->
                val valueClass = entry.value?.javaClass?.kotlin
                entry.key == param.name && (param.type.classifier == valueClass ||
                        valueClass?.supertypes?.any{it == param.type} ?: false)
            }?.value ?: throw IllegalArgumentException("UBJSON properties given didn't match constructor " +
                    "\'${constructorToUse.asString()}\'. "+map.asSequence().fold("UBJSON properties given:") {acc, entry ->
                acc+"\n\t${entry.key} = "+if(entry.value is Array<*>) {Arrays.toString(entry.value as Array<*>)
                } else entry.value})
        }

        val g:T = constructorToUse.callBy(constructorParams)
        return g

        //Don't check static methods or companion object's functions,
        //because they might not return an instance containing this data, but rather some compile-time constant,
        //like BigDecimal.ONE
    }

    /** an arbitrary number of bytes from the passed [InputStream], until an object is parsed or an error occurs.
     * This method writes the values to a Map<String, Any?>, without defining its type more explicitly.*/
    internal fun readObjectWithoutType():Map<String, Any?> {
        val values:MutableMap<String, Any?> = mutableMapOf()
        val p = Printa("readObjectWithoutType")
        val (homogeneousType, count) = checkForContainerTypeAndOrCount()
        val looper = CountDependentIterator(OBJECT_END, count)
        //loop through the array
        while(looper.unfinished()) {
            val name:String = readAnything(STRING_TYPE.marker) as String
            val data = readAnything(homogeneousType ?: readChar(shim.readOneByte()))
            //p.rintln("adding: {$name | $data}")
            values.put(name, data)
            looper.step()
        }
        return values
    }

    //can't make a function castTo(any:Any?, marker:Markers).
    //what would the return type be?

    private data class ContainerTypeAndOrCount(val homogeneousType:Char?, val count:Long?)
    private fun checkForContainerTypeAndOrCount():ContainerTypeAndOrCount {
        //NOTE: don't use a BufferedInputStream here.
        // wrapping the normal InputStream in a new BufferedInputStream instance causes that BufferedInputStream,
        //upon its first read call, to read THE ENTIRE CONTENTS of the InputStream it's wrapping.
        //this makes later calls to the normal InputStream's read(ByteArray) run out of input.
        val homogeneousType:Char? = if(readChar(shim.peekNextByte()) == HOMOGENEOUS_CONTAINER_TYPE.marker) {
            //skip past the first byte since we know it says homogeneous
            readChar(shim.readBytes(2)[1])
        } else { null }//we haven't consumed the byte checked for, so leave it to be checked as a count

        val possibleCountMarker = readChar(shim.peekNextByte())
        val countIfSpecified:Long? = if (possibleCountMarker != CONTAINER_LENGTH.marker) {
            if(homogeneousType != null) {
                //count is null, so homogeneous type must not be specified
                throw UbjsonParseException("Homogeneous type marker must not be specified without a count marker",
                    shim.bytesReadSoFar)
            }else {//homogeneous type is null, so length may safely be null
                null
            }
        } else {//count is specified, so read it
            shim.readOneByte()
            readLength()
        }

        //validate count
        if(countIfSpecified != null) {
            //the UBJSON spec explicitly requires checking for incorrectly negative count values:
            //http://ubjson.org/developer-resources/#library_req
            if (countIfSpecified < 0) {
                throw UbjsonParseException("array specified a negative count value", shim.bytesReadSoFar)
            }
            if (countIfSpecified > Int.MAX_VALUE) {
                throw UbjsonParseException("array count is longer than maximum supported by JVM: $countIfSpecified",
                    shim.bytesReadSoFar)
            }
        }

        return ContainerTypeAndOrCount(homogeneousType, countIfSpecified)
    }

    private inner class CountDependentIterator(containerEnd:Markers, count:Long?=null) {
        private var nextByte:Byte = shim.peekNextByte()
        //count?.let { p.rintln("count: $it") }
        private var index = 0
        //define end conditions
        val unfinished:() -> Boolean = if(count != null) {{
            index < count
        }}else {{
            readChar(nextByte) != containerEnd.marker
        }}

        //define loop step/increment
        val step:() -> Unit = if(count != null) {{
            index++
        }} else {{
            nextByte = shim.peekNextByte()
        }}
    }

    /**Reads an arbitrary number of bytes from the passed [InputStream], until the array is parsed or an error occurs.
     *
     * Unlike the `read` methods for value types, we can't pass a ByteArray to this function,
     * because we don't know in advance how many bytes long the array is.
     * In those cases finding out its count in bytes would amount to parsing it.
     *
     * (Technically, we do know the byte-count of a homogeneous array with a count marker,
     * whose elements are of a fixed-length type.
     * But that is too specific a case to bother handling separately.)*/
    fun readArray():Array<out Any?> {
        val values:MutableList<Any?> = mutableListOf()

        val (homogeneousType, count) = checkForContainerTypeAndOrCount()
        val looper = CountDependentIterator(ARRAY_END, count)

        //loop through the array
        while(looper.unfinished()) {
            val data = readAnything(homogeneousType ?: readChar(shim.readOneByte()))
            values.add(data)
            looper.step()
        }
        //cast array to a more specific type
        //if we've already been told the type of all elements in this array, use that
        return when(homogeneousType) {
            NULL_TYPE.marker -> arrayOfNulls(values.size)
            //since the string type is used more than the others (for object variable names),
            //check it early to avoid having to go through all the other types
            STRING_TYPE.marker -> values.map{it as String}.toTypedArray()
            TRUE_TYPE.marker, FALSE_TYPE.marker -> values.map{it as Boolean}.toTypedArray()
            NO_OP_TYPE.marker -> values.map{it as Unit}.toTypedArray()
            INT8_TYPE.marker -> values.map{it as Byte}.toTypedArray()
            UINT8_TYPE.marker -> values.map{ readUint8(it as Byte)}.toTypedArray()
            INT16_TYPE.marker -> values.map{it as Short}.toTypedArray()
            INT32_TYPE.marker -> values.map{it as Int}.toTypedArray()
            INT64_TYPE.marker -> values.map{it as Long}.toTypedArray()
            FLOAT32_TYPE.marker -> values.map{it as Float}.toTypedArray()
            FLOAT64_TYPE.marker -> values.map{it as Double}.toTypedArray()
            CHAR_TYPE.marker -> values.map{it as Char}.toTypedArray()
            HIGH_PRECISION_NUMBER_TYPE.marker -> values.map{it as BigDecimal}.toTypedArray()
            OBJECT_START.marker -> values.map{it as Any}.toTypedArray()//non-null, because a read object is guaranteed to exist
            ARRAY_START.marker -> values.map{it as Array<Any?>}.toTypedArray()
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
            else -> throw UbjsonParseException("unexpected char/byte in type marker: $homogeneousType",
                shim.bytesReadSoFar)
        }
    }

    companion object {
        /**Read the contents of the Int8 from the start of the passed [ByteArray],
         * without a preceding type marker or count.*/
        internal fun readInt8(b: Byte): Byte {
            return ByteBuffer.wrap(byteArrayOf(b)).order(ByteOrder.BIG_ENDIAN).get()
        }

        /**Read the contents of the UInt8 in the start of the passed [ByteArray],
         * without a preceding type marker or count.*/
        @UseExperimental(ExperimentalUnsignedTypes::class)
        internal fun readUint8(b: Byte): UByte {
            return readInt8(b).toUByte()
        }

        /**Read a UBJSON Int16 value into a JVM Short.
         * @param b a [ByteArray] of the contents (no type marker)*/
        internal fun readInt16(@Size(min = 2, max = 2) b: ByteArray): Short {
            return ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).getShort()
        }

        /**Read the contents of the Int32 contained at the start of the passed [ByteArray] into a JVM Int(eger),
         * without a preceding type marker or count.*/
        internal fun readInt32(@Size(min = 4, max = 4) b: ByteArray): Int {
            return ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).getInt()
        }

        /**Read the contents of the Int64 contained at the start of the passed [ByteArray] into a JVM Long,
         * without a preceding type marker or count.*/
        internal fun readInt64(@Size(min = 8, max = 8) b: ByteArray): Long {
            return ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).getLong()
        }

        internal fun readFloat32(@Size(min = 4, max = 4) b: ByteArray): Float {
            return ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).getFloat()
        }

        internal fun readFloat64(@Size(min = 8, max = 8) b: ByteArray): Double {
            return ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).getDouble()
        }

        internal fun readChar(b: Byte): Char {
            return readInt8(b).toChar()
        }

        /**Read the value of a UBJSON UTF-8 encoded string into a JVM UTF-16 String
         * @param b a [ByteArray] containing just the content of the string -- no type marker or count field*/
        internal fun readString(b: ByteArray): String {
            return b.toString(Charsets.UTF_8)
        }

        internal fun readHighPrecisionNumber(b: ByteArray): BigDecimal {
            return BigDecimal(readString(b))
        }
    }
}