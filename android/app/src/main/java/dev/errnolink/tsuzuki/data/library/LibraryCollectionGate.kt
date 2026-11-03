package dev.errnolink.tsuzuki.data.library

fun libraryCollectionActive(visible: Boolean, bulkWriteRunning: Boolean, loaded: Boolean): Boolean =
    visible && (!bulkWriteRunning || !loaded)
