/*
 * Copyright (c) 2008-2019 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.diedavids.cuba.runtimediagnose.diagnose

import com.haulmont.cuba.core.global.BuildInfo
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import org.springframework.stereotype.Component

import javax.inject.Inject
import java.util.zip.ZipFile

@CompileStatic
@Component(DiagnoseExecutionFactory.NAME)
class DiagnoseExecutionFactoryBean implements DiagnoseExecutionFactory {

    public static final String MANIFEST_FILENAME = 'manifest.json'

    @Inject
    ZipFileHelper zipFileHelper

    @Inject
    BuildInfo buildInfo


    DiagnoseExecution createDiagnoseExecutionFromFile(File file) {
        def diagnoseZipFile = new ZipFile(file)

        def result = new DiagnoseExecution()

        result.manifest = createManifestFromDiagnoseFile(diagnoseZipFile)
        result.diagnoseScript = readDiagnoseScriptFromDiagnoseFile(result, diagnoseZipFile)
        result.executionType = DiagnoseExecutionType.WIZARD

        result

    }

    @Override
    DiagnoseExecution createAdHocDiagnoseExecution(String diagnoseScript, DiagnoseType diagnoseType) {
        new DiagnoseExecution(
            manifest: new DiagnoseManifest(
                diagnoseType: diagnoseType,
                appName: buildInfo.content.appName,
                appVersion: buildInfo.content.version
            ),
            diagnoseScript: diagnoseScript,
            executionType: DiagnoseExecutionType.CONSOLE
        )
    }

    private String readDiagnoseScriptFromDiagnoseFile(DiagnoseExecution diagnoseExecution, ZipFile diagnoseZipFile) {
        zipFileHelper.readFileContentFromArchive(getDiagnoseScriptFilename(diagnoseExecution), diagnoseZipFile)
    }

    private DiagnoseManifest createManifestFromDiagnoseFile(ZipFile diagnoseZipFile) {
        DiagnoseManifest result = null
        def manifestInputStream = zipFileHelper.readFileFromArchive(MANIFEST_FILENAME, diagnoseZipFile)
        if (manifestInputStream) {
            def manifestJson = new JsonSlurper().parse(manifestInputStream)
            result = manifestJson as DiagnoseManifest
        }

        result
    }


    byte[] createExecutionResultFromDiagnoseExecution(DiagnoseExecution diagnoseExecution) {
        zipFileHelper.createZipFileForEntries(diagnoseExecution.executionResultFileMap)
    }

    @Override
    byte[] createDiagnoseRequestFileFromDiagnoseExecution(DiagnoseExecution diagnoseExecution) {
        def files = [
                (getDiagnoseScriptFilename(diagnoseExecution)): diagnoseExecution.diagnoseScript,
                (MANIFEST_FILENAME)                           : JsonOutput.prettyPrint(JsonOutput.toJson(diagnoseExecution.manifest)),
        ]
        zipFileHelper.createZipFileForEntries(files)
    }

    protected String getDiagnoseScriptFilename(DiagnoseExecution diagnoseExecution) {
        "diagnose.${diagnoseExecution.executedScriptFileExtension}"
    }
}
