
# Offline-first data layer generation

Door's objective is to provide an offline-first database layer where data can be accessed, queried, and 
updated offline and synced when a network connection is available (directly to a cloud server and/or via a peer device). 
This is similar to the objective in the [Android app architecture recommendation offline-first data layer](https://developer.android.com/topic/architecture/data-layer/offline-first)
without all the manual boilerplate.

This is intended for those who want to understand how the Door offline-first data system works under the hood. 
It is assumed that readers of this are familiar with the concepts in an offline-first data layer and
[Android's Room Database](https://developer.android.com/training/data-storage/room).

There are two ways that data can be transferred:

* __Push__: when the sending node initiates transfer to the receiving node. This makes sense if the data is highly likely to
 be relevant to the other device (e.g. data about an item where the user selected to make it available offline or a 
 chat message for a user logged into the other receiving node).

* __Pull__: when the receiving node initiates transfers from the sending node. Data that is pulled when the receiving node is 
connected should still be accessible when it goes offline. When the objective is for the user to have the
best possible experience offline, it is tempting to want to push everything the user has access to in advance. There are 
various scenarios when pull on demand is better than push in advance:
   * The user wants specific data now e.g. when they are looking at a particular screen and they don't want to wait for
     (potentially large) sync to complete
   * The user is unlikely to want to use all the data available e.g. there is a large database of books in a library, 
     so pushing everything could be a waste of bandwidth. This is also the case when a user has access to a large amount
     of data (e.g. they are an admin user) and are unlikely to want to access all of it. Downloading all the data they 
     have access to could use a lot of bandwidth and exceed the storage/processing capacity of a mobile device.
   * The user is using a web browser and has not installed a [PWA](https://web.dev/progressive-web-apps/) and they do not 
     have an expectation to use the data offline. It is possible that they will never come back. Downloading data in 
     advance is probably a waste of bandwidth (and running SQL in the browser is more complex than on Android/JVM).

## Push (Replication)

Push replication works as follows where the sending node initiates transfer to the receiving node:

1. The sending node updates data on its local database instance (eg using the normal DAO functions etc).
2. The sending node inserts a row into the OutgoingReplication table e.g.
```
INSERT INTO OutgoingReplication(destNodeId, orTableId, orPk1, orPk2) VALUES(42, MyEntity.TABLE_ID, pk1, pk2)
```
3. A database trigger picks up any new insertions that were done into OutgoingReplication and creates a new NodeEvent 
   row. The NodeEvent row contains the destination node, table id, and primary key(s). 
* __On JDBC/SQLite__: the trigger is created before any write transaction by the NodeEventManager. This has to be done for
  each transaction because on JDBC temporary tables and triggers lifespan is limited to the lifespan of the connection
  itself (one transactions uses one connection).
* __On Android__: the trigger is created by DoorSetupCallbackAndroid onOpen. Temporary tables and triggers will survive 
  for the lifespan of the app (but will not be persisted, so must be recreated each time the database is opened).

4. NodeEventManager will emit a list of the NodeEvent(s) generated by the transaction on the outgoingEvents flow.

* __On JDBC/SQLite__: The NodeEventManager will be called when a write transaction is finished, and can then select
  the NodeEvent(s) generated by the transaction.
* __On Android__:  The NodeEventManager will listen for invalidations of the OutgoingReplication table.

5. A "Message Transmitter" (e.g ServerSentEvent endpoint, send over HTTP processor, etc) on the sending node will
   observe the outgoingEvents flow. If the events are relevant for the target receiving node, then the events will be 
   converted into a NodeEventMessage (by running an SQL query to get the actual data). The NodeEventMessage will contain 
   the actual JSON of the entities that are to be replicated (see NodeEventMessage). There are two main ways that 
   messages could be sent:
* __Using Server Sent Events (SSE)__: This is normally used by a sending node to send messages to a receiver node which
does not have an IP address that is directly reachable (e.g. when the cloud node is the sending node and the receiver 
is a "client" e.g. phone or PC behind an NAT router). The SSE endpoint on the sending node will emit the NodeEventMessage 
through SSE, and the receive node will then process the received NodeEventMessage via 
NodeEventManagerCommon#onIncomingEventReceived and emit it on the incomingMessages flow.
* __Send over HTTP PUT__: This is used when the sending node can directly reach the receiving node via an IP address / 
domain name. The sending node will send an HTTP request to the receiver node (e.g. /receiveMessage). The received message 
will then be processed via NodeEventManager#onIncomingEventReceived and emitted on the incomingMessages flow.

6. The receiver node processes the NodeEventMessage and updates its local database accordingly. This can be done in one of
   three ways:
* Direct data insert/replace: e.g. a no questions asked direct upsert of the data from the JSON into the table.
* Trigger using a receive view: most of the time data is received from another node it should be validated and permission
  checks etc. should take place. This can be done by creating an SQL View containing all the fields of the entity itself
  and one additional field representing the nodeId the data was received from. The trigger can then decide how it wants
  to accept/reject the incoming data.
* Event-only: logic on the receiving node listens for the onIncomingMessage of the NodeEventManager, and processes the
  data via the event handler.

## Pull

Pull replication works as follows where the receiving node initiates transfer by making a request to the sending node.
Every DAO query that can be accessed for data pull must be annotated @HttpAccessible e.g. 
```
@HttpAccessible 
@Query("SELECT ..") 
suspend fun findAllItemsForSomeScreenByType(type: Int): List<Item>
```
The typical behavior of the generated repository on the receiver is as follows:

1. If using a Flow or PagingSource, return a Flow/PagingSource immediately that will query the local database to show
the user what is available immediately.
2. If connected, the receiver makes an HTTP request for the query that it wants to use e.g. 
endpoint/DaoName/findAllItemsForSomeScreenByType?type=42. The sender replies with a NodeEventMessage that contains
the data matching the query to be replicated from the sending node to the receiving node. The receiver MUST add an
http header Door-Response: NodeEventMessage (see below re complex return types). If Door-Response: NodeEventMessage is
not included, then the sending node (e.g. http server) will return the JSON as it comes directly from the query.
3. If any data is returned in the http response from the sender, the receiver node receives the JSON and updates its 
local database the same way as would be done if receiving a NodeEventMessage data push (as per step #6 above) e.g. 
the data may be directly inserted, inserted into a receive view connected to a trigger, or rely on processing via the 
event handler.
4. If using a Flow or PagingSource, the changes to the local database will invalidate it so that the update data can be 
displayed. If using a "normal" return (async suspend function or "normal" blocking function), then run the query on the
local database and return the result.

### Complex return type scenarios

Sometimes data is returned that uses aggregate queries that are not directly related to a given row, for example:

```
data class SalesPersonDisplayInfo(
   val salesPersonName: String,
   val totalSales: Float
)


@Query("""
     SELECT Person.name AS salesPersonName,
            (SELECT SUM(*) 
               FROM Sale
              WHERE Sale.saleType = :type
                AND Sales.salesPersonId = Person.personId) AS totalSales)
      FROM Person
""")
suspend fun findSalesPersonTotalsBySaleType(type: Int): List<SalesPersonDisplayInfo>
```
Here the returned data can't be directly inserted into the local database by the receiver. There is no 
SalesPersonDisplayInfo table, the data itself is from the Person table and the Sale table. Door cannot determine
where the underlying data comes from. This can be resolved by linking other queries as follows:

```
@Query("""
SELECT Person.* 
  FROM Person 
 WHERE Person.role = 'sales' 
"")
suspend fun selectAllSalesPeople(): List<Person>

@Query("""
SELECT Sale.*
  FROM Sale
 WHERE Sale.saleType = :type 
""")
suspend fun selectAllSalesByType(type: Int): List<Sale>

@Query("""
     SELECT Person.name AS salesPersonName,
            (SELECT SUM(*) 
               FROM Sale
              WHERE Sale.saleType = :type
                AND Sales.salesPersonId = Person.personId) AS totalSales)
      FROM Person
""")
@HttpAccessible(replicateData = ["selectAllSalesPeople", "selectAllSalesByType"]
suspend fun findSalesPersonTotalsBySaleType(type: Int): List<SalesPersonDisplayInfo>
```
The queries linked by replicateData annotation must:
1. Return a class that is annotated as an Entity from the database
2. Any query parameter names used in the function must also be present on the function anntoated @HttpAccessible and the
parameter type must match.


