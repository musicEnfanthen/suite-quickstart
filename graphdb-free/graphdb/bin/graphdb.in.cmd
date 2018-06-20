@echo off

:: If GDB_HEAP_SIZE is provided it will override GDB_MIN_MEM and GDB_MAX_MEM
if NOT "%GDB_HEAP_SIZE%" == "" (
    set GDB_MIN_MEM=%GDB_HEAP_SIZE%
    set GDB_MAX_MEM=%GDB_HEAP_SIZE%
)

:: Use GDB_MIN_MEM and GDB_MAX_MEM to set -Xms and -Xmx if they have values
if NOT "%GDB_MIN_MEM%" == "" (
    set JAVA_OPTS=%JAVA_OPTS% -Xms%GDB_MIN_MEM%
) else (
    :: an absolute default for minimum heap size, this helps with 32-bit "client" java
    set JAVA_OPTS=%JAVA_OPTS% -Xms1g
)

if NOT "%GDB_MAX_MEM%" == "" (
    set JAVA_OPTS=%JAVA_OPTS% -Xmx%GDB_MAX_MEM%
)

:: Use GDB_HEAP_NEWSIZE for -Xmn if it has values
if NOT "%GDB_HEAP_NEWSIZE%" == "" (
    set JAVA_OPTS=%JAVA_OPTS% -Xmn%GDB_HEAP_NEWSIZE%
)

:: Set to headless, just in case
set JAVA_OPTS=%JAVA_OPTS% -Djava.awt.headless=true

:: Ensure UTF-8 encoding by default (e.g. filenames)
set JAVA_OPTS=%JAVA_OPTS% -Dfile.encoding=UTF-8

:: Prefer IPv4 stack, helps on broken IPv6 configs
set JAVA_OPTS=%JAVA_OPTS% -Djava.net.preferIPv4Stack=true

:: Default garbage collector
set JAVA_OPTS=%JAVA_OPTS% -XX:+UseParallelGC

:: Alternative garbage collector (comment the above and uncomment this)
:: set JAVA_OPTS=%JAVA_OPTS% -XX:+UseConcMarkSweepGC

:: Don't omit stack traces when the JVM recompiles on the fly and swaps with precompiled exceptions
set JAVA_OPTS=%JAVA_OPTS% -XX:-OmitStackTraceInFastThrow

:: Causes the JVM to dump its heap on OutOfMemory.
set JAVA_OPTS=%JAVA_OPTS% -XX:+HeapDumpOnOutOfMemoryError
:: The path to the heap dump location, note directory must exists and have enough
:: space for a full heap dump.
set JAVA_OPTS=%JAVA_OPTS% -XX:HeapDumpPath="%GDB_DIST%\heapdump.hprof"

:: Exit immediately on out of memory error (but a heap dump will still be done if configured)
:: (not set as there is no easy way to do kill -9 on Windows)

:: Garbage collect logs, set GDB_GC_LOG to true to enable
if "%GDB_GC_LOG%" == "true" (
    if "%GDB_GC_LOG_FILE%" == "" (
        set GDB_GC_LOG_FILE=%GDB_DIST%\gc-%%p.log
    )

    :: Print current heap distributions - before and after GC
    set JAVA_OPTS=%JAVA_OPTS% -XX:+PrintGCDetails
    :: Don't use timestamps but dates instead
    set JAVA_OPTS=%JAVA_OPTS% -XX:+PrintGCDateStamps
    :: Print Tunering distribution so we can spot resizing
    set JAVA_OPTS=%JAVA_OPTS% -XX:+PrintTenuringDistribution
    :: Logs rotation options
    set JAVA_OPTS=%JAVA_OPTS% -XX:+UseGCLogFileRotation
    set JAVA_OPTS=%JAVA_OPTS% -XX:GCLogFileSize=2M
    set JAVA_OPTS=%JAVA_OPTS% -XX:NumberOfGCLogFiles=5
    set JAVA_OPTS=%JAVA_OPTS% -Xloggc:"!GDB_GC_LOG_FILE!"
)