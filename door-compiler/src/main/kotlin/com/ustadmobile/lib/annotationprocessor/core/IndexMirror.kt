package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.Index

class IndexMirror(val name: String = "", val value: Array<out String>, val unique: Boolean = false) {

    constructor(index: Index) : this(index.name, index.value, index.unique)

}