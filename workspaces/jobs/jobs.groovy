// Constants
def platformToolsGitURL = "ssh://jenkins@gerrit:29418/platform-management"

// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def workspaceFolder = folder(workspaceFolderName)

def projectManagementFolderName= workspaceFolderName + "/Project_Management"
def projectManagementFolder = folder(projectManagementFolderName) { displayName('Project Management') }

// Jobs
def generateProjectJob = freeStyleJob(projectManagementFolderName + "/Generate_Project")

// Setup Generate_Project
generateProjectJob.with{
    parameters{
        stringParam("PROJECT_NAME","","The name of the project to be generated.")
        stringParam("ADMIN_USERS","gitlab@accenture.com","The list of users' email addresses that should be setup initially as admin. They will have full access to all jobs within the project.")
        stringParam("DEVELOPER_USERS","gitlab@accenture.com","The list of users' email addresses that should be setup initially as developers. They will have full access to all non-admin jobs within the project.")
        stringParam("VIEWER_USERS","gitlab@accenture.com","The list of users' email addresses that should be setup initially as viewers. They will have read-only access to all non-admin jobs within the project.")
    }
    label("ldap")
    environmentVariables {
        env('WORKSPACE_NAME',workspaceFolderName)
    }
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        environmentVariables {
            env('DC',"${DC}")
            env('OU_GROUPS','ou=groups')
            env('OU_PEOPLE','ou=people')
            env('OUTPUT_FILE','output.ldif')
        }
        credentialsBinding {
            usernamePassword("LDAP_ADMIN_USER", "LDAP_ADMIN_PASSWORD", "adop-ldap-admin")
        }
        sshAgent("adop-jenkins-master")
    }
	scm {
				git {
						remote {
								url('git@gitlab:root/platform-management.git')
								credentials("adop-jenkins-master")
						}	
						branch('*/master')
				}
	}
    steps {
        shell('''#!/bin/bash -e

				# Validate Variables
				pattern=" |'"
				if [[ "${PROJECT_NAME}" =~ ${pattern} ]]; then
					echo "PROJECT_NAME contains a space, please replace with an underscore - exiting..."
					exit 1
				fi
		''')
        shell('''set -e
		
				chmod +x ${WORKSPACE}/common/ldap/*.sh
				chmod +x ${WORKSPACE}/common/ldap/lib/*.sh
				chmod +x ${WORKSPACE}/common/gitlab/*.sh
				chmod +x ${WORKSPACE}/common/gitlab/group/*.sh		
				chmod +x ${WORKSPACE}/projects/gitlab/*.sh	

				# LDAP
				${WORKSPACE}/common/ldap/generate_role.sh -r "admin" -n "${WORKSPACE_NAME}.${PROJECT_NAME}" -d "${DC}" -g "${OU_GROUPS}" -p "${OU_PEOPLE}" -u "${ADMIN_USERS}" -f "${OUTPUT_FILE}" -w "${WORKSPACE}"
				${WORKSPACE}/common/ldap/generate_role.sh -r "developer" -n "${WORKSPACE_NAME}.${PROJECT_NAME}" -d "${DC}" -g "${OU_GROUPS}" -p "${OU_PEOPLE}" -u "${DEVELOPER_USERS}" -f "${OUTPUT_FILE}" -w "${WORKSPACE}"
				${WORKSPACE}/common/ldap/generate_role.sh -r "viewer" -n "${WORKSPACE_NAME}.${PROJECT_NAME}" -d "${DC}" -g "${OU_GROUPS}" -p "${OU_PEOPLE}" -u "${VIEWER_USERS}" -f "${OUTPUT_FILE}" -w "${WORKSPACE}"

				set +e
				${WORKSPACE}/common/ldap/load_ldif.sh -h ldap -u "${LDAP_ADMIN_USER}" -p "${LDAP_ADMIN_PASSWORD}" -b "${DC}" -f "${OUTPUT_FILE}"
				set -e

				ADMIN_USERS=$(echo ${ADMIN_USERS} | tr ',' ' ')
				DEVELOPER_USERS=$(echo ${DEVELOPER_USERS} | tr ',' ' ')
				VIEWER_USERS=$(echo ${VIEWER_USERS} | tr ',' ' ')

				# Gitlab
				token="$(curl -X POST "http://gitlab:9080/api/v3/session?login=root&password=gitlab1234" | python -c "import json,sys;obj=json.load(sys.stdin);print obj['private_token'];")"
				
				# get the namespace id of the group
				gid="$(curl --header "PRIVATE-TOKEN: $token" "http://gitlab:9080/api/v3/groups/${WORKSPACE_NAME}" | python -c "import json,sys;obj=json.load(sys.stdin);print obj['id'];")"
				
				# create new project				
				${WORKSPACE}/common/gitlab/create_project.sh -g http://gitlab:9080/ -t "${token}" -w "${gid}" -p "${PROJECT_NAME}"
				
				# get project id
				pid="$(curl --header "PRIVATE-TOKEN: $token" "http://gitlab:9080/api/v3/projects/${WORKSPACE_NAME}%2F${PROJECT_NAME}" | python -c "import json,sys;obj=json.load(sys.stdin);print obj['id'];")"
				
				# add the users to the project as owners
				for owner in $ADMIN_USERS
				do
						ownername=$(echo ${owner} | cut -d'@' -f1)
						uid="$(curl --header "PRIVATE-TOKEN: $token" "http://gitlab:9080/api/v3/users?username=${ownername}" | python -c "import json,sys;obj=json.load(sys.stdin);print obj[0]['id'];")"
						${WORKSPACE}/projects/gitlab/add_user_to_project.sh -g http://gitlab:9080/ -t $token -p $pid -u $uid -a 50
				done
				
				# add the users to the project as developers
				for developer in $DEVELOPER_USERS
				do
						developername=$(echo ${developer} | cut -d'@' -f1)
						uid="$(curl --header "PRIVATE-TOKEN: $token" "http://gitlab:9080/api/v3/users?username=${developername}" | python -c "import json,sys;obj=json.load(sys.stdin);print obj[0]['id'];")"
						${WORKSPACE}/projects/gitlab/add_user_to_project.sh -g http://gitlab:9080/ -t $token -p $pid -u $uid -a 30
				done
				
				# add the users to the project as guests
				for guest in $VIEWER_USERS
				do
						guestname=$(echo ${guest} | cut -d'@' -f1)
						uid="$(curl --header "PRIVATE-TOKEN: $token" "http://gitlab:9080/api/v3/users?username=${guestname}" | python -c "import json,sys;obj=json.load(sys.stdin);print obj[0]['id'];")"
						${WORKSPACE}/projects/gitlab/add_user_to_project.sh -g http://gitlab:9080/ -t $token -p $pid -u $uid -a 10
				done
		''')
        shell('''#!/bin/bash -ex
				# Gerrit
				source ${WORKSPACE}/projects/gerrit/configure.sh
		''')
        dsl {
            external("projects/jobs/**/*.groovy")
        }
        systemGroovyScriptFile('${WORKSPACE}/projects/groovy/acl_admin.groovy')
        systemGroovyScriptFile('${WORKSPACE}/projects/groovy/acl_developer.groovy')
        systemGroovyScriptFile('${WORKSPACE}/projects/groovy/acl_viewer.groovy')
    }
}
