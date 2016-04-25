# GitLab Platform Management

This is a variety of the Accenture Platform Management - https://github.com/Accenture/adop-platform-management

You can use this repo by pulling this repo first: https://github.com/arcyteodoroacn/adop-b-framework-gitlab-load-platform

This uses GitLab instead of Gerrit as the SCM, and performs GitLab API calls to create a Group, create a Project, and add users on your GitLab instance.

# What is Platform Management?

The platform management repository includes the Jenkins jobs and supporting scripts that facilitate:

- Cartridges
- Multi-tenancy via workspaces & projects

This repository is loaded into the platform using the custom "GitLab\_Load\_Platform" job and contains:

- Jenkins Job DSL - defines the Jenkins jobs for workspaces, projects, and cartridges
- Jenkins Groovy scripts - for automating the configuration of Jenkins
- Shell Scripts - for automating command line actions, such as with Gerrit or LDAP