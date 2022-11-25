package tasklist
import com.squareup.moshi.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.datetime.*
import java.io.File
import java.time.LocalTime
import kotlin.Exception

val moshi:Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory())
    .add(LocalDateAdapter())
    .add(LocalTimeAdapter())
    .build()
class LocalTimeAdapter {
    @ToJson
    fun toJson(ldt: LocalTime): String {
        return ldt.toString()
    }

    @FromJson
    fun fromJson(ldt: String): LocalTime {
        return LocalTime.parse(ldt)
    }
}

class LocalDateAdapter {
    @ToJson
    fun toJson(ld: LocalDate): String {
        return ld.toString()
    }

    @FromJson
    fun fromJson(ld: String): LocalDate {
        return LocalDate.parse(ld)
    }
}
fun main() {
    val taskStorageImpl = TaskStorageImpl()
    taskStorageImpl.start()
    while (true) {
        when (searchInEnum<Actions>(getSomething("Input an action (add, print, edit, delete, end):"))) {
            Actions.ADD -> taskStorageImpl.add()
            Actions.PRINT -> taskStorageImpl.print()
            Actions.END -> {taskStorageImpl.end(); break}
            Actions.DELETE -> taskStorageImpl.delete()
            Actions.EDIT -> taskStorageImpl.edit()
            Actions.EXCEPTION -> println("The input action is invalid")
        }
    }
}

fun getSomething(textBefore:String):String{
    println(textBefore)
    return readln()
}

//Serching in enum class by value. Your enum class need exeption value at the end
inline fun <reified EnumType: Enum<EnumType>> searchInEnum(value:String):EnumType {
    return try {
        enumValueOf(value.uppercase())
    }catch (e:IllegalArgumentException){
        enumValues<EnumType>().last()
    }
}

//User Actions
enum class Actions{
    ADD, PRINT, END,
    DELETE, EDIT, EXCEPTION
}

enum class TaskPriority(val priority:String, val color:String){
    C("C","\u001B[101m \u001B[0m"),
    H("H","\u001B[103m \u001B[0m"),
    N("N","\u001B[102m \u001B[0m"),
    L("L","\u001B[104m \u001B[0m"),
    ERROR("Error","\u001B[101m \u001B[0m")
}
enum class Tag(val color: String){
    I("\u001B[102m \u001B[0m"),
    T("\u001B[103m \u001B[0m"),
    O("\u001B[101m \u001B[0m")
}
data class TaskInformation(var date:LocalDate, var time:LocalTime, var taskInf:String,var overdue:Tag,
                           var priority:TaskPriority):Comparable<TaskInformation>{
    override fun compareTo(other: TaskInformation): Int {
        when(other.priority){
            TaskPriority.L -> return when (priority) {
                TaskPriority.L -> 0
                else -> 1
            }
            TaskPriority.N -> return when (priority) {
                TaskPriority.N -> 0
                TaskPriority.L -> -1
                else -> 1
            }
            TaskPriority.H -> return when (priority) {
                TaskPriority.H -> 0
                TaskPriority.C -> 1
                else -> -1
            }
            TaskPriority.C -> return when (priority) {
                TaskPriority.C -> 0
                else -> -1
            }
            else -> return 0
        }
    }
}

interface TaskStorage{
    fun start()
    fun add()
    fun print()
    fun delete()
    fun edit()
    fun printByPriority()
    fun end()
}

@Suppress("UNREACHABLE_CODE")
class TaskStorageImpl:TaskStorage{
    val taskInfMutableListType = Types.newParameterizedType(MutableList::class.java, TaskInformation::class.java)
    val moshiAdapter = moshi.adapter<MutableList<TaskInformation>>(taskInfMutableListType)
    var listOfTasks = mutableListOf<TaskInformation>()
    val listOfFields = listOf("priority", "date", "time", "task")
    val jsonFile = File("tasklist.json")

    private fun updateCurrentDate() = Clock.System.now().toLocalDateTime(TimeZone.of("UTC+0")).date

    private fun isNumeric(toCheck: String): Boolean {
        return toCheck.all { char -> char.isDigit() }
    }

    private fun deleteEditCorrect(textBefore: String, invalidText:String, textAfter:String, deleteEdit: (Int) -> Unit){
        while (true){
            val value = getSomething("$textBefore " +
                        "(${listOfTasks.indices.first + 1}-${listOfTasks.indices.last + 1}):")

                if(isNumeric(value) && value.toInt() <= listOfTasks.indices.last + 1 && value.toInt() > 0 ){
                    deleteEdit(value.toInt())
                    println(textAfter)
                    break
                }else println(invalidText)
        }
    }

    override fun start() {
        try {
            val textFromFile = moshiAdapter.fromJson(jsonFile.readText())
            if(!textFromFile.isNullOrEmpty()){
                listOfTasks.addAll(textFromFile)
            }
        }catch (e:Exception){
            listOfTasks = mutableListOf()
        }
    }

    override fun add() {
        val task = inputNewTask()
        listOfTasks.add(task)
    }
    private fun checkListOfTasksIsEmpty():Boolean{
        if(listOfTasks.isEmpty()){
            println("No tasks have been input")
            return true
        }else return false
    }

