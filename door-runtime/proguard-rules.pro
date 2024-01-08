
#
-keep public class * extends com.ustadmobile.door.ext.DoorDatabaseMetadata {
    *;
}

# On databases that don't use replication, the DoorDatabaseWrapper constructor was removed
-keepclassmembers public class * extends com.ustadmobile.door.DoorDatabaseWrapper {
    public <init>(...);
}
