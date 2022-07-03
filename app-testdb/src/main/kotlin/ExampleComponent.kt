import androidx.lifecycle.FullLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import db2.ExampleEntity2
import kotlinx.css.*
import kotlinx.css.properties.border
import kotlinx.css.properties.scale
import kotlinx.css.properties.transform
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import react.*
import styled.*

class ExampleComponent(mProps: PropsWithChildren): RComponent<PropsWithChildren, State>(mProps),
    ExampleView, LifecycleOwner {

    private var mPresenter: ExamplePresenter<*>? = null

    private var lifecycleState = Lifecycle.State.DESTROYED

    private val observerList: MutableList<LifecycleObserver> = mutableListOf()

    override val lifecycle: Lifecycle
        get() = TODO("Not yet implemented")

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
        lifecycleState = Lifecycle.State.STARTED

        for (doorLifecycleObserver in observerList) {
            (doorLifecycleObserver as FullLifecycleObserver).onCreate(this)
        }
    }

    override fun RBuilder.render() {
            styledDiv {
                css{
                    display = Display.flex
                    flexDirection = FlexDirection.column
                }

                styledDiv {
                    css{
                        display = Display.flex
                        flexDirection = FlexDirection.row
                    }

                    styledInput(type = InputType.checkBox){
                        attrs.onChangeFunction = {
                            setState {
                                entity.checked = it.target.asDynamic().checked.toString().toBoolean()
                            }
                        }

                        css {
                            transform{
                                scale(4,4)
                            }
                            margin = "30px"
                        }
                    }

                    styledInput(type = InputType.text){
                        attrs.onChangeFunction = {
                            setState {
                               entity.name = it.target.asDynamic().value.toString()
                            }
                        }
                        attrs.placeholder = "Enter name"

                        css {
                            flex(3.0)
                            padding = "16px"
                            fontSize = LinearDimension("1.7em")
                            border(width = LinearDimension("1px"), style = BorderStyle.solid, color = Color.black)
                        }
                    }

                    styledButton(type = ButtonType.button) {
                        attrs.text("Save")
                        attrs.disabled = entity.name.isNullOrEmpty()
                        attrs.onClickFunction = {
                            if(!entity.name.isNullOrEmpty()){
                                mPresenter?.handleSaveEntity(entity)
                            }
                        }
                        css{
                            flex(1.0)
                            padding = "16px"
                            fontSize = LinearDimension("1.8em")
                            marginLeft = LinearDimension("20px")
                            backgroundColor = if(entity.name.isNullOrEmpty()) Color.grey.lighten(200) else Color.black
                            color = if(entity.name.isNullOrEmpty()) Color.black else Color.white
                        }
                    }

                    styledButton(type = ButtonType.button) {
                        attrs.text("Download")
                        attrs.disabled = list.isNullOrEmpty()
                        attrs.onClickFunction = {
                            mPresenter?.handleDownloadDbClicked()
                        }
                        css{
                            flex(1.0)
                            padding = "16px"
                            fontSize = LinearDimension("1.8em")
                            marginLeft = LinearDimension("20px")
                            backgroundColor = if(list.isNullOrEmpty()) Color.grey.lighten(200) else Color.black
                            color = if(list.isNullOrEmpty()) Color.black else Color.white
                        }
                    }
                }


                styledDiv {
                    css {
                        marginTop = LinearDimension("30px")
                    }
                    list?.forEach {
                        styledDiv {
                            css {
                                margin = "20px"
                                flexDirection = FlexDirection.row
                                display = Display.flex
                            }
                            renderData(it.uid, 1.0)
                            renderData(if(it.checked) "Checked" else "Unchecked", 1.0)
                            renderData(it.name, 3.0)
                            renderData(it.someNumber, 1.0)
                        }
                    }
                }
            }
    }

    private fun RBuilder.renderData(data: Any?, flex: Double){
        styledH6 {
            attrs.text("$data")
            css {
                flex(flexGrow= flex, flexBasis = FlexBasis.auto)
                fontSize = LinearDimension("1.9em")
            }
        }
    }

//   This will be obsolete: moving to FC
//    override fun addObserver(observer: LifecycleObserver) {
//        observerList.add(observer)
//    }
//
//    override fun removeObserver(observer: LifecycleObserver) {
//        observerList.remove(observer)
//    }

    override fun componentWillUnmount() {
        lifecycleState = Lifecycle.State.DESTROYED

        for (doorLifecycleObserver in observerList) {
            (doorLifecycleObserver as FullLifecycleObserver).onStop(this)
        }

        mPresenter?.onDestroy()
    }
}

fun RBuilder.renderApp(){
    child(ExampleComponent::class) {}
}