    override fun print() {
        if(!checkListOfTasksIsEmpty()){
            println(header)
            listOfTasks.forEachIndexed { index, s ->
                s.overdue = updateOverdue(updateCurrentDate(), s.date)
                    println("""$separator
| ${index + 1}  | ${s.date} | ${s.time} | ${s.priority.color} | ${s.overdue.color} |${s.taskInf}""".trimIndent())
            }
            println(separator)
        }
    }

    override fun delete() {
        if(!checkListOfTasksIsEmpty()) {
            print()
            deleteEditCorrect(textBefore = "Input the task number",
                    invalidText = "Invalid task number",
                    textAfter = "The task is deleted",
                    deleteEdit = ::deleteAt)

        }
    }
    private fun deleteAt(value:Int){
        listOfTasks.removeAt(value - 1)
    }

    override fun edit() {
        if(!checkListOfTasksIsEmpty()) {
            print()
            deleteEditCorrect(textBefore = "Input the task number",
                    invalidText = "Invalid task number",
                    textAfter = "The task is changed",
                    deleteEdit = ::editAt)
        }
    }

    private fun editAt(index:Int){
        var expt = true
        while (expt){
            val value = getSomething("Input a field to edit (priority, date, time, task):")
            if(!listOfFields.contains(value)){
                expt = true ; println("Invalid field")
            }else when(value){
                "priority" -> {listOfTasks[index - 1].priority = getPriority();expt = false}
                "date" -> {listOfTasks[index - 1].date = getDate();expt = false}
                "time" -> {listOfTasks[index - 1].time = getTime();expt = false}
                "task" -> {listOfTasks[index - 1].taskInf = getTaskInformation();expt = false}
            }
        }
    }

    private fun inputNewTask():TaskInformation = TaskInformation(
            priority = getPriority(),
            date = getDate(),
            time = getTime(),
            taskInf = getTaskInformation(),
            overdue = Tag.O)

    private fun updateOverdue(currentDate: LocalDate, date: LocalDate):Tag = if(currentDate.daysUntil(date) > 0)  Tag.I else if(currentDate.daysUntil(date) < 0)  Tag.O else Tag.T

    private fun getTaskInformation():String{
        var inf = ""
        var firstFlag = true
        println("Input a new task (enter a blank line to end):")
        do{
            val readln = readln()
            if(allIsBlank(readln,inf)) {
                println("The task is blank")
                continue
            }
            if(!firstTimeAndNotBlank(firstFlag, readln)){
                readln.chunked(44){
                    inf = inf.toTaskInf(it.toString())
                }
            }
            if(firstTimeAndNotBlank(firstFlag, readln)){
                val chanked = readln.chunked(44)
                chanked.forEach{
                    inf = if(chanked[0] == it) inf.toTaskInfFirst(it) else inf.toTaskInf(it)
                }
                    firstFlag = false
                }
            if(infIsBlankAndWrite(readln, inf)) inf += readln.trim()
        }while(readln.isNotBlank())
        return inf.trim(' ')
    }
    private fun firstTimeAndNotBlank(state:Boolean,readln: String):Boolean =
        state && readln.isNotBlank() && readln.isNotBlank()

    private fun infIsBlankAndWrite(readln: String, inf:String):Boolean = readln.isNotBlank() && inf.isBlank()

    private fun allIsBlank(readln: String, inf:String):Boolean = readln.isBlank() && inf.isBlank()

    private fun String.toTaskInf(readln:String) = "$this|    |            |       |   |   |" + readln.trim()
        .padEnd(44) + "|\n"

    private fun String.toTaskInfFirst(readln:String) = this + readln.trim().padEnd(44) + "|\n"

    private fun getPriority():TaskPriority{
        var taskPriority = searchInEnum<TaskPriority>(getSomething("Input the task priority (C, H, N, L):"))
        while(taskPriority == TaskPriority.ERROR){
            taskPriority = searchInEnum(getSomething("Input the task priority (C, H, N, L):"))
        }
        return taskPriority
    }
    private fun getTime():LocalTime{
        var timeList:List<String>
        var expt = true
        while (expt) {
            try {
                timeList = getSomething("Input the time (hh:mm):").split(":").toList()
                return LocalTime.of(timeList[0].toInt(), timeList[1].toInt())
                expt = false
            } catch (e: Exception) {
                expt = true
                println("The input time is invalid")
            }
        }
        return LocalTime.now()
    }

    private fun getDate():LocalDate{
        var expt = true
        while (expt) {
            try {
                val list = getSomething("Input the date (yyyy-mm-dd):")
                        .split("-")
                        .toList()
                        .map{it.toInt()}
                return LocalDate(list[0],list[1],list[2])
                expt = false
            } catch (e: Exception) {
                expt = true
                println("The input date is invalid")
            }
        }
        return Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }

    override fun printByPriority() {
        listOfTasks.sort() //check data class
        if(!checkListOfTasksIsEmpty()){
            println(header)
            listOfTasks.forEachIndexed { index, s ->
                s.overdue = updateOverdue(updateCurrentDate(), s.date)
                println("""$separator
| ${index + 1}  | ${s.date} | ${s.time} | ${s.priority.color} | ${s.overdue.color} |${s.taskInf}""".trimIndent())
            }
        }
    }

    override fun end() {
        val jsonFile = File("tasklist.json")
        jsonFile.writeText(moshiAdapter.toJson(listOfTasks))
        println("Tasklist exiting!")
    }
}