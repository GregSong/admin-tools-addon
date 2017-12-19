# CUBA Platform Component - Admin Tools

The Admin Tools component is a set of instruments that allows:
* interacting with a database by using JPQL / SQL / Groovy scripts;
* pre-configuring servers and transferring data between them;
* exporting project entities in SQL scripts.

The component comprises the following parts:
* Generator of SQL scripts for project entities;
* Auto Import subsystem;
* JPQL / SQL / Groovy console.

## Installation

The process of installation is described below.

1. Add the following maven repository `https://repo.cuba-platform.com/content/repositories/premium-snapshots`
to the build.gradle of your CUBA application:
    ```groovy
    buildscript {
        
        //...
        
        repositories {
        
            // ...
        
            maven {
                url  "https://repo.cuba-platform.com/content/repositories/premium-snapshots"
            }
        }
        
        // ...
    }
    ```

2. Select a version of the add-on which is compatible with the platform version used in your project:
   
   | Platform Version | Add-on Version |
   | ---------------- | -------------- |
   | 6.7.x            | 0.1-SNAPSHOT   |
      
   Add custom application component to your project:
   
   * Artifact group: `com.haulmont.addon.admintools`
   * Artifact name: `cuba-at-global`
   * Version: *add-on version*
   
**Note:** for Auto Import an additional configuration is required (see Create an auto-import configuration file)
  
## Generator of SQL scripts for project entities

This part of Admin Tools allows generating SQL scripts for selected entities of a project.

// add image

JPQL requests are used for entity selection. The user has to specify a metaclass, view and type
of the script to be generated (insert, update, insert update). Selecting the metaclass automatically
generates a JPQL request:

```sql
select e from example$Entity e
```

// add image

After that, SQL scripts of the specified type are generated for the found entities.

If there are no results found, then the following notification appears: 'No data found'.

## Auto Import

AutoImport can be used for server preconfiguration and transferring data among servers. The process
is launched automatically during the server start/restart. 

For importing data to the server, specify the path to a zip-archive in the configuration file. If the
archive with the same name has already been processed, then it is skipped.

The component comprises ready-made solutions for importing security roles and access groups. The 'Export as ZIP'
button allows generating archives containing the required data about security roles or access groups. The user
can export project entities to a zip-archive using Entity Inspector. 

There is a class-processor responsible for file processing that can be implemented as a bean or
a simple java-class. If necessary, the user can specify a custom implementation of the processor
for any entity within a project by applying the AutoImportProcessor interface.

### Creating a custom import processor

To create a custom processor, the following steps have to be followed:

1. Create a class that implements the AutoImportProcessor interface
   ```java
   @Component("autoimport_ReportsAutoImportProcessor")
   public class ReportsAutoImportProcessor implements AutoImportProcessor {
    
       @Inject
       protected ReportService reportService;
       @Inject
       protected Resources resources;
    
       @Override
       public void processFile(String filePath) {
           InputStream stream = resources.getResourceAsStream(filePath);
           processFile(stream);
       }
    
       @Override
       public void processFile(InputStream inputStream) {
           try {
               byte[] fileBytes = IOUtils.toByteArray(inputStream);
               reportService.importReports(fileBytes);
           } catch (IOException | RuntimeException e) {
               throw new AutoImportException("Unable to import Reports file", e);
           }
       }
   }
   ```
   
2. If the processor is implemented as a java bean, then specify the component name and a path
to the required zip-archive in the configuration file. If the processor is implemented as a class,
then provide a path to the class
   ```xml
   <?xml version="1.0" encoding="UTF-8" standalone="no"?>
   <auto-import>
       ...
    
       <auto-import-file path="com/company/demoforadmintoolscomponent/Reports.zip" bean="autoimport_ReportsAutoImportProcessor"/>
       ...
   </auto-import>
   ```
   
### Create an auto-import configuration file

1. Configuration file example:
   ```xml
   <?xml version="1.0" encoding="UTF-8" standalone="no"?>
   <auto-import>
       <!--default processors-->
       <auto-import-file path="com/company/demoforadmintoolscomponent/Roles.zip" bean="autoimport_RolesAutoImportProcessor"/>
       <auto-import-file path="com/company/demoforadmintoolscomponent/Groups.zip" bean="autoimport_GroupsAutoImportProcessor"/>
        
       <!--custom processor-->
       <auto-import-file path="com/company/demoforadmintoolscomponent/Groups.zip" class="com.company.demoforadmintoolscomponent.processors.SampleAutoImportProcessor"/>
   </auto-import>
   ```
   
   Where path is a path to the zip-archive, bean/class — a processor. Bean = [bean name], class [class path].
   
2. Add the `admin.autoImportConfig` property to app.properties including the configuration file path.

### Additional information. Logging

See logging information in `app.log` file.

#### Successful import

```
com.haulmont.addon.admintools.listeners.AutoImportListener - Importing file com/company/autoimporttest/Roles.zip by bean autoimport_RolesAutoImportProcessor
...
com.haulmont.addon.admintools.processors.RolesAutoImportProcessor - Successful importing file com/company/autoimporttest/Roles.zip
```

#### Incorrect name of a processor

```
com.haulmont.addon.admintools.listeners.AutoImportListener - Importing file com/company/demoforadmintoolscomponent/Groups.zip by bean autoimport_InvalidAutoImportProcessor
...
com.haulmont.addon.admintools.listeners.AutoImportListener - org.springframework.beans.factory.NoSuchBeanDefinitionException: No bean named 'autoimport_InvalidAutoImportProcessor' available
```

```
com.haulmont.addon.admintools.listeners.AutoImportListener - Importing file com/company/demoforadmintoolscomponent/Groups.zip by class com.example.InvalidAutoImportProcessor ... com.haulmont.addon.admintools.listeners.AutoImportListener - java.lang.ClassNotFoundException: com.example.InvalidAutoImportProcessor
```

#### Uploaded archive is not found

```
com.haulmont.addon.admintools.listeners.AutoImportListener - Importing file com/example/invalid.zip by bean autoimport_ReportsAutoImportProcessor
com.haulmont.addon.admintools.processors.ReportsAutoImportProcessor - File com/example/invalid.zip not found.
```

## JPQL Console

User can find documentation in appropriate [documentation](https://github.com/mariodavid/cuba-component-runtime-diagnose/blob/master/README.md)