import com.ustadmobile.door.*
import com.ustadmobile.door.jdbc.types.Date
import db2.ExampleDao2
import db2.ExampleDatabase2
import db2.ExampleDatabase2_JdbcKt
import db2.ExampleEntity2
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ExamplePresenter<V :  ExampleView> (private val view: V, private val lifecycleOwner: DoorLifecycleOwner): DoorLifecycleObserver() {

    private var dao: ExampleDao2? = null

    private val observer = ObserverFnWrapper<List<ExampleEntity2>>{
        view.list = it.sortedByDescending { data -> data.uid }
    }

    init {
        lifecycleOwner.addObserver(this)
    }

    fun onCreate(){
        GlobalScope.launch {
            val builderOptions = DatabaseBuilderOptions(
                ExampleDatabase2::class,
                ExampleDatabase2_JdbcKt::class, "jsDb1","./worker.sql-asm-debug.js")

            val database =  DatabaseBuilder.databaseBuilder<ExampleDatabase2>(builderOptions).build()
            dao = database.exampleDao2()

            val listLiveData = dao?.queryAllLiveAsync()?.getData(0, Int.MAX_VALUE)
            listLiveData?.removeObserver(observer)
            listLiveData?.observe(lifecycleOwner,observer)
        }
    }

    fun handleSaveEntity(entity: ExampleEntity2){
       GlobalScope.launch {
           if(entity.uid == 0L){
               val id = dao?.insertAsyncAndGiveId(entity.apply {
                   someNumber = Date().getTime().toLong()
               })
               console.log("Inserted rowID $id")
           }else{
               val id = dao?.updateByParamAsync(entity.name ?: "", entity.someNumber)
               console.log("Updated rowID $id")
           }
           view.entity = ExampleEntity2()
       }
    }

    fun handleEditEntity(entity: ExampleEntity2){
        view.entity = entity
    }

    fun onDestroy(){
        lifecycleOwner.removeObserver(this)
    }
}