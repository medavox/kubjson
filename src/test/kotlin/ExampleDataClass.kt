data class ExampleDataClass(val bougie:Boolean,
                            val fish:Int,
                            val dollars:Long,
                            val name:String,
                            val shitsGiven:Float,
                            val penisSizeInches:Double,
                            val kerakte:Char,
                            val listOfPeopleIveSleptWith:Array<String>,
                            val faker:Boolean?=null) {
    constructor(naam:String, dalers:Long) : this(bougie=true, fish=0, dollars=dalers, name=naam, shitsGiven=0F,
        penisSizeInches=-3.0, kerakte='X', listOfPeopleIveSleptWith=arrayOf<String>())
}
