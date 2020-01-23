import com.github.medavox.kubjson.Printa
import com.github.medavox.kubjson.Reader
import com.github.medavox.kubjson.Writer
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

/**Tests which encode a JVM type (using the appropriate write method),
 * and then decode it using the corresponding [read method](Reader).
 *
 * This tests both methods together, which is less preferable than testing each individually
 * */
class CodecTests {
    @Test
    fun nullTest() {

    }
    @Test
    fun noopTest() {
        TODO()
    }
    @Test
    fun booleanTest() {
        TODO()
    }
    @Test
    fun int8Test() {
        TODO()
    }
    @Test
    fun uint8Test() {
        TODO()
    }@Test
    fun int16Test() {
        TODO()
    }
    @Test
    fun int32Test() {
        TODO()
    }
    @Test
    fun int64Test() {
        TODO()
    }
    @Test
    fun float32Test() {
        TODO()
    }
    @Test
    fun float64Test() {
        TODO()
    }
    @Test
    fun charTest() {
        TODO()
    }
    @Test
    fun stringTest() {
        TODO()
    }
    @Test
    fun highPrecisionNumberTest() {
        TODO()
    }

    @Test
    fun endToEndTest() {
        val p = Printa("main")
        val example = ExampleDataClass(
            true,
            fish = 36,
            dollars = 900000000,
            name = "alfred",
            kerakte = 'J',
            shitsGiven = 0.454f,
            penisSizeInches = -0.483754875,
            listOfPeopleIveSleptWith = arrayOf("Jack", "Jill", "Pail", "Wasserman")
        )
        p.rintln("object before decoding: $example")


        //val arrayOfInts:Array<Int> = arrayOf()
        //val intArray:IntArray = arrayOfInts.toIntArray()

        val exampleAsUbjson = Writer.writeAnything(example)
        //val exampleAsUbjson = byteArrayOf(*Writer.writeChar(Markers.OBJECT_START.marker), *Writer.writeObject(example))
        File("output.ubjson").writeBytes(exampleAsUbjson)
        p.rintln("output byte array length:${exampleAsUbjson.size}")
        val decodedExample = Reader(ByteArrayInputStream(exampleAsUbjson.sliceArray(1 until exampleAsUbjson.size))).readObject(
            ExampleDataClass::class)
        p.rintln("decoded object:$decodedExample")
    }

    /*val props = example.javaClass.kotlin.declaredMemberProperties
props.forEach{
    val a = it.returnType.classifier as KClass<*>
    println("classifier      :"+a)
    val b = it.get(example)?.javaClass?.kotlin
    println("raw data's class:"+b)
    println("equal: ${a == b}")
}*/
    /*val props = example::class.declaredMemberProperties
    for(prop in props) {
        val type = prop.returnType.classifier as KClass<*>
        println(prop.name+": "+type.qualifiedName)
    }*/
    // Writer.writeObject(example)
    //Writer.writeArray(arrayOf<Any?>(1, 2, 3, "yes", 55L, null))

    //val snul:String? = null;


}