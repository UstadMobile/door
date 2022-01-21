
# Replication

Door implements a selective multi-primary (e.g. primary-primary) sync/replication system. 
Mobile clients should (for security and performance reasons) normally only replicate the 
data for active accounts on that device. It is designed to support offline-first database-driven applications. 
Multiple nodes can replicate to/from a central node and nodes can also replicate with each other peer-to-peer.
Nodes can switch seamlessly between synchronizing with peers and synchronizing with a central node.
It supports:
* Room SQLite databases on Android
* Door SQLite databases on Kotlin/JS (powered by SQL.js)
* Door SQLite and Postgres databases on JVM

Each entity has a replication tracker entity with a few annotated fields. To replicate an entity, just insert
into the replication entity table. At it's simplest, it looks something like this:

```
INSERT INTO EntityReplication(entityPrimaryKey, destinationNodeId)
SELECT Entity.primaryKey AS entityPrimaryKey, 
       DoorNode.nodeId AS destinationNodeId
  FROM Entity
       JOIN DoorNode -- DoorNode is a special table with a list of all known nodes             
```

Each node has a 64bit node id (randomly generated). Replication is modeled as a special 1-many join between the Entity 
and the Replication Tracker entity. The replication tracker has annotated fields for:
* A foreign key that links to the primary key of the entity itself
* The destination node id (e.g. the destination node to which the entity should be replicated)
* The version identifier of the entity (normally last changed timestamp, could be a hash). This allows 
  each node to 'remember' the version of an entity on any other node, and use this information when 
  deciding what should be replicated.
* A processed boolean flag to indicate whether or not the destination node has received the Entity

To mark an entity replication, simply insert / update the replication trackers (1 row per entity per destination node). 
Door will deliver the entity to the destination node (almost immediately if the node is currently connected, or 
as soon as it next connects).

Data received from a remote device should normally be checked and validated before it is inserted (for security and 
version conflict resolution purposes). Door replication inserts data from a remote node into a special SQL View called
the Receive View. This is an SQL View that is connected to SQL triggers. The SQL triggers can check permissions, existing data, 
etc. to decide how to process incoming replication data.

### Entity Setup
```

/*
 • The replication tracker is another entity with specially annotated fields.
 */
@ReplicateEntity(
tracker = EntityReplicate::class,
tableId = 42,
batch = 100) //Where a batch is specified, the lowest numbered batches will be replicated first. No higher batch
            //will start replication until earlier batches finish. This can be used to manage dependencies (eg 
            //sync permissions first)
/*
 * Optional: The replication process will by default automatically create a Remote Insert View. 
 * This annotation itself is optional. If it is not specified, Door will automatically create "Entity_ReceiveView" 
 * as per the default.
 */ 
@ReplicateReceiveView(name = "Entity_ReceiveView", 
""""
SELECT Entity_ReplicationTracker.*, Entity.*
    FROM Entity_ReplicationTracker
                LEFT JOIN Entity ON Entity.primaryKey = Entity_ReplicationTracker.trkPk
"""")
/*
 *  Now use the triggers to determine how to handle incoming data from another node.
 */ 
@Triggers(arrayOf(Trigger(
name = "remove_insert_trig",
order = Trigger.Order.INSTEAD_OF
events = [Trigger.Event.INSERT],
on = Trigger.On.RECEIVEVIEW
sqlStatements = ["""
CREATE TRIGGER EntityCheckRemoteInsert 
INSTEAD OF INSERT ON Entity_ReceiveView
BEGIN
REPLACE INTO Entity(entityPrimaryKey, entityLastChangedTime, aNumberField) 
        VALUES(NEW.entityPrimaryKey, NEW.entityLastChangedTime, NEW.aNumberField);
END
"""]
//Optionally: add conditionSql to control if the SQL statements execute.
)))
class Entity {
   @PrimaryKey(autoGenerate = true)
   var entityPrimaryKey: Long = 0

   @ReplicationVersionId
   @LastChangedTime
   var entityLastChangedTime: Long = 0 
   
   //TODO: Add @ReplicationLastChangedBy
   var entityReceivedBy: Long = 0

   var aNumberField: Long 

} 
```
Annotate exactly one field as ReplicationVersionId. This will be used to avoid exchanging duplicate entities.
You can use the optional LastChangedTime annotation so that the Door repository will automatically use the
modification timestamp as a version identifier. 

