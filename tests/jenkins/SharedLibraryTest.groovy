import java.util.regex.Pattern

class JenkinsMock {
    Map environment = [SSC_URL: 'https://ssc.example.com', BRANCH_NAME: 'main',
                       BUILD_TAG: 'jenkins-test-main-42', BUILD_NUMBER: '42']
    List environments = []
    List credentialScopes = []
    List commands = []
    List stages = []
    List reports = []
    List archives = []
    List deletedDirectories = []
    String directory
    String failOnCommand
    Map parameters = [:]
    Binding binding

    JenkinsMock(Map additionalEnvironment = [:]) {
        environment.putAll(additionalEnvironment)
        binding = new Binding([
            env: environment, scm: 'test-scm', params: parameters,
            error: { message -> throw new IllegalArgumentException(message.toString()) },
            pwd: { options -> '/workspace/tmp' },
            withEnv: { values, body ->
                def saved = new LinkedHashMap(environment)
                def resolved = values.collectEntries { value ->
                    def entry = value.toString().split('=', 2)
                    [(entry[0]): entry[1]]
                }
                environments.add(resolved)
                environment.putAll(resolved)
                try { body.call() } finally {
                    environment.clear()
                    environment.putAll(saved)
                }
            },
            withCredentials: { values, body -> credentialScopes.add(values); body.call() },
            usernamePassword: { values -> [kind: 'usernamePassword'] + values },
            string: { values ->
                if (values.containsKey('name')) {
                    parameters[values.name] = values.defaultValue
                } else {
                    return [kind: 'string'] + values
                }
            },
            booleanParam: { values -> parameters[values.name] = values.defaultValue },
            stage: { name, body -> stages.add(name); body.call() },
            sh: { command ->
                commands.add(command.toString())
                if (failOnCommand && command.toString().contains(failOnCommand)) {
                    throw new IllegalStateException('Simulated shell failure')
                }
            },
            echo: { message -> },
            dir: { path, body ->
                def saved = directory
                directory = path.toString()
                try { body.call() } finally { directory = saved }
            },
            deleteDir: { deletedDirectories.add(directory) },
            sarif: { values -> values },
            recordIssues: { values -> reports.add(values) },
            archiveArtifacts: { values -> archives.add(values) },
            checkout: { value -> assert value == 'test-scm' },
            label: { value -> }, jdk: { value -> }, maven: { value -> },
            skipDefaultCheckout: { value -> }, disableConcurrentBuilds: { -> }
        ])
        ['pipeline', 'agent', 'tools', 'options', 'parameters', 'stages', 'steps', 'script', 'post', 'success'].each { name ->
            binding.setVariable(name, { body -> body.call() })
        }
        binding.setVariable('fortifyCi', { options -> step().call(options) })
    }

    Script step() {
        new GroovyShell(binding).parse(new File('vars/fortifyCi.groovy'))
    }

    Script wrapper() {
        new GroovyShell(binding).parse(new File('vars/fortifyPipeline.groovy'))
    }

    List tokens(String command) {
        def matcher = Pattern.compile('([^"]\\S*|".+?")\\s*').matcher(command)
        def result = []
        while (matcher.find()) {
            result.add(matcher.group(1).replace('"', ''))
        }
        result
    }
}

def casesPassed = 0
def check = { String name, Closure body ->
    body.call()
    casesPassed++
    println "PASS: ${name}"
}

check('default lifecycle, SARIF publication, and environment restoration') {
    def mock = new JenkinsMock()
    def result = mock.step().call(appName: 'orders')
    assert result == [appVersion: 'orders:main', scanPolicy: 'security', remediationBranch: null]
    assert mock.stages == ['Setup fcli', 'Fortify ScanCentral SAST', 'Export SARIF']
    assert mock.reports[0].tools == [[pattern: 'fortify-sast.sarif']]
    assert mock.reports[0].skipPublishingChecks
    assert mock.archives[0].artifacts == 'fortify-sast.sarif'
    assert mock.deletedDirectories == ['/workspace/tmp/fortify-ci']
    assert !mock.environment.containsKey('FORTIFY_CI_DIR')
    assert mock.environments[0].DO_AVIATOR_AUDIT == 'false'
    assert mock.environments[0].DO_AVIATOR_REMEDIATIONS == 'false'
    assert mock.environments[0].DO_SETUP == 'true'
    assert mock.environments[0].DO_SAST_SCAN == 'true'
    assert mock.environments[0].DO_WAIT == 'true'
    assert mock.environments[0].DO_SAST_WAIT == 'true'
    assert mock.environments[0].DO_APPVERSION_SUMMARY == 'true'
    assert mock.environments[0].DO_SAST_EXPORT == 'false'
    assert mock.environments[0].DO_DEBRICKED_EXPORT == 'false'
    assert mock.environments[0].DO_PR_COMMENT == 'false'
    assert mock.environments[0].FCLI_DATA_DIR == '/workspace/tmp/fortify-ci/data/fcli/v3'
    assert mock.environments[0].FCLI_STATE_DIR == '/workspace/tmp/fortify-ci/data/fcli/v3/state'
}

