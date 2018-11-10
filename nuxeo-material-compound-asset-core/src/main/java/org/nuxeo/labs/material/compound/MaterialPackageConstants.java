package org.nuxeo.labs.material.compound;

import java.util.ArrayList;
import java.util.List;

public class MaterialPackageConstants {
    public static final String EXTENSION_U3M = "u3m";
    public static final String EXTENSION_XTEX = "xtex";

    public static final List SUPPORTED_EXTENSIONS = new ArrayList<String>() {
        {
            add(EXTENSION_U3M);
            add(EXTENSION_XTEX);
        }
    };

    public static final String COMPOUND_FACET = "CompoundDocument";
    public static final String COMPONENT_FACET = "ComponentDocument";

    public static final String COMPONENTS_XPATH = "compound:docs";
    public static final String ARCHIVE_XPATH = "compound:archive";
    public static final String RENDITIONS_XPATH = "compound:renditions";

    public static final String COMPOUNDS_XPATH = "componentdoc:usedin";

    public static final String ROOT_FOLDERISH_TYPE = "Material";
    public static final String CHILD_FOLDERISH_TYPE = "Folder";

    private MaterialPackageConstants(){

    }
}
