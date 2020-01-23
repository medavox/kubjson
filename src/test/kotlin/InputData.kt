import com.github.medavox.kubjson.Markers.*
import org.apache.commons.codec.binary.Hex

val nullJvm = null
val nullUbjson = byteArrayOf(NULL_TYPE.marker.toByte())

val noopJvm = Unit
val noopUbjson = byteArrayOf(NO_OP_TYPE.marker.toByte())

val trueBooleanJvm = true
val trueBooleanUbjson = byteArrayOf(TRUE_TYPE.marker.toByte())

val falseBooleanJvm = false
val falseBooleanUbjson = byteArrayOf(FALSE_TYPE.marker.toByte())

val int8Jvm:Short = 127
val int8Ubjson = Hex.decodeHex(charArrayOf('i', 'ÿ'))

//val uint8Jvm:Short