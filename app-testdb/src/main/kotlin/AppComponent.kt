import com.ccfraser.muirwik.components.*
import com.ccfraser.muirwik.components.button.MButtonSize
import com.ccfraser.muirwik.components.button.MButtonVariant
import com.ccfraser.muirwik.components.button.mButton
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

class AppComponent(mProps: RProps): RComponent<RProps, RState>(mProps) {

    private var entryList: List<ExampleEntity2>? = null

    private lateinit var dao: ExampleDao2

    private var entity: ExampleEntity2 = ExampleEntity2()

    override fun RState.init(props: RProps) {
        GlobalScope.launch {
            setupDatabase()
        }
    }

    override fun RBuilder.render() {
        styledDiv {
            mGridContainer(MGridSpacing.spacing4) {
                mGridItem(MGridSize.cells8){
                    mTextField(label = "Entity name",
                        helperText = "",
                        value = entity.name,
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
                    mButton("Save Now", size = MButtonSize.large,
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
                                mGridContainer {
                                    mGridItem(MGridSize.cells11){
                                        mTypography(entry.name, variant = MTypographyVariant.body2)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun saveData(){
        GlobalScope.launch {
            dao.insertAsync(entity)
            window.setTimeout(suspend{
                val dataList = dao.findAllAsync()
                setState {
                    entryList = dataList
                }
            }, 500)
        }
    }

    private suspend fun setupDatabase() {
        val builderOptions = DatabaseBuilderOptions(ExampleDatabase2::class, ExampleDatabase2_JdbcKt::class, "jsDb1")
        val database =  DatabaseBuilder.databaseBuilder<ExampleDatabase2>(Any(),builderOptions)
            .webWorker("./worker.sql-asm.js")
            .build()
        dao = database.exampleDao2()
    }
}

fun RBuilder.renderApp() = child(AppComponent::class) {}