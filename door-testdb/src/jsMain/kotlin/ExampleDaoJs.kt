import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ustadmobile.door.DoorDatabase

@Dao
abstract class ExampleDaoJs(private val database: DoorDatabase) {

    suspend fun insertAsync(entity: ExampleJsEntity){
        val connection = database.openConnection()
        val statement = connection.prepareStatement("INSERT INTO ExampleEntity VALUES (?,?)")
        entity.uid?.let { statement.setLong(1, it) }
        entity.name?.let { statement.setString(2, it) }
        statement.executeUpdateAsync()
        connection.commit()
        connection.close()
    }

    suspend fun insertListAsync(entities: List<ExampleJsEntity>){
        val connection = database.openConnection()
        val statement = connection.prepareStatement("INSERT INTO ExampleEntity VALUES ${makeQueryValues(entities)}")
        makeQueryParams(entities).forEachIndexed { index, param ->
            statement.setString(index + 1,param.toString())
        }
        statement.executeUpdateAsync()
        connection.commit()
        connection.close()
    }

    private fun makeQueryValues(entities: List<ExampleJsEntity>): String{
        val params = mutableListOf<String>()
        entities.map { params.add("(?,?)") }
        return params.joinToString(",")
    }

    private fun makeQueryParams(entities: List<ExampleJsEntity>): Array<Any>{
        val params = mutableListOf<Any>()
        entities.forEach {
            it.uid?.let { it1 -> params.add(it1) }
            it.name?.let { it1 -> params.add(it1) }
        }
        return params.toTypedArray()
    }

    suspend fun findByUid(mUid: Long): ExampleJsEntity?{
        val connection = database.openConnection()
        val statement = connection.prepareStatement("SELECT * FROM ExampleEntity WHERE uid = ?")
        statement.setLong(1, mUid)
        val resultSet = statement.executeQueryAsync()
        if(resultSet.next()) {
            return ExampleJsEntity().apply {
                uid = resultSet.getString(0)?.toLong()
                name = resultSet.getString(1)
            }
        }
        return null
    }

    suspend fun findAll(): List<ExampleJsEntity> {
        val connection = database.openConnection()
        val statement = connection.prepareStatement("SELECT * FROM ExampleJsEntity")
        val resultSet = statement.executeQueryAsync()
        val result = mutableListOf<ExampleJsEntity>()
        while(resultSet.next()) {
            result.add(ExampleJsEntity().apply {
                uid = resultSet.getString(0)?.toLong()
                name = resultSet.getString(1)
            })
        }
        return result
    }
}