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

        DocumentModel material = fileManager.createDocumentFromBlob(coreSession,blob,root.getPathAsString(),true,file.getName());

        Assert.assertNotNull(material);

        Assert.assertEquals("sample",material.getPropertyValue("dc:title"));

        // There are two textures included in the sample.
        String textures[] = (String[]) material.getPropertyValue(COMPONENTS_XPATH);
        Assert.assertEquals(2,textures.length);

        // There's one preview image in the sample.
        List<Blob> renditions= (List<Blob>) material.getPropertyValue("compound:renditions");
        Assert.assertEquals(1,renditions.size());

        // Get the u3m file, check the title
        DocumentModel u3mFile = coreSession.getDocument(coreSession.getChild(material.getRef(),"sample.u3m").getRef());
        Assert.assertEquals("sample.u3m",u3mFile.getPropertyValue("dc:title"));

        // There's 3 children in the sample
        DocumentModelList children = coreSession.getChildren(material.getRef());
        Assert.assertEquals(3,children.totalSize());
    }
}
