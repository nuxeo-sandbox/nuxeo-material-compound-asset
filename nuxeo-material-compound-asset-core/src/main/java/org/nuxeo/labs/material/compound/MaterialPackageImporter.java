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
 *
 */

package org.nuxeo.labs.material.compound;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CloseableFile;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.platform.filemanager.api.FileImporterContext;
import org.nuxeo.ecm.platform.filemanager.service.extension.AbstractFileImporter;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.services.config.ConfigurationService;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.nuxeo.labs.material.compound.MaterialPackageConstants.ARCHIVE_XPATH;
import static org.nuxeo.labs.material.compound.MaterialPackageConstants.CHILD_FOLDERISH_TYPE;
import static org.nuxeo.labs.material.compound.MaterialPackageConstants.COMPONENTS_XPATH;
import static org.nuxeo.labs.material.compound.MaterialPackageConstants.COMPONENT_FACET;
import static org.nuxeo.labs.material.compound.MaterialPackageConstants.COMPOUNDS_XPATH;
import static org.nuxeo.labs.material.compound.MaterialPackageConstants.COMPOUND_FACET;
import static org.nuxeo.labs.material.compound.MaterialPackageConstants.EXTENSION_CONF_PROPERTY;

/**
 * Imports Material Zip package into Nuxeo.
 *
 * @since 10.2
 */
public class MaterialPackageImporter extends AbstractFileImporter {


    private static final Log log = LogFactory.getLog(MaterialPackageImporter.class);

    /**
     * Check to see if this is a Materials zip.
     *
     * @param zip
     * @return
     */
    public boolean isValid(ZipFile zip) {
        ConfigurationService configurationService = Framework.getService(ConfigurationService.class);
        List<String> extensions = Arrays.asList(configurationService.getProperty(EXTENSION_CONF_PROPERTY).split(";"));
        // Check if this is a Material package.
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (extensions.contains(FileUtils.getFileExtension(entry.getName().toLowerCase()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Recurse the package and add any assets as components to the compound document.
     *
     * @param session
     * @param materialDocId
     * @param components
     * @param root
     */
    private void updateComponents(CoreSession session, String materialDocId, DocumentModelList components, DocumentModel root) {
        if (root.hasFacet("Folderish")) {
            DocumentModelList children = session.getChildren(root.getRef());
            for (DocumentModel child : children) {
                updateComponents(session, materialDocId, components, child);
            }
        } else {
            updateComponent(materialDocId, components, root);
        }
    }

    /**
     * Update component metadata.
     *
     * @param materialDocId
     * @param components
     * @param component
     */
    private void updateComponent(String materialDocId, DocumentModelList components, DocumentModel component) {
        // Add the ComponentDocument facet to each component
        if (!component.hasFacet(COMPONENT_FACET)) {
            component.addFacet(COMPONENT_FACET);
        }

        // Add the componentdoc:usedin value to each component
        String usedIn[] = (String[]) component.getPropertyValue(COMPOUNDS_XPATH);
        List<String> usedInList = usedIn != null ? new ArrayList<>(Arrays.asList(usedIn)) : new ArrayList<>();
        if (!usedInList.contains(materialDocId)) {
            usedInList.add(materialDocId);
            component.setPropertyValue(COMPOUNDS_XPATH, (Serializable) usedInList);
        }
        components.add(component);
    }

    /**
     * After the zip is imported, this method "massages" the documents.
     *
     * @param session
     * @param materialDoc
     * @param blob
     * @return
     */
    public DocumentModel process(CoreSession session, DocumentModel materialDoc, Blob blob) {

        // Add the CompoundDocument facet to the Material
        materialDoc.addFacet(COMPOUND_FACET);

        // Save the zip BLOB so we have a copy
        materialDoc.setPropertyValue(ARCHIVE_XPATH, (Serializable) blob);

        // TODO: (optional) specify which files are "renditions" of the Material

        // Add anything that's not folderish as a component
        // TODO: (optional) Locate the contents of the textures folder, add them as components instead of adding everything.
        DocumentModelList components = new DocumentModelListImpl();
        updateComponents(session, materialDoc.getId(), components, materialDoc);

        List<String> componentIds = new ArrayList<>();
        for (DocumentModel component : components) {
            componentIds.add(component.getId());
        }

        materialDoc.setPropertyValue(COMPONENTS_XPATH, (Serializable) componentIds);

        session.saveDocument(materialDoc);
        session.saveDocuments(components.toArray(new DocumentModel[]{}));

        return materialDoc;

    }

    @Override
    public DocumentModel createOrUpdate(FileImporterContext context) throws IOException {
        try (CloseableFile source = context.getBlob().getCloseableFile()) {
            try (ZipFile zip = new ZipFile(source.getFile())) {
                if (!isValid(zip)) {
                    return null;
                }

                PathRef targetFolderishPathRef = new PathRef(context.getParentPath());
                DocumentModel targetFolderishDoc = context.getSession().getDocument(targetFolderishPathRef);

                UnzipToDocuments unzipToDocs = new UnzipToDocuments(targetFolderishDoc, context.getBlob(), getDocType(), CHILD_FOLDERISH_TYPE);

                // First extract the zip file, creating Nuxeo documents...
                DocumentModel materialDoc = unzipToDocs.run();

                // Then process those documents (add facets, copy data, etc.)
                materialDoc = this.process(context.getSession(), materialDoc, context.getBlob());

                return materialDoc;

            }
        }
    }
}