/*
 * (C) Copyright 2017 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Michael Vachette
 */

package org.nuxeo.labs.material.compound;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.filemanager.api.FileManager;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import javax.inject.Inject;
import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.nuxeo.labs.material.compound.MaterialPackageImporter.COMPONENTS_XPATH;
import static org.nuxeo.labs.material.compound.MaterialPackageImporter.COMPOUNDS_XPATH;

@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy({
    "nuxeo-material-compound-asset-core",
    "org.nuxeo.ecm.platform.filemanager.core",
    "org.nuxeo.ecm.platform.types.core"})
public class TestMaterialPackageImporter {

    @Inject
    protected CoreSession coreSession;

    @Inject
    protected FileManager fileManager;

    @Test
    public void testImportViaFileManager() throws Exception {
        File file = new File(getClass().getResource("/files/sample.zip").getPath());
        Blob blob = new FileBlob(file);
        DocumentModel root = coreSession.getRootDocument();
        DocumentModel compound = fileManager.createDocumentFromBlob(coreSession,blob,root.getPathAsString(),true,file.getName());
        Assert.assertNotNull(compound);

        Assert.assertEquals("sample.u3m",compound.getPropertyValue("dc:title"));

        String elements[] = (String[]) compound.getPropertyValue(COMPONENTS_XPATH);
        Assert.assertEquals(3,elements.length);

        List<Blob> renditions= (List<Blob>) compound.getPropertyValue("compound:renditions");
        Assert.assertEquals(1,renditions.size());

        DocumentModel folder = coreSession.getDocument(compound.getParentRef());
        Assert.assertEquals("sample",folder.getPropertyValue("dc:title"));

        DocumentModelList children = coreSession.getChildren(compound.getParentRef());
        Assert.assertEquals(4,children.totalSize());

        for(DocumentModel component : children) {
            if (!component.getId().equals(compound.getId())) {
                String compounds[] = (String[]) component.getPropertyValue(COMPOUNDS_XPATH);
                Assert.assertNotNull(compounds);
                List<String> compoundList = Arrays.asList(compounds);
                Assert.assertTrue(compoundList.contains(compound.getId()));
            }
        }

    }
}