Door will generate a unique 64bit primary key where autoGenerate is set to true. The 64bit key is based on 
a combination of the time, device identifiers, and a sequence number on the device. It can generate up to 4,096
unique primary keys per second (per table). This is loosely based on the Twitter snowflake approach.

### Replication Tracker Entity setup
```
@Entity(primaryKeys = arrayOf("trkrEntityPrimaryKey", "trkrDestinationNode"))
class EntityReplicate {
     
    @ReplicationEntityForeignKey
    var trkrEntityPrimaryKey: Long = 0

    @ReplicationNodeId
    var trkrDestinationNode: Long = 0

    @ReplicationVersionId
    var trkrVersionId: Long = 0

    @ReplicationTrackerProcessed
    var trkrProcessed: Boolean = false

}
```
Create a normal entity with special annotations as above. The fields annotated @ReplicationVersionId must be of the same 
type on both the entity itself and the replication tracker entity. 

### DAO Setup
```
@Dao
class EntityDao { 
    /* Query that runs whenever a change happens on the table to determine what needs
    to be replicated.  
    */
    @ReplicationRunOnChange([Entity::class])
    /* By default, a check will be made to see if there are pending replications for remote 
     * devices for the same entity (e.g. Entity::class). To check for other entities, they 
     * should be given in the ReplicationCheckPendingNotificationsFor annotation
     */
    @ReplicationCheckPendingNotificationsFor([Entity::class])for changes
    @Query("""
REPLACE INTO EntityReplicate(trkrEntityPrimaryKey, trkrDestinationNode)
SELECT Entity.entityPrimaryKey AS trkrEntityPrimaryKey,
       DoorNode.nodeId AS trkrDestinationNode
       FROM ChangeLog
            JOIN Entity ON ChangeLog.chTableId = 42 AND ChangeLog.chEntityPk = Entity.entityPrimaryKey
            JOIN DoorNode ON DoorNode.nodeId != 0
  
 /*psql- ON CONFLICT(...) DO UPDATE  -*/:
 ")
    fun updateEntityReplicationOnEntityChanged()
    
    /*
     * Query that runs whenever a new node is connected to determine what needs 
     * to be replicated
     */
    @ReplicationRunOnNewNode 
    @Query("""
    REPLACE INTO EntityReplicate(trkrEntityPrimaryKey, trkrDestinationNode)
     SELECT Entity.entityPrimaryKey AS trkrEntityPrimaryKey,
            :newNodeId AS trkrDestinationNode       
    /*psql- ON CONFLICT(...) DO UPDATE  -*/:
    """) 
    fun updateEntityReplicationOnNewNode(@NewNodeParam newNodeId: Long) 
    
}

```
It is possible to simply insert/update replication trackers manually. To make things easier, you can annotate 
DAO functions so that the replication trackers are inserted/updated automatically when changes happen.

This uses a special table called ChangeLog. Each time any entity is changed, an SQL trigger will insert
a row into the ChangeLog. This allows (potentially complex) queries that find destination nodes for 
replication to run asynchronously.

## Replication process
All HTTP requests contains a header with the client's node id and auth. 

### Exchange Trackers
#### Update trackers on remote
Request unprocessed replication tracker entities from remote:
```
GET endpoint/replication/pendingtrackers?tableId=42
returns
[
 {'primaryKey': 123, versionId: 1000},
 ...
]

```
Remote runs:
```
SELECT Entity_RT.entityUid, Entity_RT.identifier
FROM Entity_RT
WHERE nodeId = :nodeId
AND processed = 0
```
Where :nodeId is the local node id (the one making the http request).

