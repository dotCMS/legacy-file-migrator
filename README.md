# Legacy File Migrator for dotCMS 3.7.1

This OSGi plugin is in charge of migrating the deprecated Legacy Files to the new Files as Content. This migration is required for all customers who will be upgrading their environments to dotCMS 4.x and up as Legacy Files will not be handled by our code base anymore.

* [How to build this plugin](#build)
* [Pre-setup](#presetup)
* [Configuring your Log4J2 Logger](#logging)
* [Running the Legacy File Migrator](#running)
* [Fixing Problems and Running the Migrator Again](#fixingProblems)
* [After a Successful Migration](#success)
* [WARNINGS (!)](#warnings)



## <a id="build">How to build this plugin</a>

To install it, all you need to do is build the JAR. To do this, just run
``./gradlew clean jar`` and Gradle will build a .JAR inside the "build/libs/" directory. Building the first time will take longer as many dependencies will be downloaded.


## <a id="presetup">Pre-setup</a>

Please follow these steps in order to set up everything you need for the migration process:

1. Before running the migration process, it's very important to know the exact number of Legacy Files that will be transformed into content so that you can keep a record of them. In order to get this number, please run the following SQL query in your database:
```sql
SELECT COUNT(DISTINCT(identifier)) FROM file_asset;
```
2. **IMPORTANT:** In case you have a custom page extension (e.g., the "dot" postfix used in HTML pages), make sure that such a plugin is in place.
3. It's a good idea to run the Fix Assets Inconsistencies tool before running the migrator. Inconsistent data can interfere with the process.
4. As a health check, make sure that you have successfully re-indexed 100% of your contents and the ElasticSearch index is working correctly.

Before starting the migration process, there a are few configuration properties that you can review or set which allow you to have more control on the behavior of the plugin:

Property | Description
--------| -----------
*legacyfile.migrator.loadquery* | The SQL query that will return the Legacy Files that will be migrated to Files as Content. Similar to the Reindex Process approach, this is likely to be the call to a Stored Procedure or Function that returns a batch of a specific size to the code in the plugin. The call can be different between databases.
*legacyfile.migrator.batchsize* | The number of records that will be returned by the previous SQL query. A DB transaction will be created for each batch.
*legacyfile.migrator.status* | The status code assigned to a Legacy File when it's being processed: 0 (not processed yet), 1 (starting migration process), 2 (migrated successfully), 3 (failed to migrate). This property used to fetch records whose status is lower or equal than the value of this property. 
*legacyfile.migrator.loggingfrequency* | The number of logging lines that will be sent to the log file at once. Instead of logging a new entry for every migrated file, this property queues every logging line up to the specified "loggingfrequency" value, and send them to the log file. This helps minimize the constant I/O output.


## <a id="logging">Configuring your Log4J2 Logger</a>

By default, the plugin will log all of its progress and potential errors to the default dotCMS file: ``dotcms.log``. However you can make  the plugin log information to a different file. All you need to do is compare the `log4j2-default-config.xml` file in this plugin with the vanilla [log4j2.xml](https://github.com/dotCMS/core/blob/3.7.1/dotCMS/WEB-INF/log4j/log4j2.xml) file of the dotCMS 3.7.1 distro, and apply the specific changes to your current Log4J2 configuration. Our changes include:
* Two properties.
* One async appender.
* Only rolling file configuration.
* One logger.


## <a id="running">Running the Legacy File Migrator</a>

The migrator process will start as soon as the plugin is uploaded. It runs on a separate thread in order to keep the migration from interfering with the OSGi Framework. This way, the purpose of the ``start`` process of the bundle will just be spawning the migration thread. It's also worth noting that the plugin **can be deployed in every single instance of a dotCMS cluster**. This improves the performance by making it scalable, so that all nodes can help process different sets of Legacy Files at once. This is the same approach used by the dotCMS Reindex Process where all nodes in a cluster assist in the creation of a new content index. Remember, after making all the required changes, all you need to do is run the ``./gradlew clean jar`` command, and you're good to go.

In order to install this bundle, copy the bundle JAR file inside the Felix OSGi container (`${DOTCMS_HOME}/dotserver/tomcat-8.0.18/webapps/ROOT/WEB-INF/felix/load/`) or upload the bundle JAR file using the dotCMS UI (CMS Admin -> Dynamic Plugins -> Upload Plugin) in as many dotCMS nodes as you want. All you have to do is make sure that all of them point to the same assets and DB. Right after deploying it, you'll see information such as the following in the log file:
```
[16/10/18 15:41:21:360 EDT]  INFO migrator.LegacyFilesMigrator:  
 
=======================================================================
===== Initializing conversion of Legacy Files to Files as Content =====
=======================================================================

[16/10/18 15:41:21:360 EDT]  INFO migrator.LegacyFilesMigrator: NOTE: Files as Contents CANNOT LIVE UNDER SYSTEM_HOST anymore. Therefore, such Legacy Files will be automatically deleted.
[16/10/18 15:41:21:391 EDT]  INFO migrator.LegacyFilesMigrator: ---> Number of Legacy Files left to migrate: 607
[16/10/18 15:41:21:391 EDT]  INFO migrator.LegacyFilesMigrator: ---> Batch size: 50
[16/10/18 15:41:21:391 EDT]  INFO migrator.LegacyFilesMigrator: ---> Instance ID: 86579328-d970-4418-a163-fc8d404d476e
[16/10/18 15:41:21:391 EDT]  INFO migrator.LegacyFilesMigrator: ---> Start time: 2018-10-16 15:41:21.390
```

The initial logging provides useful information that allows you to keep track of the migration process:

* The number of Legacy Files that the dotCMS instance where the plugin was deployed can migrate.
* The size of the batch that contains the references to the Legacy Files. After all the elements in the bacth are migrated, the DB transacction is committed.
* The ID of the dotCMS instance where the plugin was deployed. This is useful in order to determine what records in the temporary table of the migrator plugin are being or have been processed by this instance.
* The start time of the migration. 

The following considerations must be taken into account before running this plugin:
1. The first step is to run the pre-execution script that applies for the database your dotCMS is running on (e.g, ``sql_scripts/pre-postgres.sql``). This script is going to create a special table to store the references to the Legacy Files, the function or stored procedure that reads the entries in it, and other additional queries.
2. Before performing the actual migration, all Identifiers whose parent path is null or empty will have the following value: "/".
3. All Legacy Files with the "SYSTEM_HOST" Inode **will be deleted, NOT MIGRATED**, as the new Files as Content MUST live inside a specific Site.
4. Legacy Files are not associated to a file reference (missing file) in the file system will be deleted, no exception.
5. In order to improve the performance of the migration process:
5.1 The physical file associated to a Legacy File **WILL NOT BE DELETED by the migration process**. The Engineering Team will provide a special script that will delete them.
5.2 The deletion of Legacy Files has been updated to ONLY delete the Identifier and either the Working or Live Inode. These two pieces of information are re-used for the new File as Content. All the remaining data will be manually deleted later or deleted as part of other Upgrade Tasks when moving to a dotCMS 4.x version or above.
6. Please try to keep track of the log file in order to get more information about any potential error or Java exception when migrating a Legacy File.
7. Once the migration is done, run the post-execution script for the database your dotCMS is running on (e.g, ``sql_scripts/post-postgres.sql``). This script will clean up any temporary DB objects tat were created for the migrator plugin.

Once the migration is over, the following message will be displayed in the logging file:
```
[16/10/18 15:42:54:788 EDT]  INFO migrator.LegacyFilesMigrator: ---> End time: 2018-10-16 15:42:54.788

[16/10/18 15:42:54:788 EDT]  INFO migrator.LegacyFilesMigrator: ---> Total time: 0 Days 0 Hours 1 Minutes 33 Seconds

[16/10/18 15:42:54:788 EDT]  INFO migrator.LegacyFilesMigrator:  

-> Total processed files = 607
 
All legacy files have been processed. Please undeploy the Legacy File Migrator plugin now.
```

The finalization summary provides the following:

* The end date of the migration process.
* An easier display of the days/hours/minutes/seconds that the migration process needed to transform all Legacy Files.
* The number of Legacy Files that the current dotCMS instance processed correctly.

At this moment, please proceed to stop and un-deploy the migrator from all dotCMS instances immediately by removing the bundle JAR file from the Felix OSGi container (`${DOTCMS_HOME}/dotserver/tomcat-8.0.18/webapps/ROOT/WEB-INF/felix/load/`) or un-deploying the bundle using the dotCMS UI (CMS Admin -> Dynamic Plugins -> Undeploy).


## <a id="fixingProblems">Fixing Problems and Running the Migrator Again</a>

In the example depicted in the previous section, "607" Legacy Files are detected, and they all were processed correctly. Different situations can make the migration process fail, some of them might be specific to the Legacy File being processed, database timeouts, unexpected system restarts, and so on. Keep in mind that status of Legacy Files falls into 4 categories:
* 0 = not being processed yet.
* 1 = going through the migration process.
* 2 = migrated correctly.
* 3 = failed to migrate.

Therefore, all records with a status equal or higher than 3, need to be analyzed by content authors and/or developers to either fix them, or delete them. You can get the total number of Legacy Files that failed to migrate running a query like this:

```sql
SELECT COUNT(*) FROM file_migration WHERE status >= 3;
```
That query can also help you validate that all Legacy Files were migrated without problems (if you get zero results) at the end of the process. So, once they have been handled appropriately, you need to update the `file_migration` table and re-deploy the plugin to process them again. To accomplish this, all you have to do is run a SQL query like this:

```sql
UPDATE file_migration SET status = 0, server_id = NULL WHERE status >= 3;
```
Setting the `status` to 0 and the `server_id` to null will cause the plugin to **re-process the failed Legacy File records ONLY**. Re-setting the `server_id` allows you to deploy the plugin in other dotCMS instances so that other servers can help with the second processing round. After and only after you have run the previous query, go to the Dynamic Plugins portlet and start the Migrator Plugin once again, just like the first time. If you don't run the previous update query, no records will be read by the plugin. Repeat this process as many times as necessary until all Legacy Files are transformed into Files as Content.


## <a id="success">After a Successful Migration</a>

Once your dotCMS instances are able to transform all Legacy Files, you need to follow these steps:

1. Run the ``sql_scripts/post-postgres.sql`` script which deletes the DB objects required by this plugin.
2. Ask the Support/Engineering Team for the shel script that will delete the physical Legacy Files from the File System and other non-necessary information.

Keep in mind that subsequent Upgrade Tasks that run when upgrading your dotCMS environment will delete any table that contains or references information on Legacy Files, so you don't need to worry about that.



________________________________________________________________________________________

# <a id="warnings">WARNINGS (!)</a>

* **ALWAYS CREATE A DB AND ASSETS BACKUP** before running the migrator process in case particular errors arise and it's required to start all over.

* **DO NOT FORGET** to un-deploy the migrator plugin from all dotCMS instances once the process has finished correctly.

* Make sure you have access to the appropriate logging file in order to keep track of the migration process.

* As mentioned before, Legacy Files located under the "System Host" will be automatically deleted during the migration without exception as Files as Content **CANNOT** live under such a Site.

* As mentioned before, Legacy Files that lost or are not associated to a file reference in the file system will be deleted, no exception.
