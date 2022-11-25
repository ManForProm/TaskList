open class Dog( name:String){
    var name = name
}// write here
class Labrador(name:String,val color:String,val age:Int):Dog(name){
    fun printInfo(){println("The dog's name is $name, his color is $color and his weight is $age")}
}
fun main(){
    val labrador = Labrador("Nola", "white", 4)
    labrador.printInfo()
}