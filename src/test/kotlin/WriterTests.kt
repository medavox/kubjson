import com.github.medavox.kubjson.Writer
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

internal fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

fun main() {
    //tust('h')
    //tust('д')
}


@Test
fun testWriteShortAsUint8() {
    Writer.writeUint8(hexOf(127))
    Writer.writeUint8(hexOf(128))
    Writer.writeUint8(hexOf(255))
    Writer.writeUint8(hexOf(256))
}

internal fun tust(c:Char) {
    with(System.out) {
        //println("char size in bytes:" + Char.SIZE_BYTES)
        print("bytes of '$c': ")
        println(ByteBuffer.allocate(Char.SIZE_BYTES).putChar(c).array().toHexString())
        print("'$c'.toByte: ")
        println(byteArrayOf(c.toByte()).toHexString())
    }
    Writer.writeChar(c)
}
fun hexOf(owt:Short):Short {
    with(System.out) {
        //println("char size in bytes:" + Char.SIZE_BYTES)
        print("bytes of '$owt': ")
        println(ByteBuffer.allocate(Short.SIZE_BYTES).putShort(owt).array().toHexString())
    }
    return owt
}