check('PR comments default on for change requests and remain overridable') {
    def prMock = new JenkinsMock([BRANCH_NAME: 'PR-42', CHANGE_ID: '42'])
    prMock.step().call(appName: 'orders', exportSarif: false)
    assert prMock.environments[0].SSC_APPVERSION == 'orders:PR-42'
    assert prMock.environments[0].DO_PR_COMMENT == 'true'

    def disabledMock = new JenkinsMock([BRANCH_NAME: 'PR-42', CHANGE_ID: '42'])
    disabledMock.step().call(appName: 'orders', exportSarif: false, doPrComment: false)
    assert disabledMock.environments[0].DO_PR_COMMENT == 'false'
}

check('application can override default fcli action flags') {
    def mock = new JenkinsMock()
    mock.step().call(appName: 'orders', exportSarif: false,
        doSetup: false, doSastScan: false, doWait: false, doSastWait: false,
        doAppVersionSummary: false, doSastExport: true, doDebrickedExport: true, doPrComment: true)
    assert mock.environments[0].DO_SETUP == 'false'
    assert mock.environments[0].DO_SAST_SCAN == 'false'
    assert mock.environments[0].DO_WAIT == 'false'
    assert mock.environments[0].DO_SAST_WAIT == 'false'
    assert mock.environments[0].DO_APPVERSION_SUMMARY == 'false'
    assert mock.environments[0].DO_SAST_EXPORT == 'true'
    assert mock.environments[0].DO_DEBRICKED_EXPORT == 'true'
    assert mock.environments[0].DO_PR_COMMENT == 'true'
}

[
    [branch: 'main', source: null, policy: 'security'],
    [branch: 'feature/login', source: null, policy: 'devops'],
    [branch: 'PR-42', source: 'feature/login', policy: 'devops'],
    [branch: 'PR-42', source: 'bugfix/login', policy: 'security'],
    [branch: 'release/1.0', source: null, policy: 'security'],
    [branch: null, source: null, policy: 'security']
].each { scenario ->
    check("policy selection for ${scenario.branch}/${scenario.source}") {
        def mock = new JenkinsMock([BRANCH_NAME: scenario.branch, CHANGE_BRANCH: scenario.source])
        mock.step().call(appName: 'orders', exportSarif: false, sensorPool: ' Linux Pool ', scanTimeoutMinutes: 60)
        assert mock.tokens(mock.environments[0].SAST_SCAN_EXTRA_OPTS) ==
            ['--pool', 'Linux Pool', '--scan-timeout=60', '--sargs', "-scan-policy ${scenario.policy}".toString()]
        def expectedSetupOptions = '--issue-template "Prioritized High Risk Issue Template"'
        if (scenario.policy == 'devops') {
            expectedSetupOptions += ' --copy-from "orders:main"'
        }
        assert mock.environments[0].SETUP_EXTRA_OPTS == expectedSetupOptions
        assert mock.reports.empty
    }
}

check('feature version copies from configured parent branch') {
    def mock = new JenkinsMock([BRANCH_NAME: 'feature/login', FORTIFY_PARENT_BRANCH: 'develop'])
    mock.step().call(appName: 'orders', exportSarif: false)
    assert mock.environments[0].SETUP_EXTRA_OPTS ==
        '--issue-template "Prioritized High Risk Issue Template" --copy-from "orders:develop"'
}

check('per-application settings and partial credential overrides') {
    def mock = new JenkinsMock([FORTIFY_SENSOR_POOL: 'global-pool', FORTIFY_ISSUE_TEMPLATE: 'global-template',
                                NPM_REGISTRY: 'https://global.example.com/npm/'])
    mock.step().call(appName: 'orders', versionName: 'v1', issueTemplate: 'Team Template',
        sensorPool: 'team-pool', npmRegistry: 'https://repo.example.com/npm/',
        credentials: [ssc: 'team-ssc', repository: 'team-repo'])
    assert mock.environments[0].SSC_APPVERSION == 'orders:v1'
    assert mock.environments[0].SETUP_EXTRA_OPTS == '--issue-template "Team Template"'
    assert mock.environments[0].FORTIFY_REPOSITORY_AUTH == 'true'
    assert mock.environments[0].NPM_REGISTRY == 'https://repo.example.com/npm/'
    assert mock.credentialScopes[0][0].credentialsId == 'team-repo'
    assert mock.credentialScopes[1]*.credentialsId == ['team-repo', 'team-ssc', 'sc-client-auth-token']
}

