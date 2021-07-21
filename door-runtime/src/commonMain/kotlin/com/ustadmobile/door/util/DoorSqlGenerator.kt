package com.ustadmobile.door.util

/**
 * Various SQL generators. These are here to prevent generated code and migration functions getting too long
 *
 *  ****DO NOT EVER CHANGE A FUNCTION AFTER PUBLICATION****
 *
 *  This would compromise the integrity of migrations. Add another function (e.g. generateSomethingV2)
 */
object DoorSqlGenerator {

    /**
     * Generate SQL for SQLite to generate trigger statements
     */
    fun generateSyncableEntityInsertTriggersSqlite(
        entityName: String,
        tableId: Int,
        pkFieldName: String,
        localCsnFieldName: String,
        primaryCsnFieldName: String
    ): List<String> {
        val localCsnTrigger = """CREATE TRIGGER INS_LOC_${tableId}
            AFTER INSERT ON $entityName
            FOR EACH ROW WHEN (((SELECT CAST(master AS INTEGER) FROM SyncNode) = 0) AND
                NEW.$localCsnFieldName = 0)
            BEGIN
                UPDATE $entityName
                SET $primaryCsnFieldName = (SELECT sCsnNextPrimary FROM SqliteChangeSeqNums WHERE sCsnTableId = $tableId)
                WHERE $pkFieldName = NEW.$pkFieldName;
                
                UPDATE SqliteChangeSeqNums
                SET sCsnNextPrimary = sCsnNextPrimary + 1
                WHERE sCsnTableId = $tableId;
            END"""

        val primaryCsnTrigger = """
            CREATE TRIGGER INS_PRI_${tableId}
                           AFTER INSERT ON $entityName
                            
         FOR EACH ROW WHEN (((SELECT CAST(master AS INTEGER) FROM SyncNode) = 1) AND NEW.$primaryCsnFieldName = 0)
                           BEGIN
                                 UPDATE $entityName
                                    SET $primaryCsnFieldName = (
                                        SELECT sCsnNextPrimary 
                                          FROM SqliteChangeSeqNums 
                                         WHERE sCsnTableId = $tableId)
                                  WHERE $pkFieldName = NEW.$pkFieldName;
                            
                                 UPDATE SqliteChangeSeqNums
                                    SET sCsnNextPrimary = sCsnNextPrimary + 1
                                  WHERE sCsnTableId = $tableId;
                            
                                 INSERT INTO ChangeLog(chTableId, chEntityPk, dispatched, chTime) 
                                 SELECT ${tableId}, NEW.${pkFieldName}, 0, (strftime('%s','now') * 1000) + ((strftime('%f','now') * 1000) % 1000);    
                           END      
        """.trimIndent()

        return listOf(localCsnTrigger, primaryCsnTrigger)
    }

    fun generateSyncableEntityUpdateTriggersSqlite(
        entityName: String,
        tableId: Int,
        pkFieldName: String,
        localCsnFieldName: String,
        primaryCsnFieldName: String
    ): List<String> {
        val localCsnTrigger = """
            CREATE TRIGGER UPD_LOC_$tableId
            AFTER UPDATE ON $entityName
            FOR EACH ROW WHEN (((SELECT CAST(master AS INTEGER) FROM SyncNode) = 0)
                AND (NEW.$localCsnFieldName == OLD.$localCsnFieldName OR
                    NEW.$localCsnFieldName == 0))
            BEGIN
                UPDATE $entityName
                SET $localCsnFieldName = (SELECT sCsnNextLocal FROM SqliteChangeSeqNums WHERE sCsnTableId = $tableId) 
                WHERE $pkFieldName = NEW.$pkFieldName;
                
                UPDATE SqliteChangeSeqNums 
                SET sCsnNextLocal = sCsnNextLocal + 1
                WHERE sCsnTableId = $tableId;
            END
        """.trimIndent()

        val primaryCsnTrigger = """
            CREATE TRIGGER UPD_PRI_$tableId
            AFTER UPDATE ON $entityName
            FOR EACH ROW WHEN (((SELECT CAST(master AS INTEGER) FROM SyncNode) = 1)
                AND (NEW.$primaryCsnFieldName == OLD.$primaryCsnFieldName OR
                    NEW.$primaryCsnFieldName == 0))
            BEGIN
                UPDATE $entityName
                SET $primaryCsnFieldName = (SELECT sCsnNextPrimary FROM SqliteChangeSeqNums WHERE sCsnTableId = $tableId)
                WHERE $pkFieldName = NEW.$pkFieldName;
                
                UPDATE SqliteChangeSeqNums
                SET sCsnNextPrimary = sCsnNextPrimary + 1
                WHERE sCsnTableId = $tableId;
                
                INSERT INTO ChangeLog(chTableId, chEntityPk, dispatched, chTime) 
                SELECT ${tableId}, NEW.${pkFieldName}, 0, (strftime('%s','now') * 1000) + ((strftime('%f','now') * 1000) % 1000);    

            END            
        """.trimIndent()

        return listOf(localCsnTrigger, primaryCsnTrigger)
    }


    fun generateSyncableEntityFunctionAndTriggerPostgres(
        entityName: String,
        tableId: Int,
        pkFieldName: String,
        localCsnFieldName: String,
        primaryCsnFieldName: String
    ): List<String> {
        val functionSql = """CREATE OR REPLACE FUNCTION 
                    | inccsn_${tableId}_fn() RETURNS trigger AS $$
                    | BEGIN  
                    | UPDATE ${entityName} SET ${localCsnFieldName} =
                    | (SELECT CASE WHEN (SELECT master FROM SyncNode) THEN NEW.${localCsnFieldName} 
                    | ELSE NEXTVAL('${entityName}_lcsn_seq') END),
                    | ${primaryCsnFieldName} = 
                    | (SELECT CASE WHEN (SELECT master FROM SyncNode) 
                    | THEN NEXTVAL('${entityName}_mcsn_seq') 
                    | ELSE NEW.${primaryCsnFieldName} END)
                    | WHERE ${pkFieldName} = NEW.${pkFieldName};
                    | INSERT INTO ChangeLog(chTableId, chEntityPk, dispatched, chTime) 
                    | SELECT ${tableId}, NEW.${pkFieldName}, false, cast(extract(epoch from now()) * 1000 AS BIGINT)
                    | WHERE COALESCE((SELECT master From SyncNode LIMIT 1), false);
                    | RETURN null;
                    | END $$
                    | LANGUAGE plpgsql
                """.trimMargin()

        val triggerSql = """
            CREATE TRIGGER inccsn_${tableId}_trig 
                   AFTER UPDATE OR INSERT ON ${entityName}             
                   FOR EACH ROW WHEN (pg_trigger_depth() = 0) 
                   EXECUTE PROCEDURE inccsn_${tableId}_fn()
        """.trimIndent()

        return listOf(functionSql, triggerSql)
    }


}