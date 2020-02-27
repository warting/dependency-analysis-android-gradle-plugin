#!/usr/bin/env bash
# Script to execute functional tests against a list of AGP versions
# Must be run from the project root directory (not THIS directory!)

RED='\033[0;31m'
NC='\033[0m'

if [[ $(pwd) == *scripts ]]; then
  >&2 echo "Must execute script from project root"
  exit 1
fi

# TODO fix issues with 4.0.0-beta01
## Looks like the bundleLibCompileDebug task is gone, or renamed
# TODO add 4.1.0-alpha01
agpVersions=('3.5.3' '3.6.0')

for v in "${agpVersions[@]}"; do
  echo "Executing functional tests against AGP $v"
  ./gradlew functionalTest -DfuncTest.agpVersion="$v"
  if [ $? -ne 0 ]; then
    >&2 echo -e "${RED}Functional test failed against AGP $v${NC}"
  fi
done
