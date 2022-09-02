package androidx.room


/**
 * Ignores the marked element from Room's processing logic.
 *
 *
 * This annotation can be used in multiple places where Room processor runs. For instance, you can
 * add it to a field of an [Entity] and Room will not persist that field.
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.FIELD,
    AnnotationTarget.CONSTRUCTOR
)
@Retention(AnnotationRetention.BINARY)
annotation class Ignore
