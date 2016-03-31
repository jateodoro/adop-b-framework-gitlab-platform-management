#!/bin/bash
set -e

# Usage
usage() {
    echo "Usage:"
    echo "    ${0} -g <GITLAB_URL> -t <TOKEN> -u <USER> -p <PASSWORD> -e <EMAIL>"
    exit 1
}

# Constants
SLEEP_TIME=5
MAX_RETRY=2

while getopts "g:t:u:p:e:" opt; do
  case $opt in
    g)
      gitlab_url=${OPTARG}
      ;;
    t)
      token=${OPTARG}
      ;;
    u)
      username=${OPTARG}
      ;;
    p)
      password=${OPTARG}
      ;;
    e)
      email=${OPTARG}
      ;;
    *)
      echo "Invalid parameter(s) or option(s)."
      usage
      ;;
  esac
done

if [ -z "${gitlab_url}" ] || [ -z "${token}" ] || [ -z "${username}" ] || [ -z "${password}" ] || [ -z "${email}" ]; then
    echo "Parameters missing"
    usage
fi

echo "Creating user: ${username}"
count=1
until [ $count -ge ${MAX_RETRY} ]
do
  ret=$(curl --header "PRIVATE-TOKEN: ${token}" -X POST "${gitlab_url}api/v3/users?email=${email}&name=${username}&username=${username}&password=${password}&provider=ldap&extern_uid=cn=${username},ou=people,dc=ldap,dc=example,dc=com&confirm=false")
  [[ ${ret} -eq 302  ]] && break
  count=$[$count+1]
  echo "Unable to create user ${username}, response code ${ret}, retry ... ${count}"
  sleep ${SLEEP_TIME}	
done
