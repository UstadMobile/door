import androidx.room.Dao
import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.jdbc.ext.executeQueryAsyncKmp

@Dao
class ExampleDaoJs(private val database: DoorDatabase) {

    suspend fun insertAsync(entity: ExampleJsEntity){
        val connection = database.openConnection()
        val statement = connection.prepareStatement("INSERT INTO ExampleEntity VALUES (?,?)")
        statement.setLong(1, entity.uid)
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
        val resultSet = statement.executeQueryAsyncKmp()
        if(resultSet.next()) {
            return ExampleJsEntity().apply {
                uid = resultSet.getLong("uid")
                name = resultSet.getString("name")
            }
        }
        statement.close()
        resultSet.close()
        return null
    }

    suspend fun findAll(): List<ExampleJsEntity> {
        val connection = database.openConnection()
        val statement = connection.prepareStatement("SELECT * FROM ExampleJsEntity")
        val resultSet = statement.executeQueryAsyncKmp()
        val result = mutableListOf<ExampleJsEntity>()
        while(resultSet.next()) {
            result.add(ExampleJsEntity().apply {
                uid = resultSet.getLong("uid")
                name = resultSet.getString("name")
            })
        }
        statement.close()
        resultSet.close()
        return result
    }
}