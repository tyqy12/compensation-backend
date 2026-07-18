#!/bin/sh
set -eu

mkdir -p /var/log/compensation /var/lib/compensation/files
chown -R compensation:compensation /var/log/compensation /var/lib/compensation/files

exec gosu compensation sh -c "exec java ${JAVA_OPTS:-} -jar /app/app.jar"
