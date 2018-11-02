package com.dotmarketing.osgi.migrator.data;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.dotcms.repackage.org.apache.commons.lang.StringUtils;
import com.dotcms.repackage.org.apache.commons.lang.exception.ExceptionUtils;
import com.dotmarketing.business.CacheLocator;
import com.dotmarketing.common.db.DotConnect;
import com.dotmarketing.db.DbConnectionFactory;
import com.dotmarketing.db.HibernateUtil;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.osgi.migrator.Configuration;
import com.dotmarketing.osgi.migrator.MigrationStatus;
import com.dotmarketing.util.ConfigUtils;
import com.dotmarketing.util.Logger;

/**
 * Provides all the DB operations related to the process of migrating Legacy
 * Files to Files as Content. In the end, the migration process was improved to
 * work very similar to the Reindex Process in dotCMS.
 * <p>
 * Using a separate SQL script, basic information of Legacy Files is added to a
 * DB table created for this specific migration. This allows a cluster of dotCMS
 * instances to run the migrator plugin on each node, improving performance and
 * executing the process in a parallel manner, just like the Reindex Process.
 * </p>
 * 
 * @author Jose Castro
 * @version 3.7.1
 * @since Oct 9, 2018
 *
 */
public class MigratorDAO {

	private Configuration pluginConfig;

	private MigratorDAO(Configuration pluginConfig) {
		this.pluginConfig = pluginConfig;
	}

	/**
	 * Singleton holder of the MigratorDAO instance.
	 */
	private static class SingletonHolder {

		private static MigratorDAO INSTANCE;

		private static MigratorDAO createInstance(Configuration pluginConfig) {
			INSTANCE = new MigratorDAO(pluginConfig);
			return INSTANCE;
		}

	}

	/**
	 * Returns an instance of the {@link MigratorDAO} class.
	 * 
	 * @param pluginConfig
	 *            - The {@link Configuration} object containing the configuration
	 *            properties of this plugin.
	 * @return A unique instance of the {@link MigratorDAO} class.
	 */
	public static MigratorDAO getInstance(Configuration pluginConfig) {
		return SingletonHolder.createInstance(pluginConfig);
	}

	/**
	 * Sets a valid parent path for legacy files whose parent path is null or empty,
	 * which can be caused by different circumstances. This is a data inconsistency
	 * and must be fixed before migrating such legacy files to files as content.
	 * 
	 * @throws DotDataException
	 *             An error occurred when updating the records in the data source.
	 */
	public void recreateMissingParentPath() throws DotDataException {
		final DotConnect dc = new DotConnect();
		final String whereClause = "WHERE parent_path IS NULL OR parent_path = '' OR parent_path = ' ' AND asset_type = 'file_asset'";
		String query = "SELECT id FROM identifier " + whereClause;
		dc.setSQL(query);
		final List<Map<String, Object>> results = dc.loadObjectResults();
		if (!results.isEmpty()) {
			Logger.info(this.getClass(), " \n=== A total of " + results.size()
					+ " Legacy Files have an invalid parent path. Fixing data... ===");
			query = "UPDATE identifier SET parent_path = '/' " + whereClause;
			dc.setSQL(query);
			dc.loadResult();
			for (final Map<String, Object> record : results) {
				final String identifier = record.get("id").toString();
				CacheLocator.getIdentifierCache().removeFromCacheByIdentifier(identifier);
			}
		}
	}

	/**
	 * Returns the total count of Legacy Files in the system that are not being
	 * processed by any dotCMS instance yet.
	 * 
	 * @return The number of Legacy Files that are ready for migration.
	 * @throws DotDataException
	 *             An error occurred when accessing the data source.
	 */
	public String getCountPendingLegacyFiles() throws DotDataException {
		final DotConnect dc = new DotConnect();
		dc.setSQL("SELECT COUNT(identifier) total FROM file_migration WHERE server_id IS NULL");
		final List<Map<String, Object>> results = dc.loadObjectResults();
		String totalCount = "0";
		if (null != results && !results.isEmpty()) {
			totalCount = results.get(0).get("total").toString();
		}
		return totalCount;
	}

	/**
	 * Updates the migration status of a Legacy File when it has been processed
	 * successfully.
	 * 
	 * @param legacyFile
	 *            - The Legacy File that was migrated.
	 * @param status
	 *            - The successful status, defined by the {@link MigrationStatus}
	 *            Enum.
	 * @throws DotDataException
	 *             An error occurred when updating the Legacy File's processing
	 *             status.
	 */
	public void updateLegacyFileRecord(LegacyFile legacyFile, MigrationStatus status) throws DotDataException {
		updateLegacyFileRecord(legacyFile, status, null);
	}

