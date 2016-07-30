// Constants
def WorkspaceManagementFolderName= "/Workspace_Management"
def WorkspaceManagementFolder = folder(WorkspaceManagementFolderName) { displayName('Workspace_Management') }

// Jobs
def generateWorkspaceJob = freeStyleJob(WorkspaceManagementFolderName + "/Generate_Workspace")
 
// Setup generateBuildPipelineJobs
generateWorkspaceJob.with {
		parameters {
			stringParam('WORKSPACE_NAME', '','')
			stringParam("ADMIN_USERS","gitlabuser@accenture.com","The list of users' email addresses that should be setup initially as admin. They will have full access to all jobs within the project.")
			stringParam("DEVELOPER_USERS","gitlabuser@accenture.com","The list of users' email addresses that should be setup initially as developers. They will have full access to all non-admin jobs within the project.")
			stringParam("VIEWER_USERS","gitlabuser@accenture.com","The list of users' email addresses that should be setup initially as viewers. They will have read-only access to all non-admin jobs within the project.")
		}
		label("ldap")
		wrappers {
			preBuildCleanup()
			injectPasswords()
			maskPasswords()
			environmentVariables {
				env('DC',"${LDAP_ROOTDN}")
				env('OU_GROUPS','ou=groups')
				env('OU_PEOPLE','ou=people')
				env('OUTPUT_FILE','output.ldif')
			}
			credentialsBinding {
				usernamePassword("LDAP_ADMIN_USER", "LDAP_ADMIN_PASSWORD", "adop-ldap-admin")
			}
		}
		configure { project ->
			project / 'buildWrappers' / 'org.jenkinsci.plugins.credentialsbinding.impl.SecretBuildWrapper' / 'bindings' / 'org.jenkinsci.plugins.credentialsbinding.impl.StringBinding' {
			    'credentialsId'('gitlab-secrets-id')
				'variable'('GITLAB_TOKEN')
			}
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
				shell('''#!/bin/bash
					# Validate Variables
					pattern=" |'"
					if [[ "${WORKSPACE_NAME}" =~ ${pattern} ]]; then
						echo "WORKSPACE_NAME contains a space, please replace with an underscore - exiting..."
						exit 1
					fi
				''')
				shell('''
# LDAP
chmod +x ${WORKSPACE}/common/ldap/*.sh
chmod +x ${WORKSPACE}/common/ldap/lib/*.sh
chmod +x ${WORKSPACE}/common/gitlab/*.sh
chmod +x ${WORKSPACE}/common/gitlab/group/*.sh
				
${WORKSPACE}/common/ldap/generate_role.sh -r "admin" -n "${WORKSPACE_NAME}" -d "${DC}" -g "${OU_GROUPS}" -p "${OU_PEOPLE}" -u "${ADMIN_USERS}" -f "${OUTPUT_FILE}" -w "${WORKSPACE}"
${WORKSPACE}/common/ldap/generate_role.sh -r "developer" -n "${WORKSPACE_NAME}" -d "${DC}" -g "${OU_GROUPS}" -p "${OU_PEOPLE}" -u "${DEVELOPER_USERS}" -f "${OUTPUT_FILE}" -w "${WORKSPACE}"
${WORKSPACE}/common/ldap/generate_role.sh -r "viewer" -n "${WORKSPACE_NAME}" -d "${DC}" -g "${OU_GROUPS}" -p "${OU_PEOPLE}" -u "${VIEWER_USERS}" -f "${OUTPUT_FILE}" -w "${WORKSPACE}"

set +e
${WORKSPACE}/common/ldap/load_ldif.sh -h ldap -u "${LDAP_ADMIN_USER}" -p "${LDAP_ADMIN_PASSWORD}" -b "${DC}" -f "${OUTPUT_FILE}"
set -e

ADMIN_USERS=$(echo ${ADMIN_USERS} | tr ',' ' ')
DEVELOPER_USERS=$(echo ${DEVELOPER_USERS} | tr ',' ' ')
VIEWER_USERS=$(echo ${VIEWER_USERS} | tr ',' ' ')
					
# GitLab
for user in $ADMIN_USERS $DEVELOPER_USERS $VIEWER_USERS
do
		username=$(echo ${user} | cut -d'@' -f1)
		${WORKSPACE}/common/gitlab/create_user.sh -g http://gitlab/gitlab/ -t "${GITLAB_TOKEN}" -u "${username}" -p "${username}" -e "${user}" 
done

# create new group			
${WORKSPACE}/common/gitlab/create_group.sh -g http://gitlab/gitlab/ -t "${GITLAB_TOKEN}" -w "${WORKSPACE_NAME}"
										
# get the id of the group
gid="$(curl --header "PRIVATE-TOKEN: $GITLAB_TOKEN" "http://gitlab/gitlab/api/v3/groups/${WORKSPACE_NAME}" | python -c "import json,sys;obj=json.load(sys.stdin);print obj['id'];")"
					
# add the users to the group as owners
for owner in $ADMIN_USERS
do
		ownername=$(echo ${owner} | cut -d'@' -f1)
		uid="$(curl --header "PRIVATE-TOKEN: $GITLAB_TOKEN" "http://gitlab/gitlab/api/v3/users?username=${ownername}" | python -c "import json,sys;obj=json.load(sys.stdin);print obj[0]['id'];")"
		${WORKSPACE}/common/gitlab/group/add_user_to_group.sh -g http://gitlab/gitlab/ -t $GITLAB_TOKEN -i $gid -u $uid -a 50
done

# add the users to the group as guests
for guest in $DEVELOPER_USERS $VIEWER_USERS
do
		guestname=$(echo ${guest} | cut -d'@' -f1)
		uid="$(curl --header "PRIVATE-TOKEN: $GITLAB_TOKEN" "http://gitlab/gitlab/api/v3/users?username=${guestname}" | python -c "import json,sys;obj=json.load(sys.stdin);print obj[0]['id'];")"
		${WORKSPACE}/common/gitlab/group/add_user_to_group.sh -g http://gitlab/gitlab/ -t $GITLAB_TOKEN -i $gid -u $uid -a 10
done''')
				dsl {
					external("workspaces/jobs/**/*.groovy")
				}
				systemGroovyScriptFile('${WORKSPACE}/workspaces/groovy/acl_admin.groovy')
				systemGroovyScriptFile('${WORKSPACE}/workspaces/groovy/acl_developer.groovy')
				systemGroovyScriptFile('${WORKSPACE}/workspaces/groovy/acl_viewer.groovy')
		}
}

