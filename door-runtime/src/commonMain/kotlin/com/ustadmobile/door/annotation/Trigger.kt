package com.ustadmobile.door.annotation

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)

/**
 * Attempt to implement a common trigger wrapper that works on both SQL and Postgres.
 *
 * ON SQLite this will simply create a trigger using the normal create trigger syntax.
 *
 * ON Postgres, it will create a trigger and a function. Postgres does not allow a when
 * clause to contain queries like SQLite does. Here a function will be generated that
 * will run the function.
 *
 */
annotation class Trigger(

    val name: String,

    val order: Order,

    val events: Array<Event>,

    val sqlStatements: Array<String>,



    //By default, the trigger will be on the entity that it is annotated on
    val on: On = Trigger.On.ENTITY,

    /**
     * An SQL query that evaluates as a Boolean to determine when the
     */
    val conditionSql: String = "",

    ) {

    enum class Event(val sqlKeyWord: String) {
        INSERT("INSERT"), UPDATE("UPDATE"), DELETE("DELETE")
    }

    enum class Order(val sqlStr: String) { AFTER("AFTER"), BEFORE("BEFORE"), INSTEAD_OF("INSTEAD OF") }

    enum class On { ENTITY, RECEIVEVIEW }

}
