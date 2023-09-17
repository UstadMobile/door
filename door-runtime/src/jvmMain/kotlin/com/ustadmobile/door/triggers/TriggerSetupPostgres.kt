package com.ustadmobile.door.triggers

import com.ustadmobile.door.annotation.ReplicateEntity
import com.ustadmobile.door.annotation.Trigger
import com.ustadmobile.door.ext.DoorDatabaseMetadata
import com.ustadmobile.door.ext.useAsPostgresSqlIfNotBlankOrFallback
import com.ustadmobile.door.ext.useAsPostgresSqlIfNotEmptyOrFallback

fun DoorDatabaseMetadata<*>.createPostgresTriggerSetupStatementList() : List<String> {
    return buildList {
        replicateEntities.values.forEach { entity ->
            if(entity.remoteInsertStrategy == ReplicateEntity.RemoteInsertStrategy.INSERT_INTO_RECEIVE_VIEW) {
                add(entity.createReceiveViewSql)
            }

            entity.triggers.forEach { trigger ->
                val tableOrViewName = if(trigger.on == Trigger.On.RECEIVEVIEW) {
                    entity.receiveViewName
                }else {
                    entity.entityTableName
                }

                val createFunctionSql = buildString {
                    append("CREATE OR REPLACE FUNCTION ${Trigger.NAME_PREFIX}${trigger.name}_fn() RETURNS TRIGGER AS $$ ")
                    if(trigger.conditionSql != "") {
                        append("""
                       DECLARE
                         whereVar boolean;
                         curs1 CURSOR FOR ${trigger.conditionSqlPostgres.useAsPostgresSqlIfNotBlankOrFallback(trigger.conditionSql)} ;
                         """)
                    }
                    append("BEGIN \n")
                    if(trigger.conditionSql != "") {
                        append("""
                        OPEN curs1;
                        FETCH curs1 INTO whereVar;
                        IF whereVar = true THEN """)
                    }

                    append(trigger.postgreSqlStatements.useAsPostgresSqlIfNotEmptyOrFallback(trigger.sqlStatements)
                        .joinToString(separator = ";", postfix = ";"))

                    if(trigger.conditionSql != "") {
                        append("END IF; CLOSE curs1;")
                    }

                    append("""
                    IF (TG_OP = 'INSERT' OR TG_OP = 'UPDATE') THEN
                        RETURN NEW;
                    ELSE
                        RETURN OLD;
                    END IF;
                """)

                    append("END $$ LANGUAGE plpgsql")
                }
                add(createFunctionSql)

                add("""
                    CREATE TRIGGER ${Trigger.NAME_PREFIX}${trigger.name}_trig ${trigger.order.sqlStr} ${trigger.events.joinToString(separator = " OR ") { it.sqlKeyWord }}
                    ON $tableOrViewName
                    FOR EACH ROW EXECUTE PROCEDURE ${Trigger.NAME_PREFIX}${trigger.name}_fn()
                """)
            }
        }
    }

}