check('remediations use a separate branch and enable required audit') {
    def mock = new JenkinsMock([AVIATOR_URL: 'https://aviator.example.com'])
    def result = mock.step().call(appName: 'orders', aviatorRemediations: true)
    assert result.remediationBranch == 'aviator/remediations/jenkins-test-main-42'
    assert mock.tokens(mock.environments[0].AVIATOR_REMEDIATIONS_EXTRA_OPTS) ==
        ['--branch-name', result.remediationBranch.toString()]
    assert mock.environments[0].AVIATOR_REMEDIATIONS_ACTION == 'push-remediations'
    assert mock.environments[0].DO_AVIATOR_AUDIT == 'true'
    assert mock.credentialScopes[1]*.credentialsId == ['ssc-ci-token', 'sc-client-auth-token', 'aviator-token', 'git-push-token']
}

check('optional integrations bind only required credentials') {
    def mock = new JenkinsMock()
    mock.step().call(appName: 'orders', debrickedScan: true, dastScan: true, waitForDast: true,
        dastSettings: ' 123 ', checkPolicy: true, exportSarif: false)
    assert mock.environments[0].DAST_SETTINGS == '123'
    assert mock.environments[0].DO_DAST_SCAN == 'true'
    assert mock.environments[0].DO_DAST_WAIT == 'true'
    assert mock.environments[0].DO_CHECK_POLICY == 'true'
    assert mock.credentialScopes[1]*.credentialsId == ['ssc-ci-token', 'sc-client-auth-token', 'debricked-access-token']
}

check('preinstalled bootstrap skips npm setup and requires preinstalled tools') {
    def mock = new JenkinsMock()
    mock.step().call(appName: 'orders', bootstrap: 'preinstalled', credentials: [repository: 'team-repo'])
    assert mock.environments[0].PREINSTALLED == 'true'
    assert mock.environments[0].FORTIFY_BOOTSTRAP == 'preinstalled'
    assert !mock.commands.any { it.contains('npx -y') }
    assert mock.commands[0].contains('scancentral --version')
    assert mock.credentialScopes[0]*.credentialsId == ['team-repo', 'ssc-ci-token', 'sc-client-auth-token']
}

check('npx bootstrap remains the default') {
    def mock = new JenkinsMock()
    mock.step().call(appName: 'orders', exportSarif: false)
    assert mock.environments[0].PREINSTALLED == 'false'
    assert mock.commands[0].contains('npx -y')
}

[
    [name: 'invalid bootstrap', config: [bootstrap: 'curl'], env: [:]],
    [name: 'unknown setting', config: [typo: true], env: [:]],
    [name: 'missing app', config: [appName: ''], env: [:]],
    [name: 'invalid Boolean', config: [exportSarif: 'false'], env: [:]],
    [name: 'invalid fcli action Boolean', config: [doSastScan: 'false'], env: [:]],
    [name: 'pool argument injection', config: [sensorPool: 'linux" --pool main'], env: [:]],
    [name: 'issue-template argument injection', config: [issueTemplate: 'bad" --copy main'], env: [:]],
    [name: 'parent-branch argument injection', config: [parentBranch: 'main" --skip-if-exists'], env: [:]],
    [name: 'SSC delimiter in app', config: [appName: 'orders:main'], env: [:]],
    [name: 'unsafe SARIF path', config: [sarifFile: '../report.sarif'], env: [:]],
    [name: 'non-feature policy downgrade', config: [scanPolicy: 'devops'], env: [:]],
    [name: 'fork PR', config: [:], env: [CHANGE_FORK: 'untrusted/fork']],
    [name: 'wait without DAST', config: [waitForDast: true], env: [:]],
    [name: 'invalid DAST settings', config: [dastScan: true, dastSettings: 'token'], env: [:]],
    [name: 'Aviator without URL', config: [aviatorAudit: true], env: [:]],
    [name: 'invalid timeout', config: [scanTimeoutMinutes: '-1'], env: [:]],
    [name: 'conflicting inherited scan args', config: [:], env: [SAST_SCAN_EXTRA_OPTS: '--sargs "-quick"']],
    [name: 'conflicting timeout', config: [scanTimeoutMinutes: 60], env: [SAST_SCAN_EXTRA_OPTS: '--scan-timeout=100']],
    [name: 'invalid credential map', config: [credentials: 'token'], env: [:]],
    [name: 'missing SSC credential ID', config: [credentials: [ssc: null]], env: [:]],
    [name: 'empty repository credential ID', config: [credentials: [repository: '']], env: [:]],
    [name: 'invalid pool type', config: [sensorPool: 12], env: [:]]
].each { scenario ->
    check("reject ${scenario.name} before executing commands") {
        def mock = new JenkinsMock(scenario.env)
        boolean rejected = false
        try {
            mock.step().call([appName: 'orders'] + scenario.config)
        } catch (IllegalArgumentException expected) {
            rejected = true
        }
        assert rejected
        assert mock.commands.empty
        assert mock.credentialScopes.empty
    }
}

