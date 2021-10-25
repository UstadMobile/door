package com.ustadmobile.lib.annotationprocessor.core.ext

import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.annotation.Trigger
import com.ustadmobile.door.ext.minifySql
import com.ustadmobile.lib.annotationprocessor.core.applyIf
import com.ustadmobile.lib.annotationprocessor.core.entityTableName
import com.ustadmobile.lib.annotationprocessor.core.replicationEntityReceiveViewName
import javax.lang.model.element.TypeElement

fun Trigger.toSql(entityTypeEl: TypeElement, dbProductType: Int) : List<String>{
    val tableOrViewName = if(on == Trigger.On.ENTITY)
        entityTypeEl.entityTableName
    else
        entityTypeEl.replicationEntityReceiveViewName

    return when(dbProductType) {
        DoorDbType.SQLITE -> this.events.map { event ->
            val postfix = event.sqlKeyWord.lowercase().substring(0, 3)
            """
                CREATE TRIGGER ${this.name}_$postfix 
                ${order.sqlStr} $event ON $tableOrViewName
                FOR EACH ROW ${if(conditionSql != "") " WHEN ($conditionSql) " else ""}
                BEGIN
                    ${sqlStatements.joinToString(separator = ";")};
                END
            """.minifySql()
        }
        DoorDbType.POSTGRES -> listOf(
            StringBuilder()
                .append("CREATE OR REPLACE FUNCTION ${name}_fn() RETURNS TRIGGER AS $$ ")
                .applyIf(conditionSql != "") {
                    append("""
                       DECLARE
                         whereVar boolean;
                         curs1 CURSOR FOR $conditionSql ;
                         """)
                }
                .append("BEGIN \n")
                .applyIf(conditionSql != "") {
                    append("""
                        OPEN curs1;
                        FETCH curs1 INTO whereVar;
                        IF whereVar = true THEN """)
                }
                .append(sqlStatements.joinToString(separator = ";", postfix = ";"))
                .applyIf(conditionSql != "") {
                    append("END IF; CLOSE curs1;")
                }
                .append("RETURN NULL; END $$ LANGUAGE plpgsql")
                .toString().minifySql(),
            """
                CREATE TRIGGER ${name}_trig ${order.sqlStr} ${events.joinToString(separator = " OR ") { it.sqlKeyWord } }
                       ON $tableOrViewName
                       FOR EACH ROW EXECUTE PROCEDURE ${name}_fn()
            """.minifySql()
        )
        else -> throw Exception("Invalid DB type")
    }
}
