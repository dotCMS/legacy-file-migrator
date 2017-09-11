# Legacy File Migrator for dotCMS 3.3

This OSGi plugin is in charge of migrating the deprecated Legacy Files to the new Files as Content. This migration is required for all customers who will be upgrading their environments to dotCMS 4.x as Legacy Files will not be handled by our code base anymore.



## How to build this plugin

To install it, all you need to do is build the JAR. To do this, just run
*./gradlew clean jar* and Gradle will build a .JAR inside the build/libs directory. Building the first time will take longer as many dependencies will be downloaded.


## Pre-setup

Please follow these steps in order to set up everything you need for the migration process:

1. Before running the migration process, it's very important to know the exact number of Legacy Files that will be transformed into content. In order to get this number, please run the following SQL query in your database:
```sql
SELECT COUNT(DISTINCT(identifier)) FROM file_asset;
```
2. Make sure that you have re-indexed your contents and the Elasticsearch index is working correctly.
3. In case you have a custom page extension (e.g., the "dot" postfix in legacy HTML pages), make sure that the plugin is in place.


## Running the Migrator

In order to install this bundle, copy the bundle jar file inside the Felix OSGI container (dotCMS/felix/load) OR upload the bundle jar file using the dotCMS UI (CMS Admin -> Dynamic Plugins -> Upload Plugin).

The migrator process will start on a separate thread in order to keep the migration from interferring with our OSGi Framework. This way, the purpose of the ``start`` process of the bundle will just be spawning the migration thread. The following considerations must be taken into account before running this plugin:
1. Before starting the migration, all Identifiers whose parent path is null or empty will have the following value: "/".
2. All Legacy Files, all their versions and the edited images generated by the WYSIWYG, Image Editor, or Image Servlet, will be deleted.
3. In case an exception is thrown, please keep track of the ``dotcms.log`` file in order to get more information.
4. All Legacy Files with the "SYSTEM_HOST" Inode will be deleted, NOT MIGRATED, as the new Files as Content MUST live inside a specific Site.
5. Legacy Files that lost or are not associated to a file reference in the file system will be deleted, no exception.

As soon as the plugin is uploaded, the migration task will begin and its progress will be logged via the ``catalina.out`` file (or the appropriate logging file for the specific container or WAS you use). The Legacy Files will be processed per Site, and the time it will take to finish will depend heavily on the number of Legacy Files to transform. Once the migration is over, the following message will be displayed in the logging file:
```
-> Total processed files = <TOTAL-PROCESSED-FILES>
 
All legacy files have been processed. Please undeploy the Legacy File Migrator plugin now.
```
At this moment, please proceed to un-deploy the migrator immediately by removing the bundle jar file from the Felix OSGI container (dotCMS/felix/load) OR un-deploying the bundle using the dotCMS UI (CMS Admin -> Dynamic Plugins -> Undeploy). You can run the following SQL query to make sure that there are no Legacy Files to process (the count should be zero):
```sql
SELECT COUNT(DISTINCT(identifier)) FROM file_asset;
```



________________________________________________________________________________________

# WARNING (!)

* ALWAYS CREATE A DB AND ASSETS BACKUP before running the migrator process in case particular errors arise and it's required to start all over.

* DO NOT FORGET to un-deploy the migrator plugin once the process has finished correctly.

* Make sure you have access to the appropriate logging file in order to keep track of the migration process. The ``dotcms.log`` file will only contain potential errors that might surface during the migration.

* Legacy Files located under the "System Host" will be automatically deleted during the migration without exception as Files as Content CANNOT live under such a Site.

* Legacy Files that lost or are not associated to a file reference in the file system will be deleted, no exception.