['npx -y', 'fcli action run ci', 'sarif-sast-report'].each { failingCommand ->
    check("cleanup after ${failingCommand} failure") {
        def mock = new JenkinsMock()
        mock.failOnCommand = failingCommand
        boolean failed = false
        try { mock.step().call(appName: 'orders') } catch (IllegalStateException expected) { failed = true }
        assert failed
        assert mock.deletedDirectories == ['/workspace/tmp/fortify-ci']
        assert !mock.environment.containsKey('FORTIFY_CI_DIR')
    }
}

check('wrapper accepts custom build steps and repository credential override') {
    def mock = new JenkinsMock()
    mock.wrapper().call([appName: 'orders', credentials: [repository: 'team-repo'], exportSarif: false]) {
        mock.binding.getVariable('sh').call('./mvnw -B verify')
    }
    assert mock.commands.contains('./mvnw -B verify')
    assert !mock.commands.contains('mvn -B clean verify')
    assert mock.credentialScopes[0][0].credentialsId == 'team-repo'
    assert mock.archives[0].artifacts == 'target/*.jar'
}

check('wrapper default build and disabled artifact archiving') {
    def mock = new JenkinsMock()
    mock.wrapper().call(appName: 'orders', buildCommand: './gradlew build', artifacts: '', exportSarif: false)
    assert mock.commands.contains('./gradlew build')
    assert mock.archives.empty
}

check('application pipeline delegates to the library with existing parameters') {
    def mock = new JenkinsMock([NPM_REGISTRY: 'https://repo.onfortify.com/repository/npm-public/'])
    def consumer = new File('Jenkinsfile').text.replaceFirst(/(?m)^@Library[^\r\n]*\r?\n/, '')
    new GroovyShell(mock.binding).evaluate(consumer)
    assert mock.commands.contains('mvn -B clean verify')
    assert mock.environments[0].SSC_APPVERSION == 'jenkins-fcli-scancentral:main'
    assert mock.environments[0].NPM_REGISTRY == 'https://repo.onfortify.com/repository/npm-public/'
    assert mock.reports.size() == 1
    assert mock.credentialScopes[0][0].credentialsId == 'nexus-credentials'
}

check('interpolated configuration and disabled global feature flags') {
    def mock = new JenkinsMock([DO_DAST_SCAN: 'true', DO_AVIATOR_REMEDIATIONS: 'true'])
    def application = 'orders'
    mock.step().call(appName: "${application}", exportSarif: false)
    assert mock.environments[0].DO_DAST_SCAN == 'false'
    assert mock.environments[0].DO_AVIATOR_REMEDIATIONS == 'false'
}

check('nonconflicting inherited scan options are preserved') {
    def mock = new JenkinsMock([SAST_SCAN_EXTRA_OPTS: '--scan-timeout=100 --diagnose'])
    mock.step().call(appName: 'orders', exportSarif: false)
    assert mock.tokens(mock.environments[0].SAST_SCAN_EXTRA_OPTS) ==
        ['--scan-timeout=100', '--diagnose', '--sargs', '-scan-policy security']
}

check('standard wrapper rejects forks before build credential binding') {
    def mock = new JenkinsMock([CHANGE_FORK: 'untrusted/fork'])
    boolean rejected = false
    try {
        mock.wrapper().call(appName: 'orders', credentials: [repository: 'team-repo'])
    } catch (IllegalArgumentException expected) {
        rejected = true
    }
    assert rejected
    assert mock.credentialScopes.empty
    assert mock.commands.empty
}

println "${casesPassed} shared-library checks passed; no external commands, scans, or Git writes executed."