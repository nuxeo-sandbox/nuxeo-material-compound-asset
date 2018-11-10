package org.nuxeo.labs.material.compound;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.platform.filemanager.api.FileManager;
import org.nuxeo.runtime.api.Framework;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @since 10.2
 */
public class UnzipToDocuments {

    protected static Log logger = LogFactory.getLog(UnzipToDocuments.class);

    private static String DEFAULT_FOLDERISH_TYPE = "Folder";

    protected DocumentModel parentDoc;

    protected Blob zipBlob;

    protected String childFolderishType;

    protected String rootFolderishType;

    private DocumentModel rootDocument;

    public UnzipToDocuments(DocumentModel parentDoc, Blob zipBlob, String rootFolderishType, String childFolderishType) {
        this.parentDoc = parentDoc;
        this.zipBlob = zipBlob;
        this.childFolderishType = StringUtils.isBlank(childFolderishType) ? DEFAULT_FOLDERISH_TYPE : childFolderishType;
        this.rootFolderishType = StringUtils.isBlank(rootFolderishType) ? DEFAULT_FOLDERISH_TYPE : rootFolderishType;
    }

    public UnzipToDocuments(DocumentModel parentDoc, Blob zipBlob) {
        this(parentDoc, zipBlob, DEFAULT_FOLDERISH_TYPE, DEFAULT_FOLDERISH_TYPE);
    }

    /**
     * Creates Documents, in a hierarchical way, copying the content stored in the zip file.
     *
     * @return the main document containing the unzipped data
     * @since 10.2
     */
    public DocumentModel run() throws NuxeoException {

        File tempFolderFile = null;
        ZipFile zipFile = null;
        DocumentModel rootDocument = null;

        CoreSession session = parentDoc.getCoreSession();
        FileManager fileManager = Framework.getService(FileManager.class);

        try {

            Path pathToTempFolder = Framework.createTempDirectory(rootFolderishType + "-Unzip");
            tempFolderFile = new File(pathToTempFolder.toString());
            File zipBlobFile = zipBlob.getFile();
            zipFile = new ZipFile(zipBlobFile);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {

                DocumentModel parentFolderDoc;
                ZipEntry entry = entries.nextElement();
                String entryPath = entry.getName();

                if (shouldIgnoreEntry(entryPath)) {
                    continue;
                }

                Boolean isDirectory = entry.isDirectory();

                // Create folderish documents as needed and get the parent for the File
                parentFolderDoc = handleFolders(session, entryPath, isDirectory);

                // I only need to unzip the files, not the folders, folderish docs are created by handleFolders()
                if (!isDirectory) {
                    String systemPath = pathToTempFolder.toString() + File.separator + entryPath;
                    File newFile = new File(systemPath);
                    if (!newFile.getParentFile().exists()) {
                        newFile.getParentFile().mkdirs();
                    }
                    FileOutputStream fos = new FileOutputStream(newFile);
                    InputStream zipEntryStream = zipFile.getInputStream(entry);
                    int len;
                    byte[] buffer = new byte[4096];
                    while ((len = zipEntryStream.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();

                    if (parentFolderDoc != null) {
                        // Import
                        FileBlob blob = new FileBlob(newFile);
                        fileManager.createDocumentFromBlob(session, blob, parentFolderDoc.getPathAsString(), true, blob.getFilename());
                    }
                }

            }

        } catch (IOException e) {
            throw new NuxeoException("Error while unzipping and creating Documents", e);
        } finally {
            org.apache.commons.io.FileUtils.deleteQuietly(tempFolderFile);
            try {
                zipFile.close();
            } catch (IOException e) {
                // Ignore;
            }
        }

        return rootDocument;
    }

    /**
     * Given a path from the zip file, make sure there are folderish documents in Nuxeo for each folder.
     *
     * @param session
     * @param entryPath
     * @param isDirectory
     * @return
     */
    private DocumentModel handleFolders(CoreSession session, String entryPath, Boolean isDirectory) {
        DocumentModel parentFolderForNewEntry = null;

        String repoPathToCurrentDoc = parentDoc.getPathAsString();
        String repoPathToCurrentDocParent = parentDoc.getPathAsString();
        String[] pathParts = entryPath.split("/");

        int limit;
        if (isDirectory)
            limit = pathParts.length;
        else
            limit = pathParts.length - 1;

        for (int i = 0; i < limit; i++) {

            String docType;

            if (i == 0) {
                docType = rootFolderishType;
            } else {
                docType = childFolderishType;
            }

            repoPathToCurrentDoc += "/" + pathParts[i];

            // Test to see if the document already exists...
            PathRef repoPathRefToCurrentDoc = new PathRef(repoPathToCurrentDoc);
            if (!session.exists(repoPathRefToCurrentDoc)) {
                parentFolderForNewEntry = session.createDocument(session.createDocumentModel(repoPathToCurrentDocParent, pathParts[i], docType));

            } else {
                parentFolderForNewEntry = session.getDocument(repoPathRefToCurrentDoc);
            }

            if (i == 0 && rootDocument == null)
                rootDocument = parentFolderForNewEntry;
        }

        repoPathToCurrentDocParent += "/" + pathParts[i];
    }

        return parentFolderForNewEntry;
}

    private boolean shouldIgnoreEntry(String fileName) {
        if (fileName.startsWith("__MACOSX/")
            || fileName.startsWith(".")
            || fileName.contentEquals("../")
            || fileName.endsWith(".DS_Store")) {
            return true;
        }

        return false;
    }


}
