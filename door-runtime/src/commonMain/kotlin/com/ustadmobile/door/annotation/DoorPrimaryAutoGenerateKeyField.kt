package com.ustadmobile.door.annotation

/**
 * This is used as a workaround for Javascript compilation. KSP does not seem to read the annotations on fields when
 * processing entities that are already compiled in another module, even though it does read the annotations on the class
 * itself.
 *
 * When using an autoGenerate primary key on an entity that is in a different module, use the normal
 * @PrimaryKey(autoGenerate=true) on the field AND add @DoorPrimaryAutoGenerateKeyField("primaryKeyFieldName")
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
annotation class DoorPrimaryAutoGenerateKeyField(val value: String)

