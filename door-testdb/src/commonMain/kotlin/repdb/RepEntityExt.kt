package repdb

import com.ustadmobile.door.annotation.ShallowCopy

@ShallowCopy
expect fun RepEntity.shallowCopy(
    block: RepEntity.() -> Unit,
): RepEntity
