package org.nuxeo.labs.material.compound;

import com.sun.xml.internal.bind.v2.TODO;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.platform.filemanager.api.FileManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

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
            boolean isMainUzippedFolderDoc = false;
            File zipBlobFile = zipBlob.getFile();
            zipFile = new ZipFile(zipBlobFile);

            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                DocumentModel parentFolder = null;

                String entryPath = entry.getName();
                if (shouldIgnoreEntry(entryPath)) {
                    continue;
                }

                // Does this entry contain folders?
                if (containsFolders(entryPath)) {
                    // If so, create them
                    parentFolder = createFolders(entryPath);
                }

                // Then create the file
                File newFile = new File(pathToTempFolder.toString() + File.separator + entryPath);
                if(newFile.mkdirs()) {
                    FileOutputStream fos = new FileOutputStream(newFile);
                    InputStream zipEntryStream = zipFile.getInputStream(entry);
                    int len;
                    byte[] buffer = new byte[4096];
                    while ((len = zipEntryStream.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();

                    // Import
                    FileBlob blob = new FileBlob(newFile);
                    fileManager.createDocumentFromBlob(session, blob, parentFolder.getPathAsString(), true, blob.getFilename());
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

    private boolean containsFolders(String entryPath) {
        // TODO not implemented;
        return false;
    }

    private DocumentModel createFolders(String entryPath) {
        // TODO not implemented;
        // If this is the top level folder, use rootFolderishType
        // Else use childFolderishType
        DocumentModel parentFolder = null;
        return parentFolder;
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