Local finds all pending replication trackers that it already has (e.g. replications from another node):
```
//For each primaryKey/versionId pair received from server
SELECT Entity.entityUid AS primaryKey, Entity.lastChangedTime AS identifier
 FROM Entity
WHERE Entity.entityUid = :entityUid 
  AND lastChangedTime = :lastChangedTime
```
If there are any matches, send acknowledgements to the remote server so that entities for which we already have a 
matching version are not included in the replication itself.

```
PUT endpoint/replication/markReplicateTrackersAsProcessed?tableId=42
Request BODY:
[
   {'primaryKey': 456, versionId: 101},
   ...
]
```
Remote runs:
```
//For each acknowledgment received
UPDATE EntityReplicate
   SET trkrProcessed = (
       SELECT CASE
       WHEN ((SELECT entityVersionIdField
                FROM Entity
               WHERE entityPk = :primaryKey) = :versionId) THEN 1
       ELSE 0
       END),
       trkrVersionIdField = :versionId
 WHERE trkrEntityPkField = :primaryKey
   AND trkrDestinationNodeId = :nodeId
    
```

#### Update trackers on local
Local runs:
```
SELECT Entity_RT.entityUid AS primaryKey, 
       Entity_RT.lastChangedTime AS versionId
  FROM Entity_RT
 WHERE nodeId = :remoteNodeId
   AND processed = 0
```
Then posts to server:
```
POST endpoint/replication/checkForEntitiesAlreadyReceived?tableId=42

Request BODY
[
{'primaryKey': 456, versionId: 101},
{'primaryKey': 789, versionId: 102},
...
]

Response BODY:
[
{'primaryKey': 789, versionId: 102},
...
]
```
For each entity received from the request, local runs:
```
UPDATE Entity_RT
   SET processed = 1
 WHERE trkrPrimaryKey = :primaryKey
   AND trkrVersionId = :versionId
   AND nodeId = :remoteNodeId  
```
### Send/Receive entities to replicate

#### Receive entities from remote
```
GET endpoint/replication/pendingReplication?tableId=42

Response Body
[
{
   //JSON of fields on entitiy
   entityPrimaryKey: 123,
   lastChanged: 100,
   aNumberField: 32,
   
   //Json of fields from tracker
   ...
}
]
```

Remote runs SQL:
```
SELECT Entity.*, Entity_RT.*
  FROM Entity_RT
       JOIN Entity ON Entity_RT.trkrEntityPrimaryKey = Entity.primaryKey
 WHERE Entity_RT.trkrDestinationNode = :nodeId
   AND Entity_RT.trkrProcessed = 0 
```

Local runs:
```
INSERT INTO RemoteInsertView (entityPrimaryKey, lastChanged, aNumberField, trkrEntityPrimaryKey,
trkrDestinationNode, trkrVersionId, trkrProcessed)
VALES (?, ?, ?, ?, ?, ?, ?)
```
Then post acknowledgements to server:
```
PUT endpoint/replication/markReplicateTrackersAsProcessed?tableId=42
Request Body:
[
{'primaryKey' : 123, versionId: 412},
...
]
```
Server marks given replication logs as processed (as per the same endpoint aforementioned).

### Send entities to replicate from local to remote
```
PUT endpoint/replication/receive?tableId=42
Request Body:
[
{
   //JSON of fields on entitiy
   entityPrimaryKey: 123,
   lastChanged: 100,
   aNumberField: 32,
   
   //Json of fields from tracker
   ...
}
]
```

Remote runs:
```
INSERT INTO RemoteInsertView (entityPrimaryKey, lastChanged, aNumberField, trkrEntityPrimaryKey,
trkrDestinationNode, trkrVersionId, trkrProcessed)
VALES (?, ?, ?, ?, ?, ?, ?)
```
Upon receiving 200 OK response, local runs SQL:
```
UPDATE Entity_RT
   SET processed = 1
 WHERE trkrPrimaryKey = :primaryKey
   AND trkrVersionId = :versionId
   AND nodeId = :remoteNodeId  
```
