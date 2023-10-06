package com.ustadmobile.door.log

enum class DoorLogLevel {
    /**
     * Extremely verbose: log each SQL query, start/finish of transactions
     */
    VERBOSE,

    /**
     * Very verbose: log invalidations,
     */
    DEBUG,
    /*
     * Normal - logs init, close etc
     */
    INFO,
    /*
     *
     */
    WARNING,
    /**
     * Something definitely wrong
     */
    ERROR,
    ASSERT,
}