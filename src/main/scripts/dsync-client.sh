#!/bin/bash

type_p_java=`type -p java`

if [[ "$type_p_java" ]]; then
    _java=$type_p_java
elif [[ -n "$JAVA_HOME" ]] && [[ -x "$JAVA_HOME/bin/java" ]];  then
    _java="$JAVA_HOME/bin/java"
else
    echo "No java found. Please, install one"
    exit
fi

if [[ "$_java" ]]; then
    
    JAVA_VER=$("$_java" -version 2>&1 | sed 's/java version "\(.*\)\.\(.*\)\..*"/\1\2/; 1q')
    
    if [ "$JAVA_VER" -ge 18 ]; then
        
        $_java -cp "lib/*:config" com.yet.dsync.DSyncClient
        
    else
        echo "Java version is less than 1.8. Program won't run"
    fi
fi