# About

This plugin provides a filemanager plugin to import Material packages in the Nuxeo Platform

WIP: this plugin relies on a Studio configuration currently, specifically it requires a document type named "Material".

# Build

Assuming maven is correctly setup on your computer:

```
git clone
mvn package
```

# Install

Install the package on your instance.


# Limitations

The class `nuxeo.zip.utils.UnzipToDocuments` is copied from [nuxeo-zip-utils](https://github.com/nuxeo-sandbox/nuxeo-zip-utils). I did not add it as a dependency since it's published in a private repo currently. TODO: add dependency when `nuxeo-zip-utils` is published.

# About Nuxeo

Nuxeo provides a modular, extensible Java-based [open source software platform for enterprise content management](http://www.nuxeo.com/en/products/ep) and packaged applications for [document management](http://www.nuxeo.com/en/products/document-management), [digital asset management](http://www.nuxeo.com/en/products/dam) and [case management](http://www.nuxeo.com/en/products/case-management). Designed by developers for developers, the Nuxeo platform offers a modern architecture, a powerful plug-in model and extensive packaging capabilities for building content applications.

More information at <http://www.nuxeo.com/>