	/**
	 * Updates the migration status of a Legacy File when an error occurred when
	 * processing.
	 * 
	 * @param legacyFile
	 *            - The Legacy File that was not migrated.
	 * @param status
	 *            - The failed status, defined by the {@link MigrationStatus} Enum.
	 * @param exception
	 *            - The Java stack trace originated by the error during the
	 *            migration of the file.
	 * @throws DotDataException
	 *             An error occurred when updating the Legacy File's processing
	 *             status.
	 */
	public void updateLegacyFileRecord(LegacyFile legacyFile, MigrationStatus status, Exception exception)
			throws DotDataException {
		DotConnect dc = new DotConnect();
		StringBuilder query = new StringBuilder();
		query.append("UPDATE file_migration SET status = ").append(status.getStatus());
		if (null != exception) {
			query.append(", errormsg = '").append(ExceptionUtils.getStackTrace(exception)).append("'");
		}
		query.append(" WHERE identifier = '").append(legacyFile.getIdentifier()).append("'");
		dc.setSQL(query.toString());
		dc.loadResult();
	}

	/**
	 * Fills the queue of Legacy Files that will be processed by the plugin. This is
	 * the same approach used by the Reindex Process: Legacy Files are processed in
	 * batches.
	 * 
	 * @param legacyFiles
	 *            - The list that will contain the {@link LegacyFile} objects to
	 *            migrate.
	 * @throws DotDataException
	 *             An error occurred when retrieving the batch of Legacy Files.
	 */
	public void fillLegacyFileQueue(final List<LegacyFile> legacyFiles) throws DotDataException {
		try {
			HibernateUtil.startTransaction();
			legacyFiles.addAll(getLegacyFileBatchFromDb());
			HibernateUtil.commitTransaction();
		} catch (Exception ex) {
			HibernateUtil.rollbackTransaction();
			throw (ex);
		} finally {
			HibernateUtil.closeSession();
		}
	}

	/**
	 * Loads the next batch of records pulled from the DB table that was created to
	 * store the references all the Legacy Files that will be transformed into Files
	 * as Content. This mechanism is based on the way that the Reindex Process
	 * works: A Function or Stored Procedure that pulls a specific amount of
	 * records, "assigns" them to a specific dotCMS instance, and returns them to
	 * the code that handles the migration.
	 * <p>
	 * Three parameters that determine the behavior of this method can be configured
	 * via the <code>/src/main/resources/plugin.properties</code> file:
	 * <ul>
	 * <li><code>legacyfile.migrator.loadquery</code>: The SQL call to the query
	 * that will fetch the data.</li>
	 * <li><code>legacyfile.migrator.batchsize</code>: The number of records that
	 * the previous query will pull.</li>
	 * <li><code>legacyfile.migrator.status</code>: Records with a status lower or
	 * equal than the specified will be returned.</li>
	 * </ul>
	 * </p>
	 * 
	 * @return A list containing a specified number of Legacy File references that
	 *         will be migrated.
	 * @throws DotDataException
	 */
	private Collection<? extends LegacyFile> getLegacyFileBatchFromDb() throws DotDataException {
		final DotConnect dc = new DotConnect();
		final List<LegacyFile> legacyFiles = new ArrayList<>();
		final List<Map<String, Object>> results;
		Connection conn = null;
		final String serverId = ConfigUtils.getServerId();
		try {
			conn = DbConnectionFactory.getConnection();
			conn.setAutoCommit(Boolean.FALSE);
			dc.setSQL(pluginConfig.getLoadQuery());
			dc.addParam(serverId);
			dc.addParam(pluginConfig.getBatchSize());
			dc.addParam(pluginConfig.getStatus());
			results = dc.loadObjectResults(conn);
			for (final Map<String, Object> record : results) {
				final String identifier = record.get("identifier").toString();
				final int status = Integer.parseInt(record.get("status").toString());
				final LegacyFile legacyFile = new LegacyFile(identifier, serverId, status, StringUtils.EMPTY);
				legacyFiles.add(legacyFile);
			}
		} catch (final SQLException e) {
			Logger.error(this, "An error occurred when retrieving the batch of Legacy Files.", e);
			throw new DotDataException(e.getMessage(), e);
		} finally {
			try {
				if (null != conn) {
					conn.commit();
				}
			} catch (final Exception e) {
				Logger.error(this, e.getMessage(), e);
			} finally {
				try {
					if (null != conn) {
						conn.close();
					}
				} catch (final Exception e) {
					Logger.error(this, e.getMessage(), e);
				}
			}
		}
		return legacyFiles;
	}

}
