# Legacy File Migrator for dotCMS 3.7.2

** This version of gradle may not be compatible with the latest JDK, FYSA. 

This OSGi plugin is in charge of migrating the deprecated Legacy Files to the new Files as Content. This migration is required for all customers needing to upgrade their environments from dotCMS 3.x to dotCMS 4.x and above as Legacy Files will not be handled at all by our code base anymore.

* [How to Build the Dynamic Plugin (Legacy File Migrator)](#build-dp)
* [How to Build the Static Plugin (Overwritten Classes)](#build-sp)
* [Pre-setup](#presetup)
* [Configuring your Dedicated Log4J2 Logger (optional)](#logging)
* [Running the Legacy File Migrator](#running)
* [Fixing Problems and Re-running the Migrator](#fixingProblems)
* [After a Successful Migration](#success)
* [WARNINGS (!)](#warnings)



## <a id="build-dp">How to Build the Dynamic Plugin (Legacy File Migrator)</a>

To install it, all you need to do is build the JAR file that will be uploaded as a Dynamic Plugin. To do this, just go to the plugin's root folder and run `./gradlew clean jar`. Gradle will build a .JAR file inside the `${PLUGIN_FOLDER}/build/libs/` directory. Building the plugin for the first time will take longer as many dependencies need to be downloaded in order to compile it.


## <a id="build-sp">How to Build the Static Plugin (Overwritten Classes)</a>

The required Static Plugin is located in the `${PLUGIN_FOLDER}/legacy-file-migration-overwrite/` folder. To install it, you need to follow these steps:

1. First of all, take a look at the Java classes located under the `${PLUGIN_FOLDER}/legacy-file-migration-overwrite/src/` folder, and make sure that **those classes are NOT being already overwritten by any other Static or Dynamic Plugin**. If they are, you'll need to either un-deploy those other plugins, or merge their changes with the ones in the `legacy-file-migration-overwrite` plugin.
2. Stop your dotCMS instance.
3. Run the `${DOTCMS_HOME}/bin/undeploy-plugins.sh` script to un-deploy all existing plugins.
4. Copy the `${PLUGIN_FOLDER}/legacy-file-migration-overwrite/` plugin and paste it inside the `${DOTCMS_HOME}/plugins/` folder.
5. Now, run the `${DOTCMS_HOME}/bin/deploy-plugins.sh` script to re-deploy all the plugins.
6. Start up dotCMS.


## <a id="presetup">Pre-setup</a>

Please follow these steps in order to set up everything you need for the migration process (steps might change depending on the specifics of the data set being used):

1. The first step is to run the pre-execution script that applies for the database your dotCMS is running on (e.g, ``sql_scripts/pre-postgres.sql``). This script is going to create a special table to store the references to the Legacy Files, the function or stored procedure that reads the entries in it, and other additional queries.
2. Before starting off, and making sure that the required Static Plugin was deployed without errors, it's very important to know the exact number of Legacy Files that will be transformed into Contentlets so that you can keep a record of them. In order to get this number, please run the following SQL query in your database:
```sql
SELECT COUNT(DISTINCT(identifier)) FROM file_asset;
```
3. **IMPORTANT:** In case you have a custom page extension (e.g., the "dot" postfix used in HTML Pages), make sure that such a plugin is in place.
4. It's a good idea to run the Fix Assets Inconsistencies tool before running the migrator. Inconsistent data can interfere with the process and increase the troubleshooting times. Remember to ALWAYS make a database and assets back-up before doing that. 
5. As a health check, make sure that you have successfully re-indexed 100% of your contents and the ElasticSearch index is working correctly (green status). If you see records that failed to index, fix them or delete them before moving on.
6. It's always a very good idea -- if possible -- to **go through the list of Sites in your instance which can be deleted if they're not used anymore**. Sites that have been in the **Stopped** status for a long time might interfere with the migration process and can unexpected cause errors during the migration. Processing files that belong to Sites that are not being used will just delay both the migration and troubleshoot phases.
7. For testing purposes, after the migration process has run, you can have a list of Legacy Files that you'd like to go through and verify that they were correctly migrated to Files as Content. This will depend on how deep your test cases are, but having a list of them or checking them out through the HTML Pages that use them can be very useful.
8. Flush ALL of your cache regions and Menu Cache right before starting the migration of the Legacy Files (.i.e, before uploading the Dynamic Plugin).
9. The migration process will print a high amount of information in the log file. So, it's always a good idea to disable/stop as many logging-intensive processes as possible, such as: Content editing, Push Publishing, Link Checker Job, and any other custom Quartz Jobs or plugins that might generate unnecessary noise in the log.
10. There a are few configuration properties located inside the `${PLUGIN_FOLDER}/src/main/resources/plugin.properties` file that you need to review or change which allow you to have more control on the behavior of the migration process:

Property | Description
--------| -----------
*legacyfile.migrator.loadquery* | The SQL query that will return the Legacy Files that will be migrated to Files as Content. Similar to the Reindex Process approach, this is likely to be the call to a Stored Procedure or Function that returns a batch of a specific size to the code in the plugin. The SQL code to call it can be different between databases.
*legacyfile.migrator.batchsize* | The number of records that will be returned by the previous SQL query. A brand new DB transaction will be created for each batch.
*legacyfile.migrator.status* | The status code assigned to a Legacy File when it's being processed: 0 (not processed yet), 1 (starting migration process), 2 (migrated successfully), 3 (failed to migrate). This property used to fetch records whose status is lower or equal than the value of this property. 
*legacyfile.migrator.loggingfrequency* | The number of logging lines that will be sent to the log file at once. Instead of logging a new entry for every migrated file, this property queues every logging line up to the specified "loggingfrequency" value, and send them to the log file. This helps minimize the constant I/O output.


## <a id="logging">Configuring your Dedicated Log4J2 Logger (optional)</a>

By default, the plugin will log all of its progress and potential errors to the default dotCMS logging file: The ``dotcms.log``. However, you can make the plugin log information to a different file. All you need to do is compare the `${PLUGIN_FOLDER}/log4j2-default-config.xml` file in this plugin with the existing [log4j2.xml](https://github.com/dotCMS/core/blob/3.7.1/dotCMS/WEB-INF/log4j/log4j2.xml) file in the dotCMS distro, and apply the specific changes to your current Log4J2 configuration. These custom changes include:
* Two properties.
* One async appender.
* Only rolling file configuration.
* One logger.


## <a id="running">Running the Legacy File Migrator</a>

The migrator process will start as soon as the Dynamic Plugin is uploaded. It runs on a separate thread in order to keep the migration from interfering with the OSGi Framework process and other Dynamic Plugins. This way, the purpose of the ``start`` phase in the bundle is just spawning the migration thread. It's also worth noting that the plugin **can be deployed in every single instance of a dotCMS cluster**. This improves the performance by making it scalable, which means all nodes can help process different sets of Legacy Files at once (see the *legacyfile.migrator.batchsize* property). This is the same approach used by the dotCMS Reindex Process in which all nodes in a cluster assist in the creation of a new Content Index. Remember, after making all the required configuration changes in the `${PLUGIN_FOLDER}/src/main/resources/plugin.properties` file, all you need to do is run the ``./gradlew clean jar`` command inside the plugin's root folder, and you're good to go.

In order to install this bundle, copy the bundle JAR file inside the Felix OSGi container folder: `${DOTCMS_HOME}/dotserver/tomcat-8.0.18/webapps/ROOT/WEB-INF/felix/load/` (if using SSH) or upload the bundle .JAR file using the dotCMS UI (*CMS Admin -> Dynamic Plugins -> Upload Plugin*) in as many dotCMS nodes as you deem necessary. Make sure that all of them point to the same assets folder and DB. Right after the deployment, you'll see information such as the following in the log file:
```
[16/10/18 15:41:21:360 EDT]  INFO migrator.LegacyFilesMigrator:  
 
=======================================================================
===== Initializing conversion of Legacy Files to Files as Content =====
=======================================================================

[16/10/18 15:41:21:391 EDT]  INFO migrator.LegacyFilesMigrator: ---> Number of Legacy Files left to migrate: 607
[16/10/18 15:41:21:391 EDT]  INFO migrator.LegacyFilesMigrator: ---> Batch size: 50
[16/10/18 15:41:21:391 EDT]  INFO migrator.LegacyFilesMigrator: ---> Instance ID: 86579328-d970-4418-a163-fc8d404d476e
[16/10/18 15:41:21:391 EDT]  INFO migrator.LegacyFilesMigrator: ---> Start time: 2018-10-16 15:41:21.390
```

The initial logging provides useful information that allows you to keep track of the migration process:

* The number of Legacy Files in the dotCMS instance that can migrated by the plugin. This is simply a reference parameter.
* The size of the batch that contains the references to the Legacy Files. After all the elements in a batch are migrated, the DB transaction will be committed.
* The ID of the dotCMS instance where the plugin was deployed. This is useful in order to determine what records in the temporary table of the Migrator Plugin are being or have been processed by this instance.
* The start date and time of the migration. 

The following considerations must be taken into account before running this plugin:

1. Before performing the actual migration, all Identifiers whose parent path is null or empty will have the following value: "/".
2. The following Legacy Files **will be deleted without exception, NOT MIGRATED**:
2.1 Files pointing to the "SYSTEM_HOST" Inode, as the new Files as Content MUST live inside a specific valid Site.
2.2 Files pointing to a Site that is not valid, e.g., pointing to Content Types that are not of type `Host`.
2.3 Files with an invalid file name.
2.4 When one or more folders that make up the file's parent path are missing. 
2.5 Files that are not associated to a valid file reference (missing or empty file) in the file system.
3. In order to improve the performance of the migration process:
3.1 The physical file associated to a Legacy File **WILL NOT BE DELETED by the migration process**. The Engineering Team will provide a special script that will delete them.
3.2 The deletion of Legacy Files has been updated to ONLY delete the Identifier and either the Working or Live Inode. These two pieces of information are re-used for building the new File as Content. All the remaining data will be manually deleted later on, or deleted as part of other Upgrade Tasks when moving to a dotCMS 4.x version or above.
4. Please try to keep track of the log file in order to get information about any potential error or Java exception when migrating a Legacy File as soon as possible.
5. For performance and stability reasons, the Files as Content that replace every Legacy File ARE NOT indexed after being created. Remember to reindex the `File Asset` Content Type after a successful migration.

Once the migration is over, the following message will be displayed in the log file (this is example data):
```
[16/10/18 15:42:54:788 EDT]  INFO migrator.LegacyFilesMigrator: ---> End time: 2018-10-16 15:42:54.788

[16/10/18 15:42:54:788 EDT]  INFO migrator.LegacyFilesMigrator: ---> Total time: 0 Days 0 Hours 1 Minutes 33 Seconds

[16/10/18 15:42:54:788 EDT]  INFO migrator.LegacyFilesMigrator:  

-> Total processed files = 607
 
All legacy files have been processed. Please un-deploy the Legacy File Migrator Plugin now.
```

The finalization summary provides the following:

* The end date and time of the migration process.
* An easier display of the days/hours/minutes/seconds that the migration process required to transform/delete the Legacy Files.
* The number of Legacy Files that the current dotCMS instance processed correctly.

In this example, "607" Legacy Files were detected and processed correctly. Different situations can make the migration process fail, some of them might be specific to the Legacy File being processed, database timeouts, unexpected system restarts, and so on. From a DB standpoint, keep in mind that the status of a Legacy File falls into 4 categories:
* 0 = not being processed by any dotCMS instance yet.
* 1 = going through the migration process.
* 2 = migrated correctly.
* 3 = failed to migrate.

Therefore, all records with a status equal to 3 need to be analyzed by Developers and/or Content Authors to either fix them, or delete them. You can get the total number of Legacy Files that failed to migrate running a query like this:

```sql
SELECT COUNT(*) FROM file_migration WHERE status = 3;
```
If you get NO results, the move on to the <a id="success">After a Successful Migration</a> section. Otherwise, go to the <a id="fixingProblems">Fixing Problems and Re-running the Migrator</a> section.


## <a id="success">After a Successful Migration</a>

Once your dotCMS instances processed all the Legacy Files in the database, you need to follow these steps:

1. Stop and un-deploy the Dynamic Plugin from all dotCMS instances immediately by removing the bundle .JAR file from the Felix OSGi container (`${DOTCMS_HOME}/dotserver/tomcat-8.0.18/webapps/ROOT/WEB-INF/felix/load/`) or un-deploying it using the dotCMS UI (*CMS Admin -> Dynamic Plugins -> Stop -> Undeploy*).
2. As mentioned in the <a id="presetup">Pre-setup</a> section, go through the Files that you kept in a list before starting the migration and verify that they are now Files as Content. If 
3. Stop your dotCMS instance(s).
4. Un-deploy the `${PLUGIN_FOLDER}/legacy-file-migration-overwrite/` Static Plugin. 
5. Run the ``sql_scripts/post-postgres.sql`` script which deletes the DB objects required by this plugin.
6. Start up dotCMS.
7. **IMPORTANT:** Kick off a reindex specifically for the `File Asset` Content Type (not necessary to reindex all Content Types). 
8. As a cleanup task, ask the dotCMS Support/Engineering Team for help with the shell script that will delete the physical Legacy Files from the File System and other no-longer-required information.

Keep in mind that subsequent Upgrade Tasks that run when upgrading your dotCMS environment to 4.x or above will delete any table and data that contains or references information from Legacy Files, so you don't need to worry about that.


## <a id="fixingProblems">Fixing Problems and Re-running the Migrator</a>

Records in the `file_migration` with a status of 3 are usually records that need to be inspected and fixed manually. So, once they have been handled appropriately -- fixed or deleted -- you need to update the `file_migration` table. To accomplish this, you need to run the following SQL query:

```sql
UPDATE file_migration SET status = 0, server_id = NULL, errormsg = NULL WHERE status >= 3;
```
Setting the `status` to 0 and the `server_id` to null will cause the plugin to **re-process ONLY the failed Legacy File records**. Setting the `server_id`  to NULL frees a record from a specific dotCMS instance so that other servers can help with the second processing round. If you don't run the previous update query, no records will be read by the plugin. At this point, again, make sure that you also flush ALL of your cache regions as well. If you're seeing unexpected errors in the system, sometimes it's also a good idea to un-deploy the plugin, restart the dotCMS instance(s), and re-deploy the plugin.

After and only after you have run the previous SQL query, start the Migrator Plugin once again, just like the first time. Repeat this process as many times as necessary until all Legacy Files are transformed into Files as Content.



________________________________________________________________________________________

# <a id="warnings">WARNINGS (!)</a>

* **ALWAYS CREATE A DB, INDEX, AND ASSETS BACKUP** before running the migration process in case unexpected errors arise and you need to start over.

* **DO NOT FORGET** to un-deploy the Migrator Plugin and the associated Static Plugin from all dotCMS instances once the process has finished correctly.

* Make sure you have access to the appropriate logging file in order to keep track of the migration process.

* The following Legacy Files will be **automatically deleted by the migration process, without exception**:

    * Files located under the "System Host".
    * Files pointing to an invalid Site.
    * Files with an invalid file name.
    * Files whose parent path has at least one folder that is missing, i.e., a folder that does not exist.
    * Files that are not associated to a file reference in the file system or point to a zero-size file.
