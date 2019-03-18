#!/usr/bin/env bash

set -e

GRAPHDB="http://localhost:7200"
CONSOLE="openrdf-console/bin/console.sh --force --echo --serverURL $GRAPHDB"

GREEN='\033[0;32m'
RED='\033[0;31m'
NO_COLOUR='\033[0m'
DELIMITER="****************************************************************************************************\n* "

printf "${GREEN}${DELIMITER}Deleting repository${NO_COLOUR}\n\n"

cat graphdb-drop-knora-test-unit-repository.ttl | $CONSOLE

printf "\n${GREEN}${DELIMITER}Creating repository${NO_COLOUR}\n\n"

sed -e 's@PIE_FILE@'"/graphdb/KnoraRules.pie"'@' graphdb-free-knora-test-unit-repository-config.ttl.tmpl > graphdb-free-knora-test-unit-repository-config.ttl

curl -X POST -H "Content-Type:text/turtle" -T graphdb-free-knora-test-unit-repository-config.ttl "$GRAPHDB/repositories/SYSTEM/rdf-graphs/service?graph=http://www.knora.org/config-test-unit"

curl -X POST -H "Content-Type:text/turtle" -d "<http://www.knora.org/config-test-unit> a <http://www.openrdf.org/config/repository#RepositoryContext> ." $GRAPHDB/repositories/SYSTEM/statements

printf "${GREEN}Repository created.\n\n${DELIMITER}Creating Lucene Index${NO_COLOUR}\n\n"

STATUS=$(curl -s -w '%{http_code}' -S -X POST --data-urlencode 'update@./graphdb-knora-index-create.rq' $GRAPHDB/repositories/knora-test-unit/statements)

if [ "$STATUS" == "204" ]
then
    printf "${GREEN}Lucene index built.${NO_COLOUR}\n\n"
else
    printf "${RED}Building of Lucene index failed: ${STATUS}${NO_COLOUR}\n\n"
fi
