// Constants
def gerritBaseUrl = "ssh://jenkins@gerrit:29418"
def cartridgeBaseUrl = gerritBaseUrl + "/cartridges"
def platformToolsGitUrl = gerritBaseUrl + "/platform-management"

// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"

def projectFolderName = workspaceFolderName + "/${PROJECT_NAME}"
def projectFolder = folder(projectFolderName)

def cartridgeManagementFolderName= projectFolderName + "/Cartridge_Management"
def cartridgeManagementFolder = folder(cartridgeManagementFolderName) { displayName('Cartridge Management') }

// Cartridge List
//def cartridge_list = []
//readFileFromWorkspace("${WORKSPACE}/cartridges.txt").eachLine { line ->
//    cartridge_repo_name = line.tokenize("/").last()
//    local_cartridge_url = cartridgeBaseUrl + "/" + cartridge_repo_name
//    cartridge_list << local_cartridge_url
//}


// Jobs
def loadCartridgeJob = freeStyleJob(cartridgeManagementFolderName + "/Load_Cartridge")

// Setup Load_Cartridge
loadCartridgeJob.with{
    parameters{
        stringParam('CARTRIDGE_CLONE_URL', '', 'Cartridge URL to load')
    }
    environmentVariables {
        env('WORKSPACE_NAME',workspaceFolderName)
        env('PROJECT_NAME',projectFolderName)
    }
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        sshAgent("adop-jenkins-master")
    }
	configure { project ->
		project / 'buildWrappers' / 'org.jenkinsci.plugins.credentialsbinding.impl.SecretBuildWrapper' / 'bindings' / 'org.jenkinsci.plugins.credentialsbinding.impl.StringBinding' {
		    'credentialsId'('gitlab-secrets-id')
			'variable'('GITLAB_TOKEN')
		}
	}
    steps {
        shell('''#!/bin/bash -ex
chmod +x ${WORKSPACE}/common/gitlab/create_project.sh

# We trust everywhere
#echo -e "#!/bin/sh 
#exec ssh -o StrictHostKeyChecking=no "\$@" 
#" > ${WORKSPACE}/custom_ssh
#chmod +x ${WORKSPACE}/custom_ssh
#export GIT_SSH="${WORKSPACE}/custom_ssh"        
        
# Clone Cartridge
git clone ${CARTRIDGE_CLONE_URL} cartridge

repo_namespace="${PROJECT_NAME}"
permissions_repo="${repo_namespace}/permissions"

# Create repositories
mkdir ${WORKSPACE}/tmp
cd ${WORKSPACE}/tmp
while read repo_url; do
    if [ ! -z "${repo_url}" ]; then
        repo_name=$(echo "${repo_url}" | rev | cut -d'/' -f1 | rev | sed 's#.git$##g')
        target_repo_name="${WORKSPACE_NAME}/${repo_name}"
        
        # get the namespace id of the group
		gid="$(curl --header "PRIVATE-TOKEN: $GITLAB_TOKEN" "http://gitlab/gitlab/api/v3/groups/${WORKSPACE_NAME}" | python -c "import json,sys;obj=json.load(sys.stdin);print obj['id'];")"
				
		# create new project				
		${WORKSPACE}/common/gitlab/create_project.sh -g http://gitlab/gitlab/ -t "${GITLAB_TOKEN}" -w "${gid}" -p "${repo_name}"	
        
        # Populate repository
        git clone git@gitlab:"${target_repo_name}.git"
        cd "${repo_name}"
        git remote add source "${repo_url}"
        git fetch source
        git push origin +refs/remotes/source/*:refs/heads/*
        cd -
    fi
done < ${WORKSPACE}/cartridge/src/urls.txt

# Provision one-time infrastructure
if [ -d ${WORKSPACE}/cartridge/infra ]; then
    cd ${WORKSPACE}/cartridge/infra
    if [ -f provision.sh ]; then
        source provision.sh
    else
        echo "INFO: cartridge/infra/provision.sh not found"
    fi
fi

# Generate Jenkins Jobs
if [ -d ${WORKSPACE}/cartridge/jenkins/jobs ]; then
    cd ${WORKSPACE}/cartridge/jenkins/jobs
    if [ -f generate.sh ]; then
        source generate.sh
    else
        echo "INFO: cartridge/jenkins/jobs/generate.sh not found"
    fi
fi
''')
        systemGroovyCommand('''
import jenkins.model.*
import groovy.io.FileType

def jenkinsInstace = Jenkins.instance
def projectName = build.getEnvironment(listener).get('PROJECT_NAME')
def xmlDir = new File(build.getWorkspace().toString() + "/cartridge/jenkins/jobs/xml")
def fileList = []

xmlDir.eachFileRecurse (FileType.FILES) { file ->
    if(file.name.endsWith('.xml')) {
        fileList << file
    }
}
fileList.each {
	String configPath = it.path
  	File configFile = new File(configPath)
    String configXml = configFile.text
    ByteArrayInputStream xmlStream = new ByteArrayInputStream( configXml.getBytes() )
    String jobName = configFile.getName().substring(0, configFile.getName().lastIndexOf('.'))

    jenkinsInstace.getItem(projectName,jenkinsInstace).createProjectFromXML(jobName, xmlStream)
}
''')
        dsl {
            external("cartridge/jenkins/jobs/dsl/**/*.groovy")
        }

    }
    scm {
        git {
            remote {
                name("origin")
                url("git@gitlab:root/platform-management.git")
                credentials("adop-jenkins-master")
            }
            branch("*/master")
        }
    }
}