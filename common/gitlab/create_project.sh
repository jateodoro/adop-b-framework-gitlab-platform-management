#!/bin/bash
set -e

# Usage
usage() {
    echo "Usage:"
    echo "    ${0} -g <GITLAB_URL> -t <TOKEN> -w <GROUP_ID> -p <PROJECT_NAME>"
    exit 1
}

# Constants
SLEEP_TIME=5
MAX_RETRY=2

while getopts "g:t:w:p:" opt; do
  case $opt in
    g)
      gitlab_url=${OPTARG}
      ;;
    t)
      token=${OPTARG}
      ;;
    w)
      groupid=${OPTARG}
      ;;
    p)
      projectname=${OPTARG}
      ;;
    *)
      echo "Invalid parameter(s) or option(s)."
      usage
      ;;
  esac
done

if [ -z "${gitlab_url}" ] || [ -z "${token}" ] || [ -z "${groupid}" ] || [ -z "${projectname}" ]; then
    echo "Parameters missing"
    usage
fi

echo "Creating project: ${projectname}"
count=1
until [ $count -ge ${MAX_RETRY} ]
do
  ret=$(curl --header "PRIVATE-TOKEN: $token" -X POST "${gitlab_url}api/v3/projects?&name=${projectname}&path=${projectname}&namespace_id=${groupid}")
  [[ ${ret} -eq 302  ]] && break
  count=$[$count+1]
  echo "Unable to create group ${username}, response code ${ret}, retry ... ${count}"
  sleep ${SLEEP_TIME}	
done
