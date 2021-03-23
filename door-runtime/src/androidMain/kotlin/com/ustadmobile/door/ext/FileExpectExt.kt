package com.ustadmobile.door.ext

import android.net.Uri
import com.ustadmobile.door.DoorUri
import java.io.File

actual fun File.toDoorUri(): DoorUri = DoorUri(Uri.fromFile(this))
