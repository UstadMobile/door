package com.ustadmobile.door.annotation

/**
 * Request generation of a ShallowCopy extension function for the annotated class
 *
 * e.g.
 * @ShallowCopyable
 * class Customer() {
 * var id: Int = 0
 * var name: String? = null
 * }
 *
 *
 * Will generate:
 *
 * fun Customer.shallowCopy(
 * id: Int = this.id,
 * name: String? = this.name
 * ) = Customer().apply {
 * this.id = id
 * this.name = name
 * }
 *
 * @param functionName
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class ShallowCopy()
