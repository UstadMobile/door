package db3

import kotlinx.serialization.Serializable

@Serializable
class BadgeWithTotal: Badge() {

    var total: Int = 0

}