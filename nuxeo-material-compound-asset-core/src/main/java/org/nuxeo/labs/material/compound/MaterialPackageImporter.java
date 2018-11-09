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
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.platform.filemanager.api.FileManager;
import org.nuxeo.ecm.platform.filemanager.service.extension.AbstractFileImporter;
import org.nuxeo.ecm.platform.types.TypeManager;
import org.nuxeo.runtime.api.Framework;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Simple Plugin that imports Material Zip package into Nuxeo.
 */
public class MaterialPackageImporter extends AbstractFileImporter {

    public static final String U3M_EXT = ".u3m";
    public static final String XTEX_EXT = ".xtex";

    public static final String COMPOUND_FACET = "CompoundDocument";
    public static final String COMPONENT_FACET = "ComponentDocument";

    public static final String COMPONENTS_XPATH = "compound:docs";
    public static final String ARCHIVE_XPATH = "compound:archive";
    public static final String RENDITIONS_XPATH = "compound:renditions";

    public static final String COMPOUNDS_XPATH = "componentdoc:usedin";

    public static final String CONTAINER_TYPE = "Material";


    private static final Log log = LogFactory.getLog(MaterialPackageImporter.class);

    public boolean isValid(ZipFile zip) {
        // Check if this is a Material package.
        // This means there's either a u3m or xtex file in the root.
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.getName().toLowerCase().endsWith(U3M_EXT) || entry.getName().toLowerCase().endsWith(XTEX_EXT)) {
                return true;
            }
        }
        return false;
    }

    public String getFilename(String path) {
        return path.split("/")[path.split("/").length - 1];
    }


    public DocumentModel unzip(CoreSession session, DocumentModel materialDoc, ZipFile zipFile, Blob blob)
            throws IOException {
        FileManager fileManager = Framework.getService(FileManager.class);
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        ZipEntry materialMetadataFile = null;
        List<Blob> renditions = new ArrayList<>();

        DocumentModelList components = new DocumentModelListImpl();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String fileName = entry.getName();
            if (fileName.startsWith("__MACOSX/")
                 || fileName.startsWith(".")
                 || fileName.contentEquals("../") //Avoid hacks trying to access a directory outside the current one
                 || fileName.endsWith(".DS_Store")
                 || fileName.toLowerCase().endsWith(".idml")) {
             continue;
            }

            if (entry.isDirectory()) {
             continue;
            }

            if(fileName.startsWith("textures")){
                // Create textures folder if it doesn't already exist.
                // Hint: with the Studio project the textures folder is automatically created.
                continue;
            }

            if (fileName.toLowerCase().endsWith(U3M_EXT)) {
                materialMetadataFile = entry;
                continue;
            }

            if (fileName.toLowerCase().endsWith(XTEX_EXT)) {
                Blob fileBlob = new FileBlob(zipFile.getInputStream(entry),"application/pdf");
                fileBlob.setFilename(getFilename(fileName));
                renditions.add(fileBlob);
                continue;
            }

            String name = getFilename(fileName);

            //check if duplicates
            String query = String.format("Select * FROM Document Where dc:title='%s' AND ecm:isCheckedInVersion = 0 AND ecm:isProxy = 0",name);
            DocumentModelList duplicates = session.query(query);
            if (duplicates.size()>0) {
                components.add(duplicates.get(0));
            } else {
                Blob fileBlob = new FileBlob(zipFile.getInputStream(entry));
                fileBlob.setFilename(name);

                DocumentModel element = fileManager.createDocumentFromBlob(
                        session, fileBlob, materialDoc.getPathAsString(), true, fileBlob.getFilename());
                components.add(element);
            }
        }

        DocumentModel materialMetadataDoc;

        Blob materialBlob = new FileBlob(zipFile.getInputStream(materialMetadataFile));
        materialBlob.setFilename(getFilename(materialMetadataFile.getName()));
        materialMetadataDoc = session.createDocumentModel(
                materialDoc.getPathAsString(),blob.getFilename(),"File");
        materialMetadataDoc.setPropertyValue("file:content", (Serializable) materialBlob);
        materialMetadataDoc.setPropertyValue("dc:title",materialBlob.getFilename());
        materialMetadataDoc = session.createDocument(materialMetadataDoc);


        materialDoc.addFacet(COMPOUND_FACET);

        List<String> componentIds = new ArrayList<>();
        for(DocumentModel component: components) {
            componentIds.add(component.getId());
        }

        materialDoc.setPropertyValue(COMPONENTS_XPATH, (Serializable) componentIds);
        materialDoc.setPropertyValue(ARCHIVE_XPATH, (Serializable) blob);
        materialDoc.setPropertyValue(RENDITIONS_XPATH, (Serializable) renditions);
        session.saveDocument(materialDoc);

        //update components
        for(DocumentModel component:components) {
             if (!component.hasFacet(COMPONENT_FACET)) {
                 component.addFacet(COMPONENT_FACET);
            }
            String compounds[] = (String[]) component.getPropertyValue(COMPOUNDS_XPATH);
            List<String> compoundList = compounds != null ? new ArrayList<>(Arrays.asList(compounds)) : new ArrayList<>();
            if (!compoundList.contains(materialDoc.getId())) {
                compoundList.add(materialDoc.getId());
                component.setPropertyValue(COMPOUNDS_XPATH, (Serializable) compoundList);
            }
        }

        session.saveDocuments(components.toArray(new DocumentModel[]{}));
        return materialDoc;

    }

    public DocumentModel create(CoreSession session, Blob content, String path, boolean overwrite,
                                String filename, TypeManager typeService) throws IOException {
        try (CloseableFile source = content.getCloseableFile()) {
            try (ZipFile zip = new ZipFile(source.getFile())) {
                if (!isValid(zip)) {
                    return null;
                }
                String name = filename.substring(0, filename.length() - 4);
                DocumentModel material = session.createDocumentModel(path,name,CONTAINER_TYPE);
                material.setPropertyValue("dc:title",name);
                material = session.createDocument(material);
                return unzip(session,material,zip,content);
            }
        }
    }
}