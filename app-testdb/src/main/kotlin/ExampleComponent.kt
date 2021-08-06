import com.ccfraser.muirwik.components.*
import com.ccfraser.muirwik.components.button.*
import com.ccfraser.muirwik.components.form.MFormControlVariant
import com.ccfraser.muirwik.components.list.mList
import com.ccfraser.muirwik.components.list.mListItem
import com.ustadmobile.door.DoorLifecycleObserver
import com.ustadmobile.door.DoorLifecycleOwner
import db2.ExampleEntity2
import kotlinx.css.*
import react.*
import styled.css
import styled.styledDiv

class ExampleComponent(mProps: RProps): RComponent<RProps, RState>(mProps),
    ExampleView, DoorLifecycleOwner {

    private var mPresenter: ExamplePresenter<*>? = null

    private var observerStatus: Int = -1

    private val observerList: MutableList<DoorLifecycleObserver> = mutableListOf()

    override var list: List<ExampleEntity2>? = null
        get() = field
        set(value) {
            setState {
                field = value
            }
        }

    override var entity: ExampleEntity2 = ExampleEntity2()
        get() = field
        set(value) {
            setState {
                field = value
            }
        }

    override fun componentDidMount() {
        mPresenter = ExamplePresenter(this, this)
        mPresenter?.onCreate()
        observerStatus = DoorLifecycleObserver.STARTED

        for (doorLifecycleObserver in observerList) {
            doorLifecycleObserver.onCreate(this)
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
                            mPresenter?.handleSaveEntity(entity)
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
                        mListItem {
                            mGridContainer(MGridSpacing.spacing4) {
                                mGridItem(MGridSize.cells3){
                                    mTypography("#", variant = MTypographyVariant.body2)
                                }

                                mGridItem(MGridSize.cells5){
                                    mTypography("Name", variant = MTypographyVariant.body2)
                                }

                                mGridItem(MGridSize.cells3){
                                    mTypography("Number", variant = MTypographyVariant.body2)
                                }

                                mGridItem(MGridSize.cells1){
                                }
                            }
                        }
                        list?.forEach { entry ->
                            mListItem {
                                mGridContainer(MGridSpacing.spacing4) {
                                    mGridItem(MGridSize.cells3){
                                        mTypography(entry.uid.toString(), variant = MTypographyVariant.body2)
                                    }

                                    mGridItem(MGridSize.cells5){
                                        mTypography(entry.name, variant = MTypographyVariant.body2)
                                    }

                                    mGridItem(MGridSize.cells3){
                                        mTypography(entry.someNumber.toString(), variant = MTypographyVariant.body2)
                                    }

                                    mGridItem(MGridSize.cells1){
                                        mIconButton("edit",size = MIconButtonSize.medium, onClick = {
                                            mPresenter?.handleEditEntity(entry)
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

    override val currentState: Int
        get() = observerStatus

    override fun addObserver(observer: DoorLifecycleObserver) {
        observerList.add(observer)
    }

    override fun removeObserver(observer: DoorLifecycleObserver) {
        observerList.remove(observer)
    }

    override fun componentWillUnmount() {
        observerStatus = DoorLifecycleObserver.STOPPED

        for (doorLifecycleObserver in observerList) {
            doorLifecycleObserver.onStop(this)
        }

        mPresenter?.onDestroy()
    }
}

fun RBuilder.renderApp() = child(ExampleComponent::class) {}