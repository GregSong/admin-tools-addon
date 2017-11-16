package com.haulmont.addon.admintools.config;

import com.haulmont.addon.admintools.config.type.AutoImportType;
import com.haulmont.cuba.core.config.Config;
import com.haulmont.cuba.core.config.Property;
import com.haulmont.cuba.core.config.Source;
import com.haulmont.cuba.core.config.SourceType;
import com.haulmont.cuba.core.config.type.Factory;
import com.haulmont.cuba.core.config.type.Stringify;

import java.util.Map;

@Source(type = SourceType.DATABASE)
public interface AutoImportConfiguration extends Config {

    @Property("admintools.hashes")
    @Factory(factory = AutoImportType.Factory.class)
    @Stringify(stringify = AutoImportType.Stringify.class)
    Map<String, Map<String, String>> getHashes();

    void setHashes(Map<String, Map<String, String>> hashes);
}
