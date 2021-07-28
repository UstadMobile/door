import com.ccfraser.muirwik.components.*
import com.ccfraser.muirwik.components.button.*
import com.ccfraser.muirwik.components.form.MFormControlVariant
import com.ccfraser.muirwik.components.list.mList
import com.ccfraser.muirwik.components.list.mListItem
import com.ustadmobile.door.DatabaseBuilder
import com.ustadmobile.door.DatabaseBuilderOptions
import db2.ExampleDao2
import db2.ExampleDatabase2
import db2.ExampleDatabase2_JdbcKt
import db2.ExampleEntity2
import kotlinx.browser.window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.css.*
import react.*
import styled.css
import styled.styledDiv
import wrappers.DatabaseExportToIndexedDbCallback
import wrappers.SQLiteDatasourceJs
import kotlin.js.Date

class AppComponent(mProps: RProps): RComponent<RProps, RState>(mProps) {

    private var entryList: List<ExampleEntity2>? = null

    private lateinit var dao: ExampleDao2

    private var entity: ExampleEntity2 = ExampleEntity2()

    override fun RState.init(props: RProps) {
        GlobalScope.launch {
            setupDatabase()
            fetchData()
        }
    }

    override fun RBuilder.render() {
        styledDiv {
            mGridContainer(MGridSpacing.spacing4) {
                mGridItem(MGridSize.cells8){
                    mTextField(label = "Entity name",
                        helperText = "",
                        value = entity.name ?: "",
                        variant = MFormControlVariant.outlined,
                        onChange = {
                            it.persist()
                            setState {
                                entity.name = it.targetInputValue
                            }
                        }){
                        css{
                            width = LinearDimension("100%")
                        }
                    }
                }

                mGridItem(MGridSize.cells4){
                    mButton(if(entity.uid == 0L) "Save Now" else "Update Now", size = MButtonSize.large,
                        disabled = entity.name.isNullOrEmpty(),
                        variant = MButtonVariant.contained,
                        onClick = {
                           if(entity.name.isNullOrEmpty()){
                               return@mButton
                           }
                            saveData()
                        }
                    ){
                        css{
                            marginTop = 3.spacingUnits
                            if(entity.name.isNullOrEmpty()){
                                backgroundColor = Color.green
                                color = Color.white
                            }else{
                                color = Color.black
                            }
                        }
                    }
                }

                mGridItem(MGridSize.cells12){
                    mList {
                        entryList?.forEach { entry ->
                            mListItem {
                                mGridContainer(MGridSpacing.spacing4) {
                                    mGridItem(MGridSize.cells5){
                                        mTypography(entry.name, variant = MTypographyVariant.body2)
                                    }

                                    mGridItem(MGridSize.cells3){
                                        mTypography(entry.uid.toString(), variant = MTypographyVariant.body2)
                                    }

                                    mGridItem(MGridSize.cells3){
                                        mTypography(entry.someNumber.toString(), variant = MTypographyVariant.body2)
                                    }

                                    mGridItem(MGridSize.cells1){
                                        mIconButton("edit",size = MIconButtonSize.medium, onClick = {
                                            editEntry(entry)
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun editEntry(entry: ExampleEntity2) {
        setState {
            entity = entry
        }
    }

    private fun saveData(){
        GlobalScope.launch {
            if(entity.uid == 0L){
                entity.apply {
                    uid = Date().getTime().toLong()
                    someNumber = Date().getTime().toLong()/3000
                }
                dao.insertAsync(entity)
            }else{
                entity.name?.let { dao.updateByParamAsync(it, entity.someNumber) }
            }

            window.setTimeout(fetchData(), 500)
        }
    }

    private suspend fun fetchData(){
        entity.name = null
        val dataList = dao.findAllAsync()
        setState {
            entryList = dataList
        }
    }

    private suspend fun exportHandler(datasource: SQLiteDatasourceJs){
        datasource.importDbToIndexedDb()
    }

    private suspend fun setupDatabase() {
        //Listen for tables to be changed and trigger save to indexdb
        val dbExportCallback = object: DatabaseExportToIndexedDbCallback{
            override suspend fun onExport(datasource: SQLiteDatasourceJs) {
                window.setTimeout(exportHandler(datasource), 5000)
            }
        }
        val builderOptions = DatabaseBuilderOptions(ExampleDatabase2::class, ExampleDatabase2_JdbcKt::class, "jsDb1")
        val database =  DatabaseBuilder.databaseBuilder<ExampleDatabase2>(builderOptions, dbExportCallback)
            .webWorker("./worker.sql-asm.js")
            .build()
        dao = database.exampleDao2()
    }
}

fun RBuilder.renderApp() = child(AppComponent::class) {}