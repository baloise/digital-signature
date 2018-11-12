library identifier: 'WorkflowLibsShared@master', retriever: modernSCM(
  [$class: 'GitSCMSource', remote: 'https://git.balgroupit.com/CICD-DevOps/WorkflowLibsShared.git']
)

pipeline {
    agent {
        label 'common'
    }

    options {
        skipStagesAfterUnstable()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '28'))
        timeout(time: 1, unit: 'HOURS')
    }

    triggers {
        // at least once a day
        cron('H H(0-7) * * *')
        // every sixty minutes
        pollSCM('H/5 * * * *')
    }

    stages {
        stage("SCM Checkout") {
            steps {
                deleteDir()
                checkout scm
            }
        }

        stage("Maven") {
            steps {
                mvn "clean verify"
            }
        }
    }
}
