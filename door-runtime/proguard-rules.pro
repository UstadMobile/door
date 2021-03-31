# EntityAck is sent to the server to acknowledge receipt of entities, but is not itself an entity
# It must not be obfuscated.
-keep public class com.ustadmobile.door.EntityAck {
    *;
}
