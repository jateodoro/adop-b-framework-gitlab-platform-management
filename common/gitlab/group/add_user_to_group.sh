#!/bin/bash
set -e

# Usage
usage() {
    echo "Usage:"
    echo "    ${0} -g <GITLAB_URL> -t <TOKEN> -i <GROUP_ID> -u <USER_ID> -a <ACCESS_LEVEL>"
    exit 1
}

# Constants
SLEEP_TIME=5
MAX_RETRY=2

while getopts "g:t:i:u:a:" opt; do
  case $opt in
    g)
      gitlab_url=${OPTARG}
      ;;
    t)
      token=${OPTARG}
      ;;
    i)
      groupid=${OPTARG}
      ;;
    u)
      userid=${OPTARG}
      ;;
    a)
      accesslevel=${OPTARG}
      ;;
    *)
      echo "Invalid parameter(s) or option(s)."
      usage
      ;;
  esac
done

if [ -z "${gitlab_url}" ] || [ -z "${token}" ] || [ -z "${groupid}" ] || [ -z "${userid}" ] || [ -z "${accesslevel}" ]; then
    echo "Parameters missing"
    usage
fi

echo "Adding user: ${userid} to group {groupid}"
count=1
until [ $count -ge ${MAX_RETRY} ]
do
  ret=$(curl --header "PRIVATE-TOKEN: $token" -X POST "${gitlab_url}api/v3/groups/${groupid}/members?id=${groupid}&user_id=${userid}&access_level=${accesslevel}")
  [[ ${ret} -eq 302  ]] && break
  count=$[$count+1]
  echo "Unable to create group ${username}, response code ${ret}, retry ... ${count}"
  sleep ${SLEEP_TIME}	
done
