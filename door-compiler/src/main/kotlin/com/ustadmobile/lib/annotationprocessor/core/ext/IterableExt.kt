package com.ustadmobile.lib.annotationprocessor.core.ext

fun <T> Iterable<T>.findDuplicates(
    comparison: (T, T) -> Boolean
): List<T> {
    return filter { item ->
        this.count { comparison(it, item)  } > 1
    }
}
